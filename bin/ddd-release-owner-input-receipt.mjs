#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { validateReleaseOwnerInputReceipt } from "./ddd-release-owner-input-receipt-contract.mjs";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const readinessPath = path.join(releaseDir, "release-env-readiness-redacted.json");
const packetPath = path.join(releaseDir, "release-env-owner-input-packet.json");
const reconciliationPath = path.join(releaseDir, "release-config-owner-input-reconciliation.json");
const outputFile = process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_REPORT
  ? path.resolve(process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_REPORT)
  : path.join(releaseDir, "release-owner-input-receipt.json");
const markdownOutputFile = process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_MARKDOWN
  ? path.resolve(process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_MARKDOWN)
  : path.join(releaseDir, "release-owner-input-receipt.md");
const csvOutputFile = process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_CSV
  ? path.resolve(process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_CSV)
  : path.join(releaseDir, "release-owner-input-receipt.csv");
const itemCsvOutputFile = process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_ITEMS_CSV
  ? path.resolve(process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_ITEMS_CSV)
  : path.join(releaseDir, "release-owner-input-receipt-items.csv");
const itemMarkdownOutputFile = process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_ITEMS_MARKDOWN
  ? path.resolve(process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_ITEMS_MARKDOWN)
  : path.join(releaseDir, "release-owner-input-receipt-items.md");

function readJson(file, label) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing ${label}: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
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
    // Regenerate the timestamp when the previous report is unreadable.
  }
  return new Date().toISOString();
}

function ownerReceipts(packet, readiness) {
  const readinessByOwner = new Map((readiness.byOwner || readiness.owners || []).map((owner) => [owner.owner, owner]));
  return (packet.owners || []).map((owner) => {
    const observed = readinessByOwner.get(owner.owner) || {};
    const remainingPlaceholders = Number(observed.placeholders || observed.placeholder || 0);
    const remainingMissing = Number(observed.missing || 0);
    return {
      owner: owner.owner,
      requiredOwnerInputs: Number(owner.totalInputs || 0),
      secretInputs: Number(owner.secretInputs || 0),
      productionEndpointInputs: Number(owner.productionEndpointInputs || 0),
      ownerProductionValueInputs: Number(owner.ownerProductionValueInputs || 0),
      remainingPlaceholders,
      remainingMissing,
      ready: remainingPlaceholders === 0 && remainingMissing === 0,
      packetPath: owner.packetPath,
      handoffPath: owner.handoffPath,
    };
  });
}

function itemReceipts(packet, owners) {
  const ownersByName = new Map(owners.map((owner) => [owner.owner, owner]));
  return (packet.items || []).map((item) => {
    const owner = ownersByName.get(item.owner) || {};
    return {
      inputOrder: Number(item.inputOrder || 0),
      fillOrder: Number(item.fillOrder || 0),
      owner: item.owner || "unknown",
      ownerReady: owner.ready === true,
      canonicalKey: item.canonicalKey || "",
      aliases: item.aliases || [],
      group: item.group || "",
      requirement: item.requirement || "",
      status: item.status || "UNKNOWN",
      valueClass: item.valueClass || "",
      ownerInputReason: item.ownerInputReason || "",
      secret: item.secret === true,
      requiresOwnerInput: item.requiresOwnerInput === true,
      required: item.required === true,
      httpsRequired: item.validation?.https === true,
      nonLocalRequired: item.validation?.nonLocal === true,
      minLength: item.validation?.minLength ?? "",
      safeDefaultAvailable: item.safeDefaultAvailable === true,
      packetPath: owner.packetPath || "",
      handoffPath: owner.handoffPath || "",
      collectionGuidance: item.collectionGuidance || "",
    };
  }).sort((left, right) => left.inputOrder - right.inputOrder || left.owner.localeCompare(right.owner));
}

