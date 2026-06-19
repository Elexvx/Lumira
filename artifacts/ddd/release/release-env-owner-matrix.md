# DDD Release Env Owner Matrix

Generated at: 2026-06-18T19:37:26.213Z
Status: NOT_READY
Release gate blockers: 94
Owners: 18
Template env keys: 76
Unique unresolved template env keys: 59
Unresolved owner assignments: 143

## release-infra

- Env keys: 60
- Unresolved env keys: 57
- Ready batches: p0-docker-release-infra, p0-release-config-release-infra, p0-release-env-lint-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: none
- Expected artifacts: artifacts/ddd/build/docker-image-evidence.json, artifacts/ddd/config/release-config-evidence.json, artifacts/ddd/readiness/summary.json, artifacts/ddd/release/release-env-lint.json
- Unresolved template env keys:
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
- Alias mappings:
  - `SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS` -> `CORS_ALLOWED_ORIGIN_PATTERNS`
  - `MYSQL_PASSWORD` -> `DB_PASSWORD`
  - `SPRING_DATASOURCE_PASSWORD` -> `DB_PASSWORD`
  - `SPRING_DATASOURCE_URL` -> `DB_URL`
  - `MYSQL_USER` -> `DB_USERNAME`
  - `SPRING_DATASOURCE_USERNAME` -> `DB_USERNAME`
  - `SAAS_SECURITY_FIELD_SECRET` -> `FIELD_SECRET`
  - `SAAS_SECURITY_JWT_SECRET` -> `JWT_SECRET`
  - `DEPLOY_CHECK_BASE_URL` -> `LUMIRA_BASE_URL`
  - `FRONTEND_BASE_URL` -> `PLAYWRIGHT_BASE_URL`
  - `SPRING_DATA_REDIS_HOST` -> `REDIS_HOST`

## ai-owner

