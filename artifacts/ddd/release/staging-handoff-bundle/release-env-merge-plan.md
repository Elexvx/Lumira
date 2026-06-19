# DDD Release Env Merge Plan

Status: BLOCKED
Owners: 5
Blockers: 34
Placeholders: 34
Secret keys: 10
Canonical template: `artifacts/ddd/release/release-env-canonical-fill.template.env`
Release env file: `<release-env-file>`

## collect-owner-values

Owner: release-owner

Commands:

- `node scripts/ddd-staging-execution-checklist.mjs --release-env-owner-matrix`
- `node scripts/ddd-staging-execution-checklist.mjs --release-env-next-owner-template`
- `node scripts/ddd-staging-execution-checklist.mjs --owner-packets`

Artifacts:

- `artifacts/ddd/release/staging-handoff-bundle/release-env-next-owner.template.env`
- `artifacts/ddd/release/staging-handoff-bundle/owner-packets/*.blocking-inputs.template.env`

## merge-owner-values

Owner: release-infra

Commands:

- `node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env <release-env-file>`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-alias-sync.mjs`

Artifacts:

- `artifacts/ddd/release/release-env-canonical-fill.template.env`
- `<release-env-file>`

## validate-release-env

Owner: release-infra

Commands:

- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs`
- `node scripts/ddd-release-readiness-summary.mjs`
- `node scripts/ddd-staging-execution-checklist.mjs --rollup`
- `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce`

Artifacts:

- `artifacts/ddd/release/release-env-lint.json`
- `artifacts/ddd/config/release-config-evidence.json`
- `artifacts/ddd/release/readiness-summary.json`

## Safety

- Do not use release-env-next-owner.template.env or any owner-packets/*.template.env file as DDD_RELEASE_ENV_FILE.
- Merge completed values into a permission-safe release env file only.
- Run canonical lint and env-file lint before config evidence.
- Rerun final review only after readiness summary is regenerated from the completed env file.

Next: `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=release-infra`
