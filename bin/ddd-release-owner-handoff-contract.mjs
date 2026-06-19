#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const handoffPath = path.join(releaseDir, "release-owner-handoff.json");
const commandCatalogPath = path.join(releaseDir, "release-command-catalog.json");
const envOwnerMatrixPath = path.join(releaseDir, "release-env-owner-matrix.json");
const executionQueuePath = path.join(releaseDir, "release-execution-queue.json");

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release owner handoff artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const handoff = readJson(handoffPath);
const commandCatalog = readJson(commandCatalogPath);
const envOwnerMatrix = readJson(envOwnerMatrixPath);
const executionQueue = readJson(executionQueuePath);
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function sameStringSet(left = [], right = []) {
  return JSON.stringify([...new Set(left)].sort()) === JSON.stringify([...new Set(right)].sort());
}

function containsAll(values = [], required = []) {
  const valueSet = new Set(values);
  return required.every((value) => valueSet.has(value));
}

function assertCommandSet(label, commands, expectedOwner) {
  if (!commands || typeof commands !== "object" || Array.isArray(commands)) {
    addFailure(`${label}.commandSet must be an object`);
    return;
  }
  for (const key of ["list", "envCheck", "dryRun", "execute"]) {
    if (!commands[key] || typeof commands[key] !== "string") addFailure(`${label}.commandSet.${key} is required`);
    if (commands[key] && !commands[key].includes("bash artifacts/ddd/release/release-execution-commands.sh")) {
      addFailure(`${label}.commandSet.${key} must call release-execution-commands.sh`);
    }
    if (commands[key] && !commands[key].includes(`DDD_RELEASE_OWNER=${expectedOwner}`)) {
      addFailure(`${label}.commandSet.${key} must include owner filter`);
    }
  }
  if (!commands.list?.includes("DDD_RELEASE_LIST_BATCHES=1")) addFailure(`${label}.commandSet.list must set DDD_RELEASE_LIST_BATCHES=1`);
  if (!commands.envCheck?.includes("DDD_RELEASE_CHECK_ENV_ONLY=1")) addFailure(`${label}.commandSet.envCheck must set DDD_RELEASE_CHECK_ENV_ONLY=1`);
  if (!commands.dryRun?.includes("DDD_RELEASE_DRY_RUN=1")) addFailure(`${label}.commandSet.dryRun must set DDD_RELEASE_DRY_RUN=1`);
  if (commands.execute?.includes("DDD_RELEASE_DRY_RUN=1") || commands.execute?.includes("DDD_RELEASE_CHECK_ENV_ONLY=1") || commands.execute?.includes("DDD_RELEASE_LIST_BATCHES=1")) {
    addFailure(`${label}.commandSet.execute must be the execution command without dry-run/list/env-check toggles`);
  }
}

if (handoff.noAutoWaivers !== true) addFailure("releaseOwnerHandoff must keep noAutoWaivers=true");
if (handoff.recommendation !== commandCatalog.recommendation) addFailure("releaseOwnerHandoff recommendation must match command catalog");
if (!handoff.finalDecision || typeof handoff.finalDecision !== "object" || Array.isArray(handoff.finalDecision)) {
  addFailure("releaseOwnerHandoff finalDecision must be an object");
} else if (JSON.stringify(handoff.finalDecision) !== JSON.stringify(commandCatalog.finalDecision || null)) {
  addFailure("releaseOwnerHandoff finalDecision must match command catalog");
}
if (!commandCatalog.finalDecision || typeof commandCatalog.finalDecision !== "object" || Array.isArray(commandCatalog.finalDecision)) {
  addFailure("releaseCommandCatalog finalDecision must be an object");
}
if (JSON.stringify(handoff.safetySignals?.releaseEnvFile || null) !== JSON.stringify(commandCatalog.safetySignals?.releaseEnvFile || null)) {
  addFailure("releaseOwnerHandoff safetySignals.releaseEnvFile must match command catalog");
}
if (handoff.releaseEnvFileCutoverSafe !== commandCatalog.releaseEnvFileCutoverSafe) {
  addFailure("releaseOwnerHandoff releaseEnvFileCutoverSafe must match command catalog");
}
if (!Array.isArray(handoff.owners)) addFailure("releaseOwnerHandoff owners must be an array");