- Env keys: 15
- Unresolved env keys: 9
- Ready batches: p0-release-config-ai-owner
- Blocked batches: p1-rollback-ai-owner
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json, artifacts/ddd/rollback/rollback-drill.json
- Unresolved template env keys:
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN`
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
- Alias mappings:
  - `LUMIRA_AI_OWNER_FILE_BASE_URL` -> `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_IAM_BASE_URL` -> `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_INTERNAL_TOKEN` -> `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN`
  - `LUMIRA_AI_OWNER_PLATFORM_BASE_URL` -> `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL`
  - `LUMIRA_AI_PROVIDER_API_KEY` -> `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`
  - `LUMIRA_AI_PROVIDER_BASE_URL` -> `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`

## payment-owner

- Env keys: 12
- Unresolved env keys: 6
- Ready batches: p0-release-config-payment-owner
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json, artifacts/ddd/payment/payment-webhook-e2e.json, artifacts/ddd/rollback/rollback-drill.json
- Unresolved template env keys:
  - `BASE_URL`
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `LUMIRA_BASE_URL`
  - `PAYMENT_PUBLIC_BASE_URL`
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
- Alias mappings:
  - `DEPLOY_CHECK_BASE_URL` -> `LUMIRA_BASE_URL`

## platform-events

- Env keys: 9
- Unresolved env keys: 9
- Ready batches: p0-release-config-platform-events
- Blocked batches: none
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- Unresolved template env keys:
  - `SAAS_EVENT_REDIS_STREAM_KEY`
  - `SAAS_JOB_BACKEND_BASE_URL`
  - `SAAS_JOB_FILE_SERVICE_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN`
  - `SAAS_JOB_MESSAGE_SERVICE_BASE_URL`
  - `SAAS_JOB_PAYMENT_SERVICE_BASE_URL`
  - `SAAS_JOB_PLUGIN_SERVICE_BASE_URL`
  - `XXL_JOB_ACCESS_TOKEN`
  - `XXL_JOB_ADMIN_ADDRESSES`
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
- Alias mappings:
  - `LUMIRA_EVENT_REDIS_STREAM_KEY` -> `SAAS_EVENT_REDIS_STREAM_KEY`
  - `LUMIRA_JOB_BACKEND_BASE_URL` -> `SAAS_JOB_BACKEND_BASE_URL`
  - `LUMIRA_JOB_FILE_SERVICE_BASE_URL` -> `SAAS_JOB_FILE_SERVICE_BASE_URL`
  - `DDD_JOB_INTERNAL_TOKEN` -> `SAAS_JOB_INTERNAL_TOKEN`
  - `LUMIRA_JOB_INTERNAL_TOKEN` -> `SAAS_JOB_INTERNAL_TOKEN`
  - `LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL` -> `SAAS_JOB_MESSAGE_SERVICE_BASE_URL`
  - `LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL` -> `SAAS_JOB_PAYMENT_SERVICE_BASE_URL`
  - `LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL` -> `SAAS_JOB_PLUGIN_SERVICE_BASE_URL`
  - `LUMIRA_XXL_JOB_ACCESS_TOKEN` -> `XXL_JOB_ACCESS_TOKEN`
  - `XXL_JOB_ADMIN_ACCESS_TOKEN` -> `XXL_JOB_ACCESS_TOKEN`
  - `LUMIRA_XXL_JOB_ADMIN_ADDRESSES` -> `XXL_JOB_ADMIN_ADDRESSES`

## platform-owners

- Env keys: 9
- Unresolved env keys: 9
- Ready batches: p0-release-config-platform-owners
- Blocked batches: none
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- Unresolved template env keys:
  - `AI_SERVICE_BASE_URL`
  - `AUTH_SERVICE_BASE_URL`
  - `FILE_SERVICE_BASE_URL`
  - `JOB_EXECUTOR_BASE_URL`
  - `LOCALIZATION_SERVICE_BASE_URL`
  - `MESSAGE_SERVICE_BASE_URL`
  - `PAYMENT_SERVICE_BASE_URL`
  - `PLUGIN_SERVICE_BASE_URL`
  - `SYSTEM_SERVICE_BASE_URL`
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
- Alias mappings:
  - `LUMIRA_AI_BASE_URL` -> `AI_SERVICE_BASE_URL`
  - `LUMIRA_AI_SERVICE_BASE_URL` -> `AI_SERVICE_BASE_URL`
  - `LUMIRA_AUTH_SERVICE_BASE_URL` -> `AUTH_SERVICE_BASE_URL`
  - `LUMIRA_FILE_SERVICE_BASE_URL` -> `FILE_SERVICE_BASE_URL`
  - `LUMIRA_JOB_EXECUTOR_BASE_URL` -> `JOB_EXECUTOR_BASE_URL`
  - `LUMIRA_LOCALIZATION_SERVICE_BASE_URL` -> `LOCALIZATION_SERVICE_BASE_URL`
  - `LUMIRA_MESSAGE_SERVICE_BASE_URL` -> `MESSAGE_SERVICE_BASE_URL`
  - `LUMIRA_PAYMENT_SERVICE_BASE_URL` -> `PAYMENT_SERVICE_BASE_URL`
  - `LUMIRA_PLUGIN_SERVICE_BASE_URL` -> `PLUGIN_SERVICE_BASE_URL`
  - `LUMIRA_SYSTEM_SERVICE_BASE_URL` -> `SYSTEM_SERVICE_BASE_URL`

## release-owner

- Env keys: 5
- Unresolved env keys: 2
- Ready batches: p0-manifest-release-owner
- Blocked batches: p3-orchestrator-release-owner
- Expected artifacts: artifacts/ddd/release/evidence-manifest.json, artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/readiness-summary.json, artifacts/ddd/release/release-evidence-gate.json
- Unresolved template env keys:
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_RELEASE_EVIDENCE_STRICT`
  - `DDD_RELEASE_MANIFEST_STRICT`

## release-performance

- Env keys: 4
- Unresolved env keys: 4
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none
- Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json, artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json, artifacts/ddd/performance/authenticated-runtime-baseline.json
- Unresolved template env keys:
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_RELEASE_CANDIDATE`
- Template env keys:
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_RELEASE_CANDIDATE`

## database

