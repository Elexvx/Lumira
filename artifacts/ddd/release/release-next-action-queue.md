# DDD Release Next Action Queue

Generated at: 2026-06-19T18:19:45.629Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
Cutover allowed: false
Stop reasons: 8
releaseEnvFileCutoverSafe: false
Run now: 3
Waiting: 13
Owner input receipt status: PENDING_OWNER_INPUT
Owner input receipt cutover ready: false
Owner input receipt required inputs: 0
Owner input receipt pending owners: 0

## Final Cutover Decision

- finalRecommendation: NO_GO_STRICT
- cutoverAllowed: false
- releaseEnvFileCutoverSafe: false
- gateBlockers: 94
- blockedCutoverItems: 7
- stopReasonCount: 8
- stopReasonCoverage: catalog-snapshot
- cutoverAuthority: final-go-no-go-gate
- requiresFinalGate: true
- source: artifacts/ddd/release/release-final-go-no-go.json
- enforceCommand: `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

### Current Stop Reasons

- cutover checklist blocked: database-performance
- cutover checklist blocked: deployable-images
- cutover checklist blocked: evidence-integrity
- cutover checklist blocked: production-equivalence
- cutover checklist blocked: rollback-safety
- cutover checklist blocked: runtime-business-acceptance
- cutover checklist blocked: strict-release-gate
- strict release gate blockers=94

## Owner Input Receipt

- Status: PENDING_OWNER_INPUT
- Cutover ready: false
- Required owner inputs: 0
- Owners: 0
- Pending owners: 0
- Missing criteria:
  - releaseEnvReadinessStatus
- Pending owner inputs:
  - none

## 1. release-infra

- Queue status: RUN_NOW
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 21
- Ready batches: p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: p3-orchestrator-release-infra
- Next action: Regenerate runtime readiness against an HTTPS non-local production-equivalent backend URL so the artifact includes structured `productionEquivalence` evidence.
- Reason: strictGate=runtime-readiness-summary runtime readiness productionEquivalence.strict must be true for strict release evidence
- Executable commands:
  - `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Env keys: BASE_URL, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL

## 2. release-performance

- Queue status: RUN_NOW
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 13
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none
- Next action: Regenerate authenticated performance actual against an HTTPS non-local production-equivalent backend URL, then rerun baseline comparison or promotion.
- Reason: strictGate=authenticated-performance-shape authenticated performance actual productionEquivalence.strict must be true for strict release evidence
- Executable commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

## 3. lumira-ui

- Queue status: RUN_NOW
- Receipt status: ARTIFACT_MISSING
- Strict gate blockers: 11
- Ready batches: p0-manifest-lumira-ui
- Blocked batches: none
- Next action: Run deployed frontend smoke with HTTPS `PLAYWRIGHT_BASE_URL`, `DDD_FRONTEND_EXPECT_DEPLOYED=true`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR`; then convert it with `node bin/ddd-frontend-smoke-evidence.mjs`.
- Reason: strictGate=frontend-smoke-freshness generatedAt is 57.8h old; limit=24h
- Executable commands:
  - `DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_FRONTEND_EXPECT_DEPLOYED, DDD_RELEASE_CANDIDATE, PLAYWRIGHT_BASE_URL

## 4. release-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 14
- Ready batches: none
- Blocked batches: p3-orchestrator-release-owner
- Next action: Inspect the strict release gate blocker and attach an owner-specific remediation.
- Reason: strictGate=physical-split-readiness-freshness generatedAt is 57.7h old; limit=24h
- Env keys: DDD_RELEASE_EVIDENCE_STRICT

## 5. ai

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 10
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai
- Next action: Run `DDD_AI_EXPECT_PROVIDER_REMOTE=true DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true node bin/ddd-ai-runtime-drill.mjs` against production-equivalent AI runtime.
- Reason: strictGate=ai-runtime-drill AI runtime productionEquivalence.strict must be true for strict release evidence
- Env keys: DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE, LUMIRA_AI_OWNER_FILE_BASE_URL, LUMIRA_AI_OWNER_IAM_BASE_URL, LUMIRA_AI_OWNER_PLATFORM_BASE_URL

## 6. file-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 7
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-rollback-file-owner
- Next action: Regenerate File processing E2E evidence within the release freshness window against the production-equivalent environment.
- Reason: strictGate=file-processing-freshness finishedAt is 131.7h old; limit=24h
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, LUMIRA_JOB_INTERNAL_TOKEN, LUMIRA_UPLOAD_STORAGE_ROOT

