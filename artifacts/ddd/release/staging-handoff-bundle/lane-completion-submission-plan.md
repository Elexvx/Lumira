# DDD Lane Completion Submission Plan

Status: BLOCKED
Redacted: true
Lanes: 0
Current coverage: 0/0
Workflow: `.github/workflows/ddd-release-evidence.yml`
Workflow file input: `lane_completion_receipt_file`
Workflow base64 input: `lane_completion_receipt_base64`
Decoded path: `artifacts/ddd/release/lane-completion-receipt.submitted.json`

## Lanes

| Key | Acceptance commands | Expected artifacts | Missing artifacts |
| --- | --- | --- | --- |

## Commands

- `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>`
- `node bin/ddd-lane-completion-receipt-autofill.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>`
- `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template`
- `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>`
- `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage --lane-completion-receipt-file=<receipt-file>`
- `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>`
- `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>`
- `node bin/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>`
- `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>`

## Pass Criteria

- receipt.redacted must be true
- receipt.status must be PASS
- every lane receipt must be PASS
- every PASS lane must include providedArtifacts and empty missingArtifacts
- every PASS lane must include completedAt and completedBy
- every owner:lane key must be unique
- coverage must show Coverage: 5/5
- base64 generation must succeed before using lane_completion_receipt_base64
- final review must pass with the submitted receipt file

## Current Missing Lanes

- none

Next: `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>`
