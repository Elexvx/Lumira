# DDD Release Unblock Brief

Generated at: 2026-06-17T08:13:42.805Z
Recommendation: NO_GO_STRICT
Cutover allowed: false
No auto waivers: true
releaseEnvFileCutoverSafe: false
Strict gate blockers: 0
Env owner blockers: 34
Orchestrator preflight blockers: 4

## Release Env Safety

Cutover safe: false
Ready: false
Status: FAIL
Input kind: release-env-file
Env file present: true
Generated missing template: false
Security checked: true
Permission safe: true
Permission check skipped: false
Mode: 600
Required mode: 600
Blocking safe defaults available: 0
Blocking values requiring owner input: 34
Safe defaults exhausted: true

Owner input reasons:

- owner-production-value: 2
- production-endpoint: 24
- secret-manager: 8

Owner input owners:

- platform-events: requiresOwnerInput=9, safeDefaultAvailable=0
- platform-owners: requiresOwnerInput=9, safeDefaultAvailable=0
- release-infra: requiresOwnerInput=9, safeDefaultAvailable=0
- ai-owner: requiresOwnerInput=6, safeDefaultAvailable=0
- payment-owner: requiresOwnerInput=1, safeDefaultAvailable=0

Pending release env actions:

- release-env-lint-placeholders
- release-env-lint-status

## First Owner Action

Owner: release-infra
Order: 1
Reason: release-env-lint:release-env-lint-placeholders unresolvedTemplateKeys=93
Next action: Replace every placeholder-like value (`<placeholder>`, `replace-with-*`, TODO/TBD, example domains) in `DDD_RELEASE_ENV_FILE` before running release evidence.
Command: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`

Env keys:

- AI_SERVICE_BASE_URL
- AUTH_SERVICE_BASE_URL
- BASE_URL
- CORS_ALLOWED_ORIGIN_PATTERNS
- DB_PASSWORD
- DB_URL
- DB_USERNAME
- DDD_AUTH_PASSWORD
- DDD_AUTH_PERF_BASELINE_ACCEPTED_BY
- DDD_AUTH_PERF_BASELINE_ENVIRONMENT
- DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT
- DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE
- DDD_AUTH_PERF_ENVIRONMENT
- DDD_AUTH_USERNAME
- DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE
- DDD_DEPLOYMENT_EVIDENCE
- DDD_EXPLAIN_DATABASE
- DDD_FRONTEND_DEPLOYMENT_EVIDENCE
- DDD_MIGRATION_COMPLETED_AT
- DDD_MIGRATION_FRESH_DB_EVIDENCE
- DDD_MIGRATION_FRESH_DB_VALIDATED
- DDD_MIGRATION_OPERATOR
- DDD_MIGRATION_UPGRADE_DB_EVIDENCE
- DDD_MIGRATION_UPGRADE_DB_VALIDATED
- FIELD_SECRET
- FILE_SERVICE_BASE_URL
- JOB_EXECUTOR_BASE_URL
- JWT_SECRET
- LOCALIZATION_SERVICE_BASE_URL
- LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL
- LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL
- LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN
- LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL
- LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY
- LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL
- LUMIRA_BASE_URL
- MESSAGE_SERVICE_BASE_URL
- MYSQL_DATABASE
- MYSQL_HOST
- MYSQL_PORT
- PAYMENT_PUBLIC_BASE_URL
- PAYMENT_SERVICE_BASE_URL
- PLAYWRIGHT_BASE_URL
- PLUGIN_SERVICE_BASE_URL
- REDIS_HOST
- SAAS_EVENT_REDIS_STREAM_KEY
- SAAS_JOB_BACKEND_BASE_URL
- SAAS_JOB_FILE_SERVICE_BASE_URL
- SAAS_JOB_INTERNAL_TOKEN
- SAAS_JOB_MESSAGE_SERVICE_BASE_URL
- SAAS_JOB_PAYMENT_SERVICE_BASE_URL
- SAAS_JOB_PLUGIN_SERVICE_BASE_URL
- SYSTEM_SERVICE_BASE_URL
- XXL_JOB_ACCESS_TOKEN
- XXL_JOB_ADMIN_ADDRESSES

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

- Owner: ai
- Check: ai-runtime-base-url
- Reason: missing AI runtime base URL
- Command: `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Env keys: BASE_URL,DEPLOY_CHECK_BASE_URL,LUMIRA_AI_BASE_URL,LUMIRA_BASE_URL

