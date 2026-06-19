# DDD Release Action Batches

Generated at: 2026-06-18T19:37:26.213Z
Status: NOT_READY
Release gate mode: strict
Release gate blockers: 94
Batch count: 29
Total pending items: 111

## Execution Notes

- Batches are ordered by release priority, source order, and owner.
- Batch `id`, `dependsOn`, and `canRunImmediately` define the machine-readable execution graph.
- P0 batches can run immediately; P1/P2/P3 batches should wait until their dependencies meet exit criteria and the release gate is rerun.
- Commands are hints extracted from action text; environment evidence still has to be real and production-equivalent.
- The current release gate remains authoritative after every batch; strict mode is required for final release approval.

## Batches

### 1. P0 release-env-lint -> release-infra

- Batch id: p0-release-env-lint-release-infra
- Depends on: none
- Can run immediately: true
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
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/release-env-lint.json`
  - `artifacts/ddd/config/release-config-evidence.json`
- Exit criteria:
  - Use a completed DDD_RELEASE_ENV_FILE, not release-env-missing.template.env.
  - release-env-lint summary primaryBlockers is 0 before expensive runtime evidence is rerun.
  - Clear this batch before running downstream runtime-heavy evidence.

- release-env-lint-placeholders: unresolvedTemplateKeys=93
- release-env-lint-status: status=FAIL primaryBlockers=55

### 2. P0 release-config -> ai-owner

- Batch id: p0-release-config-ai-owner
- Depends on: none
- Can run immediately: true
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

- file owner url: placeholder value is not allowed
- file owner url: must use HTTPS for production-equivalent evidence
- iam owner url: placeholder value is not allowed
- iam owner url: must use HTTPS for production-equivalent evidence
- owner internal token: placeholder value is not allowed
- owner internal token: must be at least 32 characters
- platform owner url: placeholder value is not allowed
- platform owner url: must use HTTPS for production-equivalent evidence
- provider api key: placeholder value is not allowed
- provider api key: must be at least 32 characters
- provider base url: placeholder value is not allowed
- provider base url: must use HTTPS for production-equivalent evidence

### 3. P0 release-config -> payment-owner

- Batch id: p0-release-config-payment-owner
- Depends on: none
- Can run immediately: true
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

- payment public url: placeholder value is not allowed
- payment public url: must use HTTPS for production-equivalent evidence

### 4. P0 release-config -> platform-events

- Batch id: p0-release-config-platform-events
- Depends on: none
- Can run immediately: true
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

- event stream key: placeholder value is not allowed
- job backend url: placeholder value is not allowed
- job backend url: must use HTTPS for production-equivalent evidence
- job file url: placeholder value is not allowed
- job file url: must use HTTPS for production-equivalent evidence
- job internal token: placeholder value is not allowed
- job internal token: must be at least 32 characters
- job message url: placeholder value is not allowed
- job message url: must use HTTPS for production-equivalent evidence
- job payment url: placeholder value is not allowed
- job payment url: must use HTTPS for production-equivalent evidence
- job plugin url: placeholder value is not allowed
- job plugin url: must use HTTPS for production-equivalent evidence
- xxl job admin: placeholder value is not allowed
- xxl job admin: must use HTTPS for production-equivalent evidence
- xxl job token: placeholder value is not allowed
- xxl job token: must be at least 32 characters

### 5. P0 release-config -> platform-owners

- Batch id: p0-release-config-platform-owners
- Depends on: none
- Can run immediately: true
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

- ai service: placeholder value is not allowed
- ai service: must use HTTPS for production-equivalent evidence
- auth service: placeholder value is not allowed
- auth service: must use HTTPS for production-equivalent evidence
- file service: placeholder value is not allowed
- file service: must use HTTPS for production-equivalent evidence
- job executor: placeholder value is not allowed
- job executor: must use HTTPS for production-equivalent evidence
- localization service: placeholder value is not allowed
- localization service: must use HTTPS for production-equivalent evidence
- message service: placeholder value is not allowed
- message service: must use HTTPS for production-equivalent evidence
- payment service: placeholder value is not allowed
- payment service: must use HTTPS for production-equivalent evidence
- plugin service: placeholder value is not allowed
- plugin service: must use HTTPS for production-equivalent evidence
- system service: placeholder value is not allowed
- system service: must use HTTPS for production-equivalent evidence

### 6. P0 release-config -> release-infra

- Batch id: p0-release-config-release-infra
- Depends on: none
- Can run immediately: true
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

- backend base url: placeholder value is not allowed
- backend base url: must use HTTPS for production-equivalent evidence
- cors origins: placeholder value is not allowed
- database password: placeholder value is not allowed
- database password: must be at least 16 characters
- database url: placeholder value is not allowed
- database username: placeholder value is not allowed
- field secret: placeholder value is not allowed
- field secret: must be at least 32 characters
- frontend base url: placeholder value is not allowed
- frontend base url: must use HTTPS for production-equivalent evidence
- jwt secret: placeholder value is not allowed
- jwt secret: must be at least 32 characters
- redis host: placeholder value is not allowed

### 7. P0 docker -> release-infra

- Batch id: p0-docker-release-infra
- Depends on: none
- Can run immediately: true
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

- docker-blocker-1: lumira-server: docker build failed after 3 attempt(s) with transient registry/network error status 1
- docker-blocker-2: frontend: docker build failed after 3 attempt(s) with transient registry/network error status 1
- docker-image-frontend-failed: docker build failed after 3 attempt(s) with transient registry/network error status 1
- docker-image-lumira-server-failed: docker build failed after 3 attempt(s) with transient registry/network error status 1

### 8. P0 runtime-readiness -> release-infra

- Batch id: p0-runtime-readiness-release-infra
- Depends on: none
- Can run immediately: true
- Pending items: 4
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL
- Env check groups:
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
- Commands:
  - `node scripts/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`
- Exit criteria:
  - Runtime readiness is generated from an HTTPS non-local backend base URL.
  - All 30 owner readiness/health/metrics checks pass.
  - Clear this batch before running downstream runtime-heavy evidence.

- runtime-readiness-contract-1: runtime readiness productionEquivalence.strict must be true for strict release evidence
- runtime-readiness-contract-2: runtime readiness productionEquivalence.https must be true for strict release evidence
- runtime-readiness-contract-3: runtime readiness productionEquivalence.localOnly must be false for strict release evidence
- runtime-readiness-contract-4: runtime readiness productionEquivalence.deploymentEvidence is required

### 9. P0 manifest -> release-owner

- Batch id: p0-manifest-release-owner
- Depends on: none
- Can run immediately: true
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_RELEASE_MANIFEST_STRICT
- Env check groups:
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_RELEASE_MANIFEST_STRICT=DDD_RELEASE_MANIFEST_STRICT`
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
- Exit criteria:
  - All required release evidence artifacts are present and checksummed.
  - Clear this batch before running downstream runtime-heavy evidence.

