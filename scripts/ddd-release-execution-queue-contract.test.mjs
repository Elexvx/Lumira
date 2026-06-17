#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function baseArtifacts() {
  const ready = {
    id: "p0-release-env-lint-release-infra",
    priority: "P0",
    source: "release-env-lint",
    owner: "release-infra",
    pendingItems: 1,
    dependsOn: [],
    commands: ["node scripts/ddd-release-env-file-lint.mjs"],
    envKeys: ["DDD_RELEASE_ENV_FILE"],
    expectedArtifacts: ["artifacts/ddd/release/release-env-lint.json"],
    exitCriteria: ["release env lint passes"],
  };
  const blocked = {
    id: "p1-frontend-smoke-frontend",
    priority: "P1",
    source: "frontend-smoke",
    owner: "frontend",
    pendingItems: 1,
    dependsOn: [ready.id],
    unmetDependencyCount: 1,
    unmetDependencies: [
      {
        id: ready.id,
        priority: ready.priority,
        source: ready.source,
        owner: ready.owner,
        expectedArtifacts: ready.expectedArtifacts,
        exitCriteria: ready.exitCriteria,
      },
    ],
    commands: ["node scripts/ddd-frontend-smoke-evidence.mjs"],
    envKeys: ["PLAYWRIGHT_BASE_URL"],
    expectedArtifacts: ["artifacts/ddd/frontend/frontend-smoke.json"],
    exitCriteria: ["frontend smoke passes"],
  };
  return {
    batches: {
      batchCount: 2,
      batches: [ready, blocked],
    },
    queue: {
      batchCount: 2,
      readyBatchCount: 1,
      blockedBatchCount: 1,
      nextPriority: "P0",
      nextBatchIds: [ready.id],
      readyBatches: [ready],
      blockedBatches: [blocked],
    },
    sprintBoard: {
      summary: {
        readyBatchCount: 1,
        blockedBatchCount: 1,
      },
      nextWave: {
        batchIds: [ready.id],
      },
      batchCards: [
        { id: ready.id, status: "READY" },
        { id: blocked.id, status: "BLOCKED" },
      ],
    },
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-action-batches.json"), `${JSON.stringify(artifacts.batches, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-execution-queue.json"), `${JSON.stringify(artifacts.queue, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-sprint-board.json"), `${JSON.stringify(artifacts.sprintBoard, null, 2)}\n`);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-execution-queue-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-execution-queue-contract.mjs"], {
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
assert.match(passResult.stdout, /ready=1 blocked=1/);

const missingNextBatchResult = runContract((artifacts) => {
  artifacts.queue.nextBatchIds = [];
});
assert.notEqual(missingNextBatchResult.status, 0);
assert.match(missingNextBatchResult.stderr, /nextBatchIds/);

const readyWithDependencyResult = runContract((artifacts) => {
  artifacts.queue.readyBatches[0] = {
    ...artifacts.queue.readyBatches[0],
    dependsOn: ["p0-other"],
  };
});
assert.notEqual(readyWithDependencyResult.status, 0);
assert.match(readyWithDependencyResult.stderr, /must not have unmet dependencies/);

const blockedWithoutDependencyResult = runContract((artifacts) => {
  artifacts.queue.blockedBatches[0] = {
    ...artifacts.queue.blockedBatches[0],
    unmetDependencies: [],
    unmetDependencyCount: 0,
  };
});
assert.notEqual(blockedWithoutDependencyResult.status, 0);
assert.match(blockedWithoutDependencyResult.stderr, /must include unmetDependencies/);

const sprintMismatchResult = runContract((artifacts) => {
  artifacts.sprintBoard.batchCards[0].status = "BLOCKED";
});
assert.notEqual(sprintMismatchResult.status, 0);
assert.match(sprintMismatchResult.stderr, /READY cards/);

const missingCommandsResult = runContract((artifacts) => {
  artifacts.queue.readyBatches[0] = {
    ...artifacts.queue.readyBatches[0],
    commands: [],
  };
});
assert.notEqual(missingCommandsResult.status, 0);
assert.match(missingCommandsResult.stderr, /must include commands/);

console.log("[ddd-release-execution-queue-contract.test] ok");
