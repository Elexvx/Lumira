# Production Readiness Execution Plan

## 1. Current Goal

Move the current DDD modular-monolith release candidate from architecture-complete to production-ready.

The release path is:

1. Freeze architecture scope.
2. Pass local code and build gates.
3. Collect staging or production-equivalent evidence.
4. Exercise rollback and migration drills.
5. Run strict release evidence gate.
6. Cut over only when final go/no-go allows it.

## 2. Scope Freeze

Until the first production cutover, avoid broad architecture movement:

- No new bounded context split.
- No package-wide refactor.
- No table ownership reshuffle.
- No API path redesign.

Allowed changes:

- Release blockers.
- Test, deployment, evidence, and rollback fixes.
- Narrow production-readiness bug fixes.

## 3. Local Gates Run On 2026-06-17

Use the local preflight runner for repeatable checks:

```bash
node bin/ddd-production-readiness-preflight.mjs --quick --no-report
```

Both operator entry points have built-in help:

```bash
node bin/ddd-production-readiness-preflight.mjs --help
node bin/ddd-staging-execution-checklist.mjs --help
```

Quick mode skips the heavy backend DDD architecture test slice by default. Include it explicitly only when a long local run is acceptable:

```bash
node bin/ddd-production-readiness-preflight.mjs --quick --include-backend-architecture-tests
```

For static release-contract checks only:

```bash
node bin/ddd-production-readiness-preflight.mjs --static-only
```

For a clean CI/local gate that does not write the JSON report:

```bash
node bin/ddd-production-readiness-preflight.mjs --static-only --no-report
```

Preview the quick gate without running checks:

```bash
node bin/ddd-production-readiness-preflight.mjs --quick --no-report --list
```

Generate the current staging execution checklist from release artifacts:

```bash
node bin/ddd-staging-execution-checklist.mjs
```

Print the short dispatch summary when the release owner only needs the current state and owner routing:

```bash
node bin/ddd-staging-execution-checklist.mjs --dispatch-check
node bin/ddd-staging-execution-checklist.mjs --rollup
node bin/ddd-staging-execution-checklist.mjs --rollup-markdown
node bin/ddd-staging-execution-checklist.mjs --rollup-enforce
node bin/ddd-staging-execution-checklist.mjs --evidence-gaps
node bin/ddd-staging-execution-checklist.mjs --evidence-runbook
node bin/ddd-staging-execution-checklist.mjs --evidence-runbook-markdown
node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance
node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance-markdown
node bin/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report
node bin/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report-markdown
node bin/ddd-staging-execution-checklist.mjs --explain-artifact-plan
node bin/ddd-staging-execution-checklist.mjs --explain-artifact-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --closure-plan
node bin/ddd-staging-execution-checklist.mjs --closure-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --next-action-queue
node bin/ddd-staging-execution-checklist.mjs --next-action-queue-markdown
node bin/ddd-staging-execution-checklist.mjs --owner-lane-matrix
node bin/ddd-staging-execution-checklist.mjs --owner-lane-matrix-markdown
node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template
node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template-markdown
node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>
node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage --lane-completion-receipt-file=<receipt-file>
node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>
node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>
node bin/ddd-staging-execution-checklist.mjs --evidence-closure-board
node bin/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown
node bin/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown --lane-completion-receipt-file=<receipt-file>
node bin/ddd-staging-execution-checklist.mjs --evidence-closure-board-csv
node bin/ddd-staging-execution-checklist.mjs --evidence-closure-board-csv --lane-completion-receipt-file=<receipt-file>
node bin/ddd-staging-execution-checklist.mjs --lane-receipt-fragments
node bin/ddd-staging-execution-checklist.mjs --lane-receipt-fragments-markdown
node bin/ddd-staging-execution-checklist.mjs --lane-receipt-draft
node bin/ddd-staging-execution-checklist.mjs --lane-receipt-draft-markdown
node bin/ddd-staging-execution-checklist.mjs --owner-evidence-intake
node bin/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown
node bin/ddd-staging-execution-checklist.mjs --lane-completion-submission-plan
node bin/ddd-staging-execution-checklist.mjs --lane-completion-submission-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --next-action-env-template
node bin/ddd-staging-execution-checklist.mjs --next-action-env-check --next-action-env-file=<env-file>
node bin/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file>
node bin/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file> --next-action-env-receipt-output=<receipt-file>
node bin/ddd-staging-execution-checklist.mjs --next-action-env-receipt-markdown --next-action-env-file=<env-file>
node bin/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>
node bin/ddd-staging-execution-checklist.mjs --next-action-verification-plan
node bin/ddd-staging-execution-checklist.mjs --next-action-verification-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --operator-progress
node bin/ddd-staging-execution-checklist.mjs --operator-progress-markdown
node bin/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>
node bin/ddd-staging-execution-checklist.mjs --release-owner-daily-brief
node bin/ddd-staging-execution-checklist.mjs --release-owner-daily-brief-markdown
node bin/ddd-staging-execution-checklist.mjs --release-env-plan
node bin/ddd-staging-execution-checklist.mjs --release-env-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --release-env-owner-matrix
node bin/ddd-staging-execution-checklist.mjs --release-env-owner-matrix-markdown
node bin/ddd-staging-execution-checklist.mjs --release-env-next-owner-template
node bin/ddd-staging-execution-checklist.mjs --release-env-merge-plan
node bin/ddd-staging-execution-checklist.mjs --release-env-merge-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --release-env-submission-plan
node bin/ddd-staging-execution-checklist.mjs --release-env-submission-plan-markdown
node bin/ddd-release-env-fill-checklist.mjs --markdown
node bin/ddd-release-env-fill-checklist.mjs --env-template
node bin/ddd-staging-execution-checklist.mjs --docker-image-plan
node bin/ddd-staging-execution-checklist.mjs --docker-image-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --docker-image-submission-plan
node bin/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --runtime-business-plan
node bin/ddd-staging-execution-checklist.mjs --runtime-business-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --runtime-smoke-plan
node bin/ddd-staging-execution-checklist.mjs --runtime-smoke-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan
node bin/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --data-safety-plan
node bin/ddd-staging-execution-checklist.mjs --data-safety-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --data-safety-owner-plan
node bin/ddd-staging-execution-checklist.mjs --data-safety-owner-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --data-safety-submission-plan
node bin/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --cutover-rehearsal-plan
node bin/ddd-staging-execution-checklist.mjs --cutover-rehearsal-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --blocking-inputs
node bin/ddd-staging-execution-checklist.mjs --blocking-inputs-markdown
node bin/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template
node bin/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-plan
node bin/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-plan-markdown
node bin/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-inputs
node bin/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-command
node bin/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-inputs-contract --release-evidence-dispatch-inputs-file=<inputs-file>
node bin/ddd-staging-execution-checklist.mjs --execution-status
node bin/ddd-staging-execution-checklist.mjs --execution-status-markdown
node bin/ddd-staging-execution-checklist.mjs --handoff-summary-markdown
node bin/ddd-staging-execution-checklist.mjs --release-owner-closeout
node bin/ddd-staging-execution-checklist.mjs --release-owner-closeout-markdown
node bin/ddd-staging-execution-checklist.mjs --release-owner-closeout-markdown --lane-completion-receipt-file=<receipt-file>
node bin/ddd-staging-execution-checklist.mjs --production-closeout-status
node bin/ddd-staging-execution-checklist.mjs --production-closeout-status-markdown
cat artifacts/ddd/release/staging-handoff-bundle/production-unblock-quickstart.md
node bin/ddd-staging-execution-checklist.mjs --final-review
node bin/ddd-staging-execution-checklist.mjs --final-review-markdown
node bin/ddd-staging-execution-checklist.mjs --final-review-enforce
node bin/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>
node bin/ddd-staging-execution-checklist.mjs --evidence-env-template
node bin/ddd-staging-execution-checklist.mjs --handoff-bundle
node bin/ddd-staging-execution-checklist.mjs --handoff-bundle-verify
node bin/ddd-staging-execution-checklist.mjs --summary
node bin/ddd-staging-execution-checklist.mjs --commands
```

`--dispatch-check` is read-only JSON output for confirming that expected owners, execution tracks, env-init commands, Docker evidence check commands, runtime staging check commands, and source artifacts are present before creating owner packets. It embeds the read-only env-init, Docker evidence, and runtime staging check results; local `BLOCKED` results can still mean dispatch is ready while external staging evidence must be collected.

`--rollup` is read-only JSON output for release-owner triage. It compresses dispatch check details into six rows: release env, Docker images, runtime/business, rollback, migration, and EXPLAIN, including each row's first blocker and blocking input keys. `--rollup-markdown` renders the same six rows as a Markdown table for release notes or handoff messages. `--rollup-enforce` prints the same JSON and exits non-zero unless every rollup row is `PASS`; use it as a staging gate after evidence env is filled.

