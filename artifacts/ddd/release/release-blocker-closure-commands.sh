#!/usr/bin/env bash
set -euo pipefail

# Lumira DDD release blocker closure commands.
# Generated at: 2026-06-18T19:37:26.213Z
# Status: NOT_READY
# Release gate blockers: 94
# Default mode lists runnable closure items. Set DDD_RELEASE_CLOSURE_EXECUTE=1 to execute commands.
# Use DDD_RELEASE_CLOSURE_ORDER, DDD_RELEASE_CLOSURE_OWNER, DDD_RELEASE_CLOSURE_PRIORITY, or DDD_RELEASE_CLOSURE_KIND to filter.
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

DDD_RELEASE_CLOSURE_ORDER="${DDD_RELEASE_CLOSURE_ORDER:-}"
DDD_RELEASE_CLOSURE_OWNER="${DDD_RELEASE_CLOSURE_OWNER:-}"
DDD_RELEASE_CLOSURE_PRIORITY="${DDD_RELEASE_CLOSURE_PRIORITY:-}"
DDD_RELEASE_CLOSURE_KIND="${DDD_RELEASE_CLOSURE_KIND:-}"
DDD_RELEASE_CLOSURE_DETAIL="${DDD_RELEASE_CLOSURE_DETAIL:-}"
DDD_RELEASE_CLOSURE_CHECK_ENV="${DDD_RELEASE_CLOSURE_CHECK_ENV:-}"
DDD_RELEASE_CLOSURE_EXECUTE="${DDD_RELEASE_CLOSURE_EXECUTE:-}"
DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR="${DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR:-}"
DDD_RELEASE_CLOSURE_MATCHED=0
DDD_RELEASE_CLOSURE_COMMAND_FAILURES=0
if [[ "${DDD_RELEASE_CLOSURE_EXECUTE}" == "1" || "${DDD_RELEASE_CLOSURE_EXECUTE}" == "true" || "${DDD_RELEASE_CLOSURE_CHECK_ENV}" == "1" || "${DDD_RELEASE_CLOSURE_CHECK_ENV}" == "true" ]]; then
  if [[ -z "${DDD_RELEASE_ENV_FILE:-}" ]]; then
    echo "DDD_RELEASE_ENV_FILE is required when executing or checking closure env." >&2
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
if [[ "${DDD_RELEASE_CLOSURE_EXECUTE}" == "1" || "${DDD_RELEASE_CLOSURE_EXECUTE}" == "true" || "${DDD_RELEASE_CLOSURE_CHECK_ENV}" == "1" || "${DDD_RELEASE_CLOSURE_CHECK_ENV}" == "true" ]]; then
  safe_load_release_env_file
fi
matches_closure_filter() {
  local order="$1"
  local owner="$2"
  local priority="$3"
  local kind="$4"
  if [[ -n "${DDD_RELEASE_CLOSURE_ORDER}" && "${DDD_RELEASE_CLOSURE_ORDER}" != "${order}" ]]; then return 1; fi
  if [[ -n "${DDD_RELEASE_CLOSURE_OWNER}" && "${DDD_RELEASE_CLOSURE_OWNER}" != "${owner}" ]]; then return 1; fi
  if [[ -n "${DDD_RELEASE_CLOSURE_PRIORITY}" && "${DDD_RELEASE_CLOSURE_PRIORITY}" != "${priority}" ]]; then return 1; fi
  if [[ -n "${DDD_RELEASE_CLOSURE_KIND}" && "${DDD_RELEASE_CLOSURE_KIND}" != "${kind}" ]]; then return 1; fi
  return 0
}
check_closure_env() {
  local order="$1"
  local owner="$2"
  shift 2
  local missing=0
  local key
  if [[ "$#" -eq 0 ]]; then
    echo "[ddd-release-closure][env-ok] order=${order} owner=${owner} requiredEnv=none"
    return 0
  fi
  for key in "$@"; do
    if [[ -z "${!key:-}" ]]; then
      echo "[ddd-release-closure][env-missing] order=${order} owner=${owner} key=${key}" >&2
      missing=1
    fi
  done
  if [[ "${missing}" == "0" ]]; then
    echo "[ddd-release-closure][env-ok] order=${order} owner=${owner}"
  fi
  return "${missing}"
}
run_closure_command() {
  local command="$1"
  if [[ "${DDD_RELEASE_CLOSURE_EXECUTE}" != "1" && "${DDD_RELEASE_CLOSURE_EXECUTE}" != "true" ]]; then
    echo "[ddd-release-closure][dry-run] ${command}"
    return 0
  fi
  set +e
  bash -lc "${command}"
  local status=$?
  set -e
  if [[ "${status}" != "0" ]]; then
    echo "[ddd-release-closure][command-failed] status=${status} command=${command}" >&2
    DDD_RELEASE_CLOSURE_COMMAND_FAILURES=$((DDD_RELEASE_CLOSURE_COMMAND_FAILURES + 1))
    if [[ "${DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR}" == "1" || "${DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR}" == "true" ]]; then
      echo "[ddd-release-closure][command-failed] continuing because DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR=${DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR}" >&2
      return 0
    fi
    return "${status}"
  fi
  return 0
}

