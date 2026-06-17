#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { requiredRollbackContexts } from "./ddd-rollback-drill-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-rollback-deferral-template-"));
const outputFile = path.join(directory, "rollback-deferrals.template.json");
const handoffDir = path.join(directory, "handoff");

const result = spawnSync("node", ["scripts/ddd-rollback-deferral-template.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_ROLLBACK_DRILL_DEFERRAL_FILE: outputFile,
    DDD_ROLLBACK_DRILL_DEFERRAL_HANDOFF_DIR: handoffDir,
    DDD_ROLLBACK_DRILL_RISK_ACCEPTED_BY: "release-owner",
    DDD_ROLLBACK_DRILL_DEFERRAL_EVIDENCE: "CHANGE-12345",
    DDD_ROLLBACK_DRILL_DEFERRAL_EXPIRES_AT: "2026-12-31T00:00:00.000Z",
  },
});

assert.equal(result.status, 0, result.stderr || result.stdout);
const template = JSON.parse(fs.readFileSync(outputFile, "utf8"));
assert.equal(typeof template.generatedAt, "string");
assert.deepEqual(template.contexts.map((entry) => entry.context), requiredRollbackContexts);
assert.equal(new Set(template.contexts.map((entry) => entry.context)).size, requiredRollbackContexts.length);

for (const entry of template.contexts) {
  assert.equal(entry.riskAcceptedBy, "release-owner");
  assert.equal(entry.deferralEvidence, "CHANGE-12345");
  assert.equal(entry.expiresAt, "2026-12-31T00:00:00.000Z");
  assert.equal(typeof entry.owner, "string");
  assert.equal(typeof entry.intendedRollbackAction, "string");
  assert.match(entry.notExercisableReason, new RegExp(`^${entry.context} rollback drill cannot be safely exercised`));
}

const handoffReadme = fs.readFileSync(path.join(handoffDir, "README.md"), "utf8");
assert.match(handoffReadme, /Rollback Deferral Owner Handoff/);
assert.match(handoffReadme, /DDD_ROLLBACK_DRILL_STRICT=true/);
assert.match(handoffReadme, /iam-owner/);

const iamHandoff = fs.readFileSync(path.join(handoffDir, "iam-owner.md"), "utf8");
assert.match(iamHandoff, /Rollback Deferral Handoff: iam-owner/);
assert.match(iamHandoff, /IAM/);
assert.match(iamHandoff, /permission snapshot rollback/);

const secondResult = spawnSync("node", ["scripts/ddd-rollback-deferral-template.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_ROLLBACK_DRILL_DEFERRAL_FILE: outputFile,
  },
});

assert.notEqual(secondResult.status, 0);
assert.match(secondResult.stderr, /already exists/);

console.log("[ddd-rollback-deferral-template.test] ok");
