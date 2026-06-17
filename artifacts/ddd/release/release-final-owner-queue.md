# DDD Final Owner Queue

Generated at: 2026-06-17T08:13:17.325Z
Status: ADVISORY
Recommendation: NO_GO_STRICT
Cutover allowed: false
No auto waivers: true
Owners: 18
Actionable owners: 5
Waiting owners: 13
Unique missing artifacts: 15
Unique content blockers: 0
Owner input receipt status: PENDING_OWNER_INPUT
Owner input receipt cutover ready: false
Owner input receipt required inputs: 34
Owner input receipt pending owners: 5
Owner input receipt missing criteria: 3
Next executable owner: release-infra
Next executable command: DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh
Queue order: ACTIONABLE owners first, then WAITING owners.

## Fast Path

- Objective: Run the next actionable owner queue through strict evidence refresh and final go/no-go without bypassing safety gates.
- Blocked until: Required owner env keys and expected evidence artifacts are available in a permission-safe release env file.
- Owner: release-infra
- Queue order: 1
- First command: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- Release env file required: true
- Env keys: 68
- Missing artifacts: 3
- Commands:
  - `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## Safety Signals

- releaseEnvFileCutoverSafe: false
- releaseEnvFile: ready=false status=FAIL inputKind=release-env-file envFilePresent=true
  - securityChecked=true permissionSafe=true mode=600 requiredMode=600 reason=env-file permissionCheckSkipped=false
  - pendingActions=release-env-lint-status, release-env-lint-placeholders

## Owner Input Receipt

- Status: PENDING_OWNER_INPUT
- Cutover ready: false
- Required owner inputs: 34
- Owners: 5
- Ready owners: 0
- Pending owners: 5
- Artifact: artifacts/ddd/release/release-owner-input-receipt.json
- Markdown: artifacts/ddd/release/release-owner-input-receipt.md
- Missing criteria:
  - releaseEnvReadinessBlockers
  - releaseEnvReadinessPlaceholders
  - releaseEnvReadinessStatus
- Pending owner inputs:
  - platform-events: required=9 placeholders=9 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/01-platform-events.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/01-platform-events.md checklist=artifacts/ddd/release/release-owner-input-receipt-items/01-platform-events.md
  - platform-owners: required=9 placeholders=9 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/02-platform-owners.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/02-platform-owners.md checklist=artifacts/ddd/release/release-owner-input-receipt-items/02-platform-owners.md
  - release-infra: required=9 placeholders=9 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md checklist=artifacts/ddd/release/release-owner-input-receipt-items/03-release-infra.md
  - ai-owner: required=6 placeholders=6 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/04-ai-owner.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/04-ai-owner.md checklist=artifacts/ddd/release/release-owner-input-receipt-items/04-ai-owner.md
  - payment-owner: required=1 placeholders=1 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/05-payment-owner.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/05-payment-owner.md checklist=artifacts/ddd/release/release-owner-input-receipt-items/05-payment-owner.md

## release-infra

- Queue order: 1
- Execution order hint: 1
- Queue status: ACTIONABLE
- Can execute: true
- Cutover items: deployable-images, release-environment
- Ready batches: p0-docker-release-infra, p0-release-config-release-infra, p0-release-env-lint-release-infra
- Blocked batches: none
- Closure waves: 1, 6, 7
- Commands: 7
- Env keys: 68
- Missing artifacts: 3
- Content blockers: 0
- Stop reasons: 2
- First command: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- Env key names:
  - `AI_SERVICE_BASE_URL`
  - `AUTH_SERVICE_BASE_URL`
  - `BASE_URL`
  - `CORS_ALLOWED_ORIGIN_PATTERNS`
  - `DB_PASSWORD`
  - `DB_URL`
  - `DB_USERNAME`
  - `DDD_AUTH_PASSWORD`
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE`
  - `DDD_AUTH_PERF_ENVIRONMENT`
  - `DDD_AUTH_USERNAME`
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
  - `DDD_DEPLOYMENT_EVIDENCE`
  - `DDD_DOCKER_BUILD_STRICT`
  - `DDD_DOCKER_COMMAND`
  - `DDD_EXPLAIN_DATABASE`
  - `DDD_FRONTEND_DEPLOYMENT_EVIDENCE`
  - `DDD_MIGRATION_COMPLETED_AT`
  - `DDD_MIGRATION_FRESH_DB_EVIDENCE`
  - `DDD_MIGRATION_FRESH_DB_VALIDATED`
  - `DDD_MIGRATION_OPERATOR`
  - `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`
  - `DDD_MIGRATION_UPGRADE_DB_VALIDATED`
  - `DEPLOY_CHECK_BASE_URL`
  - `FIELD_SECRET`
  - `FILE_SERVICE_BASE_URL`
  - `FRONTEND_BASE_URL`
  - `JOB_EXECUTOR_BASE_URL`
  - `JWT_SECRET`
  - `LOCALIZATION_SERVICE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`
  - `LUMIRA_BASE_URL`
  - `MESSAGE_SERVICE_BASE_URL`
  - `MYSQL_DATABASE`
  - `MYSQL_HOST`
  - `MYSQL_PASSWORD`
  - `MYSQL_PORT`
  - `MYSQL_USER`
  - `PAYMENT_PUBLIC_BASE_URL`
  - `PAYMENT_SERVICE_BASE_URL`
  - `PLAYWRIGHT_BASE_URL`
  - `PLUGIN_SERVICE_BASE_URL`
  - `REDIS_HOST`
  - `SAAS_EVENT_REDIS_STREAM_KEY`
  - `SAAS_JOB_BACKEND_BASE_URL`
  - `SAAS_JOB_FILE_SERVICE_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN`
  - `SAAS_JOB_MESSAGE_SERVICE_BASE_URL`
  - `SAAS_JOB_PAYMENT_SERVICE_BASE_URL`
  - `SAAS_JOB_PLUGIN_SERVICE_BASE_URL`
  - `SAAS_SECURITY_FIELD_SECRET`
  - `SAAS_SECURITY_JWT_SECRET`
  - `SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS`
  - `SPRING_DATASOURCE_PASSWORD`
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATA_REDIS_HOST`
  - `SYSTEM_SERVICE_BASE_URL`
  - `XXL_JOB_ACCESS_TOKEN`
  - `XXL_JOB_ADMIN_ADDRESSES`
- Missing artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/release/release-env-lint.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## ai-owner

- Queue order: 2
- Execution order hint: 2
- Queue status: ACTIONABLE
- Can execute: true
- Cutover items: release-environment, rollback-safety
- Ready batches: p0-release-config-ai-owner
- Blocked batches: p1-rollback-ai-owner
- Closure waves: 2
- Commands: 6
- Env keys: 21
- Missing artifacts: 2
- Content blockers: 0
- Stop reasons: 2
- First command: `node scripts/ddd-release-config-evidence.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
  - `LUMIRA_AI_OWNER_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL`
  - `LUMIRA_AI_OWNER_INTERNAL_TOKEN`
  - `LUMIRA_AI_OWNER_PLATFORM_BASE_URL`
  - `LUMIRA_AI_PROVIDER_API_KEY`
  - `LUMIRA_AI_PROVIDER_BASE_URL`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN`
- Missing artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## payment-owner

- Queue order: 3
- Execution order hint: 3
- Queue status: ACTIONABLE
- Can execute: true
- Cutover items: release-environment, rollback-safety, runtime-business-acceptance
- Ready batches: p0-release-config-payment-owner
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
- Closure waves: 3
- Commands: 7
- Env keys: 13
- Missing artifacts: 3
- Content blockers: 0
- Stop reasons: 3
- First command: `node scripts/ddd-release-config-evidence.mjs`
- Env key names:
  - `BASE_URL`
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
  - `DEPLOY_CHECK_BASE_URL`
  - `LUMIRA_BASE_URL`
  - `PAYMENT_PUBLIC_BASE_URL`
- Missing artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/payment/payment-webhook-e2e.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## platform-events

- Queue order: 4
- Execution order hint: 4
- Queue status: ACTIONABLE
- Can execute: true
- Cutover items: release-environment
- Ready batches: p0-release-config-platform-events
- Blocked batches: none
- Closure waves: 4
- Commands: 3
- Env keys: 20
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 1
- First command: `node scripts/ddd-release-config-evidence.mjs`
- Env key names:
  - `DDD_JOB_INTERNAL_TOKEN`
  - `LUMIRA_EVENT_REDIS_STREAM_KEY`
  - `LUMIRA_JOB_BACKEND_BASE_URL`
  - `LUMIRA_JOB_FILE_SERVICE_BASE_URL`
  - `LUMIRA_JOB_INTERNAL_TOKEN`
  - `LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL`
  - `LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL`
  - `LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL`
  - `LUMIRA_XXL_JOB_ACCESS_TOKEN`
  - `LUMIRA_XXL_JOB_ADMIN_ADDRESSES`
  - `SAAS_EVENT_REDIS_STREAM_KEY`
  - `SAAS_JOB_BACKEND_BASE_URL`
  - `SAAS_JOB_FILE_SERVICE_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN`
  - `SAAS_JOB_MESSAGE_SERVICE_BASE_URL`
  - `SAAS_JOB_PAYMENT_SERVICE_BASE_URL`
  - `SAAS_JOB_PLUGIN_SERVICE_BASE_URL`
  - `XXL_JOB_ACCESS_TOKEN`
  - `XXL_JOB_ADMIN_ACCESS_TOKEN`
  - `XXL_JOB_ADMIN_ADDRESSES`
- Missing artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## platform-owners

- Queue order: 5
- Execution order hint: 5
- Queue status: ACTIONABLE
- Can execute: true
- Cutover items: release-environment
- Ready batches: p0-release-config-platform-owners
- Blocked batches: none
- Closure waves: 5
- Commands: 3
- Env keys: 19
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 1
- First command: `node scripts/ddd-release-config-evidence.mjs`
- Env key names:
  - `AI_SERVICE_BASE_URL`
  - `AUTH_SERVICE_BASE_URL`
  - `FILE_SERVICE_BASE_URL`
  - `JOB_EXECUTOR_BASE_URL`
  - `LOCALIZATION_SERVICE_BASE_URL`
  - `LUMIRA_AI_BASE_URL`
  - `LUMIRA_AI_SERVICE_BASE_URL`
  - `LUMIRA_AUTH_SERVICE_BASE_URL`
  - `LUMIRA_FILE_SERVICE_BASE_URL`
  - `LUMIRA_JOB_EXECUTOR_BASE_URL`
  - `LUMIRA_LOCALIZATION_SERVICE_BASE_URL`
  - `LUMIRA_MESSAGE_SERVICE_BASE_URL`
  - `LUMIRA_PAYMENT_SERVICE_BASE_URL`
  - `LUMIRA_PLUGIN_SERVICE_BASE_URL`
  - `LUMIRA_SYSTEM_SERVICE_BASE_URL`
  - `MESSAGE_SERVICE_BASE_URL`
  - `PAYMENT_SERVICE_BASE_URL`
  - `PLUGIN_SERVICE_BASE_URL`
  - `SYSTEM_SERVICE_BASE_URL`
- Missing artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## ai

- Queue order: 6
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: runtime-business-acceptance
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai
- Closure waves: none
- Commands: 1
- Env keys: 12
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 1
- First command: `node scripts/ddd-ai-runtime-drill.mjs`
- Env key names:
  - `BASE_URL`
  - `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE`
  - `DDD_AI_EXPECT_PROVIDER_REMOTE`
  - `DEPLOY_CHECK_BASE_URL`
  - `LUMIRA_AI_BASE_URL`
  - `LUMIRA_AI_OWNER_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_PLATFORM_BASE_URL`
  - `LUMIRA_AI_PROVIDER`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`
  - `LUMIRA_BASE_URL`
