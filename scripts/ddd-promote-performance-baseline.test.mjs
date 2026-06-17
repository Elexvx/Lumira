#!/usr/bin/env node

import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");

function actualArtifact(overrides = {}) {
  const perEndpoint = Object.fromEntries([
    ["GET /api/v2/auth/current-user", 80],
    ["GET /api/v2/iam/tenants/current", 85],
    ["GET /api/v2/iam/permissions", 90],
    ["GET /api/v2/message/unread-count", 70],
    ["GET /api/v2/message/messages?pageNo=1&pageSize=20", 95],
    ["GET /api/v2/files?pageNo=1&pageSize=20", 100],
    ["GET /api/v2/plugins/current/bootstrap", 88],
    ["GET /api/v2/localization/runtime/zh-CN", 60],
    ["GET /api/v2/payment/providers", 75],
  ].map(([endpoint, p95]) => [endpoint, {
    samples: 20,
    p50: Math.max(1, p95 - 30),
    p95,
    p99: p95 + 20,
    statusCounts: {
      200: 20,
    },
  }]));
  return {
    baseUrl: "https://api.staging.lumira.app",
    productionEquivalence: {
      strict: true,
      https: true,
      localOnly: false,
      deploymentEvidence: "artifacts/ddd/deploy/staging-api.txt",
      issues: [],
    },
    sourceEnvironment: "staging",
    releaseCandidate: "rc-1",
    evidenceOperator: "release-owner",
    checkedAt: "2026-06-14T00:00:00.000Z",
    durationMs: 5000,
    concurrency: 2,
    samples: 180,
    ok: 180,
    failed: 0,
    p50: 70,
    p95: 120,
    p99: 150,
    upload: {
      path: "/api/v2/files/upload",
      status: 200,
      elapsedMs: 140,
      fileId: 1001,
    },
    perEndpoint,
    oneShots: [
      {
        name: "POST /api/v2/auth/session/keepalive",
        status: 200,
        elapsedMs: 60,
      },
    ],
    ...overrides,
  };
}

