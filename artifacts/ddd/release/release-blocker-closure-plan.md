# DDD Release Blocker Closure Plan

Generated at: 2026-06-19T06:54:03.604Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
releaseEnvFileCutoverSafe: false
RUN_NOW_LOCAL: 3
RUN_NOW_WITH_REAL_ENV: 80
WAIT_FOR_DEPENDENCIES: 28
Runnable waves: 10
Owner input receipt status: PENDING_OWNER_INPUT
Owner input receipt cutover ready: false
Owner input receipt required inputs: 34
Owner input receipt pending owners: 5

## Owner Input Receipt

- Status: PENDING_OWNER_INPUT
- Cutover ready: false
- Required owner inputs: 34
- Owners: 5
- Pending owners: 5
- Missing criteria:
  - releaseEnvReadinessBlockers
  - releaseEnvReadinessPlaceholders
  - releaseEnvReadinessStatus
- Pending owner inputs:
  - platform-events: required=9 placeholders=9 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/01-platform-events.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/01-platform-events.md
  - platform-owners: required=9 placeholders=9 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/02-platform-owners.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/02-platform-owners.md
  - release-infra: required=9 placeholders=9 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md
  - ai-owner: required=6 placeholders=6 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/04-ai-owner.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/04-ai-owner.md
  - payment-owner: required=1 placeholders=1 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/05-payment-owner.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/05-payment-owner.md

## Runnable Waves

### Wave 1. release-infra / p0-release-env-lint-release-infra

- Priority: P0
- Closure kinds: RUN_NOW_WITH_REAL_ENV
- Item orders: 1, 2
- Item ids: release-env-lint-placeholders, release-env-lint-status
- Env keys: AI_SERVICE_BASE_URL, AUTH_SERVICE_BASE_URL, BASE_URL, CORS_ALLOWED_ORIGIN_PATTERNS, DB_PASSWORD, DB_URL, DB_USERNAME, DDD_AUTH_PASSWORD, DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE, DDD_AUTH_PERF_ENVIRONMENT, DDD_AUTH_USERNAME, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DDD_DEPLOYMENT_EVIDENCE, DDD_EXPLAIN_DATABASE, DDD_FRONTEND_DEPLOYMENT_EVIDENCE, DDD_MIGRATION_COMPLETED_AT, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_OPERATOR, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED, FIELD_SECRET, FILE_SERVICE_BASE_URL, JOB_EXECUTOR_BASE_URL, JWT_SECRET, LOCALIZATION_SERVICE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_BASE_URL, MESSAGE_SERVICE_BASE_URL, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PORT, PAYMENT_PUBLIC_BASE_URL, PAYMENT_SERVICE_BASE_URL, PLAYWRIGHT_BASE_URL, PLUGIN_SERVICE_BASE_URL, REDIS_HOST, SAAS_EVENT_REDIS_STREAM_KEY, SAAS_JOB_BACKEND_BASE_URL, SAAS_JOB_FILE_SERVICE_BASE_URL, SAAS_JOB_INTERNAL_TOKEN, SAAS_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL, SAAS_JOB_PLUGIN_SERVICE_BASE_URL, SYSTEM_SERVICE_BASE_URL, XXL_JOB_ACCESS_TOKEN, XXL_JOB_ADMIN_ADDRESSES
- Commands:
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/release/release-env-lint.json`

### Wave 2. ai-owner / p0-release-config-ai-owner

- Priority: P0
- Closure kinds: RUN_NOW_WITH_REAL_ENV
- Item orders: 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14
- Item ids: file owner url, iam owner url, owner internal token, platform owner url, provider api key, provider base url
- Env keys: LUMIRA_AI_OWNER_FILE_BASE_URL, LUMIRA_AI_OWNER_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_OWNER_INTERNAL_TOKEN, LUMIRA_AI_OWNER_PLATFORM_BASE_URL, LUMIRA_AI_PROVIDER_API_KEY, LUMIRA_AI_PROVIDER_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, SAAS_JOB_INTERNAL_TOKEN
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

### Wave 3. payment-owner / p0-release-config-payment-owner

- Priority: P0
- Closure kinds: RUN_NOW_WITH_REAL_ENV
- Item orders: 15, 16
- Item ids: payment public url
- Env keys: PAYMENT_PUBLIC_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

### Wave 4. platform-events / p0-release-config-platform-events

- Priority: P0
- Closure kinds: RUN_NOW_WITH_REAL_ENV
- Item orders: 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33
- Item ids: event stream key, job backend url, job file url, job internal token, job message url, job payment url, job plugin url, xxl job admin, xxl job token
- Env keys: DDD_JOB_INTERNAL_TOKEN, LUMIRA_EVENT_REDIS_STREAM_KEY, LUMIRA_JOB_BACKEND_BASE_URL, LUMIRA_JOB_FILE_SERVICE_BASE_URL, LUMIRA_JOB_INTERNAL_TOKEN, LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL, LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL, LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL, LUMIRA_XXL_JOB_ACCESS_TOKEN, LUMIRA_XXL_JOB_ADMIN_ADDRESSES, SAAS_EVENT_REDIS_STREAM_KEY, SAAS_JOB_BACKEND_BASE_URL, SAAS_JOB_FILE_SERVICE_BASE_URL, SAAS_JOB_INTERNAL_TOKEN, SAAS_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL, SAAS_JOB_PLUGIN_SERVICE_BASE_URL, XXL_JOB_ACCESS_TOKEN, XXL_JOB_ADMIN_ACCESS_TOKEN, XXL_JOB_ADMIN_ADDRESSES
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

### Wave 5. platform-owners / p0-release-config-platform-owners

- Priority: P0
- Closure kinds: RUN_NOW_WITH_REAL_ENV
- Item orders: 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51
- Item ids: ai service, auth service, file service, job executor, localization service, message service, payment service, plugin service, system service
- Env keys: AI_SERVICE_BASE_URL, AUTH_SERVICE_BASE_URL, FILE_SERVICE_BASE_URL, JOB_EXECUTOR_BASE_URL, LOCALIZATION_SERVICE_BASE_URL, LUMIRA_AI_BASE_URL, LUMIRA_AI_SERVICE_BASE_URL, LUMIRA_AUTH_SERVICE_BASE_URL, LUMIRA_FILE_SERVICE_BASE_URL, LUMIRA_JOB_EXECUTOR_BASE_URL, LUMIRA_LOCALIZATION_SERVICE_BASE_URL, LUMIRA_MESSAGE_SERVICE_BASE_URL, LUMIRA_PAYMENT_SERVICE_BASE_URL, LUMIRA_PLUGIN_SERVICE_BASE_URL, LUMIRA_SYSTEM_SERVICE_BASE_URL, MESSAGE_SERVICE_BASE_URL, PAYMENT_SERVICE_BASE_URL, PLUGIN_SERVICE_BASE_URL, SYSTEM_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

### Wave 6. release-infra / p0-release-config-release-infra

- Priority: P0
- Closure kinds: RUN_NOW_WITH_REAL_ENV
- Item orders: 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65
- Item ids: backend base url, cors origins, database password, database url, database username, field secret, frontend base url, jwt secret, redis host
- Env keys: CORS_ALLOWED_ORIGIN_PATTERNS, DB_PASSWORD, DB_URL, DB_USERNAME, DEPLOY_CHECK_BASE_URL, FIELD_SECRET, FRONTEND_BASE_URL, JWT_SECRET, LUMIRA_BASE_URL, MYSQL_PASSWORD, MYSQL_USER, PLAYWRIGHT_BASE_URL, REDIS_HOST, SAAS_SECURITY_FIELD_SECRET, SAAS_SECURITY_JWT_SECRET, SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS, SPRING_DATASOURCE_PASSWORD, SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATA_REDIS_HOST
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

### Wave 7. release-infra / p0-docker-release-infra

- Priority: P0
- Closure kinds: RUN_NOW_WITH_REAL_ENV
- Item orders: 66, 67, 68, 69
- Item ids: docker-blocker-1, docker-blocker-2, docker-image-frontend-failed, docker-image-lumira-server-failed
- Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`