- Env keys: 16
- Unresolved env keys: 12
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Expected artifacts: artifacts/ddd/release/explain-gate-report.json, artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/readiness-summary.json, artifacts/ddd/release/release-evidence-gate.json, tmp/ddd-explain/*.json
- Unresolved template env keys:
  - `DB_PASSWORD`
  - `DB_USERNAME`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_EXPLAIN_DATABASE`
  - `DDD_MIGRATION_FRESH_DB_EVIDENCE`
  - `DDD_MIGRATION_FRESH_DB_VALIDATED`
  - `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`
  - `DDD_MIGRATION_UPGRADE_DB_VALIDATED`
  - `DDD_RELEASE_CANDIDATE`
  - `MYSQL_DATABASE`
  - `MYSQL_HOST`
  - `MYSQL_PORT`
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
- Alias mappings:
  - `MYSQL_PASSWORD` -> `DB_PASSWORD`
  - `MYSQL_USER` -> `DB_USERNAME`

## file-owner

- Env keys: 13
- Unresolved env keys: 7
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-rollback-file-owner
- Expected artifacts: artifacts/ddd/file/file-processing-e2e.json, artifacts/ddd/rollback/rollback-drill.json
- Unresolved template env keys:
  - `BASE_URL`
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `LUMIRA_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN`
  - `UPLOAD_STORAGE_ROOT`
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
- Alias mappings:
  - `DEPLOY_CHECK_BASE_URL` -> `LUMIRA_BASE_URL`
  - `LUMIRA_JOB_INTERNAL_TOKEN` -> `SAAS_JOB_INTERNAL_TOKEN`
  - `LUMIRA_UPLOAD_STORAGE_ROOT` -> `UPLOAD_STORAGE_ROOT`

## job-owner

- Env keys: 12
- Unresolved env keys: 6
- Ready batches: none
- Blocked batches: p1-business-e2e-job-owner, p1-rollback-job-owner
- Expected artifacts: artifacts/ddd/jobs/job-e2e-smoke.json, artifacts/ddd/rollback/rollback-drill.json
- Unresolved template env keys:
  - `BASE_URL`
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `LUMIRA_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN`
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
- Alias mappings:
  - `DEPLOY_CHECK_BASE_URL` -> `LUMIRA_BASE_URL`
  - `LUMIRA_JOB_INTERNAL_TOKEN` -> `SAAS_JOB_INTERNAL_TOKEN`

## ai

- Env keys: 11
- Unresolved env keys: 9
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai
- Expected artifacts: artifacts/ddd/ai/ai-runtime-drill.json
- Unresolved template env keys:
  - `AI_SERVICE_BASE_URL`
  - `BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL`
  - `LUMIRA_AI_PROVIDER`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`
  - `LUMIRA_BASE_URL`
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
- Alias mappings:
  - `LUMIRA_AI_BASE_URL` -> `AI_SERVICE_BASE_URL`
  - `LUMIRA_AI_OWNER_FILE_BASE_URL` -> `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_IAM_BASE_URL` -> `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_PLATFORM_BASE_URL` -> `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL`
  - `DEPLOY_CHECK_BASE_URL` -> `LUMIRA_BASE_URL`

## auth-owner

- Env keys: 8
- Unresolved env keys: 2
- Ready batches: none
- Blocked batches: p1-rollback-auth-owner
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- Unresolved template env keys:
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`

## iam-owner

- Env keys: 8
- Unresolved env keys: 2
- Ready batches: none
- Blocked batches: p1-rollback-iam-owner
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- Unresolved template env keys:
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`

## localization-owner

- Env keys: 8
- Unresolved env keys: 2
- Ready batches: none
- Blocked batches: p1-rollback-localization-owner
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- Unresolved template env keys:
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`

## message-owner

- Env keys: 8
- Unresolved env keys: 2
- Ready batches: none
- Blocked batches: p1-rollback-message-owner
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- Unresolved template env keys:
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`

## platform-owner

- Env keys: 8
- Unresolved env keys: 2
- Ready batches: none
- Blocked batches: p1-rollback-platform-owner
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- Unresolved template env keys:
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`

## plugin-owner

- Env keys: 8
- Unresolved env keys: 2
- Ready batches: none
- Blocked batches: p1-rollback-plugin-owner
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json
- Unresolved template env keys:
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`

## frontend

- Env keys: 2
- Unresolved env keys: 1
- Ready batches: none
- Blocked batches: p1-frontend-smoke-frontend, p3-orchestrator-frontend
- Expected artifacts: artifacts/ddd/frontend/frontend-smoke.json, artifacts/ddd/frontend/playwright-smoke-results.json, artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/readiness-summary.json, artifacts/ddd/release/release-evidence-gate.json
- Unresolved template env keys:
  - `PLAYWRIGHT_BASE_URL`
- Template env keys:
  - `DDD_FRONTEND_EXPECT_DEPLOYED`
  - `PLAYWRIGHT_BASE_URL`
- Alias mappings:
  - `FRONTEND_BASE_URL` -> `PLAYWRIGHT_BASE_URL`

