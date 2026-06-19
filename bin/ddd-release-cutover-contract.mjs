#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const fastTrackPath = path.join(releaseDir, "release-fast-track.json");
const finalGoNoGoPath = path.join(releaseDir, "release-final-go-no-go.json");
const ownerMatrixPath = path.join(releaseDir, "release-cutover-owner-matrix.json");
const repoRoot = path.resolve(process.cwd());
const repoRootPattern = repoRoot.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const unsafeDisplayCommandPatterns = [
  /DDD_RELEASE_ENV_FILE=(?!<release-env-file>)(?:"[^"`\s|]+"|'[^'`\s|]+'|[^\s`|]+)/,
  /(^|\s)(?:[^\s`|]*\/)?\.env\.release(?:\.[A-Za-z0-9_-]+)?(?=\s|`|\)|,|$)/,
  new RegExp(repoRootPattern),
];

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release cutover artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const fastTrack = readJson(fastTrackPath);
const finalGoNoGo = readJson(finalGoNoGoPath);
const ownerMatrix = readJson(ownerMatrixPath);
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

function blockedChecklistItems(artifact) {
  return (artifact.cutoverChecklist || []).filter((item) => item.status !== "PASS");
}

function assertNoAutoWaivers(name, artifact) {
  if (artifact.noAutoWaivers !== true) {
    addFailure(`${name} must keep noAutoWaivers=true`);
  }
}

function assertReleaseEnvFileSignal(name, signal) {
  if (!signal || typeof signal !== "object") {
    addFailure(`${name}.safetySignals.releaseEnvFile is required`);
    return;
  }
  if (typeof signal.ready !== "boolean") addFailure(`${name}.releaseEnvFile.ready must be boolean`);
  if (signal.securityChecked !== true) addFailure(`${name}.releaseEnvFile.securityChecked must be true`);
  if (signal.permissionSafe !== true) addFailure(`${name}.releaseEnvFile.permissionSafe must be true`);
  if (signal.ready === true && signal.status !== "PASS") addFailure(`${name}.releaseEnvFile ready=true requires status=PASS`);
  if (signal.ready !== true && signal.status === "PASS") addFailure(`${name}.releaseEnvFile status=PASS requires ready=true`);
}

function releaseEnvFileIsCutoverSafe(signal) {
  return signal?.ready === true
    && signal?.status === "PASS"
    && signal?.inputKind === "release-env-file"
    && signal?.envFilePresent === true
    && signal?.generatedMissingTemplate !== true
    && signal?.securityChecked === true
    && signal?.permissionSafe === true
    && signal?.permissionCheckSkipped !== true
    && signal?.modeOctal === (signal?.requiredMode || "600")
    && (signal?.requiredMode || "600") === "600";
}

assertNoAutoWaivers("releaseFastTrack", fastTrack);
assertNoAutoWaivers("releaseFinalGoNoGo", finalGoNoGo);
assertNoAutoWaivers("releaseCutoverOwnerMatrix", ownerMatrix);
assertReleaseEnvFileSignal("releaseFastTrack", fastTrack.safetySignals?.releaseEnvFile);
assertReleaseEnvFileSignal("releaseFinalGoNoGo", finalGoNoGo.safetySignals?.releaseEnvFile);

if (!["GO_STRICT", "NO_GO_STRICT"].includes(fastTrack.recommendation)) {
  addFailure(`releaseFastTrack recommendation is invalid: ${fastTrack.recommendation}`);
}
if (finalGoNoGo.recommendation !== fastTrack.recommendation) {
  addFailure("releaseFinalGoNoGo recommendation must match releaseFastTrack");
}
if (finalGoNoGo.finalRecommendation !== finalGoNoGo.recommendation) {
  addFailure("releaseFinalGoNoGo finalRecommendation must match recommendation");
}
if (ownerMatrix.recommendation !== fastTrack.recommendation) {
  addFailure("releaseCutoverOwnerMatrix recommendation must match releaseFastTrack");
}
assertReleaseEnvFileSignal("releaseCutoverOwnerMatrix", ownerMatrix.safetySignals?.releaseEnvFile);
if (JSON.stringify(ownerMatrix.safetySignals?.releaseEnvFile || null) !== JSON.stringify(fastTrack.safetySignals?.releaseEnvFile || null)) {
  addFailure("releaseCutoverOwnerMatrix safetySignals.releaseEnvFile must match releaseFastTrack");
}

const blockedItems = blockedChecklistItems(fastTrack);
if ((fastTrack.summary?.blockedCutoverItems ?? -1) !== blockedItems.length) {
  addFailure("releaseFastTrack blockedCutoverItems must match cutoverChecklist");
}
if ((finalGoNoGo.summary?.blockedCutoverItems ?? -1) !== blockedItems.length) {
  addFailure("releaseFinalGoNoGo blockedCutoverItems must match releaseFastTrack checklist");
}
if (fastTrack.recommendation === "GO_STRICT" && blockedItems.length > 0) {
  addFailure("GO_STRICT requires every cutover checklist item to PASS");
}
if (fastTrack.recommendation === "NO_GO_STRICT" && blockedItems.length === 0) {
  addFailure("NO_GO_STRICT requires at least one blocked cutover checklist item");
}

