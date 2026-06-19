# ADR-0001: Adopt DDD Modular Monolith Architecture

## Status

Proposed

## Context

Lumira currently runs as a modular monolith through `services/lumira-admin`, while preserving Maven service modules such as `system-service`, `auth-service`, `message-service`, `file-service`, `plugin-service`, `localization-service`, and `payment-service`.

This shape is operationally simple, but several modules still mix resource-oriented CRUD, application orchestration, persistence entities, cache handling, and domain rules. `system-service` in particular contains multiple business meanings under one broad `system` package. If this continues, future service extraction will become difficult and performance-sensitive rules such as permission snapshot invalidation will remain scattered.

DDD offers two useful tools for this codebase:

- Strategic design to define bounded contexts and ownership.
- Tactical design to move core rules into entities, aggregates, domain services, repositories, and domain events.

## Decision

Adopt a DDD-oriented modular monolith as the target backend architecture.

We will keep `services/lumira-admin` as the primary runtime entrypoint, and evolve each business module toward explicit bounded contexts. Physical microservice extraction remains a later operational decision, not the first step.

Each bounded context should converge on this internal shape:

```text
interfaces -> application -> domain
infrastructure -> domain/application ports
```

New code should prefer:

- `interfaces` for REST controllers, request validation, response assembly.
- `application` for use-case orchestration, transaction boundaries, authorization checks, audit, and event publication.
- `domain` for aggregates, value objects, domain services, repository interfaces, and domain events.
- `infrastructure` for MyBatis, Redis, external clients, messaging, object storage, and repository implementations.

The first migration candidate will be IAM, especially role, permission, and permission snapshot behavior. Lower-coupling contexts such as message and file should follow after the first migration proves the pattern.

## Consequences

### Positive

- Business boundaries become explicit and easier to protect.
- Core rules move out of controllers, mappers, and transaction scripts.
- Cache invalidation and cross-module events become modeled as domain facts.
- Current modular monolith deployment remains simple.
- Future microservice extraction becomes less risky because contracts and owner boundaries already exist.

### Negative

- Short-term migration cost is real.
- Developers need shared understanding of bounded contexts, aggregates, and domain events.
- Some existing packages will temporarily contain old and new styles.
- Over-modeling simple CRUD areas would slow delivery if not controlled.

### Neutral

- API paths do not need to change.
- Database ownership rules remain compatible with existing service data ownership docs.
- This decision does not require adopting event sourcing or CQRS globally.

## Alternatives Considered

**Keep current layered CRUD style**

- Rejected because the existing broad modules already show boundary pressure, especially in `system-service`.
- It does not sufficiently protect future service extraction or domain rule consistency.

**Immediately split into microservices**

- Rejected because physical separation would increase deployment, observability, transaction, and debugging complexity before the domain boundaries are clean.
- It risks turning current in-process coupling into distributed coupling.

**Adopt a heavy DDD framework**

- Rejected because Lumira already has Spring Boot, MyBatis, Redis, Flyway, and Outbox foundations.
- Lightweight DDD conventions and architecture tests are enough for the current stage.

## References

- `doc/26-ddd-architecture-migration.md`
- `doc/07-backend-architecture.md`
- `doc/13-service-data-ownership.md`
- `doc/14-system-service-module-boundaries.md`
- `doc/16-event-outbox-architecture.md`
- https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/ddd-oriented-microservice
- https://learn.microsoft.com/en-us/azure/architecture/microservices/model/domain-analysis
- https://learn.microsoft.com/en-us/azure/architecture/microservices/model/tactical-domain-driven-design
- https://martinfowler.com/bliki/BoundedContext.html
- https://martinfowler.com/bliki/DomainDrivenDesign.html
