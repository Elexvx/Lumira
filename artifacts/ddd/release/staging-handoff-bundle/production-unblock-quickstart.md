# DDD Production Unblock Quickstart

Status: BLOCKED
Final recommendation: NO_GO_STRICT
Cutover allowed: false
Blocked audit items: 5

## Fast Path

1. Fill release env values from `release-env-fill.template.env` into `.env.release.local`.
2. Validate the release env file:

```bash
DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-file-lint.mjs
DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-config-evidence.mjs
```

3. Start with Validate first-wave secure env file: `node bin/ddd-staging-execution-checklist.mjs --next-action-env-check --next-action-env-file=<env-file>`
4. Verify with `node bin/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>`.
5. Continue the parallel workstreams below until every completion signal is satisfied.

## Parallel Workstreams

- first-wave-env: owner=release-infra; command=`node bin/ddd-staging-execution-checklist.mjs --next-action-env-check --next-action-env-file=<env-file>`; verify=`node bin/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>`; done=next-action env receipt contract passes
- lane-completion-receipt: owner=release-owner; command=`node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>`; verify=`node bin/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>`; done=lane completion submission check reports dispatchReady=true

## Final Gate

- `node bin/ddd-staging-execution-checklist.mjs --production-evidence-readiness-enforce` must exit 0.
- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh` must exit 0.
- No manual waiver is allowed while `finalRecommendation` remains `NO_GO_STRICT`.