### Wave 8. release-infra / p0-runtime-readiness-release-infra

- Priority: P0
- Closure kinds: RUN_NOW_WITH_REAL_ENV
- Item orders: 70, 71, 72, 73
- Item ids: runtime-readiness-contract-1, runtime-readiness-contract-2, runtime-readiness-contract-3, runtime-readiness-contract-4
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL
- Commands:
  - `node scripts/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`

### Wave 9. release-owner / p0-manifest-release-owner

- Priority: P0
- Closure kinds: RUN_NOW_WITH_REAL_ENV
- Item orders: 74
- Item ids: manifest-missing-no-explain-json-files-in-tmp-ddd-explain
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_RELEASE_MANIFEST_STRICT
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`

### Wave 10. release-performance / p0-authenticated-performance-release-performance

- Priority: P0
- Closure kinds: RUN_NOW_LOCAL, RUN_NOW_WITH_REAL_ENV
- Item orders: 75, 76, 77, 78, 79, 80, 81, 82, 83
- Item ids: performance-actual-shape-1, performance-actual-shape-2, performance-actual-shape-3, performance-actual-shape-4, performance-baseline-metadata-5, performance-baseline-metadata-6, performance-baseline-metadata-7, performance-baseline-metadata-8, performance-baseline-metadata-9
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## Items

## 1. release-infra / release-env-lint-placeholders

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-env-lint
- Batch: p0-release-env-lint-release-infra (ready)
- Dependencies: none
- Reason: unresolvedTemplateKeys=93
- Action: Replace every placeholder-like value (`<placeholder>`, `replace-with-*`, TODO/TBD, example domains) in `DDD_RELEASE_ENV_FILE` before running release evidence.
- Env keys: AI_SERVICE_BASE_URL, AUTH_SERVICE_BASE_URL, BASE_URL, CORS_ALLOWED_ORIGIN_PATTERNS, DB_PASSWORD, DB_URL, DB_USERNAME, DDD_AUTH_PASSWORD, DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE, DDD_AUTH_PERF_ENVIRONMENT, DDD_AUTH_USERNAME, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DDD_DEPLOYMENT_EVIDENCE, DDD_EXPLAIN_DATABASE, DDD_FRONTEND_DEPLOYMENT_EVIDENCE, DDD_MIGRATION_COMPLETED_AT, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_OPERATOR, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED, FIELD_SECRET, FILE_SERVICE_BASE_URL, JOB_EXECUTOR_BASE_URL, JWT_SECRET, LOCALIZATION_SERVICE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_BASE_URL, MESSAGE_SERVICE_BASE_URL, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PORT, PAYMENT_PUBLIC_BASE_URL, PAYMENT_SERVICE_BASE_URL, PLAYWRIGHT_BASE_URL, PLUGIN_SERVICE_BASE_URL, REDIS_HOST, SAAS_EVENT_REDIS_STREAM_KEY, SAAS_JOB_BACKEND_BASE_URL, SAAS_JOB_FILE_SERVICE_BASE_URL, SAAS_JOB_INTERNAL_TOKEN, SAAS_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL, SAAS_JOB_PLUGIN_SERVICE_BASE_URL, SYSTEM_SERVICE_BASE_URL, XXL_JOB_ACCESS_TOKEN, XXL_JOB_ADMIN_ADDRESSES
- Commands:
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/release/release-env-lint.json`

