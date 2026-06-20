# DDD Release Env Canonical Fill

Generated at: 2026-06-20T19:42:26.704Z
Status: NOT_READY
Env file: .env.release.local
Canonical fill items: 48
Unresolved aliases covered: 0
Owners: 10

Fill the canonical key once, then run alias sync to propagate equivalent keys.

## 1. LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL

- Owner: ai-owner
- Group: ai
- Requirement: chat model
- Required: true
- Value class: runtime-setting; secret=false; safeToPreFill=true
- Fill guidance: Use the production runtime setting agreed by the owning context.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL` (present)
  - `LUMIRA_AI_CHAT_MODEL` (present)

## 2. LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL

- Owner: ai-owner
- Group: ai
- Requirement: embedding model
- Required: true
- Value class: runtime-setting; secret=false; safeToPreFill=true
- Fill guidance: Use the production runtime setting agreed by the owning context.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL` (present)
  - `LUMIRA_AI_EMBEDDING_MODEL` (present)

## 3. LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED

- Owner: ai-owner
- Group: ai
- Requirement: file owner enabled
- Required: true
- Value class: toggle; secret=false; safeToPreFill=true
- Fill guidance: Use one of: true.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=true
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED` (present)

## 4. LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL

- Owner: ai-owner
- Group: ai
- Requirement: file owner url
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL` (present)
  - `LUMIRA_AI_OWNER_FILE_BASE_URL` (present)

## 5. LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED

- Owner: ai-owner
- Group: ai
- Requirement: iam owner enabled
- Required: true
- Value class: toggle; secret=false; safeToPreFill=true
- Fill guidance: Use one of: true.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=true
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED` (present)

## 6. LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL

- Owner: ai-owner
- Group: ai
- Requirement: iam owner url
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL` (present)
  - `LUMIRA_AI_OWNER_IAM_BASE_URL` (present)

## 7. LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN

- Owner: ai-owner
- Group: ai
- Requirement: owner internal token
- Required: true
- Value class: secret; secret=true; safeToPreFill=false
- Fill guidance: Provide via approved secret manager or secure release channel; never commit.
- Validation: https=false, nonLocal=false, minLength=32, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN` (present)
  - `LUMIRA_AI_OWNER_INTERNAL_TOKEN` (present)
  - `SAAS_JOB_INTERNAL_TOKEN` (present)

## 8. LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED

- Owner: ai-owner
- Group: ai
- Requirement: platform owner enabled
- Required: true
- Value class: toggle; secret=false; safeToPreFill=true
- Fill guidance: Use one of: true.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=true
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED` (present)

## 9. LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL

- Owner: ai-owner
- Group: ai
- Requirement: platform owner url
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL` (present)
  - `LUMIRA_AI_OWNER_PLATFORM_BASE_URL` (present)

## 10. LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY

- Owner: ai-owner
- Group: ai
- Requirement: provider api key
- Required: true
- Value class: secret; secret=true; safeToPreFill=false
- Fill guidance: Provide via approved secret manager or secure release channel; never commit.
- Validation: https=false, nonLocal=false, minLength=32, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY` (present)
  - `LUMIRA_AI_PROVIDER_API_KEY` (present)

## 11. LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL

- Owner: ai-owner
- Group: ai
- Requirement: provider base url
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL` (present)
  - `LUMIRA_AI_PROVIDER_BASE_URL` (present)

## 12. LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED

- Owner: ai-owner
- Group: ai
- Requirement: provider enabled
- Required: true
- Value class: toggle; secret=false; safeToPreFill=true
- Fill guidance: Use one of: true.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=true
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED` (present)
  - `LUMIRA_AI_PROVIDER_ENABLED` (present)

## 13. LUMIRA_FILE_OCR_MODE

- Owner: file-owner
- Group: file-processing
- Requirement: ocr mode
- Required: true
- Value class: runtime-setting; secret=false; safeToPreFill=true
- Fill guidance: Use the production runtime setting agreed by the owning context.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_FILE_OCR_MODE` (present)

## 14. LUMIRA_FILE_SECURITY_SCAN_MODE

- Owner: file-owner
- Group: file-processing
- Requirement: security scan mode
- Required: true
- Value class: runtime-setting; secret=false; safeToPreFill=true
- Fill guidance: Use the production runtime setting agreed by the owning context.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_FILE_SECURITY_SCAN_MODE` (present)

