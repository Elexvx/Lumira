#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify(data, null, 2)}\n`);
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function baseArtifacts({ ready = false } = {}) {
  const placeholders = ready ? 0 : 1;
  const status = ready ? "PASS" : "ADVISORY";
  return {
    readiness: {
      generatedAt: "2026-06-17T00:00:00.000Z",
      status,
      redacted: true,
      envFile: "<release-env-file>",
      summary: {
        blockers: placeholders,
        placeholders,
        missing: 0,
      },
      byOwner: [{
        owner: "release-infra",
        blockers: placeholders,
        placeholders,
        missing: 0,
      }],
    },
    packet: {
      generatedAt: "2026-06-17T00:00:00.000Z",
      status: ready ? "PASS" : "ADVISORY",
      redacted: true,
      valuePolicy: "No concrete environment values are emitted; this packet lists only owner, key, validation, reason, and redacted collection guidance.",
      envFile: "<release-env-file>",
      summary: {
        requiredOwnerInputs: 1,
        ownerCount: 1,
      },
      postCollectionReceipt: {
        redacted: true,
        purpose: "Verify that collected owner values removed release env placeholders without exposing concrete values.",
        commands: [
          "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs",
          "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs",
          "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-alias-sync.mjs",
          "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env",
          "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
          "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
          "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs",
          "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-readiness-summary.mjs",
          "node scripts/ddd-release-config-owner-input-reconciliation.mjs",
          "DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts/ddd/release/release-preflight-gate.sh",
          "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
        ],
        passCriteria: {
          releaseEnvReadinessStatus: "PASS",
          releaseEnvReadinessBlockers: 0,
          releaseEnvReadinessPlaceholders: 0,
          releaseEnvReadinessMissing: 0,
          configOwnerInputReconciliationStatus: "PASS",
          configOwnerInputReconciliationUnmappedKeys: 0,
        },
      },
      owners: [{
        owner: "release-infra",
        totalInputs: 1,
        secretInputs: 1,
        productionEndpointInputs: 0,
        ownerProductionValueInputs: 0,
        packetPath: "artifacts/ddd/release/release-env-owner-input-packet/01-release-infra.json",
        handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/01-release-infra.md",
      }],
      items: [{
        owner: "release-infra",
        canonicalKey: "DB_PASSWORD",
      }],
    },
    reconciliation: {
      generatedAt: "2026-06-17T00:00:00.000Z",
      status: "PASS",
      redacted: true,
      contract: "ddd-release-config-owner-input-reconciliation",
      summary: {
        unmappedConfigPlaceholderKeys: 0,
      },
    },
  };
}

function writeArtifacts(directory, artifacts) {
  writeJson(path.join(directory, "release-env-readiness-redacted.json"), artifacts.readiness);
  writeJson(path.join(directory, "release-env-owner-input-packet.json"), artifacts.packet);
  writeJson(path.join(directory, "release-config-owner-input-reconciliation.json"), artifacts.reconciliation);
}

function runGenerator(mutator = () => {}, env = {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-owner-input-receipt-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  const result = spawnSync("node", ["scripts/ddd-release-owner-input-receipt.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_DIR: directory, ...env },
  });
  return {
    directory,
    result,
    receiptPath: path.join(directory, "release-owner-input-receipt.json"),
    csvPath: path.join(directory, "release-owner-input-receipt.csv"),
    itemCsvPath: path.join(directory, "release-owner-input-receipt-items.csv"),
    itemMarkdownPath: path.join(directory, "release-owner-input-receipt-items.md"),
    markdownPath: path.join(directory, "release-owner-input-receipt.md"),
  };
}

function runContract(directory) {
  return spawnSync("node", ["scripts/ddd-release-owner-input-receipt-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_DIR: directory },
  });
}

const pending = runGenerator();
assert.equal(pending.result.status, 0, pending.result.stderr);
const pendingReceipt = readJson(pending.receiptPath);
assert.equal(pendingReceipt.status, "PENDING_OWNER_INPUT");
assert.equal(pendingReceipt.cutoverReady, false);
assert.equal(pendingReceipt.summary.itemReceiptCount, 1);
assert.equal(pendingReceipt.itemReceipts.length, 1);
assert.deepEqual(pendingReceipt.missingCriteria.sort(), [
  "releaseEnvReadinessBlockers",
  "releaseEnvReadinessPlaceholders",
  "releaseEnvReadinessStatus",
].sort());
const pendingCsv = fs.readFileSync(pending.csvPath, "utf8");
assert.match(pendingCsv, /owner,ready,requiredOwnerInputs/);
assert.match(pendingCsv, /release-infra,false,1/);
assert.match(pendingCsv, /PENDING_OWNER_INPUT/);
assert.doesNotMatch(pendingCsv, /DB_PASSWORD=|__REQUIRED__|\.env\.release\.local/);
const pendingItemCsv = fs.readFileSync(pending.itemCsvPath, "utf8");
assert.match(pendingItemCsv, /inputOrder,fillOrder,owner,ownerReady,canonicalKey/);
assert.match(pendingItemCsv, /release-infra,false,DB_PASSWORD/);
assert.doesNotMatch(pendingItemCsv, /DB_PASSWORD=|__REQUIRED__|\.env\.release\.local/);
const pendingItemMarkdown = fs.readFileSync(pending.itemMarkdownPath, "utf8");
assert.match(pendingItemMarkdown, /^# DDD Release Owner Input Receipt Items/m);
assert.match(pendingItemMarkdown, /\[ \] 0\. `DB_PASSWORD`|\[ \] 1\. `DB_PASSWORD`/);
assert.doesNotMatch(pendingItemMarkdown, /DB_PASSWORD=|__REQUIRED__|\.env\.release\.local/);
assert.match(fs.readFileSync(pending.markdownPath, "utf8"), /Concrete values are intentionally omitted/);
assert.equal(runContract(pending.directory).status, 0);

const pass = runGenerator((artifacts) => {
  Object.assign(artifacts, baseArtifacts({ ready: true }));
});
assert.equal(pass.result.status, 0, pass.result.stderr);
const passReceipt = readJson(pass.receiptPath);
assert.equal(passReceipt.status, "PASS");
assert.equal(passReceipt.cutoverReady, true);
assert.deepEqual(passReceipt.missingCriteria, []);
assert.match(fs.readFileSync(pass.csvPath, "utf8"), /release-infra,true,1/);
assert.equal(runContract(pass.directory).status, 0);

const leak = runGenerator();
const leakedReceipt = readJson(leak.receiptPath);
leakedReceipt.validationCommands.push("DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs");
writeJson(leak.receiptPath, leakedReceipt);
const leakContract = runContract(leak.directory);
assert.notEqual(leakContract.status, 0);
assert.match(leakContract.stderr, /redact DDD_RELEASE_ENV_FILE|concrete env/);

console.log("[ddd-release-owner-input-receipt.test] ok");
