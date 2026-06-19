# DDD Lane Completion Submission Check

Status: BLOCKED
Receipt file: not provided
Redacted: false
Contract: MISSING
Receipt status: not provided
Coverage: 0/5
Base64 ready: false
Dispatch ready: false
Preferred workflow input: `lane_completion_receipt_base64`
Decoded workflow path: `artifacts/ddd/release/lane-completion-receipt.submitted.json`

## Blocking Issues

- lane completion receipt file not provided
- DDD_LANE_COMPLETION_RECEIPT_FILE or --lane-completion-receipt-file is required
- missing lanes=release-infra:p0-release-env, release-infra:p0-docker-images, release-infra:p1-p2-data-safety, release-infra:final-review, ai-owner:p1-runtime-business

## Missing Lanes

- `release-infra:p0-release-env`
- `release-infra:p0-docker-images`
- `release-infra:p1-p2-data-safety`
- `release-infra:final-review`
- `ai-owner:p1-runtime-business`

## Submission Commands

- Check coverage: `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>`
- Generate base64: `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>`
- Dispatch: `gh workflow run ddd-release-evidence.yml -f mode=run -f lane_completion_receipt_base64=<base64-value>`

Next: `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>`
