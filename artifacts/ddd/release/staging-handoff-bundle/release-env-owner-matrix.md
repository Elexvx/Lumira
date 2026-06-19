# DDD Release Env Owner Matrix

Status: BLOCKED
Target: `tmp/ddd-dispatch-check-env-init.env`
Owners: 5
Blockers: 34
Placeholders: 34
Secret keys: 10

## Owners

| Owner | Blockers | Placeholders | Secret keys | Keys | First key | Env template |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| platform-events | 9 | 9 | 3 | 10 | `SAAS_EVENT_REDIS_STREAM_KEY` | `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=platform-events` |
| platform-owners | 9 | 9 | 0 | 9 | `AI_SERVICE_BASE_URL` | `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=platform-owners` |
| release-infra | 9 | 9 | 4 | 12 | `LUMIRA_BASE_URL` | `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=release-infra` |
| ai-owner | 6 | 6 | 2 | 12 | `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL` | `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=ai-owner` |
| payment-owner | 1 | 1 | 1 | 2 | `PAYMENT_PUBLIC_BASE_URL` | `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=payment-owner` |

## Commands

- Owner packets: `node scripts/ddd-staging-execution-checklist.mjs --owner-packets`
- All inputs template: `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template`
- Validate: `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
- Validate: `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs`
- Validate: `node scripts/ddd-release-readiness-summary.mjs`
- Validate: `node scripts/ddd-staging-execution-checklist.mjs --rollup`
- Validate: `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce`

Next: `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=release-infra`
