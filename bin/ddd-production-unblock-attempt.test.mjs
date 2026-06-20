#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const scriptFile = path.join(repoRoot, "bin", "ddd-production-unblock-attempt.mjs");
const scriptText = fs.readFileSync(scriptFile, "utf8");

assert(
  scriptText.includes('const releaseEnvLintAttemptFile = path.join(outputDir, "release-env-lint.attempt.json");'),
  "production unblock attempt must keep scaffold lint output inside the attempt directory",
);
assert(
  scriptText.includes("DDD_RELEASE_ENV_LINT_REPORT: releaseEnvLintAttemptFile"),
  "production unblock attempt must not overwrite the canonical release-env-lint.json artifact",
);
assert(
  scriptText.includes("if (fs.existsSync(releaseEnvLintAttemptFile)) createdArtifacts.push(rel(releaseEnvLintAttemptFile));"),
  "production unblock attempt must list its attempt lint report as a created artifact",
);
assert(
  scriptText.includes("This is a scaffold, not production evidence"),
  "production unblock attempt must label generated env files as scaffolds rather than production evidence",
);
assert(
  scriptText.includes('finalRecommendation || "NO_GO_STRICT"'),
  "production unblock attempt must default to NO_GO_STRICT when strict readiness cannot prove go",
);

console.log("[ddd-production-unblock-attempt.test] ok");
