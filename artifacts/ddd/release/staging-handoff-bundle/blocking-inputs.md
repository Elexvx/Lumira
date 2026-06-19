# DDD Staging Blocking Inputs

Status: BLOCKED
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Blocking inputs: 31
Blocked gates: 4

| Input | Gates | Owners | Next commands |
| --- | --- | --- | --- |
| `DDD_EVIDENCE_ENVIRONMENT` | `rollback`, `migration`, `explain` | bounded-context owners, database | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_EVIDENCE_OPERATOR` | `rollback`, `migration`, `explain` | bounded-context owners, database | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_RELEASE_ENVIRONMENT` | `rollback`, `migration`, `explain` | bounded-context owners, database | `node bin/ddd-staging-data-safety-check.mjs` |
| `GITHUB_ACTOR` | `rollback`, `migration`, `explain` | bounded-context owners, database | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_RELEASE_CANDIDATE` | `rollback`, `explain` | bounded-context owners, database | `node bin/ddd-staging-data-safety-check.mjs` |
| `GITHUB_SHA` | `rollback`, `explain` | bounded-context owners, database | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE` | `runtime-business` | ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra | `node bin/ddd-staging-runtime-check.mjs` |
| `DDD_AI_EXPECT_PROVIDER_REMOTE` | `runtime-business` | ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra | `node bin/ddd-staging-runtime-check.mjs` |
| `DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE` | `runtime-business` | ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra | `node bin/ddd-staging-runtime-check.mjs` |
| `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE` | `runtime-business` | ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra | `node bin/ddd-staging-runtime-check.mjs` |
| `DDD_DEPLOYMENT_EVIDENCE` | `runtime-business` | ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra | `node bin/ddd-staging-runtime-check.mjs` |
| `DDD_EXPLAIN_DATABASE` | `explain` | database | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_EXPLAIN_ENVIRONMENT` | `explain` | database | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_FRONTEND_DEPLOYMENT_EVIDENCE` | `runtime-business` | ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra | `node bin/ddd-staging-runtime-check.mjs` |
| `DDD_FRONTEND_EXPECT_DEPLOYED` | `runtime-business` | ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra | `node bin/ddd-staging-runtime-check.mjs` |
| `DDD_MIGRATION_COMPLETED_AT` | `migration` | database | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_MIGRATION_ENVIRONMENT` | `migration` | database | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_MIGRATION_FRESH_DB_EVIDENCE` | `migration` | database | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_MIGRATION_FRESH_DB_VALIDATED` | `migration` | database | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_MIGRATION_OPERATOR` | `migration` | database | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_MIGRATION_UPGRADE_DB_EVIDENCE` | `migration` | database | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_MIGRATION_UPGRADE_DB_VALIDATED` | `migration` | database | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_ROLLBACK_DRILL_DEFERRAL_FILE` | `rollback` | bounded-context owners | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_ROLLBACK_DRILL_ENVIRONMENT` | `rollback` | bounded-context owners | `node bin/ddd-staging-data-safety-check.mjs` |
| `DDD_ROLLBACK_DRILL_FILE` | `rollback` | bounded-context owners | `node bin/ddd-staging-data-safety-check.mjs` |
| `LUMIRA_BASE_URL` | `runtime-business` | ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra | `node bin/ddd-staging-runtime-check.mjs` |
| `MYSQL_HOST` | `explain` | database | `node bin/ddd-staging-data-safety-check.mjs` |
| `MYSQL_PASSWORD` | `explain` | database | `node bin/ddd-staging-data-safety-check.mjs` |
| `MYSQL_PORT` | `explain` | database | `node bin/ddd-staging-data-safety-check.mjs` |
| `MYSQL_USER` | `explain` | database | `node bin/ddd-staging-data-safety-check.mjs` |
| `PLAYWRIGHT_BASE_URL` | `runtime-business` | ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra | `node bin/ddd-staging-runtime-check.mjs` |

## Gate Details

### DDD_EVIDENCE_ENVIRONMENT

- rollback: owner=bounded-context owners; blocker=rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE; next=node bin/ddd-staging-data-safety-check.mjs
- migration: owner=database; blocker=DDD_MIGRATION_FRESH_DB_VALIDATED must be true; next=node bin/ddd-staging-data-safety-check.mjs
- explain: owner=database; blocker=DDD_EXPLAIN_DATABASE is required; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_EVIDENCE_OPERATOR

- rollback: owner=bounded-context owners; blocker=rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE; next=node bin/ddd-staging-data-safety-check.mjs
- migration: owner=database; blocker=DDD_MIGRATION_FRESH_DB_VALIDATED must be true; next=node bin/ddd-staging-data-safety-check.mjs
- explain: owner=database; blocker=DDD_EXPLAIN_DATABASE is required; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_RELEASE_ENVIRONMENT

- rollback: owner=bounded-context owners; blocker=rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE; next=node bin/ddd-staging-data-safety-check.mjs
- migration: owner=database; blocker=DDD_MIGRATION_FRESH_DB_VALIDATED must be true; next=node bin/ddd-staging-data-safety-check.mjs
- explain: owner=database; blocker=DDD_EXPLAIN_DATABASE is required; next=node bin/ddd-staging-data-safety-check.mjs

### GITHUB_ACTOR

- rollback: owner=bounded-context owners; blocker=rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE; next=node bin/ddd-staging-data-safety-check.mjs
- migration: owner=database; blocker=DDD_MIGRATION_FRESH_DB_VALIDATED must be true; next=node bin/ddd-staging-data-safety-check.mjs
- explain: owner=database; blocker=DDD_EXPLAIN_DATABASE is required; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_RELEASE_CANDIDATE

