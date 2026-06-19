#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  requiredJobSmokeEndpoints,
  requiredOutboxReplayContracts,
  requiredOutboxReplayTestClasses,
  validateJobE2eArtifact,
  validateOutboxReplayArtifact,
} from "./ddd-outbox-job-evidence-contract.mjs";

function outboxArtifact(overrides = {}) {
  return {
    status: "PASS",
    command: "./mvnw -Dtest=OutboxRelay test",
    startedAt: "2026-06-14T00:00:00.000Z",
    finishedAt: "2026-06-14T00:00:02.000Z",
    elapsedMs: 2000,
    testedContracts: [...requiredOutboxReplayContracts],
    reports: requiredOutboxReplayTestClasses.map((className) => ({
      className,
      reportPath: `/tmp/TEST-${className}.xml`,
      present: true,
      tests: 1,
      failures: 0,
      errors: 0,
      skipped: 0,
      timeSeconds: 0.1,
    })),
    ...overrides,
  };
}

function jobArtifact(overrides = {}) {
  const endpoints = requiredJobSmokeEndpoints.map((endpoint, index) => ({
    name: endpoint.name,
    path: endpoint.path,
    status: 200,
    elapsedMs: index + 1,
    data: endpoint.dataType === "boolean" ? true : 0,
  }));
  return {
    baseUrl: "https://job.staging.lumira.app",
    checkedAt: "2026-06-14T00:00:00.000Z",
    unauthorized: { path: requiredJobSmokeEndpoints[0].path, status: 401 },
    summary: { total: requiredJobSmokeEndpoints.length, failed: 0, maxElapsedMs: endpoints.length },
    endpoints,
    diagnostics: {
      outboxOwnership: {
        crossOwnerPayloadFailuresDelta: 0,
      },
    },
    ...overrides,
  };
}

assert.deepEqual(validateOutboxReplayArtifact(outboxArtifact()), []);
assert.deepEqual(validateJobE2eArtifact(jobArtifact()), []);
assert(validateJobE2eArtifact(jobArtifact(), { strict: true })
  .includes("job E2E productionEquivalence is required for strict release evidence"));

{
  const issues = validateJobE2eArtifact(jobArtifact({
    productionEquivalence: {
      strict: false,
      https: "yes",
      localOnly: "no",
      deploymentEvidence: 42,
      issues: "none",
    },
  }), { strict: true });
  assert(issues.includes("job E2E productionEquivalence.strict must be true for strict release evidence"));
  assert(issues.includes("job E2E productionEquivalence.https must be boolean"));
  assert(issues.includes("job E2E productionEquivalence.localOnly must be boolean"));
  assert(issues.includes("job E2E productionEquivalence.deploymentEvidence must be string or null"));
  assert(issues.includes("job E2E productionEquivalence.deploymentEvidence is required"));
  assert(issues.includes("job E2E productionEquivalence.issues must be an array"));
}

{
  const issues = validateJobE2eArtifact(jobArtifact({
    productionEquivalence: {
      strict: true,
      https: true,
      localOnly: false,
      deploymentEvidence: "https://example.com/deployments/123",
      issues: [],
    },
  }), { strict: true });
  assert(issues.includes("job E2E productionEquivalence.deploymentEvidence must not contain placeholder text"));
}

assert(
  validateOutboxReplayArtifact(outboxArtifact({
    status: "FAIL",
    reports: outboxArtifact().reports.filter((report) => report.className !== requiredOutboxReplayTestClasses[0]),
  })).includes(`missing owner relay report ${requiredOutboxReplayTestClasses[0]}`),
);

assert(
  validateOutboxReplayArtifact(outboxArtifact({
    reports: outboxArtifact().reports.map((report, index) => index === 0
      ? { ...report, failures: 1, errors: 1, skipped: 1 }
      : report),
  })).includes(`${requiredOutboxReplayTestClasses[0]} skipped=1`),
);

