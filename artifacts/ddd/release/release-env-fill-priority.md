# DDD Release Env Fill Priority

Generated at: 2026-06-19T06:54:03.604Z
Status: NOT_READY
Release gate blockers: 94
Owners with unresolved keys: 18
Run now owners: 7
Waiting owners: 11
Unique unresolved template env keys: 59
Unresolved owner assignments: 143
Filled owner assignments: 29
Placeholder owner assignments: 114
Missing owner assignments: 0

## 1. release-infra

- Priority: RUN_NOW
- Unresolved env keys: 57
- Fill status: filled=2, placeholder=55, missing=0
- Ready batches: p0-docker-release-infra, p0-release-config-release-infra, p0-release-env-lint-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: none
- Fill keys:
  - `AI_SERVICE_BASE_URL` (placeholder)
  - `AUTH_SERVICE_BASE_URL` (placeholder)
  - `BASE_URL` (placeholder)
  - `CORS_ALLOWED_ORIGIN_PATTERNS` (placeholder)
  - `DB_PASSWORD` (placeholder)
  - `DB_URL` (placeholder)
  - `DB_USERNAME` (placeholder)
  - `DDD_AUTH_PASSWORD` (placeholder)
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY` (placeholder)
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT` (placeholder)
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT` (placeholder)
  - `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE` (placeholder)
  - `DDD_AUTH_PERF_ENVIRONMENT` (placeholder)
  - `DDD_AUTH_USERNAME` (placeholder)
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` (placeholder)
  - `DDD_DEPLOYMENT_EVIDENCE` (placeholder)
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_EXPLAIN_DATABASE` (placeholder)
  - `DDD_FRONTEND_DEPLOYMENT_EVIDENCE` (placeholder)
  - `DDD_MIGRATION_COMPLETED_AT` (placeholder)
  - `DDD_MIGRATION_FRESH_DB_EVIDENCE` (placeholder)
  - `DDD_MIGRATION_FRESH_DB_VALIDATED` (placeholder)
  - `DDD_MIGRATION_OPERATOR` (placeholder)
  - `DDD_MIGRATION_UPGRADE_DB_EVIDENCE` (placeholder)
  - `DDD_MIGRATION_UPGRADE_DB_VALIDATED` (placeholder)
  - `DDD_RELEASE_CANDIDATE` (filled)
  - `FIELD_SECRET` (placeholder)
  - `FILE_SERVICE_BASE_URL` (placeholder)
  - `JOB_EXECUTOR_BASE_URL` (placeholder)
  - `JWT_SECRET` (placeholder)
  - `LOCALIZATION_SERVICE_BASE_URL` (placeholder)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL` (placeholder)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL` (placeholder)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN` (placeholder)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL` (placeholder)
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY` (placeholder)
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL` (placeholder)
  - `LUMIRA_BASE_URL` (placeholder)
  - `MESSAGE_SERVICE_BASE_URL` (placeholder)
  - `MYSQL_DATABASE` (placeholder)
  - `MYSQL_HOST` (placeholder)
  - `MYSQL_PORT` (placeholder)
  - `PAYMENT_PUBLIC_BASE_URL` (placeholder)
  - `PAYMENT_SERVICE_BASE_URL` (placeholder)
  - `PLAYWRIGHT_BASE_URL` (placeholder)
  - `PLUGIN_SERVICE_BASE_URL` (placeholder)
  - `REDIS_HOST` (placeholder)
  - `SAAS_EVENT_REDIS_STREAM_KEY` (placeholder)
  - `SAAS_JOB_BACKEND_BASE_URL` (placeholder)
  - `SAAS_JOB_FILE_SERVICE_BASE_URL` (placeholder)
  - `SAAS_JOB_INTERNAL_TOKEN` (placeholder)
  - `SAAS_JOB_MESSAGE_SERVICE_BASE_URL` (placeholder)
  - `SAAS_JOB_PAYMENT_SERVICE_BASE_URL` (placeholder)
  - `SAAS_JOB_PLUGIN_SERVICE_BASE_URL` (placeholder)
  - `SYSTEM_SERVICE_BASE_URL` (placeholder)
  - `XXL_JOB_ACCESS_TOKEN` (placeholder)
  - `XXL_JOB_ADMIN_ADDRESSES` (placeholder)
- Rerun after fill:
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-runtime-readiness-smoke.mjs`