- manifest-missing-no-explain-json-files-in-tmp-ddd-explain: no explain JSON files in tmp\ddd-explain

### 10. P0 authenticated-performance -> release-performance

- Batch id: p0-authenticated-performance-release-performance
- Depends on: none
- Can run immediately: true
- Pending items: 9
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Env check groups:
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT=DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT=DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
- Exit criteria:
  - Authenticated performance actual is generated from a production-equivalent HTTPS backend.
  - Accepted baseline exists and current p95/upload metrics do not regress beyond the configured threshold.
  - Clear this batch before running downstream runtime-heavy evidence.

- performance-actual-shape-1: authenticated performance actual productionEquivalence.strict must be true for strict release evidence
- performance-actual-shape-2: authenticated performance actual productionEquivalence.https must be true for strict release evidence
- performance-actual-shape-3: authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence
- performance-actual-shape-4: authenticated performance actual productionEquivalence.deploymentEvidence is required
- performance-baseline-metadata-5: strict release baseline requires baselineType=authenticated-runtime
- performance-baseline-metadata-6: acceptedAt must be an ISO timestamp
- performance-baseline-metadata-7: acceptedBy is required
- performance-baseline-metadata-8: sourceArtifact is required
- performance-baseline-metadata-9: sourceSha256 must be a SHA-256 hex digest