When `DDD_RELEASE_ENV_FILE` is set, the release-env row dynamically runs the release env lint check against that file and reports its first real blocker without exposing the file path in the copy-ready command. Without `DDD_RELEASE_ENV_FILE`, the row remains blocked from the existing release packet until owners provide a secure release env file.

`--evidence-gaps` is read-only JSON output for the blocked staging tracks, owner routing, next command, required artifacts, and env keys that still need real environment evidence.

`--evidence-runbook` is read-only JSON output for staging evidence collection by track. It lists owner, reason, setup commands, evidence commands, expected artifacts, env keys, and the next command for every track. `--evidence-runbook-markdown` renders the same runbook as a paste-ready operator checklist.

`--evidence-acceptance` is read-only JSON output for the staging evidence acceptance checklist. It maps each release gate to its owner, acceptance command, expected artifacts, artifact present/missing checks, env keys, current blocker, blocking input keys, and pass/fail acceptance state. `--evidence-acceptance-markdown` renders the same checklist for release review meetings after staging evidence has been collected.

`--evidence-artifact-gap-report` is read-only JSON output for the missing evidence artifact reverse index. It groups expected artifacts by artifact path, shows whether each is present, lists the dependent gates, original owners, dispatch owners, acceptance commands, and evidence commands, and highlights missing artifacts first. `--evidence-artifact-gap-report-markdown` renders the same report for release-owner handoff; use it when an artifact exists in multiple gates, such as EXPLAIN output shared by migration and explain acceptance.

`--explain-artifact-plan` is read-only JSON output for closing the current EXPLAIN artifact gap. It turns the missing `tmp/ddd-explain/*.json` artifact into a focused owner plan with dispatch owners, source owners, dependent gates, required inputs, redacted env skeleton, command sequence, expected artifacts, and pass criteria. `--explain-artifact-plan-markdown` renders the same plan for release-owner handoff, CI summaries, and the manual release evidence workflow.

`--closure-plan` is read-only JSON output for release-owner scheduling. It turns the current blocked gates into P0/P1/P2 phases, owner routing, parallel groups, ETA bands, top blocking inputs, and final verification commands. `--closure-plan-markdown` renders the same critical path as a paste-ready planning note; today it reports a fast path of 0.5-1.5 days with staging access and owner evidence ready, or 1-3 days when deployment, Docker, database, or approval evidence must be produced.

`--next-action-queue` is read-only JSON output for the immediate staging owner queue. It compresses the release-env, Docker image, runtime smoke, data safety, and final review lanes into the next command, source plan, owner, artifacts, blocking inputs, owner-scoped missing evidence artifact paths, artifact-plan commands, and parallel-now lanes. `--next-action-queue-markdown` is the first paste-ready view to send to release owners before they drill into the detailed plans; for the current data-safety lane it surfaces `tmp/ddd-explain/*.json` and the `--explain-artifact-plan-markdown` route directly in the queue.

`--owner-lane-matrix` is read-only JSON output for owner-to-lane dispatch. It groups queue lanes by dispatch owner and shows acceptance commands, expected artifacts, missing artifacts, and the next command per lane. `--owner-lane-matrix-markdown` renders the same matrix for standups and handoffs.

`--lane-completion-receipt-template` prints a redacted owner lane completion receipt template. Owners fill the receipt with PASS/BLOCKED lane results after running each lane acceptance command and listing expected artifacts in `providedArtifacts`; each PASS lane must also include `completedAt` and `completedBy`, and each owner:lane key may appear only once. The template itself starts as BLOCKED and is not completion evidence. The JSON template includes `submissionFlow`, and `--lane-completion-receipt-template-markdown` renders `## Submission Flow` with the contract, coverage, base64, operator-progress-with-receipt, and final-review-with-receipt command route. `--lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>` validates that the submitted receipt is redacted, structurally complete, internally consistent, contains unique owner:lane keys, and includes PASS-lane audit fields. Its `summary` reports `passLaneCount`, `blockedLaneCount`, `duplicateLaneKeys`, and `passLaneKeysMissingAudit` for CI and release-owner dashboards, but it does not prove full release coverage by itself.

`--lane-completion-receipt-coverage --lane-completion-receipt-file=<receipt-file>` validates that a submitted lane receipt covers every expected owner:lane pair from the owner-lane matrix. `--lane-completion-receipt-coverage-markdown` renders the same result and must show `Coverage: 5/5` before final review. It also renders `## Contract Summary` with PASS/BLOCKED lane counts, duplicate owner:lane keys, and PASS lanes missing audit fields. Partial receipts can pass the receipt contract and still fail coverage; the coverage output lists missing and unexpected owner:lane keys.

`--lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>` prints the single-line value for the formal release workflow `lane_completion_receipt_base64` dispatch input. It first requires the receipt contract and full lane coverage to PASS, then prints only base64 to stdout; partial receipts fail and do not produce a workflow input value.

`--evidence-closure-board` is read-only JSON output for the owner lane evidence closure board. It combines the owner-lane matrix, optional lane completion receipt contract, receipt coverage, lane receipt status, expected artifacts, provided artifacts, currently missing artifacts, acceptance commands, source plans, and next commands into one lane-by-lane closeout view. `--evidence-closure-board-markdown` renders the same board for release meetings and owner follow-up; `--evidence-closure-board-csv` renders the same lane rows for spreadsheets or issue-tracker import. With `--lane-completion-receipt-file=<receipt-file>`, both rendered formats show whether each submitted owner lane is closed or still BLOCKED before final review.

`--lane-receipt-fragments` is read-only JSON output for assembling the formal owner lane completion receipt. It indexes the five current receipt fragments from release env, Docker image, runtime/business, data safety, and final review lanes, keeps the output redacted, and includes the validation/base64/final-review command route. `--lane-receipt-fragments-markdown` renders the same 5-lane receipt assembly skeleton for release-owner handoff before the final submitted receipt is produced.

`--lane-receipt-draft` is read-only JSON output for the redacted lane completion receipt draft assembled from the current receipt fragments. It preserves owner/lane keys, acceptance commands, expected/provided artifact paths, missing artifacts, and audit placeholders in the same shape consumed by `--lane-completion-receipt-contract`. `--lane-receipt-draft-markdown` renders the draft summary and validation commands for release-owner review.

`--owner-evidence-intake` is read-only JSON output for owner-scoped evidence collection. It groups each dispatch owner with its owner packet, env template, lane commands, blocking inputs, missing artifacts, receipt fragments, and submission commands, so the release owner can hand each owner one complete intake checklist. `--owner-evidence-intake-markdown` renders the same owner evidence intake as a paste-ready handoff note; use `--owner=<owner>` to generate a single-owner version.

`--lane-completion-submission-plan` is read-only JSON output for the owner receipt submission route. It lists every expected owner:lane key, lane acceptance commands, expected and currently missing artifacts, pass criteria, the formal release workflow inputs, the decoded submitted receipt path, and the exact validation/base64/final-review commands. `--lane-completion-submission-plan-markdown` renders the same route for release-owner handoff before a submitted receipt exists.

`--next-action-env-template` prints a focused `.env` skeleton for the immediate staging owner queue. It groups the first-wave release-env, Docker, runtime, and data-safety inputs by lane and uses placeholders only; populate values only in an approved secret store or permission-safe runner.

`--next-action-env-check` is read-only JSON output for validating a populated next-action env file before owners run heavier checks. Pass `--next-action-env-file=<env-file>` or set `DDD_NEXT_ACTION_ENV_FILE`; it blocks on missing files, duplicate keys, unresolved placeholders, non-HTTPS staging URLs, and required boolean flags that are not `true`.

`--next-action-env-receipt` is read-only JSON output for a redacted receipt after validating the populated next-action env file. It includes status, env file SHA-256, key counts, selected key names, lane pass counts, pass criteria, and issues, but never writes env values. Add `--next-action-env-receipt-output=<receipt-file>` to write the JSON receipt and immediately validate it with the receipt contract. `--next-action-env-receipt-markdown` renders the same receipt for release-owner handoff and CI summaries. `--next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>` validates a receipt before it is attached as owner evidence, rejecting invalid JSON, non-redacted receipts, missing SHA-256 on PASS receipts, duplicate keys, failed PASS lane counts, URLs, env assignments with values, and secret-like key names.

`--next-action-verification-plan` is read-only JSON output for the post-env-check verification sequence. It orders env validation, release-env lint/config evidence, Docker image evidence, runtime checks, data-safety checks, and final review into one operator route. `--next-action-verification-plan-markdown` renders the same route for CI summaries and release meetings.