| Owner | Actions | Env keys | First check | First reason |
|---|---:|---|---|---|
| ai | 1 | BASE_URL,DEPLOY_CHECK_BASE_URL,LUMIRA_AI_BASE_URL,LUMIRA_BASE_URL | ai-runtime-base-url | missing AI runtime base URL |
| database | 1 | DDD_MIGRATION_FRESH_DB_EVIDENCE,DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_EVIDENCE,DDD_MIGRATION_UPGRADE_DB_VALIDATED | migration-runtime-evidence | missing migration drill env: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_EVIDENCE |
| frontend | 1 | FRONTEND_BASE_URL,PLAYWRIGHT_BASE_URL | frontend-runtime-base-url | missing deployed frontend base URL |
| release-infra | 1 | BASE_URL,DEPLOY_CHECK_BASE_URL,LUMIRA_BASE_URL | backend-runtime-base-url | missing backend runtime base URL |

## Owner Input Receipt

Status: PENDING_OWNER_INPUT
Cutover ready: false
Required owner inputs: 34
Owners: 5
Ready owners: 0
Pending owners: 5
Artifact: artifacts/ddd/release/release-owner-input-receipt.json
Markdown: artifacts/ddd/release/release-owner-input-receipt.md

Missing criteria:

- releaseEnvReadinessBlockers
- releaseEnvReadinessPlaceholders
- releaseEnvReadinessStatus

| Owner | Required inputs | Remaining placeholders | Remaining missing | Packet | Handoff |
|---|---:|---:|---:|---|---|
| platform-events | 9 | 9 | 0 | artifacts/ddd/release/release-env-owner-input-packet/01-platform-events.json | artifacts/ddd/release/release-env-owner-handoff-redacted/01-platform-events.md |
| platform-owners | 9 | 9 | 0 | artifacts/ddd/release/release-env-owner-input-packet/02-platform-owners.json | artifacts/ddd/release/release-env-owner-handoff-redacted/02-platform-owners.md |
| release-infra | 9 | 9 | 0 | artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json | artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md |
| ai-owner | 6 | 6 | 0 | artifacts/ddd/release/release-env-owner-input-packet/04-ai-owner.json | artifacts/ddd/release/release-env-owner-handoff-redacted/04-ai-owner.md |
| payment-owner | 1 | 1 | 0 | artifacts/ddd/release/release-env-owner-input-packet/05-payment-owner.json | artifacts/ddd/release/release-env-owner-handoff-redacted/05-payment-owner.md |

## Blocked Cutover Items

| Item | Pending | Ready batches | Blocked batches | Title |
|---|---:|---|---|---|
| release-environment | 65 | p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | none | Completed release env file and config matrix are valid. |
| deployable-images | 4 | p0-docker-release-infra | none | Deployable backend/frontend images are built and inspected. |
| runtime-business-acceptance | 7 | none | p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-frontend-smoke-frontend | AI, frontend, file, job, and payment acceptance evidence is complete. |
| rollback-safety | 10 | none | p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner | Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance. |
| database-performance | 6 | none | p2-explain-database | Fresh production-equivalent EXPLAIN evidence has no scan/index blockers. |
| evidence-integrity | 3 | none | p3-orchestrator-database,p3-orchestrator-frontend,p3-orchestrator-release-owner | Evidence manifest and final orchestrator strict rerun are clean. |

Cutover batch details:

