#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const rawArgs = process.argv.slice(2);

function readArg(name, fallback) {
  const prefix = `--${name}=`;
  const value = rawArgs.find((arg) => arg.startsWith(prefix));
  if (value) {
    return value.slice(prefix.length);
  }
  return process.env[name.toUpperCase().replaceAll("-", "_")] || fallback;
}

const receiptFile = readArg("receipt-file", "tmp/lane-completion-receipt.next.json");
const ownerEvidenceIntakeFile = readArg(
  "owner-evidence-intake-file",
  "artifacts/ddd/release/staging-handoff-bundle/owner-evidence-intake.json",
);
const outputFile = readArg("output", "tmp/lane-completion-receipt.autofill.json");
const completedBy = readArg("completed-by", process.env.GITHUB_ACTOR || process.env.USERNAME || process.env.USER || os.userInfo().username || "local-autofill");
const force = rawArgs.includes("--force");

function readJson(file) {
  return JSON.parse(fs.readFileSync(path.resolve(repoRoot, file), "utf8"));
}

function writeJson(file, value) {
  const absoluteFile = path.resolve(repoRoot, file);
  if (fs.existsSync(absoluteFile) && !force) {
    throw new Error(`refusing to overwrite ${file}; pass --force to replace it`);
  }
  fs.mkdirSync(path.dirname(absoluteFile), { recursive: true });
  fs.writeFileSync(absoluteFile, `${JSON.stringify(value, null, 2)}\n`);
}

function receiptKey(item) {
  return `${item.owner}:${item.lane}`;
}

const receipt = readJson(receiptFile);
const intake = readJson(ownerEvidenceIntakeFile);
const fragments = (intake.owners || []).flatMap((owner) => owner.receiptFragments || []);
const fragmentsByKey = new Map(fragments.map((fragment) => [fragment.key, fragment]));
const generatedAt = new Date().toISOString();
const autofilled = [];

for (const laneReceipt of receipt.laneReceipts || []) {
  const key = receiptKey(laneReceipt);
  const fragment = fragmentsByKey.get(key);
  if (!fragment || fragment.status !== "PASS") {
    continue;
  }

  laneReceipt.status = "PASS";
  laneReceipt.providedArtifacts = fragment.providedArtifacts || [];
  laneReceipt.missingArtifacts = fragment.missingArtifacts || [];
  laneReceipt.completedAt = laneReceipt.completedAt || generatedAt;
  laneReceipt.completedBy = laneReceipt.completedBy || completedBy;
  laneReceipt.evidenceNotes = [
    ...(laneReceipt.evidenceNotes || []),
    `autofilled from owner-evidence-intake receipt fragment ${key}; verify acceptance commands before final submission`,
  ];
  autofilled.push(key);
}

const laneReceipts = receipt.laneReceipts || [];
receipt.status = laneReceipts.length > 0 && laneReceipts.every((laneReceipt) => laneReceipt.status === "PASS") ? "PASS" : "BLOCKED";
receipt.laneReceiptCount = laneReceipts.length;
receipt.generatedAt = generatedAt;
receipt.autofill = {
  generatedAt,
  source: ownerEvidenceIntakeFile,
  output: outputFile,
  autofilledLaneCount: autofilled.length,
  autofilled,
  blockedLaneCount: laneReceipts.filter((laneReceipt) => laneReceipt.status !== "PASS").length,
};

writeJson(outputFile, receipt);

console.log(JSON.stringify({
  status: "PASS",
  generatedAt,
  willWriteFiles: true,
  receiptFile,
  ownerEvidenceIntakeFile,
  outputFile,
  autofilledLaneCount: autofilled.length,
  autofilled,
  blockedLaneCount: receipt.autofill.blockedLaneCount,
  receiptStatus: receipt.status,
  nextCommand: `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=${outputFile}`,
}, null, 2));