### 11. P1 ai-runtime -> ai

- Batch id: p1-ai-runtime-ai
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 3
- Env keys: 12 keys
  - BASE_URL, DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE, DDD_AI_EXPECT_PROVIDER_REMOTE, DEPLOY_CHECK_BASE_URL
  - LUMIRA_AI_BASE_URL, LUMIRA_AI_OWNER_FILE_BASE_URL, LUMIRA_AI_OWNER_IAM_BASE_URL, LUMIRA_AI_OWNER_PLATFORM_BASE_URL
  - LUMIRA_AI_PROVIDER, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_BASE_URL
- Env check groups: 11 groups
  - `BASE_URL=BASE_URL`
  - `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE`
  - `DDD_AI_EXPECT_PROVIDER_REMOTE=DDD_AI_EXPECT_PROVIDER_REMOTE`
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
  - `AI_SERVICE_BASE_URL=AI_SERVICE_BASE_URL|LUMIRA_AI_BASE_URL|LUMIRA_AI_SERVICE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL=LUMIRA_AI_OWNER_FILE_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL=LUMIRA_AI_OWNER_IAM_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL=LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL|LUMIRA_AI_OWNER_PLATFORM_BASE_URL`
  - `LUMIRA_AI_PROVIDER=LUMIRA_AI_PROVIDER`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=LUMIRA_AI_PROVIDER_API_KEY|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL=LUMIRA_AI_PROVIDER_BASE_URL|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`
- Commands:
  - `node scripts/ddd-ai-runtime-drill.mjs`
- Expected artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`
- Exit criteria:
  - AI runtime drill uses HTTPS non-local base URL with remote provider and owner gateway expectations enabled.
  - Provider is not local fallback and owner gateway has configured owner integrations.

- ai-owner-gateway: ownerGateway status=CONFIGURED configuredOwners=0
- ai-provider-runtime: provider status=CONFIGURED remoteConfigured=false
- ai-runtime-base-url: missing production-equivalent AI base URL

### 12. P1 frontend-smoke -> frontend

- Batch id: p1-frontend-smoke-frontend
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_FRONTEND_EXPECT_DEPLOYED
- Env check groups:
  - `DDD_FRONTEND_EXPECT_DEPLOYED=DDD_FRONTEND_EXPECT_DEPLOYED`
- Commands:
  - `node scripts/ddd-frontend-playwright-smoke.mjs`
  - `node scripts/ddd-frontend-smoke-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/frontend/frontend-smoke.json`
  - `artifacts/ddd/frontend/playwright-smoke-results.json`
- Exit criteria:
  - Frontend smoke runs against a deployed HTTPS frontend with DDD_FRONTEND_EXPECT_DEPLOYED=true.
  - Required Playwright smoke flows all pass and produce a JSON report.

- frontend-deployed-expectation: strict release requires deployed frontend smoke expectation

### 13. P1 business-e2e -> file-owner

- Batch id: p1-business-e2e-file-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, LUMIRA_JOB_INTERNAL_TOKEN, LUMIRA_UPLOAD_STORAGE_ROOT
- Env check groups:
  - `BASE_URL=BASE_URL`
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN`
  - `UPLOAD_STORAGE_ROOT=LUMIRA_UPLOAD_STORAGE_ROOT|UPLOAD_STORAGE_ROOT`
- Commands:
  - `node scripts/ddd-file-processing-e2e-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/file/file-processing-e2e.json`
- Exit criteria:
  - File, Job, and Payment business E2E artifacts are generated from HTTPS non-local runtime endpoints.
  - Owner-specific task/webhook/job contract checks pass with production-equivalence metadata.

- file-processing-production-equivalence: strict file processing E2E requires HTTPS baseUrl evidence; strict file processing E2E requires non-local baseUrl, got http://127.0.0.1:8080

### 14. P1 business-e2e -> job-owner

- Batch id: p1-business-e2e-job-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, LUMIRA_JOB_INTERNAL_TOKEN
- Env check groups:
  - `BASE_URL=BASE_URL`
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN`
- Commands:
  - `node scripts/ddd-job-e2e-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/jobs/job-e2e-smoke.json`
