#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const fillPriorityPath = path.join(releaseDir, "release-env-fill-priority.json");
const matrixPath = path.join(releaseDir, "release-env-owner-matrix.json");

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env fill priority artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const fillPriority = readJson(fillPriorityPath);
const matrix = readJson(matrixPath);
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function sameStringSet(left = [], right = []) {
  return JSON.stringify([...new Set(left)].sort()) === JSON.stringify([...new Set(right)].sort());
}

function hasDuplicates(values = []) {
  return new Set(values).size !== values.length;
}

function ownerLabel(owner) {
  return `owner ${owner?.owner || "unknown"}`;
}

const owners = Array.isArray(fillPriority.owners) ? fillPriority.owners : [];
const matrixOwners = Array.isArray(matrix.owners) ? matrix.owners : [];
const unresolvedMatrixOwners = matrixOwners.filter((owner) => (owner.unresolvedTemplateKeyCount || 0) > 0);
const matrixByOwner = new Map(matrixOwners.map((owner) => [owner.owner, owner]));

if (!Array.isArray(fillPriority.owners)) addFailure("releaseEnvFillPriority owners must be an array");
if (fillPriority.ownerCount !== owners.length) addFailure("ownerCount must match owners length");
if (fillPriority.ownerCount !== unresolvedMatrixOwners.length) addFailure("ownerCount must match matrix owners with unresolved template keys");
if (fillPriority.runNowOwnerCount !== owners.filter((owner) => owner.priority === "RUN_NOW").length) addFailure("runNowOwnerCount must match RUN_NOW owners");
if (fillPriority.waitingOwnerCount !== owners.filter((owner) => owner.priority !== "RUN_NOW").length) addFailure("waitingOwnerCount must match non-RUN_NOW owners");
if (fillPriority.uniqueUnresolvedTemplateKeyCount !== matrix.uniqueUnresolvedTemplateKeyCount) addFailure("uniqueUnresolvedTemplateKeyCount must match release env owner matrix");
if (fillPriority.unresolvedOwnerAssignmentCount !== matrix.unresolvedOwnerAssignmentCount) addFailure("unresolvedOwnerAssignmentCount must match release env owner matrix");
if (!sameStringSet(owners.map((owner) => owner.owner), unresolvedMatrixOwners.map((owner) => owner.owner))) {
  addFailure("fill priority owners must match matrix owners with unresolved template keys");
}

const assignmentTotal = owners.reduce((sum, owner) => sum + (owner.unresolvedTemplateKeys || []).length, 0);
const filledAssignments = owners.reduce((sum, owner) => sum + (owner.filledTemplateKeyCount || 0), 0);
const placeholderAssignments = owners.reduce((sum, owner) => sum + (owner.placeholderTemplateKeyCount || 0), 0);
const missingAssignments = owners.reduce((sum, owner) => sum + (owner.missingTemplateKeyCount || 0), 0);
const uniqueUnresolvedKeys = [...new Set(owners.flatMap((owner) => owner.unresolvedTemplateKeys || []))];

if (fillPriority.unresolvedOwnerAssignmentCount !== assignmentTotal) addFailure("unresolvedOwnerAssignmentCount must match owner unresolved assignments");
if (fillPriority.uniqueUnresolvedTemplateKeyCount !== uniqueUnresolvedKeys.length) addFailure("uniqueUnresolvedTemplateKeyCount must match unique owner unresolved keys");
if (fillPriority.filledOwnerAssignmentCount !== filledAssignments) addFailure("filledOwnerAssignmentCount must match owner totals");
if (fillPriority.placeholderOwnerAssignmentCount !== placeholderAssignments) addFailure("placeholderOwnerAssignmentCount must match owner totals");
if (fillPriority.missingOwnerAssignmentCount !== missingAssignments) addFailure("missingOwnerAssignmentCount must match owner totals");
if (filledAssignments + placeholderAssignments + missingAssignments !== assignmentTotal) addFailure("filled+placeholder+missing assignments must equal unresolved assignments");

