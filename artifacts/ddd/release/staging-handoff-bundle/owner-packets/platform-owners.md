# DDD Staging Owner Packet: platform-owners

Generated at: 2026-06-19T09:03:45.762Z
Owner: platform-owners
Blockers: 9
Placeholders: 9
Secret keys: 0
Handoff: artifacts/ddd/release/release-env-owner-handoff-redacted/02-platform-owners.md

## Required Keys

- AI_SERVICE_BASE_URL
- AUTH_SERVICE_BASE_URL
- FILE_SERVICE_BASE_URL
- JOB_EXECUTOR_BASE_URL
- LOCALIZATION_SERVICE_BASE_URL
- MESSAGE_SERVICE_BASE_URL
- PAYMENT_SERVICE_BASE_URL
- PLUGIN_SERVICE_BASE_URL
- SYSTEM_SERVICE_BASE_URL

## Input Reasons

- production-endpoint

## Post-Fill Validation

  - `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
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
| 4 | `p1-p2-data-safety` | BLOCKED | 1 | `node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown` | `data-safety-submission-plan.json` |

## Submission Routes

### p1-p2-data-safety

Source plan: `data-safety-submission-plan.json`
Next command: `node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown`
Acceptance commands: `node scripts/ddd-staging-data-safety-check.mjs`
Expected artifacts: `artifacts/ddd/rollback/rollback-drill.json`, `artifacts/ddd/migration/migration-evidence.json`, `tmp/ddd-explain/*.json`, `artifacts/ddd/release/explain-gate-report.json`
Currently missing artifacts: `tmp/ddd-explain/*.json`

## Current Blocking Inputs

### release-env

Status: BLOCKED
First blocker: release env file is not cutover-safe; blockers=34
Next command: `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
Blocking inputs: DDD_RELEASE_ENV_FILE

### rollback

Status: BLOCKED
First blocker: rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE
Next command: `node scripts/ddd-staging-data-safety-check.mjs`
Blocking inputs: DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_ENVIRONMENT, DDD_EVIDENCE_ENVIRONMENT, DDD_RELEASE_ENVIRONMENT, DDD_RELEASE_CANDIDATE, GITHUB_SHA, DDD_EVIDENCE_OPERATOR, GITHUB_ACTOR

### migration

Status: BLOCKED
First blocker: DDD_MIGRATION_FRESH_DB_VALIDATED must be true
Next command: `node scripts/ddd-staging-data-safety-check.mjs`
Blocking inputs: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_ENVIRONMENT, DDD_EVIDENCE_ENVIRONMENT, DDD_RELEASE_ENVIRONMENT, DDD_MIGRATION_OPERATOR, DDD_EVIDENCE_OPERATOR, GITHUB_ACTOR, DDD_MIGRATION_COMPLETED_AT

### explain

Status: BLOCKED
First blocker: DDD_EXPLAIN_DATABASE is required
Next command: `node scripts/ddd-staging-data-safety-check.mjs`
Blocking inputs: DDD_EXPLAIN_DATABASE, MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, DDD_EXPLAIN_ENVIRONMENT, DDD_EVIDENCE_ENVIRONMENT, DDD_RELEASE_ENVIRONMENT, DDD_RELEASE_CANDIDATE, GITHUB_SHA, DDD_EVIDENCE_OPERATOR, GITHUB_ACTOR

## Staging Evidence Gaps

### p0-release-env: P0 release env and config

Reason: release env file is not cutover-safe; blockers=34
Next command: `node scripts/ddd-release-env-init.mjs --check`
Artifacts: artifacts/ddd/release/release-env-lint.json, artifacts/ddd/config/release-config-evidence.json, artifacts/ddd/release/readiness-summary.json
Env keys: AI_SERVICE_BASE_URL, AUTH_SERVICE_BASE_URL, BASE_URL, CORS_ALLOWED_ORIGIN_PATTERNS, DB_PASSWORD, DB_URL, DB_USERNAME, DDD_AUTH_PASSWORD, DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE, DDD_AUTH_PERF_ENVIRONMENT, DDD_AUTH_USERNAME, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DDD_DEPLOYMENT_EVIDENCE, DDD_EXPLAIN_DATABASE, DDD_FRONTEND_DEPLOYMENT_EVIDENCE, DDD_MIGRATION_COMPLETED_AT, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_OPERATOR, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED, FIELD_SECRET, FILE_SERVICE_BASE_URL, JOB_EXECUTOR_BASE_URL, JWT_SECRET, LOCALIZATION_SERVICE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_BASE_URL, MESSAGE_SERVICE_BASE_URL, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PORT, PAYMENT_PUBLIC_BASE_URL, PAYMENT_SERVICE_BASE_URL, PLAYWRIGHT_BASE_URL, PLUGIN_SERVICE_BASE_URL, REDIS_HOST, SAAS_EVENT_REDIS_STREAM_KEY, SAAS_JOB_BACKEND_BASE_URL, SAAS_JOB_FILE_SERVICE_BASE_URL, SAAS_JOB_INTERNAL_TOKEN, SAAS_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL, SAAS_JOB_PLUGIN_SERVICE_BASE_URL, SYSTEM_SERVICE_BASE_URL, XXL_JOB_ACCESS_TOKEN, XXL_JOB_ADMIN_ADDRESSES, DEPLOY_CHECK_BASE_URL, LUMIRA_AI_BASE_URL, FRONTEND_BASE_URL

### p1-rollback: P1 rollback safety

Reason: every bounded context needs PASS rollback drill evidence or approved unexpired deferral
Next command: `node scripts/ddd-staging-data-safety-check.mjs`
Artifacts: artifacts/ddd/rollback/rollback-drill.json
Env keys: DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_STRICT, DDD_ROLLBACK_DRILL_FILE

### p2-database-performance: P2 database migration and EXPLAIN

Reason: fresh production-equivalent migration and hot-path EXPLAIN evidence are required
Next command: `node scripts/ddd-staging-data-safety-check.mjs`
Artifacts: artifacts/ddd/migration/migration-evidence.json, tmp/ddd-explain/*.json, artifacts/ddd/release/explain-gate-report.json
Env keys: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_ENVIRONMENT

## Missing Evidence Artifacts

- `tmp/ddd-explain/*.json`: gate=migration; next=`node scripts/ddd-staging-data-safety-check.mjs`
- `tmp/ddd-explain/*.json`: gate=explain; next=`node scripts/ddd-staging-data-safety-check.mjs`

## Owner Completion Receipt

- Fill `lane-completion-receipt.template.json` with redacted PASS/BLOCKED results for this owner's queue lanes.
- A lane is complete only after its acceptance commands pass and expected artifacts are listed in providedArtifacts.
- Submit the redacted receipt file to release-infra, then re-run final review with that receipt.
- Edit rule: update only this owner's laneReceipts entries, then leave unrelated owner/lane pairs unchanged
- Lane keys: `platform-owners:p1-p2-data-safety`

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

