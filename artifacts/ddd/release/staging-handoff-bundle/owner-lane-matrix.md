# DDD Owner Lane Matrix

Status: BLOCKED
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Owners with lanes: 2/5

| Owner | Lanes | Blocking inputs | Evidence gaps | Missing artifacts | Next command |
| --- | ---: | ---: | ---: | ---: | --- |
| platform-events | 0 | 0 | 0 | 0 | `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh` |
| platform-owners | 0 | 0 | 2 | 0 | `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh` |
| release-infra | 4 | 0 | 2 | 0 | `node scripts/ddd-staging-execution-checklist.mjs --release-env-next-owner-template` |
| ai-owner | 1 | 0 | 1 | 0 | `node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown` |
| payment-owner | 0 | 0 | 1 | 0 | `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh` |

## Lane Details

### platform-events

- none

### platform-owners

- none

### release-infra

- 1. `p0-release-env`: status=PASS; missingArtifacts=0; accept=`DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`; next=`node scripts/ddd-staging-execution-checklist.mjs --release-env-next-owner-template`; source=`release-env-plan.json`
- 2. `p0-docker-images`: status=PASS; missingArtifacts=0; accept=`node scripts/ddd-docker-build-evidence.mjs --check`; next=`node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown`; source=`docker-image-submission-plan.json`
- 4. `p1-p2-data-safety`: status=PASS; missingArtifacts=0; accept=`node scripts/ddd-staging-data-safety-check.mjs`; next=`node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown`; source=`data-safety-submission-plan.json`
- 5. `final-review`: status=BLOCKED; missingArtifacts=0; accept=`DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`, `node scripts/ddd-docker-build-evidence.mjs --check`, `node scripts/ddd-staging-runtime-check.mjs`, `node scripts/ddd-staging-data-safety-check.mjs`; next=`node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce`; source=`final-review.json`

### ai-owner

- 3. `p1-runtime-business`: status=PASS; missingArtifacts=0; accept=`node scripts/ddd-staging-runtime-check.mjs`; next=`node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown`; source=`runtime-business-submission-plan.json`

### payment-owner

- none
