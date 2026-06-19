#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-env-fill-checklist-"));

try {
  const lintFile = path.join(tmpDir, "release-env-lint.json");
  const configEvidenceFile = path.join(tmpDir, "release-config-evidence.json");
  const markdownOutput = path.join(tmpDir, "fill-checklist.md");
  const jsonOutput = path.join(tmpDir, "fill-keys.json");
  const envTemplateOutput = path.join(tmpDir, "fill.template.env");

  fs.writeFileSync(lintFile, `${JSON.stringify({
    status: "FAIL",
    envFile: ".env.release.local",
    summary: {
      primaryBlockers: 5,
    },
    primaryBlockers: [
      "LUMIRA_BASE_URL: __REQUIRED__ placeholder must be replaced",
      "DB_PASSWORD: __REQUIRED__ placeholder must be replaced",
      "JWT_SECRET: __REQUIRED__ placeholder must be replaced",
      "DDD_DEPLOYMENT_EVIDENCE: __REQUIRED__ placeholder must be replaced",
      "UNOWNED_KEY: __REQUIRED__ placeholder must be replaced",
    ],
  }, null, 2)}\n`);
  fs.writeFileSync(configEvidenceFile, `${JSON.stringify({
    status: "FAIL",
    summary: {
      blockers: 7,
    },
  }, null, 2)}\n`);

  const result = spawnSync("node", [
    "scripts/ddd-release-env-fill-checklist.mjs",
    `--lint-file=${lintFile}`,
    `--config-evidence-file=${configEvidenceFile}`,
    `--markdown-output=${markdownOutput}`,
    `--json-output=${jsonOutput}`,
    `--env-template-output=${envTemplateOutput}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const summary = JSON.parse(result.stdout);
  assert.equal(summary.status, "FAIL");
  assert.equal(summary.envTemplateOutput, envTemplateOutput);
  assert.equal(summary.keyCount, 5);
  assert.equal(summary.primaryBlockerCount, 5);
  assert.equal(summary.configBlockerCount, 7);

  const checklist = JSON.parse(fs.readFileSync(jsonOutput, "utf8"));
  assert.deepEqual(checklist.groups.runtime, ["LUMIRA_BASE_URL"]);
  assert.deepEqual(checklist.groups.database, ["DB_PASSWORD"]);
  assert.deepEqual(checklist.groups.security, ["JWT_SECRET"]);
  assert.deepEqual(checklist.groups.evidence, ["DDD_DEPLOYMENT_EVIDENCE"]);
  assert.deepEqual(checklist.groups.other, ["UNOWNED_KEY"]);

  const markdown = fs.readFileSync(markdownOutput, "utf8");
  assert.match(markdown, /^# P0 Release Env Fill Checklist/m);
  assert.match(markdown, /### runtime/);
  assert.match(markdown, /- LUMIRA_BASE_URL/);
  assert.match(markdown, /DDD_RELEASE_ENV_FILE=.env.release.local node scripts\/ddd-release-env-file-lint\.mjs/);
  assert.match(markdown, /Do not mark `release-infra:p0-release-env` PASS/);

  const envTemplate = fs.readFileSync(envTemplateOutput, "utf8");
  assert.match(envTemplate, /^# P0 release env fill template\./m);
  assert.match(envTemplate, /^LUMIRA_BASE_URL=__REQUIRED_HTTPS__$/m);
  assert.match(envTemplate, /^DB_PASSWORD=__REQUIRED_SECRET_REF__$/m);
  assert.match(envTemplate, /^JWT_SECRET=__REQUIRED_SECRET_REF__$/m);
  assert.match(envTemplate, /^DDD_DEPLOYMENT_EVIDENCE=__REQUIRED_ARTIFACT_PATH_OR_URL__$/m);
  assert.doesNotMatch(envTemplate, /real-/);

  const jsonOnlyResult = spawnSync("node", [
    "scripts/ddd-release-env-fill-checklist.mjs",
    `--lint-file=${lintFile}`,
    `--config-evidence-file=${configEvidenceFile}`,
    "--json",
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(jsonOnlyResult.status, 0, jsonOnlyResult.stderr || jsonOnlyResult.stdout);
  assert.equal(JSON.parse(jsonOnlyResult.stdout).keyCount, 5);

  const envTemplateOnlyResult = spawnSync("node", [
    "scripts/ddd-release-env-fill-checklist.mjs",
    `--lint-file=${lintFile}`,
    `--config-evidence-file=${configEvidenceFile}`,
    "--env-template",
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(envTemplateOnlyResult.status, 0, envTemplateOnlyResult.stderr || envTemplateOnlyResult.stdout);
  assert.match(envTemplateOnlyResult.stdout, /^# P0 release env fill template\./m);
  assert.match(envTemplateOnlyResult.stdout, /^JWT_SECRET=__REQUIRED_SECRET_REF__$/m);

  const commandsResult = spawnSync("node", ["scripts/ddd-staging-execution-checklist.mjs", "--commands"], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
  });
  assert.equal(commandsResult.status, 0, commandsResult.stderr || commandsResult.stdout);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-release-env-fill-checklist\.mjs$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-release-env-fill-checklist\.mjs --markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-release-env-fill-checklist\.mjs --env-template$/m);

  console.log("[ddd-release-env-fill-checklist.test] ok");
} finally {
  fs.rmSync(tmpDir, { recursive: true, force: true });
}
