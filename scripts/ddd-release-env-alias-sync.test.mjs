#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-alias-sync-"));
const envFile = path.join(tmpDir, ".env.release.local");
const reportFile = path.join(tmpDir, "alias-sync.json");

fs.writeFileSync(envFile, [
  "DB_URL=jdbc:mysql://prod-db.internal:3306/lumira",
  "SPRING_DATASOURCE_URL=__REQUIRED__",
  "JWT_SECRET=abcdefghijklmnopqrstuvwxyz1234567890",
  "SAAS_SECURITY_JWT_SECRET=__REQUIRED__",
  "LUMIRA_BASE_URL=https://api.lumira-prod.internal",
  "DEPLOY_CHECK_BASE_URL=__REQUIRED__",
  "",
].join("\n"));
fs.chmodSync(envFile, 0o600);

const run = spawnSync("node", ["scripts/ddd-release-env-alias-sync.mjs", envFile], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ENV_ALIAS_SYNC_REPORT: reportFile,
  },
});
assert.equal(run.status, 0, run.stderr);
assert.match(run.stdout, /updates=3/);
const synced = fs.readFileSync(envFile, "utf8");
assert.match(synced, /^SPRING_DATASOURCE_URL=jdbc:mysql:\/\/prod-db\.internal:3306\/lumira$/m);
assert.match(synced, /^SAAS_SECURITY_JWT_SECRET=abcdefghijklmnopqrstuvwxyz1234567890$/m);
assert.match(synced, /^DEPLOY_CHECK_BASE_URL=https:\/\/api\.lumira-prod\.internal$/m);
if (process.platform !== "win32") {
  assert.equal((fs.statSync(envFile).mode & 0o777), 0o600);
}
const report = JSON.parse(fs.readFileSync(reportFile, "utf8"));
assert.equal(report.status, "PASS");
assert.equal(report.envFileSecurity.permissionCheckSkipped, process.platform === "win32");
assert.equal(report.summary.updates, 3);
assert.equal(report.summary.conflicts, 0);

const conflictEnvFile = path.join(tmpDir, ".env.conflict");
fs.writeFileSync(conflictEnvFile, [
  "DB_URL=jdbc:mysql://one.internal:3306/lumira",
  "SPRING_DATASOURCE_URL=jdbc:mysql://two.internal:3306/lumira",
  "",
].join("\n"));
fs.chmodSync(conflictEnvFile, 0o600);
const conflictRun = spawnSync("node", ["scripts/ddd-release-env-alias-sync.mjs", conflictEnvFile], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ENV_ALIAS_SYNC_REPORT: path.join(tmpDir, "conflict.json"),
  },
});
assert.notEqual(conflictRun.status, 0);
assert.match(conflictRun.stderr, /multiple concrete values/);

console.log("[ddd-release-env-alias-sync.test] ok");