if [[ "${DDD_RELEASE_CLOSURE_DETAIL}" == "1" || "${DDD_RELEASE_CLOSURE_DETAIL}" == "true" ]]; then
  DDD_RELEASE_CLOSURE_DETAIL_MATCHED=0
  if matches_closure_filter '1' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=1'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=release-env-lint-placeholders'
    echo 'batch=p0-release-env-lint-release-infra'
    echo 'reason=unresolvedTemplateKeys=93'
    echo 'envKeys=AI_SERVICE_BASE_URL;AUTH_SERVICE_BASE_URL;BASE_URL;CORS_ALLOWED_ORIGIN_PATTERNS;DB_PASSWORD;DB_URL;DB_USERNAME;DDD_AUTH_PASSWORD;DDD_AUTH_PERF_BASELINE_ACCEPTED_BY;DDD_AUTH_PERF_BASELINE_ENVIRONMENT;DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT;DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE;DDD_AUTH_PERF_ENVIRONMENT;DDD_AUTH_USERNAME;DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE;DDD_DEPLOYMENT_EVIDENCE;DDD_EXPLAIN_DATABASE;DDD_FRONTEND_DEPLOYMENT_EVIDENCE;DDD_MIGRATION_COMPLETED_AT;DDD_MIGRATION_FRESH_DB_EVIDENCE;DDD_MIGRATION_FRESH_DB_VALIDATED;DDD_MIGRATION_OPERATOR;DDD_MIGRATION_UPGRADE_DB_EVIDENCE;DDD_MIGRATION_UPGRADE_DB_VALIDATED;FIELD_SECRET;FILE_SERVICE_BASE_URL;JOB_EXECUTOR_BASE_URL;JWT_SECRET;LOCALIZATION_SERVICE_BASE_URL;LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL;LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL;LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN;LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL;LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY;LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL;LUMIRA_BASE_URL;MESSAGE_SERVICE_BASE_URL;MYSQL_DATABASE;MYSQL_HOST;MYSQL_PORT;PAYMENT_PUBLIC_BASE_URL;PAYMENT_SERVICE_BASE_URL;PLAYWRIGHT_BASE_URL;PLUGIN_SERVICE_BASE_URL;REDIS_HOST;SAAS_EVENT_REDIS_STREAM_KEY;SAAS_JOB_BACKEND_BASE_URL;SAAS_JOB_FILE_SERVICE_BASE_URL;SAAS_JOB_INTERNAL_TOKEN;SAAS_JOB_MESSAGE_SERVICE_BASE_URL;SAAS_JOB_PAYMENT_SERVICE_BASE_URL;SAAS_JOB_PLUGIN_SERVICE_BASE_URL;SYSTEM_SERVICE_BASE_URL;XXL_JOB_ACCESS_TOKEN;XXL_JOB_ADMIN_ADDRESSES'
    echo "commands:"
    echo '- DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs'
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '2' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=2'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=release-env-lint-status'
    echo 'batch=p0-release-env-lint-release-infra'
    echo 'reason=status=FAIL primaryBlockers=55'
    echo 'envKeys=AI_SERVICE_BASE_URL;AUTH_SERVICE_BASE_URL;BASE_URL;CORS_ALLOWED_ORIGIN_PATTERNS;DB_PASSWORD;DB_URL;DB_USERNAME;DDD_AUTH_PASSWORD;DDD_AUTH_PERF_BASELINE_ACCEPTED_BY;DDD_AUTH_PERF_BASELINE_ENVIRONMENT;DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT;DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE;DDD_AUTH_PERF_ENVIRONMENT;DDD_AUTH_USERNAME;DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE;DDD_DEPLOYMENT_EVIDENCE;DDD_EXPLAIN_DATABASE;DDD_FRONTEND_DEPLOYMENT_EVIDENCE;DDD_MIGRATION_COMPLETED_AT;DDD_MIGRATION_FRESH_DB_EVIDENCE;DDD_MIGRATION_FRESH_DB_VALIDATED;DDD_MIGRATION_OPERATOR;DDD_MIGRATION_UPGRADE_DB_EVIDENCE;DDD_MIGRATION_UPGRADE_DB_VALIDATED;FIELD_SECRET;FILE_SERVICE_BASE_URL;JOB_EXECUTOR_BASE_URL;JWT_SECRET;LOCALIZATION_SERVICE_BASE_URL;LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL;LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL;LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN;LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL;LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY;LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL;LUMIRA_BASE_URL;MESSAGE_SERVICE_BASE_URL;MYSQL_DATABASE;MYSQL_HOST;MYSQL_PORT;PAYMENT_PUBLIC_BASE_URL;PAYMENT_SERVICE_BASE_URL;PLAYWRIGHT_BASE_URL;PLUGIN_SERVICE_BASE_URL;REDIS_HOST;SAAS_EVENT_REDIS_STREAM_KEY;SAAS_JOB_BACKEND_BASE_URL;SAAS_JOB_FILE_SERVICE_BASE_URL;SAAS_JOB_INTERNAL_TOKEN;SAAS_JOB_MESSAGE_SERVICE_BASE_URL;SAAS_JOB_PAYMENT_SERVICE_BASE_URL;SAAS_JOB_PLUGIN_SERVICE_BASE_URL;SYSTEM_SERVICE_BASE_URL;XXL_JOB_ACCESS_TOKEN;XXL_JOB_ADMIN_ADDRESSES'
    echo "commands:"
    echo '- DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs'
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '3' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=3'
    echo 'owner=ai-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=file owner url'
    echo 'batch=p0-release-config-ai-owner'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_AI_OWNER_FILE_BASE_URL;LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '4' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=4'
    echo 'owner=ai-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=file owner url'
    echo 'batch=p0-release-config-ai-owner'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_AI_OWNER_FILE_BASE_URL;LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '5' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=5'
    echo 'owner=ai-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=iam owner url'
    echo 'batch=p0-release-config-ai-owner'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_AI_OWNER_IAM_BASE_URL;LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '6' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=6'
    echo 'owner=ai-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=iam owner url'
    echo 'batch=p0-release-config-ai-owner'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_AI_OWNER_IAM_BASE_URL;LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '7' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=7'
    echo 'owner=ai-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=owner internal token'
    echo 'batch=p0-release-config-ai-owner'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN;LUMIRA_AI_OWNER_INTERNAL_TOKEN;SAAS_JOB_INTERNAL_TOKEN'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '8' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=8'
    echo 'owner=ai-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=owner internal token'
    echo 'batch=p0-release-config-ai-owner'
    echo 'reason=must be at least 32 characters'
    echo 'envKeys=LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN;LUMIRA_AI_OWNER_INTERNAL_TOKEN;SAAS_JOB_INTERNAL_TOKEN'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '9' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=9'
    echo 'owner=ai-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=platform owner url'
    echo 'batch=p0-release-config-ai-owner'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL;LUMIRA_AI_OWNER_PLATFORM_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '10' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=10'
    echo 'owner=ai-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=platform owner url'
    echo 'batch=p0-release-config-ai-owner'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL;LUMIRA_AI_OWNER_PLATFORM_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '11' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=11'
    echo 'owner=ai-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=provider api key'
    echo 'batch=p0-release-config-ai-owner'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_AI_PROVIDER_API_KEY;LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '12' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=12'
    echo 'owner=ai-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=provider api key'
    echo 'batch=p0-release-config-ai-owner'
    echo 'reason=must be at least 32 characters'
    echo 'envKeys=LUMIRA_AI_PROVIDER_API_KEY;LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '13' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=13'
    echo 'owner=ai-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=provider base url'
    echo 'batch=p0-release-config-ai-owner'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_AI_PROVIDER_BASE_URL;LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '14' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=14'
    echo 'owner=ai-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=provider base url'
    echo 'batch=p0-release-config-ai-owner'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_AI_PROVIDER_BASE_URL;LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '15' 'payment-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=15'
    echo 'owner=payment-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=payment public url'
    echo 'batch=p0-release-config-payment-owner'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=PAYMENT_PUBLIC_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '16' 'payment-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=16'
    echo 'owner=payment-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=payment public url'
    echo 'batch=p0-release-config-payment-owner'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=PAYMENT_PUBLIC_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '17' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=17'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=event stream key'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_EVENT_REDIS_STREAM_KEY;SAAS_EVENT_REDIS_STREAM_KEY'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '18' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=18'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job backend url'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_JOB_BACKEND_BASE_URL;SAAS_JOB_BACKEND_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '19' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=19'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job backend url'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_JOB_BACKEND_BASE_URL;SAAS_JOB_BACKEND_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '20' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=20'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job file url'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_JOB_FILE_SERVICE_BASE_URL;SAAS_JOB_FILE_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '21' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=21'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job file url'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_JOB_FILE_SERVICE_BASE_URL;SAAS_JOB_FILE_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '22' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=22'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job internal token'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=DDD_JOB_INTERNAL_TOKEN;LUMIRA_JOB_INTERNAL_TOKEN;SAAS_JOB_INTERNAL_TOKEN'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '23' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=23'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job internal token'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=must be at least 32 characters'
    echo 'envKeys=DDD_JOB_INTERNAL_TOKEN;LUMIRA_JOB_INTERNAL_TOKEN;SAAS_JOB_INTERNAL_TOKEN'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '24' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=24'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job message url'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL;SAAS_JOB_MESSAGE_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '25' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=25'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job message url'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL;SAAS_JOB_MESSAGE_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '26' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=26'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job payment url'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL;SAAS_JOB_PAYMENT_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '27' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=27'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job payment url'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL;SAAS_JOB_PAYMENT_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '28' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=28'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job plugin url'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL;SAAS_JOB_PLUGIN_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '29' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=29'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job plugin url'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL;SAAS_JOB_PLUGIN_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '30' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=30'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=xxl job admin'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_XXL_JOB_ADMIN_ADDRESSES;XXL_JOB_ADMIN_ADDRESSES'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '31' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=31'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=xxl job admin'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_XXL_JOB_ADMIN_ADDRESSES;XXL_JOB_ADMIN_ADDRESSES'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '32' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=32'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=xxl job token'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_XXL_JOB_ACCESS_TOKEN;XXL_JOB_ACCESS_TOKEN;XXL_JOB_ADMIN_ACCESS_TOKEN'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '33' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=33'
    echo 'owner=platform-events'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=xxl job token'
    echo 'batch=p0-release-config-platform-events'
    echo 'reason=must be at least 32 characters'
    echo 'envKeys=LUMIRA_XXL_JOB_ACCESS_TOKEN;XXL_JOB_ACCESS_TOKEN;XXL_JOB_ADMIN_ACCESS_TOKEN'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '34' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=34'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=ai service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=AI_SERVICE_BASE_URL;LUMIRA_AI_BASE_URL;LUMIRA_AI_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '35' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=35'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=ai service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=AI_SERVICE_BASE_URL;LUMIRA_AI_BASE_URL;LUMIRA_AI_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '36' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=36'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=auth service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=AUTH_SERVICE_BASE_URL;LUMIRA_AUTH_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '37' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=37'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=auth service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=AUTH_SERVICE_BASE_URL;LUMIRA_AUTH_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '38' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=38'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=file service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=FILE_SERVICE_BASE_URL;LUMIRA_FILE_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '39' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=39'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=file service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=FILE_SERVICE_BASE_URL;LUMIRA_FILE_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '40' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=40'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job executor'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=JOB_EXECUTOR_BASE_URL;LUMIRA_JOB_EXECUTOR_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '41' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=41'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=job executor'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=JOB_EXECUTOR_BASE_URL;LUMIRA_JOB_EXECUTOR_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '42' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=42'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=localization service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LOCALIZATION_SERVICE_BASE_URL;LUMIRA_LOCALIZATION_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '43' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=43'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=localization service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LOCALIZATION_SERVICE_BASE_URL;LUMIRA_LOCALIZATION_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '44' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=44'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=message service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_MESSAGE_SERVICE_BASE_URL;MESSAGE_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '45' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=45'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=message service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_MESSAGE_SERVICE_BASE_URL;MESSAGE_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '46' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=46'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=payment service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_PAYMENT_SERVICE_BASE_URL;PAYMENT_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '47' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=47'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=payment service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_PAYMENT_SERVICE_BASE_URL;PAYMENT_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '48' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=48'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=plugin service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_PLUGIN_SERVICE_BASE_URL;PLUGIN_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '49' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=49'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=plugin service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_PLUGIN_SERVICE_BASE_URL;PLUGIN_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '50' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=50'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=system service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=LUMIRA_SYSTEM_SERVICE_BASE_URL;SYSTEM_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '51' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=51'
    echo 'owner=platform-owners'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=system service'
    echo 'batch=p0-release-config-platform-owners'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=LUMIRA_SYSTEM_SERVICE_BASE_URL;SYSTEM_SERVICE_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '52' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=52'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=backend base url'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=DEPLOY_CHECK_BASE_URL;LUMIRA_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '53' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=53'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=backend base url'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=DEPLOY_CHECK_BASE_URL;LUMIRA_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '54' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=54'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=cors origins'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=CORS_ALLOWED_ORIGIN_PATTERNS;SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '55' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=55'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=database password'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=DB_PASSWORD;MYSQL_PASSWORD;SPRING_DATASOURCE_PASSWORD'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '56' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=56'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=database password'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=must be at least 16 characters'
    echo 'envKeys=DB_PASSWORD;MYSQL_PASSWORD;SPRING_DATASOURCE_PASSWORD'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '57' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=57'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=database url'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=DB_URL;SPRING_DATASOURCE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '58' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=58'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=database username'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=DB_USERNAME;MYSQL_USER;SPRING_DATASOURCE_USERNAME'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '59' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=59'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=field secret'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=FIELD_SECRET;SAAS_SECURITY_FIELD_SECRET'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '60' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=60'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=field secret'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=must be at least 32 characters'
    echo 'envKeys=FIELD_SECRET;SAAS_SECURITY_FIELD_SECRET'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '61' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=61'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=frontend base url'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=FRONTEND_BASE_URL;PLAYWRIGHT_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '62' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=62'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=frontend base url'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=must use HTTPS for production-equivalent evidence'
    echo 'envKeys=FRONTEND_BASE_URL;PLAYWRIGHT_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '63' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=63'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=jwt secret'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=JWT_SECRET;SAAS_SECURITY_JWT_SECRET'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '64' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=64'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=jwt secret'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=must be at least 32 characters'
    echo 'envKeys=JWT_SECRET;SAAS_SECURITY_JWT_SECRET'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '65' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=65'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=redis host'
    echo 'batch=p0-release-config-release-infra'
    echo 'reason=placeholder value is not allowed'
    echo 'envKeys=REDIS_HOST;SPRING_DATA_REDIS_HOST'
    echo "commands:"
    echo '- node scripts/ddd-release-config-evidence.mjs'
  fi
  if matches_closure_filter '66' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=66'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=docker-blocker-1'
    echo 'batch=p0-docker-release-infra'
    echo 'reason=lumira-server: docker build failed after 3 attempt(s) with transient registry/network error status 1'
    echo 'envKeys=DDD_DOCKER_BUILD_STRICT;DDD_DOCKER_COMMAND'
    echo "commands:"
    echo '- DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs'
    echo '- node scripts/ddd-docker-build-evidence.mjs'
  fi
  if matches_closure_filter '67' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=67'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=docker-blocker-2'
    echo 'batch=p0-docker-release-infra'
    echo 'reason=frontend: docker build failed after 3 attempt(s) with transient registry/network error status 1'
    echo 'envKeys=DDD_DOCKER_BUILD_STRICT;DDD_DOCKER_COMMAND'
    echo "commands:"
    echo '- DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs'
    echo '- node scripts/ddd-docker-build-evidence.mjs'
  fi
  if matches_closure_filter '68' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=68'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=docker-image-frontend-failed'
    echo 'batch=p0-docker-release-infra'
    echo 'reason=docker build failed after 3 attempt(s) with transient registry/network error status 1'
    echo 'envKeys=DDD_DOCKER_BUILD_STRICT;DDD_DOCKER_COMMAND'
    echo "commands:"
    echo '- DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs'
    echo '- node scripts/ddd-docker-build-evidence.mjs'
  fi
  if matches_closure_filter '69' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=69'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=docker-image-lumira-server-failed'
    echo 'batch=p0-docker-release-infra'
    echo 'reason=docker build failed after 3 attempt(s) with transient registry/network error status 1'
    echo 'envKeys=DDD_DOCKER_BUILD_STRICT;DDD_DOCKER_COMMAND'
    echo "commands:"
    echo '- DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs'
    echo '- node scripts/ddd-docker-build-evidence.mjs'
  fi
  if matches_closure_filter '70' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=70'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=runtime-readiness-contract-1'
    echo 'batch=p0-runtime-readiness-release-infra'
    echo 'reason=runtime readiness productionEquivalence.strict must be true for strict release evidence'
    echo 'envKeys=DDD_EVIDENCE_ENVIRONMENT;DDD_EVIDENCE_OPERATOR;DDD_RELEASE_CANDIDATE;LUMIRA_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-runtime-readiness-smoke.mjs'
  fi
  if matches_closure_filter '71' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=71'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=runtime-readiness-contract-2'
    echo 'batch=p0-runtime-readiness-release-infra'
    echo 'reason=runtime readiness productionEquivalence.https must be true for strict release evidence'
    echo 'envKeys=DDD_EVIDENCE_ENVIRONMENT;DDD_EVIDENCE_OPERATOR;DDD_RELEASE_CANDIDATE;LUMIRA_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-runtime-readiness-smoke.mjs'
  fi
  if matches_closure_filter '72' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=72'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=runtime-readiness-contract-3'
    echo 'batch=p0-runtime-readiness-release-infra'
    echo 'reason=runtime readiness productionEquivalence.localOnly must be false for strict release evidence'
    echo 'envKeys=DDD_EVIDENCE_ENVIRONMENT;DDD_EVIDENCE_OPERATOR;DDD_RELEASE_CANDIDATE;LUMIRA_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-runtime-readiness-smoke.mjs'
  fi
  if matches_closure_filter '73' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=73'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=runtime-readiness-contract-4'
    echo 'batch=p0-runtime-readiness-release-infra'
    echo 'reason=runtime readiness productionEquivalence.deploymentEvidence is required'
    echo 'envKeys=DDD_EVIDENCE_ENVIRONMENT;DDD_EVIDENCE_OPERATOR;DDD_RELEASE_CANDIDATE;LUMIRA_BASE_URL'
    echo "commands:"
    echo '- node scripts/ddd-runtime-readiness-smoke.mjs'
  fi
  if matches_closure_filter '74' 'release-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=74'
    echo 'owner=release-owner'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=manifest-missing-no-explain-json-files-in-tmp-ddd-explain'
    echo 'batch=p0-manifest-release-owner'
    echo 'reason=no explain JSON files in tmp\ddd-explain'
    echo 'envKeys=DDD_EVIDENCE_ENVIRONMENT;DDD_EVIDENCE_OPERATOR;DDD_RELEASE_CANDIDATE;DDD_RELEASE_MANIFEST_STRICT'
    echo "commands:"
    echo '- DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs'
    echo '- node scripts/ddd-promote-performance-baseline.mjs'
    echo '- DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs'
  fi
  if matches_closure_filter '75' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=75'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_LOCAL'
    echo 'id=performance-actual-shape-1'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=authenticated performance actual productionEquivalence.strict must be true for strict release evidence'
    echo 'envKeys=none'
    echo "commands:"
    echo '- node scripts/ddd-authenticated-performance-smoke.mjs'
    echo '- node scripts/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '76' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=76'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=performance-actual-shape-2'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=authenticated performance actual productionEquivalence.https must be true for strict release evidence'
    echo 'envKeys=none'
    echo "commands:"
    echo '- node scripts/ddd-authenticated-performance-smoke.mjs'
    echo '- node scripts/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '77' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=77'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_LOCAL'
    echo 'id=performance-actual-shape-3'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence'
    echo 'envKeys=none'
    echo "commands:"
    echo '- node scripts/ddd-authenticated-performance-smoke.mjs'
    echo '- node scripts/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '78' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=78'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_LOCAL'
    echo 'id=performance-actual-shape-4'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=authenticated performance actual productionEquivalence.deploymentEvidence is required'
    echo 'envKeys=none'
    echo "commands:"
    echo '- node scripts/ddd-authenticated-performance-smoke.mjs'
    echo '- node scripts/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '79' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=79'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=performance-baseline-metadata-5'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=strict release baseline requires baselineType=authenticated-runtime'
    echo 'envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY;DDD_AUTH_PERF_BASELINE_ENVIRONMENT;DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT;DDD_RELEASE_CANDIDATE'
    echo "commands:"
    echo '- node scripts/ddd-authenticated-performance-smoke.mjs'
    echo '- node scripts/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '80' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=80'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=performance-baseline-metadata-6'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=acceptedAt must be an ISO timestamp'
    echo 'envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY;DDD_AUTH_PERF_BASELINE_ENVIRONMENT;DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT;DDD_RELEASE_CANDIDATE'
    echo "commands:"
    echo '- node scripts/ddd-authenticated-performance-smoke.mjs'
    echo '- node scripts/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '81' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=81'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=performance-baseline-metadata-7'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=acceptedBy is required'
    echo 'envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY;DDD_AUTH_PERF_BASELINE_ENVIRONMENT;DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT;DDD_RELEASE_CANDIDATE'
    echo "commands:"
    echo '- node scripts/ddd-authenticated-performance-smoke.mjs'
    echo '- node scripts/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '82' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=82'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=performance-baseline-metadata-8'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=sourceArtifact is required'
    echo 'envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY;DDD_AUTH_PERF_BASELINE_ENVIRONMENT;DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT;DDD_RELEASE_CANDIDATE'
    echo "commands:"
    echo '- node scripts/ddd-authenticated-performance-smoke.mjs'
    echo '- node scripts/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '83' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=83'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=performance-baseline-metadata-9'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=sourceSha256 must be a SHA-256 hex digest'
    echo 'envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY;DDD_AUTH_PERF_BASELINE_ENVIRONMENT;DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT;DDD_RELEASE_CANDIDATE'
    echo "commands:"
    echo '- node scripts/ddd-authenticated-performance-smoke.mjs'
    echo '- node scripts/ddd-promote-performance-baseline.mjs'
  fi
  if [[ "${DDD_RELEASE_CLOSURE_DETAIL_MATCHED}" != "1" ]]; then
    echo "No runnable closure item matched the requested filters." >&2
    exit 1
  fi
  exit 0
