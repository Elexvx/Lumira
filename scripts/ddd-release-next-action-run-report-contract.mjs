#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repoRootPattern = new RegExp(`${repoRoot.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}${path.sep.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`);
const unsafeCommandPatterns = [
  /\b[A-Z][A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|API_KEY)=(?!<redacted>)("[^"]*"|'[^']*'|[^\s`|]+)/,
  /\bDDD_RELEASE_ENV_FILE=(?!<release-env-file>)[^\s`|]+/,
  repoRootPattern,
];

export function validateReleaseNextActionRunReport(report = {}) {
  const issues = [];
  if (!report || typeof report !== "object" || Array.isArray(report)) {
    return ["release next action run report must be a JSON object"];
  }
  if (!report.generatedAt || Number.isNaN(Date.parse(report.generatedAt))) {
    issues.push("release next action run report generatedAt must be an ISO-like datetime");
  }
  if (!["PASS", "FAIL"].includes(report.reportStatus)) {
    issues.push(`release next action run report reportStatus must be PASS or FAIL, got ${report.reportStatus ?? "missing"}`);
  }
  if (!Number.isInteger(report.exitCode) || report.exitCode < 0) {
    issues.push(`release next action run report exitCode must be a non-negative integer, got ${report.exitCode ?? "missing"}`);
  }
  if (report.reportStatus === "PASS" && report.exitCode !== 0) {
    issues.push(`release next action run report PASS must have exitCode=0, got ${report.exitCode}`);
  }
  if (report.reportStatus === "FAIL" && report.exitCode === 0) {
    issues.push("release next action run report FAIL must have non-zero exitCode");
  }
  if (report.ownerFilter !== null && report.ownerFilter !== undefined && typeof report.ownerFilter !== "string") {
    issues.push("release next action run report ownerFilter must be a string or null");
  }
  if (report.orderFilter !== null && report.orderFilter !== undefined && typeof report.orderFilter !== "string") {
    issues.push("release next action run report orderFilter must be a string or null");
  }
  if (!report.summary || typeof report.summary !== "object" || Array.isArray(report.summary)) {
    issues.push("release next action run report summary must be an object");
  }
  if (!Array.isArray(report.entries)) {
    issues.push("release next action run report entries must be an array");
    return issues;
  }

  let nonZeroEntryCount = 0;
  for (const [index, entry] of report.entries.entries()) {
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
      issues.push(`release next action run report entries[${index}] must be an object`);
      continue;
    }
    if (!Number.isInteger(entry.order) || entry.order < 0) {
      issues.push(`release next action run report entries[${index}].order must be a non-negative integer`);
    }
    if (!entry.owner || typeof entry.owner !== "string") {
      issues.push(`release next action run report entries[${index}].owner is required`);
    }
    if (!entry.receiptStatus || typeof entry.receiptStatus !== "string") {
      issues.push(`release next action run report entries[${index}].receiptStatus is required`);
    }
    if (!entry.command || typeof entry.command !== "string") {
      issues.push(`release next action run report entries[${index}].command is required`);
    } else {
      for (const pattern of unsafeCommandPatterns) {
        if (pattern.test(entry.command)) {
          issues.push(`release next action run report entries[${index}].command must not expose concrete secret values, release env files, or local repo paths`);
        }
      }
    }
    if (!Number.isInteger(entry.status) || entry.status < 0) {
      issues.push(`release next action run report entries[${index}].status must be a non-negative integer`);
    } else if (entry.status !== 0) {
      nonZeroEntryCount += 1;
    }
    if (!Number.isFinite(Number(entry.durationMs)) || Number(entry.durationMs) < 0) {
      issues.push(`release next action run report entries[${index}].durationMs must be a non-negative number`);
    }
    if (!entry.finishedAt || Number.isNaN(Date.parse(entry.finishedAt))) {
      issues.push(`release next action run report entries[${index}].finishedAt must be an ISO-like datetime`);
    }
  }

  if (nonZeroEntryCount > 0 && report.reportStatus !== "FAIL") {
    issues.push("release next action run report must be FAIL when any command entry failed");
  }
  if (report.reportStatus === "PASS" && report.entries.length === 0) {
    issues.push("release next action run report PASS must include at least one command entry");
  }
  if (report.summary && typeof report.summary === "object" && !Array.isArray(report.summary)) {
    const totalEntries = report.entries.length;
    const failedEntries = nonZeroEntryCount;
    const succeededEntries = totalEntries - failedEntries;
    if (report.summary.totalEntries !== totalEntries) {
      issues.push(`release next action run report summary.totalEntries must be ${totalEntries}, got ${report.summary.totalEntries ?? "missing"}`);
    }
    if (report.summary.failedEntries !== failedEntries) {
      issues.push(`release next action run report summary.failedEntries must be ${failedEntries}, got ${report.summary.failedEntries ?? "missing"}`);
    }
    if (report.summary.succeededEntries !== succeededEntries) {
      issues.push(`release next action run report summary.succeededEntries must be ${succeededEntries}, got ${report.summary.succeededEntries ?? "missing"}`);
    }
  }
  return issues;
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function main() {
  const reportPath = process.env.DDD_RELEASE_NEXT_ACTION_REPORT || path.join(repoRoot, "artifacts/ddd/release/release-next-action-run-report.json");
  if (!fs.existsSync(reportPath)) {
    console.error(`[ddd-release-next-action-run-report-contract] missing report: ${reportPath}`);
    process.exit(1);
  }
  const issues = validateReleaseNextActionRunReport(readJson(reportPath));
  if (issues.length > 0) {
    for (const issue of issues) console.error(`[ddd-release-next-action-run-report-contract][blocker] ${issue}`);
    process.exit(1);
  }
  console.log(`[ddd-release-next-action-run-report-contract] ok report=${reportPath}`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
