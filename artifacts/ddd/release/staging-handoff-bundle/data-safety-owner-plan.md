# DDD Data Safety Owner Plan

Status: BLOCKED
Owners: `bounded-context owners`, `database`, `release-infra`
Next phase: rollback-evidence-source

## Phases

| Phase | Owner | Depends On | Required Inputs | Commands | Artifacts |
| --- | --- | --- | --- | --- | --- |
| rollback-evidence-source | bounded-context owners | none | `DDD_ROLLBACK_DRILL_FILE or DDD_ROLLBACK_DRILL_DEFERRAL_FILE`, `DDD_ROLLBACK_DRILL_ENVIRONMENT or DDD_EVIDENCE_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE or GITHUB_SHA`, `DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR` | `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`<br>`DDD_ROLLBACK_DRILL_STRICT=true node bin/ddd-rollback-drill-evidence.mjs` | `artifacts/ddd/rollback/rollback-drill.json` |
| migration-fresh-drill | database | none | `DDD_MIGRATION_FRESH_DB_VALIDATED=true`, `DDD_MIGRATION_FRESH_DB_EVIDENCE`, `DDD_MIGRATION_ENVIRONMENT or DDD_EVIDENCE_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT`, `DDD_MIGRATION_OPERATOR or DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR` | `DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs`<br>`DDD_MIGRATION_STRICT=true node bin/ddd-migration-evidence.mjs` | `artifacts/ddd/migration/migration-evidence.json` |
| migration-upgrade-drill | database | `migration-fresh-drill` | `DDD_MIGRATION_UPGRADE_DB_VALIDATED=true`, `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`, `DDD_MIGRATION_COMPLETED_AT` | `DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs`<br>`DDD_MIGRATION_STRICT=true node bin/ddd-migration-evidence.mjs` | `artifacts/ddd/migration/migration-evidence.json` |
| explain-collect | database | none | `DDD_EXPLAIN_DATABASE`, `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PASSWORD`, `DDD_EXPLAIN_ENVIRONMENT or DDD_EVIDENCE_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT` | `node bin/ddd-collect-explain.mjs` | `tmp/ddd-explain/*.json` |
| explain-gate | database | `explain-collect` | `DDD_RELEASE_CANDIDATE or GITHUB_SHA`, `DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR` | `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs` | `tmp/ddd-explain/*.json`<br>`artifacts/ddd/release/explain-gate-report.json` |
| data-safety-acceptance | release-infra | `rollback-evidence-source`, `migration-upgrade-drill`, `explain-gate` | none | `node bin/ddd-staging-data-safety-check.mjs`<br>`node bin/ddd-release-readiness-summary.mjs`<br>`node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance`<br>`node bin/ddd-staging-execution-checklist.mjs --final-review-enforce` | `artifacts/ddd/rollback/rollback-drill.json`<br>`artifacts/ddd/migration/migration-evidence.json`<br>`tmp/ddd-explain/*.json`<br>`artifacts/ddd/release/explain-gate-report.json` |

## Parallel Start

- `rollback-evidence-source`
- `migration-fresh-drill`
- `explain-collect`

## Validate

- `node bin/ddd-staging-data-safety-check.mjs`
- `node bin/ddd-release-readiness-summary.mjs`
- `node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance`
- `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce`

## Safety

- Use rollback deferrals only when accepted by release owners and bounded-context owners.
- Run migration and EXPLAIN evidence against production-equivalent staging data, never a developer-local database.
- Use read-only database credentials for EXPLAIN collection and keep populated credentials out of committed artifacts.

Next: `node bin/ddd-staging-data-safety-check.mjs`
