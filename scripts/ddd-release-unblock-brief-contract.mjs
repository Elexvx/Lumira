#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(import.meta.dirname, "..");
const releaseDir = process.env.DDD_RELEASE_DIR
  ? path.resolve(process.env.DDD_RELEASE_DIR)
  : path.join(repoRoot, "artifacts", "ddd", "release");
const jsonPath = process.env.DDD_RELEASE_UNBLOCK_BRIEF_JSON
  ? path.resolve(process.env.DDD_RELEASE_UNBLOCK_BRIEF_JSON)
  : path.join(releaseDir, "release-unblock-brief.json");
const markdownPath = process.env.DDD_RELEASE_UNBLOCK_BRIEF_MD
  ? path.resolve(process.env.DDD_RELEASE_UNBLOCK_BRIEF_MD)
  : path.join(releaseDir, "release-unblock-brief.md");

const forbiddenPatterns = [
  /\b[A-Z][A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|API_KEY)=(?!<redacted>)("[^"]*"|'[^']*'|[^\s`|]+)/,
  /\bDDD_RELEASE_ENV_FILE=(?!<release-env-file>)[^\s`|]+/,
  new RegExp(`${repoRoot.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}${path.sep.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`),
  new RegExp(repoRoot.replaceAll("\\", "\\\\").replace(/[.*+?^${}()|[\]\\]/g, "\\$&")),
  new RegExp(repoRoot.replaceAll("\\", "/").replace(/[.*+?^${}()|[\]\\]/g, "\\$&")),
  /\b(?:mysql|postgres|jdbc|redis|amqp):\/\/[^\s`|)]+/i,
  /\b(?:sk-[A-Za-z0-9_-]{12,}|eyJ[A-Za-z0-9_-]{20,})\b/,
  /__REQUIRED__/,
];

