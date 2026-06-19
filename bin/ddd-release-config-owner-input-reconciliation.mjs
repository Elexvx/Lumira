#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const configPath = process.env.DDD_RELEASE_CONFIG_REPORT || "artifacts/ddd/config/release-config-evidence.json";
const packetPath = path.join(releaseDir, "release-env-owner-input-packet.json");
const outputFile = process.env.DDD_RELEASE_CONFIG_OWNER_INPUT_RECONCILIATION_REPORT
  ? path.resolve(process.env.DDD_RELEASE_CONFIG_OWNER_INPUT_RECONCILIATION_REPORT)
  : path.join(releaseDir, "release-config-owner-input-reconciliation.json");
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function readJson(file, label) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing ${label}: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function uniqueSorted(values) {
  return [...new Set(values.filter(Boolean))].sort();
}

function withoutGeneratedAt(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return value;
  }
  const { generatedAt, ...rest } = value;
  return rest;
}

function stableGeneratedAt(outputPath, body) {
  if (!fs.existsSync(outputPath)) {
    return new Date().toISOString();
  }
  try {
    const existing = JSON.parse(fs.readFileSync(outputPath, "utf8"));
    if (JSON.stringify(withoutGeneratedAt(existing)) === JSON.stringify(body)) {
      return existing.generatedAt || new Date().toISOString();
    }
  } catch {
    // Fall through and regenerate the timestamp when the previous report is unreadable.
  }
  return new Date().toISOString();
}

const config = readJson(configPath, "release config evidence");
const packet = readJson(packetPath, "release env owner input packet");
const configBlockerDetails = Array.isArray(config.blockerDetails) ? config.blockerDetails : [];
const configPlaceholderDetails = configBlockerDetails.filter((detail) => detail?.blockedByPlaceholderKey === true);
const configPlaceholderKeys = uniqueSorted(configPlaceholderDetails.map((detail) => detail.matchedKey));
const ownerInputItems = Array.isArray(packet.items) ? packet.items : [];
const ownerInputKeyIndex = new Map();

for (const item of ownerInputItems) {
  const keys = uniqueSorted([item.canonicalKey, ...(item.aliases || [])]);
  for (const key of keys) {
    ownerInputKeyIndex.set(key, {
      canonicalKey: item.canonicalKey,
      owner: item.owner,
      ownerInputReason: item.ownerInputReason,
      valueClass: item.valueClass,
    });
  }
}

if (packet.redacted !== true) addFailure("owner input packet must be redacted");
if (!Array.isArray(packet.items)) addFailure("owner input packet items must be an array");
if (!Array.isArray(config.blockerDetails)) addFailure("release config evidence blockerDetails must be an array");
if (Number(config.summary?.releaseConfigBlockersFromPlaceholders || 0) !== configPlaceholderDetails.length) {
  addFailure("release config placeholder blocker summary must match blockerDetails");
}

const mappedConfigPlaceholderKeys = [];
const unmappedConfigPlaceholderKeys = [];
for (const key of configPlaceholderKeys) {
  const ownerInput = ownerInputKeyIndex.get(key);
  if (ownerInput) {
    mappedConfigPlaceholderKeys.push({
      key,
      owner: ownerInput.owner,
      canonicalKey: ownerInput.canonicalKey,
      ownerInputReason: ownerInput.ownerInputReason,
      valueClass: ownerInput.valueClass,
      duplicateConfigBlockers: configPlaceholderDetails.filter((detail) => detail.matchedKey === key).length,
    });
  } else {
    unmappedConfigPlaceholderKeys.push(key);
  }
}

const configPlaceholderKeySet = new Set(configPlaceholderKeys);
const ownerInputsWithoutConfigPlaceholder = ownerInputItems
  .filter((item) => !uniqueSorted([item.canonicalKey, ...(item.aliases || [])]).some((key) => configPlaceholderKeySet.has(key)))
  .map((item) => ({
    canonicalKey: item.canonicalKey,
    owner: item.owner,
    ownerInputReason: item.ownerInputReason,
    valueClass: item.valueClass,
  }))
  .sort((left, right) => left.canonicalKey.localeCompare(right.canonicalKey));

if (unmappedConfigPlaceholderKeys.length > 0) {
  addFailure(`config placeholder keys must be covered by owner input packet: ${unmappedConfigPlaceholderKeys.join(",")}`);
}

const reportBody = {
  status: failures.length === 0 ? "PASS" : "FAIL",
  redacted: true,
  contract: "ddd-release-config-owner-input-reconciliation",
  configArtifact: path.relative(process.cwd(), path.resolve(configPath)),
  ownerInputPacket: path.relative(process.cwd(), path.resolve(packetPath)),
  summary: {
    configPlaceholderBlockers: configPlaceholderDetails.length,
    uniqueConfigPlaceholderKeys: configPlaceholderKeys.length,
    ownerInputKeys: ownerInputItems.length,
    mappedConfigPlaceholderKeys: mappedConfigPlaceholderKeys.length,
    unmappedConfigPlaceholderKeys: unmappedConfigPlaceholderKeys.length,
    duplicateConfigPlaceholderBlockers: configPlaceholderDetails.length - configPlaceholderKeys.length,
    ownerInputsWithoutConfigPlaceholder: ownerInputsWithoutConfigPlaceholder.length,
  },
  mappedConfigPlaceholderKeys,
  unmappedConfigPlaceholderKeys,
  ownerInputsWithoutConfigPlaceholder,
  issueCount: failures.length,
  issues: failures,
};
const report = {
  generatedAt: stableGeneratedAt(outputFile, reportBody),
  ...reportBody,
};

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(report, null, 2)}\n`);

if (failures.length > 0) {
  console.error(`[ddd-release-config-owner-input-reconciliation] wrote report to ${outputFile}`);
  throw new Error(`release config owner input reconciliation failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-config-owner-input-reconciliation] ok configPlaceholderKeys=${configPlaceholderKeys.length} ownerInputs=${ownerInputItems.length} report=${outputFile}`);
