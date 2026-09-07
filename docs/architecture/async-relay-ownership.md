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

Normal Async publication additionally acquires a per-owner relay generation with
holder and short lease state in the same Redis hash. The normal holder is
`lumira-async-<instance-id>`; a validated recovery request atomically advances
the relay generation to `job-recovery` and binds that takeover to the exact
recovery epoch/token. Each owner request carries
`X-Lumira-Relay-Owner`, `X-Lumira-Relay-Generation` and
`X-Lumira-Relay-Fence`. The Async lane checks the generation immediately
before invoking the remote port, and every owner relay/replay endpoint checks
the same Redis state immediately before entering its outbox claim path. A lost
fence fails fast, is not retried, and increments
`lumira.async.fence.reject.count`.

The mapped HTTP endpoint is the only production entry into the owner claim
path. The source-compatible direct Java overloads are intentionally not
request mappings and remain available only to narrow unit tests and local
assembly code. Both the Async lane and the owner boundary fail closed when
the runtime fence store is unavailable.

## Minimal event envelope

Cross-module producers may use `com.lumira.api.event.EventEnvelope` for the
stable identity, producer, aggregate/version, schema, timestamp, trace,
release, payload digest and payload metadata shared by integration events.
Aggregate version, release ID and payload digest remain optional in the
compatibility constructor until an owner outbox exposes those facts. It is
intentionally only a DTO: it does not select a transport, replace the
transactional outbox, or require a platform-wide event-bus rewrite. Existing
owner outboxes and relay contracts remain the delivery authority while
producers and consumers adopt the fields incrementally.

The initial adapter mapping is deliberately owner-local: outbox row `id` is
the event identity, `event_type` is the event type, `source_type` or the owner
name is the source/producer, `event_key` is the aggregate identity, and
`payload_json` is the payload. Existing `trace_id`/request metadata fills
trace fields where present. No new global offset or cross-database event
sequence is introduced by this contract.

## First real event consumer: payment notification

The first production-shaped consumer is intentionally narrow: the payment
owner writes `PAYMENT_ORDER_PAID` to its transactional outbox, Async relays it
to `lumira.events.payment.v1`, and the notification consumer sends an
authenticated command to the message owner. Async does not open the message
database and the message owner remains the only writer of notification tables.

The message owner applies `message-payment-notification-v1` as a durable
consumer receipt keyed by the canonical event ID. A successful receipt creates
the system notification and delivery-log side effects; a duplicate receipt
returns success without repeating those writes. A transient owner/provider
failure leaves the source entry pending, while malformed or exhausted entries
move to the notification DLQ. The protected replay endpoint appends the
sanitized record back to the source stream before deleting the DLQ entry and
requires the recovery fence.

The notification consumer exposes pending count, oldest pending age, source
length, DLQ count, consumed/duplicate/failure/reclaim counters, and a bounded
DLQ inspection/replay surface. This closes one complete event-driven path
without introducing a central event service, Kafka, or a physical service
split. IAM event contracts remain documentation-only until their
fail-closed authorization consumer is designed and tested.

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
- Owner server relay/replay endpoints now validate the relay generation at their final HTTP boundary. The five owner outbox tables still have divergent claim schemas and do not yet persist a shared `relay_generation` column in their SQL compare-and-set. That database CAS is deliberately deferred while there is one normal Async owner lane and one mapped owner endpoint per owner; revisit it if multiple relay runtimes, a physically split owner service, or strict financial claim/publish atomicity makes the HTTP fence window insufficient. Network policy must continue to prevent Job from bypassing Async and calling owner replay endpoints directly.
- Redis approximate `MAXLEN` trimming does not inspect consumer-group PEL entries. Under an extreme backlog it can evict a source record that remains pending; production limits and oldest-pending alerts must be sized so the cap is never reached during supported recovery windows. A live-Redis integration test for this pressure case is still required.
- The protected DLQ replay surface currently covers the payment Stream consumed by `lumira-async`. Other pre-existing Stream consumers/producers require owner-specific metrics and replay surfaces before the requirement can be claimed platform-wide.
- Recovery fence correctness depends on `redis-runtime` durability and no-eviction policy. The
  in-memory fallback remains available only to narrow tests and non-production assemblies;
  Async readiness reports `DEGRADED` when the durable fence store is absent.