const requiredHandoffReferences = new Map([
  ["migration-evidence-handoff", "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs"],
  ["rollback-deferral-owner-handoff", "node scripts/ddd-rollback-deferral-template.mjs"],
  ["performance-baseline-handoff", "DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh"],
  ["release-env-owner-input-packet", "node scripts/ddd-release-env-owner-input-packet-contract.mjs"],
  ["release-owner-input-receipt", "node scripts/ddd-release-owner-input-receipt-contract.mjs"],
]);

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release unblock brief artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function readText(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release unblock brief artifact: ${file}`);
  }
  return fs.readFileSync(file, "utf8");
}

function isIsoDatetime(value) {
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}T/.test(value) && !Number.isNaN(Date.parse(value));
}

function validateCutoverBatch(batch, label, issues) {
  if (!batch || typeof batch !== "object" || Array.isArray(batch)) {
    issues.push(`${label} must be an object`);
    return;
  }
  for (const field of ["id", "priority", "source", "owner"]) {
    if (typeof batch[field] !== "string" || batch[field].length === 0) {
      issues.push(`${label}.${field} must be a non-empty string`);
    }
  }
  if (!Number.isInteger(batch.pendingItems) || batch.pendingItems < 0) {
    issues.push(`${label}.pendingItems must be a non-negative integer`);
  }
  if (typeof batch.canRunImmediately !== "boolean") {
    issues.push(`${label}.canRunImmediately must be boolean`);
  }
  for (const field of ["dependsOn", "commands", "expectedArtifacts"]) {
    if (!Array.isArray(batch[field])) {
      issues.push(`${label}.${field} must be an array`);
      continue;
    }
    for (const [index, value] of batch[field].entries()) {
      if (typeof value !== "string") issues.push(`${label}.${field}[${index}] must be a string`);
      if (field === "expectedArtifacts" && (path.isAbsolute(value) || value.includes(".."))) {
        issues.push(`${label}.${field}[${index}] must be relative and traversal-free`);
      }
      if (field === "commands" && value.includes("DDD_RELEASE_ENV_FILE=") && !value.includes("DDD_RELEASE_ENV_FILE=<release-env-file>")) {
        issues.push(`${label}.${field}[${index}] must redact DDD_RELEASE_ENV_FILE`);
      }
    }
  }
}

function validateExecutionWave(wave, label, issues) {
  if (!wave || typeof wave !== "object" || Array.isArray(wave)) {
    issues.push(`${label} must be an object`);
    return;
  }
  if (typeof wave.priority !== "string" || wave.priority.length === 0) {
    issues.push(`${label}.priority must be a non-empty string`);
  }
  if (!Number.isInteger(wave.batchCount) || wave.batchCount < 0) {
    issues.push(`${label}.batchCount must be a non-negative integer`);
  }
  if (!Number.isInteger(wave.commandCount) || wave.commandCount < 0) {
    issues.push(`${label}.commandCount must be a non-negative integer`);
  }
  for (const field of ["runnableBatchIds", "blockedBatchIds", "owners", "dependsOn", "operatorCommands"]) {
    if (!Array.isArray(wave[field])) {
      issues.push(`${label}.${field} must be an array`);
      continue;
    }
    for (const [index, value] of wave[field].entries()) {
      if (typeof value !== "string" || value.length === 0) {
        issues.push(`${label}.${field}[${index}] must be a non-empty string`);
      }
      if (field === "operatorCommands"
        && value.includes("DDD_RELEASE_ENV_FILE=")
        && !value.includes("DDD_RELEASE_ENV_FILE=<release-env-file>")) {
        issues.push(`${label}.${field}[${index}] must redact DDD_RELEASE_ENV_FILE`);
      }
    }
  }
  if (wave.runnableBatchIds?.length > 0 && wave.operatorCommands?.length === 0) {
    issues.push(`${label}.operatorCommands must be present when runnableBatchIds is non-empty`);
  }
  if (wave.runnableBatchIds?.length === 0 && wave.operatorCommands?.length > 0) {
    issues.push(`${label}.operatorCommands must be empty when no batches are runnable`);
  }
}

function validateReleaseUnblockBrief(brief, markdown = "") {
  const issues = [];
  if (!brief || typeof brief !== "object" || Array.isArray(brief)) {
    return ["release unblock brief must be a JSON object"];
  }
  if (!isIsoDatetime(brief.generatedAt)) issues.push("generatedAt must be an ISO-like datetime");
  if (!["GO_STRICT", "GO", "NO_GO_STRICT", "NO_GO", "UNKNOWN"].includes(brief.recommendation)) {
    issues.push(`recommendation must be GO_STRICT, GO, NO_GO_STRICT, NO_GO, or UNKNOWN, got ${brief.recommendation || "missing"}`);
  }
  if (typeof brief.cutoverAllowed !== "boolean") issues.push("cutoverAllowed must be boolean");
  if (typeof brief.noAutoWaivers !== "boolean") issues.push("noAutoWaivers must be boolean");
  if (typeof brief.releaseEnvFileCutoverSafe !== "boolean") {
    issues.push("releaseEnvFileCutoverSafe must be boolean");
  }
  if (brief.cutoverAllowed === true && !["GO_STRICT", "GO"].includes(brief.recommendation)) {
    issues.push("cutoverAllowed=true requires recommendation=GO_STRICT or GO");
  }
  if (brief.cutoverAllowed === false && brief.noAutoWaivers !== true) {
    issues.push("NO-GO brief must keep noAutoWaivers=true");
  }
  const summary = brief.blockerSummary || {};
  for (const field of ["strictGateBlockers", "envOwnerBlockers", "envOwnerCount", "orchestratorPreflightBlockers", "orchestratorPreflightOwners", "blockedCutoverItems", "stopReasons"]) {
    if (!Number.isInteger(summary[field]) || summary[field] < 0) {
      issues.push(`blockerSummary.${field} must be a non-negative integer`);
    }
  }
  if (brief.cutoverAllowed === true) {
    if (brief.releaseEnvFileCutoverSafe !== true) {
      issues.push("cutoverAllowed=true requires releaseEnvFileCutoverSafe=true");
    }
    for (const field of ["strictGateBlockers", "envOwnerBlockers", "orchestratorPreflightBlockers", "blockedCutoverItems", "stopReasons"]) {
      if (Number.isInteger(summary[field]) && summary[field] !== 0) {
        issues.push(`cutoverAllowed=true requires blockerSummary.${field}=0`);
      }
    }
  }
  if (!Array.isArray(brief.fastestSafePath) || brief.fastestSafePath.length < 3) {
    issues.push("fastestSafePath must include at least three safe next steps");
  }
  const releaseEnvSafety = brief.releaseEnvSafety || {};
  if (!releaseEnvSafety || typeof releaseEnvSafety !== "object" || Array.isArray(releaseEnvSafety)) {
    issues.push("releaseEnvSafety must be an object");
  } else {
    for (const field of [
      "cutoverSafe",
      "ready",
      "envFilePresent",
      "generatedMissingTemplate",
      "securityChecked",
      "permissionSafe",
      "permissionCheckSkipped",
    ]) {
      if (typeof releaseEnvSafety[field] !== "boolean") {
        issues.push(`releaseEnvSafety.${field} must be boolean`);
      }
    }
    for (const field of ["status", "inputKind", "modeOctal", "requiredMode"]) {
      if (typeof releaseEnvSafety[field] !== "string" || releaseEnvSafety[field].length === 0) {
        issues.push(`releaseEnvSafety.${field} must be a non-empty string`);
      }
    }
    if (releaseEnvSafety.requiredMode !== "600") {
      issues.push("releaseEnvSafety.requiredMode must be 600");
    }
    if (releaseEnvSafety.cutoverSafe === true && (
      releaseEnvSafety.ready !== true
      || releaseEnvSafety.status !== "PASS"
      || releaseEnvSafety.inputKind !== "release-env-file"
      || releaseEnvSafety.envFilePresent !== true
      || releaseEnvSafety.generatedMissingTemplate === true
      || releaseEnvSafety.securityChecked !== true
      || releaseEnvSafety.permissionSafe !== true
      || releaseEnvSafety.permissionCheckSkipped === true
      || releaseEnvSafety.modeOctal !== "600"
    )) {
      issues.push("releaseEnvSafety.cutoverSafe=true must imply completed release env file with checked chmod 600 permissions");
    }
    if (!Array.isArray(releaseEnvSafety.pendingActionIds)) {
      issues.push("releaseEnvSafety.pendingActionIds must be an array");
    }
  }
  const releaseEnvFileSignal = brief.safetySignals?.releaseEnvFile || null;
  if (!releaseEnvFileSignal || typeof releaseEnvFileSignal !== "object" || Array.isArray(releaseEnvFileSignal)) {
    issues.push("safetySignals.releaseEnvFile must be an object");
  } else {
    const expectedCutoverSafe = releaseEnvFileSignal.ready === true
      && releaseEnvFileSignal.status === "PASS"
      && releaseEnvFileSignal.inputKind === "release-env-file"
      && releaseEnvFileSignal.envFilePresent === true
      && releaseEnvFileSignal.generatedMissingTemplate !== true
      && releaseEnvFileSignal.securityChecked === true
      && releaseEnvFileSignal.permissionSafe === true
      && releaseEnvFileSignal.permissionCheckSkipped !== true
      && releaseEnvFileSignal.modeOctal === "600";
    if (typeof brief.releaseEnvFileCutoverSafe === "boolean"
      && brief.releaseEnvFileCutoverSafe !== expectedCutoverSafe) {
      issues.push("releaseEnvFileCutoverSafe must match safetySignals.releaseEnvFile cutover predicate");
    }
    if (releaseEnvSafety && typeof releaseEnvSafety === "object" && !Array.isArray(releaseEnvSafety)) {
      const mirroredFields = [
        "ready",
        "status",
        "inputKind",
        "envFilePresent",
        "generatedMissingTemplate",
        "securityChecked",
        "permissionSafe",
        "permissionCheckSkipped",
        "modeOctal",
        "requiredMode",
      ];
      for (const field of mirroredFields) {
        if (releaseEnvSafety[field] !== releaseEnvFileSignal[field]) {
          issues.push(`releaseEnvSafety.${field} must match safetySignals.releaseEnvFile.${field}`);
        }
      }
      if (typeof brief.releaseEnvFileCutoverSafe === "boolean"
        && releaseEnvSafety.cutoverSafe !== brief.releaseEnvFileCutoverSafe) {
        issues.push("releaseEnvSafety.cutoverSafe must match releaseEnvFileCutoverSafe");
      }
    }
  }
  const finalOwnerQueueFastPath = brief.finalOwnerQueueFastPath || {};
  if (!finalOwnerQueueFastPath || typeof finalOwnerQueueFastPath !== "object" || Array.isArray(finalOwnerQueueFastPath)) {
    issues.push("finalOwnerQueueFastPath must be an object");
  } else {
    if (typeof finalOwnerQueueFastPath.owner !== "string" || finalOwnerQueueFastPath.owner.length === 0) {
      issues.push("finalOwnerQueueFastPath.owner must be a non-empty string");
    }
    if (!Number.isInteger(finalOwnerQueueFastPath.queueOrder) || finalOwnerQueueFastPath.queueOrder < 1) {
      issues.push("finalOwnerQueueFastPath.queueOrder must be a positive integer");
    }
    for (const field of ["objective", "blockedUntil", "firstCommand", "finalGateCommand"]) {
      if (typeof finalOwnerQueueFastPath[field] !== "string" || finalOwnerQueueFastPath[field].length === 0) {
        issues.push(`finalOwnerQueueFastPath.${field} must be a non-empty string`);
      }
    }
    for (const field of ["envKeyCount", "missingArtifactCount"]) {
      if (!Number.isInteger(finalOwnerQueueFastPath[field]) || finalOwnerQueueFastPath[field] < 0) {
        issues.push(`finalOwnerQueueFastPath.${field} must be a non-negative integer`);
      }
    }
    if (typeof finalOwnerQueueFastPath.releaseEnvFileRequired !== "boolean") {
      issues.push("finalOwnerQueueFastPath.releaseEnvFileRequired must be boolean");
    }
    if (finalOwnerQueueFastPath.finalGateCommand !== "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh") {
      issues.push("finalOwnerQueueFastPath.finalGateCommand must run strict final go/no-go gate");
    }
    if (!Array.isArray(finalOwnerQueueFastPath.commands) || finalOwnerQueueFastPath.commands.length === 0) {
      issues.push("finalOwnerQueueFastPath.commands must be a non-empty array");
    } else {
      if (!finalOwnerQueueFastPath.commands.includes("node scripts/ddd-release-readiness-summary.mjs")) {
        issues.push("finalOwnerQueueFastPath.commands must include readiness summary refresh");
      }
      if (!finalOwnerQueueFastPath.commands.includes("DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh")) {
        issues.push("finalOwnerQueueFastPath.commands must include strict final go/no-go gate");
      }
      for (const [index, command] of finalOwnerQueueFastPath.commands.entries()) {
        if (typeof command !== "string" || command.length === 0) issues.push(`finalOwnerQueueFastPath.commands[${index}] must be a non-empty string`);
        if (command.includes("DDD_RELEASE_ENV_FILE=") && !command.includes("DDD_RELEASE_ENV_FILE=<release-env-file>")) {
          issues.push(`finalOwnerQueueFastPath.commands[${index}] must redact DDD_RELEASE_ENV_FILE`);
        }
      }
    }
  }
  const firstOwnerAction = brief.firstOwnerAction || {};
  if (!firstOwnerAction || typeof firstOwnerAction !== "object" || Array.isArray(firstOwnerAction)) {
    issues.push("firstOwnerAction must be an object");
  } else {
    if (!Number.isInteger(firstOwnerAction.order) || firstOwnerAction.order < 1) {
      issues.push("firstOwnerAction.order must be a positive integer");
    }
    for (const field of ["owner", "nextAction", "reason", "command"]) {
      if (typeof firstOwnerAction[field] !== "string" || firstOwnerAction[field].length === 0) {
        issues.push(`firstOwnerAction.${field} must be a non-empty string`);
      }
    }
    if (!Array.isArray(firstOwnerAction.envKeys)) {
      issues.push("firstOwnerAction.envKeys must be an array");
    }
    if (typeof firstOwnerAction.command === "string"
      && firstOwnerAction.command.includes("DDD_RELEASE_ENV_FILE=")
      && !firstOwnerAction.command.includes("DDD_RELEASE_ENV_FILE=<release-env-file>")) {
      issues.push("firstOwnerAction.command must redact DDD_RELEASE_ENV_FILE");
    }
  }
  const orchestratorPreflight = brief.orchestratorPreflight || {};
  if (!orchestratorPreflight || typeof orchestratorPreflight !== "object" || Array.isArray(orchestratorPreflight)) {
    issues.push("orchestratorPreflight must be an object");
  } else {
    for (const field of ["artifact", "mode", "status"]) {
      if (typeof orchestratorPreflight[field] !== "string" || orchestratorPreflight[field].length === 0) {
        issues.push(`orchestratorPreflight.${field} must be a non-empty string`);
      }
    }
    if (path.isAbsolute(orchestratorPreflight.artifact || "") || String(orchestratorPreflight.artifact || "").includes("..")) {
      issues.push("orchestratorPreflight.artifact must be relative and traversal-free");
    }
    if (typeof orchestratorPreflight.strict !== "boolean") {
      issues.push("orchestratorPreflight.strict must be boolean");
    }
    for (const field of ["blockers", "warnings", "selectedStepCount", "executedResultCount"]) {
      if (!Number.isInteger(orchestratorPreflight[field]) || orchestratorPreflight[field] < 0) {
        issues.push(`orchestratorPreflight.${field} must be a non-negative integer`);
      }
    }
    if (!Array.isArray(orchestratorPreflight.ownerActionSummary)) {
      issues.push("orchestratorPreflight.ownerActionSummary must be an array");
    } else {
      if (Number.isInteger(summary.orchestratorPreflightOwners)
        && summary.orchestratorPreflightOwners !== orchestratorPreflight.ownerActionSummary.length) {
        issues.push("blockerSummary.orchestratorPreflightOwners must match orchestratorPreflight.ownerActionSummary length");
      }
      for (const [ownerIndex, owner] of orchestratorPreflight.ownerActionSummary.entries()) {
        const label = `orchestratorPreflight.ownerActionSummary[${ownerIndex}]`;
        if (typeof owner?.owner !== "string" || owner.owner.length === 0) issues.push(`${label}.owner must be a non-empty string`);
        if (!Number.isInteger(owner?.pendingItems) || owner.pendingItems < 0) issues.push(`${label}.pendingItems must be a non-negative integer`);
        if (!Array.isArray(owner?.envKeys)) issues.push(`${label}.envKeys must be an array`);
        if (!Array.isArray(owner?.actions) || owner.actions.length === 0) {
          issues.push(`${label}.actions must be a non-empty array`);
        } else {
          for (const [actionIndex, action] of owner.actions.entries()) {
            const actionLabel = `${label}.actions[${actionIndex}]`;
            if (typeof action?.id !== "string" || action.id.length === 0) issues.push(`${actionLabel}.id must be a non-empty string`);
            if (action?.checkId !== null && typeof action?.checkId !== "string") issues.push(`${actionLabel}.checkId must be string or null`);
            if (typeof action?.reason !== "string") issues.push(`${actionLabel}.reason must be a string`);
            if (!Array.isArray(action?.envKeys)) issues.push(`${actionLabel}.envKeys must be an array`);
            if (typeof action?.action !== "string") issues.push(`${actionLabel}.action must be a string`);
          }
        }
      }
    }
    if (orchestratorPreflight.firstAction !== null) {
      const first = orchestratorPreflight.firstAction || {};
      for (const field of ["owner", "id", "reason", "command"]) {
        if (typeof first[field] !== "string" || first[field].length === 0) {
          issues.push(`orchestratorPreflight.firstAction.${field} must be a non-empty string`);
        }
      }
      if (first.checkId !== null && typeof first.checkId !== "string") {
        issues.push("orchestratorPreflight.firstAction.checkId must be string or null");
      }
      if (!Array.isArray(first.envKeys)) issues.push("orchestratorPreflight.firstAction.envKeys must be an array");
      if (typeof first.command === "string"
        && first.command.includes("DDD_RELEASE_ENV_FILE=")
        && !first.command.includes("DDD_RELEASE_ENV_FILE=<release-env-file>")) {
        issues.push("orchestratorPreflight.firstAction.command must redact DDD_RELEASE_ENV_FILE");
      }
    }
  }
  const ownerInputReceipt = brief.ownerInputReceipt || {};
  if (!ownerInputReceipt || typeof ownerInputReceipt !== "object" || Array.isArray(ownerInputReceipt)) {
    issues.push("ownerInputReceipt must be an object");
  } else {
    if (!["PASS", "PENDING_OWNER_INPUT", "missing"].includes(ownerInputReceipt.status)) {
      issues.push(`ownerInputReceipt.status must be PASS, PENDING_OWNER_INPUT, or missing, got ${ownerInputReceipt.status || "missing"}`);
    }
    if (typeof ownerInputReceipt.cutoverReady !== "boolean") {
      issues.push("ownerInputReceipt.cutoverReady must be boolean");
    }
    if (ownerInputReceipt.status === "PASS" && ownerInputReceipt.cutoverReady !== true) {
      issues.push("ownerInputReceipt PASS requires cutoverReady=true");
    }
    if (ownerInputReceipt.status === "PENDING_OWNER_INPUT" && ownerInputReceipt.cutoverReady !== false) {
      issues.push("ownerInputReceipt pending requires cutoverReady=false");
    }
    for (const field of ["requiredOwnerInputs", "ownerCount", "readyOwnerCount", "pendingOwnerCount"]) {
      if (!Number.isInteger(ownerInputReceipt[field]) || ownerInputReceipt[field] < 0) {
        issues.push(`ownerInputReceipt.${field} must be a non-negative integer`);
      }
    }
    if (Number.isInteger(ownerInputReceipt.ownerCount)
      && Number.isInteger(ownerInputReceipt.readyOwnerCount)
      && Number.isInteger(ownerInputReceipt.pendingOwnerCount)
      && ownerInputReceipt.ownerCount !== ownerInputReceipt.readyOwnerCount + ownerInputReceipt.pendingOwnerCount) {
      issues.push("ownerInputReceipt ownerCount must equal readyOwnerCount + pendingOwnerCount");
    }
    if (!Array.isArray(ownerInputReceipt.missingCriteria)) {
      issues.push("ownerInputReceipt.missingCriteria must be an array");
    } else if (ownerInputReceipt.status === "PENDING_OWNER_INPUT" && ownerInputReceipt.missingCriteria.length === 0) {
      issues.push("ownerInputReceipt pending status must include missing criteria");
    }
    for (const field of ["artifact", "markdown"]) {
      if (typeof ownerInputReceipt[field] !== "string" || ownerInputReceipt[field].length === 0) {
        issues.push(`ownerInputReceipt.${field} must be a non-empty string`);
      } else if (path.isAbsolute(ownerInputReceipt[field]) || ownerInputReceipt[field].includes("..")) {
        issues.push(`ownerInputReceipt.${field} must be relative and traversal-free`);
      }
    }
    if (!Array.isArray(ownerInputReceipt.pendingOwners)) {
      issues.push("ownerInputReceipt.pendingOwners must be an array");
    } else {
      if (ownerInputReceipt.pendingOwners.length > 8) issues.push("ownerInputReceipt.pendingOwners must contain at most eight owners");
      for (const [index, owner] of ownerInputReceipt.pendingOwners.entries()) {
        const label = `ownerInputReceipt.pendingOwners[${index}]`;
        if (typeof owner?.owner !== "string" || owner.owner.length === 0) issues.push(`${label}.owner must be a non-empty string`);
        for (const field of ["requiredOwnerInputs", "remainingPlaceholders", "remainingMissing"]) {
          if (!Number.isInteger(owner?.[field]) || owner[field] < 0) issues.push(`${label}.${field} must be a non-negative integer`);
        }
        if (owner?.ready !== false) issues.push(`${label}.ready must be false`);
        for (const field of ["packetPath", "handoffPath"]) {
          if (typeof owner?.[field] === "string" && owner[field].length > 0
            && (path.isAbsolute(owner[field]) || owner[field].includes(".."))) {
            issues.push(`${label}.${field} must be relative and traversal-free`);
          }
        }
      }
    }
    if (brief.cutoverAllowed === true && ownerInputReceipt.status !== "PASS") {
      issues.push("cutoverAllowed=true requires ownerInputReceipt.status=PASS");
    }
    if (brief.cutoverAllowed === true && ownerInputReceipt.cutoverReady !== true) {
      issues.push("cutoverAllowed=true requires ownerInputReceipt.cutoverReady=true");
    }
  }
  if (!Array.isArray(brief.blockedCutoverItems)) {
    issues.push("blockedCutoverItems must be an array");
  } else {
    const batchIdsFromCutover = new Set();
    if (Number.isInteger(summary.blockedCutoverItems)
      && summary.blockedCutoverItems !== brief.blockedCutoverItems.length) {
      issues.push("blockerSummary.blockedCutoverItems must match blockedCutoverItems length");
    }
    for (const [index, item] of brief.blockedCutoverItems.entries()) {
      const label = `blockedCutoverItems[${index}]`;
      if (typeof item?.id !== "string" || item.id.length === 0) issues.push(`${label}.id must be a non-empty string`);
      if (typeof item?.title !== "string") issues.push(`${label}.title must be a string`);
      if (!Number.isInteger(item?.pendingItems) || item.pendingItems < 0) issues.push(`${label}.pendingItems must be a non-negative integer`);
      if (!Array.isArray(item?.readyBatchIds)) issues.push(`${label}.readyBatchIds must be an array`);
      if (!Array.isArray(item?.blockedBatchIds)) issues.push(`${label}.blockedBatchIds must be an array`);
      for (const [batchIndex, batchId] of [...(item?.readyBatchIds || []), ...(item?.blockedBatchIds || [])].entries()) {
        if (typeof batchId !== "string" || batchId.length === 0) {
          issues.push(`${label}.batchIds[${batchIndex}] must be a non-empty string`);
        }
        batchIdsFromCutover.add(batchId);
      }
      if (!Array.isArray(item?.readyBatches)) {
        issues.push(`${label}.readyBatches must be an array`);
      } else {
        if (Array.isArray(item?.readyBatchIds) && item.readyBatches.length !== item.readyBatchIds.length) {
          issues.push(`${label}.readyBatches length must match readyBatchIds length`);
        }
        for (const [batchIndex, batch] of item.readyBatches.entries()) {
          validateCutoverBatch(batch, `${label}.readyBatches[${batchIndex}]`, issues);
          if (batch?.id) batchIdsFromCutover.add(batch.id);
        }
      }
      if (!Array.isArray(item?.blockedBatches)) {
        issues.push(`${label}.blockedBatches must be an array`);
      } else {
        if (Array.isArray(item?.blockedBatchIds) && item.blockedBatches.length !== item.blockedBatchIds.length) {
          issues.push(`${label}.blockedBatches length must match blockedBatchIds length`);
        }
        for (const [batchIndex, batch] of item.blockedBatches.entries()) {
          validateCutoverBatch(batch, `${label}.blockedBatches[${batchIndex}]`, issues);
          if (batch?.id) batchIdsFromCutover.add(batch.id);
        }
      }
    }
    if (!Array.isArray(brief.executionWaves)) {
      issues.push("executionWaves must be an array");
    } else {
      const batchIdsFromWaves = new Set();
      for (const [index, wave] of brief.executionWaves.entries()) {
        validateExecutionWave(wave, `executionWaves[${index}]`, issues);
        for (const batchId of [...(wave?.runnableBatchIds || []), ...(wave?.blockedBatchIds || [])]) {
          batchIdsFromWaves.add(batchId);
        }
      }
      for (const batchId of batchIdsFromCutover) {
        if (!batchIdsFromWaves.has(batchId)) {
          issues.push(`executionWaves must include cutover batch ${batchId}`);
        }
      }
    }
  }
  if (!Array.isArray(brief.owners)) {
    issues.push("owners must be an array");
  } else {
    let previous = null;
    for (const [index, owner] of brief.owners.entries()) {
      const label = `owners[${index}]`;
      if (!owner?.owner) issues.push(`${label}.owner is required`);
      for (const field of ["blockers", "placeholders", "secretKeys", "totalKeys"]) {
        if (!Number.isInteger(owner?.[field]) || owner[field] < 0) issues.push(`${label}.${field} must be a non-negative integer`);
      }
      if (owner?.handoffPath && (path.isAbsolute(owner.handoffPath) || owner.handoffPath.includes(".."))) {
        issues.push(`${label}.handoffPath must be relative and traversal-free`);
      }
      if (!Array.isArray(owner?.keys)) issues.push(`${label}.keys must be an array`);
      if (previous) {
        const sorted = previous.blockers - owner.blockers
          || previous.placeholders - owner.placeholders
          || owner.owner.localeCompare(previous.owner);
        if (sorted < 0) issues.push("owners must be sorted by blockers desc, placeholders desc, owner asc");
      }
      previous = owner;
    }
  }
  if (!Array.isArray(brief.stopReasons)) {
    issues.push("stopReasons must be an array");
  } else if (Number.isInteger(summary.stopReasons) && summary.stopReasons < brief.stopReasons.length) {
    issues.push("blockerSummary.stopReasons must be greater than or equal to emitted stopReasons length");
  } else if (brief.cutoverAllowed === true && brief.stopReasons.length > 0) {
    issues.push("cutoverAllowed=true requires stopReasons to be empty");
  }
  if (!Array.isArray(brief.handoffReferences) || brief.handoffReferences.length === 0) {
    issues.push("handoffReferences must be a non-empty array");
  } else {
    const seenHandoffIds = new Set();
    const handoffById = new Map();
    for (const [index, reference] of brief.handoffReferences.entries()) {
      const label = `handoffReferences[${index}]`;
      if (!reference?.id) issues.push(`${label}.id is required`);
      if (seenHandoffIds.has(reference?.id)) issues.push(`${label}.id must be unique`);
      seenHandoffIds.add(reference?.id);
      if (reference?.id) handoffById.set(reference.id, reference);
      if (!reference?.label) issues.push(`${label}.label is required`);
      if (!reference?.purpose) issues.push(`${label}.purpose is required`);
      if (!reference?.command) issues.push(`${label}.command is required`);
      if (typeof reference?.command === "string"
        && reference.command.includes("DDD_RELEASE_ENV_FILE=")
        && !reference.command.includes("DDD_RELEASE_ENV_FILE=<release-env-file>")) {
        issues.push(`${label}.command must redact DDD_RELEASE_ENV_FILE`);
      }
      if (typeof reference?.present !== "boolean") issues.push(`${label}.present must be boolean`);
      if (!reference?.path) {
        issues.push(`${label}.path is required`);
      } else if (path.isAbsolute(reference.path) || reference.path.includes("..")) {
        issues.push(`${label}.path must be relative and traversal-free`);
      }
    }
    for (const [id, expectedCommand] of requiredHandoffReferences.entries()) {
      const reference = handoffById.get(id);
      if (!reference) {
        issues.push(`handoffReferences must include required handoff ${id}`);
      } else if (reference.command !== expectedCommand) {
        issues.push(`handoffReferences ${id} command must be ${expectedCommand}`);
      }
    }
  }
  const performanceBaseline = brief.performanceBaseline || {};
  if (!performanceBaseline || typeof performanceBaseline !== "object" || Array.isArray(performanceBaseline)) {
    issues.push("performanceBaseline must be an object");
  } else {
    if (typeof performanceBaseline.status !== "string" || performanceBaseline.status.length === 0) {
      issues.push("performanceBaseline.status must be a non-empty string");
    }
    if (typeof performanceBaseline.readyToPromote !== "boolean") {
      issues.push("performanceBaseline.readyToPromote must be boolean");
    }
    if (!Number.isInteger(performanceBaseline.blockerCount) || performanceBaseline.blockerCount < 0) {
      issues.push("performanceBaseline.blockerCount must be a non-negative integer");
    }
    for (const field of ["requiredEnvKeys", "blockers", "commands"]) {
      if (!Array.isArray(performanceBaseline[field])) issues.push(`performanceBaseline.${field} must be an array`);
    }
    if (Array.isArray(performanceBaseline.blockers)
      && Number.isInteger(performanceBaseline.blockerCount)
      && performanceBaseline.blockerCount !== performanceBaseline.blockers.length) {
      issues.push("performanceBaseline.blockerCount must match blockers length");
    }
    for (const [index, command] of (performanceBaseline.commands || []).entries()) {
      if (typeof command !== "string" || command.length === 0) issues.push(`performanceBaseline.commands[${index}] must be a non-empty string`);
      if (command.includes("DDD_RELEASE_ENV_FILE=") && !command.includes("DDD_RELEASE_ENV_FILE=<release-env-file>")) {
        issues.push(`performanceBaseline.commands[${index}] must redact DDD_RELEASE_ENV_FILE`);
      }
    }
  }
  if (!Array.isArray(brief.nextActions)) {
    issues.push("nextActions must be an array");
  } else {
    if (brief.nextActions.length > 5) issues.push("nextActions must contain at most five RUN_NOW actions");
    let previousOrder = 0;
    for (const [index, action] of brief.nextActions.entries()) {
      const label = `nextActions[${index}]`;
      if (!Number.isInteger(action?.order) || action.order < 1) issues.push(`${label}.order must be a positive integer`);
      if (Number.isInteger(action?.order) && action.order < previousOrder) issues.push("nextActions must be sorted by order asc");
      if (!action?.owner) issues.push(`${label}.owner is required`);
      if (action?.queueStatus !== "RUN_NOW") issues.push(`${label}.queueStatus must be RUN_NOW`);
      if (!action?.receiptStatus) issues.push(`${label}.receiptStatus is required`);
      if (typeof action?.nextAction !== "string" || action.nextAction.length === 0) issues.push(`${label}.nextAction must be a non-empty string`);
      if (typeof action?.reason !== "string") issues.push(`${label}.reason must be a string`);
      if (!Array.isArray(action?.envKeys)) issues.push(`${label}.envKeys must be an array`);
      if (!Array.isArray(action?.executableCommands)) {
        issues.push(`${label}.executableCommands must be an array`);
      } else {
        for (const [commandIndex, command] of action.executableCommands.entries()) {
          if (typeof command !== "string" || command.length === 0) issues.push(`${label}.executableCommands[${commandIndex}] must be a non-empty string`);
          if (command.includes("DDD_RELEASE_ENV_FILE=") && !command.includes("DDD_RELEASE_ENV_FILE=<release-env-file>")) {
            issues.push(`${label}.executableCommands[${commandIndex}] must redact DDD_RELEASE_ENV_FILE`);
          }
        }
      }
      previousOrder = Number.isInteger(action?.order) ? action.order : previousOrder;
    }
  }
  if (!Array.isArray(brief.nextCommands)) {
    issues.push("nextCommands must be an array");
  } else {
    for (const [index, command] of brief.nextCommands.entries()) {
      if (typeof command !== "string" || command.length === 0) issues.push(`nextCommands[${index}] must be a non-empty string`);
      if (command.includes("DDD_RELEASE_ENV_FILE=") && !command.includes("DDD_RELEASE_ENV_FILE=<release-env-file>")) {
        issues.push(`nextCommands[${index}] must redact DDD_RELEASE_ENV_FILE`);
      }
    }
  }
  const combined = `${JSON.stringify(brief)}\n${markdown}`;
  for (const pattern of forbiddenPatterns) {
    if (pattern.test(combined)) issues.push(`brief must not expose sensitive or concrete release values matching ${pattern}`);
  }
  if (markdown) {
    if (!markdown.includes("# DDD Release Unblock Brief")) issues.push("markdown title is required");
    if (!markdown.includes("## Release Env Safety")) issues.push("markdown release env safety section is required");
    if (!markdown.includes("releaseEnvFileCutoverSafe:")) issues.push("markdown release env safety must include releaseEnvFileCutoverSafe");
    if (!markdown.includes("Cutover safe:")) issues.push("markdown release env safety must include cutover safe status");
    if (!markdown.includes("## First Owner Action")) issues.push("markdown first owner action section is required");
    if (!markdown.includes("## Orchestrator Preflight")) issues.push("markdown orchestrator preflight section is required");
    if (!markdown.includes("Orchestrator preflight blockers:")) issues.push("markdown must include orchestrator preflight blocker count");
    if (!markdown.includes("## Owner Input Receipt")) issues.push("markdown owner input receipt section is required");
    if (!markdown.includes("Required owner inputs:")) issues.push("markdown owner input receipt must include required owner input count");
    if (!markdown.includes("Missing criteria:")) issues.push("markdown owner input receipt must include missing criteria");
    if (!markdown.includes("## Blocked Cutover Items")) issues.push("markdown blocked cutover items section is required");
    if (!markdown.includes("Cutover batch details:")) issues.push("markdown blocked cutover section must include batch details");
    if (!markdown.includes("Execution waves:")) issues.push("markdown blocked cutover section must include execution waves");
    if (!markdown.includes("Wave operator commands:")) issues.push("markdown blocked cutover section must include wave operator commands");
    if (!markdown.includes("## Final Owner Queue Fast Path")) issues.push("markdown final owner queue fast path section is required");
    if (!markdown.includes("DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh")) {
      issues.push("markdown final owner queue fast path must include strict final go/no-go gate command");
    }
    if (!markdown.includes("## Fastest Safe Path")) issues.push("markdown fastest safe path section is required");
    if (!markdown.includes("## Owner Env Handoff")) issues.push("markdown owner env handoff section is required");
    if (!markdown.includes("## Evidence Handoffs")) issues.push("markdown evidence handoffs section is required");
    for (const [id, expectedCommand] of requiredHandoffReferences.entries()) {
      if (!markdown.includes(expectedCommand)) {
        issues.push(`markdown evidence handoffs must include required command for ${id}`);
      }
    }
    if (!markdown.includes("## Performance Baseline")) issues.push("markdown performance baseline section is required");
    if (!markdown.includes("## Next Action Queue")) issues.push("markdown next action queue section is required");
  }
  return issues;
}

export { validateReleaseUnblockBrief };

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const brief = readJson(jsonPath);
  const markdown = readText(markdownPath);
  const issues = validateReleaseUnblockBrief(brief, markdown);
  if (issues.length > 0) {
    for (const issue of issues) console.error(`[ddd-release-unblock-brief-contract][blocker] ${issue}`);
    process.exit(1);
  }
  console.log(`[ddd-release-unblock-brief-contract] ok recommendation=${brief.recommendation} cutoverAllowed=${brief.cutoverAllowed}`);
}
