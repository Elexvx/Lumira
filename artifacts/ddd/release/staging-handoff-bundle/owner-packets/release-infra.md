# DDD Staging Owner Packet: release-infra

Generated at: 2026-06-19T09:03:45.762Z
Owner: release-infra
Blockers: 9
Placeholders: 9
Secret keys: 4
Handoff: artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md

## Required Keys

- LUMIRA_BASE_URL
- CORS_ALLOWED_ORIGIN_PATTERNS
- DB_PASSWORD
- DB_URL
- DB_USERNAME
- FIELD_SECRET
- PLAYWRIGHT_BASE_URL
- JWT_SECRET
- REDIS_HOST
- REDIS_PASSWORD
- REDIS_PORT
- TRUST_FORWARDED_HEADERS

## Input Reasons

- owner-production-value
- production-endpoint
- secret-manager

## Post-Fill Validation

  - `node scripts/ddd-release-env-init.mjs --check`
  - `node scripts/ddd-release-env-init.mjs`
  - `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file> --next-action-env-receipt-output=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --next-action-env-receipt-file=<receipt-file>`
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template-markdown`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>`

## Queue Lanes

| Order | Lane | Status | Missing artifacts | Command | Source |
| ---: | --- | --- | ---: | --- | --- |
| 1 | `p0-release-env` | BLOCKED | 0 | `node scripts/ddd-staging-execution-checklist.mjs --release-env-next-owner-template` | `release-env-plan.json` |
| 2 | `p0-docker-images` | PASS | 0 | `node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown` | `docker-image-submission-plan.json` |
| 3 | `p1-runtime-business` | BLOCKED | 0 | `node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown` | `runtime-business-submission-plan.json` |
| 5 | `final-review` | BLOCKED | 0 | `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce` | `final-review.json` |

## Submission Routes

### p0-release-env

