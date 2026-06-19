#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repoRootPattern = new RegExp(`${repoRoot.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}${path.sep.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`, "g");

function sanitizeCell(value) {
  return String(value ?? "")
    .replace(/[|\n\r]/g, " ")
    .replace(/`/g, "");
}

function sanitizeCommand(value) {
  return sanitizeCell(value)
    .replace(/\b([A-Z][A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|API_KEY))=("[^"]*"|'[^']*'|[^\s|]+)/g, "$1=<redacted>")
    .replace(/\bDDD_RELEASE_ENV_FILE=(?!<release-env-file>)[^\s|]+/g, "DDD_RELEASE_ENV_FILE=<release-env-file>")
    .replace(repoRootPattern, "<repo>/")
    .replaceAll("\\", "/")
    .replaceAll("<repo>/<release-env-file>", "<release-env-file>");
}

export function renderReleaseNextActionRunReportSummary(report = null) {
  if (!report || typeof report !== "object" || Array.isArray(report)) {
    return [
      "## Release Next Action Run Report",
      "",
      "No release next action run report was generated.",
      "",
    ].join("\n");
  }

  const entries = Array.isArray(report.entries) ? report.entries : [];
  const failed = entries.filter((entry) => Number(entry?.status) !== 0);
  const succeeded = entries.length - failed.length;
  const lines = [
    "## Release Next Action Run Report",
    "",
    "| Field | Value |",
    "|---|---:|",
    `| Report status | ${sanitizeCell(report.reportStatus || "UNKNOWN")} |`,
    `| Exit code | ${sanitizeCell(report.exitCode ?? "UNKNOWN")} |`,
    `| Owner filter | ${sanitizeCell(report.ownerFilter || "none")} |`,
    `| Order filter | ${sanitizeCell(report.orderFilter || "none")} |`,
    `| Entries | ${entries.length} |`,
    `| Succeeded entries | ${succeeded} |`,
    `| Failed entries | ${failed.length} |`,
    "",
  ];

  if (entries.length === 0) {
    lines.push("No next-action commands were executed in this run.", "");
    return lines.join("\n");
  }

  lines.push("| Owner | Order | Receipt status | Status | Duration ms | Command |");
  lines.push("|---|---:|---|---:|---:|---|");
  for (const entry of entries) {
    const status = Number(entry?.status);
    const statusLabel = status === 0 ? "PASS" : `FAIL(${Number.isFinite(status) ? status : "unknown"})`;
    lines.push(`| ${sanitizeCell(entry?.owner || "unknown")} | ${sanitizeCell(entry?.order ?? "")} | ${sanitizeCell(entry?.receiptStatus || "unknown")} | ${statusLabel} | ${sanitizeCell(entry?.durationMs ?? "")} | \`${sanitizeCommand(entry?.command || "")}\` |`);
  }
  lines.push("");
  return lines.join("\n");
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function main() {
  const reportPath = process.env.DDD_RELEASE_NEXT_ACTION_REPORT || path.join(repoRoot, "artifacts/ddd/release/release-next-action-run-report.json");
  const outputPath = process.env.DDD_RELEASE_NEXT_ACTION_SUMMARY_OUTPUT || process.env.GITHUB_STEP_SUMMARY;
  const summary = fs.existsSync(reportPath)
    ? renderReleaseNextActionRunReportSummary(readJson(reportPath))
    : renderReleaseNextActionRunReportSummary(null);

  if (outputPath) {
    fs.appendFileSync(outputPath, `${summary}\n`);
    return;
  }
  process.stdout.write(`${summary}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
