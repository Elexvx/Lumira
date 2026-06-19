#!/usr/bin/env bash
set -euo pipefail

# Lumira DDD release execution commands.
# Generated at: 2026-06-19T18:09:18.921Z
# Status: NOT_READY
# Release gate blockers: 94
# This file contains command hints only. Provide a real DDD_RELEASE_ENV_FILE before running evidence commands.
# Do not use release-env-missing.template.env as release evidence.
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

if [[ "${DDD_RELEASE_LIST_BATCHES:-}" == "1" || "${DDD_RELEASE_LIST_BATCHES:-}" == "true" ]]; then
  DDD_RELEASE_LIST_MATCHED=0
  echo "Ready release batches:"
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-docker-release-infra' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'release-infra' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-docker-release-infra P0 docker->release-infra owner=release-infra priority=P0'
    DDD_RELEASE_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-runtime-readiness-release-infra' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'release-infra' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-runtime-readiness-release-infra P0 runtime-readiness->release-infra owner=release-infra priority=P0'
    DDD_RELEASE_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-manifest-database' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'database' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-manifest-database P0 manifest->database owner=database priority=P0'
    DDD_RELEASE_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-manifest-lumira-ui' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'lumira-ui' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-manifest-lumira-ui P0 manifest->lumira-ui owner=lumira-ui priority=P0'
    DDD_RELEASE_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-manifest-release-owner' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'release-owner' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-manifest-release-owner P0 manifest->release-owner owner=release-owner priority=P0'
    DDD_RELEASE_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-authenticated-performance-release-performance' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'release-performance' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-authenticated-performance-release-performance P0 authenticated-performance->release-performance owner=release-performance priority=P0'
    DDD_RELEASE_LIST_MATCHED=1
  fi
  if [[ "${DDD_RELEASE_LIST_MATCHED}" != "1" ]]; then
    echo "No ready release batches matched DDD_RELEASE_BATCH=${DDD_RELEASE_BATCH:-} DDD_RELEASE_OWNER=${DDD_RELEASE_OWNER:-} DDD_RELEASE_PRIORITY=${DDD_RELEASE_PRIORITY:-}" >&2
    exit 1
  fi
  exit 0
fi

DDD_RELEASE_BATCH="${DDD_RELEASE_BATCH:-}"
DDD_RELEASE_OWNER="${DDD_RELEASE_OWNER:-}"
DDD_RELEASE_PRIORITY="${DDD_RELEASE_PRIORITY:-}"
DDD_RELEASE_DRY_RUN="${DDD_RELEASE_DRY_RUN:-}"
DDD_RELEASE_CHECK_ENV_ONLY="${DDD_RELEASE_CHECK_ENV_ONLY:-}"
DDD_RELEASE_ALLOW_MISSING_ENV="${DDD_RELEASE_ALLOW_MISSING_ENV:-}"
DDD_RELEASE_CONTINUE_ON_ERROR="${DDD_RELEASE_CONTINUE_ON_ERROR:-}"
DDD_RELEASE_EXECUTION_REPORT="${DDD_RELEASE_EXECUTION_REPORT:-artifacts/ddd/release/release-execution-run-report.json}"
DDD_RELEASE_EXECUTION_REPORT_TMP="${DDD_RELEASE_EXECUTION_REPORT}.jsonl.$$"
DDD_RELEASE_EXECUTION_REPORT_FINALIZED=0
DDD_RELEASE_NEEDS_ENV=1
if [[ "${DDD_RELEASE_DRY_RUN}" == "1" || "${DDD_RELEASE_DRY_RUN}" == "true" ]]; then
  DDD_RELEASE_NEEDS_ENV=0
fi
if [[ "${DDD_RELEASE_CHECK_ENV_ONLY}" == "1" || "${DDD_RELEASE_CHECK_ENV_ONLY}" == "true" ]]; then
  DDD_RELEASE_NEEDS_ENV=1
