#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { defaultRequiredFrontendFlows } from "./ddd-frontend-smoke-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-frontend-smoke-evidence-"));
const inputFile = path.join(directory, "playwright-smoke-results.json");
const outputFile = path.join(directory, "frontend-smoke.json");
const repoDirectory = path.join(repoRoot, "tmp", `lumira-frontend-smoke-evidence-${process.pid}`);
const repoInputFile = path.join(repoDirectory, "playwright-smoke-results.json");
const repoOutputFile = path.join(repoDirectory, "frontend-smoke.json");

const report = {
  config: {
    projects: [
      {
        name: "chromium",
        use: {
          baseURL: "https://app.staging.lumira.app",
        },
      },
    ],
  },
  suites: [
    {
      title: "frontend smoke",
      specs: defaultRequiredFrontendFlows.map((flow) => ({
        title: `${flow} @smoke`,
        tests: [
          {
            projectName: "chromium",
            results: [
              {
                status: "passed",
                duration: 10,
                errors: [],
              },
            ],
          },
        ],
      })),
    },
  ],
};
fs.writeFileSync(inputFile, `${JSON.stringify(report, null, 2)}\n`);

{
  const result = spawnSync("node", ["scripts/ddd-frontend-smoke-evidence.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_FRONTEND_PLAYWRIGHT_JSON: inputFile,
      DDD_FRONTEND_SMOKE_REPORT: outputFile,
      DDD_RELEASE_EVIDENCE_STRICT: "true",
      DDD_EVIDENCE_ENVIRONMENT: "staging",
      DDD_RELEASE_CANDIDATE: "rc-1",
      DDD_EVIDENCE_OPERATOR: "release-owner",
    },
  });
  assert.notEqual(result.status, 0);
  const artifact = JSON.parse(fs.readFileSync(outputFile, "utf8"));
  assert.equal(artifact.status, "FAIL");
  assert(artifact.blockers.includes("strict release requires DDD_FRONTEND_EXPECT_DEPLOYED=true evidence"));
  assert.equal(artifact.productionEquivalence.strict, true);
  assert.equal(artifact.productionEquivalence.https, true);
  assert.equal(artifact.flowCoverage.length, defaultRequiredFrontendFlows.length);
}

{
  const result = spawnSync("node", ["scripts/ddd-frontend-smoke-evidence.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_FRONTEND_PLAYWRIGHT_JSON: inputFile,
      DDD_FRONTEND_SMOKE_REPORT: outputFile,
      DDD_RELEASE_EVIDENCE_STRICT: "true",
      DDD_FRONTEND_EXPECT_DEPLOYED: "true",
      DDD_FRONTEND_DEPLOYMENT_EVIDENCE: "artifact://deployments/frontend/rc-1",
      DDD_EVIDENCE_ENVIRONMENT: "staging",
      DDD_RELEASE_CANDIDATE: "rc-1",
      DDD_EVIDENCE_OPERATOR: "release-owner",
    },
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const artifact = JSON.parse(fs.readFileSync(outputFile, "utf8"));
  assert.equal(artifact.status, "PASS");
  assert.equal(artifact.productionEquivalence.localOnly, false);
  assert.equal(artifact.flowCoverage.every((entry) => entry.status === "passed"), true);
}

{
  const missingInput = path.join(directory, "missing-playwright.json");
  const missingOutput = path.join(directory, "frontend-smoke-missing.json");
  const result = spawnSync("node", ["scripts/ddd-frontend-smoke-evidence.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_FRONTEND_PLAYWRIGHT_JSON: missingInput,
      DDD_FRONTEND_SMOKE_REPORT: missingOutput,
      DDD_RELEASE_EVIDENCE_STRICT: "true",
      DDD_FRONTEND_EXPECT_DEPLOYED: "true",
      DDD_FRONTEND_DEPLOYMENT_EVIDENCE: "artifact://deployments/frontend/rc-1",
      DDD_EVIDENCE_ENVIRONMENT: "staging",
      DDD_RELEASE_CANDIDATE: "rc-1",
      DDD_EVIDENCE_OPERATOR: "release-owner",
      PLAYWRIGHT_BASE_URL: "https://app.staging.lumira.app",
    },
  });
  assert.notEqual(result.status, 0);
  const artifact = JSON.parse(fs.readFileSync(missingOutput, "utf8"));
  assert.equal(artifact.status, "FAIL");
  assert.equal(artifact.productionEquivalence.strict, true);
  assert.equal(artifact.productionEquivalence.https, true);
  assert.equal(artifact.summary.failed, 0);
  assert.equal(artifact.summary.missingRequiredFlows, defaultRequiredFrontendFlows.length);
  assert(artifact.blockers.some((blocker) => blocker.startsWith("missing Playwright JSON report:")));
  assert.equal(artifact.flowCoverage.length, defaultRequiredFrontendFlows.length);
  assert.equal(artifact.flowCoverage.every((entry) => entry.status === "missing"), true);
  assert.match(artifact.flowCoverage[0].reason, /missing Playwright JSON report/);
  assert(!JSON.stringify(artifact).includes(repoRoot));
  assert.equal(artifact.diagnostics.playwrightReport.present, false);
  assert.equal(artifact.diagnostics.staticSpecCoverage.present, true);
  assert.equal(artifact.diagnostics.staticSpecCoverage.covered, defaultRequiredFrontendFlows.length);
  assert.equal(artifact.diagnostics.staticSpecCoverage.missing, 0);
}

fs.mkdirSync(repoDirectory, { recursive: true });
fs.writeFileSync(repoInputFile, `${JSON.stringify(report, null, 2)}\n`);
try {
  const result = spawnSync("node", ["scripts/ddd-frontend-smoke-evidence.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_FRONTEND_PLAYWRIGHT_JSON: repoInputFile,
      DDD_FRONTEND_SMOKE_REPORT: repoOutputFile,
      DDD_RELEASE_EVIDENCE_STRICT: "true",
      DDD_FRONTEND_EXPECT_DEPLOYED: "true",
      DDD_FRONTEND_DEPLOYMENT_EVIDENCE: "artifact://deployments/frontend/rc-1",
      DDD_EVIDENCE_ENVIRONMENT: "staging",
      DDD_RELEASE_CANDIDATE: "rc-1",
      DDD_EVIDENCE_OPERATOR: "release-owner",
    },
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const artifact = JSON.parse(fs.readFileSync(repoOutputFile, "utf8"));
  assert.equal(artifact.inputFile, path.relative(repoRoot, repoInputFile).replaceAll("\\", "/"));
  assert.equal(artifact.diagnostics.playwrightReport.file, path.relative(repoRoot, repoInputFile).replaceAll("\\", "/"));
  assert.equal(artifact.diagnostics.staticSpecCoverage.file, "frontend/tests/e2e/app.spec.ts");
  assert(!JSON.stringify(artifact).includes(repoRoot));
} finally {
  fs.rmSync(repoDirectory, { recursive: true, force: true });
}

console.log("[ddd-frontend-smoke-evidence.test] ok");
