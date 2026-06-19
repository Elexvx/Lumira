#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");

const helpResult = spawnSync("node", ["scripts/ddd-staging-runtime-check.mjs", "--help"], {
  cwd: repoRoot,
  encoding: "utf8",
});
assert.equal(helpResult.status, 0, helpResult.stderr || helpResult.stdout);
assert.match(helpResult.stdout, /DDD staging runtime readiness check/);
assert.match(helpResult.stdout, /LUMIRA_BASE_URL/);

const blockedResult = spawnSync("node", ["scripts/ddd-staging-runtime-check.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    LUMIRA_BASE_URL: "http://127.0.0.1:8080",
    PLAYWRIGHT_BASE_URL: "http://localhost:8000",
    DDD_DEPLOYMENT_EVIDENCE: "",
    DDD_FRONTEND_EXPECT_DEPLOYED: "",
    DDD_AI_EXPECT_PROVIDER_REMOTE: "",
    DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "",
  },
});
assert.notEqual(blockedResult.status, 0);
const blocked = JSON.parse(blockedResult.stdout);
assert.equal(blocked.status, "BLOCKED");
assert.equal(blocked.willWriteFiles, false);
assert(blocked.issues.some((issue) => issue.includes("LUMIRA_BASE_URL must be an HTTPS URL")));
assert(blocked.issues.some((issue) => issue.includes("PLAYWRIGHT_BASE_URL must not be localhost")));
assert(blocked.issues.some((issue) => issue.includes("DDD_FRONTEND_EXPECT_DEPLOYED must be true")));

const passResult = spawnSync("node", ["scripts/ddd-staging-runtime-check.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    LUMIRA_BASE_URL: "https://api.staging.lumira.internal",
    PLAYWRIGHT_BASE_URL: "https://app.staging.lumira.internal",
    DDD_DEPLOYMENT_EVIDENCE: "gh-run-12345/staging-deploy",
    DDD_FRONTEND_DEPLOYMENT_EVIDENCE: "gh-run-12345/frontend",
    DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE: "gh-run-12345/ai-runtime",
    DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE: "gh-run-12345/auth-perf",
    DDD_FRONTEND_EXPECT_DEPLOYED: "true",
    DDD_AI_EXPECT_PROVIDER_REMOTE: "true",
    DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "true",
  },
});
assert.equal(passResult.status, 0, passResult.stderr || passResult.stdout);
const pass = JSON.parse(passResult.stdout);
assert.equal(pass.status, "PASS");
assert.equal(pass.productionEquivalence.backend.https, true);
assert.equal(pass.productionEquivalence.frontend.localOnly, false);
assert(pass.nextCommands.includes("node scripts/ddd-runtime-readiness-smoke.mjs"));

console.log("[ddd-staging-runtime-check.test] ok");
