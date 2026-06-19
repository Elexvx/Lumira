# DDD Release Env Fill Priority

Generated at: 2026-06-19T18:19:45.629Z
Status: NOT_READY
Release gate blockers: 94
Owners with unresolved keys: 15
Run now owners: 3
Waiting owners: 12
Unique unresolved template env keys: 29
Unresolved owner assignments: 65
Filled owner assignments: 64
Placeholder owner assignments: 0
Missing owner assignments: 1

## 1. release-infra

- Priority: RUN_NOW
- Unresolved env keys: 5
- Fill status: filled=5, placeholder=0, missing=0
- Ready batches: p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: p3-orchestrator-release-infra
- Fill keys:
  - `BASE_URL` (filled)
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
  - `LUMIRA_BASE_URL` (filled)
  - `PLAYWRIGHT_BASE_URL` (filled)
- Rerun after fill:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-runtime-readiness-smoke.mjs`
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`

## 2. release-performance

- Priority: RUN_NOW
- Unresolved env keys: 4
- Fill status: filled=4, placeholder=0, missing=0
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none
- Fill keys:
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY` (filled)
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT` (filled)
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`

## 3. lumira-ui

- Priority: RUN_NOW
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: p0-manifest-lumira-ui
- Blocked batches: none
- Fill keys:
  - `DDD_RELEASE_CANDIDATE` (filled)
  - `PLAYWRIGHT_BASE_URL` (filled)
- Rerun after fill:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`

## 4. database

- Priority: WAITING
- Unresolved env keys: 12
- Fill status: filled=12, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Fill keys:
  - `DB_PASSWORD` (filled)
  - `DB_USERNAME` (filled)
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_EXPLAIN_DATABASE` (filled)
  - `DDD_MIGRATION_FRESH_DB_EVIDENCE` (filled)
  - `DDD_MIGRATION_FRESH_DB_VALIDATED` (filled)
  - `DDD_MIGRATION_UPGRADE_DB_EVIDENCE` (filled)
  - `DDD_MIGRATION_UPGRADE_DB_VALIDATED` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
  - `MYSQL_DATABASE` (filled)
  - `MYSQL_HOST` (filled)
  - `MYSQL_PORT` (filled)
- Rerun after fill:
  - `node bin/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`

## 5. ai

- Priority: WAITING
- Unresolved env keys: 9
- Fill status: filled=8, placeholder=0, missing=1
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai
- Fill keys:
  - `AI_SERVICE_BASE_URL` (filled)
  - `BASE_URL` (filled)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL` (filled)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL` (filled)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL` (filled)
  - `LUMIRA_AI_PROVIDER` (missing)
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY` (filled)
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL` (filled)
  - `LUMIRA_BASE_URL` (filled)
- Rerun after fill:
  - `node bin/ddd-ai-runtime-drill.mjs`

## 6. file-owner

- Priority: WAITING
- Unresolved env keys: 7
- Fill status: filled=7, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-rollback-file-owner
- Fill keys:
  - `BASE_URL` (filled)
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` (filled)
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
  - `LUMIRA_BASE_URL` (filled)
  - `SAAS_JOB_INTERNAL_TOKEN` (filled)
  - `UPLOAD_STORAGE_ROOT` (filled)
- Rerun after fill:
  - `node bin/ddd-file-processing-e2e-smoke.mjs`
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`

## 7. job-owner

- Priority: WAITING
- Unresolved env keys: 6
- Fill status: filled=6, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-business-e2e-job-owner, p1-rollback-job-owner
- Fill keys:
  - `BASE_URL` (filled)
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` (filled)
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
  - `LUMIRA_BASE_URL` (filled)
  - `SAAS_JOB_INTERNAL_TOKEN` (filled)
- Rerun after fill:
  - `node bin/ddd-job-e2e-smoke.mjs`
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`

## 8. payment-owner

- Priority: WAITING
- Unresolved env keys: 6
- Fill status: filled=6, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
- Fill keys:
  - `BASE_URL` (filled)
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` (filled)
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
  - `LUMIRA_BASE_URL` (filled)
  - `PAYMENT_PUBLIC_BASE_URL` (filled)
- Rerun after fill:
  - `node bin/ddd-payment-webhook-e2e-smoke.mjs`
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`

## 9. ai-owner

- Priority: WAITING
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-rollback-ai-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`

## 10. auth-owner

- Priority: WAITING
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-rollback-auth-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`

## 11. iam-owner

- Priority: WAITING
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-rollback-iam-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`

## 12. localization-owner

- Priority: WAITING
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-rollback-localization-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`

## 13. message-owner

- Priority: WAITING
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-rollback-message-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`

## 14. platform-owner

- Priority: WAITING
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-rollback-platform-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`

## 15. plugin-owner

- Priority: WAITING
- Unresolved env keys: 2
- Fill status: filled=2, placeholder=0, missing=0
- Ready batches: none
- Blocked batches: p1-rollback-plugin-owner
- Fill keys:
  - `DDD_EVIDENCE_OPERATOR` (filled)
  - `DDD_RELEASE_CANDIDATE` (filled)
- Rerun after fill:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`

