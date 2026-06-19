#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const catalogPath = path.join(releaseDir, "release-command-catalog.json");
const executionQueuePath = path.join(releaseDir, "release-execution-queue.json");
const sprintBoardPath = path.join(releaseDir, "release-sprint-board.json");
const finalGoNoGoPath = path.join(releaseDir, "release-final-go-no-go.json");

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release command catalog artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const catalog = readJson(catalogPath);
const executionQueue = readJson(executionQueuePath);
const sprintBoard = readJson(sprintBoardPath);
const finalGoNoGo = readJson(finalGoNoGoPath);
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function sameStringSet(left = [], right = []) {
  return JSON.stringify([...new Set(left)].sort()) === JSON.stringify([...new Set(right)].sort());
}

function releaseEnvFileIsCutoverSafe(signal) {
  return signal?.ready === true
    && signal?.status === "PASS"
    && signal?.inputKind === "release-env-file"
    && signal?.envFilePresent === true
    && signal?.generatedMissingTemplate !== true
    && signal?.securityChecked === true
    && signal?.permissionSafe === true
    && signal?.permissionCheckSkipped !== true
    && signal?.modeOctal === (signal?.requiredMode || "600")
    && (signal?.requiredMode || "600") === "600";
}

function assertCommandSet(label, commands, expectedFilter) {
  if (!commands || typeof commands !== "object" || Array.isArray(commands)) {
    addFailure(`${label}.commands must be an object`);
    return;
  }
  for (const key of ["list", "envCheck", "dryRun", "execute"]) {
    if (!commands[key] || typeof commands[key] !== "string") addFailure(`${label}.commands.${key} is required`);
    if (commands[key] && !commands[key].includes("bash artifacts/ddd/release/release-execution-commands.sh")) {
      addFailure(`${label}.commands.${key} must call release-execution-commands.sh`);
    }
    if (commands[key] && !commands[key].includes(expectedFilter)) {
      addFailure(`${label}.commands.${key} must include ${expectedFilter}`);
    }
  }
  if (!commands.list?.includes("DDD_RELEASE_LIST_BATCHES=1")) addFailure(`${label}.commands.list must set DDD_RELEASE_LIST_BATCHES=1`);
  if (!commands.envCheck?.includes("DDD_RELEASE_CHECK_ENV_ONLY=1")) addFailure(`${label}.commands.envCheck must set DDD_RELEASE_CHECK_ENV_ONLY=1`);
  if (!commands.dryRun?.includes("DDD_RELEASE_DRY_RUN=1")) addFailure(`${label}.commands.dryRun must set DDD_RELEASE_DRY_RUN=1`);
  if (commands.execute?.includes("DDD_RELEASE_DRY_RUN=1") || commands.execute?.includes("DDD_RELEASE_CHECK_ENV_ONLY=1") || commands.execute?.includes("DDD_RELEASE_LIST_BATCHES=1")) {
    addFailure(`${label}.commands.execute must be the execution command without dry-run/list/env-check toggles`);
  }
}

