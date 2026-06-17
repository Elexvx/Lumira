#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import fs from "node:fs";
import http from "node:http";
import os from "node:os";
import path from "node:path";
import { validateAiRuntimeArtifact } from "./ddd-ai-runtime-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const outputDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-ai-runtime-drill-"));

const result = spawnSync("node", ["scripts/ddd-ai-runtime-drill.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_AI_RUNTIME_DRILL_DIR: outputDir,
    LUMIRA_AI_BASE_URL: "http://127.0.0.1:1",
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_AI_EXPECT_PROVIDER_REMOTE: "true",
    DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "true",
    DDD_EVIDENCE_ENVIRONMENT: "ai-runtime-test",
    DDD_RELEASE_CANDIDATE: "ai-runtime-sha",
    DDD_EVIDENCE_OPERATOR: "ai-runtime-runner",
  },
});

assert.notEqual(result.status, 0);

const artifact = JSON.parse(fs.readFileSync(path.join(outputDir, "ai-runtime-drill.json"), "utf8"));
assert.equal(artifact.status, "FAIL");
assert.equal(artifact.failures.length, artifact.failureDetails.length);
assert(artifact.failureDetails.some((failure) => failure.category === "endpoint"));
assert(!artifact.failureDetails.some((failure) => failure.category === "api-contract"));
assert(!artifact.failureDetails.some((failure) => failure.category === "health"));
assert(!artifact.failureDetails.some((failure) => failure.category === "metrics"));
assert.equal(artifact.summary.failureCategories.endpoint, 1);
assert.equal(artifact.sourceEnvironment, "ai-runtime-test");
assert.equal(artifact.releaseCandidate, "ai-runtime-sha");
assert.equal(artifact.evidenceOperator, "ai-runtime-runner");
assert.equal(artifact.productionEquivalence.strict, true);
assert.equal(artifact.productionEquivalence.https, false);
assert.equal(artifact.productionEquivalence.localOnly, true);
assert(artifact.productionEquivalence.issues.includes("strict AI runtime drill requires HTTPS baseUrl evidence"));
assert.deepEqual(validateAiRuntimeArtifact(artifact, { strict: false }), []);

const server = http.createServer((request, response) => {
  response.setHeader("Content-Type", "application/json");
  response.end(JSON.stringify({
    httpStatus: 200,
    data: {
      apiContracts: [
        "/api/v2/ai/chat",
        "/api/v2/ai/knowledge-bases/search",
        "/api/v2/ai/tools/execute",
      ],
      healthChecks: [
        {
          name: "ai.provider-runtime",
          status: "DOWN",
          description: "provider=lumira-local, chatModel=local-chat, embeddingModel=local-embedding, remoteConfigured=false",
        },
        {
          name: "ai.remote-owner-gateway",
          status: "DOWN",
          description: "Configured owners=[]",
        },
      ],
      metrics: [
        { name: "ai.provider.remote_configured", value: 0 },
        { name: "ai.owner_gateway.configured", value: 0 },
      ],
      path: request.url,
    },
  }));
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
try {
  const contractOutputDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-ai-runtime-contract-"));
  const { port } = server.address();
  const contractResult = await spawnNode(["scripts/ddd-ai-runtime-drill.mjs"], {
    cwd: repoRoot,
    env: {
      ...process.env,
      DDD_AI_RUNTIME_DRILL_DIR: contractOutputDir,
      LUMIRA_AI_BASE_URL: `http://127.0.0.1:${port}`,
      DDD_AI_EXPECT_PROVIDER_REMOTE: "true",
      DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "true",
    },
  });
  assert.notEqual(contractResult.status, 0);
  const contractArtifact = JSON.parse(fs.readFileSync(path.join(contractOutputDir, "ai-runtime-drill.json"), "utf8"));
  assert(contractArtifact.failureDetails.some((failure) => failure.category === "provider-runtime"));
  assert(contractArtifact.failureDetails.some((failure) => failure.category === "owner-gateway"));
  assert(contractArtifact.failureDetails.some((failure) => failure.message.includes("ai.remote-owner-gateway") && failure.owner === "ai-owner-integrations"));
  assert.equal(contractArtifact.productionEquivalence.strict, false);
  assert.equal(contractArtifact.productionEquivalence.localOnly, true);
  assert.deepEqual(validateAiRuntimeArtifact(contractArtifact, { strict: false }), []);
} finally {
  await new Promise((resolve) => server.close(resolve));
}

console.log("[ddd-ai-runtime-drill.test] ok");

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