- Missing artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## auth-owner

- Queue order: 7
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety
- Ready batches: none
- Blocked batches: p1-rollback-auth-owner
- Closure waves: none
- Commands: 3
- Env keys: 8
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 1
- First command: `node scripts/ddd-rollback-deferral-template.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Missing artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## database

- Queue order: 8
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: database-performance, evidence-integrity
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Closure waves: none
- Commands: 4
- Env keys: 16
- Missing artifacts: 5
- Content blockers: 0
- Stop reasons: 2
- First command: `node scripts/ddd-collect-explain.mjs`
- Env key names:
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_EXPLAIN_DATABASE`
  - `DDD_EXPLAIN_DIR`
  - `DDD_EXPLAIN_ENVIRONMENT`
  - `DDD_EXPLAIN_STRICT`
  - `DDD_MIGRATION_FRESH_DB_EVIDENCE`
  - `DDD_MIGRATION_FRESH_DB_VALIDATED`
  - `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`
  - `DDD_MIGRATION_UPGRADE_DB_VALIDATED`
  - `DDD_RELEASE_CANDIDATE`
  - `MYSQL_CLI`
  - `MYSQL_DATABASE`
  - `MYSQL_HOST`
  - `MYSQL_PASSWORD`
  - `MYSQL_PORT`
  - `MYSQL_USER`
