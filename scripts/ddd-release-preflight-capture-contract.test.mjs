#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { redactReleaseOutput } from "./ddd-release-redact-output.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");

function makeCaptureDir(name) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), `lumira-${name}-`));
  fs.writeFileSync(path.join(directory, "release-preflight-output.txt"), "[ddd-release-preflight] step=artifact-integrity\n");
  fs.writeFileSync(path.join(directory, "release-preflight-strict-output.txt"), "[ddd-release-preflight] step=env-readiness\n");
  return directory;
}

function writeReport(directory, report) {
  fs.writeFileSync(path.join(directory, "release-preflight-strict-report.json"), `${JSON.stringify({
    enforce: true,
    advisoryOnly: false,
    advisoryFailureCount: 0,
    advisoryFailures: [],
    cutoverAllowed: false,
    releaseEnvFileCutoverSafe: false,
    finalRecommendation: "NO_GO_STRICT",
    ...report,
  }, null, 2)}\n`);
}

function preflightSteps(overrides = {}) {
  return [
    { name: "artifact-integrity", exitCode: 0 },
    { name: "manifest-provenance-preflight", exitCode: 0 },
    { name: "artifact-path-leak", exitCode: 0 },
    { name: "unblock-brief", exitCode: 0 },
    { name: "env-owner-handoff-redacted", exitCode: 0 },
    { name: "env-readiness", exitCode: 0 },
    { name: "final-go-no-go", exitCode: 0 },
  ].map((step) => ({ ...step, ...(overrides[step.name] || {}) }));
}

function runContract(directory) {
  return spawnSync("node", ["scripts/ddd-release-preflight-capture-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_PREFLIGHT_CAPTURE_DIR: directory,
    },
  });
}

const passDir = makeCaptureDir("preflight-capture-pass");
fs.writeFileSync(path.join(passDir, "release-preflight-strict-status.txt"), "0\n");
writeReport(passDir, {
  status: "PASS",
  enforce: true,
  failedStep: null,
  steps: preflightSteps(),
});
const passResult = runContract(passDir);
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /strictStatus=0/);
const passContractReport = JSON.parse(fs.readFileSync(path.join(passDir, "release-preflight-capture-contract.json"), "utf8"));
assert.equal(passContractReport.status, "PASS");
assert.equal(passContractReport.redacted, true);
assert.equal(passContractReport.redactionPolicy.defaultOutputRedacted, true);
assert.equal(passContractReport.redactionPolicy.strictOutputRedacted, true);
assert.equal(passContractReport.strictStatus, 0);
assert.equal(passContractReport.strictReportStatus, "PASS");

const noGoDir = makeCaptureDir("preflight-capture-no-go");
fs.writeFileSync(path.join(noGoDir, "release-preflight-strict-status.txt"), "21\n");
writeReport(noGoDir, {
  status: "NO_GO",
  enforce: true,
  failedStep: "env-readiness",
  steps: preflightSteps({
    "env-readiness": { exitCode: 21 },
    "final-go-no-go": { exitCode: -1 },
  }),
});
const noGoResult = runContract(noGoDir);
assert.equal(noGoResult.status, 0, noGoResult.stderr);
assert.match(noGoResult.stdout, /strictStatus=21/);
const noGoContractReport = JSON.parse(fs.readFileSync(path.join(noGoDir, "release-preflight-capture-contract.json"), "utf8"));
assert.equal(noGoContractReport.status, "PASS");
assert.equal(noGoContractReport.strictStatus, 21);
assert.equal(noGoContractReport.strictReportStatus, "NO_GO");
assert.equal(noGoContractReport.failedStep, "env-readiness");

const skippedDir = makeCaptureDir("preflight-capture-skipped");
fs.writeFileSync(path.join(skippedDir, "release-preflight-strict-status.txt"), "127\n");
const skippedResult = runContract(skippedDir);
assert.equal(skippedResult.status, 0, skippedResult.stderr);
assert.match(skippedResult.stdout, /skipped strict report validation/);
const skippedContractReport = JSON.parse(fs.readFileSync(path.join(skippedDir, "release-preflight-capture-contract.json"), "utf8"));
assert.equal(skippedContractReport.status, "SKIPPED");
assert.equal(skippedContractReport.redacted, true);
assert.equal(skippedContractReport.redactionPolicy.defaultOutputRedacted, true);
assert.equal(skippedContractReport.redactionPolicy.strictOutputRedacted, true);
assert.equal(skippedContractReport.strictStatus, 127);

const missingDir = makeCaptureDir("preflight-capture-missing");
const missingResult = runContract(missingDir);
assert.notEqual(missingResult.status, 0);
assert.match(missingResult.stderr, /missing release preflight capture files/);

