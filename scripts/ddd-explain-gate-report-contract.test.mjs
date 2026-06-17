#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { validateExplainGateReport } from "./ddd-explain-gate-report-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");

function passingReport(overrides = {}) {
  return {
    generatedAt: "2026-06-14T00:00:00.000Z",
    status: "FAIL",
    strict: true,
    explainDir: "/tmp/ddd-explain",
    scannedExplainFileCount: 6,
    blockerCount: 1,
    issues: [{ scope: "metadata", detail: "legacyPlanImport must be false" }],
    ...overrides,
  };
}

assert.deepEqual(validateExplainGateReport(passingReport()), []);
assert.deepEqual(validateExplainGateReport(passingReport({
  status: "PASS",
  blockerCount: 0,
  issues: [],
})), []);

{
  const issues = validateExplainGateReport(null);
  assert(issues.includes("EXPLAIN gate report must be a JSON object"));
}

{
  const issues = validateExplainGateReport(passingReport({
    generatedAt: "bad-date",
    status: "UNKNOWN",
    strict: "yes",
    explainDir: "",
    scannedExplainFileCount: -1,
    blockerCount: -1,
    issues: "bad",
  }));
  assert(issues.includes("EXPLAIN gate report generatedAt must be an ISO-like datetime"));
  assert(issues.includes("EXPLAIN gate report status must be PASS or FAIL, got UNKNOWN"));
  assert(issues.includes("EXPLAIN gate report strict must be boolean"));
  assert(issues.includes("EXPLAIN gate report explainDir is required"));
  assert(issues.includes("EXPLAIN gate report scannedExplainFileCount must be a non-negative integer"));
  assert(issues.includes("EXPLAIN gate report blockerCount must be a non-negative integer"));
  assert(issues.includes("EXPLAIN gate report issues must be an array"));
}

{
  const issues = validateExplainGateReport(passingReport({ blockerCount: 2 }));
  assert(issues.includes("EXPLAIN gate report blockerCount must match issues length, got 2 and 1"));
}

{
  const issues = validateExplainGateReport(passingReport({ status: "PASS" }));
  assert(issues.includes("EXPLAIN gate report PASS must have blockerCount=0, got 1"));
}

{
  const issues = validateExplainGateReport(passingReport({ status: "FAIL", blockerCount: 0, issues: [] }));
  assert(issues.includes("EXPLAIN gate report FAIL must have blockerCount > 0"));
}

{
  const issues = validateExplainGateReport(passingReport({
    issues: [{ scope: "", detail: "" }],
  }));
  assert(issues.includes("EXPLAIN gate report issues[0].scope is required"));
  assert(issues.includes("EXPLAIN gate report issues[0].detail is required"));
}

{
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-explain-gate-contract-"));
  const reportPath = path.join(tempDir, "report.json");
  fs.writeFileSync(reportPath, `${JSON.stringify(passingReport(), null, 2)}\n`);
  const result = spawnSync("node", ["scripts/ddd-explain-gate-report-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_EXPLAIN_GATE_REPORT: reportPath },
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /ddd-explain-gate-report-contract\] ok/);
}

console.log("[ddd-explain-gate-report-contract.test] ok");