fi

if [[ "${DDD_RELEASE_CLOSURE_CHECK_ENV}" == "1" || "${DDD_RELEASE_CLOSURE_CHECK_ENV}" == "true" ]]; then
  DDD_RELEASE_CLOSURE_ENV_MATCHED=0
  DDD_RELEASE_CLOSURE_ENV_FAILED=0
  if matches_closure_filter '1' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '1' 'release-infra' 'AI_SERVICE_BASE_URL' 'AUTH_SERVICE_BASE_URL' 'BASE_URL' 'CORS_ALLOWED_ORIGIN_PATTERNS' 'DB_PASSWORD' 'DB_URL' 'DB_USERNAME' 'DDD_AUTH_PASSWORD' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE' 'DDD_AUTH_PERF_ENVIRONMENT' 'DDD_AUTH_USERNAME' 'DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE' 'DDD_DEPLOYMENT_EVIDENCE' 'DDD_EXPLAIN_DATABASE' 'DDD_FRONTEND_DEPLOYMENT_EVIDENCE' 'DDD_MIGRATION_COMPLETED_AT' 'DDD_MIGRATION_FRESH_DB_EVIDENCE' 'DDD_MIGRATION_FRESH_DB_VALIDATED' 'DDD_MIGRATION_OPERATOR' 'DDD_MIGRATION_UPGRADE_DB_EVIDENCE' 'DDD_MIGRATION_UPGRADE_DB_VALIDATED' 'FIELD_SECRET' 'FILE_SERVICE_BASE_URL' 'JOB_EXECUTOR_BASE_URL' 'JWT_SECRET' 'LOCALIZATION_SERVICE_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN' 'LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL' 'LUMIRA_BASE_URL' 'MESSAGE_SERVICE_BASE_URL' 'MYSQL_DATABASE' 'MYSQL_HOST' 'MYSQL_PORT' 'PAYMENT_PUBLIC_BASE_URL' 'PAYMENT_SERVICE_BASE_URL' 'PLAYWRIGHT_BASE_URL' 'PLUGIN_SERVICE_BASE_URL' 'REDIS_HOST' 'SAAS_EVENT_REDIS_STREAM_KEY' 'SAAS_JOB_BACKEND_BASE_URL' 'SAAS_JOB_FILE_SERVICE_BASE_URL' 'SAAS_JOB_INTERNAL_TOKEN' 'SAAS_JOB_MESSAGE_SERVICE_BASE_URL' 'SAAS_JOB_PAYMENT_SERVICE_BASE_URL' 'SAAS_JOB_PLUGIN_SERVICE_BASE_URL' 'SYSTEM_SERVICE_BASE_URL' 'XXL_JOB_ACCESS_TOKEN' 'XXL_JOB_ADMIN_ADDRESSES' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '2' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '2' 'release-infra' 'AI_SERVICE_BASE_URL' 'AUTH_SERVICE_BASE_URL' 'BASE_URL' 'CORS_ALLOWED_ORIGIN_PATTERNS' 'DB_PASSWORD' 'DB_URL' 'DB_USERNAME' 'DDD_AUTH_PASSWORD' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE' 'DDD_AUTH_PERF_ENVIRONMENT' 'DDD_AUTH_USERNAME' 'DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE' 'DDD_DEPLOYMENT_EVIDENCE' 'DDD_EXPLAIN_DATABASE' 'DDD_FRONTEND_DEPLOYMENT_EVIDENCE' 'DDD_MIGRATION_COMPLETED_AT' 'DDD_MIGRATION_FRESH_DB_EVIDENCE' 'DDD_MIGRATION_FRESH_DB_VALIDATED' 'DDD_MIGRATION_OPERATOR' 'DDD_MIGRATION_UPGRADE_DB_EVIDENCE' 'DDD_MIGRATION_UPGRADE_DB_VALIDATED' 'FIELD_SECRET' 'FILE_SERVICE_BASE_URL' 'JOB_EXECUTOR_BASE_URL' 'JWT_SECRET' 'LOCALIZATION_SERVICE_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN' 'LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL' 'LUMIRA_BASE_URL' 'MESSAGE_SERVICE_BASE_URL' 'MYSQL_DATABASE' 'MYSQL_HOST' 'MYSQL_PORT' 'PAYMENT_PUBLIC_BASE_URL' 'PAYMENT_SERVICE_BASE_URL' 'PLAYWRIGHT_BASE_URL' 'PLUGIN_SERVICE_BASE_URL' 'REDIS_HOST' 'SAAS_EVENT_REDIS_STREAM_KEY' 'SAAS_JOB_BACKEND_BASE_URL' 'SAAS_JOB_FILE_SERVICE_BASE_URL' 'SAAS_JOB_INTERNAL_TOKEN' 'SAAS_JOB_MESSAGE_SERVICE_BASE_URL' 'SAAS_JOB_PAYMENT_SERVICE_BASE_URL' 'SAAS_JOB_PLUGIN_SERVICE_BASE_URL' 'SYSTEM_SERVICE_BASE_URL' 'XXL_JOB_ACCESS_TOKEN' 'XXL_JOB_ADMIN_ADDRESSES' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '3' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '3' 'ai-owner' 'LUMIRA_AI_OWNER_FILE_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '4' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '4' 'ai-owner' 'LUMIRA_AI_OWNER_FILE_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '5' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '5' 'ai-owner' 'LUMIRA_AI_OWNER_IAM_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '6' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '6' 'ai-owner' 'LUMIRA_AI_OWNER_IAM_BASE_URL' 'LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '7' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '7' 'ai-owner' 'LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN' 'LUMIRA_AI_OWNER_INTERNAL_TOKEN' 'SAAS_JOB_INTERNAL_TOKEN' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '8' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '8' 'ai-owner' 'LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN' 'LUMIRA_AI_OWNER_INTERNAL_TOKEN' 'SAAS_JOB_INTERNAL_TOKEN' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '9' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '9' 'ai-owner' 'LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL' 'LUMIRA_AI_OWNER_PLATFORM_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '10' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '10' 'ai-owner' 'LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL' 'LUMIRA_AI_OWNER_PLATFORM_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '11' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '11' 'ai-owner' 'LUMIRA_AI_PROVIDER_API_KEY' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '12' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '12' 'ai-owner' 'LUMIRA_AI_PROVIDER_API_KEY' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '13' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '13' 'ai-owner' 'LUMIRA_AI_PROVIDER_BASE_URL' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '14' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '14' 'ai-owner' 'LUMIRA_AI_PROVIDER_BASE_URL' 'LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '15' 'payment-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '15' 'payment-owner' 'PAYMENT_PUBLIC_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '16' 'payment-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '16' 'payment-owner' 'PAYMENT_PUBLIC_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '17' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '17' 'platform-events' 'LUMIRA_EVENT_REDIS_STREAM_KEY' 'SAAS_EVENT_REDIS_STREAM_KEY' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '18' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '18' 'platform-events' 'LUMIRA_JOB_BACKEND_BASE_URL' 'SAAS_JOB_BACKEND_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '19' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '19' 'platform-events' 'LUMIRA_JOB_BACKEND_BASE_URL' 'SAAS_JOB_BACKEND_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '20' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '20' 'platform-events' 'LUMIRA_JOB_FILE_SERVICE_BASE_URL' 'SAAS_JOB_FILE_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '21' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '21' 'platform-events' 'LUMIRA_JOB_FILE_SERVICE_BASE_URL' 'SAAS_JOB_FILE_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '22' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '22' 'platform-events' 'DDD_JOB_INTERNAL_TOKEN' 'LUMIRA_JOB_INTERNAL_TOKEN' 'SAAS_JOB_INTERNAL_TOKEN' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '23' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '23' 'platform-events' 'DDD_JOB_INTERNAL_TOKEN' 'LUMIRA_JOB_INTERNAL_TOKEN' 'SAAS_JOB_INTERNAL_TOKEN' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '24' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '24' 'platform-events' 'LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL' 'SAAS_JOB_MESSAGE_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '25' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '25' 'platform-events' 'LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL' 'SAAS_JOB_MESSAGE_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '26' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '26' 'platform-events' 'LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL' 'SAAS_JOB_PAYMENT_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '27' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '27' 'platform-events' 'LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL' 'SAAS_JOB_PAYMENT_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '28' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '28' 'platform-events' 'LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL' 'SAAS_JOB_PLUGIN_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '29' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '29' 'platform-events' 'LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL' 'SAAS_JOB_PLUGIN_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '30' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '30' 'platform-events' 'LUMIRA_XXL_JOB_ADMIN_ADDRESSES' 'XXL_JOB_ADMIN_ADDRESSES' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '31' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '31' 'platform-events' 'LUMIRA_XXL_JOB_ADMIN_ADDRESSES' 'XXL_JOB_ADMIN_ADDRESSES' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '32' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '32' 'platform-events' 'LUMIRA_XXL_JOB_ACCESS_TOKEN' 'XXL_JOB_ACCESS_TOKEN' 'XXL_JOB_ADMIN_ACCESS_TOKEN' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '33' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '33' 'platform-events' 'LUMIRA_XXL_JOB_ACCESS_TOKEN' 'XXL_JOB_ACCESS_TOKEN' 'XXL_JOB_ADMIN_ACCESS_TOKEN' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '34' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '34' 'platform-owners' 'AI_SERVICE_BASE_URL' 'LUMIRA_AI_BASE_URL' 'LUMIRA_AI_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '35' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '35' 'platform-owners' 'AI_SERVICE_BASE_URL' 'LUMIRA_AI_BASE_URL' 'LUMIRA_AI_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '36' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '36' 'platform-owners' 'AUTH_SERVICE_BASE_URL' 'LUMIRA_AUTH_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '37' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '37' 'platform-owners' 'AUTH_SERVICE_BASE_URL' 'LUMIRA_AUTH_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '38' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '38' 'platform-owners' 'FILE_SERVICE_BASE_URL' 'LUMIRA_FILE_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '39' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '39' 'platform-owners' 'FILE_SERVICE_BASE_URL' 'LUMIRA_FILE_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '40' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '40' 'platform-owners' 'JOB_EXECUTOR_BASE_URL' 'LUMIRA_JOB_EXECUTOR_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '41' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '41' 'platform-owners' 'JOB_EXECUTOR_BASE_URL' 'LUMIRA_JOB_EXECUTOR_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '42' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '42' 'platform-owners' 'LOCALIZATION_SERVICE_BASE_URL' 'LUMIRA_LOCALIZATION_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '43' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '43' 'platform-owners' 'LOCALIZATION_SERVICE_BASE_URL' 'LUMIRA_LOCALIZATION_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '44' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '44' 'platform-owners' 'LUMIRA_MESSAGE_SERVICE_BASE_URL' 'MESSAGE_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '45' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '45' 'platform-owners' 'LUMIRA_MESSAGE_SERVICE_BASE_URL' 'MESSAGE_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '46' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '46' 'platform-owners' 'LUMIRA_PAYMENT_SERVICE_BASE_URL' 'PAYMENT_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '47' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '47' 'platform-owners' 'LUMIRA_PAYMENT_SERVICE_BASE_URL' 'PAYMENT_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '48' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '48' 'platform-owners' 'LUMIRA_PLUGIN_SERVICE_BASE_URL' 'PLUGIN_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '49' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '49' 'platform-owners' 'LUMIRA_PLUGIN_SERVICE_BASE_URL' 'PLUGIN_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '50' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '50' 'platform-owners' 'LUMIRA_SYSTEM_SERVICE_BASE_URL' 'SYSTEM_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '51' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '51' 'platform-owners' 'LUMIRA_SYSTEM_SERVICE_BASE_URL' 'SYSTEM_SERVICE_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '52' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '52' 'release-infra' 'DEPLOY_CHECK_BASE_URL' 'LUMIRA_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '53' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '53' 'release-infra' 'DEPLOY_CHECK_BASE_URL' 'LUMIRA_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '54' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '54' 'release-infra' 'CORS_ALLOWED_ORIGIN_PATTERNS' 'SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '55' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '55' 'release-infra' 'DB_PASSWORD' 'MYSQL_PASSWORD' 'SPRING_DATASOURCE_PASSWORD' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '56' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '56' 'release-infra' 'DB_PASSWORD' 'MYSQL_PASSWORD' 'SPRING_DATASOURCE_PASSWORD' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '57' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '57' 'release-infra' 'DB_URL' 'SPRING_DATASOURCE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '58' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '58' 'release-infra' 'DB_USERNAME' 'MYSQL_USER' 'SPRING_DATASOURCE_USERNAME' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '59' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '59' 'release-infra' 'FIELD_SECRET' 'SAAS_SECURITY_FIELD_SECRET' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '60' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '60' 'release-infra' 'FIELD_SECRET' 'SAAS_SECURITY_FIELD_SECRET' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '61' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '61' 'release-infra' 'FRONTEND_BASE_URL' 'PLAYWRIGHT_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '62' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '62' 'release-infra' 'FRONTEND_BASE_URL' 'PLAYWRIGHT_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '63' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '63' 'release-infra' 'JWT_SECRET' 'SAAS_SECURITY_JWT_SECRET' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '64' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '64' 'release-infra' 'JWT_SECRET' 'SAAS_SECURITY_JWT_SECRET' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '65' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '65' 'release-infra' 'REDIS_HOST' 'SPRING_DATA_REDIS_HOST' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '66' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '66' 'release-infra' 'DDD_DOCKER_BUILD_STRICT' 'DDD_DOCKER_COMMAND' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '67' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '67' 'release-infra' 'DDD_DOCKER_BUILD_STRICT' 'DDD_DOCKER_COMMAND' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '68' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '68' 'release-infra' 'DDD_DOCKER_BUILD_STRICT' 'DDD_DOCKER_COMMAND' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '69' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '69' 'release-infra' 'DDD_DOCKER_BUILD_STRICT' 'DDD_DOCKER_COMMAND' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '70' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '70' 'release-infra' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'LUMIRA_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '71' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '71' 'release-infra' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'LUMIRA_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '72' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '72' 'release-infra' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'LUMIRA_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '73' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '73' 'release-infra' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'LUMIRA_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '74' 'release-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '74' 'release-owner' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DDD_RELEASE_MANIFEST_STRICT' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '75' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '75' 'release-performance' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '76' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '76' 'release-performance' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '77' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '77' 'release-performance' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '78' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '78' 'release-performance' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '79' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '79' 'release-performance' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_RELEASE_CANDIDATE' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '80' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '80' 'release-performance' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_RELEASE_CANDIDATE' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '81' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '81' 'release-performance' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_RELEASE_CANDIDATE' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '82' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '82' 'release-performance' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_RELEASE_CANDIDATE' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '83' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '83' 'release-performance' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_RELEASE_CANDIDATE' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if [[ "${DDD_RELEASE_CLOSURE_ENV_MATCHED}" != "1" ]]; then
    echo "No runnable closure item matched the requested filters." >&2
    exit 1
  fi
  if [[ "${DDD_RELEASE_CLOSURE_ENV_FAILED}" != "0" ]]; then exit 1; fi
  exit 0
