#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  compareAuthenticatedPerformance,
  requiredAuthenticatedPerformanceEndpoints,
  requiredPerformanceBaselineEvidenceChecklist,
  validateAuthenticatedPerformanceBaselineMetadata,
  validateAuthenticatedPerformanceShape,
} from "./ddd-performance-evidence-contract.mjs";

function perfArtifact() {
  const perEndpoint = Object.fromEntries(requiredAuthenticatedPerformanceEndpoints.map((endpoint) => [
    endpoint,
    {
      samples: 10,
      p50: 50,
      p95: 80,
      p99: 100,
      statusCounts: {
        "200": 10,
      },
    },
  ]));
  return {
    baseUrl: "https://api.staging.lumira.app",
    checkedAt: "2026-06-14T00:00:00.000Z",
    durationMs: 1000,
    concurrency: 4,
    ok: requiredAuthenticatedPerformanceEndpoints.length * 10,
    failed: 0,
    samples: requiredAuthenticatedPerformanceEndpoints.length * 10,
    p50: 70,
    p95: 100,
    p99: 120,
    endpoints: requiredAuthenticatedPerformanceEndpoints.map((endpoint) => {
      const [method, ...pathParts] = endpoint.split(" ");
      return { method, path: pathParts.join(" ") };
    }),
    upload: {
      path: "/api/v2/files/upload",
      status: 200,
      elapsedMs: 120,
      fileId: 1,
    },
    oneShots: [
      { name: "POST /api/v2/auth/session/keepalive", status: 200, elapsedMs: 30 },
    ],
    perEndpoint,
  };
}

assert.deepEqual(validateAuthenticatedPerformanceShape("actual", perfArtifact()), []);
assert.equal(requiredPerformanceBaselineEvidenceChecklist.length, 3);
assert.deepEqual(requiredPerformanceBaselineEvidenceChecklist.map((item) => item.id), [
  "authenticated-runtime-actual-evidence",
  "authenticated-runtime-baseline-promotion-evidence",
  "baseline-release-gate-acceptance-evidence",
]);
assert(requiredPerformanceBaselineEvidenceChecklist.every((item) => item.requiredArtifacts.length > 0));
assert(requiredPerformanceBaselineEvidenceChecklist.every((item) => item.requiredFields.length > 0));
assert(requiredPerformanceBaselineEvidenceChecklist.every((item) => item.requiredEnvKeys.length > 0));
assert(requiredPerformanceBaselineEvidenceChecklist.every((item) => item.acceptanceCriteria.length >= 3));
assert(requiredPerformanceBaselineEvidenceChecklist[0].requiredFields.includes("productionEquivalence.deploymentEvidence"));
assert(requiredPerformanceBaselineEvidenceChecklist[1].requiredFields.includes("baseline.sourceSha256"));
assert(requiredPerformanceBaselineEvidenceChecklist[2].requiredArtifacts.includes("artifacts/ddd/release/release-final-go-no-go.json"));
assert(validateAuthenticatedPerformanceShape("actual", perfArtifact(), { strict: true })
  .includes("actual productionEquivalence is required for strict release evidence"));

{
  const artifact = perfArtifact();
  artifact.productionEquivalence = {
    strict: false,
    https: "yes",
    localOnly: "no",
    deploymentEvidence: 42,
    issues: "none",
  };
  const issues = validateAuthenticatedPerformanceShape("actual", artifact, { strict: true });
  assert(issues.includes("actual productionEquivalence.strict must be true for strict release evidence"));
  assert(issues.includes("actual productionEquivalence.https must be boolean"));
  assert(issues.includes("actual productionEquivalence.localOnly must be boolean"));
  assert(issues.includes("actual productionEquivalence.deploymentEvidence must be string or null"));
  assert(issues.includes("actual productionEquivalence.deploymentEvidence is required"));
  assert(issues.includes("actual productionEquivalence.issues must be an array"));
}

{
  const artifact = perfArtifact();
  artifact.productionEquivalence = {
    strict: true,
    https: true,
    localOnly: false,
    deploymentEvidence: "https://example.com/deployments/123",
    issues: [],
  };
  const issues = validateAuthenticatedPerformanceShape("actual", artifact, { strict: true });
  assert(issues.includes("actual productionEquivalence.deploymentEvidence must not contain placeholder text"));
}

assert.deepEqual(validateAuthenticatedPerformanceShape("actual", {
  checkedAt: "bad",
  durationMs: 0,
  concurrency: 0,
  p95: 0,
  upload: { status: 500, elapsedMs: 0 },
  perEndpoint: {
    "GET /broken": { samples: 0, p95: 0 },
  },
}), [
  "actual checkedAt must be an ISO timestamp",
  "actual durationMs must be positive",
  "actual concurrency must be positive",
  "actual is missing positive p95",
  "actual is missing successful upload timing",
  "actual missing required perEndpoint metrics GET /api/v2/auth/current-user",
  "actual missing required perEndpoint metrics GET /api/v2/iam/tenants/current",
  "actual missing required perEndpoint metrics GET /api/v2/iam/permissions",
  "actual missing required perEndpoint metrics GET /api/v2/message/unread-count",
  "actual missing required perEndpoint metrics GET /api/v2/message/messages?pageNo=1&pageSize=20",
  "actual missing required perEndpoint metrics GET /api/v2/files?pageNo=1&pageSize=20",
  "actual missing required perEndpoint metrics GET /api/v2/plugins/current/bootstrap",
  "actual missing required perEndpoint metrics GET /api/v2/localization/runtime/zh-CN",
  "actual missing required perEndpoint metrics GET /api/v2/payment/providers",
  "actual unknown perEndpoint metrics GET /broken",
  "actual GET /broken is missing positive samples",
  "actual GET /broken is missing positive p95",
  "actual missing keepalive oneShot timing",
]);

