# DDD Cutover Rehearsal Plan

Status: BLOCKED
Cutover ready: false
Final recommendation: NO_GO_STRICT
ETA: fast path 0.5-1.5d with staging access and owner evidence ready; 1-3d if deployment, Docker, database, or approval evidence must be produced
Accepted gates: 0/6
Blocked phases: 7/7

## Phases

| Phase | Status | Owner | Depends on | First command | Blocker |
| --- | --- | --- | --- | --- | --- |
| Secure release env initialized and linted | BLOCKED | release-infra | none | `node scripts/ddd-release-env-init.mjs --check` | release env file is not cutover-safe; blockers=34 |
| Deployable Docker images built or inspected | BLOCKED | release-infra | p0-release-env | `node scripts/ddd-docker-build-evidence.mjs --check` | docker CLI is not available: spawnSync docker ENOENT |
| HTTPS staging runtime and business smokes accepted | BLOCKED | release-infra, frontend, ai, file-owner, job-owner, payment-owner | p0-release-env, p0-docker-images | `node scripts/ddd-staging-runtime-check.mjs` | LUMIRA_BASE_URL is required |
| Rollback drill evidence accepted | BLOCKED | bounded-context owners | p1-runtime-business | `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs` | rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE |
| Fresh and upgrade migration drills accepted | BLOCKED | database | p0-release-env | `DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs` | DDD_MIGRATION_FRESH_DB_VALIDATED must be true |
| Production-equivalent EXPLAIN evidence accepted | BLOCKED | database | p0-release-env | `node scripts/ddd-collect-explain.mjs` | DDD_EXPLAIN_DATABASE is required |
| Release-owner final review and strict gate | BLOCKED | release-owner | p1-runtime-business, p1-rollback, p2-migration, p2-explain | `node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance` | accepted=0/6 |

## Validation Commands

  - `node scripts/ddd-production-readiness-preflight.mjs --static-only --no-report`
  - `node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle`
  - `node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle-verify`
  - `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce`

Next: `node scripts/ddd-release-env-init.mjs --check`
