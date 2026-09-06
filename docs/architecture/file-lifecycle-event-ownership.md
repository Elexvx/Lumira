# File Lifecycle Event Ownership

## Decision

The first File lifecycle projection is an owner-local, at-least-once event
flow. `lumira-file` remains the only writer of File business data and
projection data. `lumira-async` owns Redis Stream consumption, pending-entry
recovery and DLQ operations; it never opens a File database. `lumira-job-executor`
is recovery-only and does not become a second consumer.

This is an event-driven boundary inside the modular monolith. It does not add
a central event gateway, Kafka, a second File outbox, or a physical File
microservice before the ownership and recovery contracts require one.

## Delivery path

```text
File transaction
  -> platform_event_outbox
  -> lumira-async relay
  -> Redis Stream: lumira.events.file.v1
  -> File lifecycle consumer
  -> POST /file/internal/events/file-object-uploaded
  -> lumira-file owner transaction
```

The consumer ACKs only after the owner returns a durable decision. A transport
failure, owner timeout or owner database failure leaves the Stream entry
pending for retry. A malformed or unsupported event is copied to the File
consumer DLQ and ACKed only after the DLQ write succeeds.

## Owner transaction and idempotency

The File owner handles one command in one MySQL transaction:

1. Validate the envelope and recompute `payloadDigest`.
2. Insert `file_event_receipt` with a unique `event_id`.
3. Read the current File projection version.
4. Clear `is_current` on the previous current row when this event is newer.
5. Insert the event history row in `file_event_projection`.
6. Mark the receipt `SUCCEEDED`.

The receipt and projection are never committed independently. A duplicate
event returns `false` as an already-completed decision and does not insert a
second projection row; a first application returns `true`. The Async consumer
ACKs both decisions, and records the duplicate in its transport metrics.

There is deliberately no Redis receipt. Redis Stream PEL/group state is the
transport recovery mechanism; MySQL is the File owner idempotency authority.

`file_event_projection` keeps one row per `(file_id, aggregate_version)` so
out-of-order delivery is recorded without allowing an older event to become
current. `is_current=1` identifies the latest applied version. Queries use the
indexed current row rather than a future `MAX()`-based inference.

## Internal security boundary

The File owner command uses the scoped `X-File-Token`. `X-Job-Token` remains
reserved for Job recovery endpoints. Both sides require the event identity and
producer headers to match the envelope; the runtime release header is also
required. The body carries the event release ID and payload digest for replay
and auditability.

The stable contract is
[`FILE_OBJECT_UPLOADED.v1.yaml`](../events/contracts/file/FILE_OBJECT_UPLOADED.v1.yaml).
Its producer, source module and owner are all explicit, and its schema digest
is checked by `bin/check-event-contracts.mjs`.

## Operational controls

The Async runtime exposes source length, pending count, oldest pending age,
DLQ count, duplicate count and projection-success count for this consumer. The
protected recovery surface supports bounded DLQ inspection and replay under
the existing Job recovery fence. Replay appends a sanitized record back to the
source Stream before deleting the DLQ record.

The timeout-after-owner-commit test intentionally simulates the important
failure window: the owner records the receipt/projection, the response times
out, Async retries the same event, and the owner reports a duplicate without a
second projection write.
