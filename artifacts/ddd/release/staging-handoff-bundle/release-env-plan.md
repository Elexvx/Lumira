# DDD P0 Release Env Plan

Status: PASS
Target: `tmp/ddd-dispatch-check-env-init.env`
Target exists: false
Force enabled: false
Current blocker: release env file is cutover-safe

## Commands

### Preflight

- `node bin/ddd-release-env-init.mjs --check`
- `node bin/ddd-staging-execution-checklist.mjs --owner-packets`
- `node bin/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=release-infra`

### Initialize

- `node bin/ddd-release-env-init.mjs`

### Validate

- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-file-lint.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-config-evidence.mjs`
- `node bin/ddd-release-readiness-summary.mjs`
- `node bin/ddd-staging-execution-checklist.mjs --rollup`
- `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce`

## Owner Inputs

| Owner | Blockers | Secret keys | Handoff | Env template |
| --- | ---: | ---: | --- | --- |

## Safety

- Do not commit populated release env files.
- Keep the target owner-only where the filesystem supports chmod 600.
- Use owner packets for redacted key collection; merge values only in a secure secret store or local release runner.
- Rerun final review after lint, config evidence, and readiness summary are regenerated.

Next: `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce`
