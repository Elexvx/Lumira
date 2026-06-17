#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-migration-downgrade-"));
const report = path.join(directory, "migration-evidence.json");

const existingArtifact = {
  generatedAt: "2026-06-14T00:00:00.000Z",
  status: "PASS",
  runtime: {
    freshDatabaseValidated: true,
    upgradeDatabaseValidated: true,
    environment: "staging-prod-equivalent",
    releaseCandidate: "rc-20260614",
    operator: "release-owner",
    completedAt: "2026-06-14T01:00:00.000Z",
    freshDatabaseEvidence: "artifacts/ddd/migration/fresh-db-flyway-schema-history.json",
    upgradeDatabaseEvidence: "artifacts/ddd/migration/upgrade-db-flyway-schema-history.json",
  },
  summary: {
    locations: 0,
    migrationFiles: 0,
    duplicateVersionLocations: 0,
    emptyFiles: 0,
  },
  locations: [],
  blockers: [],
};
fs.writeFileSync(report, `${JSON.stringify(existingArtifact, null, 2)}\n`);

const blocked = spawnSync("node", ["scripts/ddd-migration-evidence.mjs"], {
  cwd: path.resolve(import.meta.dirname, ".."),
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_MIGRATION_EVIDENCE_REPORT: report,
    DDD_MIGRATION_ENVIRONMENT: "",
    DDD_EVIDENCE_ENVIRONMENT: "",
    DDD_RELEASE_ENVIRONMENT: "",
    DDD_RELEASE_CANDIDATE: "",
    GITHUB_SHA: "",
    DDD_MIGRATION_OPERATOR: "",
    DDD_EVIDENCE_OPERATOR: "",
    GITHUB_ACTOR: "",
    DDD_MIGRATION_COMPLETED_AT: "",
  },
});

assert.notEqual(blocked.status, 0);
assert.match(blocked.stderr, /refusing to overwrite existing fresh database migration validation/);
assert.match(blocked.stderr, /refusing to overwrite existing migration environment with an empty value/);
assert.match(blocked.stderr, /refusing to overwrite existing migration release candidate with an empty value/);
assert.match(blocked.stderr, /refusing to overwrite existing migration operator with an empty value/);
assert.match(blocked.stderr, /refusing to overwrite existing migration completedAt with an empty value/);
assert.deepEqual(JSON.parse(fs.readFileSync(report, "utf8")).runtime, existingArtifact.runtime);

const allowed = spawnSync("node", ["scripts/ddd-migration-evidence.mjs"], {
  cwd: path.resolve(import.meta.dirname, ".."),
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_MIGRATION_EVIDENCE_REPORT: report,
    DDD_MIGRATION_ENVIRONMENT: "local-dev",
    DDD_RELEASE_CANDIDATE: "local-worktree",
    DDD_MIGRATION_OPERATOR: "codex",
    DDD_MIGRATION_COMPLETED_AT: "2026-06-14T02:00:00.000Z",
    DDD_MIGRATION_ALLOW_EVIDENCE_DOWNGRADE: "true",
  },
});

assert.notEqual(allowed.status, 0);
assert.match(allowed.stderr, /fresh-database migration drill is not validated with concrete evidence/);
const downgraded = JSON.parse(fs.readFileSync(report, "utf8"));
assert.equal(downgraded.status, "FAIL");
assert.equal(downgraded.runtime.freshDatabaseValidated, false);
assert.equal(downgraded.runtime.upgradeDatabaseValidated, false);
assert.equal(downgraded.runtime.freshDatabaseEvidence, "");
assert.equal(downgraded.runtime.upgradeDatabaseEvidence, "");
assert.equal(downgraded.runtimeReady, false);
assert.equal(downgraded.summary.runtimeReady, false);
assert.equal(downgraded.runtimeProofs.find((proof) => proof.id === "fresh-database").validated, false);
assert.deepEqual(downgraded.runtimeProofs.find((proof) => proof.id === "upgrade-database").requiredEnvKeys, [
  "DDD_MIGRATION_UPGRADE_DB_VALIDATED",
  "DDD_MIGRATION_UPGRADE_DB_EVIDENCE",
]);
assert.equal(downgraded.runtimeDiagnostics.find((entry) => entry.id === "fresh-database-drill").owner, "database");
assert.deepEqual(downgraded.runtimeDiagnostics.find((entry) => entry.id === "upgrade-database-drill").envKeys, [
  "DDD_MIGRATION_UPGRADE_DB_VALIDATED",
  "DDD_MIGRATION_UPGRADE_DB_EVIDENCE",
]);

console.log("[ddd-migration-evidence-downgrade.test] ok");
