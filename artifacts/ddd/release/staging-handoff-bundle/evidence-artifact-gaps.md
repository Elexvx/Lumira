# DDD Evidence Artifact Gap Report

Status: BLOCKED
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Artifacts: 14/15 present; missing=1

## Missing Artifacts

| Artifact | Gates | Owners | Dispatch owners | Acceptance commands |
| --- | --- | --- | --- | --- |
| `artifacts/ddd/lumira-ui/frontend-smoke.json` | runtime-business | release-infra, lumira-ui, ai, file-owner, job-owner, payment-owner | ai-owner | `node bin/ddd-staging-runtime-check.mjs` |

## Present Artifacts

- `artifacts/ddd/ai/ai-runtime-drill.json`: gates=runtime-business; matches=1
- `artifacts/ddd/build/docker-image-evidence.json`: gates=docker-images; matches=1
- `artifacts/ddd/config/release-config-evidence.json`: gates=release-env; matches=1
- `artifacts/ddd/file/file-processing-e2e.json`: gates=runtime-business; matches=1
- `artifacts/ddd/jobs/job-e2e-smoke.json`: gates=runtime-business; matches=1
- `artifacts/ddd/migration/migration-evidence.json`: gates=explain, migration; matches=1
- `artifacts/ddd/payment/payment-webhook-e2e.json`: gates=runtime-business; matches=1
- `artifacts/ddd/performance/authenticated-runtime-actual.json`: gates=runtime-business; matches=1
- `artifacts/ddd/readiness/summary.json`: gates=runtime-business; matches=1
- `artifacts/ddd/release/explain-gate-report.json`: gates=explain, migration; matches=1
- `artifacts/ddd/release/readiness-summary.json`: gates=release-env; matches=1
- `artifacts/ddd/release/release-env-lint.json`: gates=release-env; matches=1
- `artifacts/ddd/rollback/rollback-drill.json`: gates=rollback; matches=1
- `tmp/ddd-explain/*.json`: gates=explain, migration; matches=8

Next: `node bin/ddd-staging-runtime-check.mjs`
