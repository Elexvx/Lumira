#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const handoffPath = path.join(releaseDir, "release-env-owner-handoff-redacted.json");
const handoffCsvPath = path.join(releaseDir, "release-env-owner-handoff-redacted.csv");
const handoffMarkdownPath = path.join(releaseDir, "release-env-owner-handoff-redacted.md");
const handoffDir = path.join(releaseDir, "release-env-owner-handoff-redacted");
const readinessPath = path.join(releaseDir, "release-env-readiness-redacted.json");
const outputFile = process.env.DDD_RELEASE_ENV_OWNER_HANDOFF_REDACTED_CONTRACT_REPORT
  ? path.resolve(process.env.DDD_RELEASE_ENV_OWNER_HANDOFF_REDACTED_CONTRACT_REPORT)
  : path.join(releaseDir, "release-env-owner-handoff-redacted-contract.json");
const failures = [];

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env owner handoff redacted artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function readText(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env owner handoff redacted artifact: ${file}`);
  }
  return fs.readFileSync(file, "utf8");
}

function addFailure(message) {
  failures.push(message);
}

function safeReadJson(file, label) {
  try {
    return readJson(file);
  } catch (error) {
    addFailure(`${label}: ${error.message}`);
    return {};
  }
}

function safeReadText(file, label) {
  try {
    return readText(file);
  } catch (error) {
    addFailure(`${label}: ${error.message}`);
    return "";
  }
}

let handoff = safeReadJson(handoffPath, "handoff json");
let readiness = safeReadJson(readinessPath, "readiness json");
let handoffCsv = safeReadText(handoffCsvPath, "handoff csv");
let handoffMarkdown = safeReadText(handoffMarkdownPath, "handoff markdown");

function hasDuplicates(values = []) {
  return new Set(values).size !== values.length;
}

function sameStringSet(left = [], right = []) {
  return JSON.stringify([...new Set(left)].sort()) === JSON.stringify([...new Set(right)].sort());
}

function safeOwnerFileName(owner, order) {
  return `${String(order).padStart(2, "0")}-${owner}.md`;
}

function ownerStats(items) {
  return {
    total: items.length,
    blockers: items.filter((item) => item.blocker === true).length,
    placeholders: items.filter((item) => item.status === "PLACEHOLDER").length,
    missing: items.filter((item) => item.status === "MISSING").length,
    optionalEmpty: items.filter((item) => item.status === "OPTIONAL_EMPTY").length,
    secretKeys: items.filter((item) => item.secret === true).length,
    safeDefaultAvailable: items.filter((item) => item.safeDefaultAvailable === true).length,
    requiresOwnerInput: items.filter((item) => item.requiresOwnerInput === true).length,
    ownerInputReasons: [...new Set(items.filter((item) => item.blocker === true).map((item) => item.ownerInputReason))].sort(),
    keys: items.map((item) => item.canonicalKey),
  };
}

const forbiddenValuePatterns = [
  /__REQUIRED__/,
  /\b(?!(?:DDD_RELEASE_ENV_FILE|DDD_RELEASE_ENV_READINESS_ENFORCE|DDD_FINAL_GO_NO_GO_ENFORCE)\b)[A-Z][A-Z0-9_]*=/,
  /\b(?:mysql|postgres|jdbc|redis|amqp):\/\/[^\s,;")]+/i,
  /\b(?:sk-[A-Za-z0-9_-]{12,}|eyJ[A-Za-z0-9_-]{20,})\b/,
];

const owners = Array.isArray(handoff.owners) ? handoff.owners : [];
const readinessItems = Array.isArray(readiness.items) ? readiness.items : [];
const requiredValidationCommands = [
  "node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env <release-env-file>",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-alias-sync.mjs",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
  "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
  "node scripts/ddd-release-readiness-summary.mjs",
  "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
];
const readinessOwnerMap = new Map();
for (const item of readinessItems) {
  const owner = item.owner || "release-owner";
  if (!readinessOwnerMap.has(owner)) readinessOwnerMap.set(owner, []);
  readinessOwnerMap.get(owner).push(item);
}

if (handoff.redacted !== true) addFailure("redacted must be true");
if (!String(handoff.valuePolicy || "").includes("No concrete environment values are emitted")) addFailure("valuePolicy must forbid concrete values");
if (!Array.isArray(handoff.owners)) addFailure("owners must be an array");
if (handoff.ownerCount !== owners.length) addFailure("ownerCount must match owners length");
if (handoff.ownerCount !== readiness.summary?.ownerCount) addFailure("ownerCount must match readiness ownerCount");
if (handoff.blockerOwnerCount !== owners.filter((owner) => owner.blockers > 0).length) addFailure("blockerOwnerCount must match owners with blockers");
if (handoff.templateDir !== path.posix.join("artifacts", "ddd", "release", "release-env-owner-handoff-redacted")) addFailure("templateDir must use the expected release handoff directory");
if (!sameStringSet(handoff.validationCommands || [], requiredValidationCommands)) addFailure("validationCommands must include the required release env validation commands");
if (!fs.existsSync(handoffDir) || !fs.statSync(handoffDir).isDirectory()) addFailure("handoff directory must exist");
if (!sameStringSet(owners.map((owner) => owner.owner), [...readinessOwnerMap.keys()])) addFailure("handoff owners must match readiness owners");
if (hasDuplicates(owners.map((owner) => owner.owner))) addFailure("owner names must be unique");
if (hasDuplicates(owners.map((owner) => owner.fileName))) addFailure("owner fileName values must be unique");
if (hasDuplicates(owners.map((owner) => owner.handoffPath))) addFailure("owner handoffPath values must be unique");

let previousOwner = null;
for (const [index, owner] of owners.entries()) {
  const label = `owner ${owner.owner || "unknown"}`;
  const expectedItems = readinessOwnerMap.get(owner.owner) || [];
  const expected = ownerStats(expectedItems);
  const expectedFileName = safeOwnerFileName(owner.owner, index + 1);
  const expectedPath = path.posix.join("artifacts", "ddd", "release", "release-env-owner-handoff-redacted", expectedFileName);
  if (owner.fileName !== expectedFileName) addFailure(`${label}.fileName must match queue order and owner`);
  if (owner.handoffPath !== expectedPath) addFailure(`${label}.handoffPath must stay inside release handoff directory`);
  if (owner.fileName.includes("/") || owner.fileName.includes("..")) addFailure(`${label}.fileName must not contain path traversal`);
  if (owner.handoffPath.includes("..") || path.isAbsolute(owner.handoffPath)) addFailure(`${label}.handoffPath must be relative and traversal-free`);
  for (const field of ["total", "blockers", "placeholders", "missing", "optionalEmpty", "secretKeys", "safeDefaultAvailable", "requiresOwnerInput"]) {
    if (owner[field] !== expected[field]) addFailure(`${label}.${field} must match readiness items`);
  }
  if (!sameStringSet(owner.ownerInputReasons || [], expected.ownerInputReasons || [])) addFailure(`${label}.ownerInputReasons must match readiness items`);
  if (!sameStringSet(owner.keys || [], expected.keys)) addFailure(`${label}.keys must match readiness item canonical keys`);
  if (hasDuplicates(owner.keys || [])) addFailure(`${label}.keys must be unique`);
  if (!sameStringSet(owner.postFillCommands || [], requiredValidationCommands)) addFailure(`${label}.postFillCommands must include the required validation commands`);
  const ownerFile = path.join(handoffDir, owner.fileName);
  if (!fs.existsSync(ownerFile)) {
    addFailure(`${label} handoff markdown file must exist`);
  } else {
    const ownerMarkdown = readText(ownerFile);
    if (!ownerMarkdown.includes(`DDD Release Env Owner Handoff: ${owner.owner}`)) addFailure(`${label} markdown must identify owner`);
    for (const key of owner.keys || []) {
      if (!ownerMarkdown.includes(`\`${key}\``)) addFailure(`${label} markdown must include ${key}`);
    }
    for (const command of requiredValidationCommands) {
      if (!ownerMarkdown.includes(`\`${command}\``)) addFailure(`${label} markdown must include validation command ${command}`);
    }
    for (const pattern of forbiddenValuePatterns) {
      if (pattern.test(ownerMarkdown)) addFailure(`${label} markdown must not expose concrete env assignments, placeholders, DSNs, or tokens`);
    }
  }
  if (previousOwner) {
    const sorted = previousOwner.blockers - owner.blockers
      || previousOwner.placeholders - owner.placeholders
      || owner.owner.localeCompare(previousOwner.owner);
    if (sorted < 0) addFailure("owners must be sorted by blockers desc, placeholders desc, owner asc");
  }
  previousOwner = owner;
}

