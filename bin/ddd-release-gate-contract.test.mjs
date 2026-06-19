#!/usr/bin/env node

import assert from "node:assert/strict";
import { validateReleaseGateArtifact } from "./ddd-release-gate-contract.mjs";

function validArtifact() {
  return {
    generatedAt: "2026-06-14T00:00:00.000Z",
    strict: true,
    artifactRoot: "/tmp/artifacts/ddd",
    summary: {
      checks: 3,
      blockers: 1,
      warnings: 1,
    },
    checks: [
      {
        name: "runtime-readiness-summary",
        status: "present",
        detail: "readiness/summary.json",
        file: "/tmp/artifacts/ddd/readiness/summary.json",
      },
      {
        name: "runtime-readiness-production-equivalence",
        status: "blocker",
        detail: "strict runtime readiness requires HTTPS baseUrl evidence",
        file: null,
      },
      {
        name: "runtime-readiness-environment",
        status: "warning",
        detail: "artifact is local-only: http://127.0.0.1:8080",
        file: null,
      },
    ],
    blockers: [
      "runtime-readiness-production-equivalence: strict runtime readiness requires HTTPS baseUrl evidence",
    ],
    blockerDetails: [{
      check: "runtime-readiness-production-equivalence",
      detail: "strict runtime readiness requires HTTPS baseUrl evidence",
      file: null,
    }],
    warnings: [
      "runtime-readiness-environment: artifact is local-only: http://127.0.0.1:8080",
    ],
    warningDetails: [{
      check: "runtime-readiness-environment",
      detail: "artifact is local-only: http://127.0.0.1:8080",
      file: null,
    }],
  };
}

assert.deepEqual(validateReleaseGateArtifact(validArtifact()), []);

{
  const artifact = validArtifact();
  artifact.summary.blockers = 0;
  assert.deepEqual(validateReleaseGateArtifact(artifact), [
    "release gate summary blockers mismatch: declared=0, actual=1",
  ]);
}

{
  const artifact = validArtifact();
  artifact.blockers = [];
  assert.deepEqual(validateReleaseGateArtifact(artifact), [
    "release gate blockers length mismatch: declared=0, actual=1",
  ]);
}

{
  const artifact = validArtifact();
  artifact.warnings[0] = "wrong warning";
  assert.deepEqual(validateReleaseGateArtifact(artifact), [
    "release gate warnings[0] mismatch: declared=wrong warning, actual=runtime-readiness-environment: artifact is local-only: http://127.0.0.1:8080",
  ]);
}

{
  const artifact = validArtifact();
  artifact.checks[0] = {
    status: "skipped",
    detail: "",
    file: 123,
  };
  assert.deepEqual(validateReleaseGateArtifact(artifact), [
    "release gate checks[0].name is required",
    "release gate check 0 has invalid status skipped",
    "release gate check 0 detail is required",
    "release gate check 0 file must be a string or null",
  ]);
}

{
  const artifact = validArtifact();
  artifact.summary.checks = 2;
  artifact.summary.warnings = 0;
  assert.deepEqual(validateReleaseGateArtifact(artifact), [
    "release gate summary checks mismatch: declared=2, actual=3",
    "release gate summary warnings mismatch: declared=0, actual=1",
  ]);
}

console.log("[ddd-release-gate-contract.test] ok");