let seenWaiting = false;
let previousOwner = null;
for (const [index, owner] of owners.entries()) {
  const label = ownerLabel(owner);
  if (owner.fillOrder !== index + 1) addFailure(`${label}.fillOrder must be contiguous and match list order`);
  if (!["RUN_NOW", "WAITING"].includes(owner.priority)) addFailure(`${label}.priority must be RUN_NOW or WAITING`);
  if (owner.priority === "WAITING") seenWaiting = true;
  if (seenWaiting && owner.priority === "RUN_NOW") addFailure("RUN_NOW owners must be ordered before WAITING owners");

  const matrixOwner = matrixByOwner.get(owner.owner);
  if (!matrixOwner) {
    addFailure(`${label} must exist in release env owner matrix`);
    continue;
  }

  const expectedPriority = (matrixOwner.readyGroupCount || 0) > 0 ? "RUN_NOW" : "WAITING";
  if (owner.priority !== expectedPriority) addFailure(`${label}.priority must derive from matrix readyGroupCount`);
  if (owner.readyGroupCount !== matrixOwner.readyGroupCount) addFailure(`${label}.readyGroupCount must match release env owner matrix`);
  if (owner.blockedGroupCount !== matrixOwner.blockedGroupCount) addFailure(`${label}.blockedGroupCount must match release env owner matrix`);
  if (owner.unresolvedTemplateKeyCount !== (owner.unresolvedTemplateKeys || []).length) addFailure(`${label}.unresolvedTemplateKeyCount must match unresolvedTemplateKeys length`);
  if (owner.unresolvedTemplateKeyCount !== matrixOwner.unresolvedTemplateKeyCount) addFailure(`${label}.unresolvedTemplateKeyCount must match release env owner matrix`);
  if (!sameStringSet(owner.unresolvedTemplateKeys || [], matrixOwner.unresolvedTemplateKeys || [])) addFailure(`${label}.unresolvedTemplateKeys must match release env owner matrix`);
  if (!sameStringSet(owner.readyBatchIds || [], matrixOwner.readyBatchIds || [])) addFailure(`${label}.readyBatchIds must match release env owner matrix`);
  if (!sameStringSet(owner.blockedBatchIds || [], matrixOwner.blockedBatchIds || [])) addFailure(`${label}.blockedBatchIds must match release env owner matrix`);
  if (!sameStringSet(owner.commands || [], matrixOwner.commands || [])) addFailure(`${label}.commands must match release env owner matrix`);
  if (!sameStringSet(owner.exitCriteria || [], matrixOwner.exitCriteria || [])) addFailure(`${label}.exitCriteria must match release env owner matrix`);
  if ((owner.unresolvedTemplateKeys || []).includes("DDD_RELEASE_ENV_FILE")) addFailure(`${label}.unresolvedTemplateKeys must not include DDD_RELEASE_ENV_FILE control key`);
  if (hasDuplicates(owner.unresolvedTemplateKeys || [])) addFailure(`${label}.unresolvedTemplateKeys must be unique`);
  if (hasDuplicates((owner.fillStatusByKey || []).map((item) => item.envKey))) addFailure(`${label}.fillStatusByKey envKey values must be unique`);

  const statusByKey = new Map((owner.fillStatusByKey || []).map((item) => [item.envKey, item.status]));
  if (!sameStringSet([...statusByKey.keys()], owner.unresolvedTemplateKeys || [])) addFailure(`${label}.fillStatusByKey keys must match unresolvedTemplateKeys`);
  const allowedStatuses = new Set(["filled", "placeholder", "missing"]);
  for (const [envKey, status] of statusByKey.entries()) {
    if (!allowedStatuses.has(status)) addFailure(`${label}.${envKey} has invalid fill status`);
  }
  const filledKeys = [...statusByKey.entries()].filter(([, status]) => status === "filled").map(([envKey]) => envKey);
  const placeholderKeys = [...statusByKey.entries()].filter(([, status]) => status === "placeholder").map(([envKey]) => envKey);
  const missingKeys = [...statusByKey.entries()].filter(([, status]) => status === "missing").map(([envKey]) => envKey);
  if (!sameStringSet(owner.filledTemplateKeys || [], filledKeys)) addFailure(`${label}.filledTemplateKeys must match fillStatusByKey`);
  if (!sameStringSet(owner.placeholderTemplateKeys || [], placeholderKeys)) addFailure(`${label}.placeholderTemplateKeys must match fillStatusByKey`);
  if (!sameStringSet(owner.missingTemplateKeys || [], missingKeys)) addFailure(`${label}.missingTemplateKeys must match fillStatusByKey`);
  if (owner.filledTemplateKeyCount !== filledKeys.length) addFailure(`${label}.filledTemplateKeyCount must match filledTemplateKeys length`);
  if (owner.placeholderTemplateKeyCount !== placeholderKeys.length) addFailure(`${label}.placeholderTemplateKeyCount must match placeholderTemplateKeys length`);
  if (owner.missingTemplateKeyCount !== missingKeys.length) addFailure(`${label}.missingTemplateKeyCount must match missingTemplateKeys length`);
  if (filledKeys.length + placeholderKeys.length + missingKeys.length !== owner.unresolvedTemplateKeyCount) {
    addFailure(`${label}.filled+placeholder+missing counts must match unresolvedTemplateKeyCount`);
  }

  if (owner.priority === "RUN_NOW") {
    if (!Array.isArray(owner.readyBatchIds) || owner.readyBatchIds.length === 0) addFailure(`${label}.readyBatchIds are required for RUN_NOW owners`);
    if (!Array.isArray(owner.commands) || owner.commands.length === 0) addFailure(`${label}.commands are required for RUN_NOW owners`);
  }

  if (previousOwner) {
    const previousRank = previousOwner.priority === "RUN_NOW" ? 0 : 1;
    const currentRank = owner.priority === "RUN_NOW" ? 0 : 1;
    const sorted = previousRank - currentRank
      || (owner.readyGroupCount || 0) - (previousOwner.readyGroupCount || 0)
      || (owner.unresolvedTemplateKeyCount || 0) - (previousOwner.unresolvedTemplateKeyCount || 0)
      || previousOwner.owner.localeCompare(owner.owner);
    if (sorted > 0) {
      addFailure("owners must be sorted by priority, readyGroupCount desc, unresolvedTemplateKeyCount desc, owner asc");
    }
  }
  previousOwner = owner;
}

if (failures.length > 0) {
  throw new Error(`release env fill priority contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-env-fill-priority-contract] ok owners=${owners.length} runNow=${fillPriority.runNowOwnerCount} unresolved=${fillPriority.uniqueUnresolvedTemplateKeyCount}`);
