## DDD Staging Handoff

Artifact: `ddd-staging-handoff-bundle`

# DDD Staging Readiness Rollup

Status: BLOCKED
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Blocked: 6/6

| Gate | Track | Owner | Status | First blocker | Blocking inputs | Next command |
| --- | --- | --- | --- | --- | --- | --- |
| release-env | p0-release-env | release-infra | BLOCKED | release env file is not cutover-safe; blockers=34 | `DDD_RELEASE_ENV_FILE` | `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs` |
| docker-images | p0-images | release-infra | BLOCKED | docker CLI is not available: spawnSync docker ENOENT | `DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE`, `DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE`, `DDD_DOCKER_EXISTING_FRONTEND_IMAGE` | `node scripts/ddd-docker-build-evidence.mjs --check` |
| runtime-business | p1-runtime-business | release-infra, frontend, ai, file-owner, job-owner, payment-owner | BLOCKED | LUMIRA_BASE_URL is required | `LUMIRA_BASE_URL`, `PLAYWRIGHT_BASE_URL`, `DDD_DEPLOYMENT_EVIDENCE`, `DDD_FRONTEND_DEPLOYMENT_EVIDENCE`, `DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE`, `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE`, `DDD_FRONTEND_EXPECT_DEPLOYED`, `DDD_AI_EXPECT_PROVIDER_REMOTE`, `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE` | `node scripts/ddd-staging-runtime-check.mjs` |
| rollback | p1-rollback | bounded-context owners | BLOCKED | rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE | `DDD_ROLLBACK_DRILL_FILE`, `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`, `DDD_ROLLBACK_DRILL_ENVIRONMENT`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, `GITHUB_SHA`, `DDD_EVIDENCE_OPERATOR`, `GITHUB_ACTOR` | `node scripts/ddd-staging-data-safety-check.mjs` |
| migration | p2-database-performance | database | BLOCKED | DDD_MIGRATION_FRESH_DB_VALIDATED must be true | `DDD_MIGRATION_FRESH_DB_VALIDATED`, `DDD_MIGRATION_FRESH_DB_EVIDENCE`, `DDD_MIGRATION_UPGRADE_DB_VALIDATED`, `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`, `DDD_MIGRATION_ENVIRONMENT`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_ENVIRONMENT`, `DDD_MIGRATION_OPERATOR`, `DDD_EVIDENCE_OPERATOR`, `GITHUB_ACTOR`, `DDD_MIGRATION_COMPLETED_AT` | `node scripts/ddd-staging-data-safety-check.mjs` |
| explain | p2-database-performance | database | BLOCKED | DDD_EXPLAIN_DATABASE is required | `DDD_EXPLAIN_DATABASE`, `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PASSWORD`, `DDD_EXPLAIN_ENVIRONMENT`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, `GITHUB_SHA`, `DDD_EVIDENCE_OPERATOR`, `GITHUB_ACTOR` | `node scripts/ddd-staging-data-safety-check.mjs` |

Next: `node scripts/ddd-staging-execution-checklist.mjs --commands`

## Execution Status

# DDD Staging Execution Status

Status: BLOCKED
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Blocked gates: 6/6
Evidence gaps: 6
Handoff bundle: PASS

| Gate | Owner | Status | First blocker | Next command |
| --- | --- | --- | --- | --- |
| release-env | release-infra | BLOCKED | release env file is not cutover-safe; blockers=34 | `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs` |
| docker-images | release-infra | BLOCKED | docker CLI is not available: spawnSync docker ENOENT | `node scripts/ddd-docker-build-evidence.mjs --check` |
| runtime-business | release-infra, frontend, ai, file-owner, job-owner, payment-owner | BLOCKED | LUMIRA_BASE_URL is required | `node scripts/ddd-staging-runtime-check.mjs` |
| rollback | bounded-context owners | BLOCKED | rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE | `node scripts/ddd-staging-data-safety-check.mjs` |
| migration | database | BLOCKED | DDD_MIGRATION_FRESH_DB_VALIDATED must be true | `node scripts/ddd-staging-data-safety-check.mjs` |
| explain | database | BLOCKED | DDD_EXPLAIN_DATABASE is required | `node scripts/ddd-staging-data-safety-check.mjs` |

## Lane Routes

| Order | Lane | Owner | Status | Source | Command |
| ---: | --- | --- | --- | --- | --- |
| 1 | `p0-release-env` | release-infra | BLOCKED | `release-env-plan.json` | `node scripts/ddd-staging-execution-checklist.mjs --release-env-next-owner-template` |
| 2 | `p0-docker-images` | release-infra | BLOCKED | `docker-image-submission-plan.json` | `node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown` |
| 3 | `p1-runtime-business` | release-infra | BLOCKED | `runtime-business-submission-plan.json` | `node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown` |
| 4 | `p1-p2-data-safety` | platform-owners | BLOCKED | `data-safety-submission-plan.json` | `node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown` |
| 5 | `final-review` | release-infra | BLOCKED | `final-review.json` | `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce` |

## Handoff Bundle

- Directory: `artifacts/ddd/release/staging-handoff-bundle`
- Manifest: `artifacts/ddd/release/staging-handoff-bundle/manifest.json`
- Checked files: 0
- no bundle integrity issues

Next: `node scripts/ddd-staging-execution-checklist.mjs --commands`
