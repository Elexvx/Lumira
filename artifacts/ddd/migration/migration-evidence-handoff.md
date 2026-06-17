# Migration Evidence Handoff

This handoff does not satisfy the release gate by itself. It lists the runtime evidence required before `migration-evidence.json` can become PASS.

Status: MISSING
Value policy: No concrete database credentials, DSNs, or migration artifact contents are emitted; only env key names, owners, status, and commands are included.
Checks: 6
Ready: 0
Missing: 6

Fast path:

- Objective: Close the migration release blocker without bypassing fresh-database and previous-schema upgrade evidence.
- Blocked until: Both fresh DB and previous-schema upgrade Flyway drills have concrete evidence, operator, environment, release candidate, and completion timestamp.
- Commands:

```sh
DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs
DDD_MIGRATION_STRICT=true node scripts/ddd-migration-evidence.mjs
node scripts/ddd-release-readiness-summary.mjs
DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh
```

Owner runbook:

| Owner | Status | Missing checks | Required env keys | Next command |
|---|---|---|---|---|
| database | MISSING | fresh-database-drill; upgrade-database-drill | DDD_MIGRATION_FRESH_DB_EVIDENCE; DDD_MIGRATION_FRESH_DB_VALIDATED; DDD_MIGRATION_UPGRADE_DB_EVIDENCE; DDD_MIGRATION_UPGRADE_DB_VALIDATED | DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs |
| release-infra | MISSING | migration-environment; migration-release-candidate | DDD_EVIDENCE_ENVIRONMENT; DDD_MIGRATION_ENVIRONMENT; DDD_RELEASE_CANDIDATE; DDD_RELEASE_ENVIRONMENT; GITHUB_SHA | DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs |
| release-owner | MISSING | migration-operator; migration-completed-at | DDD_EVIDENCE_OPERATOR; DDD_MIGRATION_COMPLETED_AT; DDD_MIGRATION_OPERATOR; GITHUB_ACTOR | DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs |

Evidence checklist:

| Evidence package | Owner | Required env keys | Required artifacts | Acceptance criteria |
|---|---|---|---|---|
| fresh-database-evidence-package | database | DDD_MIGRATION_FRESH_DB_VALIDATED; DDD_MIGRATION_FRESH_DB_EVIDENCE | Flyway migrate log from an empty production-equivalent database.; Schema history export after the fresh migration completes.; Database product/version and migration command provenance. | DDD_MIGRATION_FRESH_DB_VALIDATED=true is set only after the drill succeeds.; DDD_MIGRATION_FRESH_DB_EVIDENCE points to the fresh database evidence package.; Fresh database evidence is separate from previous-schema upgrade evidence. |
| previous-schema-upgrade-evidence-package | database | DDD_MIGRATION_UPGRADE_DB_VALIDATED; DDD_MIGRATION_UPGRADE_DB_EVIDENCE | Before/after schema history export from a copy of the previous production schema.; Flyway migrate log for the upgrade drill.; Rollback or restore point reference for the copied previous-schema database. | DDD_MIGRATION_UPGRADE_DB_VALIDATED=true is set only after the upgrade drill succeeds.; DDD_MIGRATION_UPGRADE_DB_EVIDENCE points to the previous-schema upgrade evidence package.; Upgrade evidence references a previous-schema source, not a fresh empty database. |

| Owner | Check | Status | Required env keys | Action |
|---|---|---|---|---|
| database | fresh-database-drill | MISSING | DDD_MIGRATION_FRESH_DB_VALIDATED; DDD_MIGRATION_FRESH_DB_EVIDENCE | Set DDD_MIGRATION_FRESH_DB_VALIDATED=true and point DDD_MIGRATION_FRESH_DB_EVIDENCE at the fresh-database Flyway log/schema-history artifact. |
| database | upgrade-database-drill | MISSING | DDD_MIGRATION_UPGRADE_DB_VALIDATED; DDD_MIGRATION_UPGRADE_DB_EVIDENCE | Set DDD_MIGRATION_UPGRADE_DB_VALIDATED=true and point DDD_MIGRATION_UPGRADE_DB_EVIDENCE at the previous-schema upgrade Flyway log/schema-history artifact. |
| release-infra | migration-environment | MISSING | DDD_MIGRATION_ENVIRONMENT or DDD_EVIDENCE_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT | Set the production-equivalent migration environment name. |
| release-infra | migration-release-candidate | MISSING | DDD_RELEASE_CANDIDATE or GITHUB_SHA | Set the immutable release candidate or commit SHA. |
| release-owner | migration-operator | MISSING | DDD_MIGRATION_OPERATOR or DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR | Set the operator or CI actor who executed the migration drill. |
| release-owner | migration-completed-at | MISSING | DDD_MIGRATION_COMPLETED_AT | Set the ISO timestamp when both migration drills completed. |

Validation commands:

```sh
DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs
DDD_MIGRATION_STRICT=true node scripts/ddd-migration-evidence.mjs
node scripts/ddd-collect-explain.mjs
DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs
node scripts/ddd-release-readiness-summary.mjs
DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh
```

