#!/usr/bin/env bash
set -euo pipefail

# Lumira DDD release preflight gate.
# Generated at: 2026-06-20T19:42:26.704Z
# Default mode reports every gate without failing on NO-GO. Set DDD_RELEASE_PREFLIGHT_ENFORCE=1 for CI blocking behavior.
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

DDD_RELEASE_PREFLIGHT_ENFORCE="${DDD_RELEASE_PREFLIGHT_ENFORCE:-}"
DDD_RELEASE_DIR="${DDD_RELEASE_DIR:-${DDD_RELEASE_EVIDENCE_DIR:-artifacts/ddd}/release}"
export DDD_RELEASE_DIR
DDD_RELEASE_CONFIG_REPORT="${DDD_RELEASE_CONFIG_REPORT:-${DDD_RELEASE_EVIDENCE_DIR:-artifacts/ddd}/config/release-config-evidence.json}"
export DDD_RELEASE_CONFIG_REPORT
DDD_RELEASE_PREFLIGHT_REPORT="${DDD_RELEASE_PREFLIGHT_REPORT:-${DDD_RELEASE_DIR}/release-preflight-report.json}"
safe_load_release_env_file() {
  local exports
  if ! exports=$(node --input-type=module -e 'import fs from '\''node:fs'\''; import path from '\''node:path'\''; const [file, permissionCheckedArg] = process.argv.slice(1); const templateNames = new Set(['\''release-env-missing.template.env'\'', '\''release-closure-wave-env.template.env'\'', '\''release-final-owner-queue-env.template.env'\'', '\''release-env-canonical-fill.template.env'\'']); if (templateNames.has(path.basename(file))) {   console.error(`[ddd-release-env][template-refused] file=${file}`);   process.exit(1); } const permissionAlreadyChecked = permissionCheckedArg === '\''1'\'' || permissionCheckedArg === '\''true'\'' || process.env.DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED === '\''1'\'' || process.env.DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED === '\''true'\''; const mode = permissionAlreadyChecked ? 0o600 : fs.statSync(file).mode & 0o777; if (!permissionAlreadyChecked && (mode & 0o077) !== 0) {   console.error(`[ddd-release-env][permission-refused] file=${file} mode=${mode.toString(8).padStart(3, '\''0'\'')} required=600`);   process.exit(1); } const text = fs.readFileSync(file, '\''utf8'\''); const quote = (value) => `'\''${String(value).replace(/'\''/g, `'\''\\'\'''\''`)}'\''`; let lineNumber = 0; for (const line of text.split(/\r?\n/)) {   lineNumber += 1;   const trimmed = line.trim();   if (!trimmed || trimmed.startsWith('\''#'\'')) continue;   const match = trimmed.match(/^(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$/);   if (!match) {     console.error(`[ddd-release-env][env-invalid] line=${lineNumber}`);     process.exit(1);   }   let value = match[2].trim();   const quoted = value.match(/^(['\''\"])(.*)\1$/s);   if (quoted) value = quoted[2];   console.log(`export ${match[1]}=${quote(value)}`); }' "$DDD_RELEASE_ENV_FILE" "${DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED:-}"); then
    return 1
  fi
  eval "${exports}"
}
if [[ -n "${DDD_RELEASE_ENV_FILE:-}" ]]; then
  safe_load_release_env_file
fi

artifact_integrity_status=-1
manifest_preflight_status=-1
path_leak_status=-1
unblock_brief_status=-1
env_owner_handoff_status=-1
env_owner_input_packet_status=-1
config_owner_input_reconciliation_status=-1
owner_input_receipt_status=-1
env_readiness_status=-1
final_go_no_go_status=-1
preflight_step_status=0
failed_step=""

write_preflight_report() {
  local status="$1"
  mkdir -p "$(dirname "${DDD_RELEASE_PREFLIGHT_REPORT}")"
  node --input-type=module - "${DDD_RELEASE_PREFLIGHT_REPORT}" "${DDD_RELEASE_PREFLIGHT_ENFORCE:-}" "${status}" "${failed_step}" "${artifact_integrity_status}" "${manifest_preflight_status}" "${path_leak_status}" "${unblock_brief_status}" "${env_owner_handoff_status}" "${env_owner_input_packet_status}" "${config_owner_input_reconciliation_status}" "${owner_input_receipt_status}" "${env_readiness_status}" "${final_go_no_go_status}" "NO_GO_STRICT" "false" "false" "148" "11" <<'NODE'
import fs from 'node:fs';
const [reportPath, enforceValue, status, failedStep, artifactIntegrityStatus, manifestPreflightStatus, pathLeakStatus, unblockBriefStatus, envOwnerHandoffStatus, envOwnerInputPacketStatus, configOwnerInputReconciliationStatus, ownerInputReceiptStatus, envReadinessStatus, finalGoNoGoStatus, finalRecommendation, cutoverAllowedValue, releaseEnvFileCutoverSafeValue, gateBlockersValue, stopReasonCountValue] = process.argv.slice(2);
const toNumber = (value) => Number.isFinite(Number(value)) ? Number(value) : -1;
const enforce = enforceValue === '1' || enforceValue === 'true';
const cutoverAllowed = cutoverAllowedValue === 'true';
const releaseEnvFileCutoverSafe = releaseEnvFileCutoverSafeValue === 'true';
const steps = [
  { name: 'artifact-integrity', exitCode: toNumber(artifactIntegrityStatus), command: 'bash ${DDD_RELEASE_DIR}/release-artifact-integrity-gate.sh' },
  { name: 'manifest-provenance-preflight', exitCode: toNumber(manifestPreflightStatus), command: 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs' },
  { name: 'artifact-path-leak', exitCode: toNumber(pathLeakStatus), command: 'node bin/ddd-release-artifact-path-leak-contract.mjs' },
  { name: 'unblock-brief', exitCode: toNumber(unblockBriefStatus), command: 'node bin/ddd-release-unblock-brief.mjs && node bin/ddd-release-unblock-brief-contract.mjs' },
  { name: 'env-owner-handoff-redacted', exitCode: toNumber(envOwnerHandoffStatus), command: 'node bin/ddd-release-env-owner-handoff-redacted-contract.mjs' },
  { name: 'env-owner-input-packet', exitCode: toNumber(envOwnerInputPacketStatus), command: 'node bin/ddd-release-env-owner-input-packet-contract.mjs' },
  { name: 'config-owner-input-reconciliation', exitCode: toNumber(configOwnerInputReconciliationStatus), command: 'DDD_RELEASE_CONFIG_REPORT=${DDD_RELEASE_CONFIG_REPORT} node bin/ddd-release-config-owner-input-reconciliation.mjs' },
  { name: 'owner-input-receipt', exitCode: toNumber(ownerInputReceiptStatus), command: 'node bin/ddd-release-owner-input-receipt.mjs && node bin/ddd-release-owner-input-receipt-contract.mjs' },
  { name: 'env-readiness', exitCode: toNumber(envReadinessStatus), command: enforceValue === '1' || enforceValue === 'true' ? 'DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash ${DDD_RELEASE_DIR}/release-env-readiness-gate.sh' : 'bash ${DDD_RELEASE_DIR}/release-env-readiness-gate.sh' },
  { name: 'final-go-no-go', exitCode: toNumber(finalGoNoGoStatus), command: enforceValue === '1' || enforceValue === 'true' ? 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash ${DDD_RELEASE_DIR}/release-final-go-no-go-gate.sh' : 'bash ${DDD_RELEASE_DIR}/release-final-go-no-go-gate.sh' },
];
const advisoryFailures = enforce ? [] : steps
  .filter((step) => step.exitCode > 0)
  .map((step) => ({ name: step.name, exitCode: step.exitCode, command: step.command }));
const report = {
  generatedAt: new Date().toISOString(),
  status,
  enforce,
  advisoryOnly: !enforce,
  advisoryFailureCount: advisoryFailures.length,
  advisoryFailures,
  cutoverAllowed,
  releaseEnvFileCutoverSafe,
  finalRecommendation,
  gateBlockers: toNumber(gateBlockersValue),
  stopReasonCount: toNumber(stopReasonCountValue),
  cutoverDecisionSource: 'artifacts/ddd/release/release-final-go-no-go.json',
  advisoryNotice: !enforce && !cutoverAllowed ? `Default preflight PASS means checks completed; it is not cutover approval. advisoryFailureCount=${advisoryFailures.length}. Run DDD_RELEASE_PREFLIGHT_ENFORCE=1 for CI blocking behavior.` : null,
  failedStep: failedStep || null,
  reportPath,
  steps,
};
fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`);
NODE
}

run_preflight_step() {
  local name="$1"
  shift
  echo "[ddd-release-preflight] step=${name}"
  set +e
  "$@"
  local status="$?"
  set -e
  preflight_step_status="${status}"
  return 0
}

run_preflight_step artifact-integrity bash "${DDD_RELEASE_DIR}/release-artifact-integrity-gate.sh"
artifact_integrity_status="${preflight_step_status}"
if [[ "${artifact_integrity_status}" != "0" ]]; then
  failed_step="artifact-integrity"
  write_preflight_report FAIL
  exit "${artifact_integrity_status}"
fi
run_preflight_step manifest-provenance-preflight env DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs
manifest_preflight_status="${preflight_step_status}"
run_preflight_step artifact-path-leak node bin/ddd-release-artifact-path-leak-contract.mjs
path_leak_status="${preflight_step_status}"
if [[ "${path_leak_status}" != "0" ]]; then
  failed_step="artifact-path-leak"
  write_preflight_report FAIL
  exit "${path_leak_status}"
fi
if [[ "${manifest_preflight_status}" != "0" && ( "${DDD_RELEASE_PREFLIGHT_ENFORCE}" == "1" || "${DDD_RELEASE_PREFLIGHT_ENFORCE}" == "true" ) ]]; then
  failed_step="manifest-provenance-preflight"
  write_preflight_report NO_GO
  exit "${manifest_preflight_status}"
fi
run_preflight_step unblock-brief node bin/ddd-release-unblock-brief.mjs
unblock_brief_status="${preflight_step_status}"
if [[ "${unblock_brief_status}" == "0" ]]; then
  run_preflight_step unblock-brief-contract node bin/ddd-release-unblock-brief-contract.mjs
  unblock_brief_status="${preflight_step_status}"
fi
if [[ "${unblock_brief_status}" != "0" ]]; then
  failed_step="unblock-brief"
  write_preflight_report FAIL
  exit "${unblock_brief_status}"
fi
run_preflight_step env-owner-handoff-redacted node bin/ddd-release-env-owner-handoff-redacted-contract.mjs
env_owner_handoff_status="${preflight_step_status}"
if [[ "${env_owner_handoff_status}" != "0" ]]; then
  failed_step="env-owner-handoff-redacted"
  write_preflight_report FAIL
  exit "${env_owner_handoff_status}"
fi
run_preflight_step env-owner-input-packet node bin/ddd-release-env-owner-input-packet-contract.mjs
env_owner_input_packet_status="${preflight_step_status}"
if [[ "${env_owner_input_packet_status}" != "0" ]]; then
  failed_step="env-owner-input-packet"
  write_preflight_report FAIL
  exit "${env_owner_input_packet_status}"
fi
run_preflight_step config-owner-input-reconciliation env DDD_RELEASE_CONFIG_REPORT="${DDD_RELEASE_CONFIG_REPORT}" node bin/ddd-release-config-owner-input-reconciliation.mjs
config_owner_input_reconciliation_status="${preflight_step_status}"
if [[ "${config_owner_input_reconciliation_status}" != "0" ]]; then
  failed_step="config-owner-input-reconciliation"
  write_preflight_report FAIL
  exit "${config_owner_input_reconciliation_status}"
fi
run_preflight_step owner-input-receipt node bin/ddd-release-owner-input-receipt.mjs
owner_input_receipt_status="${preflight_step_status}"
if [[ "${owner_input_receipt_status}" == "0" ]]; then
  run_preflight_step owner-input-receipt-contract node bin/ddd-release-owner-input-receipt-contract.mjs
  owner_input_receipt_status="${preflight_step_status}"
fi
if [[ "${owner_input_receipt_status}" != "0" ]]; then
  failed_step="owner-input-receipt"
  write_preflight_report FAIL
  exit "${owner_input_receipt_status}"
fi
if [[ "${DDD_RELEASE_PREFLIGHT_ENFORCE}" == "1" || "${DDD_RELEASE_PREFLIGHT_ENFORCE}" == "true" ]]; then
  run_preflight_step env-readiness env DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash "${DDD_RELEASE_DIR}/release-env-readiness-gate.sh"
  env_readiness_status="${preflight_step_status}"
  if [[ "${env_readiness_status}" != "0" ]]; then
    failed_step="env-readiness"
    write_preflight_report NO_GO
    exit "${env_readiness_status}"
  fi
  run_preflight_step final-go-no-go env DDD_FINAL_GO_NO_GO_ENFORCE=1 bash "${DDD_RELEASE_DIR}/release-final-go-no-go-gate.sh"
  final_go_no_go_status="${preflight_step_status}"
  if [[ "${final_go_no_go_status}" != "0" ]]; then
    failed_step="final-go-no-go"
    write_preflight_report NO_GO
    exit "${final_go_no_go_status}"
  fi
else
  run_preflight_step env-readiness bash "${DDD_RELEASE_DIR}/release-env-readiness-gate.sh"
  env_readiness_status="${preflight_step_status}"
  run_preflight_step final-go-no-go bash "${DDD_RELEASE_DIR}/release-final-go-no-go-gate.sh"
  final_go_no_go_status="${preflight_step_status}"
fi
write_preflight_report PASS
echo "[ddd-release-preflight] report=${DDD_RELEASE_PREFLIGHT_REPORT}"
echo "[ddd-release-preflight] complete enforce=${DDD_RELEASE_PREFLIGHT_ENFORCE:-false}"
