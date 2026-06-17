#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { renderReleaseNextActionRunReportSummary } from "./ddd-release-next-action-run-report-summary.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");

const report = {
  generatedAt: "2026-06-15T00:00:00.000Z",
  reportStatus: "FAIL",
  exitCode: 1,
  ownerFilter: "release-performance",
  orderFilter: "2",
  summary: {
    totalEntries: 2,
    succeededEntries: 1,
    failedEntries: 1,
  },
  entries: [
    {
      order: 2,
      owner: "release-performance",
      receiptStatus: "ARTIFACT_MISSING",
      command: "node scripts/ddd-authenticated-performance-smoke.mjs",
      status: 0,
      durationMs: 120,
      finishedAt: "2026-06-15T00:00:01.000Z",
    },
    {
      order: 1,
      owner: "database",
      receiptStatus: "ARTIFACT_MISSING",
      command: "node scripts/ddd-collect-explain.mjs",
      status: 2,
      durationMs: 80,
      finishedAt: "2026-06-15T00:00:02.000Z",
    },
  ],
};

{
  const markdown = renderReleaseNextActionRunReportSummary(report);
  assert.match(markdown, /## Release Next Action Run Report/);
  assert.match(markdown, /\| Report status \| FAIL \|/);
  assert.match(markdown, /\| Owner filter \| release-performance \|/);
  assert.match(markdown, /\| Order filter \| 2 \|/);
  assert.match(markdown, /\| Entries \| 2 \|/);
  assert.match(markdown, /\| Succeeded entries \| 1 \|/);
  assert.match(markdown, /\| Failed entries \| 1 \|/);
  assert.match(markdown, /\| Owner \| Order \| Receipt status \| Status \| Duration ms \| Command \|/);
  assert.match(markdown, /\| release-performance \| 2 \| ARTIFACT_MISSING \| PASS \| 120 \| `node scripts\/ddd-authenticated-performance-smoke.mjs` \|/);
  assert.match(markdown, /\| database \| 1 \| ARTIFACT_MISSING \| FAIL\(2\) \| 80 \| `node scripts\/ddd-collect-explain.mjs` \|/);
}

{
  const markdown = renderReleaseNextActionRunReportSummary({
    ...report,
    entries: [{
      ...report.entries[0],
      command: `OPENAI_API_KEY=real-secret DDD_RELEASE_ENV_FILE=/tmp/.env.release.local node ${path.join(repoRoot, "scripts/ddd-release-config-evidence.mjs")}`,
    }],
  });
  assert.doesNotMatch(markdown, /real-secret/);
  assert.doesNotMatch(markdown, /DDD_RELEASE_ENV_FILE=\/tmp\/\.env\.release\.local/);
  assert(!markdown.includes(repoRoot), "summary must not expose the local repo path");
  assert.match(markdown, /OPENAI_API_KEY=<redacted>/);
  assert.match(markdown, /DDD_RELEASE_ENV_FILE=<release-env-file>/);
  assert.match(markdown, /node <repo>\/scripts\/ddd-release-config-evidence\.mjs/);
}

{
  const markdown = renderReleaseNextActionRunReportSummary(null);
  assert.match(markdown, /No release next action run report was generated/);
}

{
  const markdown = renderReleaseNextActionRunReportSummary({ reportStatus: "FAIL", exitCode: 1, entries: [] });
  assert.match(markdown, /No next-action commands were executed in this run/);
}

{
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-next-action-summary-"));
  const reportPath = path.join(tempDir, "report.json");
  const outputPath = path.join(tempDir, "summary.md");
  fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`);

  const result = spawnSync("node", ["scripts/ddd-release-next-action-run-report-summary.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_NEXT_ACTION_REPORT: reportPath,
      DDD_RELEASE_NEXT_ACTION_SUMMARY_OUTPUT: outputPath,
    },
  });
  assert.equal(result.status, 0, result.stderr);
  const markdown = fs.readFileSync(outputPath, "utf8");
  assert.match(markdown, /\| Failed entries \| 1 \|/);
}

console.log("[ddd-release-next-action-run-report-summary.test] ok");
