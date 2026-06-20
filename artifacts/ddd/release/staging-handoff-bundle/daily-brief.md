# DDD Release Owner Daily Brief

Status: BLOCKED
Final recommendation: NO_GO_STRICT
Cutover ready: false
Cutover allowed: false
Accepted gates: 2/6
Blocked gates: 4/6
Evidence artifacts: 17/18 present; missing=1
Lane receipt coverage: 0/0

## Today

- none

## Lane Routes

| Order | Lane | Dispatch owner | Status | Missing artifacts | Command | Source |
| ---: | --- | --- | --- | ---: | --- | --- |
| 1 | `p0-release-env` | release-infra | PASS | 1 | `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce` | `release-env-plan.json` |
| 2 | `p0-docker-images` | release-infra | PASS | 1 | `node bin/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown` | `docker-image-submission-plan.json` |
| 3 | `p1-runtime-business` | release-infra | BLOCKED | 1 | `node bin/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown` | `runtime-business-submission-plan.json` |
| 4 | `p1-p2-data-safety` | platform-owners | BLOCKED | 0 | `node bin/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown` | `data-safety-submission-plan.json` |
| 5 | `final-review` | release-infra | BLOCKED | 1 | `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce` | `final-review.json` |

## Owner Actions

| Dispatch owner | Source owners | Status | Lanes | Blocking inputs | Missing artifacts | Packet | Next command |
| --- | --- | --- | ---: | ---: | ---: | --- | --- |

## Acceptance Commands

- release-env: accepted=true; owner=release-infra; missingArtifacts=0; command=`DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-file-lint.mjs`
- docker-images: accepted=true; owner=release-infra; missingArtifacts=0; command=`node bin/ddd-docker-build-evidence.mjs --check`
- runtime-business: accepted=false; owner=release-infra, lumira-ui, ai, file-owner, job-owner, payment-owner; missingArtifacts=1; command=`node bin/ddd-staging-runtime-check.mjs`
- rollback: accepted=false; owner=bounded-context owners; missingArtifacts=0; command=`node bin/ddd-staging-data-safety-check.mjs`
- migration: accepted=false; owner=database; missingArtifacts=0; command=`node bin/ddd-staging-data-safety-check.mjs`
- explain: accepted=false; owner=database; missingArtifacts=0; command=`node bin/ddd-staging-data-safety-check.mjs`

## Top Blocking Inputs

- `DDD_EVIDENCE_ENVIRONMENT`: gates=3; owners=bounded-context owners, database; next=`node bin/ddd-staging-data-safety-check.mjs`
- `DDD_EVIDENCE_OPERATOR`: gates=3; owners=bounded-context owners, database; next=`node bin/ddd-staging-data-safety-check.mjs`
- `DDD_RELEASE_ENVIRONMENT`: gates=3; owners=bounded-context owners, database; next=`node bin/ddd-staging-data-safety-check.mjs`
- `GITHUB_ACTOR`: gates=3; owners=bounded-context owners, database; next=`node bin/ddd-staging-data-safety-check.mjs`
- `DDD_RELEASE_CANDIDATE`: gates=2; owners=bounded-context owners, database; next=`node bin/ddd-staging-data-safety-check.mjs`
- `GITHUB_SHA`: gates=2; owners=bounded-context owners, database; next=`node bin/ddd-staging-data-safety-check.mjs`
- `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE`: gates=1; owners=ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra; next=`node bin/ddd-staging-runtime-check.mjs`
- `DDD_AI_EXPECT_PROVIDER_REMOTE`: gates=1; owners=ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra; next=`node bin/ddd-staging-runtime-check.mjs`
- `DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE`: gates=1; owners=ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra; next=`node bin/ddd-staging-runtime-check.mjs`
- `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE`: gates=1; owners=ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra; next=`node bin/ddd-staging-runtime-check.mjs`
- `DDD_DEPLOYMENT_EVIDENCE`: gates=1; owners=ai, file-owner, job-owner, lumira-ui, payment-owner, release-infra; next=`node bin/ddd-staging-runtime-check.mjs`
- `DDD_EXPLAIN_DATABASE`: gates=1; owners=database; next=`node bin/ddd-staging-data-safety-check.mjs`

Next: `node bin/ddd-staging-execution-checklist.mjs --commands`
