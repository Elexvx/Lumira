# DDD Cutover Owner Matrix

Generated at: 2026-06-17T08:13:17.325Z
Status: ADVISORY
Recommendation: NO_GO_STRICT
No auto waivers: true
releaseEnvFileCutoverSafe: false
Owner count: 17
Blocked owners: 17

## payment-owner

- Blocked items: 3
- Total items: 3
- Ready batches: p0-release-config-payment-owner
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
- [BLOCKED] release-environment: Completed release env file and config matrix are valid.
  - Pending items: 65
  - Lanes: environment
  - Batches: p0-release-config-payment-owner
  - Env check groups: PAYMENT_PUBLIC_BASE_URL=PAYMENT_PUBLIC_BASE_URL
  - Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- [BLOCKED] rollback-safety: Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance.
  - Pending items: 10
  - Lanes: rollback-safety
  - Batches: p1-rollback-payment-owner
  - Env check groups: DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT
  - Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- [BLOCKED] runtime-business-acceptance: AI, frontend, file, job, and payment acceptance evidence is complete.
  - Pending items: 7
  - Lanes: runtime-acceptance, frontend-acceptance, business-acceptance
  - Batches: p1-business-e2e-payment-owner
  - Env check groups: BASE_URL=BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL, PAYMENT_PUBLIC_BASE_URL=PAYMENT_PUBLIC_BASE_URL
  - Expected artifacts: artifacts/ddd/payment/payment-webhook-e2e.json

## ai-owner

- Blocked items: 2
- Total items: 2
- Ready batches: p0-release-config-ai-owner
- Blocked batches: p1-rollback-ai-owner
- [BLOCKED] release-environment: Completed release env file and config matrix are valid.
  - Pending items: 65
  - Lanes: environment
  - Batches: p0-release-config-ai-owner
  - Env check groups: LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL=LUMIRA_AI_OWNER_FILE_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL=LUMIRA_AI_OWNER_IAM_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN=LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN|LUMIRA_AI_OWNER_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL=LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL|LUMIRA_AI_OWNER_PLATFORM_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=LUMIRA_AI_PROVIDER_API_KEY|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL=LUMIRA_AI_PROVIDER_BASE_URL|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN
  - Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- [BLOCKED] rollback-safety: Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance.
  - Pending items: 10
  - Lanes: rollback-safety
  - Batches: p1-rollback-ai-owner
  - Env check groups: DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT
  - Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

## database

