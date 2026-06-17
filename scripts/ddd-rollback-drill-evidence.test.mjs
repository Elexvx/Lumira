#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import {
  requiredRollbackContexts,
  requiredRollbackEvidenceChecklist,
} from "./ddd-rollback-drill-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-rollback-evidence-"));
const artifactFile = path.join(directory, "rollback-drill.json");

fs.writeFileSync(artifactFile, `${JSON.stringify({
  generatedAt: "2026-06-14T00:00:00.000Z",
  status: "DRAFT",
  environment: null,
  releaseVersion: null,
  operator: null,
  contexts: requiredRollbackContexts.map((context) => ({
    context,
    status: "TODO",
    rollbackAction: null,
    drillEvidence: null,
    validatedAt: null,
    notExercisableReason: null,
    riskAcceptedBy: null,
    expiresAt: null,
  })),
}, null, 2)}\n`);

const result = spawnSync("node", ["scripts/ddd-rollback-drill-evidence.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_ROLLBACK_DRILL_FILE: artifactFile,
    DDD_EVIDENCE_ENVIRONMENT: "staging-prod-equivalent",
    DDD_RELEASE_CANDIDATE: "rc-20260614",
    DDD_EVIDENCE_OPERATOR: "release-owner",
  },
});

assert.notEqual(result.status, 0);
const artifact = JSON.parse(fs.readFileSync(artifactFile, "utf8"));
assert.notEqual(artifact.generatedAt, "2026-06-14T00:00:00.000Z");
assert.equal(artifact.generatedAt, artifact.checkedAt);
assert.equal(artifact.environment, "staging-prod-equivalent");
assert.equal(artifact.releaseVersion, "rc-20260614");
assert.equal(artifact.operator, "release-owner");
assert.equal(artifact.status, "FAIL");
assert.ok(!artifact.blockers.includes("environment is required"));
assert.ok(!artifact.blockers.includes("releaseVersion is required"));
assert.ok(!artifact.blockers.includes("operator is required"));
assert.ok(artifact.blockers.includes("IAM status must be PASS or DEFERRED"));
assert.equal(artifact.contextDiagnostics.length, requiredRollbackContexts.length);
assert.equal(artifact.contextDiagnostics.find((entry) => entry.context === "Payment").owner, "payment-owner");
assert.ok(artifact.contextDiagnostics.find((entry) => entry.context === "Payment").evidenceRequirements.length > 0);
assert.equal(artifact.contextDiagnostics.find((entry) => entry.context === "IAM").ready, false);
assert.equal(artifact.summary.requiredContexts, requiredRollbackContexts.length);
assert.equal(artifact.summary.todoContexts, requiredRollbackContexts.length);
assert.equal(artifact.summary.blockers, artifact.blockers.length);
assert.equal(artifact.summary.missingEvidenceDiagnostics, requiredRollbackContexts.length);

const checkOnlyArtifactFile = path.join(directory, "rollback-drill-check-only.json");
const checkOnlyHandoffFile = path.join(directory, "rollback-drill-handoff.md");
const checkOnlyOriginal = {
  generatedAt: "2026-06-14T00:00:00.000Z",
  status: "DRAFT",
  environment: "staging-prod-equivalent",
  releaseVersion: "rc-20260614",
  operator: "release-owner",
  contexts: requiredRollbackContexts.map((context) => ({
    context,
    status: "TODO",
    rollbackAction: null,
    drillEvidence: null,
    validatedAt: null,
    deferralEvidence: null,
  })),
};
fs.writeFileSync(checkOnlyArtifactFile, `${JSON.stringify(checkOnlyOriginal, null, 2)}\n`);
const checkOnlyResult = spawnSync("node", ["scripts/ddd-rollback-drill-evidence.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_ROLLBACK_DRILL_FILE: checkOnlyArtifactFile,
    DDD_ROLLBACK_DRILL_CHECK_ENV: "true",
    DDD_ROLLBACK_DRILL_HANDOFF_FILE: checkOnlyHandoffFile,
  },
});
assert.notEqual(checkOnlyResult.status, 0);
assert.deepEqual(JSON.parse(fs.readFileSync(checkOnlyArtifactFile, "utf8")), checkOnlyOriginal);
const checkOnlyHandoff = fs.readFileSync(checkOnlyHandoffFile, "utf8");
assert.match(checkOnlyHandoff, /Rollback Drill Evidence Handoff/);
assert.match(checkOnlyHandoff, /IAM/);
assert.match(checkOnlyHandoff, /Fast path:/);
assert.match(checkOnlyHandoff, /Owner runbook:/);
assert.match(checkOnlyHandoff, /Evidence checklist:/);
assert.match(checkOnlyHandoff, /pass-rollback-drill-evidence/);
assert.match(checkOnlyHandoff, /deferred-risk-acceptance-evidence/);
assert.match(checkOnlyHandoff, /\| iam-owner \| MISSING \| IAM \| IAM \|/);
assert.match(checkOnlyHandoff, /Validation commands:/);
assert.match(checkOnlyHandoff, /DDD_ROLLBACK_DRILL_STRICT=true node scripts\/ddd-rollback-drill-evidence\.mjs/);
assert.match(checkOnlyHandoff, /DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts\/ddd\/release\/release-final-go-no-go-gate\.sh/);
const checkOnlyHandoffJson = JSON.parse(fs.readFileSync(path.join(directory, "rollback-drill-handoff.json"), "utf8"));
assert.equal(checkOnlyHandoffJson.redacted, true);
assert.equal(checkOnlyHandoffJson.status, "MISSING");
assert.equal(checkOnlyHandoffJson.summary.contexts, requiredRollbackContexts.length);
assert.equal(checkOnlyHandoffJson.summary.missing, requiredRollbackContexts.length);
assert.deepEqual(checkOnlyHandoffJson.evidenceChecklist, requiredRollbackEvidenceChecklist);
assert.equal(checkOnlyHandoffJson.fastPath.commands.at(-1), "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh");
assert(checkOnlyHandoffJson.ownerRunbook.some((owner) => owner.owner === "iam-owner" && owner.status === "MISSING" && owner.missingContexts.includes("IAM")));
assert(checkOnlyHandoffJson.validationCommands.includes("DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs"));
assert(checkOnlyHandoffJson.validationCommands.includes("DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh"));
const checkOnlyHandoffCsv = fs.readFileSync(path.join(directory, "rollback-drill-handoff.csv"), "utf8");
assert.match(checkOnlyHandoffCsv, /^owner,context,currentStatus,envStatus,requiredEnvKeys,missingEnvKeys,evidenceRequirements,nextCommand,action/m);
assert.match(checkOnlyHandoffCsv, /iam-owner,IAM,TODO,MISSING/);

