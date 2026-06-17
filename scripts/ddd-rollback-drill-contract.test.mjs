#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  buildRollbackDrillBlockers,
  buildRollbackDrillSummary,
  requiredRollbackContexts,
  requiredRollbackEvidenceChecklist,
  rollbackContextRemediation,
  validateRollbackDrillContract,
} from "./ddd-rollback-drill-contract.mjs";

assert.deepEqual(
  requiredRollbackEvidenceChecklist.map((item) => item.id),
  ["pass-rollback-drill-evidence", "deferred-risk-acceptance-evidence"],
);
for (const item of requiredRollbackEvidenceChecklist) {
  assert(["PASS", "DEFERRED"].includes(item.status));
  assert(item.requiredFields.length >= 3);
  assert(item.requiredArtifacts.length >= 3);
  assert(item.acceptanceCriteria.length >= 3);
}
assert.deepEqual(requiredRollbackEvidenceChecklist[0].requiredFields, ["rollbackAction", "drillEvidence", "validatedAt"]);
assert.deepEqual(requiredRollbackEvidenceChecklist[1].requiredFields, ["notExercisableReason", "riskAcceptedBy", "deferralEvidence", "expiresAt"]);

function validArtifact() {
  const artifact = {
    generatedAt: "2026-06-14T00:00:00.000Z",
    status: "PASS",
    environment: "staging-prod-equivalent",
    releaseVersion: "rc-2026-06-14",
    operator: "release-owner",
    blockers: [],
    warnings: [],
    appliedDeferrals: [],
    contextDiagnostics: requiredRollbackContexts.map((context) => ({
      context,
      status: "PASS",
      owner: rollbackContextRemediation(context).owner,
      action: rollbackContextRemediation(context).action,
      evidenceRequirements: rollbackContextRemediation(context).evidenceRequirements,
      evidence: `artifacts/ddd/rollback/${context.toLowerCase()}-rollback-log.json`,
      ready: true,
      missingEvidence: false,
      deferralApplied: false,
    })),
    contexts: requiredRollbackContexts.map((context) => ({
      context,
      status: "PASS",
      rollbackAction: `${context} rollback action exercised through documented runbook.`,
      drillEvidence: `artifacts/ddd/rollback/${context.toLowerCase()}-rollback-log.json`,
      validatedAt: "2026-06-14T01:00:00.000Z",
    })),
  };
  artifact.summary = buildRollbackDrillSummary(artifact);
  return artifact;
}

assert.deepEqual(validateRollbackDrillContract(validArtifact(), { strict: true }), []);
assert.deepEqual(buildRollbackDrillBlockers(validArtifact(), { strict: true }), []);
assert.equal(rollbackContextRemediation("Payment").owner, "payment-owner");
assert.match(rollbackContextRemediation("AI").action, /provider disablement/);
assert(rollbackContextRemediation("Message").evidenceRequirements.some((item) => item.includes("idempotent replay")));

assert.deepEqual(validateRollbackDrillContract({
  ...validArtifact(),
  status: "DRAFT",
}, { strict: true }), [
  "status must be PASS for strict release, got DRAFT",
]);

{
  const artifact = validArtifact();
  artifact.contexts[0].drillEvidence = "operator says it worked";
  artifact.contextDiagnostics[0].evidence = "operator says it worked";
  artifact.summary = buildRollbackDrillSummary(artifact);
  assert.deepEqual(validateRollbackDrillContract(artifact, { strict: true }), [
    "IAM.drillEvidence must include a concrete evidence link, artifact path, log path, object URI, or ticket id",
  ]);
}

{
  const artifact = validArtifact();
  artifact.contexts = artifact.contexts.filter((entry) => entry.context !== "Payment");
  artifact.summary = buildRollbackDrillSummary(artifact);
  const issues = validateRollbackDrillContract(artifact, { strict: true });
  assert(issues.includes("missing context Payment"));
  assert(issues.includes("Payment.diagnostic status must be MISSING, got PASS"));
  assert(issues.includes("Payment.diagnostic ready must be false"));
  assert(issues.includes("Payment.diagnostic missingEvidence must be true"));
  assert(issues.includes("Payment.diagnostic evidence must match context evidence"));
}

{
  const artifact = validArtifact();
  artifact.contexts.push({
    context: "Billing",
    status: "PASS",
    rollbackAction: "Unknown context rollback.",
    drillEvidence: "artifacts/ddd/rollback/billing.json",
    validatedAt: "2026-06-14T01:00:00.000Z",
  });
  artifact.summary = buildRollbackDrillSummary(artifact);
  assert.deepEqual(validateRollbackDrillContract(artifact, { strict: true }), [
    "unknown context Billing",
  ]);
}

{
  const artifact = validArtifact();
  artifact.contexts[0] = {
    context: "IAM",
    status: "DEFERRED",
    notExercisableReason: "Provider maintenance window has not opened.",
    riskAcceptedBy: "release-owner",
    deferralEvidence: "CHANGE-12345",
    expiresAt: "2026-06-13T00:00:00.000Z",
  };
  artifact.contextDiagnostics[0] = {
    context: "IAM",
    status: "DEFERRED",
    owner: rollbackContextRemediation("IAM").owner,
    action: rollbackContextRemediation("IAM").action,
    evidenceRequirements: rollbackContextRemediation("IAM").evidenceRequirements,
    evidence: "CHANGE-12345",
    ready: true,
    missingEvidence: false,
    deferralApplied: true,
  };
  artifact.summary = buildRollbackDrillSummary(artifact);
  assert.deepEqual(validateRollbackDrillContract(artifact, {
    strict: true,
    now: new Date("2026-06-14T00:00:00.000Z"),
  }), [
    "IAM.expiresAt must be in the future for strict release",
  ]);
}

