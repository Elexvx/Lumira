#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const canonicalFillPath = path.join(releaseDir, "release-env-canonical-fill.json");
const canonicalFillTemplatePath = path.join(releaseDir, "release-env-canonical-fill.template.env");
const matrixPath = path.join(releaseDir, "release-env-owner-matrix.json");
const envLintPath = path.join(releaseDir, "release-env-lint.json");

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env canonical fill artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function readText(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env canonical fill artifact: ${file}`);
  }
  return fs.readFileSync(file, "utf8");
}

const canonicalFill = readJson(canonicalFillPath);
const matrix = readJson(matrixPath);
const envLint = readJson(envLintPath);
const templateText = readText(canonicalFillTemplatePath);
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

function templateEntries(text) {
  return [...text.matchAll(/^([A-Z][A-Z0-9_]*)=(.*)$/gm)].map((match) => ({
    key: match[1],
    value: match[2],
  }));
}

function mappingPairs(mappings = []) {
  return mappings.map((mapping) => `${mapping.alias}->${mapping.canonical}`);
}

const items = Array.isArray(canonicalFill.items) ? canonicalFill.items : [];
const matrixOwners = Array.isArray(matrix.owners) ? matrix.owners : [];
const matrixOwnerNames = new Set(matrixOwners.map((owner) => owner.owner));
const lintTemplateKeys = new Set(envLint.keys || []);
const lintUnresolvedTemplateKeys = new Set(envLint.unresolvedTemplateKeys || []);
const lintCanonicalKeys = new Set(envLint.canonicalKeys || []);
const lintCanonicalUnresolvedKeys = new Set(envLint.canonicalUnresolvedTemplateKeys || []);
const lintAliasMappings = new Map(mappingPairs(envLint.missingEnv?.templateAliasMappings || []).map((pair) => [pair, true]));
const entries = templateEntries(templateText);
const entryKeys = entries.map((entry) => entry.key);
const canonicalKeys = items.map((item) => item.canonicalKey);

if (!Array.isArray(canonicalFill.items)) addFailure("releaseEnvCanonicalFill items must be an array");
if (canonicalFill.canonicalFillItemCount !== items.length) addFailure("canonicalFillItemCount must match items length");
if (canonicalFill.ownerCount !== new Set(items.flatMap((item) => item.owners || [])).size) addFailure("ownerCount must match unique item owners");
if (canonicalFill.envFile !== envLint.envFile) addFailure("envFile must match release env lint envFile");
if (canonicalFill.unresolvedAliasCount !== items.reduce((sum, item) => sum + (item.unresolvedAliasCount || 0), 0)) {
  addFailure("unresolvedAliasCount must match item unresolved alias totals");
}
if (hasDuplicates(canonicalKeys)) addFailure("canonicalKey values must be unique");
if (hasDuplicates(entryKeys)) addFailure("template canonical keys must be unique");
if (!sameStringSet(entryKeys, canonicalKeys)) addFailure("template keys must match canonical fill item canonicalKey values");
if (!/ddd-release-env-canonical-merge\.mjs/.test(templateText)) addFailure("template must document canonical merge command");
if (!/ddd-release-env-alias-sync\.mjs/.test(templateText)) addFailure("template must document alias sync command");

let previousItem = null;
for (const [index, item] of items.entries()) {
  const label = `canonical ${item.canonicalKey || "unknown"}`;
  if (item.fillOrder !== index + 1) addFailure(`${label}.fillOrder must be contiguous and match list order`);
  if (!item.owner || typeof item.owner !== "string") addFailure(`${label}.owner is required`);
  if (!Array.isArray(item.owners) || item.owners.length === 0) addFailure(`${label}.owners must be a non-empty array`);
  if (!item.owners.includes(item.owner)) addFailure(`${label}.owners must include primary owner`);
  if ((item.owners || []).some((owner) => !matrixOwnerNames.has(owner))) addFailure(`${label}.owners must exist in release env owner matrix`);
  if (!item.group || typeof item.group !== "string") addFailure(`${label}.group is required`);
  if (!item.requirement || typeof item.requirement !== "string") addFailure(`${label}.requirement is required`);
  if (!item.canonicalKey || typeof item.canonicalKey !== "string") addFailure(`${label}.canonicalKey is required`);
  if (!Array.isArray(item.aliases) || item.aliases.length === 0) addFailure(`${label}.aliases must be a non-empty array`);
  if (item.aliases?.[0] !== item.canonicalKey) addFailure(`${label}.aliases must start with canonicalKey`);
  if (item.aliasCount !== (item.aliases || []).length) addFailure(`${label}.aliasCount must match aliases length`);
  if (hasDuplicates(item.aliases || [])) addFailure(`${label}.aliases must be unique`);
  if (item.unresolvedAliasCount !== (item.unresolvedAliases || []).length) addFailure(`${label}.unresolvedAliasCount must match unresolvedAliases length`);
  if ((item.unresolvedAliases || []).some((alias) => !(item.aliases || []).includes(alias))) addFailure(`${label}.unresolvedAliases must be a subset of aliases`);
  if ((item.unresolvedAliases || []).some((alias) => !lintUnresolvedTemplateKeys.has(alias))) addFailure(`${label}.unresolvedAliases must exist in release env lint unresolved template keys`);
  if (item.required === true && !lintCanonicalKeys.has(item.canonicalKey)) addFailure(`${label}.canonicalKey must exist in release env lint canonical keys`);
  if (item.unresolvedAliasCount > 0 && !lintCanonicalUnresolvedKeys.has(item.canonicalKey)) addFailure(`${label}.canonicalKey must exist in canonical unresolved keys when aliases are unresolved`);
  if ((item.unresolvedAliases || []).some((alias) => alias !== item.canonicalKey && !lintTemplateKeys.has(alias) && !lintAliasMappings.has(`${alias}->${item.canonicalKey}`) && !canonicalKeys.includes(alias))) {
    addFailure(`${label}.unresolvedAliases must be represented in release env lint keys, alias mappings, or canonical fill keys`);
  }
  if (!["secret", "url", "toggle", "enum", "port", "runtime-setting", "identifier"].includes(item.valueClass)) addFailure(`${label}.valueClass is invalid`);
  if (typeof item.secret !== "boolean") addFailure(`${label}.secret must be boolean`);
  if (typeof item.safeToPreFill !== "boolean") addFailure(`${label}.safeToPreFill must be boolean`);
  if (item.secret === true && item.safeToPreFill !== false) addFailure(`${label}.secret items must not be safeToPreFill`);
  if (item.secret === true && item.valueClass !== "secret") addFailure(`${label}.secret items must use secret valueClass`);
  if (typeof item.required !== "boolean") addFailure(`${label}.required must be boolean`);
  if (!item.fillGuidance || typeof item.fillGuidance !== "string") addFailure(`${label}.fillGuidance is required`);
  if (!item.aliasSyncCommand || !item.aliasSyncCommand.includes("ddd-release-env-alias-sync.mjs")) addFailure(`${label}.aliasSyncCommand must run alias sync`);
  if (!item.aliasSyncCommand.includes(canonicalFill.envFile || ".env.release.local")) addFailure(`${label}.aliasSyncCommand must target canonical fill envFile`);
  if (!item.validation || typeof item.validation !== "object") addFailure(`${label}.validation is required`);
  if (item.validation && typeof item.validation.https !== "boolean") addFailure(`${label}.validation.https must be boolean`);
  if (item.validation && typeof item.validation.nonLocal !== "boolean") addFailure(`${label}.validation.nonLocal must be boolean`);
  if (item.validation && !Array.isArray(item.validation.expectedValues)) addFailure(`${label}.validation.expectedValues must be an array`);
  if (item.validation && !Array.isArray(item.validation.disallowValues)) addFailure(`${label}.validation.disallowValues must be an array`);

  if (previousItem) {
    const sorted = previousItem.owner.localeCompare(item.owner)
      || previousItem.group.localeCompare(item.group)
      || previousItem.requirement.localeCompare(item.requirement)
      || previousItem.canonicalKey.localeCompare(item.canonicalKey);
    if (sorted > 0) addFailure("items must be sorted by owner, group, requirement, canonicalKey");
  }
  previousItem = item;
}

const valueByKey = new Map(entries.map((entry) => [entry.key, entry.value]));
for (const item of items) {
  const value = valueByKey.get(item.canonicalKey);
  const label = `canonical ${item.canonicalKey}`;
  if (item.required === true && item.secret === true && value !== "__REQUIRED__") addFailure(`${label} secret required template value must be __REQUIRED__`);
  if (item.required === false && value !== "") addFailure(`${label} optional template value must be empty`);
  if (item.required === true && item.safeToPreFill === false && item.secret !== true && value !== "__REQUIRED__") addFailure(`${label} unsafe required template value must be __REQUIRED__`);
  if ((item.aliases || []).some((alias) => alias !== item.canonicalKey && entryKeys.includes(alias) && !canonicalKeys.includes(alias))) {
    addFailure(`${label} aliases must not be emitted as template fill keys`);
  }
}

if (failures.length > 0) {
  throw new Error(`release env canonical fill contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-env-canonical-fill-contract] ok items=${items.length} unresolvedAliases=${canonicalFill.unresolvedAliasCount}`);