const mismatchDir = makeCaptureDir("preflight-capture-mismatch");
fs.writeFileSync(path.join(mismatchDir, "release-preflight-strict-status.txt"), "21\n");
writeReport(mismatchDir, {
  status: "NO_GO",
  enforce: true,
  failedStep: "final-go-no-go",
  steps: preflightSteps({
    "env-readiness": { exitCode: 21 },
    "final-go-no-go": { exitCode: -1 },
  }),
});
const mismatchResult = runContract(mismatchDir);
assert.notEqual(mismatchResult.status, 0);
assert.match(mismatchResult.stderr, /failedStep mismatch/);

const badStepOrderDir = makeCaptureDir("preflight-capture-bad-step-order");
fs.writeFileSync(path.join(badStepOrderDir, "release-preflight-strict-status.txt"), "21\n");
writeReport(badStepOrderDir, {
  status: "NO_GO",
  enforce: true,
  failedStep: "env-readiness",
  steps: [
    { name: "env-readiness", exitCode: 21 },
    { name: "artifact-integrity", exitCode: 0 },
    { name: "manifest-provenance-preflight", exitCode: 0 },
    { name: "artifact-path-leak", exitCode: 0 },
    { name: "unblock-brief", exitCode: 0 },
    { name: "env-owner-handoff-redacted", exitCode: 0 },
    { name: "final-go-no-go", exitCode: -1 },
  ],
});
const badStepOrderResult = runContract(badStepOrderDir);
assert.notEqual(badStepOrderResult.status, 0);
assert.match(badStepOrderResult.stderr, /step order mismatch/);

const failedButPassDir = makeCaptureDir("preflight-capture-failed-but-pass");
fs.writeFileSync(path.join(failedButPassDir, "release-preflight-strict-status.txt"), "21\n");
writeReport(failedButPassDir, {
  status: "PASS",
  enforce: true,
  failedStep: "env-readiness",
  steps: preflightSteps({
    "env-readiness": { exitCode: 21 },
    "final-go-no-go": { exitCode: -1 },
  }),
});
const failedButPassResult = runContract(failedButPassDir);
assert.notEqual(failedButPassResult.status, 0);
assert.match(failedButPassResult.stderr, /failed but report status is PASS/);

const missingAdvisorySchemaDir = makeCaptureDir("preflight-capture-missing-advisory-schema");
fs.writeFileSync(path.join(missingAdvisorySchemaDir, "release-preflight-strict-status.txt"), "0\n");
fs.writeFileSync(path.join(missingAdvisorySchemaDir, "release-preflight-strict-report.json"), `${JSON.stringify({
  status: "PASS",
  enforce: true,
  failedStep: null,
  cutoverAllowed: false,
  releaseEnvFileCutoverSafe: false,
  finalRecommendation: "NO_GO_STRICT",
  steps: preflightSteps(),
}, null, 2)}\n`);
const missingAdvisorySchemaResult = runContract(missingAdvisorySchemaDir);
assert.notEqual(missingAdvisorySchemaResult.status, 0);
assert.match(missingAdvisorySchemaResult.stderr, /advisoryOnly=false/);

const unredactedOutputDir = makeCaptureDir("preflight-capture-unredacted-output");
fs.appendFileSync(path.join(unredactedOutputDir, "release-preflight-output.txt"), `DDD_RELEASE_ENV_FILE=${path.join(repoRoot, ".env.release.local")} JWT_SECRET=real-secret\n`);
fs.writeFileSync(path.join(unredactedOutputDir, "release-preflight-strict-status.txt"), "0\n");
writeReport(unredactedOutputDir, {
  status: "PASS",
  enforce: true,
  failedStep: null,
  steps: preflightSteps(),
});
const unredactedOutputResult = runContract(unredactedOutputDir);
assert.notEqual(unredactedOutputResult.status, 0);
assert.match(unredactedOutputResult.stderr, /must be redacted before upload/);

const redactedOutputDir = makeCaptureDir("preflight-capture-redacted-output");
fs.appendFileSync(path.join(redactedOutputDir, "release-preflight-output.txt"), redactReleaseOutput(`DDD_RELEASE_ENV_FILE=${path.join(repoRoot, ".env.release.local")} JWT_SECRET=real-secret\n`));
fs.writeFileSync(path.join(redactedOutputDir, "release-preflight-strict-status.txt"), "0\n");
writeReport(redactedOutputDir, {
  status: "PASS",
  enforce: true,
  failedStep: null,
  steps: preflightSteps(),
});
const redactedOutputResult = runContract(redactedOutputDir);
assert.equal(redactedOutputResult.status, 0, redactedOutputResult.stderr);

console.log("[ddd-release-preflight-capture-contract.test] ok");
