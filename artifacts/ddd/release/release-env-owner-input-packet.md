# DDD Release Env Owner Input Packet

Generated at: 2026-06-17T08:13:17.325Z
Status: ADVISORY
Env file: <release-env-file>
Value policy: No concrete environment values are emitted; this packet lists only owner, key, validation, reason, and redacted collection guidance.
Required owner inputs: 34
Owners: 5
Secret inputs: 8
Production endpoint inputs: 24
Owner production value inputs: 2
Blocking safe defaults available: 0
Safe defaults exhausted: true

## Owners

- platform-events: inputs=9, secrets=3, endpoints=6, ownerValues=0, reasons=production-endpoint|secret-manager, handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/01-platform-events.md
- platform-owners: inputs=9, secrets=0, endpoints=9, ownerValues=0, reasons=production-endpoint, handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/02-platform-owners.md
- release-infra: inputs=9, secrets=3, endpoints=4, ownerValues=2, reasons=owner-production-value|production-endpoint|secret-manager, handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md
- ai-owner: inputs=6, secrets=2, endpoints=4, ownerValues=0, reasons=production-endpoint|secret-manager, handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/04-ai-owner.md
- payment-owner: inputs=1, secrets=0, endpoints=1, ownerValues=0, reasons=production-endpoint, handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/05-payment-owner.md

## Inputs

- 1. `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL` owner=ai-owner class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL, LUMIRA_AI_OWNER_FILE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 2. `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL` owner=ai-owner class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL, LUMIRA_AI_OWNER_IAM_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 3. `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN` owner=ai-owner class=secret reason=secret-manager secret=true status=PLACEHOLDER
  - validation: https=false; nonLocal=false; minLength=32; expectedValues=none
  - aliases: LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN, LUMIRA_AI_OWNER_INTERNAL_TOKEN, SAAS_JOB_INTERNAL_TOKEN
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- 4. `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL` owner=ai-owner class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL, LUMIRA_AI_OWNER_PLATFORM_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 5. `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY` owner=ai-owner class=secret reason=secret-manager secret=true status=PLACEHOLDER
  - validation: https=false; nonLocal=false; minLength=32; expectedValues=none
  - aliases: LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_API_KEY
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- 6. `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL` owner=ai-owner class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_AI_PROVIDER_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 7. `PAYMENT_PUBLIC_BASE_URL` owner=payment-owner class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: PAYMENT_PUBLIC_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 8. `SAAS_EVENT_REDIS_STREAM_KEY` owner=platform-events class=secret reason=secret-manager secret=true status=PLACEHOLDER
  - validation: https=false; nonLocal=false; minLength=none; expectedValues=none
  - aliases: SAAS_EVENT_REDIS_STREAM_KEY, LUMIRA_EVENT_REDIS_STREAM_KEY
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- 9. `SAAS_JOB_BACKEND_BASE_URL` owner=platform-events class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: SAAS_JOB_BACKEND_BASE_URL, LUMIRA_JOB_BACKEND_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 10. `SAAS_JOB_FILE_SERVICE_BASE_URL` owner=platform-events class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: SAAS_JOB_FILE_SERVICE_BASE_URL, LUMIRA_JOB_FILE_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 11. `SAAS_JOB_INTERNAL_TOKEN` owner=platform-events class=secret reason=secret-manager secret=true status=PLACEHOLDER
  - validation: https=false; nonLocal=false; minLength=32; expectedValues=none
  - aliases: SAAS_JOB_INTERNAL_TOKEN, DDD_JOB_INTERNAL_TOKEN, LUMIRA_JOB_INTERNAL_TOKEN
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- 12. `SAAS_JOB_MESSAGE_SERVICE_BASE_URL` owner=platform-events class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: SAAS_JOB_MESSAGE_SERVICE_BASE_URL, LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 13. `SAAS_JOB_PAYMENT_SERVICE_BASE_URL` owner=platform-events class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: SAAS_JOB_PAYMENT_SERVICE_BASE_URL, LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 14. `SAAS_JOB_PLUGIN_SERVICE_BASE_URL` owner=platform-events class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: SAAS_JOB_PLUGIN_SERVICE_BASE_URL, LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 15. `XXL_JOB_ADMIN_ADDRESSES` owner=platform-events class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: XXL_JOB_ADMIN_ADDRESSES, LUMIRA_XXL_JOB_ADMIN_ADDRESSES
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 16. `XXL_JOB_ACCESS_TOKEN` owner=platform-events class=secret reason=secret-manager secret=true status=PLACEHOLDER
  - validation: https=false; nonLocal=false; minLength=32; expectedValues=none
  - aliases: XXL_JOB_ACCESS_TOKEN, XXL_JOB_ADMIN_ACCESS_TOKEN, LUMIRA_XXL_JOB_ACCESS_TOKEN
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- 17. `AI_SERVICE_BASE_URL` owner=platform-owners class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: AI_SERVICE_BASE_URL, LUMIRA_AI_SERVICE_BASE_URL, LUMIRA_AI_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 18. `AUTH_SERVICE_BASE_URL` owner=platform-owners class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: AUTH_SERVICE_BASE_URL, LUMIRA_AUTH_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 19. `FILE_SERVICE_BASE_URL` owner=platform-owners class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: FILE_SERVICE_BASE_URL, LUMIRA_FILE_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 20. `JOB_EXECUTOR_BASE_URL` owner=platform-owners class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: JOB_EXECUTOR_BASE_URL, LUMIRA_JOB_EXECUTOR_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 21. `LOCALIZATION_SERVICE_BASE_URL` owner=platform-owners class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: LOCALIZATION_SERVICE_BASE_URL, LUMIRA_LOCALIZATION_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 22. `MESSAGE_SERVICE_BASE_URL` owner=platform-owners class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: MESSAGE_SERVICE_BASE_URL, LUMIRA_MESSAGE_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 23. `PAYMENT_SERVICE_BASE_URL` owner=platform-owners class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: PAYMENT_SERVICE_BASE_URL, LUMIRA_PAYMENT_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 24. `PLUGIN_SERVICE_BASE_URL` owner=platform-owners class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: PLUGIN_SERVICE_BASE_URL, LUMIRA_PLUGIN_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 25. `SYSTEM_SERVICE_BASE_URL` owner=platform-owners class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: SYSTEM_SERVICE_BASE_URL, LUMIRA_SYSTEM_SERVICE_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 26. `LUMIRA_BASE_URL` owner=release-infra class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: LUMIRA_BASE_URL, DEPLOY_CHECK_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 27. `CORS_ALLOWED_ORIGIN_PATTERNS` owner=release-infra class=identifier reason=owner-production-value secret=false status=PLACEHOLDER
  - validation: https=false; nonLocal=false; minLength=none; expectedValues=none
  - aliases: CORS_ALLOWED_ORIGIN_PATTERNS, SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS
  - guidance: Collect the production value from the owning release context.
