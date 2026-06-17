# Rollback Drill Evidence Handoff

This handoff does not satisfy the release gate by itself. Each bounded context must provide real PASS evidence or an approved DEFERRED record before `rollback-drill.json` can become PASS.

Status: MISSING
Value policy: No concrete rollback artifact contents, credentials, request payloads, or provider tokens are emitted; only context names, owner names, env key names, status, and commands are included.
Contexts: 10
Ready: 0
Missing: 10

Fast path:

- Objective: Close rollback-safety blockers without replacing real PASS drills or approved DEFERRED records.
- Blocked until: Every bounded context has PASS rollback drill evidence or an approved unexpired DEFERRED risk acceptance.
- Commands:

```sh
DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs
node scripts/ddd-rollback-deferral-template.mjs
DDD_ROLLBACK_DRILL_STRICT=true node scripts/ddd-rollback-drill-evidence.mjs
node scripts/ddd-release-readiness-summary.mjs
DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh
```

Owner runbook:

| Owner | Status | Contexts | Missing contexts | Required env keys | Next command |
|---|---|---|---|---|---|
| ai-owner | MISSING | AI | AI | AI_ROLLBACK_DEFERRAL_EVIDENCE; AI_ROLLBACK_EVIDENCE; AI_ROLLBACK_OWNER; DDD_EVIDENCE_ENVIRONMENT; DDD_EVIDENCE_OPERATOR; DDD_RELEASE_CANDIDATE; DDD_RELEASE_ENVIRONMENT; DDD_ROLLBACK_DRILL_DEFERRAL_FILE; DDD_ROLLBACK_DRILL_ENVIRONMENT; DDD_ROLLBACK_DRILL_FILE; GITHUB_ACTOR; GITHUB_SHA | DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs |
| auth-owner | MISSING | Auth | Auth | AUTH_ROLLBACK_DEFERRAL_EVIDENCE; AUTH_ROLLBACK_EVIDENCE; AUTH_ROLLBACK_OWNER; DDD_EVIDENCE_ENVIRONMENT; DDD_EVIDENCE_OPERATOR; DDD_RELEASE_CANDIDATE; DDD_RELEASE_ENVIRONMENT; DDD_ROLLBACK_DRILL_DEFERRAL_FILE; DDD_ROLLBACK_DRILL_ENVIRONMENT; DDD_ROLLBACK_DRILL_FILE; GITHUB_ACTOR; GITHUB_SHA | DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs |
| file-owner | MISSING | File | File | DDD_EVIDENCE_ENVIRONMENT; DDD_EVIDENCE_OPERATOR; DDD_RELEASE_CANDIDATE; DDD_RELEASE_ENVIRONMENT; DDD_ROLLBACK_DRILL_DEFERRAL_FILE; DDD_ROLLBACK_DRILL_ENVIRONMENT; DDD_ROLLBACK_DRILL_FILE; FILE_ROLLBACK_DEFERRAL_EVIDENCE; FILE_ROLLBACK_EVIDENCE; FILE_ROLLBACK_OWNER; GITHUB_ACTOR; GITHUB_SHA | DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs |
| iam-owner | MISSING | IAM | IAM | DDD_EVIDENCE_ENVIRONMENT; DDD_EVIDENCE_OPERATOR; DDD_RELEASE_CANDIDATE; DDD_RELEASE_ENVIRONMENT; DDD_ROLLBACK_DRILL_DEFERRAL_FILE; DDD_ROLLBACK_DRILL_ENVIRONMENT; DDD_ROLLBACK_DRILL_FILE; GITHUB_ACTOR; GITHUB_SHA; IAM_ROLLBACK_DEFERRAL_EVIDENCE; IAM_ROLLBACK_EVIDENCE; IAM_ROLLBACK_OWNER | DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs |
| job-owner | MISSING | Job | Job | DDD_EVIDENCE_ENVIRONMENT; DDD_EVIDENCE_OPERATOR; DDD_RELEASE_CANDIDATE; DDD_RELEASE_ENVIRONMENT; DDD_ROLLBACK_DRILL_DEFERRAL_FILE; DDD_ROLLBACK_DRILL_ENVIRONMENT; DDD_ROLLBACK_DRILL_FILE; GITHUB_ACTOR; GITHUB_SHA; JOB_ROLLBACK_DEFERRAL_EVIDENCE; JOB_ROLLBACK_EVIDENCE; JOB_ROLLBACK_OWNER | DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs |
| localization-owner | MISSING | Localization | Localization | DDD_EVIDENCE_ENVIRONMENT; DDD_EVIDENCE_OPERATOR; DDD_RELEASE_CANDIDATE; DDD_RELEASE_ENVIRONMENT; DDD_ROLLBACK_DRILL_DEFERRAL_FILE; DDD_ROLLBACK_DRILL_ENVIRONMENT; DDD_ROLLBACK_DRILL_FILE; GITHUB_ACTOR; GITHUB_SHA; LOCALIZATION_ROLLBACK_DEFERRAL_EVIDENCE; LOCALIZATION_ROLLBACK_EVIDENCE; LOCALIZATION_ROLLBACK_OWNER | DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs |
| message-owner | MISSING | Message | Message | DDD_EVIDENCE_ENVIRONMENT; DDD_EVIDENCE_OPERATOR; DDD_RELEASE_CANDIDATE; DDD_RELEASE_ENVIRONMENT; DDD_ROLLBACK_DRILL_DEFERRAL_FILE; DDD_ROLLBACK_DRILL_ENVIRONMENT; DDD_ROLLBACK_DRILL_FILE; GITHUB_ACTOR; GITHUB_SHA; MESSAGE_ROLLBACK_DEFERRAL_EVIDENCE; MESSAGE_ROLLBACK_EVIDENCE; MESSAGE_ROLLBACK_OWNER | DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs |
| payment-owner | MISSING | Payment | Payment | DDD_EVIDENCE_ENVIRONMENT; DDD_EVIDENCE_OPERATOR; DDD_RELEASE_CANDIDATE; DDD_RELEASE_ENVIRONMENT; DDD_ROLLBACK_DRILL_DEFERRAL_FILE; DDD_ROLLBACK_DRILL_ENVIRONMENT; DDD_ROLLBACK_DRILL_FILE; GITHUB_ACTOR; GITHUB_SHA; PAYMENT_ROLLBACK_DEFERRAL_EVIDENCE; PAYMENT_ROLLBACK_EVIDENCE; PAYMENT_ROLLBACK_OWNER | DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs |
| platform-owner | MISSING | Platform | Platform | DDD_EVIDENCE_ENVIRONMENT; DDD_EVIDENCE_OPERATOR; DDD_RELEASE_CANDIDATE; DDD_RELEASE_ENVIRONMENT; DDD_ROLLBACK_DRILL_DEFERRAL_FILE; DDD_ROLLBACK_DRILL_ENVIRONMENT; DDD_ROLLBACK_DRILL_FILE; GITHUB_ACTOR; GITHUB_SHA; PLATFORM_ROLLBACK_DEFERRAL_EVIDENCE; PLATFORM_ROLLBACK_EVIDENCE; PLATFORM_ROLLBACK_OWNER | DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs |
| plugin-owner | MISSING | Plugin | Plugin | DDD_EVIDENCE_ENVIRONMENT; DDD_EVIDENCE_OPERATOR; DDD_RELEASE_CANDIDATE; DDD_RELEASE_ENVIRONMENT; DDD_ROLLBACK_DRILL_DEFERRAL_FILE; DDD_ROLLBACK_DRILL_ENVIRONMENT; DDD_ROLLBACK_DRILL_FILE; GITHUB_ACTOR; GITHUB_SHA; PLUGIN_ROLLBACK_DEFERRAL_EVIDENCE; PLUGIN_ROLLBACK_EVIDENCE; PLUGIN_ROLLBACK_OWNER | DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs |

