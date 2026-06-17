#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const finalReadinessSummaryCommand = "node scripts/ddd-release-readiness-summary.mjs";
const finalGoNoGoEnforceCommand = "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh";

function baseArtifacts() {
  const releaseEnvFile = {
    ready: false,
    status: "FAIL",
    securityChecked: true,
    permissionSafe: true,
  };
  const ownerQueues = [
    {
      queueOrder: 1,
      owner: "release-infra",
      queueStatus: "ACTIONABLE",
      canExecute: true,
      executionOrderHint: 1,
      commands: ["node scripts/ddd-release-env-file-lint.mjs", finalReadinessSummaryCommand, finalGoNoGoEnforceCommand],
      commandCount: 3,
      firstCommand: "node scripts/ddd-release-env-file-lint.mjs",
      envKeys: ["DDD_RELEASE_ENV_FILE"],
      envKeyCount: 1,
      missingArtifacts: ["artifacts/ddd/performance/authenticated-runtime-baseline.json"],
      missingArtifactCount: 1,
      contentBlockers: ["release env placeholders"],
      contentBlockerCount: 1,
      stopReasons: ["cutover checklist blocked: release-environment"],
      stopReasonCount: 1,
    },
    {
      queueOrder: 2,
      owner: "frontend",
      queueStatus: "WAITING",
      canExecute: false,
      executionOrderHint: 2,
      commands: [],
      commandCount: 0,
      firstCommand: null,
      envKeys: [],
      envKeyCount: 0,
      missingArtifacts: [],
      missingArtifactCount: 0,
      contentBlockers: [],
      contentBlockerCount: 0,
      stopReasons: ["waiting for release-infra"],
      stopReasonCount: 1,
    },
  ];
  const ownerInputReceipt = {
    artifact: "artifacts/ddd/release/release-owner-input-receipt.json",
    markdown: "artifacts/ddd/release/release-owner-input-receipt.md",
    status: "PENDING_OWNER_INPUT",
    cutoverReady: false,
    requiredOwnerInputs: 11,
    ownerCount: 2,
    readyOwnerCount: 0,
    pendingOwnerCount: 2,
    missingCriteria: ["releaseEnvReadinessStatus"],
    pendingOwners: [
      {
        owner: "release-infra",
        requiredOwnerInputs: 10,
        remainingPlaceholders: 10,
        remainingMissing: 0,
        packetPath: "artifacts/ddd/release/release-env-owner-input-packet/02-release-infra.json",
        handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/02-release-infra.md",
      },
      {
        owner: "payment-owner",
        requiredOwnerInputs: 1,
        remainingPlaceholders: 1,
        remainingMissing: 0,
        packetPath: "artifacts/ddd/release/release-env-owner-input-packet/06-payment-owner.json",
        handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/06-payment-owner.md",
      },
    ],
  };
  return {
    queue: {
      recommendation: "NO_GO_STRICT",
      finalRecommendation: "NO_GO_STRICT",
      cutoverAllowed: false,
      noAutoWaivers: true,
      safetySignals: { releaseEnvFile },
      summary: {
        ownerCount: 2,
        actionableOwnerCount: 1,
        waitingOwnerCount: 1,
        missingArtifactCount: 1,
        contentBlockerCount: 1,
        ownerInputReceiptStatus: ownerInputReceipt.status,
        ownerInputReceiptCutoverReady: ownerInputReceipt.cutoverReady,
        ownerInputReceiptRequiredOwnerInputs: ownerInputReceipt.requiredOwnerInputs,
        ownerInputReceiptPendingOwnerCount: ownerInputReceipt.pendingOwnerCount,
        ownerInputReceiptMissingCriteriaCount: ownerInputReceipt.missingCriteria.length,
        nextExecutableOwner: "release-infra",
        nextExecutableQueueOrder: 1,
        nextExecutableCommand: "node scripts/ddd-release-env-file-lint.mjs",
        nextExecutableEnvKeyCount: 1,
        nextExecutableMissingArtifactCount: 1,
      },
      ownerInputReceipt,
      ownerQueues,
    },
    finalGoNoGo: {
      recommendation: "NO_GO_STRICT",
      finalRecommendation: "NO_GO_STRICT",
      cutoverAllowed: false,
      safetySignals: { releaseEnvFile },
      ciSummary: {
        stopOwners: ["frontend", "release-infra"],
        ownerInputReceipt: {
          status: ownerInputReceipt.status,
          cutoverReady: ownerInputReceipt.cutoverReady,
          requiredOwnerInputs: ownerInputReceipt.requiredOwnerInputs,
          ownerCount: ownerInputReceipt.ownerCount,
          readyOwnerCount: ownerInputReceipt.readyOwnerCount,
          pendingOwnerCount: ownerInputReceipt.pendingOwnerCount,
          missingCriteria: ownerInputReceipt.missingCriteria,
        },
      },
      nextCommands: ["node scripts/ddd-release-env-file-lint.mjs"],
    },
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-final-owner-queue.json"), `${JSON.stringify(artifacts.queue, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-final-go-no-go.json"), `${JSON.stringify(artifacts.finalGoNoGo, null, 2)}\n`);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-final-owner-queue-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-final-owner-queue-contract.mjs"], {
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
assert.match(passResult.stdout, /actionable=1 waiting=1/);

