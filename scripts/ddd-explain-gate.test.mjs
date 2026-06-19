#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");

function runGate(env = {}) {
  const explainDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-explain-gate-"));
  return runGateWithDir(explainDir, env);
}

function runGateWithDir(explainDir, env = {}) {
  const reportPath = path.join(fs.mkdtempSync(path.join(os.tmpdir(), "lumira-explain-gate-report-")), "report.json");
  const result = spawnSync("node", ["scripts/ddd-explain-gate.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_EXPLAIN_DIR: explainDir,
      DDD_EXPLAIN_GATE_REPORT: reportPath,
      ...env,
    },
  });
  const report = JSON.parse(fs.readFileSync(reportPath, "utf8"));
  return { result, report };
}

{
  const { result, report } = runGate();
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /hot path document validated; no explain json files found/);
  assert.equal(report.status, "PASS");
  assert.equal(report.strict, false);
  assert.equal(report.scannedExplainFileCount, 0);
  assert.equal(report.blockerCount, 0);
  assert.deepEqual(report.issues, []);
}

{
  const { result, report } = runGate({ DDD_EXPLAIN_STRICT: "true" });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /strict mode requires explain json files/);
  assert.equal(report.status, "FAIL");
  assert.equal(report.strict, true);
  assert.equal(report.scannedExplainFileCount, 0);
  assert.equal(report.blockerCount, 1);
  assert.match(report.issues[0].detail, /strict mode requires explain json files/);
}

{
  const { result, report } = runGate({ DDD_RELEASE_EVIDENCE_STRICT: "true" });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /strict mode requires explain json files/);
  assert.equal(report.status, "FAIL");
  assert.equal(report.strict, true);
}

{
  const explainDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-explain-gate-"));
  fs.writeFileSync(path.join(explainDir, "message-visible-list.json"), "{bad json");
  const { result, report } = runGateWithDir(explainDir, { DDD_EXPLAIN_STRICT: "true" });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /invalid explain json/);
  assert.match(result.stderr, /scanned 1 explain json file\(s\) with \d+ blocker\(s\)/);
  assert.doesNotMatch(result.stdout, /validated 1 explain json file/);
  assert.equal(report.status, "FAIL");
  assert.equal(report.scannedExplainFileCount, 1);
  assert(report.blockerCount > 1);
  assert(report.issues.some((issue) => issue.detail.includes("invalid explain json")));
}

{
  const explainDir = fs.mkdtempSync(path.join(repoRoot, "tmp", "lumira-explain-gate-"));
  try {
    fs.writeFileSync(path.join(explainDir, "message-visible-list.json"), "{bad json");
    const { result, report } = runGateWithDir(explainDir, { DDD_EXPLAIN_STRICT: "true" });
    assert.notEqual(result.status, 0);
    assert.equal(report.explainDir, path.relative(repoRoot, explainDir).replaceAll("\\", "/"));
    assert(report.issues.some((issue) => issue.detail.includes("tmp/lumira-explain-gate-")));
    assert(!report.issues.some((issue) => issue.detail.includes(repoRoot)));
  } finally {
    fs.rmSync(explainDir, { recursive: true, force: true });
  }
}

console.log("[ddd-explain-gate.test] ok");
