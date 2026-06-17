#!/usr/bin/env node

import assert from "node:assert/strict";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

const run = spawnSync("node", ["scripts/ddd-release-env-owner-templates-merge-contract.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
});

assert.equal(run.status, 0, run.stderr || run.stdout);
assert.match(run.stdout, /\[ddd-release-env-owner-templates-merge-contract] ok/);

console.log("[ddd-release-env-owner-templates-merge-contract.test] ok");