- Exit criteria:
  - File, Job, and Payment business E2E artifacts are generated from HTTPS non-local runtime endpoints.
  - Owner-specific task/webhook/job contract checks pass with production-equivalence metadata.

- job-e2e-production-equivalence: strict job E2E requires HTTPS baseUrl evidence; strict job E2E requires non-local baseUrl, got http://127.0.0.1:8080

### 15. P1 business-e2e -> payment-owner

- Batch id: p1-business-e2e-payment-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, PAYMENT_PUBLIC_BASE_URL
- Env check groups:
  - `BASE_URL=BASE_URL`
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
  - `PAYMENT_PUBLIC_BASE_URL=PAYMENT_PUBLIC_BASE_URL`
- Commands:
  - `node scripts/ddd-payment-webhook-e2e-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/payment/payment-webhook-e2e.json`
- Exit criteria:
  - File, Job, and Payment business E2E artifacts are generated from HTTPS non-local runtime endpoints.
  - Owner-specific task/webhook/job contract checks pass with production-equivalence metadata.

- payment-webhook-production-equivalence: strict payment webhook E2E requires HTTPS baseUrl evidence; strict payment webhook E2E requires non-local baseUrl, got http://127.0.0.1:8080

### 16. P1 rollback -> ai-owner

- Batch id: p1-rollback-ai-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- AI: AI rollback drill is DEFERRED with approved deferral evidence

### 17. P1 rollback -> auth-owner

- Batch id: p1-rollback-auth-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Auth: Auth rollback drill is DEFERRED with approved deferral evidence

### 18. P1 rollback -> file-owner

- Batch id: p1-rollback-file-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- File: File rollback drill is DEFERRED with approved deferral evidence

### 19. P1 rollback -> iam-owner

- Batch id: p1-rollback-iam-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- IAM: IAM rollback drill is DEFERRED with approved deferral evidence

### 20. P1 rollback -> job-owner

- Batch id: p1-rollback-job-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Job: Job rollback drill is DEFERRED with approved deferral evidence

### 21. P1 rollback -> localization-owner

- Batch id: p1-rollback-localization-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Localization: Localization rollback drill is DEFERRED with approved deferral evidence

### 22. P1 rollback -> message-owner

- Batch id: p1-rollback-message-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Message: Message rollback drill is DEFERRED with approved deferral evidence

### 23. P1 rollback -> payment-owner

- Batch id: p1-rollback-payment-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Payment: Payment rollback drill is DEFERRED with approved deferral evidence

### 24. P1 rollback -> platform-owner

- Batch id: p1-rollback-platform-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Platform: Platform rollback drill is DEFERRED with approved deferral evidence

### 25. P1 rollback -> plugin-owner

- Batch id: p1-rollback-plugin-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Plugin: Plugin rollback drill is DEFERRED with approved deferral evidence

### 26. P2 explain -> database

- Batch id: p2-explain-database
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Can run immediately: false
- Pending items: 8
- Env keys: 12 keys
  - DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT
  - DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE, MYSQL_CLI, MYSQL_DATABASE
  - MYSQL_HOST, MYSQL_PASSWORD, MYSQL_PORT, MYSQL_USER
