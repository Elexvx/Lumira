#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const scriptPath = path.join(repoRoot, "scripts", "ddd-release-env-alias-sync.mjs");
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function runAliasSync(args = [], env = {}) {
  return spawnSync("node", ["scripts/ddd-release-env-alias-sync.mjs", ...args], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, ...env },
  });
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const source = fs.readFileSync(scriptPath, "utf8");
for (const snippet of [
  "DDD_RELEASE_ENV_FILE or first CLI argument is required",
  "fs.chmodSync(envFile, 0o600)",
  "DDD_RELEASE_ENV_ALIAS_SYNC_DRY_RUN",
  "DDD_RELEASE_ENV_ALIAS_SYNC_FORCE",
  "multiple concrete values",
  "env file permissions are too broad",
  "duplicate env key",
]) {
  if (!source.includes(snippet)) addFailure(`alias sync script must include ${snippet}`);
}
if (/^\s*source\s+/m.test(source)) addFailure("alias sync script must not source env files");

const missingTargetReport = path.join(os.tmpdir(), `lumira-alias-sync-missing-${process.pid}.json`);
const missingTargetRun = runAliasSync([], { DDD_RELEASE_ENV_ALIAS_SYNC_REPORT: missingTargetReport, DDD_RELEASE_ENV_FILE: "" });
if (missingTargetRun.status === 0) addFailure("alias sync must fail without DDD_RELEASE_ENV_FILE or CLI argument");
if (fs.existsSync(missingTargetReport)) {
  const report = readJson(missingTargetReport);
  if (report.status !== "FAIL") addFailure("missing target report must be FAIL");
  fs.rmSync(missingTargetReport, { force: true });
}

const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-alias-sync-contract-"));
try {
  const envFile = path.join(tempDir, ".env.release.local");
  const reportFile = path.join(tempDir, "alias-sync.json");
  fs.writeFileSync(envFile, [
    "DB_PASSWORD=prod-password-value",
    "SPRING_DATASOURCE_PASSWORD=__REQUIRED__",
    "LUMIRA_BASE_URL=https://example.internal",
    "",
  ].join("\n"));
  fs.chmodSync(envFile, 0o600);
  const passRun = runAliasSync([envFile], { DDD_RELEASE_ENV_ALIAS_SYNC_REPORT: reportFile });
  if (passRun.status !== 0) addFailure(`alias sync should pass on placeholder aliases: ${passRun.stderr}`);
  const report = readJson(reportFile);
  if (report.status !== "PASS") addFailure("alias sync pass report must be PASS");
  if (report.envFileSecurity?.permissionSafe !== true) addFailure("alias sync must record safe env file permissions");
  if ((report.summary?.updates || 0) < 1) addFailure("alias sync must update placeholder aliases from concrete canonical values");
  const updatedText = fs.readFileSync(envFile, "utf8");
  if (!/^SPRING_DATASOURCE_PASSWORD=prod-password-value$/m.test(updatedText)) addFailure("alias sync must write synced alias value");
  const modeOctal = (fs.statSync(envFile).mode & 0o777).toString(8).padStart(3, "0");
  if (process.platform !== "win32" && modeOctal !== "600") addFailure("alias sync must preserve chmod 600 after writes");
  if (process.platform === "win32" && report.envFileSecurity?.permissionCheckSkipped !== true) addFailure("alias sync must mark permission check skipped on Windows");

  const dryRunFile = path.join(tempDir, ".env.dry-run.local");
  const dryRunReport = path.join(tempDir, "alias-sync-dry-run.json");
  fs.writeFileSync(dryRunFile, "DB_PASSWORD=prod-password-value\nSPRING_DATASOURCE_PASSWORD=__REQUIRED__\n");
  fs.chmodSync(dryRunFile, 0o600);
  const beforeDryRun = fs.readFileSync(dryRunFile, "utf8");
  const dryRun = runAliasSync([dryRunFile], {
    DDD_RELEASE_ENV_ALIAS_SYNC_REPORT: dryRunReport,
    DDD_RELEASE_ENV_ALIAS_SYNC_DRY_RUN: "1",
  });
  if (dryRun.status !== 0) addFailure(`alias sync dry-run should pass: ${dryRun.stderr}`);
  assert.equal(fs.readFileSync(dryRunFile, "utf8"), beforeDryRun);
  if (readJson(dryRunReport).dryRun !== true) addFailure("alias sync dry-run report must mark dryRun=true");

  const conflictFile = path.join(tempDir, ".env.conflict.local");
  const conflictReport = path.join(tempDir, "alias-sync-conflict.json");
  fs.writeFileSync(conflictFile, "DB_PASSWORD=left-secret\nMYSQL_PASSWORD=right-secret\n");
  fs.chmodSync(conflictFile, 0o600);
  const conflictRun = runAliasSync([conflictFile], { DDD_RELEASE_ENV_ALIAS_SYNC_REPORT: conflictReport });
  if (conflictRun.status === 0) addFailure("alias sync must fail on conflicting concrete alias values without force");
  const conflict = readJson(conflictReport);
  if (conflict.status !== "FAIL" || (conflict.summary?.conflicts || 0) < 1) addFailure("alias sync conflict report must record conflicts");

  const broadFile = path.join(tempDir, ".env.broad.local");
  const broadReport = path.join(tempDir, "alias-sync-broad.json");
  fs.writeFileSync(broadFile, "DB_PASSWORD=prod-password-value\n");
  fs.chmodSync(broadFile, 0o644);
  const broadRun = runAliasSync([broadFile], { DDD_RELEASE_ENV_ALIAS_SYNC_REPORT: broadReport });
  const broad = readJson(broadReport);
  if (process.platform === "win32") {
    if (broad.envFileSecurity?.permissionCheckSkipped !== true) addFailure("alias sync broad permission report must mark permissionCheckSkipped=true on Windows");
  } else {
    if (broadRun.status === 0) addFailure("alias sync must fail when env file permissions are broad");
    if (broad.envFileSecurity?.permissionSafe !== false) addFailure("alias sync broad permission report must mark permissionSafe=false");
  }
} finally {
  fs.rmSync(tempDir, { recursive: true, force: true });
}

if (failures.length > 0) {
  throw new Error(`release env alias sync contract failed: ${failures.join("; ")}`);
}

console.log("[ddd-release-env-alias-sync-contract] ok");
