#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { renderReleaseExecutionRunReportSummary } from "./ddd-release-execution-run-report-summary.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");

const report = {
  generatedAt: "2026-06-15T00:00:00.000Z",
  reportStatus: "FAIL",
  exitCode: 1,
  batchFilter: "p0-docker-release-infra",
  ownerFilter: "release-infra",
  priorityFilter: "P0",
  summary: {
    totalEntries: 2,
    succeededEntries: 1,
    failedEntries: 1,
  },
  entries: [
    {
      batchId: "p0-docker-release-infra",
      owner: "release-infra",
      priority: "P0",
      command: "DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs",
      status: 0,
      durationMs: 120,
      finishedAt: "2026-06-15T00:00:01.000Z",
    },
    {
      batchId: "p0-migration-database",
      owner: "database",
      priority: "P0",
      command: "node scripts/ddd-migration-evidence.mjs",
      status: 2,
      durationMs: 80,
      finishedAt: "2026-06-15T00:00:02.000Z",
    },
  ],
};

{
  const markdown = renderReleaseExecutionRunReportSummary(report);
  assert.match(markdown, /## Release Execution Run Report/);
  assert.match(markdown, /\| Report status \| FAIL \|/);
  assert.match(markdown, /\| Batch filter \| p0-docker-release-infra \|/);
  assert.match(markdown, /\| Owner filter \| release-infra \|/);
  assert.match(markdown, /\| Priority filter \| P0 \|/);
  assert.match(markdown, /\| Entries \| 2 \|/);
  assert.match(markdown, /\| Succeeded entries \| 1 \|/);
  assert.match(markdown, /\| Failed entries \| 1 \|/);
  assert.match(markdown, /\| Batch \| Owner \| Priority \| Status \| Duration ms \| Command \|/);
  assert.match(markdown, /\| p0-docker-release-infra \| release-infra \| P0 \| PASS \| 120 \| `DDD_DOCKER_BUILD_STRICT=true node scripts\/ddd-docker-build-evidence.mjs` \|/);
  assert.match(markdown, /\| p0-migration-database \| database \| P0 \| FAIL\(2\) \| 80 \| `node scripts\/ddd-migration-evidence.mjs` \|/);
}

{
  const markdown = renderReleaseExecutionRunReportSummary({
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
  const markdown = renderReleaseExecutionRunReportSummary(null);
  assert.match(markdown, /No release execution run report was generated/);
}

{
  const markdown = renderReleaseExecutionRunReportSummary({ reportStatus: "FAIL", exitCode: 1, entries: [] });
  assert.match(markdown, /No release execution commands were executed in this run/);
}

{
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-execution-summary-"));
  const reportPath = path.join(tempDir, "report.json");
  const outputPath = path.join(tempDir, "summary.md");
  fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`);

  const result = spawnSync("node", ["scripts/ddd-release-execution-run-report-summary.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_EXECUTION_REPORT: reportPath,
      DDD_RELEASE_EXECUTION_SUMMARY_OUTPUT: outputPath,
    },
  });
  assert.equal(result.status, 0, result.stderr);
  const markdown = fs.readFileSync(outputPath, "utf8");
  assert.match(markdown, /\| Failed entries \| 1 \|/);
}

console.log("[ddd-release-execution-run-report-summary.test] ok");
