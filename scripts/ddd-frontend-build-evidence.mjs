#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { performance } from "node:perf_hooks";
import { collectProvenanceIssues, redactLocalPaths } from "./ddd-release-evidence-utils.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const frontendDir = path.join(repoRoot, "frontend");
const distDir = path.join(frontendDir, "dist");
const outputDir = process.env.DDD_FRONTEND_BUILD_DIR
  ? path.resolve(process.env.DDD_FRONTEND_BUILD_DIR)
  : path.join(repoRoot, "artifacts", "ddd", "frontend");
const outputFile = process.env.DDD_FRONTEND_BUILD_REPORT
  ? path.resolve(process.env.DDD_FRONTEND_BUILD_REPORT)
  : path.join(outputDir, "frontend-build-evidence.json");
const skipBuild = process.env.DDD_FRONTEND_BUILD_SKIP_RUN === "true";
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === "true" || process.env.DDD_FRONTEND_BUILD_STRICT === "true";
const sourceEnvironment = process.env.DDD_FRONTEND_BUILD_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";

function tail(text, max = 12000) {
  if (!text) {
    return "";
  }
  const value = text.length <= max ? text : text.slice(text.length - max);
  return redactLocalPaths(value, { repoRoot, homeDir: process.env.HOME || "" });
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function walk(directory, files = []) {
  if (!fs.existsSync(directory)) {
    return files;
  }
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, files);
    } else if (entry.isFile()) {
      files.push(fullPath);
    }
  }
  return files;
}

function runBuild() {
  if (skipBuild) {
    return {
      skipped: true,
      status: "SKIPPED",
      command: "corepack pnpm --dir frontend build",
      exitCode: null,
      durationMs: 0,
      stdoutTail: "",
      stderrTail: "",
    };
  }
  const startedAt = performance.now();
  const result = spawnSync("corepack", ["pnpm", "--dir", "frontend", "build"], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 30 * 1024 * 1024,
  });
  return {
    skipped: false,
    status: result.status === 0 ? "PASS" : "FAIL",
    command: "corepack pnpm --dir frontend build",
    exitCode: result.status,
    signal: result.signal || null,
    durationMs: Math.round((performance.now() - startedAt) * 100) / 100,
    stdoutTail: tail(result.stdout),
    stderrTail: tail(result.stderr),
  };
}

const blockers = [];
if (strictEvidence) {
  for (const issue of collectProvenanceIssues({ sourceEnvironment, releaseCandidate, evidenceOperator })) {
    blockers.push(`frontend build provenance ${issue}`);
  }
}
if (!fs.existsSync(frontendDir)) {
  blockers.push("missing frontend directory");
}

const build = blockers.length === 0 ? runBuild() : null;
if (build && build.status !== "PASS") {
  blockers.push(`frontend build failed: status=${build.status}, exitCode=${build.exitCode}`);
}
if (strictEvidence && build?.skipped) {
  blockers.push("frontend production build was skipped; strict release requires a fresh production build");
}

const files = walk(distDir);
const indexFile = path.join(distDir, "index.html");
const assets = files.filter((file) => /\.(js|css|png|jpe?g|webp|svg|woff2?)$/i.test(file));
if (!fs.existsSync(distDir)) {
  blockers.push("missing frontend/dist after build");
}
if (!fs.existsSync(indexFile)) {
  blockers.push("missing frontend/dist/index.html after build");
}
if (assets.length === 0) {
  blockers.push("frontend/dist has no static assets");
}

const artifact = {
  generatedAt: new Date().toISOString(),
  sourceEnvironment: sourceEnvironment || null,
  releaseCandidate: releaseCandidate || null,
  evidenceOperator: evidenceOperator || null,
  status: blockers.length === 0 ? "PASS" : "FAIL",
  frontendDir: path.relative(repoRoot, frontendDir),
  distDir: path.relative(repoRoot, distDir),
  build,
  summary: {
    files: files.length,
    assets: assets.length,
    totalBytes: files.reduce((sum, file) => sum + fs.statSync(file).size, 0),
    indexHtmlPresent: fs.existsSync(indexFile),
  },
  entrypoint: fs.existsSync(indexFile)
    ? {
        file: path.relative(repoRoot, indexFile),
        bytes: fs.statSync(indexFile).size,
        sha256: sha256(indexFile),
      }
    : null,
  largestFiles: files
    .map((file) => ({
      file: path.relative(repoRoot, file),
      bytes: fs.statSync(file).size,
      sha256: sha256(file),
    }))
    .sort((left, right) => right.bytes - left.bytes)
    .slice(0, 20),
  blockers,
};

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);

if (blockers.length > 0) {
  for (const blocker of blockers) {
    console.error(`[ddd-frontend-build-evidence] ${blocker}`);
  }
  console.error(`[ddd-frontend-build-evidence] wrote artifact to ${outputFile}`);
  process.exit(1);
}

console.log(`[ddd-frontend-build-evidence] frontend build evidence passed; files=${files.length}; artifact=${outputFile}`);
