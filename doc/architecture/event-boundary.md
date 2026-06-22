# Lumira Event Boundary

Events coordinate modules without letting one module reach into another module's persistence model.

## Event Types

| Event type | Scope | Use case |
| --- | --- | --- |
| Domain Event | Inside one owner module | State changes and rule coordination inside one aggregate or bounded context. |
| Integration Event | Cross-module | Published owner state changes that other modules may project or react to. |
| Outbox Event | Reliable delivery | Recorded in the same write transaction and relayed asynchronously with retry/replay support. |

## Rules

- The module that owns the write emits the event.
- Consumers must be idempotent.
- Payloads must contain only the minimum data needed for projection or callback.
- Payloads must not contain secrets, raw invite tokens, passwords, or sensitive identity payloads.
- Events include `tenantId`, `eventId`, `occurredAt`, `sourceModule`, `aggregateType`, and `aggregateId`.
- Events do not replace synchronous authorization checks for high-risk commands.

## Team Events

`services/lumira-team` is the source module for Team events.

| Event | Suggested payload |
| --- | --- |
| `team.created` | `tenantId`, `teamId`, `ownerUserId`, `occurredAt` |
| `team.updated` | `tenantId`, `teamId`, `changedFields`, `occurredAt` |
| `team.member.joined` | `tenantId`, `teamId`, `memberUserId`, `role`, `occurredAt` |
| `team.member.removed` | `tenantId`, `teamId`, `memberUserId`, `reason`, `occurredAt` |
| `team.owner.transferred` | `tenantId`, `teamId`, `fromUserId`, `toUserId`, `occurredAt` |
| `team.invite.requested` | `tenantId`, `teamId`, `inviteId`, `requestUserId`, `occurredAt` |

Team invite approval policy: when an invite requires approval, a new pending request consumes `used_count`. Repeated pending requests do not consume another use. Rejected requests intentionally do not release the consumed count so the audit trail and capacity decision remain stable.

## Typical Flows

| Flow | Boundary |
| --- | --- |
| Registration submitted | Future `lumira-registration` writes its own aggregate and records `registration.submitted`; consumers build projections or notifications. |
| Competition needs Team data | Future `lumira-competition` calls `TeamInternalApi` or reads a Team-owned projection; it must not query `team_member`. |
| Payment order paid | `lumira-payment` verifies webhook/idempotency, writes payment owner tables, records `payment.order.paid`, and consumers react asynchronously. |
| Message notification sent | `lumira-message` writes message owner data and records `message.notification.sent`; it does not write the originating business table. |
| AI tool writes business data | AI emits intent through an authorized tool flow; the target owner application service performs validation and emits its own event. |
