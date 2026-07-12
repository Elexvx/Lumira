# Event Catalog v1

All integration events are persisted in the producing owner's MySQL outbox in
the same transaction as the aggregate change. Redis Streams is a delivery
transport and is never the system of record.

| Stream | Producer | Initial consumers | Ordering key | Consistency |
| --- | --- | --- | --- | --- |
| `lumira.events.file.v1` | file | scan, OCR, document parsing, AI indexing | file aggregate id | eventual; files remain unavailable until required scanning succeeds |
| `lumira.events.payment.v1` | payment | competition registration, entitlement, notice, audit, reconciliation | payment order id | payment state is synchronous; side effects are eventual and idempotent |
| `lumira.events.message.v1` | message | WebSocket and external channels | notice id | notice persistence precedes best-effort channel delivery |
| `lumira.events.plugin.v1` | plugin | plugin lifecycle projections | tenant-plugin id | eventual |

Envelope fields are defined by `IntegrationEventEnvelope`. Payloads may add
optional fields within a schema version. Removing, renaming, or changing the
meaning of a field requires a new event type or major stream version. Secrets,
tokens, file bodies, payment credentials, and complete personal identity data
must never be included.

Delivery is at least once. Consumers must use `event_consumer_receipt` through
`EventConsumptionGuard`; the side effect and receipt commit atomically before a
Redis record is acknowledged. Dead-letter replay retains the original event id.

Fresh databases receive the required tables from `sql/saas.sql`. Existing
databases must apply `sql/upgrade-event-platform-v1.sql` before the relay loop is
enabled.

## Payment order paid v1

`PAYMENT_ORDER_PAID` is emitted after the payment owner changes an order to
`PAID`. The payload envelope contains the payment order aggregate id and an
`attributes` object. Competition registrations use `bizType =
competition_registration`, `registrationId`, `userId`, and `userUuid`.

The payment owner must not update competition tables. `lumira-async` consumes
the event with Redis consumer group `competition-payment-v1`; the competition
owner applies the state change through `CompetitionPaymentEventHandler` and
records `competition-payment-order-paid-v1` in `event_consumer_receipt` before
acknowledging the stream record. A failed side effect remains pending.
