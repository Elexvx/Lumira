# DDD Release Execution Queue

Generated at: 2026-06-17T08:13:17.325Z
Status: ADVISORY
Release gate mode: advisory
Release gate blockers: 0
Ready batches: 7
Blocked batches: 19
Next priority: P0

## Safety Signals

- releaseEnvFileCutoverSafe: false
- releaseEnvFile: ready=false status=FAIL inputKind=release-env-file envFilePresent=true
  - securityChecked=true permissionSafe=true mode=600 requiredMode=600 reason=env-file permissionCheckSkipped=false
  - pendingActions=release-env-lint-status, release-env-lint-placeholders

## Ready Now

### p0-release-env-lint-release-infra

- Scope: P0 release-env-lint -> release-infra
- Pending items: 2
- Env keys: 55 keys
  - AI_SERVICE_BASE_URL, AUTH_SERVICE_BASE_URL, BASE_URL, CORS_ALLOWED_ORIGIN_PATTERNS
  - DB_PASSWORD, DB_URL, DB_USERNAME, DDD_AUTH_PASSWORD
  - DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE
  - DDD_AUTH_PERF_ENVIRONMENT, DDD_AUTH_USERNAME, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DDD_DEPLOYMENT_EVIDENCE
  - DDD_EXPLAIN_DATABASE, DDD_FRONTEND_DEPLOYMENT_EVIDENCE, DDD_MIGRATION_COMPLETED_AT, DDD_MIGRATION_FRESH_DB_EVIDENCE
  - DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_OPERATOR, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED
  - FIELD_SECRET, FILE_SERVICE_BASE_URL, JOB_EXECUTOR_BASE_URL, JWT_SECRET
  - LOCALIZATION_SERVICE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN
  - LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_BASE_URL
  - MESSAGE_SERVICE_BASE_URL, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PORT
  - PAYMENT_PUBLIC_BASE_URL, PAYMENT_SERVICE_BASE_URL, PLAYWRIGHT_BASE_URL, PLUGIN_SERVICE_BASE_URL
  - REDIS_HOST, SAAS_EVENT_REDIS_STREAM_KEY, SAAS_JOB_BACKEND_BASE_URL, SAAS_JOB_FILE_SERVICE_BASE_URL
  - SAAS_JOB_INTERNAL_TOKEN, SAAS_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL, SAAS_JOB_PLUGIN_SERVICE_BASE_URL
  - SYSTEM_SERVICE_BASE_URL, XXL_JOB_ACCESS_TOKEN, XXL_JOB_ADMIN_ADDRESSES
