# Lumira Business Module Template

Business modules live under `services/lumira-{business-name}` and are aggregated by `services/lumira-admin`. They are not separate microservices.

## Module Standard

- Maven module: `services/lumira-{business-name}`
- Artifact id: `{business-name}-service`
- Java package: `com.lumira.{businessName}`
- Migration location: owner module `src/main/resources/db/migration`
- API contract: a dedicated `libs/lumira-{business-name}-api` module when the contract is shared across modules

`services/lumira-team` and `libs/lumira-team-api` are the current Team implementation of this template.

## Package Responsibilities

| Package | Responsibility |
| --- | --- |
| `controller` | HTTP adapter. Validates request shape, extracts security context, calls application services, returns DTO/VO. Must not depend on mapper or entity. |
| `app` | Application services, transactions, authorization orchestration, audit calls, API implementation, and write use cases. |
| `domain` | Domain model anchors, aggregates, value objects, and domain rules. Keep framework dependencies out. |
| `entity` | Persistence entities. Owner-module private, not controller request/response contracts. |
| `mapper` | Persistence mappers. Owner-module private. |
| `dto` | Command/query request objects and internal command data. |
| `vo` | HTTP response view objects. |
| `event` | Domain/integration events, outbox adapters, and event publishers/consumers. |
| `api` | Module-local facades or API adapters. Shared contracts should live in `libs/lumira-{business-name}-api`. |
| `security` | Business authorization policy and scope helpers. |

## Required Tests

- Application service tests for writes, permissions, ownership changes, and idempotency.
- Controller tests for authentication, authorization, route shape, and DTO/VO boundaries.
- Invite or workflow tests for token hashing, expiry, usage limits, and preview shape when applicable.
- Architecture tests for table ownership, controller boundaries, API independence, and forbidden mapper/entity imports.

## Permission Rules

- Backend permission checks are the security boundary.
- Frontend button visibility is only a user-experience helper.
- High-risk writes must validate `tenantId`, actor, resource owner, and role/scope.
- Cross-module writes must go through the target owner API or application service.

## Audit Rules

- Key writes record actor, tenant, resource, action, result, and request id.
- Audit writes must go through a platform audit API/facade when available.
- Audit payloads must not include raw tokens, passwords, or sensitive identity payloads.

## Event Rules

- Owner modules emit their own domain or integration events.
- Reliable cross-module delivery uses the outbox pattern.
- Consumers are idempotent and build their own read models.

## Example Structure

```text
services/lumira-team
  pom.xml
  src/main/java/com/lumira/team
    controller
    app
    domain
    entity
    mapper
    dto
    vo
    event
    api
    security
  src/main/resources/db/migration
  src/test/java/com/lumira/team
```

Before creating a new business module, define table ownership, API contracts, permission boundaries, audit strategy, event strategy, and test evidence.
