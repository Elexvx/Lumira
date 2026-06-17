#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import fs from "node:fs";
import http from "node:http";
import os from "node:os";
import path from "node:path";
import { expectedRuntimeReadinessChecks } from "./ddd-runtime-readiness-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const outputDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-runtime-readiness-"));
const repoOutputDir = path.join(repoRoot, "tmp", `lumira-runtime-readiness-${process.pid}`);

const server = http.createServer((request, response) => {
  response.writeHead(200, { "Content-Type": "application/json" });
  response.end(JSON.stringify({
    httpStatus: 200,
    data: {
      path: request.url,
      status: "UP",
    },
  }));
});

await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
try {
  const { port } = server.address();
  const result = await spawnNode(["scripts/ddd-runtime-readiness-smoke.mjs"], {
    cwd: repoRoot,
    env: {
      ...process.env,
      DDD_RUNTIME_READINESS_DIR: outputDir,
      DDD_RELEASE_EVIDENCE_STRICT: "true",
      DDD_EVIDENCE_ENVIRONMENT: "runtime-test",
      DDD_RELEASE_CANDIDATE: "runtime-sha",
      DDD_EVIDENCE_OPERATOR: "runtime-runner",
      LUMIRA_BASE_URL: `http://127.0.0.1:${port}`,
    },
  });

  assert.notEqual(result.status, 0);
  const artifact = JSON.parse(fs.readFileSync(path.join(outputDir, "summary.json"), "utf8"));
  assert.equal(artifact.summary.length, expectedRuntimeReadinessChecks().length);
  assert.equal(artifact.productionEquivalence.strict, true);
  assert.equal(artifact.productionEquivalence.https, false);
  assert.equal(artifact.productionEquivalence.localOnly, true);
  assert(artifact.failures.includes("strict runtime readiness requires HTTPS baseUrl evidence"));
  assert(artifact.failures.some((failure) => failure.includes("strict runtime readiness requires non-local baseUrl")));
} finally {
  await new Promise((resolve) => server.close(resolve));
}

const repoServer = http.createServer((request, response) => {
  response.writeHead(200, { "Content-Type": "application/json" });
  response.end(JSON.stringify({
    httpStatus: 200,
    data: {
      path: request.url,
      status: "UP",
    },
  }));
});

await new Promise((resolve) => repoServer.listen(0, "127.0.0.1", resolve));
try {
  const { port } = repoServer.address();
  const result = await spawnNode(["scripts/ddd-runtime-readiness-smoke.mjs"], {
    cwd: repoRoot,
    env: {
      ...process.env,
      DDD_RUNTIME_READINESS_DIR: repoOutputDir,
      LUMIRA_BASE_URL: `http://127.0.0.1:${port}`,
    },
  });

  assert.equal(result.status, 0, result.stderr || result.stdout);
  const artifact = JSON.parse(fs.readFileSync(path.join(repoOutputDir, "summary.json"), "utf8"));
  assert.equal(artifact.summary.length, expectedRuntimeReadinessChecks().length);
  assert(artifact.summary.every((entry) => !path.isAbsolute(entry.artifact)));
  assert(!JSON.stringify(artifact).includes(repoRoot));
} finally {
  await new Promise((resolve) => repoServer.close(resolve));
  fs.rmSync(repoOutputDir, { recursive: true, force: true });
}

console.log("[ddd-runtime-readiness-smoke.test] ok");

function spawnNode(args, options) {
  return new Promise((resolve) => {
    const child = spawn("node", args, {
      ...options,
      stdio: ["ignore", "pipe", "pipe"],
    });
    let stdout = "";
    let stderr = "";
    child.stdout.on("data", (chunk) => {
      stdout += chunk;
    });
    child.stderr.on("data", (chunk) => {
      stderr += chunk;
    });
    child.on("close", (status) => {
      resolve({ status, stdout, stderr });
    });
  });
}