- Env check groups: 55 groups
  - `AI_SERVICE_BASE_URL=AI_SERVICE_BASE_URL|LUMIRA_AI_BASE_URL|LUMIRA_AI_SERVICE_BASE_URL`
  - `AUTH_SERVICE_BASE_URL=AUTH_SERVICE_BASE_URL|LUMIRA_AUTH_SERVICE_BASE_URL`
  - `BASE_URL=BASE_URL`
  - `CORS_ALLOWED_ORIGIN_PATTERNS=CORS_ALLOWED_ORIGIN_PATTERNS|SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS`
  - `DB_PASSWORD=DB_PASSWORD|MYSQL_PASSWORD|SPRING_DATASOURCE_PASSWORD`
  - `DB_URL=DB_URL|SPRING_DATASOURCE_URL`
  - `DB_USERNAME=DB_USERNAME|MYSQL_USER|SPRING_DATASOURCE_USERNAME`
  - `DDD_AUTH_PASSWORD=DDD_AUTH_PASSWORD`
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT=DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT=DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE=DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE`
  - `DDD_AUTH_PERF_ENVIRONMENT=DDD_AUTH_PERF_ENVIRONMENT`
  - `DDD_AUTH_USERNAME=DDD_AUTH_USERNAME`
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
  - `DDD_DEPLOYMENT_EVIDENCE=DDD_DEPLOYMENT_EVIDENCE`
  - `DDD_EXPLAIN_DATABASE=DDD_EXPLAIN_DATABASE`
  - `DDD_FRONTEND_DEPLOYMENT_EVIDENCE=DDD_FRONTEND_DEPLOYMENT_EVIDENCE`
  - `DDD_MIGRATION_COMPLETED_AT=DDD_MIGRATION_COMPLETED_AT`
  - `DDD_MIGRATION_FRESH_DB_EVIDENCE=DDD_MIGRATION_FRESH_DB_EVIDENCE`
  - `DDD_MIGRATION_FRESH_DB_VALIDATED=DDD_MIGRATION_FRESH_DB_VALIDATED`
  - `DDD_MIGRATION_OPERATOR=DDD_MIGRATION_OPERATOR`
  - `DDD_MIGRATION_UPGRADE_DB_EVIDENCE=DDD_MIGRATION_UPGRADE_DB_EVIDENCE`
  - `DDD_MIGRATION_UPGRADE_DB_VALIDATED=DDD_MIGRATION_UPGRADE_DB_VALIDATED`
  - `FIELD_SECRET=FIELD_SECRET|SAAS_SECURITY_FIELD_SECRET`
  - `FILE_SERVICE_BASE_URL=FILE_SERVICE_BASE_URL|LUMIRA_FILE_SERVICE_BASE_URL`
  - `JOB_EXECUTOR_BASE_URL=JOB_EXECUTOR_BASE_URL|LUMIRA_JOB_EXECUTOR_BASE_URL`
  - `JWT_SECRET=JWT_SECRET|SAAS_SECURITY_JWT_SECRET`
  - `LOCALIZATION_SERVICE_BASE_URL=LOCALIZATION_SERVICE_BASE_URL|LUMIRA_LOCALIZATION_SERVICE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL=LUMIRA_AI_OWNER_FILE_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL=LUMIRA_AI_OWNER_IAM_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN=LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN|LUMIRA_AI_OWNER_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL=LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL|LUMIRA_AI_OWNER_PLATFORM_BASE_URL`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=LUMIRA_AI_PROVIDER_API_KEY|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL=LUMIRA_AI_PROVIDER_BASE_URL|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
  - `MESSAGE_SERVICE_BASE_URL=LUMIRA_MESSAGE_SERVICE_BASE_URL|MESSAGE_SERVICE_BASE_URL`
  - `MYSQL_DATABASE=MYSQL_DATABASE`
  - `MYSQL_HOST=MYSQL_HOST`
  - `MYSQL_PORT=MYSQL_PORT`
  - `PAYMENT_PUBLIC_BASE_URL=PAYMENT_PUBLIC_BASE_URL`
  - `PAYMENT_SERVICE_BASE_URL=LUMIRA_PAYMENT_SERVICE_BASE_URL|PAYMENT_SERVICE_BASE_URL`
  - `PLAYWRIGHT_BASE_URL=FRONTEND_BASE_URL|PLAYWRIGHT_BASE_URL`
  - `PLUGIN_SERVICE_BASE_URL=LUMIRA_PLUGIN_SERVICE_BASE_URL|PLUGIN_SERVICE_BASE_URL`
  - `REDIS_HOST=REDIS_HOST|SPRING_DATA_REDIS_HOST`
  - `SAAS_EVENT_REDIS_STREAM_KEY=LUMIRA_EVENT_REDIS_STREAM_KEY|SAAS_EVENT_REDIS_STREAM_KEY`
  - `SAAS_JOB_BACKEND_BASE_URL=LUMIRA_JOB_BACKEND_BASE_URL|SAAS_JOB_BACKEND_BASE_URL`
  - `SAAS_JOB_FILE_SERVICE_BASE_URL=LUMIRA_JOB_FILE_SERVICE_BASE_URL|SAAS_JOB_FILE_SERVICE_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN`
  - `SAAS_JOB_MESSAGE_SERVICE_BASE_URL=LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL|SAAS_JOB_MESSAGE_SERVICE_BASE_URL`
  - `SAAS_JOB_PAYMENT_SERVICE_BASE_URL=LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL|SAAS_JOB_PAYMENT_SERVICE_BASE_URL`
  - `SAAS_JOB_PLUGIN_SERVICE_BASE_URL=LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL|SAAS_JOB_PLUGIN_SERVICE_BASE_URL`
  - `SYSTEM_SERVICE_BASE_URL=LUMIRA_SYSTEM_SERVICE_BASE_URL|SYSTEM_SERVICE_BASE_URL`
  - `XXL_JOB_ACCESS_TOKEN=LUMIRA_XXL_JOB_ACCESS_TOKEN|XXL_JOB_ACCESS_TOKEN|XXL_JOB_ADMIN_ACCESS_TOKEN`
  - `XXL_JOB_ADMIN_ADDRESSES=LUMIRA_XXL_JOB_ADMIN_ADDRESSES|XXL_JOB_ADMIN_ADDRESSES`
- Commands:
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/release-env-lint.json`
  - `artifacts/ddd/config/release-config-evidence.json`
