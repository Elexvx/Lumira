# DDD P1 Runtime Business Plan

Status: BLOCKED
Backend URL: missing
Frontend URL: missing
Current blocker: LUMIRA_BASE_URL is required

## Required Inputs

- URLs: `LUMIRA_BASE_URL`, `PLAYWRIGHT_BASE_URL`
- Deployment evidence: `DDD_DEPLOYMENT_EVIDENCE`, `DDD_FRONTEND_DEPLOYMENT_EVIDENCE`, `DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE`, `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE`, `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
- Expectation flags: `DDD_FRONTEND_EXPECT_DEPLOYED=true`, `DDD_AI_EXPECT_PROVIDER_REMOTE=true`, `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true`

## Smoke Steps

| Step | Owner | Command | Artifact |
| --- | --- | --- | --- |
| runtime-readiness | release-infra | `node scripts/ddd-runtime-readiness-smoke.mjs` | `artifacts/ddd/readiness/summary.json` |
| authenticated-performance | release-performance | `DDD_AUTH_PERF_STRICT=true node scripts/ddd-authenticated-performance-smoke.mjs` | `artifacts/ddd/performance/authenticated-runtime-actual.json` |
| ai-runtime | ai | `node scripts/ddd-ai-runtime-drill.mjs` | `artifacts/ddd/ai/ai-runtime-drill.json` |
| frontend-smoke | frontend | `node scripts/ddd-frontend-playwright-smoke.mjs && node scripts/ddd-frontend-smoke-evidence.mjs` | `artifacts/ddd/frontend/frontend-smoke.json` |
| file-processing-e2e | file-owner | `node scripts/ddd-file-processing-e2e-smoke.mjs` | `artifacts/ddd/file/file-processing-e2e.json` |
| job-e2e | job-owner | `node scripts/ddd-job-e2e-smoke.mjs` | `artifacts/ddd/jobs/job-e2e-smoke.json` |
| payment-webhook-e2e | payment-owner | `node scripts/ddd-payment-webhook-e2e-smoke.mjs` | `artifacts/ddd/payment/payment-webhook-e2e.json` |

## Validate

- `node scripts/ddd-staging-runtime-check.mjs`
- `node scripts/ddd-release-readiness-summary.mjs`
- `node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance`
- `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce`

## Safety

- Use HTTPS staging or production-equivalent URLs; localhost evidence is not accepted for strict release.
- Attach deployment evidence for backend, frontend, AI runtime, authenticated performance, and business E2E flows.
- Regenerate release readiness after every smoke artifact is refreshed.

Next: `node scripts/ddd-staging-runtime-check.mjs`