## 15. UPLOAD_STORAGE_ROOT

- Owner: file-owner
- Group: file-processing
- Requirement: upload storage root
- Required: true
- Value class: runtime-setting; secret=false; safeToPreFill=true
- Fill guidance: Use the production runtime setting agreed by the owning context.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `UPLOAD_STORAGE_ROOT` (present)
  - `LUMIRA_UPLOAD_STORAGE_ROOT` (present)

## 16. PAYMENT_PUBLIC_BASE_URL

- Owner: payment-owner
- Group: payment
- Requirement: payment public url
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `PAYMENT_PUBLIC_BASE_URL` (present)

## 17. DDD_PAYMENT_WEBHOOK_SECRET

- Owner: payment-owner
- Group: payment
- Requirement: payment webhook secret
- Required: false
- Value class: secret; secret=true; safeToPreFill=false
- Fill guidance: Provide via approved secret manager or secure release channel; never commit.
- Validation: https=false, nonLocal=false, minLength=32, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `DDD_PAYMENT_WEBHOOK_SECRET` (present)
  - `PAYMENT_WEBHOOK_SECRET` (present)
  - `LUMIRA_PAYMENT_WEBHOOK_SECRET` (present)

## 18. SAAS_EVENT_REDIS_STREAM_KEY

- Owner: platform-events
- Group: jobs-and-events
- Requirement: event stream key
- Required: true
- Value class: secret; secret=true; safeToPreFill=false
- Fill guidance: Provide via approved secret manager or secure release channel; never commit.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `SAAS_EVENT_REDIS_STREAM_KEY` (present)
  - `LUMIRA_EVENT_REDIS_STREAM_KEY` (present)

## 19. SAAS_JOB_BACKEND_BASE_URL

- Owner: platform-events
- Group: jobs-and-events
- Requirement: job backend url
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `SAAS_JOB_BACKEND_BASE_URL` (present)
  - `LUMIRA_JOB_BACKEND_BASE_URL` (present)

## 20. SAAS_JOB_FILE_SERVICE_BASE_URL

- Owner: platform-events
- Group: jobs-and-events
- Requirement: job file url
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `SAAS_JOB_FILE_SERVICE_BASE_URL` (present)
  - `LUMIRA_JOB_FILE_SERVICE_BASE_URL` (present)

## 21. SAAS_JOB_INTERNAL_TOKEN

- Owner: platform-events
- Group: jobs-and-events
- Requirement: job internal token
- Required: true
- Value class: secret; secret=true; safeToPreFill=false
- Fill guidance: Provide via approved secret manager or secure release channel; never commit.
- Validation: https=false, nonLocal=false, minLength=32, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `SAAS_JOB_INTERNAL_TOKEN` (present)
  - `DDD_JOB_INTERNAL_TOKEN` (present)
  - `LUMIRA_JOB_INTERNAL_TOKEN` (present)

## 22. SAAS_JOB_MESSAGE_SERVICE_BASE_URL

- Owner: platform-events
- Group: jobs-and-events
- Requirement: job message url
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `SAAS_JOB_MESSAGE_SERVICE_BASE_URL` (present)
  - `LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL` (present)

## 23. SAAS_JOB_PAYMENT_SERVICE_BASE_URL

- Owner: platform-events
- Group: jobs-and-events
- Requirement: job payment url
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `SAAS_JOB_PAYMENT_SERVICE_BASE_URL` (present)
  - `LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL` (present)

## 24. SAAS_JOB_PLUGIN_SERVICE_BASE_URL

- Owner: platform-events
- Group: jobs-and-events
- Requirement: job plugin url
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `SAAS_JOB_PLUGIN_SERVICE_BASE_URL` (present)
  - `LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL` (present)

## 25. SAAS_EVENT_OUTBOX_DISPATCHER

