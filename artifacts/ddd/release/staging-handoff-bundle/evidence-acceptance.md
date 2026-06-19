# DDD Staging Evidence Acceptance

Status: BLOCKED
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Accepted: 1/6
Artifacts present: 16
Artifacts missing: 2

| Gate | Owner | Accepted | Current blocker | Blocking inputs | Acceptance command | Expected artifacts |
| --- | --- | --- | --- | --- | --- | --- |
| release-env | release-infra | no | release env file is not cutover-safe; blockers=34 | `DDD_RELEASE_ENV_FILE` | `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs` | `artifacts/ddd/release/release-env-lint.json`, `artifacts/ddd/config/release-config-evidence.json`, `artifacts/ddd/release/readiness-summary.json` |
| docker-images | release-infra | yes | none | `DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE`, `DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE`, `DDD_DOCKER_EXISTING_FRONTEND_IMAGE` | `node scripts/ddd-docker-build-evidence.mjs --check` | `artifacts/ddd/build/docker-image-evidence.json` |
| runtime-business | release-infra, frontend, ai, file-owner, job-owner, payment-owner | no | LUMIRA_BASE_URL is required | `LUMIRA_BASE_URL`, `PLAYWRIGHT_BASE_URL`, `DDD_DEPLOYMENT_EVIDENCE`, `DDD_FRONTEND_DEPLOYMENT_EVIDENCE`, `DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE`, `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE`, `DDD_FRONTEND_EXPECT_DEPLOYED`, `DDD_AI_EXPECT_PROVIDER_REMOTE`, `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE` | `node scripts/ddd-staging-runtime-check.mjs` | `artifacts/ddd/readiness/summary.json`, `artifacts/ddd/performance/authenticated-runtime-actual.json`, `artifacts/ddd/ai/ai-runtime-drill.json`, `artifacts/ddd/frontend/frontend-smoke.json`, `artifacts/ddd/file/file-processing-e2e.json`, `artifacts/ddd/jobs/job-e2e-smoke.json`, `artifacts/ddd/payment/payment-webhook-e2e.json` |
| rollback | bounded-context owners | no | rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE | `DDD_ROLLBACK_DRILL_FILE`, `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`, `DDD_ROLLBACK_DRILL_ENVIRONMENT`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, `GITHUB_SHA`, `DDD_EVIDENCE_OPERATOR`, `GITHUB_ACTOR` | `node scripts/ddd-staging-data-safety-check.mjs` | `artifacts/ddd/rollback/rollback-drill.json` |
| migration | database | no | DDD_MIGRATION_FRESH_DB_VALIDATED must be true | `DDD_MIGRATION_FRESH_DB_VALIDATED`, `DDD_MIGRATION_FRESH_DB_EVIDENCE`, `DDD_MIGRATION_UPGRADE_DB_VALIDATED`, `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`, `DDD_MIGRATION_ENVIRONMENT`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_ENVIRONMENT`, `DDD_MIGRATION_OPERATOR`, `DDD_EVIDENCE_OPERATOR`, `GITHUB_ACTOR`, `DDD_MIGRATION_COMPLETED_AT` | `node scripts/ddd-staging-data-safety-check.mjs` | `artifacts/ddd/migration/migration-evidence.json`, `tmp/ddd-explain/*.json`, `artifacts/ddd/release/explain-gate-report.json` |
| explain | database | no | DDD_EXPLAIN_DATABASE is required | `DDD_EXPLAIN_DATABASE`, `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PASSWORD`, `DDD_EXPLAIN_ENVIRONMENT`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, `GITHUB_SHA`, `DDD_EVIDENCE_OPERATOR`, `GITHUB_ACTOR` | `node scripts/ddd-staging-data-safety-check.mjs` | `artifacts/ddd/migration/migration-evidence.json`, `tmp/ddd-explain/*.json`, `artifacts/ddd/release/explain-gate-report.json` |

## Criteria

### 1. P0 release env and config

- Gate: `release-env`
- Track: `p0-release-env`
- Owner: release-infra
- Status: BLOCKED
- Acceptance command: `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
- Blocking inputs: `DDD_RELEASE_ENV_FILE`
- Artifacts present: 3/3
- present: `artifacts/ddd/release/release-env-lint.json`
- present: `artifacts/ddd/config/release-config-evidence.json`
- present: `artifacts/ddd/release/readiness-summary.json`
- DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs returns PASS
- expected artifacts are produced from HTTPS staging or production-equivalent evidence
- release evidence summary is regenerated after this gate passes

### 2. P0 deployable images

- Gate: `docker-images`
- Track: `p0-images`
- Owner: release-infra
- Status: PASS
- Acceptance command: `node scripts/ddd-docker-build-evidence.mjs --check`
- Blocking inputs: `DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE`, `DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE`, `DDD_DOCKER_EXISTING_FRONTEND_IMAGE`
- Artifacts present: 1/1
- present: `artifacts/ddd/build/docker-image-evidence.json`
- node scripts/ddd-docker-build-evidence.mjs --check returns PASS
- expected artifacts are produced from HTTPS staging or production-equivalent evidence
- release evidence summary is regenerated after this gate passes

### 3. P1 runtime and business acceptance

- Gate: `runtime-business`
- Track: `p1-runtime-business`
- Owner: release-infra, frontend, ai, file-owner, job-owner, payment-owner
- Status: BLOCKED
- Acceptance command: `node scripts/ddd-staging-runtime-check.mjs`
- Blocking inputs: `LUMIRA_BASE_URL`, `PLAYWRIGHT_BASE_URL`, `DDD_DEPLOYMENT_EVIDENCE`, `DDD_FRONTEND_DEPLOYMENT_EVIDENCE`, `DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE`, `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE`, `DDD_FRONTEND_EXPECT_DEPLOYED`, `DDD_AI_EXPECT_PROVIDER_REMOTE`, `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE`
- Artifacts present: 7/7
- present: `artifacts/ddd/readiness/summary.json`
- present: `artifacts/ddd/performance/authenticated-runtime-actual.json`
- present: `artifacts/ddd/ai/ai-runtime-drill.json`
- present: `artifacts/ddd/frontend/frontend-smoke.json`
- present: `artifacts/ddd/file/file-processing-e2e.json`
- present: `artifacts/ddd/jobs/job-e2e-smoke.json`
- present: `artifacts/ddd/payment/payment-webhook-e2e.json`
- node scripts/ddd-staging-runtime-check.mjs returns PASS
- expected artifacts are produced from HTTPS staging or production-equivalent evidence
- release evidence summary is regenerated after this gate passes

### 4. P1 rollback safety

- Gate: `rollback`
- Track: `p1-rollback`
- Owner: bounded-context owners
- Status: BLOCKED
- Acceptance command: `node scripts/ddd-staging-data-safety-check.mjs`
- Blocking inputs: `DDD_ROLLBACK_DRILL_FILE`, `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`, `DDD_ROLLBACK_DRILL_ENVIRONMENT`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, `GITHUB_SHA`, `DDD_EVIDENCE_OPERATOR`, `GITHUB_ACTOR`
- Artifacts present: 1/1
- present: `artifacts/ddd/rollback/rollback-drill.json`
- node scripts/ddd-staging-data-safety-check.mjs returns PASS
- expected artifacts are produced from HTTPS staging or production-equivalent evidence
- release evidence summary is regenerated after this gate passes

### 5. P2 database migration and EXPLAIN

- Gate: `migration`
- Track: `p2-database-performance`
- Owner: database
- Status: BLOCKED
- Acceptance command: `node scripts/ddd-staging-data-safety-check.mjs`
- Blocking inputs: `DDD_MIGRATION_FRESH_DB_VALIDATED`, `DDD_MIGRATION_FRESH_DB_EVIDENCE`, `DDD_MIGRATION_UPGRADE_DB_VALIDATED`, `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`, `DDD_MIGRATION_ENVIRONMENT`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_ENVIRONMENT`, `DDD_MIGRATION_OPERATOR`, `DDD_EVIDENCE_OPERATOR`, `GITHUB_ACTOR`, `DDD_MIGRATION_COMPLETED_AT`
- Artifacts present: 2/3
- present: `artifacts/ddd/migration/migration-evidence.json`
- missing: `tmp/ddd-explain/*.json`
- present: `artifacts/ddd/release/explain-gate-report.json`
- node scripts/ddd-staging-data-safety-check.mjs returns PASS
- expected artifacts are produced from HTTPS staging or production-equivalent evidence
- release evidence summary is regenerated after this gate passes

### 6. P2 database migration and EXPLAIN

- Gate: `explain`
- Track: `p2-database-performance`
- Owner: database
- Status: BLOCKED
- Acceptance command: `node scripts/ddd-staging-data-safety-check.mjs`
- Blocking inputs: `DDD_EXPLAIN_DATABASE`, `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PASSWORD`, `DDD_EXPLAIN_ENVIRONMENT`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, `GITHUB_SHA`, `DDD_EVIDENCE_OPERATOR`, `GITHUB_ACTOR`
- Artifacts present: 2/3
- present: `artifacts/ddd/migration/migration-evidence.json`
- missing: `tmp/ddd-explain/*.json`
- present: `artifacts/ddd/release/explain-gate-report.json`
- node scripts/ddd-staging-data-safety-check.mjs returns PASS
- expected artifacts are produced from HTTPS staging or production-equivalent evidence
- release evidence summary is regenerated after this gate passes

Next: `node scripts/ddd-staging-execution-checklist.mjs --evidence-runbook-markdown`