## 7. job-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 7
- Ready batches: none
- Blocked batches: p1-business-e2e-job-owner, p1-rollback-job-owner
- Next action: Regenerate Job E2E evidence within the release freshness window against the production-equivalent environment.
- Reason: strictGate=job-e2e-freshness checkedAt is 131.4h old; limit=24h
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, LUMIRA_JOB_INTERNAL_TOKEN

## 8. payment-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 7
- Ready batches: none
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
- Next action: Regenerate Payment webhook E2E evidence within the release freshness window against the production-equivalent environment.
- Reason: strictGate=payment-webhook-freshness finishedAt is 131.6h old; limit=24h
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, PAYMENT_PUBLIC_BASE_URL

## 9. database

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: ARTIFACT_MISSING
- Strict gate blockers: 3
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Next action: Run fresh database and old database upgrade Flyway drills, then regenerate migration evidence with fresh/upgrade flags.
- Reason: strictGate=migration-evidence-freshness generatedAt is 57.7h old; limit=24h
- Executable commands:
  - `DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs`
  - `node bin/ddd-migration-evidence.mjs`
- Env keys: DDD_EVIDENCE_OPERATOR, DDD_MIGRATION_COMPLETED_AT, DDD_MIGRATION_ENVIRONMENT, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_HANDOFF_FILE, DDD_MIGRATION_OPERATOR, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_RELEASE_CANDIDATE

## 10. ai-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-rollback-ai-owner
- Next action: Exercise AI provider disablement, knowledge index job pause, and document index rebuild/degraded chat transcript evidence. Required evidence: AI provider disablement or fallback configuration evidence; knowledge index job pause/resume command or job output; document index rebuild or retry evidence; degraded chat/search transcript after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Reason: rollback:AI AI rollback drill is DEFERRED with approved deferral evidence
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT

## 11. auth-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-rollback-auth-owner
- Next action: Exercise auth adapter rollback with session TTL compatibility, login smoke, and forced logout/keepalive evidence. Required evidence: login smoke result after adapter rollback; session TTL compatibility evidence; forced logout or keepalive behavior evidence; auth readiness/health response after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Reason: rollback:Auth Auth rollback drill is DEFERRED with approved deferral evidence
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT

## 12. iam-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-rollback-iam-owner
- Next action: Exercise permission snapshot rollback, cache invalidation, and IAM v2-to-v1 adapter fallback; attach readiness, audit, and cache evidence. Required evidence: permission snapshot version before and after rollback; cache invalidation or version bump evidence; IAM v2 readiness/health response after rollback; audit entry or command log for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Reason: rollback:IAM IAM rollback drill is DEFERRED with approved deferral evidence
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT

## 13. localization-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-rollback-localization-owner
- Next action: Exercise localization release rollback and runtime bundle cache clear; attach release id, bundle metrics, and audit evidence. Required evidence: localization release id before and after rollback; runtime bundle cache clear evidence; bundle request or metrics proving rolled-back release is served; localization audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Reason: rollback:Localization Localization rollback drill is DEFERRED with approved deferral evidence
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT

## 14. message-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-rollback-message-owner
- Next action: Exercise message relay pause, monolith-compatible delivery fallback, and idempotent replay; attach relay and message state evidence. Required evidence: message relay pause/resume command or job output; delivery fallback evidence for at least one notice; idempotent replay result with duplicate-safe state; message readiness/metrics response after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Reason: rollback:Message Message rollback drill is DEFERRED with approved deferral evidence
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT

## 15. platform-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-rollback-platform-owner
- Next action: Exercise platform config/runtime appearance rollback and cache clear; attach bootstrap/config version and audit evidence. Required evidence: runtime appearance/config version before and after rollback; cache clear or version invalidation evidence; bootstrap response using the rolled-back config; platform audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Reason: rollback:Platform Platform rollback drill is DEFERRED with approved deferral evidence
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT

## 16. plugin-owner

- Queue status: WAIT_FOR_DEPENDENCIES
- Receipt status: CONTENT_BLOCKED
- Strict gate blockers: 0
- Ready batches: none
- Blocked batches: p1-rollback-plugin-owner
- Next action: Exercise tenant plugin disable/version rollback and bootstrap projection rebuild; attach audit and tenant projection evidence. Required evidence: tenant plugin disable or version rollback command output; bootstrap projection rebuild evidence; tenant plugin projection row before and after rollback; plugin audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Reason: rollback:Plugin Plugin rollback drill is DEFERRED with approved deferral evidence
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT

