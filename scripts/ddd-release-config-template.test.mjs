#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const outputDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-config-template-"));
const templateFile = path.join(repoRoot, "docs", "36-ddd-release-env-template.env");

const result = spawnSync("node", ["scripts/ddd-release-config-evidence.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_CONFIG_DIR: outputDir,
    DDD_RELEASE_ENV_FILE: templateFile,
    DDD_RELEASE_CONFIG_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "template-contract-test",
    DDD_RELEASE_CANDIDATE: "template-contract-sha",
    DDD_EVIDENCE_OPERATOR: "template-contract-runner",
  },
});

assert.notEqual(result.status, 0, "placeholder template must not pass strict release config evidence");

const artifact = JSON.parse(fs.readFileSync(path.join(outputDir, "release-config-evidence.json"), "utf8"));
const templateText = fs.readFileSync(templateFile, "utf8");
assert.equal(artifact.status, "FAIL");
assert.equal(artifact.envFile, templateFile);
assert.equal(artifact.summary.checks, 48);
for (const key of ["DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_CANDIDATE", "DDD_EVIDENCE_OPERATOR"]) {
  assert.match(templateText, new RegExp(`^${key}=`, "m"), `release env template must include ${key}`);
}
assert.ok(artifact.blockers.length > 0, "template placeholders should produce blockers");
assert.ok(
  artifact.blockers.some((blocker) => blocker.includes("placeholder value is not allowed")
    || blocker.includes("must not contain placeholder text")),
  "template should fail because placeholder values are rejected",
);
assert.equal(
  artifact.blockers.some((blocker) => /\bmissing\b/.test(blocker)),
  false,
  "release env template should define all required config keys; failures should be value quality, not missing coverage",
);
assert.deepEqual(artifact.warnings, []);

console.log("[ddd-release-config-template.test] ok");
