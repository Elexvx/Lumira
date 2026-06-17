#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const passingConfigOwnerInputReconciliation = {
  status: "PASS",
  configPlaceholderBlockers: 1,
  uniqueConfigPlaceholderKeys: 1,
  ownerInputKeys: 1,
  mappedConfigPlaceholderKeys: 1,
  unmappedConfigPlaceholderKeys: 0,
  duplicateConfigPlaceholderBlockers: 0,
  ownerInputsWithoutConfigPlaceholder: 0,
  issueCount: 0,
};
const passingOwnerInputReceipt = {
  status: "PENDING_OWNER_INPUT",
  cutoverReady: false,
  requiredOwnerInputs: 1,
  ownerCount: 1,
  readyOwnerCount: 0,
  pendingOwnerCount: 1,
  missingCriteria: ["releaseEnvReadinessStatus"],
};

const run = spawnSync("node", ["scripts/ddd-release-final-go-no-go-gate-contract.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
});

assert.equal(run.status, 0, run.stderr || run.stdout);
assert.match(run.stdout, /\[ddd-release-final-go-no-go-gate-contract] ok/);

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-final-go-no-go-contract-test-"));
try {
  const packetPath = path.join(tmpDir, "release-final-go-no-go.json");
  const markdownPath = path.join(tmpDir, "release-final-go-no-go.md");
  fs.writeFileSync(packetPath, `${JSON.stringify({
    recommendation: "NO_GO_STRICT",
    finalRecommendation: "NO_GO_STRICT",
    cutoverAllowed: false,
    noAutoWaivers: true,
    gate: { blockers: 1, warnings: 0 },
    currentStopReasons: ["authenticated performance baseline not ready: MISSING"],
    nextCommands: [
      "node scripts/ddd-release-readiness-summary.mjs",
      "DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh",
    ],
    ciSummary: {
      finalGoNoGoEnforceCommand: "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
      stopOwners: ["release-performance"],
      blockedArtifactPaths: ["artifacts/ddd/performance/authenticated-runtime-baseline.json"],
      blockedContentHints: [],
      nonGoExitCode: 10,
      exitCodeMap: {
        finalNoGo: 10,
        finalPacketInvalid: 11,
        releaseEnvUnresolved: 21,
        releaseEnvInvalidPacket: 22,
      },
      configOwnerInputReconciliation: passingConfigOwnerInputReconciliation,
      ownerInputReceipt: passingOwnerInputReceipt,
      firstNextCommand: "node scripts/ddd-release-readiness-summary.mjs",
    },
    safetySignals: {
      releaseEnvFile: {
        ready: false,
        status: "FAIL",
        inputKind: "release-env-file",
        envFilePresent: true,
        securityChecked: true,
        permissionSafe: true,
        modeOctal: "600",
        requiredMode: "600",
      },
    },
  }, null, 2)}\n`);
  fs.writeFileSync(markdownPath, [
    "# DDD Final Go/No-Go Packet",
    "",
    "## Next Commands",
    "",
    "- `node scripts/ddd-release-readiness-summary.mjs`",
    "",
  ].join("\n"));
  const missingMarkdownCommand = spawnSync("node", ["scripts/ddd-release-final-go-no-go-gate-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_FINAL_GO_NO_GO_CONTRACT_PACKET: packetPath,
      DDD_FINAL_GO_NO_GO_CONTRACT_MARKDOWN: markdownPath,
    },
  });
  assert.notEqual(missingMarkdownCommand.status, 0);
  assert.match(
    missingMarkdownCommand.stderr,
    /final go\/no-go markdown must include required handoff command: DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts\/ddd\/release\/release-performance-baseline-commands\.sh/,
  );

  fs.writeFileSync(packetPath, `${JSON.stringify({
    recommendation: "NO_GO_STRICT",
    finalRecommendation: "NO_GO_STRICT",
    cutoverAllowed: false,
    releaseEnvFileCutoverSafe: false,
    noAutoWaivers: true,
    gate: { blockers: 1, warnings: 0 },
    currentStopReasons: ["release environment unresolved"],
    nextCommands: [
      "node scripts/ddd-release-readiness-summary.mjs",
      "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs",
    ],
    ciSummary: {
      finalGoNoGoEnforceCommand: "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
      stopOwners: ["release-infra"],
      blockedArtifactPaths: [],
      blockedContentHints: [],
      nonGoExitCode: 10,
      exitCodeMap: {
        finalNoGo: 10,
        finalPacketInvalid: 11,
        releaseEnvUnresolved: 21,
        releaseEnvInvalidPacket: 22,
      },
      configOwnerInputReconciliation: passingConfigOwnerInputReconciliation,
      ownerInputReceipt: passingOwnerInputReceipt,
      firstNextCommand: "node scripts/ddd-release-readiness-summary.mjs",
    },
    safetySignals: {
      releaseEnvFile: {
        ready: false,
        status: "FAIL",
        inputKind: "release-env-file",
        envFilePresent: true,
        securityChecked: true,
        permissionSafe: true,
        modeOctal: "600",
        requiredMode: "600",
      },
    },
  }, null, 2)}\n`);
  fs.writeFileSync(markdownPath, [
    "# DDD Final Go/No-Go Packet",
    "",
    "## Next Commands",
    "",
    "- `node scripts/ddd-release-readiness-summary.mjs`",
    "- `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`",
    "",
  ].join("\n"));
  const unsafeReleaseEnvCommand = spawnSync("node", ["scripts/ddd-release-final-go-no-go-gate-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_FINAL_GO_NO_GO_CONTRACT_PACKET: packetPath,
      DDD_FINAL_GO_NO_GO_CONTRACT_MARKDOWN: markdownPath,
    },
  });
  assert.notEqual(unsafeReleaseEnvCommand.status, 0);
  assert.match(
    unsafeReleaseEnvCommand.stderr,
    /must not expose concrete release env files or local repo paths/,
  );
} finally {
  fs.rmSync(tmpDir, { recursive: true, force: true });
}

console.log("[ddd-release-final-go-no-go-gate-contract.test] ok");
