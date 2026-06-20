# DDD Release Evidence Dispatch Plan

Status: BLOCKED
Workflow: `.github/workflows/ddd-release-evidence.yml`
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Blocked inputs: 6

| Input | Required | Status | Suggested value | Source |
| --- | --- | --- | --- | --- |
| `mode` | yes | READY | `plan` | `operator` |
| `strict` | yes | READY | `true` | `operator` |
| `github_environment` | yes | READY | `staging` | `operator` |
| `evidence_environment` | yes | READY | `staging` | `operator` |
| `backend_base_url` | yes | BLOCKED | `__REQUIRED_HTTPS__` | `LUMIRA_BASE_URL` |
| `frontend_base_url` | yes | BLOCKED | `__REQUIRED_HTTPS__` | `PLAYWRIGHT_BASE_URL` |
| `ai_base_url` | no | READY | `` | `LUMIRA_AI_BASE_URL` |
| `max_artifact_age_hours` | yes | READY | `24` | `operator` |
| `expect_ai_remote` | yes | BLOCKED | `true` | `DDD_AI_EXPECT_PROVIDER_REMOTE, DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE` |
| `expect_frontend_deployed` | yes | BLOCKED | `true` | `DDD_FRONTEND_EXPECT_DEPLOYED` |
| `promote_authenticated_baseline` | yes | READY | `false` | `operator` |
| `baseline_accepted_by` | no | READY | `` | `operator` |
| `lane_completion_receipt_file` | no | BLOCKED | `` | `DDD_LANE_COMPLETION_RECEIPT_FILE` |
| `lane_completion_receipt_base64` | no | BLOCKED | `__REQUIRED_AFTER_COVERAGE_5_OF_5__` | `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>` |

## Required Before Run

  - `node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance`
  - `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>`
  - `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>`
  - `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>`

Next: `node bin/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown`
