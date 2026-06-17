#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function baseArtifacts() {
  const group = {
    batchId: "p0-release-env-lint-release-infra",
    envCheckGroups: ["LUMIRA_BASE_URL=LUMIRA_BASE_URL"],
    expectedArtifacts: ["artifacts/ddd/release/release-env-lint.json"],
    exitCriteria: ["env lint passes"],
  };
  return {
    missingEnv: {
      groupCount: 1,
      templateEnvKeyCount: 2,
      templateEnvKeys: ["LUMIRA_BASE_URL", "JWT_SECRET"],
      uniqueEnvKeys: ["LUMIRA_BASE_URL", "JWT_SECRET"],
      templateAliasMappings: [{ alias: "DEPLOY_CHECK_BASE_URL", canonical: "LUMIRA_BASE_URL" }],
    },
    matrix: {
      ownerCount: 1,
      readyOwnerCount: 1,
      templateEnvKeyCount: 2,
      uniqueUnresolvedTemplateKeyCount: 2,
      unresolvedOwnerAssignmentCount: 2,
      groupCount: 1,
      owners: [{
        owner: "release-infra",
        groupCount: 1,
        readyGroupCount: 1,
        blockedGroupCount: 0,
        templateEnvKeys: ["LUMIRA_BASE_URL", "JWT_SECRET"],
        unresolvedTemplateKeys: ["LUMIRA_BASE_URL", "JWT_SECRET"],
        aliasMappings: [{ alias: "DEPLOY_CHECK_BASE_URL", canonical: "LUMIRA_BASE_URL" }],
        batchIds: [group.batchId],
        readyBatchIds: [group.batchId],
        blockedBatchIds: [],
        groups: [group],
      }],
    },
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-env-owner-matrix.json"), `${JSON.stringify(artifacts.matrix, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-missing.json"), `${JSON.stringify(artifacts.missingEnv, null, 2)}\n`);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-owner-matrix-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-env-owner-matrix-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_DIR: directory },
  });
}

const passResult = runContract();
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /owners=1/);

const missingTemplateKeyResult = runContract((artifacts) => {
  artifacts.matrix.owners[0].templateEnvKeys = ["LUMIRA_BASE_URL"];
});
assert.notEqual(missingTemplateKeyResult.status, 0);
assert.match(missingTemplateKeyResult.stderr, /templateEnvKeys/);

const badGroupCountResult = runContract((artifacts) => {
  artifacts.matrix.owners[0].groupCount = 2;
});
assert.notEqual(badGroupCountResult.status, 0);
assert.match(badGroupCountResult.stderr, /groupCount/);

const controlKeyResult = runContract((artifacts) => {
  artifacts.matrix.owners[0].templateEnvKeys.push("DDD_RELEASE_ENV_FILE");
});
assert.notEqual(controlKeyResult.status, 0);
assert.match(controlKeyResult.stderr, /control key/);

const missingGroupMetadataResult = runContract((artifacts) => {
  artifacts.matrix.owners[0].groups[0].expectedArtifacts = [];
});
assert.notEqual(missingGroupMetadataResult.status, 0);
assert.match(missingGroupMetadataResult.stderr, /expectedArtifacts/);

console.log("[ddd-release-env-owner-matrix-contract.test] ok");
