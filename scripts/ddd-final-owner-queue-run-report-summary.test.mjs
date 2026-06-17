#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { renderFinalOwnerQueueRunReportSummary } from "./ddd-final-owner-queue-run-report-summary.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");

const report = {
  generatedAt: "2026-06-14T00:00:00.000Z",
  reportStatus: "FAIL",
  exitCode: 1,
  ownerFilter: "release-performance",
  statusFilter: "ACTIONABLE",
  summary: {
    totalEntries: 2,
    succeededEntries: 1,
    failedEntries: 1,
  },
  entries: [
    {
      owner: "release-performance",
      queueOrder: 1,
      queueStatus: "ACTIONABLE",
      commandIndex: 1,
      commandCount: 2,
      command: "node scripts/ddd-authenticated-performance-smoke.mjs",
      status: 0,
      durationMs: 120,
      finishedAt: "2026-06-14T00:00:01.000Z",
    },
    {
      owner: "database",
      queueOrder: 2,
      queueStatus: "ACTIONABLE",
      commandIndex: 2,
      commandCount: 2,
      command: "node scripts/ddd-migration-evidence.mjs",
      status: 2,
      durationMs: 80,
      finishedAt: "2026-06-14T00:00:02.000Z",
    },
  ],
};

{
  const markdown = renderFinalOwnerQueueRunReportSummary(report);
  assert.match(markdown, /## Final Owner Queue Run Report/);
  assert.match(markdown, /\| Report status \| FAIL \|/);
  assert.match(markdown, /\| Owner filter \| release-performance \|/);
  assert.match(markdown, /\| Status filter \| ACTIONABLE \|/);
  assert.match(markdown, /\| Entries \| 2 \|/);
  assert.match(markdown, /\| Succeeded entries \| 1 \|/);
  assert.match(markdown, /\| Failed entries \| 1 \|/);
  assert.match(markdown, /\| Owner \| Queue order \| Queue status \| Command \| Status \| Duration ms \| Command text \|/);
  assert.match(markdown, /\| release-performance \| 1 \| ACTIONABLE \| 1\/2 \| PASS \| 120 \| `node scripts\/ddd-authenticated-performance-smoke.mjs` \|/);
  assert.match(markdown, /\| database \| 2 \| ACTIONABLE \| 2\/2 \| FAIL\(2\) \| 80 \| `node scripts\/ddd-migration-evidence.mjs` \|/);
}

{
  const markdown = renderFinalOwnerQueueRunReportSummary({
    ...report,
    entries: [{
      ...report.entries[0],
      command: `JWT_SECRET=real-secret DDD_RELEASE_ENV_FILE=/tmp/.env.release.local node ${path.join(repoRoot, "scripts/ddd-release-readiness-summary.mjs")}`,
    }],
  });
  assert.doesNotMatch(markdown, /real-secret/);
  assert.doesNotMatch(markdown, /DDD_RELEASE_ENV_FILE=\/tmp\/\.env\.release\.local/);
  assert.doesNotMatch(markdown, /\.env\.release\.local/);
  assert(!markdown.includes(repoRoot), "summary must not expose the local repo path");
  assert.match(markdown, /JWT_SECRET=<redacted>/);
  assert.match(markdown, /DDD_RELEASE_ENV_FILE=<release-env-file>/);
  assert.match(markdown, /node <repo>\/scripts\/ddd-release-readiness-summary\.mjs/);
}

{
  const markdown = renderFinalOwnerQueueRunReportSummary(null);
  assert.match(markdown, /No final owner queue run report was generated/);
}

{
  const markdown = renderFinalOwnerQueueRunReportSummary({ reportStatus: "FAIL", exitCode: 1, entries: [] });
  assert.match(markdown, /No owner queue commands were executed in this run/);
}

{
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-owner-queue-summary-"));
  const reportPath = path.join(tempDir, "report.json");
  const outputPath = path.join(tempDir, "summary.md");
  fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`);

  const result = spawnSync("node", ["scripts/ddd-final-owner-queue-run-report-summary.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_FINAL_OWNER_QUEUE_REPORT: reportPath,
      DDD_FINAL_OWNER_QUEUE_SUMMARY_OUTPUT: outputPath,
    },
  });
  assert.equal(result.status, 0, result.stderr);
  const markdown = fs.readFileSync(outputPath, "utf8");
  assert.match(markdown, /\| Failed entries \| 1 \|/);
}

console.log("[ddd-final-owner-queue-run-report-summary.test] ok");
