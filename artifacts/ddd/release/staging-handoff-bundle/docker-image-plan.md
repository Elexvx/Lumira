# DDD Docker Image Evidence Plan

Status: PASS
Recommended mode: build-and-inspect
Docker available: true
Blocker: none

## Static Dockerfiles

| Image | Dockerfile | Status | SHA-256 | Issues |
| --- | --- | --- | --- | --- |
| lumira-server | `deploy/docker/service.Dockerfile` | PASS | 56f156c9c74d97ae4c6ccff32cec6ff7cf3aa292bd79809bd6648de4196b41b1 | none |
| lumira-ui | `deploy/docker/lumira-ui.Dockerfile` | PASS | fcfbc53f8ab90f88f02a6007ee28e0ba9afba5cf542cd02a66c74dd7b284f046 | none |

## Evidence Paths

### docker-runner-build

Owner: release-infra
When: Docker CLI and daemon are available in the evidence runner.
Command: `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`

Artifacts:

- `artifacts/ddd/build/docker-image-evidence.json`

### existing-image-inspect

Owner: release-infra
When: CI already built and pushed release-candidate images.
Command: `DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/lumira-ui:<release-candidate> node bin/ddd-docker-build-evidence.mjs`

Artifacts:

- `artifacts/ddd/build/docker-image-evidence.json`

## Required Inputs

- `DDD_DOCKER_BUILD_STRICT`
- `DDD_RELEASE_CANDIDATE`
- `DDD_EVIDENCE_OPERATOR`
- `DDD_EVIDENCE_ENVIRONMENT`
- `DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE`
- `DDD_DOCKER_EXISTING_LUMIRA_UI_IMAGE`
- `DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE`

## Validation Commands

- `node bin/ddd-docker-build-evidence.mjs --check`
- `node bin/ddd-release-readiness-summary.mjs`
- `node bin/ddd-staging-execution-checklist.mjs --rollup`
- `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce`

Next: `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