| Cutover item | Batch | Owner | Priority | Runnable | Depends on | Commands |
|---|---|---|---|---|---|---|
| release-environment | p0-release-config-ai-owner | ai-owner | P0 | true | none | node scripts/ddd-release-config-evidence.mjs |
| release-environment | p0-release-config-payment-owner | payment-owner | P0 | true | none | node scripts/ddd-release-config-evidence.mjs |
| release-environment | p0-release-config-platform-events | platform-events | P0 | true | none | node scripts/ddd-release-config-evidence.mjs |
| release-environment | p0-release-config-platform-owners | platform-owners | P0 | true | none | node scripts/ddd-release-config-evidence.mjs |
| release-environment | p0-release-config-release-infra | release-infra | P0 | true | none | node scripts/ddd-release-config-evidence.mjs |
| release-environment | p0-release-env-lint-release-infra | release-infra | P0 | true | none | DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs<br>node scripts/ddd-release-config-evidence.mjs |
| deployable-images | p0-docker-release-infra | release-infra | P0 | true | none | DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs<br>node scripts/ddd-docker-build-evidence.mjs |
| runtime-business-acceptance | p1-ai-runtime-ai | ai | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-ai-runtime-drill.mjs |
| runtime-business-acceptance | p1-business-e2e-file-owner | file-owner | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-file-processing-e2e-smoke.mjs |
| runtime-business-acceptance | p1-business-e2e-job-owner | job-owner | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-job-e2e-smoke.mjs |
| runtime-business-acceptance | p1-business-e2e-payment-owner | payment-owner | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-payment-webhook-e2e-smoke.mjs |
| runtime-business-acceptance | p1-frontend-smoke-frontend | frontend | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-frontend-playwright-smoke.mjs<br>node scripts/ddd-frontend-smoke-evidence.mjs |
| rollback-safety | p1-rollback-ai-owner | ai-owner | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs<br>node scripts/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-auth-owner | auth-owner | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs<br>node scripts/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-file-owner | file-owner | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs<br>node scripts/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-iam-owner | iam-owner | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs<br>node scripts/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-job-owner | job-owner | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs<br>node scripts/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-localization-owner | localization-owner | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs<br>node scripts/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-message-owner | message-owner | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs<br>node scripts/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-payment-owner | payment-owner | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs<br>node scripts/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-platform-owner | platform-owner | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs<br>node scripts/ddd-rollback-drill-evidence.mjs |
| rollback-safety | p1-rollback-plugin-owner | plugin-owner | P1 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | node scripts/ddd-rollback-deferral-template.mjs<br>DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs<br>node scripts/ddd-rollback-drill-evidence.mjs |
| database-performance | p2-explain-database | database | P2 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-frontend-smoke-frontend,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner | node scripts/ddd-collect-explain.mjs<br>DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs |
| evidence-integrity | p3-orchestrator-database | database | P3 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-frontend-smoke-frontend,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database | node scripts/ddd-release-evidence-orchestrator.mjs<br>DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict |
| evidence-integrity | p3-orchestrator-frontend | frontend | P3 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-frontend-smoke-frontend,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database | node scripts/ddd-release-evidence-orchestrator.mjs<br>DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict |
| evidence-integrity | p3-orchestrator-release-owner | release-owner | P3 | false | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-frontend-smoke-frontend,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database | node scripts/ddd-release-evidence-orchestrator.mjs<br>DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict |

Execution waves:

| Wave | Batches | Runnable | Blocked | Owners | Depends on | Commands |
|---|---:|---|---|---|---|---:|
| P0 | 7 | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | none | ai-owner,payment-owner,platform-events,platform-owners,release-infra | none | 9 |
| P1 | 15 | none | p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-frontend-smoke-frontend,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner | ai,ai-owner,auth-owner,file-owner,frontend,iam-owner,job-owner,localization-owner,message-owner,payment-owner,platform-owner,plugin-owner | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra | 36 |
| P2 | 1 | none | p2-explain-database | database | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-frontend-smoke-frontend,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner | 2 |
| P3 | 3 | none | p3-orchestrator-database,p3-orchestrator-frontend,p3-orchestrator-release-owner | database,frontend,release-owner | p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-frontend-smoke-frontend,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database | 6 |

