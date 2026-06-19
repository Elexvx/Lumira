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

function runDefaults(root, envFile, extraEnv = {}) {
  return spawnSync("node", ["scripts/ddd-release-provenance-defaults.mjs", envFile], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_EVIDENCE_DIR: root,
      GITHUB_SHA: "",
      GITHUB_ACTOR: "",
      ...extraEnv,
    },
  });
}

const applyRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-provenance-defaults-apply-"));
const applyEnv = path.join(applyRoot, ".env.release.local");
write(applyEnv, [
  "DDD_EVIDENCE_ENVIRONMENT=__REQUIRED__",
  "DDD_RELEASE_CANDIDATE=__REQUIRED__",
  "DDD_EVIDENCE_OPERATOR=__REQUIRED__",
  "",
].join("\n"));
const applyResult = runDefaults(applyRoot, applyEnv, {
  DDD_RELEASE_CANDIDATE_DEFAULT: "2026.06.17-rc1",
  DDD_EVIDENCE_OPERATOR_DEFAULT: "release-owner",
});
assert.equal(applyResult.status, 0, applyResult.stderr || applyResult.stdout);
const applyText = fs.readFileSync(applyEnv, "utf8");
assert.match(applyText, /^DDD_EVIDENCE_ENVIRONMENT=__REQUIRED__$/m);
assert.match(applyText, /^DDD_RELEASE_CANDIDATE=2026.06.17-rc1$/m);
assert.match(applyText, /^DDD_EVIDENCE_OPERATOR=release-owner$/m);
const applyReport = JSON.parse(fs.readFileSync(path.join(applyRoot, "release", "release-provenance-defaults.json"), "utf8"));
assert.equal(applyReport.status, "PASS");
assert.equal(applyReport.redacted, true);
assert.equal(applyReport.sourceEnvironmentDefaulted, false);
assert.equal(applyReport.summary.updates, 2);
assert(!JSON.stringify(applyReport).includes("2026.06.17-rc1"), "provenance defaults report must not echo release candidate values");
assert(!JSON.stringify(applyReport).includes("release-owner"), "provenance defaults report must not echo operator values");

const dryRunRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-provenance-defaults-dry-run-"));
const dryRunEnv = path.join(dryRunRoot, ".env.release.local");
write(dryRunEnv, "DDD_RELEASE_CANDIDATE=__REQUIRED__\nDDD_EVIDENCE_OPERATOR=__REQUIRED__\n");
const dryRunResult = runDefaults(dryRunRoot, dryRunEnv, {
  DDD_RELEASE_CANDIDATE_DEFAULT: "2026.06.17-rc2",
  DDD_EVIDENCE_OPERATOR_DEFAULT: "release-operator",
  DDD_RELEASE_PROVENANCE_DEFAULTS_DRY_RUN: "1",
});
assert.equal(dryRunResult.status, 0, dryRunResult.stderr || dryRunResult.stdout);
assert.match(fs.readFileSync(dryRunEnv, "utf8"), /^DDD_RELEASE_CANDIDATE=__REQUIRED__$/m);
const dryRunReport = JSON.parse(fs.readFileSync(path.join(dryRunRoot, "release", "release-provenance-defaults.json"), "utf8"));
assert.equal(dryRunReport.dryRun, true);
assert.equal(dryRunReport.summary.updates, 2);

const localRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-provenance-defaults-local-"));
const localEnv = path.join(localRoot, ".env.release.local");
write(localEnv, "DDD_RELEASE_CANDIDATE=__REQUIRED__\nDDD_EVIDENCE_OPERATOR=__REQUIRED__\n");
const localResult = runDefaults(localRoot, localEnv, {
  DDD_RELEASE_CANDIDATE_DEFAULT: "local-worktree",
  DDD_EVIDENCE_OPERATOR_DEFAULT: "local-operator",
});
assert.notEqual(localResult.status, 0);
assert.match(localResult.stderr, /DDD_RELEASE_CANDIDATE: must not be a local diagnostic placeholder/);
assert.match(localResult.stderr, /DDD_EVIDENCE_OPERATOR: must not be a local diagnostic placeholder/);

const conflictRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-provenance-defaults-conflict-"));
const conflictEnv = path.join(conflictRoot, ".env.release.local");
write(conflictEnv, "DDD_RELEASE_CANDIDATE=2026.06.17-rc0\nDDD_EVIDENCE_OPERATOR=release-owner\n");
const conflictResult = runDefaults(conflictRoot, conflictEnv, {
  DDD_RELEASE_CANDIDATE_DEFAULT: "2026.06.17-rc3",
  DDD_EVIDENCE_OPERATOR_DEFAULT: "release-owner",
});
assert.notEqual(conflictResult.status, 0);
assert.match(conflictResult.stderr, /DDD_RELEASE_CANDIDATE: target already has a different concrete value/);

const permissionRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-provenance-defaults-permission-"));
const permissionEnv = path.join(permissionRoot, ".env.release.local");
write(permissionEnv, "DDD_RELEASE_CANDIDATE=__REQUIRED__\n", 0o644);
const permissionResult = runDefaults(permissionRoot, permissionEnv, {
  DDD_RELEASE_CANDIDATE_DEFAULT: "2026.06.17-rc4",
  DDD_EVIDENCE_OPERATOR_DEFAULT: "release-owner",
});
if (process.platform === "win32") {
  assert.equal(permissionResult.status, 0, permissionResult.stderr);
} else {
  assert.notEqual(permissionResult.status, 0);
  assert.match(permissionResult.stderr, /env file permissions are too broad/);
}

console.log("[ddd-release-provenance-defaults.test] ok");
