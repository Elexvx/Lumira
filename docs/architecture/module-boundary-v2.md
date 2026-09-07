# Lumira module boundary v2

This document is the executable boundary contract for the current modular
monolith. It distinguishes three different shapes that must not be confused:

1. Maven modules are logical bounded contexts and contract libraries.
2. `lumira-server`, `lumira-async`, and `lumira-job-executor` are the current
   runtime processes.
3. MySQL table ownership is a write authority. A shared table exception is not
   permission for arbitrary cross-context SQL.

The current release keeps the three-process topology. No business module is a
physical microservice yet.

## Target logical map

```text
runtimes
├── lumira-server       (lumira-admin directory; artifactId lumira-server)
├── lumira-async        (transport consumers and owner relay coordinator)
└── lumira-job-executor (scheduler, recovery and explicit replay only)

identity
├── auth                (authentication orchestration and authentication contracts)
├── system auth storage (credential, binding and challenge persistence adapters)
├── system/account      (account activation token lifecycle)
└── system/iam         (canonical identity master, users, roles, permissions and data scope)

business
├── activity
├── competition         (registration and review are internal subdomains)
├── project
├── expert
├── team
├── workflow
├── payment
├── file
├── export              (export task lifecycle; delegates file storage)
├── plugin
└── ai

platform
├── system/platform     (configuration, update and runtime controls)
├── system/audit
├── system event infrastructure (outbox, relay, trust and dispatch adapters)
├── event-catalog        (rebuildable public projection owner)
└── localization

communication
├── alerting             (rules and alert generation)
└── message              (notification/channel delivery)
```

`lumira-system` remains one Maven module for now, but its packages must keep
the identity, platform, audit, account and integration responsibilities
separate. A future Maven split is allowed only after the package dependency
guard is green and the split has an independent release and database owner.

## Ownership decisions

### Authentication versus persistence

`lumira-auth` remains the Authentication bounded context: it owns login
orchestration, authentication policy and the stable authentication contracts.
The canonical MySQL write owner for `sys_user_passkey_credential`,
`sys_user_wechat_binding`, `sys_verification_binding` and
`sys_verification_challenge` is `lumira-system`. The current writers are the
System `user`, `verification` and `internal` adapters; `lumira-auth` calls the
focused `SystemInternalApi` ports and does not issue SQL for these tables.
This is an ownership correction, not a move of the tables or a merge of Auth
and IAM. IAM is the canonical owner of the `sys_user` identity master and all
`iam_*` identity tables. Account owns only activation-token lifecycle state;
Auth owns authentication orchestration and consumes `UserIdentityQueryPort`
and `UserDirectoryQueryPort`. Neither Account nor Auth may write `sys_user`
directly. This keeps the table in the current System runtime while making the
logical IAM ownership explicit.

### Account activation

`lumira-system/modules/account` is the canonical owner of
`sys_account_activation_token`, activation verification, token consumption and
the user credential activation transaction. `lumira-expert` owns only the
expert-side state transition through `ExpertAccountActivationPort`.

The activation token is therefore declared in the `ACCOUNT` row of
`doc/27-ddd-owner-table-manifest.csv`, not in `PLATFORM` or `EXPERT`. This is a
logical owner correction; it does not move the table or change the current
transaction in this release.

### Platform event outbox

`platform_event_outbox` is stored and governed by System's platform event
infrastructure. The table has source-scoped writer lanes:

- System writes `SYSTEM` events.
- File writes `FILE` events in the File owner transaction.
- Message writes `MESSAGE` delivery events in the Message owner transaction.

Activity, Competition, Workflow and AI use the context-neutral
`TransactionalEventOutboxPort` or `PlatformEventPort`; they must not import an
implementation mapper or issue SQL against the table. The shared-table
exception is limited to the declared writer lanes and source predicates.

The outbox is one event transport chain. A stream name, consumer group or
owner relay is not a second event bus.

### Event catalog

`lumira-event-catalog` owns `event_catalog_item` and its projection/rebuild
logic. System's catalog bridge may translate and route durable events, but it
must not contain catalog business decisions or write the projection table.

### Message and alerting

Alerting evaluates rules and emits an alert decision/event. Message owns
notification records and delivery through email, SMS, WebSocket or webhook.
Alerting must not call an external channel provider directly. Review
notification and review-result contracts may be consumed by Message because
they are stable contract-only APIs; Message must not depend on Review
repositories, entities or application services.

Alerting's `alert_*` tables and rule/instance/delivery state belong to
`lumira-alerting`. The local user directory is obtained through
`UserDirectoryQueryPort`; Alerting does not read IAM's `sys_user` table
directly. Its business signals are supplied by owner adapters through
`AlertBusinessSignalQueryPort`, and plugin state is read through
`PluginFeatureStateApi`. Message remains the only owner of notification delivery records and
external channel adapters.

### Export and File

`lumira-export` owns `sys_export_task`, task claiming and export completion
state. It does not own file metadata, processing or object persistence.
Exported bytes and file readiness are delegated through `FileInternalApi` to
the `lumira-file` owner; Export may retain only the resulting file identifier
and display name on its own task row.

## Dependency direction

```text
runtime assembly
        ↓ wires
domain application + domain model
        ↓ ports / stable integration contracts
platform adapters and infrastructure
        ↓
database, Redis and external providers
```

Allowed cross-context edges are:

- same-context application service calls;
- a narrow synchronous port when the caller needs an immediate decision;
- a versioned integration event through the durable outbox and Async runtime;
- a read-only query port or rebuildable projection.

The following edges are forbidden:

- any business module importing `com.lumira.saas.infrastructure.event.*`;
- any business module importing another context's `mapper`, `entity` or
  application implementation package;
- Expert importing `system/modules/account` or its repository;
- Message importing Review persistence or application classes;
- Competition or any domain module importing `lumira-async` or
  `lumira-quartz` to execute work;
- Async or Job writing a business table;
- Event Catalog querying Activity/Competition owner tables as its normal
  public read path.

## Contract library rules

`lumira-common-api`, `lumira-team-api`, `lumira-plugin-api`,
`lumira-registration-api` and `lumira-review-api` are contract libraries, not
shared persistence modules. They may contain immutable DTOs, records,
constants and ports. They must not contain Spring components, MyBatis mappers,
entities, repositories or database SQL.

`lumira-registration-api` and `lumira-review-api` remain separate artifacts in
the current release for compatibility. Their semantic scope is Competition's
registration/review contract; they must not grow unrelated business models.

## Implementation order

The first boundary commits are intentionally behavior-preserving:

1. Make Account and authentication storage ownership explicit and keep Expert
   behind its common port.
2. Keep the existing outbox implementations where their owner transaction
   requires them, but enforce that other modules use common event ports and
   never import System event infrastructure.
3. Keep registration/review artifacts but shrink their API surface to stable
   Competition contracts and add contract-only guards.

The File lifecycle tables use `sql/saas.sql` for fresh databases and
`deploy/migrations/V202609070003__add_file_event_projection.sql` for existing
databases. The old `lumira-backend/sql/upgrade-file-event-projection-v1.sql`
is retained as a legacy reference only and is not an execution path.

Renaming `lumira-admin` to `lumira-server-runtime` and `lumira-quartz` to
`lumira-job-runtime` is a later naming cleanup. It is not a runtime or schema
change and is not a current production gate.

The plugin migration governance, Async/Job execution fence, Redis runtime/cache
physical isolation and File lifecycle event ownership are stable boundaries.
Module cleanup must not introduce a second event bus, a second receipt store or
direct database writes from Async/Job.
