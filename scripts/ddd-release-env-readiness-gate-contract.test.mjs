#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function gateScript() {
  return `#!/usr/bin/env bash
set -euo pipefail
DDD_RELEASE_ENV_READINESS_PACKET="\${DDD_RELEASE_ENV_READINESS_PACKET:-artifacts/ddd/release/release-env-readiness-redacted.json}"
DDD_RELEASE_ENV_READINESS_ENFORCE="\${DDD_RELEASE_ENV_READINESS_ENFORCE:-}"
# Exit codes: 21 means release env values are unresolved; 22 means the redacted readiness packet is invalid.
node --input-type=module - "\${DDD_RELEASE_ENV_READINESS_PACKET}" "\${DDD_RELEASE_ENV_READINESS_ENFORCE}" <<'NODE'
import fs from 'node:fs';
const packet = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const enforceMode = process.argv[3];
if (packet.redacted !== true || !Array.isArray(packet.items) || !Array.isArray(packet.byOwner)) {
  console.error('[ddd-release-env-readiness][invalid-packet] items must be an array; byOwner must be an array');
  process.exit(22);
}
const summary = packet.summary || {};
const counted = {
  totalCanonicalKeys: packet.items.length,
  filledRedacted: packet.items.filter((item) => item.status === 'FILLED_REDACTED').length,
  placeholders: packet.items.filter((item) => item.status === 'PLACEHOLDER').length,
  missing: packet.items.filter((item) => item.status === 'MISSING').length,
  optionalEmpty: packet.items.filter((item) => item.status === 'OPTIONAL_EMPTY').length,
  blockers: packet.items.filter((item) => item.blocker === true).length,
  secretKeys: packet.items.filter((item) => item.secret === true).length,
  ownerCount: packet.byOwner.length,
};
if (Object.entries(counted).some(([key, value]) => Number(summary[key] ?? -1) !== value)) {
  console.error('[ddd-release-env-readiness][invalid-counts]');
  process.exit(22);
}
if (!packet.byOwner.some((owner) => owner.owner === 'release-infra')) {
  console.error('[ddd-release-env-readiness][invalid-owner-counts]');
  process.exit(22);
}
console.log('[ddd-release-env-readiness] status=NOT_READY blockers=1 placeholders=1 missing=0 optionalEmpty=0 filledRedacted=0 secretKeys=1 owners=1');
console.log('[ddd-release-env-readiness] exitCodes unresolved=21 invalidPacket=22');
console.log('[ddd-release-env-readiness] handoff=artifacts/ddd/release/release-env-owner-handoff-redacted.md handoffCsv=artifacts/ddd/release/release-env-owner-handoff-redacted.csv dir=artifacts/ddd/release/release-env-owner-handoff-redacted');
if (enforceMode === '1' || process.env.DDD_RELEASE_ENV_READINESS_ENFORCE === '1') {
  console.error('[ddd-release-env-readiness][no-go] unresolved release env values remain');
  process.exit(21);
}
process.exit(0);
NODE
`;
}

function writeArtifacts(directory, mutator = () => {}) {
  const packet = {
    redacted: true,
    status: "NOT_READY",
    summary: {
      totalCanonicalKeys: 1,
      filledRedacted: 0,
      placeholders: 1,
      missing: 0,
      optionalEmpty: 0,
      blockers: 1,
      secretKeys: 1,
      ownerCount: 1,
    },
    items: [{ owner: "release-infra", status: "PLACEHOLDER", blocker: true, secret: true }],
    byOwner: [{ owner: "release-infra" }],
  };
  const artifacts = { script: gateScript(), packet };
  mutator(artifacts);
  fs.writeFileSync(path.join(directory, "release-env-readiness-gate.sh"), artifacts.script);
  fs.writeFileSync(path.join(directory, "release-env-readiness-redacted.json"), `${JSON.stringify(artifacts.packet, null, 2)}\n`);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-readiness-gate-contract-"));
  writeArtifacts(directory, mutator);
  return spawnSync("node", ["scripts/ddd-release-env-readiness-gate-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_DIR: directory },
  });
}

const passResult = runContract();
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /unresolved=2/);

const sourceResult = runContract((artifacts) => {
  artifacts.script += "\nsource .env.release.local\n";
});
assert.notEqual(sourceResult.status, 0);
assert.match(sourceResult.stderr, /must not source/);

const missingSnippetResult = runContract((artifacts) => {
  artifacts.script = artifacts.script.replace("process.exit(21)", "process.exit(0)");
});
assert.notEqual(missingSnippetResult.status, 0);
assert.match(missingSnippetResult.stderr, /process.exit\(21\)|must exit 21/);

const badPacketResult = runContract((artifacts) => {
  artifacts.packet.redacted = false;
});
assert.notEqual(badPacketResult.status, 0);
assert.match(badPacketResult.stderr, /readiness packet/);

console.log("[ddd-release-env-readiness-gate-contract.test] ok");
