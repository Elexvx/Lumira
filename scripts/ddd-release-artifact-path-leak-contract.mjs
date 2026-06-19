#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const artifactRoot = process.env.DDD_RELEASE_EVIDENCE_DIR
  ? path.resolve(process.env.DDD_RELEASE_EVIDENCE_DIR)
  : path.join(repoRoot, "artifacts", "ddd");
const outputFile = process.env.DDD_RELEASE_PATH_LEAK_REPORT
  ? path.resolve(process.env.DDD_RELEASE_PATH_LEAK_REPORT)
  : path.join(artifactRoot, "release", "release-artifact-path-leak-contract.json");
const homeDir = process.env.DDD_RELEASE_PATH_LEAK_HOME || os.homedir();

const defaultFiles = [
  "release/release-evidence-gate.json",
  "release/readiness-summary.json",
  "release/readiness-summary.md",
  "release/release-final-go-no-go.json",
  "release/release-final-go-no-go.md",
  "release/release-config-owner-input-reconciliation.json",
  "release/release-owner-input-receipt.json",
  "release/release-owner-input-receipt.csv",
  "release/release-owner-input-receipt-items.csv",
  "release/release-owner-input-receipt-items.md",
  "release/release-owner-input-receipt.md",
  "release/release-unblock-brief.json",
  "release/release-unblock-brief.md",
  "release/evidence-manifest.json",
  "release/evidence-manifest-preflight.json",
  "release/orchestrator-report.json",
  "release/release-performance-baseline-closure.json",
  "release/release-performance-baseline-closure.md",
  "frontend/frontend-smoke.json",
  "performance/authenticated-runtime-baseline-promotion.json",
  "build/docker-image-evidence.json",
];

const defaultReleaseEnvDisplayFiles = [
  "release/owner-action-rollup.json",
  "release/owner-action-rollup.csv",
  "release/owner-action-rollup.md",
  "release/source-action-rollup.json",
  "release/source-action-rollup.csv",
  "release/source-action-rollup.md",
  "release/release-action-priority.json",
  "release/release-action-priority.csv",
  "release/release-action-priority.md",
  "release/release-action-batches.json",
  "release/release-action-batches.csv",
  "release/release-action-batches.md",
  "release/release-action-dependency-graph.json",
  "release/release-action-dependency-graph.md",
  "release/release-blocker-map.json",
  "release/release-blocker-map.csv",
  "release/release-blocker-map.md",
  "release/release-cutover-owner-matrix.json",
  "release/release-cutover-owner-matrix.csv",
  "release/release-cutover-owner-matrix.md",
  "release/release-final-go-no-go.json",
  "release/release-final-go-no-go.csv",
  "release/release-final-go-no-go.md",
  "release/release-env-owner-input-packet.json",
  "release/release-env-owner-input-packet.csv",
  "release/release-env-owner-input-packet.md",
  "release/release-owner-input-receipt.json",
  "release/release-owner-input-receipt.csv",
  "release/release-owner-input-receipt-items.csv",
  "release/release-owner-input-receipt-items.md",
  "release/release-owner-input-receipt.md",
];

const files = (process.env.DDD_RELEASE_PATH_LEAK_FILES || "")
  .split(",")
  .map((file) => file.trim())
  .filter(Boolean);
const relativeFiles = files.length > 0 ? files : defaultFiles;
const releaseEnvDisplayFiles = (process.env.DDD_RELEASE_ENV_DISPLAY_FILES || "")
  .split(",")
  .map((file) => file.trim())
  .filter(Boolean);
const relativeReleaseEnvDisplayFiles = releaseEnvDisplayFiles.length > 0
  ? releaseEnvDisplayFiles
  : defaultReleaseEnvDisplayFiles;

function releaseEnvOwnerInputPacketFiles() {
  const ownerPacketDir = path.join(artifactRoot, "release", "release-env-owner-input-packet");
  if (!fs.existsSync(ownerPacketDir) || !fs.statSync(ownerPacketDir).isDirectory()) {
    return [];
  }
  return fs.readdirSync(ownerPacketDir)
    .filter((file) => file.endsWith(".json") || file.endsWith(".md"))
    .sort()
    .map((file) => path.join("release", "release-env-owner-input-packet", file));
}

function releaseOwnerInputReceiptItemFiles() {
  const ownerReceiptItemDir = path.join(artifactRoot, "release", "release-owner-input-receipt-items");
  if (!fs.existsSync(ownerReceiptItemDir) || !fs.statSync(ownerReceiptItemDir).isDirectory()) {
    return [];
  }
  return fs.readdirSync(ownerReceiptItemDir)
    .filter((file) => file.endsWith(".md"))
    .sort()
    .map((file) => path.join("release", "release-owner-input-receipt-items", file));
}

const expandedReleaseEnvDisplayFiles = [
  ...relativeReleaseEnvDisplayFiles,
  ...(releaseEnvDisplayFiles.length > 0 ? [] : [
    ...releaseEnvOwnerInputPacketFiles(),
    ...releaseOwnerInputReceiptItemFiles(),
  ]),
];

