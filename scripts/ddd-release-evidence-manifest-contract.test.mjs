#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  collectManifestProvenanceIssues,
  optionalManifestArtifacts,
  provenanceManifestArtifacts,
  requiredManifestArtifacts,
  validateManifestArtifact,
} from "./ddd-release-evidence-manifest-contract.mjs";

function artifact(relativePath, overrides = {}) {
  return {
    relativePath,
    present: true,
    status: "PRESENT",
    bytes: 100,
    sha256: "a".repeat(64),
    timestamp: { field: "generatedAt", value: "2026-06-14T00:00:00.000Z" },
    provenanceIssues: [],
    parseError: null,
    ...overrides,
  };
}

function passingManifest(overrides = {}) {
  return {
    generatedAt: "2026-06-14T00:00:00.000Z",
    sourceEnvironment: "staging",
    releaseCandidate: "rc-20260614",
    evidenceOperator: "ci",
    artifactRoot: "/tmp/artifacts/ddd",
    status: "PASS",
    summary: {
      requiredArtifacts: requiredManifestArtifacts.length,
      presentArtifacts: requiredManifestArtifacts.length,
      invalidJsonArtifacts: 0,
      explainFiles: 6,
      provenanceIssueArtifacts: 0,
      blockers: 0,
    },
    artifacts: requiredManifestArtifacts.map((relativePath) => artifact(relativePath)),
    explain: { present: true, files: Array.from({ length: 6 }, (_, index) => ({ relativePath: `tmp/${index}.json` })) },
    blockers: [],
    ...overrides,
  };
}

assert(provenanceManifestArtifacts.has("config/release-config-evidence.json"));
assert(optionalManifestArtifacts.includes("release/release-final-owner-queue-run-report.json"));
assert(optionalManifestArtifacts.includes("release/release-next-action-run-report.json"));
assert(optionalManifestArtifacts.includes("release/release-unblock-brief.json"));
assert(optionalManifestArtifacts.includes("release/release-artifact-path-leak-contract.json"));
assert(optionalManifestArtifacts.includes("release/release-env-owner-handoff-redacted-contract.json"));
assert(optionalManifestArtifacts.includes("release/evidence-manifest-preflight.json"));
assert.deepEqual(validateManifestArtifact(passingManifest(), { strict: true }), []);
assert.deepEqual(collectManifestProvenanceIssues({
  sourceEnvironment: "staging",
  releaseCandidate: "rc-20260614",
  evidenceOperator: "ci",
}), []);

assert(
  validateManifestArtifact(passingManifest({
    sourceEnvironment: null,
    status: "FAIL",
    summary: { blockers: 1, explainFiles: 6, invalidJsonArtifacts: 0, provenanceIssueArtifacts: 0 },
  }), { strict: true }).includes("manifest provenance sourceEnvironment is required"),
);

assert(
  validateManifestArtifact(passingManifest({
    sourceEnvironment: "local-dev",
    status: "FAIL",
    summary: { blockers: 1, explainFiles: 6, invalidJsonArtifacts: 0, provenanceIssueArtifacts: 0 },
    blockers: ["manifest provenance sourceEnvironment must identify a production-equivalent release environment"],
  }), { strict: true }).includes("manifest provenance sourceEnvironment must identify a production-equivalent release environment"),
);

assert(
  validateManifestArtifact(passingManifest({
    releaseCandidate: "local-worktree",
    status: "FAIL",
    summary: { blockers: 1, explainFiles: 6, invalidJsonArtifacts: 0, provenanceIssueArtifacts: 0 },
    blockers: ["manifest provenance releaseCandidate must identify a release version, commit, or build candidate"],
  }), { strict: true }).includes("manifest provenance releaseCandidate must identify a release version, commit, or build candidate"),
);

assert(
  validateManifestArtifact(passingManifest({
    evidenceOperator: "local-operator",
    status: "FAIL",
    summary: { blockers: 1, explainFiles: 6, invalidJsonArtifacts: 0, provenanceIssueArtifacts: 0 },
    blockers: ["manifest provenance evidenceOperator must identify a real release operator"],
  }), { strict: true }).includes("manifest provenance evidenceOperator must identify a real release operator"),
);

assert(
  validateManifestArtifact(passingManifest({
    artifacts: requiredManifestArtifacts
      .filter((relativePath) => relativePath !== "performance/authenticated-runtime-baseline.json")
      .map((relativePath) => artifact(relativePath)),
    status: "FAIL",
    summary: { blockers: 1, explainFiles: 6, invalidJsonArtifacts: 0, provenanceIssueArtifacts: 0 },
  })).includes("missing manifest report for performance/authenticated-runtime-baseline.json"),
);

assert(
  validateManifestArtifact(passingManifest({
    artifacts: requiredManifestArtifacts.map((relativePath) => relativePath === "build/docker-image-evidence.json"
      ? artifact(relativePath, { status: "INVALID_JSON", parseError: "Unexpected token" })
      : artifact(relativePath)),
    status: "FAIL",
    summary: { blockers: 1, explainFiles: 6, invalidJsonArtifacts: 1, provenanceIssueArtifacts: 0 },
  })).some((issue) => issue.includes("invalid JSON artifact build/docker-image-evidence.json")),
);

