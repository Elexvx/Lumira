#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const scripts = [
  "scripts/ddd-file-processing-e2e-smoke.mjs",
  "scripts/ddd-payment-webhook-e2e-smoke.mjs",
  "scripts/ddd-job-e2e-smoke.mjs",
];

for (const script of scripts) {
  const source = fs.readFileSync(path.join(repoRoot, script), "utf8");
  assert.match(
    source,
    /const finalizeArtifactStatus = \(artifact\) => \{/,
    `${script} must finalize artifact status from release evidence blockers`,
  );
  assert.match(
    source,
    /\.\.\.\(artifact\.productionEquivalence\?\.issues \|\| \[\]\)/,
    `${script} must include productionEquivalence issues in artifact blockers`,
  );
  assert.match(
    source,
    /status: blockers\.length === 0 \?[^:]+: 'FAIL'/,
    `${script} must fail the artifact when strict production-equivalence blockers exist`,
  );
  assert.match(
    source,
    /blockers,/,
    `${script} must persist blockers in the generated artifact`,
  );
}

console.log("[ddd-business-e2e-smoke-status.test] ok");
