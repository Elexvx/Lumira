#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function commandSet(owner) {
  return {
    list: `DDD_RELEASE_OWNER=${owner} DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh`,
    envCheck: `DDD_RELEASE_OWNER=${owner} DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh`,
    dryRun: `DDD_RELEASE_OWNER=${owner} DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh`,
    execute: `DDD_RELEASE_OWNER=${owner} DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh`,
  };
}

function baseArtifacts() {
  const owner = "release-infra";
  const readyBatch = "p0-release-env-lint-release-infra";
  const blockedBatch = "p3-orchestrator-release-infra";
  const templateEnvKeys = ["DDD_RELEASE_ENV_FILE", "LUMIRA_BASE_URL"];
  const expectedArtifacts = ["artifacts/ddd/release/release-env-lint.json"];
  const aliasMappings = [{ alias: "DEPLOY_CHECK_BASE_URL", canonical: "LUMIRA_BASE_URL" }];
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
    handoff: {
      recommendation: "NO_GO_STRICT",
      noAutoWaivers: true,
      finalDecision: { ...finalDecision, stopReasons: [...finalDecision.stopReasons] },
      summary: {
        ownerCount: 1,
        readyOwnerCount: 1,
        blockedOwnerCount: 0,
        readyBatchCount: 1,
        blockedBatchCount: 1,
        templateEnvKeyCount: 2,
      },
      owners: [{
        owner,
        status: "READY",
        pendingItems: 1,
        priorities: ["P0", "P3"],
        batchIds: [readyBatch, blockedBatch],
        readyBatchIds: [readyBatch],
        blockedBatchIds: [blockedBatch],
        commandSet: commandSet(owner),
        templateEnvKeys,
        aliasMappings,
        expectedArtifacts,
        exitCriteria: ["release env lint passes"],
      }],
    },
    commandCatalog: {
      recommendation: "NO_GO_STRICT",
      finalDecision: { ...finalDecision, stopReasons: [...finalDecision.stopReasons] },
      ownerCommands: [{
        owner,
        priority: "P0",
        readyBatchIds: [readyBatch],
        expectedArtifacts,
        commands: commandSet(owner),
      }],
    },
    envOwnerMatrix: {
      owners: [{
        owner,
        templateEnvKeys,
        aliasMappings,
      }],
    },
    executionQueue: {
      readyBatches: [{ id: readyBatch }],
      blockedBatches: [{ id: blockedBatch }],
    },
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-owner-handoff.json"), `${JSON.stringify(artifacts.handoff, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-command-catalog.json"), `${JSON.stringify(artifacts.commandCatalog, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-owner-matrix.json"), `${JSON.stringify(artifacts.envOwnerMatrix, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-execution-queue.json"), `${JSON.stringify(artifacts.executionQueue, null, 2)}\n`);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-owner-handoff-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-owner-handoff-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_DIR: directory },
  });
}

const passResult = runContract();
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /owners=1/);

const missingOwnerResult = runContract((artifacts) => {
  artifacts.handoff.owners = [];
  artifacts.handoff.summary.ownerCount = 0;
  artifacts.handoff.summary.readyOwnerCount = 0;
  artifacts.handoff.summary.templateEnvKeyCount = 0;
});
assert.notEqual(missingOwnerResult.status, 0);
assert.match(missingOwnerResult.stderr, /handoff owners must cover/);

const badCommandResult = runContract((artifacts) => {
  artifacts.handoff.owners[0].commandSet.envCheck = artifacts.handoff.owners[0].commandSet.execute;
});
assert.notEqual(badCommandResult.status, 0);
assert.match(badCommandResult.stderr, /envCheck must set/);

const badEnvKeysResult = runContract((artifacts) => {
  artifacts.handoff.owners[0].templateEnvKeys = ["OTHER_KEY"];
});
assert.notEqual(badEnvKeysResult.status, 0);
assert.match(badEnvKeysResult.stderr, /templateEnvKeys must match/);

const finalDecisionMismatchResult = runContract((artifacts) => {
  artifacts.handoff.finalDecision.cutoverAllowed = true;
});
assert.notEqual(finalDecisionMismatchResult.status, 0);
assert.match(finalDecisionMismatchResult.stderr, /finalDecision must match command catalog/);

const badBatchCoverageResult = runContract((artifacts) => {
  artifacts.handoff.owners[0].readyBatchIds = [];
  artifacts.handoff.owners[0].status = "BLOCKED";
  artifacts.handoff.summary.readyOwnerCount = 0;
  artifacts.handoff.summary.blockedOwnerCount = 1;
});
assert.notEqual(badBatchCoverageResult.status, 0);
assert.match(badBatchCoverageResult.stderr, /readyBatchIds must cover/);

console.log("[ddd-release-owner-handoff-contract.test] ok");