function portablePath(filePath) {
  const absolutePath = path.resolve(filePath);
  return absolutePath === repoRoot || absolutePath.startsWith(`${repoRoot}${path.sep}`)
    ? (path.relative(repoRoot, absolutePath) || ".").replaceAll("\\", "/")
    : filePath.replaceAll("\\", "/");
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

function leakPatterns() {
  const patterns = [];
  if (repoRoot) {
    patterns.push({ label: "repoRoot", value: repoRoot });
    patterns.push({ label: "repoRoot", value: repoRoot.replaceAll("\\", "\\\\") });
  }
  if (homeDir && homeDir !== "/" && homeDir !== repoRoot) {
    patterns.push({ label: "homeDir", value: homeDir });
    patterns.push({ label: "homeDir", value: homeDir.replaceAll("\\", "\\\\") });
  }
  return patterns;
}

const patterns = leakPatterns();
const scanned = [];
const releaseEnvDisplayScanned = [];
const leaks = [];

function lineColumn(text, index) {
  const line = text.slice(0, index).split(/\r?\n/).length;
  const column = index - text.lastIndexOf("\n", index - 1);
  return { line, column };
}

function recordLeak(file, type, line, column, target = leaks) {
  target.push({
    file: portablePath(file),
    type,
    line,
    column,
  });
}

for (const relativeFile of relativeFiles) {
  const file = path.isAbsolute(relativeFile)
    ? relativeFile
    : path.join(artifactRoot, relativeFile);
  if (!fs.existsSync(file)) {
    scanned.push({
      file: portablePath(file),
      present: false,
      leaks: 0,
    });
    continue;
  }
  const text = fs.readFileSync(file, "utf8");
  const fileLeaks = [];
  for (const pattern of patterns) {
    let index = text.indexOf(pattern.value);
    while (index !== -1) {
      const { line, column } = lineColumn(text, index);
      fileLeaks.push({
        type: pattern.label,
        line,
        column,
      });
      recordLeak(file, pattern.label, line, column);
      index = text.indexOf(pattern.value, index + pattern.value.length);
    }
  }
  scanned.push({
    file: portablePath(file),
    present: true,
    leaks: fileLeaks.length,
  });
}

function releaseEnvLeakPatterns() {
  return [
    {
      label: "releaseEnvFile",
      pattern: /DDD_RELEASE_ENV_FILE=(?!<release-env-file>)(?:"[^"`\s|]+"|'[^'`\s|]+'|[^\s`|]+)/g,
    },
    {
      label: "releaseEnvFile",
      pattern: /(?:^|[\s"'=:{[(,])(?:[^\s"`'|]*\/)?\.env\.release(?:\.[A-Za-z0-9_-]+)?(?=$|[\s"`')\]},|])/gm,
    },
  ];
}

for (const relativeFile of expandedReleaseEnvDisplayFiles) {
  const file = path.isAbsolute(relativeFile)
    ? relativeFile
    : path.join(artifactRoot, relativeFile);
  if (!fs.existsSync(file)) {
    releaseEnvDisplayScanned.push({
      file: portablePath(file),
      present: false,
      leaks: 0,
    });
    continue;
  }
  const text = fs.readFileSync(file, "utf8");
  const fileLeaks = [];
  for (const leakPattern of releaseEnvLeakPatterns()) {
    const pattern = new RegExp(leakPattern.pattern.source, leakPattern.pattern.flags);
    for (const match of text.matchAll(pattern)) {
      const matched = match[0] || "";
      const offset = matched.search(/(?:DDD_RELEASE_ENV_FILE=|\.env\.release)/);
      const index = match.index + Math.max(offset, 0);
      const { line, column } = lineColumn(text, index);
      fileLeaks.push({
        type: leakPattern.label,
        line,
        column,
      });
      recordLeak(file, leakPattern.label, line, column);
    }
  }
  releaseEnvDisplayScanned.push({
    file: portablePath(file),
    present: true,
    leaks: fileLeaks.length,
  });
}

const reportBody = {
  status: leaks.length === 0 ? "PASS" : "FAIL",
  redacted: true,
  scannedFiles: scanned.length,
  releaseEnvDisplayScannedFiles: releaseEnvDisplayScanned.length,
  leakCount: leaks.length,
  scanned,
  releaseEnvDisplayScanned,
  leaks,
  policy: {
    forbiddenPatterns: patterns.map((pattern) => pattern.label),
    forbiddenReleaseEnvDisplayPatterns: ["concrete DDD_RELEASE_ENV_FILE", "concrete .env.release* token"],
    replacementGuidance: "Use repo-relative paths, <repo>, or ~ for user-home scoped paths.",
    releaseEnvReplacementGuidance: "Use <release-env-file> in human-facing release reports; keep concrete env files only in executable local scripts.",
  },
};
const report = {
  generatedAt: stableGeneratedAt(outputFile, reportBody),
  ...reportBody,
};

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(report, null, 2)}\n`);

if (leaks.length > 0) {
  for (const leak of leaks) {
    console.error(`[ddd-release-artifact-path-leak-contract] ${leak.file}:${leak.line}:${leak.column} contains ${leak.type}`);
  }
  console.error(`[ddd-release-artifact-path-leak-contract] wrote report to ${portablePath(outputFile)}`);
  process.exit(1);
}

console.log(`[ddd-release-artifact-path-leak-contract] ok files=${scanned.length} releaseEnvDisplayFiles=${releaseEnvDisplayScanned.length}`);
