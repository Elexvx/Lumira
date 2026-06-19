# DDD Staging Closure Plan

Status: BLOCKED
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Accepted gates: 1/6
ETA: fast path 0.5-1.5d with staging access and owner evidence ready; 1-3d if deployment, Docker, database, or approval evidence must be produced

| Phase | Gate | Owner | ETA | Current blocker | Next command |
| --- | --- | --- | --- | --- | --- |
| P0 | `release-env` | release-infra | 2-4h if owner inputs are available; 0.5-1d if secrets must be collected | release env file is not cutover-safe; blockers=34 | `node scripts/ddd-release-env-init.mjs --check` |
| P1 | `runtime-business` | release-infra, frontend, ai, file-owner, job-owner, payment-owner | 2-4h with staging URLs and deployment evidence; 0.5-1d if deployment must be provisioned | LUMIRA_BASE_URL is required | `node scripts/ddd-staging-runtime-check.mjs` |
| P1 | `rollback` | bounded-context owners | 2-4h with owner drill evidence; 0.5-1d if deferrals need review | rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE | `node scripts/ddd-staging-data-safety-check.mjs` |
| P2 | `explain` | database | 2-6h with database access; 0.5-1d if fresh/upgrade drills must be scheduled | DDD_EXPLAIN_DATABASE is required | `node scripts/ddd-staging-data-safety-check.mjs` |
| P2 | `migration` | database | 2-6h with database access; 0.5-1d if fresh/upgrade drills must be scheduled | DDD_MIGRATION_FRESH_DB_VALIDATED must be true | `node scripts/ddd-staging-data-safety-check.mjs` |

## Critical Path

- P0 release env and image evidence must close before expensive staging validation is trusted.
- P1 runtime and rollback checks can run in parallel after P0 inputs are available.
- P2 migration and EXPLAIN checks can run in parallel with P1 when database access is ready.
- Final cutover requires --final-review-enforce and release-final-go-no-go-gate.sh to pass.

## Top Blocking Inputs

- `DDD_EVIDENCE_ENVIRONMENT`: gates=3; owners=bounded-context owners, database; next=`node scripts/ddd-staging-data-safety-check.mjs`
- `DDD_EVIDENCE_OPERATOR`: gates=3; owners=bounded-context owners, database; next=`node scripts/ddd-staging-data-safety-check.mjs`
- `DDD_RELEASE_ENVIRONMENT`: gates=3; owners=bounded-context owners, database; next=`node scripts/ddd-staging-data-safety-check.mjs`
- `GITHUB_ACTOR`: gates=3; owners=bounded-context owners, database; next=`node scripts/ddd-staging-data-safety-check.mjs`
- `DDD_RELEASE_CANDIDATE`: gates=2; owners=bounded-context owners, database; next=`node scripts/ddd-staging-data-safety-check.mjs`
- `GITHUB_SHA`: gates=2; owners=bounded-context owners, database; next=`node scripts/ddd-staging-data-safety-check.mjs`
- `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE`: gates=1; owners=ai, file-owner, frontend, job-owner, payment-owner, release-infra; next=`node scripts/ddd-staging-runtime-check.mjs`
- `DDD_AI_EXPECT_PROVIDER_REMOTE`: gates=1; owners=ai, file-owner, frontend, job-owner, payment-owner, release-infra; next=`node scripts/ddd-staging-runtime-check.mjs`
- `DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE`: gates=1; owners=ai, file-owner, frontend, job-owner, payment-owner, release-infra; next=`node scripts/ddd-staging-runtime-check.mjs`
- `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE`: gates=1; owners=ai, file-owner, frontend, job-owner, payment-owner, release-infra; next=`node scripts/ddd-staging-runtime-check.mjs`
- `DDD_DEPLOYMENT_EVIDENCE`: gates=1; owners=ai, file-owner, frontend, job-owner, payment-owner, release-infra; next=`node scripts/ddd-staging-runtime-check.mjs`
- `DDD_EXPLAIN_DATABASE`: gates=1; owners=database; next=`node scripts/ddd-staging-data-safety-check.mjs`

## Verification

- `node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance`
- `node scripts/ddd-staging-execution-checklist.mjs --rollup-enforce`
- `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce`
- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

Next: `node scripts/ddd-release-env-init.mjs --check`
