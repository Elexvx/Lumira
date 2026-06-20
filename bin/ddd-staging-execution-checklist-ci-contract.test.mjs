#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-staging-checklist-ci-"));

function runChecklist(args, env = {}) {
  return spawnSync(process.execPath, ["bin/ddd-staging-execution-checklist.mjs", ...args], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
    env: {
      ...process.env,
      GITHUB_ACTIONS: "",
      GITHUB_SHA: "",
      GITHUB_EVENT_NAME: "",
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, `check-${args[0].replace(/^--/u, "")}`),
      ...env,
    },
  });
}

function parseJson(result, allowedStatuses = [0]) {
  assert(allowedStatuses.includes(result.status), result.stderr || result.stdout);
  return JSON.parse(result.stdout);
}

try {
  const help = runChecklist(["--help"]);
  assert.equal(help.status, 0, help.stderr || help.stdout);
  assert.match(help.stdout, /Usage:/);

  const rollup = parseJson(runChecklist(["--rollup"]));
  assert(["PASS", "BLOCKED"].includes(rollup.status));
  assert.equal(rollup.willWriteFiles, false);
  assert(Array.isArray(rollup.items));
  assert(rollup.items.some((item) => item.id === "release-env"));
  assert(rollup.items.some((item) => item.id === "docker-images"));
  assert(rollup.items.some((item) => item.id === "runtime-business"));
  assert.equal(rollup.blockedCount, rollup.items.filter((item) => item.status === "BLOCKED").length);

  const dispatch = parseJson(runChecklist(["--dispatch-check"]), [0, 1]);
  assert(["PASS", "BLOCKED"].includes(dispatch.status));
  assert.equal(dispatch.willWriteFiles, false);
  assert(Array.isArray(dispatch.nextCommands));
  assert(dispatch.nextCommands.includes("node bin/ddd-staging-runtime-check.mjs"));
  assert(dispatch.nextCommands.includes("node bin/ddd-staging-data-safety-check.mjs"));

  const commands = runChecklist(["--commands"]);
  assert.equal(commands.status, 0, commands.stderr || commands.stdout);
  assert.match(commands.stdout, /ddd-staging-execution-checklist/);

  const nextActionTemplate = runChecklist(["--next-action-env-template"]);
  assert.equal(nextActionTemplate.status, 0, nextActionTemplate.stderr || nextActionTemplate.stdout);
  assert.match(nextActionTemplate.stdout, /DDD_RELEASE_CANDIDATE/);
  assert.match(nextActionTemplate.stdout, /DDD_EVIDENCE_OPERATOR/);

  const handoffDir = path.join(tmpDir, "handoff-bundle");
  const handoff = runChecklist(["--handoff-bundle"], {
    DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffDir,
  });
  assert.equal(handoff.status, 0, handoff.stderr || handoff.stdout);
  assert.match(handoff.stdout, /handoffBundle=/);
  for (const file of ["README.md", "rollup.json", "commands.txt", "manifest.json"]) {
    assert.equal(fs.existsSync(path.join(handoffDir, file)), true, `handoff bundle should write ${file}`);
  }

  console.log("[ddd-staging-execution-checklist-ci-contract.test] ok");
} finally {
  fs.rmSync(tmpDir, { recursive: true, force: true });
}
