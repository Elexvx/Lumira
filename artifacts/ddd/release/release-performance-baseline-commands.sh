#!/usr/bin/env bash
set -euo pipefail

# Lumira DDD authenticated performance baseline closure commands.
# Generated at: 2026-06-20T19:42:26.704Z
# Status: BLOCKED
# Ready to promote: false
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

DDD_AUTH_PERF_BASELINE_DETAIL="${DDD_AUTH_PERF_BASELINE_DETAIL:-}"
DDD_AUTH_PERF_BASELINE_CHECK_ENV="${DDD_AUTH_PERF_BASELINE_CHECK_ENV:-}"
DDD_AUTH_PERF_BASELINE_EXECUTE="${DDD_AUTH_PERF_BASELINE_EXECUTE:-}"
if [[ "${DDD_AUTH_PERF_BASELINE_EXECUTE}" == "1" || "${DDD_AUTH_PERF_BASELINE_EXECUTE}" == "true" || "${DDD_AUTH_PERF_BASELINE_CHECK_ENV}" == "1" || "${DDD_AUTH_PERF_BASELINE_CHECK_ENV}" == "true" ]]; then
  if [[ -z "${DDD_RELEASE_ENV_FILE:-}" ]]; then
    echo "DDD_RELEASE_ENV_FILE is required when executing or checking performance baseline env." >&2
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
if [[ "${DDD_AUTH_PERF_BASELINE_EXECUTE}" == "1" || "${DDD_AUTH_PERF_BASELINE_EXECUTE}" == "true" || "${DDD_AUTH_PERF_BASELINE_CHECK_ENV}" == "1" || "${DDD_AUTH_PERF_BASELINE_CHECK_ENV}" == "true" ]]; then
  safe_load_release_env_file
fi
check_required_env() {
  local missing=0
  local key
  local value
  for key in "$@"; do
    value="${!key:-}"
    if [[ -z "${value}" ]]; then
      echo "[ddd-auth-perf-baseline][env-missing] key=${key}" >&2
      missing=1
      continue
    fi
    if [[ "${value}" == "__REQUIRED__" || "${value}" == *"replace-with"* || "${value}" == *"example.com"* || "${value}" == *"example.internal"* ]]; then
      echo "[ddd-auth-perf-baseline][env-placeholder] key=${key}" >&2
      missing=1
      continue
    fi
    if [[ "${key}" == "BASE_URL" || "${key}" == "DEPLOY_CHECK_BASE_URL" || "${key}" == "LUMIRA_BASE_URL" ]]; then
      if [[ "${value}" != https://* ]]; then
        echo "[ddd-auth-perf-baseline][env-not-https] key=${key}" >&2
        missing=1
        continue
      fi
      if [[ "${value}" == *"localhost"* || "${value}" == *"127.0.0.1"* || "${value}" == *"[::1]"* || "${value}" == *"0.0.0.0"* ]]; then
        echo "[ddd-auth-perf-baseline][env-local-url] key=${key}" >&2
        missing=1
        continue
      fi
    fi
  done
  if [[ "${missing}" == "0" ]]; then
    echo "[ddd-auth-perf-baseline][env-ok]"
  fi
  return "${missing}"
}
run_command() {
  local command="$1"
  if [[ "${DDD_AUTH_PERF_BASELINE_EXECUTE}" != "1" && "${DDD_AUTH_PERF_BASELINE_EXECUTE}" != "true" ]]; then
    echo "[ddd-auth-perf-baseline][dry-run] ${command}"
    return 0
  fi
  bash -lc "${command}"
}

if [[ "${DDD_AUTH_PERF_BASELINE_DETAIL}" == "1" || "${DDD_AUTH_PERF_BASELINE_DETAIL}" == "true" || ( "${DDD_AUTH_PERF_BASELINE_CHECK_ENV}" != "1" && "${DDD_AUTH_PERF_BASELINE_CHECK_ENV}" != "true" && "${DDD_AUTH_PERF_BASELINE_EXECUTE}" != "1" && "${DDD_AUTH_PERF_BASELINE_EXECUTE}" != "true" ) ]]; then
  echo 'status=BLOCKED'
  echo 'readyToPromote=false'
  echo "blockers:"
  echo '- acceptedAt must be an ISO timestamp'
  echo '- acceptedBy is required'
  echo '- authenticated performance actual productionEquivalence.deploymentEvidence is required'
  echo '- authenticated performance actual productionEquivalence.https must be true for strict release evidence'
  echo '- authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence'
  echo '- authenticated performance actual productionEquivalence.strict must be true for strict release evidence'
  echo '- sourceArtifact is required'
  echo '- sourceSha256 must be a SHA-256 hex digest'
  echo '- strict release baseline requires baselineType=authenticated-runtime'
  echo "commands:"
  echo '- DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh'
  echo '- DDD_AUTH_PERF_STRICT=true node bin/ddd-authenticated-performance-smoke.mjs'
  echo '- node bin/ddd-promote-performance-baseline.mjs'
  echo '- DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
  echo '- DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
  echo '- node bin/ddd-release-evidence-gate.mjs'
  echo '- node bin/ddd-release-readiness-summary.mjs'
  echo '- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
  if [[ "${DDD_AUTH_PERF_BASELINE_DETAIL}" == "1" || "${DDD_AUTH_PERF_BASELINE_DETAIL}" == "true" ]]; then exit 0; fi
fi

if [[ "${DDD_AUTH_PERF_BASELINE_CHECK_ENV}" == "1" || "${DDD_AUTH_PERF_BASELINE_CHECK_ENV}" == "true" ]]; then
  check_required_env 'BASE_URL' 'DDD_AUTH_PASSWORD' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE' 'DDD_AUTH_PERF_ENVIRONMENT' 'DDD_AUTH_USERNAME' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'DEPLOY_CHECK_BASE_URL' 'LUMIRA_BASE_URL'
  exit $?
fi

if [[ "${DDD_AUTH_PERF_BASELINE_EXECUTE}" != "1" && "${DDD_AUTH_PERF_BASELINE_EXECUTE}" != "true" ]]; then
  run_command 'DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh'
  run_command 'DDD_AUTH_PERF_STRICT=true node bin/ddd-authenticated-performance-smoke.mjs'
  run_command 'node bin/ddd-promote-performance-baseline.mjs'
  run_command 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
  run_command 'DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
  run_command 'node bin/ddd-release-evidence-gate.mjs'
  run_command 'node bin/ddd-release-readiness-summary.mjs'
  run_command 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
  exit 0
fi
run_command 'DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh'
run_command 'DDD_AUTH_PERF_STRICT=true node bin/ddd-authenticated-performance-smoke.mjs'
run_command 'node bin/ddd-promote-performance-baseline.mjs'
run_command 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
run_command 'DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
run_command 'node bin/ddd-release-evidence-gate.mjs'
run_command 'node bin/ddd-release-readiness-summary.mjs'
run_command 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