const badOrderResult = runContract((artifacts) => {
  artifacts.queue.ownerQueues.reverse();
  artifacts.queue.ownerQueues[0].queueOrder = 1;
  artifacts.queue.ownerQueues[1].queueOrder = 2;
});
assert.notEqual(badOrderResult.status, 0);
assert.match(badOrderResult.stderr, /ACTIONABLE owners must be ordered before WAITING/);

const badCanExecuteResult = runContract((artifacts) => {
  artifacts.queue.ownerQueues[0].canExecute = false;
});
assert.notEqual(badCanExecuteResult.status, 0);
assert.match(badCanExecuteResult.stderr, /canExecute must match queueStatus/);

const missingNextCommandResult = runContract((artifacts) => {
  artifacts.finalGoNoGo.nextCommands = [];
});
assert.notEqual(missingNextCommandResult.status, 0);
assert.match(missingNextCommandResult.stderr, /nextCommands must include/);

const missingFinalGateResult = runContract((artifacts) => {
  artifacts.queue.ownerQueues[0].commands.pop();
  artifacts.queue.ownerQueues[0].commandCount = artifacts.queue.ownerQueues[0].commands.length;
});
assert.notEqual(missingFinalGateResult.status, 0);
assert.match(missingFinalGateResult.stderr, /final go\/no-go enforce gate/);

const unsafeReleaseEnvCommandResult = runContract((artifacts) => {
  artifacts.queue.ownerQueues[0].commands.unshift("DDD_RELEASE_ENV_FILE=/secure/.env.release.local node scripts/ddd-release-env-file-lint.mjs");
  artifacts.queue.ownerQueues[0].firstCommand = artifacts.queue.ownerQueues[0].commands[0];
  artifacts.queue.ownerQueues[0].commandCount = artifacts.queue.ownerQueues[0].commands.length;
  artifacts.queue.summary.nextExecutableCommand = artifacts.queue.ownerQueues[0].firstCommand;
  artifacts.finalGoNoGo.nextCommands.push(artifacts.queue.ownerQueues[0].firstCommand);
});
assert.notEqual(unsafeReleaseEnvCommandResult.status, 0);
assert.match(unsafeReleaseEnvCommandResult.stderr, /must not expose concrete secret values/);

const mismatchedStopOwnersResult = runContract((artifacts) => {
  artifacts.finalGoNoGo.ciSummary.stopOwners = ["release-infra"];
});
assert.notEqual(mismatchedStopOwnersResult.status, 0);
assert.match(mismatchedStopOwnersResult.stderr, /owners must match finalGoNoGo stopOwners/);

const badSummaryResult = runContract((artifacts) => {
  artifacts.queue.summary.nextExecutableOwner = "frontend";
});
assert.notEqual(badSummaryResult.status, 0);
assert.match(badSummaryResult.stderr, /summary.nextExecutableOwner/);

const missingOwnerInputReceiptResult = runContract((artifacts) => {
  delete artifacts.queue.ownerInputReceipt;
});
assert.notEqual(missingOwnerInputReceiptResult.status, 0);
assert.match(missingOwnerInputReceiptResult.stderr, /ownerInputReceipt must be an object/);

const ownerInputReceiptMismatchResult = runContract((artifacts) => {
  artifacts.queue.ownerInputReceipt.status = "PASS";
});
assert.notEqual(ownerInputReceiptMismatchResult.status, 0);
assert.match(ownerInputReceiptMismatchResult.stderr, /ownerInputReceipt.status must match/);

const ownerInputReceiptSummaryMismatchResult = runContract((artifacts) => {
  artifacts.queue.summary.ownerInputReceiptPendingOwnerCount = 99;
});
assert.notEqual(ownerInputReceiptSummaryMismatchResult.status, 0);
assert.match(ownerInputReceiptSummaryMismatchResult.stderr, /summary.ownerInputReceiptPendingOwnerCount/);

console.log("[ddd-release-final-owner-queue-contract.test] ok");