- Exit criteria:
  - Use a completed DDD_RELEASE_ENV_FILE, not release-env-missing.template.env.
  - release-env-lint summary primaryBlockers is 0 before expensive runtime evidence is rerun.
  - Clear this batch before running downstream runtime-heavy evidence.

### p0-release-config-ai-owner

- Scope: P0 release-config -> ai-owner
- Pending items: 12
- Env keys: 13 keys
  - LUMIRA_AI_OWNER_FILE_BASE_URL, LUMIRA_AI_OWNER_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL
  - LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_OWNER_INTERNAL_TOKEN, LUMIRA_AI_OWNER_PLATFORM_BASE_URL
  - LUMIRA_AI_PROVIDER_API_KEY, LUMIRA_AI_PROVIDER_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL
  - SAAS_JOB_INTERNAL_TOKEN
- Env check groups: 7 groups
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL=LUMIRA_AI_OWNER_FILE_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL=LUMIRA_AI_OWNER_IAM_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN=LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN|LUMIRA_AI_OWNER_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL=LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL|LUMIRA_AI_OWNER_PLATFORM_BASE_URL`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=LUMIRA_AI_PROVIDER_API_KEY|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL=LUMIRA_AI_PROVIDER_BASE_URL|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN`
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Exit criteria:
  - release-config-evidence status is PASS with no contract issues.
  - releaseConfigBlockersAfterPlaceholders is 0 after placeholders are replaced.
  - Clear this batch before running downstream runtime-heavy evidence.

### p0-release-config-payment-owner

- Scope: P0 release-config -> payment-owner
- Pending items: 2
- Env keys: PAYMENT_PUBLIC_BASE_URL
- Env check groups:
  - `PAYMENT_PUBLIC_BASE_URL=PAYMENT_PUBLIC_BASE_URL`
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Exit criteria:
  - release-config-evidence status is PASS with no contract issues.
  - releaseConfigBlockersAfterPlaceholders is 0 after placeholders are replaced.
  - Clear this batch before running downstream runtime-heavy evidence.

### p0-release-config-platform-events

- Scope: P0 release-config -> platform-events
- Pending items: 17
- Env keys: 20 keys
  - DDD_JOB_INTERNAL_TOKEN, LUMIRA_EVENT_REDIS_STREAM_KEY, LUMIRA_JOB_BACKEND_BASE_URL, LUMIRA_JOB_FILE_SERVICE_BASE_URL
  - LUMIRA_JOB_INTERNAL_TOKEN, LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL, LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL, LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL
  - LUMIRA_XXL_JOB_ACCESS_TOKEN, LUMIRA_XXL_JOB_ADMIN_ADDRESSES, SAAS_EVENT_REDIS_STREAM_KEY, SAAS_JOB_BACKEND_BASE_URL
  - SAAS_JOB_FILE_SERVICE_BASE_URL, SAAS_JOB_INTERNAL_TOKEN, SAAS_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL
  - SAAS_JOB_PLUGIN_SERVICE_BASE_URL, XXL_JOB_ACCESS_TOKEN, XXL_JOB_ADMIN_ACCESS_TOKEN, XXL_JOB_ADMIN_ADDRESSES
