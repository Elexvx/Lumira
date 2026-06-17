#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function baseArtifacts() {
  const planWave = {
    wave: 1,
    owner: "release-infra",
    batchId: "p0-release-env-lint-release-infra",
    priority: "P0",
    itemOrders: [1],
    itemIds: ["release-env-lint-status"],
    commands: ["node scripts/ddd-release-env-file-lint.mjs"],
    expectedArtifacts: ["artifacts/ddd/release/release-env-lint.json"],
    blockerHints: [],
    exitCriteria: ["release env lint passes"],
  };
  const receiptWave = {
    ...planWave,
    receiptStatus: "READY_FOR_STRICT_GATE_RERUN",
    expectedArtifactCount: 1,
    presentArtifactCount: 1,
    missingArtifactCount: 0,
    presentArtifacts: ["artifacts/ddd/release/release-env-lint.json"],
    missingArtifacts: [],
    nextCheck: "Rerun strict release gate and readiness summary.",
    rerunCommands: [
      "node scripts/ddd-release-evidence-gate.mjs",
      "node scripts/ddd-release-readiness-summary.mjs",
    ],
  };
  return {
    plan: {
      recommendation: "NO_GO_STRICT",
      noAutoWaivers: true,
      summary: { runnableWaveCount: 1 },
      waves: [planWave],
    },
    receipts: {
      recommendation: "NO_GO_STRICT",
      noAutoWaivers: true,
      summary: {
        waveCount: 1,
        readyForStrictGateRerunCount: 1,
        artifactMissingCount: 0,
        contentBlockedCount: 0,
        expectedArtifactCount: 1,
        presentArtifactCount: 1,
        missingArtifactCount: 0,
      },
      waves: [receiptWave],
    },
    finalGoNoGo: {
      summary: {
        receiptMissingArtifactWaves: 0,
        receiptContentBlockedWaves: 0,
      },
    },
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-blocker-closure-plan.json"), `${JSON.stringify(artifacts.plan, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-closure-wave-receipts.json"), `${JSON.stringify(artifacts.receipts, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-final-go-no-go.json"), `${JSON.stringify(artifacts.finalGoNoGo, null, 2)}\n`);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-closure-wave-receipts-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-closure-wave-receipts-contract.mjs"], {
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
assert.match(passResult.stdout, /ready=1/);

const missingArtifactStatusResult = runContract((artifacts) => {
  const wave = artifacts.receipts.waves[0];
  wave.presentArtifacts = [];
  wave.missingArtifacts = ["artifacts/ddd/performance/authenticated-runtime-baseline.json"];
  wave.presentArtifactCount = 0;
  wave.missingArtifactCount = 1;
  wave.receiptStatus = "READY_FOR_STRICT_GATE_RERUN";
  artifacts.receipts.summary.readyForStrictGateRerunCount = 1;
  artifacts.receipts.summary.missingArtifactCount = 1;
});
assert.notEqual(missingArtifactStatusResult.status, 0);
assert.match(missingArtifactStatusResult.stderr, /receiptStatus must be ARTIFACT_MISSING/);

const blockerHintStatusResult = runContract((artifacts) => {
  const wave = artifacts.receipts.waves[0];
  wave.blockerHints = ["manifest requires evidenceOperator"];
  wave.receiptStatus = "READY_FOR_STRICT_GATE_RERUN";
});
assert.notEqual(blockerHintStatusResult.status, 0);
assert.match(blockerHintStatusResult.stderr, /receiptStatus must be CONTENT_BLOCKED/);

const planMismatchResult = runContract((artifacts) => {
  artifacts.receipts.waves[0].commands = ["node scripts/other.mjs"];
});
assert.notEqual(planMismatchResult.status, 0);
assert.match(planMismatchResult.stderr, /commands must match closure plan/);

const unsafeDisplayCommandResult = runContract((artifacts) => {
  artifacts.plan.waves[0].commands = [
    "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs",
  ];
  artifacts.receipts.waves[0].commands = [
    "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs",
  ];
});
assert.notEqual(unsafeDisplayCommandResult.status, 0);
assert.match(unsafeDisplayCommandResult.stderr, /must not expose concrete release env files/);

const badMissingPathResult = runContract((artifacts) => {
  const wave = artifacts.receipts.waves[0];
  wave.presentArtifacts = [];
  wave.missingArtifacts = ["../secret.txt"];
  wave.presentArtifactCount = 0;
  wave.missingArtifactCount = 1;
  wave.receiptStatus = "ARTIFACT_MISSING";
  artifacts.receipts.summary.readyForStrictGateRerunCount = 0;
  artifacts.receipts.summary.artifactMissingCount = 1;
  artifacts.receipts.summary.presentArtifactCount = 0;
  artifacts.receipts.summary.missingArtifactCount = 1;
  artifacts.finalGoNoGo.summary.receiptMissingArtifactWaves = 1;
});
assert.notEqual(badMissingPathResult.status, 0);
assert.match(badMissingPathResult.stderr, /missingArtifacts must stay/);

const finalMismatchResult = runContract((artifacts) => {
  artifacts.finalGoNoGo.summary.receiptContentBlockedWaves = 1;
});
assert.notEqual(finalMismatchResult.status, 0);
assert.match(finalMismatchResult.stderr, /receiptContentBlockedWaves/);

console.log("[ddd-release-closure-wave-receipts-contract.test] ok");