fi

if [[ "${DDD_RELEASE_CLOSURE_EXECUTE}" != "1" && "${DDD_RELEASE_CLOSURE_EXECUTE}" != "true" ]]; then
  echo "Runnable release blocker closure waves:"
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'release-infra' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '1 P0 owner=release-infra batch=p0-release-env-lint-release-infra items=1;2'
  fi
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'ai-owner' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '2 P0 owner=ai-owner batch=p0-release-config-ai-owner items=3;4;5;6;7;8;9;10;11;12;13;14'
  fi
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'payment-owner' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '3 P0 owner=payment-owner batch=p0-release-config-payment-owner items=15;16'
  fi
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'platform-events' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '4 P0 owner=platform-events batch=p0-release-config-platform-events items=17;18;19;20;21;22;23;24;25;26;27;28;29;30;31;32;33'
  fi
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'platform-owners' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '5 P0 owner=platform-owners batch=p0-release-config-platform-owners items=34;35;36;37;38;39;40;41;42;43;44;45;46;47;48;49;50;51'
  fi
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'release-infra' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '6 P0 owner=release-infra batch=p0-release-config-release-infra items=52;53;54;55;56;57;58;59;60;61;62;63;64;65'
  fi
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'release-infra' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '7 P0 owner=release-infra batch=p0-docker-release-infra items=66;67;68;69'
  fi
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'release-infra' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '8 P0 owner=release-infra batch=p0-runtime-readiness-release-infra items=70;71;72;73'
  fi
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'release-owner' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '9 P0 owner=release-owner batch=p0-manifest-release-owner items=74'
  fi
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'release-performance' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '10 P0 owner=release-performance batch=p0-authenticated-performance-release-performance items=75;76;77;78;79;80;81;82;83'
  fi
  echo ""
  echo "Runnable release blocker closure items:"
  if matches_closure_filter '1' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '1 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=release-env-lint-placeholders batch=p0-release-env-lint-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '2' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '2 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=release-env-lint-status batch=p0-release-env-lint-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '3' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '3 P0 RUN_NOW_WITH_REAL_ENV owner=ai-owner id=file owner url batch=p0-release-config-ai-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '4' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '4 P0 RUN_NOW_WITH_REAL_ENV owner=ai-owner id=file owner url batch=p0-release-config-ai-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '5' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '5 P0 RUN_NOW_WITH_REAL_ENV owner=ai-owner id=iam owner url batch=p0-release-config-ai-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '6' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '6 P0 RUN_NOW_WITH_REAL_ENV owner=ai-owner id=iam owner url batch=p0-release-config-ai-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '7' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '7 P0 RUN_NOW_WITH_REAL_ENV owner=ai-owner id=owner internal token batch=p0-release-config-ai-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '8' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '8 P0 RUN_NOW_WITH_REAL_ENV owner=ai-owner id=owner internal token batch=p0-release-config-ai-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '9' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '9 P0 RUN_NOW_WITH_REAL_ENV owner=ai-owner id=platform owner url batch=p0-release-config-ai-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '10' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '10 P0 RUN_NOW_WITH_REAL_ENV owner=ai-owner id=platform owner url batch=p0-release-config-ai-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '11' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '11 P0 RUN_NOW_WITH_REAL_ENV owner=ai-owner id=provider api key batch=p0-release-config-ai-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '12' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '12 P0 RUN_NOW_WITH_REAL_ENV owner=ai-owner id=provider api key batch=p0-release-config-ai-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '13' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '13 P0 RUN_NOW_WITH_REAL_ENV owner=ai-owner id=provider base url batch=p0-release-config-ai-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '14' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '14 P0 RUN_NOW_WITH_REAL_ENV owner=ai-owner id=provider base url batch=p0-release-config-ai-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '15' 'payment-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '15 P0 RUN_NOW_WITH_REAL_ENV owner=payment-owner id=payment public url batch=p0-release-config-payment-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '16' 'payment-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '16 P0 RUN_NOW_WITH_REAL_ENV owner=payment-owner id=payment public url batch=p0-release-config-payment-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '17' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '17 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=event stream key batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '18' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '18 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=job backend url batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '19' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '19 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=job backend url batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '20' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '20 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=job file url batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '21' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '21 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=job file url batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '22' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '22 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=job internal token batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '23' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '23 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=job internal token batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '24' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '24 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=job message url batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '25' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '25 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=job message url batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '26' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '26 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=job payment url batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '27' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '27 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=job payment url batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '28' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '28 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=job plugin url batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '29' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '29 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=job plugin url batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '30' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '30 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=xxl job admin batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '31' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '31 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=xxl job admin batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '32' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '32 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=xxl job token batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '33' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '33 P0 RUN_NOW_WITH_REAL_ENV owner=platform-events id=xxl job token batch=p0-release-config-platform-events'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '34' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '34 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=ai service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '35' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '35 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=ai service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '36' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '36 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=auth service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '37' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '37 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=auth service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '38' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '38 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=file service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '39' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '39 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=file service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '40' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '40 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=job executor batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '41' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '41 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=job executor batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '42' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '42 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=localization service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '43' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '43 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=localization service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '44' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '44 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=message service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '45' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '45 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=message service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '46' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '46 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=payment service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '47' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '47 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=payment service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '48' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '48 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=plugin service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '49' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '49 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=plugin service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '50' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '50 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=system service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '51' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '51 P0 RUN_NOW_WITH_REAL_ENV owner=platform-owners id=system service batch=p0-release-config-platform-owners'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '52' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '52 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=backend base url batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '53' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '53 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=backend base url batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '54' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '54 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=cors origins batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '55' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '55 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=database password batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '56' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '56 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=database password batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '57' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '57 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=database url batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '58' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '58 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=database username batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '59' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '59 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=field secret batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '60' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '60 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=field secret batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '61' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '61 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=frontend base url batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '62' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '62 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=frontend base url batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '63' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '63 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=jwt secret batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '64' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '64 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=jwt secret batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '65' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '65 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=redis host batch=p0-release-config-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '66' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '66 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=docker-blocker-1 batch=p0-docker-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '67' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '67 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=docker-blocker-2 batch=p0-docker-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '68' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '68 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=docker-image-frontend-failed batch=p0-docker-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '69' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '69 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=docker-image-lumira-server-failed batch=p0-docker-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '70' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '70 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=runtime-readiness-contract-1 batch=p0-runtime-readiness-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '71' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '71 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=runtime-readiness-contract-2 batch=p0-runtime-readiness-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '72' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '72 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=runtime-readiness-contract-3 batch=p0-runtime-readiness-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '73' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '73 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=runtime-readiness-contract-4 batch=p0-runtime-readiness-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '74' 'release-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '74 P0 RUN_NOW_WITH_REAL_ENV owner=release-owner id=manifest-missing-no-explain-json-files-in-tmp-ddd-explain batch=p0-manifest-release-owner'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '75' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    echo '75 P0 RUN_NOW_LOCAL owner=release-performance id=performance-actual-shape-1 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '76' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '76 P0 RUN_NOW_WITH_REAL_ENV owner=release-performance id=performance-actual-shape-2 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '77' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    echo '77 P0 RUN_NOW_LOCAL owner=release-performance id=performance-actual-shape-3 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '78' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    echo '78 P0 RUN_NOW_LOCAL owner=release-performance id=performance-actual-shape-4 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '79' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '79 P0 RUN_NOW_WITH_REAL_ENV owner=release-performance id=performance-baseline-metadata-5 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '80' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '80 P0 RUN_NOW_WITH_REAL_ENV owner=release-performance id=performance-baseline-metadata-6 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '81' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '81 P0 RUN_NOW_WITH_REAL_ENV owner=release-performance id=performance-baseline-metadata-7 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '82' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '82 P0 RUN_NOW_WITH_REAL_ENV owner=release-performance id=performance-baseline-metadata-8 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '83' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '83 P0 RUN_NOW_WITH_REAL_ENV owner=release-performance id=performance-baseline-metadata-9 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if [[ "${DDD_RELEASE_CLOSURE_MATCHED}" != "1" ]]; then
    echo "No runnable closure item matched the requested filters." >&2
    exit 1
  fi
  exit 0
