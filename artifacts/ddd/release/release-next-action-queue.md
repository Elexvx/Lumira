# DDD Release Next Action Queue

Generated at: 2026-06-17T08:13:17.325Z
Status: ADVISORY
Recommendation: NO_GO_STRICT
No auto waivers: true
Cutover allowed: false
Stop reasons: 7
releaseEnvFileCutoverSafe: false
Run now: 5
Waiting: 12
Owner input receipt status: PENDING_OWNER_INPUT
Owner input receipt cutover ready: false
Owner input receipt required inputs: 34
Owner input receipt pending owners: 5

## Final Cutover Decision

- finalRecommendation: NO_GO_STRICT
- cutoverAllowed: false
- releaseEnvFileCutoverSafe: false
- gateBlockers: 0
- blockedCutoverItems: 6
- stopReasonCount: 7
- stopReasonCoverage: catalog-snapshot
- cutoverAuthority: final-go-no-go-gate
- requiresFinalGate: true
- source: artifacts/ddd/release/release-final-go-no-go.json
- enforceCommand: `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

### Current Stop Reasons

- cutover checklist blocked: database-performance
- cutover checklist blocked: deployable-images
- cutover checklist blocked: evidence-integrity
- cutover checklist blocked: release-environment
- cutover checklist blocked: rollback-safety
- cutover checklist blocked: runtime-business-acceptance
- strict release gate blockers=0

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
  - platform-events: required=9 placeholders=9 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/01-platform-events.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/01-platform-events.md checklist=artifacts/ddd/release/release-owner-input-receipt-items/01-platform-events.md
  - platform-owners: required=9 placeholders=9 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/02-platform-owners.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/02-platform-owners.md checklist=artifacts/ddd/release/release-owner-input-receipt-items/02-platform-owners.md
  - release-infra: required=9 placeholders=9 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md checklist=artifacts/ddd/release/release-owner-input-receipt-items/03-release-infra.md
  - ai-owner: required=6 placeholders=6 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/04-ai-owner.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/04-ai-owner.md checklist=artifacts/ddd/release/release-owner-input-receipt-items/04-ai-owner.md
  - payment-owner: required=1 placeholders=1 missing=0 packet=artifacts/ddd/release/release-env-owner-input-packet/05-payment-owner.json handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/05-payment-owner.md checklist=artifacts/ddd/release/release-owner-input-receipt-items/05-payment-owner.md

## 1. release-infra

- Queue status: RUN_NOW
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: p0-docker-release-infra, p0-release-config-release-infra, p0-release-env-lint-release-infra
- Blocked batches: none
- Next action: Replace every placeholder-like value (`<placeholder>`, `replace-with-*`, TODO/TBD, example domains) in `DDD_RELEASE_ENV_FILE` before running release evidence.
- Reason: release-env-lint:release-env-lint-placeholders unresolvedTemplateKeys=93
- Executable commands:
  - `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Env keys: AI_SERVICE_BASE_URL, AUTH_SERVICE_BASE_URL, BASE_URL, CORS_ALLOWED_ORIGIN_PATTERNS, DB_PASSWORD, DB_URL, DB_USERNAME, DDD_AUTH_PASSWORD, DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE, DDD_AUTH_PERF_ENVIRONMENT, DDD_AUTH_USERNAME, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DDD_DEPLOYMENT_EVIDENCE, DDD_EXPLAIN_DATABASE, DDD_FRONTEND_DEPLOYMENT_EVIDENCE, DDD_MIGRATION_COMPLETED_AT, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_OPERATOR, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED, FIELD_SECRET, FILE_SERVICE_BASE_URL, JOB_EXECUTOR_BASE_URL, JWT_SECRET, LOCALIZATION_SERVICE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_BASE_URL, MESSAGE_SERVICE_BASE_URL, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PORT, PAYMENT_PUBLIC_BASE_URL, PAYMENT_SERVICE_BASE_URL, PLAYWRIGHT_BASE_URL, PLUGIN_SERVICE_BASE_URL, REDIS_HOST, SAAS_EVENT_REDIS_STREAM_KEY, SAAS_JOB_BACKEND_BASE_URL, SAAS_JOB_FILE_SERVICE_BASE_URL, SAAS_JOB_INTERNAL_TOKEN, SAAS_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL, SAAS_JOB_PLUGIN_SERVICE_BASE_URL, SYSTEM_SERVICE_BASE_URL, XXL_JOB_ACCESS_TOKEN, XXL_JOB_ADMIN_ADDRESSES