- Env check groups: 12 groups
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_EXPLAIN_DATABASE=DDD_EXPLAIN_DATABASE`
  - `DDD_EXPLAIN_DIR=DDD_EXPLAIN_DIR`
  - `DDD_EXPLAIN_ENVIRONMENT=DDD_EXPLAIN_ENVIRONMENT`
  - `DDD_EXPLAIN_STRICT=DDD_EXPLAIN_STRICT`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `MYSQL_CLI=MYSQL_CLI`
  - `MYSQL_DATABASE=MYSQL_DATABASE`
  - `MYSQL_HOST=MYSQL_HOST`
  - `DB_PASSWORD=DB_PASSWORD|MYSQL_PASSWORD|SPRING_DATASOURCE_PASSWORD`
  - `MYSQL_PORT=MYSQL_PORT`
  - `DB_USERNAME=DB_USERNAME|MYSQL_USER|SPRING_DATASOURCE_USERNAME`
- Commands:
  - `node scripts/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`
- Expected artifacts:
  - `tmp/ddd-explain/*.json`
  - `artifacts/ddd/release/explain-gate-report.json`
- Exit criteria:
  - Production-equivalent MySQL EXPLAIN artifacts are freshly collected for every required hot path.
  - Strict explain gate has no full scans, legacy imports, missing indexes, or contract issues.

- ai-knowledge-index-retry.json: missing required EXPLAIN artifact
- message-archive-total.json: missing required EXPLAIN artifact
- message-unread-count.json: missing required EXPLAIN artifact
- message-visible-list.json: missing required EXPLAIN artifact
- platform-outbox-owner-relay-file.json: missing required EXPLAIN artifact
- platform-outbox-owner-relay-message.json: missing required EXPLAIN artifact
- platform-runtime-appearance.json: missing required EXPLAIN artifact
- plugin-bootstrap.json: missing required EXPLAIN artifact

### 27. P3 orchestrator -> database

- Batch id: p3-orchestrator-database
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED
- Env check groups:
  - `DDD_MIGRATION_FRESH_DB_EVIDENCE=DDD_MIGRATION_FRESH_DB_EVIDENCE`
  - `DDD_MIGRATION_FRESH_DB_VALIDATED=DDD_MIGRATION_FRESH_DB_VALIDATED`
  - `DDD_MIGRATION_UPGRADE_DB_EVIDENCE=DDD_MIGRATION_UPGRADE_DB_EVIDENCE`
  - `DDD_MIGRATION_UPGRADE_DB_VALIDATED=DDD_MIGRATION_UPGRADE_DB_VALIDATED`
- Commands:
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Exit criteria:
  - Rerun release evidence orchestrator in strict run mode after P0/P1/P2 batches are clean.
  - Final strict release gate and readiness summary report zero blockers.
  - Run only after all prerequisite evidence batches are clean.

- orchestrator-preflight-migration-runtime-evidence: missing migration drill env: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_EVIDENCE

### 28. P3 orchestrator -> frontend

- Batch id: p3-orchestrator-frontend
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Can run immediately: false
- Pending items: 1
- Env keys: FRONTEND_BASE_URL, PLAYWRIGHT_BASE_URL
- Env check groups:
  - `PLAYWRIGHT_BASE_URL=FRONTEND_BASE_URL|PLAYWRIGHT_BASE_URL`
- Commands:
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Exit criteria:
  - Rerun release evidence orchestrator in strict run mode after P0/P1/P2 batches are clean.
  - Final strict release gate and readiness summary report zero blockers.
  - Run only after all prerequisite evidence batches are clean.

- orchestrator-preflight-frontend-runtime-base-url: missing deployed frontend base URL

### 29. P3 orchestrator -> release-owner

- Batch id: p3-orchestrator-release-owner
- Depends on: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_RELEASE_EVIDENCE_STRICT
- Env check groups:
  - `DDD_RELEASE_EVIDENCE_STRICT=DDD_RELEASE_EVIDENCE_STRICT`
- Commands:
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Exit criteria:
  - Rerun release evidence orchestrator in strict run mode after P0/P1/P2 batches are clean.
  - Final strict release gate and readiness summary report zero blockers.
  - Run only after all prerequisite evidence batches are clean.

- orchestrator-run-mode: strict release requires run mode report, got plan

