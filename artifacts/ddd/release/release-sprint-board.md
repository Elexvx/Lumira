# DDD Release Sprint Board

Generated at: 2026-06-19T18:19:45.629Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
Ready batches: 4
Blocked batches: 18
Next wave priority: P0

## Next Wave

- Owners: lumira-ui, release-infra, release-performance
- Batch IDs: p0-manifest-lumira-ui, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-authenticated-performance-release-performance
- Expected artifacts: artifacts/ddd/build/docker-image-evidence.json, artifacts/ddd/lumira-ui/frontend-smoke.json, artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json, artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json, artifacts/ddd/performance/authenticated-runtime-actual.json, artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json, artifacts/ddd/performance/authenticated-runtime-baseline.json, artifacts/ddd/readiness/summary.json, artifacts/ddd/release/evidence-manifest.json

## Priorities

### P0

- Pending items: 20
- Ready batches: p0-manifest-lumira-ui, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-authenticated-performance-release-performance
- Blocked batches: none
- Owners: lumira-ui, release-infra, release-performance

### P1

- Pending items: 16
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-business-e2e-file-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-business-e2e-job-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-business-e2e-payment-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Owners: ai, ai-owner, auth-owner, file-owner, iam-owner, job-owner, localization-owner, message-owner, payment-owner, platform-owner, plugin-owner

### P2

- Pending items: 2
- Ready batches: none
- Blocked batches: p2-explain-database
- Owners: database

### P3

- Pending items: 4
- Ready batches: none
- Blocked batches: p3-orchestrator-database, p3-orchestrator-release-infra, p3-orchestrator-release-owner
- Owners: database, release-infra, release-owner

## Owners

### release-infra

- Pending items: 10
- Ready batches: p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: p3-orchestrator-release-infra

### release-performance

- Pending items: 9
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none

### lumira-ui

- Pending items: 3
- Ready batches: p0-manifest-lumira-ui
- Blocked batches: none

### ai

- Pending items: 3
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai

### database

- Pending items: 3
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database

### file-owner

- Pending items: 2
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-rollback-file-owner

### job-owner

- Pending items: 2
- Ready batches: none
- Blocked batches: p1-business-e2e-job-owner, p1-rollback-job-owner

### payment-owner

- Pending items: 2
- Ready batches: none
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner

### ai-owner

- Pending items: 1
- Ready batches: none
- Blocked batches: p1-rollback-ai-owner

### auth-owner

- Pending items: 1
- Ready batches: none
- Blocked batches: p1-rollback-auth-owner

### iam-owner

- Pending items: 1
- Ready batches: none
- Blocked batches: p1-rollback-iam-owner

### localization-owner

- Pending items: 1
- Ready batches: none
- Blocked batches: p1-rollback-localization-owner

### message-owner

- Pending items: 1
- Ready batches: none
- Blocked batches: p1-rollback-message-owner

### platform-owner

- Pending items: 1
- Ready batches: none
- Blocked batches: p1-rollback-platform-owner

### plugin-owner

- Pending items: 1
- Ready batches: none
- Blocked batches: p1-rollback-plugin-owner

### release-owner

- Pending items: 1
- Ready batches: none
- Blocked batches: p3-orchestrator-release-owner

## Batch Cards

### p0-manifest-lumira-ui

- Status: READY
- Scope: P0 manifest -> lumira-ui
- Pending items: 3
- Depends on: none
- Cutover items: evidence-integrity
- Lanes: evidence-integrity
- Expected artifacts: artifacts/ddd/release/evidence-manifest.json, artifacts/ddd/lumira-ui/frontend-smoke.json, artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json, artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json

### p0-docker-release-infra

- Status: READY
- Scope: P0 docker -> release-infra
- Pending items: 4
- Depends on: none
- Cutover items: deployable-images
- Lanes: deployable-image
- Expected artifacts: artifacts/ddd/build/docker-image-evidence.json

### p0-runtime-readiness-release-infra

- Status: READY
- Scope: P0 runtime-readiness -> release-infra
- Pending items: 4
- Depends on: none
- Cutover items: production-equivalence
- Lanes: production-equivalence
- Expected artifacts: artifacts/ddd/readiness/summary.json

### p0-authenticated-performance-release-performance

