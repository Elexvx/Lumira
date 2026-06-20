# DDD Release Unblock Brief

Generated at: 2026-06-19T18:34:09.835Z
Recommendation: NO_GO_STRICT
Cutover allowed: false
No auto waivers: true
releaseEnvFileCutoverSafe: false
Strict gate blockers: 94
Env owner blockers: 0
Orchestrator preflight blockers: 4

## Release Env Safety

Cutover safe: false
Ready: true
Status: PASS
Input kind: release-env-file
Env file present: true
Generated missing template: false
Security checked: true
Permission safe: true
Permission check skipped: true
Mode: 666
Required mode: 600
Blocking safe defaults available: 0
Blocking values requiring owner input: 0
Safe defaults exhausted: true

Owner input reasons:

- none

Owner input owners:

- none

Pending release env actions:

- none

## First Owner Action

Owner: release-infra
Order: 1
Reason: strictGate=runtime-readiness-summary runtime readiness productionEquivalence.strict must be true for strict release evidence
Next action: Regenerate runtime readiness against an HTTPS non-local production-equivalent backend URL so the artifact includes structured `productionEquivalence` evidence.
Command: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`

Env keys:

- BASE_URL
- DEPLOY_CHECK_BASE_URL
- LUMIRA_BASE_URL

## Orchestrator Preflight

Artifact: artifacts/ddd/release/orchestrator-report.json
Mode: plan
Strict: true
Status: FAIL
Blockers: 4
Warnings: 0
Selected steps: 26
Executed results: 0

First preflight action:

- Owner: release-infra
- Check: backend-runtime-base-url
- Reason: missing backend runtime base URL
- Command: `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Env keys: BASE_URL,DEPLOY_CHECK_BASE_URL,LUMIRA_BASE_URL

| Owner | Actions | Env keys | First check | First reason |
|---|---:|---|---|---|
| release-infra | 2 | BASE_URL,DEPLOY_CHECK_BASE_URL,FRONTEND_BASE_URL,LUMIRA_BASE_URL,PLAYWRIGHT_BASE_URL | backend-runtime-base-url | missing backend runtime base URL |
| ai | 1 | BASE_URL,DEPLOY_CHECK_BASE_URL,LUMIRA_AI_BASE_URL,LUMIRA_BASE_URL | ai-runtime-base-url | missing AI runtime base URL |
| database | 1 | DDD_MIGRATION_FRESH_DB_EVIDENCE,DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_EVIDENCE,DDD_MIGRATION_UPGRADE_DB_VALIDATED | migration-runtime-evidence | missing migration drill env: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_EVIDENCE |

## Owner Input Receipt

Status: PENDING_OWNER_INPUT
Cutover ready: false
Required owner inputs: 0
Owners: 0
Ready owners: 0
Pending owners: 0
Artifact: artifacts/ddd/release/release-owner-input-receipt.json
Markdown: artifacts/ddd/release/release-owner-input-receipt.md

Missing criteria:

- releaseEnvReadinessStatus

| Owner | Required inputs | Remaining placeholders | Remaining missing | Packet | Handoff |
|---|---:|---:|---:|---|---|

## Blocked Cutover Items

| Item | Pending | Ready batches | Blocked batches | Title |
|---|---:|---|---|---|
| strict-release-gate | 94 | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database,p3-orchestrator-database,p3-orchestrator-release-infra,p3-orchestrator-release-owner | Strict release gate has zero blockers and no contract issues. |
| deployable-images | 4 | p0-docker-release-infra | none | Deployable backend/lumira-ui images are built and inspected. |
| production-equivalence | 13 | p0-authenticated-performance-release-performance,p0-runtime-readiness-release-infra | none | Runtime and performance evidence use HTTPS non-local production-equivalent endpoints. |
| runtime-business-acceptance | 6 | none | p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner | AI, lumira-ui, file, job, and payment acceptance evidence is complete. |
| rollback-safety | 10 | none | p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner | Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance. |
| database-performance | 2 | none | p2-explain-database | Fresh production-equivalent EXPLAIN evidence has no scan/index blockers. |
| evidence-integrity | 7 | p0-manifest-lumira-ui | p3-orchestrator-database,p3-orchestrator-release-infra,p3-orchestrator-release-owner | Evidence manifest and final orchestrator strict rerun are clean. |