fi

if matches_closure_filter '1' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=1 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=release-env-lint-placeholders'
# Reason: unresolvedTemplateKeys=93
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json; artifacts/ddd/release/release-env-lint.json
  run_closure_command 'DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs'
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '2' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=2 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=release-env-lint-status'
# Reason: status=FAIL primaryBlockers=55
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json; artifacts/ddd/release/release-env-lint.json
  run_closure_command 'DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs'
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '3' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=3 owner=ai-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=file owner url'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '4' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=4 owner=ai-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=file owner url'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '5' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=5 owner=ai-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=iam owner url'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '6' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=6 owner=ai-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=iam owner url'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '7' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=7 owner=ai-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=owner internal token'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '8' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=8 owner=ai-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=owner internal token'
# Reason: must be at least 32 characters
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '9' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=9 owner=ai-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=platform owner url'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '10' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=10 owner=ai-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=platform owner url'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '11' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=11 owner=ai-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=provider api key'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '12' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=12 owner=ai-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=provider api key'
# Reason: must be at least 32 characters
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '13' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=13 owner=ai-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=provider base url'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '14' 'ai-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=14 owner=ai-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=provider base url'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '15' 'payment-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=15 owner=payment-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=payment public url'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '16' 'payment-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=16 owner=payment-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=payment public url'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '17' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=17 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=event stream key'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '18' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=18 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job backend url'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '19' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=19 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job backend url'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '20' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=20 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job file url'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '21' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=21 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job file url'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '22' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=22 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job internal token'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '23' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=23 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job internal token'
# Reason: must be at least 32 characters
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '24' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=24 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job message url'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '25' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=25 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job message url'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '26' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=26 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job payment url'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '27' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=27 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job payment url'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '28' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=28 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job plugin url'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '29' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=29 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job plugin url'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '30' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=30 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=xxl job admin'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '31' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=31 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=xxl job admin'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '32' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=32 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=xxl job token'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '33' 'platform-events' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=33 owner=platform-events priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=xxl job token'
# Reason: must be at least 32 characters
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '34' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=34 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=ai service'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '35' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=35 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=ai service'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '36' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=36 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=auth service'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '37' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=37 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=auth service'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '38' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=38 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=file service'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '39' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=39 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=file service'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '40' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=40 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job executor'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '41' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=41 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=job executor'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '42' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=42 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=localization service'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '43' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=43 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=localization service'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '44' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=44 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=message service'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '45' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=45 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=message service'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '46' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=46 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=payment service'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '47' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=47 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=payment service'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '48' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=48 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=plugin service'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '49' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=49 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=plugin service'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '50' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=50 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=system service'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '51' 'platform-owners' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=51 owner=platform-owners priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=system service'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '52' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=52 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=backend base url'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '53' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=53 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=backend base url'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '54' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=54 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=cors origins'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '55' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=55 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=database password'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '56' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=56 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=database password'
# Reason: must be at least 16 characters
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '57' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=57 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=database url'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '58' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=58 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=database username'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '59' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=59 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=field secret'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '60' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=60 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=field secret'
# Reason: must be at least 32 characters
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '61' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=61 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=frontend base url'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '62' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=62 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=frontend base url'
# Reason: must use HTTPS for production-equivalent evidence
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '63' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=63 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=jwt secret'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '64' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=64 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=jwt secret'
# Reason: must be at least 32 characters
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '65' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=65 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=redis host'
# Reason: placeholder value is not allowed
# Expected artifacts: artifacts/ddd/config/release-config-evidence.json
  run_closure_command 'node scripts/ddd-release-config-evidence.mjs'
