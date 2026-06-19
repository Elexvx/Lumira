# DDD Docker Image Evidence Plan

Status: BLOCKED
Recommended mode: external-runner-required
Docker available: false
Blocker: docker CLI is not available: spawnSync docker ENOENT

## Static Dockerfiles

| Image | Dockerfile | Status | SHA-256 | Issues |
| --- | --- | --- | --- | --- |
| lumira-server | `deploy/docker/service.Dockerfile` | PASS | b028411f0ed5fb4bdb8a45a40d1764b118d25069179b14150dd6b6dc38494fe4 | none |
| frontend | `deploy/docker/frontend.Dockerfile` | PASS | 43dc2013cd3f3595bbbba2b76c74e873fdd7bec13826e30fbc4a3a2fd01f1f78 | none |

## Evidence Paths

### docker-runner-build

Owner: release-infra
When: Docker CLI and daemon are available in the evidence runner.
Command: `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`

Artifacts:

- `artifacts/ddd/build/docker-image-evidence.json`

### existing-image-inspect

Owner: release-infra
When: CI already built and pushed release-candidate images.
Command: `DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs`

Artifacts:

- `artifacts/ddd/build/docker-image-evidence.json`

## Required Inputs

- `DDD_DOCKER_BUILD_STRICT`
- `DDD_RELEASE_CANDIDATE`
- `DDD_EVIDENCE_OPERATOR`
- `DDD_EVIDENCE_ENVIRONMENT`
- `DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE`
- `DDD_DOCKER_EXISTING_FRONTEND_IMAGE`
- `DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE`

## Validation Commands

- `node scripts/ddd-docker-build-evidence.mjs --check`
- `node scripts/ddd-release-readiness-summary.mjs`
- `node scripts/ddd-staging-execution-checklist.mjs --rollup`
- `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce`

Next: `Run DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs on a Docker-enabled CI runner, or provide DDD_DOCKER_EXISTING_* image env vars for inspect-only evidence.`