Wave operator commands:

- P0:
  - `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_ENV_FILE=<release-env-file> DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `DDD_RELEASE_ENV_FILE=<release-env-file> DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- P1: blocked until p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra is complete
- P2: blocked until p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-frontend-smoke-frontend,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner is complete
- P3: blocked until p0-docker-release-infra,p0-release-config-ai-owner,p0-release-config-payment-owner,p0-release-config-platform-events,p0-release-config-platform-owners,p0-release-config-release-infra,p0-release-env-lint-release-infra,p1-ai-runtime-ai,p1-business-e2e-file-owner,p1-business-e2e-job-owner,p1-business-e2e-payment-owner,p1-frontend-smoke-frontend,p1-rollback-ai-owner,p1-rollback-auth-owner,p1-rollback-file-owner,p1-rollback-iam-owner,p1-rollback-job-owner,p1-rollback-localization-owner,p1-rollback-message-owner,p1-rollback-payment-owner,p1-rollback-platform-owner,p1-rollback-plugin-owner,p2-explain-database is complete

## Final Owner Queue Fast Path

Owner: release-infra
Queue order: 1
Objective: Run the next actionable owner queue through strict evidence refresh and final go/no-go without bypassing safety gates.
Blocked until: Required owner env keys and expected evidence artifacts are available in a permission-safe release env file.
First command: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
Final gate command: `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
Release env file required: true
Env keys: 68
Missing artifacts: 3

Commands:

- `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
- `node scripts/ddd-release-config-evidence.mjs`
- `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
- `node scripts/ddd-docker-build-evidence.mjs`
- `node scripts/ddd-release-readiness-summary.mjs`
- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## Fastest Safe Path

1. Fill only the listed owner keys in the release env file; do not paste values into chat or artifacts.
2. Run env bootstrap, owner template merge, canonical merge, alias sync, canonical lint, and env file lint before any runtime evidence.
3. Collect HTTPS production-equivalent runtime, migration, Docker image, manifest, and authenticated performance evidence.
4. Run strict preflight only after env readiness and production-equivalent evidence are clean.

## Owner Env Handoff

| Owner | Blockers | Placeholders | Secret keys | Handoff |
|---|---:|---:|---:|---|
| platform-events | 9 | 9 | 3 | artifacts/ddd/release/release-env-owner-handoff-redacted/01-platform-events.md |
| platform-owners | 9 | 9 | 0 | artifacts/ddd/release/release-env-owner-handoff-redacted/02-platform-owners.md |
| release-infra | 9 | 9 | 4 | artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md |
| ai-owner | 6 | 6 | 2 | artifacts/ddd/release/release-env-owner-handoff-redacted/04-ai-owner.md |
| payment-owner | 1 | 1 | 1 | artifacts/ddd/release/release-env-owner-handoff-redacted/05-payment-owner.md |

## Evidence Handoffs

| Handoff | Present | Path | Command | Purpose |
|---|---|---|---|---|
| Migration evidence handoff | true | artifacts/ddd/migration/migration-evidence-handoff.md | `DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs` | Fill production-equivalent fresh DB and previous-schema upgrade Flyway evidence before regenerating migration-evidence.json. |
| Rollback deferral owner handoff | true | artifacts/ddd/rollback/rollback-deferrals-owner-handoff/README.md | `node scripts/ddd-rollback-deferral-template.mjs` | Coordinate real PASS rollback drills or approved DEFERRED risk acceptance by bounded-context owner. |
| Authenticated performance baseline handoff | true | artifacts/ddd/release/release-performance-baseline-commands.sh | `DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh` | Check env readiness, run production-equivalent authenticated performance smoke, and promote the accepted baseline without using local-only evidence. |
| Release env owner input packet | true | artifacts/ddd/release/release-env-owner-input-packet.md | `node scripts/ddd-release-env-owner-input-packet-contract.mjs` | Collect the remaining real production-equivalent endpoints, secrets, and owner values without exposing concrete values in artifacts. |
| Release owner input receipt | true | artifacts/ddd/release/release-owner-input-receipt.md | `node scripts/ddd-release-owner-input-receipt-contract.mjs` | Confirm whether every owner-supplied production value is reconciled with env readiness before allowing strict cutover. |

## Performance Baseline

Status: READY_FOR_STRICT_GATE_RERUN
Ready to promote: false
Blockers: 0

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


Performance commands:

- `DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh`
- `DDD_AUTH_PERF_STRICT=true node scripts/ddd-authenticated-performance-smoke.mjs`
- `node scripts/ddd-promote-performance-baseline.mjs`
- `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
- `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
- `node scripts/ddd-release-evidence-gate.mjs`
- `node scripts/ddd-release-readiness-summary.mjs`
- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## Next Action Queue

| Order | Owner | Status | Receipt | Next action |
|---:|---|---|---|---|
| 1 | release-infra | RUN_NOW | CONTENT_BLOCKED | Replace every placeholder-like value (`<placeholder>`, `replace-with-*`, TODO/TBD, example domains) in `DDD_RELEASE_ENV_FILE` before running release evidence. |
| 2 | platform-owners | RUN_NOW | CONTENT_BLOCKED | Set AI_SERVICE_BASE_URL or LUMIRA_AI_SERVICE_BASE_URL or LUMIRA_AI_BASE_URL for ai service in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`. |
| 3 | platform-events | RUN_NOW | CONTENT_BLOCKED | Set SAAS_EVENT_REDIS_STREAM_KEY or LUMIRA_EVENT_REDIS_STREAM_KEY for event stream key in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`. |
| 4 | ai-owner | RUN_NOW | CONTENT_BLOCKED | Set LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL or LUMIRA_AI_OWNER_FILE_BASE_URL for file owner url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`. |
| 5 | payment-owner | RUN_NOW | CONTENT_BLOCKED | Set PAYMENT_PUBLIC_BASE_URL for payment public url in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun `node scripts/ddd-release-config-evidence.mjs`. |