fi

if matches_closure_filter '66' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=66 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=docker-blocker-1'
# Reason: lumira-server: docker build failed after 3 attempt(s) with transient registry/network error status 1
# Expected artifacts: artifacts/ddd/build/docker-image-evidence.json
  run_closure_command 'DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs'
  run_closure_command 'node scripts/ddd-docker-build-evidence.mjs'
fi

if matches_closure_filter '67' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=67 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=docker-blocker-2'
# Reason: frontend: docker build failed after 3 attempt(s) with transient registry/network error status 1
# Expected artifacts: artifacts/ddd/build/docker-image-evidence.json
  run_closure_command 'DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs'
  run_closure_command 'node scripts/ddd-docker-build-evidence.mjs'
fi

if matches_closure_filter '68' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=68 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=docker-image-frontend-failed'
# Reason: docker build failed after 3 attempt(s) with transient registry/network error status 1
# Expected artifacts: artifacts/ddd/build/docker-image-evidence.json
  run_closure_command 'DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs'
  run_closure_command 'node scripts/ddd-docker-build-evidence.mjs'
fi

if matches_closure_filter '69' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=69 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=docker-image-lumira-server-failed'
# Reason: docker build failed after 3 attempt(s) with transient registry/network error status 1
# Expected artifacts: artifacts/ddd/build/docker-image-evidence.json
  run_closure_command 'DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs'
  run_closure_command 'node scripts/ddd-docker-build-evidence.mjs'