function csvCell(value) {
  const text = Array.isArray(value) ? value.join(";") : String(value ?? "");
  if (!/[",\n\r;]/.test(text)) {
    return text;
  }
  return `"${text.replace(/"/g, '""')}"`;
}

function buildCriteria(observed) {
  const expected = packet.postCollectionReceipt?.passCriteria || {
    releaseEnvReadinessStatus: "PASS",
    releaseEnvReadinessBlockers: 0,
    releaseEnvReadinessPlaceholders: 0,
    releaseEnvReadinessMissing: 0,
    configOwnerInputReconciliationStatus: "PASS",
    configOwnerInputReconciliationUnmappedKeys: 0,
  };
  const criteria = {};
  for (const [field, expectedValue] of Object.entries(expected)) {
    const actual = observed[field];
    criteria[field] = {
      expected: expectedValue,
      actual,
      met: JSON.stringify(actual) === JSON.stringify(expectedValue),
    };
  }
  return criteria;
}

function ownerCsv(receipt) {
  const rows = [[
    "owner",
    "ready",
    "requiredOwnerInputs",
    "secretInputs",
    "productionEndpointInputs",
    "ownerProductionValueInputs",
    "remainingPlaceholders",
    "remainingMissing",
    "packetPath",
    "handoffPath",
    "receiptStatus",
    "cutoverReady",
    "missingCriteria",
  ]];
  for (const owner of receipt.ownerReceipts || []) {
    rows.push([
      owner.owner,
      owner.ready,
      owner.requiredOwnerInputs,
      owner.secretInputs,
      owner.productionEndpointInputs,
      owner.ownerProductionValueInputs,
      owner.remainingPlaceholders,
      owner.remainingMissing,
      owner.packetPath,
      owner.handoffPath,
      receipt.status,
      receipt.cutoverReady,
      receipt.missingCriteria || [],
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function itemCsv(receipt) {
  const rows = [[
    "inputOrder",
    "fillOrder",
    "owner",
    "ownerReady",
    "canonicalKey",
    "aliases",
    "group",
    "requirement",
    "status",
    "valueClass",
    "ownerInputReason",
    "secret",
    "requiresOwnerInput",
    "required",
    "httpsRequired",
    "nonLocalRequired",
    "minLength",
    "safeDefaultAvailable",
    "packetPath",
    "handoffPath",
    "collectionGuidance",
  ]];
  for (const item of receipt.itemReceipts || []) {
    rows.push([
      item.inputOrder,
      item.fillOrder,
      item.owner,
      item.ownerReady,
      item.canonicalKey,
      item.aliases || [],
      item.group,
      item.requirement,
      item.status,
      item.valueClass,
      item.ownerInputReason,
      item.secret,
      item.requiresOwnerInput,
      item.required,
      item.httpsRequired,
      item.nonLocalRequired,
      item.minLength,
      item.safeDefaultAvailable,
      item.packetPath,
      item.handoffPath,
      item.collectionGuidance,
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function itemMarkdown(receipt) {
  const lines = [
    "# DDD Release Owner Input Receipt Items",
    "",
    `Generated at: ${receipt.generatedAt}`,
    `Status: ${receipt.status}`,
    `Cutover ready: ${receipt.cutoverReady}`,
    `Required owner inputs: ${receipt.summary.requiredOwnerInputs}`,
    `Item receipts: ${receipt.summary.itemReceiptCount || 0}`,
    "",
    "## Items",
    "",
  ];
  for (const item of receipt.itemReceipts || []) {
    lines.push(
      `- [ ] ${item.inputOrder}. \`${item.canonicalKey}\` owner=${item.owner}; status=${item.status}; class=${item.valueClass}; reason=${item.ownerInputReason}; secret=${item.secret}; https=${item.httpsRequired}; nonLocal=${item.nonLocalRequired}; aliases=${(item.aliases || []).join("|") || "none"}; packet=${item.packetPath || "n/a"}; handoff=${item.handoffPath || "n/a"}`,
    );
    if (item.collectionGuidance) {
      lines.push(`  - Collection: ${item.collectionGuidance}`);
    }
  }
  lines.push("", "Concrete values are intentionally omitted from this artifact.");
  return `${lines.join("\n")}\n`;
}

function markdown(receipt) {
  const lines = [
    "# DDD Release Owner Input Receipt",
    "",
    `Generated at: ${receipt.generatedAt}`,
    `Status: ${receipt.status}`,
    `Cutover ready: ${receipt.cutoverReady}`,
    `Required owner inputs: ${receipt.summary.requiredOwnerInputs}`,
    `Owners: ${receipt.summary.ownerCount}`,
    `Missing criteria: ${receipt.summary.missingCriteria}`,
    "",
    "## Criteria",
    "",
  ];
  for (const [field, criterion] of Object.entries(receipt.criteria || {})) {
    lines.push(`- ${field}: expected=${criterion.expected}; actual=${criterion.actual}; met=${criterion.met}`);
  }
  lines.push("", "## Owners", "");
  for (const owner of receipt.ownerReceipts || []) {
    lines.push(`- ${owner.owner}: ready=${owner.ready}; inputs=${owner.requiredOwnerInputs}; placeholders=${owner.remainingPlaceholders}; missing=${owner.remainingMissing}; packet=${owner.packetPath}`);
  }
  lines.push("", "## Validation Commands", "");
  for (const command of receipt.validationCommands || []) {
    lines.push(`- \`${command}\``);
  }
  lines.push("", "Concrete values are intentionally omitted from this artifact.");
  return `${lines.join("\n")}\n`;
}

const readiness = readJson(readinessPath, "release env readiness redacted");
const packet = readJson(packetPath, "release env owner input packet");
const reconciliation = readJson(reconciliationPath, "release config owner input reconciliation");
const observed = {
  releaseEnvReadinessStatus: readiness.status,
  releaseEnvReadinessBlockers: Number(readiness.summary?.blockers || 0),
  releaseEnvReadinessPlaceholders: Number(readiness.summary?.placeholders || 0),
  releaseEnvReadinessMissing: Number(readiness.summary?.missing || 0),
  configOwnerInputReconciliationStatus: reconciliation.status,
  configOwnerInputReconciliationUnmappedKeys: Number(reconciliation.summary?.unmappedConfigPlaceholderKeys || 0),
};
const criteria = buildCriteria(observed);
const missingCriteria = Object.entries(criteria)
  .filter(([, criterion]) => criterion.met !== true)
  .map(([field]) => field);
const owners = ownerReceipts(packet, readiness);
const items = itemReceipts(packet, owners);
const body = {
  status: missingCriteria.length === 0 ? "PASS" : "PENDING_OWNER_INPUT",
  redacted: true,
  contract: "ddd-release-owner-input-receipt",
  envFile: packet.envFile ? "<release-env-file>" : "<release-env-file>",
  sourceArtifacts: {
    ownerInputPacket: "artifacts/ddd/release/release-env-owner-input-packet.json",
    releaseEnvReadiness: "artifacts/ddd/release/release-env-readiness-redacted.json",
    configOwnerInputReconciliation: "artifacts/ddd/release/release-config-owner-input-reconciliation.json",
  },
  cutoverReady: missingCriteria.length === 0,
  summary: {
    requiredOwnerInputs: owners.reduce((sum, owner) => sum + owner.requiredOwnerInputs, 0),
    itemReceiptCount: items.length,
    ownerCount: owners.length,
    readyOwnerCount: owners.filter((owner) => owner.ready).length,
    pendingOwnerCount: owners.filter((owner) => !owner.ready).length,
    missingCriteria: missingCriteria.length,
    cutoverReady: missingCriteria.length === 0,
  },
  observed,
  criteria,
  missingCriteria,
  ownerReceipts: owners,
  itemReceipts: items,
  validationCommands: packet.postCollectionReceipt?.commands || packet.validationCommands || [],
};
const receipt = {
  generatedAt: stableGeneratedAt(outputFile, body),
  ...body,
};
const issues = validateReleaseOwnerInputReceipt(receipt);

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(receipt, null, 2)}\n`);
fs.writeFileSync(csvOutputFile, ownerCsv(receipt));
fs.writeFileSync(itemCsvOutputFile, itemCsv(receipt));
fs.writeFileSync(itemMarkdownOutputFile, itemMarkdown(receipt));
fs.writeFileSync(markdownOutputFile, markdown(receipt));

if (issues.length > 0) {
  throw new Error(`generated release owner input receipt failed contract: ${issues.join("; ")}`);
}

console.log(`[ddd-release-owner-input-receipt] status=${receipt.status} inputs=${receipt.summary.requiredOwnerInputs} owners=${receipt.summary.ownerCount} missingCriteria=${receipt.summary.missingCriteria} report=${outputFile}`);
