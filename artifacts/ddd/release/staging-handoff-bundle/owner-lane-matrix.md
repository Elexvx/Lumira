# DDD Owner Lane Matrix

Status: BLOCKED
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Owners with lanes: 2/5

| Owner | Lanes | Blocking inputs | Evidence gaps | Missing artifacts | Next command |
| --- | ---: | ---: | ---: | ---: | --- |
| platform-events | 0 | 1 | 1 | 0 | `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh` |
| platform-owners | 1 | 33 | 3 | 2 | `node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown` |
| release-infra | 4 | 13 | 3 | 0 | `node scripts/ddd-staging-execution-checklist.mjs --release-env-next-owner-template` |
| ai-owner | 0 | 10 | 2 | 0 | `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh` |
| payment-owner | 0 | 10 | 2 | 0 | `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh` |

## Lane Details

### platform-events

- none

### platform-owners

- 4. `p1-p2-data-safety`: status=BLOCKED; missingArtifacts=1; accept=`node scripts/ddd-staging-data-safety-check.mjs`; next=`node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown`; source=`data-safety-submission-plan.json`

### release-infra

- 1. `p0-release-env`: status=BLOCKED; missingArtifacts=0; accept=`DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`; next=`node scripts/ddd-staging-execution-checklist.mjs --release-env-next-owner-template`; source=`release-env-plan.json`
- 2. `p0-docker-images`: status=BLOCKED; missingArtifacts=0; accept=`node scripts/ddd-docker-build-evidence.mjs --check`; next=`node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown`; source=`docker-image-submission-plan.json`
- 3. `p1-runtime-business`: status=BLOCKED; missingArtifacts=0; accept=`node scripts/ddd-staging-runtime-check.mjs`; next=`node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown`; source=`runtime-business-submission-plan.json`
- 5. `final-review`: status=BLOCKED; missingArtifacts=0; accept=`DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`, `node scripts/ddd-docker-build-evidence.mjs --check`, `node scripts/ddd-staging-runtime-check.mjs`, `node scripts/ddd-staging-data-safety-check.mjs`; next=`node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce`; source=`final-review.json`

### ai-owner

- none

### payment-owner

- none