Source plan: `release-env-plan.json`
Next command: `node scripts/ddd-staging-execution-checklist.mjs --release-env-next-owner-template`
Acceptance commands: `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
Expected artifacts: `artifacts/ddd/release/release-env-lint.json`, `artifacts/ddd/config/release-config-evidence.json`, `artifacts/ddd/release/readiness-summary.json`
Currently missing artifacts: none

### p0-docker-images

Source plan: `docker-image-submission-plan.json`
Next command: `node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown`
Acceptance commands: `node scripts/ddd-docker-build-evidence.mjs --check`
Expected artifacts: `artifacts/ddd/build/docker-image-evidence.json`
Currently missing artifacts: none

### p1-runtime-business

Source plan: `runtime-business-submission-plan.json`
Next command: `node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown`
Acceptance commands: `node scripts/ddd-staging-runtime-check.mjs`
Expected artifacts: `artifacts/ddd/readiness/summary.json`, `artifacts/ddd/performance/authenticated-runtime-actual.json`, `artifacts/ddd/ai/ai-runtime-drill.json`, `artifacts/ddd/frontend/frontend-smoke.json`, `artifacts/ddd/file/file-processing-e2e.json`, `artifacts/ddd/jobs/job-e2e-smoke.json`, `artifacts/ddd/payment/payment-webhook-e2e.json`
Currently missing artifacts: none

### final-review

Source plan: `final-review.json`
Next command: `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce`
Acceptance commands: `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`, `node scripts/ddd-docker-build-evidence.mjs --check`, `node scripts/ddd-staging-runtime-check.mjs`, `node scripts/ddd-staging-data-safety-check.mjs`
Expected artifacts: `artifacts/ddd/release/release-env-lint.json`, `artifacts/ddd/config/release-config-evidence.json`, `artifacts/ddd/release/readiness-summary.json`, `artifacts/ddd/build/docker-image-evidence.json`, `artifacts/ddd/readiness/summary.json`, `artifacts/ddd/performance/authenticated-runtime-actual.json`, `artifacts/ddd/ai/ai-runtime-drill.json`, `artifacts/ddd/frontend/frontend-smoke.json`, `artifacts/ddd/file/file-processing-e2e.json`, `artifacts/ddd/jobs/job-e2e-smoke.json`, `artifacts/ddd/payment/payment-webhook-e2e.json`, `artifacts/ddd/rollback/rollback-drill.json`, `artifacts/ddd/migration/migration-evidence.json`, `tmp/ddd-explain/*.json`, `artifacts/ddd/release/explain-gate-report.json`
Currently missing artifacts: `tmp/ddd-explain/*.json`

## Current Blocking Inputs

### release-env

Status: BLOCKED
First blocker: release env file is not cutover-safe; blockers=34
Next command: `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
Blocking inputs: DDD_RELEASE_ENV_FILE

### runtime-business

Status: BLOCKED
First blocker: LUMIRA_BASE_URL is required
Next command: `node scripts/ddd-staging-runtime-check.mjs`
Blocking inputs: LUMIRA_BASE_URL, PLAYWRIGHT_BASE_URL, DDD_DEPLOYMENT_EVIDENCE, DDD_FRONTEND_DEPLOYMENT_EVIDENCE, DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE, DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE, DDD_FRONTEND_EXPECT_DEPLOYED, DDD_AI_EXPECT_PROVIDER_REMOTE, DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE

## Staging Evidence Gaps

### p0-release-env: P0 release env and config

Reason: release env file is not cutover-safe; blockers=34
Next command: `node scripts/ddd-release-env-init.mjs --check`
Artifacts: artifacts/ddd/release/release-env-lint.json, artifacts/ddd/config/release-config-evidence.json, artifacts/ddd/release/readiness-summary.json
Env keys: AI_SERVICE_BASE_URL, AUTH_SERVICE_BASE_URL, BASE_URL, CORS_ALLOWED_ORIGIN_PATTERNS, DB_PASSWORD, DB_URL, DB_USERNAME, DDD_AUTH_PASSWORD, DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE, DDD_AUTH_PERF_ENVIRONMENT, DDD_AUTH_USERNAME, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DDD_DEPLOYMENT_EVIDENCE, DDD_EXPLAIN_DATABASE, DDD_FRONTEND_DEPLOYMENT_EVIDENCE, DDD_MIGRATION_COMPLETED_AT, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_OPERATOR, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED, FIELD_SECRET, FILE_SERVICE_BASE_URL, JOB_EXECUTOR_BASE_URL, JWT_SECRET, LOCALIZATION_SERVICE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_BASE_URL, MESSAGE_SERVICE_BASE_URL, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PORT, PAYMENT_PUBLIC_BASE_URL, PAYMENT_SERVICE_BASE_URL, PLAYWRIGHT_BASE_URL, PLUGIN_SERVICE_BASE_URL, REDIS_HOST, SAAS_EVENT_REDIS_STREAM_KEY, SAAS_JOB_BACKEND_BASE_URL, SAAS_JOB_FILE_SERVICE_BASE_URL, SAAS_JOB_INTERNAL_TOKEN, SAAS_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL, SAAS_JOB_PLUGIN_SERVICE_BASE_URL, SYSTEM_SERVICE_BASE_URL, XXL_JOB_ACCESS_TOKEN, XXL_JOB_ADMIN_ADDRESSES, DEPLOY_CHECK_BASE_URL, LUMIRA_AI_BASE_URL, FRONTEND_BASE_URL

### p0-images: P0 deployable images

Reason: backend and frontend images must be built or inspected from CI-produced release images
Next command: `node scripts/ddd-docker-build-evidence.mjs --check`
Artifacts: artifacts/ddd/build/docker-image-evidence.json
Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND, DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE, DDD_DOCKER_EXISTING_FRONTEND_IMAGE

### p1-runtime-business: P1 runtime and business acceptance

Reason: local-only runtime evidence must be replaced by HTTPS staging evidence
Next command: `node scripts/ddd-staging-runtime-check.mjs`
Artifacts: artifacts/ddd/readiness/summary.json, artifacts/ddd/performance/authenticated-runtime-actual.json, artifacts/ddd/ai/ai-runtime-drill.json, artifacts/ddd/frontend/frontend-smoke.json, artifacts/ddd/file/file-processing-e2e.json, artifacts/ddd/jobs/job-e2e-smoke.json, artifacts/ddd/payment/payment-webhook-e2e.json
Env keys: LUMIRA_BASE_URL, PLAYWRIGHT_BASE_URL, DDD_FRONTEND_EXPECT_DEPLOYED, DDD_AI_EXPECT_PROVIDER_REMOTE, DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE

## Missing Evidence Artifacts

- none

## Owner Completion Receipt

- Fill `lane-completion-receipt.template.json` with redacted PASS/BLOCKED results for this owner's queue lanes.
- A lane is complete only after its acceptance commands pass and expected artifacts are listed in providedArtifacts.
- Submit the redacted receipt file to release-infra, then re-run final review with that receipt.
- Edit rule: update only this owner's laneReceipts entries, then leave unrelated owner/lane pairs unchanged
- Lane keys: `release-infra:p0-release-env`, `release-infra:p0-docker-images`, `release-infra:p1-runtime-business`, `release-infra:final-review`

Commands:
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template-markdown`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>`

## Safety

- Do not commit populated secrets.
- Use the secure release env file referenced by DDD_RELEASE_ENV_FILE.
- Re-run the staging checklist after owner inputs are merged.

