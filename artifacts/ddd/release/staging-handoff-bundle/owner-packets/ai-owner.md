# DDD Staging Owner Packet: ai-owner

Generated at: 2026-06-19T14:23:18.022Z
Owner: ai-owner
Blockers: 0
Placeholders: 0
Secret keys: 2
Handoff: undefined

## Required Keys

- LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL
- LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL
- LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED
- LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL
- LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED
- LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL
- LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN
- LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED
- LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL
- LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY
- LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL
- LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED

## Input Reasons

- none

## Post-Fill Validation

  - `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>`
  - `node scripts/ddd-lane-completion-receipt-autofill.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template-markdown`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>`

## Queue Lanes

| Order | Lane | Status | Missing artifacts | Command | Source |
| ---: | --- | --- | ---: | --- | --- |
| 3 | `p1-runtime-business` | PASS | 0 | `node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown` | `runtime-business-submission-plan.json` |

## Submission Routes

### p1-runtime-business

Source plan: `runtime-business-submission-plan.json`
Next command: `node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown`
Acceptance commands: `node scripts/ddd-staging-runtime-check.mjs`
Expected artifacts: `artifacts/ddd/readiness/summary.json`, `artifacts/ddd/performance/authenticated-runtime-actual.json`, `artifacts/ddd/ai/ai-runtime-drill.json`, `artifacts/ddd/frontend/frontend-smoke.json`, `artifacts/ddd/file/file-processing-e2e.json`, `artifacts/ddd/jobs/job-e2e-smoke.json`, `artifacts/ddd/payment/payment-webhook-e2e.json`
Currently missing artifacts: none

## Current Blocking Inputs

- none

## Staging Evidence Gaps

### p1-runtime-business: P1 runtime and business acceptance

Reason: local-only runtime evidence must be replaced by HTTPS staging evidence
Next command: `node scripts/ddd-staging-runtime-check.mjs`
Artifacts: artifacts/ddd/readiness/summary.json, artifacts/ddd/performance/authenticated-runtime-actual.json, artifacts/ddd/ai/ai-runtime-drill.json, artifacts/ddd/frontend/frontend-smoke.json, artifacts/ddd/file/file-processing-e2e.json, artifacts/ddd/jobs/job-e2e-smoke.json, artifacts/ddd/payment/payment-webhook-e2e.json
Env keys: LUMIRA_BASE_URL, PLAYWRIGHT_BASE_URL, DDD_FRONTEND_EXPECT_DEPLOYED, DDD_AI_EXPECT_PROVIDER_REMOTE, DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE

## Missing Evidence Artifacts

- none

## Owner Completion Receipt

- Fill `lane-completion-receipt.template.json` with redacted PASS/BLOCKED results for this owner's queue lanes.
- A lane is complete only after its acceptance commands pass and expected artifacts are listed in providedArtifacts.
- Submit the redacted receipt file to release-infra, then re-run final review with that receipt.
- Edit rule: update only this owner's laneReceipts entries, then leave unrelated owner/lane pairs unchanged
- Lane keys: `ai-owner:p1-runtime-business`

Commands:
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>`
  - `node scripts/ddd-lane-completion-receipt-autofill.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template-markdown`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>`
  - `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>`

## Safety

- Do not commit populated secrets.
- Use the secure release env file referenced by DDD_RELEASE_ENV_FILE.
- Re-run the staging checklist after owner inputs are merged.

