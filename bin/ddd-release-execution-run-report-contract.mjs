#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { redactReleaseOutput } from "./ddd-release-redact-output.mjs";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

export function validateReleaseExecutionRunReport(report = {}) {
  const issues = [];
  if (!report || typeof report !== "object" || Array.isArray(report)) {
    return ["release execution run report must be a JSON object"];
  }

  if (!report.generatedAt || Number.isNaN(Date.parse(report.generatedAt))) {
    issues.push("release execution run report generatedAt must be an ISO-like datetime");
  }
  if (!["PASS", "FAIL"].includes(report.reportStatus)) {
    issues.push(`release execution run report reportStatus must be PASS or FAIL, got ${report.reportStatus ?? "missing"}`);
  }
  if (!Number.isInteger(report.exitCode) || report.exitCode < 0) {
    issues.push(`release execution run report exitCode must be a non-negative integer, got ${report.exitCode ?? "missing"}`);
  }
  if (report.reportStatus === "PASS" && report.exitCode !== 0) {
    issues.push(`release execution run report PASS must have exitCode=0, got ${report.exitCode}`);
  }
  if (report.reportStatus === "FAIL" && report.exitCode === 0) {
    issues.push("release execution run report FAIL must have non-zero exitCode");
  }
  if (report.batchFilter !== null && report.batchFilter !== undefined && typeof report.batchFilter !== "string") {
    issues.push("release execution run report batchFilter must be a string or null");
  }
  if (report.ownerFilter !== null && report.ownerFilter !== undefined && typeof report.ownerFilter !== "string") {
    issues.push("release execution run report ownerFilter must be a string or null");
  }
  if (report.priorityFilter !== null && report.priorityFilter !== undefined && typeof report.priorityFilter !== "string") {
    issues.push("release execution run report priorityFilter must be a string or null");
  }
  if (!report.summary || typeof report.summary !== "object" || Array.isArray(report.summary)) {
    issues.push("release execution run report summary must be an object");
  }
  if (!Array.isArray(report.entries)) {
    issues.push("release execution run report entries must be an array");
    return issues;
  }

  let nonZeroEntryCount = 0;
  for (const [index, entry] of report.entries.entries()) {
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
      issues.push(`release execution run report entries[${index}] must be an object`);
      continue;
    }
    if (!entry.batchId || typeof entry.batchId !== "string") {
      issues.push(`release execution run report entries[${index}].batchId is required`);
    }
    if (!entry.owner || typeof entry.owner !== "string") {
      issues.push(`release execution run report entries[${index}].owner is required`);
    }
    if (!entry.priority || typeof entry.priority !== "string") {
      issues.push(`release execution run report entries[${index}].priority is required`);
    }
    if (!entry.command || typeof entry.command !== "string") {
      issues.push(`release execution run report entries[${index}].command is required`);
    } else if (redactReleaseOutput(entry.command) !== entry.command) {
      issues.push(`release execution run report entries[${index}].command must not expose concrete secret values, release env files, or local repo paths`);
    }
    if (!Number.isInteger(entry.status) || entry.status < 0) {
      issues.push(`release execution run report entries[${index}].status must be a non-negative integer`);
    } else if (entry.status !== 0) {
      nonZeroEntryCount += 1;
    }
    if (!Number.isFinite(Number(entry.durationMs)) || Number(entry.durationMs) < 0) {
      issues.push(`release execution run report entries[${index}].durationMs must be a non-negative number`);
    }
    if (!entry.finishedAt || Number.isNaN(Date.parse(entry.finishedAt))) {
      issues.push(`release execution run report entries[${index}].finishedAt must be an ISO-like datetime`);
    }
  }

  if (nonZeroEntryCount > 0 && report.reportStatus !== "FAIL") {
    issues.push("release execution run report must be FAIL when any command entry failed");
  }
  if (report.reportStatus === "PASS" && report.entries.length === 0) {
    issues.push("release execution run report PASS must include at least one command entry");
  }
  if (report.summary && typeof report.summary === "object" && !Array.isArray(report.summary)) {
    const totalEntries = report.entries.length;
    const failedEntries = nonZeroEntryCount;
    const succeededEntries = totalEntries - failedEntries;
    if (report.summary.totalEntries !== totalEntries) {
      issues.push(`release execution run report summary.totalEntries must be ${totalEntries}, got ${report.summary.totalEntries ?? "missing"}`);
    }
    if (report.summary.failedEntries !== failedEntries) {
      issues.push(`release execution run report summary.failedEntries must be ${failedEntries}, got ${report.summary.failedEntries ?? "missing"}`);
    }
    if (report.summary.succeededEntries !== succeededEntries) {
      issues.push(`release execution run report summary.succeededEntries must be ${succeededEntries}, got ${report.summary.succeededEntries ?? "missing"}`);
    }
  }

  return issues;
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function main() {
  const reportPath = process.env.DDD_RELEASE_EXECUTION_REPORT || path.join(repoRoot, "artifacts/ddd/release/release-execution-run-report.json");
  if (!fs.existsSync(reportPath)) {
    console.error(`[ddd-release-execution-run-report-contract] missing report: ${reportPath}`);
    process.exit(1);
  }
  const issues = validateReleaseExecutionRunReport(readJson(reportPath));
  if (issues.length > 0) {
    for (const issue of issues) console.error(`[ddd-release-execution-run-report-contract][blocker] ${issue}`);
    process.exit(1);
  }
  console.log(`[ddd-release-execution-run-report-contract] ok report=${reportPath}`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
