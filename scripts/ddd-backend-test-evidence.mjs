#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { collectProvenanceIssues, redactLocalPaths } from "./ddd-release-evidence-utils.mjs";
import { requiredBackendTestClasses } from "./ddd-backend-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const outputDir = process.env.DDD_BACKEND_TEST_EVIDENCE_DIR
  ? path.resolve(process.env.DDD_BACKEND_TEST_EVIDENCE_DIR)
  : path.join(repoRoot, "artifacts", "ddd", "tests");
const outputFile = process.env.DDD_BACKEND_TEST_EVIDENCE_REPORT
  ? path.resolve(process.env.DDD_BACKEND_TEST_EVIDENCE_REPORT)
  : path.join(outputDir, "backend-test-evidence.json");
const reportRoot = process.env.DDD_SUREFIRE_REPORT_ROOT
  ? path.resolve(process.env.DDD_SUREFIRE_REPORT_ROOT)
  : repoRoot;
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === "true" || process.env.DDD_BACKEND_TEST_EVIDENCE_STRICT === "true";
const sourceEnvironment = process.env.DDD_BACKEND_TEST_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";

function walk(directory, files = []) {
  if (!fs.existsSync(directory)) {
    return files;
  }
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, files);
    } else if (entry.isFile() && /target\/surefire-reports\/TEST-.+\.xml$/.test(fullPath.replaceAll(path.sep, "/"))) {
      files.push(fullPath);
    }
  }
  return files;
}

function attr(xml, name) {
  const match = xml.match(new RegExp(`\\b${name}="([^"]*)"`));
  return match ? match[1] : "";
}

function readSuite(file) {
  const xml = fs.readFileSync(file, "utf8");
  const name = attr(xml, "name") || path.basename(file).replace(/^TEST-/, "").replace(/\.xml$/, "");
  return {
    name,
    file,
    tests: Number(attr(xml, "tests") || 0),
    failures: Number(attr(xml, "failures") || 0),
    errors: Number(attr(xml, "errors") || 0),
    skipped: Number(attr(xml, "skipped") || 0),
    timeSeconds: Number(attr(xml, "time") || 0),
  };
}

const blockers = [];
if (strictEvidence) {
  for (const issue of collectProvenanceIssues({ sourceEnvironment, releaseCandidate, evidenceOperator })) {
    blockers.push(`backend test provenance ${issue}`);
  }
}

const provenanceFailed = blockers.length > 0;
const suites = !provenanceFailed
  ? walk(reportRoot).map(readSuite).sort((left, right) => left.name.localeCompare(right.name))
  : [];
const byName = new Map(suites.map((suite) => [suite.name, suite]));
const missingRequired = requiredBackendTestClasses.filter((name) => !byName.has(name));
const failedSuites = suites.filter((suite) => suite.failures > 0 || suite.errors > 0);
const requiredSuites = requiredBackendTestClasses.map((name) => byName.get(name)).filter(Boolean);
const requiredFailures = requiredSuites.filter((suite) => suite.failures > 0 || suite.errors > 0);
const totals = suites.reduce(
  (acc, suite) => ({
    suites: acc.suites + 1,
    tests: acc.tests + suite.tests,
    failures: acc.failures + suite.failures,
    errors: acc.errors + suite.errors,
    skipped: acc.skipped + suite.skipped,
    timeSeconds: Math.round((acc.timeSeconds + suite.timeSeconds) * 1000) / 1000,
  }),
  { suites: 0, tests: 0, failures: 0, errors: 0, skipped: 0, timeSeconds: 0 },
);

if (!provenanceFailed && suites.length === 0) {
  blockers.push(`no surefire XML reports found under ${reportRoot}`);
}
if (!provenanceFailed && missingRequired.length > 0) {
  blockers.push(`missing required test classes: ${missingRequired.join(", ")}`);
}
if (!provenanceFailed && requiredFailures.length > 0) {
  blockers.push(`required test failures: ${requiredFailures.map((suite) => suite.name).join(", ")}`);
}
if (!provenanceFailed && failedSuites.length > 0) {
  blockers.push(`one or more backend test suites failed: ${failedSuites.map((suite) => suite.name).join(", ")}`);
}

const artifact = {
  generatedAt: new Date().toISOString(),
  sourceEnvironment: sourceEnvironment || null,
  releaseCandidate: releaseCandidate || null,
  evidenceOperator: evidenceOperator || null,
  status: blockers.length === 0 ? "PASS" : "FAIL",
  reportRoot: redactLocalPaths(reportRoot, { repoRoot, homeDir: process.env.HOME || "" }),
  summary: {
    ...totals,
    required: requiredBackendTestClasses.length,
    requiredPresent: requiredSuites.length,
    requiredMissing: missingRequired.length,
  },
  requiredTestClasses: requiredBackendTestClasses,
  missingRequired,
  failedSuites: failedSuites.map((suite) => ({
    name: suite.name,
    failures: suite.failures,
    errors: suite.errors,
    file: path.relative(repoRoot, suite.file),
  })),
  suites: suites.map((suite) => ({
    ...suite,
    file: path.relative(repoRoot, suite.file),
  })),
  blockers,
};

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);

if (blockers.length > 0) {
  for (const blocker of blockers) {
    console.error(`[ddd-backend-test-evidence] ${blocker}`);
  }
  console.error(`[ddd-backend-test-evidence] wrote artifact to ${outputFile}`);
  process.exit(1);
}

console.log(`[ddd-backend-test-evidence] backend test evidence passed; suites=${suites.length}; tests=${totals.tests}; artifact=${outputFile}`);
