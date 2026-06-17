#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { validateReleaseNextActionRunReport } from "./ddd-release-next-action-run-report-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");

function validReport(overrides = {}) {
  return {
    generatedAt: "2026-06-15T00:00:00.000Z",
    reportStatus: "PASS",
    exitCode: 0,
    ownerFilter: null,
    orderFilter: null,
    summary: {
      totalEntries: 1,
      succeededEntries: 1,
      failedEntries: 0,
    },
    entries: [{
      order: 1,
      owner: "database",
      receiptStatus: "ARTIFACT_MISSING",
      command: "node scripts/ddd-collect-explain.mjs",
      status: 0,
      durationMs: 12,
      finishedAt: "2026-06-15T00:00:01.000Z",
    }],
    ...overrides,
  };
}

assert.deepEqual(validateReleaseNextActionRunReport(validReport()), []);
assert(validateReleaseNextActionRunReport(null).includes("release next action run report must be a JSON object"));

{
  const issues = validateReleaseNextActionRunReport(validReport({
    generatedAt: "bad",
    reportStatus: "UNKNOWN",
    exitCode: -1,
    ownerFilter: 1,
    orderFilter: 1,
    summary: null,
    entries: "bad",
  }));
  assert(issues.includes("release next action run report generatedAt must be an ISO-like datetime"));
  assert(issues.includes("release next action run report reportStatus must be PASS or FAIL, got UNKNOWN"));
  assert(issues.includes("release next action run report exitCode must be a non-negative integer, got -1"));
  assert(issues.includes("release next action run report ownerFilter must be a string or null"));
  assert(issues.includes("release next action run report orderFilter must be a string or null"));
  assert(issues.includes("release next action run report summary must be an object"));
  assert(issues.includes("release next action run report entries must be an array"));
}

{
  const issues = validateReleaseNextActionRunReport(validReport({ reportStatus: "PASS", exitCode: 2 }));
  assert(issues.includes("release next action run report PASS must have exitCode=0, got 2"));
}

{
  const issues = validateReleaseNextActionRunReport(validReport({ reportStatus: "FAIL", exitCode: 0 }));
  assert(issues.includes("release next action run report FAIL must have non-zero exitCode"));
}

{
  const report = validReport({
    reportStatus: "PASS",
    exitCode: 0,
    entries: [{
      order: -1,
      owner: "",
      receiptStatus: "",
      command: "",
      status: -1,
      durationMs: -1,
      finishedAt: "bad",
    }],
  });
  const issues = validateReleaseNextActionRunReport(report);
  assert(issues.includes("release next action run report entries[0].order must be a non-negative integer"));
  assert(issues.includes("release next action run report entries[0].owner is required"));
  assert(issues.includes("release next action run report entries[0].receiptStatus is required"));
  assert(issues.includes("release next action run report entries[0].command is required"));
  assert(issues.includes("release next action run report entries[0].status must be a non-negative integer"));
  assert(issues.includes("release next action run report entries[0].durationMs must be a non-negative number"));
  assert(issues.includes("release next action run report entries[0].finishedAt must be an ISO-like datetime"));
}

{
  const issues = validateReleaseNextActionRunReport(validReport({
    reportStatus: "PASS",
    entries: [{ ...validReport().entries[0], status: 1 }],
    summary: { totalEntries: 99, succeededEntries: 98, failedEntries: 0 },
  }));
  assert(issues.includes("release next action run report must be FAIL when any command entry failed"));
  assert(issues.includes("release next action run report summary.totalEntries must be 1, got 99"));
  assert(issues.includes("release next action run report summary.succeededEntries must be 0, got 98"));
  assert(issues.includes("release next action run report summary.failedEntries must be 1, got 0"));
}

{
  const issues = validateReleaseNextActionRunReport(validReport({
    entries: [],
    summary: { totalEntries: 0, succeededEntries: 0, failedEntries: 0 },
  }));
  assert(issues.includes("release next action run report PASS must include at least one command entry"));
}

{
  const unsafeCommandIssue = "release next action run report entries[0].command must not expose concrete secret values, release env files, or local repo paths";
  assert(validateReleaseNextActionRunReport(validReport({
    entries: [{ ...validReport().entries[0], command: "OPENAI_API_KEY=real-secret node scripts/ddd-release-config-evidence.mjs" }],
  })).includes(unsafeCommandIssue));
  assert(validateReleaseNextActionRunReport(validReport({
    entries: [{ ...validReport().entries[0], command: "DDD_RELEASE_ENV_FILE=/tmp/.env.release.local node scripts/ddd-release-env-file-lint.mjs" }],
  })).includes(unsafeCommandIssue));
  assert(validateReleaseNextActionRunReport(validReport({
    entries: [{ ...validReport().entries[0], command: `node ${path.join(repoRoot, "scripts/ddd-release-config-evidence.mjs")}` }],
  })).includes(unsafeCommandIssue));
  assert.deepEqual(validateReleaseNextActionRunReport(validReport({
    entries: [{ ...validReport().entries[0], command: "OPENAI_API_KEY=<redacted> DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs" }],
  })), []);
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-next-action-run-report-"));
  const reportPath = path.join(directory, "release-next-action-run-report.json");
  fs.writeFileSync(reportPath, `${JSON.stringify(validReport(), null, 2)}\n`);
  const result = spawnSync("node", ["scripts/ddd-release-next-action-run-report-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_NEXT_ACTION_REPORT: reportPath,
    },
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /ddd-release-next-action-run-report-contract\] ok/);
}

console.log("[ddd-release-next-action-run-report-contract.test] ok");
