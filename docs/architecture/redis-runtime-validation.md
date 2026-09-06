# Redis runtime validation

The two Redis planes are physically separated in production, but the split is
not considered proven by a successful `PING` alone. The release gate must
capture policy, eviction, persistence, Stream pending state and retention
evidence for the exact Release Set.

## Read-only validation

Set `REDIS_RUNTIME_URL` and `REDIS_CACHE_URL` to the authenticated Redis URLs
for the target deployment and run:

```bash
REDIS_RUNTIME_URL='redis://:***@redis-runtime:6379/0' \
REDIS_CACHE_URL='redis://:***@redis-cache:6379/0' \
REDIS_RUNTIME_STREAM_KEY='lumira.events.payment.v1' \
REDIS_RUNTIME_STREAM_GROUP='lumira-async-payment' \
REDIS_RUNTIME_STREAM_MAXLEN=100000 \
node bin/redis-runtime-chaos-test.mjs
```

The command is intentionally read-only. It checks:

- runtime policy is `noeviction`, AOF is enabled, and `evicted_keys` is zero;
- cache policy is `allkeys-lru` and its eviction counter is reported but is
  not treated as a runtime-data failure;
- the configured Stream group exposes `XPENDING` data and the source length
  does not exceed the approximate retention budget by more than the documented
  tolerance;
- the configured URLs resolve to two different physical endpoints.

The command does not run `FLUSHDB`, change `CONFIG`, write test messages, or
claim pending entries. A separate controlled drill must create an unacked test
message, observe `XPENDING`, run the real consumer recovery path, and retain
the before/after evidence. That drill must be performed only against an
isolated deployment or maintenance window.

## Readiness and metrics

`RedisPlaneHealthIndicator` fails runtime readiness when runtime stats cannot
be read or `evicted_keys > 0`; cache eviction is observable and permitted. The
runtime Stream consumer exposes:

- `redis_runtime_stream_pending`;
- `redis_runtime_stream_oldest_pending_age`;
- the existing owner-specific Stream length and DLQ gauges.

The minimum acceptance evidence is a runtime health response showing dedicated
planes, `runtimeEvictedKeys=0`, and available runtime stats, plus a captured
`XPENDING`/recovery result and an `XLEN`/MAXLEN result for every production
Stream group.

## Failure and rollback

If runtime Redis reports eviction, unknown stats, a shared physical endpoint,
or a retention breach, stop the Release Set switch and keep the previous
application slot. Do not repair the situation by flushing Redis. Preserve the
runtime AOF/backup and the captured evidence; cache data may be rebuilt after
the application rollback.