- Owner: platform-events
- Group: jobs-and-events
- Requirement: outbox dispatcher
- Required: true
- Value class: runtime-setting; secret=false; safeToPreFill=true
- Fill guidance: Use the production runtime setting agreed by the owning context.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `SAAS_EVENT_OUTBOX_DISPATCHER` (present)
  - `LUMIRA_EVENT_OUTBOX_DISPATCHER` (present)

## 26. XXL_JOB_ADMIN_ADDRESSES

- Owner: platform-events
- Group: jobs-and-events
- Requirement: xxl job admin
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `XXL_JOB_ADMIN_ADDRESSES` (present)
  - `LUMIRA_XXL_JOB_ADMIN_ADDRESSES` (present)

## 27. XXL_JOB_ACCESS_TOKEN

- Owner: platform-events
- Group: jobs-and-events
- Requirement: xxl job token
- Required: true
- Value class: secret; secret=true; safeToPreFill=false
- Fill guidance: Provide via approved secret manager or secure release channel; never commit.
- Validation: https=false, nonLocal=false, minLength=32, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `XXL_JOB_ACCESS_TOKEN` (present)
  - `XXL_JOB_ADMIN_ACCESS_TOKEN` (present)
  - `LUMIRA_XXL_JOB_ACCESS_TOKEN` (present)

## 28. AI_SERVICE_BASE_URL

- Owner: platform-owners
- Group: owner-services
- Requirement: ai service
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `AI_SERVICE_BASE_URL` (present)
  - `LUMIRA_AI_SERVICE_BASE_URL` (present)
  - `LUMIRA_AI_BASE_URL` (present)

## 29. AUTH_SERVICE_BASE_URL

- Owner: platform-owners
- Group: owner-services
- Requirement: auth service
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `AUTH_SERVICE_BASE_URL` (present)
  - `LUMIRA_AUTH_SERVICE_BASE_URL` (present)

## 30. FILE_SERVICE_BASE_URL

- Owner: platform-owners
- Group: owner-services
- Requirement: file service
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `FILE_SERVICE_BASE_URL` (present)
  - `LUMIRA_FILE_SERVICE_BASE_URL` (present)

## 31. JOB_EXECUTOR_BASE_URL

- Owner: platform-owners
- Group: owner-services
- Requirement: job executor
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `JOB_EXECUTOR_BASE_URL` (present)
  - `LUMIRA_JOB_EXECUTOR_BASE_URL` (present)

## 32. LOCALIZATION_SERVICE_BASE_URL

