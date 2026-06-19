# DDD Release Env Owner Matrix

Generated at: 2026-06-19T18:09:18.921Z
Status: NOT_READY
Release gate blockers: 94
Owners: 16
Template env keys: 46
Unique unresolved template env keys: 29
Unresolved owner assignments: 67

## release-infra

- Env keys: 8
- Unresolved env keys: 5
- Ready batches: p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: p3-orchestrator-release-infra
- Expected artifacts: artifacts/ddd/build/docker-image-evidence.json, artifacts/ddd/readiness/summary.json, artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/readiness-summary.json, artifacts/ddd/release/release-evidence-gate.json
- Unresolved template env keys:
  - `BASE_URL`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `LUMIRA_BASE_URL`
  - `PLAYWRIGHT_BASE_URL`
- Template env keys:
  - `BASE_URL`
  - `DDD_DOCKER_BUILD_STRICT`
  - `DDD_DOCKER_COMMAND`
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `LUMIRA_BASE_URL`
  - `PLAYWRIGHT_BASE_URL`
- Alias mappings:
  - `DEPLOY_CHECK_BASE_URL` -> `LUMIRA_BASE_URL`
  - `FRONTEND_BASE_URL` -> `PLAYWRIGHT_BASE_URL`

## database

- Env keys: 16
- Unresolved env keys: 12
- Ready batches: p0-manifest-database
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Expected artifacts: artifacts/ddd/release/evidence-manifest.json, artifacts/ddd/release/explain-gate-report.json, artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/readiness-summary.json, artifacts/ddd/release/release-evidence-gate.json, tmp/ddd-explain/*.json
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

## lumira-ui

- Env keys: 4
- Unresolved env keys: 2
- Ready batches: p0-manifest-lumira-ui
- Blocked batches: none
- Expected artifacts: artifacts/ddd/lumira-ui/frontend-smoke.json, artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json, artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json, artifacts/ddd/release/evidence-manifest.json
- Unresolved template env keys:
  - `DDD_RELEASE_CANDIDATE`
  - `PLAYWRIGHT_BASE_URL`
- Template env keys:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_FRONTEND_EXPECT_DEPLOYED`
  - `DDD_RELEASE_CANDIDATE`
  - `PLAYWRIGHT_BASE_URL`

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

## payment-owner

- Env keys: 12
- Unresolved env keys: 6
- Ready batches: none
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
- Expected artifacts: artifacts/ddd/payment/payment-webhook-e2e.json, artifacts/ddd/rollback/rollback-drill.json
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

## ai-owner

- Env keys: 8
- Unresolved env keys: 2
- Ready batches: none
- Blocked batches: p1-rollback-ai-owner
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

