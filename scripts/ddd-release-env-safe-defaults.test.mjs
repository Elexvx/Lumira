#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");

function write(file, text, mode = 0o600) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, text);
  fs.chmodSync(file, mode);
}

function runSafeDefaults(root, envFile, extraEnv = {}) {
  return spawnSync("node", ["scripts/ddd-release-env-safe-defaults.mjs", envFile], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_EVIDENCE_DIR: root,
      ...extraEnv,
    },
  });
}

const applyRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-safe-defaults-apply-"));
const applyEnv = path.join(applyRoot, ".env.release.local");
write(applyEnv, [
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL=__REQUIRED__",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL=__REQUIRED__",
  "LUMIRA_FILE_SECURITY_SCAN_MODE=__REQUIRED__",
  "LUMIRA_FILE_OCR_MODE=__REQUIRED__",
  "UPLOAD_STORAGE_ROOT=__REQUIRED__",
  "SAAS_EVENT_OUTBOX_DISPATCHER=__REQUIRED__",
  "REDIS_PORT=__REQUIRED__",
  "LUMIRA_AI_PROVIDER=__REQUIRED__",
  "JWT_SECRET=__REQUIRED__",
  "",
].join("\n"));
const applyResult = runSafeDefaults(applyRoot, applyEnv);
assert.equal(applyResult.status, 0, applyResult.stderr || applyResult.stdout);
const applyText = fs.readFileSync(applyEnv, "utf8");
assert.match(applyText, /^LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL=gpt-4o-mini$/m);
assert.match(applyText, /^LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL=text-embedding-3-small$/m);
assert.match(applyText, /^LUMIRA_FILE_SECURITY_SCAN_MODE=CLAMAV$/m);
assert.match(applyText, /^LUMIRA_FILE_OCR_MODE=TESSERACT$/m);
assert.match(applyText, /^UPLOAD_STORAGE_ROOT=\/opt\/lumira\/data\/uploads$/m);
assert.match(applyText, /^SAAS_EVENT_OUTBOX_DISPATCHER=redis-stream$/m);
assert.match(applyText, /^REDIS_PORT=6379$/m);
assert.match(applyText, /^LUMIRA_AI_PROVIDER=openai-compatible$/m);
assert.match(applyText, /^JWT_SECRET=__REQUIRED__$/m);
const applyReport = JSON.parse(fs.readFileSync(path.join(applyRoot, "release", "release-env-safe-defaults.json"), "utf8"));
assert.equal(applyReport.status, "PASS");
assert.equal(applyReport.summary.updates, 8);
assert.equal(applyReport.summary.additions, 0);
assert(!JSON.stringify(applyReport).includes("gpt-4o-mini"), "safe defaults report should not echo concrete values");
assert(!JSON.stringify(applyReport).includes("openai-compatible"), "safe defaults report should not echo concrete values");

const dryRunRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-safe-defaults-dry-run-"));
const dryRunEnv = path.join(dryRunRoot, ".env.release.local");
write(dryRunEnv, "LUMIRA_FILE_OCR_MODE=__REQUIRED__\n");
const dryRunResult = runSafeDefaults(dryRunRoot, dryRunEnv, { DDD_RELEASE_ENV_SAFE_DEFAULTS_DRY_RUN: "1" });
assert.equal(dryRunResult.status, 0, dryRunResult.stderr || dryRunResult.stdout);
assert.match(fs.readFileSync(dryRunEnv, "utf8"), /^LUMIRA_FILE_OCR_MODE=__REQUIRED__$/m);
const dryRunReport = JSON.parse(fs.readFileSync(path.join(dryRunRoot, "release", "release-env-safe-defaults.json"), "utf8"));
assert.equal(dryRunReport.dryRun, true);
assert.equal(dryRunReport.summary.updates, 1);
assert.equal(dryRunReport.summary.additions, 7);

const conflictRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-safe-defaults-conflict-"));
const conflictEnv = path.join(conflictRoot, ".env.release.local");
write(conflictEnv, "LUMIRA_FILE_OCR_MODE=CUSTOM_OCR\n");
const conflictResult = runSafeDefaults(conflictRoot, conflictEnv);
assert.notEqual(conflictResult.status, 0);
assert.match(conflictResult.stderr, /LUMIRA_FILE_OCR_MODE: target already has a different concrete value/);
assert.match(fs.readFileSync(conflictEnv, "utf8"), /^LUMIRA_FILE_OCR_MODE=CUSTOM_OCR$/m);

const permissionRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-safe-defaults-permission-"));
const permissionEnv = path.join(permissionRoot, ".env.release.local");
write(permissionEnv, "LUMIRA_FILE_OCR_MODE=__REQUIRED__\n", 0o644);
const permissionResult = runSafeDefaults(permissionRoot, permissionEnv);
if (process.platform === "win32") {
  assert.equal(permissionResult.status, 0, permissionResult.stderr);
} else {
  assert.notEqual(permissionResult.status, 0);
  assert.match(permissionResult.stderr, /env file permissions are too broad/);
}

console.log("[ddd-release-env-safe-defaults.test] ok");
