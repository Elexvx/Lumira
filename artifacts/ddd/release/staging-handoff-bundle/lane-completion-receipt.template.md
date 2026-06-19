# DDD Lane Completion Receipt

Status: BLOCKED
Redacted: true
Lane receipts: 0

| Lane | Owner | Status | Provided artifacts | Missing artifacts | Completed at | Completed by | Acceptance commands |
| --- | --- | --- | ---: | ---: | --- | --- | --- |

## Fill Rules

- Keep `redacted=true` and do not paste secrets, tokens, passwords, or private URLs into the receipt.
- Leave a lane `BLOCKED` until its acceptance commands pass.
- To mark a lane `PASS`, copy its expected evidence paths into `providedArtifacts`, clear `missingArtifacts`, and set `completedAt` plus `completedBy`.
- A full release receipt must cover every owner:lane row exactly once and pass both contract and coverage commands.

## Edit Checklist

- Edit only the redacted receipt JSON created by `--lane-completion-receipt-init` or `--lane-completion-receipt-template`.
- Keep top-level `redacted` set to `true` for the whole receipt.
- Keep top-level `status` as `BLOCKED` until every lane row is ready for `PASS`.
- For each lane row, update `status`, `providedArtifacts`, `missingArtifacts`, `completedAt`, and `completedBy`; keep `owner` and `lane` unchanged.
- Run the submission check before generating base64 for workflow dispatch.

| Lane key | JSON row | Fields to update before PASS | Keep BLOCKED while |
| --- | ---: | --- | --- |

## Lane Details


## Pass Criteria

- redacted must be true
- receipt must not include sensitive values or URLs
- each completed lane must set status PASS and include providedArtifacts
- each PASS lane must include completedAt and completedBy
- each owner:lane key must appear at most once
- run each lane acceptanceCommands before marking PASS
- run node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>

## Submission Flow

- `node bin/ddd-lane-completion-receipt-autofill.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>`
- `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>`
- `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>`
- `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>`
- `node bin/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>`
- `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>`
