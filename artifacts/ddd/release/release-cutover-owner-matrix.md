# DDD Cutover Owner Matrix

Generated at: 2026-06-19T13:42:59.865Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
releaseEnvFileCutoverSafe: false
Owner count: 16
Blocked owners: 16

## release-infra

- Blocked items: 3
- Total items: 3
- Ready batches: p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: p3-orchestrator-release-infra
- [BLOCKED] deployable-images: Deployable backend/frontend images are built and inspected.
  - Pending items: 4
  - Lanes: deployable-image
  - Batches: p0-docker-release-infra
  - Env check groups: DDD_DOCKER_BUILD_STRICT=DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND=DDD_DOCKER_COMMAND
  - Expected artifacts: artifacts/ddd/build/docker-image-evidence.json
- [BLOCKED] evidence-integrity: Evidence manifest and final orchestrator strict rerun are clean.
  - Pending items: 5
  - Lanes: evidence-integrity, final-verification
  - Batches: p3-orchestrator-release-infra
  - Env check groups: BASE_URL=BASE_URL, LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL
  - Expected artifacts: artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/readiness-summary.json, artifacts/ddd/release/release-evidence-gate.json
- [BLOCKED] production-equivalence: Runtime and performance evidence use HTTPS non-local production-equivalent endpoints.
  - Pending items: 13
  - Lanes: production-equivalence, performance
  - Batches: p0-runtime-readiness-release-infra
  - Env check groups: DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL
  - Expected artifacts: artifacts/ddd/readiness/summary.json

## database

- Blocked items: 2
- Total items: 2
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- [BLOCKED] database-performance: Fresh production-equivalent EXPLAIN evidence has no scan/index blockers.
  - Pending items: 8
  - Lanes: database-performance
  - Batches: p2-explain-database
  - Env check groups: DB_PASSWORD=DB_PASSWORD|MYSQL_PASSWORD|SPRING_DATASOURCE_PASSWORD, DB_USERNAME=DB_USERNAME|MYSQL_USER|SPRING_DATASOURCE_USERNAME, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE=DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR=DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT=DDD_EXPLAIN_ENVIRONMENT, DDD_EXPLAIN_STRICT=DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, MYSQL_CLI=MYSQL_CLI, MYSQL_DATABASE=MYSQL_DATABASE, MYSQL_HOST=MYSQL_HOST, MYSQL_PORT=MYSQL_PORT
  - Expected artifacts: artifacts/ddd/release/explain-gate-report.json, tmp/ddd-explain/*.json
- [BLOCKED] evidence-integrity: Evidence manifest and final orchestrator strict rerun are clean.
  - Pending items: 5
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
  - Pending items: 5
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

## payment-owner

- Blocked items: 2
- Total items: 2
- Ready batches: none
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
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

## release-owner

- Blocked items: 2
- Total items: 4
- Ready batches: p0-manifest-release-owner
- Blocked batches: p3-orchestrator-release-owner
- [BLOCKED] evidence-integrity: Evidence manifest and final orchestrator strict rerun are clean.
  - Pending items: 5
  - Lanes: evidence-integrity, final-verification
  - Batches: p0-manifest-release-owner, p3-orchestrator-release-owner
  - Env check groups: DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, DDD_RELEASE_EVIDENCE_STRICT=DDD_RELEASE_EVIDENCE_STRICT, DDD_RELEASE_MANIFEST_STRICT=DDD_RELEASE_MANIFEST_STRICT
  - Expected artifacts: artifacts/ddd/release/evidence-manifest.json, artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/readiness-summary.json, artifacts/ddd/release/release-evidence-gate.json
- [BLOCKED] strict-release-gate: Strict release gate has zero blockers and no contract issues.
  - Pending items: 94
  - Lanes: none
  - Batches: none
- [PASS] data-safety: Fresh and upgrade migrations are proven with runtime metadata.
  - Pending items: 0
  - Lanes: data-safety
  - Batches: none
- [PASS] release-environment: Completed release env file and config matrix are valid.
  - Pending items: 0
  - Lanes: environment
  - Batches: none

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

## ai-owner

- Blocked items: 1
- Total items: 1
- Ready batches: none
- Blocked batches: p1-rollback-ai-owner
- [BLOCKED] rollback-safety: Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance.
  - Pending items: 10
  - Lanes: rollback-safety
  - Batches: p1-rollback-ai-owner
  - Env check groups: DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT
  - Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

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

## release-performance

- Blocked items: 1
- Total items: 1
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none
- [BLOCKED] production-equivalence: Runtime and performance evidence use HTTPS non-local production-equivalent endpoints.
  - Pending items: 13
  - Lanes: production-equivalence, performance
  - Batches: p0-authenticated-performance-release-performance
  - Env check groups: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT=DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT=DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE
  - Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json, artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json, artifacts/ddd/performance/authenticated-runtime-baseline.json

