# Lumira Module Boundary

This document defines Lumira's modular-monolith boundaries. Lumira still starts as one aggregate backend through `services/lumira-admin`; business domains must not be hidden inside `lumira-system`.

## Three Layers

| Layer | Modules | Responsibility |
| --- | --- | --- |
| Foundation | `libs/lumira-common-core`, `libs/lumira-common-domain`, `libs/lumira-common-web`, `libs/lumira-common-security`, `libs/lumira-common-api`, `libs/lumira-plugin-api`, `libs/lumira-team-api` | Shared runtime contracts, response models, security primitives, web helpers, domain/event contracts, and cross-module APIs. Foundation must not depend on `services/*`. |
| Platform | `services/lumira-auth`, `services/lumira-system`, `services/lumira-file`, `services/lumira-message`, `services/lumira-plugin`, `services/lumira-localization`, `services/lumira-payment`, `services/lumira-ai`, `services/lumira-quartz` | Authentication, IAM, permissions, menu, configuration, audit, files, messages, plugins, localization, payment, AI runtime, platform governance, and scheduler adapters. |
| Business | `services/lumira-team`; future: `services/lumira-competition`, `services/lumira-registration`, `services/lumira-schedule`, `services/lumira-score`, `services/lumira-certificate`, `services/lumira-project` | Product business domains. Each business module owns its application, domain, persistence, API contract, events, and security policy. |

## System Boundary

`lumira-system` is a Platform module. It owns IAM, permission, menu, configuration, audit, and platform governance. It must not become a container for Team, Competition, Registration, or other product business domains.

Team is a Business module. The Team Core migration target is `services/lumira-team`, and the aggregate application still starts through `services/lumira-admin`.

## Current Business Modules

| Module | Owner role |
| --- | --- |
| `services/lumira-team` | Owns `team`, `team_member`, `team_invite`, and `team_join_request`. Exposes Team data through `TeamInternalApi`, events, or read models. |

## Future Business Modules

| Future module | Responsibility |
| --- | --- |
| `services/lumira-competition` | Competition, rules, stages, groups. |
| `services/lumira-registration` | Registration, participant data, review state. |
| `services/lumira-schedule` | Matches, rounds, schedule results. |
| `services/lumira-score` | Scores and rankings. |
| `services/lumira-certificate` | Certificate templates and issuing. |
| `services/lumira-project` | Project collaboration. |

## Cross-Module Calls

Allowed patterns:

- Internal API / Facade for synchronous queries or commands.
- Domain Event / Outbox for asynchronous coordination and projections.
- Read Model for cross-domain search or reporting.

Forbidden patterns:

- Importing another module's `mapper` or `entity`.
- Directly reading or writing another module's owner table.
- Depending on a service implementation from `common-*` or `*-api` libraries.
- Letting `lumira-quartz`, `lumira-ai`, or other adapters own business tables.

Example: future Competition or Registration code that needs Team membership must call `TeamInternalApi`; it must not query `team_member` directly and must not depend on `TeamAppService`.

## Business Module Structure

```text
services/lumira-team
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
```

Controllers delegate to application services and return DTO/VO contracts. Application services own transactions, permission checks, audit calls, and writes. Entity and mapper types are private to the owner module.
