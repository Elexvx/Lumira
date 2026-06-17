# DDD Release Env Owner Input Packet: release-infra

Concrete values are intentionally omitted from this artifact.
Generated at: 2026-06-17T08:13:17.325Z
Status: ADVISORY
Inputs: 9
Secret inputs: 3
Production endpoint inputs: 4
Owner production value inputs: 2
Reasons: owner-production-value, production-endpoint, secret-manager
Redacted handoff: artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md

## Inputs

- `LUMIRA_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=runtime; requirement=backend base url
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: LUMIRA_BASE_URL, DEPLOY_CHECK_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `CORS_ALLOWED_ORIGIN_PATTERNS`: class=identifier; reason=owner-production-value; secret=false; status=PLACEHOLDER; group=runtime; requirement=cors origins
  - validation: https=false; nonLocal=false; minLength=none; expectedValues=none
  - aliases: CORS_ALLOWED_ORIGIN_PATTERNS, SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS
  - guidance: Collect the production value from the owning release context.
- `DB_PASSWORD`: class=secret; reason=secret-manager; secret=true; status=PLACEHOLDER; group=runtime; requirement=database password
  - validation: https=false; nonLocal=false; minLength=16; expectedValues=none
  - aliases: DB_PASSWORD, SPRING_DATASOURCE_PASSWORD, MYSQL_PASSWORD
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- `DB_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=runtime; requirement=database url
  - validation: https=false; nonLocal=true; minLength=none; expectedValues=none
  - aliases: DB_URL, SPRING_DATASOURCE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `DB_USERNAME`: class=identifier; reason=owner-production-value; secret=false; status=PLACEHOLDER; group=runtime; requirement=database username
  - validation: https=false; nonLocal=false; minLength=none; expectedValues=none
  - aliases: DB_USERNAME, SPRING_DATASOURCE_USERNAME, MYSQL_USER
  - guidance: Collect the production value from the owning release context.
- `FIELD_SECRET`: class=secret; reason=secret-manager; secret=true; status=PLACEHOLDER; group=runtime; requirement=field secret
  - validation: https=false; nonLocal=false; minLength=32; expectedValues=none
  - aliases: FIELD_SECRET, SAAS_SECURITY_FIELD_SECRET
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- `PLAYWRIGHT_BASE_URL`: class=url; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=runtime; requirement=frontend base url
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: PLAYWRIGHT_BASE_URL, FRONTEND_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- `JWT_SECRET`: class=secret; reason=secret-manager; secret=true; status=PLACEHOLDER; group=runtime; requirement=jwt secret
  - validation: https=false; nonLocal=false; minLength=32; expectedValues=none
  - aliases: JWT_SECRET, SAAS_SECURITY_JWT_SECRET
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- `REDIS_HOST`: class=identifier; reason=production-endpoint; secret=false; status=PLACEHOLDER; group=runtime; requirement=redis host
  - validation: https=false; nonLocal=true; minLength=none; expectedValues=none
  - aliases: REDIS_HOST, SPRING_DATA_REDIS_HOST
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
