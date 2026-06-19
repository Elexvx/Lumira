# DDD Release Performance Baseline Closure

Generated at: 2026-06-19T18:19:45.629Z
Status: BLOCKED
Recommendation: NO_GO_STRICT
No auto waivers: true
Ready to promote: false
Next command: DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh

## Fast Path

- Objective: Capture authenticated hot-path performance from a production-equivalent HTTPS backend, promote it as baseline, then rerun final release gates.
- Blocked until: authenticated-runtime-actual.json is generated from HTTPS non-local deployment evidence and baseline promotion succeeds.
- Commands:
  - `DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh`
  - `DDD_AUTH_PERF_STRICT=true node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`
  - `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## Production Equivalence Required

- HTTPS: true
- Non-local backend: true
- Deployment evidence: true
- No production-equivalence issues: true

## Evidence Checklist

### authenticated-runtime-actual-evidence

Production-equivalent authenticated hot-path runtime artifact captured from the real release candidate backend.

- Required artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
- Required fields:
  - `baseUrl`
  - `checkedAt`
  - `concurrency`
  - `durationMs`
  - `samples`
  - `failed`
  - `p95`
  - `upload.fileId`
  - `upload.elapsedMs`
  - `oneShots[POST /api/v2/auth/session/keepalive]`
  - `perEndpoint[*].samples`
  - `perEndpoint[*].p95`
  - `productionEquivalence.strict`
  - `productionEquivalence.https`
  - `productionEquivalence.localOnly`
  - `productionEquivalence.deploymentEvidence`
  - `productionEquivalence.issues`
- Required env keys:
  - `LUMIRA_BASE_URL`
  - `BASE_URL`
  - `DDD_AUTH_USERNAME`
  - `DDD_AUTH_PASSWORD`
  - `DDD_AUTH_PERF_ENVIRONMENT`
  - `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE`
  - `DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE`
- Acceptance criteria:
  - Base URL is HTTPS and non-local.
  - productionEquivalence.strict=true, https=true, localOnly=false, deploymentEvidence is non-placeholder, and issues is empty.
  - All required authenticated endpoints have positive sample counts and p95 metrics.
  - failed=0 and upload plus keepalive timing evidence succeeded.

### authenticated-runtime-baseline-promotion-evidence

Accepted runtime actual promoted to the release baseline with operator, environment, release candidate, and checksum provenance.

- Required artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`
- Required fields:
  - `status`
  - `sourceFile`
  - `sourceSha256`
  - `outputFile`
  - `sourceArtifact`
  - `sourceEnvironment`
  - `releaseCandidate`
  - `acceptedBy`
  - `baseline.baselineType`
  - `baseline.acceptedAt`
  - `baseline.acceptedBy`
  - `baseline.sourceEnvironment`
  - `baseline.sourceArtifact`
  - `baseline.sourceSha256`
  - `baseline.releaseCandidate`
  - `baseline.evidenceOperator`
- Required env keys:
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_RELEASE_CANDIDATE`
  - `DDD_EVIDENCE_OPERATOR`
- Acceptance criteria:
  - Promotion status is PASS.
  - Baseline sourceSha256 matches the accepted actual artifact.
  - Strict baseline metadata has no placeholder or missing provenance fields.
  - Baseline shape is valid under strict authenticated performance contract.

### baseline-release-gate-acceptance-evidence

Post-promotion release gates rerun with the accepted baseline and no authenticated performance regression blockers.

- Required artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-final-go-no-go.json`
- Required fields:
  - `evidence-manifest.performance.authenticated-runtime-baseline`
  - `readiness-summary.diagnostics.authenticatedPerformance.regressionIssues`
  - `release-final-go-no-go.recommendation`
  - `release-final-go-no-go.cutoverAllowed`
- Required env keys:
  - `DDD_RELEASE_MANIFEST_STRICT`
  - `DDD_FINAL_GO_NO_GO_ENFORCE`
- Acceptance criteria:
  - Evidence manifest includes the authenticated runtime baseline with checksum.
  - Regression issue list is empty or only contains reviewed non-blocking context.
  - Final go/no-go gate has been rerun after baseline promotion.
  - No manual waiver is used for missing production-equivalent performance evidence.

## Blockers

- acceptedAt must be an ISO timestamp
- acceptedBy is required
- authenticated performance actual productionEquivalence.deploymentEvidence is required
- authenticated performance actual productionEquivalence.https must be true for strict release evidence
- authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence
- authenticated performance actual productionEquivalence.strict must be true for strict release evidence
- sourceArtifact is required
- sourceSha256 must be a SHA-256 hex digest
- strict release baseline requires baselineType=authenticated-runtime

## Required Env Keys

- `BASE_URL`
- `DDD_AUTH_PASSWORD`
- `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
- `DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
- `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
- `DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE`
- `DDD_AUTH_PERF_ENVIRONMENT`
- `DDD_AUTH_USERNAME`
- `DDD_EVIDENCE_OPERATOR`
- `DDD_RELEASE_CANDIDATE`
- `DEPLOY_CHECK_BASE_URL`
- `LUMIRA_BASE_URL`

## Commands

- `DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh`
- `DDD_AUTH_PERF_STRICT=true node bin/ddd-authenticated-performance-smoke.mjs`
- `node bin/ddd-promote-performance-baseline.mjs`
- `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
- `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- `node bin/ddd-release-evidence-gate.mjs`
- `node bin/ddd-release-readiness-summary.mjs`
- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

## Expected Artifacts

- `artifacts/ddd/performance/authenticated-runtime-actual.json`
- `artifacts/ddd/performance/authenticated-runtime-baseline.json`
- `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
- `artifacts/ddd/release/evidence-manifest.json`
