#!/usr/bin/env node

import fs from "node:fs";
import { redactReleaseOutput } from "./ddd-release-redact-output.mjs";

const releaseDir = process.env.DDD_RELEASE_PREFLIGHT_CAPTURE_DIR || "artifacts/ddd/release";
const defaultOutputPath = `${releaseDir}/release-preflight-output.txt`;
const strictOutputPath = `${releaseDir}/release-preflight-strict-output.txt`;
const strictStatusPath = `${releaseDir}/release-preflight-strict-status.txt`;
const strictReportPath = `${releaseDir}/release-preflight-strict-report.json`;
const contractReportPath = process.env.DDD_RELEASE_PREFLIGHT_CAPTURE_CONTRACT_REPORT
  || `${releaseDir}/release-preflight-capture-contract.json`;

function writeContractReport(report) {
  fs.writeFileSync(contractReportPath, `${JSON.stringify({
    generatedAt: new Date().toISOString(),
    contract: "release-preflight-capture",
    redacted: true,
    redactionPolicy: {
      defaultOutputRedacted: true,
      strictOutputRedacted: true,
      rule: "release preflight captured output must not expose concrete secret values, release env files, or local repo paths",
    },
    ...report,
  }, null, 2)}\n`);
}

const requiredFiles = [
  defaultOutputPath,
  strictOutputPath,
  strictStatusPath,
];

const missing = requiredFiles.filter((file) => !fs.existsSync(file));
if (missing.length > 0) {
  throw new Error(`missing release preflight capture files: ${missing.join(", ")}`);
}

const defaultOutput = fs.readFileSync(defaultOutputPath, "utf8");
const strictOutput = fs.readFileSync(strictOutputPath, "utf8");
if (!defaultOutput.includes("[ddd-release-preflight]")) {
  throw new Error("default release preflight output does not look like preflight output");
}
if (!strictOutput.includes("[ddd-release-preflight]")) {
  throw new Error("strict release preflight output does not look like preflight output");
}
if (redactReleaseOutput(defaultOutput) !== defaultOutput) {
  throw new Error("default release preflight output must be redacted before upload");
}
if (redactReleaseOutput(strictOutput) !== strictOutput) {
  throw new Error("strict release preflight output must be redacted before upload");
}

const statusText = fs.readFileSync(strictStatusPath, "utf8").trim();
if (!/^\d+$/.test(statusText)) {
  throw new Error(`invalid strict preflight status: ${statusText}`);
}

const status = Number(statusText);
if (status === 127 && !fs.existsSync(strictReportPath)) {
  writeContractReport({
    status: "SKIPPED",
    reason: "preflight gate was not generated",
    strictStatus: status,
    failedStep: null,
    strictReportPath,
  });
  console.log("[ddd-release-preflight-capture] skipped strict report validation because preflight gate was not generated");
  process.exit(0);
}

if (!fs.existsSync(strictReportPath)) {
  throw new Error("missing strict release preflight report");
}

const report = JSON.parse(fs.readFileSync(strictReportPath, "utf8"));
if (report.enforce !== true) {
  throw new Error("strict release preflight report must have enforce=true");
}
if (report.advisoryOnly !== false) {
  throw new Error("strict release preflight report must have advisoryOnly=false");
}
if (report.advisoryFailureCount !== 0) {
  throw new Error("strict release preflight report must have advisoryFailureCount=0");
}
if (!Array.isArray(report.advisoryFailures) || report.advisoryFailures.length !== 0) {
  throw new Error("strict release preflight report must have empty advisoryFailures");
}
if (typeof report.cutoverAllowed !== "boolean") {
  throw new Error("strict release preflight report must include boolean cutoverAllowed");
}
if (typeof report.releaseEnvFileCutoverSafe !== "boolean") {
  throw new Error("strict release preflight report must include boolean releaseEnvFileCutoverSafe");
}
if (typeof report.finalRecommendation !== "string" || report.finalRecommendation.length === 0) {
  throw new Error("strict release preflight report must include finalRecommendation");
}
const expectedStepNames = [
  "artifact-integrity",
  "manifest-provenance-preflight",
  "artifact-path-leak",
  "unblock-brief",
  "env-owner-handoff-redacted",
  "env-readiness",
  "final-go-no-go",
];
if (!Array.isArray(report.steps) || report.steps.length !== expectedStepNames.length) {
  throw new Error(`strict release preflight report must contain ${expectedStepNames.length} steps`);
}

const stepNames = report.steps.map((step) => step.name);
if (JSON.stringify(stepNames) !== JSON.stringify(expectedStepNames)) {
  throw new Error(`strict release preflight report step order mismatch: ${stepNames.join(",")}`);
}
if (!["PASS", "NO_GO", "FAIL"].includes(report.status)) {
  throw new Error(`strict release preflight report has invalid status: ${report.status}`);
}
if (status === 0 && report.status !== "PASS") {
  throw new Error(`strict preflight status is 0 but report status is ${report.status}`);
}
if (status !== 0 && report.status === "PASS") {
  throw new Error("strict preflight failed but report status is PASS");
}

const failedStep = report.steps.find((step) => step.exitCode === status && status !== 0)?.name || null;
if (status !== 0 && report.failedStep !== failedStep) {
  throw new Error(`strict preflight failedStep mismatch: report=${report.failedStep} derived=${failedStep}`);
}
if (status === 0 && report.failedStep) {
  throw new Error(`strict preflight failedStep must be empty when status is 0: ${report.failedStep}`);
}

writeContractReport({
  status: "PASS",
  strictStatus: status,
  strictReportStatus: report.status,
  failedStep: report.failedStep || null,
  strictReportPath,
  strictOutputPath,
  defaultOutputPath,
});
console.log(`[ddd-release-preflight-capture] strictStatus=${status} reportStatus=${report.status} failedStep=${report.failedStep || "none"}`);