- Env check groups: 9 groups
  - `SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN`
  - `SAAS_EVENT_REDIS_STREAM_KEY=LUMIRA_EVENT_REDIS_STREAM_KEY|SAAS_EVENT_REDIS_STREAM_KEY`
  - `SAAS_JOB_BACKEND_BASE_URL=LUMIRA_JOB_BACKEND_BASE_URL|SAAS_JOB_BACKEND_BASE_URL`
  - `SAAS_JOB_FILE_SERVICE_BASE_URL=LUMIRA_JOB_FILE_SERVICE_BASE_URL|SAAS_JOB_FILE_SERVICE_BASE_URL`
  - `SAAS_JOB_MESSAGE_SERVICE_BASE_URL=LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL|SAAS_JOB_MESSAGE_SERVICE_BASE_URL`
  - `SAAS_JOB_PAYMENT_SERVICE_BASE_URL=LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL|SAAS_JOB_PAYMENT_SERVICE_BASE_URL`
  - `SAAS_JOB_PLUGIN_SERVICE_BASE_URL=LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL|SAAS_JOB_PLUGIN_SERVICE_BASE_URL`
  - `XXL_JOB_ACCESS_TOKEN=LUMIRA_XXL_JOB_ACCESS_TOKEN|XXL_JOB_ACCESS_TOKEN|XXL_JOB_ADMIN_ACCESS_TOKEN`
  - `XXL_JOB_ADMIN_ADDRESSES=LUMIRA_XXL_JOB_ADMIN_ADDRESSES|XXL_JOB_ADMIN_ADDRESSES`
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Exit criteria:
  - release-config-evidence status is PASS with no contract issues.
  - releaseConfigBlockersAfterPlaceholders is 0 after placeholders are replaced.
  - Clear this batch before running downstream runtime-heavy evidence.

### p0-release-config-platform-owners

- Scope: P0 release-config -> platform-owners
- Pending items: 18
- Env keys: 19 keys
  - AI_SERVICE_BASE_URL, AUTH_SERVICE_BASE_URL, FILE_SERVICE_BASE_URL, JOB_EXECUTOR_BASE_URL
  - LOCALIZATION_SERVICE_BASE_URL, LUMIRA_AI_BASE_URL, LUMIRA_AI_SERVICE_BASE_URL, LUMIRA_AUTH_SERVICE_BASE_URL
  - LUMIRA_FILE_SERVICE_BASE_URL, LUMIRA_JOB_EXECUTOR_BASE_URL, LUMIRA_LOCALIZATION_SERVICE_BASE_URL, LUMIRA_MESSAGE_SERVICE_BASE_URL
  - LUMIRA_PAYMENT_SERVICE_BASE_URL, LUMIRA_PLUGIN_SERVICE_BASE_URL, LUMIRA_SYSTEM_SERVICE_BASE_URL, MESSAGE_SERVICE_BASE_URL
  - PAYMENT_SERVICE_BASE_URL, PLUGIN_SERVICE_BASE_URL, SYSTEM_SERVICE_BASE_URL
- Env check groups: 9 groups
  - `AI_SERVICE_BASE_URL=AI_SERVICE_BASE_URL|LUMIRA_AI_BASE_URL|LUMIRA_AI_SERVICE_BASE_URL`
  - `AUTH_SERVICE_BASE_URL=AUTH_SERVICE_BASE_URL|LUMIRA_AUTH_SERVICE_BASE_URL`
  - `FILE_SERVICE_BASE_URL=FILE_SERVICE_BASE_URL|LUMIRA_FILE_SERVICE_BASE_URL`
  - `JOB_EXECUTOR_BASE_URL=JOB_EXECUTOR_BASE_URL|LUMIRA_JOB_EXECUTOR_BASE_URL`
  - `LOCALIZATION_SERVICE_BASE_URL=LOCALIZATION_SERVICE_BASE_URL|LUMIRA_LOCALIZATION_SERVICE_BASE_URL`
  - `MESSAGE_SERVICE_BASE_URL=LUMIRA_MESSAGE_SERVICE_BASE_URL|MESSAGE_SERVICE_BASE_URL`
  - `PAYMENT_SERVICE_BASE_URL=LUMIRA_PAYMENT_SERVICE_BASE_URL|PAYMENT_SERVICE_BASE_URL`
  - `PLUGIN_SERVICE_BASE_URL=LUMIRA_PLUGIN_SERVICE_BASE_URL|PLUGIN_SERVICE_BASE_URL`
  - `SYSTEM_SERVICE_BASE_URL=LUMIRA_SYSTEM_SERVICE_BASE_URL|SYSTEM_SERVICE_BASE_URL`
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Exit criteria:
  - release-config-evidence status is PASS with no contract issues.
  - releaseConfigBlockersAfterPlaceholders is 0 after placeholders are replaced.
  - Clear this batch before running downstream runtime-heavy evidence.

