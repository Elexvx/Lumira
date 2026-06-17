#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import {
  evidenceValueIssue,
  isHttpsUrl,
  isLocalUrlLike,
  requireRuntimeProvenanceWhenStrict,
} from "./ddd-release-evidence-utils.mjs";
import {
  runtimeReadinessContexts,
  runtimeReadinessSuffixes,
} from "./ddd-runtime-readiness-contract.mjs";

const baseUrl = process.env.LUMIRA_BASE_URL || "http://127.0.0.1:8080";
const outputDir = path.resolve(process.env.DDD_RUNTIME_READINESS_DIR || "artifacts/ddd/readiness");
const timeoutMs = Number(process.env.DDD_RUNTIME_READINESS_TIMEOUT_MS || "5000");
const authToken = process.env.LUMIRA_AUTH_TOKEN || "";
const sourceEnvironment = process.env.DDD_RUNTIME_READINESS_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === "true" || process.env.DDD_RUNTIME_READINESS_STRICT === "true";
const deploymentEvidence = process.env.DDD_RUNTIME_READINESS_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || "";
const repoRoot = path.resolve(import.meta.dirname, "..");

const failures = [];
const summary = [];

function fail(message) {
  console.error(`[ddd-runtime-readiness-smoke] ${message}`);
  failures.push(message);
}

function portablePath(filePath) {
  const absolutePath = path.resolve(filePath);
  return absolutePath === repoRoot || absolutePath.startsWith(`${repoRoot}${path.sep}`)
    ? path.relative(repoRoot, absolutePath) || "."
    : filePath;
}

async function fetchJson(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      signal: controller.signal,
      headers: {
        ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
        Accept: "application/json",
      },
    });
    const text = await response.text();
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      // Keep null; caller reports the response body shape.
    }
    return { response, text, json };
  } finally {
    clearTimeout(timer);
  }
}

fs.mkdirSync(outputDir, { recursive: true });

function productionEquivalence() {
  const https = isHttpsUrl(baseUrl);
  const localOnly = isLocalUrlLike(baseUrl);
  const issues = [];
  if (strictEvidence && !https) {
    issues.push("strict runtime readiness requires HTTPS baseUrl evidence");
  }
  if (strictEvidence && localOnly) {
    issues.push(`strict runtime readiness requires non-local baseUrl, got ${baseUrl}`);
  }
  const deploymentEvidenceIssue = evidenceValueIssue(deploymentEvidence);
  if (strictEvidence && deploymentEvidenceIssue) {
    issues.push(`strict runtime readiness deploymentEvidence ${deploymentEvidenceIssue}`);
  }
  return {
    strict: strictEvidence,
    https,
    localOnly,
    deploymentEvidence: deploymentEvidence || null,
    issues,
  };
}

function writeSummary() {
  const summaryFile = path.join(outputDir, "summary.json");
  fs.writeFileSync(summaryFile, `${JSON.stringify({
    baseUrl,
    checkedAt: new Date().toISOString(),
    sourceEnvironment: sourceEnvironment || null,
    releaseCandidate: releaseCandidate || null,
    evidenceOperator: evidenceOperator || null,
    productionEquivalence: productionEquivalence(),
    summary,
    failures,
  }, null, 2)}\n`);
}

for (const issue of requireRuntimeProvenanceWhenStrict({
  strict: strictEvidence,
  sourceEnvironment,
  releaseCandidate,
  evidenceOperator,
})) {
  fail(`runtime provenance ${issue}`);
}
for (const issue of productionEquivalence().issues) {
  fail(issue);
}

for (const [context, route] of runtimeReadinessContexts) {
  for (const suffix of runtimeReadinessSuffixes) {
    const endpoint = `${route}/${suffix}`;
    const url = new URL(endpoint, baseUrl);
    const artifact = path.join(outputDir, `${context}-${suffix}.json`);
    try {
      const { response, text, json } = await fetchJson(url);
      if (!response.ok) {
        fail(`${endpoint} returned HTTP ${response.status}`);
      }
      if (!json || json.httpStatus !== 200 || !json.data) {
        fail(`${endpoint} did not return ApiResponse data`);
      }
      fs.writeFileSync(artifact, `${JSON.stringify(json ?? { raw: text }, null, 2)}\n`);
      summary.push({ context, suffix, status: response.status, artifact: portablePath(artifact) });
    } catch (error) {
      fail(`${endpoint} failed: ${error.message}`);
    }
  }
}

writeSummary();

if (failures.length > 0) {
  console.error(`[ddd-runtime-readiness-smoke] wrote partial artifacts to ${outputDir}`);
  process.exit(1);
}

console.log(`[ddd-runtime-readiness-smoke] validated ${summary.length} endpoint(s); artifacts=${outputDir}`);
