# DDD Release Closure Wave Env Matrix

Generated at: 2026-06-18T19:37:26.213Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
Waves: 10
Unique env keys: 99

## Wave 1. release-infra / p0-release-env-lint-release-infra

- Priority: P0
- Items: 1, 2
- Item ids: release-env-lint-placeholders, release-env-lint-status
- Env keys: 55
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
  - `DDD_EXPLAIN_DATABASE`
  - `DDD_FRONTEND_DEPLOYMENT_EVIDENCE`
  - `DDD_MIGRATION_COMPLETED_AT`
  - `DDD_MIGRATION_FRESH_DB_EVIDENCE`
  - `DDD_MIGRATION_FRESH_DB_VALIDATED`
  - `DDD_MIGRATION_OPERATOR`
  - `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`
  - `DDD_MIGRATION_UPGRADE_DB_VALIDATED`
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
- Commands:
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/release/release-env-lint.json`

## Wave 2. ai-owner / p0-release-config-ai-owner

- Priority: P0
- Items: 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14
- Item ids: file owner url, iam owner url, owner internal token, platform owner url, provider api key, provider base url
- Env keys: 13
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
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## Wave 3. payment-owner / p0-release-config-payment-owner

- Priority: P0
- Items: 15, 16
- Item ids: payment public url
- Env keys: 1
  - `PAYMENT_PUBLIC_BASE_URL`
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## Wave 4. platform-events / p0-release-config-platform-events

- Priority: P0
- Items: 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33
- Item ids: event stream key, job backend url, job file url, job internal token, job message url, job payment url, job plugin url, xxl job admin, xxl job token
- Env keys: 20
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
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## Wave 5. platform-owners / p0-release-config-platform-owners

- Priority: P0
- Items: 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51
- Item ids: ai service, auth service, file service, job executor, localization service, message service, payment service, plugin service, system service
- Env keys: 19
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
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## Wave 6. release-infra / p0-release-config-release-infra

- Priority: P0
- Items: 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65
- Item ids: backend base url, cors origins, database password, database url, database username, field secret, frontend base url, jwt secret, redis host
- Env keys: 20
  - `CORS_ALLOWED_ORIGIN_PATTERNS`
  - `DB_PASSWORD`
  - `DB_URL`
  - `DB_USERNAME`
  - `DEPLOY_CHECK_BASE_URL`
  - `FIELD_SECRET`
  - `FRONTEND_BASE_URL`
  - `JWT_SECRET`
  - `LUMIRA_BASE_URL`
  - `MYSQL_PASSWORD`
  - `MYSQL_USER`
  - `PLAYWRIGHT_BASE_URL`
  - `REDIS_HOST`
  - `SAAS_SECURITY_FIELD_SECRET`
  - `SAAS_SECURITY_JWT_SECRET`
  - `SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS`
  - `SPRING_DATASOURCE_PASSWORD`
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATA_REDIS_HOST`
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`

## Wave 7. release-infra / p0-docker-release-infra

- Priority: P0
- Items: 66, 67, 68, 69
- Item ids: docker-blocker-1, docker-blocker-2, docker-image-frontend-failed, docker-image-lumira-server-failed
- Env keys: 2
  - `DDD_DOCKER_BUILD_STRICT`
  - `DDD_DOCKER_COMMAND`
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`

## Wave 8. release-infra / p0-runtime-readiness-release-infra

- Priority: P0
- Items: 70, 71, 72, 73
- Item ids: runtime-readiness-contract-1, runtime-readiness-contract-2, runtime-readiness-contract-3, runtime-readiness-contract-4
- Env keys: 4
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `LUMIRA_BASE_URL`
- Commands:
  - `node scripts/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`

## Wave 9. release-owner / p0-manifest-release-owner

- Priority: P0
- Items: 74
- Item ids: manifest-missing-no-explain-json-files-in-tmp-ddd-explain
- Env keys: 4
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_RELEASE_MANIFEST_STRICT`
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
- Blocker hints:
  - artifacts/ddd/release/evidence-manifest.json blocker: artifacts/ddd/no explain JSON files in tmp\ddd-explain

## Wave 10. release-performance / p0-authenticated-performance-release-performance

- Priority: P0
- Items: 75, 76, 77, 78, 79, 80, 81, 82, 83
- Item ids: performance-actual-shape-1, performance-actual-shape-2, performance-actual-shape-3, performance-actual-shape-4, performance-baseline-metadata-5, performance-baseline-metadata-6, performance-baseline-metadata-7, performance-baseline-metadata-8, performance-baseline-metadata-9
- Env keys: 4
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_RELEASE_CANDIDATE`
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

