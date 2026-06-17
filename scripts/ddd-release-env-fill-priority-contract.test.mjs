#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function baseArtifacts() {
  return {
    matrix: {
      ownerCount: 2,
      readyOwnerCount: 1,
      templateEnvKeyCount: 3,
      uniqueUnresolvedTemplateKeyCount: 3,
      unresolvedOwnerAssignmentCount: 3,
      groupCount: 2,
      owners: [
        {
          owner: "release-infra",
          readyGroupCount: 1,
          blockedGroupCount: 0,
          unresolvedTemplateKeyCount: 2,
          unresolvedTemplateKeys: ["LUMIRA_BASE_URL", "JWT_SECRET"],
          readyBatchIds: ["p0-release-env-lint-release-infra"],
          blockedBatchIds: [],
          commands: ["node scripts/ddd-release-env-file-lint.mjs"],
          exitCriteria: ["env lint passes"],
        },
        {
          owner: "auth",
          readyGroupCount: 0,
          blockedGroupCount: 1,
          unresolvedTemplateKeyCount: 1,
          unresolvedTemplateKeys: ["DDD_AUTH_USERNAME"],
          readyBatchIds: [],
          blockedBatchIds: ["p1-auth-baseline-auth"],
          commands: ["node scripts/ddd-auth-performance-baseline.mjs"],
          exitCriteria: ["auth baseline captured"],
        },
      ],
    },
    fillPriority: {
      status: "NO_GO_STRICT",
      ownerCount: 2,
      runNowOwnerCount: 1,
      waitingOwnerCount: 1,
      uniqueUnresolvedTemplateKeyCount: 3,
      unresolvedOwnerAssignmentCount: 3,
      filledOwnerAssignmentCount: 1,
      placeholderOwnerAssignmentCount: 1,
      missingOwnerAssignmentCount: 1,
      owners: [
        {
          owner: "release-infra",
          fillOrder: 1,
          priority: "RUN_NOW",
          readyGroupCount: 1,
          blockedGroupCount: 0,
          unresolvedTemplateKeyCount: 2,
          unresolvedTemplateKeys: ["LUMIRA_BASE_URL", "JWT_SECRET"],
          filledTemplateKeyCount: 1,
          placeholderTemplateKeyCount: 1,
          missingTemplateKeyCount: 0,
          filledTemplateKeys: ["LUMIRA_BASE_URL"],
          placeholderTemplateKeys: ["JWT_SECRET"],
          missingTemplateKeys: [],
          fillStatusByKey: [
            { envKey: "LUMIRA_BASE_URL", status: "filled" },
            { envKey: "JWT_SECRET", status: "placeholder" },
          ],
          readyBatchIds: ["p0-release-env-lint-release-infra"],
          blockedBatchIds: [],
          commands: ["node scripts/ddd-release-env-file-lint.mjs"],
          exitCriteria: ["env lint passes"],
        },
        {
          owner: "auth",
          fillOrder: 2,
          priority: "WAITING",
          readyGroupCount: 0,
          blockedGroupCount: 1,
          unresolvedTemplateKeyCount: 1,
          unresolvedTemplateKeys: ["DDD_AUTH_USERNAME"],
          filledTemplateKeyCount: 0,
          placeholderTemplateKeyCount: 0,
          missingTemplateKeyCount: 1,
          filledTemplateKeys: [],
          placeholderTemplateKeys: [],
          missingTemplateKeys: ["DDD_AUTH_USERNAME"],
          fillStatusByKey: [
            { envKey: "DDD_AUTH_USERNAME", status: "missing" },
          ],
          readyBatchIds: [],
          blockedBatchIds: ["p1-auth-baseline-auth"],
          commands: ["node scripts/ddd-auth-performance-baseline.mjs"],
          exitCriteria: ["auth baseline captured"],
        },
      ],
    },
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-env-owner-matrix.json"), `${JSON.stringify(artifacts.matrix, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-fill-priority.json"), `${JSON.stringify(artifacts.fillPriority, null, 2)}\n`);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-fill-priority-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-env-fill-priority-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_DIR: directory },
  });
}

const passResult = runContract();
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /owners=2/);

const badOrderResult = runContract((artifacts) => {
  artifacts.fillPriority.owners.reverse();
  artifacts.fillPriority.owners[0].fillOrder = 1;
  artifacts.fillPriority.owners[1].fillOrder = 2;
});
assert.notEqual(badOrderResult.status, 0);
assert.match(badOrderResult.stderr, /RUN_NOW owners|sorted by priority/);

const matrixMismatchResult = runContract((artifacts) => {
  artifacts.matrix.owners[0].unresolvedTemplateKeys = ["LUMIRA_BASE_URL"];
});
assert.notEqual(matrixMismatchResult.status, 0);
assert.match(matrixMismatchResult.stderr, /release env owner matrix/);

const statusMismatchResult = runContract((artifacts) => {
  artifacts.fillPriority.owners[0].fillStatusByKey[1].status = "filled";
});
assert.notEqual(statusMismatchResult.status, 0);
assert.match(statusMismatchResult.stderr, /placeholderTemplateKeys|filledTemplateKeys/);

const runNowWithoutCommandResult = runContract((artifacts) => {
  artifacts.fillPriority.owners[0].commands = [];
});
assert.notEqual(runNowWithoutCommandResult.status, 0);
assert.match(runNowWithoutCommandResult.stderr, /commands are required/);

const controlKeyResult = runContract((artifacts) => {
  artifacts.fillPriority.owners[0].unresolvedTemplateKeys.push("DDD_RELEASE_ENV_FILE");
  artifacts.fillPriority.owners[0].fillStatusByKey.push({ envKey: "DDD_RELEASE_ENV_FILE", status: "missing" });
  artifacts.fillPriority.owners[0].unresolvedTemplateKeyCount += 1;
  artifacts.fillPriority.missingOwnerAssignmentCount += 1;
  artifacts.fillPriority.unresolvedOwnerAssignmentCount += 1;
});
assert.notEqual(controlKeyResult.status, 0);
assert.match(controlKeyResult.stderr, /control key/);

console.log("[ddd-release-env-fill-priority-contract.test] ok");