const expectedFileNames = owners.map((owner) => owner.fileName).sort();
const actualFileNames = fs.existsSync(handoffDir)
  ? fs.readdirSync(handoffDir).filter((file) => file.endsWith(".md")).sort()
  : [];
if (!sameStringSet(actualFileNames, expectedFileNames)) addFailure("handoff directory markdown files must match owner fileName values");
if (!handoffCsv.startsWith("owner,handoffPath,ownerTotalKeys,ownerBlockers,ownerPlaceholders")) addFailure("csv header must match owner handoff redacted schema");
if (!handoffMarkdown.includes("DDD Release Env Owner Handoff Redacted")) addFailure("markdown summary title is required");
for (const command of requiredValidationCommands) {
  if (!handoffMarkdown.includes(`\`${command}\``)) addFailure(`markdown summary must include validation command ${command}`);
}
for (const owner of owners) {
  if (!handoffCsv.includes(`${owner.owner},${owner.handoffPath},`)) addFailure(`csv must include ${owner.owner} handoff rows`);
  if (!handoffMarkdown.includes(`file=${owner.handoffPath}`)) addFailure(`markdown summary must include ${owner.owner} handoff path`);
}
for (const [name, text] of [["csv", handoffCsv], ["markdown", handoffMarkdown], ["json", JSON.stringify(handoff)]]) {
  for (const pattern of forbiddenValuePatterns) {
    if (pattern.test(text)) addFailure(`${name} must not expose concrete env assignments, placeholders, DSNs, or tokens`);
  }
}

const report = {
  generatedAt: new Date().toISOString(),
  status: failures.length === 0 ? "PASS" : "FAIL",
  redacted: true,
  contract: "ddd-release-env-owner-handoff-redacted-contract",
  releaseDir,
  ownerCount: owners.length,
  blockerOwnerCount: Number(handoff.blockerOwnerCount || 0),
  expectedMarkdownFiles: expectedFileNames,
  actualMarkdownFiles: actualFileNames,
  issueCount: failures.length,
  issues: failures,
  checkedArtifacts: [
    "release-env-readiness-redacted.json",
    "release-env-owner-handoff-redacted.json",
    "release-env-owner-handoff-redacted.csv",
    "release-env-owner-handoff-redacted.md",
    "release-env-owner-handoff-redacted/*.md",
  ],
};
fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(report, null, 2)}\n`);

if (failures.length > 0) {
  console.error(`[ddd-release-env-owner-handoff-redacted-contract] wrote report to ${outputFile}`);
  throw new Error(`release env owner handoff redacted contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-env-owner-handoff-redacted-contract] ok owners=${owners.length} blockers=${handoff.blockerOwnerCount} report=${outputFile}`);
