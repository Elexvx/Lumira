#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {
  optionalManifestArtifacts,
  provenanceManifestArtifacts,
  requiredManifestArtifacts,
} from "./ddd-release-evidence-manifest-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const checklistPath = path.join(repoRoot, "docs", "34-ddd-release-evidence-checklist.md");
const checklist = fs.readFileSync(checklistPath, "utf8");
const requiredSet = new Set(requiredManifestArtifacts);
const optionalSet = new Set(optionalManifestArtifacts);

assert.equal(requiredSet.size, requiredManifestArtifacts.length, "requiredManifestArtifacts must not contain duplicates");
assert.equal(optionalSet.size, optionalManifestArtifacts.length, "optionalManifestArtifacts must not contain duplicates");

for (const artifactPath of requiredManifestArtifacts) {
  assert.match(
    checklist,
    new RegExp(escapeRegExp(`artifacts/ddd/${artifactPath}`)),
    `release evidence checklist must document artifacts/ddd/${artifactPath}`,
  );
}

for (const artifactPath of optionalManifestArtifacts) {
  assert.match(
    checklist,
    new RegExp(escapeRegExp(`artifacts/ddd/${artifactPath}`)),
    `release evidence checklist must document optional artifacts/ddd/${artifactPath}`,
  );
  assert.ok(
    !requiredSet.has(artifactPath),
    `optional manifest artifact ${artifactPath} must not also be required`,
  );
}

for (const artifactPath of provenanceManifestArtifacts) {
  assert.ok(
    requiredSet.has(artifactPath),
    `provenance-sensitive artifact ${artifactPath} must also be required by the manifest`,
  );
}

for (const forbidden of [
  "release/evidence-manifest.json",
  "release/release-evidence-gate.json",
  "release/readiness-summary.json",
  "release/readiness-summary.md",
  "release/orchestrator-report.json",
]) {
  assert.ok(
    !requiredSet.has(forbidden),
    `${forbidden} must not be a required checksum artifact; it is either derived after manifest generation or would create a release evidence cycle`,
  );
}

assert.ok(
  requiredSet.has("performance/authenticated-runtime-baseline.json"),
  "authenticated runtime baseline must remain a required manifest artifact so performance regressions cannot bypass release packaging",
);
assert.ok(
  requiredSet.has("config/release-config-evidence.json"),
  "release config evidence must remain a required manifest artifact",
);
assert.ok(
  provenanceManifestArtifacts.has("config/release-config-evidence.json"),
  "release config evidence must remain provenance-sensitive",
);

console.log("[ddd-release-evidence-manifest-sync.test] ok");

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
