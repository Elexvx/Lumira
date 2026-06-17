#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { requiredRollbackContexts } from "./ddd-rollback-drill-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const templateFile = path.join(repoRoot, "docs", "35-ddd-rollback-drill-template.json");
const deferralTemplateScript = fs.readFileSync(
  path.join(repoRoot, "scripts", "ddd-rollback-deferral-template.mjs"),
  "utf8",
);
const rollbackEvidenceScript = fs.readFileSync(
  path.join(repoRoot, "scripts", "ddd-rollback-drill-evidence.mjs"),
  "utf8",
);

function contextNames(artifact) {
  return (artifact.contexts || []).map((entry) => entry.context);
}

const template = JSON.parse(fs.readFileSync(templateFile, "utf8"));
assert.deepEqual(contextNames(template), requiredRollbackContexts);
assert.equal(new Set(contextNames(template)).size, requiredRollbackContexts.length);

for (const entry of template.contexts) {
  assert.equal(entry.status, "PASS", `${entry.context} template should show a PASS example`);
  assert.equal(typeof entry.rollbackAction, "string", `${entry.context} template needs rollbackAction`);
  assert.equal(typeof entry.drillEvidence, "string", `${entry.context} template needs drillEvidence guidance`);
  assert.equal(typeof entry.validatedAt, "string", `${entry.context} template needs validatedAt`);
}

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-rollback-init-"));
const outputFile = path.join(directory, "rollback-drill.json");
const result = spawnSync("node", ["scripts/ddd-init-rollback-drill.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_ROLLBACK_DRILL_FILE: outputFile,
    DDD_EVIDENCE_ENVIRONMENT: "sync-test",
    DDD_RELEASE_CANDIDATE: "sync-sha",
    DDD_EVIDENCE_OPERATOR: "sync-runner",
  },
});

assert.equal(result.status, 0, result.stderr || result.stdout);
const initialized = JSON.parse(fs.readFileSync(outputFile, "utf8"));
assert.deepEqual(contextNames(initialized), requiredRollbackContexts);
assert.equal(new Set(contextNames(initialized)).size, requiredRollbackContexts.length);
assert.equal(initialized.environment, "sync-test");
assert.equal(initialized.releaseVersion, "sync-sha");
assert.equal(initialized.operator, "sync-runner");
for (const entry of initialized.contexts) {
  assert.equal(entry.status, "TODO");
  assert.equal(entry.rollbackAction, null);
  assert.equal(entry.drillEvidence, null);
  assert.equal(entry.deferralEvidence, null);
  assert.equal(entry.expiresAt, null);
}

assert.match(
  deferralTemplateScript,
  /DDD_ROLLBACK_DRILL_DEFERRAL_HANDOFF_DIR/,
  "rollback deferral template must keep owner handoff output routing",
);
assert.match(
  deferralTemplateScript,
  /Rollback Deferral Owner Handoff/,
  "rollback deferral template must generate an owner handoff README",
);
assert.match(
  deferralTemplateScript,
  /rollback-deferrals-owner-handoff/,
  "rollback deferral template must keep the default owner handoff directory",
);
assert.match(
  rollbackEvidenceScript,
  /DDD_ROLLBACK_DRILL_CHECK_ENV/,
  "rollback evidence script must keep check-env mode for safe owner preflight",
);
assert.match(
  rollbackEvidenceScript,
  /DDD_ROLLBACK_DRILL_HANDOFF_FILE/,
  "rollback evidence script must allow handoff output routing",
);
assert.match(
  rollbackEvidenceScript,
  /Rollback Drill Evidence Handoff/,
  "rollback evidence script must generate a rollback owner handoff",
);

const deferralOutputFile = path.join(directory, "rollback-deferrals.template.json");
const deferralHandoffDir = path.join(directory, "rollback-deferrals-owner-handoff");
const deferralResult = spawnSync("node", ["scripts/ddd-rollback-deferral-template.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_ROLLBACK_DRILL_DEFERRAL_FILE: deferralOutputFile,
    DDD_ROLLBACK_DRILL_DEFERRAL_HANDOFF_DIR: deferralHandoffDir,
    DDD_ROLLBACK_DRILL_RISK_ACCEPTED_BY: "release-owner",
    DDD_ROLLBACK_DRILL_DEFERRAL_EVIDENCE: "CHANGE-ROLLBACK-123",
    DDD_ROLLBACK_DRILL_DEFERRAL_EXPIRES_AT: "2026-12-31T00:00:00.000Z",
  },
});
assert.equal(deferralResult.status, 0, deferralResult.stderr || deferralResult.stdout);
const deferralTemplate = JSON.parse(fs.readFileSync(deferralOutputFile, "utf8"));
assert.deepEqual(contextNames(deferralTemplate), requiredRollbackContexts);
const readme = fs.readFileSync(path.join(deferralHandoffDir, "README.md"), "utf8");
assert.match(readme, /Rollback Deferral Owner Handoff/);
assert.match(readme, /does not make rollback drills pass/);
assert.match(readme, /DDD_ROLLBACK_DRILL_STRICT=true/);
const expectedOwners = [...new Set(deferralTemplate.contexts.map((entry) => entry.owner))].sort();
for (const owner of expectedOwners) {
  const fileName = `${slug(owner)}.md`;
  assert.match(readme, new RegExp(`\\| ${escapeRegExp(owner)} \\|`));
  assert(fs.existsSync(path.join(deferralHandoffDir, fileName)), `${owner} handoff file must exist`);
}

console.log("[ddd-rollback-drill-sync.test] ok");

function slug(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "") || "owner";
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
