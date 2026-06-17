#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const queuePath = path.join(releaseDir, "release-execution-queue.json");
const batchesPath = path.join(releaseDir, "release-action-batches.json");
const sprintBoardPath = path.join(releaseDir, "release-sprint-board.json");

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release execution artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const queue = readJson(queuePath);
const batchesArtifact = readJson(batchesPath);
const sprintBoard = readJson(sprintBoardPath);
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function sorted(values = []) {
  return [...values].sort();
}

function sameStringSet(left = [], right = []) {
  return JSON.stringify(sorted([...new Set(left)])) === JSON.stringify(sorted([...new Set(right)]));
}

const batches = Array.isArray(batchesArtifact.batches) ? batchesArtifact.batches : [];
const readyBatches = Array.isArray(queue.readyBatches) ? queue.readyBatches : [];
const blockedBatches = Array.isArray(queue.blockedBatches) ? queue.blockedBatches : [];
const allQueueBatches = [...readyBatches, ...blockedBatches];
const batchIds = batches.map((batch) => batch.id);
const readyIds = readyBatches.map((batch) => batch.id);
const blockedIds = blockedBatches.map((batch) => batch.id);

if (!Array.isArray(batchesArtifact.batches)) addFailure("releaseActionBatches.batches must be an array");
if (!Array.isArray(queue.readyBatches)) addFailure("releaseExecutionQueue.readyBatches must be an array");
if (!Array.isArray(queue.blockedBatches)) addFailure("releaseExecutionQueue.blockedBatches must be an array");
if (queue.batchCount !== batches.length) addFailure("releaseExecutionQueue.batchCount must match action batches");
if (queue.readyBatchCount !== readyBatches.length) addFailure("releaseExecutionQueue.readyBatchCount must match readyBatches");
if (queue.blockedBatchCount !== blockedBatches.length) addFailure("releaseExecutionQueue.blockedBatchCount must match blockedBatches");
if (!sameStringSet([...readyIds, ...blockedIds], batchIds)) addFailure("releaseExecutionQueue ready+blocked batches must cover action batches");
if (new Set([...readyIds, ...blockedIds]).size !== readyIds.length + blockedIds.length) addFailure("releaseExecutionQueue ready/blocked batch ids must be disjoint");
if (!sameStringSet(queue.nextBatchIds || [], readyIds)) addFailure("releaseExecutionQueue.nextBatchIds must match ready batch ids");
const expectedNextPriority = readyBatches[0]?.priority || null;
if ((queue.nextPriority || null) !== expectedNextPriority) addFailure("releaseExecutionQueue.nextPriority must match first ready batch priority");

const batchById = new Map(batches.map((batch) => [batch.id, batch]));
for (const batch of allQueueBatches) {
  const source = batchById.get(batch.id);
  if (!source) {
    addFailure(`releaseExecutionQueue ${batch.id || "unknown"} is not present in action batches`);
    continue;
  }
  for (const field of ["priority", "source", "owner", "pendingItems"]) {
    if (batch[field] !== source[field]) addFailure(`releaseExecutionQueue ${batch.id}.${field} must match action batch`);
  }
  for (const field of ["commands", "envKeys", "expectedArtifacts", "exitCriteria"]) {
    if (!sameStringSet(batch[field] || [], source[field] || [])) addFailure(`releaseExecutionQueue ${batch.id}.${field} must match action batch`);
  }
  const dependencyIds = batch.dependsOn || (batch.unmetDependencies || []).map((dependency) => dependency.id);
  if (!sameStringSet(dependencyIds, source.dependsOn || [])) addFailure(`releaseExecutionQueue ${batch.id}.dependencies must match action batch`);
}

for (const batch of readyBatches) {
  if (!Array.isArray(batch.commands) || batch.commands.length === 0) addFailure(`ready batch ${batch.id} must include commands`);
  if (!Array.isArray(batch.expectedArtifacts) || batch.expectedArtifacts.length === 0) addFailure(`ready batch ${batch.id} must include expectedArtifacts`);
  if (!Array.isArray(batch.exitCriteria) || batch.exitCriteria.length === 0) addFailure(`ready batch ${batch.id} must include exitCriteria`);
  if ((batch.dependsOn || []).length !== 0) addFailure(`ready batch ${batch.id} must not have unmet dependencies`);
}

for (const batch of blockedBatches) {
  const unmetDependencyIds = (batch.unmetDependencies || []).map((dependency) => dependency.id);
  if (!Array.isArray(batch.unmetDependencies) || batch.unmetDependencies.length === 0) addFailure(`blocked batch ${batch.id} must include unmetDependencies`);
  if (batch.unmetDependencyCount !== unmetDependencyIds.length) addFailure(`blocked batch ${batch.id}.unmetDependencyCount must match unmetDependencies`);
  for (const dependency of unmetDependencyIds) {
    if (!batchById.has(dependency)) addFailure(`blocked batch ${batch.id} depends on unknown batch ${dependency}`);
  }
}

const cardReadyIds = (sprintBoard.batchCards || []).filter((card) => card.status === "READY").map((card) => card.id);
const cardBlockedIds = (sprintBoard.batchCards || []).filter((card) => card.status !== "READY").map((card) => card.id);
if (!sameStringSet(cardReadyIds, readyIds)) addFailure("releaseSprintBoard READY cards must match execution queue ready batches");
if (!sameStringSet(cardBlockedIds, blockedIds)) addFailure("releaseSprintBoard BLOCKED cards must match execution queue blocked batches");
if (!sameStringSet(sprintBoard.nextWave?.batchIds || [], readyIds)) addFailure("releaseSprintBoard nextWave batch ids must match ready batches");
if (sprintBoard.summary?.readyBatchCount !== readyBatches.length) addFailure("releaseSprintBoard readyBatchCount must match execution queue");
if (sprintBoard.summary?.blockedBatchCount !== blockedBatches.length) addFailure("releaseSprintBoard blockedBatchCount must match execution queue");

if (failures.length > 0) {
  throw new Error(`release execution queue contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-execution-queue-contract] ok batches=${batches.length} ready=${readyBatches.length} blocked=${blockedBatches.length}`);
