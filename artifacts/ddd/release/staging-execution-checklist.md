# DDD Staging Execution Checklist

Generated at: 2026-06-19T17:42:14.912Z
Status: STAGING_REQUIRED
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Evidence gate: blockers=94 warnings=8 strict=true

## First Move

- Release env is marked ready; continue with deployable image evidence and staging runtime smokes.
- If a populated env file does not exist yet, check the initializer with `node bin/ddd-release-env-init.mjs --check`, then initialize it with `node bin/ddd-release-env-init.mjs`.
- Run `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh` before expensive evidence collection.
- Do not cut over until `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh` exits cleanly.

## Release Env Owner Handoff

- no owner handoff blockers reported

## Blocked Cutover Items

- strict-release-gate: Strict release gate has zero blockers and no contract issues. pending=94
- deployable-images: Deployable backend/frontend images are built and inspected. pending=4
- production-equivalence: Runtime and performance evidence use HTTPS non-local production-equivalent endpoints. pending=13
- runtime-business-acceptance: AI, frontend, file, job, and payment acceptance evidence is complete. pending=7
- rollback-safety: Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance. pending=10
- database-performance: Fresh production-equivalent EXPLAIN evidence has no scan/index blockers. pending=8
- evidence-integrity: Evidence manifest and final orchestrator strict rerun are clean. pending=5

## Immediate P0 Waves

### p0-docker-release-infra

Owner: release-infra
Receipt status: READY_FOR_STRICT_GATE_RERUN

  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`

### p0-runtime-readiness-release-infra

Owner: release-infra
Receipt status: READY_FOR_STRICT_GATE_RERUN

  - `node scripts/ddd-runtime-readiness-smoke.mjs`

### p0-manifest-release-owner

Owner: release-owner
Receipt status: CONTENT_BLOCKED

  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`

### p0-authenticated-performance-release-performance

Owner: release-performance
Receipt status: READY_FOR_STRICT_GATE_RERUN

  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`

## Execution Tracks

### p0-release-env: P0 release env and config

Status: ready
Owner: release-infra
Reason: release env file is cutover-safe
Env keys: BASE_URL, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, LUMIRA_AI_BASE_URL, PLAYWRIGHT_BASE_URL, FRONTEND_BASE_URL, DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_EVIDENCE

Setup:
  - `node bin/ddd-release-env-init.mjs --check`
  - `node bin/ddd-release-env-init.mjs`

Commands:
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-file-lint.mjs`
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-config-evidence.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

Artifacts: artifacts/ddd/release/release-env-lint.json, artifacts/ddd/config/release-config-evidence.json, artifacts/ddd/release/readiness-summary.json

### p0-images: P0 deployable images

Status: blocked
Owner: release-infra
Reason: backend and lumira-ui images must be built or inspected from CI-produced release images
Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND, DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE, DDD_DOCKER_EXISTING_FRONTEND_IMAGE

Setup:
  - `node bin/ddd-docker-build-evidence.mjs --check`

Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

Artifacts: artifacts/ddd/build/docker-image-evidence.json

### p1-runtime-business: P1 runtime and business acceptance

Status: blocked
Owner: release-infra, lumira-ui, ai, file-owner, job-owner, payment-owner
Reason: local-only runtime evidence must be replaced by HTTPS staging evidence
Env keys: LUMIRA_BASE_URL, PLAYWRIGHT_BASE_URL, DDD_FRONTEND_EXPECT_DEPLOYED, DDD_AI_EXPECT_PROVIDER_REMOTE, DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE

Setup:
  - `node bin/ddd-staging-runtime-check.mjs`

Commands:
  - `node bin/ddd-runtime-readiness-smoke.mjs`
  - `DDD_AUTH_PERF_STRICT=true node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-ai-runtime-drill.mjs`
  - `node bin/ddd-frontend-playwright-smoke.mjs`
  - `node bin/ddd-frontend-smoke-evidence.mjs`
  - `node bin/ddd-file-processing-e2e-smoke.mjs`
  - `node bin/ddd-job-e2e-smoke.mjs`
  - `node bin/ddd-payment-webhook-e2e-smoke.mjs`

Artifacts: artifacts/ddd/readiness/summary.json, artifacts/ddd/performance/authenticated-runtime-actual.json, artifacts/ddd/ai/ai-runtime-drill.json, artifacts/ddd/lumira-ui/frontend-smoke.json, artifacts/ddd/file/file-processing-e2e.json, artifacts/ddd/jobs/job-e2e-smoke.json, artifacts/ddd/payment/payment-webhook-e2e.json

### p1-rollback: P1 rollback safety

Status: blocked
Owner: bounded-context owners
Reason: every bounded context needs PASS rollback drill evidence or approved unexpired deferral
Env keys: DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_STRICT, DDD_ROLLBACK_DRILL_FILE

Setup:
  - `node bin/ddd-staging-data-safety-check.mjs`

Commands:
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`

Artifacts: artifacts/ddd/rollback/rollback-drill.json

### p2-database-performance: P2 database migration and EXPLAIN

Status: blocked
Owner: database
Reason: fresh production-equivalent migration and hot-path EXPLAIN evidence are required
Env keys: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_ENVIRONMENT

Setup:
  - `node bin/ddd-staging-data-safety-check.mjs`

Commands:
  - `DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs`
  - `DDD_MIGRATION_STRICT=true node bin/ddd-migration-evidence.mjs`
  - `node bin/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`

Artifacts: artifacts/ddd/migration/migration-evidence.json, tmp/ddd-explain/*.json, artifacts/ddd/release/explain-gate-report.json

### p3-final-strict: P3 strict orchestrator and final gate

Status: blocked
Owner: release-owner
Reason: final recommendation is NO_GO_STRICT
Env keys: DDD_RELEASE_EVIDENCE_STRICT, DDD_FINAL_GO_NO_GO_ENFORCE

Commands:
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

Artifacts: artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/evidence-manifest.json, artifacts/ddd/release/release-evidence-gate.json, artifacts/ddd/release/release-final-go-no-go.json

## Source Artifacts

- finalPacket: artifacts/ddd/release/release-final-go-no-go.json
- evidenceGate: artifacts/ddd/release/release-evidence-gate.json
- readinessSummary: artifacts/ddd/release/readiness-summary.json
- commandCatalog: artifacts/ddd/release/release-command-catalog.md
- missingEnvTemplate: artifacts/ddd/release/release-env-missing.template.env
- releaseEnvInit: artifacts/ddd/release/release-final-owner-queue-env-init.sh

