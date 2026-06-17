# DDD Release Env Owner Handoff: ai-owner

Concrete values are intentionally omitted from this artifact.
Total keys: 12
Blocking keys: 6
Placeholders: 6
Missing: 0
Optional empty: 0
Secret keys: 2
Safe defaults available: 0
Requires owner input: 6
Owner input reasons: production-endpoint, secret-manager
Next command: `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`

## Keys

- `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL`: status=FILLED_REDACTED; class=runtime-setting; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=none; expectedValues=none
  guidance: Use the production runtime setting agreed by the owning context.
- `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL`: status=FILLED_REDACTED; class=runtime-setting; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=none; expectedValues=none
  guidance: Use the production runtime setting agreed by the owning context.
- `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED`: status=FILLED_REDACTED; class=toggle; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=none; expectedValues=true
  guidance: Use one of: true.
- `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`: status=PLACEHOLDER; class=url; secret=false; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=production-endpoint; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED`: status=FILLED_REDACTED; class=toggle; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=none; expectedValues=true
  guidance: Use one of: true.
- `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`: status=PLACEHOLDER; class=url; secret=false; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=production-endpoint; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN`: status=PLACEHOLDER; class=secret; secret=true; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=secret-manager; https=false; nonLocal=false; minLength=32; expectedValues=none
  guidance: Provide via approved secret manager or secure release channel; never commit.
- `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED`: status=FILLED_REDACTED; class=toggle; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=none; expectedValues=true
  guidance: Use one of: true.
- `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL`: status=PLACEHOLDER; class=url; secret=false; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=production-endpoint; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`: status=PLACEHOLDER; class=secret; secret=true; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=secret-manager; https=false; nonLocal=false; minLength=32; expectedValues=none
  guidance: Provide via approved secret manager or secure release channel; never commit.
- `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`: status=PLACEHOLDER; class=url; secret=false; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=production-endpoint; https=true; nonLocal=true; minLength=none; expectedValues=none
  guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED`: status=FILLED_REDACTED; class=toggle; secret=false; required=true; blocker=false; safeDefaultAvailable=false; requiresOwnerInput=false; reason=not-blocking; https=false; nonLocal=false; minLength=none; expectedValues=true
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