function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify(data, null, 2)}\n`);
}

function sha256(file) {
  return createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function runPromote(env) {
  return spawnSync("node", ["scripts/ddd-promote-performance-baseline.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      ...env,
    },
  });
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-auth-perf-baseline-"));
  const source = path.join(directory, "actual.json");
  const output = path.join(directory, "baseline.json");
  const audit = path.join(directory, "promotion.json");
  writeJson(source, actualArtifact());
  const result = runPromote({
    DDD_AUTH_PERF_BASELINE_SOURCE: source,
    DDD_AUTH_PERF_BASELINE_OUTPUT: output,
    DDD_AUTH_PERF_BASELINE_PROMOTION_OUTPUT: audit,
    DDD_AUTH_PERF_BASELINE_ACCEPTED_BY: "release-owner",
    DDD_AUTH_PERF_BASELINE_ENVIRONMENT: "staging",
    DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT: "artifacts/ddd/performance/authenticated-runtime-actual.json",
    DDD_RELEASE_CANDIDATE: "rc-1",
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const baseline = JSON.parse(fs.readFileSync(output, "utf8"));
  assert.equal(baseline.baselineType, "authenticated-runtime");
  assert.equal(baseline.acceptedBy, "release-owner");
  assert.equal(baseline.sourceEnvironment, "staging");
  assert.equal(baseline.sourceArtifact, "artifacts/ddd/performance/authenticated-runtime-actual.json");
  assert.equal(baseline.sourceSha256, sha256(source));
  assert.equal(baseline.releaseCandidate, "rc-1");
  assert.equal(baseline.p95, 120);
  const promotion = JSON.parse(fs.readFileSync(audit, "utf8"));
  assert.equal(promotion.status, "PASS");
  assert.equal(promotion.sourceSha256, sha256(source));
  assert.equal(promotion.sourceActual.endpointCount, 9);
  assert.equal(promotion.baseline.sourceSha256, sha256(source));
  assert.equal(promotion.baseline.baselineType, "authenticated-runtime");
  assert.deepEqual(promotion.blockers, []);
}

{
  const directory = fs.mkdtempSync(path.join(repoRoot, "tmp", "lumira-auth-perf-baseline-"));
  const source = path.join(directory, "actual.json");
  const output = path.join(directory, "baseline.json");
  const audit = path.join(directory, "promotion.json");
  writeJson(source, actualArtifact());
  const result = runPromote({
    DDD_AUTH_PERF_BASELINE_SOURCE: source,
    DDD_AUTH_PERF_BASELINE_OUTPUT: output,
    DDD_AUTH_PERF_BASELINE_PROMOTION_OUTPUT: audit,
    DDD_AUTH_PERF_BASELINE_ACCEPTED_BY: "release-owner",
    DDD_AUTH_PERF_BASELINE_ENVIRONMENT: "staging",
    DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT: source,
    DDD_RELEASE_CANDIDATE: "rc-1",
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const promotion = JSON.parse(fs.readFileSync(audit, "utf8"));
  assert.equal(promotion.sourceFile.startsWith("/"), false);
  assert.equal(promotion.outputFile.startsWith("/"), false);
  assert.equal(promotion.sourceArtifact.startsWith("/"), false);
  assert.equal(promotion.sourceSha256, sha256(source));
  fs.rmSync(directory, { recursive: true, force: true });
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-auth-perf-baseline-"));
  const source = path.join(directory, "actual.json");
  const output = path.join(directory, "baseline.json");
  const audit = path.join(directory, "promotion.json");
  writeJson(source, actualArtifact({ baseUrl: "http://api.staging.lumira.app" }));
  const result = runPromote({
    DDD_AUTH_PERF_BASELINE_SOURCE: source,
    DDD_AUTH_PERF_BASELINE_OUTPUT: output,
    DDD_AUTH_PERF_BASELINE_PROMOTION_OUTPUT: audit,
    DDD_AUTH_PERF_BASELINE_ACCEPTED_BY: "release-owner",
    DDD_AUTH_PERF_BASELINE_ENVIRONMENT: "staging",
    DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT: "artifacts/ddd/performance/authenticated-runtime-actual.json",
    DDD_RELEASE_CANDIDATE: "rc-1",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /source actual artifact must use HTTPS production-equivalent baseUrl/);
  assert.equal(fs.existsSync(output), false);
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-auth-perf-baseline-"));
  const source = path.join(directory, "actual.json");
  const output = path.join(directory, "baseline.json");
  const audit = path.join(directory, "promotion.json");
  const incomplete = actualArtifact();
  delete incomplete.perEndpoint["GET /api/v2/payment/providers"];
  incomplete.samples = 160;
  incomplete.ok = 160;
  writeJson(source, incomplete);
  const result = runPromote({
    DDD_AUTH_PERF_BASELINE_SOURCE: source,
    DDD_AUTH_PERF_BASELINE_OUTPUT: output,
    DDD_AUTH_PERF_BASELINE_PROMOTION_OUTPUT: audit,
    DDD_AUTH_PERF_BASELINE_ACCEPTED_BY: "release-owner",
    DDD_AUTH_PERF_BASELINE_ENVIRONMENT: "staging",
    DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT: "artifacts/ddd/performance/authenticated-runtime-actual.json",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /source actual artifact missing required perEndpoint metrics GET \/api\/v2\/payment\/providers/);
  assert.equal(fs.existsSync(output), false);
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-auth-perf-baseline-"));
  const source = path.join(directory, "actual.json");
  const output = path.join(directory, "baseline.json");
  const audit = path.join(directory, "promotion.json");
  writeJson(source, actualArtifact({ releaseCandidate: "rc-old" }));
  const result = runPromote({
    DDD_AUTH_PERF_BASELINE_SOURCE: source,
    DDD_AUTH_PERF_BASELINE_OUTPUT: output,
    DDD_AUTH_PERF_BASELINE_PROMOTION_OUTPUT: audit,
    DDD_AUTH_PERF_BASELINE_ACCEPTED_BY: "release-owner",
    DDD_AUTH_PERF_BASELINE_ENVIRONMENT: "staging",
    DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT: "artifacts/ddd/performance/authenticated-runtime-actual.json",
    DDD_RELEASE_CANDIDATE: "rc-1",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /source actual artifact releaseCandidate rc-old does not match DDD_RELEASE_CANDIDATE rc-1/);
  assert.equal(fs.existsSync(output), false);
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-auth-perf-baseline-"));
  const source = path.join(directory, "actual.json");
  const output = path.join(directory, "baseline.json");
  const audit = path.join(directory, "promotion.json");
  writeJson(source, actualArtifact({ baseUrl: "http://127.0.0.1:8080" }));
  const result = runPromote({
    DDD_AUTH_PERF_BASELINE_SOURCE: source,
    DDD_AUTH_PERF_BASELINE_OUTPUT: output,
    DDD_AUTH_PERF_BASELINE_PROMOTION_OUTPUT: audit,
    DDD_AUTH_PERF_BASELINE_ACCEPTED_BY: "release-owner",
    DDD_AUTH_PERF_BASELINE_ENVIRONMENT: "staging",
    DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT: "artifacts/ddd/performance/authenticated-runtime-actual.json",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /source actual artifact must be production-equivalent and non-local/);
  assert.equal(fs.existsSync(output), false);
  assert.match(result.stderr, /wrote promotion audit/);
  const promotion = JSON.parse(fs.readFileSync(audit, "utf8"));
  assert.equal(promotion.status, "FAIL");
  assert.equal(promotion.sourceActual.localOnly, true);
  assert(promotion.blockers.some((blocker) => blocker.includes("production-equivalent and non-local")));
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-auth-perf-baseline-"));
  const source = path.join(directory, "actual.json");
  const output = path.join(directory, "baseline.json");
  const audit = path.join(directory, "promotion.json");
  writeJson(source, actualArtifact({ failed: 1 }));
  const result = runPromote({
    DDD_AUTH_PERF_BASELINE_SOURCE: source,
    DDD_AUTH_PERF_BASELINE_OUTPUT: output,
    DDD_AUTH_PERF_BASELINE_PROMOTION_OUTPUT: audit,
    DDD_AUTH_PERF_BASELINE_ACCEPTED_BY: "release-owner",
    DDD_AUTH_PERF_BASELINE_ENVIRONMENT: "staging",
    DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT: "artifacts/ddd/performance/authenticated-runtime-actual.json",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /source actual artifact has failed=1/);
  assert.equal(fs.existsSync(output), false);
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-auth-perf-baseline-"));
  const source = path.join(directory, "actual.json");
  const output = path.join(directory, "baseline.json");
  const audit = path.join(directory, "promotion.json");
  writeJson(source, actualArtifact());
  const result = runPromote({
    DDD_AUTH_PERF_BASELINE_SOURCE: source,
    DDD_AUTH_PERF_BASELINE_OUTPUT: output,
    DDD_AUTH_PERF_BASELINE_PROMOTION_OUTPUT: audit,
    DDD_AUTH_PERF_BASELINE_ACCEPTED_BY: "todo",
    DDD_AUTH_PERF_BASELINE_ENVIRONMENT: "staging",
    DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT: "artifacts/ddd/performance/authenticated-runtime-actual.json",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /DDD_AUTH_PERF_BASELINE_ACCEPTED_BY must not contain placeholder text/);
  assert.equal(fs.existsSync(output), false);
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-auth-perf-baseline-"));
  const source = path.join(directory, "actual.json");
  const output = path.join(directory, "baseline.json");
  const audit = path.join(directory, "promotion.json");
  writeJson(source, actualArtifact({ sourceEnvironment: "" }));
  const result = runPromote({
    DDD_AUTH_PERF_BASELINE_SOURCE: source,
    DDD_AUTH_PERF_BASELINE_OUTPUT: output,
    DDD_AUTH_PERF_BASELINE_PROMOTION_OUTPUT: audit,
    DDD_AUTH_PERF_BASELINE_ACCEPTED_BY: "release-owner",
    DDD_AUTH_PERF_BASELINE_ENVIRONMENT: "staging",
    DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT: "artifacts/ddd/performance/authenticated-runtime-actual.json",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /source actual artifact provenance sourceEnvironment is required/);
  assert.equal(fs.existsSync(output), false);
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-auth-perf-baseline-"));
  const source = path.join(directory, "actual.json");
  const output = path.join(directory, "baseline.json");
  const audit = path.join(directory, "promotion.json");
  writeJson(source, actualArtifact({ sourceEnvironment: "qa" }));
  const result = runPromote({
    DDD_AUTH_PERF_BASELINE_SOURCE: source,
    DDD_AUTH_PERF_BASELINE_OUTPUT: output,
    DDD_AUTH_PERF_BASELINE_PROMOTION_OUTPUT: audit,
    DDD_AUTH_PERF_BASELINE_ACCEPTED_BY: "release-owner",
    DDD_AUTH_PERF_BASELINE_ENVIRONMENT: "staging",
    DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT: "artifacts/ddd/performance/authenticated-runtime-actual.json",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /source actual artifact sourceEnvironment qa does not match DDD_AUTH_PERF_BASELINE_ENVIRONMENT staging/);
  assert.equal(fs.existsSync(output), false);
}

console.log("[ddd-promote-performance-baseline.test] ok");