## 2. release-infra / release-env-lint-status

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-env-lint
- Batch: p0-release-env-lint-release-infra (ready)
- Dependencies: none
- Reason: status=FAIL primaryBlockers=55
- Action: Replace placeholders and invalid values in `DDD_RELEASE_ENV_FILE`, then rerun `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`.
- Env keys: AI_SERVICE_BASE_URL, AUTH_SERVICE_BASE_URL, BASE_URL, CORS_ALLOWED_ORIGIN_PATTERNS, DB_PASSWORD, DB_URL, DB_USERNAME, DDD_AUTH_PASSWORD, DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE, DDD_AUTH_PERF_ENVIRONMENT, DDD_AUTH_USERNAME, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DDD_DEPLOYMENT_EVIDENCE, DDD_EXPLAIN_DATABASE, DDD_FRONTEND_DEPLOYMENT_EVIDENCE, DDD_MIGRATION_COMPLETED_AT, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_OPERATOR, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED, FIELD_SECRET, FILE_SERVICE_BASE_URL, JOB_EXECUTOR_BASE_URL, JWT_SECRET, LOCALIZATION_SERVICE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_BASE_URL, MESSAGE_SERVICE_BASE_URL, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PORT, PAYMENT_PUBLIC_BASE_URL, PAYMENT_SERVICE_BASE_URL, PLAYWRIGHT_BASE_URL, PLUGIN_SERVICE_BASE_URL, REDIS_HOST, SAAS_EVENT_REDIS_STREAM_KEY, SAAS_JOB_BACKEND_BASE_URL, SAAS_JOB_FILE_SERVICE_BASE_URL, SAAS_JOB_INTERNAL_TOKEN, SAAS_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL, SAAS_JOB_PLUGIN_SERVICE_BASE_URL, SYSTEM_SERVICE_BASE_URL, XXL_JOB_ACCESS_TOKEN, XXL_JOB_ADMIN_ADDRESSES
- Commands:
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/release/release-env-lint.json`

## 3. ai-owner / file owner url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-ai-owner (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL or LUMIRA_AI_OWNER_FILE_BASE_URL for file owner url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_AI_OWNER_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 4. ai-owner / file owner url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-ai-owner (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL or LUMIRA_AI_OWNER_FILE_BASE_URL for file owner url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_AI_OWNER_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 5. ai-owner / iam owner url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-ai-owner (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL or LUMIRA_AI_OWNER_IAM_BASE_URL for iam owner url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_AI_OWNER_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 6. ai-owner / iam owner url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-ai-owner (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL or LUMIRA_AI_OWNER_IAM_BASE_URL for iam owner url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_AI_OWNER_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 7. ai-owner / owner internal token

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-ai-owner (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN or LUMIRA_AI_OWNER_INTERNAL_TOKEN or SAAS_JOB_INTERNAL_TOKEN for owner internal token in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTERNAL_TOKEN, SAAS_JOB_INTERNAL_TOKEN
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 8. ai-owner / owner internal token

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-ai-owner (ready)
- Dependencies: none
- Reason: must be at least 32 characters
- Action: Set LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN or LUMIRA_AI_OWNER_INTERNAL_TOKEN or SAAS_JOB_INTERNAL_TOKEN for owner internal token in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTERNAL_TOKEN, SAAS_JOB_INTERNAL_TOKEN
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 9. ai-owner / platform owner url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-ai-owner (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL or LUMIRA_AI_OWNER_PLATFORM_BASE_URL for platform owner url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_OWNER_PLATFORM_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 10. ai-owner / platform owner url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-ai-owner (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL or LUMIRA_AI_OWNER_PLATFORM_BASE_URL for platform owner url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_OWNER_PLATFORM_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 11. ai-owner / provider api key

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-ai-owner (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY or LUMIRA_AI_PROVIDER_API_KEY for provider api key in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_AI_PROVIDER_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 12. ai-owner / provider api key

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-ai-owner (ready)
- Dependencies: none
- Reason: must be at least 32 characters
- Action: Set LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY or LUMIRA_AI_PROVIDER_API_KEY for provider api key in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_AI_PROVIDER_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 13. ai-owner / provider base url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-ai-owner (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL or LUMIRA_AI_PROVIDER_BASE_URL for provider base url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_AI_PROVIDER_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 14. ai-owner / provider base url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-ai-owner (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL or LUMIRA_AI_PROVIDER_BASE_URL for provider base url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_AI_PROVIDER_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 15. payment-owner / payment public url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-payment-owner (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set PAYMENT_PUBLIC_BASE_URL for payment public url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: PAYMENT_PUBLIC_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 16. payment-owner / payment public url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-payment-owner (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set PAYMENT_PUBLIC_BASE_URL for payment public url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: PAYMENT_PUBLIC_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 17. platform-events / event stream key

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set SAAS_EVENT_REDIS_STREAM_KEY or LUMIRA_EVENT_REDIS_STREAM_KEY for event stream key in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_EVENT_REDIS_STREAM_KEY, SAAS_EVENT_REDIS_STREAM_KEY
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 18. platform-events / job backend url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set SAAS_JOB_BACKEND_BASE_URL or LUMIRA_JOB_BACKEND_BASE_URL for job backend url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_JOB_BACKEND_BASE_URL, SAAS_JOB_BACKEND_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 19. platform-events / job backend url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set SAAS_JOB_BACKEND_BASE_URL or LUMIRA_JOB_BACKEND_BASE_URL for job backend url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_JOB_BACKEND_BASE_URL, SAAS_JOB_BACKEND_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 20. platform-events / job file url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set SAAS_JOB_FILE_SERVICE_BASE_URL or LUMIRA_JOB_FILE_SERVICE_BASE_URL for job file url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_JOB_FILE_SERVICE_BASE_URL, SAAS_JOB_FILE_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 21. platform-events / job file url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set SAAS_JOB_FILE_SERVICE_BASE_URL or LUMIRA_JOB_FILE_SERVICE_BASE_URL for job file url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_JOB_FILE_SERVICE_BASE_URL, SAAS_JOB_FILE_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 22. platform-events / job internal token

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set SAAS_JOB_INTERNAL_TOKEN or DDD_JOB_INTERNAL_TOKEN or LUMIRA_JOB_INTERNAL_TOKEN for job internal token in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: DDD_JOB_INTERNAL_TOKEN, LUMIRA_JOB_INTERNAL_TOKEN, SAAS_JOB_INTERNAL_TOKEN
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 23. platform-events / job internal token

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: must be at least 32 characters
- Action: Set SAAS_JOB_INTERNAL_TOKEN or DDD_JOB_INTERNAL_TOKEN or LUMIRA_JOB_INTERNAL_TOKEN for job internal token in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: DDD_JOB_INTERNAL_TOKEN, LUMIRA_JOB_INTERNAL_TOKEN, SAAS_JOB_INTERNAL_TOKEN
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 24. platform-events / job message url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set SAAS_JOB_MESSAGE_SERVICE_BASE_URL or LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL for job message url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_MESSAGE_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 25. platform-events / job message url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set SAAS_JOB_MESSAGE_SERVICE_BASE_URL or LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL for job message url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_MESSAGE_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 26. platform-events / job payment url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set SAAS_JOB_PAYMENT_SERVICE_BASE_URL or LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL for job payment url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 27. platform-events / job payment url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set SAAS_JOB_PAYMENT_SERVICE_BASE_URL or LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL for job payment url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 28. platform-events / job plugin url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set SAAS_JOB_PLUGIN_SERVICE_BASE_URL or LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL for job plugin url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL, SAAS_JOB_PLUGIN_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 29. platform-events / job plugin url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set SAAS_JOB_PLUGIN_SERVICE_BASE_URL or LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL for job plugin url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL, SAAS_JOB_PLUGIN_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 30. platform-events / xxl job admin

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set XXL_JOB_ADMIN_ADDRESSES or LUMIRA_XXL_JOB_ADMIN_ADDRESSES for xxl job admin in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_XXL_JOB_ADMIN_ADDRESSES, XXL_JOB_ADMIN_ADDRESSES
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 31. platform-events / xxl job admin

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set XXL_JOB_ADMIN_ADDRESSES or LUMIRA_XXL_JOB_ADMIN_ADDRESSES for xxl job admin in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_XXL_JOB_ADMIN_ADDRESSES, XXL_JOB_ADMIN_ADDRESSES
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 32. platform-events / xxl job token

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set XXL_JOB_ACCESS_TOKEN or XXL_JOB_ADMIN_ACCESS_TOKEN or LUMIRA_XXL_JOB_ACCESS_TOKEN for xxl job token in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_XXL_JOB_ACCESS_TOKEN, XXL_JOB_ACCESS_TOKEN, XXL_JOB_ADMIN_ACCESS_TOKEN
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 33. platform-events / xxl job token

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-events (ready)
- Dependencies: none
- Reason: must be at least 32 characters
- Action: Set XXL_JOB_ACCESS_TOKEN or XXL_JOB_ADMIN_ACCESS_TOKEN or LUMIRA_XXL_JOB_ACCESS_TOKEN for xxl job token in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_XXL_JOB_ACCESS_TOKEN, XXL_JOB_ACCESS_TOKEN, XXL_JOB_ADMIN_ACCESS_TOKEN
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 34. platform-owners / ai service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set AI_SERVICE_BASE_URL or LUMIRA_AI_SERVICE_BASE_URL or LUMIRA_AI_BASE_URL for ai service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: AI_SERVICE_BASE_URL, LUMIRA_AI_BASE_URL, LUMIRA_AI_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 35. platform-owners / ai service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set AI_SERVICE_BASE_URL or LUMIRA_AI_SERVICE_BASE_URL or LUMIRA_AI_BASE_URL for ai service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: AI_SERVICE_BASE_URL, LUMIRA_AI_BASE_URL, LUMIRA_AI_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 36. platform-owners / auth service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set AUTH_SERVICE_BASE_URL or LUMIRA_AUTH_SERVICE_BASE_URL for auth service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: AUTH_SERVICE_BASE_URL, LUMIRA_AUTH_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 37. platform-owners / auth service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set AUTH_SERVICE_BASE_URL or LUMIRA_AUTH_SERVICE_BASE_URL for auth service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: AUTH_SERVICE_BASE_URL, LUMIRA_AUTH_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 38. platform-owners / file service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set FILE_SERVICE_BASE_URL or LUMIRA_FILE_SERVICE_BASE_URL for file service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: FILE_SERVICE_BASE_URL, LUMIRA_FILE_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 39. platform-owners / file service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set FILE_SERVICE_BASE_URL or LUMIRA_FILE_SERVICE_BASE_URL for file service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: FILE_SERVICE_BASE_URL, LUMIRA_FILE_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 40. platform-owners / job executor

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set JOB_EXECUTOR_BASE_URL or LUMIRA_JOB_EXECUTOR_BASE_URL for job executor in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: JOB_EXECUTOR_BASE_URL, LUMIRA_JOB_EXECUTOR_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 41. platform-owners / job executor

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set JOB_EXECUTOR_BASE_URL or LUMIRA_JOB_EXECUTOR_BASE_URL for job executor in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: JOB_EXECUTOR_BASE_URL, LUMIRA_JOB_EXECUTOR_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 42. platform-owners / localization service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set LOCALIZATION_SERVICE_BASE_URL or LUMIRA_LOCALIZATION_SERVICE_BASE_URL for localization service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LOCALIZATION_SERVICE_BASE_URL, LUMIRA_LOCALIZATION_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 43. platform-owners / localization service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set LOCALIZATION_SERVICE_BASE_URL or LUMIRA_LOCALIZATION_SERVICE_BASE_URL for localization service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LOCALIZATION_SERVICE_BASE_URL, LUMIRA_LOCALIZATION_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 44. platform-owners / message service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set MESSAGE_SERVICE_BASE_URL or LUMIRA_MESSAGE_SERVICE_BASE_URL for message service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_MESSAGE_SERVICE_BASE_URL, MESSAGE_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 45. platform-owners / message service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set MESSAGE_SERVICE_BASE_URL or LUMIRA_MESSAGE_SERVICE_BASE_URL for message service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_MESSAGE_SERVICE_BASE_URL, MESSAGE_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 46. platform-owners / payment service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set PAYMENT_SERVICE_BASE_URL or LUMIRA_PAYMENT_SERVICE_BASE_URL for payment service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_PAYMENT_SERVICE_BASE_URL, PAYMENT_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 47. platform-owners / payment service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set PAYMENT_SERVICE_BASE_URL or LUMIRA_PAYMENT_SERVICE_BASE_URL for payment service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_PAYMENT_SERVICE_BASE_URL, PAYMENT_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 48. platform-owners / plugin service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set PLUGIN_SERVICE_BASE_URL or LUMIRA_PLUGIN_SERVICE_BASE_URL for plugin service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_PLUGIN_SERVICE_BASE_URL, PLUGIN_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 49. platform-owners / plugin service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set PLUGIN_SERVICE_BASE_URL or LUMIRA_PLUGIN_SERVICE_BASE_URL for plugin service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_PLUGIN_SERVICE_BASE_URL, PLUGIN_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 50. platform-owners / system service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set SYSTEM_SERVICE_BASE_URL or LUMIRA_SYSTEM_SERVICE_BASE_URL for system service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_SYSTEM_SERVICE_BASE_URL, SYSTEM_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 51. platform-owners / system service

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-platform-owners (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set SYSTEM_SERVICE_BASE_URL or LUMIRA_SYSTEM_SERVICE_BASE_URL for system service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: LUMIRA_SYSTEM_SERVICE_BASE_URL, SYSTEM_SERVICE_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 52. release-infra / backend base url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set LUMIRA_BASE_URL or DEPLOY_CHECK_BASE_URL for backend base url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 53. release-infra / backend base url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set LUMIRA_BASE_URL or DEPLOY_CHECK_BASE_URL for backend base url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 54. release-infra / cors origins

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set CORS_ALLOWED_ORIGIN_PATTERNS or SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS for cors origins in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: CORS_ALLOWED_ORIGIN_PATTERNS, SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 55. release-infra / database password

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set DB_PASSWORD or SPRING_DATASOURCE_PASSWORD or MYSQL_PASSWORD for database password in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: DB_PASSWORD, MYSQL_PASSWORD, SPRING_DATASOURCE_PASSWORD
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 56. release-infra / database password

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: must be at least 16 characters
- Action: Set DB_PASSWORD or SPRING_DATASOURCE_PASSWORD or MYSQL_PASSWORD for database password in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: DB_PASSWORD, MYSQL_PASSWORD, SPRING_DATASOURCE_PASSWORD
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 57. release-infra / database url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set DB_URL or SPRING_DATASOURCE_URL for database url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: DB_URL, SPRING_DATASOURCE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 58. release-infra / database username

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set DB_USERNAME or SPRING_DATASOURCE_USERNAME or MYSQL_USER for database username in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: DB_USERNAME, MYSQL_USER, SPRING_DATASOURCE_USERNAME
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 59. release-infra / field secret

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set FIELD_SECRET or SAAS_SECURITY_FIELD_SECRET for field secret in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: FIELD_SECRET, SAAS_SECURITY_FIELD_SECRET
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 60. release-infra / field secret

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: must be at least 32 characters
- Action: Set FIELD_SECRET or SAAS_SECURITY_FIELD_SECRET for field secret in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: FIELD_SECRET, SAAS_SECURITY_FIELD_SECRET
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 61. release-infra / frontend base url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set PLAYWRIGHT_BASE_URL or FRONTEND_BASE_URL for frontend base url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: FRONTEND_BASE_URL, PLAYWRIGHT_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 62. release-infra / frontend base url

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: must use HTTPS for production-equivalent evidence
- Action: Set PLAYWRIGHT_BASE_URL or FRONTEND_BASE_URL for frontend base url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: FRONTEND_BASE_URL, PLAYWRIGHT_BASE_URL
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 63. release-infra / jwt secret

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set JWT_SECRET or SAAS_SECURITY_JWT_SECRET for jwt secret in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: JWT_SECRET, SAAS_SECURITY_JWT_SECRET
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 64. release-infra / jwt secret

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: must be at least 32 characters
- Action: Set JWT_SECRET or SAAS_SECURITY_JWT_SECRET for jwt secret in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: JWT_SECRET, SAAS_SECURITY_JWT_SECRET
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 65. release-infra / redis host

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: release-config
- Batch: p0-release-config-release-infra (ready)
- Dependencies: none
- Reason: placeholder value is not allowed
- Action: Set REDIS_HOST or SPRING_DATA_REDIS_HOST for redis host in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Env keys: REDIS_HOST, SPRING_DATA_REDIS_HOST
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## 66. release-infra / docker-blocker-1

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: docker
- Batch: p0-docker-release-infra (ready)
- Dependencies: none
- Reason: lumira-server: docker build failed after 3 attempt(s) with transient registry/network error status 1
- Action: Resolve Docker image evidence blocker and rerun `node scripts/ddd-docker-build-evidence.mjs`; for Docker Hub/network failures set `DDD_DOCKER_MAVEN_IMAGE`, `DDD_DOCKER_JRE_IMAGE`, `DDD_DOCKER_NODE_IMAGE`, and `DDD_DOCKER_NGINX_IMAGE` to trusted registry mirror images. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
- Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`

