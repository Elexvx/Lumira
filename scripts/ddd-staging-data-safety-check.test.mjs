#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");

const helpResult = spawnSync("node", ["scripts/ddd-staging-data-safety-check.mjs", "--help"], {
  cwd: repoRoot,
  encoding: "utf8",
});
assert.equal(helpResult.status, 0, helpResult.stderr || helpResult.stdout);
assert.match(helpResult.stdout, /DDD staging data safety check/);
assert.match(helpResult.stdout, /DDD_MIGRATION_FRESH_DB_VALIDATED/);

const blockedResult = spawnSync("node", ["scripts/ddd-staging-data-safety-check.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_ROLLBACK_DRILL_FILE: "",
    DDD_ROLLBACK_DRILL_DEFERRAL_FILE: "",
    DDD_MIGRATION_FRESH_DB_VALIDATED: "",
    DDD_MIGRATION_UPGRADE_DB_VALIDATED: "",
    DDD_EXPLAIN_DATABASE: "",
    MYSQL_DATABASE: "",
    MYSQL_HOST: "",
    MYSQL_PORT: "",
    MYSQL_USER: "",
    MYSQL_PASSWORD: "",
  },
});
assert.notEqual(blockedResult.status, 0);
const blocked = JSON.parse(blockedResult.stdout);
assert.equal(blocked.status, "BLOCKED");
assert.equal(blocked.willWriteFiles, false);
assert.equal(blocked.tracks.rollback.status, "BLOCKED");
assert.equal(blocked.tracks.migration.status, "BLOCKED");
assert.equal(blocked.tracks.explain.status, "BLOCKED");
assert(blocked.issues.some((issue) => issue.includes("rollback-evidence-source")));
assert(blocked.issues.some((issue) => issue.includes("DDD_MIGRATION_FRESH_DB_VALIDATED must be true")));
assert(blocked.issues.some((issue) => issue.includes("DDD_EXPLAIN_DATABASE")));

const passResult = spawnSync("node", ["scripts/ddd-staging-data-safety-check.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_ROLLBACK_DRILL_FILE: "artifacts/ddd/rollback/rollback-drill.json",
    DDD_EVIDENCE_ENVIRONMENT: "staging",
    DDD_RELEASE_CANDIDATE: "release-candidate-sha",
    DDD_EVIDENCE_OPERATOR: "release-owner",
    DDD_MIGRATION_FRESH_DB_VALIDATED: "true",
    DDD_MIGRATION_FRESH_DB_EVIDENCE: "gh-run-12345/fresh-db-flyway-log",
    DDD_MIGRATION_UPGRADE_DB_VALIDATED: "true",
    DDD_MIGRATION_UPGRADE_DB_EVIDENCE: "gh-run-12345/upgrade-db-flyway-log",
    DDD_MIGRATION_COMPLETED_AT: "2026-06-18T00:00:00Z",
    DDD_EXPLAIN_DATABASE: "lumira_staging",
    MYSQL_HOST: "mysql.staging.lumira.internal",
    MYSQL_PORT: "3306",
    MYSQL_USER: "lumira_readonly",
    MYSQL_PASSWORD: "secret-manager-ref/mysql-readonly",
  },
});
assert.equal(passResult.status, 0, passResult.stderr || passResult.stdout);
const pass = JSON.parse(passResult.stdout);
assert.equal(pass.status, "PASS");
assert.equal(pass.tracks.rollback.status, "PASS");
assert.equal(pass.tracks.migration.status, "PASS");
assert.equal(pass.tracks.explain.status, "PASS");
assert(pass.tracks.explain.nextCommands.includes("DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs"));

console.log("[ddd-staging-data-safety-check.test] ok");
