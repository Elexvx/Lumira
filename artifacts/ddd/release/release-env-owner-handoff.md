# DDD Release Env Owner Handoff

Generated at: 2026-06-17T08:13:17.325Z
Status: ADVISORY
Env file: .env.release.local
Owners: 6
Canonical fill items: 48
Unresolved aliases covered: 73

## Fast Path

- Objective: Fill canonical release env keys once, sync aliases, then rerun strict env and final go/no-go gates.
- Blocked until: All blocking release env placeholders are replaced in a permission-safe release env file.
- Commands:
  - `node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env .env.release.local`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-safe-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-provenance-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-alias-sync.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`
  - `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
  - `node scripts/ddd-release-readiness-summary.mjs`
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## Owners

## release-infra

- Queue: ACTIONABLE; canExecute=true
- Next command: `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
- Canonical fill items: 12
- Secret canonical keys: 4
- Safe-to-prefill canonical keys: 1
- Unresolved aliases covered: 20
- Ready batches: p0-docker-release-infra, p0-release-config-release-infra, p0-release-env-lint-release-infra
- Blocked batches: none
- Fill canonical keys:
  - `LUMIRA_BASE_URL` (runtime.backend base url; class=url; secret=false; aliases=LUMIRA_BASE_URL|DEPLOY_CHECK_BASE_URL)
  - `CORS_ALLOWED_ORIGIN_PATTERNS` (runtime.cors origins; class=identifier; secret=false; aliases=CORS_ALLOWED_ORIGIN_PATTERNS|SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS)
  - `DB_PASSWORD` (runtime.database password; class=secret; secret=true; aliases=DB_PASSWORD|SPRING_DATASOURCE_PASSWORD|MYSQL_PASSWORD)
  - `DB_URL` (runtime.database url; class=url; secret=false; aliases=DB_URL|SPRING_DATASOURCE_URL)
  - `DB_USERNAME` (runtime.database username; class=identifier; secret=false; aliases=DB_USERNAME|SPRING_DATASOURCE_USERNAME|MYSQL_USER)
  - `FIELD_SECRET` (runtime.field secret; class=secret; secret=true; aliases=FIELD_SECRET|SAAS_SECURITY_FIELD_SECRET)
  - `PLAYWRIGHT_BASE_URL` (runtime.frontend base url; class=url; secret=false; aliases=PLAYWRIGHT_BASE_URL|FRONTEND_BASE_URL)
  - `JWT_SECRET` (runtime.jwt secret; class=secret; secret=true; aliases=JWT_SECRET|SAAS_SECURITY_JWT_SECRET)
  - `REDIS_HOST` (runtime.redis host; class=identifier; secret=false; aliases=REDIS_HOST|SPRING_DATA_REDIS_HOST)
  - `REDIS_PASSWORD` (runtime.redis password; class=secret; secret=true; aliases=REDIS_PASSWORD|SPRING_DATA_REDIS_PASSWORD)
  - `REDIS_PORT` (runtime.redis port; class=port; secret=false; aliases=REDIS_PORT|SPRING_DATA_REDIS_PORT)
  - `TRUST_FORWARDED_HEADERS` (runtime.trusted proxy mode; class=toggle; secret=false; aliases=TRUST_FORWARDED_HEADERS|SAAS_WEB_TRUST_FORWARDED_HEADERS)
- Run after fill:
  - `node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env .env.release.local`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-safe-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-provenance-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-alias-sync.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
  - `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
  - `node scripts/ddd-release-readiness-summary.mjs`
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## ai-owner

