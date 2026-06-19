# DDD Release Env Readiness Redacted

Generated at: 2026-06-18T19:37:26.213Z
Status: NOT_READY
Env file: .env.release.local
Value policy: No concrete environment values are emitted; only key names, ownership, validation metadata, and redacted fill status.
Canonical keys: 48
Filled redacted: 12
Placeholders: 34
Missing: 0
Optional empty: 2
Blockers: 34
Secret keys: 10
Blocking safe defaults available: 0
Blocking values requiring owner input: 34
Safe defaults exhausted: true

## Owners

- platform-events: total=10, filled=1, placeholder=9, missing=0, optionalEmpty=0, blockers=9, secretKeys=3, safeDefaultAvailable=0, requiresOwnerInput=9
- platform-owners: total=9, filled=0, placeholder=9, missing=0, optionalEmpty=0, blockers=9, secretKeys=0, safeDefaultAvailable=0, requiresOwnerInput=9
- release-infra: total=12, filled=2, placeholder=9, missing=0, optionalEmpty=1, blockers=9, secretKeys=4, safeDefaultAvailable=0, requiresOwnerInput=9
- ai-owner: total=12, filled=6, placeholder=6, missing=0, optionalEmpty=0, blockers=6, secretKeys=2, safeDefaultAvailable=0, requiresOwnerInput=6
- payment-owner: total=2, filled=0, placeholder=1, missing=0, optionalEmpty=1, blockers=1, secretKeys=1, safeDefaultAvailable=0, requiresOwnerInput=1
- file-owner: total=3, filled=3, placeholder=0, missing=0, optionalEmpty=0, blockers=0, secretKeys=0, safeDefaultAvailable=0, requiresOwnerInput=0

## Blocking Keys

- `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL` owner=ai-owner class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL` owner=ai-owner class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN` owner=ai-owner class=secret secret=true status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=secret-manager
- `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL` owner=ai-owner class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY` owner=ai-owner class=secret secret=true status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=secret-manager
- `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL` owner=ai-owner class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `PAYMENT_PUBLIC_BASE_URL` owner=payment-owner class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `SAAS_EVENT_REDIS_STREAM_KEY` owner=platform-events class=secret secret=true status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=secret-manager
- `SAAS_JOB_BACKEND_BASE_URL` owner=platform-events class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `SAAS_JOB_FILE_SERVICE_BASE_URL` owner=platform-events class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `SAAS_JOB_INTERNAL_TOKEN` owner=platform-events class=secret secret=true status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=secret-manager
- `SAAS_JOB_MESSAGE_SERVICE_BASE_URL` owner=platform-events class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `SAAS_JOB_PAYMENT_SERVICE_BASE_URL` owner=platform-events class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `SAAS_JOB_PLUGIN_SERVICE_BASE_URL` owner=platform-events class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `XXL_JOB_ADMIN_ADDRESSES` owner=platform-events class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `XXL_JOB_ACCESS_TOKEN` owner=platform-events class=secret secret=true status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=secret-manager
- `AI_SERVICE_BASE_URL` owner=platform-owners class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `AUTH_SERVICE_BASE_URL` owner=platform-owners class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `FILE_SERVICE_BASE_URL` owner=platform-owners class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `JOB_EXECUTOR_BASE_URL` owner=platform-owners class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `LOCALIZATION_SERVICE_BASE_URL` owner=platform-owners class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `MESSAGE_SERVICE_BASE_URL` owner=platform-owners class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `PAYMENT_SERVICE_BASE_URL` owner=platform-owners class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `PLUGIN_SERVICE_BASE_URL` owner=platform-owners class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `SYSTEM_SERVICE_BASE_URL` owner=platform-owners class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `LUMIRA_BASE_URL` owner=release-infra class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `CORS_ALLOWED_ORIGIN_PATTERNS` owner=release-infra class=identifier secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=owner-production-value
- `DB_PASSWORD` owner=release-infra class=secret secret=true status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=secret-manager
- `DB_URL` owner=release-infra class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `DB_USERNAME` owner=release-infra class=identifier secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=owner-production-value
- `FIELD_SECRET` owner=release-infra class=secret secret=true status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=secret-manager
- `PLAYWRIGHT_BASE_URL` owner=release-infra class=url secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint
- `JWT_SECRET` owner=release-infra class=secret secret=true status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=secret-manager
- `REDIS_HOST` owner=release-infra class=identifier secret=false status=PLACEHOLDER safeDefaultAvailable=false requiresOwnerInput=true reason=production-endpoint

Concrete values are intentionally omitted from this artifact.
