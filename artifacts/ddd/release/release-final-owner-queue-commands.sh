#!/usr/bin/env bash
set -euo pipefail

# Lumira DDD final owner queue commands.
# Generated at: 2026-06-20T19:42:26.704Z
# Recommendation: NO_GO_STRICT
# Default mode lists actionable owners. Set DDD_FINAL_OWNER_QUEUE_EXECUTE=1 to run commands.
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
if [[ -z "${LUMIRA_REPO_ROOT:-}" ]]; then
  if [[ -f "bin/ddd-release-readiness-summary.mjs" ]]; then
    LUMIRA_REPO_ROOT=$(pwd)
  else
    LUMIRA_REPO_ROOT=$(cd "${SCRIPT_DIR}/../../.." && pwd)
  fi
fi
export LUMIRA_REPO_ROOT
cd "${LUMIRA_REPO_ROOT}"

DDD_FINAL_OWNER_QUEUE_OWNER="${DDD_FINAL_OWNER_QUEUE_OWNER:-}"
DDD_FINAL_OWNER_QUEUE_STATUS="${DDD_FINAL_OWNER_QUEUE_STATUS:-ACTIONABLE}"
DDD_FINAL_OWNER_QUEUE_DETAIL="${DDD_FINAL_OWNER_QUEUE_DETAIL:-}"
DDD_FINAL_OWNER_QUEUE_CHECK_ENV="${DDD_FINAL_OWNER_QUEUE_CHECK_ENV:-}"
DDD_FINAL_OWNER_QUEUE_EXECUTE="${DDD_FINAL_OWNER_QUEUE_EXECUTE:-}"
DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR="${DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR:-}"
DDD_FINAL_OWNER_QUEUE_REPORT="${DDD_FINAL_OWNER_QUEUE_REPORT:-artifacts/ddd/release/release-final-owner-queue-run-report.json}"
DDD_FINAL_OWNER_QUEUE_REPORT_TMP="${DDD_FINAL_OWNER_QUEUE_REPORT}.jsonl.$$"
DDD_FINAL_OWNER_QUEUE_COMMAND_FAILURES=0
if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
  if [[ -z "${DDD_RELEASE_ENV_FILE:-}" ]]; then
    echo "DDD_RELEASE_ENV_FILE is required when executing or checking final owner queue env." >&2
    exit 1
  fi
  if [[ ! -f "${DDD_RELEASE_ENV_FILE}" ]]; then
    echo "DDD_RELEASE_ENV_FILE does not exist: ${DDD_RELEASE_ENV_FILE}" >&2
    exit 1
  fi
  if [[ "${DDD_RELEASE_ENV_FILE}" == *"release-env-missing.template.env" || "${DDD_RELEASE_ENV_FILE}" == *"release-closure-wave-env.template.env" || "${DDD_RELEASE_ENV_FILE}" == *"release-final-owner-queue-env.template.env" ]]; then
    echo "Template env files are worksheets, not release evidence: ${DDD_RELEASE_ENV_FILE}" >&2
    exit 1
  fi
  DDD_RELEASE_ENV_FILE_MODE=$(stat -c '%a' "${DDD_RELEASE_ENV_FILE}" 2>/dev/null || node -e "const fs=require('node:fs'); const mode=fs.statSync(process.argv[1]).mode & 0o777; console.log(mode.toString(8).padStart(3, '0'));" "${DDD_RELEASE_ENV_FILE}")
  if (( 8#${DDD_RELEASE_ENV_FILE_MODE} & 077 )); then
    echo "Release env file permissions are too broad: ${DDD_RELEASE_ENV_FILE} mode=${DDD_RELEASE_ENV_FILE_MODE}; use chmod 600." >&2
    exit 1
  fi
  export DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED=1
fi
matches_owner_queue_filter() {
  local owner="$1"
  local status="$2"
  if [[ -n "${DDD_FINAL_OWNER_QUEUE_OWNER}" && "${owner}" != "${DDD_FINAL_OWNER_QUEUE_OWNER}" ]]; then return 1; fi
  if [[ -n "${DDD_FINAL_OWNER_QUEUE_STATUS}" && "${status}" != "${DDD_FINAL_OWNER_QUEUE_STATUS}" ]]; then return 1; fi
  return 0
}
env_file_has_owner_queue_key() {
  local key="$1"
  node --input-type=module -e 'import fs from '\''node:fs'\''; const [file, key] = process.argv.slice(1); const text = fs.readFileSync(file, '\''utf8'\''); const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, '\''\\$&'\''); const pattern = new RegExp(`^\\s*(?:export\\s+)?${escaped}\\s*=\\s*(.*)$`, '\''gm'\''); const matches = [...text.matchAll(pattern)]; if (matches.length === 0) process.exit(1); const raw = matches.at(-1)[1].trim(); const value = raw.replace(/^(['\''\"])(.*)\1$/, '\''$2'\'').trim(); if (!value || value === '\''__REQUIRED__'\'') process.exit(1);' "$DDD_RELEASE_ENV_FILE" "$key"
}
safe_load_release_env_file() {
  local exports
  if ! exports=$(node --input-type=module -e 'import fs from '\''node:fs'\''; import path from '\''node:path'\''; const [file, permissionCheckedArg] = process.argv.slice(1); const templateNames = new Set(['\''release-env-missing.template.env'\'', '\''release-closure-wave-env.template.env'\'', '\''release-final-owner-queue-env.template.env'\'', '\''release-env-canonical-fill.template.env'\'']); if (templateNames.has(path.basename(file))) {   console.error(`[ddd-release-env][template-refused] file=${file}`);   process.exit(1); } const permissionAlreadyChecked = permissionCheckedArg === '\''1'\'' || permissionCheckedArg === '\''true'\'' || process.env.DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED === '\''1'\'' || process.env.DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED === '\''true'\''; const mode = permissionAlreadyChecked ? 0o600 : fs.statSync(file).mode & 0o777; if (!permissionAlreadyChecked && (mode & 0o077) !== 0) {   console.error(`[ddd-release-env][permission-refused] file=${file} mode=${mode.toString(8).padStart(3, '\''0'\'')} required=600`);   process.exit(1); } const text = fs.readFileSync(file, '\''utf8'\''); const quote = (value) => `'\''${String(value).replace(/'\''/g, `'\''\\'\'''\''`)}'\''`; let lineNumber = 0; for (const line of text.split(/\r?\n/)) {   lineNumber += 1;   const trimmed = line.trim();   if (!trimmed || trimmed.startsWith('\''#'\'')) continue;   const match = trimmed.match(/^(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$/);   if (!match) {     console.error(`[ddd-release-env][env-invalid] line=${lineNumber}`);     process.exit(1);   }   let value = match[2].trim();   const quoted = value.match(/^(['\''\"])(.*)\1$/s);   if (quoted) value = quoted[2];   console.log(`export ${match[1]}=${quote(value)}`); }' "$DDD_RELEASE_ENV_FILE" "${DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED:-}"); then
    return 1
  fi
  eval "${exports}"
}
check_owner_queue_env() {
  local missing=0
  local key
  for key in "$@"; do
    if ! env_file_has_owner_queue_key "${key}"; then
      echo "[ddd-final-owner-queue][env-missing] key=${key}" >&2
      missing=1
    fi
  done
  if [[ "${missing}" == "0" ]]; then
    echo "[ddd-final-owner-queue][env-ok]"
  fi
  return "${missing}"
}
append_owner_queue_report_entry() {
  local owner="$1"
  local queue_order="$2"
  local queue_status="$3"
  local command_index="1"
  local command_count="1"
  local command=""
  local status=""
  local duration_ms=""
  if [[ "$#" -ge 8 ]]; then
    command_index="$4"
    command_count="$5"
    command="$6"
    status="$7"
    duration_ms="$8"
  elif [[ "$#" -eq 6 ]]; then
    command="$4"
    status="$5"
    duration_ms="$6"
  else
    echo "[ddd-final-owner-queue][report-entry-invalid] expected 6 legacy args or 8 indexed args, got $#" >&2
    return 2
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" != "1" && "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" != "true" ]]; then return 0; fi
  node --input-type=module -e 'import fs from "node:fs"; const [file, owner, queueOrder, queueStatus, commandIndex, commandCount, command, status, durationMs] = process.argv.slice(1); fs.appendFileSync(file, `${JSON.stringify({ owner, queueOrder: Number(queueOrder), queueStatus, commandIndex: Number(commandIndex), commandCount: Number(commandCount), command, status: Number(status), durationMs: Number(durationMs), finishedAt: new Date().toISOString() })}\n`);' "${DDD_FINAL_OWNER_QUEUE_REPORT_TMP}" "${owner}" "${queue_order}" "${queue_status}" "${command_index}" "${command_count}" "${command}" "${status}" "${duration_ms}"
}
finalize_owner_queue_report() {
  local exit_code="$1"
  if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" != "1" && "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" != "true" ]]; then return 0; fi
  mkdir -p "$(dirname "${DDD_FINAL_OWNER_QUEUE_REPORT}")"
  node --input-type=module -e 'import fs from "node:fs"; const [tmp, out, exitCode, ownerFilter, statusFilter] = process.argv.slice(1); const entries = fs.existsSync(tmp) ? fs.readFileSync(tmp, "utf8").split("\n").filter(Boolean).map((line) => JSON.parse(line)) : []; const exit = Number(exitCode); const failedEntries = entries.filter((entry) => Number(entry.status) !== 0).length; const summary = { totalEntries: entries.length, succeededEntries: entries.length - failedEntries, failedEntries }; fs.writeFileSync(out, `${JSON.stringify({ generatedAt: new Date().toISOString(), reportStatus: exit === 0 ? "PASS" : "FAIL", exitCode: exit, ownerFilter: ownerFilter || null, statusFilter: statusFilter || null, summary, entries }, null, 2)}\n`); if (fs.existsSync(tmp)) fs.rmSync(tmp);' "${DDD_FINAL_OWNER_QUEUE_REPORT_TMP}" "${DDD_FINAL_OWNER_QUEUE_REPORT}" "${exit_code}" "${DDD_FINAL_OWNER_QUEUE_OWNER}" "${DDD_FINAL_OWNER_QUEUE_STATUS}"
  if ! DDD_FINAL_OWNER_QUEUE_REPORT="${DDD_FINAL_OWNER_QUEUE_REPORT}" node bin/ddd-final-owner-queue-run-report-contract.mjs; then
    echo "[ddd-final-owner-queue][report-contract] failed" >&2
    return 1
  fi
  echo "[ddd-final-owner-queue][report] ${DDD_FINAL_OWNER_QUEUE_REPORT}"
  return "${exit_code}"
}
run_owner_queue_command() {
  local owner="$1"
  local queue_order="$2"
  local queue_status="$3"
  local command_index="$4"
  local command_count="$5"
  local command="$6"
  local execution_command="${command//DDD_RELEASE_ENV_FILE=<release-env-file>/DDD_RELEASE_ENV_FILE=${DDD_RELEASE_ENV_FILE:-}}"
  if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" != "1" && "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" != "true" ]]; then
    echo "[ddd-final-owner-queue][dry-run] ${command}"
    return 0
  fi
  local started_ms
  local finished_ms
  local status
  started_ms=$(node -e 'console.log(Date.now())')
  set +e
  bash -lc "${execution_command}"
  status=$?
  set -e
  finished_ms=$(node -e 'console.log(Date.now())')
  append_owner_queue_report_entry "${owner}" "${queue_order}" "${queue_status}" "${command_index}" "${command_count}" "${command}" "${status}" "$((finished_ms - started_ms))"
  if [[ "${status}" != "0" ]]; then
    echo "[ddd-final-owner-queue][command-failed] owner=${owner} status=${status} command=${command}" >&2
    DDD_FINAL_OWNER_QUEUE_COMMAND_FAILURES=$((DDD_FINAL_OWNER_QUEUE_COMMAND_FAILURES + 1))
    if [[ "${DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR}" == "true" ]]; then
      echo "[ddd-final-owner-queue][command-failed] continuing because DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR=${DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR}" >&2
      return 0
    fi
  fi
  return "${status}"
}
if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
  mkdir -p "$(dirname "${DDD_FINAL_OWNER_QUEUE_REPORT}")"
  : > "${DDD_FINAL_OWNER_QUEUE_REPORT_TMP}"
  trap 'finalize_owner_queue_report "$?"' EXIT
  safe_load_release_env_file
fi

DDD_FINAL_OWNER_QUEUE_MATCHED=0
if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" != "1" && "${DDD_FINAL_OWNER_QUEUE_DETAIL}" != "true" && "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" != "1" && "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" != "true" && "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" != "1" && "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" != "true" ]]; then
  echo "Final owner queue:"
  if matches_owner_queue_filter 'release-infra' 'ACTIONABLE'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=1 owner=release-infra status=ACTIONABLE ready=2 blocked=1 missingArtifacts=5 contentBlockers=0 first=node bin/ddd-release-evidence-orchestrator.mjs'
  fi
  if matches_owner_queue_filter 'lumira-ui' 'ACTIONABLE'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=2 owner=lumira-ui status=ACTIONABLE ready=1 blocked=0 missingArtifacts=4 contentBlockers=0 first=DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
  fi
  if matches_owner_queue_filter 'release-performance' 'ACTIONABLE'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=3 owner=release-performance status=ACTIONABLE ready=1 blocked=0 missingArtifacts=3 contentBlockers=0 first=node bin/ddd-authenticated-performance-smoke.mjs'
  fi
  if matches_owner_queue_filter 'ai' 'WAITING'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=4 owner=ai status=WAITING ready=0 blocked=1 missingArtifacts=1 contentBlockers=0 first=node bin/ddd-ai-runtime-drill.mjs'
  fi
  if matches_owner_queue_filter 'ai-owner' 'WAITING'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=5 owner=ai-owner status=WAITING ready=0 blocked=1 missingArtifacts=1 contentBlockers=0 first=node bin/ddd-rollback-deferral-template.mjs'
  fi
  if matches_owner_queue_filter 'auth-owner' 'WAITING'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=6 owner=auth-owner status=WAITING ready=0 blocked=1 missingArtifacts=1 contentBlockers=0 first=node bin/ddd-rollback-deferral-template.mjs'
  fi
  if matches_owner_queue_filter 'database' 'WAITING'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=7 owner=database status=WAITING ready=0 blocked=2 missingArtifacts=5 contentBlockers=0 first=node bin/ddd-collect-explain.mjs'
  fi
  if matches_owner_queue_filter 'file-owner' 'WAITING'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=8 owner=file-owner status=WAITING ready=0 blocked=2 missingArtifacts=2 contentBlockers=0 first=node bin/ddd-file-processing-e2e-smoke.mjs'
  fi
  if matches_owner_queue_filter 'iam-owner' 'WAITING'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=9 owner=iam-owner status=WAITING ready=0 blocked=1 missingArtifacts=1 contentBlockers=0 first=node bin/ddd-rollback-deferral-template.mjs'
  fi
  if matches_owner_queue_filter 'job-owner' 'WAITING'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=10 owner=job-owner status=WAITING ready=0 blocked=2 missingArtifacts=2 contentBlockers=0 first=node bin/ddd-job-e2e-smoke.mjs'
  fi
  if matches_owner_queue_filter 'localization-owner' 'WAITING'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=11 owner=localization-owner status=WAITING ready=0 blocked=1 missingArtifacts=1 contentBlockers=0 first=node bin/ddd-rollback-deferral-template.mjs'
  fi
  if matches_owner_queue_filter 'message-owner' 'WAITING'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=12 owner=message-owner status=WAITING ready=0 blocked=1 missingArtifacts=1 contentBlockers=0 first=node bin/ddd-rollback-deferral-template.mjs'
  fi
  if matches_owner_queue_filter 'payment-owner' 'WAITING'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=13 owner=payment-owner status=WAITING ready=0 blocked=2 missingArtifacts=2 contentBlockers=0 first=node bin/ddd-payment-webhook-e2e-smoke.mjs'
  fi
  if matches_owner_queue_filter 'platform-owner' 'WAITING'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=14 owner=platform-owner status=WAITING ready=0 blocked=1 missingArtifacts=1 contentBlockers=0 first=node bin/ddd-rollback-deferral-template.mjs'
  fi
  if matches_owner_queue_filter 'plugin-owner' 'WAITING'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=15 owner=plugin-owner status=WAITING ready=0 blocked=1 missingArtifacts=1 contentBlockers=0 first=node bin/ddd-rollback-deferral-template.mjs'
  fi
  if matches_owner_queue_filter 'release-owner' 'WAITING'; then
    DDD_FINAL_OWNER_QUEUE_MATCHED=1
    echo '[ddd-final-owner-queue] order=16 owner=release-owner status=WAITING ready=0 blocked=1 missingArtifacts=3 contentBlockers=0 first=node bin/ddd-release-evidence-orchestrator.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_MATCHED}" != "1" ]]; then
    echo "No final owner queue item matched the requested filters." >&2
    exit 1
  fi
  exit 0
fi

if matches_owner_queue_filter 'release-infra' 'ACTIONABLE'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=1 owner=release-infra status=ACTIONABLE'
  echo "commands:"
  echo '- node bin/ddd-release-evidence-orchestrator.mjs'
  echo '- DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs'
  echo '- node bin/ddd-docker-build-evidence.mjs'
  echo '- node bin/ddd-runtime-readiness-smoke.mjs'
  echo '- DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict'
  echo '- node bin/ddd-release-readiness-summary.mjs'
  echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- BASE_URL'
    echo '- DDD_DOCKER_BUILD_STRICT'
    echo '- DDD_DOCKER_COMMAND'
    echo '- DDD_EVIDENCE_ENVIRONMENT'
    echo '- DDD_EVIDENCE_OPERATOR'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- DEPLOY_CHECK_BASE_URL'
    echo '- FRONTEND_BASE_URL'
    echo '- LUMIRA_BASE_URL'
    echo '- PLAYWRIGHT_BASE_URL'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/build/docker-image-evidence.json'
    echo '- artifacts/ddd/readiness/summary.json'
    echo '- artifacts/ddd/release/orchestrator-report.json'
    echo '- artifacts/ddd/release/readiness-summary.json'
    echo '- artifacts/ddd/release/release-evidence-gate.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'BASE_URL' 'DDD_DOCKER_BUILD_STRICT' 'DDD_DOCKER_COMMAND' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DEPLOY_CHECK_BASE_URL' 'FRONTEND_BASE_URL' 'LUMIRA_BASE_URL' 'PLAYWRIGHT_BASE_URL'
  else
    run_owner_queue_command 'release-infra' '1' 'ACTIONABLE' '1' '7' 'node bin/ddd-release-evidence-orchestrator.mjs'
    run_owner_queue_command 'release-infra' '1' 'ACTIONABLE' '2' '7' 'DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs'
    run_owner_queue_command 'release-infra' '1' 'ACTIONABLE' '3' '7' 'node bin/ddd-docker-build-evidence.mjs'
    run_owner_queue_command 'release-infra' '1' 'ACTIONABLE' '4' '7' 'node bin/ddd-runtime-readiness-smoke.mjs'
    run_owner_queue_command 'release-infra' '1' 'ACTIONABLE' '5' '7' 'DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict'
    run_owner_queue_command 'release-infra' '1' 'ACTIONABLE' '6' '7' 'node bin/ddd-release-readiness-summary.mjs'
    run_owner_queue_command 'release-infra' '1' 'ACTIONABLE' '7' '7' 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
  fi
fi

if matches_owner_queue_filter 'lumira-ui' 'ACTIONABLE'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=2 owner=lumira-ui status=ACTIONABLE'
  echo "commands:"
  echo '- DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
  echo '- node bin/ddd-promote-performance-baseline.mjs'
  echo '- DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
  echo '- node bin/ddd-release-readiness-summary.mjs'
  echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- DDD_EVIDENCE_ENVIRONMENT'
    echo '- DDD_FRONTEND_EXPECT_DEPLOYED'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- PLAYWRIGHT_BASE_URL'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/lumira-ui/frontend-smoke.json'
    echo '- artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json'
    echo '- artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json'
    echo '- artifacts/ddd/release/evidence-manifest.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_FRONTEND_EXPECT_DEPLOYED' 'DDD_RELEASE_CANDIDATE' 'PLAYWRIGHT_BASE_URL'
  else
    run_owner_queue_command 'lumira-ui' '2' 'ACTIONABLE' '1' '5' 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
    run_owner_queue_command 'lumira-ui' '2' 'ACTIONABLE' '2' '5' 'node bin/ddd-promote-performance-baseline.mjs'
    run_owner_queue_command 'lumira-ui' '2' 'ACTIONABLE' '3' '5' 'DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
    run_owner_queue_command 'lumira-ui' '2' 'ACTIONABLE' '4' '5' 'node bin/ddd-release-readiness-summary.mjs'
    run_owner_queue_command 'lumira-ui' '2' 'ACTIONABLE' '5' '5' 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
  fi
fi

if matches_owner_queue_filter 'release-performance' 'ACTIONABLE'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=3 owner=release-performance status=ACTIONABLE'
  echo "commands:"
  echo '- node bin/ddd-authenticated-performance-smoke.mjs'
  echo '- node bin/ddd-promote-performance-baseline.mjs'
  echo '- DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh'
  echo '- DDD_AUTH_PERF_STRICT=true node bin/ddd-authenticated-performance-smoke.mjs'
  echo '- DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
  echo '- DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
  echo '- node bin/ddd-release-evidence-gate.mjs'
  echo '- node bin/ddd-release-readiness-summary.mjs'
  echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- BASE_URL'
    echo '- DDD_AUTH_PASSWORD'
    echo '- DDD_AUTH_PERF_BASELINE_ACCEPTED_BY'
    echo '- DDD_AUTH_PERF_BASELINE_ENVIRONMENT'
    echo '- DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT'
    echo '- DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE'
    echo '- DDD_AUTH_PERF_ENVIRONMENT'
    echo '- DDD_AUTH_USERNAME'
    echo '- DDD_EVIDENCE_OPERATOR'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- DEPLOY_CHECK_BASE_URL'
    echo '- LUMIRA_BASE_URL'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/performance/authenticated-runtime-actual.json'
    echo '- artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json'
    echo '- artifacts/ddd/performance/authenticated-runtime-baseline.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'BASE_URL' 'DDD_AUTH_PASSWORD' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE' 'DDD_AUTH_PERF_ENVIRONMENT' 'DDD_AUTH_USERNAME' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DEPLOY_CHECK_BASE_URL' 'LUMIRA_BASE_URL'
  else
    run_owner_queue_command 'release-performance' '3' 'ACTIONABLE' '1' '9' 'node bin/ddd-authenticated-performance-smoke.mjs'
    run_owner_queue_command 'release-performance' '3' 'ACTIONABLE' '2' '9' 'node bin/ddd-promote-performance-baseline.mjs'
    run_owner_queue_command 'release-performance' '3' 'ACTIONABLE' '3' '9' 'DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh'
    run_owner_queue_command 'release-performance' '3' 'ACTIONABLE' '4' '9' 'DDD_AUTH_PERF_STRICT=true node bin/ddd-authenticated-performance-smoke.mjs'
    run_owner_queue_command 'release-performance' '3' 'ACTIONABLE' '5' '9' 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
    run_owner_queue_command 'release-performance' '3' 'ACTIONABLE' '6' '9' 'DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
    run_owner_queue_command 'release-performance' '3' 'ACTIONABLE' '7' '9' 'node bin/ddd-release-evidence-gate.mjs'
    run_owner_queue_command 'release-performance' '3' 'ACTIONABLE' '8' '9' 'node bin/ddd-release-readiness-summary.mjs'
    run_owner_queue_command 'release-performance' '3' 'ACTIONABLE' '9' '9' 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
  fi
fi

if matches_owner_queue_filter 'ai' 'WAITING'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=4 owner=ai status=WAITING'
  echo "commands:"
  echo '- node bin/ddd-ai-runtime-drill.mjs'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- BASE_URL'
    echo '- DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE'
    echo '- DDD_AI_EXPECT_PROVIDER_REMOTE'
    echo '- DEPLOY_CHECK_BASE_URL'
    echo '- LUMIRA_AI_BASE_URL'
    echo '- LUMIRA_AI_OWNER_FILE_BASE_URL'
    echo '- LUMIRA_AI_OWNER_IAM_BASE_URL'
    echo '- LUMIRA_AI_OWNER_PLATFORM_BASE_URL'
    echo '- LUMIRA_AI_PROVIDER'
    echo '- LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY'
    echo '- LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL'
    echo '- LUMIRA_BASE_URL'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/ai/ai-runtime-drill.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'BASE_URL' 'DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE' 'DDD_AI_EXPECT_PROVIDER_REMOTE' 'DEPLOY_CHECK_BASE_URL' 'LUMIRA_AI_BASE_URL' 'LUMIRA_AI_OWNER_FILE_BASE_URL' 'LUMIRA_AI_OWNER_IAM_BASE_URL' 'LUMIRA_AI_OWNER_PLATFORM_BASE_URL' 'LUMIRA_AI_PROVIDER' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL' 'LUMIRA_BASE_URL'
  else
    if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
      echo '[ddd-final-owner-queue][blocked] owner=ai status=WAITING; resolve dependencies before executing this owner queue.' >&2
      exit 1
    fi
    echo '[ddd-final-owner-queue][waiting] owner=ai status=WAITING; use DETAIL or CHECK_ENV for diagnostics.'
  fi
fi

if matches_owner_queue_filter 'ai-owner' 'WAITING'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=5 owner=ai-owner status=WAITING'
  echo "commands:"
  echo '- node bin/ddd-rollback-deferral-template.mjs'
  echo '- DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs'
  echo '- node bin/ddd-rollback-drill-evidence.mjs'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- DDD_EVIDENCE_ENVIRONMENT'
    echo '- DDD_EVIDENCE_OPERATOR'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- DDD_ROLLBACK_DRILL_CHECK_ENV'
    echo '- DDD_ROLLBACK_DRILL_DEFERRAL_FILE'
    echo '- DDD_ROLLBACK_DRILL_FILE'
    echo '- DDD_ROLLBACK_DRILL_HANDOFF_FILE'
    echo '- DDD_ROLLBACK_DRILL_STRICT'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/rollback/rollback-drill.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DDD_ROLLBACK_DRILL_CHECK_ENV' 'DDD_ROLLBACK_DRILL_DEFERRAL_FILE' 'DDD_ROLLBACK_DRILL_FILE' 'DDD_ROLLBACK_DRILL_HANDOFF_FILE' 'DDD_ROLLBACK_DRILL_STRICT'
  else
    if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
      echo '[ddd-final-owner-queue][blocked] owner=ai-owner status=WAITING; resolve dependencies before executing this owner queue.' >&2
      exit 1
    fi
    echo '[ddd-final-owner-queue][waiting] owner=ai-owner status=WAITING; use DETAIL or CHECK_ENV for diagnostics.'
  fi
fi

if matches_owner_queue_filter 'auth-owner' 'WAITING'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=6 owner=auth-owner status=WAITING'
  echo "commands:"
  echo '- node bin/ddd-rollback-deferral-template.mjs'
  echo '- DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs'
  echo '- node bin/ddd-rollback-drill-evidence.mjs'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- DDD_EVIDENCE_ENVIRONMENT'
    echo '- DDD_EVIDENCE_OPERATOR'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- DDD_ROLLBACK_DRILL_CHECK_ENV'
    echo '- DDD_ROLLBACK_DRILL_DEFERRAL_FILE'
    echo '- DDD_ROLLBACK_DRILL_FILE'
    echo '- DDD_ROLLBACK_DRILL_HANDOFF_FILE'
    echo '- DDD_ROLLBACK_DRILL_STRICT'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/rollback/rollback-drill.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DDD_ROLLBACK_DRILL_CHECK_ENV' 'DDD_ROLLBACK_DRILL_DEFERRAL_FILE' 'DDD_ROLLBACK_DRILL_FILE' 'DDD_ROLLBACK_DRILL_HANDOFF_FILE' 'DDD_ROLLBACK_DRILL_STRICT'
  else
    if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
      echo '[ddd-final-owner-queue][blocked] owner=auth-owner status=WAITING; resolve dependencies before executing this owner queue.' >&2
      exit 1
    fi
    echo '[ddd-final-owner-queue][waiting] owner=auth-owner status=WAITING; use DETAIL or CHECK_ENV for diagnostics.'
  fi
fi

if matches_owner_queue_filter 'database' 'WAITING'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=7 owner=database status=WAITING'
  echo "commands:"
  echo '- node bin/ddd-collect-explain.mjs'
  echo '- DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs'
  echo '- node bin/ddd-release-evidence-orchestrator.mjs'
  echo '- DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- DDD_EVIDENCE_OPERATOR'
    echo '- DDD_EXPLAIN_DATABASE'
    echo '- DDD_EXPLAIN_DIR'
    echo '- DDD_EXPLAIN_ENVIRONMENT'
    echo '- DDD_EXPLAIN_STRICT'
    echo '- DDD_MIGRATION_FRESH_DB_EVIDENCE'
    echo '- DDD_MIGRATION_FRESH_DB_VALIDATED'
    echo '- DDD_MIGRATION_UPGRADE_DB_EVIDENCE'
    echo '- DDD_MIGRATION_UPGRADE_DB_VALIDATED'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- MYSQL_CLI'
    echo '- MYSQL_DATABASE'
    echo '- MYSQL_HOST'
    echo '- MYSQL_PASSWORD'
    echo '- MYSQL_PORT'
    echo '- MYSQL_USER'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/release/explain-gate-report.json'
    echo '- artifacts/ddd/release/orchestrator-report.json'
    echo '- artifacts/ddd/release/readiness-summary.json'
    echo '- artifacts/ddd/release/release-evidence-gate.json'
    echo '- tmp/ddd-explain/*.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'DDD_EVIDENCE_OPERATOR' 'DDD_EXPLAIN_DATABASE' 'DDD_EXPLAIN_DIR' 'DDD_EXPLAIN_ENVIRONMENT' 'DDD_EXPLAIN_STRICT' 'DDD_MIGRATION_FRESH_DB_EVIDENCE' 'DDD_MIGRATION_FRESH_DB_VALIDATED' 'DDD_MIGRATION_UPGRADE_DB_EVIDENCE' 'DDD_MIGRATION_UPGRADE_DB_VALIDATED' 'DDD_RELEASE_CANDIDATE' 'MYSQL_CLI' 'MYSQL_DATABASE' 'MYSQL_HOST' 'MYSQL_PASSWORD' 'MYSQL_PORT' 'MYSQL_USER'
  else
    if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
      echo '[ddd-final-owner-queue][blocked] owner=database status=WAITING; resolve dependencies before executing this owner queue.' >&2
      exit 1
    fi
    echo '[ddd-final-owner-queue][waiting] owner=database status=WAITING; use DETAIL or CHECK_ENV for diagnostics.'
  fi
fi

if matches_owner_queue_filter 'file-owner' 'WAITING'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=8 owner=file-owner status=WAITING'
  echo "commands:"
  echo '- node bin/ddd-file-processing-e2e-smoke.mjs'
  echo '- node bin/ddd-rollback-deferral-template.mjs'
  echo '- DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs'
  echo '- node bin/ddd-rollback-drill-evidence.mjs'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- BASE_URL'
    echo '- DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE'
    echo '- DDD_EVIDENCE_ENVIRONMENT'
    echo '- DDD_EVIDENCE_OPERATOR'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- DDD_ROLLBACK_DRILL_CHECK_ENV'
    echo '- DDD_ROLLBACK_DRILL_DEFERRAL_FILE'
    echo '- DDD_ROLLBACK_DRILL_FILE'
    echo '- DDD_ROLLBACK_DRILL_HANDOFF_FILE'
    echo '- DDD_ROLLBACK_DRILL_STRICT'
    echo '- DEPLOY_CHECK_BASE_URL'
    echo '- LUMIRA_BASE_URL'
    echo '- LUMIRA_JOB_INTERNAL_TOKEN'
    echo '- LUMIRA_UPLOAD_STORAGE_ROOT'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/file/file-processing-e2e.json'
    echo '- artifacts/ddd/rollback/rollback-drill.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'BASE_URL' 'DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DDD_ROLLBACK_DRILL_CHECK_ENV' 'DDD_ROLLBACK_DRILL_DEFERRAL_FILE' 'DDD_ROLLBACK_DRILL_FILE' 'DDD_ROLLBACK_DRILL_HANDOFF_FILE' 'DDD_ROLLBACK_DRILL_STRICT' 'DEPLOY_CHECK_BASE_URL' 'LUMIRA_BASE_URL' 'LUMIRA_JOB_INTERNAL_TOKEN' 'LUMIRA_UPLOAD_STORAGE_ROOT'
  else
    if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
      echo '[ddd-final-owner-queue][blocked] owner=file-owner status=WAITING; resolve dependencies before executing this owner queue.' >&2
      exit 1
    fi
    echo '[ddd-final-owner-queue][waiting] owner=file-owner status=WAITING; use DETAIL or CHECK_ENV for diagnostics.'
  fi
fi

if matches_owner_queue_filter 'iam-owner' 'WAITING'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=9 owner=iam-owner status=WAITING'
  echo "commands:"
  echo '- node bin/ddd-rollback-deferral-template.mjs'
  echo '- DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs'
  echo '- node bin/ddd-rollback-drill-evidence.mjs'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- DDD_EVIDENCE_ENVIRONMENT'
    echo '- DDD_EVIDENCE_OPERATOR'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- DDD_ROLLBACK_DRILL_CHECK_ENV'
    echo '- DDD_ROLLBACK_DRILL_DEFERRAL_FILE'
    echo '- DDD_ROLLBACK_DRILL_FILE'
    echo '- DDD_ROLLBACK_DRILL_HANDOFF_FILE'
    echo '- DDD_ROLLBACK_DRILL_STRICT'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/rollback/rollback-drill.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DDD_ROLLBACK_DRILL_CHECK_ENV' 'DDD_ROLLBACK_DRILL_DEFERRAL_FILE' 'DDD_ROLLBACK_DRILL_FILE' 'DDD_ROLLBACK_DRILL_HANDOFF_FILE' 'DDD_ROLLBACK_DRILL_STRICT'
  else
    if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
      echo '[ddd-final-owner-queue][blocked] owner=iam-owner status=WAITING; resolve dependencies before executing this owner queue.' >&2
      exit 1
    fi
    echo '[ddd-final-owner-queue][waiting] owner=iam-owner status=WAITING; use DETAIL or CHECK_ENV for diagnostics.'
  fi
fi

if matches_owner_queue_filter 'job-owner' 'WAITING'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=10 owner=job-owner status=WAITING'
  echo "commands:"
  echo '- node bin/ddd-job-e2e-smoke.mjs'
  echo '- node bin/ddd-rollback-deferral-template.mjs'
  echo '- DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs'
  echo '- node bin/ddd-rollback-drill-evidence.mjs'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- BASE_URL'
    echo '- DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE'
    echo '- DDD_EVIDENCE_ENVIRONMENT'
    echo '- DDD_EVIDENCE_OPERATOR'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- DDD_ROLLBACK_DRILL_CHECK_ENV'
    echo '- DDD_ROLLBACK_DRILL_DEFERRAL_FILE'
    echo '- DDD_ROLLBACK_DRILL_FILE'
    echo '- DDD_ROLLBACK_DRILL_HANDOFF_FILE'
    echo '- DDD_ROLLBACK_DRILL_STRICT'
    echo '- DEPLOY_CHECK_BASE_URL'
    echo '- LUMIRA_BASE_URL'
    echo '- LUMIRA_JOB_INTERNAL_TOKEN'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/jobs/job-e2e-smoke.json'
    echo '- artifacts/ddd/rollback/rollback-drill.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'BASE_URL' 'DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DDD_ROLLBACK_DRILL_CHECK_ENV' 'DDD_ROLLBACK_DRILL_DEFERRAL_FILE' 'DDD_ROLLBACK_DRILL_FILE' 'DDD_ROLLBACK_DRILL_HANDOFF_FILE' 'DDD_ROLLBACK_DRILL_STRICT' 'DEPLOY_CHECK_BASE_URL' 'LUMIRA_BASE_URL' 'LUMIRA_JOB_INTERNAL_TOKEN'
  else
    if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
      echo '[ddd-final-owner-queue][blocked] owner=job-owner status=WAITING; resolve dependencies before executing this owner queue.' >&2
      exit 1
    fi
    echo '[ddd-final-owner-queue][waiting] owner=job-owner status=WAITING; use DETAIL or CHECK_ENV for diagnostics.'
  fi
fi

if matches_owner_queue_filter 'localization-owner' 'WAITING'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=11 owner=localization-owner status=WAITING'
  echo "commands:"
  echo '- node bin/ddd-rollback-deferral-template.mjs'
  echo '- DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs'
  echo '- node bin/ddd-rollback-drill-evidence.mjs'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- DDD_EVIDENCE_ENVIRONMENT'
    echo '- DDD_EVIDENCE_OPERATOR'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- DDD_ROLLBACK_DRILL_CHECK_ENV'
    echo '- DDD_ROLLBACK_DRILL_DEFERRAL_FILE'
    echo '- DDD_ROLLBACK_DRILL_FILE'
    echo '- DDD_ROLLBACK_DRILL_HANDOFF_FILE'
    echo '- DDD_ROLLBACK_DRILL_STRICT'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/rollback/rollback-drill.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DDD_ROLLBACK_DRILL_CHECK_ENV' 'DDD_ROLLBACK_DRILL_DEFERRAL_FILE' 'DDD_ROLLBACK_DRILL_FILE' 'DDD_ROLLBACK_DRILL_HANDOFF_FILE' 'DDD_ROLLBACK_DRILL_STRICT'
  else
    if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
      echo '[ddd-final-owner-queue][blocked] owner=localization-owner status=WAITING; resolve dependencies before executing this owner queue.' >&2
      exit 1
    fi
    echo '[ddd-final-owner-queue][waiting] owner=localization-owner status=WAITING; use DETAIL or CHECK_ENV for diagnostics.'
  fi
fi

if matches_owner_queue_filter 'message-owner' 'WAITING'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=12 owner=message-owner status=WAITING'
  echo "commands:"
  echo '- node bin/ddd-rollback-deferral-template.mjs'
  echo '- DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs'
  echo '- node bin/ddd-rollback-drill-evidence.mjs'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- DDD_EVIDENCE_ENVIRONMENT'
    echo '- DDD_EVIDENCE_OPERATOR'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- DDD_ROLLBACK_DRILL_CHECK_ENV'
    echo '- DDD_ROLLBACK_DRILL_DEFERRAL_FILE'
    echo '- DDD_ROLLBACK_DRILL_FILE'
    echo '- DDD_ROLLBACK_DRILL_HANDOFF_FILE'
    echo '- DDD_ROLLBACK_DRILL_STRICT'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/rollback/rollback-drill.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DDD_ROLLBACK_DRILL_CHECK_ENV' 'DDD_ROLLBACK_DRILL_DEFERRAL_FILE' 'DDD_ROLLBACK_DRILL_FILE' 'DDD_ROLLBACK_DRILL_HANDOFF_FILE' 'DDD_ROLLBACK_DRILL_STRICT'
  else
    if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
      echo '[ddd-final-owner-queue][blocked] owner=message-owner status=WAITING; resolve dependencies before executing this owner queue.' >&2
      exit 1
    fi
    echo '[ddd-final-owner-queue][waiting] owner=message-owner status=WAITING; use DETAIL or CHECK_ENV for diagnostics.'
  fi
fi

if matches_owner_queue_filter 'payment-owner' 'WAITING'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=13 owner=payment-owner status=WAITING'
  echo "commands:"
  echo '- node bin/ddd-payment-webhook-e2e-smoke.mjs'
  echo '- node bin/ddd-rollback-deferral-template.mjs'
  echo '- DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs'
  echo '- node bin/ddd-rollback-drill-evidence.mjs'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- DDD_EVIDENCE_ENVIRONMENT'
    echo '- DDD_EVIDENCE_OPERATOR'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- DDD_ROLLBACK_DRILL_CHECK_ENV'
    echo '- DDD_ROLLBACK_DRILL_DEFERRAL_FILE'
    echo '- DDD_ROLLBACK_DRILL_FILE'
    echo '- DDD_ROLLBACK_DRILL_HANDOFF_FILE'
    echo '- DDD_ROLLBACK_DRILL_STRICT'
    echo '- LUMIRA_BASE_URL'
    echo '- PAYMENT_PUBLIC_BASE_URL'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/payment/payment-webhook-e2e.json'
    echo '- artifacts/ddd/rollback/rollback-drill.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DDD_ROLLBACK_DRILL_CHECK_ENV' 'DDD_ROLLBACK_DRILL_DEFERRAL_FILE' 'DDD_ROLLBACK_DRILL_FILE' 'DDD_ROLLBACK_DRILL_HANDOFF_FILE' 'DDD_ROLLBACK_DRILL_STRICT' 'LUMIRA_BASE_URL' 'PAYMENT_PUBLIC_BASE_URL'
  else
    if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
      echo '[ddd-final-owner-queue][blocked] owner=payment-owner status=WAITING; resolve dependencies before executing this owner queue.' >&2
      exit 1
    fi
    echo '[ddd-final-owner-queue][waiting] owner=payment-owner status=WAITING; use DETAIL or CHECK_ENV for diagnostics.'
  fi
fi

if matches_owner_queue_filter 'platform-owner' 'WAITING'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=14 owner=platform-owner status=WAITING'
  echo "commands:"
  echo '- node bin/ddd-rollback-deferral-template.mjs'
  echo '- DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs'
  echo '- node bin/ddd-rollback-drill-evidence.mjs'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- DDD_EVIDENCE_ENVIRONMENT'
    echo '- DDD_EVIDENCE_OPERATOR'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- DDD_ROLLBACK_DRILL_CHECK_ENV'
    echo '- DDD_ROLLBACK_DRILL_DEFERRAL_FILE'
    echo '- DDD_ROLLBACK_DRILL_FILE'
    echo '- DDD_ROLLBACK_DRILL_HANDOFF_FILE'
    echo '- DDD_ROLLBACK_DRILL_STRICT'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/rollback/rollback-drill.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DDD_ROLLBACK_DRILL_CHECK_ENV' 'DDD_ROLLBACK_DRILL_DEFERRAL_FILE' 'DDD_ROLLBACK_DRILL_FILE' 'DDD_ROLLBACK_DRILL_HANDOFF_FILE' 'DDD_ROLLBACK_DRILL_STRICT'
  else
    if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
      echo '[ddd-final-owner-queue][blocked] owner=platform-owner status=WAITING; resolve dependencies before executing this owner queue.' >&2
      exit 1
    fi
    echo '[ddd-final-owner-queue][waiting] owner=platform-owner status=WAITING; use DETAIL or CHECK_ENV for diagnostics.'
  fi
fi

if matches_owner_queue_filter 'plugin-owner' 'WAITING'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=15 owner=plugin-owner status=WAITING'
  echo "commands:"
  echo '- node bin/ddd-rollback-deferral-template.mjs'
  echo '- DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs'
  echo '- node bin/ddd-rollback-drill-evidence.mjs'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- DDD_EVIDENCE_ENVIRONMENT'
    echo '- DDD_EVIDENCE_OPERATOR'
    echo '- DDD_RELEASE_CANDIDATE'
    echo '- DDD_ROLLBACK_DRILL_CHECK_ENV'
    echo '- DDD_ROLLBACK_DRILL_DEFERRAL_FILE'
    echo '- DDD_ROLLBACK_DRILL_FILE'
    echo '- DDD_ROLLBACK_DRILL_HANDOFF_FILE'
    echo '- DDD_ROLLBACK_DRILL_STRICT'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/rollback/rollback-drill.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DDD_ROLLBACK_DRILL_CHECK_ENV' 'DDD_ROLLBACK_DRILL_DEFERRAL_FILE' 'DDD_ROLLBACK_DRILL_FILE' 'DDD_ROLLBACK_DRILL_HANDOFF_FILE' 'DDD_ROLLBACK_DRILL_STRICT'
  else
    if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
      echo '[ddd-final-owner-queue][blocked] owner=plugin-owner status=WAITING; resolve dependencies before executing this owner queue.' >&2
      exit 1
    fi
    echo '[ddd-final-owner-queue][waiting] owner=plugin-owner status=WAITING; use DETAIL or CHECK_ENV for diagnostics.'
  fi
fi

if matches_owner_queue_filter 'release-owner' 'WAITING'; then
  DDD_FINAL_OWNER_QUEUE_MATCHED=1
  echo '[ddd-final-owner-queue] order=16 owner=release-owner status=WAITING'
  echo "commands:"
  echo '- node bin/ddd-release-evidence-orchestrator.mjs'
  echo '- DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict'
  if [[ "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "1" || "${DDD_FINAL_OWNER_QUEUE_DETAIL}" == "true" ]]; then
    echo "envKeys:"
    echo '- DDD_RELEASE_EVIDENCE_STRICT'
    echo "missingArtifacts:"
    echo '- artifacts/ddd/release/orchestrator-report.json'
    echo '- artifacts/ddd/release/readiness-summary.json'
    echo '- artifacts/ddd/release/release-evidence-gate.json'
    echo "contentBlockers:"
    echo "rerunCommands:"
    echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
    echo '- bash artifacts/ddd/release/release-artifact-integrity-gate.sh'
    echo '- bash artifacts/ddd/release/release-preflight-gate.sh'
    echo '- node bin/ddd-release-evidence-gate.mjs'
    echo '- node bin/ddd-release-readiness-summary.mjs'
  fi
  if [[ "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "1" || "${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}" == "true" ]]; then
    check_owner_queue_env 'DDD_RELEASE_EVIDENCE_STRICT'
  else
    if [[ "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "1" || "${DDD_FINAL_OWNER_QUEUE_EXECUTE}" == "true" ]]; then
      echo '[ddd-final-owner-queue][blocked] owner=release-owner status=WAITING; resolve dependencies before executing this owner queue.' >&2
      exit 1
    fi
    echo '[ddd-final-owner-queue][waiting] owner=release-owner status=WAITING; use DETAIL or CHECK_ENV for diagnostics.'
  fi
fi

if [[ "${DDD_FINAL_OWNER_QUEUE_MATCHED}" != "1" ]]; then
  echo "No actionable final owner queue item matched the requested filters." >&2
  exit 1
fi

# After owner commands refresh evidence, rerun:
run_owner_queue_command 'post-run' '0' 'POST_RUN' '1' '3' 'node bin/ddd-release-evidence-gate.mjs'
run_owner_queue_command 'post-run' '0' 'POST_RUN' '2' '3' 'node bin/ddd-release-readiness-summary.mjs'
run_owner_queue_command 'post-run' '0' 'POST_RUN' '3' '3' 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
if [[ "${DDD_FINAL_OWNER_QUEUE_COMMAND_FAILURES}" != "0" ]]; then
  echo "[ddd-final-owner-queue][completed-with-failures] commandFailures=${DDD_FINAL_OWNER_QUEUE_COMMAND_FAILURES}" >&2
  exit 1
fi
