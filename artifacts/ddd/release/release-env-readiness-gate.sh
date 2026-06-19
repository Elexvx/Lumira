#!/usr/bin/env bash
set -euo pipefail

# Lumira DDD release env readiness gate.
# Generated at: 2026-06-19T13:42:59.865Z
# Default mode prints redacted env readiness. Set DDD_RELEASE_ENV_READINESS_ENFORCE=1 to fail while env blockers remain.
# Exit codes: 21 means release env values are unresolved; 22 means the redacted readiness packet is invalid.
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

DDD_RELEASE_ENV_READINESS_PACKET="${DDD_RELEASE_ENV_READINESS_PACKET:-artifacts/ddd/release/release-env-readiness-redacted.json}"
DDD_RELEASE_ENV_READINESS_ENFORCE="${DDD_RELEASE_ENV_READINESS_ENFORCE:-}"
DDD_NODE_BIN="${DDD_NODE_BIN:-node}"
if [[ ! -f "${DDD_RELEASE_ENV_READINESS_PACKET}" ]]; then
  echo "Release env readiness packet does not exist: ${DDD_RELEASE_ENV_READINESS_PACKET}" >&2
  echo "Run: node scripts/ddd-release-readiness-summary.mjs" >&2
  exit 2
fi
set +e
"${DDD_NODE_BIN}" --input-type=module - "${DDD_RELEASE_ENV_READINESS_PACKET}" "${DDD_RELEASE_ENV_READINESS_ENFORCE}" <<'NODE'
import fs from 'node:fs';
const packetPath = process.argv[2];
const enforceMode = process.argv[3];
const packet = JSON.parse(fs.readFileSync(packetPath, 'utf8'));
const schemaIssues = [];
if (packet.redacted !== true) schemaIssues.push('redacted must be true');
if (!packet.summary || typeof packet.summary !== 'object') schemaIssues.push('summary is missing');
if (!Array.isArray(packet.items)) schemaIssues.push('items must be an array');
if (!Array.isArray(packet.byOwner)) schemaIssues.push('byOwner must be an array');
if (schemaIssues.length > 0) {
  console.error(`[ddd-release-env-readiness][invalid-packet] ${schemaIssues.join('; ')}`);
  process.exit(22);
}
const summary = packet.summary || {};
const byOwner = Array.isArray(packet.byOwner) ? packet.byOwner : [];
const items = Array.isArray(packet.items) ? packet.items : [];
const counted = {
  totalCanonicalKeys: items.length,
  filledRedacted: items.filter((item) => item.status === 'FILLED_REDACTED').length,
  placeholders: items.filter((item) => item.status === 'PLACEHOLDER').length,
  missing: items.filter((item) => item.status === 'MISSING').length,
  optionalEmpty: items.filter((item) => item.status === 'OPTIONAL_EMPTY').length,
  blockers: items.filter((item) => item.blocker === true).length,
  secretKeys: items.filter((item) => item.secret === true).length,
  blockingSafeDefaultAvailable: items.filter((item) => item.safeDefaultAvailable === true).length,
  blockingRequiresOwnerInput: items.filter((item) => item.requiresOwnerInput === true).length,
  ownerCount: byOwner.length,
};
const countIssues = Object.entries(counted)
  .filter(([key, value]) => Number(summary[key] ?? -1) !== value)
  .map(([key, value]) => `${key} summary=${summary[key] ?? 'missing'} counted=${value}`);
