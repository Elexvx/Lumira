# DDD Docker Image Submission Plan

Status: PASS
Owner: release-infra
Gate: docker-images
Recommended mode: build-and-inspect
Docker available: true
Blocker: none
Evidence artifact: `artifacts/ddd/build/docker-image-evidence.json`

## Static Dockerfiles

| Image | Dockerfile | Status | SHA-256 |
| --- | --- | --- | --- |
| lumira-server | `deploy/docker/service.Dockerfile` | PASS | b028411f0ed5fb4bdb8a45a40d1764b118d25069179b14150dd6b6dc38494fe4 |
| frontend | `deploy/docker/frontend.Dockerfile` | PASS | 43dc2013cd3f3595bbbba2b76c74e873fdd7bec13826e30fbc4a3a2fd01f1f78 |

## Submission Modes

### docker-runner-build

Owner: release-infra
When: Docker CLI and daemon are available in the evidence runner.
Command: `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`

Prerequisites:

- Docker CLI, daemon, and buildx are available on the evidence runner.
- Release candidate and provenance env values are populated before running strict evidence capture.

Workflow inputs:

- `DDD_DOCKER_BUILD_STRICT`
- `DDD_RELEASE_CANDIDATE`
- `DDD_EVIDENCE_OPERATOR`
- `DDD_EVIDENCE_ENVIRONMENT`

Artifacts:

- `artifacts/ddd/build/docker-image-evidence.json`

### existing-image-inspect

Owner: release-infra
When: CI already built and pushed release-candidate images.
Command: `DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs`

Prerequisites:

- Release-candidate images already exist in the registry.
- Image references and CI build evidence URL are supplied through the existing-image env inputs.

Workflow inputs:

- `DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE`
- `DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE`
- `DDD_DOCKER_EXISTING_FRONTEND_IMAGE`

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
- `node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown`
- `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce`

## Lane Receipt Fragment

```json
{
  "owner": "release-infra",
  "lane": "p0-docker-images",
  "status": "PASS",
  "providedArtifacts": [
    "artifacts/ddd/build/docker-image-evidence.json"
  ],
  "missingArtifacts": [],
  "completedAt": "<ISO-8601 timestamp after validation commands pass>",
  "completedBy": "<owner or workflow actor>",
  "acceptanceCommands": [
    "node scripts/ddd-docker-build-evidence.mjs --check"
  ]
}
```

## Pass Criteria

- Docker image evidence artifact exists and passes `node scripts/ddd-docker-build-evidence.mjs --check`.
- Image references are scoped to the release candidate and include build provenance evidence.
- Readiness summary and operator progress are regenerated after evidence capture.
- The docker-images gate no longer blocks final review.

Next: `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