- Owner: platform-owners
- Group: owner-services
- Requirement: localization service
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LOCALIZATION_SERVICE_BASE_URL` (present)
  - `LUMIRA_LOCALIZATION_SERVICE_BASE_URL` (present)

## 33. MESSAGE_SERVICE_BASE_URL

- Owner: platform-owners
- Group: owner-services
- Requirement: message service
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `MESSAGE_SERVICE_BASE_URL` (present)
  - `LUMIRA_MESSAGE_SERVICE_BASE_URL` (present)

## 34. PAYMENT_SERVICE_BASE_URL

- Owner: platform-owners
- Group: owner-services
- Requirement: payment service
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `PAYMENT_SERVICE_BASE_URL` (present)
  - `LUMIRA_PAYMENT_SERVICE_BASE_URL` (present)

## 35. PLUGIN_SERVICE_BASE_URL

- Owner: platform-owners
- Group: owner-services
- Requirement: plugin service
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `PLUGIN_SERVICE_BASE_URL` (present)
  - `LUMIRA_PLUGIN_SERVICE_BASE_URL` (present)

## 36. SYSTEM_SERVICE_BASE_URL

- Owner: platform-owners
- Group: owner-services
- Requirement: system service
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `SYSTEM_SERVICE_BASE_URL` (present)
  - `LUMIRA_SYSTEM_SERVICE_BASE_URL` (present)

## 37. LUMIRA_BASE_URL

- Owner: release-infra
- Group: runtime
- Requirement: backend base url
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `LUMIRA_BASE_URL` (present)
  - `DEPLOY_CHECK_BASE_URL` (present)

## 38. CORS_ALLOWED_ORIGIN_PATTERNS

- Owner: release-infra
- Group: runtime
- Requirement: cors origins
- Required: true
- Value class: identifier; secret=false; safeToPreFill=false
- Fill guidance: Use the production value from the owning context.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `CORS_ALLOWED_ORIGIN_PATTERNS` (present)
  - `SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS` (present)

## 39. DB_PASSWORD

- Owner: release-infra
- Group: runtime
- Requirement: database password
- Required: true
- Value class: secret; secret=true; safeToPreFill=false
- Fill guidance: Provide via approved secret manager or secure release channel; never commit.
- Validation: https=false, nonLocal=false, minLength=16, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `DB_PASSWORD` (present)
  - `SPRING_DATASOURCE_PASSWORD` (present)
  - `MYSQL_PASSWORD` (present)

## 40. DB_URL

- Owner: release-infra
- Group: runtime
- Requirement: database url
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent endpoint or DSN; localhost and placeholders are rejected when nonLocal=true.
- Validation: https=false, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `DB_URL` (present)
  - `SPRING_DATASOURCE_URL` (present)

## 41. DB_USERNAME

- Owner: release-infra
- Group: runtime
- Requirement: database username
- Required: true
- Value class: identifier; secret=false; safeToPreFill=false
- Fill guidance: Use the production value from the owning context.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `DB_USERNAME` (present)
  - `SPRING_DATASOURCE_USERNAME` (present)
  - `MYSQL_USER` (present)

## 42. FIELD_SECRET

- Owner: release-infra
- Group: runtime
- Requirement: field secret
- Required: true
- Value class: secret; secret=true; safeToPreFill=false
- Fill guidance: Provide via approved secret manager or secure release channel; never commit.
- Validation: https=false, nonLocal=false, minLength=32, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `FIELD_SECRET` (present)
  - `SAAS_SECURITY_FIELD_SECRET` (present)

## 43. JWT_SECRET

- Owner: release-infra
- Group: runtime
- Requirement: jwt secret
- Required: true
- Value class: secret; secret=true; safeToPreFill=false
- Fill guidance: Provide via approved secret manager or secure release channel; never commit.
- Validation: https=false, nonLocal=false, minLength=32, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `JWT_SECRET` (present)
  - `SAAS_SECURITY_JWT_SECRET` (present)

## 44. PLAYWRIGHT_BASE_URL

- Owner: release-infra
- Group: runtime
- Requirement: lumira-ui base url
- Required: true
- Value class: url; secret=false; safeToPreFill=false
- Fill guidance: Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected.
- Validation: https=true, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `PLAYWRIGHT_BASE_URL` (present)
  - `FRONTEND_BASE_URL` (present)

## 45. REDIS_HOST

- Owner: release-infra
- Group: runtime
- Requirement: redis host
- Required: true
- Value class: identifier; secret=false; safeToPreFill=false
- Fill guidance: Use the production value from the owning context.
- Validation: https=false, nonLocal=true, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `REDIS_HOST` (present)
  - `SPRING_DATA_REDIS_HOST` (present)

## 46. REDIS_PASSWORD

- Owner: release-infra
- Group: runtime
- Requirement: redis password
- Required: false
- Value class: secret; secret=true; safeToPreFill=false
- Fill guidance: Provide via approved secret manager or secure release channel; never commit.
- Validation: https=false, nonLocal=false, minLength=16, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `REDIS_PASSWORD` (present)
  - `SPRING_DATA_REDIS_PASSWORD` (present)

## 47. REDIS_PORT

- Owner: release-infra
- Group: runtime
- Requirement: redis port
- Required: true
- Value class: port; secret=false; safeToPreFill=false
- Fill guidance: Use the production service TCP port.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=none
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `REDIS_PORT` (present)
  - `SPRING_DATA_REDIS_PORT` (present)

## 48. TRUST_FORWARDED_HEADERS

- Owner: release-infra
- Group: runtime
- Requirement: trusted proxy mode
- Required: true
- Value class: toggle; secret=false; safeToPreFill=true
- Fill guidance: Use one of: true.
- Validation: https=false, nonLocal=false, minLength=none, expectedValues=true
- Alias sync: `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-alias-sync.mjs`
- Aliases:
  - `TRUST_FORWARDED_HEADERS` (present)
  - `SAAS_WEB_TRUST_FORWARDED_HEADERS` (present)

