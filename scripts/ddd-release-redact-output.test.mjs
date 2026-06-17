#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import path from "node:path";
import { redactReleaseOutput } from "./ddd-release-redact-output.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const raw = [
  "JWT_SECRET=super-secret node scripts/ddd-release-evidence-manifest.mjs",
  "OPENAI_API_KEY='real-secret' node scripts/ddd-release-config-evidence.mjs",
  "DDD_RELEASE_ENV_FILE=/tmp/.env.release.local bash artifacts/ddd/release/release-env-bootstrap.sh",
  `report=${path.join(repoRoot, "artifacts/ddd/release/evidence-manifest.json")}`,
  `node scripts/ddd-release-env-canonical-merge.mjs template ${path.join(repoRoot, ".env.release.local")}`,
].join("\n");

const redacted = redactReleaseOutput(raw);
assert.doesNotMatch(redacted, /super-secret/);
assert.doesNotMatch(redacted, /real-secret/);
assert.doesNotMatch(redacted, /DDD_RELEASE_ENV_FILE=\/tmp\/\.env\.release\.local/);
assert.doesNotMatch(redacted, /\.env\.release\.local/);
assert(!redacted.includes(repoRoot), "redacted output must not expose the local repo path");
assert.match(redacted, /JWT_SECRET=<redacted>/);
assert.match(redacted, /OPENAI_API_KEY=<redacted>/);
assert.match(redacted, /DDD_RELEASE_ENV_FILE=<release-env-file>/);
assert.match(redacted, /report=<repo>\/artifacts\/ddd\/release\/evidence-manifest\.json/);
assert.match(redacted, /template <release-env-file>/);

const cli = spawnSync("node", ["scripts/ddd-release-redact-output.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  input: raw,
});
assert.equal(cli.status, 0, cli.stderr);
assert.equal(cli.stdout, redacted);

console.log("[ddd-release-redact-output.test] ok");
