#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-migration-evidence-"));
const outputDir = path.join(directory, "migration");
const handoffFile = path.join(outputDir, "handoff.md");
const reportFile = path.join(outputDir, "migration-evidence.json");

const missingResult = spawnSync("node", ["scripts/ddd-migration-evidence.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_MIGRATION_CHECK_ENV: "true",
    DDD_MIGRATION_EVIDENCE_DIR: outputDir,
    DDD_MIGRATION_EVIDENCE_REPORT: reportFile,
    DDD_MIGRATION_HANDOFF_FILE: handoffFile,
  },
});

assert.notEqual(missingResult.status, 0);
assert.match(missingResult.stderr, /fresh-database-drill missing env\/evidence/);
assert.equal(fs.existsSync(reportFile), false);
const missingHandoff = fs.readFileSync(handoffFile, "utf8");
assert.match(missingHandoff, /Migration Evidence Handoff/);
assert.match(missingHandoff, /DDD_MIGRATION_FRESH_DB_VALIDATED/);
assert.match(missingHandoff, /Fast path:/);
assert.match(missingHandoff, /Owner runbook:/);
assert.match(missingHandoff, /Evidence checklist:/);
assert.match(missingHandoff, /fresh-database-evidence-package/);
assert.match(missingHandoff, /previous-schema-upgrade-evidence-package/);
assert.match(missingHandoff, /\| database \| MISSING \| fresh-database-drill; upgrade-database-drill \|/);
assert.match(missingHandoff, /Validation commands:/);
assert.match(missingHandoff, /DDD_MIGRATION_STRICT=true node scripts\/ddd-migration-evidence\.mjs/);
assert.match(missingHandoff, /DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts\/ddd\/release\/release-final-go-no-go-gate\.sh/);
const missingHandoffJson = JSON.parse(fs.readFileSync(path.join(outputDir, "handoff.json"), "utf8"));
assert.equal(missingHandoffJson.redacted, true);
assert.equal(missingHandoffJson.status, "MISSING");
assert.equal(missingHandoffJson.summary.missing, 6);
assert.equal(missingHandoffJson.fastPath.commands.at(-1), "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh");
assert.deepEqual(missingHandoffJson.evidenceChecklist.map((item) => item.id), [
  "fresh-database-evidence-package",
  "previous-schema-upgrade-evidence-package",
]);
assert(missingHandoffJson.evidenceChecklist[0].requiredArtifacts.some((artifact) => artifact.includes("Flyway migrate log")));
assert(missingHandoffJson.evidenceChecklist[1].acceptanceCriteria.some((criterion) => criterion.includes("previous-schema source")));
assert(missingHandoffJson.ownerRunbook.some((owner) => owner.owner === "database" && owner.status === "MISSING" && owner.missingChecks.includes("fresh-database-drill")));
assert(missingHandoffJson.validationCommands.includes("DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs"));
assert(missingHandoffJson.validationCommands.includes("DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh"));
const missingHandoffCsv = fs.readFileSync(path.join(outputDir, "handoff.csv"), "utf8");
assert.match(missingHandoffCsv, /^owner,check,status,requiredEnvKeys,missingEnvKeys,nextCommand,action/m);
assert.match(missingHandoffCsv, /database,fresh-database-drill,MISSING/);

const readyResult = spawnSync("node", ["scripts/ddd-migration-evidence.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_MIGRATION_CHECK_ENV: "true",
    DDD_MIGRATION_EVIDENCE_DIR: outputDir,
    DDD_MIGRATION_EVIDENCE_REPORT: reportFile,
    DDD_MIGRATION_HANDOFF_FILE: handoffFile,
    DDD_MIGRATION_FRESH_DB_VALIDATED: "true",
    DDD_MIGRATION_FRESH_DB_EVIDENCE: "artifacts/ddd/migration/fresh-db-flyway-log.json",
    DDD_MIGRATION_UPGRADE_DB_VALIDATED: "true",
    DDD_MIGRATION_UPGRADE_DB_EVIDENCE: "artifacts/ddd/migration/upgrade-db-flyway-log.json",
    DDD_MIGRATION_ENVIRONMENT: "prod-equivalent",
    DDD_RELEASE_CANDIDATE: "rc-test",
    DDD_MIGRATION_OPERATOR: "release-owner",
    DDD_MIGRATION_COMPLETED_AT: "2026-06-15T00:00:00.000Z",
  },
});

assert.equal(readyResult.status, 0, readyResult.stderr || readyResult.stdout);
assert.match(readyResult.stdout, /migration evidence env ready/);
assert.equal(fs.existsSync(reportFile), false);
const readyHandoff = fs.readFileSync(handoffFile, "utf8");
assert.match(readyHandoff, /\| database \| fresh-database-drill \| READY \|/);
assert.match(readyHandoff, /\| release-owner \| migration-completed-at \| READY \|/);
const readyHandoffJson = JSON.parse(fs.readFileSync(path.join(outputDir, "handoff.json"), "utf8"));
assert.equal(readyHandoffJson.status, "READY");
assert.equal(readyHandoffJson.summary.ready, 6);
assert.equal(readyHandoffJson.summary.missing, 0);
assert(readyHandoffJson.ownerRunbook.every((owner) => owner.status === "READY"));

console.log("[ddd-migration-evidence.test] ok");
