# DDD Release Env Owner Handoff: platform-events

Concrete values are intentionally omitted from this artifact.
Total keys: 10
Blocking keys: 0
Placeholders: 0
Missing: 0
Optional empty: 0
Secret keys: 3
Safe defaults available: 0
Requires owner input: 0
Owner input reasons: none
Next command: `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`

## Keys

- `SAAS_EVENT_REDIS_STREAM_KEY`: status=FILLED_REDACTED; class=secret; secret=true; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=none; expectedValues=none
  guidance: Provide via approved secret manager or secure release channel; never commit.
- `SAAS_JOB_BACKEND_BASE_URL`: status=FILLED_REDACTED; class=url; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `SAAS_JOB_FILE_SERVICE_BASE_URL`: status=FILLED_REDACTED; class=url; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `SAAS_JOB_INTERNAL_TOKEN`: status=FILLED_REDACTED; class=secret; secret=true; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=32; expectedValues=none
  guidance: Provide via approved secret manager or secure release channel; never commit.
- `SAAS_JOB_MESSAGE_SERVICE_BASE_URL`: status=FILLED_REDACTED; class=url; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `SAAS_JOB_PAYMENT_SERVICE_BASE_URL`: status=FILLED_REDACTED; class=url; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `SAAS_JOB_PLUGIN_SERVICE_BASE_URL`: status=FILLED_REDACTED; class=url; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `SAAS_EVENT_OUTBOX_DISPATCHER`: status=FILLED_REDACTED; class=runtime-setting; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=none; expectedValues=none
  guidance: Use the production runtime setting agreed by the owning context.
- `XXL_JOB_ADMIN_ADDRESSES`: status=FILLED_REDACTED; class=url; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `XXL_JOB_ACCESS_TOKEN`: status=FILLED_REDACTED; class=secret; secret=true; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=32; expectedValues=none
  guidance: Provide via approved secret manager or secure release channel; never commit.

## After Filling

- `node bin/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env <release-env-file>`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-safe-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-provenance-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-alias-sync.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-file-lint.mjs`
- `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
- `node bin/ddd-release-readiness-summary.mjs`
- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
