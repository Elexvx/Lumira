# DDD Staging Evidence Runbook

Status: STAGING_REQUIRED
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Blocked tracks: 5/6

## 1. P0 release env and config

- Track: `p0-release-env`
- Owner: release-infra
- Status: ready
- Reason: release env file is cutover-safe
- Next command: `node scripts/ddd-release-env-init.mjs --check`

Setup commands:
- `node scripts/ddd-release-env-init.mjs --check`
- `node scripts/ddd-release-env-init.mjs`

Evidence commands:
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs`
- `node scripts/ddd-release-readiness-summary.mjs`

Expected artifacts:
- `artifacts/ddd/release/release-env-lint.json`
- `artifacts/ddd/config/release-config-evidence.json`
- `artifacts/ddd/release/readiness-summary.json`

Env keys:
- `BASE_URL`
- `DEPLOY_CHECK_BASE_URL`
- `LUMIRA_BASE_URL`
- `LUMIRA_AI_BASE_URL`
- `PLAYWRIGHT_BASE_URL`
- `FRONTEND_BASE_URL`
- `DDD_MIGRATION_FRESH_DB_VALIDATED`
- `DDD_MIGRATION_UPGRADE_DB_VALIDATED`
- `DDD_MIGRATION_FRESH_DB_EVIDENCE`
- `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`

## 2. P0 deployable images

- Track: `p0-images`
- Owner: release-infra
- Status: blocked
- Reason: backend and frontend images must be built or inspected from CI-produced release images
- Next command: `node scripts/ddd-docker-build-evidence.mjs --check`

Setup commands:
- `node scripts/ddd-docker-build-evidence.mjs --check`

Evidence commands:
- `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
- `node scripts/ddd-release-readiness-summary.mjs`

Expected artifacts:
- `artifacts/ddd/build/docker-image-evidence.json`

Env keys:
- `DDD_DOCKER_BUILD_STRICT`
- `DDD_DOCKER_COMMAND`
- `DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE`
- `DDD_DOCKER_EXISTING_FRONTEND_IMAGE`

## 3. P1 runtime and business acceptance

- Track: `p1-runtime-business`
- Owner: release-infra, frontend, ai, file-owner, job-owner, payment-owner
- Status: blocked
- Reason: local-only runtime evidence must be replaced by HTTPS staging evidence
- Next command: `node scripts/ddd-staging-runtime-check.mjs`

Setup commands:
- `node scripts/ddd-staging-runtime-check.mjs`

Evidence commands:
- `node scripts/ddd-runtime-readiness-smoke.mjs`
- `DDD_AUTH_PERF_STRICT=true node scripts/ddd-authenticated-performance-smoke.mjs`
- `node scripts/ddd-ai-runtime-drill.mjs`
- `node scripts/ddd-frontend-playwright-smoke.mjs`
- `node scripts/ddd-frontend-smoke-evidence.mjs`
- `node scripts/ddd-file-processing-e2e-smoke.mjs`
- `node scripts/ddd-job-e2e-smoke.mjs`
- `node scripts/ddd-payment-webhook-e2e-smoke.mjs`

Expected artifacts:
- `artifacts/ddd/readiness/summary.json`
- `artifacts/ddd/performance/authenticated-runtime-actual.json`
- `artifacts/ddd/ai/ai-runtime-drill.json`
- `artifacts/ddd/frontend/frontend-smoke.json`
- `artifacts/ddd/file/file-processing-e2e.json`
- `artifacts/ddd/jobs/job-e2e-smoke.json`
- `artifacts/ddd/payment/payment-webhook-e2e.json`

Env keys:
- `LUMIRA_BASE_URL`
- `PLAYWRIGHT_BASE_URL`
- `DDD_FRONTEND_EXPECT_DEPLOYED`
- `DDD_AI_EXPECT_PROVIDER_REMOTE`
- `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE`

## 4. P1 rollback safety

- Track: `p1-rollback`
- Owner: bounded-context owners
- Status: blocked
- Reason: every bounded context needs PASS rollback drill evidence or approved unexpired deferral
- Next command: `node scripts/ddd-staging-data-safety-check.mjs`

Setup commands:
- `node scripts/ddd-staging-data-safety-check.mjs`

Evidence commands:
- `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
- `node scripts/ddd-rollback-drill-evidence.mjs`

Expected artifacts:
- `artifacts/ddd/rollback/rollback-drill.json`

Env keys:
- `DDD_ROLLBACK_DRILL_CHECK_ENV`
- `DDD_ROLLBACK_DRILL_STRICT`
- `DDD_ROLLBACK_DRILL_FILE`

## 5. P2 database migration and EXPLAIN

- Track: `p2-database-performance`
- Owner: database
- Status: blocked
- Reason: fresh production-equivalent migration and hot-path EXPLAIN evidence are required
- Next command: `node scripts/ddd-staging-data-safety-check.mjs`

Setup commands:
- `node scripts/ddd-staging-data-safety-check.mjs`

Evidence commands:
- `DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs`
- `DDD_MIGRATION_STRICT=true node scripts/ddd-migration-evidence.mjs`
- `node scripts/ddd-collect-explain.mjs`
- `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`

Expected artifacts:
- `artifacts/ddd/migration/migration-evidence.json`
- `tmp/ddd-explain/*.json`
- `artifacts/ddd/release/explain-gate-report.json`

Env keys:
- `DDD_MIGRATION_FRESH_DB_VALIDATED`
- `DDD_MIGRATION_UPGRADE_DB_VALIDATED`
- `DDD_EXPLAIN_DATABASE`
- `DDD_EXPLAIN_ENVIRONMENT`

## 6. P3 strict orchestrator and final gate

- Track: `p3-final-strict`
- Owner: release-owner
- Status: blocked
- Reason: final recommendation is NO_GO_STRICT
- Next command: `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`

Setup commands:
- none

Evidence commands:
- `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
- `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-gate.mjs`
- `node scripts/ddd-release-readiness-summary.mjs`
- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

Expected artifacts:
- `artifacts/ddd/release/orchestrator-report.json`
- `artifacts/ddd/release/evidence-manifest.json`
- `artifacts/ddd/release/release-evidence-gate.json`
- `artifacts/ddd/release/release-final-go-no-go.json`

Env keys:
- `DDD_RELEASE_EVIDENCE_STRICT`
- `DDD_FINAL_GO_NO_GO_ENFORCE`

Next: `node scripts/ddd-staging-execution-checklist.mjs --execution-status`
