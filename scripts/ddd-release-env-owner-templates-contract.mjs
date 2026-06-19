#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const templatesPath = path.join(releaseDir, "release-env-owner-templates.json");
const templatesMarkdownPath = path.join(releaseDir, "release-env-owner-templates.md");
const templatesDir = path.join(releaseDir, "release-env-owner-templates");
const handoffPath = path.join(releaseDir, "release-env-owner-handoff.json");
const canonicalFillPath = path.join(releaseDir, "release-env-canonical-fill.json");

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env owner templates artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function readText(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env owner templates artifact: ${file}`);
  }
  return fs.readFileSync(file, "utf8");
}

function safeOwnerFileName(owner, queueOrder) {
  return `${String(queueOrder).padStart(2, "0")}-${String(owner || "").replace(/[^a-zA-Z0-9._-]/g, "-")}.env`;
}

function parseEnvEntries(text) {
  return [...text.matchAll(/^([A-Z][A-Z0-9_]*)=(.*)$/gm)].map((match) => ({
    key: match[1],
    value: match[2],
  }));
}

function sameStringSet(left = [], right = []) {
  return JSON.stringify([...new Set(left)].sort()) === JSON.stringify([...new Set(right)].sort());
}

function hasDuplicates(values = []) {
  return new Set(values).size !== values.length;
}

function portablePath(value) {
  return String(value || "").replaceAll("\\", "/");
}

const templates = readJson(templatesPath);
const templatesMarkdown = readText(templatesMarkdownPath);
const handoff = readJson(handoffPath);
const canonicalFill = readJson(canonicalFillPath);
const failures = [];

function addFailure(message) {
  failures.push(message);
}

const owners = Array.isArray(templates.owners) ? templates.owners : [];
const handoffOwners = Array.isArray(handoff.owners) ? handoff.owners : [];
const handoffByOwner = new Map(handoffOwners.map((owner) => [owner.owner, owner]));
const canonicalByKey = new Map((canonicalFill.items || []).map((item) => [item.canonicalKey, item]));

if (!Array.isArray(templates.owners)) addFailure("owners must be an array");
if (templates.ownerCount !== owners.length) addFailure("ownerCount must match owners length");
if (templates.ownerCount !== handoff.ownerCount) addFailure("ownerCount must match owner handoff");
if (templates.canonicalFillItemCount !== handoff.canonicalFillItemCount) addFailure("canonicalFillItemCount must match owner handoff");
if (templates.canonicalFillItemCount !== canonicalFill.canonicalFillItemCount) addFailure("canonicalFillItemCount must match canonical fill");
if (templates.envFile !== handoff.envFile || templates.envFile !== canonicalFill.envFile) addFailure("envFile must match handoff and canonical fill");
if (portablePath(templates.templateDir) !== "artifacts/ddd/release/release-env-owner-templates") addFailure("templateDir must use the expected release owner template directory");
if (!fs.existsSync(templatesDir) || !fs.statSync(templatesDir).isDirectory()) addFailure("template directory must exist");
if (!sameStringSet(owners.map((owner) => owner.owner), handoffOwners.map((owner) => owner.owner))) addFailure("template owners must match owner handoff owners");
if (hasDuplicates(owners.map((owner) => owner.owner))) addFailure("owner names must be unique");
if (hasDuplicates(owners.map((owner) => owner.fileName))) addFailure("owner fileName values must be unique");
if (hasDuplicates(owners.map((owner) => owner.templatePath))) addFailure("owner templatePath values must be unique");

const expectedSecretCount = owners.reduce((sum, owner) => sum + (owner.secretCanonicalKeyCount || 0), 0);
const expectedSafeCount = owners.reduce((sum, owner) => sum + (owner.safeToPreFillCanonicalKeyCount || 0), 0);
if (templates.secretCanonicalKeyCount !== expectedSecretCount) addFailure("secretCanonicalKeyCount must match owner totals");
if (templates.safeToPreFillCanonicalKeyCount !== expectedSafeCount) addFailure("safeToPreFillCanonicalKeyCount must match owner totals");

for (const owner of owners) {
  const label = `owner ${owner.owner || "unknown"}`;
  const handoffOwner = handoffByOwner.get(owner.owner);
  if (!handoffOwner) {
    addFailure(`${label} must exist in owner handoff`);
    continue;
  }
  const expectedFileName = safeOwnerFileName(owner.owner, owner.queueOrder);
  const expectedPath = `artifacts/ddd/release/release-env-owner-templates/${expectedFileName}`;
  if (owner.fileName !== expectedFileName) addFailure(`${label}.fileName must match queue order and owner`);
  if (portablePath(owner.templatePath) !== expectedPath) addFailure(`${label}.templatePath must stay inside owner template directory`);
  if (owner.fileName.includes("/") || owner.fileName.includes("..")) addFailure(`${label}.fileName must not contain path traversal`);
  if (owner.templatePath.includes("..") || path.isAbsolute(owner.templatePath)) addFailure(`${label}.templatePath must be relative and traversal-free`);
  for (const field of ["queueOrder", "queueStatus", "canExecute", "canonicalFillItemCount", "secretCanonicalKeyCount", "safeToPreFillCanonicalKeyCount"]) {
    if (owner[field] !== handoffOwner[field]) addFailure(`${label}.${field} must match owner handoff`);
  }
  if (!sameStringSet(owner.canonicalKeys || [], handoffOwner.canonicalKeys || [])) addFailure(`${label}.canonicalKeys must match owner handoff`);
  if (!sameStringSet(owner.secretCanonicalKeys || [], handoffOwner.secretCanonicalKeys || [])) addFailure(`${label}.secretCanonicalKeys must match owner handoff`);
  if (!sameStringSet(owner.safeToPreFillCanonicalKeys || [], handoffOwner.safeToPreFillCanonicalKeys || [])) addFailure(`${label}.safeToPreFillCanonicalKeys must match owner handoff`);
  if (!sameStringSet(owner.postFillCommands || [], handoffOwner.postFillCommands || [])) addFailure(`${label}.postFillCommands must match owner handoff`);
  if ((owner.postFillCommands || [])[0] !== "node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env") {
    addFailure(`${label}.postFillCommands must start with owner template merge`);
  }
  if (!(owner.postFillCommands || []).some((command) => command === `DDD_RELEASE_ENV_FILE=${templates.envFile || ".env.release.local"} node scripts/ddd-release-env-file-lint.mjs`)) {
    addFailure(`${label}.postFillCommands must run env file lint against the explicit release env file`);
  }
  if (!(owner.postFillCommands || []).some((command) => command === "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh")) {
    addFailure(`${label}.postFillCommands must finish with enforced env readiness gate`);
  }

  const templateFile = path.join(templatesDir, owner.fileName);
  if (!fs.existsSync(templateFile)) {
    addFailure(`${label} template file must exist`);
    continue;
  }
  const templateText = readText(templateFile);
  if (!templateText.includes(`# Owner: ${owner.owner}`)) addFailure(`${label} template must identify owner`);
  if (!templateText.includes("# Do not commit populated secrets.")) addFailure(`${label} template must warn against committing secrets`);
  const entries = parseEnvEntries(templateText);
  const entryKeys = entries.map((entry) => entry.key);
  if (hasDuplicates(entryKeys)) addFailure(`${label} template env keys must be unique`);
  if (!sameStringSet(entryKeys, owner.canonicalKeys || [])) addFailure(`${label} template env keys must match owner canonicalKeys`);
  for (const entry of entries) {
    const canonical = canonicalByKey.get(entry.key);
    if (!canonical) {
      addFailure(`${label}.${entry.key} must exist in canonical fill`);
      continue;
    }
    if (canonical.owner !== owner.owner) addFailure(`${label}.${entry.key} canonical owner must match template owner`);
    const expectedValues = Array.isArray(canonical.validation?.expectedValues) ? canonical.validation.expectedValues : [];
    const allowedValues = new Set(["__REQUIRED__"]);
    if (canonical.required === false) allowedValues.add("");
    if (canonical.safeToPreFill === true && expectedValues.length > 0) allowedValues.add(String(expectedValues[0]));
    if (!allowedValues.has(entry.value)) {
      addFailure(`${label}.${entry.key} template value must be __REQUIRED__, empty optional, or an allowed safe prefill`);
    }
    if (canonical.secret === true && entry.value !== "__REQUIRED__" && entry.value !== "") {
      addFailure(`${label}.${entry.key} secret template value must not be prefilled`);
    }
  }
}

const expectedFileNames = owners.map((owner) => owner.fileName).sort();
const actualFileNames = fs.existsSync(templatesDir)
  ? fs.readdirSync(templatesDir).filter((file) => file.endsWith(".env")).sort()
  : [];
if (!sameStringSet(actualFileNames, expectedFileNames)) addFailure("owner template directory files must match owner fileName values");
if (!templatesMarkdown.includes("Each owner template is intentionally scoped to one owner")) addFailure("templates markdown must explain owner scoping");
for (const owner of owners) {
  if (!templatesMarkdown.replaceAll("\\", "/").includes(`\`${portablePath(owner.templatePath)}\``)) addFailure(`templates markdown must include ${owner.owner} template path`);
  for (const command of owner.postFillCommands || []) {
    if (!templatesMarkdown.includes(`\`${command}\``)) addFailure(`templates markdown must include ${owner.owner} post-fill command ${command}`);
  }
}

if (failures.length > 0) {
  throw new Error(`release env owner templates contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-env-owner-templates-contract] ok owners=${owners.length} canonicalKeys=${templates.canonicalFillItemCount}`);