if (catalog.noAutoWaivers !== true) addFailure("releaseCommandCatalog must keep noAutoWaivers=true");
if (catalog.recommendation !== sprintBoard.recommendation) addFailure("releaseCommandCatalog recommendation must match sprint board");
if (!catalog.finalDecision || typeof catalog.finalDecision !== "object" || Array.isArray(catalog.finalDecision)) {
  addFailure("releaseCommandCatalog finalDecision must be an object");
} else {
  if (catalog.finalDecision.finalRecommendation !== finalGoNoGo.finalRecommendation) {
    addFailure("releaseCommandCatalog finalDecision.finalRecommendation must match final go/no-go");
  }
  if (catalog.finalDecision.cutoverAllowed === true && finalGoNoGo.cutoverAllowed !== true) {
    addFailure("releaseCommandCatalog finalDecision.cutoverAllowed must not be true while final go/no-go blocks cutover");
  }
  if (catalog.finalDecision.releaseEnvFileCutoverSafe !== finalGoNoGo.releaseEnvFileCutoverSafe) {
    addFailure("releaseCommandCatalog finalDecision.releaseEnvFileCutoverSafe must match final go/no-go");
  }
  if (catalog.finalDecision.gateBlockers !== finalGoNoGo.gate?.blockers) {
    addFailure("releaseCommandCatalog finalDecision.gateBlockers must match final go/no-go");
  }
  if (catalog.finalDecision.blockedCutoverItems !== finalGoNoGo.summary?.blockedCutoverItems) {
    addFailure("releaseCommandCatalog finalDecision.blockedCutoverItems must match final go/no-go");
  }
  if (!Number.isInteger(catalog.finalDecision.stopReasonCount) || catalog.finalDecision.stopReasonCount < 0) {
    addFailure("releaseCommandCatalog finalDecision.stopReasonCount must be a non-negative integer");
  }
  if (catalog.finalDecision.stopReasonCoverage !== "catalog-snapshot") {
    addFailure("releaseCommandCatalog finalDecision.stopReasonCoverage must be catalog-snapshot");
  }
  if (catalog.finalDecision.source !== "artifacts/ddd/release/release-final-go-no-go.json") {
    addFailure("releaseCommandCatalog finalDecision.source must point to final go/no-go packet");
  }
  if (catalog.finalDecision.enforceCommand !== "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh") {
    addFailure("releaseCommandCatalog finalDecision.enforceCommand must rerun final go/no-go gate");
  }
  if (catalog.finalDecision.cutoverAuthority !== "final-go-no-go-gate") {
    addFailure("releaseCommandCatalog finalDecision.cutoverAuthority must be final-go-no-go-gate");
  }
  if (catalog.finalDecision.requiresFinalGate !== true) {
    addFailure("releaseCommandCatalog finalDecision.requiresFinalGate must be true");
  }
}
if (JSON.stringify(catalog.safetySignals?.releaseEnvFile || null) !== JSON.stringify(executionQueue.safetySignals?.releaseEnvFile || null)) {
  addFailure("releaseCommandCatalog safetySignals.releaseEnvFile must match execution queue");
}
if (typeof catalog.releaseEnvFileCutoverSafe !== "boolean") {
  addFailure("releaseCommandCatalog releaseEnvFileCutoverSafe must be boolean");
} else if (catalog.releaseEnvFileCutoverSafe !== releaseEnvFileIsCutoverSafe(catalog.safetySignals?.releaseEnvFile)) {
  addFailure("releaseCommandCatalog releaseEnvFileCutoverSafe must match release env safety predicate");
}
if (catalog.summary?.nextPriority !== executionQueue.nextPriority) addFailure("summary.nextPriority must match execution queue");
if (catalog.summary?.readyBatchCount !== executionQueue.readyBatchCount) addFailure("summary.readyBatchCount must match execution queue");
if (catalog.summary?.batchCommandCount !== (catalog.batchCommands || []).length) addFailure("summary.batchCommandCount must match batchCommands");
if (catalog.summary?.ownerCommandCount !== (catalog.ownerCommands || []).length) addFailure("summary.ownerCommandCount must match ownerCommands");
if (catalog.scriptPath !== "artifacts/ddd/release/release-execution-commands.sh") addFailure("scriptPath must point to release-execution-commands.sh");

const readyBatchIds = (executionQueue.readyBatches || []).map((batch) => batch.id);
const nextWaveOwners = sprintBoard.nextWave?.owners || [];
const batchCommandIds = (catalog.batchCommands || []).map((batch) => batch.batchId);
const ownerCommandOwners = (catalog.ownerCommands || []).map((owner) => owner.owner);
if (!sameStringSet(batchCommandIds, readyBatchIds)) addFailure("batchCommands must cover every ready batch exactly once");
if (!sameStringSet(ownerCommandOwners, nextWaveOwners)) addFailure("ownerCommands must cover every nextWave owner exactly once");
assertCommandSet("nextPriority", catalog.nextPriorityCommands, `DDD_RELEASE_PRIORITY=${executionQueue.nextPriority}`);

const readyBatchById = new Map((executionQueue.readyBatches || []).map((batch) => [batch.id, batch]));
for (const batch of catalog.batchCommands || []) {
  const source = readyBatchById.get(batch.batchId);
  const label = `batch ${batch.batchId || "unknown"}`;
  if (!source) {
    addFailure(`${label} is not a ready execution batch`);
    continue;
  }
  if (batch.owner !== source.owner) addFailure(`${label}.owner must match ready batch`);
  if (batch.priority !== source.priority) addFailure(`${label}.priority must match ready batch`);
  if (!sameStringSet(batch.expectedArtifacts || [], source.expectedArtifacts || [])) addFailure(`${label}.expectedArtifacts must match ready batch`);
  assertCommandSet(label, batch.commands, `DDD_RELEASE_BATCH=${batch.batchId}`);
}

for (const owner of catalog.ownerCommands || []) {
  const label = `owner ${owner.owner || "unknown"}`;
  const ownerReadyBatches = (executionQueue.readyBatches || []).filter((batch) => batch.owner === owner.owner);
  if (owner.priority !== executionQueue.nextPriority) addFailure(`${label}.priority must match next priority`);
  if (!sameStringSet(owner.readyBatchIds || [], ownerReadyBatches.map((batch) => batch.id))) addFailure(`${label}.readyBatchIds must match ready batches for owner`);
  if (!sameStringSet(owner.expectedArtifacts || [], ownerReadyBatches.flatMap((batch) => batch.expectedArtifacts || []))) addFailure(`${label}.expectedArtifacts must match owner ready batches`);
  assertCommandSet(label, owner.commands, `DDD_RELEASE_OWNER=${owner.owner}`);
  for (const command of Object.values(owner.commands || {})) {
    if (typeof command === "string" && !command.includes(`DDD_RELEASE_PRIORITY=${owner.priority}`)) {
      addFailure(`${label}.commands must include owner priority filter`);
    }
  }
}

if (failures.length > 0) {
  throw new Error(`release command catalog contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-command-catalog-contract] ok owners=${(catalog.ownerCommands || []).length} batches=${(catalog.batchCommands || []).length}`);
