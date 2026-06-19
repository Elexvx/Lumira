# DDD Next Action Verification Plan

Status: BLOCKED
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Blocked phases: 1/6
Next command: `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-check --next-action-env-file=<env-file>`

| Phase | Owner | Status | Command | Follow-up | Source |
| --- | --- | --- | --- | --- | --- |
| verify-first-wave-env | release-infra | BLOCKED | `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-check --next-action-env-file=<env-file>` | `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file> --next-action-env-receipt-output=<receipt-file>`<br>`node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>` | `next-action.template.env` |
| verify-release-env | release-infra | PASS | `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs` | none | `release-env-plan.json` |
| verify-docker-images | release-infra | PASS | `node scripts/ddd-docker-build-evidence.mjs --check` | none | `docker-image-plan.json` |
| verify-runtime | release-infra, frontend, ai | PASS | `node scripts/ddd-staging-runtime-check.mjs` | none | `runtime-smoke-plan.json` |
| verify-data-safety | bounded-context owners, database | PASS | `node scripts/ddd-staging-data-safety-check.mjs` | none | `data-safety-owner-plan.json` |
| verify-final-acceptance | release-infra | PASS | `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce` | none | `final-review.json` |

## Safety

- Do not run final review until every phase reports PASS from real staging evidence.
- Keep populated env files outside committed artifacts.
- Use `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-markdown --next-action-env-file=<env-file>` only for human-readable status; submit the JSON receipt written by receiptCommand.
- Use source plans for owner-specific smoke and evidence commands.
