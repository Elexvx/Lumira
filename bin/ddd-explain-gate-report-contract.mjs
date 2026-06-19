#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export function validateExplainGateReport(report = {}) {
  const issues = [];
  if (!report || typeof report !== "object" || Array.isArray(report)) {
    return ["EXPLAIN gate report must be a JSON object"];
  }
  if (!report.generatedAt || Number.isNaN(Date.parse(report.generatedAt))) {
    issues.push("EXPLAIN gate report generatedAt must be an ISO-like datetime");
  }
  if (!["PASS", "FAIL"].includes(report.status)) {
    issues.push(`EXPLAIN gate report status must be PASS or FAIL, got ${report.status ?? "missing"}`);
  }
  if (typeof report.strict !== "boolean") {
    issues.push("EXPLAIN gate report strict must be boolean");
  }
  if (!report.explainDir || typeof report.explainDir !== "string") {
    issues.push("EXPLAIN gate report explainDir is required");
  }
  if (!Number.isInteger(report.scannedExplainFileCount) || report.scannedExplainFileCount < 0) {
    issues.push("EXPLAIN gate report scannedExplainFileCount must be a non-negative integer");
  }
  if (!Number.isInteger(report.blockerCount) || report.blockerCount < 0) {
    issues.push("EXPLAIN gate report blockerCount must be a non-negative integer");
  }
  if (!Array.isArray(report.issues)) {
    issues.push("EXPLAIN gate report issues must be an array");
    return issues;
  }
  if (report.blockerCount !== report.issues.length) {
    issues.push(`EXPLAIN gate report blockerCount must match issues length, got ${report.blockerCount} and ${report.issues.length}`);
  }
  if (report.status === "PASS" && report.blockerCount !== 0) {
    issues.push(`EXPLAIN gate report PASS must have blockerCount=0, got ${report.blockerCount}`);
  }
  if (report.status === "FAIL" && report.blockerCount === 0) {
    issues.push("EXPLAIN gate report FAIL must have blockerCount > 0");
  }
  for (const [index, issue] of report.issues.entries()) {
    if (!issue || typeof issue !== "object" || Array.isArray(issue)) {
      issues.push(`EXPLAIN gate report issues[${index}] must be an object`);
      continue;
    }
    if (!issue.scope || typeof issue.scope !== "string") {
      issues.push(`EXPLAIN gate report issues[${index}].scope is required`);
    }
    if (!issue.detail || typeof issue.detail !== "string") {
      issues.push(`EXPLAIN gate report issues[${index}].detail is required`);
    }
  }
  return issues;
}

function main() {
  const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
  const reportPath = process.env.DDD_EXPLAIN_GATE_REPORT || path.join(repoRoot, "artifacts/ddd/release/explain-gate-report.json");
  if (!fs.existsSync(reportPath)) {
    console.error(`[ddd-explain-gate-report-contract] missing report: ${reportPath}`);
    process.exit(1);
  }
  const issues = validateExplainGateReport(JSON.parse(fs.readFileSync(reportPath, "utf8")));
  if (issues.length > 0) {
    for (const issue of issues) {
      console.error(`[ddd-explain-gate-report-contract][blocker] ${issue}`);
    }
    process.exit(1);
  }
  console.log(`[ddd-explain-gate-report-contract] ok report=${reportPath}`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
