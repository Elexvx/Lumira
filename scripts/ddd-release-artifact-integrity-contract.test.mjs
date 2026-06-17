#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const requiredPaths = [
  "artifacts/ddd/release/release-artifact-integrity-gate.sh",
  "artifacts/ddd/release/release-env-readiness-gate.sh",
  "artifacts/ddd/release/release-final-go-no-go-gate.sh",
  "artifacts/ddd/release/release-preflight-gate.sh",
  "artifacts/ddd/release/release-final-go-no-go.json",
  "artifacts/ddd/release/release-final-go-no-go.md",
  "artifacts/ddd/release/release-config-owner-input-reconciliation.json",
];

function makePacket(overrides = {}) {
  const entries = requiredPaths.map((artifactPath, index) => ({
    name: `required-${index}`,
    path: artifactPath,
    bytes: index + 1,
    executable: artifactPath.endsWith(".sh"),
    sha256: `${String(index).padStart(2, "0")}${"a".repeat(62)}`,
  }));
  const packet = {
    generatedAt: "2026-06-15T00:00:00.000Z",
    status: "ADVISORY",
    redacted: true,
    algorithm: "sha256",
    selfExcluded: true,
    artifactCount: entries.length,
    totalBytes: entries.reduce((sum, entry) => sum + entry.bytes, 0),
    entries,
    ...overrides,
  };
  if (overrides.entries) {
    packet.artifactCount = overrides.artifactCount ?? overrides.entries.length;
    packet.totalBytes = overrides.totalBytes ?? overrides.entries.reduce((sum, entry) => sum + Number(entry.bytes || 0), 0);
  }
  return packet;
}

function ownerInputPacketFixture() {
  return {
    redacted: true,
    owners: [
      {
        owner: "release-infra",
        fileName: "01-release-infra",
      },
    ],
  };
}

function ownerInputPacketEntries() {
  return [
    {
      name: "releaseEnvOwnerInputPacketOwner01Json",
      path: "artifacts/ddd/release/release-env-owner-input-packet/01-release-infra.json",
      bytes: 10,
      executable: false,
      sha256: "c".repeat(64),
    },
    {
      name: "releaseEnvOwnerInputPacketOwner01Markdown",
      path: "artifacts/ddd/release/release-env-owner-input-packet/01-release-infra.md",
      bytes: 11,
      executable: false,
      sha256: "d".repeat(64),
    },
  ];
}

function runContract(packet, pathLeakReport = null, ownerInputPacket = null) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-artifact-integrity-contract-"));
  const packetPath = path.join(directory, "release-artifact-integrity.json");
  fs.writeFileSync(packetPath, `${JSON.stringify(packet, null, 2)}\n`);
  if (pathLeakReport) {
    fs.writeFileSync(path.join(directory, "release-artifact-path-leak-contract.json"), `${JSON.stringify(pathLeakReport, null, 2)}\n`);
  }
  if (ownerInputPacket) {
    fs.writeFileSync(path.join(directory, "release-env-owner-input-packet.json"), `${JSON.stringify(ownerInputPacket, null, 2)}\n`);
  }
  return spawnSync("node", ["scripts/ddd-release-artifact-integrity-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET: packetPath,
    },
  });
}

const passResult = runContract(makePacket());
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /ok artifacts=7/);

const missingGeneratedAtResult = runContract(makePacket({ generatedAt: "" }));
assert.notEqual(missingGeneratedAtResult.status, 0);
assert.match(missingGeneratedAtResult.stderr, /generatedAt must be an ISO-like datetime/);

const notReadyStatusResult = runContract(makePacket({ status: "NOT_READY" }));
assert.equal(notReadyStatusResult.status, 0, notReadyStatusResult.stderr);

const invalidStatusResult = runContract(makePacket({ status: "UNKNOWN" }));
assert.notEqual(invalidStatusResult.status, 0);
assert.match(invalidStatusResult.stderr, /status must be one of ADVISORY,PASS,FAIL,NOT_READY/);

const unredactedResult = runContract(makePacket({ redacted: false }));
assert.notEqual(unredactedResult.status, 0);
assert.match(unredactedResult.stderr, /packet must be redacted/);

const passWithPathLeakReport = runContract(makePacket(), {
  status: "PASS",
  leakCount: 0,
});
assert.equal(passWithPathLeakReport.status, 0, passWithPathLeakReport.stderr);

