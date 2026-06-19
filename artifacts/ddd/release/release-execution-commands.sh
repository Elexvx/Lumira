#!/usr/bin/env bash
set -euo pipefail

# Lumira DDD release execution commands.
# Generated at: 2026-06-18T19:37:26.213Z
# Status: NOT_READY
# Release gate blockers: 94
# This file contains command hints only. Provide a real DDD_RELEASE_ENV_FILE before running evidence commands.
# Do not use release-env-missing.template.env as release evidence.
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
if [[ -z "${LUMIRA_REPO_ROOT:-}" ]]; then
  if [[ -f "scripts/ddd-release-readiness-summary.mjs" ]]; then
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
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-release-env-lint-release-infra' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'release-infra' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-release-env-lint-release-infra P0 release-env-lint->release-infra owner=release-infra priority=P0'
    DDD_RELEASE_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-release-config-ai-owner' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'ai-owner' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-release-config-ai-owner P0 release-config->ai-owner owner=ai-owner priority=P0'
    DDD_RELEASE_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-release-config-payment-owner' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'payment-owner' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-release-config-payment-owner P0 release-config->payment-owner owner=payment-owner priority=P0'
    DDD_RELEASE_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-release-config-platform-events' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'platform-events' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-release-config-platform-events P0 release-config->platform-events owner=platform-events priority=P0'
    DDD_RELEASE_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-release-config-platform-owners' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'platform-owners' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-release-config-platform-owners P0 release-config->platform-owners owner=platform-owners priority=P0'
    DDD_RELEASE_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-release-config-release-infra' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'release-infra' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-release-config-release-infra P0 release-config->release-infra owner=release-infra priority=P0'
    DDD_RELEASE_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-docker-release-infra' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'release-infra' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-docker-release-infra P0 docker->release-infra owner=release-infra priority=P0'
    DDD_RELEASE_LIST_MATCHED=1
  fi
  if [[ ( -z "${DDD_RELEASE_BATCH:-}" || "${DDD_RELEASE_BATCH:-}" == 'p0-runtime-readiness-release-infra' ) && ( -z "${DDD_RELEASE_OWNER:-}" || "${DDD_RELEASE_OWNER:-}" == 'release-infra' ) && ( -z "${DDD_RELEASE_PRIORITY:-}" || "${DDD_RELEASE_PRIORITY:-}" == 'P0' ) ]]; then
    echo 'p0-runtime-readiness-release-infra P0 runtime-readiness->release-infra owner=release-infra priority=P0'
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
  if ! DDD_RELEASE_EXECUTION_REPORT="${DDD_RELEASE_EXECUTION_REPORT}" node scripts/ddd-release-execution-run-report-contract.mjs; then
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

