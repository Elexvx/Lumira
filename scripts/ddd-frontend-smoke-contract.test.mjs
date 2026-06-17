#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  buildFrontendSmokeBlockers,
  buildFrontendSmokeArtifact,
  defaultRequiredFrontendFlows,
  parseRequiredFrontendFlows,
  validateFrontendSmokeArtifact,
} from "./ddd-frontend-smoke-contract.mjs";

function playwrightReport(titles, baseURL = "https://app.staging.lumira.app") {
  return {
    config: {
      projects: [
        {
          name: "chromium",
          use: { baseURL },
        },
      ],
    },
    suites: [
      {
        title: "frontend smoke",
        specs: titles.map((title) => ({
          title: `${title} @smoke`,
          tests: [
            {
              projectName: "chromium",
              results: [
                {
                  status: "passed",
                  duration: 10,
                  errors: [],
                },
              ],
            },
          ],
        })),
      },
    ],
  };
}

assert.deepEqual(parseRequiredFrontendFlows("a, b ,, c"), ["a", "b", "c"]);

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows),
    inputFile: "playwright.json",
    expectDeployed: true,
    sourceEnvironment: "staging",
    releaseCandidate: "rc-1",
    evidenceOperator: "release-owner",
    deploymentEvidence: "ci://deploy/123",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  assert.equal(artifact.status, "PASS");
  assert.deepEqual(validateFrontendSmokeArtifact(artifact, { strict: true }), []);
  assert.deepEqual(buildFrontendSmokeBlockers(artifact, { strict: true }), []);
  assert.equal(artifact.productionEquivalence.strict, true);
  assert.equal(artifact.productionEquivalence.https, true);
  assert.equal(artifact.productionEquivalence.localOnly, false);
  assert.equal(artifact.flowCoverage.length, defaultRequiredFrontendFlows.length);
  assert.equal(artifact.flowCoverage.every((entry) => entry.status === "passed"), true);
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows),
    inputFile: "playwright.json",
    expectDeployed: true,
    sourceEnvironment: "staging",
    releaseCandidate: "rc-1",
    evidenceOperator: "release-owner",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  delete artifact.productionEquivalence;
  assert(
    validateFrontendSmokeArtifact(artifact, { strict: true })
      .includes("frontend smoke productionEquivalence is required for strict release evidence"),
  );
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows),
    inputFile: "playwright.json",
    expectDeployed: true,
    sourceEnvironment: "staging",
    releaseCandidate: "rc-1",
    evidenceOperator: "release-owner",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  artifact.productionEquivalence = {
    strict: "true",
    https: true,
    localOnly: false,
    deploymentEvidence: 42,
    issues: "none",
  };
  const issues = validateFrontendSmokeArtifact(artifact, { strict: true });
  assert(issues.includes("frontend smoke productionEquivalence.strict must be boolean"));
  assert(issues.includes("frontend smoke productionEquivalence.strict must be true for strict release evidence"));
  assert(issues.includes("frontend smoke productionEquivalence.deploymentEvidence must be string or null"));
  assert(issues.includes("frontend smoke productionEquivalence.deploymentEvidence is required"));
  assert(issues.includes("frontend smoke productionEquivalence.issues must be an array of strings"));
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows),
    inputFile: "playwright.json",
    expectDeployed: true,
    sourceEnvironment: "staging",
    releaseCandidate: "rc-1",
    evidenceOperator: "release-owner",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  artifact.productionEquivalence = {
    strict: true,
    https: true,
    localOnly: false,
    deploymentEvidence: "https://example.com/deployments/123",
    issues: [],
  };
  const issues = validateFrontendSmokeArtifact(artifact, { strict: true });
  assert(issues.includes("frontend smoke productionEquivalence.deploymentEvidence must not contain placeholder text"));
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows),
    inputFile: "playwright.json",
    expectDeployed: true,
    sourceEnvironment: "staging",
    releaseCandidate: "rc-1",
    evidenceOperator: "release-owner",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  artifact.productionEquivalence = {
    strict: false,
    https: true,
    localOnly: false,
    deploymentEvidence: "ci://deploy/123",
    issues: [],
  };
  const issues = validateFrontendSmokeArtifact(artifact, { strict: true });
  assert(issues.includes("frontend smoke productionEquivalence.strict must be true for strict release evidence"));
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows.slice(0, -1)),
    inputFile: "playwright.json",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  assert.equal(artifact.status, "FAIL");
  assert.ok(artifact.blockers.includes("missing required flows=1"));
  assert.deepEqual(validateFrontendSmokeArtifact(artifact, { validateBlockers: true }), [
    "status=FAIL",
    "missing required flows=1",
  ]);
  assert.deepEqual(artifact.flowCoverage.at(-1), {
    flow: defaultRequiredFrontendFlows.at(-1),
    status: "missing",
    matchedTitle: null,
    reason: "no passed Playwright @smoke test matched this required flow",
  });
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows),
    inputFile: "playwright.json",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  artifact.flowCoverage = [];
  assert.ok(validateFrontendSmokeArtifact(artifact).includes(`flow coverage entries=0, expected=${defaultRequiredFrontendFlows.length}`));
}