`--operator-progress` is read-only JSON output for day-by-day release-owner progress tracking. It combines the first-wave env check, optional first-wave env receipt contract, optional lane completion receipt coverage, handoff bundle manifest verification, post-env verification route, final review, evidence artifact present/missing totals, missing evidence by owner, top blocking inputs, and `laneRoutes` into one status object. Pass `--next-action-env-receipt-file=<receipt-file>` or set `DDD_NEXT_ACTION_ENV_RECEIPT_FILE` after first-wave env receipt generation so the progress view can show whether that receipt passes contract. Pass `--lane-completion-receipt-file=<receipt-file>` or set `DDD_LANE_COMPLETION_RECEIPT_FILE` after owner lane receipt submission so the progress view can show `Lane receipt coverage: 5/5`; partial receipts are marked BLOCKED and list missing lanes. `--operator-progress-markdown` renders the same progress view for CI summaries and release meetings, including `## Lane Routes` with owner, status, source plan, next command, acceptance commands, and missing artifacts.

`--release-owner-daily-brief` is read-only JSON output for the release owner daily standup. It turns final review, operator progress, owner packets, missing artifacts, blocking inputs, evidence acceptance commands, and `laneRoutes` into a prioritized owner action list. `--release-owner-daily-brief-markdown` renders the same brief for GitHub Step Summary and daily handoff notes, including `## Lane Routes`; it is intentionally action-oriented and remains BLOCKED until the same cutover gates pass.

`--release-env-plan` is read-only JSON output for the P0 release-env lane. It combines the env initializer check, owner handoff rows, owner-scoped env template commands, lint/config/readiness validation commands, and safety notes into one operator view. `--release-env-plan-markdown` renders the same sequence for the release meeting before owners start filling the secure env target.

`--release-env-owner-matrix` is read-only JSON output for owner-scoped release env collection. It summarizes blockers, placeholders, secret-key counts, keys, handoff paths, and owner template commands per owner without exposing values. `--release-env-owner-matrix-markdown` renders the same matrix for release meetings and CI summaries.

`--release-env-next-owner-template` prints the focused env template for the current top release-env owner. It is read-only and uses placeholders only; the handoff bundle also writes this as `release-env-next-owner.template.env` so release-infra can start from the first required owner file without searching `owner-packets/`.

`--release-env-merge-plan` is read-only JSON output for the owner-value merge and validation sequence after P0 collection. It lists owner-template merge, canonical merge, safe defaults, provenance defaults, alias sync, canonical lint, env-file lint, config evidence, readiness regeneration, and final review commands without reading or printing secret values. `--release-env-merge-plan-markdown` renders the same sequence for release-infra.

`--release-env-submission-plan` is read-only JSON output for the release-env owner submission route. It combines the owner matrix, merge plan, redacted first-wave env receipt commands, a `release-infra:p0-release-env` lane receipt fragment, final validation commands, and pass criteria into one artifact without exposing values. `--release-env-submission-plan-markdown` renders the same route for CI summaries and release-infra handoff.

`ddd-release-env-fill-checklist.mjs --markdown` renders the current P0 release-env blocker keys grouped by runtime, database, security, evidence, AI, jobs, and other. `ddd-release-env-fill-checklist.mjs --env-template` renders the same blocker set as a placeholder-only `.env` fill template; the staging handoff bundle writes this as `release-env-fill.template.env`, and operators copy those values into `.env.release.local` before running release env lint/config evidence checks. Secret-like values stay as secret references or placeholders and must not be committed.

`--docker-image-plan` is read-only JSON output for P0 Docker image evidence. It combines the Docker readiness probe, static Dockerfile checks, Docker-enabled build path, existing-image inspect path, required inputs, remediation actions, and final validation commands. `--docker-image-plan-markdown` renders the same plan for release-infra or the Docker-enabled CI runner.

`--docker-image-submission-plan` is read-only JSON output for the release-infra Docker evidence submission route. It turns the Docker image plan into two operator-ready modes, Docker-enabled runner build or existing-image inspect, with required workflow inputs, prerequisites, expected `artifacts/ddd/build/docker-image-evidence.json`, a `release-infra:p0-docker-images` lane receipt fragment, validation commands, and pass criteria. `--docker-image-submission-plan-markdown` renders the same route for CI summaries and the formal release evidence workflow.

`--runtime-business-plan` is read-only JSON output for the P1 runtime/business lane. It combines staging URL checks, deployment evidence keys, expectation flags, owner-routed smoke commands, expected artifacts, and final validation commands into one operator view. `--runtime-business-plan-markdown` renders the same sequence for coordinating release-infra, lumira-ui, AI, file, job, payment, and performance owners.

`--runtime-smoke-plan` is read-only JSON output for the P1 runtime smoke execution route. It expands the runtime/business lane into owner-phased deployment evidence, AI runtime, authenticated performance, lumira-ui, file, job, payment, and final acceptance phases with dependencies, required inputs, commands, and artifacts. `--runtime-smoke-plan-markdown` renders the same route for release meetings and CI summaries.

`--runtime-business-submission-plan` is read-only JSON output for the P1 runtime/business owner submission route. It packages the deployment evidence phase, parallel owner smoke submissions, expected artifacts, a `release-infra:p1-runtime-business` lane receipt fragment, final validation commands, and pass criteria into one release-owner handoff. `--runtime-business-submission-plan-markdown` renders the same route for CI summaries and the formal release evidence workflow.

`--data-safety-plan` is read-only JSON output for rollback, migration, and EXPLAIN evidence. It combines data safety precheck output, track owners, required inputs, evidence commands, expected artifacts, and final validation commands. `--data-safety-plan-markdown` renders the same plan for coordinating rollback drill, migration drill, and database EXPLAIN owners before final review.

`--data-safety-owner-plan` is read-only JSON output for the rollback, migration, and EXPLAIN execution route. It expands the data safety lane into owner-phased rollback evidence/deferral, fresh migration, upgrade migration, EXPLAIN collection, EXPLAIN gate, and final acceptance phases with dependencies, required inputs, commands, and artifacts. `--data-safety-owner-plan-markdown` renders the same route for database and bounded-context owner coordination.

`--data-safety-submission-plan` is read-only JSON output for rollback, migration, and EXPLAIN owner evidence submission. It combines bounded-context rollback submission, database migration and EXPLAIN submission, focused EXPLAIN artifact env template, expected artifacts, validation commands, a `platform-owners:p1-p2-data-safety` lane receipt fragment, and pass criteria into one handoff. `--data-safety-submission-plan-markdown` renders the same route for CI summaries and the formal release evidence workflow; owners keep the fragment BLOCKED until validation passes, then submit it with `status=PASS`, non-empty `providedArtifacts`, empty `missingArtifacts`, `completedAt`, and `completedBy`.

`--cutover-rehearsal-plan` is read-only JSON output for the ordered staging cutover rehearsal. It stitches the release-env plan, Docker image evidence, runtime/business smokes, rollback drill, migration drill, EXPLAIN evidence, handoff bundle verification, and strict final gate into one phase list with owners, dependencies, first commands, blockers, artifacts, and validation commands. `--cutover-rehearsal-plan-markdown` renders the same route for the release meeting.

`--blocking-inputs` is read-only JSON output for the current blocking input reverse index. It groups every missing input key by gate, owner, track, first blocker, and next command, so release-infra can prepare the staging env and secret-store checklist from one machine-readable view. `--blocking-inputs-markdown` renders the same index for handoff notes.

`--blocking-inputs-env-template` renders a focused `.env` skeleton from the current blocking input reverse index. It is the fastest way to convert the current blocked state into a secure staging env fill checklist, and it should still be populated only in an approved secret store or local permission-safe release runner.

Add `--owner=<owner>` to `--blocking-inputs`, `--blocking-inputs-markdown`, or `--blocking-inputs-env-template` to produce an owner-scoped view, such as `--owner=release-infra`, before sending work to a specific owner.

`--release-evidence-dispatch-plan` is read-only JSON output for the formal `.github/workflows/ddd-release-evidence.yml` manual dispatch inputs. It maps current blockers to workflow inputs such as `backend_base_url`, `frontend_base_url`, remote AI/lumira-ui booleans, and `lane_completion_receipt_base64`, then lists the commands that must pass before using `mode=run`. `--release-evidence-dispatch-plan-markdown` renders the same checklist for GitHub Step Summary and release-owner handoff.

`--release-evidence-dispatch-inputs` renders the same workflow dispatch state as a JSON object with a `payload` field and blocked-input notes, so release owners can inspect or transform the exact `workflow_dispatch` values without reading Markdown. `--release-evidence-dispatch-command` renders a paste-ready `gh workflow run ddd-release-evidence.yml -f ...` command template using the current placeholders.

`--release-evidence-dispatch-inputs-contract --release-evidence-dispatch-inputs-file=<inputs-file>` validates a filled workflow dispatch JSON payload before triggering GitHub Actions. It accepts either the wrapper shape from `--release-evidence-dispatch-inputs` or a raw payload object, rejects missing inputs, placeholder URLs, non-HTTPS backend/lumira-ui URLs, invalid booleans, invalid artifact age, and missing `lane_completion_receipt_file`/`lane_completion_receipt_base64`.

