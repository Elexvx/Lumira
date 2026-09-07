# Redis production topology

## Contract

Production uses two physical Redis instances. Logical database numbers are not an isolation boundary and are not used by the production Compose topology.

| Plane | Service | Policy | Persistence | Contents |
| --- | --- | --- | --- | --- |
| Cache | `redis-cache` | `allkeys-lru` | none required | Rebuildable caches and short-lived rate-limit buckets only |
| Runtime | `redis-runtime` | `noeviction` | AOF, `appendfsync everysec` | Session, authorization/read-model versions, locks, idempotency, Streams, DLQ and job state |

Each plane has an independent host, port, password, memory limit, volume, health check and exporter. `redis-cache` loss must never invalidate or recreate runtime facts. `redis-runtime` exhaustion fails writes instead of evicting authentication or delivery state; authentication and authorization paths remain fail-closed.

`lumira-server` and `lumira-async` bind their existing Spring Redis connection to `REDIS_RUNTIME_*`. The server also receives `REDIS_CACHE_*` for cache-specific adapters. During the first safety cutover, any unclassified existing key stays on runtime Redis. A key may move to cache Redis only after its owner proves that it is rebuildable and has no session, authorization, lock, idempotency, Stream, DLQ or task-state semantics.

The authoritative key registry is [`redis-key-ownership.yaml`](redis-key-ownership.yaml). In production, `REDIS_CACHE_ENABLED=true` makes `RedisConfig` create a separate cache `LettuceConnectionFactory`; message read-model counts, display-name caches, WebSocket tickets and WeChat access-token caches use that template. Runtime session, authorization, idempotency, fence and Stream paths remain on the primary runtime template. The `redisPlane` health contributor and `lumira.redis.plane.*` metrics expose both ping results and whether the two templates use different physical connection factories. If isolation is required but the application falls back to the runtime template, health is `DEGRADED`.

## Stream retention and monitoring

`REDIS_RUNTIME_STREAM_MAXLEN` and `REDIS_RUNTIME_DLQ_MAXLEN` are the deployment contract for producer-side approximate trimming. Trimming must not delete entries that are still pending. Alert on:

- `evicted_keys > 0` on either instance; runtime Redis must also retain `maxmemory_policy=noeviction`.
- runtime memory near the configured maximum or rejected writes caused by `noeviction`.
- pending count, oldest pending age (default threshold `REDIS_RUNTIME_PENDING_OLDEST_ALERT_SECONDS=300`) and DLQ count per owner/group.
- AOF rewrite/load errors, exporter loss and health-check failures.

DLQ replay is an authenticated control-plane operation. It records event id, owner, operation epoch/fence token, original delivery metadata and replay result. Never replay by copying arbitrary Redis keys.

## Migration

1. Back up MySQL and the old Redis instance; retain the evidence with the current Release Set.
2. Start `redis-cache` and `redis-runtime` without switching application traffic. Verify password authentication, health checks, exporters, `allkeys-lru`, `noeviction`, and AOF configuration.
3. Quiesce/drain Async and Job work as required by Release Set v3.
4. Copy only classified runtime keys and Streams to `redis-runtime`. Do not bulk-copy unknown keys into cache Redis.
5. Deploy the whole Release Set whose manifest declares `redis-split-cache-runtime-v1`; verify Server readiness and worker identity before switching UI/API together.
6. Keep the old Redis artifact through the rollback deadline. Cache Redis may warm naturally after traffic switch.

## Rollback

Rollback restores the complete `previousRelease`: UI, Server, Async and Job Executor together. It is allowed only when the previous Release Set declares the same Redis topology identity and can read the target protocol/schema versions. Cache data is never restored. Runtime Redis is preserved across an application rollback; restoring a Redis backup is a separate disaster-recovery action that requires write freeze, worker stop, evidence capture and an explicit recovery plan.