{
  const duplicateFlow = defaultRequiredFrontendFlows[0];
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows),
    inputFile: "playwright.json",
    requiredFlows: [duplicateFlow, duplicateFlow, ...defaultRequiredFrontendFlows.slice(1)],
  });
  const issues = validateFrontendSmokeArtifact(artifact);
  assert(issues.includes(`duplicate required frontend flow ${duplicateFlow}`));
  assert(issues.includes(`duplicate flow coverage entry ${duplicateFlow}`));
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows),
    inputFile: "playwright.json",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  artifact.flowCoverage[0] = {
    flow: "unknown flow",
    status: "passed",
    matchedTitle: "unknown flow",
    reason: null,
  };
  const issues = validateFrontendSmokeArtifact(artifact);
  assert(issues.includes(`flow coverage missing required flow ${defaultRequiredFrontendFlows[0]}`));
  assert(issues.includes("unknown flow coverage entry unknown flow"));
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows),
    inputFile: "playwright.json",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  artifact.flowCoverage[0] = {
    ...artifact.flowCoverage[0],
    matchedTitle: null,
  };
  assert(validateFrontendSmokeArtifact(artifact).includes(`${defaultRequiredFrontendFlows[0]} passed flow coverage must include matchedTitle`));
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows, "http://127.0.0.1:8000"),
    inputFile: "playwright.json",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  assert.equal(artifact.status, "PASS");
  assert.deepEqual(artifact.blockers, []);
  assert.deepEqual(validateFrontendSmokeArtifact(artifact, { validateBlockers: true }), []);
  assert.equal(artifact.productionEquivalence.localOnly, true);
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows, "http://localhost:3000"),
    inputFile: "playwright.json",
    expectDeployed: true,
    sourceEnvironment: "staging",
    releaseCandidate: "rc-1",
    evidenceOperator: "release-owner",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  assert.deepEqual(artifact.blockers, [
    "frontend smoke productionEquivalence.https must be true for strict release evidence",
    "frontend smoke productionEquivalence.localOnly must be false for strict release evidence",
    "frontend smoke productionEquivalence.deploymentEvidence is required",
    "frontend smoke productionEquivalence.issues must be empty for strict release evidence",
    "strict release requires HTTPS frontend baseURL evidence",
    "artifact is local-only: http://localhost:3000",
  ]);
  assert.equal(artifact.productionEquivalence.issues[0], "strict frontend smoke requires HTTPS baseUrl evidence");
  assert.equal(artifact.productionEquivalence.localOnly, true);
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows),
    inputFile: "playwright.json",
    strict: true,
    sourceEnvironment: "staging",
    releaseCandidate: "rc-1",
    evidenceOperator: "release-owner",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  assert.equal(artifact.status, "FAIL");
  assert.ok(artifact.blockers.includes("strict release requires DDD_FRONTEND_EXPECT_DEPLOYED=true evidence"));
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows),
    inputFile: "playwright.json",
    expectDeployed: true,
    requiredFlows: defaultRequiredFrontendFlows,
  });
  assert.ok(artifact.blockers.includes("deployed smoke evidence provenance sourceEnvironment is required"));
  assert.ok(artifact.blockers.includes("deployed smoke evidence provenance releaseCandidate is required"));
  assert.ok(artifact.blockers.includes("deployed smoke evidence provenance evidenceOperator is required"));
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows),
    inputFile: "playwright.json",
    expectDeployed: true,
    sourceEnvironment: "staging",
    releaseCandidate: "rc-1",
    evidenceOperator: "release-owner",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  artifact.summary = {
    ...artifact.summary,
    total: artifact.summary.total + 1,
    passed: artifact.summary.passed + 1,
    failed: artifact.summary.failed + 1,
    skipped: artifact.summary.skipped + 1,
    requiredFlows: artifact.summary.requiredFlows - 1,
    missingRequiredFlows: artifact.summary.missingRequiredFlows + 1,
  };
  artifact.blockers = ["forced blocker"];
  artifact.status = "PASS";
  const issues = validateFrontendSmokeArtifact(artifact, { strict: true });
  assert(issues.includes("frontend smoke status must be FAIL, got PASS"));
  assert(issues.includes(`frontend smoke summary total mismatch: declared=${defaultRequiredFrontendFlows.length + 1}, actual=${defaultRequiredFrontendFlows.length}`));
  assert(issues.includes(`frontend smoke summary passed mismatch: declared=${defaultRequiredFrontendFlows.length + 1}, actual=${defaultRequiredFrontendFlows.length}`));
  assert(issues.includes("frontend smoke summary failed mismatch: declared=1, actual=0"));
  assert(issues.includes("frontend smoke summary skipped mismatch: declared=1, actual=0"));
  assert(issues.includes(`frontend smoke summary requiredFlows mismatch: declared=${defaultRequiredFrontendFlows.length - 1}, actual=${defaultRequiredFrontendFlows.length}`));
  assert(issues.includes("frontend smoke summary missingRequiredFlows mismatch: declared=1, actual=0"));
  assert(issues.includes("frontend smoke flow coverage missing mismatch: declared=1, actual=0"));
}

