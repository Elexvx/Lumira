#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const readinessPath = path.join(releaseDir, "release-env-readiness-redacted.json");
const readinessCsvPath = path.join(releaseDir, "release-env-readiness-redacted.csv");
const readinessMarkdownPath = path.join(releaseDir, "release-env-readiness-redacted.md");
const canonicalFillPath = path.join(releaseDir, "release-env-canonical-fill.json");
const envLintPath = path.join(releaseDir, "release-env-lint.json");

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env readiness redacted artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function readText(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env readiness redacted artifact: ${file}`);
  }
  return fs.readFileSync(file, "utf8");
}

const readiness = readJson(readinessPath);
const canonicalFill = readJson(canonicalFillPath);
const envLint = readJson(envLintPath);
const readinessCsv = readText(readinessCsvPath);
const readinessMarkdown = readText(readinessMarkdownPath);
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

function ownerKey(owner) {
  return owner?.owner || "unknown";
}

const items = Array.isArray(readiness.items) ? readiness.items : [];
const canonicalItems = Array.isArray(canonicalFill.items) ? canonicalFill.items : [];
const canonicalByKey = new Map(canonicalItems.map((item) => [item.canonicalKey, item]));
const byOwner = Array.isArray(readiness.byOwner) ? readiness.byOwner : [];
const unresolvedKeys = new Set(envLint.unresolvedTemplateKeys || []);
const presentKeys = new Set(envLint.keys || []);
const allowedStatuses = new Set(["FILLED_REDACTED", "PLACEHOLDER", "MISSING", "OPTIONAL_EMPTY"]);
const allowedOwnerInputReasons = new Set([
  "not-blocking",
  "secret-manager",
  "production-endpoint",
  "production-port",
  "safe-default-or-owner-choice",
  "owner-secure-value",
  "owner-production-value",
]);
const forbiddenValuePatterns = [
  /__REQUIRED__/,
  /\b[A-Z][A-Z0-9_]*=/,
  /\b(?:mysql|postgres|jdbc|redis|amqp):\/\/[^\s,;"]+/i,
  /\b(?:sk-[A-Za-z0-9_-]{12,}|eyJ[A-Za-z0-9_-]{20,})\b/,
];

function expectedOwnerInputReason(item) {
  const validation = item?.validation || {};
  if (item?.blocker !== true) return "not-blocking";
  if (item?.secret === true) return "secret-manager";
  if (validation.https === true || validation.nonLocal === true || item?.valueClass === "url") return "production-endpoint";
  if (item?.valueClass === "port") return "production-port";
  if (item?.safeToPreFill === true) return "safe-default-or-owner-choice";
  if (validation.minLength) return "owner-secure-value";
  return "owner-production-value";
}

function expectedSafeDefaultAvailable(item) {
  return item?.blocker === true
    && item?.secret !== true
    && item?.safeToPreFill === true
    && ["PLACEHOLDER", "MISSING"].includes(item?.status);
}

if (readiness.redacted !== true) addFailure("redacted must be true");
if (!String(readiness.valuePolicy || "").includes("No concrete environment values are emitted")) addFailure("valuePolicy must forbid concrete values");
if (readiness.envFile !== canonicalFill.envFile || readiness.envFile !== envLint.envFile) addFailure("envFile must match canonical fill and env lint");
if (!Array.isArray(readiness.items)) addFailure("items must be an array");
if (!Array.isArray(readiness.byOwner)) addFailure("byOwner must be an array");
if (readiness.summary?.totalCanonicalKeys !== items.length) addFailure("summary.totalCanonicalKeys must match items length");
if (readiness.summary?.totalCanonicalKeys !== canonicalFill.canonicalFillItemCount) addFailure("summary.totalCanonicalKeys must match canonical fill count");
if (!sameStringSet(items.map((item) => item.canonicalKey), canonicalItems.map((item) => item.canonicalKey))) {
  addFailure("readiness items must match canonical fill canonical keys");
}
if (hasDuplicates(items.map((item) => item.canonicalKey))) addFailure("readiness canonical keys must be unique");

const statusCounts = {
  FILLED_REDACTED: items.filter((item) => item.status === "FILLED_REDACTED").length,
  PLACEHOLDER: items.filter((item) => item.status === "PLACEHOLDER").length,
  MISSING: items.filter((item) => item.status === "MISSING").length,
  OPTIONAL_EMPTY: items.filter((item) => item.status === "OPTIONAL_EMPTY").length,
};
if (readiness.summary?.filledRedacted !== statusCounts.FILLED_REDACTED) addFailure("summary.filledRedacted must match items");
if (readiness.summary?.placeholders !== statusCounts.PLACEHOLDER) addFailure("summary.placeholders must match items");
if (readiness.summary?.missing !== statusCounts.MISSING) addFailure("summary.missing must match items");
if (readiness.summary?.optionalEmpty !== statusCounts.OPTIONAL_EMPTY) addFailure("summary.optionalEmpty must match items");
if (readiness.summary?.blockers !== items.filter((item) => item.blocker === true).length) addFailure("summary.blockers must match blocker items");
if (readiness.summary?.secretKeys !== items.filter((item) => item.secret === true).length) addFailure("summary.secretKeys must match secret items");
if (readiness.summary?.ownerCount !== byOwner.length) addFailure("summary.ownerCount must match byOwner length");
if (readiness.summary?.blockingSafeDefaultAvailable !== items.filter((item) => item.safeDefaultAvailable === true).length) addFailure("summary.blockingSafeDefaultAvailable must match items");
if (readiness.summary?.blockingRequiresOwnerInput !== items.filter((item) => item.requiresOwnerInput === true).length) addFailure("summary.blockingRequiresOwnerInput must match items");
if (readiness.summary?.safeDefaultsExhausted !== (items.filter((item) => item.safeDefaultAvailable === true).length === 0)) addFailure("summary.safeDefaultsExhausted must match safe default availability");

let previousItem = null;
for (const [index, item] of items.entries()) {
  const label = `readiness ${item.canonicalKey || "unknown"}`;
  const canonical = canonicalByKey.get(item.canonicalKey);
  if (!canonical) {
    addFailure(`${label} must exist in canonical fill`);
    continue;
  }
  if (item.fillOrder !== index + 1) addFailure(`${label}.fillOrder must be contiguous and match list order`);
  if (item.fillOrder !== canonical.fillOrder) addFailure(`${label}.fillOrder must match canonical fill`);
  if (item.owner !== canonical.owner) addFailure(`${label}.owner must match canonical fill`);
  if (!sameStringSet(item.owners || [], canonical.owners || [])) addFailure(`${label}.owners must match canonical fill`);
  if (item.group !== canonical.group) addFailure(`${label}.group must match canonical fill`);
  if (item.requirement !== canonical.requirement) addFailure(`${label}.requirement must match canonical fill`);
  if (item.required !== canonical.required) addFailure(`${label}.required must match canonical fill`);
  if (item.secret !== canonical.secret) addFailure(`${label}.secret must match canonical fill`);
  if (item.valueClass !== canonical.valueClass) addFailure(`${label}.valueClass must match canonical fill`);
  if (item.safeToPreFill !== canonical.safeToPreFill) addFailure(`${label}.safeToPreFill must match canonical fill`);
  if (!sameStringSet(item.aliases || [], canonical.aliases || [])) addFailure(`${label}.aliases must match canonical fill`);
  if (!allowedStatuses.has(item.status)) addFailure(`${label}.status is invalid`);
  const expectedStatus = unresolvedKeys.has(item.canonicalKey)
    ? "PLACEHOLDER"
    : presentKeys.has(item.canonicalKey)
      ? "FILLED_REDACTED"
      : item.required === false
        ? "OPTIONAL_EMPTY"
        : "MISSING";
  if (item.status !== expectedStatus) addFailure(`${label}.status must derive from release env lint without exposing values`);
  if (item.status === "FILLED_REDACTED" && item.blocker === true) addFailure(`${label}.blocker must not be true for FILLED_REDACTED`);
  if (item.status === "OPTIONAL_EMPTY" && item.required !== false) addFailure(`${label}.OPTIONAL_EMPTY requires required=false`);
  if (item.secret === true && item.safeToPreFill !== false) addFailure(`${label}.secret keys must not be safeToPreFill`);
  const expectedSafeDefault = expectedSafeDefaultAvailable(item);
  if (item.safeDefaultAvailable !== expectedSafeDefault) addFailure(`${label}.safeDefaultAvailable must derive from blocker, status, secret, and safeToPreFill`);
  const expectedRequiresOwnerInput = item.blocker === true && expectedSafeDefault !== true;
  if (item.requiresOwnerInput !== expectedRequiresOwnerInput) addFailure(`${label}.requiresOwnerInput must derive from blocker and safeDefaultAvailable`);
  if (!allowedOwnerInputReasons.has(item.ownerInputReason)) addFailure(`${label}.ownerInputReason is invalid`);
  if (item.ownerInputReason !== expectedOwnerInputReason(item)) addFailure(`${label}.ownerInputReason must derive from validation and value class`);
  if (item.validation && typeof item.validation.https !== "boolean") addFailure(`${label}.validation.https must be boolean`);
  if (item.validation && typeof item.validation.nonLocal !== "boolean") addFailure(`${label}.validation.nonLocal must be boolean`);
  if (item.validation && !Array.isArray(item.validation.expectedValues)) addFailure(`${label}.validation.expectedValues must be an array`);
  if (item.validation && !Array.isArray(item.validation.disallowValues)) addFailure(`${label}.validation.disallowValues must be an array`);
  if (JSON.stringify(item).includes("__REQUIRED__")) addFailure(`${label} must not contain template placeholder values`);
  if (previousItem && previousItem.fillOrder >= item.fillOrder) addFailure("items must be sorted by fillOrder ascending");
  previousItem = item;
}

const ownerCounts = new Map();
for (const item of items) {
  const owner = item.owner || "release-owner";
  if (!ownerCounts.has(owner)) {
    ownerCounts.set(owner, {
      owner,
      total: 0,
      filled: 0,
      placeholder: 0,
      missing: 0,
      optionalEmpty: 0,
      blockers: 0,
      secretKeys: 0,
      safeDefaultAvailable: 0,
      requiresOwnerInput: 0,
    });
  }
  const entry = ownerCounts.get(owner);
  entry.total += 1;
  if (item.status === "FILLED_REDACTED") entry.filled += 1;
  if (item.status === "PLACEHOLDER") entry.placeholder += 1;
  if (item.status === "MISSING") entry.missing += 1;
  if (item.status === "OPTIONAL_EMPTY") entry.optionalEmpty += 1;
  if (item.blocker === true) entry.blockers += 1;
  if (item.secret === true) entry.secretKeys += 1;
  if (item.safeDefaultAvailable === true) entry.safeDefaultAvailable += 1;
  if (item.requiresOwnerInput === true) entry.requiresOwnerInput += 1;
}

let previousOwner = null;
for (const owner of byOwner) {
  const expected = ownerCounts.get(owner.owner);
  const label = `owner ${ownerKey(owner)}`;
  if (!expected) {
    addFailure(`${label} must have readiness items`);
    continue;
  }
  for (const field of ["total", "filled", "placeholder", "missing", "optionalEmpty", "blockers", "secretKeys", "safeDefaultAvailable", "requiresOwnerInput"]) {
    if (owner[field] !== expected[field]) addFailure(`${label}.${field} must match readiness items`);
  }
  if (previousOwner) {
    const sorted = previousOwner.blockers - owner.blockers
      || previousOwner.placeholder - owner.placeholder
      || previousOwner.missing - owner.missing
      || owner.owner.localeCompare(previousOwner.owner);
    if (sorted < 0) addFailure("byOwner must be sorted by blockers desc, placeholder desc, missing desc, owner asc");
  }
  previousOwner = owner;
}

for (const [name, text] of [["csv", readinessCsv], ["markdown", readinessMarkdown], ["json", JSON.stringify(readiness)]]) {
  for (const pattern of forbiddenValuePatterns) {
    if (pattern.test(text)) addFailure(`${name} must not expose concrete env assignments, placeholders, DSNs, or tokens`);
  }
}
if (!readinessCsv.startsWith("fillOrder,owner,owners,group,requirement,canonicalKey,status,required,secret,valueClass")) {
  addFailure("csv header must match redacted readiness schema");
}
if (!readinessMarkdown.includes("Concrete values are intentionally omitted")) {
  addFailure("markdown must state concrete values are intentionally omitted");
}

if (failures.length > 0) {
  throw new Error(`release env readiness redacted contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-env-readiness-redacted-contract] ok items=${items.length} blockers=${readiness.summary?.blockers ?? 0} owners=${byOwner.length}`);
