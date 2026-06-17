#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const queuePath = path.join(releaseDir, "release-next-action-queue.json");
const receiptsPath = path.join(releaseDir, "release-owner-receipts.json");
const handoffPath = path.join(releaseDir, "release-owner-handoff.json");
const repoRoot = path.resolve(import.meta.dirname, "..");
const repoRootPattern = new RegExp(`${repoRoot.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}${path.sep.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`);
const unsafeCommandPatterns = [
  /\b[A-Z][A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|API_KEY)=(?!<redacted>)("[^"]*"|'[^']*'|[^\s`|]+)/,
  /\bDDD_RELEASE_ENV_FILE=(?!<release-env-file>)[^\s`|]+/,
  repoRootPattern,
];

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release next action artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const queue = readJson(queuePath);
const receipts = readJson(receiptsPath);
const handoff = readJson(handoffPath);
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function sameStringSet(left = [], right = []) {
  return JSON.stringify([...new Set(left)].sort()) === JSON.stringify([...new Set(right)].sort());
}

if (queue.noAutoWaivers !== true) addFailure("releaseNextActionQueue must keep noAutoWaivers=true");
if (queue.recommendation !== receipts.recommendation) addFailure("releaseNextActionQueue recommendation must match owner receipts");
if (!queue.finalDecision || typeof queue.finalDecision !== "object" || Array.isArray(queue.finalDecision)) {
  addFailure("releaseNextActionQueue finalDecision must be an object");
} else if (JSON.stringify(queue.finalDecision) !== JSON.stringify(handoff.finalDecision || null)) {
  addFailure("releaseNextActionQueue finalDecision must match owner handoff");
}
if (queue.finalDecision?.cutoverAuthority !== "final-go-no-go-gate") {
  addFailure("releaseNextActionQueue finalDecision.cutoverAuthority must be final-go-no-go-gate");
}
if (queue.finalDecision?.requiresFinalGate !== true) {
  addFailure("releaseNextActionQueue finalDecision.requiresFinalGate must be true");
}
if (JSON.stringify(queue.safetySignals?.releaseEnvFile || null) !== JSON.stringify(handoff.safetySignals?.releaseEnvFile || null)) {
  addFailure("releaseNextActionQueue safetySignals.releaseEnvFile must match owner handoff");
}
if (queue.releaseEnvFileCutoverSafe !== handoff.releaseEnvFileCutoverSafe) {
  addFailure("releaseNextActionQueue releaseEnvFileCutoverSafe must match owner handoff");
}
if (!queue.ownerInputReceipt || typeof queue.ownerInputReceipt !== "object" || Array.isArray(queue.ownerInputReceipt)) {
  addFailure("releaseNextActionQueue ownerInputReceipt must be an object");
} else {
  if (!["PASS", "PENDING_OWNER_INPUT", "missing"].includes(queue.ownerInputReceipt.status)) {
    addFailure("releaseNextActionQueue ownerInputReceipt.status must be PASS, PENDING_OWNER_INPUT, or missing");
  }
  if (typeof queue.ownerInputReceipt.cutoverReady !== "boolean") {
    addFailure("releaseNextActionQueue ownerInputReceipt.cutoverReady must be boolean");
  }
  if (queue.ownerInputReceipt.status === "PASS" && queue.ownerInputReceipt.cutoverReady !== true) {
    addFailure("releaseNextActionQueue ownerInputReceipt PASS requires cutoverReady=true");
  }
  if (queue.ownerInputReceipt.status === "PENDING_OWNER_INPUT" && queue.ownerInputReceipt.cutoverReady !== false) {
    addFailure("releaseNextActionQueue pending ownerInputReceipt requires cutoverReady=false");
  }
  for (const field of ["requiredOwnerInputs", "ownerCount", "readyOwnerCount", "pendingOwnerCount"]) {
    if (!Number.isInteger(queue.ownerInputReceipt[field]) || queue.ownerInputReceipt[field] < 0) {
      addFailure(`releaseNextActionQueue ownerInputReceipt.${field} must be a non-negative integer`);
    }
  }
  if (queue.ownerInputReceipt.ownerCount !== queue.ownerInputReceipt.readyOwnerCount + queue.ownerInputReceipt.pendingOwnerCount) {
    addFailure("releaseNextActionQueue ownerInputReceipt ownerCount must equal readyOwnerCount + pendingOwnerCount");
  }
  if (!Array.isArray(queue.ownerInputReceipt.missingCriteria)) {
    addFailure("releaseNextActionQueue ownerInputReceipt.missingCriteria must be an array");
  } else if (queue.ownerInputReceipt.status === "PENDING_OWNER_INPUT" && queue.ownerInputReceipt.missingCriteria.length === 0) {
    addFailure("releaseNextActionQueue pending ownerInputReceipt must include missing criteria");
  }
  if (!Array.isArray(queue.ownerInputReceipt.pendingOwners)) {
    addFailure("releaseNextActionQueue ownerInputReceipt.pendingOwners must be an array");
  }
  if (queue.finalDecision?.cutoverAllowed === true && (queue.ownerInputReceipt.status !== "PASS" || queue.ownerInputReceipt.cutoverReady !== true)) {
    addFailure("releaseNextActionQueue cutoverAllowed=true requires ownerInputReceipt PASS and cutoverReady=true");
  }
}
if (!Array.isArray(queue.items)) addFailure("releaseNextActionQueue items must be an array");
if (!Array.isArray(receipts.owners)) addFailure("releaseOwnerReceipts owners must be an array");

const items = Array.isArray(queue.items) ? queue.items : [];
const owners = Array.isArray(receipts.owners) ? receipts.owners : [];
if (!sameStringSet(items.map((item) => item.owner), owners.map((owner) => owner.owner))) {
  addFailure("releaseNextActionQueue owners must match releaseOwnerReceipts owners");
}
if (queue.summary?.itemCount !== items.length) addFailure("summary.itemCount must match items length");

const runNowItems = items.filter((item) => item.queueStatus === "RUN_NOW");
const waitingItems = items.filter((item) => item.queueStatus !== "RUN_NOW");
const artifactMissingItems = items.filter((item) => item.receiptStatus === "ARTIFACT_MISSING");
const contentBlockedItems = items.filter((item) => item.receiptStatus === "CONTENT_BLOCKED");
const readyForStrictGateRerunItems = items.filter((item) => item.receiptStatus === "READY_FOR_STRICT_GATE_RERUN");
if (queue.summary?.runNowCount !== runNowItems.length) addFailure("summary.runNowCount must match RUN_NOW items");
if (queue.summary?.waitingCount !== waitingItems.length) addFailure("summary.waitingCount must match non-RUN_NOW items");
if (queue.summary?.artifactMissingCount !== artifactMissingItems.length) addFailure("summary.artifactMissingCount must match items");
if (queue.summary?.contentBlockedCount !== contentBlockedItems.length) addFailure("summary.contentBlockedCount must match items");
if (queue.summary?.readyForStrictGateRerunCount !== readyForStrictGateRerunItems.length) addFailure("summary.readyForStrictGateRerunCount must match items");
if (queue.summary?.ownerInputReceiptStatus !== queue.ownerInputReceipt?.status) addFailure("summary.ownerInputReceiptStatus must match ownerInputReceipt");
if (queue.summary?.ownerInputReceiptCutoverReady !== queue.ownerInputReceipt?.cutoverReady) addFailure("summary.ownerInputReceiptCutoverReady must match ownerInputReceipt");
if (queue.summary?.ownerInputReceiptRequiredOwnerInputs !== queue.ownerInputReceipt?.requiredOwnerInputs) addFailure("summary.ownerInputReceiptRequiredOwnerInputs must match ownerInputReceipt");
if (queue.summary?.ownerInputReceiptPendingOwnerCount !== queue.ownerInputReceipt?.pendingOwnerCount) addFailure("summary.ownerInputReceiptPendingOwnerCount must match ownerInputReceipt");
if (queue.summary?.ownerInputReceiptMissingCriteriaCount !== (queue.ownerInputReceipt?.missingCriteria || []).length) {
  addFailure("summary.ownerInputReceiptMissingCriteriaCount must match ownerInputReceipt");
}

let seenWaiting = false;
for (const [index, item] of items.entries()) {
  const label = `item ${item.order ?? index + 1} ${item.owner || "unknown"}`;
  if (item.order !== index + 1) addFailure(`${label}.order must be ${index + 1}`);
  if (!["RUN_NOW", "WAIT_FOR_DEPENDENCIES"].includes(item.queueStatus)) addFailure(`${label}.queueStatus must be RUN_NOW or WAIT_FOR_DEPENDENCIES`);
  if (item.queueStatus !== "RUN_NOW") seenWaiting = true;
  if (seenWaiting && item.queueStatus === "RUN_NOW") addFailure("RUN_NOW next actions must be ordered before waiting actions");
  if (item.queueStatus === "RUN_NOW" && (!Array.isArray(item.executableCommands) || item.executableCommands.length === 0)) {
    addFailure(`${label}.executableCommands are required for RUN_NOW items`);
  }
  if (!Array.isArray(item.executableCommands)) addFailure(`${label}.executableCommands must be an array`);
  if (!Array.isArray(item.envKeys)) addFailure(`${label}.envKeys must be an array`);
  for (const [envIndex, envKey] of (item.envKeys || []).entries()) {
    if (typeof envKey !== "string" || !/^[A-Z][A-Z0-9_]*$/.test(envKey)) {
      addFailure(`${label}.envKeys[${envIndex}] must be an uppercase env key`);
    }
  }
  for (const [commandIndex, command] of (item.executableCommands || []).entries()) {
    if (typeof command !== "string" || command.length === 0) {
      addFailure(`${label}.executableCommands[${commandIndex}] must be a non-empty string`);
      continue;
    }
    for (const pattern of unsafeCommandPatterns) {
      if (pattern.test(command)) {
        addFailure(`${label}.executableCommands[${commandIndex}] must not expose concrete secret values, release env files, or local repo paths`);
      }
    }
  }
  if (!item.nextAction || typeof item.nextAction !== "string") addFailure(`${label}.nextAction is required`);
  if (!item.reason || typeof item.reason !== "string") addFailure(`${label}.reason is required`);
  if (!item.commandHint || typeof item.commandHint !== "string") addFailure(`${label}.commandHint is required`);
  if (!Number.isInteger(item.pendingActionCount) || item.pendingActionCount < 0) addFailure(`${label}.pendingActionCount must be non-negative integer`);
  if (!Number.isInteger(item.collapsedActionCount) || item.collapsedActionCount < 0) addFailure(`${label}.collapsedActionCount must be non-negative integer`);
  if ((item.missingArtifacts || []).some((value) => !/^(artifacts\/ddd\/|tmp\/ddd-explain\/)[^\s]+$/.test(value))) {
    addFailure(`${label}.missingArtifacts must stay under artifacts/ddd or tmp/ddd-explain`);
  }

  const receipt = owners.find((owner) => owner.owner === item.owner);
  if (!receipt) {
    addFailure(`${label} has no matching owner receipt`);
    continue;
  }
  if (item.receiptStatus !== receipt.receiptStatus) addFailure(`${label}.receiptStatus must match owner receipt`);
  if (item.queueStatus !== (receipt.status === "READY" ? "RUN_NOW" : "WAIT_FOR_DEPENDENCIES")) {
    addFailure(`${label}.queueStatus must match owner receipt status`);
  }
  if (!sameStringSet(item.readyBatchIds || [], receipt.readyBatchIds || [])) addFailure(`${label}.readyBatchIds must match owner receipt`);
  if (!sameStringSet(item.blockedBatchIds || [], receipt.blockedBatchIds || [])) addFailure(`${label}.blockedBatchIds must match owner receipt`);
  if (!sameStringSet(item.missingArtifacts || [], receipt.missingArtifacts || [])) addFailure(`${label}.missingArtifacts must match owner receipt`);
  if (item.pendingActionCount !== receipt.pendingActionCount) addFailure(`${label}.pendingActionCount must match owner receipt`);
  if (item.collapsedActionCount !== receipt.collapsedActionCount) addFailure(`${label}.collapsedActionCount must match owner receipt`);
}

if (failures.length > 0) {
  throw new Error(`release next action queue contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-next-action-queue-contract] ok items=${items.length} runNow=${runNowItems.length} waiting=${waitingItems.length}`);