- Status: READY
- Scope: P0 authenticated-performance -> release-performance
- Pending items: 9
- Depends on: none
- Cutover items: production-equivalence
- Lanes: performance
- Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json, artifacts/ddd/performance/authenticated-runtime-baseline.json, artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json

### p1-ai-runtime-ai

- Status: BLOCKED
- Scope: P1 ai-runtime -> ai
- Pending items: 3
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: runtime-business-acceptance
- Lanes: runtime-acceptance
- Expected artifacts: artifacts/ddd/ai/ai-runtime-drill.json

### p1-rollback-ai-owner

- Status: BLOCKED
- Scope: P1 rollback -> ai-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: rollback-safety
- Lanes: rollback-safety
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

### p1-rollback-auth-owner

- Status: BLOCKED
- Scope: P1 rollback -> auth-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: rollback-safety
- Lanes: rollback-safety
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

### p1-business-e2e-file-owner

- Status: BLOCKED
- Scope: P1 business-e2e -> file-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: runtime-business-acceptance
- Lanes: business-acceptance
- Expected artifacts: artifacts/ddd/file/file-processing-e2e.json

### p1-rollback-file-owner

- Status: BLOCKED
- Scope: P1 rollback -> file-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: rollback-safety
- Lanes: rollback-safety
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

### p1-rollback-iam-owner

- Status: BLOCKED
- Scope: P1 rollback -> iam-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: rollback-safety
- Lanes: rollback-safety
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

### p1-business-e2e-job-owner

- Status: BLOCKED
- Scope: P1 business-e2e -> job-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: runtime-business-acceptance
- Lanes: business-acceptance
- Expected artifacts: artifacts/ddd/jobs/job-e2e-smoke.json

### p1-rollback-job-owner

- Status: BLOCKED
- Scope: P1 rollback -> job-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: rollback-safety
- Lanes: rollback-safety
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

### p1-rollback-localization-owner

- Status: BLOCKED
- Scope: P1 rollback -> localization-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: rollback-safety
- Lanes: rollback-safety
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

### p1-rollback-message-owner

- Status: BLOCKED
- Scope: P1 rollback -> message-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: rollback-safety
- Lanes: rollback-safety
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

### p1-business-e2e-payment-owner

- Status: BLOCKED
- Scope: P1 business-e2e -> payment-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: runtime-business-acceptance
- Lanes: business-acceptance
- Expected artifacts: artifacts/ddd/payment/payment-webhook-e2e.json

### p1-rollback-payment-owner

- Status: BLOCKED
- Scope: P1 rollback -> payment-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: rollback-safety
- Lanes: rollback-safety
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

### p1-rollback-platform-owner

- Status: BLOCKED
- Scope: P1 rollback -> platform-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: rollback-safety
- Lanes: rollback-safety
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

### p1-rollback-plugin-owner

- Status: BLOCKED
- Scope: P1 rollback -> plugin-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Cutover items: rollback-safety
- Lanes: rollback-safety
- Expected artifacts: artifacts/ddd/rollback/rollback-drill.json

### p2-explain-database

- Status: BLOCKED
- Scope: P2 explain -> database
- Pending items: 2
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Cutover items: database-performance
- Lanes: database-performance
- Expected artifacts: tmp/ddd-explain/*.json, artifacts/ddd/release/explain-gate-report.json

### p3-orchestrator-database

- Status: BLOCKED
- Scope: P3 orchestrator -> database
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Cutover items: evidence-integrity
- Lanes: final-verification
- Expected artifacts: artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/release-evidence-gate.json, artifacts/ddd/release/readiness-summary.json

### p3-orchestrator-release-infra

- Status: BLOCKED
- Scope: P3 orchestrator -> release-infra
- Pending items: 2
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Cutover items: evidence-integrity
- Lanes: final-verification
- Expected artifacts: artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/release-evidence-gate.json, artifacts/ddd/release/readiness-summary.json

### p3-orchestrator-release-owner

- Status: BLOCKED
- Scope: P3 orchestrator -> release-owner
- Pending items: 1
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Cutover items: evidence-integrity
- Lanes: final-verification
- Expected artifacts: artifacts/ddd/release/orchestrator-report.json, artifacts/ddd/release/release-evidence-gate.json, artifacts/ddd/release/readiness-summary.json