const deferredArtifactFile = path.join(directory, "rollback-drill-deferred.json");
const deferralFile = path.join(directory, "rollback-deferrals.json");
fs.writeFileSync(deferredArtifactFile, `${JSON.stringify({
  generatedAt: "2026-06-14T00:00:00.000Z",
  status: "DRAFT",
  environment: "staging-prod-equivalent",
  releaseVersion: "rc-20260614",
  operator: "release-owner",
  contexts: requiredRollbackContexts.map((context) => ({
    context,
    status: "TODO",
    rollbackAction: null,
    drillEvidence: null,
    validatedAt: null,
    notExercisableReason: null,
    riskAcceptedBy: null,
    deferralEvidence: null,
    expiresAt: null,
  })),
}, null, 2)}\n`);
fs.writeFileSync(deferralFile, `${JSON.stringify({
  contexts: requiredRollbackContexts.map((context) => ({
    context,
    notExercisableReason: `${context} rollback drill is deferred until the approved maintenance window.`,
    riskAcceptedBy: "release-owner",
    deferralEvidence: "CHANGE-12345",
    expiresAt: "2026-12-31T00:00:00.000Z",
  })),
}, null, 2)}\n`);

const deferredResult = spawnSync("node", ["scripts/ddd-rollback-drill-evidence.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_ROLLBACK_DRILL_FILE: deferredArtifactFile,
    DDD_ROLLBACK_DRILL_DEFERRAL_FILE: deferralFile,
    DDD_ROLLBACK_DRILL_STRICT: "true",
  },
});

assert.equal(deferredResult.status, 0, deferredResult.stderr || deferredResult.stdout);
const deferredArtifact = JSON.parse(fs.readFileSync(deferredArtifactFile, "utf8"));
assert.notEqual(deferredArtifact.generatedAt, "2026-06-14T00:00:00.000Z");
assert.equal(deferredArtifact.generatedAt, deferredArtifact.checkedAt);
assert.equal(deferredArtifact.status, "PASS");
assert.deepEqual(deferredArtifact.blockers, []);
assert.deepEqual(deferredArtifact.appliedDeferrals, requiredRollbackContexts);
assert.equal(deferredArtifact.contexts.find((entry) => entry.context === "AI").status, "DEFERRED");
assert.equal(deferredArtifact.contextDiagnostics.find((entry) => entry.context === "AI").deferralApplied, true);
assert.ok(deferredArtifact.contextDiagnostics.find((entry) => entry.context === "AI").evidenceRequirements.some((item) => item.includes("provider")));
assert.equal(deferredArtifact.summary.deferredContexts, requiredRollbackContexts.length);
assert.equal(deferredArtifact.summary.appliedDeferrals, requiredRollbackContexts.length);
assert.equal(deferredArtifact.summary.blockers, 0);

const readyCheckOnlyHandoffFile = path.join(directory, "rollback-drill-ready-handoff.md");
const readyCheckOnlyResult = spawnSync("node", ["scripts/ddd-rollback-drill-evidence.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_ROLLBACK_DRILL_FILE: deferredArtifactFile,
    DDD_ROLLBACK_DRILL_CHECK_ENV: "true",
    DDD_ROLLBACK_DRILL_HANDOFF_FILE: readyCheckOnlyHandoffFile,
    DDD_ROLLBACK_DRILL_DEFERRAL_FILE: deferralFile,
    DDD_EVIDENCE_ENVIRONMENT: "staging-prod-equivalent",
    DDD_RELEASE_CANDIDATE: "rc-20260614",
    DDD_EVIDENCE_OPERATOR: "release-owner",
  },
});
assert.equal(readyCheckOnlyResult.status, 0, readyCheckOnlyResult.stderr || readyCheckOnlyResult.stdout);
assert.match(fs.readFileSync(readyCheckOnlyHandoffFile, "utf8"), /\| ai-owner \| AI \| DEFERRED \| READY \|/);
const readyCheckOnlyHandoffJson = JSON.parse(fs.readFileSync(path.join(directory, "rollback-drill-ready-handoff.json"), "utf8"));
assert.equal(readyCheckOnlyHandoffJson.status, "READY");
assert.equal(readyCheckOnlyHandoffJson.summary.ready, requiredRollbackContexts.length);
assert.equal(readyCheckOnlyHandoffJson.summary.missing, 0);
assert(readyCheckOnlyHandoffJson.ownerRunbook.every((owner) => owner.status === "READY"));

console.log("[ddd-rollback-drill-evidence.test] ok");