Next action commands:

- release-infra: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- release-infra: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- release-infra: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- release-infra: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- platform-owners: `node scripts/ddd-release-config-evidence.mjs`
- platform-owners: `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- platform-owners: `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- platform-owners: `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- platform-owners: `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- platform-events: `node scripts/ddd-release-config-evidence.mjs`
- platform-events: `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- platform-events: `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- platform-events: `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- platform-events: `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- ai-owner: `node scripts/ddd-release-config-evidence.mjs`
- ai-owner: `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- ai-owner: `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- ai-owner: `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- ai-owner: `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`
- payment-owner: `node scripts/ddd-release-config-evidence.mjs`
- payment-owner: `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- payment-owner: `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- payment-owner: `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- payment-owner: `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

## Stop Reasons

- authenticated performance baseline not ready: READY_FOR_STRICT_GATE_RERUN
- cutover checklist blocked: database-performance
- cutover checklist blocked: deployable-images
- cutover checklist blocked: evidence-integrity
- cutover checklist blocked: release-environment
- cutover checklist blocked: rollback-safety
- cutover checklist blocked: runtime-business-acceptance
- owner input receipt pending: releaseEnvReadinessStatus,releaseEnvReadinessBlockers,releaseEnvReadinessPlaceholders
- strict release gate blockers=0

## Next Commands

- `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
- `DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts/ddd/release/release-env-bootstrap.sh`
- `node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env <release-env-file>`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-alias-sync.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
- `node scripts/ddd-release-config-evidence.mjs`

