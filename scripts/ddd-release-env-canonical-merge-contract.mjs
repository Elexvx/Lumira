#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const scriptPath = path.join(repoRoot, "scripts", "ddd-release-env-canonical-merge.mjs");
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function runMerge(sourceFile, targetFile, reportFile, env = {}) {
  return spawnSync("node", ["scripts/ddd-release-env-canonical-merge.mjs", sourceFile, targetFile], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_ENV_CANONICAL_MERGE_REPORT: reportFile, ...env },
  });
}

const source = fs.readFileSync(scriptPath, "utf8");
for (const snippet of [
  "DDD_RELEASE_ENV_FILE or second CLI argument is required",
  "target env file permissions are too broad",
  "canonical source file has concrete secret values and permissions are too broad",
  "target already has a different concrete value",
  "DDD_RELEASE_ENV_CANONICAL_MERGE_DRY_RUN",
  "DDD_RELEASE_ENV_CANONICAL_MERGE_FORCE",
  "fs.chmodSync(targetFile, 0o600)",
  "must be an HTTPS URL",
  "must not point to localhost or loopback",
]) {
  if (!source.includes(snippet)) addFailure(`canonical merge script must include ${snippet}`);
}
if (/^\s*source\s+/m.test(source)) addFailure("canonical merge script must not source env files");

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-canonical-merge-contract-"));
try {
  const sourceFile = path.join(tmpDir, "canonical.env");
  const targetFile = path.join(tmpDir, ".env.release.local");
  const reportFile = path.join(tmpDir, "merge.json");
  fs.writeFileSync(sourceFile, [
    "DB_URL=jdbc:mysql://prod-db.internal:3306/lumira",
    "DB_PASSWORD=abcdefghijklmnopqrstuvwxyz123456",
    "JWT_SECRET=__REQUIRED__",
    "",
  ].join("\n"));
  fs.writeFileSync(targetFile, [
    "DB_URL=__REQUIRED__",
    "DB_PASSWORD=__REQUIRED__",
    "JWT_SECRET=__REQUIRED__",
    "",
  ].join("\n"));
  fs.chmodSync(sourceFile, 0o600);
  fs.chmodSync(targetFile, 0o600);
  const passRun = runMerge(sourceFile, targetFile, reportFile);
  if (passRun.status !== 0) addFailure(`canonical merge should pass for valid concrete source values: ${passRun.stderr}`);
  const report = readJson(reportFile);
  if (report.status !== "PASS") addFailure("canonical merge report must be PASS");
  if (report.summary?.updates !== 2) addFailure("canonical merge must update two target placeholder values");
  if (report.summary?.unresolvedSourceKeys !== 1) addFailure("canonical merge must keep unresolved source keys unresolved");
  const targetText = fs.readFileSync(targetFile, "utf8");
  if (!/^DB_URL=jdbc:mysql:\/\/prod-db\.internal:3306\/lumira$/m.test(targetText)) addFailure("canonical merge must update DB_URL");
  if (!/^DB_PASSWORD=abcdefghijklmnopqrstuvwxyz123456$/m.test(targetText)) addFailure("canonical merge must update DB_PASSWORD");
  if (!/^JWT_SECRET=__REQUIRED__$/m.test(targetText)) addFailure("canonical merge must not copy unresolved source keys");
  if (process.platform !== "win32" && (fs.statSync(targetFile).mode & 0o777) !== 0o600) addFailure("canonical merge must chmod target env file to 600 after writes");

  const drySource = path.join(tmpDir, "dry-source.env");
  const dryTarget = path.join(tmpDir, "dry-target.env");
  const dryReport = path.join(tmpDir, "dry.json");
  fs.writeFileSync(drySource, "DB_PASSWORD=abcdefghijklmnopqrstuvwxyz123456\n");
  fs.writeFileSync(dryTarget, "DB_PASSWORD=__REQUIRED__\n");
  fs.chmodSync(drySource, 0o600);
  fs.chmodSync(dryTarget, 0o600);
  const beforeDry = fs.readFileSync(dryTarget, "utf8");
  const dryRun = runMerge(drySource, dryTarget, dryReport, { DDD_RELEASE_ENV_CANONICAL_MERGE_DRY_RUN: "1" });
  if (dryRun.status !== 0) addFailure(`canonical merge dry-run should pass: ${dryRun.stderr}`);
  if (fs.readFileSync(dryTarget, "utf8") !== beforeDry) addFailure("canonical merge dry-run must not mutate target file");
  if (readJson(dryReport).dryRun !== true) addFailure("canonical merge dry-run report must set dryRun=true");

  const conflictSource = path.join(tmpDir, "conflict-source.env");
  const conflictTarget = path.join(tmpDir, "conflict-target.env");
  const conflictReport = path.join(tmpDir, "conflict.json");
  fs.writeFileSync(conflictSource, "DB_URL=jdbc:mysql://one.internal:3306/lumira\n");
  fs.writeFileSync(conflictTarget, "DB_URL=jdbc:mysql://two.internal:3306/lumira\n");
  fs.chmodSync(conflictSource, 0o600);
  fs.chmodSync(conflictTarget, 0o600);
  const conflictRun = runMerge(conflictSource, conflictTarget, conflictReport);
  if (conflictRun.status === 0) addFailure("canonical merge must fail on target concrete value conflicts without force");
  if (readJson(conflictReport).summary?.conflicts !== 1) addFailure("canonical merge conflict report must record one conflict");

  const invalidSource = path.join(tmpDir, "invalid-source.env");
  const invalidTarget = path.join(tmpDir, "invalid-target.env");
  const invalidReport = path.join(tmpDir, "invalid.json");
  fs.writeFileSync(invalidSource, "LUMIRA_BASE_URL=http://localhost:8080\n");
  fs.writeFileSync(invalidTarget, "LUMIRA_BASE_URL=__REQUIRED__\n");
  fs.chmodSync(invalidSource, 0o600);
  fs.chmodSync(invalidTarget, 0o600);
  const invalidRun = runMerge(invalidSource, invalidTarget, invalidReport);
  if (invalidRun.status === 0) addFailure("canonical merge must fail invalid source URL values");
  const invalid = readJson(invalidReport);
  if (!invalid.blockers.some((blocker) => blocker.includes("must be an HTTPS URL"))) addFailure("canonical merge invalid report must include HTTPS blocker");
  if (!invalid.blockers.some((blocker) => blocker.includes("must not point to localhost"))) addFailure("canonical merge invalid report must include non-local blocker");

  const broadSource = path.join(tmpDir, "broad-source.env");
  const broadTarget = path.join(tmpDir, "broad-target.env");
  const broadReport = path.join(tmpDir, "broad.json");
  fs.writeFileSync(broadSource, "JWT_SECRET=abcdefghijklmnopqrstuvwxyz1234567890\n");
  fs.writeFileSync(broadTarget, "JWT_SECRET=__REQUIRED__\n");
  fs.chmodSync(broadSource, 0o644);
  fs.chmodSync(broadTarget, 0o600);
  const broadRun = runMerge(broadSource, broadTarget, broadReport);
  const broad = readJson(broadReport);
  if (process.platform === "win32") {
    if (broad.sourceSecurity?.permissionCheckSkipped !== true) addFailure("canonical merge broad report must mark permissionCheckSkipped=true on Windows");
  } else {
    if (broadRun.status === 0) addFailure("canonical merge must fail broad source permissions with concrete secret values");
    if (!broad.blockers.some((blocker) => blocker.includes("permissions are too broad"))) addFailure("canonical merge broad report must include permission blocker");
  }
} finally {
  fs.rmSync(tmpDir, { recursive: true, force: true });
}

if (failures.length > 0) {
  throw new Error(`release env canonical merge contract failed: ${failures.join("; ")}`);
}

console.log("[ddd-release-env-canonical-merge-contract] ok");