- Blocked items: 2
- Total items: 2
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- [BLOCKED] database-performance: Fresh production-equivalent EXPLAIN evidence has no scan/index blockers.
  - Pending items: 6
  - Lanes: database-performance
  - Batches: p2-explain-database
  - Env check groups: DB_PASSWORD=DB_PASSWORD|MYSQL_PASSWORD|SPRING_DATASOURCE_PASSWORD, DB_USERNAME=DB_USERNAME|MYSQL_USER|SPRING_DATASOURCE_USERNAME, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE=DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR=DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT=DDD_EXPLAIN_ENVIRONMENT, DDD_EXPLAIN_STRICT=DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, MYSQL_CLI=MYSQL_CLI, MYSQL_DATABASE=MYSQL_DATABASE, MYSQL_HOST=MYSQL_HOST, MYSQL_PORT=MYSQL_PORT
  - Expected artifacts: artifacts/ddd/release/explain-gate-report.json, tmp/ddd-explain/*.json
- [BLOCKED] evidence-integrity: Evidence manifest and final orchestrator strict rerun are clean.
  - Pending items: 3
  - Lanes: evidence-integrity, final-verification
  - Batches: p3-orchestrator-database
  - Env check groups: DDD_MIGRATION_FRESH_DB_EVIDENCE=DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED=DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_EVIDENCE=DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED=DDD_MIGRATION_UPGRADE_DB_VALIDATED
  - Expected artifacts: artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/readiness-summary.json, artifacts/ddd/release/release-evidence-gate.json

## file-owner

- Blocked items: 2
- Total items: 2
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-rollback-file-owner
- [BLOCKED] rollback-safety: Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance.
  - Pending items: 10
  - Lanes: rollback-safety
  - Batches: p1-rollback-file-owner
  - Env check groups: DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT
  - Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- [BLOCKED] runtime-business-acceptance: AI, frontend, file, job, and payment acceptance evidence is complete.
  - Pending items: 7
  - Lanes: runtime-acceptance, frontend-acceptance, business-acceptance
  - Batches: p1-business-e2e-file-owner
  - Env check groups: BASE_URL=BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL, SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN, UPLOAD_STORAGE_ROOT=LUMIRA_UPLOAD_STORAGE_ROOT|UPLOAD_STORAGE_ROOT
  - Expected artifacts: artifacts/ddd/file/file-processing-e2e.json

## frontend

- Blocked items: 2
- Total items: 2
- Ready batches: none
- Blocked batches: p1-frontend-smoke-frontend, p3-orchestrator-frontend
- [BLOCKED] evidence-integrity: Evidence manifest and final orchestrator strict rerun are clean.
  - Pending items: 3
  - Lanes: evidence-integrity, final-verification
  - Batches: p3-orchestrator-frontend
  - Env check groups: PLAYWRIGHT_BASE_URL=FRONTEND_BASE_URL|PLAYWRIGHT_BASE_URL
  - Expected artifacts: artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/readiness-summary.json, artifacts/ddd/release/release-evidence-gate.json
- [BLOCKED] runtime-business-acceptance: AI, frontend, file, job, and payment acceptance evidence is complete.
  - Pending items: 7
  - Lanes: runtime-acceptance, frontend-acceptance, business-acceptance
  - Batches: p1-frontend-smoke-frontend
  - Env check groups: DDD_FRONTEND_EXPECT_DEPLOYED=DDD_FRONTEND_EXPECT_DEPLOYED
  - Expected artifacts: artifacts/ddd/frontend/frontend-smoke.json, artifacts/ddd/frontend/playwright-smoke-results.json

## job-owner

- Blocked items: 2
- Total items: 2
- Ready batches: none
- Blocked batches: p1-business-e2e-job-owner, p1-rollback-job-owner
- [BLOCKED] rollback-safety: Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance.
  - Pending items: 10
  - Lanes: rollback-safety
  - Batches: p1-rollback-job-owner
  - Env check groups: DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT
  - Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- [BLOCKED] runtime-business-acceptance: AI, frontend, file, job, and payment acceptance evidence is complete.
  - Pending items: 7
  - Lanes: runtime-acceptance, frontend-acceptance, business-acceptance
  - Batches: p1-business-e2e-job-owner
  - Env check groups: BASE_URL=BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL, SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN
  - Expected artifacts: artifacts/ddd/jobs/job-e2e-smoke.json

## release-infra

- Blocked items: 2
- Total items: 2
- Ready batches: p0-docker-release-infra, p0-release-config-release-infra, p0-release-env-lint-release-infra
- Blocked batches: none
- [BLOCKED] deployable-images: Deployable backend/frontend images are built and inspected.
  - Pending items: 4
  - Lanes: deployable-image
  - Batches: p0-docker-release-infra
  - Env check groups: DDD_DOCKER_BUILD_STRICT=DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND=DDD_DOCKER_COMMAND
  - Expected artifacts: artifacts/ddd/build/docker-image-evidence.json
- [BLOCKED] release-environment: Completed release env file and config matrix are valid.
  - Pending items: 65
  - Lanes: environment
  - Batches: p0-release-env-lint-release-infra, p0-release-config-release-infra
  - Env check groups: AI_SERVICE_BASE_URL=AI_SERVICE_BASE_URL|LUMIRA_AI_BASE_URL|LUMIRA_AI_SERVICE_BASE_URL, AUTH_SERVICE_BASE_URL=AUTH_SERVICE_BASE_URL|LUMIRA_AUTH_SERVICE_BASE_URL, BASE_URL=BASE_URL, CORS_ALLOWED_ORIGIN_PATTERNS=CORS_ALLOWED_ORIGIN_PATTERNS|SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS, DB_PASSWORD=DB_PASSWORD|MYSQL_PASSWORD|SPRING_DATASOURCE_PASSWORD, DB_URL=DB_URL|SPRING_DATASOURCE_URL, DB_USERNAME=DB_USERNAME|MYSQL_USER|SPRING_DATASOURCE_USERNAME, DDD_AUTH_PASSWORD=DDD_AUTH_PASSWORD, DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT=DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT=DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE=DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE, DDD_AUTH_PERF_ENVIRONMENT=DDD_AUTH_PERF_ENVIRONMENT, DDD_AUTH_USERNAME=DDD_AUTH_USERNAME, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DDD_DEPLOYMENT_EVIDENCE=DDD_DEPLOYMENT_EVIDENCE, DDD_EXPLAIN_DATABASE=DDD_EXPLAIN_DATABASE, DDD_FRONTEND_DEPLOYMENT_EVIDENCE=DDD_FRONTEND_DEPLOYMENT_EVIDENCE, DDD_MIGRATION_COMPLETED_AT=DDD_MIGRATION_COMPLETED_AT, DDD_MIGRATION_FRESH_DB_EVIDENCE=DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED=DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_OPERATOR=DDD_MIGRATION_OPERATOR, DDD_MIGRATION_UPGRADE_DB_EVIDENCE=DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED=DDD_MIGRATION_UPGRADE_DB_VALIDATED, FIELD_SECRET=FIELD_SECRET|SAAS_SECURITY_FIELD_SECRET, FILE_SERVICE_BASE_URL=FILE_SERVICE_BASE_URL|LUMIRA_FILE_SERVICE_BASE_URL, JOB_EXECUTOR_BASE_URL=JOB_EXECUTOR_BASE_URL|LUMIRA_JOB_EXECUTOR_BASE_URL, JWT_SECRET=JWT_SECRET|SAAS_SECURITY_JWT_SECRET, LOCALIZATION_SERVICE_BASE_URL=LOCALIZATION_SERVICE_BASE_URL|LUMIRA_LOCALIZATION_SERVICE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL=LUMIRA_AI_OWNER_FILE_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL=LUMIRA_AI_OWNER_IAM_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN=LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN|LUMIRA_AI_OWNER_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL=LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL|LUMIRA_AI_OWNER_PLATFORM_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=LUMIRA_AI_PROVIDER_API_KEY|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL=LUMIRA_AI_PROVIDER_BASE_URL|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL, MESSAGE_SERVICE_BASE_URL=LUMIRA_MESSAGE_SERVICE_BASE_URL|MESSAGE_SERVICE_BASE_URL, MYSQL_DATABASE=MYSQL_DATABASE, MYSQL_HOST=MYSQL_HOST, MYSQL_PORT=MYSQL_PORT, PAYMENT_PUBLIC_BASE_URL=PAYMENT_PUBLIC_BASE_URL, PAYMENT_SERVICE_BASE_URL=LUMIRA_PAYMENT_SERVICE_BASE_URL|PAYMENT_SERVICE_BASE_URL, PLAYWRIGHT_BASE_URL=FRONTEND_BASE_URL|PLAYWRIGHT_BASE_URL, PLUGIN_SERVICE_BASE_URL=LUMIRA_PLUGIN_SERVICE_BASE_URL|PLUGIN_SERVICE_BASE_URL, REDIS_HOST=REDIS_HOST|SPRING_DATA_REDIS_HOST, SAAS_EVENT_REDIS_STREAM_KEY=LUMIRA_EVENT_REDIS_STREAM_KEY|SAAS_EVENT_REDIS_STREAM_KEY, SAAS_JOB_BACKEND_BASE_URL=LUMIRA_JOB_BACKEND_BASE_URL|SAAS_JOB_BACKEND_BASE_URL, SAAS_JOB_FILE_SERVICE_BASE_URL=LUMIRA_JOB_FILE_SERVICE_BASE_URL|SAAS_JOB_FILE_SERVICE_BASE_URL, SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN, SAAS_JOB_MESSAGE_SERVICE_BASE_URL=LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL|SAAS_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL=LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL|SAAS_JOB_PAYMENT_SERVICE_BASE_URL, SAAS_JOB_PLUGIN_SERVICE_BASE_URL=LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL|SAAS_JOB_PLUGIN_SERVICE_BASE_URL, SYSTEM_SERVICE_BASE_URL=LUMIRA_SYSTEM_SERVICE_BASE_URL|SYSTEM_SERVICE_BASE_URL, XXL_JOB_ACCESS_TOKEN=LUMIRA_XXL_JOB_ACCESS_TOKEN|XXL_JOB_ACCESS_TOKEN|XXL_JOB_ADMIN_ACCESS_TOKEN, XXL_JOB_ADMIN_ADDRESSES=LUMIRA_XXL_JOB_ADMIN_ADDRESSES|XXL_JOB_ADMIN_ADDRESSES
  - Expected artifacts: artifacts/ddd/config/release-config-evidence.json, artifacts/ddd/release/release-env-lint.json

## ai

- Blocked items: 1
- Total items: 1
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai
- [BLOCKED] runtime-business-acceptance: AI, frontend, file, job, and payment acceptance evidence is complete.
  - Pending items: 7
  - Lanes: runtime-acceptance, frontend-acceptance, business-acceptance
  - Batches: p1-ai-runtime-ai
  - Env check groups: AI_SERVICE_BASE_URL=AI_SERVICE_BASE_URL|LUMIRA_AI_BASE_URL|LUMIRA_AI_SERVICE_BASE_URL, BASE_URL=BASE_URL, DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE, DDD_AI_EXPECT_PROVIDER_REMOTE=DDD_AI_EXPECT_PROVIDER_REMOTE, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL=LUMIRA_AI_OWNER_FILE_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL=LUMIRA_AI_OWNER_IAM_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL=LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL|LUMIRA_AI_OWNER_PLATFORM_BASE_URL, LUMIRA_AI_PROVIDER=LUMIRA_AI_PROVIDER, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=LUMIRA_AI_PROVIDER_API_KEY|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL=LUMIRA_AI_PROVIDER_BASE_URL|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL
  - Expected artifacts: artifacts/ddd/ai/ai-runtime-drill.json

## auth-owner

- Blocked items: 1
- Total items: 1
- Ready batches: none
- Blocked batches: p1-rollback-auth-owner
- [BLOCKED] rollback-safety: Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance.
  - Pending items: 10
  - Lanes: rollback-safety
  - Batches: p1-rollback-auth-owner
  - Env check groups: DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT
  - Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

## iam-owner

- Blocked items: 1
- Total items: 1
- Ready batches: none
- Blocked batches: p1-rollback-iam-owner
- [BLOCKED] rollback-safety: Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance.
  - Pending items: 10
  - Lanes: rollback-safety
  - Batches: p1-rollback-iam-owner
  - Env check groups: DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT
  - Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

## localization-owner

- Blocked items: 1
- Total items: 1
- Ready batches: none
- Blocked batches: p1-rollback-localization-owner
- [BLOCKED] rollback-safety: Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance.
  - Pending items: 10
  - Lanes: rollback-safety
  - Batches: p1-rollback-localization-owner
  - Env check groups: DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT
  - Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

## message-owner

- Blocked items: 1
- Total items: 1
- Ready batches: none
- Blocked batches: p1-rollback-message-owner
- [BLOCKED] rollback-safety: Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance.
  - Pending items: 10
  - Lanes: rollback-safety
  - Batches: p1-rollback-message-owner
  - Env check groups: DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT
  - Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

## platform-events

- Blocked items: 1
- Total items: 1
- Ready batches: p0-release-config-platform-events
- Blocked batches: none
- [BLOCKED] release-environment: Completed release env file and config matrix are valid.
  - Pending items: 65
  - Lanes: environment
  - Batches: p0-release-config-platform-events
  - Env check groups: SAAS_EVENT_REDIS_STREAM_KEY=LUMIRA_EVENT_REDIS_STREAM_KEY|SAAS_EVENT_REDIS_STREAM_KEY, SAAS_JOB_BACKEND_BASE_URL=LUMIRA_JOB_BACKEND_BASE_URL|SAAS_JOB_BACKEND_BASE_URL, SAAS_JOB_FILE_SERVICE_BASE_URL=LUMIRA_JOB_FILE_SERVICE_BASE_URL|SAAS_JOB_FILE_SERVICE_BASE_URL, SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN, SAAS_JOB_MESSAGE_SERVICE_BASE_URL=LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL|SAAS_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL=LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL|SAAS_JOB_PAYMENT_SERVICE_BASE_URL, SAAS_JOB_PLUGIN_SERVICE_BASE_URL=LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL|SAAS_JOB_PLUGIN_SERVICE_BASE_URL, XXL_JOB_ACCESS_TOKEN=LUMIRA_XXL_JOB_ACCESS_TOKEN|XXL_JOB_ACCESS_TOKEN|XXL_JOB_ADMIN_ACCESS_TOKEN, XXL_JOB_ADMIN_ADDRESSES=LUMIRA_XXL_JOB_ADMIN_ADDRESSES|XXL_JOB_ADMIN_ADDRESSES
  - Expected artifacts: artifacts/ddd/config/release-config-evidence.json

## platform-owner

- Blocked items: 1
- Total items: 1
- Ready batches: none
- Blocked batches: p1-rollback-platform-owner
- [BLOCKED] rollback-safety: Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance.
  - Pending items: 10
  - Lanes: rollback-safety
  - Batches: p1-rollback-platform-owner
  - Env check groups: DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT
  - Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

## platform-owners

- Blocked items: 1
- Total items: 1
- Ready batches: p0-release-config-platform-owners
- Blocked batches: none
- [BLOCKED] release-environment: Completed release env file and config matrix are valid.
  - Pending items: 65
  - Lanes: environment
  - Batches: p0-release-config-platform-owners
  - Env check groups: AI_SERVICE_BASE_URL=AI_SERVICE_BASE_URL|LUMIRA_AI_BASE_URL|LUMIRA_AI_SERVICE_BASE_URL, AUTH_SERVICE_BASE_URL=AUTH_SERVICE_BASE_URL|LUMIRA_AUTH_SERVICE_BASE_URL, FILE_SERVICE_BASE_URL=FILE_SERVICE_BASE_URL|LUMIRA_FILE_SERVICE_BASE_URL, JOB_EXECUTOR_BASE_URL=JOB_EXECUTOR_BASE_URL|LUMIRA_JOB_EXECUTOR_BASE_URL, LOCALIZATION_SERVICE_BASE_URL=LOCALIZATION_SERVICE_BASE_URL|LUMIRA_LOCALIZATION_SERVICE_BASE_URL, MESSAGE_SERVICE_BASE_URL=LUMIRA_MESSAGE_SERVICE_BASE_URL|MESSAGE_SERVICE_BASE_URL, PAYMENT_SERVICE_BASE_URL=LUMIRA_PAYMENT_SERVICE_BASE_URL|PAYMENT_SERVICE_BASE_URL, PLUGIN_SERVICE_BASE_URL=LUMIRA_PLUGIN_SERVICE_BASE_URL|PLUGIN_SERVICE_BASE_URL, SYSTEM_SERVICE_BASE_URL=LUMIRA_SYSTEM_SERVICE_BASE_URL|SYSTEM_SERVICE_BASE_URL
  - Expected artifacts: artifacts/ddd/config/release-config-evidence.json

## plugin-owner

- Blocked items: 1
- Total items: 1
- Ready batches: none
- Blocked batches: p1-rollback-plugin-owner
- [BLOCKED] rollback-safety: Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance.
  - Pending items: 10
  - Lanes: rollback-safety
  - Batches: p1-rollback-plugin-owner
  - Env check groups: DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT
  - Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

## release-owner

- Blocked items: 1
- Total items: 4
- Ready batches: none
- Blocked batches: p3-orchestrator-release-owner
- [BLOCKED] evidence-integrity: Evidence manifest and final orchestrator strict rerun are clean.
  - Pending items: 3
  - Lanes: evidence-integrity, final-verification
  - Batches: p3-orchestrator-release-owner
  - Env check groups: DDD_RELEASE_EVIDENCE_STRICT=DDD_RELEASE_EVIDENCE_STRICT
  - Expected artifacts: artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/readiness-summary.json, artifacts/ddd/release/release-evidence-gate.json
- [PASS] data-safety: Fresh and upgrade migrations are proven with runtime metadata.
  - Pending items: 0
  - Lanes: data-safety
  - Batches: none
- [PASS] production-equivalence: Runtime and performance evidence use HTTPS non-local production-equivalent endpoints.
  - Pending items: 0
  - Lanes: production-equivalence, performance
  - Batches: none
- [PASS] strict-release-gate: Strict release gate has zero blockers and no contract issues.
  - Pending items: 0
  - Lanes: none
  - Batches: none

