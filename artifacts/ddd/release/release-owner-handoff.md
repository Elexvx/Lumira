# DDD Release Owner Handoff

Generated at: 2026-06-19T06:54:03.604Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
Cutover allowed: false
Stop reasons: 9
releaseEnvFileCutoverSafe: false
Ready owners: 7
Blocked owners: 11

## Final Cutover Decision

- finalRecommendation: NO_GO_STRICT
- cutoverAllowed: false
- releaseEnvFileCutoverSafe: false
- gateBlockers: 94
- blockedCutoverItems: 8
- stopReasonCount: 9
- stopReasonCoverage: catalog-snapshot
- cutoverAuthority: final-go-no-go-gate
- requiresFinalGate: true
- source: artifacts/ddd/release/release-final-go-no-go.json
- enforceCommand: `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

### Current Stop Reasons

- cutover checklist blocked: database-performance
- cutover checklist blocked: deployable-images
- cutover checklist blocked: evidence-integrity
- cutover checklist blocked: production-equivalence
- cutover checklist blocked: release-environment
- cutover checklist blocked: rollback-safety
- cutover checklist blocked: runtime-business-acceptance
- cutover checklist blocked: strict-release-gate
- strict release gate blockers=94

## release-infra

- Status: READY
- Pending items: 24
- Ready batches: p0-docker-release-infra, p0-release-config-release-infra, p0-release-env-lint-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: none
- Blocked by: none
- Env keys: 60
- Expected artifacts: artifacts/ddd/build/docker-image-evidence.json, artifacts/ddd/config/release-config-evidence.json, artifacts/ddd/readiness/summary.json, artifacts/ddd/release/release-env-lint.json
- Commands:
  - list: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - envCheck: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - dryRun: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - execute: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Template env keys:
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
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_EXPLAIN_DATABASE`
  - `DDD_FRONTEND_DEPLOYMENT_EVIDENCE`
  - `DDD_MIGRATION_COMPLETED_AT`
  - `DDD_MIGRATION_FRESH_DB_EVIDENCE`
  - `DDD_MIGRATION_FRESH_DB_VALIDATED`
  - `DDD_MIGRATION_OPERATOR`
  - `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`
  - `DDD_MIGRATION_UPGRADE_DB_VALIDATED`
  - `DDD_RELEASE_CANDIDATE`
  - `FIELD_SECRET`
  - `FILE_SERVICE_BASE_URL`
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
  - `MYSQL_PORT`
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
  - `SYSTEM_SERVICE_BASE_URL`
  - `XXL_JOB_ACCESS_TOKEN`
  - `XXL_JOB_ADMIN_ADDRESSES`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Run the owner env-check command before collecting evidence.
  - Run the owner dry-run command to confirm batch scope.
  - Run the owner execute command in the production-equivalent release environment.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## platform-owners

- Status: READY
- Pending items: 18
- Ready batches: p0-release-config-platform-owners
- Blocked batches: none
- Blocked by: none
- Env keys: 9
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- Commands:
  - list: `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - envCheck: `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - dryRun: `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - execute: `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Template env keys:
  - `AI_SERVICE_BASE_URL`
  - `AUTH_SERVICE_BASE_URL`
  - `FILE_SERVICE_BASE_URL`
  - `JOB_EXECUTOR_BASE_URL`
  - `LOCALIZATION_SERVICE_BASE_URL`
  - `MESSAGE_SERVICE_BASE_URL`
  - `PAYMENT_SERVICE_BASE_URL`
  - `PLUGIN_SERVICE_BASE_URL`
  - `SYSTEM_SERVICE_BASE_URL`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Run the owner env-check command before collecting evidence.
  - Run the owner dry-run command to confirm batch scope.
  - Run the owner execute command in the production-equivalent release environment.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## platform-events

- Status: READY
- Pending items: 17
- Ready batches: p0-release-config-platform-events
- Blocked batches: none
- Blocked by: none
- Env keys: 9
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- Commands:
  - list: `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - envCheck: `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - dryRun: `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - execute: `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Template env keys:
  - `SAAS_EVENT_REDIS_STREAM_KEY`
  - `SAAS_JOB_BACKEND_BASE_URL`
  - `SAAS_JOB_FILE_SERVICE_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN`
  - `SAAS_JOB_MESSAGE_SERVICE_BASE_URL`
  - `SAAS_JOB_PAYMENT_SERVICE_BASE_URL`
  - `SAAS_JOB_PLUGIN_SERVICE_BASE_URL`
  - `XXL_JOB_ACCESS_TOKEN`
  - `XXL_JOB_ADMIN_ADDRESSES`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Run the owner env-check command before collecting evidence.
  - Run the owner dry-run command to confirm batch scope.
  - Run the owner execute command in the production-equivalent release environment.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## ai-owner

- Status: READY
- Pending items: 13
- Ready batches: p0-release-config-ai-owner
- Blocked batches: p1-rollback-ai-owner
- Blocked by: none
- Env keys: 15
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json, artifacts/ddd/rollback/rollback-drill.json
- Commands:
  - list: `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - envCheck: `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - dryRun: `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - execute: `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Run the owner env-check command before collecting evidence.
  - Run the owner dry-run command to confirm batch scope.
  - Run the owner execute command in the production-equivalent release environment.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## release-performance