## 2. ai-owner

- Priority: RUN_NOW
- Unresolved env keys: 9
- Fill status: filled=2, placeholder=7, missing=0
- Ready batches: p0-release-config-ai-owner
- Blocked batches: p1-rollback-ai-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL` (placeholder)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL` (placeholder)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN` (placeholder)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL` (placeholder)
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY` (placeholder)
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL` (placeholder)
  - `SAAS_JOB_INTERNAL_TOKEN` (placeholder)
- Rerun after fill:
  - `node scripts/ddd-release-config-evidence.mjs`
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`

## 3. platform-events

- Priority: RUN_NOW
- Unresolved env keys: 9
- Fill status: filled=0, placeholder=9, missing=0
- Ready batches: p0-release-config-platform-events
- Blocked batches: none
- Fill keys:
  - `SAAS_EVENT_REDIS_STREAM_KEY` (placeholder)
  - `SAAS_JOB_BACKEND_BASE_URL` (placeholder)
  - `SAAS_JOB_FILE_SERVICE_BASE_URL` (placeholder)
  - `SAAS_JOB_INTERNAL_TOKEN` (placeholder)
  - `SAAS_JOB_MESSAGE_SERVICE_BASE_URL` (placeholder)
  - `SAAS_JOB_PAYMENT_SERVICE_BASE_URL` (placeholder)
  - `SAAS_JOB_PLUGIN_SERVICE_BASE_URL` (placeholder)
  - `XXL_JOB_ACCESS_TOKEN` (placeholder)
  - `XXL_JOB_ADMIN_ADDRESSES` (placeholder)
- Rerun after fill:
  - `node scripts/ddd-release-config-evidence.mjs`

## 4. platform-owners

- Priority: RUN_NOW
- Unresolved env keys: 9
- Fill status: filled=0, placeholder=9, missing=0
- Ready batches: p0-release-config-platform-owners
- Blocked batches: none
- Fill keys:
  - `AI_SERVICE_BASE_URL` (placeholder)
  - `AUTH_SERVICE_BASE_URL` (placeholder)
  - `FILE_SERVICE_BASE_URL` (placeholder)
  - `JOB_EXECUTOR_BASE_URL` (placeholder)
  - `LOCALIZATION_SERVICE_BASE_URL` (placeholder)
  - `MESSAGE_SERVICE_BASE_URL` (placeholder)
  - `PAYMENT_SERVICE_BASE_URL` (placeholder)
  - `PLUGIN_SERVICE_BASE_URL` (placeholder)
  - `SYSTEM_SERVICE_BASE_URL` (placeholder)
- Rerun after fill:
  - `node scripts/ddd-release-config-evidence.mjs`

## 5. payment-owner

- Priority: RUN_NOW
- Unresolved env keys: 6
- Fill status: filled=2, placeholder=4, missing=0
- Ready batches: p0-release-config-payment-owner
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
- Fill keys:
  - `BASE_URL` (placeholder)
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` (placeholder)
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
  - `LUMIRA_BASE_URL` (placeholder)
  - `PAYMENT_PUBLIC_BASE_URL` (placeholder)
- Rerun after fill:
  - `node scripts/ddd-release-config-evidence.mjs`
  - `node scripts/ddd-payment-webhook-e2e-smoke.mjs`
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`

## 6. release-performance

- Priority: RUN_NOW
- Unresolved env keys: 4
- Fill status: filled=1, placeholder=3, missing=0
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none
- Fill keys:
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY` (placeholder)
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT` (placeholder)
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT` (placeholder)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`

## 7. release-owner

- Priority: RUN_NOW
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: p0-manifest-release-owner
- Blocked batches: p3-orchestrator-release-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`