### p0-release-config-release-infra

- Scope: P0 release-config -> release-infra
- Pending items: 14
- Env keys: 20 keys
  - CORS_ALLOWED_ORIGIN_PATTERNS, DB_PASSWORD, DB_URL, DB_USERNAME
  - DEPLOY_CHECK_BASE_URL, FIELD_SECRET, FRONTEND_BASE_URL, JWT_SECRET
  - LUMIRA_BASE_URL, MYSQL_PASSWORD, MYSQL_USER, PLAYWRIGHT_BASE_URL
  - REDIS_HOST, SAAS_SECURITY_FIELD_SECRET, SAAS_SECURITY_JWT_SECRET, SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS
  - SPRING_DATASOURCE_PASSWORD, SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATA_REDIS_HOST
- Env check groups: 9 groups
  - `CORS_ALLOWED_ORIGIN_PATTERNS=CORS_ALLOWED_ORIGIN_PATTERNS|SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS`
  - `DB_PASSWORD=DB_PASSWORD|MYSQL_PASSWORD|SPRING_DATASOURCE_PASSWORD`
  - `DB_URL=DB_URL|SPRING_DATASOURCE_URL`
  - `DB_USERNAME=DB_USERNAME|MYSQL_USER|SPRING_DATASOURCE_USERNAME`
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
  - `FIELD_SECRET=FIELD_SECRET|SAAS_SECURITY_FIELD_SECRET`
  - `PLAYWRIGHT_BASE_URL=FRONTEND_BASE_URL|PLAYWRIGHT_BASE_URL`
  - `JWT_SECRET=JWT_SECRET|SAAS_SECURITY_JWT_SECRET`
  - `REDIS_HOST=REDIS_HOST|SPRING_DATA_REDIS_HOST`
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Exit criteria:
  - release-config-evidence status is PASS with no contract issues.
  - releaseConfigBlockersAfterPlaceholders is 0 after placeholders are replaced.
  - Clear this batch before running downstream runtime-heavy evidence.

### p0-docker-release-infra

- Scope: P0 docker -> release-infra
- Pending items: 4
- Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND
- Env check groups:
  - `DDD_DOCKER_BUILD_STRICT=DDD_DOCKER_BUILD_STRICT`
  - `DDD_DOCKER_COMMAND=DDD_DOCKER_COMMAND`
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`
- Exit criteria:
  - Docker CLI and daemon are available in the evidence runner.
  - Required lumira-server and frontend images are built, inspected, and not skipped.
  - Clear this batch before running downstream runtime-heavy evidence.

## Blocked Later

- p1-ai-runtime-ai: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/ai/ai-runtime-drill.json`
- p1-frontend-smoke-frontend: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/frontend/frontend-smoke.json`
    - `artifacts/ddd/frontend/playwright-smoke-results.json`
- p1-business-e2e-file-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/file/file-processing-e2e.json`
- p1-business-e2e-job-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/jobs/job-e2e-smoke.json`
- p1-business-e2e-payment-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/payment/payment-webhook-e2e.json`
- p1-rollback-ai-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-auth-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-file-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-iam-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-job-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-localization-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-message-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-payment-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-platform-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-plugin-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p2-explain-database: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
  - Expected artifacts:
    - `tmp/ddd-explain/*.json`
    - `artifacts/ddd/release/explain-gate-report.json`
- p3-orchestrator-database: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
  - Expected artifacts:
    - `artifacts/ddd/release/orchestrator-report.json`
    - `artifacts/ddd/release/release-evidence-gate.json`
    - `artifacts/ddd/release/readiness-summary.json`
- p3-orchestrator-frontend: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
  - Expected artifacts:
    - `artifacts/ddd/release/orchestrator-report.json`
    - `artifacts/ddd/release/release-evidence-gate.json`
    - `artifacts/ddd/release/readiness-summary.json`
- p3-orchestrator-release-owner: waits for p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
  - Expected artifacts:
    - `artifacts/ddd/release/orchestrator-report.json`
    - `artifacts/ddd/release/release-evidence-gate.json`
    - `artifacts/ddd/release/readiness-summary.json`
