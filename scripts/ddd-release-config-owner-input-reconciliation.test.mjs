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

function baseArtifacts() {
  return {
    config: {
      generatedAt: "2026-06-17T00:00:00.000Z",
      status: "FAIL",
      summary: {
        releaseConfigBlockersFromPlaceholders: 3,
      },
      blockerDetails: [
        {
          blocker: "runtime.database password: must not contain placeholder text",
          group: "runtime",
          owner: "release-infra",
          check: "database password",
          matchedKey: "DB_PASSWORD",
          blockedByPlaceholderKey: true,
        },
        {
          blocker: "runtime.database password duplicate: must not contain placeholder text",
          group: "runtime",
          owner: "release-infra",
          check: "database password",
          matchedKey: "DB_PASSWORD",
          blockedByPlaceholderKey: true,
        },
        {
          blocker: "runtime.database username: must not contain placeholder text",
          group: "runtime",
          owner: "release-infra",
          check: "database username",
          matchedKey: "MYSQL_USER",
          blockedByPlaceholderKey: true,
        },
      ],
    },
    packet: {
      generatedAt: "2026-06-17T00:00:00.000Z",
      redacted: true,
      summary: {
        requiredOwnerInputs: 2,
      },
      items: [
        {
          canonicalKey: "DB_PASSWORD",
          aliases: ["MYSQL_PASSWORD"],
          owner: "release-infra",
          ownerInputReason: "secret-manager",
          valueClass: "secret",
        },
        {
          canonicalKey: "DB_USERNAME",
          aliases: ["MYSQL_USER"],
          owner: "release-infra",
          ownerInputReason: "owner-production-value",
          valueClass: "identifier",
        },
      ],
    },
  };
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-config-owner-input-reconciliation-"));
  const releaseDir = path.join(directory, "release");
  const configPath = path.join(directory, "config", "release-config-evidence.json");
  const reportPath = path.join(releaseDir, "release-config-owner-input-reconciliation.json");
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeJson(configPath, artifacts.config);
  writeJson(path.join(releaseDir, "release-env-owner-input-packet.json"), artifacts.packet);
  const result = spawnSync("node", ["scripts/ddd-release-config-owner-input-reconciliation.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_DIR: releaseDir,
      DDD_RELEASE_CONFIG_REPORT: configPath,
      DDD_RELEASE_CONFIG_OWNER_INPUT_RECONCILIATION_REPORT: reportPath,
    },
  });
  return {
    result,
    reportPath,
    report: fs.existsSync(reportPath) ? JSON.parse(fs.readFileSync(reportPath, "utf8")) : null,
  };
}

const pass = runContract();
assert.equal(pass.result.status, 0, pass.result.stderr);
assert.match(pass.result.stdout, /configPlaceholderKeys=2/);
assert.equal(pass.report.status, "PASS");
assert.equal(pass.report.summary.configPlaceholderBlockers, 3);
assert.equal(pass.report.summary.uniqueConfigPlaceholderKeys, 2);
assert.equal(pass.report.summary.mappedConfigPlaceholderKeys, 2);
assert.equal(pass.report.summary.unmappedConfigPlaceholderKeys, 0);
assert.equal(pass.report.summary.duplicateConfigPlaceholderBlockers, 1);
assert.equal(pass.report.summary.ownerInputsWithoutConfigPlaceholder, 0);
assert.deepEqual(pass.report.unmappedConfigPlaceholderKeys, []);

const missingOwnerInput = runContract((artifacts) => {
  artifacts.packet.items = artifacts.packet.items.filter((item) => item.canonicalKey !== "DB_USERNAME");
});
assert.notEqual(missingOwnerInput.result.status, 0);
assert.match(missingOwnerInput.result.stderr, /config placeholder keys must be covered/);
assert.equal(missingOwnerInput.report.status, "FAIL");
assert.deepEqual(missingOwnerInput.report.unmappedConfigPlaceholderKeys, ["MYSQL_USER"]);

const summaryMismatch = runContract((artifacts) => {
  artifacts.config.summary.releaseConfigBlockersFromPlaceholders = 2;
});
assert.notEqual(summaryMismatch.result.status, 0);
assert.match(summaryMismatch.result.stderr, /placeholder blocker summary/);

const unredactedPacket = runContract((artifacts) => {
  artifacts.packet.redacted = false;
});
assert.notEqual(unredactedPacket.result.status, 0);
assert.match(unredactedPacket.result.stderr, /owner input packet must be redacted/);

console.log("[ddd-release-config-owner-input-reconciliation.test] ok");
