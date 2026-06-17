#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { redactReleaseOutput } from "./ddd-release-redact-output.mjs";

function sanitizeCell(value) {
  return String(value ?? "")
    .replace(/[|\n\r]/g, " ")
    .replace(/`/g, "");
}

function sanitizeCommand(value) {
  return sanitizeCell(redactReleaseOutput(value));
}

export function renderReleaseExecutionRunReportSummary(report = null) {
  if (!report || typeof report !== "object" || Array.isArray(report)) {
    return [
      "## Release Execution Run Report",
      "",
      "No release execution run report was generated.",
      "",
    ].join("\n");
  }

  const entries = Array.isArray(report.entries) ? report.entries : [];
  const failed = entries.filter((entry) => Number(entry?.status) !== 0);
  const succeeded = entries.length - failed.length;
  const lines = [
    "## Release Execution Run Report",
    "",
    "| Field | Value |",
    "|---|---:|",
    `| Report status | ${sanitizeCell(report.reportStatus || "UNKNOWN")} |`,
    `| Exit code | ${sanitizeCell(report.exitCode ?? "UNKNOWN")} |`,
    `| Batch filter | ${sanitizeCell(report.batchFilter || "none")} |`,
    `| Owner filter | ${sanitizeCell(report.ownerFilter || "none")} |`,
    `| Priority filter | ${sanitizeCell(report.priorityFilter || "none")} |`,
    `| Entries | ${entries.length} |`,
    `| Succeeded entries | ${succeeded} |`,
    `| Failed entries | ${failed.length} |`,
    "",
  ];

  if (entries.length === 0) {
    lines.push("No release execution commands were executed in this run.", "");
    return lines.join("\n");
  }

  lines.push("| Batch | Owner | Priority | Status | Duration ms | Command |");
  lines.push("|---|---|---|---:|---:|---|");
  for (const entry of entries) {
    const status = Number(entry?.status);
    const statusLabel = status === 0 ? "PASS" : `FAIL(${Number.isFinite(status) ? status : "unknown"})`;
    lines.push(`| ${sanitizeCell(entry?.batchId || "unknown")} | ${sanitizeCell(entry?.owner || "unknown")} | ${sanitizeCell(entry?.priority || "unknown")} | ${statusLabel} | ${sanitizeCell(entry?.durationMs ?? "")} | \`${sanitizeCommand(entry?.command || "")}\` |`);
  }
  lines.push("");
  return lines.join("\n");
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function main() {
  const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
  const reportPath = process.env.DDD_RELEASE_EXECUTION_REPORT || path.join(repoRoot, "artifacts/ddd/release/release-execution-run-report.json");
  const outputPath = process.env.DDD_RELEASE_EXECUTION_SUMMARY_OUTPUT || process.env.GITHUB_STEP_SUMMARY;
  const summary = fs.existsSync(reportPath)
    ? renderReleaseExecutionRunReportSummary(readJson(reportPath))
    : renderReleaseExecutionRunReportSummary(null);

  if (outputPath) {
    fs.appendFileSync(outputPath, `${summary}\n`);
    return;
  }
  process.stdout.write(`${summary}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