`--execution-status` is read-only JSON output for the current staging execution state. It combines the readiness rollup, blocked evidence gaps, handoff bundle manifest verification, laneRoutes, and the next release-owner command. `--execution-status-markdown` renders the same status as a paste-ready release update with `## Lane Routes`.

`--handoff-summary-markdown` is the CI-ready Markdown summary. It renders the handoff artifact name, readiness rollup, and execution status in one output stream; CI appends it to `$GITHUB_STEP_SUMMARY` before the final review.

`--release-owner-closeout` is read-only JSON output for the single-page release-owner closeout. It combines final recommendation, cutover readiness, handoff bundle verification, accepted/blocked gate counts, lane receipt coverage, evidence closure, immediate next lane, blocking gates, and the required command sequence. `--release-owner-closeout-markdown` renders the same page for ticket text and GitHub Step Summary before detailed closure plans.

`--production-closeout-status` is read-only JSON output for answering whether production can close and roughly how long the remaining path is. It derives its status from final review, operator progress, cutover rehearsal, and daily owner priorities, then reports the current ETA band, next owner action, blocked stages, blocked phases, lane receipt coverage, evidence artifact counts, and required pre-production checks. `--production-closeout-status-markdown` renders the same concise page as the first status view in the handoff bundle; it does not override `NO_GO_STRICT` or act as a waiver.

`production-unblock-quickstart.md` is the one-page fast path in the staging handoff bundle. It starts from `release-env-fill.template.env`, validates `.env.release.local`, creates and verifies the first-wave env receipt, initializes and verifies the lane completion receipt, routes owner evidence gaps, and ends with `--production-evidence-readiness-enforce` plus the strict final go/no-go gate. It is intentionally shorter than `production-unblock-plan.md`; use it as the first screen for handoff, then drill into the detailed plan when a workstream needs context.

`bin/ddd-production-unblock-attempt.mjs` is a local dry-run wrapper for the quickstart path. It copies the handoff bundle env template into `artifacts/ddd/release/production-unblock-attempt/release-env.local.scaffold.env`, runs the env lint and next-action receipt checks against that scaffold, drafts a redacted lane completion receipt from available fragments, and records the strict readiness result. It writes attempt-only files, including `release-env-lint.attempt.json`, inside `artifacts/ddd/release/production-unblock-attempt/` and must not overwrite canonical release evidence such as `artifacts/ddd/release/release-env-lint.json`. A BLOCKED/NO_GO_STRICT result is expected until real release values and owner evidence are supplied.

`--final-review` is read-only JSON output for the release-owner cutover review. It combines handoff bundle integrity, owner dispatch template completeness, owner dispatch lane routes, lane completion receipt contract and full coverage, evidence closure board closed/open lane counts, evidence acceptance, cutover allowance, top blocking inputs, a `release-infra:final-review` lane receipt fragment, and the next command into one final go/no-go checklist. `--final-review-markdown` renders the same review as a paste-ready release meeting note with `## Evidence Closure`, `## Owner Lane Routes`, and `## Lane Receipt Fragment`. `--final-review-enforce` prints the JSON review and exits non-zero unless `cutoverReady=true`, so release automation can block cutover without parsing Markdown. Use `--final-review-enforce --lane-completion-receipt-file=<receipt-file>` for the final review path; the receipt item only passes when the receipt contract passes, receiptStatus is PASS, and coverage is `5/5`.

The generated final gate `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh` also runs `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce` before printing cutover approval. This keeps the historical final packet checks intact while preventing a strict GO unless staging evidence acceptance, owner templates, lane receipt coverage, bundle integrity, and cutover allowance all agree. `DDD_STAGING_FINAL_REVIEW_ENFORCE=0` is reserved for packet-only contract tests and should not be used for production cutover.

`--evidence-env-template` prints a focused staging evidence env template for the current rollup blockers. It is narrower than `release-env-missing.template.env` and is meant for CI secret/env preparation, not for committing populated values.

`--handoff-bundle` writes a compact release-owner bundle with `README.md`, `rollup.json`, `rollup.md`, `handoff-summary.md`, `execution-status.json`, `execution-status.md`, `final-review.json`, `final-review.md`, `release-owner-closeout.json`, `release-owner-closeout.md`, `production-unblock-quickstart.md`, `operator-progress.json`, `operator-progress.md`, `daily-brief.json`, `daily-brief.md`, `closure-plan.json`, `closure-plan.md`, `next-action-queue.json`, `next-action-queue.md`, `owner-lane-matrix.json`, `owner-lane-matrix.md`, `lane-completion-receipt.template.json`, `lane-completion-receipt.template.md`, `lane-completion-receipt.coverage.json`, `lane-completion-receipt.coverage.md`, `evidence-closure-board.json`, `evidence-closure-board.md`, `evidence-closure-board.csv`, `lane-receipt-fragments.json`, `lane-receipt-fragments.md`, `lane-receipt-draft.json`, `lane-receipt-draft.md`, `owner-evidence-intake.json`, `owner-evidence-intake.md`, `lane-completion-submission-plan.json`, `lane-completion-submission-plan.md`, `next-action.template.env`, `next-action-env-receipt.sample.json`, `next-action-env-receipt.sample.md`, `next-action-verification-plan.json`, `next-action-verification-plan.md`, `release-env-plan.json`, `release-env-plan.md`, `release-env-owner-matrix.json`, `release-env-owner-matrix.md`, `release-env-next-owner.template.env`, `release-env-fill.template.env`, `release-env-merge-plan.json`, `release-env-merge-plan.md`, `release-env-submission-plan.json`, `release-env-submission-plan.md`, `docker-image-plan.json`, `docker-image-plan.md`, `docker-image-submission-plan.json`, `docker-image-submission-plan.md`, `runtime-business-plan.json`, `runtime-business-plan.md`, `runtime-smoke-plan.json`, `runtime-smoke-plan.md`, `runtime-business-submission-plan.json`, `runtime-business-submission-plan.md`, `data-safety-plan.json`, `data-safety-plan.md`, `data-safety-owner-plan.json`, `data-safety-owner-plan.md`, `data-safety-submission-plan.json`, `data-safety-submission-plan.md`, `cutover-rehearsal-plan.json`, `cutover-rehearsal-plan.md`, `evidence-gaps.json`, `evidence-runbook.json`, `evidence-runbook.md`, `evidence-acceptance.json`, `evidence-acceptance.md`, `evidence-artifact-gaps.json`, `evidence-artifact-gaps.md`, `explain-artifact-plan.json`, `explain-artifact-plan.md`, `blocking-inputs.json`, `blocking-inputs.md`, `blocking-inputs.template.env`, `release-evidence-dispatch-plan.json`, `release-evidence-dispatch-plan.md`, `release-evidence-dispatch-inputs.json`, `release-evidence-dispatch-command.sh`, `evidence-env.template.env`, `commands.txt`, `owner-dispatch.json`, `owner-packets/`, and `manifest.json` with file sizes and SHA-256 checksums. `release-owner-closeout.md` is the single-page closeout entry for handoff or ticket text: it shows NO_GO/GO state, gate counts, lane receipt coverage, evidence closure, immediate next lane, blocking gates, and the required final command sequence. The bundle README starts with release-owner quick-start status views and a `## Status Views` section that points owners to production unblock quickstart, daily brief, operator progress, execution status, final review lane-route sections, the owner evidence intake, the 5-lane receipt assembly index, the redacted receipt draft, and formal workflow dispatch inputs. The initial lane receipt coverage files are expected to be BLOCKED until owners submit a redacted receipt that covers all 5 lanes. Owner packets are emitted as Markdown, JSON, and per-owner `*.blocking-inputs.template.env` files so humans can read them, release bots can route them, and each owner can fill only the env inputs they own. Each owner packet also carries owner-scoped missing evidence artifacts from the current acceptance check, queue lanes, submission routes, expected artifacts, currently missing artifacts, and owner completion receipt commands, so release owners can forward a single packet without requiring the owner to inspect global progress. Use `DDD_STAGING_HANDOFF_BUNDLE_DIR` to choose a temporary or artifact output directory. `--handoff-bundle-verify` is read-only JSON output that validates the existing bundle against `manifest.json`, required Markdown markers, non-empty `laneRoutes` in daily brief/operator progress/execution status, final-review owner lane routes, release-owner closeout immediate next lane and required command sequence markers, receipt-template `submissionFlow`, lane receipt fragment JSON lane count and required owner:lane keys, lane receipt fragment Markdown title and receipt skeleton, lane receipt draft count and redaction state, owner evidence intake owner count and owner row shape, release evidence dispatch plan Markdown and JSON shape, release evidence dispatch payload and command templates, evidence closure board Markdown title and lane table, evidence closure board CSV header and lane row count, evidence closure board JSON lane count alignment with `owner-lane-matrix.json`, production unblock quickstart fast-path and final-gate markers, release-env fill template markers, owner-dispatch lane arrays with `laneCount`, `command`, and `sourcePlan`, owner packet top-level JSON summary fields including `laneCount`, `nextCommand`, `missingEvidenceArtifactCount`, and `blockingInputCount`, owner packet submission-route markers including `Expected artifacts:` and `Currently missing artifacts:`, owner packet lane fragments for each lane name, source plan, next command, expected artifact, and missing artifact, and owner packet JSON `queueLanes` field alignment with `owner-dispatch.json` for lane order, status, command, source plan, acceptance commands, expected artifacts, and missing artifacts before sharing it.

