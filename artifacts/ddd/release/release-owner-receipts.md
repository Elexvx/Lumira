# DDD Release Owner Receipts

Generated at: 2026-06-18T19:37:26.213Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
Ready for strict gate rerun owners: 0
Content blocked owners: 17
Artifact missing owners: 1
Missing artifacts: 1
Pending actions: 111

## release-infra

- Receipt status: CONTENT_BLOCKED
- Ready batches: p0-docker-release-infra, p0-release-config-release-infra, p0-release-env-lint-release-infra, p0-runtime-readiness-release-infra
- Present artifacts: 4
- Missing artifacts: 0
- Pending actions: 24
- Pending action reasons:
  - [docker] docker-blocker-1: lumira-server: docker build failed after 3 attempt(s) with transient registry/network error status 1
  - [docker] docker-blocker-2: frontend: docker build failed after 3 attempt(s) with transient registry/network error status 1
  - [docker] docker-image-frontend-failed: docker build failed after 3 attempt(s) with transient registry/network error status 1
  - [docker] docker-image-lumira-server-failed: docker build failed after 3 attempt(s) with transient registry/network error status 1
  - [release-config] backend base url: placeholder value is not allowed
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## platform-owners

- Receipt status: CONTENT_BLOCKED
- Ready batches: p0-release-config-platform-owners
- Present artifacts: 1
- Missing artifacts: 0
- Pending actions: 18
- Pending action reasons:
  - [release-config] ai service: placeholder value is not allowed
  - [release-config] ai service: must use HTTPS for production-equivalent evidence
  - [release-config] auth service: placeholder value is not allowed
  - [release-config] auth service: must use HTTPS for production-equivalent evidence
  - [release-config] file service: placeholder value is not allowed
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## platform-events

- Receipt status: CONTENT_BLOCKED
- Ready batches: p0-release-config-platform-events
- Present artifacts: 1
- Missing artifacts: 0
- Pending actions: 17
- Pending action reasons:
  - [release-config] event stream key: placeholder value is not allowed
  - [release-config] job backend url: placeholder value is not allowed
  - [release-config] job backend url: must use HTTPS for production-equivalent evidence
  - [release-config] job file url: placeholder value is not allowed
  - [release-config] job file url: must use HTTPS for production-equivalent evidence
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## ai-owner

- Receipt status: CONTENT_BLOCKED
- Ready batches: p0-release-config-ai-owner
- Present artifacts: 2
- Missing artifacts: 0
- Pending actions: 13
- Pending action reasons:
  - [release-config] file owner url: placeholder value is not allowed
  - [release-config] file owner url: must use HTTPS for production-equivalent evidence
  - [release-config] iam owner url: placeholder value is not allowed
  - [release-config] iam owner url: must use HTTPS for production-equivalent evidence
  - [release-config] owner internal token: placeholder value is not allowed
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## release-performance

- Receipt status: CONTENT_BLOCKED
- Ready batches: p0-authenticated-performance-release-performance
- Present artifacts: 3
- Missing artifacts: 0
- Pending actions: 9
- Pending action reasons:
  - [authenticated-performance] performance-actual-shape-1: authenticated performance actual productionEquivalence.strict must be true for strict release evidence
  - [authenticated-performance] performance-actual-shape-2: authenticated performance actual productionEquivalence.https must be true for strict release evidence
  - [authenticated-performance] performance-actual-shape-3: authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence
  - [authenticated-performance] performance-actual-shape-4: authenticated performance actual productionEquivalence.deploymentEvidence is required
  - [authenticated-performance] performance-baseline-metadata-5: strict release baseline requires baselineType=authenticated-runtime
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## payment-owner

- Receipt status: CONTENT_BLOCKED
- Ready batches: p0-release-config-payment-owner
- Present artifacts: 3
- Missing artifacts: 0
- Pending actions: 4
- Pending action reasons:
  - [business-e2e] payment-webhook-production-equivalence: strict payment webhook E2E requires HTTPS baseUrl evidence; strict payment webhook E2E requires non-local baseUrl, got http://127.0.0.1:8080
  - [release-config] payment public url: placeholder value is not allowed
  - [release-config] payment public url: must use HTTPS for production-equivalent evidence
  - [rollback] Payment: Payment rollback drill is DEFERRED with approved deferral evidence
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## release-owner

- Receipt status: CONTENT_BLOCKED
- Ready batches: p0-manifest-release-owner
- Present artifacts: 4
- Missing artifacts: 0
- Pending actions: 2
- Pending action reasons:
  - [manifest] manifest-missing-no-explain-json-files-in-tmp-ddd-explain: no explain JSON files in tmp\ddd-explain
  - [orchestrator] orchestrator-run-mode: strict release requires run mode report, got plan
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## database

