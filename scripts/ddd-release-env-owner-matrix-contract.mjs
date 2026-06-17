#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const matrixPath = path.join(releaseDir, "release-env-owner-matrix.json");
const missingEnvPath = path.join(releaseDir, "release-env-missing.json");

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env owner artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const matrix = readJson(matrixPath);
const missingEnv = readJson(missingEnvPath);
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function sameStringSet(left = [], right = []) {
  return JSON.stringify([...new Set(left)].sort()) === JSON.stringify([...new Set(right)].sort());
}

function mappingKeys(mappings = []) {
  return mappings.map((entry) => `${entry.alias}->${entry.canonical}`);
}

const owners = Array.isArray(matrix.owners) ? matrix.owners : [];
if (!Array.isArray(matrix.owners)) addFailure("releaseEnvOwnerMatrix owners must be an array");
if (matrix.ownerCount !== owners.length) addFailure("ownerCount must match owners length");
if (matrix.readyOwnerCount !== owners.filter((owner) => owner.readyGroupCount > 0).length) addFailure("readyOwnerCount must match owners with ready groups");
if (matrix.groupCount !== missingEnv.groupCount) addFailure("groupCount must match release-env-missing");
if (matrix.templateEnvKeyCount !== missingEnv.templateEnvKeyCount) addFailure("templateEnvKeyCount must match release-env-missing");
if (!sameStringSet(owners.flatMap((owner) => owner.templateEnvKeys || []), missingEnv.templateEnvKeys || [])) {
  addFailure("owner templateEnvKeys must cover release-env-missing templateEnvKeys");
}
if (!sameStringSet(owners.flatMap((owner) => mappingKeys(owner.aliasMappings || [])), mappingKeys(missingEnv.templateAliasMappings || []))) {
  addFailure("owner aliasMappings must cover release-env-missing templateAliasMappings");
}

const unresolvedKeys = [...new Set(owners.flatMap((owner) => owner.unresolvedTemplateKeys || []))];
if (matrix.uniqueUnresolvedTemplateKeyCount !== unresolvedKeys.length) addFailure("uniqueUnresolvedTemplateKeyCount must match owner unresolved keys");
const unresolvedAssignments = owners.reduce((sum, owner) => sum + (owner.unresolvedTemplateKeys || []).length, 0);
if (matrix.unresolvedOwnerAssignmentCount !== unresolvedAssignments) addFailure("unresolvedOwnerAssignmentCount must match owner unresolved assignments");

for (const owner of owners) {
  const label = `owner ${owner.owner || "unknown"}`;
  if (!Number.isInteger(owner.groupCount) || owner.groupCount < 0) addFailure(`${label}.groupCount must be a non-negative integer`);
  if (owner.groupCount !== (owner.groups || []).length) addFailure(`${label}.groupCount must match groups length`);
  if (owner.readyGroupCount + owner.blockedGroupCount !== owner.groupCount) addFailure(`${label}.readyGroupCount + blockedGroupCount must match groupCount`);
  if (!sameStringSet(owner.batchIds || [], [...(owner.readyBatchIds || []), ...(owner.blockedBatchIds || [])])) {
    addFailure(`${label}.batchIds must equal ready+blocked batch ids`);
  }
  if ((owner.templateEnvKeys || []).includes("DDD_RELEASE_ENV_FILE")) {
    addFailure(`${label}.templateEnvKeys must not include DDD_RELEASE_ENV_FILE control key`);
  }
  const ownerTemplateKeys = new Set(owner.templateEnvKeys || []);
  const missingTemplateKeys = new Set(missingEnv.templateEnvKeys || []);
  if ((owner.unresolvedTemplateKeys || []).some((key) => !ownerTemplateKeys.has(key) || !missingTemplateKeys.has(key))) {
    addFailure(`${label}.unresolvedTemplateKeys must be a subset of owner template env keys from release-env-missing`);
  }
  for (const group of owner.groups || []) {
    if (!group.batchId || typeof group.batchId !== "string") addFailure(`${label}.groups batchId is required`);
    if (!Array.isArray(group.envCheckGroups) || group.envCheckGroups.length === 0) addFailure(`${label}.${group.batchId}.envCheckGroups are required`);
    if (!Array.isArray(group.expectedArtifacts) || group.expectedArtifacts.length === 0) addFailure(`${label}.${group.batchId}.expectedArtifacts are required`);
    if (!Array.isArray(group.exitCriteria) || group.exitCriteria.length === 0) addFailure(`${label}.${group.batchId}.exitCriteria are required`);
  }
}

if (failures.length > 0) {
  throw new Error(`release env owner matrix contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-env-owner-matrix-contract] ok owners=${owners.length} templateKeys=${matrix.templateEnvKeyCount} unresolved=${matrix.uniqueUnresolvedTemplateKeyCount}`);
