#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { performance } from "node:perf_hooks";
import { collectProvenanceIssues, redactLocalPaths } from "./ddd-release-evidence-utils.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const frontendDir = path.join(repoRoot, "frontend");
const outputDir = process.env.DDD_FRONTEND_STATIC_DIR
  ? path.resolve(process.env.DDD_FRONTEND_STATIC_DIR)
  : path.join(repoRoot, "artifacts", "ddd", "frontend");
const outputFile = process.env.DDD_FRONTEND_STATIC_REPORT
  ? path.resolve(process.env.DDD_FRONTEND_STATIC_REPORT)
  : path.join(outputDir, "frontend-static-evidence.json");
const skipRun = process.env.DDD_FRONTEND_STATIC_SKIP_RUN === "true";
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === "true" || process.env.DDD_FRONTEND_STATIC_STRICT === "true";
const sourceEnvironment = process.env.DDD_FRONTEND_STATIC_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";

const commands = [
  { name: "lint", args: ["pnpm", "--dir", "frontend", "lint"] },
  { name: "typecheck", args: ["pnpm", "--dir", "frontend", "typecheck"] },
  { name: "unit", args: ["pnpm", "--dir", "frontend", "test"] },
];

function tail(text, max = 12000) {
  if (!text) {
    return "";
  }
  const value = text.length <= max ? text : text.slice(text.length - max);
  return redactLocalPaths(value, { repoRoot, homeDir: process.env.HOME || "" });
}

function runCommand(command) {
  if (skipRun) {
    return {
      name: command.name,
      command: ["corepack", ...command.args].join(" "),
      skipped: true,
      status: "SKIPPED",
      exitCode: null,
      durationMs: 0,
      stdoutTail: "",
      stderrTail: "",
    };
  }
  const startedAt = performance.now();
  const result = spawnSync("corepack", command.args, {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 20 * 1024 * 1024,
  });
  const durationMs = Math.round((performance.now() - startedAt) * 100) / 100;
  return {
    name: command.name,
    command: ["corepack", ...command.args].join(" "),
    skipped: false,
    status: result.status === 0 ? "PASS" : "FAIL",
    exitCode: result.status,
    signal: result.signal || null,
    durationMs,
    stdoutTail: tail(result.stdout),
    stderrTail: tail(result.stderr),
  };
}

const blockers = [];
if (strictEvidence) {
  for (const issue of collectProvenanceIssues({ sourceEnvironment, releaseCandidate, evidenceOperator })) {
    blockers.push(`frontend static provenance ${issue}`);
  }
}
if (!fs.existsSync(frontendDir)) {
  blockers.push("missing frontend directory");
}

const results = blockers.length === 0 ? commands.map(runCommand) : [];
for (const result of results) {
  if (result.status !== "PASS") {
    blockers.push(`${result.name} did not pass: status=${result.status}, exitCode=${result.exitCode}`);
  }
  if (strictEvidence && result.skipped) {
    blockers.push(`${result.name} was skipped; strict release requires lint/typecheck/unit to run`);
  }
}

const artifact = {
  generatedAt: new Date().toISOString(),
  sourceEnvironment: sourceEnvironment || null,
  releaseCandidate: releaseCandidate || null,
  evidenceOperator: evidenceOperator || null,
  status: blockers.length === 0 ? "PASS" : "FAIL",
  frontendDir: path.relative(repoRoot, frontendDir),
  skipRun,
  summary: {
    commands: results.length,
    passed: results.filter((result) => result.status === "PASS").length,
    failed: results.filter((result) => result.status === "FAIL").length,
    skipped: results.filter((result) => result.status === "SKIPPED").length,
    durationMs: Math.round(results.reduce((sum, result) => sum + result.durationMs, 0) * 100) / 100,
  },
  results,
  blockers,
};

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);

if (blockers.length > 0) {
  for (const blocker of blockers) {
    console.error(`[ddd-frontend-static-evidence] ${blocker}`);
  }
  console.error(`[ddd-frontend-static-evidence] wrote artifact to ${outputFile}`);
  process.exit(1);
}

console.log(`[ddd-frontend-static-evidence] frontend static evidence passed; commands=${results.length}; artifact=${outputFile}`);
