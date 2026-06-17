#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function baseArtifacts() {
  const item = {
    fillOrder: 1,
    owner: "release-infra",
    owners: ["release-infra"],
    group: "runtime",
    requirement: "database password",
    canonicalKey: "DB_PASSWORD",
    required: true,
    secret: true,
    valueClass: "secret",
    safeToPreFill: false,
    validation: {
      https: false,
      nonLocal: false,
      minLength: 16,
      expectedValues: [],
      pattern: null,
      disallowValues: [],
    },
    aliases: ["DB_PASSWORD", "MYSQL_PASSWORD"],
    fillGuidance: "Provide via approved secret manager or secure release channel; never commit.",
  };
  return {
    envLint: {
      envFile: ".env.release.local",
      keys: ["DB_PASSWORD"],
      unresolvedTemplateKeys: ["DB_PASSWORD"],
    },
    canonicalFill: {
      envFile: ".env.release.local",
      canonicalFillItemCount: 1,
      items: [item],
    },
    readiness: {
      generatedAt: "2026-06-15T00:00:00.000Z",
      status: "NOT_READY",
      redacted: true,
      valuePolicy: "No concrete environment values are emitted; only key names, ownership, validation metadata, and redacted fill status.",
      envFile: ".env.release.local",
      summary: {
        totalCanonicalKeys: 1,
        filledRedacted: 0,
        placeholders: 1,
        missing: 0,
        optionalEmpty: 0,
        blockers: 1,
        secretKeys: 1,
        ownerCount: 1,
        blockingSafeDefaultAvailable: 0,
        blockingRequiresOwnerInput: 1,
        safeDefaultsExhausted: true,
        ownerInputReasonCounts: {
          "secret-manager": 1,
        },
      },
      byOwner: [{
        owner: "release-infra",
        total: 1,
        filled: 0,
        placeholder: 1,
        missing: 0,
        optionalEmpty: 0,
        blockers: 1,
        secretKeys: 1,
        safeDefaultAvailable: 0,
        requiresOwnerInput: 1,
      }],
      items: [{
        ...item,
        status: "PLACEHOLDER",
        blocker: true,
        safeDefaultAvailable: false,
        requiresOwnerInput: true,
        ownerInputReason: "secret-manager",
      }],
    },
    csv: [
      "fillOrder,owner,owners,group,requirement,canonicalKey,status,required,secret,valueClass,safeToPreFill,safeDefaultAvailable,requiresOwnerInput,ownerInputReason,blocker,aliases,https,nonLocal,minLength,expectedValues,pattern,disallowValues,fillGuidance",
      "1,release-infra,release-infra,runtime,database password,DB_PASSWORD,PLACEHOLDER,true,true,secret,false,false,true,secret-manager,true,DB_PASSWORD;MYSQL_PASSWORD,false,false,16,,,,Provide via approved secret manager or secure release channel; never commit.",
      "",
    ].join("\n"),
    markdown: [
      "# DDD Release Env Readiness Redacted",
      "",
      "Concrete values are intentionally omitted.",
      "",
    ].join("\n"),
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-env-lint.json"), `${JSON.stringify(artifacts.envLint, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-canonical-fill.json"), `${JSON.stringify(artifacts.canonicalFill, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-readiness-redacted.json"), `${JSON.stringify(artifacts.readiness, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-readiness-redacted.csv"), artifacts.csv);
  fs.writeFileSync(path.join(directory, "release-env-readiness-redacted.md"), artifacts.markdown);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-readiness-redacted-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-env-readiness-redacted-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_DIR: directory },
  });
}

const passResult = runContract();
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /items=1/);

const leakAssignmentResult = runContract((artifacts) => {
  artifacts.csv += "DB_PASSWORD=super-secret\n";
});
assert.notEqual(leakAssignmentResult.status, 0);
assert.match(leakAssignmentResult.stderr, /must not expose/);

const summaryMismatchResult = runContract((artifacts) => {
  artifacts.readiness.summary.placeholders = 0;
});
assert.notEqual(summaryMismatchResult.status, 0);
assert.match(summaryMismatchResult.stderr, /summary.placeholders/);

const canonicalMismatchResult = runContract((artifacts) => {
  artifacts.readiness.items[0].safeToPreFill = true;
});
assert.notEqual(canonicalMismatchResult.status, 0);
assert.match(canonicalMismatchResult.stderr, /safeToPreFill/);

const ownerMismatchResult = runContract((artifacts) => {
  artifacts.readiness.byOwner[0].blockers = 0;
});
assert.notEqual(ownerMismatchResult.status, 0);
assert.match(ownerMismatchResult.stderr, /owner release-infra.blockers/);

const statusMismatchResult = runContract((artifacts) => {
  artifacts.readiness.items[0].status = "FILLED_REDACTED";
  artifacts.readiness.summary.filledRedacted = 1;
  artifacts.readiness.summary.placeholders = 0;
  artifacts.readiness.byOwner[0].filled = 1;
  artifacts.readiness.byOwner[0].placeholder = 0;
});
assert.notEqual(statusMismatchResult.status, 0);
assert.match(statusMismatchResult.stderr, /status must derive/);

console.log("[ddd-release-env-readiness-redacted-contract.test] ok");
