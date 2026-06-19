# DDD P0 Release Env Plan

Status: BLOCKED
Target: `tmp/ddd-dispatch-check-env-init.env`
Target exists: false
Force enabled: false
Current blocker: release env file is not cutover-safe; blockers=34

## Commands

### Preflight

- `node scripts/ddd-release-env-init.mjs --check`
- `node scripts/ddd-staging-execution-checklist.mjs --owner-packets`
- `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=release-infra`

### Initialize

- `node scripts/ddd-release-env-init.mjs`

### Validate

- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs`
- `node scripts/ddd-release-readiness-summary.mjs`
- `node scripts/ddd-staging-execution-checklist.mjs --rollup`
- `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce`

## Owner Inputs

| Owner | Blockers | Secret keys | Handoff | Env template |
| --- | ---: | ---: | --- | --- |
| platform-events | 9 | 3 | `artifacts/ddd/release/release-env-owner-handoff-redacted/01-platform-events.md` | `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=platform-events` |
| platform-owners | 9 | 0 | `artifacts/ddd/release/release-env-owner-handoff-redacted/02-platform-owners.md` | `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=platform-owners` |
| release-infra | 9 | 4 | `artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md` | `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=release-infra` |
| ai-owner | 6 | 2 | `artifacts/ddd/release/release-env-owner-handoff-redacted/04-ai-owner.md` | `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=ai-owner` |
| payment-owner | 1 | 1 | `artifacts/ddd/release/release-env-owner-handoff-redacted/05-payment-owner.md` | `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=payment-owner` |

## Safety

- Do not commit populated release env files.
- Keep the target owner-only where the filesystem supports chmod 600.
- Use owner packets for redacted key collection; merge values only in a secure secret store or local release runner.
- Rerun final review after lint, config evidence, and readiness summary are regenerated.

Next: `node scripts/ddd-release-env-init.mjs --check`