Evidence checklist:

| Evidence path | Status | Required fields | Required artifacts | Acceptance criteria |
|---|---|---|---|---|
| pass-rollback-drill-evidence | PASS | rollbackAction; drillEvidence; validatedAt | Rollback command, job output, or operator log for the bounded context.; Post-rollback readiness, health, metrics, or smoke evidence.; Audit entry, ticket, or artifact path proving the rollback action was reviewed. | Context status is PASS only after rollback behavior is exercised.; drillEvidence references a concrete evidence link, artifact path, log path, object URI, or ticket id.; validatedAt is an ISO timestamp from the completed drill. |
| deferred-risk-acceptance-evidence | DEFERRED | notExercisableReason; riskAcceptedBy; deferralEvidence; expiresAt | Approved risk acceptance record for the bounded context.; Reason the rollback drill cannot be exercised before release.; Expiry timestamp for the deferral window. | Context status is DEFERRED only with explicit risk acceptance.; deferralEvidence references a concrete change ticket, approval artifact, or evidence link.; expiresAt is in the future for strict release evaluation. |

| Owner | Context | Current status | Env status | Required env keys | Action |
|---|---|---|---|---|---|
| iam-owner | IAM | MISSING | MISSING | DDD_ROLLBACK_DRILL_FILE; DDD_ROLLBACK_DRILL_DEFERRAL_FILE or IAM_ROLLBACK_EVIDENCE or IAM_ROLLBACK_DEFERRAL_EVIDENCE; DDD_EVIDENCE_ENVIRONMENT or DDD_ROLLBACK_DRILL_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT; DDD_RELEASE_CANDIDATE or GITHUB_SHA; DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR or IAM_ROLLBACK_OWNER | Exercise permission snapshot rollback, cache invalidation, and IAM v2-to-v1 adapter fallback; attach readiness, audit, and cache evidence. |
| auth-owner | Auth | MISSING | MISSING | DDD_ROLLBACK_DRILL_FILE; DDD_ROLLBACK_DRILL_DEFERRAL_FILE or AUTH_ROLLBACK_EVIDENCE or AUTH_ROLLBACK_DEFERRAL_EVIDENCE; DDD_EVIDENCE_ENVIRONMENT or DDD_ROLLBACK_DRILL_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT; DDD_RELEASE_CANDIDATE or GITHUB_SHA; DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR or AUTH_ROLLBACK_OWNER | Exercise auth adapter rollback with session TTL compatibility, login smoke, and forced logout/keepalive evidence. |
| platform-owner | Platform | MISSING | MISSING | DDD_ROLLBACK_DRILL_FILE; DDD_ROLLBACK_DRILL_DEFERRAL_FILE or PLATFORM_ROLLBACK_EVIDENCE or PLATFORM_ROLLBACK_DEFERRAL_EVIDENCE; DDD_EVIDENCE_ENVIRONMENT or DDD_ROLLBACK_DRILL_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT; DDD_RELEASE_CANDIDATE or GITHUB_SHA; DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR or PLATFORM_ROLLBACK_OWNER | Exercise platform config/runtime appearance rollback and cache clear; attach bootstrap/config version and audit evidence. |
| message-owner | Message | MISSING | MISSING | DDD_ROLLBACK_DRILL_FILE; DDD_ROLLBACK_DRILL_DEFERRAL_FILE or MESSAGE_ROLLBACK_EVIDENCE or MESSAGE_ROLLBACK_DEFERRAL_EVIDENCE; DDD_EVIDENCE_ENVIRONMENT or DDD_ROLLBACK_DRILL_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT; DDD_RELEASE_CANDIDATE or GITHUB_SHA; DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR or MESSAGE_ROLLBACK_OWNER | Exercise message relay pause, monolith-compatible delivery fallback, and idempotent replay; attach relay and message state evidence. |
| file-owner | File | MISSING | MISSING | DDD_ROLLBACK_DRILL_FILE; DDD_ROLLBACK_DRILL_DEFERRAL_FILE or FILE_ROLLBACK_EVIDENCE or FILE_ROLLBACK_DEFERRAL_EVIDENCE; DDD_EVIDENCE_ENVIRONMENT or DDD_ROLLBACK_DRILL_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT; DDD_RELEASE_CANDIDATE or GITHUB_SHA; DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR or FILE_ROLLBACK_OWNER | Exercise file processing pause, stable object-key access, and task rerun by id; attach upload, processing row, and storage evidence. |
| plugin-owner | Plugin | MISSING | MISSING | DDD_ROLLBACK_DRILL_FILE; DDD_ROLLBACK_DRILL_DEFERRAL_FILE or PLUGIN_ROLLBACK_EVIDENCE or PLUGIN_ROLLBACK_DEFERRAL_EVIDENCE; DDD_EVIDENCE_ENVIRONMENT or DDD_ROLLBACK_DRILL_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT; DDD_RELEASE_CANDIDATE or GITHUB_SHA; DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR or PLUGIN_ROLLBACK_OWNER | Exercise tenant plugin disable/version rollback and bootstrap projection rebuild; attach audit and tenant projection evidence. |
| localization-owner | Localization | MISSING | MISSING | DDD_ROLLBACK_DRILL_FILE; DDD_ROLLBACK_DRILL_DEFERRAL_FILE or LOCALIZATION_ROLLBACK_EVIDENCE or LOCALIZATION_ROLLBACK_DEFERRAL_EVIDENCE; DDD_EVIDENCE_ENVIRONMENT or DDD_ROLLBACK_DRILL_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT; DDD_RELEASE_CANDIDATE or GITHUB_SHA; DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR or LOCALIZATION_ROLLBACK_OWNER | Exercise localization release rollback and runtime bundle cache clear; attach release id, bundle metrics, and audit evidence. |
| payment-owner | Payment | MISSING | MISSING | DDD_ROLLBACK_DRILL_FILE; DDD_ROLLBACK_DRILL_DEFERRAL_FILE or PAYMENT_ROLLBACK_EVIDENCE or PAYMENT_ROLLBACK_DEFERRAL_EVIDENCE; DDD_EVIDENCE_ENVIRONMENT or DDD_ROLLBACK_DRILL_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT; DDD_RELEASE_CANDIDATE or GITHUB_SHA; DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR or PAYMENT_ROLLBACK_OWNER | Exercise payment webhook route fallback and idempotent event replay; attach provider routing, webhook metrics, and order trace evidence. |
| ai-owner | AI | MISSING | MISSING | DDD_ROLLBACK_DRILL_FILE; DDD_ROLLBACK_DRILL_DEFERRAL_FILE or AI_ROLLBACK_EVIDENCE or AI_ROLLBACK_DEFERRAL_EVIDENCE; DDD_EVIDENCE_ENVIRONMENT or DDD_ROLLBACK_DRILL_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT; DDD_RELEASE_CANDIDATE or GITHUB_SHA; DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR or AI_ROLLBACK_OWNER | Exercise AI provider disablement, knowledge index job pause, and document index rebuild/degraded chat transcript evidence. |
| job-owner | Job | MISSING | MISSING | DDD_ROLLBACK_DRILL_FILE; DDD_ROLLBACK_DRILL_DEFERRAL_FILE or JOB_ROLLBACK_EVIDENCE or JOB_ROLLBACK_DEFERRAL_EVIDENCE; DDD_EVIDENCE_ENVIRONMENT or DDD_ROLLBACK_DRILL_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT; DDD_RELEASE_CANDIDATE or GITHUB_SHA; DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR or JOB_ROLLBACK_OWNER | Exercise XXL-JOB handler disablement and manual owner internal endpoint fallback; attach dashboard, token, and endpoint evidence. |

Validation commands:

```sh
DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs
node scripts/ddd-rollback-deferral-template.mjs
DDD_ROLLBACK_DRILL_STRICT=true node scripts/ddd-rollback-drill-evidence.mjs
node scripts/ddd-release-readiness-summary.mjs
DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh
```

