# DDD Staging Readiness Rollup

Status: PASS
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Blocked: 0/6

| Gate | Track | Owner | Status | First blocker | Blocking inputs | Next command |
| --- | --- | --- | --- | --- | --- | --- |
| release-env | p0-release-env | release-infra | PASS | none | none | `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs` |
| docker-images | p0-images | release-infra | PASS | none | `DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE`, `DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE`, `DDD_DOCKER_EXISTING_FRONTEND_IMAGE` | `node scripts/ddd-docker-build-evidence.mjs --check` |
| runtime-business | p1-runtime-business | release-infra, frontend, ai, file-owner, job-owner, payment-owner | PASS | none | none | `node scripts/ddd-staging-runtime-check.mjs` |
| rollback | p1-rollback | bounded-context owners | PASS | none | none | `node scripts/ddd-staging-data-safety-check.mjs` |
| migration | p2-database-performance | database | PASS | none | none | `node scripts/ddd-staging-data-safety-check.mjs` |
| explain | p2-database-performance | database | PASS | none | none | `node scripts/ddd-staging-data-safety-check.mjs` |

Next: `node scripts/ddd-staging-execution-checklist.mjs --commands`