## 67. release-infra / docker-blocker-2

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: docker
- Batch: p0-docker-release-infra (ready)
- Dependencies: none
- Reason: frontend: docker build failed after 3 attempt(s) with transient registry/network error status 1
- Action: Resolve Docker image evidence blocker and rerun `node scripts/ddd-docker-build-evidence.mjs`; for Docker Hub/network failures set `DDD_DOCKER_MAVEN_IMAGE`, `DDD_DOCKER_JRE_IMAGE`, `DDD_DOCKER_NODE_IMAGE`, and `DDD_DOCKER_NGINX_IMAGE` to trusted registry mirror images. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
- Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`

## 68. release-infra / docker-image-frontend-failed

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: docker
- Batch: p0-docker-release-infra (ready)
- Dependencies: none
- Reason: docker build failed after 3 attempt(s) with transient registry/network error status 1
- Action: Fix Docker image build/inspect failure and regenerate image evidence with `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`; for transient registry failures configure `DDD_DOCKER_*_IMAGE` mirror overrides and rerun. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
- Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`

## 69. release-infra / docker-image-lumira-server-failed

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: docker
- Batch: p0-docker-release-infra (ready)
- Dependencies: none
- Reason: docker build failed after 3 attempt(s) with transient registry/network error status 1
- Action: Fix Docker image build/inspect failure and regenerate image evidence with `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`; for transient registry failures configure `DDD_DOCKER_*_IMAGE` mirror overrides and rerun. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
- Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`

## 70. release-infra / runtime-readiness-contract-1

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: runtime-readiness
- Batch: p0-runtime-readiness-release-infra (ready)
- Dependencies: none
- Reason: runtime readiness productionEquivalence.strict must be true for strict release evidence
- Action: Fix runtime readiness artifact contract issues and regenerate summary.json with `node scripts/ddd-runtime-readiness-smoke.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL
- Commands:
  - `node scripts/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`

