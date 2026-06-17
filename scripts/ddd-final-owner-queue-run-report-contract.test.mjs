#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { validateFinalOwnerQueueRunReport } from "./ddd-final-owner-queue-run-report-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");

function passingReport(overrides = {}) {
  const report = {
    generatedAt: "2026-06-14T00:00:00.000Z",
    reportStatus: "PASS",
    exitCode: 0,
    ownerFilter: "release-performance",
    statusFilter: "ACTIONABLE",
    entries: [
      {
        owner: "release-performance",
        queueOrder: 1,
        queueStatus: "ACTIONABLE",
        commandIndex: 1,
        commandCount: 1,
        command: "node scripts/ddd-release-readiness-summary.mjs",
        status: 0,
        durationMs: 120,
        finishedAt: "2026-06-14T00:00:01.000Z",
      },
    ],
  };
  const merged = { ...report, ...overrides };
  const entries = Array.isArray(merged.entries) ? merged.entries : [];
  const failedEntries = entries.filter((entry) => Number(entry?.status) !== 0).length;
  if (!Object.hasOwn(merged, "summary")) {
    merged.summary = {
      totalEntries: entries.length,
      succeededEntries: entries.length - failedEntries,
      failedEntries,
    };
  }
  return merged;
}

assert.deepEqual(validateFinalOwnerQueueRunReport(passingReport()), []);
assert.deepEqual(validateFinalOwnerQueueRunReport(passingReport({
  reportStatus: "FAIL",
  exitCode: 1,
  entries: [],
})), []);

{
  const issues = validateFinalOwnerQueueRunReport(null);
  assert(issues.includes("final owner queue run report must be a JSON object"));
}

{
  const issues = validateFinalOwnerQueueRunReport(passingReport({
    generatedAt: "not-a-date",
    reportStatus: "UNKNOWN",
    exitCode: -1,
    ownerFilter: 123,
    statusFilter: {},
    summary: null,
    entries: "bad",
  }));
  assert(issues.includes("final owner queue run report generatedAt must be an ISO-like datetime"));
  assert(issues.includes("final owner queue run report reportStatus must be PASS or FAIL, got UNKNOWN"));
  assert(issues.includes("final owner queue run report exitCode must be a non-negative integer, got -1"));
  assert(issues.includes("final owner queue run report ownerFilter must be a string or null"));
  assert(issues.includes("final owner queue run report statusFilter must be a string or null"));
  assert(issues.includes("final owner queue run report summary must be an object"));
  assert(issues.includes("final owner queue run report entries must be an array"));
}

{
  const issues = validateFinalOwnerQueueRunReport(passingReport({ reportStatus: "PASS", exitCode: 2 }));
  assert(issues.includes("final owner queue run report PASS must have exitCode=0, got 2"));
}

{
  const issues = validateFinalOwnerQueueRunReport(passingReport({ reportStatus: "FAIL", exitCode: 0 }));
  assert(issues.includes("final owner queue run report FAIL must have non-zero exitCode"));
}

{
  const issues = validateFinalOwnerQueueRunReport(passingReport({ entries: [] }));
  assert(issues.includes("final owner queue run report PASS must include at least one command entry"));
}

{
  const issues = validateFinalOwnerQueueRunReport(passingReport({
    entries: [
      {
        owner: "",
        queueOrder: -1,
        queueStatus: "",
        commandIndex: 0,
        commandCount: 0,
        command: "",
        status: -1,
        durationMs: -2,
        finishedAt: "bad-date",
      },
    ],
  }));
  assert(issues.includes("final owner queue run report entries[0].owner is required"));
  assert(issues.includes("final owner queue run report entries[0].queueOrder must be a non-negative integer"));
  assert(issues.includes("final owner queue run report entries[0].queueStatus is required"));
  assert(issues.includes("final owner queue run report entries[0].commandIndex must be a positive integer"));
  assert(issues.includes("final owner queue run report entries[0].commandCount must be a positive integer"));
  assert(issues.includes("final owner queue run report entries[0].command is required"));
  assert(issues.includes("final owner queue run report entries[0].status must be a non-negative integer"));
  assert(issues.includes("final owner queue run report entries[0].durationMs must be a non-negative number"));
  assert(issues.includes("final owner queue run report entries[0].finishedAt must be an ISO-like datetime"));
}

{
  const issues = validateFinalOwnerQueueRunReport(passingReport({
    entries: [{ ...passingReport().entries[0], commandIndex: 2, commandCount: 1 }],
  }));
  assert(issues.includes("final owner queue run report entries[0].commandIndex must be <= commandCount"));
}

{
  const issues = validateFinalOwnerQueueRunReport(passingReport({
    entries: [
      {
        owner: "release-performance",
        queueOrder: 1,
        queueStatus: "ACTIONABLE",
        command: "false",
        status: 1,
        durationMs: 1,
        finishedAt: "2026-06-14T00:00:01.000Z",
      },
    ],
  }));
  assert(issues.includes("final owner queue run report must be FAIL when any command entry failed"));
}

{
  const issues = validateFinalOwnerQueueRunReport(passingReport({
    summary: {
      totalEntries: 99,
      succeededEntries: 98,
      failedEntries: 1,
    },
  }));
  assert(issues.includes("final owner queue run report summary.totalEntries must be 1, got 99"));
  assert(issues.includes("final owner queue run report summary.succeededEntries must be 1, got 98"));
  assert(issues.includes("final owner queue run report summary.failedEntries must be 0, got 1"));
}

{
  const unsafeCommandIssue = "final owner queue run report entries[0].command must not expose concrete secret values, release env files, or local repo paths";
  assert(validateFinalOwnerQueueRunReport(passingReport({
    entries: [{ ...passingReport().entries[0], command: "JWT_SECRET=real-secret node scripts/ddd-release-readiness-summary.mjs" }],
  })).includes(unsafeCommandIssue));
  assert(validateFinalOwnerQueueRunReport(passingReport({
    entries: [{ ...passingReport().entries[0], command: "DDD_RELEASE_ENV_FILE=/tmp/.env.release.local node scripts/ddd-release-env-file-lint.mjs" }],
  })).includes(unsafeCommandIssue));
  assert(validateFinalOwnerQueueRunReport(passingReport({
    entries: [{ ...passingReport().entries[0], command: `node ${path.join(repoRoot, "scripts/ddd-release-readiness-summary.mjs")}` }],
  })).includes(unsafeCommandIssue));
  assert.deepEqual(validateFinalOwnerQueueRunReport(passingReport({
    entries: [{ ...passingReport().entries[0], command: "JWT_SECRET=<redacted> DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-readiness-summary.mjs" }],
  })), []);
}

{
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-owner-queue-report-"));
  const reportPath = path.join(tempDir, "report.json");
  fs.writeFileSync(reportPath, `${JSON.stringify(passingReport(), null, 2)}\n`);
  const result = spawnSync("node", ["scripts/ddd-final-owner-queue-run-report-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_FINAL_OWNER_QUEUE_REPORT: reportPath,
    },
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /ddd-final-owner-queue-run-report-contract\] ok/);
}

{
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-owner-queue-report-"));
  const missingPath = path.join(tempDir, "missing.json");
  const result = spawnSync("node", ["scripts/ddd-final-owner-queue-run-report-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_FINAL_OWNER_QUEUE_REPORT: missingPath,
    },
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /missing report/);
}

console.log("[ddd-final-owner-queue-run-report-contract.test] ok");