- Missing artifacts:
  - `artifacts/ddd/release/explain-gate-report.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `tmp/ddd-explain/*.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## file-owner

- Queue order: 9
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety, runtime-business-acceptance
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-rollback-file-owner
- Closure waves: none
- Commands: 4
- Env keys: 14
- Missing artifacts: 2
- Content blockers: 0
- Stop reasons: 2
- First command: `node scripts/ddd-file-processing-e2e-smoke.mjs`
- Env key names:
  - `BASE_URL`
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
  - `DEPLOY_CHECK_BASE_URL`
  - `LUMIRA_BASE_URL`
  - `LUMIRA_JOB_INTERNAL_TOKEN`
  - `LUMIRA_UPLOAD_STORAGE_ROOT`
- Missing artifacts:
  - `artifacts/ddd/file/file-processing-e2e.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## frontend

- Queue order: 10
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: evidence-integrity, runtime-business-acceptance
- Ready batches: none
- Blocked batches: p1-frontend-smoke-frontend, p3-orchestrator-frontend
- Closure waves: none
- Commands: 4
- Env keys: 3
- Missing artifacts: 5
- Content blockers: 0
- Stop reasons: 2
- First command: `node scripts/ddd-frontend-playwright-smoke.mjs`
- Env key names:
  - `DDD_FRONTEND_EXPECT_DEPLOYED`
  - `FRONTEND_BASE_URL`
  - `PLAYWRIGHT_BASE_URL`
- Missing artifacts:
  - `artifacts/ddd/frontend/frontend-smoke.json`
  - `artifacts/ddd/frontend/playwright-smoke-results.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## iam-owner

- Queue order: 11
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety
- Ready batches: none
- Blocked batches: p1-rollback-iam-owner
- Closure waves: none
- Commands: 3
- Env keys: 8
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 1
- First command: `node scripts/ddd-rollback-deferral-template.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Missing artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## job-owner

- Queue order: 12
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety, runtime-business-acceptance
- Ready batches: none
- Blocked batches: p1-business-e2e-job-owner, p1-rollback-job-owner
- Closure waves: none
- Commands: 4
- Env keys: 13
- Missing artifacts: 2
- Content blockers: 0
- Stop reasons: 2
- First command: `node scripts/ddd-job-e2e-smoke.mjs`
- Env key names:
  - `BASE_URL`
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
  - `DEPLOY_CHECK_BASE_URL`
  - `LUMIRA_BASE_URL`
  - `LUMIRA_JOB_INTERNAL_TOKEN`
- Missing artifacts:
  - `artifacts/ddd/jobs/job-e2e-smoke.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## localization-owner

- Queue order: 13
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety
- Ready batches: none
- Blocked batches: p1-rollback-localization-owner
- Closure waves: none
- Commands: 3
- Env keys: 8
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 1
- First command: `node scripts/ddd-rollback-deferral-template.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Missing artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## message-owner

- Queue order: 14
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety
- Ready batches: none
- Blocked batches: p1-rollback-message-owner
- Closure waves: none
- Commands: 3
- Env keys: 8
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 1
- First command: `node scripts/ddd-rollback-deferral-template.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Missing artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## platform-owner

- Queue order: 15
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety
- Ready batches: none
- Blocked batches: p1-rollback-platform-owner
- Closure waves: none
- Commands: 3
- Env keys: 8
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 1
- First command: `node scripts/ddd-rollback-deferral-template.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Missing artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## plugin-owner

- Queue order: 16
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety
- Ready batches: none
- Blocked batches: p1-rollback-plugin-owner
- Closure waves: none
- Commands: 3
- Env keys: 8
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 1
- First command: `node scripts/ddd-rollback-deferral-template.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Missing artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## release-owner

- Queue order: 17
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: evidence-integrity
- Ready batches: none
- Blocked batches: p3-orchestrator-release-owner
- Closure waves: none
- Commands: 2
- Env keys: 1
- Missing artifacts: 3
- Content blockers: 0
- Stop reasons: 1
- First command: `node scripts/ddd-release-evidence-orchestrator.mjs`
- Env key names:
  - `DDD_RELEASE_EVIDENCE_STRICT`
- Missing artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## release-performance

- Queue order: 18
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: none
- Ready batches: none
- Blocked batches: none
- Closure waves: none
- Commands: 8
- Env keys: 12
- Missing artifacts: 0
- Content blockers: 0
- Stop reasons: 1
- First command: `DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh`
- Env key names:
  - `BASE_URL`
  - `DDD_AUTH_PASSWORD`
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE`
  - `DDD_AUTH_PERF_ENVIRONMENT`
  - `DDD_AUTH_USERNAME`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DEPLOY_CHECK_BASE_URL`
  - `LUMIRA_BASE_URL`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