## 71. release-infra / runtime-readiness-contract-2

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: runtime-readiness
- Batch: p0-runtime-readiness-release-infra (ready)
- Dependencies: none
- Reason: runtime readiness productionEquivalence.https must be true for strict release evidence
- Action: Fix runtime readiness artifact contract issues and regenerate summary.json with `node scripts/ddd-runtime-readiness-smoke.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL
- Commands:
  - `node scripts/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`

## 72. release-infra / runtime-readiness-contract-3

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: runtime-readiness
- Batch: p0-runtime-readiness-release-infra (ready)
- Dependencies: none
- Reason: runtime readiness productionEquivalence.localOnly must be false for strict release evidence
- Action: Fix runtime readiness artifact contract issues and regenerate summary.json with `node scripts/ddd-runtime-readiness-smoke.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL
- Commands:
  - `node scripts/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`

## 73. release-infra / runtime-readiness-contract-4

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: runtime-readiness
- Batch: p0-runtime-readiness-release-infra (ready)
- Dependencies: none
- Reason: runtime readiness productionEquivalence.deploymentEvidence is required
- Action: Fix runtime readiness artifact contract issues and regenerate summary.json with `node scripts/ddd-runtime-readiness-smoke.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL
- Commands:
  - `node scripts/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`

## 74. release-owner / manifest-missing-no-explain-json-files-in-tmp-ddd-explain

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: manifest
- Batch: p0-manifest-release-owner (ready)
- Dependencies: none
- Reason: no explain JSON files in tmp\ddd-explain
- Action: Regenerate the missing evidence artifact, then rerun `node scripts/ddd-release-evidence-manifest.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_RELEASE_MANIFEST_STRICT
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`

## 75. release-performance / performance-actual-shape-1

- Closure kind: RUN_NOW_LOCAL
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: authenticated performance actual productionEquivalence.strict must be true for strict release evidence
- Action: Fix authenticated performance actual artifact shape and rerun the smoke.
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 76. release-performance / performance-actual-shape-2

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: authenticated performance actual productionEquivalence.https must be true for strict release evidence
- Action: Fix authenticated performance actual artifact shape and rerun the smoke.
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 77. release-performance / performance-actual-shape-3

- Closure kind: RUN_NOW_LOCAL
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence
- Action: Fix authenticated performance actual artifact shape and rerun the smoke.
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 78. release-performance / performance-actual-shape-4

- Closure kind: RUN_NOW_LOCAL
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: authenticated performance actual productionEquivalence.deploymentEvidence is required
- Action: Fix authenticated performance actual artifact shape and rerun the smoke.
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 79. release-performance / performance-baseline-metadata-5

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: strict release baseline requires baselineType=authenticated-runtime
- Action: Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 80. release-performance / performance-baseline-metadata-6

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: acceptedAt must be an ISO timestamp
- Action: Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 81. release-performance / performance-baseline-metadata-7

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: acceptedBy is required
- Action: Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 82. release-performance / performance-baseline-metadata-8

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: sourceArtifact is required
- Action: Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 83. release-performance / performance-baseline-metadata-9

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: sourceSha256 must be a SHA-256 hex digest
- Action: Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 84. ai / ai-owner-gateway

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: ai-runtime
- Batch: p1-ai-runtime-ai (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: ownerGateway status=CONFIGURED configuredOwners=0
- Action: Configure and verify remote AI owner gateways for IAM/File/Platform integrations.
- Env keys: DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE, LUMIRA_AI_OWNER_FILE_BASE_URL, LUMIRA_AI_OWNER_IAM_BASE_URL, LUMIRA_AI_OWNER_PLATFORM_BASE_URL
- Commands:
  - `node scripts/ddd-ai-runtime-drill.mjs`
- Expected artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`