- 28. `DB_PASSWORD` owner=release-infra class=secret reason=secret-manager secret=true status=PLACEHOLDER
  - validation: https=false; nonLocal=false; minLength=16; expectedValues=none
  - aliases: DB_PASSWORD, SPRING_DATASOURCE_PASSWORD, MYSQL_PASSWORD
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- 29. `DB_URL` owner=release-infra class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=false; nonLocal=true; minLength=none; expectedValues=none
  - aliases: DB_URL, SPRING_DATASOURCE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 30. `DB_USERNAME` owner=release-infra class=identifier reason=owner-production-value secret=false status=PLACEHOLDER
  - validation: https=false; nonLocal=false; minLength=none; expectedValues=none
  - aliases: DB_USERNAME, SPRING_DATASOURCE_USERNAME, MYSQL_USER
  - guidance: Collect the production value from the owning release context.
- 31. `FIELD_SECRET` owner=release-infra class=secret reason=secret-manager secret=true status=PLACEHOLDER
  - validation: https=false; nonLocal=false; minLength=32; expectedValues=none
  - aliases: FIELD_SECRET, SAAS_SECURITY_FIELD_SECRET
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- 32. `PLAYWRIGHT_BASE_URL` owner=release-infra class=url reason=production-endpoint secret=false status=PLACEHOLDER
  - validation: https=true; nonLocal=true; minLength=none; expectedValues=none
  - aliases: PLAYWRIGHT_BASE_URL, FRONTEND_BASE_URL
  - guidance: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- 33. `JWT_SECRET` owner=release-infra class=secret reason=secret-manager secret=true status=PLACEHOLDER
  - validation: https=false; nonLocal=false; minLength=32; expectedValues=none
  - aliases: JWT_SECRET, SAAS_SECURITY_JWT_SECRET
  - guidance: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- 34. `REDIS_HOST` owner=release-infra class=identifier reason=production-endpoint secret=false status=PLACEHOLDER
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
Pass criteria:
- releaseEnvReadinessStatus: PASS
- releaseEnvReadinessBlockers: 0
- releaseEnvReadinessPlaceholders: 0
- releaseEnvReadinessMissing: 0
- configOwnerInputReconciliationStatus: PASS
- configOwnerInputReconciliationUnmappedKeys: 0
Commands:
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

Concrete values are intentionally omitted from this artifact.
