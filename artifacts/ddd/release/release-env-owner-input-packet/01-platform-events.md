# DDD Release Env Owner Input Packet: platform-events

Concrete values are intentionally omitted from this artifact.
Generated at: 2026-06-17T08:13:17.325Z
Status: ADVISORY
Inputs: 9
Secret inputs: 3
Production endpoint inputs: 6
Owner production value inputs: 0
Reasons: production-endpoint, secret-manager
Redacted handoff: artifacts/ddd/release/release-env-owner-handoff-redacted/01-platform-events.md

## Inputs

- `SAAS_EVENT_REDIS_STREAM_KEY`: class=secret; reason=secret-manager; secret=true; status=PLACEHOLDER; group=jobs-and-events; requirement=event stream key
  - validation: https=false; nonLocal=false; minLength=none; expectedValues=none
  - aliases: SAAS_EVENT_REDIS_STREAM_KEY, LUMIRA_EVENT_REDIS_STREAM_KEY
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- `SAAS_JOB_BACKEND_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=jobs-and-events; requirement=job backend url
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: SAAS_JOB_BACKEND_BASE_URL, LUMIRA_JOB_BACKEND_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `SAAS_JOB_FILE_SERVICE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=jobs-and-events; requirement=job file url
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: SAAS_JOB_FILE_SERVICE_BASE_URL, LUMIRA_JOB_FILE_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `SAAS_JOB_INTERNAL_TOKEN`: class=secret; reason=secret-manager; secret=true; status=PLACEHOLDER; group=jobs-and-events; requirement=job internal token
  - validation: https=false; nonLocal=false; minLength=32; expectedValues=none
  - aliases: SAAS_JOB_INTERNAL_TOKEN, DDD_JOB_INTERNAL_TOKEN, LUMIRA_JOB_INTERNAL_TOKEN
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- `SAAS_JOB_MESSAGE_SERVICE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=jobs-and-events; requirement=job message url
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: SAAS_JOB_MESSAGE_SERVICE_BASE_URL, LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `SAAS_JOB_PAYMENT_SERVICE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=jobs-and-events; requirement=job payment url
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: SAAS_JOB_PAYMENT_SERVICE_BASE_URL, LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `SAAS_JOB_PLUGIN_SERVICE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=jobs-and-events; requirement=job plugin url
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: SAAS_JOB_PLUGIN_SERVICE_BASE_URL, LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `XXL_JOB_ADMIN_ADDRESSES`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=jobs-and-events; requirement=xxl job admin
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: XXL_JOB_ADMIN_ADDRESSES, LUMIRA_XXL_JOB_ADMIN_ADDRESSES
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `XXL_JOB_ACCESS_TOKEN`: class=secret; reason=secret-manager; secret=true; status=PLACEHOLDER; group=jobs-and-events; requirement=xxl job token
  - validation: https=false; nonLocal=false; minLength=32; expectedValues=none
  - aliases: XXL_JOB_ACCESS_TOKEN, XXL_JOB_ADMIN_ACCESS_TOKEN, LUMIRA_XXL_JOB_ACCESS_TOKEN
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.

## After Collection

- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-alias-sync.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
- `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
- `node scripts/ddd-release-readiness-summary.mjs`
- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## Receipt Gate

Purpose: Verify that collected owner values removed release env placeholders without exposing concrete values.
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-alias-sync.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
- `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-readiness-summary.mjs`
- `node scripts/ddd-release-config-owner-input-reconciliation.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts/ddd/release/release-preflight-gate.sh`
- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
