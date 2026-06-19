#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import {
  missingRequiredExplainFiles,
  validateExplainArtifact,
} from "./ddd-explain-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const hotPathDoc = path.join(repoRoot, "docs", "28-ddd-hot-path-explain-plan.md");
const explainDir = process.env.DDD_EXPLAIN_DIR
  ? path.resolve(process.env.DDD_EXPLAIN_DIR)
  : path.join(repoRoot, "tmp", "ddd-explain");
const reportFile = process.env.DDD_EXPLAIN_GATE_REPORT
  ? path.resolve(process.env.DDD_EXPLAIN_GATE_REPORT)
  : path.join(repoRoot, "artifacts", "ddd", "release", "explain-gate-report.json");
const strict = process.env.DDD_EXPLAIN_STRICT === "true" || process.env.DDD_RELEASE_EVIDENCE_STRICT === "true";

const requiredHotPaths = [
  "Platform runtime appearance",
  "Plugin bootstrap",
  "Message visible list",
  "Message unread count",
  "Message archive total",
  "IAM permission snapshot",
  "AI knowledge index retry",
  "Message outbox owner relay",
  "File outbox owner relay",
  "File owner metadata lookup",
  "Payment webhook idempotency",
];

let issueCount = 0;
const issues = [];
let scannedExplainFileCount = 0;

function fail(message, scope = "gate") {
  issueCount += 1;
  issues.push({ scope, detail: message });
  console.error(`[ddd-explain-gate] ${message}`);
  process.exitCode = 1;
}

function displayPath(file) {
  const relative = path.relative(repoRoot, file);
  return relative && !relative.startsWith("..") && !path.isAbsolute(relative)
    ? relative.replaceAll("\\", "/")
    : file;
}

function writeReport() {
  fs.mkdirSync(path.dirname(reportFile), { recursive: true });
  const report = {
    generatedAt: new Date().toISOString(),
    status: issueCount === 0 ? "PASS" : "FAIL",
    strict,
    explainDir: displayPath(explainDir),
    scannedExplainFileCount,
    blockerCount: issueCount,
    issues,
  };
  fs.writeFileSync(reportFile, `${JSON.stringify(report, null, 2)}\n`);
}

function readHotPathRows(markdown) {
  return markdown
    .split(/\r?\n/)
    .filter((line) => line.startsWith("| ") && !line.includes("---"))
    .map((line) => line.split("|").slice(1, -1).map((cell) => cell.trim()))
    .filter((cells) => cells.length >= 6 && cells[0] !== "热路径");
}

function validateHotPathDoc() {
  if (!fs.existsSync(hotPathDoc)) {
    fail(`missing hot path explain document: ${hotPathDoc}`);
    return;
  }

  const rows = readHotPathRows(fs.readFileSync(hotPathDoc, "utf8"));
  const present = new Set(rows.map((row) => row[0]));

  for (const hotPath of requiredHotPaths) {
    if (!present.has(hotPath)) {
      fail(`missing required hot path row: ${hotPath}`);
    }
  }

  for (const row of rows) {
    const [hotPath, owner, entrypoint, filters, expectedIndex] = row;
    if (!owner || !entrypoint || !filters || !expectedIndex) {
      fail(`incomplete hot path row: ${hotPath}`);
    }
  }
}

function listJsonFiles(directory) {
  if (!fs.existsSync(directory)) {
    return [];
  }

  const files = [];
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...listJsonFiles(fullPath));
    } else if (entry.isFile() && entry.name.endsWith(".json")) {
      files.push(fullPath);
    }
  }
  return files;
}

function validateExplainJson(file) {
  let parsed;
  try {
    parsed = JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (error) {
    fail(`invalid explain json ${displayPath(file)}: ${error.message}`);
    return;
  }

  for (const issue of validateExplainArtifact(path.basename(file), parsed, { strict })) {
      fail(`${displayPath(file)}: ${issue.detail}`, issue.scope || "artifact");
  }
}

validateHotPathDoc();

const explainFiles = listJsonFiles(explainDir);
scannedExplainFileCount = explainFiles.length;
if (explainFiles.length === 0) {
  if (strict) {
    fail(`strict mode requires explain json files in ${explainDir}`);
  } else {
    console.log(`[ddd-explain-gate] hot path document validated; no explain json files found in ${explainDir}`);
  }
} else {
  const explainFileNames = explainFiles.map((file) => path.basename(file));
  for (const fileName of missingRequiredExplainFiles(explainFileNames)) {
    fail(`missing required explain json file: ${fileName}`);
  }
  for (const file of explainFiles) {
    validateExplainJson(file);
  }
  if (issueCount === 0) {
    console.log(`[ddd-explain-gate] validated ${explainFiles.length} explain json file(s)`);
  } else {
    console.error(`[ddd-explain-gate] scanned ${explainFiles.length} explain json file(s) with ${issueCount} blocker(s)`);
  }
}

if (process.exitCode) {
  writeReport();
  process.exit(process.exitCode);
}
writeReport();
