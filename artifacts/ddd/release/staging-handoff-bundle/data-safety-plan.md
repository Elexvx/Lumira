# DDD Data Safety Plan

Status: BLOCKED
Shared inputs: `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, `GITHUB_SHA`, `DDD_EVIDENCE_OPERATOR`, `GITHUB_ACTOR`

| Track | Phase | Owner | Status | First blocker | Commands | Artifacts |
| --- | --- | --- | --- | --- | --- | --- |
| rollback | P1 | bounded-context owners | BLOCKED | rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE | `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`<br>`DDD_ROLLBACK_DRILL_STRICT=true node scripts/ddd-rollback-drill-evidence.mjs` | `artifacts/ddd/rollback/rollback-drill.json` |
| migration | P2 | database | BLOCKED | DDD_MIGRATION_FRESH_DB_VALIDATED must be true | `DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs`<br>`DDD_MIGRATION_STRICT=true node scripts/ddd-migration-evidence.mjs` | `artifacts/ddd/migration/migration-evidence.json` |
| explain | P2 | database | BLOCKED | DDD_EXPLAIN_DATABASE is required | `node scripts/ddd-collect-explain.mjs`<br>`DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs` | `tmp/ddd-explain/*.json`<br>`artifacts/ddd/release/explain-gate-report.json` |

## Required Inputs

### rollback

- `DDD_ROLLBACK_DRILL_FILE`
- `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
- `DDD_ROLLBACK_DRILL_ENVIRONMENT`
- `DDD_EVIDENCE_ENVIRONMENT`
- `DDD_RELEASE_ENVIRONMENT`
- `DDD_RELEASE_CANDIDATE`
- `GITHUB_SHA`
- `DDD_EVIDENCE_OPERATOR`
- `GITHUB_ACTOR`

### migration

- `DDD_MIGRATION_FRESH_DB_VALIDATED`
- `DDD_MIGRATION_FRESH_DB_EVIDENCE`
- `DDD_MIGRATION_UPGRADE_DB_VALIDATED`
- `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`
- `DDD_MIGRATION_ENVIRONMENT`
- `DDD_EVIDENCE_ENVIRONMENT`
- `DDD_RELEASE_ENVIRONMENT`
- `DDD_MIGRATION_OPERATOR`
- `DDD_EVIDENCE_OPERATOR`
- `GITHUB_ACTOR`
- `DDD_MIGRATION_COMPLETED_AT`

### explain

- `DDD_EXPLAIN_DATABASE`
- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_USER`
- `MYSQL_PASSWORD`
- `DDD_EXPLAIN_ENVIRONMENT`
- `DDD_EVIDENCE_ENVIRONMENT`
- `DDD_RELEASE_ENVIRONMENT`
- `DDD_RELEASE_CANDIDATE`
- `GITHUB_SHA`
- `DDD_EVIDENCE_OPERATOR`
- `GITHUB_ACTOR`

## Validate

- `node scripts/ddd-staging-data-safety-check.mjs`
- `node scripts/ddd-release-readiness-summary.mjs`
- `node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance`
- `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce`

## Safety

- Rollback evidence must be PASS for each bounded context or have an approved unexpired deferral.
- Migration evidence must cover both fresh database and upgrade-from-previous-schema drills.
- EXPLAIN evidence must be collected from the production-equivalent database shape with read-only credentials.
- Regenerate readiness after rollback, migration, and EXPLAIN artifacts are refreshed.

Next: `node scripts/ddd-staging-data-safety-check.mjs`
