# DDD Final Owner Queue

Generated at: 2026-06-19T18:19:45.629Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
Cutover allowed: false
No auto waivers: true
Owners: 16
Actionable owners: 3
Waiting owners: 13
Unique missing artifacts: 19
Unique content blockers: 0
Owner input receipt status: PENDING_OWNER_INPUT
Owner input receipt cutover ready: false
Owner input receipt required inputs: 0
Owner input receipt pending owners: 0
Owner input receipt missing criteria: 1
Next executable owner: release-infra
Next executable command: DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh
Queue order: ACTIONABLE owners first, then WAITING owners.

## Fast Path

- Objective: Run the next actionable owner queue through strict evidence refresh and final go/no-go without bypassing safety gates.
- Blocked until: Required owner env keys and expected evidence artifacts are available in a permission-safe release env file.
- Owner: release-infra
- Queue order: 1
- First command: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- Release env file required: true
- Env keys: 10
- Missing artifacts: 5
- Commands:
  - `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-runtime-readiness-smoke.mjs`
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
  - `node bin/ddd-release-readiness-summary.mjs`
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## Safety Signals

- releaseEnvFileCutoverSafe: false
- releaseEnvFile: ready=true status=PASS inputKind=release-env-file envFilePresent=true
  - securityChecked=true permissionSafe=true mode=666 requiredMode=600 reason=env-file permissionCheckSkipped=true
  - pendingActions=none

## Owner Input Receipt

- Status: PENDING_OWNER_INPUT
- Cutover ready: false
- Required owner inputs: 0
- Owners: 0
- Ready owners: 0
- Pending owners: 0
- Artifact: artifacts/ddd/release/release-owner-input-receipt.json
- Markdown: artifacts/ddd/release/release-owner-input-receipt.md
- Missing criteria:
  - releaseEnvReadinessStatus
- Pending owner inputs:
  - none

## release-infra

- Queue order: 1
- Execution order hint: 1
- Queue status: ACTIONABLE
- Can execute: true
- Cutover items: deployable-images, evidence-integrity, production-equivalence, strict-release-gate
- Ready batches: p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: p3-orchestrator-release-infra
- Closure waves: 1, 2
- Commands: 7
- Env keys: 10
- Missing artifacts: 5
- Content blockers: 0
- Stop reasons: 4
- First command: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- Env key names:
  - `BASE_URL`
  - `DDD_DOCKER_BUILD_STRICT`
  - `DDD_DOCKER_COMMAND`
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DEPLOY_CHECK_BASE_URL`
  - `FRONTEND_BASE_URL`
  - `LUMIRA_BASE_URL`
  - `PLAYWRIGHT_BASE_URL`
- Missing artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`
  - `artifacts/ddd/readiness/summary.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## lumira-ui

- Queue order: 2
- Execution order hint: 3
- Queue status: ACTIONABLE
- Can execute: true
- Cutover items: evidence-integrity, strict-release-gate
- Ready batches: p0-manifest-lumira-ui
- Blocked batches: none
- Closure waves: 3
- Commands: 5
- Env keys: 4
- Missing artifacts: 4
- Content blockers: 0
- Stop reasons: 3
- First command: `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_FRONTEND_EXPECT_DEPLOYED`
  - `DDD_RELEASE_CANDIDATE`
  - `PLAYWRIGHT_BASE_URL`
- Missing artifacts:
  - `artifacts/ddd/lumira-ui/frontend-smoke.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json`
  - `artifacts/ddd/release/evidence-manifest.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## release-performance

- Queue order: 3
- Execution order hint: 4
- Queue status: ACTIONABLE
- Can execute: true
- Cutover items: production-equivalence, strict-release-gate
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none
- Closure waves: 4
- Commands: 9
- Env keys: 12
- Missing artifacts: 3
- Content blockers: 0
- Stop reasons: 12
- First command: `node bin/ddd-authenticated-performance-smoke.mjs`
- Env key names:
  - `BASE_URL`
  - `DDD_AUTH_PASSWORD`
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE`
  - `DDD_AUTH_PERF_ENVIRONMENT`
  - `DDD_AUTH_USERNAME`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DEPLOY_CHECK_BASE_URL`
  - `LUMIRA_BASE_URL`
- Missing artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## ai

- Queue order: 4
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: runtime-business-acceptance, strict-release-gate
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai
- Closure waves: none
- Commands: 1
- Env keys: 12
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 2
- First command: `node bin/ddd-ai-runtime-drill.mjs`
- Env key names:
  - `BASE_URL`
  - `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE`
  - `DDD_AI_EXPECT_PROVIDER_REMOTE`
  - `DEPLOY_CHECK_BASE_URL`
  - `LUMIRA_AI_BASE_URL`
  - `LUMIRA_AI_OWNER_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_PLATFORM_BASE_URL`
  - `LUMIRA_AI_PROVIDER`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`
  - `LUMIRA_BASE_URL`
- Missing artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## ai-owner

- Queue order: 5
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety, strict-release-gate
- Ready batches: none
- Blocked batches: p1-rollback-ai-owner
- Closure waves: none
- Commands: 3
- Env keys: 8
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 2
- First command: `node bin/ddd-rollback-deferral-template.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Missing artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## auth-owner