{
  const artifact = {
    generatedAt: "2026-06-14T00:00:00.000Z",
    status: "FAIL",
    inputFile: "missing.json",
    baseUrl: "https://app.staging.lumira.app",
    sourceEnvironment: "staging",
    releaseCandidate: "rc-1",
    evidenceOperator: "release-owner",
    expectDeployed: true,
    summary: {
      total: 0,
      passed: 0,
      failed: 0,
      skipped: 0,
      requiredFlows: defaultRequiredFrontendFlows.length,
      missingRequiredFlows: defaultRequiredFrontendFlows.length,
    },
    requiredFlows: defaultRequiredFrontendFlows,
    missingRequiredFlows: defaultRequiredFrontendFlows,
    flowCoverage: defaultRequiredFrontendFlows.map((flow) => ({
      flow,
      status: "missing",
      matchedTitle: null,
      reason: "missing Playwright JSON report",
    })),
    tests: [],
    diagnostics: {
      playwrightReport: {
        present: false,
        reason: "missing Playwright JSON report",
      },
    },
    blockers: ["missing Playwright JSON report"],
  };
  const issues = validateFrontendSmokeArtifact(artifact, { strict: true });
  assert(!issues.some((issue) => issue.includes("summary failed mismatch")));
  assert(issues.includes("status=FAIL"));
  assert(!issues.includes("failed=1"));
  assert(issues.includes("missing Playwright JSON report: missing Playwright JSON report"));
  assert(issues.includes(`missing required flows=${defaultRequiredFrontendFlows.length}`));
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows.slice(0, -1)),
    inputFile: "playwright.json",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  artifact.blockers = ["operator rewrote missing flow blocker"];
  const issues = validateFrontendSmokeArtifact(artifact, { validateBlockers: true });
  assert(issues.includes("frontend smoke blockers[0] mismatch: declared=operator rewrote missing flow blocker, actual=missing required flows=1"));
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows.slice(0, -1)),
    inputFile: "playwright.json",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  artifact.blockers = [];
  artifact.status = "PASS";
  const issues = validateFrontendSmokeArtifact(artifact, { validateBlockers: true });
  assert(issues.includes("frontend smoke blockers length mismatch: declared=0, actual=1"));
}

{
  const artifact = buildFrontendSmokeArtifact({
    report: playwrightReport(defaultRequiredFrontendFlows),
    inputFile: "playwright.json",
    requiredFlows: defaultRequiredFrontendFlows,
  });
  artifact.diagnostics = {
    staticSpecCoverage: {
      present: true,
      file: "frontend/tests/e2e/app.spec.ts",
      covered: 0,
      missing: 0,
      coverage: [
        { flow: defaultRequiredFrontendFlows[0], status: "covered", reason: null },
        { flow: "unknown flow", status: "missing", reason: "not declared" },
      ],
    },
  };
  const issues = validateFrontendSmokeArtifact(artifact);
  assert(issues.includes(`frontend static smoke coverage entries=2, expected=${defaultRequiredFrontendFlows.length}`));
  assert(issues.includes("frontend static smoke covered mismatch: declared=0, actual=1"));
  assert(issues.includes("frontend static smoke missing mismatch: declared=0, actual=1"));
  assert(issues.includes(`frontend static smoke coverage missing required flow ${defaultRequiredFrontendFlows[1]}`));
  assert(issues.includes("unknown frontend static smoke coverage entry unknown flow"));
}

console.log("[ddd-frontend-smoke-contract.test] ok");
