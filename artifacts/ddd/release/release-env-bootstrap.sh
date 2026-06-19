#!/usr/bin/env bash
set -euo pipefail

# Lumira DDD release env bootstrap.
# Merges owner-scoped env templates, canonical values, aliases, and strict env/config evidence.
# Generated at: 2026-06-18T19:37:26.213Z
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

DDD_RELEASE_ENV_FILE="${DDD_RELEASE_ENV_FILE:-.env.release.local}"
DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR="${DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR:-artifacts/ddd/release/release-env-owner-templates}"
DDD_RELEASE_CANONICAL_ENV_FILE="${DDD_RELEASE_CANONICAL_ENV_FILE:-artifacts/ddd/release/release-env-canonical-fill.template.env}"
DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT="${DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT:-artifacts/ddd/release/release-env-bootstrap-receipt.json}"
DDD_NODE_BIN="${DDD_NODE_BIN:-node}"
export DDD_NODE_BIN
DDD_RELEASE_ENV_BOOTSTRAP_STEP="init"
write_bootstrap_receipt() {
  local status="$1"
  local exit_code="$2"
  local step="$3"
  "${DDD_NODE_BIN}" --input-type=module -e 'import fs from '\''node:fs'\''; import path from '\''node:path'\''; const [receiptPath, status, exitCode, step, envFile, canonicalFile, ownerDir, repoRoot] = process.argv.slice(1); function portablePath(value) {   if (!value) return value;   const absolute = path.resolve(value);   const root = repoRoot ? path.resolve(repoRoot) : process.cwd();   if (absolute === root) return '\''.'\'';   if (absolute.startsWith(`${root}${path.sep}`)) return path.relative(root, absolute) || '\''.'\'';   const homeDir = process.env.HOME ? path.resolve(process.env.HOME) : '\'''\'';   if (homeDir && absolute === homeDir) return '\''~'\'';   if (homeDir && absolute.startsWith(`${homeDir}${path.sep}`)) return `~/${path.relative(homeDir, absolute)}`;   return value; } const portableEnvFile = portablePath(envFile); const receipt = {   generatedAt: new Date().toISOString(),   status,   exitCode: Number(exitCode),   step,   failedStep: status === '\''FAIL'\'' ? step : null,   completedStep: status === '\''PASS'\'' ? step : null,   envFile: portableEnvFile,   canonicalEnvFile: portablePath(canonicalFile),   ownerTemplateDir: portablePath(ownerDir),   repoRoot: portablePath(repoRoot),   receiptPath: portablePath(receiptPath),   artifactIntegrityGateCommand: '\''bash artifacts/ddd/release/release-artifact-integrity-gate.sh'\'',   artifactIntegrityArtifact: '\''artifacts/ddd/release/release-artifact-integrity.json'\'',   artifactIntegrityMarkdown: '\''artifacts/ddd/release/release-artifact-integrity.md'\'',   envSafeDefaultsCommand: '\''node scripts/ddd-release-env-safe-defaults.mjs'\'',   envSafeDefaultsArtifact: '\''artifacts/ddd/release/release-env-safe-defaults.json'\'',   provenanceDefaultsCommand: '\''node scripts/ddd-release-provenance-defaults.mjs'\'',   provenanceDefaultsArtifact: '\''artifacts/ddd/release/release-provenance-defaults.json'\'',   envReadinessGateCommand: '\''DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh'\'',   envReadinessArtifact: '\''artifacts/ddd/release/release-env-readiness-redacted.json'\'',   envReadinessCsv: '\''artifacts/ddd/release/release-env-readiness-redacted.csv'\'',   ownerHandoffArtifact: '\''artifacts/ddd/release/release-env-owner-handoff-redacted.json'\'',   ownerHandoffCsv: '\''artifacts/ddd/release/release-env-owner-handoff-redacted.csv'\'',   ownerHandoffDir: '\''artifacts/ddd/release/release-env-owner-handoff-redacted'\'',   finalGoNoGoGateCommand: '\''DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'\'',   finalGoNoGoPacket: '\''artifacts/ddd/release/release-final-go-no-go.json'\'',   finalGoNoGoMarkdown: '\''artifacts/ddd/release/release-final-go-no-go.md'\'',   nextCommand: `DDD_RELEASE_ENV_FILE=${portableEnvFile} bash artifacts/ddd/release/release-env-bootstrap.sh`, }; fs.mkdirSync(path.dirname(receiptPath), { recursive: true }); fs.writeFileSync(receiptPath, `${JSON.stringify(receipt, null, 2)}\n`);' "$DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT" "$status" "$exit_code" "$step" "$DDD_RELEASE_ENV_FILE" "$DDD_RELEASE_CANONICAL_ENV_FILE" "$DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR" "$LUMIRA_REPO_ROOT"
}
on_bootstrap_exit() {
  local exit_code="$?"
  if [[ "${exit_code}" -ne 0 ]]; then
    write_bootstrap_receipt FAIL "${exit_code}" "${DDD_RELEASE_ENV_BOOTSTRAP_STEP}"
  fi
}
trap on_bootstrap_exit EXIT

