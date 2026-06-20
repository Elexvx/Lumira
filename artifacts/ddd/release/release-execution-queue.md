# DDD Release Execution Queue

Generated at: 2026-06-20T19:42:26.704Z
Status: NOT_READY
Release gate mode: strict
Release gate blockers: 148
Ready batches: 4
Blocked batches: 18
Next priority: P0

## Safety Signals

- releaseEnvFileCutoverSafe: false
- releaseEnvFile: ready=true status=PASS inputKind=release-env-file envFilePresent=true
  - securityChecked=true permissionSafe=true mode=666 requiredMode=600 reason=env-file permissionCheckSkipped=true
  - pendingActions=none

## Ready Now

### p0-docker-release-infra

- Scope: P0 docker -> release-infra
- Pending items: 4
- Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND
- Env check groups:
  - `DDD_DOCKER_BUILD_STRICT=DDD_DOCKER_BUILD_STRICT`
  - `DDD_DOCKER_COMMAND=DDD_DOCKER_COMMAND`
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`
- Exit criteria:
  - Docker CLI and daemon are available in the evidence runner.
  - Required lumira-server and lumira-ui images are built, inspected, and not skipped.
  - Clear this batch before running downstream runtime-heavy evidence.

### p0-runtime-readiness-release-infra

- Scope: P0 runtime-readiness -> release-infra
- Pending items: 4
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL
- Env check groups:
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
- Commands:
  - `node bin/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`
- Exit criteria:
  - Runtime readiness is generated from an HTTPS non-local backend base URL.
  - All 30 owner readiness/health/metrics checks pass.
  - Clear this batch before running downstream runtime-heavy evidence.

### p0-manifest-lumira-ui

- Scope: P0 manifest -> lumira-ui
- Pending items: 3
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_FRONTEND_EXPECT_DEPLOYED, DDD_RELEASE_CANDIDATE, PLAYWRIGHT_BASE_URL
- Env check groups:
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_FRONTEND_EXPECT_DEPLOYED=DDD_FRONTEND_EXPECT_DEPLOYED`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `PLAYWRIGHT_BASE_URL=FRONTEND_BASE_URL|PLAYWRIGHT_BASE_URL`
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
  - `artifacts/ddd/lumira-ui/frontend-smoke.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json`
- Exit criteria:
  - All required release evidence artifacts are present and checksummed.
  - Clear this batch before running downstream runtime-heavy evidence.

### p0-authenticated-performance-release-performance

- Scope: P0 authenticated-performance -> release-performance
- Pending items: 9
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Env check groups:
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT=DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT=DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
- Exit criteria:
  - Authenticated performance actual is generated from a production-equivalent HTTPS backend.
  - Accepted baseline exists and current p95/upload metrics do not regress beyond the configured threshold.
  - Clear this batch before running downstream runtime-heavy evidence.

## Blocked Later

- p1-ai-runtime-ai: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/ai/ai-runtime-drill.json`
- p1-business-e2e-file-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/file/file-processing-e2e.json`
- p1-business-e2e-job-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/jobs/job-e2e-smoke.json`
- p1-business-e2e-payment-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/payment/payment-webhook-e2e.json`
- p1-rollback-ai-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-auth-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-file-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-iam-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-job-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-localization-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-message-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-payment-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-platform-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p1-rollback-plugin-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
  - Expected artifacts:
    - `artifacts/ddd/rollback/rollback-drill.json`
- p2-explain-database: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
  - Expected artifacts:
    - `tmp/ddd-explain/*.json`
    - `artifacts/ddd/release/explain-gate-report.json`
- p3-orchestrator-database: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
  - Expected artifacts:
    - `artifacts/ddd/release/orchestrator-report.json`
    - `artifacts/ddd/release/release-evidence-gate.json`
    - `artifacts/ddd/release/readiness-summary.json`
- p3-orchestrator-release-infra: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
  - Expected artifacts:
    - `artifacts/ddd/release/orchestrator-report.json`
    - `artifacts/ddd/release/release-evidence-gate.json`
    - `artifacts/ddd/release/readiness-summary.json`
- p3-orchestrator-release-owner: waits for p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
  - Expected artifacts:
    - `artifacts/ddd/release/orchestrator-report.json`
    - `artifacts/ddd/release/release-evidence-gate.json`
    - `artifacts/ddd/release/readiness-summary.json`
