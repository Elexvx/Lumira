#!/usr/bin/env node

import assert from "node:assert/strict";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

const result = spawnSync("node", ["scripts/ddd-release-env-canonical-lint-contract.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
});

assert.equal(result.status, 0, result.stderr);
assert.match(result.stdout, /\[ddd-release-env-canonical-lint-contract] ok/);

console.log("[ddd-release-env-canonical-lint-contract.test] ok");
