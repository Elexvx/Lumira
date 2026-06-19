#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const packetPath = path.join(releaseDir, "release-env-owner-input-packet.json");
const packetCsvPath = path.join(releaseDir, "release-env-owner-input-packet.csv");
const packetMarkdownPath = path.join(releaseDir, "release-env-owner-input-packet.md");
const ownerPacketDir = path.join(releaseDir, "release-env-owner-input-packet");
const readinessPath = path.join(releaseDir, "release-env-readiness-redacted.json");
const outputFile = process.env.DDD_RELEASE_ENV_OWNER_INPUT_PACKET_CONTRACT_REPORT
  ? path.resolve(process.env.DDD_RELEASE_ENV_OWNER_INPUT_PACKET_CONTRACT_REPORT)
  : path.join(releaseDir, "release-env-owner-input-packet-contract.json");
const failures = [];

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env owner input packet artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function readText(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env owner input packet artifact: ${file}`);
  }
  return fs.readFileSync(file, "utf8");
}

function addFailure(message) {
  failures.push(message);
}

function sameStringSet(left = [], right = []) {
  return JSON.stringify([...new Set(left)].sort()) === JSON.stringify([...new Set(right)].sort());
}

function hasDuplicates(values = []) {
  return new Set(values).size !== values.length;
}

function ownerStats(items) {
  return [...items.reduce((map, item) => {
    const ownerName = item.owner || "release-owner";
    if (!map.has(ownerName)) {
      map.set(ownerName, {
        owner: ownerName,
        totalInputs: 0,
        secretInputs: 0,
        productionEndpointInputs: 0,
        ownerProductionValueInputs: 0,
        keys: [],
        reasons: new Set(),
      });
    }
    const owner = map.get(ownerName);
    owner.totalInputs += 1;
    if (item.secret === true) owner.secretInputs += 1;
    if (item.ownerInputReason === "production-endpoint") owner.productionEndpointInputs += 1;
    if (item.ownerInputReason === "owner-production-value") owner.ownerProductionValueInputs += 1;
    owner.keys.push(item.canonicalKey);
    owner.reasons.add(item.ownerInputReason);
    return map;
  }, new Map()).values()].map((owner) => ({
    ...owner,
    keys: owner.keys.sort(),
    reasons: [...owner.reasons].sort(),
  }));
}

function expectedOwnerFileName(owner, index) {
  return `${String(index + 1).padStart(2, "0")}-${owner.owner}`;
}

const packet = readJson(packetPath);
const readiness = readJson(readinessPath);
const packetCsv = readText(packetCsvPath);
const packetMarkdown = readText(packetMarkdownPath);
const items = Array.isArray(packet.items) ? packet.items : [];
const owners = Array.isArray(packet.owners) ? packet.owners : [];
const readinessRequiredInputs = (readiness.items || []).filter((item) => item.requiresOwnerInput === true);
const readinessByKey = new Map(readinessRequiredInputs.map((item) => [item.canonicalKey, item]));
const forbiddenValuePatterns = [
  /__REQUIRED__/,
  /\b(?!(?:DDD_RELEASE_ENV_FILE|DDD_RELEASE_ENV_READINESS_ENFORCE|DDD_FINAL_GO_NO_GO_ENFORCE)\b)[A-Z][A-Z0-9_]*=/,
  /\bDDD_RELEASE_ENV_FILE=(?!<release-env-file>)[^\s`|]+/,
  /\b(?:mysql|postgres|jdbc|redis|amqp):\/\/[^\s,;")]+/i,
  /\b(?:sk-[A-Za-z0-9_-]{12,}|eyJ[A-Za-z0-9_-]{20,})\b/,
];
const requiredReceiptCommands = [
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
  "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-readiness-summary.mjs",
  "node scripts/ddd-release-config-owner-input-reconciliation.mjs",
  "DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts/ddd/release/release-preflight-gate.sh",
  "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
];
const requiredReceiptCriteria = {
  releaseEnvReadinessStatus: "PASS",
  releaseEnvReadinessBlockers: 0,
  releaseEnvReadinessPlaceholders: 0,
  releaseEnvReadinessMissing: 0,
  configOwnerInputReconciliationStatus: "PASS",
  configOwnerInputReconciliationUnmappedKeys: 0,
};

