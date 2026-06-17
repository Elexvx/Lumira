#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function baseArtifacts() {
  const releaseEnvFile = {
    ready: false,
    status: "FAIL",
    inputKind: "release-env-file",
    envFilePresent: true,
    generatedMissingTemplate: false,
    securityChecked: true,
    permissionSafe: true,
    permissionCheckSkipped: false,
    modeOctal: "600",
    requiredMode: "600",
  };
  const checklist = [
    { id: "strict-release-gate", required: true, status: "BLOCKED", pendingItems: 1 },
    { id: "release-environment", required: true, status: "BLOCKED", pendingItems: 1 },
  ];
  return {
    fastTrack: {
      recommendation: "NO_GO_STRICT",
      noAutoWaivers: true,
      gate: { strict: true, blockers: 2, warnings: 0 },
      safetySignals: { releaseEnvFile },
      summary: { blockedCutoverItems: 2, cutoverChecklistItems: 2 },
      cutoverChecklist: checklist,
    },
    finalGoNoGo: {
      recommendation: "NO_GO_STRICT",
      finalRecommendation: "NO_GO_STRICT",
      cutoverAllowed: false,
      releaseEnvFileCutoverSafe: false,
      noAutoWaivers: true,
      gate: { blockers: 2, warnings: 0 },
      summary: { blockedCutoverItems: 2, stopReasons: 1 },
      safetySignals: { releaseEnvFile },
      currentStopReasons: ["cutover checklist blocked: strict-release-gate"],
      ciSummary: {
        nonGoExitCode: 10,
        stopOwners: ["release-owner"],
        enforceCommand: "DDD_RELEASE_PREFLIGHT_ENFORCE=1 bash artifacts/ddd/release/release-preflight-gate.sh",
      },
    },
    ownerMatrix: {
      recommendation: "NO_GO_STRICT",
      noAutoWaivers: true,
      releaseEnvFileCutoverSafe: false,
      safetySignals: {
        releaseEnvFile,
      },
      summary: { blockedOwnerCount: 1 },
      owners: [
        {
          owner: "release-owner",
          blockedItems: 2,
          totalItems: 2,
          items: checklist.map((item) => ({ checklistId: item.id, status: item.status })),
        },
      ],
    },
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-fast-track.json"), `${JSON.stringify(artifacts.fastTrack, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-final-go-no-go.json"), `${JSON.stringify(artifacts.finalGoNoGo, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-cutover-owner-matrix.json"), `${JSON.stringify(artifacts.ownerMatrix, null, 2)}\n`);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-cutover-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-cutover-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_DIR: directory,
    },
  });
}

const passResult = runContract();
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /cutoverAllowed=false/);

const unsafeCutoverResult = runContract((artifacts) => {
  artifacts.finalGoNoGo.cutoverAllowed = true;
});
assert.notEqual(unsafeCutoverResult.status, 0);
assert.match(unsafeCutoverResult.stderr, /cutoverAllowed must require GO_STRICT/);

const mismatchedEnvSafetyResult = runContract((artifacts) => {
  artifacts.finalGoNoGo.releaseEnvFileCutoverSafe = true;
});
assert.notEqual(mismatchedEnvSafetyResult.status, 0);
assert.match(mismatchedEnvSafetyResult.stderr, /releaseEnvFileCutoverSafe must match release env safety predicate/);

const waiverResult = runContract((artifacts) => {
  artifacts.fastTrack.noAutoWaivers = false;
});
assert.notEqual(waiverResult.status, 0);
assert.match(waiverResult.stderr, /noAutoWaivers/);

const missingStopOwnersResult = runContract((artifacts) => {
  artifacts.finalGoNoGo.ciSummary.stopOwners = [];
});
assert.notEqual(missingStopOwnersResult.status, 0);
assert.match(missingStopOwnersResult.stderr, /stopOwners/);

const missingMatrixCoverageResult = runContract((artifacts) => {
  artifacts.ownerMatrix.owners[0].items = artifacts.ownerMatrix.owners[0].items.slice(0, 1);
  artifacts.ownerMatrix.owners[0].totalItems = 1;
  artifacts.ownerMatrix.owners[0].blockedItems = 1;
});
assert.notEqual(missingMatrixCoverageResult.status, 0);
assert.match(missingMatrixCoverageResult.stderr, /cover checklist item release-environment/);

const unsafeDisplayCommandResult = runContract((artifacts) => {
  artifacts.fastTrack.lanes = [
    {
      lane: "environment",
      commands: ["DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs"],
    },
  ];
  artifacts.ownerMatrix.owners[0].items[0].commands = [
    "DDD_RELEASE_ENV_FILE=/secure/.env.release.local node scripts/ddd-release-env-file-lint.mjs",
  ];
});
assert.notEqual(unsafeDisplayCommandResult.status, 0);
assert.match(unsafeDisplayCommandResult.stderr, /must not expose concrete release env files/);

const safeGoResult = runContract((artifacts) => {
  const releaseEnvFile = {
    ready: true,
    status: "PASS",
    inputKind: "release-env-file",
    envFilePresent: true,
    generatedMissingTemplate: false,
    securityChecked: true,
    permissionSafe: true,
    permissionCheckSkipped: false,
    modeOctal: "600",
    requiredMode: "600",
  };
  artifacts.fastTrack.recommendation = "GO_STRICT";
  artifacts.fastTrack.gate.blockers = 0;
  artifacts.fastTrack.safetySignals.releaseEnvFile = releaseEnvFile;
  artifacts.fastTrack.cutoverChecklist = artifacts.fastTrack.cutoverChecklist.map((item) => ({
    ...item,
    status: "PASS",
    pendingItems: 0,
  }));
  artifacts.fastTrack.summary.blockedCutoverItems = 0;
  artifacts.finalGoNoGo.recommendation = "GO_STRICT";
  artifacts.finalGoNoGo.finalRecommendation = "GO_STRICT";
  artifacts.finalGoNoGo.cutoverAllowed = true;
  artifacts.finalGoNoGo.releaseEnvFileCutoverSafe = true;
  artifacts.finalGoNoGo.summary.blockedCutoverItems = 0;
  artifacts.finalGoNoGo.summary.stopReasons = 0;
  artifacts.finalGoNoGo.gate.blockers = 0;
  artifacts.finalGoNoGo.safetySignals.releaseEnvFile = releaseEnvFile;
  artifacts.finalGoNoGo.currentStopReasons = [];
  artifacts.finalGoNoGo.ciSummary.stopOwners = [];
  artifacts.ownerMatrix.recommendation = "GO_STRICT";
  artifacts.ownerMatrix.summary.blockedOwnerCount = 0;
  artifacts.ownerMatrix.owners[0].blockedItems = 0;
  artifacts.ownerMatrix.safetySignals.releaseEnvFile = releaseEnvFile;
  artifacts.ownerMatrix.releaseEnvFileCutoverSafe = true;
  artifacts.ownerMatrix.owners[0].items = artifacts.ownerMatrix.owners[0].items.map((item) => ({
    ...item,
    status: "PASS",
  }));
});
assert.equal(safeGoResult.status, 0, safeGoResult.stderr);
assert.match(safeGoResult.stdout, /cutoverAllowed=true/);

const unsafeGoWithStopReasonsResult = runContract((artifacts) => {
  const releaseEnvFile = {
    ready: true,
    status: "PASS",
    inputKind: "release-env-file",
    envFilePresent: true,
    generatedMissingTemplate: false,
    securityChecked: true,
    permissionSafe: true,
    permissionCheckSkipped: false,
    modeOctal: "600",
    requiredMode: "600",
  };
  artifacts.fastTrack.recommendation = "GO_STRICT";
  artifacts.fastTrack.gate.blockers = 0;
  artifacts.fastTrack.safetySignals.releaseEnvFile = releaseEnvFile;
  artifacts.fastTrack.cutoverChecklist = artifacts.fastTrack.cutoverChecklist.map((item) => ({
    ...item,
    status: "PASS",
    pendingItems: 0,
  }));
  artifacts.fastTrack.summary.blockedCutoverItems = 0;
  artifacts.finalGoNoGo.recommendation = "GO_STRICT";
  artifacts.finalGoNoGo.finalRecommendation = "GO_STRICT";
  artifacts.finalGoNoGo.cutoverAllowed = true;
  artifacts.finalGoNoGo.releaseEnvFileCutoverSafe = true;
  artifacts.finalGoNoGo.summary.blockedCutoverItems = 0;
  artifacts.finalGoNoGo.summary.stopReasons = 1;
  artifacts.finalGoNoGo.gate.blockers = 0;
  artifacts.finalGoNoGo.safetySignals.releaseEnvFile = releaseEnvFile;
  artifacts.finalGoNoGo.currentStopReasons = ["cutover checklist blocked: release-environment"];
  artifacts.ownerMatrix.recommendation = "GO_STRICT";
  artifacts.ownerMatrix.summary.blockedOwnerCount = 0;
  artifacts.ownerMatrix.owners[0].blockedItems = 0;
  artifacts.ownerMatrix.safetySignals.releaseEnvFile = releaseEnvFile;
  artifacts.ownerMatrix.releaseEnvFileCutoverSafe = true;
  artifacts.ownerMatrix.owners[0].items = artifacts.ownerMatrix.owners[0].items.map((item) => ({
    ...item,
    status: "PASS",
  }));
});
assert.notEqual(unsafeGoWithStopReasonsResult.status, 0);
assert.match(unsafeGoWithStopReasonsResult.stderr, /GO_STRICT requires zero currentStopReasons/);
assert.match(unsafeGoWithStopReasonsResult.stderr, /cutoverAllowed requires zero currentStopReasons/);

const unsafeGoWithGateBlockersResult = runContract((artifacts) => {
  const releaseEnvFile = {
    ready: true,
    status: "PASS",
    inputKind: "release-env-file",
    envFilePresent: true,
    generatedMissingTemplate: false,
    securityChecked: true,
    permissionSafe: true,
    permissionCheckSkipped: false,
    modeOctal: "600",
    requiredMode: "600",
  };
  artifacts.fastTrack.recommendation = "GO_STRICT";
  artifacts.fastTrack.gate.blockers = 0;
  artifacts.fastTrack.safetySignals.releaseEnvFile = releaseEnvFile;
  artifacts.fastTrack.cutoverChecklist = artifacts.fastTrack.cutoverChecklist.map((item) => ({
    ...item,
    status: "PASS",
    pendingItems: 0,
  }));
  artifacts.fastTrack.summary.blockedCutoverItems = 0;
  artifacts.finalGoNoGo.recommendation = "GO_STRICT";
  artifacts.finalGoNoGo.finalRecommendation = "GO_STRICT";
  artifacts.finalGoNoGo.cutoverAllowed = true;
  artifacts.finalGoNoGo.releaseEnvFileCutoverSafe = true;
  artifacts.finalGoNoGo.summary.blockedCutoverItems = 0;
  artifacts.finalGoNoGo.summary.stopReasons = 0;
  artifacts.finalGoNoGo.gate.blockers = 1;
  artifacts.finalGoNoGo.safetySignals.releaseEnvFile = releaseEnvFile;
  artifacts.finalGoNoGo.currentStopReasons = [];
  artifacts.ownerMatrix.recommendation = "GO_STRICT";
  artifacts.ownerMatrix.summary.blockedOwnerCount = 0;
  artifacts.ownerMatrix.owners[0].blockedItems = 0;
  artifacts.ownerMatrix.safetySignals.releaseEnvFile = releaseEnvFile;
  artifacts.ownerMatrix.releaseEnvFileCutoverSafe = true;
  artifacts.ownerMatrix.owners[0].items = artifacts.ownerMatrix.owners[0].items.map((item) => ({
    ...item,
    status: "PASS",
  }));
});
assert.notEqual(unsafeGoWithGateBlockersResult.status, 0);
assert.match(unsafeGoWithGateBlockersResult.stderr, /cutoverAllowed requires zero gate blockers/);

const unsafePermissionGoResult = runContract((artifacts) => {
  const releaseEnvFile = {
    ready: true,
    status: "PASS",
    inputKind: "release-env-file",
    envFilePresent: true,
    generatedMissingTemplate: false,
    securityChecked: true,
    permissionSafe: false,
    permissionCheckSkipped: false,
    modeOctal: "644",
    requiredMode: "600",
  };
  artifacts.fastTrack.recommendation = "GO_STRICT";
  artifacts.fastTrack.gate.blockers = 0;
  artifacts.fastTrack.safetySignals.releaseEnvFile = releaseEnvFile;
  artifacts.fastTrack.cutoverChecklist = artifacts.fastTrack.cutoverChecklist.map((item) => ({
    ...item,
    status: "PASS",
    pendingItems: 0,
  }));
  artifacts.fastTrack.summary.blockedCutoverItems = 0;
  artifacts.finalGoNoGo.recommendation = "GO_STRICT";
  artifacts.finalGoNoGo.finalRecommendation = "GO_STRICT";
  artifacts.finalGoNoGo.cutoverAllowed = true;
  artifacts.finalGoNoGo.releaseEnvFileCutoverSafe = false;
  artifacts.finalGoNoGo.summary.blockedCutoverItems = 0;
  artifacts.finalGoNoGo.summary.stopReasons = 0;
  artifacts.finalGoNoGo.gate.blockers = 0;
  artifacts.finalGoNoGo.safetySignals.releaseEnvFile = releaseEnvFile;
  artifacts.finalGoNoGo.currentStopReasons = [];
  artifacts.finalGoNoGo.ciSummary.stopOwners = [];
  artifacts.ownerMatrix.recommendation = "GO_STRICT";
  artifacts.ownerMatrix.summary.blockedOwnerCount = 0;
  artifacts.ownerMatrix.owners[0].blockedItems = 0;
  artifacts.ownerMatrix.safetySignals.releaseEnvFile = releaseEnvFile;
  artifacts.ownerMatrix.releaseEnvFileCutoverSafe = false;
  artifacts.ownerMatrix.owners[0].items = artifacts.ownerMatrix.owners[0].items.map((item) => ({
    ...item,
    status: "PASS",
  }));
});
assert.notEqual(unsafePermissionGoResult.status, 0);
assert.match(unsafePermissionGoResult.stderr, /cutoverAllowed must require GO_STRICT, release env cutover safety/);

console.log("[ddd-release-cutover-contract.test] ok");
