# DDD Production Rollout Plan

## Current Decision

Local production-equivalent release evidence is green.

- Source env: `.env.release.local` (git-ignored)
- Final attempt report: `artifacts/ddd/release/production-unblock-attempt/production-unblock-attempt.json`
- Final recommendation: `GO_STRICT`
- Cutover allowed: `true`
- Ready evidence gates: `5/5`
- Blockers: `0`

This is not proof that a remote production deployment has already happened. It proves the local production-equivalent gate can be executed end to end with strict release evidence.

## Evidence Commands

Run these before any production promotion decision:

```powershell
$env:DDD_RELEASE_ENV_FILE = '.env.release.local'
node bin/ddd-release-env-file-lint.mjs
node bin/ddd-release-config-evidence.mjs
Remove-Item Env:DDD_RELEASE_ENV_FILE

node bin/ddd-explain-gate.mjs
node bin/ddd-production-unblock-attempt.mjs
```

Expected result:

- release env lint: `PASS`
- release config evidence: `PASS`
- EXPLAIN gate: validates 8 JSON files
- production unblock attempt: `PASS`, `GO_STRICT`, `blockerCount=0`

## Local Production-Equivalent Setup

The local run uses Docker-backed MySQL for real `EXPLAIN FORMAT=JSON` collection.

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml --profile local-mysql up -d mysql
```

If the MySQL data volume was initialized with an old password, rebuild only the local MySQL volume:

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml --profile local-mysql down
docker volume rm deploy_mysql_data
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml --profile local-mysql up -d mysql
```

Then import the minimal EXPLAIN schema and collect plans:

```powershell
Get-Content .env.release.local | Where-Object { $_ -match '^[A-Z_][A-Z0-9_]*=' } | ForEach-Object {
  $k,$v = $_ -split '=',2
  Set-Item -Path "Env:$k" -Value $v
}

$env:DOCKER_CLI_PS = 'scripts\docker-wrapper.ps1'
$env:MYSQL_STDIN_FILE = 'bin/ddd-explain-local-schema.sql'
node bin/ddd-mysql-docker-exec-wrapper.mjs --batch --raw --host=mysql --port=3306 --user=$env:MYSQL_USER --password=$env:MYSQL_PASSWORD $env:DDD_EXPLAIN_DATABASE
Remove-Item Env:MYSQL_STDIN_FILE

$env:MYSQL_CLI_NODE_SCRIPT = 'bin/ddd-mysql-docker-exec-wrapper.mjs'
node bin/ddd-collect-explain.mjs
Remove-Item Env:MYSQL_CLI_NODE_SCRIPT
Remove-Item Env:DOCKER_CLI_PS

node bin/ddd-explain-gate.mjs
```

## Production Promotion Sequence

1. Freeze the release candidate SHA.
2. Re-run `node bin/ddd-production-unblock-attempt.mjs`.
3. Review `artifacts/ddd/release/production-unblock-attempt/production-unblock-attempt.json`.
4. Review the redacted lane receipt:
   `artifacts/ddd/release/production-unblock-attempt/lane-completion-receipt.attempt.json`
5. Use the reported next command only after the JSON report shows `PASS`:
   `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`
6. Promote using the existing deployment process.
7. After deployment, collect remote production evidence and do not reuse local-only EXPLAIN artifacts as proof of remote production health.

## Commit Scope Recommendation

Recommended to commit:

- Script fixes under `bin/`
- Focused production-unblock attempt artifacts
- This rollout plan

Recommended not to commit without review:

- `.env.release.local`
- Broad regenerated handoff bundle churn unless the team wants refreshed generated evidence in git
- Unrelated lumira-ui/security files already present in the working tree

## Verification Notes

Focused checks run locally:

- `node bin/ddd-migration-evidence-contract.test.mjs`
- `node bin/ddd-rollback-drill-evidence.test.mjs`
- `node bin/ddd-explain-gate.mjs`
- `node bin/ddd-production-unblock-attempt.mjs`

The full `node bin/ddd-staging-execution-checklist.test.mjs` was attempted but exceeded the working time window without output, so it was not counted as a pass.