assert.deepEqual(validateOutboxReplayArtifact(outboxArtifact({
  reports: [
    outboxArtifact().reports[0],
    outboxArtifact().reports[0],
    ...outboxArtifact().reports.slice(1),
  ],
})).filter((issue) => issue.includes("duplicate")), [
  "outbox replay reports contain duplicate className",
]);

{
  const artifact = outboxArtifact({
    testedContracts: [
      requiredOutboxReplayContracts[0],
      requiredOutboxReplayContracts[0],
      "unknown outbox behavior",
      ...requiredOutboxReplayContracts.slice(2),
    ],
    reports: [
      ...outboxArtifact().reports,
      {
        className: "com.lumira.UnknownOutboxTest",
        present: true,
        tests: 1,
        failures: 0,
        errors: 0,
        skipped: 0,
      },
    ],
  });
  const issues = validateOutboxReplayArtifact(artifact);
  assert(issues.includes(`duplicate tested outbox contract ${requiredOutboxReplayContracts[0]}`));
  assert(issues.includes(`missing tested outbox contract ${requiredOutboxReplayContracts[1]}`));
  assert(issues.includes("unknown tested outbox contract unknown outbox behavior"));
  assert(issues.includes("unknown owner relay report com.lumira.UnknownOutboxTest"));
  assert(issues.includes("com.lumira.UnknownOutboxTest reportPath is required"));
  assert(issues.includes("com.lumira.UnknownOutboxTest timeSeconds must be non-negative"));
}

assert(
  validateJobE2eArtifact(jobArtifact({
    unauthorized: { path: requiredJobSmokeEndpoints[0].path, status: 200 },
  })).includes("internal endpoint accepted unauthenticated job request"),
);

assert(
  validateJobE2eArtifact(jobArtifact({
    endpoints: jobArtifact().endpoints.filter((endpoint) => endpoint.name !== "file-processing-run"),
  })).includes("missing job endpoint result file-processing-run"),
);

assert.deepEqual(validateJobE2eArtifact(jobArtifact({
  summary: { total: 99, failed: 99, maxElapsedMs: 99 },
  endpoints: [
    {
      ...jobArtifact().endpoints[0],
      status: 500,
      elapsedMs: 0,
    },
    jobArtifact().endpoints[0],
    ...jobArtifact().endpoints.slice(1),
  ],
})).filter((issue) => issue.includes("duplicate")
  || issue.includes("summary.")
  || issue.includes("status=")
  || issue.includes("elapsedMs")
  || issue.includes("count")), [
    "job endpoints contain duplicate name",
    "platform-outbox-relay status=500",
    "platform-outbox-relay missing positive elapsedMs",
    "job endpoint result count must be 9, got 10",
    "summary.total must be 10, got 99",
    "summary.failed must be 1, got 99",
    "summary.maxElapsedMs must be 9, got 99",
  ]);

assert(
  validateJobE2eArtifact(jobArtifact({
    diagnostics: {
      outboxOwnership: {
        crossOwnerPayloadFailuresDelta: 1,
      },
    },
  })).includes("cross-owner outbox payload failure count increased"),
);

{
  const issues = validateJobE2eArtifact(jobArtifact({
    baseUrl: "",
    checkedAt: "not-a-date",
    unauthorized: { path: "/wrong", status: 401 },
    endpoints: [
      ...jobArtifact().endpoints,
      { name: "unknown-job", path: "/unknown", status: 200, elapsedMs: 1, data: true },
    ],
    summary: { total: 10, failed: 0 },
  }));
  assert(issues.includes("job E2E baseUrl is required"));
  assert(issues.includes("job E2E checkedAt must be an ISO-like datetime"));
  assert(issues.includes(`unauthorized probe path must be ${requiredJobSmokeEndpoints[0].path}, got /wrong`));
  assert(issues.includes("unknown job endpoint result unknown-job"));
  assert(issues.includes("summary.maxElapsedMs is required"));
}

console.log("[ddd-outbox-job-evidence-contract.test] ok");