if [[ "${DDD_RELEASE_ENV_FILE}" == *"release-env-missing.template.env" || "${DDD_RELEASE_ENV_FILE}" == *"release-closure-wave-env.template.env" || "${DDD_RELEASE_ENV_FILE}" == *"release-final-owner-queue-env.template.env" || "${DDD_RELEASE_ENV_FILE}" == *"release-env-canonical-fill.template.env" ]]; then
  echo "Refusing to use a generated template as DDD_RELEASE_ENV_FILE: ${DDD_RELEASE_ENV_FILE}" >&2
  exit 1
fi
if [[ ! -f "${DDD_RELEASE_ENV_FILE}" ]]; then
  echo "DDD_RELEASE_ENV_FILE does not exist: ${DDD_RELEASE_ENV_FILE}" >&2
  echo "Run: bash artifacts/ddd/release/release-final-owner-queue-env-init.sh" >&2
  exit 1
fi
if [[ ! -d "${DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR}" ]]; then
  echo "Owner template dir does not exist: ${DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR}" >&2
  exit 1
fi
if [[ ! -f "${DDD_RELEASE_CANONICAL_ENV_FILE}" ]]; then
  echo "Canonical env file does not exist: ${DDD_RELEASE_CANONICAL_ENV_FILE}" >&2
  exit 1
fi
if [[ "${DDD_RELEASE_CANONICAL_ENV_FILE}" != *"release-env-canonical-fill.template.env" ]]; then
  echo "Refusing to use a non-canonical generated env file as DDD_RELEASE_CANONICAL_ENV_FILE: ${DDD_RELEASE_CANONICAL_ENV_FILE}" >&2
  exit 1
fi

DDD_RELEASE_ENV_BOOTSTRAP_STEP="owner-templates-merge"
echo "[ddd-release-env-bootstrap] owner templates -> canonical"
"${DDD_NODE_BIN}" scripts/ddd-release-env-owner-templates-merge.mjs "${DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR}" "${DDD_RELEASE_CANONICAL_ENV_FILE}"
DDD_RELEASE_ENV_BOOTSTRAP_STEP="canonical-merge"
echo "[ddd-release-env-bootstrap] canonical -> release env"
"${DDD_NODE_BIN}" scripts/ddd-release-env-canonical-merge.mjs "${DDD_RELEASE_CANONICAL_ENV_FILE}" "${DDD_RELEASE_ENV_FILE}"
DDD_RELEASE_ENV_BOOTSTRAP_STEP="safe-defaults"
echo "[ddd-release-env-bootstrap] safe defaults"
"${DDD_NODE_BIN}" scripts/ddd-release-env-safe-defaults.mjs
DDD_RELEASE_ENV_BOOTSTRAP_STEP="provenance-defaults"
echo "[ddd-release-env-bootstrap] provenance defaults"
"${DDD_NODE_BIN}" scripts/ddd-release-provenance-defaults.mjs
DDD_RELEASE_ENV_BOOTSTRAP_STEP="alias-sync"
echo "[ddd-release-env-bootstrap] alias sync"
"${DDD_NODE_BIN}" scripts/ddd-release-env-alias-sync.mjs
DDD_RELEASE_ENV_BOOTSTRAP_STEP="canonical-lint"
echo "[ddd-release-env-bootstrap] canonical lint"
"${DDD_NODE_BIN}" scripts/ddd-release-env-canonical-lint.mjs "${DDD_RELEASE_CANONICAL_ENV_FILE}"
DDD_RELEASE_ENV_BOOTSTRAP_STEP="env-readiness-gate"
echo "[ddd-release-env-bootstrap] env readiness gate"
DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh
DDD_RELEASE_ENV_BOOTSTRAP_STEP="release-env-lint"
echo "[ddd-release-env-bootstrap] release env lint"
"${DDD_NODE_BIN}" scripts/ddd-release-env-file-lint.mjs
DDD_RELEASE_ENV_BOOTSTRAP_STEP="release-config-evidence"
echo "[ddd-release-env-bootstrap] release config evidence"
"${DDD_NODE_BIN}" scripts/ddd-release-config-evidence.mjs
DDD_RELEASE_ENV_BOOTSTRAP_STEP="manifest-provenance-env"
echo "[ddd-release-env-bootstrap] manifest provenance env"
DDD_RELEASE_MANIFEST_CHECK_ENV=true "${DDD_NODE_BIN}" scripts/ddd-release-evidence-manifest.mjs
DDD_RELEASE_ENV_BOOTSTRAP_STEP="readiness-summary"
echo "[ddd-release-env-bootstrap] readiness summary"
"${DDD_NODE_BIN}" scripts/ddd-release-readiness-summary.mjs
DDD_RELEASE_ENV_BOOTSTRAP_STEP="final-go-no-go"
echo "[ddd-release-env-bootstrap] final go/no-go"
DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh
DDD_RELEASE_ENV_BOOTSTRAP_STEP="complete"
write_bootstrap_receipt PASS 0 "${DDD_RELEASE_ENV_BOOTSTRAP_STEP}"
trap - EXIT
echo "[ddd-release-env-bootstrap] receipt=${DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT}"
