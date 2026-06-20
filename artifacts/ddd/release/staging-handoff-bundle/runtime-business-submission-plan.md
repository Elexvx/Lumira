# DDD Runtime Business Submission Plan

Status: BLOCKED
Owner: release-infra
Gate: runtime-business
Backend URL: missing
Frontend URL: missing

## Deployment Submission

Owner: release-infra
Status: BLOCKED
Blocker: LUMIRA_BASE_URL is required

Required inputs:

- `LUMIRA_BASE_URL`
- `PLAYWRIGHT_BASE_URL`
- `DDD_DEPLOYMENT_EVIDENCE`
- `DDD_FRONTEND_DEPLOYMENT_EVIDENCE`
- `DDD_FRONTEND_EXPECT_DEPLOYED=true`

Commands:

- `node bin/ddd-staging-runtime-check.mjs`
- `node bin/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=release-infra`

Artifacts:

- `artifacts/ddd/readiness/summary.json`

## Owner Submissions

| Phase | Owner | Depends On | Required Inputs | Commands | Artifacts |
| --- | --- | --- | --- | --- | --- |
| ai-runtime-evidence | ai | `runtime-deployment-evidence` | `DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE`, `DDD_AI_EXPECT_PROVIDER_REMOTE=true`, `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true` | `node bin/ddd-ai-runtime-drill.mjs` | `artifacts/ddd/ai/ai-runtime-drill.json` |
| auth-performance-evidence | release-performance | `runtime-deployment-evidence` | `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE` | `DDD_AUTH_PERF_STRICT=true node bin/ddd-authenticated-performance-smoke.mjs` | `artifacts/ddd/performance/authenticated-runtime-actual.json` |
| lumira-ui-runtime-smoke | lumira-ui | `runtime-deployment-evidence` | `PLAYWRIGHT_BASE_URL`, `DDD_FRONTEND_DEPLOYMENT_EVIDENCE`, `DDD_FRONTEND_EXPECT_DEPLOYED=true` | `node bin/ddd-frontend-playwright-smoke.mjs && node bin/ddd-frontend-smoke-evidence.mjs` | `artifacts/ddd/lumira-ui/frontend-smoke.json` |
| business-file-smoke | file-owner | `runtime-deployment-evidence` | `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` | `node bin/ddd-file-processing-e2e-smoke.mjs` | `artifacts/ddd/file/file-processing-e2e.json` |
| business-job-smoke | job-owner | `runtime-deployment-evidence` | `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` | `node bin/ddd-job-e2e-smoke.mjs` | `artifacts/ddd/jobs/job-e2e-smoke.json` |
| business-payment-smoke | payment-owner | `runtime-deployment-evidence` | `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` | `node bin/ddd-payment-webhook-e2e-smoke.mjs` | `artifacts/ddd/payment/payment-webhook-e2e.json` |

## Parallel After Deployment

- `ai-runtime-evidence`
- `auth-performance-evidence`
- `lumira-ui-runtime-smoke`
- `business-file-smoke`
- `business-job-smoke`
- `business-payment-smoke`

## Validation Commands

- `node bin/ddd-staging-runtime-check.mjs`
- `node bin/ddd-release-readiness-summary.mjs`
- `node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance`
- `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce`

## Expected Artifacts

- `artifacts/ddd/readiness/summary.json`
- `artifacts/ddd/performance/authenticated-runtime-actual.json`
- `artifacts/ddd/ai/ai-runtime-drill.json`
- `artifacts/ddd/lumira-ui/frontend-smoke.json`
- `artifacts/ddd/file/file-processing-e2e.json`
- `artifacts/ddd/jobs/job-e2e-smoke.json`
- `artifacts/ddd/payment/payment-webhook-e2e.json`

## Lane Receipt Fragment

```json
{
  "owner": "release-infra",
  "lane": "p1-runtime-business",
  "status": "BLOCKED",
  "providedArtifacts": [
    "artifacts/ddd/readiness/summary.json",
    "artifacts/ddd/performance/authenticated-runtime-actual.json",
    "artifacts/ddd/ai/ai-runtime-drill.json",
    "artifacts/ddd/lumira-ui/frontend-smoke.json",
    "artifacts/ddd/file/file-processing-e2e.json",
    "artifacts/ddd/jobs/job-e2e-smoke.json",
    "artifacts/ddd/payment/payment-webhook-e2e.json"
  ],
  "missingArtifacts": [
    "artifacts/ddd/readiness/summary.json",
    "artifacts/ddd/performance/authenticated-runtime-actual.json",
    "artifacts/ddd/ai/ai-runtime-drill.json",
    "artifacts/ddd/lumira-ui/frontend-smoke.json",
    "artifacts/ddd/file/file-processing-e2e.json",
    "artifacts/ddd/jobs/job-e2e-smoke.json",
    "artifacts/ddd/payment/payment-webhook-e2e.json"
  ],
  "completedAt": "<ISO-8601 timestamp after validation commands pass>",
  "completedBy": "<owner or workflow actor>",
  "acceptanceCommands": [
    "node bin/ddd-staging-runtime-check.mjs",
    "node bin/ddd-release-readiness-summary.mjs",
    "node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance",
    "node bin/ddd-staging-execution-checklist.mjs --final-review-enforce"
  ]
}
```

## Pass Criteria

- Deployment evidence phase passes with HTTPS backend and lumira-ui URLs.
- AI, authenticated performance, lumira-ui, file, job, and payment owners refresh their smoke artifacts after deployment evidence lands.
- Runtime staging check and evidence acceptance pass after all owner artifacts are present.
- Final review no longer reports the runtime-business gate as blocked.

Next: `node bin/ddd-staging-runtime-check.mjs`
