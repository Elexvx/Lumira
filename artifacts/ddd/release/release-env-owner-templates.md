# DDD Release Env Owner Templates

Generated at: 2026-06-18T19:37:26.213Z
Status: NOT_READY
Env file: .env.release.local
Template dir: artifacts/ddd/release/release-env-owner-templates
Owners: 6
Canonical fill items: 48
Secret canonical keys: 10
Safe-to-prefill canonical keys: 11

Each owner template is intentionally scoped to one owner so release values can be collected in parallel without sharing unrelated secrets.

## release-infra

- Template: `artifacts/ddd/release/release-env-owner-templates/01-release-infra.env`
- Queue: ACTIONABLE; canExecute=true
- Canonical fill items: 12
- Secret canonical keys: 4
- Safe-to-prefill canonical keys: 1
- Keys: LUMIRA_BASE_URL, CORS_ALLOWED_ORIGIN_PATTERNS, DB_PASSWORD, DB_URL, DB_USERNAME, FIELD_SECRET, PLAYWRIGHT_BASE_URL, JWT_SECRET, REDIS_HOST, REDIS_PASSWORD, REDIS_PORT, TRUST_FORWARDED_HEADERS
- Run after merging owner values into the canonical fill template:
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

- Template: `artifacts/ddd/release/release-env-owner-templates/02-ai-owner.env`
- Queue: ACTIONABLE; canExecute=true
- Canonical fill items: 12
- Secret canonical keys: 2
- Safe-to-prefill canonical keys: 6
- Keys: LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED, LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED, LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED, LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED
- Run after merging owner values into the canonical fill template:
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

- Template: `artifacts/ddd/release/release-env-owner-templates/03-payment-owner.env`
- Queue: ACTIONABLE; canExecute=true
- Canonical fill items: 2
- Secret canonical keys: 1
- Safe-to-prefill canonical keys: 0
- Keys: PAYMENT_PUBLIC_BASE_URL, DDD_PAYMENT_WEBHOOK_SECRET
- Run after merging owner values into the canonical fill template:
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

- Template: `artifacts/ddd/release/release-env-owner-templates/04-platform-events.env`
- Queue: ACTIONABLE; canExecute=true
- Canonical fill items: 10
- Secret canonical keys: 3
- Safe-to-prefill canonical keys: 1
- Keys: SAAS_EVENT_REDIS_STREAM_KEY, SAAS_JOB_BACKEND_BASE_URL, SAAS_JOB_FILE_SERVICE_BASE_URL, SAAS_JOB_INTERNAL_TOKEN, SAAS_JOB_MESSAGE_SERVICE_BASE_URL, SAAS_JOB_PAYMENT_SERVICE_BASE_URL, SAAS_JOB_PLUGIN_SERVICE_BASE_URL, SAAS_EVENT_OUTBOX_DISPATCHER, XXL_JOB_ADMIN_ADDRESSES, XXL_JOB_ACCESS_TOKEN
- Run after merging owner values into the canonical fill template:
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

- Template: `artifacts/ddd/release/release-env-owner-templates/05-platform-owners.env`
- Queue: ACTIONABLE; canExecute=true
- Canonical fill items: 9
- Secret canonical keys: 0
- Safe-to-prefill canonical keys: 0
- Keys: AI_SERVICE_BASE_URL, AUTH_SERVICE_BASE_URL, FILE_SERVICE_BASE_URL, JOB_EXECUTOR_BASE_URL, LOCALIZATION_SERVICE_BASE_URL, MESSAGE_SERVICE_BASE_URL, PAYMENT_SERVICE_BASE_URL, PLUGIN_SERVICE_BASE_URL, SYSTEM_SERVICE_BASE_URL
- Run after merging owner values into the canonical fill template:
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

- Template: `artifacts/ddd/release/release-env-owner-templates/11-file-owner.env`
- Queue: WAITING; canExecute=false
- Canonical fill items: 3
- Secret canonical keys: 0
- Safe-to-prefill canonical keys: 3
- Keys: LUMIRA_FILE_OCR_MODE, LUMIRA_FILE_SECURITY_SCAN_MODE, UPLOAD_STORAGE_ROOT
- Run after merging owner values into the canonical fill template:
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

