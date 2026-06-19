# DDD Next Action Env Receipt

Status: BLOCKED
Redacted: true
Env file: not provided
Env file SHA-256: missing
Keys present: 0
Required selected keys: 0
Issues: 16

| Lane | Owner | Status | Inputs | Selected keys | First issue |
| --- | --- | --- | ---: | --- | --- |
| p0-release-env | release-infra | BLOCKED | 0/1 | none | DDD_RELEASE_ENV_FILE is required |
| p1-runtime-business | release-infra | BLOCKED | 0/5 | none | LUMIRA_BASE_URL is required |
| p1-p2-data-safety | bounded-context owners | BLOCKED | 0/4 | none | DDD_ROLLBACK_DRILL_FILE is required |

## Pass Criteria

- status must be PASS
- duplicateKeys must be empty
- all laneReceipts must have status PASS
- envFileSha256 must be present for the validated file
- receipt must not include env values

Next: `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-template`
