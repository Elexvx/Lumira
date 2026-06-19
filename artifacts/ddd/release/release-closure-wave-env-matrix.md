# DDD Release Closure Wave Env Matrix

Generated at: 2026-06-19T18:09:18.921Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
Waves: 6
Unique env keys: 16

## Wave 1. release-infra / p0-docker-release-infra

- Priority: P0
- Items: 1, 2, 3, 4
- Item ids: docker-blocker-1, docker-blocker-2, docker-image-frontend-failed, docker-image-lumira-server-failed
- Env keys: 2
  - `DDD_DOCKER_BUILD_STRICT`
  - `DDD_DOCKER_COMMAND`
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
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

## Wave 3. database / p0-manifest-database

- Priority: P0
- Items: 9, 10
- Item ids: manifest-missing-optional-artifact-release-release-unblock-brief-json-handoffreferences-migration-evidence-handoff-command-must-be-ddd-migration-check-env-true-node-bin-ddd-migration-evidence-mjs, manifest-missing-optional-artifact-release-release-unblock-brief-json-markdown-evidence-handoffs-must-include-required-command-for-migration-evidence-handoff
- Env keys: 4
  - `DDD_MIGRATION_FRESH_DB_EVIDENCE`
  - `DDD_MIGRATION_FRESH_DB_VALIDATED`
  - `DDD_MIGRATION_UPGRADE_DB_EVIDENCE`
  - `DDD_MIGRATION_UPGRADE_DB_VALIDATED`
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
- Blocker hints:
  - artifacts/ddd/release/evidence-manifest.json blocker: artifacts/ddd/optional artifact release/release-unblock-brief.json: handoffReferences migration-evidence-handoff command must be DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs
  - artifacts/ddd/release/evidence-manifest.json blocker: artifacts/ddd/optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for migration-evidence-handoff

## Wave 4. lumira-ui / p0-manifest-lumira-ui

- Priority: P0
- Items: 11, 12, 13
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

## Wave 5. release-owner / p0-manifest-release-owner

- Priority: P0
- Items: 14, 15, 16, 17, 18, 19, 20
- Item ids: manifest-missing-optional-artifact-release-release-unblock-brief-json-finalownerqueuefastpath-commands-must-include-readiness-summary-refresh, manifest-missing-optional-artifact-release-release-unblock-brief-json-handoffreferences-release-env-owner-input-packet-command-must-be-node-bin-ddd-release-env-owner-input-packet-contract-mjs, manifest-missing-optional-artifact-release-release-unblock-brief-json-handoffreferences-release-owner-input-receipt-command-must-be-node-bin-ddd-release-owner-input-receipt-contract-mjs, manifest-missing-optional-artifact-release-release-unblock-brief-json-handoffreferences-rollback-deferral-owner-handoff-command-must-be-node-bin-ddd-rollback-deferral-template-mjs, manifest-missing-optional-artifact-release-release-unblock-brief-json-markdown-evidence-handoffs-must-include-required-command-for-release-env-owner-input-packet, manifest-missing-optional-artifact-release-release-unblock-brief-json-markdown-evidence-handoffs-must-include-required-command-for-release-owner-input-receipt, manifest-missing-optional-artifact-release-release-unblock-brief-json-markdown-evidence-handoffs-must-include-required-command-for-rollback-deferral-owner-handoff
- Env keys: 4
  - `DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_RELEASE_MANIFEST_STRICT`
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
- Blocker hints:
  - artifacts/ddd/release/evidence-manifest.json blocker: artifacts/ddd/optional artifact release/release-unblock-brief.json: finalOwnerQueueFastPath.commands must include readiness summary refresh
  - artifacts/ddd/release/evidence-manifest.json blocker: artifacts/ddd/optional artifact release/release-unblock-brief.json: handoffReferences release-env-owner-input-packet command must be node bin/ddd-release-env-owner-input-packet-contract.mjs
  - artifacts/ddd/release/evidence-manifest.json blocker: artifacts/ddd/optional artifact release/release-unblock-brief.json: handoffReferences release-owner-input-receipt command must be node bin/ddd-release-owner-input-receipt-contract.mjs
  - artifacts/ddd/release/evidence-manifest.json blocker: artifacts/ddd/optional artifact release/release-unblock-brief.json: handoffReferences rollback-deferral-owner-handoff command must be node bin/ddd-rollback-deferral-template.mjs
  - artifacts/ddd/release/evidence-manifest.json blocker: artifacts/ddd/optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for release-env-owner-input-packet
  - artifacts/ddd/release/evidence-manifest.json blocker: artifacts/ddd/optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for release-owner-input-receipt
  - artifacts/ddd/release/evidence-manifest.json blocker: artifacts/ddd/optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for rollback-deferral-owner-handoff

## Wave 6. release-performance / p0-authenticated-performance-release-performance

- Priority: P0
- Items: 21, 22, 23, 24, 25, 26, 27, 28, 29
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

