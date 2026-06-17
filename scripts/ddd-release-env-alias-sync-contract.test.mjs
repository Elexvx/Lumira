#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import assert from "node:assert/strict";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");

const result = spawnSync("node", ["scripts/ddd-release-env-alias-sync-contract.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
});

assert.equal(result.status, 0, result.stderr);
assert.match(result.stdout, /\[ddd-release-env-alias-sync-contract] ok/);

console.log("[ddd-release-env-alias-sync-contract.test] ok");
