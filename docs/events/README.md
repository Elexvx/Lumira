# Lumira event contracts

These contracts describe the first event-driven flows without introducing a
central event service. Producers write their owner-local transactional outbox;
`lumira-async` relays or consumes Redis Streams; consumers keep idempotency and
business writes in the owner that owns those tables.

The wire-compatible payment event remains `PAYMENT_ORDER_PAID`. The contract
name `PaymentSucceeded` is used in documentation so the business meaning is
clear without breaking existing outbox rows and consumer groups.

Required evidence for a new consumer is:

- a stable event identity and aggregate identity;
- an owner-side durable consumption receipt keyed by consumer and `eventId`;
- retry by leaving the Stream entry pending;
- bounded delivery attempts with a consumer-owned DLQ;
- an authenticated replay path that validates a recovery fence.

The IAM contracts are now consumed by `lumira-async` only for Runtime Redis
authorization-version invalidation. The consumer never opens the IAM database,
changes permission data, or invalidates sessions; a missing version key is
rehydrated by the fail-closed control-plane authorization path.

The IAM consumer exposes both the existing `lumira.iam.authz.consumer.*`
meters and release-gate counters named `iam_event_invalidation_success_total`,
`iam_event_duplicate_total`, `iam_event_dlq_total`, and
`iam_event_schema_reject_total`. The Docker-backed plugin migration drill emits
`PASS` evidence when Docker is available and classifies an unavailable local
daemon as `SKIPPED_ENVIRONMENT`; a release candidate must run it with Docker
available. Run it explicitly with `node bin/plugin-migration-recovery-docker.mjs`
so an ordinary source-contract test run does not start Docker containers.

The File lifecycle projection follows the same owner-local pattern. The File
owner stores `file_event_receipt` and `file_event_projection` in MySQL, while
`lumira-async` owns Stream recovery and the File DLQ. See
[`file-lifecycle-event-ownership.md`](../architecture/file-lifecycle-event-ownership.md)
and the versioned File contracts under `docs/events/contracts/file/`.

## Contract governance

Checked-in contracts live under `docs/events/contracts/<domain>/`. Each version
declares the canonical `EventEnvelope` identity fields, the producer and source
module, the delivery mode, and a `schemaDigest`. The digest is a SHA-256 of the
normalized contract schema with the `schemaDigest` line removed; it is a schema
checksum, not a checksum of an individual event payload.

`bin/check-event-contracts.mjs` is the CI gate. It rejects duplicate event
identities, missing envelope fields, digest drift, version gaps, and backward
incompatibilities. A new version must retain the previous version's required
fields and their declared types. This is intentionally repository-local
governance for now; Kafka, a schema registry, and a new event service are not
required to evolve these contracts safely.
