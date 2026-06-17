import {
  buildProductionEquivalenceEvidence,
  isHttpsUrl,
  isLocalUrlLike,
  requireRuntimeProvenanceWhenStrict,
  validateProductionEquivalenceEvidence,
} from "./ddd-release-evidence-utils.mjs";

export const defaultRequiredFrontendFlows = [
  "dashboard page is reachable",
  "download center page is reachable",
  "AI assistant page is reachable",
  "users page is reachable",
  "roles page is reachable",
  "security settings page is reachable",
  "payment settings page is reachable",
  "system files page is reachable",
  "plugins page is reachable",
  "localization page is reachable",
  "session survives a browser refresh",
  "message center can be opened",
  "user can log out from the top menu",
];

export function parseRequiredFrontendFlows(value) {
  return (value || defaultRequiredFrontendFlows.join(","))
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

export function buildFrontendSmokeArtifact({
  report,
  inputFile,
  baseUrlOverride = null,
  expectDeployed = false,
  strict = false,
  sourceEnvironment = "",
  releaseCandidate = "",
  evidenceOperator = "",
  deploymentEvidence = "",
  requiredFlows = defaultRequiredFrontendFlows,
}) {
  const specs = (report.suites || []).flatMap((suite) => collectSpecs(suite));
  const tests = [];
  for (const spec of specs) {
    for (const test of spec.tests || []) {
      const status = statusFor(test);
      const durationMs = (test.results || []).reduce((sum, result) => sum + (result.duration || 0), 0);
      tests.push({
        title: spec.title,
        projectName: test.projectName || null,
        status,
        durationMs,
        errors: (test.results || []).flatMap((result) => result.errors || []).length,
      });
    }
  }

  const passed = tests.filter((test) => test.status === "passed").length;
  const failed = tests.filter((test) => ["failed", "timedOut", "interrupted", "unknown"].includes(test.status)).length;
  const skipped = tests.filter((test) => test.status === "skipped").length;
  const presentPassedTitles = new Set(
    tests
      .filter((test) => test.status === "passed")
      .map((test) => test.title.replace(/\s*@smoke\b/g, "").trim()),
  );
  const flowCoverage = requiredFlows.map((flow) => {
    const normalizedFlow = flow.toLowerCase();
    const matchedTitle = Array.from(presentPassedTitles).find((title) => title.toLowerCase().includes(normalizedFlow));
    return {
      flow,
      status: matchedTitle ? "passed" : "missing",
      matchedTitle: matchedTitle || null,
      reason: matchedTitle ? null : "no passed Playwright @smoke test matched this required flow",
    };
  });
  const missingRequiredFlows = flowCoverage
    .filter((coverage) => coverage.status === "missing")
    .map((coverage) => coverage.flow);
  const baseUrl = baseUrlOverride || baseUrlFrom(report);
  const artifact = {
    generatedAt: new Date().toISOString(),
    status: "PASS",
    inputFile,
    baseUrl,
    sourceEnvironment: sourceEnvironment || null,
    releaseCandidate: releaseCandidate || null,
    evidenceOperator: evidenceOperator || null,
    productionEquivalence: buildProductionEquivalenceEvidence({
      strict: strict || expectDeployed,
      baseUrl,
      deploymentEvidence,
      evidenceName: "frontend smoke",
    }),
    expectDeployed,
    summary: {
      total: tests.length,
      passed,
      failed,
      skipped,
      requiredFlows: requiredFlows.length,
      missingRequiredFlows: missingRequiredFlows.length,
    },
    requiredFlows,
    missingRequiredFlows,
    flowCoverage,
    tests,
    blockers: [],
  };
  artifact.blockers = buildFrontendSmokeBlockers(artifact, { strict: strict || expectDeployed });
  artifact.status = artifact.blockers.length === 0 ? "PASS" : "FAIL";
  return artifact;
}

export function buildFrontendSmokeBlockers(artifact, { strict = false } = {}) {
  const candidate = {
    ...(artifact || {}),
    status: "PASS",
    blockers: [],
  };
  return validateFrontendSmokeCore(candidate, { strict });
}

export function validateFrontendSmokeArtifact(artifact, { strict = false, validateBlockers = false } = {}) {
  const issues = validateFrontendSmokeCore(artifact, { strict });
  if (validateBlockers) {
    const blockers = Array.isArray(artifact?.blockers) ? artifact.blockers : [];
    compareStringArrays("frontend smoke blockers", blockers, buildFrontendSmokeBlockers(artifact, { strict }), issues);
  }
  return issues;
}

function validateFrontendSmokeCore(artifact, { strict = false } = {}) {
  const issues = [];
  const blockers = Array.isArray(artifact?.blockers) ? artifact.blockers : [];
  const tests = Array.isArray(artifact?.tests) ? artifact.tests : [];
  const requiredFlows = Array.isArray(artifact?.requiredFlows) ? artifact.requiredFlows : [];
  const missingRequiredFlows = Array.isArray(artifact?.missingRequiredFlows) ? artifact.missingRequiredFlows : [];
  const coverage = Array.isArray(artifact?.flowCoverage) ? artifact.flowCoverage : [];
  const expectedStatus = blockers.length === 0 ? "PASS" : "FAIL";
  const requiredFlowCounts = countValues(requiredFlows);
  const coverageFlowCounts = countValues(coverage.map((entry) => entry?.flow).filter(Boolean));

  issues.push(...validateProductionEquivalenceEvidence("frontend smoke", artifact, {
    strict,
    issuesMustBeStrings: true,
  }));
  if (artifact?.status !== "PASS") {
    issues.push(`status=${artifact?.status ?? "missing"}`);
  }
  if (artifact?.status !== expectedStatus) {
    issues.push(`frontend smoke status must be ${expectedStatus}, got ${artifact?.status ?? "missing"}`);
  }
  if ((artifact?.summary?.requiredFlows || 0) !== requiredFlows.length) {
    issues.push(`frontend smoke summary requiredFlows mismatch: declared=${artifact?.summary?.requiredFlows || 0}, actual=${requiredFlows.length}`);
  }
  if ((artifact?.summary?.missingRequiredFlows || 0) !== missingRequiredFlows.length) {
    issues.push(`frontend smoke summary missingRequiredFlows mismatch: declared=${artifact?.summary?.missingRequiredFlows || 0}, actual=${missingRequiredFlows.length}`);
  }
  const actualMissingCoverage = coverage.filter((entry) => entry?.status === "missing").length;
  if ((artifact?.summary?.missingRequiredFlows || 0) !== actualMissingCoverage) {
    issues.push(`frontend smoke flow coverage missing mismatch: declared=${artifact?.summary?.missingRequiredFlows || 0}, actual=${actualMissingCoverage}`);
  }
  if (artifact?.diagnostics?.playwrightReport?.present !== false) {
    const actualPassed = tests.filter((test) => test.status === "passed").length;
    const actualFailed = tests.filter((test) => ["failed", "timedOut", "interrupted", "unknown"].includes(test.status)).length;
    const actualSkipped = tests.filter((test) => test.status === "skipped").length;
    if ((artifact?.summary?.total || 0) !== tests.length) {
      issues.push(`frontend smoke summary total mismatch: declared=${artifact?.summary?.total || 0}, actual=${tests.length}`);
    }
    if ((artifact?.summary?.passed || 0) !== actualPassed) {
      issues.push(`frontend smoke summary passed mismatch: declared=${artifact?.summary?.passed || 0}, actual=${actualPassed}`);
    }
    if ((artifact?.summary?.failed || 0) !== actualFailed) {
      issues.push(`frontend smoke summary failed mismatch: declared=${artifact?.summary?.failed || 0}, actual=${actualFailed}`);
    }
    if ((artifact?.summary?.skipped || 0) !== actualSkipped) {
      issues.push(`frontend smoke summary skipped mismatch: declared=${artifact?.summary?.skipped || 0}, actual=${actualSkipped}`);
    }
  } else {
    const reason = artifact.diagnostics.playwrightReport.reason || artifact.diagnostics.playwrightReport.file || "missing Playwright JSON report";
    issues.push(`missing Playwright JSON report: ${reason}`);
  }
  if ((artifact?.summary?.failed || 0) > 0) {
    issues.push(`failed=${artifact.summary.failed}`);
  }
  if ((artifact?.summary?.missingRequiredFlows || 0) > 0) {
    issues.push(`missing required flows=${artifact.summary.missingRequiredFlows}`);
  }
  if ((artifact?.summary?.requiredFlows || 0) > 0) {
    for (const [flow, count] of requiredFlowCounts.entries()) {
      if (count > 1) {
        issues.push(`duplicate required frontend flow ${flow}`);
      }
    }
    if (coverage.length !== artifact.summary.requiredFlows) {
      issues.push(`flow coverage entries=${coverage.length}, expected=${artifact.summary.requiredFlows}`);
    }
    for (const flow of requiredFlows) {
      if (!coverageFlowCounts.has(flow)) {
        issues.push(`flow coverage missing required flow ${flow}`);
      }
    }
    for (const [flow, count] of coverageFlowCounts.entries()) {
      if (count > 1) {
        issues.push(`duplicate flow coverage entry ${flow}`);
      }
      if (!requiredFlowCounts.has(flow)) {
        issues.push(`unknown flow coverage entry ${flow}`);
      }
    }
    for (const entry of coverage) {
      if (!entry?.flow) {
        issues.push("flow coverage entry is missing flow");
        continue;
      }
      if (!["passed", "missing"].includes(entry.status)) {
        issues.push(`${entry.flow} flow coverage status is invalid`);
      }
      if (entry.status === "missing" && (!entry.reason || typeof entry.reason !== "string")) {
        issues.push(`${entry.flow} missing flow coverage must include reason`);
      }
      if (entry.status === "passed" && (!entry.matchedTitle || typeof entry.matchedTitle !== "string")) {
        issues.push(`${entry.flow} passed flow coverage must include matchedTitle`);
      }
    }
  }
  issues.push(...validateStaticSpecCoverage(artifact?.diagnostics?.staticSpecCoverage, requiredFlows));
  if (!artifact?.baseUrl) {
    issues.push("missing baseUrl");
  }
  if (strict && artifact?.expectDeployed !== true) {
    issues.push("strict release requires DDD_FRONTEND_EXPECT_DEPLOYED=true evidence");
  }
  if (strict && artifact?.baseUrl && !isHttpsUrl(artifact.baseUrl)) {
    issues.push("strict release requires HTTPS frontend baseURL evidence");
  }
  if (strict && isLocalUrlLike(artifact?.baseUrl)) {
    issues.push(`artifact is local-only: ${artifact.baseUrl}`);
  }
  for (const issue of requireRuntimeProvenanceWhenStrict({
    strict,
    sourceEnvironment: artifact?.sourceEnvironment,
    releaseCandidate: artifact?.releaseCandidate,
    evidenceOperator: artifact?.evidenceOperator,
  })) {
    issues.push(`deployed smoke evidence provenance ${issue}`);
  }
  return issues;
}

function compareStringArrays(label, declared, expected, issues) {
  if (declared.length !== expected.length) {
    issues.push(`${label} length mismatch: declared=${declared.length}, actual=${expected.length}`);
    return;
  }
  for (let index = 0; index < expected.length; index += 1) {
    if (declared[index] !== expected[index]) {
      issues.push(`${label}[${index}] mismatch: declared=${declared[index] ?? "missing"}, actual=${expected[index]}`);
    }
  }
}

function validateStaticSpecCoverage(staticSpecCoverage, requiredFlows) {
  const issues = [];
  if (!staticSpecCoverage) {
    return issues;
  }
  const coverage = Array.isArray(staticSpecCoverage.coverage) ? staticSpecCoverage.coverage : [];
  const requiredFlowCounts = countValues(requiredFlows);
  const coverageFlowCounts = countValues(coverage.map((entry) => entry?.flow).filter(Boolean));
  const covered = coverage.filter((entry) => entry?.status === "covered").length;
  const missing = coverage.filter((entry) => entry?.status === "missing").length;
  if (coverage.length !== requiredFlows.length) {
    issues.push(`frontend static smoke coverage entries=${coverage.length}, expected=${requiredFlows.length}`);
  }
  if ((staticSpecCoverage.covered || 0) !== covered) {
    issues.push(`frontend static smoke covered mismatch: declared=${staticSpecCoverage.covered || 0}, actual=${covered}`);
  }
  if ((staticSpecCoverage.missing || 0) !== missing) {
    issues.push(`frontend static smoke missing mismatch: declared=${staticSpecCoverage.missing || 0}, actual=${missing}`);
  }
  for (const flow of requiredFlows) {
    if (!coverageFlowCounts.has(flow)) {
      issues.push(`frontend static smoke coverage missing required flow ${flow}`);
    }
  }
  for (const [flow, count] of coverageFlowCounts.entries()) {
    if (count > 1) {
      issues.push(`duplicate frontend static smoke coverage entry ${flow}`);
    }
    if (!requiredFlowCounts.has(flow)) {
      issues.push(`unknown frontend static smoke coverage entry ${flow}`);
    }
  }
  for (const entry of coverage) {
    if (!entry?.flow) {
      issues.push("frontend static smoke coverage entry is missing flow");
      continue;
    }
    if (!["covered", "missing"].includes(entry.status)) {
      issues.push(`${entry.flow} frontend static smoke coverage status is invalid`);
    }
    if (entry.status === "missing" && (!entry.reason || typeof entry.reason !== "string")) {
      issues.push(`${entry.flow} missing frontend static smoke coverage must include reason`);
    }
  }
  return issues;
}

function countValues(items) {
  const counts = new Map();
  for (const item of items) {
    counts.set(item, (counts.get(item) || 0) + 1);
  }
  return counts;
}

function collectSpecs(suite, specs = []) {
  for (const spec of suite.specs || []) {
    specs.push(spec);
  }
  for (const child of suite.suites || []) {
    collectSpecs(child, specs);
  }
  return specs;
}

function statusFor(test) {
  const statuses = (test.results || []).map((result) => result.status);
  if (statuses.includes("failed") || statuses.includes("timedOut") || statuses.includes("interrupted")) {
    return "failed";
  }
  if (statuses.includes("passed")) {
    return "passed";
  }
  if (statuses.includes("skipped")) {
    return "skipped";
  }
  if (typeof test.status === "string") {
    return test.status === "expected" ? "passed" : test.status;
  }
  return statuses[0] || "unknown";
}

function baseUrlFrom(report) {
  for (const project of report.config?.projects || []) {
    const baseUrl = project.use?.baseURL;
    if (baseUrl) {
      return baseUrl;
    }
  }
  return null;
}