- Queue order: 6
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety, strict-release-gate
- Ready batches: none
- Blocked batches: p1-rollback-auth-owner
- Closure waves: none
- Commands: 3
- Env keys: 8
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 2
- First command: `node bin/ddd-rollback-deferral-template.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Missing artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## database

- Queue order: 7
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: database-performance, evidence-integrity, strict-release-gate
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Closure waves: none
- Commands: 4
- Env keys: 16
- Missing artifacts: 5
- Content blockers: 0
- Stop reasons: 3
- First command: `node bin/ddd-collect-explain.mjs`
- Env key names:
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
  - `MYSQL_PASSWORD`
  - `MYSQL_PORT`
  - `MYSQL_USER`
- Missing artifacts:
  - `artifacts/ddd/release/explain-gate-report.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `tmp/ddd-explain/*.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## file-owner

- Queue order: 8
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety, runtime-business-acceptance, strict-release-gate
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-rollback-file-owner
- Closure waves: none
- Commands: 4
- Env keys: 14
- Missing artifacts: 2
- Content blockers: 0
- Stop reasons: 3
- First command: `node bin/ddd-file-processing-e2e-smoke.mjs`
- Env key names:
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
  - `DEPLOY_CHECK_BASE_URL`
  - `LUMIRA_BASE_URL`
  - `LUMIRA_JOB_INTERNAL_TOKEN`
  - `LUMIRA_UPLOAD_STORAGE_ROOT`
- Missing artifacts:
  - `artifacts/ddd/file/file-processing-e2e.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## iam-owner

- Queue order: 9
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety, strict-release-gate
- Ready batches: none
- Blocked batches: p1-rollback-iam-owner
- Closure waves: none
- Commands: 3
- Env keys: 8
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 2
- First command: `node bin/ddd-rollback-deferral-template.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Missing artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## job-owner

- Queue order: 10
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety, runtime-business-acceptance, strict-release-gate
- Ready batches: none
- Blocked batches: p1-business-e2e-job-owner, p1-rollback-job-owner
- Closure waves: none
- Commands: 4
- Env keys: 13
- Missing artifacts: 2
- Content blockers: 0
- Stop reasons: 3
- First command: `node bin/ddd-job-e2e-smoke.mjs`
- Env key names:
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
  - `DEPLOY_CHECK_BASE_URL`
  - `LUMIRA_BASE_URL`
  - `LUMIRA_JOB_INTERNAL_TOKEN`
- Missing artifacts:
  - `artifacts/ddd/jobs/job-e2e-smoke.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## localization-owner

- Queue order: 11
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety, strict-release-gate
- Ready batches: none
- Blocked batches: p1-rollback-localization-owner
- Closure waves: none
- Commands: 3
- Env keys: 8
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 2
- First command: `node bin/ddd-rollback-deferral-template.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Missing artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## message-owner

- Queue order: 12
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety, strict-release-gate
- Ready batches: none
- Blocked batches: p1-rollback-message-owner
- Closure waves: none
- Commands: 3
- Env keys: 8
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 2
- First command: `node bin/ddd-rollback-deferral-template.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Missing artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## payment-owner

- Queue order: 13
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety, runtime-business-acceptance, strict-release-gate
- Ready batches: none
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
- Closure waves: none
- Commands: 4
- Env keys: 13
- Missing artifacts: 2
- Content blockers: 0
- Stop reasons: 3
- First command: `node bin/ddd-payment-webhook-e2e-smoke.mjs`
- Env key names:
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
  - `DEPLOY_CHECK_BASE_URL`
  - `LUMIRA_BASE_URL`
  - `PAYMENT_PUBLIC_BASE_URL`
- Missing artifacts:
  - `artifacts/ddd/payment/payment-webhook-e2e.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## platform-owner

- Queue order: 14
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety, strict-release-gate
- Ready batches: none
- Blocked batches: p1-rollback-platform-owner
- Closure waves: none
- Commands: 3
- Env keys: 8
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 2
- First command: `node bin/ddd-rollback-deferral-template.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Missing artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## plugin-owner

- Queue order: 15
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: rollback-safety, strict-release-gate
- Ready batches: none
- Blocked batches: p1-rollback-plugin-owner
- Closure waves: none
- Commands: 3
- Env keys: 8
- Missing artifacts: 1
- Content blockers: 0
- Stop reasons: 2
- First command: `node bin/ddd-rollback-deferral-template.mjs`
- Env key names:
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT`
- Missing artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## release-owner

- Queue order: 16
- Execution order hint: none
- Queue status: WAITING
- Can execute: false
- Cutover items: evidence-integrity, strict-release-gate
- Ready batches: none
- Blocked batches: p3-orchestrator-release-owner
- Closure waves: none
- Commands: 2
- Env keys: 1
- Missing artifacts: 3
- Content blockers: 0
- Stop reasons: 2
- First command: `node bin/ddd-release-evidence-orchestrator.mjs`
- Env key names:
  - `DDD_RELEASE_EVIDENCE_STRICT`
- Missing artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
- Rerun commands:
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
  - `bash artifacts/ddd/release/release-artifact-integrity-gate.sh`
  - `bash artifacts/ddd/release/release-preflight-gate.sh`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

