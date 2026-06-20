# DDD Runtime Smoke Owner Plan

Status: BLOCKED
Backend URL: missing
Frontend URL: missing
Owners: `release-infra`, `ai`, `release-performance`, `lumira-ui`, `file-owner`, `job-owner`, `payment-owner`
Next phase: runtime-deployment-evidence

## Phases

| Phase | Owner | Depends On | Required Inputs | Commands | Artifacts |
| --- | --- | --- | --- | --- | --- |
| runtime-deployment-evidence | release-infra | none | `LUMIRA_BASE_URL`, `PLAYWRIGHT_BASE_URL`, `DDD_DEPLOYMENT_EVIDENCE`, `DDD_FRONTEND_DEPLOYMENT_EVIDENCE`, `DDD_FRONTEND_EXPECT_DEPLOYED=true` | `node bin/ddd-staging-runtime-check.mjs`<br>`node bin/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=release-infra` | `artifacts/ddd/readiness/summary.json` |
| ai-runtime-evidence | ai | `runtime-deployment-evidence` | `DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE`, `DDD_AI_EXPECT_PROVIDER_REMOTE=true`, `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true` | `node bin/ddd-ai-runtime-drill.mjs` | `artifacts/ddd/ai/ai-runtime-drill.json` |
| auth-performance-evidence | release-performance | `runtime-deployment-evidence` | `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE` | `DDD_AUTH_PERF_STRICT=true node bin/ddd-authenticated-performance-smoke.mjs` | `artifacts/ddd/performance/authenticated-runtime-actual.json` |
| lumira-ui-runtime-smoke | lumira-ui | `runtime-deployment-evidence` | `PLAYWRIGHT_BASE_URL`, `DDD_FRONTEND_DEPLOYMENT_EVIDENCE`, `DDD_FRONTEND_EXPECT_DEPLOYED=true` | `node bin/ddd-frontend-playwright-smoke.mjs && node bin/ddd-frontend-smoke-evidence.mjs` | `artifacts/ddd/lumira-ui/frontend-smoke.json` |
| business-file-smoke | file-owner | `runtime-deployment-evidence` | `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` | `node bin/ddd-file-processing-e2e-smoke.mjs` | `artifacts/ddd/file/file-processing-e2e.json` |
| business-job-smoke | job-owner | `runtime-deployment-evidence` | `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` | `node bin/ddd-job-e2e-smoke.mjs` | `artifacts/ddd/jobs/job-e2e-smoke.json` |
| business-payment-smoke | payment-owner | `runtime-deployment-evidence` | `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` | `node bin/ddd-payment-webhook-e2e-smoke.mjs` | `artifacts/ddd/payment/payment-webhook-e2e.json` |
| runtime-acceptance | release-infra | `ai-runtime-evidence`, `auth-performance-evidence`, `lumira-ui-runtime-smoke`, `business-file-smoke`, `business-job-smoke`, `business-payment-smoke` | none | `node bin/ddd-staging-runtime-check.mjs`<br>`node bin/ddd-release-readiness-summary.mjs`<br>`node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance`<br>`node bin/ddd-staging-execution-checklist.mjs --final-review-enforce` | `artifacts/ddd/readiness/summary.json`<br>`artifacts/ddd/performance/authenticated-runtime-actual.json`<br>`artifacts/ddd/ai/ai-runtime-drill.json`<br>`artifacts/ddd/lumira-ui/frontend-smoke.json`<br>`artifacts/ddd/file/file-processing-e2e.json`<br>`artifacts/ddd/jobs/job-e2e-smoke.json`<br>`artifacts/ddd/payment/payment-webhook-e2e.json` |

## Parallel After Deployment

- `ai-runtime-evidence`
- `auth-performance-evidence`
- `lumira-ui-runtime-smoke`
- `business-file-smoke`
- `business-job-smoke`
- `business-payment-smoke`

## Validate

- `node bin/ddd-staging-runtime-check.mjs`
- `node bin/ddd-release-readiness-summary.mjs`
- `node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance`
- `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce`

## Safety

- Run smoke commands only against HTTPS staging URLs with production-equivalent deployment evidence.
- Do not paste secrets into Markdown outputs; use secure env files or CI secret stores for populated values.
- Regenerate readiness after every owner smoke artifact is refreshed, then run final review enforcement.

Next: `node bin/ddd-staging-runtime-check.mjs`
