#!/usr/bin/env bash
set -euo pipefail

# Lumira DDD release artifact integrity gate.
# Generated at: 2026-06-19T18:19:45.629Z
# Exit code 12 means the integrity packet is invalid or at least one artifact hash no longer matches.
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

DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET="${DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET:-artifacts/ddd/release/release-artifact-integrity.json}"
DDD_NODE_BIN="${DDD_NODE_BIN:-node}"
if [[ ! -f "${DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET}" ]]; then
  echo "Release artifact integrity packet does not exist: ${DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET}" >&2
  exit 12
fi
"${DDD_NODE_BIN}" --input-type=module - "${DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET}" <<'NODE'
import crypto from 'node:crypto';
import fs from 'node:fs';
const packetPath = process.argv[2];
const packet = JSON.parse(fs.readFileSync(packetPath, 'utf8'));
const failures = [];
const allowedStatuses = new Set(['ADVISORY', 'PASS', 'FAIL', 'NOT_READY']);
if (packet.algorithm !== 'sha256') failures.push('algorithm');
if (!packet.generatedAt || Number.isNaN(Date.parse(packet.generatedAt))) failures.push('generatedAt');
if (!allowedStatuses.has(packet.status)) failures.push('status');
if (packet.redacted !== true) failures.push('redacted');
if (packet.selfExcluded !== true) failures.push('selfExcluded');
if (!Array.isArray(packet.entries)) failures.push('entries');
const entries = Array.isArray(packet.entries) ? packet.entries : [];
if (entries.length === 0) failures.push('entries-empty');
const seenNames = new Set();
const seenPaths = new Set();
for (const entry of entries) {
  if (!entry || typeof entry.path !== 'string' || typeof entry.sha256 !== 'string') {
    failures.push(`invalid-entry:${entry?.name || 'unknown'}`);
    continue;
  }
  if (!entry.name) failures.push(`name:${entry.path}`);
  if (seenNames.has(entry.name)) failures.push(`duplicate-name:${entry.name}`);
  if (seenPaths.has(entry.path)) failures.push(`duplicate-path:${entry.path}`);
  seenNames.add(entry.name);
  seenPaths.add(entry.path);
  if (!/^[a-f0-9]{64}$/.test(entry.sha256)) failures.push(`sha256-format:${entry.path}`);
  if (!Number.isInteger(entry.bytes) || entry.bytes < 0) failures.push(`bytes:${entry.path}`);
  if (typeof entry.executable !== 'boolean') failures.push(`executable:${entry.path}`);
  if (!fs.existsSync(entry.path)) {
    failures.push(`missing:${entry.path}`);
    continue;
  }
  const stat = fs.statSync(entry.path);
  const executable = process.platform === 'win32' && entry.path.endsWith('.sh') ? true : (stat.mode & 0o111) !== 0;
  const sha256 = crypto.createHash('sha256').update(fs.readFileSync(entry.path)).digest('hex');
  if (stat.size !== entry.bytes) failures.push(`size:${entry.path}`);
  if (executable !== entry.executable) failures.push(`mode:${entry.path}`);
  if (sha256 !== entry.sha256) failures.push(`sha256:${entry.path}`);
}
if (packet.artifactCount !== entries.length) failures.push('artifactCount');
const totalBytes = entries.reduce((sum, entry) => sum + Number(entry.bytes || 0), 0);
if (packet.totalBytes !== totalBytes) failures.push('totalBytes');
if (failures.length > 0) {
  console.error(`[ddd-release-artifact-integrity][invalid] ${failures.join(',')}`);
  process.exit(12);
}
console.log(`[ddd-release-artifact-integrity] ok artifacts=${entries.length} totalBytes=${packet.totalBytes}`);
NODE
