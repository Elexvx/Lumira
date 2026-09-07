# Target runtime architecture

## Decision

Lumira is a modular monolith with exactly three production runtimes:

| Runtime | Responsibility | Business database | Normal Outbox relay |
| --- | --- | --- | --- |
| `lumira-server` | Synchronous API, control plane, and assemblies for Auth, IAM, Project, Competition, Payment, File, Message, Plugin and Team | Sole owner through `lumira_app`; schema changes only through the central migrator | No |
| `lumira-async` | Redis Stream consumption and the per-owner bounded relay lanes | Forbidden | Sole owner |
| `lumira-job-executor` | Cron, compensation, explicit replay, stale recovery and fenced takeover | Forbidden | Forbidden |

The current source tree is migrated incrementally. Maven metadata, Enforcer rules and
`ArchitectureBoundaryTest` express the target `runtimes/modules/contracts/platform`
boundaries until moving directories can be done without a disruptive repository-wide
rename. Only the three runtime entry classes may carry `@SpringBootApplication`; module
assembly classes are ordinary Spring configuration.

## Communication and ownership

- Synchronous module-to-module calls use focused application ports. DTOs and ports live
  in contract libraries; Spring HTTP annotations and client mechanics live in adapters.
- Async and Job call the control plane through `InternalHttpClientFactory`, which adds
  bounded timeouts, response limits, trace/release/schema identity and an explicit retry
  policy. They never import persistence stacks.
- Server owns all business tables. Table and migration ownership is machine-readable in
  `module-data-ownership.yaml`; every online migration declares owner, phase, rollback,
  compatible readers and cleanup horizon.
- Runtime state uses `redis-runtime`; rebuildable caches use `redis-cache`. Logical Redis
  database numbers are not an isolation boundary.

## Deployment invariants

The Signed Release Set v3, Server/UI blue-green slots, worker drain protocol,
expand-only database migration and whole-Release-Set rollback remain authoritative.
Candidate Server instances must pass complete readiness and cannot run write-producing
background work. Async drain covers scheduled relay lanes and Stream consumers; Job
drain covers recovery and cron dispatch. MySQL/Redis/Async/Job/XXL-JOB remain off the
external edge network.

See also `async-relay-ownership.md`, `plugin-lifecycle-and-migration.md`,
`redis-topology.md`, `deployment-and-database-roles.md` and
`compatibility-rollback-and-risks.md`.