CI also generates and verifies this bundle in the `ddd-gates` job, appends `--handoff-summary-markdown`, `--production-closeout-status-markdown`, `production-unblock-quickstart.md`, `--release-owner-closeout-markdown`, `--closure-plan-markdown`, `--evidence-artifact-gap-report-markdown`, `--explain-artifact-plan-markdown`, `--next-action-queue-markdown`, `--owner-lane-matrix-markdown`, `--lane-completion-receipt-template-markdown`, `--lane-completion-receipt-coverage-markdown`, `--evidence-closure-board-markdown`, `--lane-receipt-fragments-markdown`, `--lane-receipt-draft-markdown`, `--lane-completion-submission-plan-markdown`, `--release-evidence-dispatch-plan-markdown`, `--next-action-env-template`, `--next-action-env-receipt-markdown`, `--next-action-verification-plan-markdown`, `--operator-progress-markdown`, `--release-owner-daily-brief-markdown`, `--owner-evidence-intake-markdown`, `--release-env-plan-markdown`, `--release-env-owner-matrix-markdown`, `--release-env-merge-plan-markdown`, `--release-env-submission-plan-markdown`, `ddd-release-env-fill-checklist.mjs --markdown`, `ddd-release-env-fill-checklist.mjs --env-template`, `--docker-image-plan-markdown`, `--docker-image-submission-plan-markdown`, `--runtime-business-plan-markdown`, `--runtime-smoke-plan-markdown`, `--runtime-business-submission-plan-markdown`, `--data-safety-plan-markdown`, `--data-safety-owner-plan-markdown`, `--data-safety-submission-plan-markdown`, `--cutover-rehearsal-plan-markdown`, and `--final-review-markdown` to the GitHub Step Summary, and uploads it as the `ddd-staging-handoff-bundle` artifact, so release-infra can download the latest PR/main handoff without relying on a developer workstation. The upload fails if the bundle is missing and keeps the artifact for 14 days.

The formal manual release workflow `.github/workflows/ddd-release-evidence.yml` also generates and verifies the same staging handoff bundle after preflight capture and before the final manifest refresh/upload. Because that workflow uploads `artifacts/ddd/**`, the bundle is included in the `ddd-release-evidence-<environment>-<run_number>` artifact alongside release evidence. Its optional `lane_completion_receipt_file` dispatch input is copied to `DDD_LANE_COMPLETION_RECEIPT_FILE`, and its optional `lane_completion_receipt_base64` dispatch input is decoded to `artifacts/ddd/release/lane-completion-receipt.submitted.json` and takes precedence over the file path. The decoded submitted receipt lives beside the handoff bundle, not inside `artifacts/ddd/release/staging-handoff-bundle`, so the bundle manifest remains a closed inventory of bundle-owned files while the submitted receipt is still uploaded with the release evidence artifact. Generate that input with `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>` after the receipt shows `Coverage: 5/5`. This lets release owners paste a base64-encoded redacted receipt into the manual workflow without committing or uploading a separate file. In `mode=run`, the workflow writes the actual `workflow_dispatch` input values to a runner-local temporary JSON file and runs `--release-evidence-dispatch-inputs-contract` before preflight evidence starts, so placeholder URLs, non-HTTPS runtime URLs, invalid booleans, invalid freshness windows, or missing lane receipts fail before heavier evidence collection. `--production-closeout-status-markdown`, `--release-owner-closeout-markdown`, `--lane-completion-receipt-coverage-markdown`, `--evidence-closure-board-markdown`, `--lane-receipt-fragments-markdown`, `--lane-receipt-draft-markdown`, `--lane-completion-submission-plan-markdown`, `--release-evidence-dispatch-plan-markdown`, `--evidence-artifact-gap-report-markdown`, `--explain-artifact-plan-markdown`, `--operator-progress-markdown`, `--release-owner-daily-brief-markdown`, `--owner-evidence-intake-markdown`, `--release-env-submission-plan-markdown`, `--docker-image-submission-plan-markdown`, `--runtime-business-submission-plan-markdown`, `--data-safety-submission-plan-markdown`, `--final-review-markdown`, and the strict final gate all evaluate the same redacted owner receipt when one is supplied. When either receipt input is non-empty, the workflow also runs `--lane-completion-receipt-contract` and `--lane-completion-receipt-coverage` as a hard validation step, so a malformed, partial, or missing receipt path fails the release evidence run before final summary. Its GitHub Step Summary appends `--handoff-summary-markdown`, `--production-closeout-status-markdown`, `--release-owner-closeout-markdown`, `--lane-completion-receipt-coverage-markdown`, `--evidence-closure-board-markdown`, `--lane-receipt-fragments-markdown`, `--lane-receipt-draft-markdown`, `--lane-completion-submission-plan-markdown`, `--release-evidence-dispatch-plan-markdown`, `--evidence-artifact-gap-report-markdown`, `--explain-artifact-plan-markdown`, `--operator-progress-markdown`, `--release-owner-daily-brief-markdown`, `--owner-evidence-intake-markdown`, `--release-env-submission-plan-markdown`, `--docker-image-submission-plan-markdown`, `--runtime-business-submission-plan-markdown`, `--data-safety-submission-plan-markdown`, and `--final-review-markdown`, so the release owner can see the production closeout ETA, current `Coverage: 0/5` or `Coverage: 5/5`, owner-lane closure board, 5-lane receipt assembly skeleton, redacted receipt draft, owner evidence intake, receipt submission route, workflow dispatch inputs, artifact gaps, EXPLAIN collection plan, release-env submission route, Docker evidence submission route, runtime/business owner submission route, data-safety submission route, operator state, daily owner actions, and final go/no-go result directly from the release evidence run. The release evidence summary keeps handoff summary, production closeout status, release-owner closeout, lane receipt coverage, closure board, receipt fragments, receipt draft, owner evidence intake, operator progress, daily brief, dispatch inputs, and final review visible even when the EXPLAIN artifact plan is only used as the focused database follow-up section.

`--commands` is read-only plain text output for copying the recommended staging preparation command sequence.

`node bin/ddd-staging-runtime-check.mjs` is read-only JSON output for P1 runtime/business preconditions: HTTPS backend and lumira-ui staging URLs, deployment evidence, and remote AI/lumira-ui expectation flags. It does not call the application or write smoke artifacts.

`node bin/ddd-staging-data-safety-check.mjs` is read-only JSON output for rollback, migration, and EXPLAIN preconditions. It does not write rollback or migration handoff files; the existing `DDD_ROLLBACK_DRILL_CHECK_ENV=true` and `DDD_MIGRATION_CHECK_ENV=true` commands still produce those handoffs when operators are ready.

Initialize the local secure env file before owners start filling real values:

```bash
node bin/ddd-release-env-init.mjs --check
node bin/ddd-release-env-init.mjs
node bin/ddd-docker-build-evidence.mjs --check
node bin/ddd-staging-runtime-check.mjs
node bin/ddd-staging-data-safety-check.mjs
```

The Node wrapper runs the generated bash initializer through an LF-normalized temporary copy, so the same command works from Windows worktrees where `.sh` files may be checked out with CRLF. The `--check` mode is read-only and reports bash availability, target overwrite risk, and the receipt path before any file is created.

The final go/no-go gate supports `DDD_NODE_BIN` for Windows/WSL-style shells where `bash` cannot resolve `node` from `PATH`. Production runners can leave it unset when `node` is already on `PATH`; local contract tests set it to the current Node executable so the final packet check and staging final-review hook both execute.

On Windows/WSL working trees, filesystem permission emulation may still report the initialized file as broader than `600`; use the generated file only as a local fill template unless the target filesystem enforces owner-only permissions, and keep real staging secrets in the approved secret store or a permission-aware release runner.

Generate owner-specific handoff packets for release env collection:

```bash
node bin/ddd-staging-execution-checklist.mjs --owner-packets
```

List available owner filters before generating a single-owner packet:

```bash
node bin/ddd-staging-execution-checklist.mjs --list-owners
```

