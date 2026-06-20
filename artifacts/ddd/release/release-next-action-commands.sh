#!/usr/bin/env bash
set -euo pipefail

# Lumira DDD release next-action commands.
# Generated at: 2026-06-19T18:19:45.629Z
# Status: NOT_READY
# Release gate blockers: 94
# Default mode lists RUN_NOW items. Set DDD_RELEASE_NEXT_ACTION_EXECUTE=1 to execute commands.
# Use DDD_RELEASE_NEXT_ACTION_ORDER or DDD_RELEASE_NEXT_ACTION_OWNER to narrow execution.
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

DDD_RELEASE_NEXT_ACTION_ORDER="${DDD_RELEASE_NEXT_ACTION_ORDER:-}"
DDD_RELEASE_NEXT_ACTION_OWNER="${DDD_RELEASE_NEXT_ACTION_OWNER:-}"
DDD_RELEASE_NEXT_ACTION_LIST="${DDD_RELEASE_NEXT_ACTION_LIST:-}"
DDD_RELEASE_NEXT_ACTION_DETAIL="${DDD_RELEASE_NEXT_ACTION_DETAIL:-}"
DDD_RELEASE_NEXT_ACTION_CHECK_ENV="${DDD_RELEASE_NEXT_ACTION_CHECK_ENV:-}"
DDD_RELEASE_NEXT_ACTION_EXECUTE="${DDD_RELEASE_NEXT_ACTION_EXECUTE:-}"
DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR="${DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR:-}"
DDD_RELEASE_NEXT_ACTION_REPORT="${DDD_RELEASE_NEXT_ACTION_REPORT:-artifacts/ddd/release/release-next-action-run-report.json}"
DDD_RELEASE_NEXT_ACTION_REPORT_TMP="${DDD_RELEASE_NEXT_ACTION_REPORT}.jsonl.$$"
DDD_RELEASE_NEXT_ACTION_REPORT_FINALIZED=0
DDD_RELEASE_NEXT_ACTION_MATCHED=0
DDD_RELEASE_NEXT_ACTION_ENV_LOADED=0
DDD_RELEASE_NEXT_ACTION_COMMAND_FAILURES=0
safe_load_release_env_file() {
  local exports
  if ! exports=$(node --input-type=module -e 'import fs from '\''node:fs'\''; import path from '\''node:path'\''; const [file, permissionCheckedArg] = process.argv.slice(1); const templateNames = new Set(['\''release-env-missing.template.env'\'', '\''release-closure-wave-env.template.env'\'', '\''release-final-owner-queue-env.template.env'\'', '\''release-env-canonical-fill.template.env'\'']); if (templateNames.has(path.basename(file))) {   console.error(`[ddd-release-env][template-refused] file=${file}`);   process.exit(1); } const permissionAlreadyChecked = permissionCheckedArg === '\''1'\'' || permissionCheckedArg === '\''true'\'' || process.env.DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED === '\''1'\'' || process.env.DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED === '\''true'\''; const mode = permissionAlreadyChecked ? 0o600 : fs.statSync(file).mode & 0o777; if (!permissionAlreadyChecked && (mode & 0o077) !== 0) {   console.error(`[ddd-release-env][permission-refused] file=${file} mode=${mode.toString(8).padStart(3, '\''0'\'')} required=600`);   process.exit(1); } const text = fs.readFileSync(file, '\''utf8'\''); const quote = (value) => `'\''${String(value).replace(/'\''/g, `'\''\\'\'''\''`)}'\''`; let lineNumber = 0; for (const line of text.split(/\r?\n/)) {   lineNumber += 1;   const trimmed = line.trim();   if (!trimmed || trimmed.startsWith('\''#'\'')) continue;   const match = trimmed.match(/^(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$/);   if (!match) {     console.error(`[ddd-release-env][env-invalid] line=${lineNumber}`);     process.exit(1);   }   let value = match[2].trim();   const quoted = value.match(/^(['\''\"])(.*)\1$/s);   if (quoted) value = quoted[2];   console.log(`export ${match[1]}=${quote(value)}`); }' "$DDD_RELEASE_ENV_FILE" "${DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED:-}"); then
    return 1
  fi
  eval "${exports}"
}
if [[ "${DDD_RELEASE_NEXT_ACTION_EXECUTE}" == "1" || "${DDD_RELEASE_NEXT_ACTION_EXECUTE}" == "true" || "${DDD_RELEASE_NEXT_ACTION_CHECK_ENV}" == "1" || "${DDD_RELEASE_NEXT_ACTION_CHECK_ENV}" == "true" ]]; then
  if [[ -z "${DDD_RELEASE_ENV_FILE:-}" ]]; then
    echo "DDD_RELEASE_ENV_FILE is required when executing or checking release next-action env." >&2
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
  safe_load_release_env_file
  DDD_RELEASE_NEXT_ACTION_ENV_LOADED=1
fi
check_next_action_env() {
  local order="$1"
  local owner="$2"
  shift 2
  local missing=0
  local key
  if [[ "$#" -eq 0 ]]; then
    echo "[ddd-release-next-action][env-ok] order=${order} owner=${owner} requiredEnv=none"
    return 0
  fi
  for key in "$@"; do
    if [[ -z "${!key:-}" ]]; then
      echo "[ddd-release-next-action][env-missing] order=${order} owner=${owner} key=${key}" >&2
      missing=1
    fi
  done
  if [[ "${missing}" == "0" ]]; then
    echo "[ddd-release-next-action][env-ok] order=${order} owner=${owner}"
  fi
  return "${missing}"
}
append_next_action_report_entry() {
  local order="$1"
  local owner="$2"
  local receipt_status="$3"
  local command="$4"
  local status="$5"
  local duration_ms="$6"
  if [[ "${DDD_RELEASE_NEXT_ACTION_EXECUTE}" != "1" && "${DDD_RELEASE_NEXT_ACTION_EXECUTE}" != "true" ]]; then return 0; fi
  node --input-type=module -e 'import fs from "node:fs"; const [file, order, owner, receiptStatus, command, status, durationMs] = process.argv.slice(1); fs.appendFileSync(file, `${JSON.stringify({ order: Number(order), owner, receiptStatus, command, status: Number(status), durationMs: Number(durationMs), finishedAt: new Date().toISOString() })}\n`);' "${DDD_RELEASE_NEXT_ACTION_REPORT_TMP}" "${order}" "${owner}" "${receipt_status}" "${command}" "${status}" "${duration_ms}"
}
finalize_next_action_report() {
  local exit_code="$1"
  if [[ "${DDD_RELEASE_NEXT_ACTION_EXECUTE}" != "1" && "${DDD_RELEASE_NEXT_ACTION_EXECUTE}" != "true" ]]; then return 0; fi
  if [[ "${DDD_RELEASE_NEXT_ACTION_REPORT_FINALIZED}" == "1" ]]; then return "${exit_code}"; fi
  DDD_RELEASE_NEXT_ACTION_REPORT_FINALIZED=1
  mkdir -p "$(dirname "${DDD_RELEASE_NEXT_ACTION_REPORT}")"
  node --input-type=module -e 'import fs from "node:fs"; const [tmp, out, exitCode, ownerFilter, orderFilter] = process.argv.slice(1); const entries = fs.existsSync(tmp) ? fs.readFileSync(tmp, "utf8").split("\n").filter(Boolean).map((line) => JSON.parse(line)) : []; const exit = Number(exitCode); const failedEntries = entries.filter((entry) => Number(entry.status) !== 0).length; const summary = { totalEntries: entries.length, succeededEntries: entries.length - failedEntries, failedEntries }; fs.writeFileSync(out, `${JSON.stringify({ generatedAt: new Date().toISOString(), reportStatus: exit === 0 ? "PASS" : "FAIL", exitCode: exit, ownerFilter: ownerFilter || null, orderFilter: orderFilter || null, summary, entries }, null, 2)}\n`); if (fs.existsSync(tmp)) fs.rmSync(tmp);' "${DDD_RELEASE_NEXT_ACTION_REPORT_TMP}" "${DDD_RELEASE_NEXT_ACTION_REPORT}" "${exit_code}" "${DDD_RELEASE_NEXT_ACTION_OWNER}" "${DDD_RELEASE_NEXT_ACTION_ORDER}"
  if ! DDD_RELEASE_NEXT_ACTION_REPORT="${DDD_RELEASE_NEXT_ACTION_REPORT}" node bin/ddd-release-next-action-run-report-contract.mjs; then
    echo "[ddd-release-next-action][report-contract] failed" >&2
    return 1
  fi
  echo "[ddd-release-next-action][report] ${DDD_RELEASE_NEXT_ACTION_REPORT}"
  return "${exit_code}"
}
trap 'status=$?; finalize_next_action_report "${status}"; exit "${status}"' EXIT
run_next_action_command() {
  local order="$1"
  local owner="$2"
  local receipt_status="$3"
  local command="$4"
  if [[ "${DDD_RELEASE_NEXT_ACTION_EXECUTE}" != "1" && "${DDD_RELEASE_NEXT_ACTION_EXECUTE}" != "true" ]]; then
    echo "[ddd-release-next-action][dry-run] ${command}"
    return 0
  fi
  local started_ms
  started_ms=$(node -e 'console.log(Date.now())')
  set +e
  bash -lc "${command}"
  local status=$?
  set -e
  local finished_ms
  finished_ms=$(node -e 'console.log(Date.now())')
  append_next_action_report_entry "${order}" "${owner}" "${receipt_status}" "${command}" "${status}" "$((finished_ms - started_ms))"
  if [[ "${status}" != "0" ]]; then
    echo "[ddd-release-next-action][command-failed] status=${status} command=${command}" >&2
    DDD_RELEASE_NEXT_ACTION_COMMAND_FAILURES=$((DDD_RELEASE_NEXT_ACTION_COMMAND_FAILURES + 1))
    if [[ "${DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR}" == "1" || "${DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR}" == "true" ]]; then
      echo "[ddd-release-next-action][command-failed] continuing because DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR=${DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR}" >&2
      return 0
    fi
    return "${status}"
  fi
  return 0
}
maybe_run_next_action() {
  local order="$1"
  local owner="$2"
  local receipt_status="$3"
  local next_action="$4"
  if [[ -n "${DDD_RELEASE_NEXT_ACTION_ORDER}" && "${DDD_RELEASE_NEXT_ACTION_ORDER}" != "${order}" ]]; then
    return 0
  fi
  if [[ -n "${DDD_RELEASE_NEXT_ACTION_OWNER}" && "${DDD_RELEASE_NEXT_ACTION_OWNER}" != "${owner}" ]]; then
    return 0
  fi
  DDD_RELEASE_NEXT_ACTION_MATCHED=1
  echo "[ddd-release-next-action] order=${order} owner=${owner} receiptStatus=${receipt_status}"
  echo "[ddd-release-next-action] next=${next_action}"
}

if [[ "${DDD_RELEASE_NEXT_ACTION_LIST}" == "1" || "${DDD_RELEASE_NEXT_ACTION_LIST}" == "true" ]]; then
  DDD_RELEASE_NEXT_ACTION_LIST_MATCHED=0
  echo "RUN_NOW release next actions:"
  if [[ ( -z "${DDD_RELEASE_NEXT_ACTION_ORDER}" || "${DDD_RELEASE_NEXT_ACTION_ORDER}" == '1' ) && ( -z "${DDD_RELEASE_NEXT_ACTION_OWNER}" || "${DDD_RELEASE_NEXT_ACTION_OWNER}" == 'release-infra' ) ]]; then
    echo '1 owner=release-infra receiptStatus=CONTENT_BLOCKED next=Regenerate runtime readiness against an HTTPS non-local production-equivalent backend URL so the artifact includes structured `productionEquivalence` evidence.'
    DDD_RELEASE_NEXT_ACTION_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_NEXT_ACTION_ORDER}" || "${DDD_RELEASE_NEXT_ACTION_ORDER}" == '2' ) && ( -z "${DDD_RELEASE_NEXT_ACTION_OWNER}" || "${DDD_RELEASE_NEXT_ACTION_OWNER}" == 'release-performance' ) ]]; then
    echo '2 owner=release-performance receiptStatus=CONTENT_BLOCKED next=Regenerate authenticated performance actual against an HTTPS non-local production-equivalent backend URL, then rerun baseline comparison or promotion.'
    DDD_RELEASE_NEXT_ACTION_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_NEXT_ACTION_ORDER}" || "${DDD_RELEASE_NEXT_ACTION_ORDER}" == '3' ) && ( -z "${DDD_RELEASE_NEXT_ACTION_OWNER}" || "${DDD_RELEASE_NEXT_ACTION_OWNER}" == 'lumira-ui' ) ]]; then
    echo '3 owner=lumira-ui receiptStatus=ARTIFACT_MISSING next=Run deployed frontend smoke with HTTPS `PLAYWRIGHT_BASE_URL`, `DDD_FRONTEND_EXPECT_DEPLOYED=true`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR`; then convert it with `node bin/ddd-frontend-smoke-evidence.mjs`.'
    DDD_RELEASE_NEXT_ACTION_LIST_MATCHED=1
  fi
  if [[ "${DDD_RELEASE_NEXT_ACTION_LIST_MATCHED}" != "1" ]]; then
    echo "No RUN_NOW next action matched DDD_RELEASE_NEXT_ACTION_ORDER=${DDD_RELEASE_NEXT_ACTION_ORDER} DDD_RELEASE_NEXT_ACTION_OWNER=${DDD_RELEASE_NEXT_ACTION_OWNER}" >&2
    exit 1
  fi
  exit 0
fi

if [[ "${DDD_RELEASE_NEXT_ACTION_DETAIL}" == "1" || "${DDD_RELEASE_NEXT_ACTION_DETAIL}" == "true" ]]; then
  DDD_RELEASE_NEXT_ACTION_DETAIL_MATCHED=0
  if [[ ( -z "${DDD_RELEASE_NEXT_ACTION_ORDER}" || "${DDD_RELEASE_NEXT_ACTION_ORDER}" == '1' ) && ( -z "${DDD_RELEASE_NEXT_ACTION_OWNER}" || "${DDD_RELEASE_NEXT_ACTION_OWNER}" == 'release-infra' ) ]]; then
    DDD_RELEASE_NEXT_ACTION_DETAIL_MATCHED=1
    echo 'order=1'
    echo 'owner=release-infra'
    echo 'receiptStatus=CONTENT_BLOCKED'
    echo 'next=Regenerate runtime readiness against an HTTPS non-local production-equivalent backend URL so the artifact includes structured `productionEquivalence` evidence.'
    echo 'reason=strictGate=runtime-readiness-summary runtime readiness productionEquivalence.strict must be true for strict release evidence'
    echo 'readyBatches=p0-docker-release-infra;p0-runtime-readiness-release-infra'
    echo 'blockedBatches=p3-orchestrator-release-infra'
    echo 'missingArtifacts=none'
    echo 'envKeys=BASE_URL;DEPLOY_CHECK_BASE_URL;LUMIRA_BASE_URL'
    echo "commands:"
    echo '- DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh'
    echo '- DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh'
    echo '- DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh'
    echo '- DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh'
  fi
  if [[ ( -z "${DDD_RELEASE_NEXT_ACTION_ORDER}" || "${DDD_RELEASE_NEXT_ACTION_ORDER}" == '2' ) && ( -z "${DDD_RELEASE_NEXT_ACTION_OWNER}" || "${DDD_RELEASE_NEXT_ACTION_OWNER}" == 'release-performance' ) ]]; then
    DDD_RELEASE_NEXT_ACTION_DETAIL_MATCHED=1
    echo 'order=2'
    echo 'owner=release-performance'
    echo 'receiptStatus=CONTENT_BLOCKED'
    echo 'next=Regenerate authenticated performance actual against an HTTPS non-local production-equivalent backend URL, then rerun baseline comparison or promotion.'
    echo 'reason=strictGate=authenticated-performance-shape authenticated performance actual productionEquivalence.strict must be true for strict release evidence'
    echo 'readyBatches=p0-authenticated-performance-release-performance'
    echo 'blockedBatches=none'
    echo 'missingArtifacts=none'
    echo 'envKeys=none'
    echo "commands:"
    echo '- node bin/ddd-authenticated-performance-smoke.mjs'
    echo '- node bin/ddd-promote-performance-baseline.mjs'
    echo '- DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh'
    echo '- DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh'
    echo '- DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh'
    echo '- DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh'
  fi
  if [[ ( -z "${DDD_RELEASE_NEXT_ACTION_ORDER}" || "${DDD_RELEASE_NEXT_ACTION_ORDER}" == '3' ) && ( -z "${DDD_RELEASE_NEXT_ACTION_OWNER}" || "${DDD_RELEASE_NEXT_ACTION_OWNER}" == 'lumira-ui' ) ]]; then
    DDD_RELEASE_NEXT_ACTION_DETAIL_MATCHED=1
    echo 'order=3'
    echo 'owner=lumira-ui'
    echo 'receiptStatus=ARTIFACT_MISSING'
    echo 'next=Run deployed frontend smoke with HTTPS `PLAYWRIGHT_BASE_URL`, `DDD_FRONTEND_EXPECT_DEPLOYED=true`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR`; then convert it with `node bin/ddd-frontend-smoke-evidence.mjs`.'
    echo 'reason=strictGate=frontend-smoke-freshness generatedAt is 57.8h old; limit=24h'
    echo 'readyBatches=p0-manifest-lumira-ui'
    echo 'blockedBatches=none'
    echo 'missingArtifacts=artifacts/ddd/lumira-ui/frontend-smoke.json;artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json;artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json'
    echo 'envKeys=DDD_EVIDENCE_ENVIRONMENT;DDD_FRONTEND_EXPECT_DEPLOYED;DDD_RELEASE_CANDIDATE;PLAYWRIGHT_BASE_URL'
    echo "commands:"
    echo '- DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh'
    echo '- DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh'
    echo '- DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh'
    echo '- DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh'
  fi
  if [[ "${DDD_RELEASE_NEXT_ACTION_DETAIL_MATCHED}" != "1" ]]; then
    echo "No RUN_NOW next action matched DDD_RELEASE_NEXT_ACTION_ORDER=${DDD_RELEASE_NEXT_ACTION_ORDER} DDD_RELEASE_NEXT_ACTION_OWNER=${DDD_RELEASE_NEXT_ACTION_OWNER}" >&2
    exit 1
  fi
  exit 0
fi

if [[ "${DDD_RELEASE_NEXT_ACTION_CHECK_ENV}" == "1" || "${DDD_RELEASE_NEXT_ACTION_CHECK_ENV}" == "true" ]]; then
  DDD_RELEASE_NEXT_ACTION_ENV_CHECK_MATCHED=0
  DDD_RELEASE_NEXT_ACTION_ENV_CHECK_FAILED=0
  if [[ ( -z "${DDD_RELEASE_NEXT_ACTION_ORDER}" || "${DDD_RELEASE_NEXT_ACTION_ORDER}" == '1' ) && ( -z "${DDD_RELEASE_NEXT_ACTION_OWNER}" || "${DDD_RELEASE_NEXT_ACTION_OWNER}" == 'release-infra' ) ]]; then
    DDD_RELEASE_NEXT_ACTION_ENV_CHECK_MATCHED=1
    check_next_action_env '1' 'release-infra' 'BASE_URL' 'DEPLOY_CHECK_BASE_URL' 'LUMIRA_BASE_URL' || DDD_RELEASE_NEXT_ACTION_ENV_CHECK_FAILED=1
  fi
  if [[ ( -z "${DDD_RELEASE_NEXT_ACTION_ORDER}" || "${DDD_RELEASE_NEXT_ACTION_ORDER}" == '2' ) && ( -z "${DDD_RELEASE_NEXT_ACTION_OWNER}" || "${DDD_RELEASE_NEXT_ACTION_OWNER}" == 'release-performance' ) ]]; then
    DDD_RELEASE_NEXT_ACTION_ENV_CHECK_MATCHED=1
    check_next_action_env '2' 'release-performance' || DDD_RELEASE_NEXT_ACTION_ENV_CHECK_FAILED=1
  fi
  if [[ ( -z "${DDD_RELEASE_NEXT_ACTION_ORDER}" || "${DDD_RELEASE_NEXT_ACTION_ORDER}" == '3' ) && ( -z "${DDD_RELEASE_NEXT_ACTION_OWNER}" || "${DDD_RELEASE_NEXT_ACTION_OWNER}" == 'lumira-ui' ) ]]; then
    DDD_RELEASE_NEXT_ACTION_ENV_CHECK_MATCHED=1
    check_next_action_env '3' 'lumira-ui' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_FRONTEND_EXPECT_DEPLOYED' 'DDD_RELEASE_CANDIDATE' 'PLAYWRIGHT_BASE_URL' || DDD_RELEASE_NEXT_ACTION_ENV_CHECK_FAILED=1
  fi
  if [[ "${DDD_RELEASE_NEXT_ACTION_ENV_CHECK_MATCHED}" != "1" ]]; then
    echo "No RUN_NOW next action matched DDD_RELEASE_NEXT_ACTION_ORDER=${DDD_RELEASE_NEXT_ACTION_ORDER} DDD_RELEASE_NEXT_ACTION_OWNER=${DDD_RELEASE_NEXT_ACTION_OWNER}" >&2
    exit 1
  fi
  if [[ "${DDD_RELEASE_NEXT_ACTION_ENV_CHECK_FAILED}" != "0" ]]; then
    exit 1
  fi
  exit 0
fi

maybe_run_next_action '1' 'release-infra' 'CONTENT_BLOCKED' 'Regenerate runtime readiness against an HTTPS non-local production-equivalent backend URL so the artifact includes structured `productionEquivalence` evidence.'
if [[ "${DDD_RELEASE_NEXT_ACTION_MATCHED}" == "1" && ( -z "${DDD_RELEASE_NEXT_ACTION_ORDER}" || "${DDD_RELEASE_NEXT_ACTION_ORDER}" == '1' ) && ( -z "${DDD_RELEASE_NEXT_ACTION_OWNER}" || "${DDD_RELEASE_NEXT_ACTION_OWNER}" == 'release-infra' ) ]]; then
# -----
# Reason: strictGate=runtime-readiness-summary runtime readiness productionEquivalence.strict must be true for strict release evidence
  run_next_action_command '1' 'release-infra' 'CONTENT_BLOCKED' 'DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh'
  run_next_action_command '1' 'release-infra' 'CONTENT_BLOCKED' 'DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh'
  run_next_action_command '1' 'release-infra' 'CONTENT_BLOCKED' 'DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh'
  run_next_action_command '1' 'release-infra' 'CONTENT_BLOCKED' 'DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh'
fi

maybe_run_next_action '2' 'release-performance' 'CONTENT_BLOCKED' 'Regenerate authenticated performance actual against an HTTPS non-local production-equivalent backend URL, then rerun baseline comparison or promotion.'
if [[ "${DDD_RELEASE_NEXT_ACTION_MATCHED}" == "1" && ( -z "${DDD_RELEASE_NEXT_ACTION_ORDER}" || "${DDD_RELEASE_NEXT_ACTION_ORDER}" == '2' ) && ( -z "${DDD_RELEASE_NEXT_ACTION_OWNER}" || "${DDD_RELEASE_NEXT_ACTION_OWNER}" == 'release-performance' ) ]]; then
# -----
# Reason: strictGate=authenticated-performance-shape authenticated performance actual productionEquivalence.strict must be true for strict release evidence
  run_next_action_command '2' 'release-performance' 'CONTENT_BLOCKED' 'node bin/ddd-authenticated-performance-smoke.mjs'
  run_next_action_command '2' 'release-performance' 'CONTENT_BLOCKED' 'node bin/ddd-promote-performance-baseline.mjs'
  run_next_action_command '2' 'release-performance' 'CONTENT_BLOCKED' 'DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh'
  run_next_action_command '2' 'release-performance' 'CONTENT_BLOCKED' 'DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh'
  run_next_action_command '2' 'release-performance' 'CONTENT_BLOCKED' 'DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh'
  run_next_action_command '2' 'release-performance' 'CONTENT_BLOCKED' 'DDD_RELEASE_OWNER=release-performance DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh'
fi

maybe_run_next_action '3' 'lumira-ui' 'ARTIFACT_MISSING' 'Run deployed frontend smoke with HTTPS `PLAYWRIGHT_BASE_URL`, `DDD_FRONTEND_EXPECT_DEPLOYED=true`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR`; then convert it with `node bin/ddd-frontend-smoke-evidence.mjs`.'
if [[ "${DDD_RELEASE_NEXT_ACTION_MATCHED}" == "1" && ( -z "${DDD_RELEASE_NEXT_ACTION_ORDER}" || "${DDD_RELEASE_NEXT_ACTION_ORDER}" == '3' ) && ( -z "${DDD_RELEASE_NEXT_ACTION_OWNER}" || "${DDD_RELEASE_NEXT_ACTION_OWNER}" == 'lumira-ui' ) ]]; then
# -----
# Reason: strictGate=frontend-smoke-freshness generatedAt is 57.8h old; limit=24h
# Missing artifacts: artifacts/ddd/lumira-ui/frontend-smoke.json; artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json; artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json
  run_next_action_command '3' 'lumira-ui' 'ARTIFACT_MISSING' 'DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh'
  run_next_action_command '3' 'lumira-ui' 'ARTIFACT_MISSING' 'DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh'
  run_next_action_command '3' 'lumira-ui' 'ARTIFACT_MISSING' 'DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh'
  run_next_action_command '3' 'lumira-ui' 'ARTIFACT_MISSING' 'DDD_RELEASE_OWNER=lumira-ui DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh'
fi

if [[ "${DDD_RELEASE_NEXT_ACTION_MATCHED}" != "1" ]]; then
  echo "No RUN_NOW next action matched DDD_RELEASE_NEXT_ACTION_ORDER=${DDD_RELEASE_NEXT_ACTION_ORDER} DDD_RELEASE_NEXT_ACTION_OWNER=${DDD_RELEASE_NEXT_ACTION_OWNER}" >&2
  exit 1
fi

# After next-action commands refresh artifacts, rerun:
run_next_action_command '0' 'release-next-action' 'RERUN' 'node bin/ddd-release-evidence-gate.mjs'
run_next_action_command '0' 'release-next-action' 'RERUN' 'node bin/ddd-release-readiness-summary.mjs'
run_next_action_command '0' 'release-next-action' 'RERUN' 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
if [[ "${DDD_RELEASE_NEXT_ACTION_COMMAND_FAILURES}" != "0" ]]; then
  echo "[ddd-release-next-action][completed-with-failures] commandFailures=${DDD_RELEASE_NEXT_ACTION_COMMAND_FAILURES}" >&2
  finalize_next_action_report 1
  exit 1
fi
finalize_next_action_report 0
