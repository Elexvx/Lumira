# ADR: Three-runtime modular monolith

Status: Accepted
Date: 2026-08-31

## Decision

Lumira is one modular business system deployed as exactly three production
runtimes:

1. `lumira-server` is the only synchronous API/control plane and the only
   owner of the business database.
2. `lumira-async` is the only normal outbox relay and Redis Streams consumer.
   It reaches business capabilities through authenticated internal application
   ports and has no JDBC, MyBatis, Flyway, or MySQL driver.
3. `lumira-job-executor` runs cron, compensation, manual replay, and fenced
   recovery. It does not run the normal relay loop and has no business database
   dependency.

Business areas remain modules assembled into `lumira-server`; they are not
independently deployed services. Release Set v3 remains the deployment and
rollback unit. Server/UI blue-green replacement, worker drain, expand-only
migration, and whole-release rollback remain mandatory.

## Source layout transition

The target layout is:

```text
lumira-backend/
  runtimes/{lumira-server,lumira-async,lumira-job-executor}
  modules/*
  contracts/*
  platform/*
```

The current `services/*` and `libs/*` paths are retained during Phase 1 to
avoid a high-risk bulk move. Maven artifact metadata and architecture tests
express the target boundary now. Only the three runtime entry classes may use
`@SpringBootApplication`; module packages expose assembly configuration and
focused ports instead of production `main` methods.

## Dependency rules

- Domain code is framework-free and cannot depend on Spring Web, JDBC,
  MyBatis, Redis, or infrastructure implementations.
- A module may consume another module only through a contract/port or public
  assembly. Controller, mapper, entity, infrastructure, and implementation
  packages are private.
- Services never depend on controllers. HTTP controllers and in-process
  adapters invoke the same application boundary.
- `contracts` contains DTOs, focused ports, and stable error models. HTTP
  annotations belong to client adapters.
- Common/platform libraries cannot depend back on business modules.
- Bean definition overriding is disabled in production and test assemblies.

## Consequences

The architecture can be hardened in small batches while keeping compatible
Java types and Release Set v3. The deprecated `SystemInternalApi` is an empty
compatibility alias; new dependencies use focused ports. Temporary source-path
debt is visible and mechanically checked rather than hidden behind additional
runtimes.

Kubernetes, service discovery, business microservices, distributed
transactions, and independently rolled back production components are outside
this architecture.
