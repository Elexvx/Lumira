#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");

function runScript(script, env) {
  return spawnSync("node", [script], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_EVIDENCE_STRICT: "true",
      DDD_EVIDENCE_ENVIRONMENT: "",
      DDD_RELEASE_CANDIDATE: "",
      DDD_EVIDENCE_OPERATOR: "",
      ...env,
    },
  });
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const root = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-runtime-provenance-"));

{
  const dir = path.join(root, "job");
  const result = runScript("scripts/ddd-job-e2e-smoke.mjs", {
    DDD_JOB_SMOKE_DIR: dir,
    DDD_JOB_DB_CHECK_ENABLED: "false",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /runtime provenance sourceEnvironment is required/);
  const artifact = readJson(path.join(dir, "job-e2e-smoke.json"));
  assert.match(artifact.error, /runtime provenance sourceEnvironment is required/);
  assert.equal(artifact.sourceEnvironment, null);
}

{
  const dir = path.join(root, "file");
  const result = runScript("scripts/ddd-file-processing-e2e-smoke.mjs", {
    DDD_FILE_PROCESSING_E2E_DIR: dir,
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /runtime provenance sourceEnvironment is required/);
  const artifact = readJson(path.join(dir, "file-processing-e2e.json"));
  assert.equal(artifact.status, "FAIL");
  assert.match(artifact.error, /runtime provenance sourceEnvironment is required/);
}

{
  const dir = path.join(root, "payment");
  const result = runScript("scripts/ddd-payment-webhook-e2e-smoke.mjs", {
    DDD_PAYMENT_WEBHOOK_E2E_DIR: dir,
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /runtime provenance sourceEnvironment is required/);
  const artifact = readJson(path.join(dir, "payment-webhook-e2e.json"));
  assert.equal(artifact.status, "FAIL");
  assert.match(artifact.error, /runtime provenance sourceEnvironment is required/);
}

{
  const dir = path.join(root, "ai");
  const result = runScript("scripts/ddd-ai-runtime-drill.mjs", {
    DDD_AI_RUNTIME_DRILL_DIR: dir,
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /runtime provenance sourceEnvironment is required/);
  const artifact = readJson(path.join(dir, "ai-runtime-drill.json"));
  assert.equal(artifact.status, "FAIL");
  assert(artifact.failures.some((failure) => failure.includes("runtime provenance sourceEnvironment is required")));
}

console.log("[ddd-runtime-provenance-preflight.test] ok");
