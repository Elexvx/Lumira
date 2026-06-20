# DDD Data Safety Submission Plan

Status: BLOCKED
Owner: platform-owners
Gate: data-safety
Owners: `bounded-context owners`, `database`, `release-infra`

## Owner Submissions

| Phase | Owner | Depends On | Required Inputs | Commands | Artifacts |
| --- | --- | --- | --- | --- | --- |
| rollback-evidence-source | bounded-context owners | none | `DDD_ROLLBACK_DRILL_FILE or DDD_ROLLBACK_DRILL_DEFERRAL_FILE`, `DDD_ROLLBACK_DRILL_ENVIRONMENT or DDD_EVIDENCE_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE or GITHUB_SHA`, `DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR` | `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`<br>`DDD_ROLLBACK_DRILL_STRICT=true node bin/ddd-rollback-drill-evidence.mjs` | `artifacts/ddd/rollback/rollback-drill.json` |
| migration-fresh-drill | database | none | `DDD_MIGRATION_FRESH_DB_VALIDATED=true`, `DDD_MIGRATION_FRESH_DB_EVIDENCE`, `DDD_MIGRATION_ENVIRONMENT or DDD_EVIDENCE_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT`, `DDD_MIGRATION_OPERATOR or DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR` | `DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs`<br>`DDD_MIGRATION_STRICT=true node bin/ddd-migration-evidence.mjs` | `artifacts/ddd/migration/migration-evidence.json` |
| migration-upgrade-drill | database | `migration-fresh-drill` | `DDD_MIGRATION_UPGRADE_DB_VALIDATED=true`, `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`, `DDD_MIGRATION_COMPLETED_AT` | `DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs`<br>`DDD_MIGRATION_STRICT=true node bin/ddd-migration-evidence.mjs` | `artifacts/ddd/migration/migration-evidence.json` |
| explain-collect | database | none | `DDD_EXPLAIN_DATABASE`, `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PASSWORD`, `DDD_EXPLAIN_ENVIRONMENT or DDD_EVIDENCE_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT` | `node bin/ddd-collect-explain.mjs` | `tmp/ddd-explain/*.json` |
| explain-gate | database | `explain-collect` | `DDD_RELEASE_CANDIDATE or GITHUB_SHA`, `DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR` | `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs` | `tmp/ddd-explain/*.json`<br>`artifacts/ddd/release/explain-gate-report.json` |

## EXPLAIN Artifact

Artifact: `null`
Present: true
Dispatch owners: `platform-owners`

Required inputs:

- `DDD_EXPLAIN_DATABASE`
- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_USER`
- `MYSQL_PASSWORD`
- `DDD_EXPLAIN_ENVIRONMENT or DDD_EVIDENCE_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT`
- `DDD_RELEASE_CANDIDATE or GITHUB_SHA`
- `DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR`

Env template:

```env
DDD_EXPLAIN_DATABASE=__REQUIRED__
DDD_EXPLAIN_ENVIRONMENT=staging
DDD_EXPLAIN_STRICT=true
MYSQL_CLI=mysql
MYSQL_HOST=__REQUIRED__
MYSQL_PORT=3306
MYSQL_USER=__REQUIRED_READONLY_USER__
MYSQL_PASSWORD=__SECRET_REFERENCE_ONLY__
DDD_RELEASE_CANDIDATE=__REQUIRED_SHA_OR_TAG__
DDD_EVIDENCE_OPERATOR=__REQUIRED__
```

Commands:

- `node bin/ddd-staging-data-safety-check.mjs`
- `node bin/ddd-collect-explain.mjs`
- `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`
- `DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs`
- `DDD_MIGRATION_STRICT=true node bin/ddd-migration-evidence.mjs`
- `node bin/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report`
- `node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance`

## Validation Commands

- `node bin/ddd-staging-data-safety-check.mjs`
- `node bin/ddd-release-readiness-summary.mjs`
- `node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance`
- `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce`

## Expected Artifacts

- `artifacts/ddd/rollback/rollback-drill.json`
- `artifacts/ddd/migration/migration-evidence.json`
- `tmp/ddd-explain/*.json`
- `artifacts/ddd/release/explain-gate-report.json`

## Lane Receipt Fragment

```json
{
  "owner": "platform-owners",
  "lane": "p1-p2-data-safety",
  "status": "BLOCKED",
  "providedArtifacts": [
    "artifacts/ddd/rollback/rollback-drill.json",
    "artifacts/ddd/migration/migration-evidence.json",
    "tmp/ddd-explain/*.json",
    "artifacts/ddd/release/explain-gate-report.json"
  ],
  "missingArtifacts": [
    "tmp/ddd-explain/*.json"
  ],
  "completedAt": "<ISO-8601 timestamp after validation commands pass>",
  "completedBy": "<owner or workflow actor>",
  "acceptanceCommands": [
    "node bin/ddd-staging-data-safety-check.mjs",
    "node bin/ddd-release-readiness-summary.mjs",
    "node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance",
    "node bin/ddd-staging-execution-checklist.mjs --final-review-enforce"
  ]
}
```

## Pass Criteria

- Rollback evidence or approved rollback deferral is accepted for every affected bounded context.
- Migration evidence covers both fresh database and upgrade-from-previous-schema drills.
- EXPLAIN collection writes `tmp/ddd-explain/*.json` and strict EXPLAIN gate writes `artifacts/ddd/release/explain-gate-report.json`.
- Data safety check and evidence acceptance pass after rollback, migration, and EXPLAIN artifacts are refreshed.
- Final review no longer reports rollback, migration, or explain gates as blocked.

Next: `node bin/ddd-staging-data-safety-check.mjs`
