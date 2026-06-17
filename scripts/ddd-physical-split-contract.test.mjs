#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  buildPhysicalSplitSummary,
  requiredPhysicalSplitContexts,
  validatePhysicalSplitContract,
} from "./ddd-physical-split-contract.mjs";

function validArtifact() {
  const artifact = {
    generatedAt: "2026-06-14T00:00:00.000Z",
    sourceEnvironment: "staging-prod-equivalent",
    releaseCandidate: "rc-20260614",
    evidenceOperator: "release-owner",
    strict: true,
    contexts: requiredPhysicalSplitContexts.map((context) => ({
      ...context,
      standaloneBootApplication: context.name !== "AI",
      migrationFiles: context.name === "Payment"
        ? ["services/payment-service/src/main/resources/db/migration/payment/V1__payment.sql"]
        : [],
      missingBusinessEndpoints: context.name === "AI" ? ["POST /api/v2/ai/chat"] : [],
      checks: [
        { name: "module", status: "pass", detail: context.module },
        { name: "owner-manifest", status: "pass", detail: `${context.ownerContext}: owner tables declared` },
        { name: "readiness-endpoint", status: "pass", detail: `${context.route}/readiness` },
        { name: "health-endpoint", status: "pass", detail: `${context.route}/health` },
        { name: "metrics-endpoint", status: "pass", detail: `${context.route}/metrics` },
        { name: "cross-service-pom-dependency", status: "pass", detail: "no direct service module dependency" },
      ],
      blockers: context.name === "AI" ? ["standalone Spring Boot application entrypoint is not present yet"] : [],
      warnings: context.name === "AI" ? ["operational runbook does not contain readiness drill evidence reference"] : [],
    })),
    globalChecks: [
      { name: "split-gate-document", status: "pass", detail: "present" },
      { name: "architecture-boundary-test", status: "pass", detail: "present" },
    ],
  };
  artifact.summary = buildPhysicalSplitSummary(artifact);
  return artifact;
}

assert.deepEqual(validatePhysicalSplitContract(validArtifact()), []);

{
  const artifact = validArtifact();
  artifact.summary.blockers = 0;
  assert.deepEqual(validatePhysicalSplitContract(artifact), [
    "summary.blockers must be 1, got 0",
  ]);
}

{
  const artifact = validArtifact();
  artifact.sourceEnvironment = null;
  artifact.summary = buildPhysicalSplitSummary(artifact);
  assert.deepEqual(validatePhysicalSplitContract(artifact), []);
  assert.equal(artifact.summary.failures, 1);
}

{
  const artifact = validArtifact();
  artifact.globalChecks[0].status = "fail";
  artifact.summary = buildPhysicalSplitSummary(artifact);
  assert.equal(artifact.summary.failedChecks, 1);
  assert.equal(artifact.summary.failures, 1);
  assert.deepEqual(validatePhysicalSplitContract(artifact), []);
}

{
  const artifact = validArtifact();
  const payment = artifact.contexts.find((context) => context.name === "Payment");
  payment.checks[5] = {
    name: "cross-service-pom-dependency",
    status: "fail",
    detail: "system-service",
  };
  artifact.summary = buildPhysicalSplitSummary(artifact);
  assert.equal(artifact.summary.crossServiceDependencyFailures, 1);
  assert.equal(artifact.summary.failedChecks, 1);
  assert.deepEqual(validatePhysicalSplitContract(artifact), []);
}

{
  const artifact = validArtifact();
  artifact.contexts = artifact.contexts.filter((context) => context.name !== "Payment");
  artifact.summary = buildPhysicalSplitSummary(artifact);
  assert(validatePhysicalSplitContract(artifact).includes("missing physical split context Payment"));
}

{
  const artifact = validArtifact();
  artifact.contexts.push({ ...artifact.contexts[0] });
  artifact.summary = buildPhysicalSplitSummary(artifact);
  assert(validatePhysicalSplitContract(artifact).includes("duplicate physical split context IAM"));
}

{
  const artifact = validArtifact();
  artifact.contexts[0].module = "services/unknown-service";
  artifact.summary = buildPhysicalSplitSummary(artifact);
  assert(validatePhysicalSplitContract(artifact).includes("IAM module must be services/system-service, got services/unknown-service"));
}

{
  const artifact = validArtifact();
  artifact.contexts[0].checks = artifact.contexts[0].checks.filter((check) => check.name !== "metrics-endpoint");
  artifact.summary = buildPhysicalSplitSummary(artifact);
  assert(validatePhysicalSplitContract(artifact).includes("IAM missing context check metrics-endpoint"));
}

{
  const artifact = validArtifact();
  artifact.globalChecks = artifact.globalChecks.filter((check) => check.name !== "architecture-boundary-test");
  artifact.summary = buildPhysicalSplitSummary(artifact);
  assert(validatePhysicalSplitContract(artifact).includes("missing global check architecture-boundary-test"));
}

console.log("[ddd-physical-split-contract.test] ok");