fi
if [[ "${DDD_RELEASE_NEEDS_ENV}" == "1" ]]; then
  if [[ -z "${DDD_RELEASE_ENV_FILE:-}" ]]; then
    echo "DDD_RELEASE_ENV_FILE is required and must point to a completed release env file." >&2
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
safe_load_release_env_file() {
  local exports
  if ! exports=$(node --input-type=module -e 'import fs from '\''node:fs'\''; import path from '\''node:path'\''; const [file, permissionCheckedArg] = process.argv.slice(1); const templateNames = new Set(['\''release-env-missing.template.env'\'', '\''release-closure-wave-env.template.env'\'', '\''release-final-owner-queue-env.template.env'\'', '\''release-env-canonical-fill.template.env'\'']); if (templateNames.has(path.basename(file))) {   console.error(`[ddd-release-env][template-refused] file=${file}`);   process.exit(1); } const permissionAlreadyChecked = permissionCheckedArg === '\''1'\'' || permissionCheckedArg === '\''true'\'' || process.env.DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED === '\''1'\'' || process.env.DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED === '\''true'\''; const mode = permissionAlreadyChecked ? 0o600 : fs.statSync(file).mode & 0o777; if (!permissionAlreadyChecked && (mode & 0o077) !== 0) {   console.error(`[ddd-release-env][permission-refused] file=${file} mode=${mode.toString(8).padStart(3, '\''0'\'')} required=600`);   process.exit(1); } const text = fs.readFileSync(file, '\''utf8'\''); const quote = (value) => `'\''${String(value).replace(/'\''/g, `'\''\\'\'''\''`)}'\''`; let lineNumber = 0; for (const line of text.split(/\r?\n/)) {   lineNumber += 1;   const trimmed = line.trim();   if (!trimmed || trimmed.startsWith('\''#'\'')) continue;   const match = trimmed.match(/^(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$/);   if (!match) {     console.error(`[ddd-release-env][env-invalid] line=${lineNumber}`);     process.exit(1);   }   let value = match[2].trim();   const quoted = value.match(/^(['\''\"])(.*)\1$/s);   if (quoted) value = quoted[2];   console.log(`export ${match[1]}=${quote(value)}`); }' "$DDD_RELEASE_ENV_FILE" "${DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED:-}"); then
    return 1
  fi
  eval "${exports}"
}
if [[ "${DDD_RELEASE_NEEDS_ENV}" == "1" ]]; then
  safe_load_release_env_file
fi
DDD_RELEASE_BATCH_MATCHED=0
DDD_RELEASE_COMMAND_FAILURES=0
append_release_execution_report_entry() {
  local batch_id="$1"
  local batch_owner="$2"
  local batch_priority="$3"
  local command="$4"
  local status="$5"
  local duration_ms="$6"
  if [[ "${DDD_RELEASE_DRY_RUN}" == "1" || "${DDD_RELEASE_DRY_RUN}" == "true" || "${DDD_RELEASE_CHECK_ENV_ONLY}" == "1" || "${DDD_RELEASE_CHECK_ENV_ONLY}" == "true" ]]; then return 0; fi
  node --input-type=module -e 'import fs from "node:fs"; const [file, batchId, owner, priority, command, status, durationMs] = process.argv.slice(1); fs.appendFileSync(file, `${JSON.stringify({ batchId, owner, priority, command, status: Number(status), durationMs: Number(durationMs), finishedAt: new Date().toISOString() })}\n`);' "${DDD_RELEASE_EXECUTION_REPORT_TMP}" "${batch_id}" "${batch_owner}" "${batch_priority}" "${command}" "${status}" "${duration_ms}"
}
finalize_release_execution_report() {
  local exit_code="$1"
  if [[ "${DDD_RELEASE_DRY_RUN}" == "1" || "${DDD_RELEASE_DRY_RUN}" == "true" || "${DDD_RELEASE_CHECK_ENV_ONLY}" == "1" || "${DDD_RELEASE_CHECK_ENV_ONLY}" == "true" ]]; then return 0; fi
  if [[ "${DDD_RELEASE_EXECUTION_REPORT_FINALIZED}" == "1" ]]; then return "${exit_code}"; fi
  DDD_RELEASE_EXECUTION_REPORT_FINALIZED=1
  mkdir -p "$(dirname "${DDD_RELEASE_EXECUTION_REPORT}")"
  node --input-type=module -e 'import fs from "node:fs"; const [tmp, out, exitCode, batchFilter, ownerFilter, priorityFilter] = process.argv.slice(1); const entries = fs.existsSync(tmp) ? fs.readFileSync(tmp, "utf8").split("\n").filter(Boolean).map((line) => JSON.parse(line)) : []; const exit = Number(exitCode); const failedEntries = entries.filter((entry) => Number(entry.status) !== 0).length; const summary = { totalEntries: entries.length, succeededEntries: entries.length - failedEntries, failedEntries }; fs.writeFileSync(out, `${JSON.stringify({ generatedAt: new Date().toISOString(), reportStatus: exit === 0 ? "PASS" : "FAIL", exitCode: exit, batchFilter: batchFilter || null, ownerFilter: ownerFilter || null, priorityFilter: priorityFilter || null, summary, entries }, null, 2)}\n`); if (fs.existsSync(tmp)) fs.rmSync(tmp);' "${DDD_RELEASE_EXECUTION_REPORT_TMP}" "${DDD_RELEASE_EXECUTION_REPORT}" "${exit_code}" "${DDD_RELEASE_BATCH}" "${DDD_RELEASE_OWNER}" "${DDD_RELEASE_PRIORITY}"
  if ! DDD_RELEASE_EXECUTION_REPORT="${DDD_RELEASE_EXECUTION_REPORT}" node bin/ddd-release-execution-run-report-contract.mjs; then
    echo "[ddd-release-execution][report-contract] failed" >&2
    return 1
  fi
  echo "[ddd-release-execution][report] ${DDD_RELEASE_EXECUTION_REPORT}"
  return "${exit_code}"
}
trap 'status=$?; finalize_release_execution_report "${status}"; exit "${status}"' EXIT
print_missing_env_groups() {
  local batch_id="$1"
  shift
  local missing=()
  local spec label keys key found
  for spec in "$@"; do
    label="${spec%%=*}"
    keys="${spec#*=}"
    found=0
    IFS='|' read -r -a key_group <<< "${keys}"
    for key in "${key_group[@]}"; do
      if [[ -n "${!key:-}" ]]; then
        found=1
        break
      fi
    done
    if [[ "${found}" != "1" ]]; then
      missing+=("${label}(${keys//|/ or })")
    fi
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    echo "[ddd-release-execution][env-check] ${batch_id} missing env groups: ${missing[*]}" >&2
    echo "[ddd-release-execution][env-check] ${batch_id} at least one key in each group must be present." >&2
    if [[ "${DDD_RELEASE_ALLOW_MISSING_ENV}" == "1" || "${DDD_RELEASE_ALLOW_MISSING_ENV}" == "true" ]]; then
      echo "[ddd-release-execution][env-check] ${batch_id} continuing because DDD_RELEASE_ALLOW_MISSING_ENV=${DDD_RELEASE_ALLOW_MISSING_ENV}" >&2
      return 0
    fi
    return 1
  fi
  return 0
}
run_command() {
  local batch_id="$1"
  local batch_owner="$2"
  local batch_priority="$3"
  local command="$4"
  if [[ "${DDD_RELEASE_CHECK_ENV_ONLY}" == "1" || "${DDD_RELEASE_CHECK_ENV_ONLY}" == "true" ]]; then
    echo "[ddd-release-execution][env-check-only] skip ${command}"
    return 0
  fi
  if [[ "${DDD_RELEASE_DRY_RUN}" == "1" || "${DDD_RELEASE_DRY_RUN}" == "true" ]]; then
    echo "[ddd-release-execution][dry-run] ${command}"
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
  append_release_execution_report_entry "${batch_id}" "${batch_owner}" "${batch_priority}" "${command}" "${status}" "$((finished_ms - started_ms))"
  if [[ "${status}" != "0" ]]; then
    echo "[ddd-release-execution][command-failed] status=${status} command=${command}" >&2
    DDD_RELEASE_COMMAND_FAILURES=$((DDD_RELEASE_COMMAND_FAILURES + 1))
    if [[ "${DDD_RELEASE_CONTINUE_ON_ERROR}" == "1" || "${DDD_RELEASE_CONTINUE_ON_ERROR}" == "true" ]]; then
      echo "[ddd-release-execution][command-failed] continuing because DDD_RELEASE_CONTINUE_ON_ERROR=${DDD_RELEASE_CONTINUE_ON_ERROR}" >&2
      return 0
    fi
    return "${status}"
  fi
  return 0
}
run_batch() {
  local batch_id="$1"
  local batch_owner="$2"
  local batch_priority="$3"
  if [[ -n "${DDD_RELEASE_BATCH}" && "${DDD_RELEASE_BATCH}" != "${batch_id}" ]]; then
    return 0
  fi
  if [[ -n "${DDD_RELEASE_OWNER}" && "${DDD_RELEASE_OWNER}" != "${batch_owner}" ]]; then
    return 0
  fi
  if [[ -n "${DDD_RELEASE_PRIORITY}" && "${DDD_RELEASE_PRIORITY}" != "${batch_priority}" ]]; then
    return 0
  fi
  DDD_RELEASE_BATCH_MATCHED=1
  echo "[ddd-release-execution] running ${batch_id} owner=${batch_owner} priority=${batch_priority}"
}

run_batch 'p0-docker-release-infra' 'release-infra' 'P0'
if [[ "${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "${DDD_RELEASE_BATCH}" || "${DDD_RELEASE_BATCH}" == 'p0-docker-release-infra' ) && ( -z "${DDD_RELEASE_OWNER}" || "${DDD_RELEASE_OWNER}" == 'release-infra' ) && ( -z "${DDD_RELEASE_PRIORITY}" || "${DDD_RELEASE_PRIORITY}" == 'P0' ) ]]; then
# -----
# p0-docker-release-infra: P0 docker -> release-infra
# Pending items: 4
# Expected artifacts: artifacts/ddd/build/docker-image-evidence.json
# Env keys: DDD_DOCKER_BUILD_STRICT; DDD_DOCKER_COMMAND
  print_missing_env_groups 'p0-docker-release-infra' 'DDD_DOCKER_BUILD_STRICT=DDD_DOCKER_BUILD_STRICT' 'DDD_DOCKER_COMMAND=DDD_DOCKER_COMMAND'
# Exit criteria:
# - Docker CLI and daemon are available in the evidence runner.
# - Required lumira-server and lumira-ui images are built, inspected, and not skipped.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-docker-release-infra' 'release-infra' 'P0' 'DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs'
fi

run_batch 'p0-runtime-readiness-release-infra' 'release-infra' 'P0'
if [[ "${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "${DDD_RELEASE_BATCH}" || "${DDD_RELEASE_BATCH}" == 'p0-runtime-readiness-release-infra' ) && ( -z "${DDD_RELEASE_OWNER}" || "${DDD_RELEASE_OWNER}" == 'release-infra' ) && ( -z "${DDD_RELEASE_PRIORITY}" || "${DDD_RELEASE_PRIORITY}" == 'P0' ) ]]; then
# -----
# p0-runtime-readiness-release-infra: P0 runtime-readiness -> release-infra
# Pending items: 4
# Expected artifacts: artifacts/ddd/readiness/summary.json
# Env keys: DDD_EVIDENCE_ENVIRONMENT; DDD_EVIDENCE_OPERATOR; DDD_RELEASE_CANDIDATE; LUMIRA_BASE_URL
  print_missing_env_groups 'p0-runtime-readiness-release-infra' 'DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE' 'LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL'
# Exit criteria:
# - Runtime readiness is generated from an HTTPS non-local backend base URL.
# - All 30 owner readiness/health/metrics checks pass.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-runtime-readiness-release-infra' 'release-infra' 'P0' 'node bin/ddd-runtime-readiness-smoke.mjs'
fi

run_batch 'p0-manifest-database' 'database' 'P0'
if [[ "${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "${DDD_RELEASE_BATCH}" || "${DDD_RELEASE_BATCH}" == 'p0-manifest-database' ) && ( -z "${DDD_RELEASE_OWNER}" || "${DDD_RELEASE_OWNER}" == 'database' ) && ( -z "${DDD_RELEASE_PRIORITY}" || "${DDD_RELEASE_PRIORITY}" == 'P0' ) ]]; then
# -----
# p0-manifest-database: P0 manifest -> database
# Pending items: 2
# Expected artifacts: artifacts/ddd/release/evidence-manifest.json
# Env keys: DDD_MIGRATION_FRESH_DB_EVIDENCE; DDD_MIGRATION_FRESH_DB_VALIDATED; DDD_MIGRATION_UPGRADE_DB_EVIDENCE; DDD_MIGRATION_UPGRADE_DB_VALIDATED
  print_missing_env_groups 'p0-manifest-database' 'DDD_MIGRATION_FRESH_DB_EVIDENCE=DDD_MIGRATION_FRESH_DB_EVIDENCE' 'DDD_MIGRATION_FRESH_DB_VALIDATED=DDD_MIGRATION_FRESH_DB_VALIDATED' 'DDD_MIGRATION_UPGRADE_DB_EVIDENCE=DDD_MIGRATION_UPGRADE_DB_EVIDENCE' 'DDD_MIGRATION_UPGRADE_DB_VALIDATED=DDD_MIGRATION_UPGRADE_DB_VALIDATED'
# Exit criteria:
# - All required release evidence artifacts are present and checksummed.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-manifest-database' 'database' 'P0' 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
run_command 'p0-manifest-database' 'database' 'P0' 'node bin/ddd-promote-performance-baseline.mjs'
run_command 'p0-manifest-database' 'database' 'P0' 'DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
fi

run_batch 'p0-manifest-lumira-ui' 'lumira-ui' 'P0'
if [[ "${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "${DDD_RELEASE_BATCH}" || "${DDD_RELEASE_BATCH}" == 'p0-manifest-lumira-ui' ) && ( -z "${DDD_RELEASE_OWNER}" || "${DDD_RELEASE_OWNER}" == 'lumira-ui' ) && ( -z "${DDD_RELEASE_PRIORITY}" || "${DDD_RELEASE_PRIORITY}" == 'P0' ) ]]; then
# -----
# p0-manifest-lumira-ui: P0 manifest -> lumira-ui
# Pending items: 3
# Expected artifacts: artifacts/ddd/release/evidence-manifest.json; artifacts/ddd/lumira-ui/frontend-smoke.json; artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json; artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json
# Env keys: DDD_EVIDENCE_ENVIRONMENT; DDD_FRONTEND_EXPECT_DEPLOYED; DDD_RELEASE_CANDIDATE; PLAYWRIGHT_BASE_URL
  print_missing_env_groups 'p0-manifest-lumira-ui' 'DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT' 'DDD_FRONTEND_EXPECT_DEPLOYED=DDD_FRONTEND_EXPECT_DEPLOYED' 'DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE' 'PLAYWRIGHT_BASE_URL=FRONTEND_BASE_URL|PLAYWRIGHT_BASE_URL'
# Exit criteria:
# - All required release evidence artifacts are present and checksummed.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-manifest-lumira-ui' 'lumira-ui' 'P0' 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
run_command 'p0-manifest-lumira-ui' 'lumira-ui' 'P0' 'node bin/ddd-promote-performance-baseline.mjs'
run_command 'p0-manifest-lumira-ui' 'lumira-ui' 'P0' 'DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
fi

run_batch 'p0-manifest-release-owner' 'release-owner' 'P0'
if [[ "${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "${DDD_RELEASE_BATCH}" || "${DDD_RELEASE_BATCH}" == 'p0-manifest-release-owner' ) && ( -z "${DDD_RELEASE_OWNER}" || "${DDD_RELEASE_OWNER}" == 'release-owner' ) && ( -z "${DDD_RELEASE_PRIORITY}" || "${DDD_RELEASE_PRIORITY}" == 'P0' ) ]]; then
# -----
# p0-manifest-release-owner: P0 manifest -> release-owner
# Pending items: 7
# Expected artifacts: artifacts/ddd/release/evidence-manifest.json
# Env keys: DDD_EVIDENCE_ENVIRONMENT; DDD_EVIDENCE_OPERATOR; DDD_RELEASE_CANDIDATE; DDD_RELEASE_MANIFEST_STRICT
  print_missing_env_groups 'p0-manifest-release-owner' 'DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE' 'DDD_RELEASE_MANIFEST_STRICT=DDD_RELEASE_MANIFEST_STRICT'
# Exit criteria:
# - All required release evidence artifacts are present and checksummed.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-manifest-release-owner' 'release-owner' 'P0' 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
run_command 'p0-manifest-release-owner' 'release-owner' 'P0' 'node bin/ddd-promote-performance-baseline.mjs'
run_command 'p0-manifest-release-owner' 'release-owner' 'P0' 'DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
fi

run_batch 'p0-authenticated-performance-release-performance' 'release-performance' 'P0'
if [[ "${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "${DDD_RELEASE_BATCH}" || "${DDD_RELEASE_BATCH}" == 'p0-authenticated-performance-release-performance' ) && ( -z "${DDD_RELEASE_OWNER}" || "${DDD_RELEASE_OWNER}" == 'release-performance' ) && ( -z "${DDD_RELEASE_PRIORITY}" || "${DDD_RELEASE_PRIORITY}" == 'P0' ) ]]; then
# -----
# p0-authenticated-performance-release-performance: P0 authenticated-performance -> release-performance
# Pending items: 9
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json
# Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY; DDD_AUTH_PERF_BASELINE_ENVIRONMENT; DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT; DDD_RELEASE_CANDIDATE
  print_missing_env_groups 'p0-authenticated-performance-release-performance' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT=DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT=DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE'
# Exit criteria:
# - Authenticated performance actual is generated from a production-equivalent HTTPS backend.
# - Accepted baseline exists and current p95/upload metrics do not regress beyond the configured threshold.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-authenticated-performance-release-performance' 'release-performance' 'P0' 'node bin/ddd-authenticated-performance-smoke.mjs'
run_command 'p0-authenticated-performance-release-performance' 'release-performance' 'P0' 'node bin/ddd-promote-performance-baseline.mjs'
fi

if [[ -n "${DDD_RELEASE_BATCH}" && "${DDD_RELEASE_BATCH_MATCHED}" != "1" ]]; then
  echo "No ready release batch matched DDD_RELEASE_BATCH=${DDD_RELEASE_BATCH}" >&2
  exit 1
fi
if [[ -n "${DDD_RELEASE_OWNER}" && "${DDD_RELEASE_BATCH_MATCHED}" != "1" ]]; then
  echo "No ready release batch matched DDD_RELEASE_OWNER=${DDD_RELEASE_OWNER}" >&2
  exit 1
fi
if [[ -n "${DDD_RELEASE_PRIORITY}" && "${DDD_RELEASE_BATCH_MATCHED}" != "1" ]]; then
  echo "No ready release batch matched DDD_RELEASE_PRIORITY=${DDD_RELEASE_PRIORITY}" >&2
  exit 1
fi

# After these commands refresh artifacts, rerun:
run_command 'release-execution-rerun' 'release-owner' 'RERUN' 'node bin/ddd-release-evidence-gate.mjs'
run_command 'release-execution-rerun' 'release-owner' 'RERUN' 'node bin/ddd-release-readiness-summary.mjs'
run_command 'release-execution-rerun' 'release-owner' 'RERUN' 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
if [[ "${DDD_RELEASE_COMMAND_FAILURES}" != "0" ]]; then
  echo "[ddd-release-execution][completed-with-failures] commandFailures=${DDD_RELEASE_COMMAND_FAILURES}" >&2
  finalize_release_execution_report 1
  exit 1
fi
finalize_release_execution_report 0
