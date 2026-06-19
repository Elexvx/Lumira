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

export function renderFinalOwnerQueueRunReportSummary(report = null) {
  if (!report || typeof report !== "object" || Array.isArray(report)) {
    return [
      "## Final Owner Queue Run Report",
      "",
      "No final owner queue run report was generated.",
      "",
    ].join("\n");
  }

  const entries = Array.isArray(report.entries) ? report.entries : [];
  const failed = entries.filter((entry) => Number(entry?.status) !== 0);
  const succeeded = entries.length - failed.length;
  const lines = [
    "## Final Owner Queue Run Report",
    "",
    "| Field | Value |",
    "|---|---:|",
    `| Report status | ${sanitizeCell(report.reportStatus || "UNKNOWN")} |`,
    `| Exit code | ${sanitizeCell(report.exitCode ?? "UNKNOWN")} |`,
    `| Owner filter | ${sanitizeCell(report.ownerFilter || "none")} |`,
    `| Status filter | ${sanitizeCell(report.statusFilter || "none")} |`,
    `| Entries | ${entries.length} |`,
    `| Succeeded entries | ${succeeded} |`,
    `| Failed entries | ${failed.length} |`,
    "",
  ];

  if (entries.length === 0) {
    lines.push("No owner queue commands were executed in this run.", "");
    return lines.join("\n");
  }

  lines.push("| Owner | Queue order | Queue status | Command | Status | Duration ms | Command text |");
  lines.push("|---|---:|---|---:|---:|---:|---|");
  for (const entry of entries) {
    const status = Number(entry?.status);
    const statusLabel = status === 0 ? "PASS" : `FAIL(${Number.isFinite(status) ? status : "unknown"})`;
    const commandOrdinal = Number.isInteger(entry?.commandIndex) && Number.isInteger(entry?.commandCount)
      ? `${entry.commandIndex}/${entry.commandCount}`
      : "unknown";
    lines.push(`| ${sanitizeCell(entry?.owner || "unknown")} | ${sanitizeCell(entry?.queueOrder ?? "")} | ${sanitizeCell(entry?.queueStatus || "unknown")} | ${sanitizeCell(commandOrdinal)} | ${statusLabel} | ${sanitizeCell(entry?.durationMs ?? "")} | \`${sanitizeCommand(entry?.command || "")}\` |`);
  }
  lines.push("");
  return lines.join("\n");
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function main() {
  const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
  const reportPath = process.env.DDD_FINAL_OWNER_QUEUE_REPORT || path.join(repoRoot, "artifacts/ddd/release/release-final-owner-queue-run-report.json");
  const outputPath = process.env.DDD_FINAL_OWNER_QUEUE_SUMMARY_OUTPUT || process.env.GITHUB_STEP_SUMMARY;
  const summary = fs.existsSync(reportPath)
    ? renderFinalOwnerQueueRunReportSummary(readJson(reportPath))
    : renderFinalOwnerQueueRunReportSummary(null);

  if (outputPath) {
    fs.appendFileSync(outputPath, `${summary}\n`);
    return;
  }
  process.stdout.write(`${summary}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
