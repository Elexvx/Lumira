# Plugin lifecycle and migration boundary

## Decision

Plugin installation is a control-plane workflow, not an application-owned DDL transaction. `lumira-server` may verify packages, install immutable files and perform DML against `sys_plugin_*` control tables. It must not execute plugin SQL with the business `DataSource`.

The separately privileged central Migrator is the only component allowed to execute an approved plugin migration. `deploy/plugin-migrator` is a small JDBC adapter packaged into the existing one-shot migrator image. It validates the persisted fence (`requestId`, `operationEpoch`, `packageDigest`, `migrationDigest`, `releaseId`), claims the request, re-computes the payload digest, executes the recorded statements with the dedicated `lumira_migrator` identity, and persists completion or failure. It is not a Spring runtime and is never included in Server, Async, or Job.

## Lifecycle

The persisted lifecycle is monotonic and checked by `PluginLifecycleStateMachine`:

`UPLOADED -> VERIFIED -> MIGRATION_PENDING -> MIGRATED -> RUNTIME_VERIFIED -> ACTIVATED`

Any verification, migration, or runtime failure moves to `FAILED`. When application rollback cannot safely compensate an already expanded schema, the state is `ROLLBACK_BLOCKED`. Retrying a failed request creates a new fenced operation; an old process cannot complete the newer operation.

Upload currently performs package verification in the same command, so the durable version row is first visible as `VERIFIED`; the upload audit log retains the preceding `UPLOADED` event.

## Request contract

`sys_plugin_migration_request` is the immutable hand-off record. It stores:

- plugin code and package version;
- declared schema version, optional expected schema digest, `EXPAND` phase, rollback mode and compatible readers;
- normalized `plugin_<plugin_code>_` table namespace;
- monotonically increasing operation epoch;
- SHA-256 package and migration digests;
- release ID;
- request and lifecycle status;
- the exact ordered SQL payload reviewed and executed;
- bounded failure reason and recovery action;
- approval identity/reason plus approval, start and completion timestamps.

The release set may repeat the plugin's `migrationVersion`, `schemaDigest`, `migrationDigest` and
compatible readers. When a package carries `migrationSchemaDigest`, the request stores it as
`expected_schema_digest`; the central Migrator records object-level hashes in `plugin_schema_snapshot`
and refuses to mark the request successful when the aggregate schema digest differs. Older requests
without that optional field remain executable but are explicitly evidence-only rather than digest-verified.

`plugin_migration_execution_log` is the execution fact ledger. Its generated active-request key allows
only one `STARTED` execution per request; later attempts are possible only after a terminal failure and
must use a new fenced request for ordinary recovery. `CENTRAL_MIGRATOR` is the only executor type.

The application initially writes `PENDING_APPROVAL` and exposes no approve, claim, complete, or fail operation. Approval is a separate operator action. The Migrator can only claim `APPROVED` + `EXPAND` requests for its explicit release ID and every update is a compare-and-set on epoch, digest, release ID and current status. Migration success and the version transition to `MIGRATED` are one Migrator-side DML transaction. Failure remains recorded and cannot be mistaken for schema readiness. `sys_plugin_migration_audit` records `APPROVED`, `CLAIMED`, `SUCCEEDED`, `FAILED`, and `ROLLBACK_BLOCKED` events with the full fence and actor.

## Approval and execution workflow

Approval is deliberately not an HTTP endpoint. An operator with access to the deployment host and the dedicated Migrator credential uses `bin/approve-plugin-migration.mjs`. The command requires request ID, operation epoch, package digest, migration digest, release ID, approver identity, reason, and a second copy of the migration digest. Production approval rejects an unpinned Migrator image. Database credentials are forwarded as environment variables and are not placed in Docker argv.

```bash
node bin/approve-plugin-migration.mjs \
  --request-id 42 \
  --operation-epoch 3 \
  --package-digest <sha256> \
  --migration-digest <sha256> \
  --confirm-migration-digest <sha256> \
  --release-id v2026.08.31 \
  --approver operator@example.com \
  --reason "Reviewed expand-only namespace-safe SQL" \
  --execute
```

`--execute` immediately starts a second one-shot container in `plugin-execute` mode. Without it, the request remains `APPROVED` until the next one-shot Migrator run. Normal platform migration uses `platform` mode and preserves the order Flyway, approved plugin requests, then administrator bootstrap.

## Package metadata

Packages containing `migrations/up/*.sql` must declare these `plugin.json` fields:

```json
{
  "migrationSchemaVersion": "1",
  "migrationSchemaDigest": "sha256:<64 lowercase hex characters>",
  "migrationPhase": "expand",
  "rollbackMode": "application_only",
  "compatibleReaders": ["1.x", "2.x"]
}
```

Allowed rollback modes are `application_only` and `not_required`. They describe runtime rollback compatibility; they do not authorize a down migration. Production down migrations are forbidden, and application install/enable/uninstall code never runs them.

## SQL policy

Only these expand forms are accepted:

- `CREATE TABLE [IF NOT EXISTS] plugin_<normalized_code>_*`;
- `CREATE [UNIQUE] INDEX ... ON plugin_<normalized_code>_*`;
- `ALTER TABLE plugin_<normalized_code>_* ADD COLUMN|INDEX|KEY|CONSTRAINT ...`.

`DROP`, `TRUNCATE`, `RENAME`, `CHANGE [COLUMN]`, `MODIFY [COLUMN]`, `ALTER COLUMN`, DML and references to any table outside the plugin namespace are rejected before a request is persisted and checked again after claim by the independently built Migrator. Built-in platform tables such as `sys_sensitive_word` remain platform-owned and must be changed by the signed release-set migration, not by a plugin package.

## Runtime isolation

`saas.plugin.allow-in-process-backend-plugins` defaults to `false`. The production startup validator rejects `true`, even if an environment variable attempts to override the default. A package reaches runtime loading only after the central Migrator has persisted `MIGRATED`; runtime health verification precedes activation.

## Recovery

MySQL DDL may commit statement-by-statement. Therefore a failed request must never be deleted or silently retried. The Migrator records the failing request and recovery action, operators reconcile the schema against the recorded ordered payload, and recovery uses a new higher operation epoch. Automatic down migration and database restore are not recovery mechanisms for ordinary plugin installation.
