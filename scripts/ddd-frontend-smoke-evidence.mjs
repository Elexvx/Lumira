#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import {
  buildFrontendSmokeBlockers,
  buildFrontendSmokeArtifact,
  parseRequiredFrontendFlows,
} from "./ddd-frontend-smoke-contract.mjs";
import { buildProductionEquivalenceEvidence } from "./ddd-release-evidence-utils.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const artifactRoot = process.env.DDD_FRONTEND_SMOKE_DIR
  ? path.resolve(process.env.DDD_FRONTEND_SMOKE_DIR)
  : path.join(repoRoot, "artifacts", "ddd", "frontend");
const inputFile = process.env.DDD_FRONTEND_PLAYWRIGHT_JSON
  ? path.resolve(process.env.DDD_FRONTEND_PLAYWRIGHT_JSON)
  : path.join(artifactRoot, "playwright-smoke-results.json");
const outputFile = process.env.DDD_FRONTEND_SMOKE_REPORT
  ? path.resolve(process.env.DDD_FRONTEND_SMOKE_REPORT)
  : path.join(artifactRoot, "frontend-smoke.json");
const expectDeployed = process.env.DDD_FRONTEND_EXPECT_DEPLOYED === "true";
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === "true" || process.env.DDD_FRONTEND_SMOKE_STRICT === "true";
const sourceEnvironment = process.env.DDD_FRONTEND_SMOKE_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";
const deploymentEvidence = process.env.DDD_FRONTEND_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || "";
const requiredFlows = parseRequiredFrontendFlows(process.env.DDD_FRONTEND_REQUIRED_FLOWS);
const smokeSpecFile = process.env.DDD_FRONTEND_SMOKE_SPEC
  ? path.resolve(process.env.DDD_FRONTEND_SMOKE_SPEC)
  : path.join(repoRoot, "frontend", "tests", "e2e", "app.spec.ts");

function portablePath(filePath) {
  const absolutePath = path.resolve(filePath);
  return absolutePath === repoRoot || absolutePath.startsWith(`${repoRoot}${path.sep}`)
    ? (path.relative(repoRoot, absolutePath) || ".").replaceAll("\\", "/")
    : filePath;
}

function staticSpecCoverage() {
  if (!fs.existsSync(smokeSpecFile)) {
    return {
      present: false,
      file: portablePath(smokeSpecFile),
      covered: 0,
      missing: requiredFlows.length,
      coverage: requiredFlows.map((flow) => ({
        flow,
        status: "missing",
        reason: `missing frontend smoke spec ${portablePath(smokeSpecFile)}`,
      })),
    };
  }
  const source = fs.readFileSync(smokeSpecFile, "utf8");
  const coverage = requiredFlows.map((flow) => {
    const pageFlowSuffix = " page is reachable";
    const matched = flow.endsWith(pageFlowSuffix)
      ? pageSmokeCovered(source, flow.slice(0, -pageFlowSuffix.length))
      : standaloneSmokeCovered(source, flow);
    return {
      flow,
      status: matched ? "covered" : "missing",
      reason: matched ? null : "required flow is not declared as a @smoke Playwright spec",
    };
  });
  return {
    present: true,
    file: portablePath(smokeSpecFile),
    covered: coverage.filter((entry) => entry.status === "covered").length,
    missing: coverage.filter((entry) => entry.status === "missing").length,
    coverage,
  };
}

function pageSmokeCovered(source, label) {
  return new RegExp(`\\{\\s*path:\\s*'[^']+',\\s*label:\\s*'${escapeRegExp(label)}',\\s*tag:\\s*'@smoke'\\s*\\}`).test(source);
}

function standaloneSmokeCovered(source, flow) {
  return new RegExp(`test\\(\\s*'${escapeRegExp(flow)}\\s+@smoke'`).test(source);
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function fail(message) {
  writeFailureArtifact(message);
  console.error(`[ddd-frontend-smoke-evidence] ${message}`);
  process.exit(1);
}

function writeFailureArtifact(message) {
  const baseUrl = process.env.PLAYWRIGHT_BASE_URL || null;
  const artifact = {
    generatedAt: new Date().toISOString(),
    status: "FAIL",
    inputFile: portablePath(inputFile),
    baseUrl,
    sourceEnvironment: sourceEnvironment || null,
    releaseCandidate: releaseCandidate || null,
    evidenceOperator: evidenceOperator || null,
    productionEquivalence: buildProductionEquivalenceEvidence({
      strict: strictEvidence || expectDeployed,
      baseUrl,
      deploymentEvidence,
      evidenceName: "frontend smoke",
    }),
    expectDeployed,
    summary: {
      total: 0,
      passed: 0,
      failed: 0,
      skipped: 0,
      requiredFlows: requiredFlows.length,
      missingRequiredFlows: requiredFlows.length,
    },
    requiredFlows,
    missingRequiredFlows: requiredFlows,
    flowCoverage: requiredFlows.map((flow) => ({
      flow,
      status: "missing",
      matchedTitle: null,
      reason: message,
    })),
    tests: [],
    diagnostics: {
      playwrightReport: {
        present: false,
        file: portablePath(inputFile),
        reason: message,
      },
      staticSpecCoverage: staticSpecCoverage(),
    },
    blockers: [],
  };
  artifact.blockers = buildFrontendSmokeBlockers(artifact, { strict: strictEvidence || expectDeployed });
  artifact.status = artifact.blockers.length === 0 ? "PASS" : "FAIL";
  fs.mkdirSync(path.dirname(outputFile), { recursive: true });
  fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);
}

function readJson(file) {
  if (!fs.existsSync(file)) {
    fail(`missing Playwright JSON report ${portablePath(file)}`);
  }
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (error) {
    fail(`invalid Playwright JSON report ${portablePath(file)}: ${error.message}`);
  }
}

const report = readJson(inputFile);
const artifact = buildFrontendSmokeArtifact({
  report,
  inputFile: portablePath(inputFile),
  baseUrlOverride: process.env.PLAYWRIGHT_BASE_URL || null,
  expectDeployed,
  strict: strictEvidence,
  sourceEnvironment,
  releaseCandidate,
  evidenceOperator,
  deploymentEvidence,
  requiredFlows,
});
artifact.diagnostics = {
  ...(artifact.diagnostics || {}),
  playwrightReport: {
    present: true,
    file: portablePath(inputFile),
  },
  staticSpecCoverage: staticSpecCoverage(),
};

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);

if (artifact.blockers.length > 0) {
  for (const blocker of artifact.blockers) {
    console.error(`[ddd-frontend-smoke-evidence] ${blocker}`);
  }
  console.error(`[ddd-frontend-smoke-evidence] wrote artifact to ${outputFile}`);
  process.exit(1);
}

console.log(`[ddd-frontend-smoke-evidence] frontend smoke evidence passed; tests=${artifact.tests.length}; artifact=${outputFile}`);