Regenerate one owner packet when only one owner needs a refresh:

```bash
node bin/ddd-staging-execution-checklist.mjs --owner-packets --owner=release-infra
```

Unknown owner names fail fast and print the available owner filters, so a typo cannot silently produce an empty packet set.

Without `--no-report`, the preflight script writes a machine-readable report to:

```text
artifacts/ddd/release/production-readiness-preflight.json
```

The staging checklist writes:

```text
artifacts/ddd/release/staging-execution-checklist.json
artifacts/ddd/release/staging-execution-checklist.md
artifacts/ddd/release/staging-execution-checklist-owner-packets/README.md
artifacts/ddd/release/staging-execution-checklist-owner-packets/*.md
artifacts/ddd/release/staging-execution-checklist-owner-packets/*.blocking-inputs.template.env
```

The checklist includes release env owner handoff blockers, owner-owned key names, input reasons, immediate P0 waves, execution tracks, commands, expected artifacts, and source artifact references. Owner packets also include the blocked staging evidence gaps, current blocking input keys relevant to that owner, owner-scoped missing evidence artifacts, and an owner-scoped env skeleton, so each handoff packet can drive both env collection and evidence closure. Use it as the staging operator entry point after local code gates are clean.

CI runs the static mode in the `ddd-gates` job with:

```bash
node bin/ddd-production-readiness-preflight.mjs --static-only --no-report
```

The following gates were executed locally:

