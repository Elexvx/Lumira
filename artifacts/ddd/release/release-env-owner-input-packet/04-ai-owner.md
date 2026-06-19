# DDD Release Env Owner Input Packet: ai-owner

Concrete values are intentionally omitted from this artifact.
Generated at: 2026-06-18T19:37:26.213Z
Status: NOT_READY
Inputs: 6
Secret inputs: 2
Production endpoint inputs: 4
Owner production value inputs: 0
Reasons: production-endpoint, secret-manager
Redacted handoff: artifacts/ddd/release/release-env-owner-handoff-redacted/04-ai-owner.md

## Inputs

- `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=ai; requirement=file owner url
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_FILE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=ai; requirement=iam owner url
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_IAM_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN`: class=secret; reason=secret-manager; secret=true; status=PLACEHOLDER; group=ai; requirement=owner internal token
  - validation: https=false; nonLocal=false; minLength=32; expectedValues=none
  - aliases: LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTERNAL_TOKEN, SAAS_JOB_INTERNAL_TOKEN
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=ai; requirement=platform owner url
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_OWNER_PLATFORM_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`: class=secret; reason=secret-manager; secret=true; status=PLACEHOLDER; group=ai; requirement=provider api key
  - validation: https=false; nonLocal=false; minLength=32; expectedValues=none
  - aliases: LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_API_KEY
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=ai; requirement=provider base url
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_AI_PROVIDER_BASE_URL
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
