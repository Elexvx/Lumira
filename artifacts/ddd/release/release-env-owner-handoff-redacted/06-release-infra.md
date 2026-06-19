# DDD Release Env Owner Handoff: release-infra

Concrete values are intentionally omitted from this artifact.
Total keys: 12
Blocking keys: 0
Placeholders: 0
Missing: 0
Optional empty: 0
Secret keys: 4
Safe defaults available: 0
Requires owner input: 0
Owner input reasons: none
Next command: `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`

## Keys

- `LUMIRA_BASE_URL`: status=FILLED_REDACTED; class=url; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `CORS_ALLOWED_ORIGIN_PATTERNS`: status=FILLED_REDACTED; class=identifier; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=none; expectedValues=none
  guidance: Use the production value from the owning context.
- `DB_PASSWORD`: status=FILLED_REDACTED; class=secret; secret=true; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=16; expectedValues=none
  guidance: Provide via approved secret manager or secure release channel; never commit.
- `DB_URL`: status=FILLED_REDACTED; class=url; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent endpoint or DSN; localhost and placeholders are rejected when nonLocal=true.
- `DB_USERNAME`: status=FILLED_REDACTED; class=identifier; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=none; expectedValues=none
  guidance: Use the production value from the owning context.
- `FIELD_SECRET`: status=FILLED_REDACTED; class=secret; secret=true; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=32; expectedValues=none
  guidance: Provide via approved secret manager or secure release channel; never commit.
- `PLAYWRIGHT_BASE_URL`: status=FILLED_REDACTED; class=url; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `JWT_SECRET`: status=FILLED_REDACTED; class=secret; secret=true; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=32; expectedValues=none
  guidance: Provide via approved secret manager or secure release channel; never commit.
- `REDIS_HOST`: status=FILLED_REDACTED; class=identifier; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production value from the owning context.
- `REDIS_PASSWORD`: status=FILLED_REDACTED; class=secret; secret=true; required=false; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=16; expectedValues=none
  guidance: Provide via approved secret manager or secure release channel; never commit.
- `REDIS_PORT`: status=FILLED_REDACTED; class=port; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=none; expectedValues=none
  guidance: Use the production service TCP port.
- `TRUST_FORWARDED_HEADERS`: status=FILLED_REDACTED; class=toggle; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=none; expectedValues=true
  guidance: Use one of: true.

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
