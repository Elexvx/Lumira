# DDD Release Env Merge Plan

Status: PASS
Owners: 0
Blockers: 0
Placeholders: 0
Secret keys: 0
Canonical template: `artifacts/ddd/release/release-env-canonical-fill.template.env`
Release env file: `<release-env-file>`

## collect-owner-values

Owner: release-owner

Commands:

- `node bin/ddd-staging-execution-checklist.mjs --release-env-owner-matrix`
- `node bin/ddd-staging-execution-checklist.mjs --release-env-next-owner-template`
- `node bin/ddd-staging-execution-checklist.mjs --owner-packets`

Artifacts:

- `artifacts/ddd/release/staging-handoff-bundle/release-env-next-owner.template.env`
- `artifacts/ddd/release/staging-handoff-bundle/owner-packets/*.blocking-inputs.template.env`

## merge-owner-values

Owner: release-infra

Commands:

- `node bin/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env <release-env-file>`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-safe-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-provenance-defaults.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-alias-sync.mjs`

Artifacts:

- `artifacts/ddd/release/release-env-canonical-fill.template.env`
- `<release-env-file>`

## validate-release-env

Owner: release-infra

Commands:

- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-file-lint.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-config-evidence.mjs`
- `node bin/ddd-release-readiness-summary.mjs`
- `node bin/ddd-staging-execution-checklist.mjs --rollup`
- `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce`

Artifacts:

- `artifacts/ddd/release/release-env-lint.json`
- `artifacts/ddd/config/release-config-evidence.json`
- `artifacts/ddd/release/readiness-summary.json`

## Safety

- Do not use release-env-next-owner.template.env or any owner-packets/*.template.env file as DDD_RELEASE_ENV_FILE.
- Merge completed values into a permission-safe release env file only.
- Run canonical lint and env-file lint before config evidence.
- Rerun final review only after readiness summary is regenerated from the completed env file.

Next: `node bin/ddd-staging-execution-checklist.mjs --release-env-owner-matrix`