run_batch 'p0-release-env-lint-release-infra' 'release-infra' 'P0'
if [[ "${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "${DDD_RELEASE_BATCH}" || "${DDD_RELEASE_BATCH}" == 'p0-release-env-lint-release-infra' ) && ( -z "${DDD_RELEASE_OWNER}" || "${DDD_RELEASE_OWNER}" == 'release-infra' ) && ( -z "${DDD_RELEASE_PRIORITY}" || "${DDD_RELEASE_PRIORITY}" == 'P0' ) ]]; then
# -----
# p0-release-env-lint-release-infra: P0 release-env-lint -> release-infra
# Pending items: 2
# Expected artifacts: artifacts/ddd/release/release-env-lint.json; artifacts/ddd/config/release-config-evidence.json
# Env keys: AI_SERVICE_BASE_URL; AUTH_SERVICE_BASE_URL; BASE_URL; CORS_ALLOWED_ORIGIN_PATTERNS; DB_PASSWORD; DB_URL; DB_USERNAME; DDD_AUTH_PASSWORD; DDD_AUTH_PERF_BASELINE_ACCEPTED_BY; DDD_AUTH_PERF_BASELINE_ENVIRONMENT; DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT; DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE; DDD_AUTH_PERF_ENVIRONMENT; DDD_AUTH_USERNAME; DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE; DDD_DEPLOYMENT_EVIDENCE; DDD_EXPLAIN_DATABASE; DDD_FRONTEND_DEPLOYMENT_EVIDENCE; DDD_MIGRATION_COMPLETED_AT; DDD_MIGRATION_FRESH_DB_EVIDENCE; DDD_MIGRATION_FRESH_DB_VALIDATED; DDD_MIGRATION_OPERATOR; DDD_MIGRATION_UPGRADE_DB_EVIDENCE; DDD_MIGRATION_UPGRADE_DB_VALIDATED; FIELD_SECRET; FILE_SERVICE_BASE_URL; JOB_EXECUTOR_BASE_URL; JWT_SECRET; LOCALIZATION_SERVICE_BASE_URL; LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL; LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL; LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN; LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL; LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY; LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL; LUMIRA_BASE_URL; MESSAGE_SERVICE_BASE_URL; MYSQL_DATABASE; MYSQL_HOST; MYSQL_PORT; PAYMENT_PUBLIC_BASE_URL; PAYMENT_SERVICE_BASE_URL; PLAYWRIGHT_BASE_URL; PLUGIN_SERVICE_BASE_URL; REDIS_HOST; SAAS_EVENT_REDIS_STREAM_KEY; SAAS_JOB_BACKEND_BASE_URL; SAAS_JOB_FILE_SERVICE_BASE_URL; SAAS_JOB_INTERNAL_TOKEN; SAAS_JOB_MESSAGE_SERVICE_BASE_URL; SAAS_JOB_PAYMENT_SERVICE_BASE_URL; SAAS_JOB_PLUGIN_SERVICE_BASE_URL; SYSTEM_SERVICE_BASE_URL; XXL_JOB_ACCESS_TOKEN; XXL_JOB_ADMIN_ADDRESSES
  print_missing_env_groups 'p0-release-env-lint-release-infra' 'AI_SERVICE_BASE_URL=AI_SERVICE_BASE_URL|LUMIRA_AI_BASE_URL|LUMIRA_AI_SERVICE_BASE_URL' 'AUTH_SERVICE_BASE_URL=AUTH_SERVICE_BASE_URL|LUMIRA_AUTH_SERVICE_BASE_URL' 'BASE_URL=BASE_URL' 'CORS_ALLOWED_ORIGIN_PATTERNS=CORS_ALLOWED_ORIGIN_PATTERNS|SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS' 'DB_PASSWORD=DB_PASSWORD|MYSQL_PASSWORD|SPRING_DATASOURCE_PASSWORD' 'DB_URL=DB_URL|SPRING_DATASOURCE_URL' 'DB_USERNAME=DB_USERNAME|MYSQL_USER|SPRING_DATASOURCE_USERNAME' 'DDD_AUTH_PASSWORD=DDD_AUTH_PASSWORD' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT=DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT=DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE=DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE' 'DDD_AUTH_PERF_ENVIRONMENT=DDD_AUTH_PERF_ENVIRONMENT' 'DDD_AUTH_USERNAME=DDD_AUTH_USERNAME' 'DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE' 'DDD_DEPLOYMENT_EVIDENCE=DDD_DEPLOYMENT_EVIDENCE' 'DDD_EXPLAIN_DATABASE=DDD_EXPLAIN_DATABASE' 'DDD_FRONTEND_DEPLOYMENT_EVIDENCE=DDD_FRONTEND_DEPLOYMENT_EVIDENCE' 'DDD_MIGRATION_COMPLETED_AT=DDD_MIGRATION_COMPLETED_AT' 'DDD_MIGRATION_FRESH_DB_EVIDENCE=DDD_MIGRATION_FRESH_DB_EVIDENCE' 'DDD_MIGRATION_FRESH_DB_VALIDATED=DDD_MIGRATION_FRESH_DB_VALIDATED' 'DDD_MIGRATION_OPERATOR=DDD_MIGRATION_OPERATOR' 'DDD_MIGRATION_UPGRADE_DB_EVIDENCE=DDD_MIGRATION_UPGRADE_DB_EVIDENCE' 'DDD_MIGRATION_UPGRADE_DB_VALIDATED=DDD_MIGRATION_UPGRADE_DB_VALIDATED' 'FIELD_SECRET=FIELD_SECRET|SAAS_SECURITY_FIELD_SECRET' 'FILE_SERVICE_BASE_URL=FILE_SERVICE_BASE_URL|LUMIRA_FILE_SERVICE_BASE_URL' 'JOB_EXECUTOR_BASE_URL=JOB_EXECUTOR_BASE_URL|LUMIRA_JOB_EXECUTOR_BASE_URL' 'JWT_SECRET=JWT_SECRET|SAAS_SECURITY_JWT_SECRET' 'LOCALIZATION_SERVICE_BASE_URL=LOCALIZATION_SERVICE_BASE_URL|LUMIRA_LOCALIZATION_SERVICE_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL=LUMIRA_AI_OWNER_FILE_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL=LUMIRA_AI_OWNER_IAM_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN=LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN|LUMIRA_AI_OWNER_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN' 'LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL=LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL|LUMIRA_AI_OWNER_PLATFORM_BASE_URL' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=LUMIRA_AI_PROVIDER_API_KEY|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL=LUMIRA_AI_PROVIDER_BASE_URL|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL' 'LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL' 'MESSAGE_SERVICE_BASE_URL=LUMIRA_MESSAGE_SERVICE_BASE_URL|MESSAGE_SERVICE_BASE_URL' 'MYSQL_DATABASE=MYSQL_DATABASE' 'MYSQL_HOST=MYSQL_HOST' 'MYSQL_PORT=MYSQL_PORT' 'PAYMENT_PUBLIC_BASE_URL=PAYMENT_PUBLIC_BASE_URL' 'PAYMENT_SERVICE_BASE_URL=LUMIRA_PAYMENT_SERVICE_BASE_URL|PAYMENT_SERVICE_BASE_URL' 'PLAYWRIGHT_BASE_URL=FRONTEND_BASE_URL|PLAYWRIGHT_BASE_URL' 'PLUGIN_SERVICE_BASE_URL=LUMIRA_PLUGIN_SERVICE_BASE_URL|PLUGIN_SERVICE_BASE_URL' 'REDIS_HOST=REDIS_HOST|SPRING_DATA_REDIS_HOST' 'SAAS_EVENT_REDIS_STREAM_KEY=LUMIRA_EVENT_REDIS_STREAM_KEY|SAAS_EVENT_REDIS_STREAM_KEY' 'SAAS_JOB_BACKEND_BASE_URL=LUMIRA_JOB_BACKEND_BASE_URL|SAAS_JOB_BACKEND_BASE_URL' 'SAAS_JOB_FILE_SERVICE_BASE_URL=LUMIRA_JOB_FILE_SERVICE_BASE_URL|SAAS_JOB_FILE_SERVICE_BASE_URL' 'SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN' 'SAAS_JOB_MESSAGE_SERVICE_BASE_URL=LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL|SAAS_JOB_MESSAGE_SERVICE_BASE_URL' 'SAAS_JOB_PAYMENT_SERVICE_BASE_URL=LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL|SAAS_JOB_PAYMENT_SERVICE_BASE_URL' 'SAAS_JOB_PLUGIN_SERVICE_BASE_URL=LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL|SAAS_JOB_PLUGIN_SERVICE_BASE_URL' 'SYSTEM_SERVICE_BASE_URL=LUMIRA_SYSTEM_SERVICE_BASE_URL|SYSTEM_SERVICE_BASE_URL' 'XXL_JOB_ACCESS_TOKEN=LUMIRA_XXL_JOB_ACCESS_TOKEN|XXL_JOB_ACCESS_TOKEN|XXL_JOB_ADMIN_ACCESS_TOKEN' 'XXL_JOB_ADMIN_ADDRESSES=LUMIRA_XXL_JOB_ADMIN_ADDRESSES|XXL_JOB_ADMIN_ADDRESSES'
# Exit criteria:
# - Use a completed DDD_RELEASE_ENV_FILE, not release-env-missing.template.env.
# - release-env-lint summary primaryBlockers is 0 before expensive runtime evidence is rerun.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-release-env-lint-release-infra' 'release-infra' 'P0' 'DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs'
run_command 'p0-release-env-lint-release-infra' 'release-infra' 'P0' 'node scripts/ddd-release-config-evidence.mjs'
fi

run_batch 'p0-release-config-ai-owner' 'ai-owner' 'P0'
if [[ "${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "${DDD_RELEASE_BATCH}" || "${DDD_RELEASE_BATCH}" == 'p0-release-config-ai-owner' ) && ( -z "${DDD_RELEASE_OWNER}" || "${DDD_RELEASE_OWNER}" == 'ai-owner' ) && ( -z "${DDD_RELEASE_PRIORITY}" || "${DDD_RELEASE_PRIORITY}" == 'P0' ) ]]; then
# -----
# p0-release-config-ai-owner: P0 release-config -> ai-owner
# Pending items: 12
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
# Env keys: LUMIRA_AI_OWNER_FILE_BASE_URL; LUMIRA_AI_OWNER_IAM_BASE_URL; LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL; LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL; LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN; LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL; LUMIRA_AI_OWNER_INTERNAL_TOKEN; LUMIRA_AI_OWNER_PLATFORM_BASE_URL; LUMIRA_AI_PROVIDER_API_KEY; LUMIRA_AI_PROVIDER_BASE_URL; LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY; LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL; SAAS_JOB_INTERNAL_TOKEN
  print_missing_env_groups 'p0-release-config-ai-owner' 'LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL=LUMIRA_AI_OWNER_FILE_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL=LUMIRA_AI_OWNER_IAM_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN=LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN|LUMIRA_AI_OWNER_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN' 'LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL=LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL|LUMIRA_AI_OWNER_PLATFORM_BASE_URL' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=LUMIRA_AI_PROVIDER_API_KEY|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL=LUMIRA_AI_PROVIDER_BASE_URL|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL' 'SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN'
# Exit criteria:
# - release-config-evidence status is PASS with no contract issues.
# - releaseConfigBlockersAfterPlaceholders is 0 after placeholders are replaced.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-release-config-ai-owner' 'ai-owner' 'P0' 'node scripts/ddd-release-config-evidence.mjs'
fi

run_batch 'p0-release-config-payment-owner' 'payment-owner' 'P0'
if [[ "${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "${DDD_RELEASE_BATCH}" || "${DDD_RELEASE_BATCH}" == 'p0-release-config-payment-owner' ) && ( -z "${DDD_RELEASE_OWNER}" || "${DDD_RELEASE_OWNER}" == 'payment-owner' ) && ( -z "${DDD_RELEASE_PRIORITY}" || "${DDD_RELEASE_PRIORITY}" == 'P0' ) ]]; then
# -----
# p0-release-config-payment-owner: P0 release-config -> payment-owner
# Pending items: 2
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
# Env keys: PAYMENT_PUBLIC_BASE_URL
  print_missing_env_groups 'p0-release-config-payment-owner' 'PAYMENT_PUBLIC_BASE_URL=PAYMENT_PUBLIC_BASE_URL'
# Exit criteria:
# - release-config-evidence status is PASS with no contract issues.
# - releaseConfigBlockersAfterPlaceholders is 0 after placeholders are replaced.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-release-config-payment-owner' 'payment-owner' 'P0' 'node scripts/ddd-release-config-evidence.mjs'
fi

run_batch 'p0-release-config-platform-events' 'platform-events' 'P0'
if [[ "${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "${DDD_RELEASE_BATCH}" || "${DDD_RELEASE_BATCH}" == 'p0-release-config-platform-events' ) && ( -z "${DDD_RELEASE_OWNER}" || "${DDD_RELEASE_OWNER}" == 'platform-events' ) && ( -z "${DDD_RELEASE_PRIORITY}" || "${DDD_RELEASE_PRIORITY}" == 'P0' ) ]]; then
# -----
# p0-release-config-platform-events: P0 release-config -> platform-events
# Pending items: 17
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
# Env keys: DDD_JOB_INTERNAL_TOKEN; LUMIRA_EVENT_REDIS_STREAM_KEY; LUMIRA_JOB_BACKEND_BASE_URL; LUMIRA_JOB_FILE_SERVICE_BASE_URL; LUMIRA_JOB_INTERNAL_TOKEN; LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL; LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL; LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL; LUMIRA_XXL_JOB_ACCESS_TOKEN; LUMIRA_XXL_JOB_ADMIN_ADDRESSES; SAAS_EVENT_REDIS_STREAM_KEY; SAAS_JOB_BACKEND_BASE_URL; SAAS_JOB_FILE_SERVICE_BASE_URL; SAAS_JOB_INTERNAL_TOKEN; SAAS_JOB_MESSAGE_SERVICE_BASE_URL; SAAS_JOB_PAYMENT_SERVICE_BASE_URL; SAAS_JOB_PLUGIN_SERVICE_BASE_URL; XXL_JOB_ACCESS_TOKEN; XXL_JOB_ADMIN_ACCESS_TOKEN; XXL_JOB_ADMIN_ADDRESSES
  print_missing_env_groups 'p0-release-config-platform-events' 'SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN' 'SAAS_EVENT_REDIS_STREAM_KEY=LUMIRA_EVENT_REDIS_STREAM_KEY|SAAS_EVENT_REDIS_STREAM_KEY' 'SAAS_JOB_BACKEND_BASE_URL=LUMIRA_JOB_BACKEND_BASE_URL|SAAS_JOB_BACKEND_BASE_URL' 'SAAS_JOB_FILE_SERVICE_BASE_URL=LUMIRA_JOB_FILE_SERVICE_BASE_URL|SAAS_JOB_FILE_SERVICE_BASE_URL' 'SAAS_JOB_MESSAGE_SERVICE_BASE_URL=LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL|SAAS_JOB_MESSAGE_SERVICE_BASE_URL' 'SAAS_JOB_PAYMENT_SERVICE_BASE_URL=LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL|SAAS_JOB_PAYMENT_SERVICE_BASE_URL' 'SAAS_JOB_PLUGIN_SERVICE_BASE_URL=LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL|SAAS_JOB_PLUGIN_SERVICE_BASE_URL' 'XXL_JOB_ACCESS_TOKEN=LUMIRA_XXL_JOB_ACCESS_TOKEN|XXL_JOB_ACCESS_TOKEN|XXL_JOB_ADMIN_ACCESS_TOKEN' 'XXL_JOB_ADMIN_ADDRESSES=LUMIRA_XXL_JOB_ADMIN_ADDRESSES|XXL_JOB_ADMIN_ADDRESSES'
# Exit criteria:
# - release-config-evidence status is PASS with no contract issues.
# - releaseConfigBlockersAfterPlaceholders is 0 after placeholders are replaced.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-release-config-platform-events' 'platform-events' 'P0' 'node scripts/ddd-release-config-evidence.mjs'
fi

run_batch 'p0-release-config-platform-owners' 'platform-owners' 'P0'
if [[ "${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "${DDD_RELEASE_BATCH}" || "${DDD_RELEASE_BATCH}" == 'p0-release-config-platform-owners' ) && ( -z "${DDD_RELEASE_OWNER}" || "${DDD_RELEASE_OWNER}" == 'platform-owners' ) && ( -z "${DDD_RELEASE_PRIORITY}" || "${DDD_RELEASE_PRIORITY}" == 'P0' ) ]]; then
# -----
# p0-release-config-platform-owners: P0 release-config -> platform-owners
# Pending items: 18
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
# Env keys: AI_SERVICE_BASE_URL; AUTH_SERVICE_BASE_URL; FILE_SERVICE_BASE_URL; JOB_EXECUTOR_BASE_URL; LOCALIZATION_SERVICE_BASE_URL; LUMIRA_AI_BASE_URL; LUMIRA_AI_SERVICE_BASE_URL; LUMIRA_AUTH_SERVICE_BASE_URL; LUMIRA_FILE_SERVICE_BASE_URL; LUMIRA_JOB_EXECUTOR_BASE_URL; LUMIRA_LOCALIZATION_SERVICE_BASE_URL; LUMIRA_MESSAGE_SERVICE_BASE_URL; LUMIRA_PAYMENT_SERVICE_BASE_URL; LUMIRA_PLUGIN_SERVICE_BASE_URL; LUMIRA_SYSTEM_SERVICE_BASE_URL; MESSAGE_SERVICE_BASE_URL; PAYMENT_SERVICE_BASE_URL; PLUGIN_SERVICE_BASE_URL; SYSTEM_SERVICE_BASE_URL
  print_missing_env_groups 'p0-release-config-platform-owners' 'AI_SERVICE_BASE_URL=AI_SERVICE_BASE_URL|LUMIRA_AI_BASE_URL|LUMIRA_AI_SERVICE_BASE_URL' 'AUTH_SERVICE_BASE_URL=AUTH_SERVICE_BASE_URL|LUMIRA_AUTH_SERVICE_BASE_URL' 'FILE_SERVICE_BASE_URL=FILE_SERVICE_BASE_URL|LUMIRA_FILE_SERVICE_BASE_URL' 'JOB_EXECUTOR_BASE_URL=JOB_EXECUTOR_BASE_URL|LUMIRA_JOB_EXECUTOR_BASE_URL' 'LOCALIZATION_SERVICE_BASE_URL=LOCALIZATION_SERVICE_BASE_URL|LUMIRA_LOCALIZATION_SERVICE_BASE_URL' 'MESSAGE_SERVICE_BASE_URL=LUMIRA_MESSAGE_SERVICE_BASE_URL|MESSAGE_SERVICE_BASE_URL' 'PAYMENT_SERVICE_BASE_URL=LUMIRA_PAYMENT_SERVICE_BASE_URL|PAYMENT_SERVICE_BASE_URL' 'PLUGIN_SERVICE_BASE_URL=LUMIRA_PLUGIN_SERVICE_BASE_URL|PLUGIN_SERVICE_BASE_URL' 'SYSTEM_SERVICE_BASE_URL=LUMIRA_SYSTEM_SERVICE_BASE_URL|SYSTEM_SERVICE_BASE_URL'
# Exit criteria:
# - release-config-evidence status is PASS with no contract issues.
# - releaseConfigBlockersAfterPlaceholders is 0 after placeholders are replaced.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-release-config-platform-owners' 'platform-owners' 'P0' 'node scripts/ddd-release-config-evidence.mjs'
fi

run_batch 'p0-release-config-release-infra' 'release-infra' 'P0'
if [[ "${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "${DDD_RELEASE_BATCH}" || "${DDD_RELEASE_BATCH}" == 'p0-release-config-release-infra' ) && ( -z "${DDD_RELEASE_OWNER}" || "${DDD_RELEASE_OWNER}" == 'release-infra' ) && ( -z "${DDD_RELEASE_PRIORITY}" || "${DDD_RELEASE_PRIORITY}" == 'P0' ) ]]; then
# -----
# p0-release-config-release-infra: P0 release-config -> release-infra
# Pending items: 14
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
# Env keys: CORS_ALLOWED_ORIGIN_PATTERNS; DB_PASSWORD; DB_URL; DB_USERNAME; DEPLOY_CHECK_BASE_URL; FIELD_SECRET; FRONTEND_BASE_URL; JWT_SECRET; LUMIRA_BASE_URL; MYSQL_PASSWORD; MYSQL_USER; PLAYWRIGHT_BASE_URL; REDIS_HOST; SAAS_SECURITY_FIELD_SECRET; SAAS_SECURITY_JWT_SECRET; SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS; SPRING_DATASOURCE_PASSWORD; SPRING_DATASOURCE_URL; SPRING_DATASOURCE_USERNAME; SPRING_DATA_REDIS_HOST
  print_missing_env_groups 'p0-release-config-release-infra' 'CORS_ALLOWED_ORIGIN_PATTERNS=CORS_ALLOWED_ORIGIN_PATTERNS|SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS' 'DB_PASSWORD=DB_PASSWORD|MYSQL_PASSWORD|SPRING_DATASOURCE_PASSWORD' 'DB_URL=DB_URL|SPRING_DATASOURCE_URL' 'DB_USERNAME=DB_USERNAME|MYSQL_USER|SPRING_DATASOURCE_USERNAME' 'LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL' 'FIELD_SECRET=FIELD_SECRET|SAAS_SECURITY_FIELD_SECRET' 'PLAYWRIGHT_BASE_URL=FRONTEND_BASE_URL|PLAYWRIGHT_BASE_URL' 'JWT_SECRET=JWT_SECRET|SAAS_SECURITY_JWT_SECRET' 'REDIS_HOST=REDIS_HOST|SPRING_DATA_REDIS_HOST'
# Exit criteria:
# - release-config-evidence status is PASS with no contract issues.
# - releaseConfigBlockersAfterPlaceholders is 0 after placeholders are replaced.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-release-config-release-infra' 'release-infra' 'P0' 'node scripts/ddd-release-config-evidence.mjs'
fi

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
# - Required lumira-server and frontend images are built, inspected, and not skipped.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-docker-release-infra' 'release-infra' 'P0' 'DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs'
run_command 'p0-docker-release-infra' 'release-infra' 'P0' 'node scripts/ddd-docker-build-evidence.mjs'
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
run_command 'p0-runtime-readiness-release-infra' 'release-infra' 'P0' 'node scripts/ddd-runtime-readiness-smoke.mjs'
fi

run_batch 'p0-manifest-release-owner' 'release-owner' 'P0'
if [[ "${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "${DDD_RELEASE_BATCH}" || "${DDD_RELEASE_BATCH}" == 'p0-manifest-release-owner' ) && ( -z "${DDD_RELEASE_OWNER}" || "${DDD_RELEASE_OWNER}" == 'release-owner' ) && ( -z "${DDD_RELEASE_PRIORITY}" || "${DDD_RELEASE_PRIORITY}" == 'P0' ) ]]; then
# -----
# p0-manifest-release-owner: P0 manifest -> release-owner
# Pending items: 1
# Expected artifacts: artifacts/ddd/release/evidence-manifest.json
# Env keys: DDD_EVIDENCE_ENVIRONMENT; DDD_EVIDENCE_OPERATOR; DDD_RELEASE_CANDIDATE; DDD_RELEASE_MANIFEST_STRICT
  print_missing_env_groups 'p0-manifest-release-owner' 'DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE' 'DDD_RELEASE_MANIFEST_STRICT=DDD_RELEASE_MANIFEST_STRICT'
# Exit criteria:
# - All required release evidence artifacts are present and checksummed.
# - Clear this batch before running downstream runtime-heavy evidence.
run_command 'p0-manifest-release-owner' 'release-owner' 'P0' 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs'
run_command 'p0-manifest-release-owner' 'release-owner' 'P0' 'node scripts/ddd-promote-performance-baseline.mjs'
run_command 'p0-manifest-release-owner' 'release-owner' 'P0' 'DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs'
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
run_command 'p0-authenticated-performance-release-performance' 'release-performance' 'P0' 'node scripts/ddd-authenticated-performance-smoke.mjs'
run_command 'p0-authenticated-performance-release-performance' 'release-performance' 'P0' 'node scripts/ddd-promote-performance-baseline.mjs'
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
run_command 'release-execution-rerun' 'release-owner' 'RERUN' 'node scripts/ddd-release-evidence-gate.mjs'
run_command 'release-execution-rerun' 'release-owner' 'RERUN' 'node scripts/ddd-release-readiness-summary.mjs'
run_command 'release-execution-rerun' 'release-owner' 'RERUN' 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
if [[ "${DDD_RELEASE_COMMAND_FAILURES}" != "0" ]]; then
  echo "[ddd-release-execution][completed-with-failures] commandFailures=${DDD_RELEASE_COMMAND_FAILURES}" >&2
  finalize_release_execution_report 1
  exit 1
fi
finalize_release_execution_report 0
