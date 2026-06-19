# DDD Release Closure Wave Receipts

Generated at: 2026-06-19T18:19:45.629Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
Ready for strict gate rerun: 3
Artifact missing: 1
Content blocked: 0
Missing artifacts: 3

## Wave 1. release-infra / p0-docker-release-infra

- Receipt status: READY_FOR_STRICT_GATE_RERUN
- Expected artifacts: 1
- Present artifacts: 1
- Missing artifacts: 0
- Next check: Rerun strict release gate and readiness summary; this wave's expected artifacts are present.
- Rerun commands:
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## Wave 2. release-infra / p0-runtime-readiness-release-infra

- Receipt status: READY_FOR_STRICT_GATE_RERUN
- Expected artifacts: 1
- Present artifacts: 1
- Missing artifacts: 0
- Next check: Rerun strict release gate and readiness summary; this wave's expected artifacts are present.
- Rerun commands:
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## Wave 3. lumira-ui / p0-manifest-lumira-ui

- Receipt status: ARTIFACT_MISSING
- Expected artifacts: 4
- Present artifacts: 1
- Missing artifacts: 3
- Missing artifact paths:
  - `artifacts/ddd/lumira-ui/frontend-smoke.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json`
- Next check: Run this wave in a production-equivalent environment, then rerun strict release gate and readiness summary.
- Rerun commands:
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## Wave 4. release-performance / p0-authenticated-performance-release-performance

- Receipt status: READY_FOR_STRICT_GATE_RERUN
- Expected artifacts: 3
- Present artifacts: 3
- Missing artifacts: 0
- Next check: Rerun strict release gate and readiness summary; this wave's expected artifacts are present.
- Rerun commands:
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