{
  const artifact = validArtifact();
  artifact.contexts[0] = {
    context: "IAM",
    status: "DEFERRED",
    notExercisableReason: "Provider maintenance window has not opened.",
    riskAcceptedBy: "release-owner",
    deferralEvidence: "release owner approved this in chat",
    expiresAt: "2026-06-15T00:00:00.000Z",
  };
  artifact.contextDiagnostics[0] = {
    context: "IAM",
    status: "DEFERRED",
    owner: rollbackContextRemediation("IAM").owner,
    action: rollbackContextRemediation("IAM").action,
    evidenceRequirements: rollbackContextRemediation("IAM").evidenceRequirements,
    evidence: "release owner approved this in chat",
    ready: true,
    missingEvidence: false,
    deferralApplied: true,
  };
  artifact.summary = buildRollbackDrillSummary(artifact);
  assert.deepEqual(validateRollbackDrillContract(artifact, {
    strict: true,
    now: new Date("2026-06-14T00:00:00.000Z"),
  }), [
    "IAM.deferralEvidence must include a concrete evidence link, artifact path, log path, object URI, or ticket id",
  ]);
}

{
  const artifact = validArtifact();
  artifact.summary.passContexts = 9;
  assert.deepEqual(validateRollbackDrillContract(artifact, { strict: true }), [
    "summary.passContexts must be 10, got 9",
  ]);
}

{
  const artifact = validArtifact();
  artifact.contexts[0] = {
    context: "IAM",
    status: "MISSING",
    rollbackAction: null,
    drillEvidence: null,
    validatedAt: null,
  };
  artifact.contextDiagnostics[0] = {
    context: "IAM",
    status: "MISSING",
    owner: rollbackContextRemediation("IAM").owner,
    action: rollbackContextRemediation("IAM").action,
    evidenceRequirements: rollbackContextRemediation("IAM").evidenceRequirements,
    evidence: null,
    ready: false,
    missingEvidence: true,
    deferralApplied: false,
  };
  artifact.blockers = ["operator manually rewrote the blocker"];
  artifact.summary = buildRollbackDrillSummary(artifact);
  const blockers = buildRollbackDrillBlockers(artifact, { strict: true });
  assert(blockers.includes("IAM status must be PASS or DEFERRED"));
  const issues = validateRollbackDrillContract(artifact, { strict: true, validateBlockers: true });
  assert(issues.includes("rollback blockers[0] mismatch: declared=operator manually rewrote the blocker, actual=IAM status must be PASS or DEFERRED"));
}

{
  const artifact = validArtifact();
  artifact.contexts[0] = {
    context: "IAM",
    status: "MISSING",
    rollbackAction: null,
    drillEvidence: null,
    validatedAt: null,
  };
  artifact.contextDiagnostics[0] = {
    context: "IAM",
    status: "MISSING",
    owner: rollbackContextRemediation("IAM").owner,
    action: rollbackContextRemediation("IAM").action,
    evidenceRequirements: rollbackContextRemediation("IAM").evidenceRequirements,
    evidence: null,
    ready: false,
    missingEvidence: true,
    deferralApplied: false,
  };
  artifact.blockers = [];
  artifact.summary = buildRollbackDrillSummary(artifact);
  const issues = validateRollbackDrillContract(artifact, { strict: true, validateBlockers: true });
  assert(issues.includes("rollback blockers length mismatch: declared=0, actual=1"));
}

{
  const artifact = validArtifact();
  artifact.contextDiagnostics[0] = {
    context: "IAM",
    status: "MISSING",
    owner: "wrong-owner",
    action: "wrong action",
    evidenceRequirements: ["wrong requirement"],
    evidence: "wrong-evidence",
    ready: false,
    missingEvidence: true,
    deferralApplied: true,
  };
  artifact.summary = buildRollbackDrillSummary(artifact);
  assert.deepEqual(validateRollbackDrillContract(artifact, { strict: true }).filter((issue) => issue.startsWith("IAM.diagnostic")), [
    "IAM.diagnostic owner must be iam-owner, got wrong-owner",
    "IAM.diagnostic action must match rollback remediation",
    "IAM.diagnostic evidenceRequirements must match rollback remediation",
    "IAM.diagnostic status must be PASS, got MISSING",
    "IAM.diagnostic ready must be true",
    "IAM.diagnostic missingEvidence must be false",
    "IAM.diagnostic deferralApplied must be false",
    "IAM.diagnostic evidence must match context evidence",
  ]);
}

{
  const artifact = validArtifact();
  artifact.contextDiagnostics = [
    ...artifact.contextDiagnostics.filter((entry) => entry.context !== "Payment"),
    artifact.contextDiagnostics.find((entry) => entry.context === "IAM"),
    {
      context: "Billing",
      status: "PASS",
      owner: "billing-owner",
      action: "billing",
      evidence: "artifacts/ddd/rollback/billing.json",
      ready: true,
      missingEvidence: false,
      deferralApplied: false,
    },
  ];
  artifact.summary = buildRollbackDrillSummary(artifact);
  const issues = validateRollbackDrillContract(artifact, { strict: true });
  assert(issues.includes("duplicate contextDiagnostic IAM"));
  assert(issues.includes("unknown contextDiagnostic Billing"));
  assert(issues.includes("missing contextDiagnostic Payment"));
}

console.log("[ddd-rollback-drill-contract.test] ok");
