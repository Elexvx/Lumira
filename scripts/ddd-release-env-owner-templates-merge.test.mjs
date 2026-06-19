#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-owner-templates-merge-"));

function runMerge(sourceDir, targetFile, reportFile) {
  return spawnSync("node", ["scripts/ddd-release-env-owner-templates-merge.mjs", sourceDir, targetFile], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_ENV_OWNER_TEMPLATES_MERGE_REPORT: reportFile,
    },
  });
}

const ownerDir = path.join(tmpDir, "owners");
const targetFile = path.join(tmpDir, "canonical.env");
const reportFile = path.join(tmpDir, "merge.json");
fs.mkdirSync(ownerDir);
fs.writeFileSync(path.join(ownerDir, "01-release-infra.env"), [
  "LUMIRA_BASE_URL=https://api.lumira-prod.internal",
  "DB_PASSWORD=abcdefghijklmnopqrstuvwxyz123456",
  "JWT_SECRET=__REQUIRED__",
  "",
].join("\n"));
fs.writeFileSync(path.join(ownerDir, "02-ai-owner.env"), [
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL=lumira-chat",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=__REQUIRED__",
  "",
].join("\n"));
fs.chmodSync(path.join(ownerDir, "01-release-infra.env"), 0o600);
fs.chmodSync(path.join(ownerDir, "02-ai-owner.env"), 0o600);
fs.writeFileSync(targetFile, [
  "LUMIRA_BASE_URL=__REQUIRED__",
  "DB_PASSWORD=__REQUIRED__",
  "JWT_SECRET=__REQUIRED__",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL=__REQUIRED__",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=__REQUIRED__",
  "",
].join("\n"));
fs.chmodSync(targetFile, 0o644);

const run = runMerge(ownerDir, targetFile, reportFile);
assert.equal(run.status, 0, run.stderr);
assert.match(run.stdout, /updates=3/);
const targetText = fs.readFileSync(targetFile, "utf8");
assert.match(targetText, /^LUMIRA_BASE_URL=https:\/\/api\.lumira-prod\.internal$/m);
assert.match(targetText, /^DB_PASSWORD=abcdefghijklmnopqrstuvwxyz123456$/m);
assert.match(targetText, /^LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL=lumira-chat$/m);
assert.match(targetText, /^JWT_SECRET=__REQUIRED__$/m);
const report = JSON.parse(fs.readFileSync(reportFile, "utf8"));
assert.equal(report.status, "PASS");
assert.equal(report.summary.concreteSourceKeys, 3);

const conflictDir = path.join(tmpDir, "conflict");
const conflictTarget = path.join(tmpDir, "conflict.env");
fs.mkdirSync(conflictDir);
fs.writeFileSync(path.join(conflictDir, "01.env"), "DB_URL=jdbc:mysql://one.internal:3306/lumira\n");
fs.writeFileSync(path.join(conflictDir, "02.env"), "DB_URL=jdbc:mysql://two.internal:3306/lumira\n");
fs.writeFileSync(conflictTarget, "DB_URL=__REQUIRED__\n");
fs.chmodSync(path.join(conflictDir, "01.env"), 0o600);
fs.chmodSync(path.join(conflictDir, "02.env"), 0o600);
const conflictRun = runMerge(conflictDir, conflictTarget, path.join(tmpDir, "conflict.json"));
assert.notEqual(conflictRun.status, 0);
assert.match(conflictRun.stderr, /conflicting owner template values/);

const invalidDir = path.join(tmpDir, "invalid");
const invalidTarget = path.join(tmpDir, "invalid.env");
fs.mkdirSync(invalidDir);
fs.writeFileSync(path.join(invalidDir, "01.env"), "LUMIRA_BASE_URL=http://localhost:8080\n");
fs.writeFileSync(invalidTarget, "LUMIRA_BASE_URL=__REQUIRED__\n");
fs.chmodSync(path.join(invalidDir, "01.env"), 0o600);
const invalidRun = runMerge(invalidDir, invalidTarget, path.join(tmpDir, "invalid.json"));
assert.notEqual(invalidRun.status, 0);
assert.match(invalidRun.stderr, /must be an HTTPS URL/);
assert.match(fs.readFileSync(invalidTarget, "utf8"), /^LUMIRA_BASE_URL=__REQUIRED__$/m);

const broadSecretDir = path.join(tmpDir, "broad-secret");
const broadSecretTarget = path.join(tmpDir, "broad-secret.env");
fs.mkdirSync(broadSecretDir);
fs.writeFileSync(path.join(broadSecretDir, "01.env"), "JWT_SECRET=abcdefghijklmnopqrstuvwxyz1234567890\n");
fs.writeFileSync(broadSecretTarget, "JWT_SECRET=__REQUIRED__\n");
fs.chmodSync(path.join(broadSecretDir, "01.env"), 0o644);
const broadSecretRun = runMerge(broadSecretDir, broadSecretTarget, path.join(tmpDir, "broad-secret.json"));
if (process.platform === "win32") {
  assert.equal(broadSecretRun.status, 0, broadSecretRun.stderr);
} else {
  assert.notEqual(broadSecretRun.status, 0);
  assert.match(broadSecretRun.stderr, /concrete secret values require chmod 600/);
}
assert.match(
  fs.readFileSync(broadSecretTarget, "utf8"),
  process.platform === "win32" ? /^JWT_SECRET=abcdefghijklmnopqrstuvwxyz1234567890$/m : /^JWT_SECRET=__REQUIRED__$/m,
);

console.log("[ddd-release-env-owner-templates-merge.test] ok");
