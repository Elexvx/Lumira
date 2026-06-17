# DDD Release Owner Input Receipt

Generated at: 2026-06-17T08:00:49.110Z
Status: PENDING_OWNER_INPUT
Cutover ready: false
Required owner inputs: 34
Owners: 5
Missing criteria: 3

## Criteria

- releaseEnvReadinessStatus: expected=PASS; actual=ADVISORY; met=false
- releaseEnvReadinessBlockers: expected=0; actual=34; met=false
- releaseEnvReadinessPlaceholders: expected=0; actual=34; met=false
- releaseEnvReadinessMissing: expected=0; actual=0; met=true
- configOwnerInputReconciliationStatus: expected=PASS; actual=PASS; met=true
- configOwnerInputReconciliationUnmappedKeys: expected=0; actual=0; met=true

## Owners

- platform-events: ready=false; inputs=9; placeholders=9; missing=0; packet=artifacts/ddd/release/release-env-owner-input-packet/01-platform-events.json
- platform-owners: ready=false; inputs=9; placeholders=9; missing=0; packet=artifacts/ddd/release/release-env-owner-input-packet/02-platform-owners.json
- release-infra: ready=false; inputs=9; placeholders=9; missing=0; packet=artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json
- ai-owner: ready=false; inputs=6; placeholders=6; missing=0; packet=artifacts/ddd/release/release-env-owner-input-packet/04-ai-owner.json
- payment-owner: ready=false; inputs=1; placeholders=1; missing=0; packet=artifacts/ddd/release/release-env-owner-input-packet/05-payment-owner.json

## Validation Commands

- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-alias-sync.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
- `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-readiness-summary.mjs`
- `node scripts/ddd-release-config-owner-input-reconciliation.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts/ddd/release/release-preflight-gate.sh`
- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`

Concrete values are intentionally omitted from this artifact.