if (packet.redacted !== true) addFailure("redacted must be true");
if (!String(packet.valuePolicy || "").includes("No concrete environment values are emitted")) addFailure("valuePolicy must forbid concrete values");
if (!Array.isArray(packet.items)) addFailure("items must be an array");
if (!Array.isArray(packet.owners)) addFailure("owners must be an array");
if (!sameStringSet(items.map((item) => item.canonicalKey), readinessRequiredInputs.map((item) => item.canonicalKey))) {
  addFailure("packet items must match readiness requiresOwnerInput keys");
}
if (hasDuplicates(items.map((item) => item.canonicalKey))) addFailure("packet canonical keys must be unique");
if (packet.summary?.requiredOwnerInputs !== items.length) addFailure("summary.requiredOwnerInputs must match item count");
if (packet.summary?.ownerCount !== owners.length) addFailure("summary.ownerCount must match owners length");
if (packet.summary?.secretInputs !== items.filter((item) => item.secret === true).length) addFailure("summary.secretInputs must match items");
if (packet.summary?.productionEndpointInputs !== items.filter((item) => item.ownerInputReason === "production-endpoint").length) addFailure("summary.productionEndpointInputs must match items");
if (packet.summary?.ownerProductionValueInputs !== items.filter((item) => item.ownerInputReason === "owner-production-value").length) addFailure("summary.ownerProductionValueInputs must match items");
if (packet.summary?.blockingSafeDefaultAvailable !== readiness.summary?.blockingSafeDefaultAvailable) addFailure("summary.blockingSafeDefaultAvailable must match readiness");
if (packet.summary?.safeDefaultsExhausted !== readiness.summary?.safeDefaultsExhausted) addFailure("summary.safeDefaultsExhausted must match readiness");
if (packet.postCollectionReceipt?.redacted !== true) addFailure("postCollectionReceipt.redacted must be true");
if (!String(packet.postCollectionReceipt?.purpose || "").includes("without exposing concrete values")) {
  addFailure("postCollectionReceipt.purpose must forbid concrete values");
}
if (!Array.isArray(packet.postCollectionReceipt?.commands)) {
  addFailure("postCollectionReceipt.commands must be an array");
} else {
  for (const command of requiredReceiptCommands) {
    if (!packet.postCollectionReceipt.commands.includes(command)) {
      addFailure(`postCollectionReceipt.commands must include ${command}`);
    }
  }
  for (const command of packet.postCollectionReceipt.commands) {
    if (!String(command).includes("<release-env-file>") && String(command).includes("DDD_RELEASE_ENV_FILE=")) {
      addFailure("postCollectionReceipt.commands must redact DDD_RELEASE_ENV_FILE");
    }
  }
}
for (const [field, expected] of Object.entries(requiredReceiptCriteria)) {
  if (JSON.stringify(packet.postCollectionReceipt?.passCriteria?.[field]) !== JSON.stringify(expected)) {
    addFailure(`postCollectionReceipt.passCriteria.${field} must be ${expected}`);
  }
}

for (const [index, item] of items.entries()) {
  const label = `item ${item.canonicalKey || index + 1}`;
  const expected = readinessByKey.get(item.canonicalKey);
  if (!expected) {
    addFailure(`${label} must exist as readiness requiresOwnerInput`);
    continue;
  }
  if (item.inputOrder !== index + 1) addFailure(`${label}.inputOrder must be contiguous`);
  for (const field of ["owner", "group", "requirement", "status", "valueClass", "ownerInputReason", "secret", "safeDefaultAvailable", "requiresOwnerInput", "required"]) {
    if (JSON.stringify(item[field]) !== JSON.stringify(expected[field])) addFailure(`${label}.${field} must match readiness`);
  }
  if (!sameStringSet(item.aliases || [], expected.aliases || [])) addFailure(`${label}.aliases must match readiness`);
  if (item.requiresOwnerInput !== true) addFailure(`${label}.requiresOwnerInput must be true`);
  if (item.safeDefaultAvailable !== false) addFailure(`${label}.safeDefaultAvailable must be false for owner-input packet`);
  if (!item.collectionGuidance || typeof item.collectionGuidance !== "string") addFailure(`${label}.collectionGuidance is required`);
  if (JSON.stringify(item).includes("__REQUIRED__")) addFailure(`${label} must not contain template placeholder values`);
}

