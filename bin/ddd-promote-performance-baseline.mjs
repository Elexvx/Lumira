#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { createHash } from "node:crypto";
import { collectProvenanceIssues, evidenceValueIssue } from "./ddd-release-evidence-utils.mjs";
import { validateAuthenticatedPerformanceShape } from "./ddd-performance-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const sourceFile = process.env.DDD_AUTH_PERF_BASELINE_SOURCE
  ? path.resolve(process.env.DDD_AUTH_PERF_BASELINE_SOURCE)
  : path.join(repoRoot, "artifacts", "ddd", "performance", "authenticated-runtime-actual.json");
const outputFile = process.env.DDD_AUTH_PERF_BASELINE_OUTPUT
  ? path.resolve(process.env.DDD_AUTH_PERF_BASELINE_OUTPUT)
  : path.join(repoRoot, "artifacts", "ddd", "performance", "authenticated-runtime-baseline.json");
const promotionAuditFile = process.env.DDD_AUTH_PERF_BASELINE_PROMOTION_OUTPUT
  ? path.resolve(process.env.DDD_AUTH_PERF_BASELINE_PROMOTION_OUTPUT)
  : path.join(repoRoot, "artifacts", "ddd", "performance", "authenticated-runtime-baseline-promotion.json");
const acceptedBy = process.env.DDD_AUTH_PERF_BASELINE_ACCEPTED_BY || "";
const sourceEnvironment = process.env.DDD_AUTH_PERF_BASELINE_ENVIRONMENT || "";
const sourceArtifact = process.env.DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT || sourceFile;
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";

const blockers = [];

function portablePath(filePath) {
  const absolutePath = path.resolve(filePath);
  return absolutePath === repoRoot || absolutePath.startsWith(`${repoRoot}${path.sep}`)
    ? (path.relative(repoRoot, absolutePath) || ".").replaceAll("\\", "/")
    : filePath;
}