{
  const issues = validateManifestArtifact(passingManifest({
    summary: {
      requiredArtifacts: requiredManifestArtifacts.length - 1,
      presentArtifacts: requiredManifestArtifacts.length - 2,
      invalidJsonArtifacts: 1,
      explainFiles: 0,
      provenanceIssueArtifacts: 1,
      blockers: 1,
    },
    blockers: [],
  }), { strict: true });
  assert(issues.includes(`manifest summary requiredArtifacts mismatch: declared=${requiredManifestArtifacts.length - 1}, actual=${requiredManifestArtifacts.length}`));
  assert(issues.includes(`manifest summary presentArtifacts mismatch: declared=${requiredManifestArtifacts.length - 2}, actual=${requiredManifestArtifacts.length}`));
  assert(issues.includes("manifest summary invalidJsonArtifacts mismatch: declared=1, actual=0"));
  assert(issues.includes("manifest summary provenanceIssueArtifacts mismatch: declared=1, actual=0"));
  assert(issues.includes("manifest summary explainFiles mismatch: declared=0, actual=6"));
  assert(issues.includes("manifest summary blockers mismatch: declared=1, actual=0"));
}

{
  const issues = validateManifestArtifact(passingManifest({
    status: "PASS",
    blockers: ["missing artifact performance/authenticated-runtime-baseline.json"],
    summary: {
      requiredArtifacts: requiredManifestArtifacts.length,
      presentArtifacts: requiredManifestArtifacts.length,
      invalidJsonArtifacts: 0,
      explainFiles: 6,
      provenanceIssueArtifacts: 0,
      blockers: 1,
    },
  }), { strict: true });
  assert(issues.includes("manifest status must be FAIL, got PASS"));
}

{
  const duplicatePath = requiredManifestArtifacts[0];
  const issues = validateManifestArtifact(passingManifest({
    artifacts: [
      ...requiredManifestArtifacts.map((relativePath) => artifact(relativePath)),
      artifact(duplicatePath),
    ],
  }), { strict: true });
  assert(issues.includes(`duplicate manifest artifact report ${duplicatePath}`));
  assert(issues.includes(`manifest summary presentArtifacts mismatch: declared=${requiredManifestArtifacts.length}, actual=${requiredManifestArtifacts.length + 1}`));
}

{
  const unknownPath = "release/handwritten-extra.json";
  const issues = validateManifestArtifact(passingManifest({
    summary: {
      requiredArtifacts: requiredManifestArtifacts.length,
      presentArtifacts: requiredManifestArtifacts.length + 1,
      invalidJsonArtifacts: 0,
      explainFiles: 6,
      provenanceIssueArtifacts: 0,
      blockers: 0,
    },
    artifacts: [
      ...requiredManifestArtifacts.map((relativePath) => artifact(relativePath)),
      artifact(unknownPath),
    ],
  }), { strict: true });
  assert(issues.includes(`unknown manifest artifact report ${unknownPath}`));
}

{
  const optionalPath = "release/release-final-owner-queue-run-report.json";
  assert.deepEqual(validateManifestArtifact(passingManifest({
    summary: {
      requiredArtifacts: requiredManifestArtifacts.length,
      presentArtifacts: requiredManifestArtifacts.length + 1,
      invalidJsonArtifacts: 0,
      explainFiles: 6,
      provenanceIssueArtifacts: 0,
      optionalArtifacts: 1,
      blockers: 0,
    },
    artifacts: [
      ...requiredManifestArtifacts.map((relativePath) => artifact(relativePath)),
      artifact(optionalPath, { contractIssues: [] }),
    ],
  }), { strict: true }), []);
}

{
  const optionalPath = "release/release-unblock-brief.json";
  assert.deepEqual(validateManifestArtifact(passingManifest({
    summary: {
      requiredArtifacts: requiredManifestArtifacts.length,
      presentArtifacts: requiredManifestArtifacts.length + 1,
      invalidJsonArtifacts: 0,
      explainFiles: 6,
      provenanceIssueArtifacts: 0,
      optionalArtifacts: 1,
      blockers: 0,
    },
    artifacts: [
      ...requiredManifestArtifacts.map((relativePath) => artifact(relativePath)),
      artifact(optionalPath, { contractIssues: [] }),
    ],
  }), { strict: true }), []);
}

{
  const optionalPath = "release/release-next-action-run-report.json";
  assert.deepEqual(validateManifestArtifact(passingManifest({
    summary: {
      requiredArtifacts: requiredManifestArtifacts.length,
      presentArtifacts: requiredManifestArtifacts.length + 1,
      invalidJsonArtifacts: 0,
      explainFiles: 6,
      provenanceIssueArtifacts: 0,
      optionalArtifacts: 1,
      blockers: 0,
    },
    artifacts: [
      ...requiredManifestArtifacts.map((relativePath) => artifact(relativePath)),
      artifact(optionalPath, { contractIssues: [] }),
    ],
  }), { strict: true }), []);
}

