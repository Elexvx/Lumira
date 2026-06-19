# DDD Release Closure Wave Env Matrix

Generated at: 2026-06-19T13:42:59.865Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
Waves: 4
Unique env keys: 10

## Wave 1. release-infra / p0-docker-release-infra

- Priority: P0
- Items: 1, 2, 3, 4
- Item ids: docker-blocker-1, docker-blocker-2, docker-image-frontend-failed, docker-image-lumira-server-failed
- Env keys: 2
  - `DDD_DOCKER_BUILD_STRICT`
  - `DDD_DOCKER_COMMAND`
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
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
  - `node scripts/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`

## Wave 3. release-owner / p0-manifest-release-owner

- Priority: P0
- Items: 9
- Item ids: manifest-missing-no-explain-json-files-in-tmp-ddd-explain
- Env keys: 4
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_RELEASE_MANIFEST_STRICT`
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
- Blocker hints:
  - artifacts/ddd/release/evidence-manifest.json blocker: artifacts/ddd/no explain JSON files in tmp\ddd-explain

## Wave 4. release-performance / p0-authenticated-performance-release-performance

- Priority: P0
- Items: 10, 11, 12, 13, 14, 15, 16, 17, 18
- Item ids: performance-actual-shape-1, performance-actual-shape-2, performance-actual-shape-3, performance-actual-shape-4, performance-baseline-metadata-5, performance-baseline-metadata-6, performance-baseline-metadata-7, performance-baseline-metadata-8, performance-baseline-metadata-9
- Env keys: 4
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_RELEASE_CANDIDATE`
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

