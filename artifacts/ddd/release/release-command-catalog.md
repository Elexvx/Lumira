# DDD Release Command Catalog

Generated at: 2026-06-18T19:37:26.213Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
Cutover allowed: false
Stop reasons: 9
Script: artifacts/ddd/release/release-execution-commands.sh
Next priority: P0

## Final Cutover Decision

- finalRecommendation: NO_GO_STRICT
- cutoverAllowed: false
- releaseEnvFileCutoverSafe: false
- gateBlockers: 94
- blockedCutoverItems: 8
- stopReasonCount: 9
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
- cutover checklist blocked: release-environment
- cutover checklist blocked: rollback-safety
- cutover checklist blocked: runtime-business-acceptance
- cutover checklist blocked: strict-release-gate
- strict release gate blockers=94

## Safety Signals

- releaseEnvFileCutoverSafe: false
- releaseEnvFile: ready=false status=FAIL inputKind=release-env-file envFilePresent=true
  - securityChecked=true permissionSafe=true mode=600 requiredMode=600 reason=env-file permissionCheckSkipped=false
  - safeDefaultsExhausted=true blockingSafeDefaultAvailable=0 blockingRequiresOwnerInput=34
  - ownerInputReasons=production-endpoint:24, secret-manager:8, owner-production-value:2
  - ownerInputOwners=platform-events:9, platform-owners:9, release-infra:9, ai-owner:6, payment-owner:1
  - pendingActions=release-env-lint-status, release-env-lint-placeholders

## Next Priority

- list: `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

## Owners

### ai-owner

- Priority: P0
- Ready batches: p0-release-config-ai-owner
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- list: `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_OWNER=ai-owner DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

### payment-owner

- Priority: P0
- Ready batches: p0-release-config-payment-owner
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- list: `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_OWNER=payment-owner DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

### platform-events

- Priority: P0
- Ready batches: p0-release-config-platform-events
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- list: `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_OWNER=platform-events DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

### platform-owners

- Priority: P0
- Ready batches: p0-release-config-platform-owners
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- list: `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_OWNER=platform-owners DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

### release-infra

- Priority: P0
- Ready batches: p0-docker-release-infra, p0-release-config-release-infra, p0-release-env-lint-release-infra, p0-runtime-readiness-release-infra
- Expected artifacts: artifacts/ddd/build/docker-image-evidence.json, artifacts/ddd/config/release-config-evidence.json, artifacts/ddd/readiness/summary.json, artifacts/ddd/release/release-env-lint.json
- list: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

### release-owner

- Priority: P0
- Ready batches: p0-manifest-release-owner
- Expected artifacts: artifacts/ddd/release/evidence-manifest.json
- list: `DDD_RELEASE_OWNER=release-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_OWNER=release-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_OWNER=release-owner DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_OWNER=release-owner DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

### release-performance

- Priority: P0
- Ready batches: p0-authenticated-performance-release-performance
- Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json, artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json, artifacts/ddd/performance/authenticated-runtime-baseline.json
- list: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`

## Batches

### p0-release-config-ai-owner

- Owner: ai-owner
- Priority: P0
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- list: `DDD_RELEASE_BATCH=p0-release-config-ai-owner DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-release-config-ai-owner DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-release-config-ai-owner DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-release-config-ai-owner bash artifacts/ddd/release/release-execution-commands.sh`

### p0-release-config-payment-owner

- Owner: payment-owner
- Priority: P0
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- list: `DDD_RELEASE_BATCH=p0-release-config-payment-owner DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-release-config-payment-owner DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-release-config-payment-owner DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-release-config-payment-owner bash artifacts/ddd/release/release-execution-commands.sh`

### p0-release-config-platform-events

- Owner: platform-events
- Priority: P0
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- list: `DDD_RELEASE_BATCH=p0-release-config-platform-events DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-release-config-platform-events DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-release-config-platform-events DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-release-config-platform-events bash artifacts/ddd/release/release-execution-commands.sh`

### p0-release-config-platform-owners

- Owner: platform-owners
- Priority: P0
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- list: `DDD_RELEASE_BATCH=p0-release-config-platform-owners DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-release-config-platform-owners DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-release-config-platform-owners DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-release-config-platform-owners bash artifacts/ddd/release/release-execution-commands.sh`

### p0-docker-release-infra

- Owner: release-infra
- Priority: P0
- Expected artifacts: artifacts/ddd/build/docker-image-evidence.json
- list: `DDD_RELEASE_BATCH=p0-docker-release-infra DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-docker-release-infra DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-docker-release-infra DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-docker-release-infra bash artifacts/ddd/release/release-execution-commands.sh`

### p0-release-config-release-infra

- Owner: release-infra
- Priority: P0
- Expected artifacts: artifacts/ddd/config/release-config-evidence.json
- list: `DDD_RELEASE_BATCH=p0-release-config-release-infra DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-release-config-release-infra DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-release-config-release-infra DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-release-config-release-infra bash artifacts/ddd/release/release-execution-commands.sh`

### p0-release-env-lint-release-infra

- Owner: release-infra
- Priority: P0
- Expected artifacts: artifacts/ddd/release/release-env-lint.json, artifacts/ddd/config/release-config-evidence.json
- list: `DDD_RELEASE_BATCH=p0-release-env-lint-release-infra DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-release-env-lint-release-infra DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-release-env-lint-release-infra DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-release-env-lint-release-infra bash artifacts/ddd/release/release-execution-commands.sh`

### p0-runtime-readiness-release-infra

- Owner: release-infra
- Priority: P0
- Expected artifacts: artifacts/ddd/readiness/summary.json
- list: `DDD_RELEASE_BATCH=p0-runtime-readiness-release-infra DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-runtime-readiness-release-infra DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-runtime-readiness-release-infra DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-runtime-readiness-release-infra bash artifacts/ddd/release/release-execution-commands.sh`

### p0-manifest-release-owner

- Owner: release-owner
- Priority: P0
- Expected artifacts: artifacts/ddd/release/evidence-manifest.json
- list: `DDD_RELEASE_BATCH=p0-manifest-release-owner DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-manifest-release-owner DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-manifest-release-owner DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-manifest-release-owner bash artifacts/ddd/release/release-execution-commands.sh`

### p0-authenticated-performance-release-performance

- Owner: release-performance
- Priority: P0
- Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json, artifacts/ddd/performance/authenticated-runtime-baseline.json, artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json
- list: `DDD_RELEASE_BATCH=p0-authenticated-performance-release-performance DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`
- envCheck: `DDD_RELEASE_BATCH=p0-authenticated-performance-release-performance DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`
- dryRun: `DDD_RELEASE_BATCH=p0-authenticated-performance-release-performance DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`
- execute: `DDD_RELEASE_BATCH=p0-authenticated-performance-release-performance bash artifacts/ddd/release/release-execution-commands.sh`

