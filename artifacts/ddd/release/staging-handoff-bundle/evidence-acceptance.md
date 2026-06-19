# DDD Staging Evidence Acceptance

Status: PASS
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Accepted: 6/6
Artifacts present: 18
Artifacts missing: 0

| Gate | Owner | Accepted | Current blocker | Blocking inputs | Acceptance command | Expected artifacts |
| --- | --- | --- | --- | --- | --- | --- |
| release-env | release-infra | yes | none | none | `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs` | `artifacts/ddd/release/release-env-lint.json`, `artifacts/ddd/config/release-config-evidence.json`, `artifacts/ddd/release/readiness-summary.json` |
| docker-images | release-infra | yes | none | `DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE`, `DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE`, `DDD_DOCKER_EXISTING_FRONTEND_IMAGE` | `node scripts/ddd-docker-build-evidence.mjs --check` | `artifacts/ddd/build/docker-image-evidence.json` |
| runtime-business | release-infra, frontend, ai, file-owner, job-owner, payment-owner | yes | none | none | `node scripts/ddd-staging-runtime-check.mjs` | `artifacts/ddd/readiness/summary.json`, `artifacts/ddd/performance/authenticated-runtime-actual.json`, `artifacts/ddd/ai/ai-runtime-drill.json`, `artifacts/ddd/frontend/frontend-smoke.json`, `artifacts/ddd/file/file-processing-e2e.json`, `artifacts/ddd/jobs/job-e2e-smoke.json`, `artifacts/ddd/payment/payment-webhook-e2e.json` |
| rollback | bounded-context owners | yes | none | none | `node scripts/ddd-staging-data-safety-check.mjs` | `artifacts/ddd/rollback/rollback-drill.json` |
| migration | database | yes | none | none | `node scripts/ddd-staging-data-safety-check.mjs` | `artifacts/ddd/migration/migration-evidence.json`, `tmp/ddd-explain/*.json`, `artifacts/ddd/release/explain-gate-report.json` |
| explain | database | yes | none | none | `node scripts/ddd-staging-data-safety-check.mjs` | `artifacts/ddd/migration/migration-evidence.json`, `tmp/ddd-explain/*.json`, `artifacts/ddd/release/explain-gate-report.json` |

## Criteria

### 1. P0 release env and config

- Gate: `release-env`
- Track: `p0-release-env`
- Owner: release-infra
- Status: PASS
- Acceptance command: `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
- Blocking inputs: none
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
- Status: PASS
- Acceptance command: `node scripts/ddd-staging-runtime-check.mjs`
- Blocking inputs: none
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
- Status: PASS
- Acceptance command: `node scripts/ddd-staging-data-safety-check.mjs`
- Blocking inputs: none
- Artifacts present: 1/1
- present: `artifacts/ddd/rollback/rollback-drill.json`
- node scripts/ddd-staging-data-safety-check.mjs returns PASS
- expected artifacts are produced from HTTPS staging or production-equivalent evidence
- release evidence summary is regenerated after this gate passes

### 5. P2 database migration and EXPLAIN

- Gate: `migration`
- Track: `p2-database-performance`
- Owner: database
- Status: PASS
- Acceptance command: `node scripts/ddd-staging-data-safety-check.mjs`
- Blocking inputs: none
- Artifacts present: 3/3
- present: `artifacts/ddd/migration/migration-evidence.json`
- present: `tmp/ddd-explain/*.json` (8 matches)
- present: `artifacts/ddd/release/explain-gate-report.json`
- node scripts/ddd-staging-data-safety-check.mjs returns PASS
- expected artifacts are produced from HTTPS staging or production-equivalent evidence
- release evidence summary is regenerated after this gate passes

### 6. P2 database migration and EXPLAIN

- Gate: `explain`
- Track: `p2-database-performance`
- Owner: database
- Status: PASS
- Acceptance command: `node scripts/ddd-staging-data-safety-check.mjs`
- Blocking inputs: none
- Artifacts present: 3/3
- present: `artifacts/ddd/migration/migration-evidence.json`
- present: `tmp/ddd-explain/*.json` (8 matches)
- present: `artifacts/ddd/release/explain-gate-report.json`
- node scripts/ddd-staging-data-safety-check.mjs returns PASS
- expected artifacts are produced from HTTPS staging or production-equivalent evidence
- release evidence summary is regenerated after this gate passes

Next: `node scripts/ddd-staging-execution-checklist.mjs --rollup-enforce`
