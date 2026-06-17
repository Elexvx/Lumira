#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const receiptPath = process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_REPORT
  ? path.resolve(process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_REPORT)
  : path.join(releaseDir, "release-owner-input-receipt.json");
const failures = [];

const forbiddenValuePatterns = [
  /__REQUIRED__/,
  /\b(?!(?:DDD_RELEASE_ENV_FILE|DDD_RELEASE_ENV_READINESS_ENFORCE|DDD_FINAL_GO_NO_GO_ENFORCE)\b)[A-Z][A-Z0-9_]*=/,
  /\bDDD_RELEASE_ENV_FILE=(?!<release-env-file>)[^\s`|]+/,
  /\b(?:mysql|postgres|jdbc|redis|amqp):\/\/[^\s,;")]+/i,
  /\b(?:sk-[A-Za-z0-9_-]{12,}|eyJ[A-Za-z0-9_-]{20,})\b/,
];

const requiredStatusValues = new Set(["PASS", "PENDING_OWNER_INPUT"]);
const requiredCriteria = {
  releaseEnvReadinessStatus: "PASS",
  releaseEnvReadinessBlockers: 0,
  releaseEnvReadinessPlaceholders: 0,
  releaseEnvReadinessMissing: 0,
  configOwnerInputReconciliationStatus: "PASS",
  configOwnerInputReconciliationUnmappedKeys: 0,
};

function addFailure(message) {
  failures.push(message);
}

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release owner input receipt artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function hasDuplicates(values = []) {
  return new Set(values).size !== values.length;
}

export function validateReleaseOwnerInputReceipt(receipt) {
  const issues = [];
  const fail = (message) => issues.push(message);
  if (!receipt || typeof receipt !== "object" || Array.isArray(receipt)) {
    return ["release owner input receipt must be a JSON object"];
  }
  if (!requiredStatusValues.has(receipt.status)) {
    fail(`status must be PASS or PENDING_OWNER_INPUT, got ${receipt.status || "missing"}`);
  }
  if (receipt.redacted !== true) fail("redacted must be true");
  if (receipt.contract !== "ddd-release-owner-input-receipt") {
    fail("contract must be ddd-release-owner-input-receipt");
  }
  if (!receipt.generatedAt || Number.isNaN(Date.parse(receipt.generatedAt))) {
    fail("generatedAt must be an ISO timestamp");
  }
  if (receipt.envFile !== "<release-env-file>") fail("envFile must be redacted to <release-env-file>");
  if (receipt.cutoverReady !== (receipt.status === "PASS")) {
    fail("cutoverReady must be true only when status is PASS");
  }
  if (!Array.isArray(receipt.ownerReceipts)) fail("ownerReceipts must be an array");
  if (receipt.itemReceipts !== undefined && !Array.isArray(receipt.itemReceipts)) fail("itemReceipts must be an array when present");
  if (!Array.isArray(receipt.missingCriteria)) fail("missingCriteria must be an array");
  if (!Array.isArray(receipt.validationCommands)) fail("validationCommands must be an array");
  if (hasDuplicates((receipt.ownerReceipts || []).map((owner) => owner.owner))) {
    fail("ownerReceipts owners must be unique");
  }
  if (receipt.summary?.ownerCount !== (receipt.ownerReceipts || []).length) {
    fail("summary.ownerCount must match ownerReceipts length");
  }
  if (receipt.summary?.itemReceiptCount !== undefined && receipt.summary.itemReceiptCount !== (receipt.itemReceipts || []).length) {
    fail("summary.itemReceiptCount must match itemReceipts length");
  }
  if (receipt.summary?.missingCriteria !== (receipt.missingCriteria || []).length) {
    fail("summary.missingCriteria must match missingCriteria length");
  }
  if (receipt.summary?.cutoverReady !== receipt.cutoverReady) {
    fail("summary.cutoverReady must match cutoverReady");
  }
  for (const [field, expected] of Object.entries(requiredCriteria)) {
    if (!Object.hasOwn(receipt.criteria || {}, field)) {
      fail(`criteria.${field} is required`);
    }
    const actual = receipt.observed?.[field];
    const met = JSON.stringify(actual) === JSON.stringify(expected);
    const criterion = (receipt.criteria || {})[field];
    if (criterion && JSON.stringify(criterion.expected) !== JSON.stringify(expected)) {
      fail(`criteria.${field}.expected must be ${expected}`);
    }
    if (criterion && JSON.stringify(criterion.actual) !== JSON.stringify(actual)) {
      fail(`criteria.${field}.actual must match observed.${field}`);
    }
    if (criterion && criterion.met !== met) {
      fail(`criteria.${field}.met must match expected/actual comparison`);
    }
    const listed = (receipt.missingCriteria || []).includes(field);
    if (!met && !listed) fail(`missingCriteria must include unmet ${field}`);
    if (met && listed) fail(`missingCriteria must not include met ${field}`);
  }
  const criteriaMet = Object.keys(requiredCriteria).every((field) => receipt.criteria?.[field]?.met === true);
  if (criteriaMet && receipt.status !== "PASS") fail("status must be PASS when all criteria are met");
  if (!criteriaMet && receipt.status !== "PENDING_OWNER_INPUT") fail("status must be PENDING_OWNER_INPUT when criteria are unmet");
  const ownerInputCount = (receipt.ownerReceipts || []).reduce((sum, owner) => sum + Number(owner.requiredOwnerInputs || 0), 0);
  if (receipt.summary?.requiredOwnerInputs !== ownerInputCount) {
    fail("summary.requiredOwnerInputs must match owner receipt input total");
  }
  for (const owner of receipt.ownerReceipts || []) {
    const label = `owner ${owner.owner || "missing"}`;
    if (!owner.owner) fail(`${label} owner is required`);
    if (!Number.isInteger(owner.requiredOwnerInputs) || owner.requiredOwnerInputs < 0) {
      fail(`${label}.requiredOwnerInputs must be a non-negative integer`);
    }
    if (!Number.isInteger(owner.remainingPlaceholders) || owner.remainingPlaceholders < 0) {
      fail(`${label}.remainingPlaceholders must be a non-negative integer`);
    }
    if (!Number.isInteger(owner.remainingMissing) || owner.remainingMissing < 0) {
      fail(`${label}.remainingMissing must be a non-negative integer`);
    }
    const expectedReady = owner.remainingPlaceholders === 0 && owner.remainingMissing === 0;
    if (owner.ready !== expectedReady) fail(`${label}.ready must match remaining placeholders/missing`);
  }
  for (const item of receipt.itemReceipts || []) {
    const label = `item ${item.canonicalKey || "missing"}`;
    if (!Number.isInteger(item.inputOrder) || item.inputOrder < 0) fail(`${label}.inputOrder must be a non-negative integer`);
    if (!item.owner) fail(`${label}.owner is required`);
    if (!item.canonicalKey) fail(`${label}.canonicalKey is required`);
    if (item.aliases !== undefined && !Array.isArray(item.aliases)) fail(`${label}.aliases must be an array`);
    if (typeof item.secret !== "boolean") fail(`${label}.secret must be boolean`);
    if (typeof item.requiresOwnerInput !== "boolean") fail(`${label}.requiresOwnerInput must be boolean`);
    if (typeof item.required !== "boolean") fail(`${label}.required must be boolean`);
    if (String(item.collectionGuidance || "").includes("=")) fail(`${label}.collectionGuidance must not contain assignments`);
  }
  for (const command of receipt.validationCommands || []) {
    if (!String(command).includes("<release-env-file>") && String(command).includes("DDD_RELEASE_ENV_FILE=")) {
      fail("validationCommands must redact DDD_RELEASE_ENV_FILE");
    }
  }
  const text = JSON.stringify(receipt);
  for (const pattern of forbiddenValuePatterns) {
    if (pattern.test(text)) fail("receipt must not expose concrete env assignments, placeholders, DSNs, or tokens");
  }
  return issues;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const receipt = readJson(receiptPath);
  for (const issue of validateReleaseOwnerInputReceipt(receipt)) {
    addFailure(issue);
  }

  const report = {
    generatedAt: new Date().toISOString(),
    status: failures.length === 0 ? "PASS" : "FAIL",
    redacted: true,
    contract: "ddd-release-owner-input-receipt-contract",
    receipt: path.relative(process.cwd(), path.resolve(receiptPath)),
    issueCount: failures.length,
    issues: failures,
  };
  const outputFile = process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_CONTRACT_REPORT
    ? path.resolve(process.env.DDD_RELEASE_OWNER_INPUT_RECEIPT_CONTRACT_REPORT)
    : path.join(releaseDir, "release-owner-input-receipt-contract.json");
  fs.mkdirSync(path.dirname(outputFile), { recursive: true });
  fs.writeFileSync(outputFile, `${JSON.stringify(report, null, 2)}\n`);

  if (failures.length > 0) {
    console.error(`[ddd-release-owner-input-receipt-contract] wrote report to ${outputFile}`);
    throw new Error(`release owner input receipt contract failed: ${failures.join("; ")}`);
  }

  console.log(`[ddd-release-owner-input-receipt-contract] ok status=${receipt.status} owners=${receipt.summary?.ownerCount ?? "unknown"} report=${outputFile}`);
}