Cutover batch details:

| Cutover item | Batch | Owner | Priority | Runnable | Depends on | Commands |
|---|---|---|---|---|---|---|
| strict-release-gate | p0-authenticated-performance-release-performance | release-performance | P0 | true | none | node bin/ddd-authenticated-performance-smoke.mjs<br>node bin/ddd-promote-performance-baseline.mjs |
| strict-release-gate | p0-docker-release-infra | release-infra | P0 | true | none | DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs |
| strict-release-gate | p0-manifest-lumira-ui | lumira-ui | P0 | true | none | DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs<br>node bin/ddd-promote-performance-baseline.mjs<br>DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs |
| strict-release-gate | p0-runtime-readiness-release-infra | release-infra | P0 | true | none | node bin/ddd-runtime-readiness-smoke.mjs |
| strict-release-gate | p1-ai-runtime-ai | ai | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-ai-runtime-drill.mjs |
| strict-release-gate | p1-business-e2e-file-owner | file-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-file-processing-e2e-smoke.mjs |
| strict-release-gate | p1-business-e2e-job-owner | job-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-job-e2e-smoke.mjs |
| strict-release-gate | p1-business-e2e-payment-owner | payment-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-payment-webhook-e2e-smoke.mjs |
| strict-release-gate | p1-rollback-ai-owner | ai-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| strict-release-gate | p1-rollback-auth-owner | auth-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| strict-release-gate | p1-rollback-file-owner | file-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| strict-release-gate | p1-rollback-iam-owner | iam-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| strict-release-gate | p1-rollback-job-owner | job-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| strict-release-gate | p1-rollback-localization-owner | localization-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| strict-release-gate | p1-rollback-message-owner | message-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| strict-release-gate | p1-rollback-payment-owner | payment-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| strict-release-gate | p1-rollback-platform-owner | platform-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| strict-release-gate | p1-rollback-plugin-owner | plugin-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| strict-release-gate | p2-explain-database | database | P2 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner | node bin/ddd-collect-explain.mjs<br>DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs |
| strict-release-gate | p3-orchestrator-database | database | P3 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database | node bin/ddd-release-evidence-orchestrator.mjs<br>DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict |
| strict-release-gate | p3-orchestrator-release-infra | release-infra | P3 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database | node bin/ddd-release-evidence-orchestrator.mjs<br>DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict |
| strict-release-gate | p3-orchestrator-release-owner | release-owner | P3 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database | node bin/ddd-release-evidence-orchestrator.mjs<br>DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict |
| deployable-images | p0-docker-release-infra | release-infra | P0 | true | none | DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs |
| production-equivalence | p0-authenticated-performance-release-performance | release-performance | P0 | true | none | node bin/ddd-authenticated-performance-smoke.mjs<br>node bin/ddd-promote-performance-baseline.mjs |
| production-equivalence | p0-runtime-readiness-release-infra | release-infra | P0 | true | none | node bin/ddd-runtime-readiness-smoke.mjs |
| runtime-business-acceptance | p1-ai-runtime-ai | ai | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-ai-runtime-drill.mjs |
| runtime-business-acceptance | p1-business-e2e-file-owner | file-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-file-processing-e2e-smoke.mjs |
| runtime-business-acceptance | p1-business-e2e-job-owner | job-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-job-e2e-smoke.mjs |
| runtime-business-acceptance | p1-business-e2e-payment-owner | payment-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-payment-webhook-e2e-smoke.mjs |
| rollback-safety | p1-rollback-ai-owner | ai-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-auth-owner | auth-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-file-owner | file-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-iam-owner | iam-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-job-owner | job-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-localization-owner | localization-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-message-owner | message-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-payment-owner | payment-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-platform-owner | platform-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-plugin-owner | plugin-owner | P1 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | node bin/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs<br>node bin/ddd-rollback-drill-evidence.mjs |
| database-performance | p2-explain-database | database | P2 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner | node bin/ddd-collect-explain.mjs<br>DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs |
| evidence-integrity | p0-manifest-lumira-ui | lumira-ui | P0 | true | none | DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs<br>node bin/ddd-promote-performance-baseline.mjs<br>DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs |
| evidence-integrity | p3-orchestrator-database | database | P3 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database | node bin/ddd-release-evidence-orchestrator.mjs<br>DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict |
| evidence-integrity | p3-orchestrator-release-infra | release-infra | P3 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database | node bin/ddd-release-evidence-orchestrator.mjs<br>DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict |
| evidence-integrity | p3-orchestrator-release-owner | release-owner | P3 | false | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database | node bin/ddd-release-evidence-orchestrator.mjs<br>DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict |

Execution waves:

| Wave | Batches | Runnable | Blocked | Owners | Depends on | Commands |
|---|---:|---|---|---|---|---:|
| P0 | 4 | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | none | lumira-ui,release-infra,release-performance | none | 7 |
| P1 | 14 | none | p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner | ai,ai-owner,auth-owner,file-owner,iam-owner,job-owner,localization-owner,message-owner,payment-owner,platform-owner,plugin-owner | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra | 34 |
| P2 | 1 | none | p2-explain-database | database | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner | 2 |
| P3 | 3 | none | p3-orchestrator-database,p3-orchestrator-release-infra,p3-orchestrator-release-owner | database,release-infra,release-owner | p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database | 6 |

Wave operator commands:

- P0:
  - `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_ENV_FILE=<release-env-file> DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_ENV_FILE=<release-env-file> DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- P1: blocked until p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra is complete
- P2: blocked until p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner is complete
- P3: blocked until p0-authenticated-performance-release-performance,p0-docker-release-infra,p0-manifest-lumira-ui,p0-runtime-readiness-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database is complete

## Final Owner Queue Fast Path

Owner: release-infra
Queue order: 1
Objective: Run the next actionable owner queue through strict evidence refresh and final go/no-go without bypassing safety gates.
Blocked until: Required owner env keys and expected evidence artifacts are available in a permission-safe release env file.
First command: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
Final gate command: `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
Release env file required: true
Env keys: 10
Missing artifacts: 5

Commands:

- `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
- `node bin/ddd-runtime-readiness-smoke.mjs`
- `node bin/ddd-release-evidence-orchestrator.mjs`
- `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- `node bin/ddd-release-readiness-summary.mjs`
- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## Fastest Safe Path

1. Fill only the listed owner keys in the release env file; do not paste values into chat or artifacts.
2. Run env bootstrap, owner template merge, canonical merge, alias sync, canonical lint, and env file lint before any runtime evidence.
3. Collect HTTPS production-equivalent runtime, migration, Docker image, manifest, and authenticated performance evidence.
4. Run strict preflight only after env readiness and production-equivalent evidence are clean.

## Owner Env Handoff

| Owner | Blockers | Placeholders | Secret keys | Handoff |
|---|---:|---:|---:|---|

## Evidence Handoffs

| Handoff | Present | Path | Command | Purpose |
|---|---|---|---|---|
| Migration evidence handoff | true | artifacts/ddd/migration/migration-evidence-handoff.md | `DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs` | Fill production-equivalent fresh DB and previous-schema upgrade Flyway evidence before regenerating migration-evidence.json. |
| Rollback deferral owner handoff | true | artifacts/ddd/rollback/rollback-deferrals-owner-handoff/README.md | `node bin/ddd-rollback-deferral-template.mjs` | Coordinate real PASS rollback drills or approved DEFERRED risk acceptance by bounded-context owner. |
| Authenticated performance baseline handoff | true | artifacts/ddd/release/release-performance-baseline-commands.sh | `DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh` | Check env readiness, run production-equivalent authenticated performance smoke, and promote the accepted baseline without using local-only evidence. |
| Release env owner input packet | true | artifacts/ddd/release/release-env-owner-input-packet.md | `node bin/ddd-release-env-owner-input-packet-contract.mjs` | Collect the remaining real production-equivalent endpoints, secrets, and owner values without exposing concrete values in artifacts. |
| Release owner input receipt | true | artifacts/ddd/release/release-owner-input-receipt.md | `node bin/ddd-release-owner-input-receipt-contract.mjs` | Confirm whether every owner-supplied production value is reconciled with env readiness before allowing strict cutover. |

