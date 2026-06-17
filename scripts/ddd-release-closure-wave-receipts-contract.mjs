#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const receiptsPath = path.join(releaseDir, "release-closure-wave-receipts.json");
const planPath = path.join(releaseDir, "release-blocker-closure-plan.json");
const finalGoNoGoPath = path.join(releaseDir, "release-final-go-no-go.json");
const repoRoot = path.resolve(process.cwd());
const repoRootPattern = repoRoot.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const unsafeDisplayCommandPatterns = [
  /DDD_RELEASE_ENV_FILE=(?!<release-env-file>)(?:"[^"`\s|]+"|'[^'`\s|]+'|[^\s`|]+)/,
  /(^|\s)(?:[^\s`|]*\/)?\.env\.release(?:\.[A-Za-z0-9_-]+)?(?=\s|`|\)|,|$)/,
  new RegExp(repoRootPattern),
];

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release closure wave artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const receipts = readJson(receiptsPath);
const plan = readJson(planPath);
const finalGoNoGo = readJson(finalGoNoGoPath);
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function assertSafeDisplayCommand(label, command) {
  if (!command) return;
  const value = String(command);
  if (unsafeDisplayCommandPatterns.some((pattern) => pattern.test(value))) {
    addFailure(`${label} must not expose concrete release env files or local repo paths`);
  }
}

function sameArray(left = [], right = []) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function expectedReceiptStatus(wave) {
  if ((wave.missingArtifacts || []).length > 0) return "ARTIFACT_MISSING";
  if ((wave.blockerHints || []).length > 0) return "CONTENT_BLOCKED";
  return "READY_FOR_STRICT_GATE_RERUN";
}

if (receipts.noAutoWaivers !== true) addFailure("releaseClosureWaveReceipts must keep noAutoWaivers=true");
if (receipts.recommendation !== plan.recommendation) addFailure("releaseClosureWaveReceipts recommendation must match closure plan");
if (!Array.isArray(receipts.waves)) addFailure("releaseClosureWaveReceipts waves must be an array");
if (!Array.isArray(plan.waves)) addFailure("releaseBlockerClosurePlan waves must be an array");

const receiptWaves = Array.isArray(receipts.waves) ? receipts.waves : [];
const planWaves = Array.isArray(plan.waves) ? plan.waves : [];
if (receipts.summary?.waveCount !== receiptWaves.length) addFailure("summary.waveCount must match receipt waves length");
if (receipts.summary?.waveCount !== planWaves.length) addFailure("summary.waveCount must match closure plan waves length");

let readyCount = 0;
let artifactMissingCount = 0;
let contentBlockedCount = 0;
let expectedArtifactCount = 0;
let presentArtifactCount = 0;
let missingArtifactCount = 0;

for (const [index, wave] of receiptWaves.entries()) {
  const label = `wave ${wave.wave ?? index + 1}`;
  const planWave = planWaves[index];
  if (!planWave) {
    addFailure(`${label} has no matching closure plan wave`);
    continue;
  }
  if (wave.wave !== planWave.wave) addFailure(`${label}.wave must match closure plan`);
  if (wave.owner !== planWave.owner) addFailure(`${label}.owner must match closure plan`);
  if (wave.batchId !== planWave.batchId) addFailure(`${label}.batchId must match closure plan`);
  if (wave.priority !== planWave.priority) addFailure(`${label}.priority must match closure plan`);
  if (!sameArray(wave.itemOrders || [], planWave.itemOrders || [])) addFailure(`${label}.itemOrders must match closure plan`);
  if (!sameArray(wave.itemIds || [], planWave.itemIds || [])) addFailure(`${label}.itemIds must match closure plan`);
  if (!sameArray(wave.commands || [], planWave.commands || [])) addFailure(`${label}.commands must match closure plan`);
  if (!sameArray(wave.exitCriteria || [], planWave.exitCriteria || [])) addFailure(`${label}.exitCriteria must match closure plan`);
  for (const [commandIndex, command] of (planWave.commands || []).entries()) {
    assertSafeDisplayCommand(`${label}.plan.commands[${commandIndex}]`, command);
  }
  for (const [commandIndex, command] of (wave.commands || []).entries()) {
    assertSafeDisplayCommand(`${label}.receipt.commands[${commandIndex}]`, command);
  }

  const expectedStatus = expectedReceiptStatus(wave);
  if (wave.receiptStatus !== expectedStatus) {
    addFailure(`${label}.receiptStatus must be ${expectedStatus}, got ${wave.receiptStatus || "missing"}`);
  }
  if (!Array.isArray(wave.rerunCommands) || !wave.rerunCommands.includes("node scripts/ddd-release-readiness-summary.mjs")) {
    addFailure(`${label}.rerunCommands must include readiness summary rerun`);
  }
  if (wave.expectedArtifactCount !== (wave.presentArtifacts || []).length + (wave.missingArtifacts || []).length) {
    addFailure(`${label}.expectedArtifactCount must equal present + missing artifacts`);
  }
  if (wave.presentArtifactCount !== (wave.presentArtifacts || []).length) addFailure(`${label}.presentArtifactCount must match presentArtifacts`);
  if (wave.missingArtifactCount !== (wave.missingArtifacts || []).length) addFailure(`${label}.missingArtifactCount must match missingArtifacts`);
  if ((wave.missingArtifacts || []).some((value) => !/^(artifacts\/ddd\/|tmp\/ddd-explain\/)[^\s]+$/.test(value))) {
    addFailure(`${label}.missingArtifacts must stay under artifacts/ddd or tmp/ddd-explain`);
  }

  if (wave.receiptStatus === "READY_FOR_STRICT_GATE_RERUN") readyCount += 1;
  if (wave.receiptStatus === "ARTIFACT_MISSING") artifactMissingCount += 1;
  if (wave.receiptStatus === "CONTENT_BLOCKED") contentBlockedCount += 1;
  expectedArtifactCount += Number(wave.expectedArtifactCount || 0);
  presentArtifactCount += Number(wave.presentArtifactCount || 0);
  missingArtifactCount += Number(wave.missingArtifactCount || 0);
}

if (receipts.summary?.readyForStrictGateRerunCount !== readyCount) addFailure("summary.readyForStrictGateRerunCount must match waves");
if (receipts.summary?.artifactMissingCount !== artifactMissingCount) addFailure("summary.artifactMissingCount must match waves");
if (receipts.summary?.contentBlockedCount !== contentBlockedCount) addFailure("summary.contentBlockedCount must match waves");
if (receipts.summary?.expectedArtifactCount !== expectedArtifactCount) addFailure("summary.expectedArtifactCount must match waves");
if (receipts.summary?.presentArtifactCount !== presentArtifactCount) addFailure("summary.presentArtifactCount must match waves");
if (receipts.summary?.missingArtifactCount !== missingArtifactCount) addFailure("summary.missingArtifactCount must match waves");
if (finalGoNoGo.summary?.receiptMissingArtifactWaves !== artifactMissingCount) addFailure("finalGoNoGo receiptMissingArtifactWaves must match closure receipts");
if (finalGoNoGo.summary?.receiptContentBlockedWaves !== contentBlockedCount) addFailure("finalGoNoGo receiptContentBlockedWaves must match closure receipts");

if (failures.length > 0) {
  throw new Error(`release closure wave receipts contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-closure-wave-receipts-contract] ok waves=${receiptWaves.length} ready=${readyCount} missing=${artifactMissingCount} contentBlocked=${contentBlockedCount}`);
