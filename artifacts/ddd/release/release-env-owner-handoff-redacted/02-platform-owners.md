# DDD Release Env Owner Handoff: platform-owners

Concrete values are intentionally omitted from this artifact.
Total keys: 9
Blocking keys: 9
Placeholders: 9
Missing: 0
Optional empty: 0
Secret keys: 0
Safe defaults available: 0
Requires owner input: 9
Owner input reasons: production-endpoint
Next command: `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`

## Keys

- `AI_SERVICE_BASE_URL`: status=PLACEHOLDER; class=url; secret=false; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=production-endpoint; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `AUTH_SERVICE_BASE_URL`: status=PLACEHOLDER; class=url; secret=false; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=production-endpoint; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `FILE_SERVICE_BASE_URL`: status=PLACEHOLDER; class=url; secret=false; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=production-endpoint; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `JOB_EXECUTOR_BASE_URL`: status=PLACEHOLDER; class=url; secret=false; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=production-endpoint; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `LOCALIZATION_SERVICE_BASE_URL`: status=PLACEHOLDER; class=url; secret=false; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=production-endpoint; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `MESSAGE_SERVICE_BASE_URL`: status=PLACEHOLDER; class=url; secret=false; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=production-endpoint; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `PAYMENT_SERVICE_BASE_URL`: status=PLACEHOLDER; class=url; secret=false; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=production-endpoint; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `PLUGIN_SERVICE_BASE_URL`: status=PLACEHOLDER; class=url; secret=false; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=production-endpoint; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `SYSTEM_SERVICE_BASE_URL`: status=PLACEHOLDER; class=url; secret=false; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=production-endpoint; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.

## After Filling

- `node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env <release-env-file>`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-alias-sync.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
- `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
- `node scripts/ddd-release-readiness-summary.mjs`
- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