const expectedOwners = ownerStats(items);
if (!sameStringSet(owners.map((owner) => owner.owner), expectedOwners.map((owner) => owner.owner))) {
  addFailure("owners must match packet item owners");
}
for (const expected of expectedOwners) {
  const owner = owners.find((entry) => entry.owner === expected.owner);
  if (!owner) continue;
  const expectedIndex = owners.findIndex((entry) => entry.owner === owner.owner);
  const expectedBaseName = expectedOwnerFileName(owner, expectedIndex);
  if (owner.fileName !== expectedBaseName) addFailure(`owner ${owner.owner}.fileName must match owner packet order`);
  const expectedPacketPath = path.posix.join("artifacts", "ddd", "release", "release-env-owner-input-packet", `${expectedBaseName}.json`);
  const expectedPacketMarkdownPath = path.posix.join("artifacts", "ddd", "release", "release-env-owner-input-packet", `${expectedBaseName}.md`);
  if (owner.packetPath !== expectedPacketPath) addFailure(`owner ${owner.owner}.packetPath must stay in owner input packet directory`);
  if (owner.packetMarkdownPath !== expectedPacketMarkdownPath) addFailure(`owner ${owner.owner}.packetMarkdownPath must stay in owner input packet directory`);
  for (const field of ["totalInputs", "secretInputs", "productionEndpointInputs", "ownerProductionValueInputs"]) {
    if (owner[field] !== expected[field]) addFailure(`owner ${owner.owner}.${field} must match items`);
  }
  if (!sameStringSet(owner.keys || [], expected.keys)) addFailure(`owner ${owner.owner}.keys must match items`);
  if (!sameStringSet(owner.reasons || [], expected.reasons)) addFailure(`owner ${owner.owner}.reasons must match items`);
  if (!String(owner.handoffPath || "").startsWith(path.posix.join("artifacts", "ddd", "release", "release-env-owner-handoff-redacted"))) {
    addFailure(`owner ${owner.owner}.handoffPath must stay in redacted handoff directory`);
  }
}

const expectedOwnerFiles = owners.flatMap((owner) => [`${owner.fileName}.json`, `${owner.fileName}.md`]).sort();
const actualOwnerFiles = fs.existsSync(ownerPacketDir)
  ? fs.readdirSync(ownerPacketDir).filter((file) => file.endsWith(".json") || file.endsWith(".md")).sort()
  : [];