fi

if matches_closure_filter '70' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=70 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=runtime-readiness-contract-1'
# Reason: runtime readiness productionEquivalence.strict must be true for strict release evidence
# Expected artifacts: artifacts/ddd/readiness/summary.json
  run_closure_command 'node scripts/ddd-runtime-readiness-smoke.mjs'
fi

if matches_closure_filter '71' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=71 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=runtime-readiness-contract-2'
# Reason: runtime readiness productionEquivalence.https must be true for strict release evidence
# Expected artifacts: artifacts/ddd/readiness/summary.json
  run_closure_command 'node scripts/ddd-runtime-readiness-smoke.mjs'
fi

if matches_closure_filter '72' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=72 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=runtime-readiness-contract-3'
# Reason: runtime readiness productionEquivalence.localOnly must be false for strict release evidence
# Expected artifacts: artifacts/ddd/readiness/summary.json
  run_closure_command 'node scripts/ddd-runtime-readiness-smoke.mjs'
fi

if matches_closure_filter '73' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=73 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=runtime-readiness-contract-4'
# Reason: runtime readiness productionEquivalence.deploymentEvidence is required
# Expected artifacts: artifacts/ddd/readiness/summary.json
  run_closure_command 'node scripts/ddd-runtime-readiness-smoke.mjs'