| Gate | Command | Result |
| --- | --- | --- |
| DDD system architecture and owner tests | `.\mvnw.cmd -pl services/lumira-system -am "-Dtest=DddArchitectureBoundaryTest,DddArchitectureCatalogControllerTest,OwnerReadModelMetricsServiceTest,IamV2ControllerTest,IamReadinessV2ControllerTest,PlatformReadinessV2ControllerTest,PlatformV2ControllerTest,AiReadinessV2ControllerTest,AiOwnerMetricsServiceTest,AiKnowledgeBaseAppServiceTest,AiKnowledgeVectorServiceTest,RuntimeSecurityPropertiesValidatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS, 42 tests |
| Aggregated backend package | `.\mvnw.cmd -pl services/lumira-admin -am "-DskipTests" package` | PASS |
| Frontend dependencies | `corepack pnpm --dir lumira-ui install --frozen-lockfile` | PASS |
| Frontend lint | `corepack pnpm --dir lumira-ui run lint` | PASS |
| Frontend typecheck | `corepack pnpm --dir lumira-ui run typecheck` | PASS |
| Frontend unit tests | `corepack pnpm --dir lumira-ui run test` | PASS, 12 files / 37 tests |
| Frontend production build | `corepack pnpm --dir lumira-ui run build` | PASS after Windows path fix; reverified after quick preflight changes |
| Full backend test reactor | `.\mvnw.cmd clean test` | PASS, 17 Maven modules, total time 6:41 |
| Migration evidence sync | `node bin/ddd-migration-evidence-sync.test.mjs` | PASS |
| Release config sync | `node bin/ddd-release-config-sync.test.mjs` | PASS after whitespace-tolerant assertion fix |
| Dockerfile contract | `node bin/ddd-dockerfile-contract.test.mjs` | PASS |
| Docker image evidence probe | `DDD_DOCKER_BUILD_REPORT=tmp/ddd-docker-image-evidence.json DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_BUILD_ENVIRONMENT=staging DDD_RELEASE_CANDIDATE=local-ddd-readiness DDD_EVIDENCE_OPERATOR=codex-local-preflight node bin/ddd-docker-build-evidence.mjs` | FAIL locally because Docker CLI/daemon is unavailable; static Dockerfile checks pass |
| Final go/no-go gate contract | `node bin/ddd-release-final-go-no-go-gate-contract.test.mjs` | PASS after Windows/WSL local compatibility fix |
| Production readiness preflight quick mode | `node bin/ddd-production-readiness-preflight.mjs --quick --no-report` | PASS, includes staging dispatch check; skips heavy backend DDD architecture tests and lumira-ui production build; report skipped |
| Production readiness preflight plan preview | `node bin/ddd-production-readiness-preflight.mjs --quick --no-report --list` | PASS, prints JSON plan without running checks or writing a report |
| Production readiness preflight static mode | `node bin/ddd-production-readiness-preflight.mjs --static-only --no-report` | PASS, 7 passed / 7 skipped; includes staging checklist, runtime check, and data safety check contracts; report skipped |
| Staging dispatch pre-check | `node bin/ddd-staging-execution-checklist.mjs --dispatch-check` | PASS, read-only JSON; ownerCount `5`, blocked tracks `6`, cutoverAllowed `false`, Docker check reports `external-runner-required` locally |
| Staging readiness rollup | `node bin/ddd-staging-execution-checklist.mjs --rollup` / `--rollup-markdown` / `--rollup-enforce` | BLOCKED locally; summarizes six release-owner triage rows as JSON or Markdown; enforce exits non-zero while blocked |
| Staging evidence runbook | `node bin/ddd-staging-execution-checklist.mjs --evidence-runbook` / `--evidence-runbook-markdown` | PASS command; prints owner, command, artifact, and env-key runbook for all six staging tracks |
| Staging evidence acceptance | `node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance` / `--evidence-acceptance-markdown` | BLOCKED locally; prints per-gate acceptance state, owner, acceptance command, expected artifacts, artifact present/missing state, env keys, and first blocker |
| Staging evidence artifact gaps | `node bin/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report` / `--evidence-artifact-gap-report-markdown` | BLOCKED locally; groups missing expected artifact paths by dependent gates, original owners, dispatch owners, acceptance commands, and evidence commands |
| Staging EXPLAIN artifact plan | `node bin/ddd-staging-execution-checklist.mjs --explain-artifact-plan` / `--explain-artifact-plan-markdown` | BLOCKED locally; focuses the missing `tmp/ddd-explain/*.json` artifact into owner routing, required inputs, redacted env skeleton, commands, expected artifacts, and pass criteria |
| Staging blocking inputs | `node bin/ddd-staging-execution-checklist.mjs --blocking-inputs` / `--blocking-inputs-markdown` / `--blocking-inputs-env-template` | BLOCKED locally; prints a reverse index from missing input key to gate, owner, blocker, next command, and a focused env fill template |
| Staging evidence env template | `node bin/ddd-staging-execution-checklist.mjs --evidence-env-template` | PASS command; prints focused placeholder env for Docker, runtime, rollback, migration, and EXPLAIN evidence |
| Staging handoff bundle | `node bin/ddd-staging-execution-checklist.mjs --handoff-bundle` / `--handoff-bundle-verify` | PASS command; writes status views, lane routes, receipt submission flow, lane receipt fragments, evidence closure board, submission plans, owner dispatch, owner packets, commands, and checksum manifest; verifier checks manifest/hash plus required Markdown markers, non-empty `laneRoutes`, final-review owner lane routes, receipt-template `submissionFlow`, lane receipt fragment count/key/skeleton alignment, evidence closure board Markdown and lane count alignment, owner-dispatch lane route shape, owner packet top-level JSON summary alignment, owner packet `Expected artifacts:` / `Currently missing artifacts:` route markers, lane fragment alignment with `owner-dispatch.json`, and owner packet JSON `queueLanes` alignment with `owner-dispatch.json` |
| CI staging handoff artifact | `.github/workflows/ci.yml` / `ddd-staging-handoff-bundle` | Added; CI generates, structurally verifies, appends handoff summary, release-owner closeout, artifact gaps, EXPLAIN artifact plan, operator progress, daily brief, lane submission routes, and final review, then uploads the bundle from the `ddd-gates` job with missing-file failure and 14-day retention |
| Release evidence handoff artifact | `.github/workflows/ddd-release-evidence.yml` / `ddd-release-evidence-<environment>-<run_number>` | Added; formal release evidence workflow generates and verifies `artifacts/ddd/release/staging-handoff-bundle` before final manifest refresh, uploads it inside `artifacts/ddd/**`, accepts optional `lane_completion_receipt_file` as `DDD_LANE_COMPLETION_RECEIPT_FILE`, accepts optional `lane_completion_receipt_base64` decoded to `artifacts/ddd/release/lane-completion-receipt.submitted.json` beside the bundle, hard-validates the supplied receipt with contract and coverage, and appends handoff summary, release-owner closeout, lane receipt coverage, evidence closure board, artifact gaps, EXPLAIN artifact plan, operator progress, daily brief, lane submission routes, and final review to the release evidence Step Summary |
| Staging execution status | `node bin/ddd-staging-execution-checklist.mjs --execution-status` / `--execution-status-markdown` | BLOCKED locally; combines rollup, evidence gaps, handoff bundle verification, `laneRoutes`, and next release-owner command into one status page |
| Release-owner daily brief | `node bin/ddd-staging-execution-checklist.mjs --release-owner-daily-brief` / `--release-owner-daily-brief-markdown` | BLOCKED locally; prioritizes owner actions from final review, operator progress, owner packets, missing artifacts, blocking inputs, acceptance commands, and `## Lane Routes` for daily release follow-up |
| Release-owner final review | `node bin/ddd-staging-execution-checklist.mjs --final-review` / `--final-review-markdown` / `--final-review-enforce` | BLOCKED locally; combines bundle integrity, owner template completeness, owner lane routes, receipt contract and coverage, evidence closure board closed/open lane counts, evidence acceptance, cutover allowance, top blocking inputs, and next command into one final go/no-go review; enforce exits non-zero while `cutoverReady=false` |
| Final go/no-go staging hook | `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh` | BLOCKED locally; final gate now invokes staging final review enforce before approving cutover |
| Staging runtime pre-check | `node bin/ddd-staging-runtime-check.mjs` | BLOCKED locally because staging HTTPS URLs, deployment evidence, and remote expectation flags are not configured |
| Staging data safety pre-check | `node bin/ddd-staging-data-safety-check.mjs` | BLOCKED locally because rollback, migration, and EXPLAIN staging evidence env is not configured |
| Staging execution checklist | `node bin/ddd-staging-execution-checklist.mjs` | PASS with `DDD_STAGING_CHECKLIST_OUTPUT=tmp/ddd-staging-execution-checklist`; status `STAGING_REQUIRED`, 6 tracks |
| Staging execution checklist contract | `node bin/ddd-staging-execution-checklist.test.mjs` | PASS |
| CI production readiness static preflight | `.github/workflows/ci.yml` / `ddd-gates` | Added |

Full `.\mvnw.cmd clean test` completed locally with a longer timeout. The quick preflight now skips the heavy DDD system architecture slice by default and keeps it behind `--include-backend-architecture-tests`, because that slice is better treated as an explicit deep local check than as the everyday operator preflight. Static checks stay fast enough for CI and local release-script validation.

## 4. Fixes Made During Readiness Pass

### Frontend build post-processing

`lumira-ui/bin/adapt-cdn-assets.mjs` now uses `fileURLToPath(...)` instead of URL `.pathname`, so Windows builds can find `lumira-ui/dist`.

### Release config sync contract

`bin/ddd-release-config-sync.test.mjs` now checks the manifest preflight workflow step with whitespace-tolerant matching, so equivalent YAML formatting does not fail the sync contract.

### Final go/no-go contract local execution

`bin/ddd-release-final-go-no-go-gate-contract.mjs` now handles Windows/WSL local execution more safely:

- Converts Windows paths for bash.
- Normalizes temporary shell copies to LF.
- Skips dynamic bash execution when bash has no `node`, while preserving Linux CI behavior.
- Keeps executable-bit enforcement on non-Windows platforms.

## 5. Current Go/No-Go State

Local advisory evidence gate:

- `node bin/ddd-release-evidence-gate.mjs`
- Result on 2026-06-17: advisory checks completed, blockers `0`, warnings `18`

Final packet:

- Recommendation: `NO_GO_STRICT`
- `cutoverAllowed`: `false`

Current stop categories include:

- Authenticated performance baseline not ready.
- Database performance cutover item blocked.
- Deployable images blocked.
- Evidence integrity blocked.
- Release environment blocked.
- Rollback safety blocked.
- Runtime business acceptance blocked.
- Owner input receipt pending.

The warning set also shows that existing runtime, performance, file, payment, job, AI, and lumira-ui smoke artifacts are local-only. They cannot be used as production-equivalent launch evidence.

The current warning set should be handled as five execution tracks:

1. Replace localhost runtime evidence with staging or production-equivalent HTTPS evidence.
2. Replace template release env values and clear release config blockers.
3. Produce successful deployable image evidence for backend and lumira-ui.
4. Capture migration drill and hot-path EXPLAIN evidence from the target database shape.
5. Rerun runtime, lumira-ui, file, payment, job, AI, and performance smokes against the deployed environment.

## 6. Required Production-Equivalent Evidence

Before strict production cutover, collect evidence from a staging or production-equivalent HTTPS environment:

1. Real `.env.release` with placeholders removed and permissions set to `600`.
2. Release env lint and release config evidence.
3. Fresh database migration drill.
4. Existing database upgrade drill.
5. Rollback drill or explicitly approved bounded-context deferrals.
6. Docker image build and inspect evidence for `lumira-server` and `lumira-ui`.
7. Runtime owner readiness, health, and metrics for all bounded contexts.
8. Frontend smoke evidence against deployed lumira-ui URL.
9. Authenticated runtime performance actual and accepted baseline.
10. Hot-path SQL EXPLAIN evidence.
11. File processing E2E.
12. Payment webhook E2E.
13. Job owner relay E2E.
14. AI runtime drill with remote provider and owner gateway evidence.
15. Strict release evidence gate.
16. Final go/no-go gate with `cutoverAllowed=true`.

## 7. Recommended Execution Order

### Wave 1: Release Environment

Prepare a real release env file outside git:

```bash
node bin/ddd-staging-execution-checklist.mjs --dispatch-check
node bin/ddd-release-env-init.mjs --check
node bin/ddd-release-env-init.mjs
```

Fill the generated env file with real staging or production-equivalent values, then run:

```bash
DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-file-lint.mjs
DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-config-evidence.mjs
node bin/ddd-release-readiness-summary.mjs
```

### Wave 2: Build And Deployment Evidence

Build or pull deployable images, then capture Docker evidence:

```bash
node bin/ddd-docker-build-evidence.mjs --check
DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs
```

Local probe result on 2026-06-18: `node bin/ddd-docker-build-evidence.mjs --check` reports `recommendedMode=external-runner-required` because Docker CLI is not available on this workstation, while static Dockerfile checks pass. Deployable image evidence must be collected on a Docker-enabled runner or by inspecting CI-pushed images.

Docker-enabled runner path:

```bash
DDD_DOCKER_BUILD_STRICT=true \
DDD_DOCKER_BUILD_ENVIRONMENT=staging \
DDD_RELEASE_CANDIDATE=<release-candidate> \
DDD_EVIDENCE_OPERATOR=<operator> \
node bin/ddd-docker-build-evidence.mjs
```

Inspect-only path when CI already built and pushed the release images:

```bash
DDD_DOCKER_BUILD_STRICT=true \
DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> \
DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> \
DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/lumira-ui:<release-candidate> \
DDD_DOCKER_BUILD_ENVIRONMENT=staging \
DDD_RELEASE_CANDIDATE=<release-candidate> \
DDD_EVIDENCE_OPERATOR=<operator> \
node bin/ddd-docker-build-evidence.mjs
```

Deploy the candidate to a production-equivalent HTTPS environment.

### Wave 3: Migration And Rollback

Collect migration evidence:

```bash
DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs
DDD_MIGRATION_STRICT=true node bin/ddd-migration-evidence.mjs
```

Collect rollback evidence:

```bash
DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs
node bin/ddd-rollback-drill-evidence.mjs
```

### Wave 4: Runtime And Business E2E

Run runtime and business smoke evidence against the deployed environment:

```bash
node bin/ddd-runtime-readiness-smoke.mjs
node bin/ddd-frontend-playwright-smoke.mjs
node bin/ddd-authenticated-performance-smoke.mjs
node bin/ddd-file-processing-e2e-smoke.mjs
node bin/ddd-payment-webhook-e2e-smoke.mjs
node bin/ddd-job-e2e-smoke.mjs
node bin/ddd-ai-runtime-drill.mjs
```

Promote the authenticated performance baseline only after the actual run is production-equivalent and clean:

```bash
node bin/ddd-promote-performance-baseline.mjs
```

### Wave 5: Strict Gate And Cutover

Refresh release evidence and enforce the final decision:

```bash
DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-gate.mjs
node bin/ddd-release-readiness-summary.mjs
DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh
```

Only cut over when the final packet reports:

- `finalRecommendation=GO_STRICT`
- `cutoverAllowed=true`
- no current stop reasons

## 8. Immediate Next Actions

Use `node bin/ddd-staging-execution-checklist.mjs --commands` to print the copy-ready staging preparation sequence.

1. Review and commit the local production-readiness script fixes.
2. Run `node bin/ddd-production-readiness-preflight.mjs --quick --no-report` before handing work to owners; it now includes the read-only staging dispatch check.
3. Run `node bin/ddd-staging-execution-checklist.mjs --dispatch-check` to verify owner routing, tracks, and env-init readiness without writing files.
4. Run `node bin/ddd-staging-execution-checklist.mjs --rollup` for a compact release-owner triage view, or `node bin/ddd-staging-execution-checklist.mjs --rollup-markdown` for a paste-ready table. Use `node bin/ddd-staging-execution-checklist.mjs --rollup-enforce` only after evidence env is expected to be complete.
5. Run `node bin/ddd-staging-execution-checklist.mjs --evidence-gaps` to print the blocked staging evidence gaps as JSON.
6. Print or paste the evidence runbook with `node bin/ddd-staging-execution-checklist.mjs --evidence-runbook` or `--evidence-runbook-markdown`.
7. Print or paste the evidence acceptance checklist with `node bin/ddd-staging-execution-checklist.mjs --evidence-acceptance` or `--evidence-acceptance-markdown`; use the artifact present/missing rows to confirm which evidence files have landed.
8. Print or paste the artifact gap report with `node bin/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report` or `--evidence-artifact-gap-report-markdown`; use it to confirm which missing artifact path is shared across multiple gates and which dispatch owner must follow up.
   For the current EXPLAIN gap, print or paste `node bin/ddd-staging-execution-checklist.mjs --explain-artifact-plan` or `--explain-artifact-plan-markdown` to route `tmp/ddd-explain/*.json` collection with the required inputs, commands, expected artifacts, and pass criteria.
9. Print or paste the staging closure plan with `node bin/ddd-staging-execution-checklist.mjs --closure-plan` or `--closure-plan-markdown`; use it as the owner/ETA critical path for the release meeting.
10. Print or paste the immediate next-action queue with `node bin/ddd-staging-execution-checklist.mjs --next-action-queue` or `--next-action-queue-markdown`; use it as the first release-owner dispatch view.
11. Print or paste the owner lane matrix with `node bin/ddd-staging-execution-checklist.mjs --owner-lane-matrix` or `--owner-lane-matrix-markdown`; use it to confirm which owner owns each lane before collecting receipts.
12. Generate the lane completion receipt template with `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template` or `--lane-completion-receipt-template-markdown`; owners must submit a redacted receipt after their lane acceptance commands pass.
13. Print the immediate next-action env skeleton with `node bin/ddd-staging-execution-checklist.mjs --next-action-env-template`; use it to prepare secure env or CI secret-store entries for the first dispatch wave.
14. Validate the populated first-wave env file with `node bin/ddd-staging-execution-checklist.mjs --next-action-env-check --next-action-env-file=<env-file>` before owners run heavier checks.
15. Generate and validate the redacted first-wave env receipt with `node bin/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file> --next-action-env-receipt-output=<receipt-file>`; attach the receipt, not the populated env file, to release-owner status. Use `--next-action-env-receipt-markdown` only for paste-ready human status.
16. Print or paste the post-env-check verification route with `node bin/ddd-staging-execution-checklist.mjs --next-action-verification-plan` or `--next-action-verification-plan-markdown`; use it as the ordered route after first-wave env passes.
17. Print or paste the operator progress view with `node bin/ddd-staging-execution-checklist.mjs --operator-progress` or `--operator-progress-markdown`; include `--next-action-env-receipt-file=<receipt-file>` after env receipt generation and `--lane-completion-receipt-file=<receipt-file>` after owner lane receipt submission. Use the evidence artifact totals, lane receipt coverage, and missing-by-owner list as the daily release-owner status after each evidence wave.
18. Print or paste the daily action brief with `node bin/ddd-staging-execution-checklist.mjs --release-owner-daily-brief` or `--release-owner-daily-brief-markdown`; use its Today section to route owner packets, env templates, missing artifacts, and acceptance commands during the daily release standup.
19. Print or paste the P0 release-env plan with `node bin/ddd-staging-execution-checklist.mjs --release-env-plan` or `--release-env-plan-markdown`; use it to drive secure env initialization and owner value collection.
20. Print or paste the release-env owner matrix with `node bin/ddd-staging-execution-checklist.mjs --release-env-owner-matrix` or `--release-env-owner-matrix-markdown`; use it to assign owner value collection without exposing secrets.
21. Print the current top owner template with `node bin/ddd-staging-execution-checklist.mjs --release-env-next-owner-template`; use it as the first focused fill file for release-infra.
22. Print or paste the release-env merge plan with `node bin/ddd-staging-execution-checklist.mjs --release-env-merge-plan` or `--release-env-merge-plan-markdown`; use it after owner values are collected.
23. Print or paste the Docker image plan with `node bin/ddd-staging-execution-checklist.mjs --docker-image-plan` or `--docker-image-plan-markdown`; then route the release-infra submission with `--docker-image-submission-plan` or `--docker-image-submission-plan-markdown` and choose Docker-runner build evidence or existing-image inspect evidence.
24. Print or paste the P1 runtime/business plan with `node bin/ddd-staging-execution-checklist.mjs --runtime-business-plan` or `--runtime-business-plan-markdown`; use it to coordinate staging URLs, deployment evidence, and smoke owners.
25. Print or paste the owner-phased runtime smoke plan with `node bin/ddd-staging-execution-checklist.mjs --runtime-smoke-plan` or `--runtime-smoke-plan-markdown`; then route runtime/business owner submissions with `--runtime-business-submission-plan` or `--runtime-business-submission-plan-markdown` after deployment evidence lands.
26. Print or paste the data safety plan with `node bin/ddd-staging-execution-checklist.mjs --data-safety-plan` or `--data-safety-plan-markdown`; use it to coordinate rollback drill, migration drill, and EXPLAIN evidence owners.
27. Print or paste the owner-phased data safety plan with `node bin/ddd-staging-execution-checklist.mjs --data-safety-owner-plan` or `--data-safety-owner-plan-markdown`; then route owner submissions with `--data-safety-submission-plan` or `--data-safety-submission-plan-markdown` for bounded-context rollback, database migration, EXPLAIN collection, and release-infra acceptance.
28. Print or paste the cutover rehearsal plan with `node bin/ddd-staging-execution-checklist.mjs --cutover-rehearsal-plan` or `--cutover-rehearsal-plan-markdown`; use it as the ordered staging dry-run route.
29. Print or paste the blocking input reverse index with `node bin/ddd-staging-execution-checklist.mjs --blocking-inputs` or `--blocking-inputs-markdown`.
30. Generate the current blocking input env skeleton with `node bin/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template`.
31. Print or paste the one-page status with `node bin/ddd-staging-execution-checklist.mjs --execution-status` or `--execution-status-markdown`.
32. Before final review, validate the submitted lane receipt with `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>` and `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage --lane-completion-receipt-file=<receipt-file>`; require `Coverage: 5/5`. To feed the formal manual release workflow, generate the `lane_completion_receipt_base64` value with `node bin/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>`.
33. Print or paste the release-owner final review with `node bin/ddd-staging-execution-checklist.mjs --final-review` or `--final-review-markdown`; use `--final-review-enforce --lane-completion-receipt-file=<receipt-file>` only when evidence is expected to be complete.
34. Print a focused env skeleton with `node bin/ddd-staging-execution-checklist.mjs --evidence-env-template`.
35. Generate a release-owner bundle with `node bin/ddd-staging-execution-checklist.mjs --handoff-bundle`, then verify it with `node bin/ddd-staging-execution-checklist.mjs --handoff-bundle-verify`.
36. Initialize the local fill template with `node bin/ddd-release-env-init.mjs --check`, then `node bin/ddd-release-env-init.mjs`.
37. List owner filters with `node bin/ddd-staging-execution-checklist.mjs --list-owners`.
38. Generate owner packets with `node bin/ddd-staging-execution-checklist.mjs --owner-packets`; each owner packet includes that owner's current blocking inputs, staging evidence gaps, owner-scoped missing evidence artifacts, queue lanes, `Expected artifacts:`, `Currently missing artifacts:`, and owner completion receipt commands.
39. Track lane closeout with `node bin/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown --lane-completion-receipt-file=<receipt-file>` or export a sheet with `node bin/ddd-staging-execution-checklist.mjs --evidence-closure-board-csv --lane-completion-receipt-file=<receipt-file>`; every owner:lane row must show PASS, provided artifacts, and no missing artifacts before final review.
39. Collect owner values into the secure staging `.env.release`, then run `DDD_RELEASE_ENV_FILE=<release-env-file> node bin/ddd-release-env-file-lint.mjs`.
40. Run `node bin/ddd-docker-build-evidence.mjs --check` to choose Docker-enabled build evidence or existing-image inspect evidence.
41. Run `node bin/ddd-staging-runtime-check.mjs` before P1 runtime/business smokes.
42. Run `node bin/ddd-staging-data-safety-check.mjs` before rollback, migration, and EXPLAIN evidence collection.
43. Run `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`.
44. Produce Docker image evidence on a Docker-enabled runner or inspect CI-pushed images.
45. Deploy the current candidate to a staging HTTPS URL.
46. Execute Waves 1 through 5 in order.
47. Re-run `node bin/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>` and require exit code `0` with `cutoverReady=true`.
48. Cut over only after strict final go/no-go reports `GO_STRICT` and `cutoverAllowed=true`.
