# DDD Release Command Catalog

Generated at: 2026-06-19T18:19:45.629Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
Cutover allowed: false
Stop reasons: 8
Script: artifacts/ddd/release/release-execution-commands.sh
Next priority: P0

## Final Cutover Decision

- finalRecommendation: NO_GO_STRICT
- cutoverAllowed: false
- releaseEnvFileCutoverSafe: false
- gateBlockers: 94
- blockedCutoverItems: 7
- stopReasonCount: 8
- stopReasonCoverage: catalog-snapshot
- cutoverAuthority: final-go-no-go-gate
- requiresFinalGate: true
- source: artifacts/ddd/release/release-final-go-no-go.json
- enforceCommand: `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

### Current Stop Reasons

- cutover checklist blocked: database-performance
- cutover checklist blocked: deployable-images
- cutover checklist blocked: evidence-integrity
- cutover checklist blocked: production-equivalence
- cutover checklist blocked: rollback-safety
- cutover checklist blocked: runtime-business-acceptance
- cutover checklist blocked: strict-release-gate
- strict release gate blockers=94

## Safety Signals

- releaseEnvFileCutoverSafe: false
- releaseEnvFile: ready=true status=PASS inputKind=release-env-file envFilePresent=true
  - securityChecked=true permissionSafe=true mode=666 requiredMode=600 reason=env-file permissionCheckSkipped=true
  - safeDefaultsExhausted=true blockingSafeDefaultAvailable=0 blockingRequiresOwnerInput=0
  - ownerInputReasons=none
  - ownerInputOwners=none
  - pendingActions=none

## Next Priority

- list: `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

## Owners

### lumira-ui

- Priority: P0
- Ready batches: p0-manifest-lumira-ui
- Expected artifacts: artifacts/ddd/lumira-ui/frontend-smoke.json, artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json, artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json, artifacts/ddd/release/evidence-manifest.json
- list: `DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

### release-infra

- Priority: P0
- Ready batches: p0-docker-release-infra, p0-runtime-readiness-release-infra
- Expected artifacts: artifacts/ddd/build/docker-image-evidence.json, artifacts/ddd/readiness/summary.json
- list: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

### release-performance

- Priority: P0
- Ready batches: p0-authenticated-performance-release-performance
- Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json, artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json, artifacts/ddd/performance/authenticated-runtime-baseline.json
- list: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

## Batches

### p0-manifest-lumira-ui

- Owner: lumira-ui
- Priority: P0
- Expected artifacts: artifacts/ddd/release/evidence-manifest.json, artifacts/ddd/lumira-ui/frontend-smoke.json, artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json, artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json
- list: `DDD_RELEASE_BATCH=p0-manifest-lumira-ui DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-manifest-lumira-ui DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-manifest-lumira-ui DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-manifest-lumira-ui bash artifacts/ddd/release/release-execution-commands.sh`

### p0-docker-release-infra

- Owner: release-infra
- Priority: P0
- Expected artifacts: artifacts/ddd/build/docker-image-evidence.json
- list: `DDD_RELEASE_BATCH=p0-docker-release-infra DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-docker-release-infra DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-docker-release-infra DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-docker-release-infra bash artifacts/ddd/release/release-execution-commands.sh`

### p0-runtime-readiness-release-infra

- Owner: release-infra
- Priority: P0
- Expected artifacts: artifacts/ddd/readiness/summary.json
- list: `DDD_RELEASE_BATCH=p0-runtime-readiness-release-infra DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-runtime-readiness-release-infra DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-runtime-readiness-release-infra DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-runtime-readiness-release-infra bash artifacts/ddd/release/release-execution-commands.sh`

### p0-authenticated-performance-release-performance

- Owner: release-performance
- Priority: P0
- Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json, artifacts/ddd/performance/authenticated-runtime-baseline.json, artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json
- list: `DDD_RELEASE_BATCH=p0-authenticated-performance-release-performance DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-authenticated-performance-release-performance DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-authenticated-performance-release-performance DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-authenticated-performance-release-performance bash artifacts/ddd/release/release-execution-commands.sh`

