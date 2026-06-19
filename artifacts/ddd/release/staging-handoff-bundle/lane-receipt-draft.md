# DDD Lane Receipt Draft

Status: BLOCKED
Redacted: true
Lane receipts: 5

| Owner | Lane | Status | Provided artifacts | Missing artifacts |
| --- | --- | --- | ---: | ---: |
| release-infra | `p0-release-env` | PASS | 3 | 0 |
| release-infra | `p0-docker-images` | PASS | 1 | 0 |
| release-infra | `p1-runtime-business` | PASS | 7 | 0 |
| platform-owners | `p1-p2-data-safety` | PASS | 4 | 0 |
| release-infra | `final-review` | BLOCKED | 16 | 0 |

## Pass Criteria

- keep redacted=true
- set receipt status to PASS only after every lane status is PASS
- clear missingArtifacts before marking a lane PASS
- keep providedArtifacts non-empty for every PASS lane
- set completedAt and completedBy for every PASS lane
- run contract and coverage before base64 submission

## Validation Commands

- `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>`
- `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>`
- `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>`
- `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>`

Next: `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>`