const owners = Array.isArray(handoff.owners) ? handoff.owners : [];
const envOwners = envOwnerMatrix.owners || [];
const commandOwners = commandCatalog.ownerCommands || [];
const readyBatchIds = (executionQueue.readyBatches || []).map((batch) => batch.id);
const blockedBatchIds = (executionQueue.blockedBatches || []).map((batch) => batch.id);

if (handoff.summary?.ownerCount !== owners.length) addFailure("summary.ownerCount must match owners length");
if (handoff.summary?.readyOwnerCount !== owners.filter((owner) => owner.status === "READY").length) addFailure("summary.readyOwnerCount must match READY owners");
if (handoff.summary?.blockedOwnerCount !== owners.filter((owner) => owner.status !== "READY").length) addFailure("summary.blockedOwnerCount must match blocked owners");
if (handoff.summary?.readyBatchCount !== readyBatchIds.length) addFailure("summary.readyBatchCount must match execution queue");
if (handoff.summary?.blockedBatchCount !== blockedBatchIds.length) addFailure("summary.blockedBatchCount must match execution queue");
if (!sameStringSet(owners.flatMap((owner) => owner.readyBatchIds || []), readyBatchIds)) addFailure("handoff readyBatchIds must cover execution queue ready batches");

const expectedOwners = [...new Set([
  ...envOwners.map((owner) => owner.owner),
  ...commandOwners.map((owner) => owner.owner),
])];
if (!sameStringSet(owners.map((owner) => owner.owner), expectedOwners)) {
  addFailure("handoff owners must cover env owner matrix and command catalog owners");
}

const envOwnerByName = new Map(envOwners.map((owner) => [owner.owner, owner]));
const commandOwnerByName = new Map(commandOwners.map((owner) => [owner.owner, owner]));
for (const owner of owners) {
  const label = `owner ${owner.owner || "unknown"}`;
  const envOwner = envOwnerByName.get(owner.owner);
  const commandOwner = commandOwnerByName.get(owner.owner);
  if (owner.status !== ((owner.readyBatchIds || []).length > 0 ? "READY" : "BLOCKED")) {
    addFailure(`${label}.status must match readyBatchIds`);
  }
  if (!sameStringSet(owner.batchIds || [], [...(owner.readyBatchIds || []), ...(owner.blockedBatchIds || [])])) {
    addFailure(`${label}.batchIds must equal ready+blocked batch ids`);
  }
  if (envOwner) {
    if (!sameStringSet(owner.templateEnvKeys || [], envOwner.templateEnvKeys || [])) addFailure(`${label}.templateEnvKeys must match env owner matrix`);
    if (!sameStringSet((owner.aliasMappings || []).map((entry) => `${entry.alias}->${entry.canonical}`), (envOwner.aliasMappings || []).map((entry) => `${entry.alias}->${entry.canonical}`))) {
      addFailure(`${label}.aliasMappings must match env owner matrix`);
    }
  }
  if (commandOwner) {
    if (!sameStringSet(owner.readyBatchIds || [], commandOwner.readyBatchIds || [])) addFailure(`${label}.readyBatchIds must match command catalog`);
    if (!containsAll(owner.expectedArtifacts || [], commandOwner.expectedArtifacts || [])) {
      addFailure(`${label}.expectedArtifacts must include command catalog expected artifacts`);
    }
    assertCommandSet(label, owner.commandSet, owner.owner);
  }
}

const templateKeyCount = new Set(owners.flatMap((owner) => owner.templateEnvKeys || [])).size;
if (handoff.summary?.templateEnvKeyCount !== templateKeyCount) addFailure("summary.templateEnvKeyCount must match unique template env keys");

if (failures.length > 0) {
  throw new Error(`release owner handoff contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-owner-handoff-contract] ok owners=${owners.length} ready=${handoff.summary?.readyOwnerCount} blocked=${handoff.summary?.blockedOwnerCount}`);
