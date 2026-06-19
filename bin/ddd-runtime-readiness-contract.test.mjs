#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import {
  expectedRuntimeReadinessChecks,
  runtimeReadinessContextLabels,
  validateRuntimeReadinessArtifact,
} from "./ddd-runtime-readiness-contract.mjs";

const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-runtime-readiness-"));

function passingArtifact(overrides = {}) {
  const summary = expectedRuntimeReadinessChecks().map(({ context, suffix }) => ({
    context,
    suffix,
    status: 200,
    artifact: path.join(tempDir, `${context}-${suffix}-${Math.random().toString(36).slice(2)}.json`),
  }));
  for (const item of summary) {
    writeEndpointArtifact(item);
  }
  return {
    baseUrl: "https://api.staging.lumira.app",
    checkedAt: "2026-06-14T00:00:00.000Z",
    failures: [],
    summary,
    ...overrides,
  };
}

function writeEndpointArtifact(item, dataOverrides = {}) {
  const contextLabel = runtimeReadinessContextLabels.get(item.context);
  const common = {
    context: contextLabel,
    ownerModule: `${item.context}-service`,
    status: item.suffix === "readiness" ? "READY_WITH_BLOCKERS" : item.suffix === "health" ? "UP" : "METRICS_DECLARED",
    ...dataOverrides,
  };
  const data = item.suffix === "readiness"
    ? {
        ...common,
        readinessLevel: "contract-and-observability",
        ownerTablePatterns: [`${item.context}_*`],
        apiContracts: [`/api/v2/${item.context}`],
        eventContracts: [`${contextLabel}Changed`],
        healthChecks: [`${item.context}.db.owner-tables`],
        metrics: [`${item.context}.latency.p95`],
        dependencies: [`${item.context}-cache`],
        rollbackSteps: [`route ${item.context} back to v1 adapter`],
        blockers: [],
      }
    : {
        ...common,
        observedAt: "2026-06-14T00:00:00.000Z",
        healthChecks: item.suffix === "health"
          ? [{ name: `${item.context}.db.owner-tables`, status: "CONFIGURED", description: "Owner tables are guarded." }]
          : [],
        metrics: [{ name: `${item.context}.latency.p95`, type: "timer", unit: "milliseconds", description: "p95 latency." }],
      };
  fs.writeFileSync(item.artifact, `${JSON.stringify({ httpStatus: 200, code: "0", data }, null, 2)}\n`);
}

assert.equal(expectedRuntimeReadinessChecks().length, 30);
assert.deepEqual(validateRuntimeReadinessArtifact(passingArtifact()), []);
assert.deepEqual(validateRuntimeReadinessArtifact(passingArtifact({
  productionEquivalence: {
    strict: true,
    https: true,
    localOnly: false,
    deploymentEvidence: "ci://deploy/123",
    issues: [],
  },
}), { strict: true }), []);

assert(
  validateRuntimeReadinessArtifact(passingArtifact(), { strict: true })
    .includes("runtime readiness productionEquivalence is required for strict release evidence"),
);

{
  const issues = validateRuntimeReadinessArtifact(passingArtifact({
    productionEquivalence: {
      strict: "true",
      https: true,
      localOnly: false,
      deploymentEvidence: 42,
      issues: "none",
    },
  }), { strict: true });
  assert(issues.includes("runtime readiness productionEquivalence.strict must be boolean"));
  assert(issues.includes("runtime readiness productionEquivalence.strict must be true for strict release evidence"));
  assert(issues.includes("runtime readiness productionEquivalence.deploymentEvidence must be string or null"));
  assert(issues.includes("runtime readiness productionEquivalence.deploymentEvidence is required"));
  assert(issues.includes("runtime readiness productionEquivalence.issues must be an array of strings"));
}

{
  const issues = validateRuntimeReadinessArtifact(passingArtifact({
    productionEquivalence: {
      strict: true,
      https: true,
      localOnly: false,
      deploymentEvidence: "https://example.com/deployments/123",
      issues: [],
    },
  }), { strict: true });
  assert(issues.includes("runtime readiness productionEquivalence.deploymentEvidence must not contain placeholder text"));
}

