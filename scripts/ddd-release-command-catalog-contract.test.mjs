#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function commandSet(filter, priority = null) {
  const priorityPart = priority ? ` DDD_RELEASE_PRIORITY=${priority}` : "";
  return {
    list: `${filter}${priorityPart} DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`,
    envCheck: `${filter}${priorityPart} DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`,
    dryRun: `${filter}${priorityPart} DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`,
    execute: `${filter}${priorityPart} bash artifacts/ddd/release/release-execution-commands.sh`,
  };
}

function baseArtifacts() {
  const readyBatch = {
    id: "p0-release-env-lint-release-infra",
    owner: "release-infra",
    priority: "P0",
    expectedArtifacts: ["artifacts/ddd/release/release-env-lint.json"],
  };
  const batchCommands = [
    {
      batchId: readyBatch.id,
      owner: readyBatch.owner,
      priority: readyBatch.priority,
      expectedArtifacts: readyBatch.expectedArtifacts,
      commands: commandSet(`DDD_RELEASE_BATCH=${readyBatch.id}`),
    },
  ];
  const finalDecision = {
    recommendation: "NO_GO_STRICT",
    finalRecommendation: "NO_GO_STRICT",
    cutoverAllowed: false,
    releaseEnvFileCutoverSafe: false,
    gateBlockers: 0,
    blockedCutoverItems: 2,
    stopReasonCount: 1,
    stopReasonCoverage: "catalog-snapshot",
    stopReasons: ["cutover checklist blocked: release-environment"],
    source: "artifacts/ddd/release/release-final-go-no-go.json",
    enforceCommand: "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    cutoverAuthority: "final-go-no-go-gate",
    requiresFinalGate: true,
  };
  return {
    finalGoNoGo: {
      finalRecommendation: "NO_GO_STRICT",
      cutoverAllowed: false,
      releaseEnvFileCutoverSafe: false,
      gate: { blockers: 0 },
      summary: { blockedCutoverItems: 2 },
      currentStopReasons: ["cutover checklist blocked: release-environment"],
    },
    executionQueue: {
      nextPriority: "P0",
      readyBatchCount: 1,
      readyBatches: [readyBatch],
    },
    sprintBoard: {
      recommendation: "NO_GO_STRICT",
      nextWave: {
        owners: ["release-infra"],
      },
    },
    catalog: {
      recommendation: "NO_GO_STRICT",
      noAutoWaivers: true,
      finalDecision,
      releaseEnvFileCutoverSafe: false,
      scriptPath: "artifacts/ddd/release/release-execution-commands.sh",
      summary: {
        ownerCommandCount: 1,
        batchCommandCount: 1,
        nextPriority: "P0",
        readyBatchCount: 1,
      },
      nextPriorityCommands: commandSet("DDD_RELEASE_PRIORITY=P0"),
      ownerCommands: [
        {
          owner: "release-infra",
          priority: "P0",
          readyBatchIds: [readyBatch.id],
          expectedArtifacts: readyBatch.expectedArtifacts,
          commands: commandSet("DDD_RELEASE_OWNER=release-infra", "P0"),
        },
      ],
      batchCommands,
    },
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-command-catalog.json"), `${JSON.stringify(artifacts.catalog, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-execution-queue.json"), `${JSON.stringify(artifacts.executionQueue, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-sprint-board.json"), `${JSON.stringify(artifacts.sprintBoard, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-final-go-no-go.json"), `${JSON.stringify(artifacts.finalGoNoGo, null, 2)}\n`);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-command-catalog-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-command-catalog-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_DIR: directory,
    },
  });
}

const passResult = runContract();
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /owners=1 batches=1/);

const missingBatchResult = runContract((artifacts) => {
  artifacts.catalog.batchCommands = [];
  artifacts.catalog.summary.batchCommandCount = 0;
});
assert.notEqual(missingBatchResult.status, 0);
assert.match(missingBatchResult.stderr, /batchCommands must cover/);

const missingCommandToggleResult = runContract((artifacts) => {
  artifacts.catalog.ownerCommands[0].commands.envCheck = "DDD_RELEASE_OWNER=release-infra DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh";
});
assert.notEqual(missingCommandToggleResult.status, 0);
assert.match(missingCommandToggleResult.stderr, /envCheck must set/);

const executeWithDryRunResult = runContract((artifacts) => {
  artifacts.catalog.batchCommands[0].commands.execute = `${artifacts.catalog.batchCommands[0].commands.execute} DDD_RELEASE_DRY_RUN=1`;
});
assert.notEqual(executeWithDryRunResult.status, 0);
assert.match(executeWithDryRunResult.stderr, /execute must be/);

const ownerMismatchResult = runContract((artifacts) => {
  artifacts.sprintBoard.nextWave.owners = ["ai-owner"];
});
assert.notEqual(ownerMismatchResult.status, 0);
assert.match(ownerMismatchResult.stderr, /ownerCommands must cover/);

const cutoverDecisionMismatchResult = runContract((artifacts) => {
  artifacts.catalog.finalDecision.cutoverAllowed = true;
});
assert.notEqual(cutoverDecisionMismatchResult.status, 0);
assert.match(cutoverDecisionMismatchResult.stderr, /finalDecision\.cutoverAllowed must not be true while final go\/no-go blocks cutover/);

const priorityCommandMismatchResult = runContract((artifacts) => {
  artifacts.catalog.nextPriorityCommands.execute = "DDD_RELEASE_PRIORITY=P1 bash artifacts/ddd/release/release-execution-commands.sh";
});
assert.notEqual(priorityCommandMismatchResult.status, 0);
assert.match(priorityCommandMismatchResult.stderr, /nextPriority/);

console.log("[ddd-release-command-catalog-contract.test] ok");
