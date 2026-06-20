# DDD Release Closure Wave Env Matrix

Generated at: 2026-06-20T19:42:26.704Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
Waves: 4
Unique env keys: 11

## Wave 1. release-infra / p0-docker-release-infra

- Priority: P0
- Items: 1, 2, 3, 4
- Item ids: docker-blocker-1, docker-blocker-2, docker-image-frontend-failed, docker-image-lumira-server-failed
- Env keys: 2
  - `DDD_DOCKER_BUILD_STRICT`
  - `DDD_DOCKER_COMMAND`
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`

## Wave 2. release-infra / p0-runtime-readiness-release-infra

- Priority: P0
- Items: 5, 6, 7, 8
- Item ids: runtime-readiness-contract-1, runtime-readiness-contract-2, runtime-readiness-contract-3, runtime-readiness-contract-4
- Env keys: 4
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `LUMIRA_BASE_URL`
- Commands:
  - `node bin/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`

## Wave 3. lumira-ui / p0-manifest-lumira-ui

- Priority: P0
- Items: 9, 10, 11
- Item ids: manifest-missing-lumira-ui-frontend-smoke-json, manifest-missing-lumira-ui-lumira-ui-build-evidence-json, manifest-missing-lumira-ui-lumira-ui-static-evidence-json
- Env keys: 4
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_FRONTEND_EXPECT_DEPLOYED`
  - `DDD_RELEASE_CANDIDATE`
  - `PLAYWRIGHT_BASE_URL`
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/lumira-ui/frontend-smoke.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json`
  - `artifacts/ddd/release/evidence-manifest.json`

## Wave 4. release-performance / p0-authenticated-performance-release-performance

- Priority: P0
- Items: 12, 13, 14, 15, 16, 17, 18, 19, 20
- Item ids: performance-actual-shape-1, performance-actual-shape-2, performance-actual-shape-3, performance-actual-shape-4, performance-baseline-metadata-5, performance-baseline-metadata-6, performance-baseline-metadata-7, performance-baseline-metadata-8, performance-baseline-metadata-9
- Env keys: 4
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_RELEASE_CANDIDATE`
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

