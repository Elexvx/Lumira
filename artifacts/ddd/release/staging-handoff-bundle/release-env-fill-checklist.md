# P0 Release Env Fill Checklist

Generated at: 2026-06-19T18:17:45.175Z

Lint status: PASS
Env file: .env.release.local
Primary blockers: 0
Config blocker count: 0

## Required Keys By Group

## Validation Commands

```bash
DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-file-lint.mjs
DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-config-evidence.mjs
node bin/ddd-release-readiness-summary.mjs
node bin/ddd-staging-execution-checklist.mjs --release-env-submission-plan
```

## Acceptance Rule

Do not mark `release-infra:p0-release-env` PASS until release env lint and release config evidence are PASS, and the resulting artifacts are attached to the lane receipt.
