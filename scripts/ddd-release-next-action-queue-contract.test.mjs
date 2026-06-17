#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function baseArtifacts() {
  const safetySignals = {
    releaseEnvFile: {
      ready: false,
      status: "FAIL",
      inputKind: "release-env-file",
      envFilePresent: true,
      generatedMissingTemplate: false,
      securityChecked: true,
      permissionSafe: true,
      permissionCheckSkipped: false,
      modeOctal: "600",
      requiredMode: "600",
      pendingActionIds: ["release-env-lint-pass-mode", "release-env-lint-status"],
    },
  };
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
  const owners = [
    {
      owner: "database",
      status: "READY",
      receiptStatus: "ARTIFACT_MISSING",
      readyBatchIds: ["p0-migration-database"],
      blockedBatchIds: ["p2-explain-database"],
      missingArtifacts: ["tmp/ddd-explain/*.json"],
      pendingActionCount: 3,
      collapsedActionCount: 1,
    },
    {
      owner: "frontend",
      status: "BLOCKED",
      receiptStatus: "CONTENT_BLOCKED",
      readyBatchIds: [],
      blockedBatchIds: ["p1-frontend-smoke-frontend"],
      missingArtifacts: [],
      pendingActionCount: 2,
      collapsedActionCount: 1,
    },
  ];
  const items = [
    {
      order: 1,
      owner: "database",
      queueStatus: "RUN_NOW",
      receiptStatus: "ARTIFACT_MISSING",
      readyBatchIds: ["p0-migration-database"],
      blockedBatchIds: ["p2-explain-database"],
      missingArtifacts: ["tmp/ddd-explain/*.json"],
      pendingActionCount: 3,
      collapsedActionCount: 1,
      nextAction: "Produce missing artifact: tmp/ddd-explain/*.json",
      reason: "missingArtifact=tmp/ddd-explain/*.json",
      commandHint: "Run EXPLAIN collection.",
      executableCommands: ["node scripts/ddd-collect-explain.mjs"],
      envKeys: ["MYSQL_HOST"],
    },
    {
      order: 2,
      owner: "frontend",
      queueStatus: "WAIT_FOR_DEPENDENCIES",
      receiptStatus: "CONTENT_BLOCKED",
      readyBatchIds: [],
      blockedBatchIds: ["p1-frontend-smoke-frontend"],
      missingArtifacts: [],
      pendingActionCount: 2,
      collapsedActionCount: 1,
      nextAction: "Wait for dependencies.",
      reason: "blockedBatchIds=p1-frontend-smoke-frontend",
      commandHint: "Resolve upstream blockers.",
      executableCommands: [],
      envKeys: [],
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
    receipts: {
      recommendation: "NO_GO_STRICT",
      noAutoWaivers: true,
      safetySignals,
      releaseEnvFileCutoverSafe: false,
      summary: { ownerCount: 2 },
      owners,
    },
    queue: {
      recommendation: "NO_GO_STRICT",
      noAutoWaivers: true,
      finalDecision: { ...finalDecision, stopReasons: [...finalDecision.stopReasons] },
      safetySignals,
      releaseEnvFileCutoverSafe: false,
      summary: {
        itemCount: 2,
        runNowCount: 1,
        waitingCount: 1,
        artifactMissingCount: 1,
        contentBlockedCount: 1,
        readyForStrictGateRerunCount: 0,
        ownerInputReceiptStatus: ownerInputReceipt.status,
        ownerInputReceiptCutoverReady: ownerInputReceipt.cutoverReady,
        ownerInputReceiptRequiredOwnerInputs: ownerInputReceipt.requiredOwnerInputs,
        ownerInputReceiptPendingOwnerCount: ownerInputReceipt.pendingOwnerCount,
        ownerInputReceiptMissingCriteriaCount: ownerInputReceipt.missingCriteria.length,
      },
      ownerInputReceipt,
      items,
    },
    handoff: {
      recommendation: "NO_GO_STRICT",
      noAutoWaivers: true,
      finalDecision: { ...finalDecision, stopReasons: [...finalDecision.stopReasons] },
      safetySignals,
      releaseEnvFileCutoverSafe: false,
    },
    commandCatalog: {
      recommendation: "NO_GO_STRICT",
      ownerCommands: [],
    },
    envOwnerMatrix: {
      owners: [],
    },
    executionQueue: {
      readyBatches: [],
      blockedBatches: [],
    },
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-owner-receipts.json"), `${JSON.stringify(artifacts.receipts, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-next-action-queue.json"), `${JSON.stringify(artifacts.queue, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-owner-handoff.json"), `${JSON.stringify(artifacts.handoff, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-command-catalog.json"), `${JSON.stringify(artifacts.commandCatalog, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-owner-matrix.json"), `${JSON.stringify(artifacts.envOwnerMatrix, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-execution-queue.json"), `${JSON.stringify(artifacts.executionQueue, null, 2)}\n`);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-next-action-queue-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-next-action-queue-contract.mjs"], {
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
assert.match(passResult.stdout, /runNow=1 waiting=1/);

const badOrderResult = runContract((artifacts) => {
  artifacts.queue.items.reverse();
  artifacts.queue.items[0].order = 1;
  artifacts.queue.items[1].order = 2;
});
assert.notEqual(badOrderResult.status, 0);
assert.match(badOrderResult.stderr, /RUN_NOW next actions must be ordered before waiting actions/);

const missingCommandResult = runContract((artifacts) => {
  artifacts.queue.items[0].executableCommands = [];
});
assert.notEqual(missingCommandResult.status, 0);
assert.match(missingCommandResult.stderr, /executableCommands are required/);

const receiptMismatchResult = runContract((artifacts) => {
  artifacts.queue.items[0].receiptStatus = "CONTENT_BLOCKED";
});
assert.notEqual(receiptMismatchResult.status, 0);
assert.match(receiptMismatchResult.stderr, /receiptStatus must match owner receipt/);

const finalDecisionMismatchResult = runContract((artifacts) => {
  artifacts.queue.finalDecision.cutoverAllowed = true;
});
assert.notEqual(finalDecisionMismatchResult.status, 0);
assert.match(finalDecisionMismatchResult.stderr, /finalDecision must match owner handoff/);

const badMissingPathResult = runContract((artifacts) => {
  artifacts.queue.items[0].missingArtifacts = ["../secret.txt"];
});
assert.notEqual(badMissingPathResult.status, 0);
assert.match(badMissingPathResult.stderr, /missingArtifacts must stay/);

const badEnvKeyResult = runContract((artifacts) => {
  artifacts.queue.items[0].envKeys = ["MYSQL_HOST", "bad-key"];
});
assert.notEqual(badEnvKeyResult.status, 0);
assert.match(badEnvKeyResult.stderr, /envKeys\[1\] must be an uppercase env key/);

const unsafeReleaseEnvCommandResult = runContract((artifacts) => {
  artifacts.queue.items[0].executableCommands = ["DDD_RELEASE_ENV_FILE=/secure/.env.release.local node scripts/ddd-release-evidence-manifest.mjs"];
});
assert.notEqual(unsafeReleaseEnvCommandResult.status, 0);
assert.match(unsafeReleaseEnvCommandResult.stderr, /must not expose concrete secret values/);

const unsafeSecretCommandResult = runContract((artifacts) => {
  artifacts.queue.items[0].executableCommands = ["JWT_SECRET=super-secret node scripts/ddd-release-evidence-manifest.mjs"];
});
assert.notEqual(unsafeSecretCommandResult.status, 0);
assert.match(unsafeSecretCommandResult.stderr, /must not expose concrete secret values/);

const unsafeRepoPathCommandResult = runContract((artifacts) => {
  artifacts.queue.items[0].executableCommands = [`node ${path.join(repoRoot, "scripts/ddd-release-evidence-manifest.mjs")}`];
});
assert.notEqual(unsafeRepoPathCommandResult.status, 0);
assert.match(unsafeRepoPathCommandResult.stderr, /must not expose concrete secret values/);

const badSummaryResult = runContract((artifacts) => {
  artifacts.queue.summary.runNowCount = 2;
});
assert.notEqual(badSummaryResult.status, 0);
assert.match(badSummaryResult.stderr, /summary.runNowCount/);

const missingOwnerInputReceiptResult = runContract((artifacts) => {
  delete artifacts.queue.ownerInputReceipt;
});
assert.notEqual(missingOwnerInputReceiptResult.status, 0);
assert.match(missingOwnerInputReceiptResult.stderr, /ownerInputReceipt must be an object/);

const ownerInputReceiptSummaryMismatchResult = runContract((artifacts) => {
  artifacts.queue.summary.ownerInputReceiptPendingOwnerCount = 99;
});
assert.notEqual(ownerInputReceiptSummaryMismatchResult.status, 0);
assert.match(ownerInputReceiptSummaryMismatchResult.stderr, /summary.ownerInputReceiptPendingOwnerCount/);

const pendingOwnerInputReceiptWithoutCriteriaResult = runContract((artifacts) => {
  artifacts.queue.ownerInputReceipt.missingCriteria = [];
  artifacts.queue.summary.ownerInputReceiptMissingCriteriaCount = 0;
});
assert.notEqual(pendingOwnerInputReceiptWithoutCriteriaResult.status, 0);
assert.match(pendingOwnerInputReceiptWithoutCriteriaResult.stderr, /pending ownerInputReceipt must include missing criteria/);

console.log("[ddd-release-next-action-queue-contract.test] ok");