fi

if matches_closure_filter '74' 'release-owner' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=74 owner=release-owner priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=manifest-missing-no-explain-json-files-in-tmp-ddd-explain'
# Reason: no explain JSON files in tmp\ddd-explain
# Expected artifacts: artifacts/ddd/release/evidence-manifest.json
  run_closure_command 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs'
  run_closure_command 'node scripts/ddd-promote-performance-baseline.mjs'
  run_closure_command 'DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs'
fi

if matches_closure_filter '75' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=75 owner=release-performance priority=P0 kind=RUN_NOW_LOCAL id=performance-actual-shape-1'
# Reason: authenticated performance actual productionEquivalence.strict must be true for strict release evidence
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node scripts/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node scripts/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '76' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=76 owner=release-performance priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=performance-actual-shape-2'
# Reason: authenticated performance actual productionEquivalence.https must be true for strict release evidence
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node scripts/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node scripts/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '77' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=77 owner=release-performance priority=P0 kind=RUN_NOW_LOCAL id=performance-actual-shape-3'
# Reason: authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node scripts/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node scripts/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '78' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=78 owner=release-performance priority=P0 kind=RUN_NOW_LOCAL id=performance-actual-shape-4'
# Reason: authenticated performance actual productionEquivalence.deploymentEvidence is required
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node scripts/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node scripts/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '79' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=79 owner=release-performance priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=performance-baseline-metadata-5'
# Reason: strict release baseline requires baselineType=authenticated-runtime
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node scripts/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node scripts/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '80' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=80 owner=release-performance priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=performance-baseline-metadata-6'
# Reason: acceptedAt must be an ISO timestamp
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node scripts/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node scripts/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '81' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=81 owner=release-performance priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=performance-baseline-metadata-7'
# Reason: acceptedBy is required
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node scripts/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node scripts/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '82' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=82 owner=release-performance priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=performance-baseline-metadata-8'
# Reason: sourceArtifact is required
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node scripts/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node scripts/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '83' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=83 owner=release-performance priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=performance-baseline-metadata-9'
# Reason: sourceSha256 must be a SHA-256 hex digest
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node scripts/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node scripts/ddd-promote-performance-baseline.mjs'
fi

if [[ "${DDD_RELEASE_CLOSURE_MATCHED}" != "1" ]]; then
  echo "No runnable closure item matched the requested filters." >&2
  exit 1
fi

# After closure commands refresh artifacts, rerun:
run_closure_command 'node scripts/ddd-release-evidence-gate.mjs'
run_closure_command 'node scripts/ddd-release-readiness-summary.mjs'
run_closure_command 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
if [[ "${DDD_RELEASE_CLOSURE_COMMAND_FAILURES}" != "0" ]]; then
  echo "[ddd-release-closure][completed-with-failures] commandFailures=${DDD_RELEASE_CLOSURE_COMMAND_FAILURES}" >&2
  exit 1
fi
