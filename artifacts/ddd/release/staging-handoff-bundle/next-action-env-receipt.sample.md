# DDD Next Action Env Receipt

Status: BLOCKED
Redacted: true
Env file: not provided
Env file SHA-256: missing
Keys present: 0
Required selected keys: 0
Issues: 1

| Lane | Owner | Status | Inputs | Selected keys | First issue |
| --- | --- | --- | ---: | --- | --- |

## Pass Criteria

- status must be PASS
- duplicateKeys must be empty
- all laneReceipts must have status PASS
- envFileSha256 must be present for the validated file
- receipt must not include env values

Next: `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-template`