## 8. database

- Priority: WAITING
- Unresolved env keys: 12
- Fill status: filled=2, placeholder=10, missing=0
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Fill keys:
  - `DB_PASSWORD` (placeholder)
  - `DB_USERNAME` (placeholder)
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_EXPLAIN_DATABASE` (placeholder)
  - `DDD_MIGRATION_FRESH_DB_EVIDENCE` (placeholder)
  - `DDD_MIGRATION_FRESH_DB_VALIDATED` (placeholder)
  - `DDD_MIGRATION_UPGRADE_DB_EVIDENCE` (placeholder)
  - `DDD_MIGRATION_UPGRADE_DB_VALIDATED` (placeholder)
  - `DDD_RELEASE_CANDIDATE` (filled)
  - `MYSQL_DATABASE` (placeholder)
  - `MYSQL_HOST` (placeholder)
  - `MYSQL_PORT` (placeholder)
- Rerun after fill:
  - `node scripts/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`

## 9. ai

- Priority: WAITING
- Unresolved env keys: 9
- Fill status: filled=1, placeholder=8, missing=0
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai
- Fill keys:
  - `AI_SERVICE_BASE_URL` (placeholder)
  - `BASE_URL` (placeholder)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL` (placeholder)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL` (placeholder)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL` (placeholder)
  - `LUMIRA_AI_PROVIDER` (filled)
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY` (placeholder)
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL` (placeholder)
  - `LUMIRA_BASE_URL` (placeholder)
- Rerun after fill:
  - `node scripts/ddd-ai-runtime-drill.mjs`

## 10. file-owner

- Priority: WAITING
- Unresolved env keys: 7
- Fill status: filled=3, placeholder=4, missing=0
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-rollback-file-owner
- Fill keys:
  - `BASE_URL` (placeholder)
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` (placeholder)
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
  - `LUMIRA_BASE_URL` (placeholder)
  - `SAAS_JOB_INTERNAL_TOKEN` (placeholder)
  - `UPLOAD_STORAGE_ROOT` (filled)
- Rerun after fill:
  - `node scripts/ddd-file-processing-e2e-smoke.mjs`
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`

## 11. job-owner

- Priority: WAITING
- Unresolved env keys: 6
- Fill status: filled=2, placeholder=4, missing=0
- Ready batches: none
- Blocked batches: p1-business-e2e-job-owner, p1-rollback-job-owner
- Fill keys:
  - `BASE_URL` (placeholder)
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` (placeholder)
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
  - `LUMIRA_BASE_URL` (placeholder)
  - `SAAS_JOB_INTERNAL_TOKEN` (placeholder)
- Rerun after fill:
  - `node scripts/ddd-job-e2e-smoke.mjs`
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`

## 12. auth-owner

- Priority: WAITING
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-rollback-auth-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`

## 13. iam-owner

- Priority: WAITING
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-rollback-iam-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`

## 14. localization-owner

- Priority: WAITING
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-rollback-localization-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`

## 15. message-owner

- Priority: WAITING
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-rollback-message-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`

## 16. platform-owner

- Priority: WAITING
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-rollback-platform-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`

## 17. plugin-owner

- Priority: WAITING
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-rollback-plugin-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`

## 18. frontend

- Priority: WAITING
- Unresolved env keys: 1
- Fill status: filled=0, placeholder=1, missing=0
- Ready batches: none
- Blocked batches: p1-frontend-smoke-frontend, p3-orchestrator-frontend
- Fill keys:
  - `PLAYWRIGHT_BASE_URL` (placeholder)
- Rerun after fill:
  - `node scripts/ddd-frontend-playwright-smoke.mjs`
  - `node scripts/ddd-frontend-smoke-evidence.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`

