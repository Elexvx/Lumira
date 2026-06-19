# DDD Release Env Owner Input Packet: platform-owners

Concrete values are intentionally omitted from this artifact.
Generated at: 2026-06-18T19:37:26.213Z
Status: NOT_READY
Inputs: 9
Secret inputs: 0
Production endpoint inputs: 9
Owner production value inputs: 0
Reasons: production-endpoint
Redacted handoff: artifacts/ddd/release/release-env-owner-handoff-redacted/02-platform-owners.md

## Inputs

- `AI_SERVICE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=owner-services; requirement=ai service
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: AI_SERVICE_BASE_URL, LUMIRA_AI_SERVICE_BASE_URL, LUMIRA_AI_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `AUTH_SERVICE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=owner-services; requirement=auth service
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: AUTH_SERVICE_BASE_URL, LUMIRA_AUTH_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `FILE_SERVICE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=owner-services; requirement=file service
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: FILE_SERVICE_BASE_URL, LUMIRA_FILE_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `JOB_EXECUTOR_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=owner-services; requirement=job executor
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: JOB_EXECUTOR_BASE_URL, LUMIRA_JOB_EXECUTOR_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `LOCALIZATION_SERVICE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=owner-services; requirement=localization service
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: LOCALIZATION_SERVICE_BASE_URL, LUMIRA_LOCALIZATION_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `MESSAGE_SERVICE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=owner-services; requirement=message service
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: MESSAGE_SERVICE_BASE_URL, LUMIRA_MESSAGE_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `PAYMENT_SERVICE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=owner-services; requirement=payment service
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: PAYMENT_SERVICE_BASE_URL, LUMIRA_PAYMENT_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `PLUGIN_SERVICE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=owner-services; requirement=plugin service
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: PLUGIN_SERVICE_BASE_URL, LUMIRA_PLUGIN_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `SYSTEM_SERVICE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=owner-services; requirement=system service
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: SYSTEM_SERVICE_BASE_URL, LUMIRA_SYSTEM_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.

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