{
  const optionalPath = "release/release-artifact-path-leak-contract.json";
  assert.deepEqual(validateManifestArtifact(passingManifest({
    summary: {
      requiredArtifacts: requiredManifestArtifacts.length,
      presentArtifacts: requiredManifestArtifacts.length + 1,
      invalidJsonArtifacts: 0,
      explainFiles: 6,
      provenanceIssueArtifacts: 0,
      optionalArtifacts: 1,
      blockers: 0,
    },
    artifacts: [
      ...requiredManifestArtifacts.map((relativePath) => artifact(relativePath)),
      artifact(optionalPath, { contractIssues: [] }),
    ],
  }), { strict: true }), []);
}

{
  const optionalPath = "release/release-env-owner-handoff-redacted-contract.json";
  assert.deepEqual(validateManifestArtifact(passingManifest({
    summary: {
      requiredArtifacts: requiredManifestArtifacts.length,
      presentArtifacts: requiredManifestArtifacts.length + 1,
      invalidJsonArtifacts: 0,
      explainFiles: 6,
      provenanceIssueArtifacts: 0,
      optionalArtifacts: 1,
      blockers: 0,
    },
    artifacts: [
      ...requiredManifestArtifacts.map((relativePath) => artifact(relativePath)),
      artifact(optionalPath, { contractIssues: [] }),
    ],
  }), { strict: true }), []);
}

{
  const optionalPath = "release/release-final-owner-queue-run-report.json";
  const issues = validateManifestArtifact(passingManifest({
    status: "FAIL",
    summary: {
      requiredArtifacts: requiredManifestArtifacts.length,
      presentArtifacts: requiredManifestArtifacts.length + 1,
      invalidJsonArtifacts: 0,
      explainFiles: 6,
      provenanceIssueArtifacts: 0,
      optionalArtifacts: 1,
      blockers: 1,
    },
    artifacts: [
      ...requiredManifestArtifacts.map((relativePath) => artifact(relativePath)),
      artifact(optionalPath, { contractIssues: ["final owner queue run report PASS must include at least one command entry"] }),
    ],
    blockers: ["optional artifact release/release-final-owner-queue-run-report.json: final owner queue run report PASS must include at least one command entry"],
  }), { strict: true });
  assert(issues.includes("status=FAIL, blockers=1"));
}

{
  const missingPath = "performance/authenticated-runtime-baseline.json";
  const issues = validateManifestArtifact(passingManifest({
    artifacts: requiredManifestArtifacts
      .filter((relativePath) => relativePath !== missingPath)
      .map((relativePath) => artifact(relativePath)),
    status: "FAIL",
    summary: {
      requiredArtifacts: requiredManifestArtifacts.length,
      presentArtifacts: requiredManifestArtifacts.length - 1,
      invalidJsonArtifacts: 0,
      explainFiles: 6,
      provenanceIssueArtifacts: 0,
      blockers: 0,
    },
    blockers: [],
  }), { strict: true });
  assert(issues.includes("manifest blockers length mismatch: declared=0, actual=1"));
}

{
  const issues = validateManifestArtifact(passingManifest({
    status: "FAIL",
    summary: {
      requiredArtifacts: requiredManifestArtifacts.length,
      presentArtifacts: requiredManifestArtifacts.length,
      invalidJsonArtifacts: 0,
      explainFiles: 0,
      provenanceIssueArtifacts: 0,
      blockers: 1,
    },
    explain: { present: true, directory: "/tmp/ddd-explain", files: [] },
    blockers: ["wrong blocker text"],
  }), { strict: true });
  assert(issues.includes("manifest blockers[0] mismatch: declared=wrong blocker text, actual=no explain JSON files in /tmp/ddd-explain"));
}

{
  const badPath = requiredManifestArtifacts[0];
  const issues = validateManifestArtifact(passingManifest({
    artifacts: requiredManifestArtifacts.map((relativePath) => relativePath === badPath
      ? artifact(relativePath, { bytes: 0, sha256: "not-a-sha", timestamp: null })
      : artifact(relativePath)),
  }), { strict: true });
  assert(issues.includes(`manifest artifact ${badPath} bytes must be positive`));
  assert(issues.includes(`manifest artifact ${badPath} sha256 must be 64 hex characters`));
  assert(issues.includes(`manifest artifact ${badPath} timestamp is required`));
}

{
  const badPath = requiredManifestArtifacts[0];
  const issues = validateManifestArtifact(passingManifest({
    artifacts: requiredManifestArtifacts.map((relativePath) => relativePath === badPath
      ? artifact(relativePath, { timestamp: { field: "", value: "not-a-date" } })
      : artifact(relativePath)),
  }), { strict: true });
  assert(issues.includes(`manifest artifact ${badPath} timestamp.field is required`));
  assert(issues.includes(`manifest artifact ${badPath} timestamp.value must be ISO-like datetime`));
}

console.log("[ddd-release-evidence-manifest-contract.test] ok");