- Queue: ACTIONABLE; canExecute=true
- Next command: `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
- Canonical fill items: 12
- Secret canonical keys: 2
- Safe-to-prefill canonical keys: 6
- Unresolved aliases covered: 13
- Ready batches: p0-release-config-ai-owner
- Blocked batches: p1-rollback-ai-owner
- Fill canonical keys:
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL` (ai.chat model; class=runtime-setting; secret=false; aliases=LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL|LUMIRA_AI_CHAT_MODEL)
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL` (ai.embedding model; class=runtime-setting; secret=false; aliases=LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL|LUMIRA_AI_EMBEDDING_MODEL)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED` (ai.file owner enabled; class=toggle; secret=false; aliases=LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL` (ai.file owner url; class=url; secret=false; aliases=LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL|LUMIRA_AI_OWNER_FILE_BASE_URL)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED` (ai.iam owner enabled; class=toggle; secret=false; aliases=LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL` (ai.iam owner url; class=url; secret=false; aliases=LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL|LUMIRA_AI_OWNER_IAM_BASE_URL)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN` (ai.owner internal token; class=secret; secret=true; aliases=LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN|LUMIRA_AI_OWNER_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED` (ai.platform owner enabled; class=toggle; secret=false; aliases=LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED)
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL` (ai.platform owner url; class=url; secret=false; aliases=LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL|LUMIRA_AI_OWNER_PLATFORM_BASE_URL)
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY` (ai.provider api key; class=secret; secret=true; aliases=LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY|LUMIRA_AI_PROVIDER_API_KEY)
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL` (ai.provider base url; class=url; secret=false; aliases=LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL|LUMIRA_AI_PROVIDER_BASE_URL)
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED` (ai.provider enabled; class=toggle; secret=false; aliases=LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED|LUMIRA_AI_PROVIDER_ENABLED)
- Run after fill:
  - `node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env .env.release.local`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-safe-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-provenance-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-alias-sync.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
  - `node scripts/ddd-release-readiness-summary.mjs`
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## payment-owner

- Queue: ACTIONABLE; canExecute=true
- Next command: `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
- Canonical fill items: 2
- Secret canonical keys: 1
- Safe-to-prefill canonical keys: 0
- Unresolved aliases covered: 1
- Ready batches: p0-release-config-payment-owner
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
- Fill canonical keys:
  - `PAYMENT_PUBLIC_BASE_URL` (payment.payment public url; class=url; secret=false; aliases=PAYMENT_PUBLIC_BASE_URL)
  - `DDD_PAYMENT_WEBHOOK_SECRET` (payment.payment webhook secret; class=secret; secret=true; aliases=DDD_PAYMENT_WEBHOOK_SECRET|PAYMENT_WEBHOOK_SECRET|LUMIRA_PAYMENT_WEBHOOK_SECRET)
- Run after fill:
  - `node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env .env.release.local`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-safe-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-provenance-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-alias-sync.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
  - `node scripts/ddd-release-readiness-summary.mjs`
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## platform-events

- Queue: ACTIONABLE; canExecute=true
- Next command: `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
- Canonical fill items: 10
- Secret canonical keys: 3
- Safe-to-prefill canonical keys: 1
- Unresolved aliases covered: 20
- Ready batches: p0-release-config-platform-events
- Blocked batches: none
- Fill canonical keys:
  - `SAAS_EVENT_REDIS_STREAM_KEY` (jobs-and-events.event stream key; class=secret; secret=true; aliases=SAAS_EVENT_REDIS_STREAM_KEY|LUMIRA_EVENT_REDIS_STREAM_KEY)
  - `SAAS_JOB_BACKEND_BASE_URL` (jobs-and-events.job backend url; class=url; secret=false; aliases=SAAS_JOB_BACKEND_BASE_URL|LUMIRA_JOB_BACKEND_BASE_URL)
  - `SAAS_JOB_FILE_SERVICE_BASE_URL` (jobs-and-events.job file url; class=url; secret=false; aliases=SAAS_JOB_FILE_SERVICE_BASE_URL|LUMIRA_JOB_FILE_SERVICE_BASE_URL)
  - `SAAS_JOB_INTERNAL_TOKEN` (jobs-and-events.job internal token; class=secret; secret=true; aliases=SAAS_JOB_INTERNAL_TOKEN|DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN)
  - `SAAS_JOB_MESSAGE_SERVICE_BASE_URL` (jobs-and-events.job message url; class=url; secret=false; aliases=SAAS_JOB_MESSAGE_SERVICE_BASE_URL|LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL)
  - `SAAS_JOB_PAYMENT_SERVICE_BASE_URL` (jobs-and-events.job payment url; class=url; secret=false; aliases=SAAS_JOB_PAYMENT_SERVICE_BASE_URL|LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL)
  - `SAAS_JOB_PLUGIN_SERVICE_BASE_URL` (jobs-and-events.job plugin url; class=url; secret=false; aliases=SAAS_JOB_PLUGIN_SERVICE_BASE_URL|LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL)
  - `SAAS_EVENT_OUTBOX_DISPATCHER` (jobs-and-events.outbox dispatcher; class=runtime-setting; secret=false; aliases=SAAS_EVENT_OUTBOX_DISPATCHER|LUMIRA_EVENT_OUTBOX_DISPATCHER)
  - `XXL_JOB_ADMIN_ADDRESSES` (jobs-and-events.xxl job admin; class=url; secret=false; aliases=XXL_JOB_ADMIN_ADDRESSES|LUMIRA_XXL_JOB_ADMIN_ADDRESSES)
  - `XXL_JOB_ACCESS_TOKEN` (jobs-and-events.xxl job token; class=secret; secret=true; aliases=XXL_JOB_ACCESS_TOKEN|XXL_JOB_ADMIN_ACCESS_TOKEN|LUMIRA_XXL_JOB_ACCESS_TOKEN)
- Run after fill:
  - `node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env .env.release.local`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-safe-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-provenance-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-alias-sync.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
  - `node scripts/ddd-release-readiness-summary.mjs`
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## platform-owners

- Queue: ACTIONABLE; canExecute=true
- Next command: `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
- Canonical fill items: 9
- Secret canonical keys: 0
- Safe-to-prefill canonical keys: 0
- Unresolved aliases covered: 19
- Ready batches: p0-release-config-platform-owners
- Blocked batches: none
- Fill canonical keys:
  - `AI_SERVICE_BASE_URL` (owner-services.ai service; class=url; secret=false; aliases=AI_SERVICE_BASE_URL|LUMIRA_AI_SERVICE_BASE_URL|LUMIRA_AI_BASE_URL)
  - `AUTH_SERVICE_BASE_URL` (owner-services.auth service; class=url; secret=false; aliases=AUTH_SERVICE_BASE_URL|LUMIRA_AUTH_SERVICE_BASE_URL)
  - `FILE_SERVICE_BASE_URL` (owner-services.file service; class=url; secret=false; aliases=FILE_SERVICE_BASE_URL|LUMIRA_FILE_SERVICE_BASE_URL)
  - `JOB_EXECUTOR_BASE_URL` (owner-services.job executor; class=url; secret=false; aliases=JOB_EXECUTOR_BASE_URL|LUMIRA_JOB_EXECUTOR_BASE_URL)
  - `LOCALIZATION_SERVICE_BASE_URL` (owner-services.localization service; class=url; secret=false; aliases=LOCALIZATION_SERVICE_BASE_URL|LUMIRA_LOCALIZATION_SERVICE_BASE_URL)
  - `MESSAGE_SERVICE_BASE_URL` (owner-services.message service; class=url; secret=false; aliases=MESSAGE_SERVICE_BASE_URL|LUMIRA_MESSAGE_SERVICE_BASE_URL)
  - `PAYMENT_SERVICE_BASE_URL` (owner-services.payment service; class=url; secret=false; aliases=PAYMENT_SERVICE_BASE_URL|LUMIRA_PAYMENT_SERVICE_BASE_URL)
  - `PLUGIN_SERVICE_BASE_URL` (owner-services.plugin service; class=url; secret=false; aliases=PLUGIN_SERVICE_BASE_URL|LUMIRA_PLUGIN_SERVICE_BASE_URL)
  - `SYSTEM_SERVICE_BASE_URL` (owner-services.system service; class=url; secret=false; aliases=SYSTEM_SERVICE_BASE_URL|LUMIRA_SYSTEM_SERVICE_BASE_URL)
- Run after fill:
  - `node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env .env.release.local`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-safe-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-provenance-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-alias-sync.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
  - `node scripts/ddd-release-readiness-summary.mjs`
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## file-owner

- Queue: WAITING; canExecute=false
- Next command: `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
- Canonical fill items: 3
- Secret canonical keys: 0
- Safe-to-prefill canonical keys: 3
- Unresolved aliases covered: 0
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-rollback-file-owner
- Fill canonical keys:
  - `LUMIRA_FILE_OCR_MODE` (file-processing.ocr mode; class=runtime-setting; secret=false; aliases=LUMIRA_FILE_OCR_MODE)
  - `LUMIRA_FILE_SECURITY_SCAN_MODE` (file-processing.security scan mode; class=runtime-setting; secret=false; aliases=LUMIRA_FILE_SECURITY_SCAN_MODE)
  - `UPLOAD_STORAGE_ROOT` (file-processing.upload storage root; class=runtime-setting; secret=false; aliases=UPLOAD_STORAGE_ROOT|LUMIRA_UPLOAD_STORAGE_ROOT)
- Run after fill:
  - `node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env .env.release.local`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-safe-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-provenance-defaults.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-alias-sync.mjs`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
  - `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
  - `node scripts/ddd-file-processing-e2e-smoke.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

