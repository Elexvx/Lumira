#!/usr/bin/env node

import assert from "node:assert/strict";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

const run = spawnSync("node", ["scripts/ddd-release-evidence-gate-contract.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
});

assert.equal(run.status, 0, run.stderr || run.stdout);
assert.match(run.stdout, /\[ddd-release-evidence-gate-contract] ok/);

console.log("[ddd-release-evidence-gate-contract.test] ok");
