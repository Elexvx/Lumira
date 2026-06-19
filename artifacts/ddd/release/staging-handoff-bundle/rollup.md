# DDD Staging Readiness Rollup

Status: BLOCKED
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Blocked: 6/6

| Gate | Track | Owner | Status | First blocker | Blocking inputs | Next command |
| --- | --- | --- | --- | --- | --- | --- |
| release-env | p0-release-env | release-infra | BLOCKED | release env file is not cutover-safe; blockers=34 | `DDD_RELEASE_ENV_FILE` | `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs` |
| docker-images | p0-images | release-infra | BLOCKED | docker CLI is not available: spawnSync docker ENOENT | `DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE`, `DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE`, `DDD_DOCKER_EXISTING_FRONTEND_IMAGE` | `node scripts/ddd-docker-build-evidence.mjs --check` |
| runtime-business | p1-runtime-business | release-infra, frontend, ai, file-owner, job-owner, payment-owner | BLOCKED | LUMIRA_BASE_URL is required | `LUMIRA_BASE_URL`, `PLAYWRIGHT_BASE_URL`, `DDD_DEPLOYMENT_EVIDENCE`, `DDD_FRONTEND_DEPLOYMENT_EVIDENCE`, `DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE`, `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE`, `DDD_FRONTEND_EXPECT_DEPLOYED`, `DDD_AI_EXPECT_PROVIDER_REMOTE`, `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE` | `node scripts/ddd-staging-runtime-check.mjs` |
| rollback | p1-rollback | bounded-context owners | BLOCKED | rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE | `DDD_ROLLBACK_DRILL_FILE`, `DDD_ROLLBACK_DRILL_DEFERRAL_FILE`, `DDD_ROLLBACK_DRILL_ENVIRONMENT`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, `GITHUB_SHA`, `DDD_EVIDENCE_OPERATOR`, `GITHUB_ACTOR` | `node scripts/ddd-staging-data-safety-check.mjs` |
| migration | p2-database-performance | database | BLOCKED | DDD_MIGRATION_FRESH_DB_VALIDATED must be true | `DDD_MIGRATION_FRESH_DB_VALIDATED`, `DDD_MIGRATION_FRESH_DB_EVIDENCE`, `DDD_MIGRATION_UPGRADE_DB_VALIDATED`, `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`, `DDD_MIGRATION_ENVIRONMENT`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_ENVIRONMENT`, `DDD_MIGRATION_OPERATOR`, `DDD_EVIDENCE_OPERATOR`, `GITHUB_ACTOR`, `DDD_MIGRATION_COMPLETED_AT` | `node scripts/ddd-staging-data-safety-check.mjs` |
| explain | p2-database-performance | database | BLOCKED | DDD_EXPLAIN_DATABASE is required | `DDD_EXPLAIN_DATABASE`, `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PASSWORD`, `DDD_EXPLAIN_ENVIRONMENT`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, `GITHUB_SHA`, `DDD_EVIDENCE_OPERATOR`, `GITHUB_ACTOR` | `node scripts/ddd-staging-data-safety-check.mjs` |

Next: `node scripts/ddd-staging-execution-checklist.mjs --commands`