{
  const artifact = perfArtifact();
  artifact.samples = artifact.samples - 1;
  artifact.perEndpoint["GET /api/v2/auth/current-user"].statusCounts = { "200": 9 };
  artifact.oneShots[0].status = 500;
  assert.deepEqual(validateAuthenticatedPerformanceShape("actual", artifact), [
    "actual ok + failed must equal samples",
    "actual GET /api/v2/auth/current-user statusCounts total must equal samples",
    "actual perEndpoint sample total must equal samples",
    "actual oneShots must contain successful timing evidence",
  ]);
}

{
  const artifact = perfArtifact();
  artifact.endpoints = artifact.endpoints.filter((endpoint) => `${endpoint.method} ${endpoint.path}` !== "GET /api/v2/plugins/current/bootstrap");
  artifact.endpoints.push({ method: "GET", path: "/api/v2/unknown" });
  assert(validateAuthenticatedPerformanceShape("actual", artifact)
    .includes("actual missing required endpoint GET /api/v2/plugins/current/bootstrap"));
  assert(validateAuthenticatedPerformanceShape("actual", artifact)
    .includes("actual unknown endpoint definition GET /api/v2/unknown"));
}

{
  const artifact = perfArtifact();
  artifact.p50 = 101;
  artifact.p99 = 90;
  artifact.upload.path = "/wrong";
  artifact.upload.fileId = null;
  artifact.perEndpoint["GET /api/v2/auth/current-user"].p50 = 90;
  artifact.perEndpoint["GET /api/v2/auth/current-user"].p99 = 70;
  artifact.perEndpoint["GET /api/v2/auth/current-user"].statusCounts = { "200": 9, "500": 1 };
  assert.deepEqual(validateAuthenticatedPerformanceShape("actual", artifact).filter((issue) => (
    issue.includes("p50")
    || issue.includes("p95")
    || issue.includes("upload")
    || issue.includes("non-200")
  )), [
    "actual p50 must be <= p95",
    "actual p95 must be <= p99",
    "actual upload path must be /api/v2/files/upload",
    "actual upload fileId is required",
    "actual GET /api/v2/auth/current-user p50 must be <= p95",
    "actual GET /api/v2/auth/current-user p95 must be <= p99",
    "actual GET /api/v2/auth/current-user has non-200 statusCounts despite failed=0",
  ]);
}

assert.deepEqual(validateAuthenticatedPerformanceBaselineMetadata({
  ...perfArtifact(),
  baselineType: "authenticated-runtime",
  acceptedAt: "2026-06-14T00:00:00.000Z",
  acceptedBy: "release-owner",
  sourceEnvironment: "staging",
  sourceArtifact: "artifacts/ddd/performance/authenticated-runtime-actual.json",
  sourceSha256: "a".repeat(64),
  releaseCandidate: "rc-20260614",
  evidenceOperator: "release-runner",
}, { strict: true }), []);

assert.deepEqual(validateAuthenticatedPerformanceBaselineMetadata({
  baselineType: "wrong",
  acceptedAt: "not-a-time",
  acceptedBy: "todo",
  sourceEnvironment: "",
  sourceArtifact: "https://example.com/actual.json",
  sourceSha256: "not-a-sha",
  releaseCandidate: "",
  evidenceOperator: "todo",
}, { strict: true }), [
  "strict release baseline requires baselineType=authenticated-runtime",
  "acceptedAt must be an ISO timestamp",
  "acceptedBy must not contain placeholder text",
  "sourceEnvironment is required",
  "sourceArtifact must not contain placeholder text",
  "releaseCandidate is required",
  "evidenceOperator must not contain placeholder text",
  "sourceSha256 must be a SHA-256 hex digest",
]);

{
  const actual = perfArtifact();
  const baseline = perfArtifact();
  actual.p95 = 112;
  actual.upload.elapsedMs = 140;
  actual.perEndpoint["GET /api/v2/auth/current-user"].p95 = 90;
  assert.deepEqual(compareAuthenticatedPerformance(actual, baseline, { maxRegressionRatio: 0.10 }), [
    {
      name: "authenticated-performance-regression",
      detail: "p95 112ms exceeds baseline 100ms by more than 10%",
    },
    {
      name: "authenticated-performance-upload-regression",
      detail: "upload elapsed 140ms exceeds baseline 120ms by more than 10%",
    },
    {
      name: "authenticated-performance-regression GET /api/v2/auth/current-user",
      detail: "p95 90ms exceeds baseline 80ms by more than 10%",
    },
  ]);
}

{
  const actual = perfArtifact();
  const baseline = perfArtifact();
  delete actual.perEndpoint["GET /api/v2/message/unread-count"];
  assert.deepEqual(compareAuthenticatedPerformance(actual, baseline, { maxRegressionRatio: 0.10 }), [
    {
      name: "authenticated-performance-regression GET /api/v2/message/unread-count",
      detail: "actual artifact is missing endpoint present in baseline",
    },
  ]);
}

console.log("[ddd-performance-evidence-contract.test] ok");