function sha256(file) {
  return createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function isLocalBaseUrl(value) {
  return typeof value === "string"
    && /^(http:\/\/)?(127\.0\.0\.1|localhost|\[::1\])(?::\d+)?/i.test(value.replace(/^https?:\/\//i, ""));
}

function isHttpsBaseUrl(value) {
  try {
    return new URL(value).protocol === "https:";
  } catch {
    return false;
  }
}

function summarizeActual(actual) {
  if (!actual) {
    return null;
  }
  return {
    baseUrl: actual.baseUrl || null,
    localOnly: isLocalBaseUrl(actual.baseUrl),
    sourceEnvironment: actual.sourceEnvironment || null,
    releaseCandidate: actual.releaseCandidate || null,
    evidenceOperator: actual.evidenceOperator || null,
    failed: actual.failed ?? null,
    p95: actual.p95 ?? null,
    uploadStatus: actual.upload?.status ?? null,
    uploadElapsedMs: actual.upload?.elapsedMs ?? null,
    endpointCount: actual.perEndpoint && typeof actual.perEndpoint === "object"
      ? Object.keys(actual.perEndpoint).length
      : 0,
  };
}

function writePromotionAudit(status, actual = null, baseline = null) {
  const audit = {
    generatedAt: new Date().toISOString(),
    status,
    sourceFile: portablePath(sourceFile),
    sourceSha256: actual && fs.existsSync(sourceFile) ? sha256(sourceFile) : null,
    outputFile: portablePath(outputFile),
    sourceArtifact: portablePath(sourceArtifact),
    sourceEnvironment: sourceEnvironment || null,
    releaseCandidate: releaseCandidate || null,
    acceptedBy: acceptedBy || null,
    requiredEnvKeys: [
      "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY",
      "DDD_AUTH_PERF_BASELINE_ENVIRONMENT",
      "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT",
      "DDD_RELEASE_CANDIDATE",
    ],
    sourceActual: summarizeActual(actual),
    baseline: baseline ? {
      baselineType: baseline.baselineType || null,
      acceptedAt: baseline.acceptedAt || null,
      acceptedBy: baseline.acceptedBy || null,
      sourceEnvironment: baseline.sourceEnvironment || null,
      sourceArtifact: baseline.sourceArtifact || null,
      sourceSha256: baseline.sourceSha256 || null,
      releaseCandidate: baseline.releaseCandidate || null,
      p95: baseline.p95 ?? null,
      uploadElapsedMs: baseline.upload?.elapsedMs ?? null,
      endpointCount: baseline.perEndpoint && typeof baseline.perEndpoint === "object"
        ? Object.keys(baseline.perEndpoint).length
        : 0,
    } : null,
    blockers,
  };
  fs.mkdirSync(path.dirname(promotionAuditFile), { recursive: true });
  fs.writeFileSync(promotionAuditFile, `${JSON.stringify(audit, null, 2)}\n`);
}

if (!fs.existsSync(sourceFile)) {
  blockers.push(`source actual artifact does not exist: ${sourceFile}`);
}
for (const [name, value] of Object.entries({
  DDD_AUTH_PERF_BASELINE_ACCEPTED_BY: acceptedBy,
  DDD_AUTH_PERF_BASELINE_ENVIRONMENT: sourceEnvironment,
  DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT: sourceArtifact,
})) {
  const issue = evidenceValueIssue(value);
  if (issue) {
    blockers.push(`${name} ${issue}`);
  }
}

let actual = null;
if (blockers.length === 0) {
  try {
    actual = JSON.parse(fs.readFileSync(sourceFile, "utf8"));
  } catch (error) {
    blockers.push(`source actual artifact is invalid JSON: ${error.message}`);
  }
}

if (actual) {
  if (isLocalBaseUrl(actual.baseUrl)) {
    blockers.push(`source actual artifact must be production-equivalent and non-local, got ${actual.baseUrl}`);
  }
  if (!isHttpsBaseUrl(actual.baseUrl)) {
    blockers.push(`source actual artifact must use HTTPS production-equivalent baseUrl, got ${actual.baseUrl || "missing"}`);
  }
  if (actual.failed !== 0) {
    blockers.push(`source actual artifact has failed=${actual.failed}`);
  }
  for (const issue of collectProvenanceIssues({
    sourceEnvironment: actual.sourceEnvironment,
    releaseCandidate: actual.releaseCandidate,
    evidenceOperator: actual.evidenceOperator,
  })) {
    blockers.push(`source actual artifact provenance ${issue}`);
  }
  if (actual.sourceEnvironment && actual.sourceEnvironment !== sourceEnvironment) {
    blockers.push(`source actual artifact sourceEnvironment ${actual.sourceEnvironment} does not match DDD_AUTH_PERF_BASELINE_ENVIRONMENT ${sourceEnvironment}`);
  }
  if (releaseCandidate && actual.releaseCandidate && actual.releaseCandidate !== releaseCandidate) {
    blockers.push(`source actual artifact releaseCandidate ${actual.releaseCandidate} does not match DDD_RELEASE_CANDIDATE ${releaseCandidate}`);
  }
  blockers.push(...validateAuthenticatedPerformanceShape("source actual artifact", actual, { strict: true }));
}

if (blockers.length > 0) {
  writePromotionAudit("FAIL", actual);
  for (const blocker of blockers) {
    console.error(`[ddd-promote-performance-baseline] ${blocker}`);
  }
  console.error(`[ddd-promote-performance-baseline] wrote promotion audit to ${promotionAuditFile}`);
  process.exit(1);
}

const baseline = {
  ...actual,
  baselineType: "authenticated-runtime",
  acceptedAt: new Date().toISOString(),
  acceptedBy,
  sourceEnvironment,
  sourceArtifact: portablePath(sourceArtifact),
  sourceSha256: sha256(sourceFile),
  releaseCandidate: releaseCandidate || actual.releaseCandidate || null,
};

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(baseline, null, 2)}\n`);
writePromotionAudit("PASS", actual, baseline);
console.log(`[ddd-promote-performance-baseline] promoted authenticated runtime baseline; p95=${baseline.p95}ms; artifact=${outputFile}`);
