# Async Relay Ownership

## Runtime ownership

`lumira-async` is the only runtime that performs normal, continuous Outbox Relay. The server remains the only business database owner and exposes narrow owner-specific internal endpoints; Async invokes those endpoints and never reads an outbox table directly.

`lumira-job-executor` does not run a normal relay loop. The former `AdaptiveRelayScheduler` and the five legacy `*OutboxRelayJobHandler` adapters were removed. XXL-JOB now exposes only these recovery handlers:

| Handler | Purpose |
| --- | --- |
| `outboxEventReplayJob` | replay one specified `eventId` |
| `staleOutboxRecoveryJob` | recover an explicitly selected stale owner |
| `manualOutboxRecoveryJob` | operator-requested owner recovery |
| `fencedOutboxTakeoverJob` | advance an owner fence before takeover |

Business cron jobs remain separate (`userExportTaskJob`, `registrationExportTaskJob`, `reviewAssignmentExpirationJob`, file processing, catalog rebuild, heartbeats and AI indexing). They are not Relay owners.

## Per-owner lanes

`OutboxRelayCoordinator` submits all owners before awaiting results. Each owner has a dedicated `OwnerRelayLane` with its own executor, bounded queue and circuit state. The default is one concurrent request and one queued request per owner; values can be overridden under `lumira.event.relay-loop.owners.<owner>`.

Each lane enforces:

- bounded queue and configured maximum concurrency (bulkhead);
- retry budget and bounded retry backoff;
- consecutive-failure circuit breaker and open duration;
- completion deadline at the coordinator boundary;
- independent drain leases so quiesce observes real in-flight work;
- owner and operation tagged submitted, rejected, retry, success, failure, published, duration, active, queued and circuit metrics.

A slow or open-circuit owner cannot prevent another owner lane from starting or completing. Queue rejection never spills work into a shared executor.

## Internal HTTP contract

`InternalHttpClientFactory` is shared by Async Relay, competition payment event delivery, alerting, and Job-to-runtime calls. It uses the JDK HTTP client with redirects disabled and requires an absolute trusted HTTP(S) origin.

Every request has:

- explicit connect and response timeouts;
- a hard maximum response size checked before JSON deserialization;
- `X-Trace-Id` (current trace or generated trace);
- `X-Lumira-Release-Id`;
- `X-Lumira-Event-Schema-Version`;
- a scoped `X-Job-Token`;
- an explicit retry mode. Only idempotent calls opt in to bounded retry.

Configuration is under `lumira.internal-http` for Async and `saas.job.internal-http` for Job. Responses above the configured limit and cross-origin paths fail closed.

## Recovery fencing

Job recovery request JSON must include:

```json
{
  "owner": "payment",
  "eventId": 12345,
  "operationEpoch": 42,
  "fenceToken": "a-random-secret-with-at-least-24-characters"
}
```

`eventId` is required only for `outboxEventReplayJob`. Job forwards `operationEpoch` and `fenceToken` as `X-Lumira-Operation-Epoch` and `X-Lumira-Fence-Token` to the Async recovery endpoint. Async validates the fence before submitting work to an owner lane.

The production fence is atomically advanced in `redis-runtime` at `lumira:runtime:recovery-fence:<owner>`. A higher epoch replaces the current token digest; the same epoch is accepted only with the same token; lower epochs and same-epoch token changes are rejected. Tokens are stored only as SHA-256 digests. The in-memory implementation is retained solely for narrow tests or assemblies without a Redis bean.

## Database boundary

Both runtime POMs apply Maven Enforcer `bannedDependencies` with transitive search for Spring JDBC, JDBC starter, MyBatis/MyBatis-Plus, Flyway and MySQL drivers. `spring-jdbc` is excluded from their `common-web` dependency. Neither runtime receives a business database URL or DataSource.

## Drain and release compatibility

Normal Relay tasks acquire independent `AsyncRuntimeDrainCoordinator` leases. Quiesce rejects new lane work while existing HTTP attempts finish within their response and completion deadlines. This preserves the Release Set v3 sequence: quiesce, drain, replace, verify identity/schema, then resume.

## Payment Stream retention and DLQ recovery

The normal payment producer and DLQ replay both append to `lumira.events.payment.v1` with approximate `XADD MAXLEN`; poison/exhausted records append to `lumira.events.payment.v1:dead-letter` with an independent approximate limit. The deployment contract is shared by Server and Async:

- `REDIS_RUNTIME_STREAM_MAXLEN` (default `100000`);
- `REDIS_RUNTIME_DLQ_MAXLEN` (default `50000`).

Async publishes current source length, pending count, oldest pending age, and DLQ count as `lumira.payment.consumer.stream.length`, `lumira.payment.consumer.pending.count`, `lumira.payment.consumer.pending.oldest.age.seconds`, and `lumira.payment.consumer.dead-letter.count`.

The scoped payment token protects DLQ stats/list/replay endpoints. Replay additionally requires `X-Lumira-Operation-Epoch` and `X-Lumira-Fence-Token`, advances the `payment-stream` recovery fence, appends the sanitized event before deleting its DLQ record, and returns/logs the replayed Stream ID, deletion outcome, and original failure metadata. The fence token itself is never logged.

## Remaining integration risks

- The fenced Async recovery APIs and Job handlers are implemented, but there is no operator-facing admin UI or durable audit table. Structured service logs are the current DLQ replay audit trail.
- Owner server replay endpoints remain individually idempotent but do not themselves persist `operationEpoch`/`fenceToken`; the authoritative fence is applied at the Async ingress. Network policy must continue to prevent Job from bypassing Async and calling owner replay endpoints directly.
- Redis approximate `MAXLEN` trimming does not inspect consumer-group PEL entries. Under an extreme backlog it can evict a source record that remains pending; production limits and oldest-pending alerts must be sized so the cap is never reached during supported recovery windows. A live-Redis integration test for this pressure case is still required.
- The protected DLQ replay surface currently covers the payment Stream consumed by `lumira-async`. Other pre-existing Stream consumers/producers require owner-specific metrics and replay surfaces before the requirement can be claimed platform-wide.
- Recovery fence correctness depends on `redis-runtime` durability and no-eviction policy. If the Redis bean is absent, Async starts with the documented in-memory fallback; production readiness should reject that state in a follow-up hardening change.