if (!fs.existsSync(ownerPacketDir) || !fs.statSync(ownerPacketDir).isDirectory()) addFailure("owner packet directory must exist");
if (!sameStringSet(actualOwnerFiles, expectedOwnerFiles)) addFailure("owner packet directory files must match owner fileName values");
for (const owner of owners) {
  const label = `owner ${owner.owner}`;
  const ownerJsonPath = path.join(ownerPacketDir, `${owner.fileName}.json`);
  const ownerMarkdownPath = path.join(ownerPacketDir, `${owner.fileName}.md`);
  if (!fs.existsSync(ownerJsonPath)) {
    addFailure(`${label} json packet file must exist`);
  } else {
    const ownerPacket = readJson(ownerJsonPath);
    const ownerItems = items.filter((item) => item.owner === owner.owner);
    if (ownerPacket.redacted !== true) addFailure(`${label} json packet must be redacted`);
    if (ownerPacket.owner !== owner.owner) addFailure(`${label} json owner must match`);
    if (ownerPacket.summary?.totalInputs !== ownerItems.length) addFailure(`${label} json totalInputs must match owner items`);
    if (!sameStringSet((ownerPacket.items || []).map((item) => item.canonicalKey), ownerItems.map((item) => item.canonicalKey))) {
      addFailure(`${label} json items must match owner keys`);
    }
    if ((ownerPacket.items || []).some((item) => item.owner !== owner.owner)) addFailure(`${label} json must contain only owner-owned inputs`);
    if (JSON.stringify(ownerPacket.postCollectionReceipt || {}) !== JSON.stringify(packet.postCollectionReceipt || {})) {
      addFailure(`${label} json postCollectionReceipt must match packet receipt`);
    }
    for (const pattern of forbiddenValuePatterns) {
      if (pattern.test(JSON.stringify(ownerPacket))) addFailure(`${label} json must not expose concrete env assignments, placeholders, DSNs, or tokens`);
    }
  }
  if (!fs.existsSync(ownerMarkdownPath)) {
    addFailure(`${label} markdown packet file must exist`);
  } else {
    const ownerMarkdown = readText(ownerMarkdownPath);
    if (!ownerMarkdown.includes(`DDD Release Env Owner Input Packet: ${owner.owner}`)) addFailure(`${label} markdown must identify owner`);
    for (const key of owner.keys || []) {
      if (!ownerMarkdown.includes(`\`${key}\``)) addFailure(`${label} markdown must include ${key}`);
    }
    if (!ownerMarkdown.includes("Receipt Gate")) addFailure(`${label} markdown must include receipt gate`);
    for (const command of requiredReceiptCommands) {
      if (!ownerMarkdown.includes(command)) addFailure(`${label} markdown must include receipt command ${command}`);
    }
    for (const pattern of forbiddenValuePatterns) {
      if (pattern.test(ownerMarkdown)) addFailure(`${label} markdown must not expose concrete env assignments, placeholders, DSNs, or tokens`);
    }
  }
}

for (const command of packet.validationCommands || []) {
  if (!String(command).includes("<release-env-file>") && String(command).includes("DDD_RELEASE_ENV_FILE=")) {
    addFailure("validationCommands must redact DDD_RELEASE_ENV_FILE");
  }
}
for (const [name, text] of [["json", JSON.stringify(packet)], ["csv", packetCsv], ["markdown", packetMarkdown]]) {
  for (const pattern of forbiddenValuePatterns) {
    if (pattern.test(text)) addFailure(`${name} must not expose concrete env assignments, placeholders, DSNs, or tokens`);
  }
}
if (!packetCsv.startsWith("inputOrder,owner,canonicalKey,aliases,group,requirement,status,valueClass,ownerInputReason")) {
  addFailure("csv header must match owner input packet schema");
}
if (!packetMarkdown.includes("DDD Release Env Owner Input Packet")) addFailure("markdown title is required");
if (!packetMarkdown.includes("Concrete values are intentionally omitted")) addFailure("markdown must state concrete values are omitted");
if (!packetMarkdown.includes("Receipt Gate")) addFailure("markdown must include receipt gate");
for (const command of requiredReceiptCommands) {
  if (!packetMarkdown.includes(command)) addFailure(`markdown must include receipt command ${command}`);
}

const report = {
  generatedAt: new Date().toISOString(),
  status: failures.length === 0 ? "PASS" : "FAIL",
  redacted: true,
  contract: "ddd-release-env-owner-input-packet-contract",
  releaseDir,
  requiredOwnerInputs: items.length,
  ownerCount: owners.length,
  issueCount: failures.length,
  issues: failures,
  ownerPacketDir: path.join("artifacts", "ddd", "release", "release-env-owner-input-packet"),
  expectedOwnerFiles,
  actualOwnerFiles,
  checkedArtifacts: [
    "release-env-readiness-redacted.json",
    "release-env-owner-input-packet.json",
    "release-env-owner-input-packet.csv",
    "release-env-owner-input-packet.md",
    "release-env-owner-input-packet/*.json",
    "release-env-owner-input-packet/*.md",
  ],
};
fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(report, null, 2)}\n`);

if (failures.length > 0) {
  console.error(`[ddd-release-env-owner-input-packet-contract] wrote report to ${outputFile}`);
  throw new Error(`release env owner input packet contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-env-owner-input-packet-contract] ok inputs=${items.length} owners=${owners.length} report=${outputFile}`);
