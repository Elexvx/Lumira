#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");

function run(env = {}) {
  return spawnSync("node", ["scripts/ddd-backfill-runtime-provenance.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_EVIDENCE_DIR: "",
      DDD_RUNTIME_PROVENANCE_BACKFILL: "",
      DDD_RUNTIME_PRODUCTION_EQUIVALENCE_BACKFILL: "",
      DDD_RUNTIME_PROVENANCE_BACKFILL_ARTIFACTS: "",
      DDD_RUNTIME_PROVENANCE_BACKFILL_OVERWRITE: "",
      DDD_RUNTIME_PRODUCTION_EQUIVALENCE_BACKFILL_OVERWRITE: "",
      DDD_RUNTIME_PRODUCTION_EQUIVALENCE_DEPLOYMENT_EVIDENCE: "",
      DDD_EVIDENCE_ENVIRONMENT: "",
      DDD_RELEASE_CANDIDATE: "",
      DDD_EVIDENCE_OPERATOR: "",
      ...env,
    },
  });
}

function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify(data, null, 2)}\n`);
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function makeRoot() {
  return fs.mkdtempSync(path.join(os.tmpdir(), "lumira-runtime-provenance-backfill-"));
}

{
  const root = makeRoot();
  writeJson(path.join(root, "readiness", "summary.json"), {
    baseUrl: "http://127.0.0.1:8080",
    failures: [],
  });
  const result = run({
    DDD_RELEASE_EVIDENCE_DIR: root,
    DDD_RUNTIME_PROVENANCE_BACKFILL_ARTIFACTS: "readiness/summary.json",
    DDD_EVIDENCE_ENVIRONMENT: "local-dev",
    DDD_RELEASE_CANDIDATE: "abc123",
    DDD_EVIDENCE_OPERATOR: "codex",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /DDD_RUNTIME_PROVENANCE_BACKFILL=true or DDD_RUNTIME_PRODUCTION_EQUIVALENCE_BACKFILL=true is required/);
  const artifact = readJson(path.join(root, "readiness", "summary.json"));
  assert.equal(artifact.sourceEnvironment, undefined);
}

{
  const root = makeRoot();
  writeJson(path.join(root, "readiness", "summary.json"), {
    baseUrl: "http://127.0.0.1:8080",
    failures: [],
  });
  const result = run({
    DDD_RELEASE_EVIDENCE_DIR: root,
    DDD_RUNTIME_PRODUCTION_EQUIVALENCE_BACKFILL: "true",
    DDD_RUNTIME_PROVENANCE_BACKFILL_ARTIFACTS: "readiness/summary.json",
  });
  assert.equal(result.status, 0, result.stderr);
  const artifact = readJson(path.join(root, "readiness", "summary.json"));
  assert.equal(artifact.sourceEnvironment, undefined);
  assert.deepEqual(artifact.productionEquivalence, {
    strict: true,
    https: false,
    localOnly: true,
    deploymentEvidence: null,
    issues: [
      "strict runtime readiness requires HTTPS baseUrl evidence",
      "strict runtime readiness requires non-local baseUrl, got http://127.0.0.1:8080",
      "strict runtime readiness deploymentEvidence is required",
    ],
  });
  assert.match(artifact.productionEquivalenceBackfillReason, /baseUrl-derived production equivalence metadata/);
  const report = readJson(path.join(root, "release", "runtime-provenance-backfill.json"));
  assert.equal(report.status, "PASS");
  assert.equal(report.summary.provenanceBackfilledArtifacts, 0);
  assert.equal(report.summary.productionEquivalenceBackfilledArtifacts, 1);
  assert.equal(report.artifacts[0].productionEquivalenceBackfilled, true);
}

{
  const root = makeRoot();
  writeJson(path.join(root, "file", "file-processing-e2e.json"), {
    status: "PASS",
    baseUrl: "http://127.0.0.1:8080",
    result: { uploadAccepted: true },
  });
  const result = run({
    DDD_RELEASE_EVIDENCE_DIR: root,
    DDD_RUNTIME_PROVENANCE_BACKFILL: "true",
    DDD_RUNTIME_PROVENANCE_BACKFILL_ARTIFACTS: "file/file-processing-e2e.json",
    DDD_EVIDENCE_ENVIRONMENT: "local-dev",
    DDD_RELEASE_CANDIDATE: "abc123",
    DDD_EVIDENCE_OPERATOR: "codex",
  });
  assert.equal(result.status, 0, result.stderr);
  const artifact = readJson(path.join(root, "file", "file-processing-e2e.json"));
  assert.equal(artifact.status, "PASS");
  assert.deepEqual(artifact.result, { uploadAccepted: true });
  assert.equal(artifact.sourceEnvironment, "local-dev");
  assert.equal(artifact.releaseCandidate, "abc123");
  assert.equal(artifact.evidenceOperator, "codex");
  assert.match(artifact.provenanceBackfillReason, /result fields were not changed/);
  const report = readJson(path.join(root, "release", "runtime-provenance-backfill.json"));
  assert.equal(report.status, "PASS");
  assert.equal(report.summary.backfilledArtifacts, 1);
  assert.equal(report.summary.provenanceBackfilledArtifacts, 1);
  assert.equal(report.summary.productionEquivalenceBackfilledArtifacts, 0);
}

{
  const root = makeRoot();
  writeJson(path.join(root, "payment", "payment-webhook-e2e.json"), {
    status: "PASS",
    sourceEnvironment: "staging",
    releaseCandidate: "abc123",
    evidenceOperator: "codex",
  });
  const result = run({
    DDD_RELEASE_EVIDENCE_DIR: root,
    DDD_RUNTIME_PROVENANCE_BACKFILL: "true",
    DDD_RUNTIME_PROVENANCE_BACKFILL_ARTIFACTS: "payment/payment-webhook-e2e.json",
    DDD_EVIDENCE_ENVIRONMENT: "local-dev",
    DDD_RELEASE_CANDIDATE: "abc123",
    DDD_EVIDENCE_OPERATOR: "codex",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /refusing overwrite/);
}

{
  const root = makeRoot();
  writeJson(path.join(root, "jobs", "job-e2e-smoke.json"), {
    checkedAt: "2026-06-13T19:15:58.531Z",
    sourceEnvironment: "staging",
  });
  const result = run({
    DDD_RELEASE_EVIDENCE_DIR: root,
    DDD_RUNTIME_PROVENANCE_BACKFILL: "true",
    DDD_RUNTIME_PROVENANCE_BACKFILL_OVERWRITE: "true",
    DDD_RUNTIME_PROVENANCE_BACKFILL_ARTIFACTS: "jobs/job-e2e-smoke.json",
    DDD_EVIDENCE_ENVIRONMENT: "local-dev",
    DDD_RELEASE_CANDIDATE: "abc123",
    DDD_EVIDENCE_OPERATOR: "codex",
  });
  assert.equal(result.status, 0, result.stderr);
  const artifact = readJson(path.join(root, "jobs", "job-e2e-smoke.json"));
  assert.equal(artifact.checkedAt, "2026-06-13T19:15:58.531Z");
  assert.equal(artifact.sourceEnvironment, "local-dev");
}

{
  const root = makeRoot();
  writeJson(path.join(root, "frontend", "frontend-smoke.json"), {
    generatedAt: "2026-06-14T00:00:00.000Z",
    status: "FAIL",
    baseUrl: null,
    sourceEnvironment: null,
    releaseCandidate: null,
    evidenceOperator: null,
    productionEquivalence: {
      strict: true,
      https: false,
      localOnly: false,
      deploymentEvidence: null,
      issues: ["strict frontend smoke requires HTTPS baseUrl evidence"],
    },
    expectDeployed: false,
    summary: {
      total: 0,
      passed: 0,
      failed: 1,
      skipped: 0,
      requiredFlows: 1,
      missingRequiredFlows: 1,
    },
    requiredFlows: ["dashboard page is reachable"],
    missingRequiredFlows: ["dashboard page is reachable"],
    flowCoverage: [{
      flow: "dashboard page is reachable",
      status: "missing",
      matchedTitle: null,
      reason: "missing Playwright JSON report",
    }],
    tests: [],
    diagnostics: {
      playwrightReport: {
        present: false,
        reason: "missing Playwright JSON report",
      },
    },
    blockers: [
      "failed=1",
      "missing required flows=1",
      "missing baseUrl",
      "strict release requires DDD_FRONTEND_EXPECT_DEPLOYED=true evidence",
      "deployed smoke evidence provenance sourceEnvironment is required",
      "deployed smoke evidence provenance releaseCandidate is required",
      "deployed smoke evidence provenance evidenceOperator is required",
    ],
  });
  const result = run({
    DDD_RELEASE_EVIDENCE_DIR: root,
    DDD_RUNTIME_PROVENANCE_BACKFILL: "true",
    DDD_RUNTIME_PROVENANCE_BACKFILL_ARTIFACTS: "frontend/frontend-smoke.json",
    DDD_EVIDENCE_ENVIRONMENT: "local-dev",
    DDD_RELEASE_CANDIDATE: "abc123",
    DDD_EVIDENCE_OPERATOR: "codex",
  });
  assert.equal(result.status, 0, result.stderr);
  const artifact = readJson(path.join(root, "frontend", "frontend-smoke.json"));
  assert.equal(artifact.status, "FAIL");
  assert.equal(artifact.sourceEnvironment, "local-dev");
  assert.deepEqual(artifact.blockers, [
    "frontend smoke productionEquivalence.https must be true for strict release evidence",
    "frontend smoke productionEquivalence.deploymentEvidence is required",
    "frontend smoke productionEquivalence.issues must be empty for strict release evidence",
    "missing Playwright JSON report: missing Playwright JSON report",
    "failed=1",
    "missing required flows=1",
    "missing baseUrl",
    "strict release requires DDD_FRONTEND_EXPECT_DEPLOYED=true evidence",
  ]);
}

{
  const root = makeRoot();
  writeJson(path.join(root, "ai", "ai-runtime-drill.json"), {
    status: "FAIL",
    sourceEnvironment: "todo",
  });
  const result = run({
    DDD_RELEASE_EVIDENCE_DIR: root,
    DDD_RUNTIME_PROVENANCE_BACKFILL: "true",
    DDD_RUNTIME_PROVENANCE_BACKFILL_ARTIFACTS: "ai/ai-runtime-drill.json",
    DDD_EVIDENCE_ENVIRONMENT: "todo",
    DDD_RELEASE_CANDIDATE: "abc123",
    DDD_EVIDENCE_OPERATOR: "codex",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /runtime provenance sourceEnvironment must not contain placeholder text/);
}

console.log("[ddd-backfill-runtime-provenance.test] ok");