## 2. platform-owners

- Queue status: RUN_NOW
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: p0-release-config-platform-owners
- Blocked batches: none
- Next action: Set AI_SERVICE_BASE_URL or LUMIRA_AI_SERVICE_BASE_URL or LUMIRA_AI_BASE_URL for ai service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Reason: release-config:ai service placeholder value is not allowed
- Executable commands:
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Env keys: AI_SERVICE_BASE_URL, LUMIRA_AI_BASE_URL, LUMIRA_AI_SERVICE_BASE_URL

## 3. platform-events

- Queue status: RUN_NOW
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: p0-release-config-platform-events
- Blocked batches: none
- Next action: Set SAAS_EVENT_REDIS_STREAM_KEY or LUMIRA_EVENT_REDIS_STREAM_KEY for event stream key in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Reason: release-config:event stream key placeholder value is not allowed
- Executable commands:
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Env keys: LUMIRA_EVENT_REDIS_STREAM_KEY, SAAS_EVENT_REDIS_STREAM_KEY

## 4. ai-owner

- Queue status: RUN_NOW
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: p0-release-config-ai-owner
- Blocked batches: p1-rollback-ai-owner
- Next action: Set LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL or LUMIRA_AI_OWNER_FILE_BASE_URL for file owner url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Reason: release-config:file owner url placeholder value is not allowed
- Executable commands:
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Env keys: LUMIRA_AI_OWNER_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL

## 5. payment-owner

- Queue status: RUN_NOW
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: p0-release-config-payment-owner
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
- Next action: Set PAYMENT_PUBLIC_BASE_URL for payment public url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`.
- Reason: release-config:payment public url placeholder value is not allowed
- Executable commands:
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Env keys: PAYMENT_PUBLIC_BASE_URL

## 6. database

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: ARTIFACT_MISSING
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Next action: Produce missing artifact: tmp/ddd-explain/*.json
- Reason: missingArtifact=tmp/ddd-explain/*.json
- Executable commands:
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
- Env keys: DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED

## 7. ai

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai
- Next action: Configure and verify remote AI owner gateways for IAM/File/Platform integrations.
- Reason: ai-runtime:ai-owner-gateway ownerGateway status=CONFIGURED configuredOwners=0
- Env keys: DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE, LUMIRA_AI_OWNER_FILE_BASE_URL, LUMIRA_AI_OWNER_IAM_BASE_URL, LUMIRA_AI_OWNER_PLATFORM_BASE_URL

## 8. file-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-rollback-file-owner
- Next action: Regenerate File processing E2E smoke against an HTTPS non-local production-equivalent backend with real storage and job token evidence using `node scripts/ddd-file-processing-e2e-smoke.mjs`.
- Reason: business-e2e:file-processing-production-equivalence strict file processing E2E requires HTTPS baseUrl evidence; strict file processing E2E requires non-local baseUrl, got http://127.0.0.1:8080
- Executable commands:
  - `node scripts/ddd-file-processing-e2e-smoke.mjs`
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, LUMIRA_JOB_INTERNAL_TOKEN, LUMIRA_UPLOAD_STORAGE_ROOT

## 9. frontend

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-frontend-smoke-frontend, p3-orchestrator-frontend
- Next action: Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node scripts/ddd-release-evidence-orchestrator.mjs`.
- Reason: orchestrator:orchestrator-preflight-frontend-runtime-base-url missing deployed frontend base URL
- Executable commands:
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
- Env keys: FRONTEND_BASE_URL, PLAYWRIGHT_BASE_URL

