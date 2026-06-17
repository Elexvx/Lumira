# DDD Release Closure Wave Blocker Map

Generated at: 2026-06-17T08:13:17.325Z
Status: ADVISORY
Recommendation: NO_GO_STRICT
No auto waivers: true
Waves: 7
Mapped actions: 40
Candidate blocker hints: 0
Non-artifact blocker hints: 0

Candidate blockers are traceability hints only. The strict release evidence gate remains authoritative.

## Wave 1. release-infra / p0-release-env-lint-release-infra

- Priority: P0
- Sources: release-env-lint
- Category hints: release-environment
- Item ids: release-env-lint-placeholders, release-env-lint-status
- Candidate blocker hints: 0
- Commands:
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/release/release-env-lint.json`
- Rerun commands:
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## Wave 2. ai-owner / p0-release-config-ai-owner

- Priority: P0
- Sources: release-config
- Category hints: release-config
- Item ids: file owner url, iam owner url, owner internal token, platform owner url, provider api key, provider base url
- Candidate blocker hints: 0
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Rerun commands:
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## Wave 3. payment-owner / p0-release-config-payment-owner

- Priority: P0
- Sources: release-config
- Category hints: release-config
- Item ids: payment public url
- Candidate blocker hints: 0
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Rerun commands:
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## Wave 4. platform-events / p0-release-config-platform-events

- Priority: P0
- Sources: release-config
- Category hints: release-config
- Item ids: event stream key, job backend url, job file url, job internal token, job message url, job payment url, job plugin url, xxl job admin, xxl job token
- Candidate blocker hints: 0
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Rerun commands:
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## Wave 5. platform-owners / p0-release-config-platform-owners

- Priority: P0
- Sources: release-config
- Category hints: release-config
- Item ids: ai service, auth service, file service, job executor, localization service, message service, payment service, plugin service, system service
- Candidate blocker hints: 0
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Rerun commands:
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## Wave 6. release-infra / p0-release-config-release-infra

- Priority: P0
- Sources: release-config
- Category hints: release-config
- Item ids: backend base url, cors origins, database password, database url, database username, field secret, frontend base url, jwt secret, redis host
- Candidate blocker hints: 0
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Rerun commands:
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

## Wave 7. release-infra / p0-docker-release-infra

- Priority: P0
- Sources: docker
- Category hints: docker
- Item ids: docker-blocker-1, docker-blocker-2, docker-image-frontend-failed, docker-image-lumira-server-failed
- Candidate blocker hints: 0
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`
- Rerun commands:
  - `node scripts/ddd-release-evidence-gate.mjs`
  - `node scripts/ddd-release-readiness-summary.mjs`