{
  const issues = validateRuntimeReadinessArtifact(passingArtifact({
    productionEquivalence: {
      strict: false,
      https: true,
      localOnly: false,
      deploymentEvidence: "ci://deploy/123",
      issues: [],
    },
  }), { strict: true });
  assert(issues.includes("runtime readiness productionEquivalence.strict must be true for strict release evidence"));
}

assert(
  validateRuntimeReadinessArtifact(passingArtifact({
    failures: ["boom"],
  })).includes("failures=1"),
);

assert(
  validateRuntimeReadinessArtifact(passingArtifact({
    summary: passingArtifact().summary.filter((item) => !(item.context === "payment" && item.suffix === "metrics")),
  })).includes("missing readiness check payment/metrics"),
);

assert.deepEqual(validateRuntimeReadinessArtifact(passingArtifact({
  summary: [
    ...passingArtifact().summary,
    {
      context: "billing",
      suffix: "health",
      status: 200,
      artifact: "/tmp/billing-health.json",
    },
  ],
})).filter((issue) => issue.includes("endpoint checks") || issue.startsWith("unknown")), [
  "expected 30 endpoint checks, got 31",
  "unknown readiness check billing/health",
]);

{
  const summary = passingArtifact().summary;
  summary[1] = {
    ...summary[0],
    artifact: "/tmp/duplicate-iam-readiness.json",
  };
  assert.deepEqual(validateRuntimeReadinessArtifact(passingArtifact({ summary }))
    .filter((issue) => issue.includes("duplicate readiness check") || issue.includes("missing readiness check")), [
      "duplicate readiness check iam/readiness",
      "missing readiness check iam/health",
    ]);
}

{
  const summary = passingArtifact().summary;
  summary[1] = {
    ...summary[1],
    artifact: summary[0].artifact,
  };
  assert.deepEqual(validateRuntimeReadinessArtifact(passingArtifact({ summary }))
    .filter((issue) => issue.includes("duplicate artifact reference")), [
      "iam/health duplicate artifact reference",
    ]);
}

assert.deepEqual(validateRuntimeReadinessArtifact(passingArtifact({
  summary: passingArtifact().summary.map((item) => item.context === "ai" && item.suffix === "health"
    ? { ...item, status: 503, artifact: "" }
    : item),
})).filter((issue) => issue.startsWith("ai/health")), [
  "ai/health status=503",
  "ai/health missing artifact reference",
]);

{
  const artifact = passingArtifact();
  const item = artifact.summary.find((entry) => entry.context === "message" && entry.suffix === "metrics");
  fs.writeFileSync(item.artifact, `${JSON.stringify({
    httpStatus: 200,
    code: "0",
    data: {
      context: "Wrong",
      ownerModule: "",
      status: "",
      observedAt: "not-a-date",
      healthChecks: [],
      metrics: [{ name: "", type: "", unit: "", description: "" }],
    },
  }, null, 2)}\n`);
  const issues = validateRuntimeReadinessArtifact(artifact);
  assert(issues.includes("message/metrics artifact context must be Message, got Wrong"));
  assert(issues.includes("message/metrics artifact ownerModule is required"));
  assert(issues.includes("message/metrics artifact status is required"));
  assert(issues.includes("message/metrics artifact observedAt must be an ISO-like datetime"));
  assert(issues.includes("message/metrics artifact metrics[0].name is required"));
}

{
  const artifact = passingArtifact();
  const item = artifact.summary.find((entry) => entry.context === "files" && entry.suffix === "readiness");
  fs.writeFileSync(item.artifact, `${JSON.stringify({
    httpStatus: 200,
    code: "0",
    data: {
      context: "File",
      ownerModule: "file-service",
      status: "READY_WITH_BLOCKERS",
      ownerTablePatterns: [],
      apiContracts: [],
      healthChecks: [],
      metrics: [],
      dependencies: [],
      rollbackSteps: [],
      blockers: null,
    },
  }, null, 2)}\n`);
  const issues = validateRuntimeReadinessArtifact(artifact);
  assert(issues.includes("files/readiness artifact ownerTablePatterns must be a non-empty string array"));
  assert(issues.includes("files/readiness artifact blockers must be an array"));
}

console.log("[ddd-runtime-readiness-contract.test] ok");