const releaseEnvCutoverSafe = releaseEnvFileIsCutoverSafe(finalGoNoGo.safetySignals?.releaseEnvFile);
if (typeof finalGoNoGo.releaseEnvFileCutoverSafe !== "boolean") {
  addFailure("releaseFinalGoNoGo releaseEnvFileCutoverSafe must be boolean");
} else if (finalGoNoGo.releaseEnvFileCutoverSafe !== releaseEnvCutoverSafe) {
  addFailure("releaseFinalGoNoGo releaseEnvFileCutoverSafe must match release env safety predicate");
}
if (typeof ownerMatrix.releaseEnvFileCutoverSafe !== "boolean") {
  addFailure("releaseCutoverOwnerMatrix releaseEnvFileCutoverSafe must be boolean");
} else if (ownerMatrix.releaseEnvFileCutoverSafe !== releaseEnvFileIsCutoverSafe(ownerMatrix.safetySignals?.releaseEnvFile)) {
  addFailure("releaseCutoverOwnerMatrix releaseEnvFileCutoverSafe must match release env safety predicate");
}
const currentStopReasons = Array.isArray(finalGoNoGo.currentStopReasons) ? finalGoNoGo.currentStopReasons : [];
const finalGateBlockers = Number(finalGoNoGo.gate?.blockers ?? fastTrack.gate?.blockers ?? 0);
if (Number(finalGoNoGo.summary?.stopReasons ?? currentStopReasons.length) !== currentStopReasons.length) {
  addFailure("releaseFinalGoNoGo summary.stopReasons must match currentStopReasons length");
}
if (finalGoNoGo.finalRecommendation === "GO_STRICT" && currentStopReasons.length > 0) {
  addFailure("releaseFinalGoNoGo GO_STRICT requires zero currentStopReasons");
}
if (finalGoNoGo.cutoverAllowed === true && currentStopReasons.length > 0) {
  addFailure("releaseFinalGoNoGo cutoverAllowed requires zero currentStopReasons");
}
if (finalGoNoGo.cutoverAllowed === true && finalGateBlockers > 0) {
  addFailure("releaseFinalGoNoGo cutoverAllowed requires zero gate blockers");
}
const expectedCutoverAllowed = fastTrack.recommendation === "GO_STRICT"
  && releaseEnvCutoverSafe
  && blockedItems.length === 0
  && currentStopReasons.length === 0
  && finalGateBlockers === 0;
if (finalGoNoGo.cutoverAllowed !== expectedCutoverAllowed) {
  addFailure("releaseFinalGoNoGo cutoverAllowed must require GO_STRICT, release env cutover safety, zero blocked checklist items, zero stop reasons, and zero gate blockers");
}
if (finalGoNoGo.cutoverAllowed === true && finalGoNoGo.ciSummary?.nonGoExitCode !== 10) {
  addFailure("releaseFinalGoNoGo ciSummary.nonGoExitCode must remain 10 even when cutover is allowed");
}
if (finalGoNoGo.cutoverAllowed === false) {
  if (currentStopReasons.length === 0) {
    addFailure("NO-GO final packet must include currentStopReasons");
  }
  if (!Array.isArray(finalGoNoGo.ciSummary?.stopOwners) || finalGoNoGo.ciSummary.stopOwners.length === 0) {
    addFailure("NO-GO final packet must include ciSummary.stopOwners");
  }
}
if (!String(finalGoNoGo.ciSummary?.enforceCommand || "").includes("DDD_RELEASE_PREFLIGHT_ENFORCE=1")) {
  addFailure("releaseFinalGoNoGo must enforce the one-command preflight gate");
}

const matrixChecklistIds = new Set((ownerMatrix.owners || []).flatMap((owner) => (
  (owner.items || []).map((item) => item.checklistId)
)));
for (const item of fastTrack.cutoverChecklist || []) {
  if (!matrixChecklistIds.has(item.id)) {
    addFailure(`releaseCutoverOwnerMatrix must cover checklist item ${item.id}`);
  }
}
const matrixBlockedOwnerCount = (ownerMatrix.owners || []).filter((owner) => owner.blockedItems > 0).length;
if ((ownerMatrix.summary?.blockedOwnerCount ?? -1) !== matrixBlockedOwnerCount) {
  addFailure("releaseCutoverOwnerMatrix blockedOwnerCount must match owner rows");
}
for (const owner of ownerMatrix.owners || []) {
  const itemCount = (owner.items || []).length;
  const blockedCount = (owner.items || []).filter((item) => item.status !== "PASS").length;
  if (owner.totalItems !== itemCount) addFailure(`releaseCutoverOwnerMatrix.${owner.owner}.totalItems must match items.length`);
  if (owner.blockedItems !== blockedCount) addFailure(`releaseCutoverOwnerMatrix.${owner.owner}.blockedItems must match items`);
  for (const [itemIndex, item] of (owner.items || []).entries()) {
    for (const [commandIndex, command] of (item.commands || []).entries()) {
      assertSafeDisplayCommand(`releaseCutoverOwnerMatrix.${owner.owner}.items[${itemIndex}].commands[${commandIndex}]`, command);
    }
  }
}
for (const [laneIndex, lane] of (fastTrack.lanes || []).entries()) {
  for (const [commandIndex, command] of (lane.commands || []).entries()) {
    assertSafeDisplayCommand(`releaseFastTrack.lanes[${laneIndex}].commands[${commandIndex}]`, command);
  }
}

if (failures.length > 0) {
  throw new Error(`release cutover contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-cutover-contract] ok recommendation=${finalGoNoGo.finalRecommendation} cutoverAllowed=${finalGoNoGo.cutoverAllowed} blockedCutoverItems=${blockedItems.length}`);
