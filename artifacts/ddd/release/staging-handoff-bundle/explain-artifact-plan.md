# DDD EXPLAIN Artifact Plan

Status: PASS
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Missing artifact: none
Dispatch owners: platform-owners
Source owners: database
Dependent gates: explain, migration

## Required Inputs

- `DDD_EXPLAIN_DATABASE`
- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_USER`
- `MYSQL_PASSWORD`
- `DDD_EXPLAIN_ENVIRONMENT or DDD_EVIDENCE_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT`
- `DDD_RELEASE_CANDIDATE or GITHUB_SHA`
- `DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR`

## Env Template

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

## Commands

- `node bin/ddd-staging-data-safety-check.mjs`
- `node bin/ddd-collect-explain.mjs`
- `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`
- `DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs`
- `DDD_MIGRATION_STRICT=true node bin/ddd-migration-evidence.mjs`
- `node bin/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report`
- `node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance`

## Expected Artifacts

- `tmp/ddd-explain/*.json`
- `artifacts/ddd/release/explain-gate-report.json`

## Pass Criteria

- `node bin/ddd-collect-explain.mjs` writes JSON files under `tmp/ddd-explain/`.
- `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs` passes and writes the EXPLAIN gate report.
- `node bin/ddd-staging-data-safety-check.mjs` accepts rollback, migration, and EXPLAIN evidence.
- `node bin/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report` shows no missing EXPLAIN artifact.

Next: `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`
