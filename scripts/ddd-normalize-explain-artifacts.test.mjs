#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { requiredExplainFilesWhenPresent, validateExplainArtifact } from "./ddd-explain-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const explainDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-explain-normalize-"));
const barePlan = {
  query_block: {
    table: {
      table_name: "platform_event_outbox",
      access_type: "ref",
      key: "idx_platform_event_outbox_owner_queue",
      rows: 12,
    },
  },
};

for (const fileName of requiredExplainFilesWhenPresent) {
  fs.writeFileSync(path.join(explainDir, fileName), `${JSON.stringify(barePlan, null, 2)}\n`);
}

const result = spawnSync("node", ["scripts/ddd-normalize-explain-artifacts.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_EXPLAIN_DIR: explainDir,
    DDD_EVIDENCE_ENVIRONMENT: "normalizer-test",
    DDD_RELEASE_CANDIDATE: "normalizer-sha",
    DDD_EVIDENCE_OPERATOR: "normalizer-runner",
  },
});

assert.equal(result.status, 0, result.stderr || result.stdout);
assert.match(result.stdout, /normalized=8/);

const normalized = JSON.parse(fs.readFileSync(path.join(explainDir, "platform-outbox-owner-relay-message.json"), "utf8"));
assert.equal(normalized.sourceEnvironment, "normalizer-test");
assert.equal(normalized.releaseCandidate, "normalizer-sha");
assert.equal(normalized.evidenceOperator, "normalizer-runner");
assert.equal(normalized.queryName, "platform-outbox-owner-relay-message");
assert.equal(normalized.legacyPlanImport, true);
assert.equal(typeof normalized.sqlSha256, "string");
assert.equal(normalized.sqlSha256.length, 64);
assert.deepEqual(validateExplainArtifact("platform-outbox-owner-relay-message.json", normalized, { strict: false }), []);
assert.deepEqual(validateExplainArtifact("platform-outbox-owner-relay-message.json", normalized, { strict: true }), [{
  scope: "metadata",
  detail: "platform-outbox-owner-relay-message.json.legacyPlanImport must be false for strict release evidence",
}]);

const secondRun = spawnSync("node", ["scripts/ddd-normalize-explain-artifacts.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_EXPLAIN_DIR: explainDir,
    DDD_EVIDENCE_ENVIRONMENT: "normalizer-test",
    DDD_RELEASE_CANDIDATE: "normalizer-sha",
    DDD_EVIDENCE_OPERATOR: "normalizer-runner",
  },
});
assert.equal(secondRun.status, 0, secondRun.stderr || secondRun.stdout);
assert.match(secondRun.stdout, /normalized=0/);

console.log("[ddd-normalize-explain-artifacts.test] ok");
