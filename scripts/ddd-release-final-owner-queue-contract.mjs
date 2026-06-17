#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const queuePath = path.join(releaseDir, "release-final-owner-queue.json");
const finalGoNoGoPath = path.join(releaseDir, "release-final-go-no-go.json");
const repoRoot = path.resolve(import.meta.dirname, "..");
const repoRootPattern = new RegExp(`${repoRoot.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}${path.sep.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`);
const unsafeCommandPatterns = [
  /\b[A-Z][A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|API_KEY)=(?!<redacted>)("[^"]*"|'[^']*'|[^\s`|]+)/,
  /\bDDD_RELEASE_ENV_FILE=(?!<release-env-file>)[^\s`|]+/,
  repoRootPattern,
];

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release final owner queue artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const queue = readJson(queuePath);
const finalGoNoGo = readJson(finalGoNoGoPath);
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function sameStringSet(left = [], right = []) {
  return JSON.stringify([...new Set(left)].sort()) === JSON.stringify([...new Set(right)].sort());
}

function assertSafeDisplayCommand(label, command) {
  if (typeof command !== "string" || command.length === 0) return;
  for (const pattern of unsafeCommandPatterns) {
    if (pattern.test(command)) {
      addFailure(`${label} must not expose concrete secret values, release env files, or local repo paths`);
    }
  }
}

if (queue.noAutoWaivers !== true) addFailure("releaseFinalOwnerQueue must keep noAutoWaivers=true");
if (queue.recommendation !== finalGoNoGo.recommendation) addFailure("releaseFinalOwnerQueue recommendation must match finalGoNoGo");
if (queue.finalRecommendation !== finalGoNoGo.finalRecommendation) addFailure("releaseFinalOwnerQueue finalRecommendation must match finalGoNoGo");
if (queue.cutoverAllowed !== finalGoNoGo.cutoverAllowed) addFailure("releaseFinalOwnerQueue cutoverAllowed must match finalGoNoGo");
if (queue.releaseEnvFileCutoverSafe !== finalGoNoGo.releaseEnvFileCutoverSafe) {
  addFailure("releaseFinalOwnerQueue releaseEnvFileCutoverSafe must match finalGoNoGo");
}
if (JSON.stringify(queue.safetySignals?.releaseEnvFile || null) !== JSON.stringify(finalGoNoGo.safetySignals?.releaseEnvFile || null)) {
  addFailure("releaseFinalOwnerQueue safetySignals.releaseEnvFile must match finalGoNoGo");
}
if (!queue.ownerInputReceipt || typeof queue.ownerInputReceipt !== "object" || Array.isArray(queue.ownerInputReceipt)) {
  addFailure("releaseFinalOwnerQueue ownerInputReceipt must be an object");
} else {
  const finalReceipt = finalGoNoGo.ciSummary?.ownerInputReceipt || {};
  if (queue.ownerInputReceipt.status !== finalReceipt.status) {
    addFailure("releaseFinalOwnerQueue ownerInputReceipt.status must match finalGoNoGo ciSummary.ownerInputReceipt");
  }
  if (queue.ownerInputReceipt.cutoverReady !== finalReceipt.cutoverReady) {
    addFailure("releaseFinalOwnerQueue ownerInputReceipt.cutoverReady must match finalGoNoGo ciSummary.ownerInputReceipt");
  }
  for (const field of ["requiredOwnerInputs", "ownerCount", "readyOwnerCount", "pendingOwnerCount"]) {
    if (!Number.isInteger(queue.ownerInputReceipt[field]) || queue.ownerInputReceipt[field] < 0) {
      addFailure(`releaseFinalOwnerQueue ownerInputReceipt.${field} must be a non-negative integer`);
    }
    if (Number.isInteger(finalReceipt[field]) && queue.ownerInputReceipt[field] !== finalReceipt[field]) {
      addFailure(`releaseFinalOwnerQueue ownerInputReceipt.${field} must match finalGoNoGo ciSummary.ownerInputReceipt`);
    }
  }
  if (!Array.isArray(queue.ownerInputReceipt.missingCriteria)) {
    addFailure("releaseFinalOwnerQueue ownerInputReceipt.missingCriteria must be an array");
  } else if (!sameStringSet(queue.ownerInputReceipt.missingCriteria, finalReceipt.missingCriteria || [])) {
    addFailure("releaseFinalOwnerQueue ownerInputReceipt.missingCriteria must match finalGoNoGo ciSummary.ownerInputReceipt");
  }
  if (!Array.isArray(queue.ownerInputReceipt.pendingOwners)) {
    addFailure("releaseFinalOwnerQueue ownerInputReceipt.pendingOwners must be an array");
  }
  if (queue.ownerInputReceipt.status === "PENDING_OWNER_INPUT" && queue.ownerInputReceipt.cutoverReady !== false) {
    addFailure("releaseFinalOwnerQueue pending ownerInputReceipt must have cutoverReady=false");
  }
  if (queue.cutoverAllowed === true && (queue.ownerInputReceipt.status !== "PASS" || queue.ownerInputReceipt.cutoverReady !== true)) {
    addFailure("releaseFinalOwnerQueue cutoverAllowed=true requires ownerInputReceipt PASS and cutoverReady=true");
  }
  if (path.isAbsolute(queue.ownerInputReceipt.artifact || "") || String(queue.ownerInputReceipt.artifact || "").includes("..")) {
    addFailure("releaseFinalOwnerQueue ownerInputReceipt.artifact must be relative and traversal-free");
  }
  if (path.isAbsolute(queue.ownerInputReceipt.markdown || "") || String(queue.ownerInputReceipt.markdown || "").includes("..")) {
    addFailure("releaseFinalOwnerQueue ownerInputReceipt.markdown must be relative and traversal-free");
  }
}

const ownerQueues = Array.isArray(queue.ownerQueues) ? queue.ownerQueues : [];
const finalReadinessSummaryCommand = "node scripts/ddd-release-readiness-summary.mjs";
const finalGoNoGoEnforceCommand = "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh";
if (!Array.isArray(queue.ownerQueues)) addFailure("releaseFinalOwnerQueue ownerQueues must be an array");
if (queue.summary?.ownerCount !== ownerQueues.length) addFailure("summary.ownerCount must match ownerQueues length");
for (const [index, command] of (queue.fastPath?.commands || []).entries()) {
  assertSafeDisplayCommand(`fastPath.commands[${index}]`, command);
}
assertSafeDisplayCommand("fastPath.firstCommand", queue.fastPath?.firstCommand || "");
assertSafeDisplayCommand("summary.nextExecutableCommand", queue.summary?.nextExecutableCommand || "");

const actionableOwners = ownerQueues.filter((owner) => owner.queueStatus === "ACTIONABLE");
const waitingOwners = ownerQueues.filter((owner) => owner.queueStatus === "WAITING");
if (queue.summary?.actionableOwnerCount !== actionableOwners.length) addFailure("summary.actionableOwnerCount must match ACTIONABLE owner rows");
if (queue.summary?.waitingOwnerCount !== waitingOwners.length) addFailure("summary.waitingOwnerCount must match WAITING owner rows");
if (queue.cutoverAllowed === false && actionableOwners.length === 0) addFailure("NO-GO final owner queue must include actionable owners");

if (!sameStringSet(ownerQueues.map((owner) => owner.owner), finalGoNoGo.ciSummary?.stopOwners || [])) {
  addFailure("releaseFinalOwnerQueue owners must match finalGoNoGo stopOwners");
}

let seenWaiting = false;
for (const [index, owner] of ownerQueues.entries()) {
  const label = owner.owner || `owner[${index}]`;
  if (owner.queueOrder !== index + 1) addFailure(`${label}.queueOrder must be ${index + 1}`);
  if (!["ACTIONABLE", "WAITING"].includes(owner.queueStatus)) addFailure(`${label}.queueStatus must be ACTIONABLE or WAITING`);
  if (owner.queueStatus === "WAITING") seenWaiting = true;
  if (seenWaiting && owner.queueStatus === "ACTIONABLE") addFailure("ACTIONABLE owners must be ordered before WAITING owners");
  if (owner.canExecute !== (owner.queueStatus === "ACTIONABLE")) addFailure(`${label}.canExecute must match queueStatus`);
  if (owner.commandCount !== (owner.commands || []).length) addFailure(`${label}.commandCount must match commands length`);
  if (owner.envKeyCount !== (owner.envKeys || []).length) addFailure(`${label}.envKeyCount must match envKeys length`);
  if (owner.missingArtifactCount !== (owner.missingArtifacts || []).length) addFailure(`${label}.missingArtifactCount must match missingArtifacts length`);
  if (owner.contentBlockerCount !== (owner.contentBlockers || []).length) addFailure(`${label}.contentBlockerCount must match contentBlockers length`);
  if (owner.stopReasonCount !== (owner.stopReasons || []).length) addFailure(`${label}.stopReasonCount must match stopReasons length`);
  if (owner.canExecute && (!Array.isArray(owner.commands) || owner.commands.length === 0)) addFailure(`${label}.commands are required for executable owners`);
  if (owner.canExecute && owner.firstCommand !== owner.commands?.[0]) addFailure(`${label}.firstCommand must match commands[0]`);
  assertSafeDisplayCommand(`${label}.firstCommand`, owner.firstCommand || "");
  for (const [commandIndex, command] of (owner.commands || []).entries()) {
    assertSafeDisplayCommand(`${label}.commands[${commandIndex}]`, command);
  }
  for (const [commandIndex, command] of (owner.rerunCommands || []).entries()) {
    assertSafeDisplayCommand(`${label}.rerunCommands[${commandIndex}]`, command);
  }
  if (owner.canExecute && !owner.commands?.includes(finalReadinessSummaryCommand)) addFailure(`${label}.commands must include final readiness summary refresh`);
  if (owner.canExecute && !owner.commands?.includes(finalGoNoGoEnforceCommand)) addFailure(`${label}.commands must include final go/no-go enforce gate`);
  if (owner.canExecute && owner.commands?.at(-2) !== finalReadinessSummaryCommand) addFailure(`${label}.commands must end with readiness summary before final gate`);
  if (owner.canExecute && owner.commands?.at(-1) !== finalGoNoGoEnforceCommand) addFailure(`${label}.commands must end with final go/no-go enforce gate`);
}

const firstExecutable = ownerQueues.find((owner) => owner.canExecute === true) || null;
if ((queue.summary?.nextExecutableOwner || null) !== (firstExecutable?.owner || null)) addFailure("summary.nextExecutableOwner must match first executable owner");
if ((queue.summary?.nextExecutableQueueOrder || null) !== (firstExecutable?.queueOrder || null)) addFailure("summary.nextExecutableQueueOrder must match first executable owner");
if ((queue.summary?.nextExecutableCommand || null) !== (firstExecutable?.firstCommand || null)) addFailure("summary.nextExecutableCommand must match first executable owner");
if ((queue.summary?.nextExecutableEnvKeyCount || 0) !== (firstExecutable?.envKeyCount || 0)) addFailure("summary.nextExecutableEnvKeyCount must match first executable owner");
if ((queue.summary?.nextExecutableMissingArtifactCount || 0) !== (firstExecutable?.missingArtifactCount || 0)) addFailure("summary.nextExecutableMissingArtifactCount must match first executable owner");
if (firstExecutable && !finalGoNoGo.nextCommands?.includes(firstExecutable.firstCommand)) {
  addFailure("finalGoNoGo.nextCommands must include final owner queue next executable command");
}

const uniqueMissingArtifacts = new Set(ownerQueues.flatMap((owner) => owner.missingArtifacts || []));
const uniqueContentBlockers = new Set(ownerQueues.flatMap((owner) => owner.contentBlockers || []));
if (queue.summary?.missingArtifactCount !== uniqueMissingArtifacts.size) addFailure("summary.missingArtifactCount must match unique missing artifacts");
if (queue.summary?.contentBlockerCount !== uniqueContentBlockers.size) addFailure("summary.contentBlockerCount must match unique content blockers");
if (queue.summary?.ownerInputReceiptStatus !== queue.ownerInputReceipt?.status) addFailure("summary.ownerInputReceiptStatus must match ownerInputReceipt");
if (queue.summary?.ownerInputReceiptCutoverReady !== queue.ownerInputReceipt?.cutoverReady) addFailure("summary.ownerInputReceiptCutoverReady must match ownerInputReceipt");
if (queue.summary?.ownerInputReceiptRequiredOwnerInputs !== queue.ownerInputReceipt?.requiredOwnerInputs) addFailure("summary.ownerInputReceiptRequiredOwnerInputs must match ownerInputReceipt");
if (queue.summary?.ownerInputReceiptPendingOwnerCount !== queue.ownerInputReceipt?.pendingOwnerCount) addFailure("summary.ownerInputReceiptPendingOwnerCount must match ownerInputReceipt");
if (queue.summary?.ownerInputReceiptMissingCriteriaCount !== (queue.ownerInputReceipt?.missingCriteria || []).length) {
  addFailure("summary.ownerInputReceiptMissingCriteriaCount must match ownerInputReceipt");
}

if (failures.length > 0) {
  throw new Error(`release final owner queue contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-final-owner-queue-contract] ok owners=${ownerQueues.length} actionable=${actionableOwners.length} waiting=${waitingOwners.length}`);
