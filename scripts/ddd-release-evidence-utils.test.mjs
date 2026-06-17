#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  buildProductionEquivalenceEvidence,
  collectProvenanceIssues,
  evidenceValueIssue,
  isHttpsUrl,
  isIsoTimestamp,
  isLocalUrlLike,
  redactLocalPaths,
  requireRuntimeProvenanceWhenStrict,
  validateProductionEquivalenceEvidence,
} from "./ddd-release-evidence-utils.mjs";

assert.equal(evidenceValueIssue("staging"), null);
assert.equal(evidenceValueIssue(""), "is required");
assert.equal(evidenceValueIssue(null), "is required");
assert.equal(evidenceValueIssue("replace-with-owner"), "must not contain placeholder text");
assert.equal(evidenceValueIssue("https://example.com"), "must not contain placeholder text");
assert.equal(evidenceValueIssue("https://api.example.internal/v1"), "must not contain placeholder text");
assert.equal(evidenceValueIssue("https://api.example.test/v1"), "must not contain placeholder text");
assert.equal(evidenceValueIssue("https://api.lumira.test/v1"), "must not contain placeholder text");
assert.equal(evidenceValueIssue("https://api.staging.lumira.invalid/v1"), "must not contain placeholder text");

assert.equal(redactLocalPaths(
  "Cannot connect at /Users/example/project and /Users/example/.docker/run/docker.sock",
  { repoRoot: "/Users/example/project", homeDir: "/Users/example" },
), "Cannot connect at . and ~/.docker/run/docker.sock");

assert.deepEqual(collectProvenanceIssues({
  sourceEnvironment: "staging",
  releaseCandidate: "2026.06.14-rc1",
  evidenceOperator: "release-operator",
}), []);

assert.deepEqual(requireRuntimeProvenanceWhenStrict({
  strict: false,
  sourceEnvironment: "",
  releaseCandidate: "",
  evidenceOperator: "",
}), []);

assert.deepEqual(requireRuntimeProvenanceWhenStrict({
  strict: true,
  sourceEnvironment: "",
  releaseCandidate: "todo",
  evidenceOperator: "release-operator",
}), [
  "sourceEnvironment is required",
  "releaseCandidate must not contain placeholder text",
]);

assert.equal(isHttpsUrl("https://staging.lumira.internal"), true);
assert.equal(isHttpsUrl("http://staging.lumira.internal"), false);
assert.equal(isHttpsUrl("not-a-url"), false);

assert.equal(isLocalUrlLike("http://localhost:8080"), true);
assert.equal(isLocalUrlLike("jdbc:mysql://127.0.0.1:3306/saas"), true);
assert.equal(isLocalUrlLike("http://0.0.0.0:8080"), true);
assert.equal(isLocalUrlLike("https://staging.lumira.internal"), false);

assert.deepEqual(buildProductionEquivalenceEvidence({
  strict: true,
  baseUrl: "http://127.0.0.1:8080",
  deploymentEvidence: "",
  evidenceName: "file processing E2E",
}), {
  strict: true,
  https: false,
  localOnly: true,
  deploymentEvidence: null,
  issues: [
    "strict file processing E2E requires HTTPS baseUrl evidence",
    "strict file processing E2E requires non-local baseUrl, got http://127.0.0.1:8080",
    "strict file processing E2E deploymentEvidence is required",
  ],
});

assert.deepEqual(buildProductionEquivalenceEvidence({
  strict: false,
  baseUrl: "https://staging.lumira.internal",
  deploymentEvidence: "deploy-123",
  evidenceName: "payment webhook E2E",
}), {
  strict: false,
  https: true,
  localOnly: false,
  deploymentEvidence: "deploy-123",
  issues: [],
});

assert.deepEqual(buildProductionEquivalenceEvidence({
  strict: true,
  baseUrl: "https://api.lumira.test",
  deploymentEvidence: "https://example.com/deployments/123",
  evidenceName: "frontend smoke",
}), {
  strict: true,
  https: true,
  localOnly: false,
  deploymentEvidence: "https://example.com/deployments/123",
  issues: [
    "strict frontend smoke baseUrl must not contain placeholder text",
    "strict frontend smoke deploymentEvidence must not contain placeholder text",
  ],
});

assert.deepEqual(validateProductionEquivalenceEvidence("runtime readiness", {
  productionEquivalence: {
    strict: true,
    https: false,
    localOnly: true,
    deploymentEvidence: "ci://deploy/123",
    issues: ["strict runtime readiness requires HTTPS baseUrl evidence"],
  },
}, { strict: true, issuesMustBeStrings: true }), [
  "runtime readiness productionEquivalence.https must be true for strict release evidence",
  "runtime readiness productionEquivalence.localOnly must be false for strict release evidence",
  "runtime readiness productionEquivalence.issues must be empty for strict release evidence",
]);

assert.equal(isIsoTimestamp("2026-06-14T00:00:00.000Z"), true);
assert.equal(isIsoTimestamp("2026-06-14"), true);
assert.equal(isIsoTimestamp("not-a-time"), false);

console.log("[ddd-release-evidence-utils.test] ok");