- Receipt status: ARTIFACT_MISSING
- Ready batches: none
- Present artifacts: 4
- Missing artifacts: 1
- Pending actions: 9
- Missing artifact paths:
  - `tmp/ddd-explain/*.json`
- Pending action reasons:
  - [explain] ai-knowledge-index-retry.json: missing required EXPLAIN artifact
  - [explain] message-archive-total.json: missing required EXPLAIN artifact
  - [explain] message-unread-count.json: missing required EXPLAIN artifact
  - [explain] message-visible-list.json: missing required EXPLAIN artifact
  - [explain] platform-outbox-owner-relay-file.json: missing required EXPLAIN artifact
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## ai

- Receipt status: CONTENT_BLOCKED
- Ready batches: none
- Present artifacts: 1
- Missing artifacts: 0
- Pending actions: 3
- Pending action reasons:
  - [ai-runtime] ai-owner-gateway: ownerGateway status=CONFIGURED configuredOwners=0
  - [ai-runtime] ai-provider-runtime: provider status=CONFIGURED remoteConfigured=false
  - [ai-runtime] ai-runtime-base-url: missing production-equivalent AI base URL
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## file-owner

- Receipt status: CONTENT_BLOCKED
- Ready batches: none
- Present artifacts: 2
- Missing artifacts: 0
- Pending actions: 2
- Pending action reasons:
  - [business-e2e] file-processing-production-equivalence: strict file processing E2E requires HTTPS baseUrl evidence; strict file processing E2E requires non-local baseUrl, got http://127.0.0.1:8080
  - [rollback] File: File rollback drill is DEFERRED with approved deferral evidence
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## frontend

- Receipt status: CONTENT_BLOCKED
- Ready batches: none
- Present artifacts: 5
- Missing artifacts: 0
- Pending actions: 2
- Pending action reasons:
  - [frontend-smoke] frontend-deployed-expectation: strict release requires deployed frontend smoke expectation
  - [orchestrator] orchestrator-preflight-frontend-runtime-base-url: missing deployed frontend base URL
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## job-owner

- Receipt status: CONTENT_BLOCKED
- Ready batches: none
- Present artifacts: 2
- Missing artifacts: 0
- Pending actions: 2
- Pending action reasons:
  - [business-e2e] job-e2e-production-equivalence: strict job E2E requires HTTPS baseUrl evidence; strict job E2E requires non-local baseUrl, got http://127.0.0.1:8080
  - [rollback] Job: Job rollback drill is DEFERRED with approved deferral evidence
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## auth-owner

- Receipt status: CONTENT_BLOCKED
- Ready batches: none
- Present artifacts: 1
- Missing artifacts: 0
- Pending actions: 1
- Pending action reasons:
  - [rollback] Auth: Auth rollback drill is DEFERRED with approved deferral evidence
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## iam-owner

- Receipt status: CONTENT_BLOCKED
- Ready batches: none
- Present artifacts: 1
- Missing artifacts: 0
- Pending actions: 1
- Pending action reasons:
  - [rollback] IAM: IAM rollback drill is DEFERRED with approved deferral evidence
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## localization-owner

- Receipt status: CONTENT_BLOCKED
- Ready batches: none
- Present artifacts: 1
- Missing artifacts: 0
- Pending actions: 1
- Pending action reasons:
  - [rollback] Localization: Localization rollback drill is DEFERRED with approved deferral evidence
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## message-owner

- Receipt status: CONTENT_BLOCKED
- Ready batches: none
- Present artifacts: 1
- Missing artifacts: 0
- Pending actions: 1
- Pending action reasons:
  - [rollback] Message: Message rollback drill is DEFERRED with approved deferral evidence
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## platform-owner

- Receipt status: CONTENT_BLOCKED
- Ready batches: none
- Present artifacts: 1
- Missing artifacts: 0
- Pending actions: 1
- Pending action reasons:
  - [rollback] Platform: Platform rollback drill is DEFERRED with approved deferral evidence
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

## plugin-owner

- Receipt status: CONTENT_BLOCKED
- Ready batches: none
- Present artifacts: 1
- Missing artifacts: 0
- Pending actions: 1
- Pending action reasons:
  - [rollback] Plugin: Plugin rollback drill is DEFERRED with approved deferral evidence
- Next check: Rerun strict release gate and readiness summary after all missing artifacts are present.

