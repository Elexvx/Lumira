#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");

function runChecklist(args, options = {}) {
  return spawnSync("node", ["scripts/ddd-staging-execution-checklist.mjs", ...args], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
    timeout: 90000,
    ...options,
  });
}

function parseJsonResult(result) {
  assert.equal(result.error, undefined, result.error?.message);
  return JSON.parse(result.stdout);
}

const productionUnblockPlanResult = runChecklist(["--production-unblock-plan"]);
assert.equal(productionUnblockPlanResult.status, 0, productionUnblockPlanResult.stderr || productionUnblockPlanResult.stdout);
const productionUnblockPlan = parseJsonResult(productionUnblockPlanResult);
assert.equal(productionUnblockPlan.status, "BLOCKED");
assert.equal(productionUnblockPlan.finalRecommendation, "NO_GO_STRICT");
assert.equal(productionUnblockPlan.blockedAuditItemCount, 5);
assert(productionUnblockPlan.parallelWorkstreams.some((item) => item.id === "first-wave-env" && item.command.includes("--next-action-env-check")));
assert(productionUnblockPlan.parallelWorkstreams.some((item) => item.id === "first-wave-env" && item.verifyCommand.includes("--next-action-env-receipt-contract")));
assert(productionUnblockPlan.parallelWorkstreams.some((item) => item.id === "lane-completion-receipt" && item.verifyCommand.includes("--lane-completion-submission-check")));
assert(productionUnblockPlan.parallelWorkstreams.some((item) => item.id === "owner-evidence" && item.evidence.includes("artifacts/ddd/release/staging-handoff-bundle/owner-evidence-intake.json")));
assert(productionUnblockPlan.blockedAuditItems.some((item) => item.id === "final-review-enforced" && item.command.includes("--final-review-enforce")));
assert(productionUnblockPlan.blockedAuditItems.some((item) => item.id === "strict-go-no-go" && item.command.includes("release-final-go-no-go-gate.sh")));

const productionEvidenceReadinessResult = runChecklist(["--production-evidence-readiness"]);
assert.equal(productionEvidenceReadinessResult.status, 0, productionEvidenceReadinessResult.stderr || productionEvidenceReadinessResult.stdout);
const productionEvidenceReadiness = parseJsonResult(productionEvidenceReadinessResult);
assert.equal(productionEvidenceReadiness.status, "BLOCKED");
assert.equal(productionEvidenceReadiness.evidenceGateCount, 5);
assert.equal(productionEvidenceReadiness.readyEvidenceCount, 0);
assert.equal(productionEvidenceReadiness.blockedAuditItemCount, 5);
assert(productionEvidenceReadiness.evidenceGates.some((gate) => gate.id === "first-wave-env-receipt" && gate.status === "MISSING"));
assert(productionEvidenceReadiness.evidenceGates.some((gate) => gate.id === "lane-completion-receipt" && gate.blocker.includes("receipt file not provided")));
assert(productionEvidenceReadiness.evidenceGates.some((gate) => gate.id === "owner-evidence" && gate.blocker.includes("missingArtifacts=2")));
assert(productionEvidenceReadiness.evidenceGates.some((gate) => gate.id === "production-audit" && gate.command.includes("--production-cutover-audit-markdown")));
assert(productionEvidenceReadiness.evidenceGates.some((gate) => gate.id === "final-go-no-go" && gate.command.includes("--final-review-enforce")));

const handoffBundleVerifyResult = runChecklist(["--handoff-bundle-verify"]);
assert.equal(handoffBundleVerifyResult.status, 0, handoffBundleVerifyResult.stderr || handoffBundleVerifyResult.stdout);
const handoffBundleVerify = parseJsonResult(handoffBundleVerifyResult);
assert.equal(handoffBundleVerify.status, "PASS");
assert.equal(handoffBundleVerify.issues.length, 0);
assert(handoffBundleVerify.checkedFiles.some((item) => item.file === "production-unblock-plan.json"));
assert(handoffBundleVerify.checkedFiles.some((item) => item.file === "production-evidence-readiness.json"));

console.log("[ddd-production-evidence-readiness.test] ok");