- rollback: owner=bounded-context owners; blocker=rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE; next=node bin/ddd-staging-data-safety-check.mjs
- explain: owner=database; blocker=DDD_EXPLAIN_DATABASE is required; next=node bin/ddd-staging-data-safety-check.mjs

### GITHUB_SHA

- rollback: owner=bounded-context owners; blocker=rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE; next=node bin/ddd-staging-data-safety-check.mjs
- explain: owner=database; blocker=DDD_EXPLAIN_DATABASE is required; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE

- runtime-business: owner=release-infra, lumira-ui, ai, file-owner, job-owner, payment-owner; blocker=LUMIRA_BASE_URL is required; next=node bin/ddd-staging-runtime-check.mjs

### DDD_AI_EXPECT_PROVIDER_REMOTE

- runtime-business: owner=release-infra, lumira-ui, ai, file-owner, job-owner, payment-owner; blocker=LUMIRA_BASE_URL is required; next=node bin/ddd-staging-runtime-check.mjs

### DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE

- runtime-business: owner=release-infra, lumira-ui, ai, file-owner, job-owner, payment-owner; blocker=LUMIRA_BASE_URL is required; next=node bin/ddd-staging-runtime-check.mjs

### DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE

- runtime-business: owner=release-infra, lumira-ui, ai, file-owner, job-owner, payment-owner; blocker=LUMIRA_BASE_URL is required; next=node bin/ddd-staging-runtime-check.mjs

### DDD_DEPLOYMENT_EVIDENCE

- runtime-business: owner=release-infra, lumira-ui, ai, file-owner, job-owner, payment-owner; blocker=LUMIRA_BASE_URL is required; next=node bin/ddd-staging-runtime-check.mjs

### DDD_EXPLAIN_DATABASE

- explain: owner=database; blocker=DDD_EXPLAIN_DATABASE is required; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_EXPLAIN_ENVIRONMENT

- explain: owner=database; blocker=DDD_EXPLAIN_DATABASE is required; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_FRONTEND_DEPLOYMENT_EVIDENCE

- runtime-business: owner=release-infra, lumira-ui, ai, file-owner, job-owner, payment-owner; blocker=LUMIRA_BASE_URL is required; next=node bin/ddd-staging-runtime-check.mjs

### DDD_FRONTEND_EXPECT_DEPLOYED

- runtime-business: owner=release-infra, lumira-ui, ai, file-owner, job-owner, payment-owner; blocker=LUMIRA_BASE_URL is required; next=node bin/ddd-staging-runtime-check.mjs

### DDD_MIGRATION_COMPLETED_AT

- migration: owner=database; blocker=DDD_MIGRATION_FRESH_DB_VALIDATED must be true; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_MIGRATION_ENVIRONMENT

- migration: owner=database; blocker=DDD_MIGRATION_FRESH_DB_VALIDATED must be true; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_MIGRATION_FRESH_DB_EVIDENCE

- migration: owner=database; blocker=DDD_MIGRATION_FRESH_DB_VALIDATED must be true; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_MIGRATION_FRESH_DB_VALIDATED

- migration: owner=database; blocker=DDD_MIGRATION_FRESH_DB_VALIDATED must be true; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_MIGRATION_OPERATOR

- migration: owner=database; blocker=DDD_MIGRATION_FRESH_DB_VALIDATED must be true; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_MIGRATION_UPGRADE_DB_EVIDENCE

- migration: owner=database; blocker=DDD_MIGRATION_FRESH_DB_VALIDATED must be true; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_MIGRATION_UPGRADE_DB_VALIDATED

- migration: owner=database; blocker=DDD_MIGRATION_FRESH_DB_VALIDATED must be true; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_ROLLBACK_DRILL_DEFERRAL_FILE

- rollback: owner=bounded-context owners; blocker=rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_ROLLBACK_DRILL_ENVIRONMENT

- rollback: owner=bounded-context owners; blocker=rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE; next=node bin/ddd-staging-data-safety-check.mjs

### DDD_ROLLBACK_DRILL_FILE

- rollback: owner=bounded-context owners; blocker=rollback-evidence-source requires one of DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE; next=node bin/ddd-staging-data-safety-check.mjs

### LUMIRA_BASE_URL

- runtime-business: owner=release-infra, lumira-ui, ai, file-owner, job-owner, payment-owner; blocker=LUMIRA_BASE_URL is required; next=node bin/ddd-staging-runtime-check.mjs

### MYSQL_HOST

- explain: owner=database; blocker=DDD_EXPLAIN_DATABASE is required; next=node bin/ddd-staging-data-safety-check.mjs

### MYSQL_PASSWORD

- explain: owner=database; blocker=DDD_EXPLAIN_DATABASE is required; next=node bin/ddd-staging-data-safety-check.mjs

### MYSQL_PORT

- explain: owner=database; blocker=DDD_EXPLAIN_DATABASE is required; next=node bin/ddd-staging-data-safety-check.mjs

### MYSQL_USER

- explain: owner=database; blocker=DDD_EXPLAIN_DATABASE is required; next=node bin/ddd-staging-data-safety-check.mjs

### PLAYWRIGHT_BASE_URL

- runtime-business: owner=release-infra, lumira-ui, ai, file-owner, job-owner, payment-owner; blocker=LUMIRA_BASE_URL is required; next=node bin/ddd-staging-runtime-check.mjs

Next: `node bin/ddd-staging-execution-checklist.mjs --evidence-env-template`