## 10. job-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-business-e2e-job-owner, p1-rollback-job-owner
- Next action: Regenerate Job/internal E2E smoke against HTTPS non-local owner endpoints with the release job token using `node scripts/ddd-job-e2e-smoke.mjs`.
- Reason: business-e2e:job-e2e-production-equivalence strict job E2E requires HTTPS baseUrl evidence; strict job E2E requires non-local baseUrl, got http://127.0.0.1:8080
- Executable commands:
  - `node scripts/ddd-job-e2e-smoke.mjs`
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, LUMIRA_JOB_INTERNAL_TOKEN

## 11. auth-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-rollback-auth-owner
- Next action: Exercise auth adapter rollback with session TTL compatibility, login smoke, and forced logout/keepalive evidence. Required evidence: login smoke result after adapter rollback; session TTL compatibility evidence; forced logout or keepalive behavior evidence; auth readiness/health response after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Reason: rollback:Auth Auth rollback drill is DEFERRED with approved deferral evidence
- Executable commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT

## 12. iam-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-rollback-iam-owner
- Next action: Exercise permission snapshot rollback, cache invalidation, and IAM v2-to-v1 adapter fallback; attach readiness, audit, and cache evidence. Required evidence: permission snapshot version before and after rollback; cache invalidation or version bump evidence; IAM v2 readiness/health response after rollback; audit entry or command log for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Reason: rollback:IAM IAM rollback drill is DEFERRED with approved deferral evidence
- Executable commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT

## 13. localization-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-rollback-localization-owner
- Next action: Exercise localization release rollback and runtime bundle cache clear; attach release id, bundle metrics, and audit evidence. Required evidence: localization release id before and after rollback; runtime bundle cache clear evidence; bundle request or metrics proving rolled-back release is served; localization audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Reason: rollback:Localization Localization rollback drill is DEFERRED with approved deferral evidence
- Executable commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT

## 14. message-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-rollback-message-owner
- Next action: Exercise message relay pause, monolith-compatible delivery fallback, and idempotent replay; attach relay and message state evidence. Required evidence: message relay pause/resume command or job output; delivery fallback evidence for at least one notice; idempotent replay result with duplicate-safe state; message readiness/metrics response after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Reason: rollback:Message Message rollback drill is DEFERRED with approved deferral evidence
- Executable commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT

## 15. platform-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-rollback-platform-owner
- Next action: Exercise platform config/runtime appearance rollback and cache clear; attach bootstrap/config version and audit evidence. Required evidence: runtime appearance/config version before and after rollback; cache clear or version invalidation evidence; bootstrap response using the rolled-back config; platform audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Reason: rollback:Platform Platform rollback drill is DEFERRED with approved deferral evidence
- Executable commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT

## 16. plugin-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-rollback-plugin-owner
- Next action: Exercise tenant plugin disable/version rollback and bootstrap projection rebuild; attach audit and tenant projection evidence. Required evidence: tenant plugin disable or version rollback command output; bootstrap projection rebuild evidence; tenant plugin projection row before and after rollback; plugin audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node scripts/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node scripts/ddd-rollback-drill-evidence.mjs`.
- Reason: rollback:Plugin Plugin rollback drill is DEFERRED with approved deferral evidence
- Executable commands:
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT

## 17. release-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p3-orchestrator-release-owner
- Next action: Run the release evidence orchestrator in strict run mode after preflight blockers are resolved with `node scripts/ddd-release-evidence-orchestrator.mjs`.
- Reason: orchestrator:orchestrator-run-mode strict release requires run mode report, got plan
- Executable commands:
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
- Env keys: DDD_RELEASE_EVIDENCE_STRICT