- Status: READY
- Pending items: 9
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none
- Blocked by: none
- Env keys: 4
- Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json, artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json, artifacts/ddd/performance/authenticated-runtime-baseline.json
- Commands:
  - list: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - envCheck: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - dryRun: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - execute: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Template env keys:
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_RELEASE_CANDIDATE`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Run the owner env-check command before collecting evidence.
  - Run the owner dry-run command to confirm batch scope.
  - Run the owner execute command in the production-equivalent release environment.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## payment-owner

- Status: READY
- Pending items: 4
- Ready batches: p0-release-config-payment-owner
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
- Blocked by: none
- Env keys: 12
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json, artifacts/ddd/payment/payment-webhook-e2e.json, artifacts/ddd/rollback/rollback-drill.json
- Commands:
  - list: `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - envCheck: `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - dryRun: `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - execute: `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Template env keys:
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
  - `LUMIRA_BASE_URL`
  - `PAYMENT_PUBLIC_BASE_URL`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Run the owner env-check command before collecting evidence.
  - Run the owner dry-run command to confirm batch scope.
  - Run the owner execute command in the production-equivalent release environment.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## release-owner

- Status: READY
- Pending items: 2
- Ready batches: p0-manifest-release-owner
- Blocked batches: p3-orchestrator-release-owner
- Blocked by: p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-frontend-smoke-frontend, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Env keys: 5
- Expected artifacts: artifacts/ddd/release/evidence-manifest.json, artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/readiness-summary.json, artifacts/ddd/release/release-evidence-gate.json
- Commands:
  - list: `DDD_RELEASE_OWNER=release-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - envCheck: `DDD_RELEASE_OWNER=release-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - dryRun: `DDD_RELEASE_OWNER=release-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - execute: `DDD_RELEASE_OWNER=release-owner DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_RELEASE_EVIDENCE_STRICT`
  - `DDD_RELEASE_MANIFEST_STRICT`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Run the owner env-check command before collecting evidence.
  - Run the owner dry-run command to confirm batch scope.
  - Run the owner execute command in the production-equivalent release environment.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## database

- Status: BLOCKED
- Pending items: 9
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Blocked by: p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-frontend-smoke-frontend, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Env keys: 16
- Expected artifacts: artifacts/ddd/release/explain-gate-report.json, artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/readiness-summary.json, artifacts/ddd/release/release-evidence-gate.json, tmp/ddd-explain/*.json
- Template env keys:
  - `DB_PASSWORD`
  - `DB_USERNAME`
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
  - `MYSQL_PORT`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## ai

- Status: BLOCKED
- Pending items: 3
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai
- Blocked by: none
- Env keys: 11
- Expected artifacts: artifacts/ddd/ai/ai-runtime-drill.json
- Template env keys:
  - `AI_SERVICE_BASE_URL`
  - `BASE_URL`
  - `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE`
  - `DDD_AI_EXPECT_PROVIDER_REMOTE`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL`
  - `LUMIRA_AI_PROVIDER`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`
  - `LUMIRA_BASE_URL`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## file-owner

- Status: BLOCKED
- Pending items: 2
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-rollback-file-owner
- Blocked by: none
- Env keys: 13
- Expected artifacts: artifacts/ddd/file/file-processing-e2e.json, artifacts/ddd/rollback/rollback-drill.json
- Template env keys:
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
  - `LUMIRA_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN`
  - `UPLOAD_STORAGE_ROOT`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## frontend

- Status: BLOCKED
- Pending items: 2
- Ready batches: none
- Blocked batches: p1-frontend-smoke-frontend, p3-orchestrator-frontend
- Blocked by: p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-frontend-smoke-frontend, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Env keys: 2
- Expected artifacts: artifacts/ddd/frontend/frontend-smoke.json, artifacts/ddd/frontend/playwright-smoke-results.json, artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/readiness-summary.json, artifacts/ddd/release/release-evidence-gate.json
- Template env keys:
  - `DDD_FRONTEND_EXPECT_DEPLOYED`
  - `PLAYWRIGHT_BASE_URL`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## job-owner

- Status: BLOCKED
- Pending items: 2
- Ready batches: none
- Blocked batches: p1-business-e2e-job-owner, p1-rollback-job-owner
- Blocked by: none
- Env keys: 12
- Expected artifacts: artifacts/ddd/jobs/job-e2e-smoke.json, artifacts/ddd/rollback/rollback-drill.json
- Template env keys:
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
  - `LUMIRA_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## auth-owner

- Status: BLOCKED
- Pending items: 1
- Ready batches: none
- Blocked batches: p1-rollback-auth-owner
- Blocked by: none
- Env keys: 8
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## iam-owner

- Status: BLOCKED
- Pending items: 1
- Ready batches: none
- Blocked batches: p1-rollback-iam-owner
- Blocked by: none
- Env keys: 8
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## localization-owner

- Status: BLOCKED
- Pending items: 1
- Ready batches: none
- Blocked batches: p1-rollback-localization-owner
- Blocked by: none
- Env keys: 8
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## message-owner

- Status: BLOCKED
- Pending items: 1
- Ready batches: none
- Blocked batches: p1-rollback-message-owner
- Blocked by: none
- Env keys: 8
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## platform-owner

- Status: BLOCKED
- Pending items: 1
- Ready batches: none
- Blocked batches: p1-rollback-platform-owner
- Blocked by: none
- Env keys: 8
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

## plugin-owner

- Status: BLOCKED
- Pending items: 1
- Ready batches: none
- Blocked batches: p1-rollback-plugin-owner
- Blocked by: none
- Env keys: 8
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Handoff checklist:
  - Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values.
  - Archive or refresh every expected artifact listed for this owner.
  - Confirm every exit criterion before the next dependent batch starts.

