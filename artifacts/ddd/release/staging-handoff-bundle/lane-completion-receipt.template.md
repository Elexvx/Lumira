# DDD Lane Completion Receipt

Status: BLOCKED
Redacted: true
Lane receipts: 5

| Lane | Owner | Status | Provided artifacts | Missing artifacts | Completed at | Completed by | Acceptance commands |
| --- | --- | --- | ---: | ---: | --- | --- | --- |
| `p0-release-env` | release-infra | BLOCKED | 0 | 0 | required when PASS | required when PASS | `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs` |
| `p0-docker-images` | release-infra | BLOCKED | 0 | 0 | required when PASS | required when PASS | `node scripts/ddd-docker-build-evidence.mjs --check` |
| `p1-p2-data-safety` | release-infra | BLOCKED | 0 | 0 | required when PASS | required when PASS | `node scripts/ddd-staging-data-safety-check.mjs` |
| `final-review` | release-infra | BLOCKED | 0 | 0 | required when PASS | required when PASS | `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`<br>`node scripts/ddd-docker-build-evidence.mjs --check`<br>`node scripts/ddd-staging-runtime-check.mjs`<br>`node scripts/ddd-staging-data-safety-check.mjs` |
| `p1-runtime-business` | ai-owner | BLOCKED | 0 | 0 | required when PASS | required when PASS | `node scripts/ddd-staging-runtime-check.mjs` |

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
| `release-infra:p0-release-env` | laneReceipts[0] | `status`, `providedArtifacts`, `missingArtifacts`, `completedAt`, `completedBy` | acceptance commands have not passed |
| `release-infra:p0-docker-images` | laneReceipts[1] | `status`, `providedArtifacts`, `missingArtifacts`, `completedAt`, `completedBy` | acceptance commands have not passed |
| `release-infra:p1-p2-data-safety` | laneReceipts[2] | `status`, `providedArtifacts`, `missingArtifacts`, `completedAt`, `completedBy` | acceptance commands have not passed |
| `release-infra:final-review` | laneReceipts[3] | `status`, `providedArtifacts`, `missingArtifacts`, `completedAt`, `completedBy` | acceptance commands have not passed |
| `ai-owner:p1-runtime-business` | laneReceipts[4] | `status`, `providedArtifacts`, `missingArtifacts`, `completedAt`, `completedBy` | acceptance commands have not passed |

## Lane Details

### release-infra:p0-release-env

Acceptance commands: `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
Expected artifacts: `artifacts/ddd/release/release-env-lint.json`, `artifacts/ddd/config/release-config-evidence.json`, `artifacts/ddd/release/readiness-summary.json`
Currently missing artifacts: none

### release-infra:p0-docker-images

Acceptance commands: `node scripts/ddd-docker-build-evidence.mjs --check`
Expected artifacts: `artifacts/ddd/build/docker-image-evidence.json`
Currently missing artifacts: none

### release-infra:p1-p2-data-safety

Acceptance commands: `node scripts/ddd-staging-data-safety-check.mjs`
Expected artifacts: `artifacts/ddd/rollback/rollback-drill.json`, `artifacts/ddd/migration/migration-evidence.json`, `tmp/ddd-explain/*.json`, `artifacts/ddd/release/explain-gate-report.json`
Currently missing artifacts: none

### release-infra:final-review

Acceptance commands: `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`, `node scripts/ddd-docker-build-evidence.mjs --check`, `node scripts/ddd-staging-runtime-check.mjs`, `node scripts/ddd-staging-data-safety-check.mjs`
Expected artifacts: `artifacts/ddd/release/release-env-lint.json`, `artifacts/ddd/config/release-config-evidence.json`, `artifacts/ddd/release/readiness-summary.json`, `artifacts/ddd/build/docker-image-evidence.json`, `artifacts/ddd/readiness/summary.json`, `artifacts/ddd/performance/authenticated-runtime-actual.json`, `artifacts/ddd/ai/ai-runtime-drill.json`, `artifacts/ddd/frontend/frontend-smoke.json`, `artifacts/ddd/file/file-processing-e2e.json`, `artifacts/ddd/jobs/job-e2e-smoke.json`, `artifacts/ddd/payment/payment-webhook-e2e.json`, `artifacts/ddd/rollback/rollback-drill.json`, `artifacts/ddd/migration/migration-evidence.json`, `tmp/ddd-explain/*.json`, `artifacts/ddd/release/explain-gate-report.json`
Currently missing artifacts: none

### ai-owner:p1-runtime-business

Acceptance commands: `node scripts/ddd-staging-runtime-check.mjs`
Expected artifacts: `artifacts/ddd/readiness/summary.json`, `artifacts/ddd/performance/authenticated-runtime-actual.json`, `artifacts/ddd/ai/ai-runtime-drill.json`, `artifacts/ddd/frontend/frontend-smoke.json`, `artifacts/ddd/file/file-processing-e2e.json`, `artifacts/ddd/jobs/job-e2e-smoke.json`, `artifacts/ddd/payment/payment-webhook-e2e.json`
Currently missing artifacts: none


## Pass Criteria

- redacted must be true
- receipt must not include sensitive values or URLs
- each completed lane must set status PASS and include providedArtifacts
- each PASS lane must include completedAt and completedBy
- each owner:lane key must appear at most once
- run each lane acceptanceCommands before marking PASS
- run node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>

## Submission Flow

- `node scripts/ddd-lane-completion-receipt-autofill.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>`
- `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>`
- `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>`
- `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>`
- `node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>`
- `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>`