## 85. ai / ai-provider-runtime

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: ai-runtime
- Batch: p1-ai-runtime-ai (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: provider status=CONFIGURED remoteConfigured=false
- Action: Configure and verify a remote AI provider runtime; strict release must not rely on local fallback.
- Env keys: DDD_AI_EXPECT_PROVIDER_REMOTE, LUMIRA_AI_PROVIDER, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL
- Commands:
  - `node scripts/ddd-ai-runtime-drill.mjs`
- Expected artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`

## 86. ai / ai-runtime-base-url

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: ai-runtime
- Batch: p1-ai-runtime-ai (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: missing production-equivalent AI base URL
- Action: Run AI runtime drill against an HTTPS non-local AI runtime base URL.
- Env keys: BASE_URL, DEPLOY_CHECK_BASE_URL, LUMIRA_AI_BASE_URL, LUMIRA_BASE_URL
- Commands:
  - `node scripts/ddd-ai-runtime-drill.mjs`
- Expected artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`

## 87. frontend / frontend-deployed-expectation

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: frontend-smoke
- Batch: p1-frontend-smoke-frontend (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: strict release requires deployed frontend smoke expectation
- Action: Set DDD_FRONTEND_EXPECT_DEPLOYED=true for strict deployed frontend smoke evidence.
- Env keys: DDD_FRONTEND_EXPECT_DEPLOYED
- Commands:
  - `node scripts/ddd-frontend-playwright-smoke.mjs`
  - `node scripts/ddd-frontend-smoke-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/frontend/frontend-smoke.json`
  - `artifacts/ddd/frontend/playwright-smoke-results.json`

## 88. file-owner / file-processing-production-equivalence

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: business-e2e
- Batch: p1-business-e2e-file-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: strict file processing E2E requires HTTPS baseUrl evidence; strict file processing E2E requires non-local baseUrl, got http://127.0.0.1:8080
- Action: Regenerate File processing E2E smoke against an HTTPS non-local production-equivalent backend with real storage and job token evidence using `node scripts/ddd-file-processing-e2e-smoke.mjs`.
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, LUMIRA_JOB_INTERNAL_TOKEN, LUMIRA_UPLOAD_STORAGE_ROOT
- Commands:
  - `node scripts/ddd-file-processing-e2e-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/file/file-processing-e2e.json`

## 89. job-owner / job-e2e-production-equivalence

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: business-e2e
- Batch: p1-business-e2e-job-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: strict job E2E requires HTTPS baseUrl evidence; strict job E2E requires non-local baseUrl, got http://127.0.0.1:8080
- Action: Regenerate Job/internal E2E smoke against HTTPS non-local owner endpoints with the release job token using `node scripts/ddd-job-e2e-smoke.mjs`.
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, LUMIRA_JOB_INTERNAL_TOKEN
- Commands:
  - `node scripts/ddd-job-e2e-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/jobs/job-e2e-smoke.json`

## 90. payment-owner / payment-webhook-production-equivalence

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: business-e2e
- Batch: p1-business-e2e-payment-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: strict payment webhook E2E requires HTTPS baseUrl evidence; strict payment webhook E2E requires non-local baseUrl, got http://127.0.0.1:8080
- Action: Regenerate Payment webhook E2E smoke against an HTTPS non-local webhook URL with provider sandbox or deployment evidence using `node scripts/ddd-payment-webhook-e2e-smoke.mjs`.
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, PAYMENT_PUBLIC_BASE_URL
- Commands:
  - `node scripts/ddd-payment-webhook-e2e-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/payment/payment-webhook-e2e.json`

## 91. ai-owner / AI

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-ai-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: AI rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise AI provider disablement, knowledge index job pause, and document index rebuild/degraded chat transcript evidence. Required evidence: AI provider disablement or fallback configuration evidence; knowledge index job pause/resume command or job output; document index rebuild or retry evidence; degraded chat/search transcript after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 92. auth-owner / Auth

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-auth-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: Auth rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise auth adapter rollback with session TTL compatibility, login smoke, and forced logout/keepalive evidence. Required evidence: login smoke result after adapter rollback; session TTL compatibility evidence; forced logout or keepalive behavior evidence; auth readiness/health response after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 93. file-owner / File

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-file-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: File rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise file processing pause, stable object-key access, and task rerun by id; attach upload, processing row, and storage evidence. Required evidence: file processing pause/resume command or job output; stable object-key read evidence after rollback; processing task rerun by id with final state; storage artifact or upload row proving access continuity. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 94. iam-owner / IAM

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-iam-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: IAM rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise permission snapshot rollback, cache invalidation, and IAM v2-to-v1 adapter fallback; attach readiness, audit, and cache evidence. Required evidence: permission snapshot version before and after rollback; cache invalidation or version bump evidence; IAM v2 readiness/health response after rollback; audit entry or command log for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 95. job-owner / Job

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-job-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: Job rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise XXL-JOB handler disablement and manual owner internal endpoint fallback; attach dashboard, token, and endpoint evidence. Required evidence: XXL-JOB handler disablement or dashboard evidence; manual owner internal endpoint fallback result; internal job token provenance or redacted request evidence; job readiness/metrics response after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 96. localization-owner / Localization

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-localization-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: Localization rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise localization release rollback and runtime bundle cache clear; attach release id, bundle metrics, and audit evidence. Required evidence: localization release id before and after rollback; runtime bundle cache clear evidence; bundle request or metrics proving rolled-back release is served; localization audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 97. message-owner / Message

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-message-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: Message rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise message relay pause, monolith-compatible delivery fallback, and idempotent replay; attach relay and message state evidence. Required evidence: message relay pause/resume command or job output; delivery fallback evidence for at least one notice; idempotent replay result with duplicate-safe state; message readiness/metrics response after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 98. payment-owner / Payment

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-payment-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: Payment rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise payment webhook route fallback and idempotent event replay; attach provider routing, webhook metrics, and order trace evidence. Required evidence: payment provider route fallback configuration evidence; webhook idempotent replay result; order status trace before and after replay; webhook metrics or audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 99. platform-owner / Platform

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-platform-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: Platform rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise platform config/runtime appearance rollback and cache clear; attach bootstrap/config version and audit evidence. Required evidence: runtime appearance/config version before and after rollback; cache clear or version invalidation evidence; bootstrap response using the rolled-back config; platform audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 100. plugin-owner / Plugin

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-plugin-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance
- Reason: Plugin rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise tenant plugin disable/version rollback and bootstrap projection rebuild; attach audit and tenant projection evidence. Required evidence: tenant plugin disable or version rollback command output; bootstrap projection rebuild evidence; tenant plugin projection row before and after rollback; plugin audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 101. database / ai-knowledge-index-retry.json

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P2
- Source: explain
- Batch: p2-explain-database (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Reason: missing required EXPLAIN artifact
- Action: Run production-equivalent MySQL EXPLAIN collection with `node scripts/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`.
- Env keys: DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT, DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE, MYSQL_CLI, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PASSWORD, MYSQL_PORT, MYSQL_USER
- Commands:
  - `node scripts/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/explain-gate-report.json`
  - `tmp/ddd-explain/*.json`

## 102. database / message-archive-total.json

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P2
- Source: explain
- Batch: p2-explain-database (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Reason: missing required EXPLAIN artifact
- Action: Run production-equivalent MySQL EXPLAIN collection with `node scripts/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`.
- Env keys: DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT, DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE, MYSQL_CLI, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PASSWORD, MYSQL_PORT, MYSQL_USER
- Commands:
  - `node scripts/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/explain-gate-report.json`
  - `tmp/ddd-explain/*.json`

## 103. database / message-unread-count.json

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P2
- Source: explain
- Batch: p2-explain-database (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Reason: missing required EXPLAIN artifact
- Action: Run production-equivalent MySQL EXPLAIN collection with `node scripts/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`.
- Env keys: DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT, DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE, MYSQL_CLI, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PASSWORD, MYSQL_PORT, MYSQL_USER
- Commands:
  - `node scripts/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/explain-gate-report.json`
  - `tmp/ddd-explain/*.json`

## 104. database / message-visible-list.json

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P2
- Source: explain
- Batch: p2-explain-database (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Reason: missing required EXPLAIN artifact
- Action: Run production-equivalent MySQL EXPLAIN collection with `node scripts/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`.
- Env keys: DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT, DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE, MYSQL_CLI, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PASSWORD, MYSQL_PORT, MYSQL_USER
- Commands:
  - `node scripts/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/explain-gate-report.json`
  - `tmp/ddd-explain/*.json`

## 105. database / platform-outbox-owner-relay-file.json

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P2
- Source: explain
- Batch: p2-explain-database (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Reason: missing required EXPLAIN artifact
- Action: Run production-equivalent MySQL EXPLAIN collection with `node scripts/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`.
- Env keys: DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT, DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE, MYSQL_CLI, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PASSWORD, MYSQL_PORT, MYSQL_USER
- Commands:
  - `node scripts/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/explain-gate-report.json`
  - `tmp/ddd-explain/*.json`

## 106. database / platform-outbox-owner-relay-message.json

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P2
- Source: explain
- Batch: p2-explain-database (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Reason: missing required EXPLAIN artifact
- Action: Run production-equivalent MySQL EXPLAIN collection with `node scripts/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`.
- Env keys: DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT, DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE, MYSQL_CLI, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PASSWORD, MYSQL_PORT, MYSQL_USER
- Commands:
  - `node scripts/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/explain-gate-report.json`
  - `tmp/ddd-explain/*.json`

## 107. database / platform-runtime-appearance.json

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P2
- Source: explain
- Batch: p2-explain-database (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Reason: missing required EXPLAIN artifact
- Action: Run production-equivalent MySQL EXPLAIN collection with `node scripts/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`.
- Env keys: DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT, DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE, MYSQL_CLI, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PASSWORD, MYSQL_PORT, MYSQL_USER
- Commands:
  - `node scripts/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/explain-gate-report.json`
  - `tmp/ddd-explain/*.json`

## 108. database / plugin-bootstrap.json

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P2
- Source: explain
- Batch: p2-explain-database (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Reason: missing required EXPLAIN artifact
- Action: Run production-equivalent MySQL EXPLAIN collection with `node scripts/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`.
- Env keys: DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT, DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE, MYSQL_CLI, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PASSWORD, MYSQL_PORT, MYSQL_USER
- Commands:
  - `node scripts/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/explain-gate-report.json`
  - `tmp/ddd-explain/*.json`

## 109. database / orchestrator-preflight-migration-runtime-evidence

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P3
- Source: orchestrator
- Batch: p3-orchestrator-database (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Reason: missing migration drill env: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_EVIDENCE
- Action: Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node scripts/ddd-release-evidence-orchestrator.mjs`.
- Env keys: DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED
- Commands:
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-evidence-gate.json`

## 110. frontend / orchestrator-preflight-frontend-runtime-base-url

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P3
- Source: orchestrator
- Batch: p3-orchestrator-frontend (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Reason: missing deployed frontend base URL
- Action: Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node scripts/ddd-release-evidence-orchestrator.mjs`.
- Env keys: FRONTEND_BASE_URL, PLAYWRIGHT_BASE_URL
- Commands:
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-evidence-gate.json`

## 111. release-owner / orchestrator-run-mode

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P3
- Source: orchestrator
- Batch: p3-orchestrator-release-owner (blocked)
- Dependencies: p0-release-env-lint-release-infra, p0-release-config-ai-owner, p0-release-config-payment-owner, p0-release-config-platform-events, p0-release-config-platform-owners, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-release-owner, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-frontend-smoke-frontend, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Reason: strict release requires run mode report, got plan
- Action: Run the release evidence orchestrator in strict run mode after preflight blockers are resolved with `node scripts/ddd-release-evidence-orchestrator.mjs`.
- Env keys: DDD_RELEASE_EVIDENCE_STRICT
- Commands:
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-evidence-gate.json`