const passWithOwnerInputPacketEntries = runContract(
  makePacket({ entries: [...makePacket().entries, ...ownerInputPacketEntries()] }),
  null,
  ownerInputPacketFixture(),
);
assert.equal(passWithOwnerInputPacketEntries.status, 0, passWithOwnerInputPacketEntries.stderr);

const missingOwnerInputPacketEntry = runContract(
  makePacket({ entries: [...makePacket().entries, ownerInputPacketEntries()[0]] }),
  null,
  ownerInputPacketFixture(),
);
assert.notEqual(missingOwnerInputPacketEntry.status, 0);
assert.match(missingOwnerInputPacketEntry.stderr, /missing owner input packet integrity entry/);

const missingOwnerInputPacketName = runContract(
  makePacket({
    entries: [
      ...makePacket().entries,
      { ...ownerInputPacketEntries()[0], name: "wrongOwnerJsonName" },
      ownerInputPacketEntries()[1],
    ],
  }),
  null,
  ownerInputPacketFixture(),
);
assert.notEqual(missingOwnerInputPacketName.status, 0);
assert.match(missingOwnerInputPacketName.stderr, /missing owner input packet integrity name/);

const failedPathLeakReport = runContract(makePacket(), {
  status: "FAIL",
  leakCount: 1,
});
assert.notEqual(failedPathLeakReport.status, 0);
assert.match(failedPathLeakReport.stderr, /path leak report status must be PASS/);
assert.match(failedPathLeakReport.stderr, /path leak report leakCount must be 0/);

const duplicatePathPacket = makePacket();
duplicatePathPacket.entries[1] = {
  ...duplicatePathPacket.entries[1],
  path: duplicatePathPacket.entries[0].path,
};
const duplicatePathResult = runContract(duplicatePathPacket);
assert.notEqual(duplicatePathResult.status, 0);
assert.match(duplicatePathResult.stderr, /duplicate entry path/);

const duplicateNamePacket = makePacket();
duplicateNamePacket.entries[1] = {
  ...duplicateNamePacket.entries[1],
  name: duplicateNamePacket.entries[0].name,
};
const duplicateNameResult = runContract(duplicateNamePacket);
assert.notEqual(duplicateNameResult.status, 0);
assert.match(duplicateNameResult.stderr, /duplicate entry name/);

const traversalPacket = makePacket({
  entries: [
    ...makePacket().entries,
    {
      name: "escape",
      path: "artifacts/ddd/release/../secret.txt",
      bytes: 1,
      executable: false,
      sha256: "b".repeat(64),
    },
  ],
});
const traversalResult = runContract(traversalPacket);
assert.notEqual(traversalResult.status, 0);
assert.match(traversalResult.stderr, /path traversal is forbidden/);

const missingRequiredPacket = makePacket({
  entries: makePacket().entries.filter((entry) => !entry.path.endsWith("release-preflight-gate.sh")),
});
const missingRequiredResult = runContract(missingRequiredPacket);
assert.notEqual(missingRequiredResult.status, 0);
assert.match(missingRequiredResult.stderr, /missing required release artifact/);

const invalidHashPacket = makePacket();
invalidHashPacket.entries[0] = {
  ...invalidHashPacket.entries[0],
  sha256: "not-a-hash",
};
const invalidHashResult = runContract(invalidHashPacket);
assert.notEqual(invalidHashResult.status, 0);
assert.match(invalidHashResult.stderr, /invalid sha256/);

const wrongTotalPacket = makePacket({ totalBytes: 1 });
const wrongTotalResult = runContract(wrongTotalPacket);
assert.notEqual(wrongTotalResult.status, 0);
assert.match(wrongTotalResult.stderr, /totalBytes/);

const requiredModeMismatchPacket = makePacket();
requiredModeMismatchPacket.entries[0] = {
  ...requiredModeMismatchPacket.entries[0],
  executable: !requiredModeMismatchPacket.entries[0].path.endsWith(".sh"),
};
const requiredModeMismatchResult = runContract(requiredModeMismatchPacket);
assert.notEqual(requiredModeMismatchResult.status, 0);
assert.match(requiredModeMismatchResult.stderr, /required artifact executable flag mismatch/);

console.log("[ddd-release-artifact-integrity-contract.test] ok");
