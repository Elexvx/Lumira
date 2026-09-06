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

The IAM contracts are definitions only in this sprint. They must not become a
runtime dependency of JWT authentication until a separate migration proves
that the current fail-closed permission/version path remains intact.