## Performance Baseline

Status: BLOCKED
Ready to promote: false
Blockers: 8

Required env keys:

- BASE_URL
- DDD_AUTH_PASSWORD
- DDD_AUTH_PERF_BASELINE_ACCEPTED_BY
- DDD_AUTH_PERF_BASELINE_ENVIRONMENT
- DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT
- DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE
- DDD_AUTH_PERF_ENVIRONMENT
- DDD_AUTH_USERNAME
- DDD_EVIDENCE_OPERATOR
- DDD_RELEASE_CANDIDATE
- DEPLOY_CHECK_BASE_URL
- LUMIRA_BASE_URL

Performance blockers:

- acceptedAt must be an ISO timestamp
- acceptedBy is required
- authenticated performance actual productionEquivalence.deploymentEvidence is required
- authenticated performance actual productionEquivalence.https must be true for strict release evidence
- authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence
- authenticated performance actual productionEquivalence.strict must be true for strict release evidence
- sourceArtifact is required
- sourceSha256 must be a SHA-256 hex digest

Performance commands:

- `DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh`
- `DDD_AUTH_PERF_STRICT=true node bin/ddd-authenticated-performance-smoke.mjs`
- `node bin/ddd-promote-performance-baseline.mjs`
- `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
- `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- `node bin/ddd-release-evidence-gate.mjs`
- `node bin/ddd-release-readiness-summary.mjs`
- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## Next Action Queue

| Order | Owner | Status | Receipt | Next action |
|---:|---|---|---|---|
| 1 | release-infra | RUN_NOW | CONTENT_BLOCKED | Regenerate runtime readiness against an HTTPS non-local production-equivalent backend URL so the artifact includes structured `productionEquivalence` evidence. |
| 2 | release-performance | RUN_NOW | CONTENT_BLOCKED | Regenerate authenticated performance actual against an HTTPS non-local production-equivalent backend URL, then rerun baseline comparison or promotion. |
| 3 | lumira-ui | RUN_NOW | ARTIFACT_MISSING | Run deployed frontend smoke with HTTPS `PLAYWRIGHT_BASE_URL`, `DDD_FRONTEND_EXPECT_DEPLOYED=true`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR`; then convert it with `node bin/ddd-frontend-smoke-evidence.mjs`. |

Next action commands:

- release-infra: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- release-infra: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- release-infra: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- release-infra: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- release-performance: `node bin/ddd-authenticated-performance-smoke.mjs`
- release-performance: `node bin/ddd-promote-performance-baseline.mjs`
- release-performance: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- release-performance: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- release-performance: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- lumira-ui: `DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- lumira-ui: `DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- lumira-ui: `DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- lumira-ui: `DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

## Stop Reasons

- authenticated performance baseline not ready: BLOCKED
- closure wave ARTIFACT_MISSING: wave 3 lumira-ui/p0-manifest-lumira-ui
- cutover checklist blocked: database-performance
- cutover checklist blocked: deployable-images
- cutover checklist blocked: evidence-integrity
- cutover checklist blocked: production-equivalence
- cutover checklist blocked: rollback-safety
- cutover checklist blocked: runtime-business-acceptance
- cutover checklist blocked: strict-release-gate
- owner input receipt pending: releaseEnvReadinessStatus
- strict release gate blockers=94

## Next Commands

- `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
- `DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts/ddd/release/release-env-bootstrap.sh`
- `node bin/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env <release-env-file>`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-safe-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-provenance-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-alias-sync.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-file-lint.mjs`
- `node bin/ddd-authenticated-performance-smoke.mjs`

