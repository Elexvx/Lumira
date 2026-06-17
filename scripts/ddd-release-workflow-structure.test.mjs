#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const workflowFile = path.join(repoRoot, ".github", "workflows", "ddd-release-evidence.yml");
const ciWorkflowFile = path.join(repoRoot, ".github", "workflows", "ci.yml");

const workflowText = fs.readFileSync(workflowFile, "utf8");
const ciWorkflowText = fs.readFileSync(ciWorkflowFile, "utf8");

function stepNames(text) {
  return [...text.matchAll(/^\s{6}- name:\s+(.+)$/gm)].map((match) => match[1].trim());
}

function stepIndex(name) {
  const index = workflowText.indexOf(`- name: ${name}`);
  assert(index >= 0, `release evidence workflow must include step: ${name}`);
  return index;
}

function assertBefore(left, right) {
  assert(stepIndex(left) < stepIndex(right), `${left} must run before ${right}`);
}

const names = stepNames(workflowText);
assert(names.length > 0, "release evidence workflow must declare named steps");
const duplicateNames = names.filter((name, index) => names.indexOf(name) !== index);
assert.deepEqual(duplicateNames, [], "release evidence workflow step names must be unique");

assertBefore("Prepare release evidence environment file", "Preflight release configuration evidence");
assertBefore("Preflight release configuration evidence", "Run release evidence orchestrator");
assertBefore("Run release evidence orchestrator", "Refresh final go no-go packet");
assertBefore("Refresh final go no-go packet", "Generate release unblock brief");
assertBefore("Generate release unblock brief", "Validate release unblock brief contract");
assertBefore("Validate release unblock brief contract", "Validate final owner queue run report");
assertBefore("Validate final owner queue run report", "Validate release execution run report");
assertBefore("Validate release execution run report", "Validate release artifact integrity contract");
assertBefore("Validate release artifact integrity contract", "Validate release artifact integrity gate contract");
assertBefore("Validate release artifact integrity gate contract", "Validate release artifact path leak contract");
assertBefore("Validate release artifact path leak contract", "Validate release cutover contract");
assertBefore("Validate release cutover contract", "Validate release final go no-go gate contract");
assertBefore("Validate release final go no-go gate contract", "Validate release preflight gate contract");
assertBefore("Validate release preflight gate contract", "Validate release evidence gate contract");
assertBefore("Validate release evidence gate contract", "Validate EXPLAIN gate report");
assertBefore("Validate EXPLAIN gate report", "Capture release preflight output");
assertBefore("Capture release preflight output", "Validate release preflight capture");
assertBefore("Validate release preflight capture", "Refresh release manifest after preflight capture");
assertBefore("Refresh release manifest after preflight capture", "Upload release evidence artifacts");
assertBefore("Upload release evidence artifacts", "Append release readiness summary");
assertBefore("Append release readiness summary", "Enforce release evidence result");

assert(
  workflowText.includes("node scripts/ddd-release-execution-run-report-contract.mjs"),
  "release evidence workflow must validate release execution run reports",
);
assert(
  workflowText.includes("node scripts/ddd-release-execution-run-report-summary.mjs"),
  "release evidence workflow must append release execution run report summaries",
);
assert(
  workflowText.includes("node scripts/ddd-release-artifact-path-leak-contract.mjs"),
  "release evidence workflow must validate release artifacts for local path leaks",
);
assert(
  workflowText.includes("DDD_RELEASE_PREFLIGHT_ENFORCE=1"),
  "release evidence workflow must keep strict preflight enforcement available",
);
assert(
  workflowText.includes("node scripts/ddd-release-redact-output.mjs < artifacts/ddd/release/release-preflight-output.txt"),
  "release evidence workflow must redact captured preflight output",
);
assert(
  workflowText.includes("DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false"),
  "release evidence workflow must refresh manifest after preflight capture without failing before upload",
);
assert(
  workflowText.includes("DDD_RELEASE_MANIFEST_CHECK_ENV=true"),
  "release evidence workflow must refresh manifest provenance preflight before the final manifest checksum",
);
assert(
  workflowText.includes("node scripts/ddd-release-unblock-brief-contract.mjs"),
  "release evidence workflow must revalidate unblock brief after the final pre-upload manifest refresh",
);

assert(
  ciWorkflowText.includes("node --check scripts/ddd-release-workflow-structure.test.mjs"),
  "CI syntax checks must cover release workflow structure tests",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-release-workflow-structure.test.mjs"),
  "CI must run release workflow structure tests",
);

console.log("[ddd-release-workflow-structure.test] ok");
