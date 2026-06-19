#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { validateReleaseGateArtifact } from "./ddd-release-gate-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const scriptPath = path.join(repoRoot, "scripts", "ddd-release-evidence-gate.mjs");
const testPath = path.join(repoRoot, "scripts", "ddd-release-evidence-gate.test.mjs");
const reportPath = path.join(repoRoot, "artifacts", "ddd", "release", "release-evidence-gate.json");
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const source = fs.readFileSync(scriptPath, "utf8");
for (const snippet of [
  "DDD_RELEASE_EVIDENCE_STRICT",
  "validateReleaseGateArtifact(report)",
  "process.exit(1)",
  "blockers.length > 0 && strict",
  "validateAuthenticatedPerformance",
  "validateRuntimeReadiness",
  "validateReleaseConfigArtifact",
  "validateEvidenceManifest",
  "validateRollbackDrillContract",
  "validateMigrationEvidenceContract",
  "validateDockerBuildArtifact",
  "validateFrontendSmokeArtifact",
]) {
  if (!source.includes(snippet)) addFailure(`release evidence gate must include ${snippet}`);
}

const testSource = fs.readFileSync(testPath, "utf8");
for (const snippet of [
  "DDD_RELEASE_EVIDENCE_STRICT: \"true\"",
  "assert.notEqual(result.status, 0)",
  "validateReleaseGateArtifact(report)",
  "strict authenticated performance actual requires HTTPS baseUrl evidence",
  "strict AI runtime drill requires HTTPS baseUrl evidence",
  "strict frontend smoke requires HTTPS baseUrl evidence",
  "release-env-lint-placeholders",
  "DDD_RELEASE_EVIDENCE_STRICT: \"false\"",
  "assert.equal(advisoryResult.status, 0)",
]) {
  if (!testSource.includes(snippet)) addFailure(`release evidence gate test must cover ${snippet}`);
}

const testRun = spawnSync("node", ["scripts/ddd-release-evidence-gate.test.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: { ...process.env },
});
if (testRun.status !== 0) {
  addFailure(`release evidence gate behavior test must pass: ${testRun.stderr || testRun.stdout}`);
}

spawnSync("node", ["scripts/ddd-release-evidence-gate.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_RELEASE_EVIDENCE_REPORT: reportPath,
  },
});

if (!fs.existsSync(reportPath)) {
  addFailure(`release evidence gate report must exist: ${reportPath}`);
} else {
  const report = readJson(reportPath);
  for (const issue of validateReleaseGateArtifact(report)) {
    addFailure(`current release evidence gate report is invalid: ${issue}`);
  }
  if ((report.summary?.checks || 0) <= 0) addFailure("current release evidence gate report must include checks");
  if (report.strict && (report.summary?.blockers || 0) <= 0) {
    addFailure("strict release evidence gate report must keep blockers until release evidence is complete");
  }
  if (!report.strict && !Number.isInteger(report.summary?.blockers)) {
    addFailure("non-strict release evidence gate summary.blockers must be present");
  }
  if (!Array.isArray(report.blockers) || report.blockers.length !== report.summary?.blockers) {
    addFailure("current release evidence gate blockers length must match summary");
  }
  if (!Array.isArray(report.warnings) || report.warnings.length !== report.summary?.warnings) {
    addFailure("current release evidence gate warnings length must match summary");
  }
  const releaseEnvLintStatus = readJson(path.join(repoRoot, "artifacts", "ddd", "release", "release-env-lint.json"));
  const hasReleaseEnvLintBlocker = Boolean(report.blockers.some((item) => item.startsWith("release-env-lint:")));
  if (releaseEnvLintStatus.status === "FAIL") {
    if (!hasReleaseEnvLintBlocker) {
      addFailure("current release evidence gate must expose release-env-lint blocker while release-env-lint is FAIL");
    }
  } else {
    if (hasReleaseEnvLintBlocker) {
      addFailure("current release evidence gate should not expose release-env-lint blocker while release-env-lint is PASS");
    }
  }
  if (report.strict) {
    if (!report.blockers.some((blocker) => blocker.startsWith("authenticated-performance-baseline-strict:"))) {
      addFailure("current release evidence gate must expose authenticated-performance-baseline-strict: blocker in strict mode");
    }
    if (!report.blockers.some((blocker) => blocker.startsWith("release-evidence-manifest:"))) {
      addFailure("current release evidence gate must expose release-evidence-manifest: blocker in strict mode");
    }
    if ((report.summary?.blockers || 0) <= 0) addFailure("current release evidence gate report must keep blockers until release evidence is complete");
  } else if ((report.summary?.blockers || 0) !== 0) {
    addFailure("non-strict release evidence gate report should not report release blockers by default");
  }
}

if (failures.length > 0) {
  throw new Error(`release evidence gate contract failed: ${failures.join("; ")}`);
}

console.log("[ddd-release-evidence-gate-contract] ok");
