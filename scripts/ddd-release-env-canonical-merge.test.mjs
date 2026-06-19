#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-canonical-merge-"));
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

const run = spawnSync("node", ["scripts/ddd-release-env-canonical-merge.mjs", sourceFile, targetFile], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ENV_CANONICAL_MERGE_REPORT: reportFile,
  },
});
assert.equal(run.status, 0, run.stderr);
assert.match(run.stdout, /updates=2/);
assert.match(run.stdout, /unresolvedSourceKeys=1/);
const targetText = fs.readFileSync(targetFile, "utf8");
assert.match(targetText, /^DB_URL=jdbc:mysql:\/\/prod-db\.internal:3306\/lumira$/m);
assert.match(targetText, /^DB_PASSWORD=abcdefghijklmnopqrstuvwxyz123456$/m);
assert.match(targetText, /^JWT_SECRET=__REQUIRED__$/m);
if (process.platform !== "win32") {
  assert.equal((fs.statSync(targetFile).mode & 0o777), 0o600);
}
const report = JSON.parse(fs.readFileSync(reportFile, "utf8"));
assert.equal(report.status, "PASS");
assert.equal(report.summary.concreteSourceKeys, 2);
assert.equal(report.summary.unresolvedSourceKeys, 1);

const conflictSource = path.join(tmpDir, "conflict-source.env");
const conflictTarget = path.join(tmpDir, "conflict-target.env");
fs.writeFileSync(conflictSource, "DB_URL=jdbc:mysql://one.internal:3306/lumira\n");
fs.writeFileSync(conflictTarget, "DB_URL=jdbc:mysql://two.internal:3306/lumira\n");
fs.chmodSync(conflictSource, 0o600);
fs.chmodSync(conflictTarget, 0o600);
const conflictRun = spawnSync("node", ["scripts/ddd-release-env-canonical-merge.mjs", conflictSource, conflictTarget], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ENV_CANONICAL_MERGE_REPORT: path.join(tmpDir, "conflict.json"),
  },
});
assert.notEqual(conflictRun.status, 0);
assert.match(conflictRun.stderr, /target already has a different concrete value/);

const invalidSource = path.join(tmpDir, "invalid-source.env");
const invalidTarget = path.join(tmpDir, "invalid-target.env");
fs.writeFileSync(invalidSource, "LUMIRA_BASE_URL=http://localhost:8080\n");
fs.writeFileSync(invalidTarget, "LUMIRA_BASE_URL=__REQUIRED__\n");
fs.chmodSync(invalidSource, 0o600);
fs.chmodSync(invalidTarget, 0o600);
const invalidRun = spawnSync("node", ["scripts/ddd-release-env-canonical-merge.mjs", invalidSource, invalidTarget], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ENV_CANONICAL_MERGE_REPORT: path.join(tmpDir, "invalid.json"),
  },
});
assert.notEqual(invalidRun.status, 0);
assert.match(invalidRun.stderr, /LUMIRA_BASE_URL: must be an HTTPS URL/);
assert.match(fs.readFileSync(invalidTarget, "utf8"), /^LUMIRA_BASE_URL=__REQUIRED__$/m);

const broadSecretSource = path.join(tmpDir, "broad-secret-source.env");
const broadSecretTarget = path.join(tmpDir, "broad-secret-target.env");
fs.writeFileSync(broadSecretSource, "JWT_SECRET=abcdefghijklmnopqrstuvwxyz1234567890\n");
fs.writeFileSync(broadSecretTarget, "JWT_SECRET=__REQUIRED__\n");
fs.chmodSync(broadSecretSource, 0o644);
fs.chmodSync(broadSecretTarget, 0o600);
const broadSecretRun = spawnSync("node", ["scripts/ddd-release-env-canonical-merge.mjs", broadSecretSource, broadSecretTarget], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ENV_CANONICAL_MERGE_REPORT: path.join(tmpDir, "broad-secret.json"),
  },
});
if (process.platform === "win32") {
  assert.equal(broadSecretRun.status, 0, broadSecretRun.stderr);
} else {
  assert.notEqual(broadSecretRun.status, 0);
  assert.match(broadSecretRun.stderr, /concrete secret values and permissions are too broad/);
}
assert.match(
  fs.readFileSync(broadSecretTarget, "utf8"),
  process.platform === "win32" ? /^JWT_SECRET=abcdefghijklmnopqrstuvwxyz1234567890$/m : /^JWT_SECRET=__REQUIRED__$/m,
);

console.log("[ddd-release-env-canonical-merge.test] ok");