if (countIssues.length > 0) {
  console.error(`[ddd-release-env-readiness][invalid-counts] ${countIssues.join('; ')}`);
  process.exit(22);
}
const ownerCounts = new Map();
for (const item of items) {
  const ownerName = item.owner || 'release-owner';
  if (!ownerCounts.has(ownerName)) ownerCounts.set(ownerName, { total: 0, filled: 0, placeholder: 0, missing: 0, optionalEmpty: 0, blockers: 0, secretKeys: 0, safeDefaultAvailable: 0, requiresOwnerInput: 0 });
  const entry = ownerCounts.get(ownerName);
  entry.total += 1;
  if (item.status === 'FILLED_REDACTED') entry.filled += 1;
  if (item.status === 'PLACEHOLDER') entry.placeholder += 1;
  if (item.status === 'MISSING') entry.missing += 1;
  if (item.status === 'OPTIONAL_EMPTY') entry.optionalEmpty += 1;
  if (item.blocker === true) entry.blockers += 1;
  if (item.secret === true) entry.secretKeys += 1;
  if (item.safeDefaultAvailable === true) entry.safeDefaultAvailable += 1;
  if (item.requiresOwnerInput === true) entry.requiresOwnerInput += 1;
}
const ownerIssues = [];
for (const owner of byOwner) {
  const countedOwner = ownerCounts.get(owner.owner);
  if (!countedOwner) { ownerIssues.push(`${owner.owner}: no matching items`); continue; }
  for (const key of ['total', 'filled', 'placeholder', 'missing', 'optionalEmpty', 'blockers', 'secretKeys', 'safeDefaultAvailable', 'requiresOwnerInput']) {
    if (Number(owner[key] ?? -1) !== countedOwner[key]) ownerIssues.push(`${owner.owner}.${key} summary=${owner[key] ?? 'missing'} counted=${countedOwner[key]}`);
  }
}
for (const ownerName of ownerCounts.keys()) if (!byOwner.some((owner) => owner.owner === ownerName)) ownerIssues.push(`${ownerName}: missing owner summary`);
if (ownerIssues.length > 0) {
  console.error(`[ddd-release-env-readiness][invalid-owner-counts] ${ownerIssues.join('; ')}`);
  process.exit(22);
}
console.log(`[ddd-release-env-readiness] status=${packet.status || 'missing'} blockers=${summary.blockers ?? 'unknown'} placeholders=${summary.placeholders ?? 'unknown'} missing=${summary.missing ?? 'unknown'} optionalEmpty=${summary.optionalEmpty ?? 'unknown'} filledRedacted=${summary.filledRedacted ?? 'unknown'} secretKeys=${summary.secretKeys ?? 'unknown'} safeDefaultAvailable=${summary.blockingSafeDefaultAvailable ?? 'unknown'} requiresOwnerInput=${summary.blockingRequiresOwnerInput ?? 'unknown'} owners=${summary.ownerCount ?? byOwner.length}`);
console.log('[ddd-release-env-readiness] exitCodes unresolved=21 invalidPacket=22');
console.log('[ddd-release-env-readiness] handoff=artifacts/ddd/release/release-env-owner-handoff-redacted.md handoffCsv=artifacts/ddd/release/release-env-owner-handoff-redacted.csv dir=artifacts/ddd/release/release-env-owner-handoff-redacted');
if (byOwner.length > 0) {
  console.log('[ddd-release-env-readiness] owners:');
  for (const owner of byOwner) console.log(`- ${owner.owner}: blockers=${owner.blockers} placeholder=${owner.placeholder} missing=${owner.missing} optionalEmpty=${owner.optionalEmpty} secretKeys=${owner.secretKeys} safeDefaultAvailable=${owner.safeDefaultAvailable} requiresOwnerInput=${owner.requiresOwnerInput}`);
}
const unresolved = Number(summary.blockers || 0) + Number(summary.missing || 0) + Number(summary.placeholders || 0);
if (enforceMode === '1' || enforceMode === 'true' || process.env.DDD_RELEASE_ENV_READINESS_ENFORCE === '1' || process.env.DDD_RELEASE_ENV_READINESS_ENFORCE === 'true') {
  if (unresolved > 0) {
    console.error(`[ddd-release-env-readiness][no-go] unresolved release env values remain: blockers=${summary.blockers ?? 0} placeholders=${summary.placeholders ?? 0} missing=${summary.missing ?? 0}`);
    process.exit(21);
  }
}
process.exit(0);
NODE
node_status=$?
set -e
exit "${node_status}"
