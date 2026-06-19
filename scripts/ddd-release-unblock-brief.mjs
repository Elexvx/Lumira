#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(import.meta.dirname, "..");
const releaseDir = process.env.DDD_RELEASE_DIR
  ? path.resolve(process.env.DDD_RELEASE_DIR)
  : path.join(repoRoot, "artifacts", "ddd", "release");
const outputJson = process.env.DDD_RELEASE_UNBLOCK_BRIEF_JSON
  ? path.resolve(process.env.DDD_RELEASE_UNBLOCK_BRIEF_JSON)
  : path.join(releaseDir, "release-unblock-brief.json");
const outputMarkdown = process.env.DDD_RELEASE_UNBLOCK_BRIEF_MD
  ? path.resolve(process.env.DDD_RELEASE_UNBLOCK_BRIEF_MD)
  : path.join(releaseDir, "release-unblock-brief.md");

function readJson(fileName, fallback = null) {
  const file = path.join(releaseDir, fileName);
  if (!fs.existsSync(file)) return fallback;
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

function firstInteger(...values) {
  for (const value of values) {
    const number = Number(value);
    if (Number.isInteger(number) && number >= 0) return number;
  }
  return 0;
}

function redactCommand(command) {
  return redactLocalPaths(String(command || "")
    .replace(/(TOKEN|SECRET|PASSWORD|API_KEY)=("[^"]*"|'[^']*'|[^\s]+)/g, "$1=<redacted>")
    .replace(/(DDD_RELEASE_ENV_FILE=)([^\s]+)/g, "$1<release-env-file>")
    .replace(/__REQUIRED__/g, "<placeholder>"));
}

function redactLocalPaths(text) {
  return String(text || "")
    .replaceAll(`${repoRoot}${path.sep}`, "")
    .replaceAll("\\", "/")
    .replace(/__REQUIRED__/g, "<placeholder>");
}

function buildPerformanceBaselineSummary(finalGoNoGo, performanceClosure) {
  const source = performanceClosure && typeof performanceClosure === "object"
    ? performanceClosure
    : finalGoNoGo?.performanceBaseline || {};
  const blockers = asArray(source.blockers).map(redactLocalPaths).slice(0, 8);
  const requiredEnvKeys = unique(asArray(source.requiredEnvKeys).map(String)).sort();
  const commands = asArray(source.commands).map(redactCommand).slice(0, 8);
  return {
    status: source.status || finalGoNoGo?.performanceBaseline?.status || "UNKNOWN",
    readyToPromote: source.readyToPromote === true,
    blockerCount: blockers.length,
    requiredEnvKeys,
    blockers,
    commands,
  };
}

function buildNextActionSummary(nextActionQueue) {
  return asArray(nextActionQueue?.items)
    .filter((item) => item.queueStatus === "RUN_NOW")
    .slice(0, 5)
    .map((item) => ({
      order: firstInteger(item.order),
      owner: item.owner || "unknown",
      queueStatus: item.queueStatus || "UNKNOWN",
      receiptStatus: item.receiptStatus || "UNKNOWN",
      nextAction: redactCommand(item.nextAction || ""),
      reason: redactCommand(item.reason || ""),
      envKeys: unique(asArray(item.envKeys).map(String)).sort(),
      executableCommands: asArray(item.executableCommands).map(redactCommand).slice(0, 5),
    }));
}

function buildFinalOwnerQueueFastPath(finalOwnerQueue) {
  const fastPath = finalOwnerQueue?.fastPath || {};
  const commands = asArray(fastPath.commands).map(redactCommand).slice(0, 10);
  return {
    objective: redactLocalPaths(fastPath.objective || ""),
    blockedUntil: redactLocalPaths(fastPath.blockedUntil || ""),
    owner: fastPath.owner || finalOwnerQueue?.summary?.nextExecutableOwner || "unknown",
    queueOrder: firstInteger(fastPath.queueOrder, finalOwnerQueue?.summary?.nextExecutableQueueOrder),
    firstCommand: redactCommand(fastPath.firstCommand || finalOwnerQueue?.summary?.nextExecutableCommand || commands[0] || ""),
    envKeyCount: firstInteger(fastPath.envKeyCount, finalOwnerQueue?.summary?.nextExecutableEnvKeyCount),
    missingArtifactCount: firstInteger(fastPath.missingArtifactCount, finalOwnerQueue?.summary?.nextExecutableMissingArtifactCount),
    releaseEnvFileRequired: fastPath.releaseEnvFileRequired === true,
    finalGateCommand: redactCommand(fastPath.finalGateCommand || "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh"),
    commands,
  };
}

function buildReleaseEnvSafety(finalGoNoGo) {
  const releaseEnvFile = finalGoNoGo?.safetySignals?.releaseEnvFile || {};
  return {
    cutoverSafe: finalGoNoGo?.releaseEnvFileCutoverSafe === true,
    ready: releaseEnvFile.ready === true,
    status: releaseEnvFile.status || "missing",
    inputKind: releaseEnvFile.inputKind || "missing",
    envFilePresent: releaseEnvFile.envFilePresent === true,
    generatedMissingTemplate: releaseEnvFile.generatedMissingTemplate === true,
    securityChecked: releaseEnvFile.securityChecked === true,
    permissionSafe: releaseEnvFile.permissionSafe === true,
    permissionCheckSkipped: releaseEnvFile.permissionCheckSkipped === true,
    modeOctal: releaseEnvFile.modeOctal || "missing",
    requiredMode: releaseEnvFile.requiredMode || "600",
    pendingActionIds: unique(asArray(releaseEnvFile.pendingActionIds).map(String)).sort(),
    blockingSafeDefaultAvailable: firstInteger(releaseEnvFile.blockingSafeDefaultAvailable),
    blockingRequiresOwnerInput: firstInteger(releaseEnvFile.blockingRequiresOwnerInput),
    safeDefaultsExhausted: releaseEnvFile.safeDefaultsExhausted === true,
    ownerInputReasonCounts: releaseEnvFile.ownerInputReasonCounts && typeof releaseEnvFile.ownerInputReasonCounts === "object"
      ? Object.fromEntries(Object.entries(releaseEnvFile.ownerInputReasonCounts)
        .map(([reason, count]) => [reason, firstInteger(count)])
        .sort(([left], [right]) => left.localeCompare(right)))
      : {},
    ownerInputOwners: asArray(releaseEnvFile.ownerInputOwners)
      .map((owner) => ({
        owner: owner.owner || "unknown",
        requiresOwnerInput: firstInteger(owner.requiresOwnerInput),
        safeDefaultAvailable: firstInteger(owner.safeDefaultAvailable),
      }))
      .sort((left, right) => right.requiresOwnerInput - left.requiresOwnerInput || left.owner.localeCompare(right.owner)),
  };
}

function buildSafetySignals(finalGoNoGo) {
  return {
    releaseEnvFile: finalGoNoGo?.safetySignals?.releaseEnvFile || {},
  };
}

function normalizeFirstOwnerAction(action) {
  if (!action || typeof action !== "object") return null;
  const commands = [
    action.command,
    action.firstOwnerActionCommand,
    ...asArray(action.executableCommands),
  ].filter(Boolean);
  const command = commands.length > 0 ? redactCommand(commands[0]) : "";
  return {
    order: firstInteger(action.order, 1) || 1,
    owner: action.owner || "unknown",
    nextAction: redactCommand(action.nextAction || action.action || ""),
    reason: redactCommand(action.reason || ""),
    command,
    envKeys: unique(asArray(action.envKeys).map(String)).sort(),
  };
}

function buildFirstOwnerAction(finalGoNoGo, nextActionQueue) {
  const ciAction = normalizeFirstOwnerAction({
    ...finalGoNoGo?.ciSummary?.firstOwnerAction,
    command: finalGoNoGo?.ciSummary?.firstOwnerAction?.command
      || finalGoNoGo?.ciSummary?.firstOwnerActionCommand,
  });
  if (ciAction?.owner && ciAction.nextAction && ciAction.command) return ciAction;

  const nextAction = asArray(nextActionQueue?.items)
    .filter((item) => item.queueStatus === "RUN_NOW")
    .sort((left, right) => firstInteger(left.order) - firstInteger(right.order))[0];
  return normalizeFirstOwnerAction(nextAction) || {
    order: 1,
    owner: "release-owner",
    nextAction: "Review release-final-go-no-go.json and release-next-action-queue.json.",
    reason: "No RUN_NOW owner action was present in the release queue.",
    command: "node scripts/ddd-release-final-go-no-go.mjs",
    envKeys: [],
  };
}

function buildOrchestratorPreflightSummary(finalGoNoGo) {
  const preflight = finalGoNoGo?.ciSummary?.orchestratorPreflight || {};
  const ownerActionSummary = asArray(preflight.ownerActionSummary)
    .map((owner) => ({
      owner: owner.owner || "unknown",
      pendingItems: firstInteger(owner.pendingItems, asArray(owner.actions).length),
      envKeys: unique(asArray(owner.envKeys).map(String)).sort(),
      actions: asArray(owner.actions)
        .map((action) => ({
          id: action.id || "unknown",
          checkId: action.checkId || null,
          reason: redactCommand(action.reason || ""),
          envKeys: unique(asArray(action.envKeys).map(String)).sort(),
          action: redactCommand(action.action || ""),
        }))
        .slice(0, 8),
    }))
    .filter((owner) => owner.actions.length > 0)
    .sort((left, right) => right.actions.length - left.actions.length || left.owner.localeCompare(right.owner));
  const firstAction = finalGoNoGo?.ciSummary?.firstOrchestratorPreflightAction || null;
  return {
    artifact: preflight.artifact || "artifacts/ddd/release/orchestrator-report.json",
    mode: preflight.mode || "missing",
    strict: preflight.strict === true,
    status: preflight.status || "missing",
    blockers: firstInteger(preflight.blockers),
    warnings: firstInteger(preflight.warnings),
    selectedStepCount: firstInteger(preflight.selectedStepCount),
    executedResultCount: firstInteger(preflight.executedResultCount),
    ownerActionSummary,
    firstAction: firstAction ? {
      owner: firstAction.owner || "unknown",
      id: firstAction.id || "unknown",
      checkId: firstAction.checkId || null,
      reason: redactLocalPaths(firstAction.reason || ""),
      envKeys: unique(asArray(firstAction.envKeys).map(String)).sort(),
      command: redactCommand(firstAction.command || "DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict"),
    } : null,
  };
}

function buildOwnerInputReceiptSummary(finalGoNoGo, ownerInputReceipt) {
  const source = ownerInputReceipt && typeof ownerInputReceipt === "object"
    ? ownerInputReceipt
    : finalGoNoGo?.ciSummary?.ownerInputReceipt || {};
  const sourceSummary = source.summary && typeof source.summary === "object" ? source.summary : {};
  const missingCriteria = unique(asArray(source.missingCriteria).map(String)).sort();
  const ownerReceipts = asArray(source.ownerReceipts)
    .map((owner) => ({
      owner: owner.owner || "unknown",
      requiredOwnerInputs: firstInteger(owner.requiredOwnerInputs),
      remainingPlaceholders: firstInteger(owner.remainingPlaceholders),
      remainingMissing: firstInteger(owner.remainingMissing),
      ready: owner.ready === true,
      packetPath: redactLocalPaths(owner.packetPath || ""),
      handoffPath: redactLocalPaths(owner.handoffPath || ""),
    }))
    .sort((left, right) => right.requiredOwnerInputs - left.requiredOwnerInputs
      || left.owner.localeCompare(right.owner));
  const pendingOwners = ownerReceipts.filter((owner) => owner.ready !== true);
  return {
    artifact: redactLocalPaths(source.artifact || "artifacts/ddd/release/release-owner-input-receipt.json"),
    markdown: redactLocalPaths(source.markdown || "artifacts/ddd/release/release-owner-input-receipt.md"),
    status: source.status || "missing",
    cutoverReady: source.cutoverReady === true,
    requiredOwnerInputs: firstInteger(source.requiredOwnerInputs, sourceSummary.requiredOwnerInputs),
    ownerCount: firstInteger(source.ownerCount, sourceSummary.ownerCount, ownerReceipts.length),
    readyOwnerCount: firstInteger(source.readyOwnerCount, sourceSummary.readyOwnerCount, ownerReceipts.filter((owner) => owner.ready === true).length),
    pendingOwnerCount: firstInteger(source.pendingOwnerCount, sourceSummary.pendingOwnerCount, pendingOwners.length),
    missingCriteria,
    pendingOwners: pendingOwners.slice(0, 8),
  };
}

function buildActionBatchMap(actionBatches) {
  return new Map(asArray(actionBatches?.batches)
    .filter((batch) => batch?.id)
    .map((batch) => [batch.id, {
      id: batch.id,
      priority: batch.priority || "UNKNOWN",
      source: batch.source || "unknown",
      owner: batch.owner || "unknown",
      pendingItems: firstInteger(batch.pendingItems),
      canRunImmediately: batch.canRunImmediately === true,
      dependsOn: unique(asArray(batch.dependsOn).map(String)).sort(),
      commands: asArray(batch.commands).map(redactCommand).slice(0, 6),
      expectedArtifacts: asArray(batch.expectedArtifacts).map(redactLocalPaths).slice(0, 8),
    }]));
}

function batchDetailsFor(batchIds, batchById) {
  return batchIds.map((batchId) => batchById.get(batchId) || {
    id: batchId,
    priority: "UNKNOWN",
    source: "unknown",
    owner: "unknown",
    pendingItems: 0,
    canRunImmediately: false,
    dependsOn: [],
    commands: [],
    expectedArtifacts: [],
  });
}

function buildBlockedCutoverItems(finalGoNoGo, actionBatches) {
  const batchById = buildActionBatchMap(actionBatches);
  return asArray(finalGoNoGo?.blockedCutoverItems)
    .map((item) => {
      const readyBatchIds = unique(asArray(item.readyBatchIds).map(String)).sort();
      const blockedBatchIds = unique(asArray(item.blockedBatchIds).map(String)).sort();
      return {
        id: item.id || "unknown",
        title: redactLocalPaths(item.title || ""),
        pendingItems: firstInteger(item.pendingItems),
        readyBatchIds,
        blockedBatchIds,
        readyBatches: batchDetailsFor(readyBatchIds, batchById),
        blockedBatches: batchDetailsFor(blockedBatchIds, batchById),
      };
    })
    .filter((item) => item.id !== "unknown" || item.title.length > 0);
}

function priorityRank(priority) {
  const match = /^P(\d+)$/i.exec(String(priority || ""));
  return match ? Number(match[1]) : 99;
}

function buildWaveOperatorCommands(priority, hasRunnableBatches) {
  if (!hasRunnableBatches) return [];
  const script = "bash artifacts/ddd/release/release-execution-commands.sh";
  const prefix = `DDD_RELEASE_PRIORITY=${priority}`;
  const envPrefix = `DDD_RELEASE_ENV_FILE=<release-env-file> ${prefix}`;
  return [
    `${prefix} DDD_RELEASE_LIST_BATCHES=1 ${script}`,
    `${envPrefix} DDD_RELEASE_CHECK_ENV_ONLY=1 ${script}`,
    `${prefix} DDD_RELEASE_DRY_RUN=1 ${script}`,
    `${envPrefix} ${script}`,
  ];
}

function buildExecutionWaves(blockedCutoverItems) {
  const batchById = new Map();
  for (const item of blockedCutoverItems || []) {
    for (const batch of [...asArray(item.readyBatches), ...asArray(item.blockedBatches)]) {
      if (!batchById.has(batch.id)) {
        batchById.set(batch.id, batch);
      }
    }
  }
  const waves = new Map();
  for (const batch of batchById.values()) {
    const priority = batch.priority || "UNKNOWN";
    if (!waves.has(priority)) {
      waves.set(priority, {
        priority,
        batchCount: 0,
        runnableBatchIds: [],
        blockedBatchIds: [],
        owners: [],
        dependsOn: [],
        commandCount: 0,
      });
    }
    const wave = waves.get(priority);
    wave.batchCount += 1;
    if (batch.canRunImmediately) {
      wave.runnableBatchIds.push(batch.id);
    } else {
      wave.blockedBatchIds.push(batch.id);
    }
    wave.owners.push(batch.owner);
    wave.dependsOn.push(...asArray(batch.dependsOn));
    wave.commandCount += asArray(batch.commands).length;
  }
  return [...waves.values()]
    .map((wave) => ({
      ...wave,
      runnableBatchIds: unique(wave.runnableBatchIds).sort(),
      blockedBatchIds: unique(wave.blockedBatchIds).sort(),
      owners: unique(wave.owners).sort(),
      dependsOn: unique(wave.dependsOn).sort(),
      operatorCommands: buildWaveOperatorCommands(wave.priority, wave.runnableBatchIds.length > 0),
    }))
    .sort((left, right) => priorityRank(left.priority) - priorityRank(right.priority)
      || left.priority.localeCompare(right.priority));
}

function handoffReference(id, label, relativePath, purpose, command) {
  const absolutePath = path.join(repoRoot, relativePath);
  return {
    id,
    label,
    path: relativePath,
    present: fs.existsSync(absolutePath),
    purpose,
    command,
  };
}

function buildHandoffReferences() {
  return [
    handoffReference(
      "migration-evidence-handoff",
      "Migration evidence handoff",
      "artifacts/ddd/migration/migration-evidence-handoff.md",
      "Fill production-equivalent fresh DB and previous-schema upgrade Flyway evidence before regenerating migration-evidence.json.",
      "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
    ),
    handoffReference(
      "rollback-deferral-owner-handoff",
      "Rollback deferral owner handoff",
      "artifacts/ddd/rollback/rollback-deferrals-owner-handoff/README.md",
      "Coordinate real PASS rollback drills or approved DEFERRED risk acceptance by bounded-context owner.",
      "node scripts/ddd-rollback-deferral-template.mjs",
    ),
    handoffReference(
      "performance-baseline-handoff",
      "Authenticated performance baseline handoff",
      "artifacts/ddd/release/release-performance-baseline-commands.sh",
      "Check env readiness, run production-equivalent authenticated performance smoke, and promote the accepted baseline without using local-only evidence.",
      "DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh",
    ),
    handoffReference(
      "release-env-owner-input-packet",
      "Release env owner input packet",
      "artifacts/ddd/release/release-env-owner-input-packet.md",
      "Collect the remaining real production-equivalent endpoints, secrets, and owner values without exposing concrete values in artifacts.",
      "node scripts/ddd-release-env-owner-input-packet-contract.mjs",
    ),
    handoffReference(
      "release-owner-input-receipt",
      "Release owner input receipt",
      "artifacts/ddd/release/release-owner-input-receipt.md",
      "Confirm whether every owner-supplied production value is reconciled with env readiness before allowing strict cutover.",
      "node scripts/ddd-release-owner-input-receipt-contract.mjs",
    ),
  ];
}

function buildUnblockBrief({ finalGoNoGo, envHandoff, performanceClosure, nextActionQueue, finalOwnerQueue, actionBatches, ownerInputReceipt }) {
  const stopReasons = unique([
    ...asArray(finalGoNoGo?.currentStopReasons),
    ...asArray(finalGoNoGo?.stopReasons),
  ]);
  const nextCommands = asArray(finalGoNoGo?.nextCommands).map(redactCommand);
  const blockedCutoverItems = asArray(finalGoNoGo?.blockedCutoverItems);
  const owners = asArray(envHandoff?.owners)
    .map((owner) => ({
      owner: owner.owner,
      blockers: Number(owner.blockers || 0),
      placeholders: Number(owner.placeholders || 0),
      secretKeys: Number(owner.secretKeys || 0),
      safeDefaultAvailable: Number(owner.safeDefaultAvailable || 0),
      requiresOwnerInput: Number(owner.requiresOwnerInput || 0),
      ownerInputReasons: asArray(owner.ownerInputReasons).map(String).sort(),
      totalKeys: Number(owner.total || owner.keys?.length || 0),
      handoffPath: owner.handoffPath,
      keys: asArray(owner.keys),
    }))
    .sort((left, right) => right.blockers - left.blockers
      || right.placeholders - left.placeholders
      || left.owner.localeCompare(right.owner));
  const firstEnvOwners = owners.filter((owner) => owner.blockers > 0).slice(0, 6);
  const ownerBlockers = firstEnvOwners.reduce((sum, owner) => sum + owner.blockers, 0);
  const nextOwnerCommands = nextCommands.filter((command) => (
    command.includes("release-env")
    || command.includes("release-config")
    || command.includes("release-evidence-manifest")
    || command.includes("authenticated-performance")
  )).slice(0, 10);
  const safetySignals = buildSafetySignals(finalGoNoGo);
  const releaseEnvSafety = buildReleaseEnvSafety(finalGoNoGo);
  const orchestratorPreflight = buildOrchestratorPreflightSummary(finalGoNoGo);
  const ownerInputReceiptSummary = buildOwnerInputReceiptSummary(finalGoNoGo, ownerInputReceipt);
  const blockedCutoverItemsSummary = buildBlockedCutoverItems(finalGoNoGo, actionBatches);
  const executionWaves = buildExecutionWaves(blockedCutoverItemsSummary);
  return {
    generatedAt: new Date().toISOString(),
    status: finalGoNoGo?.status || "UNKNOWN",
    recommendation: finalGoNoGo?.finalRecommendation || finalGoNoGo?.recommendation || "UNKNOWN",
    cutoverAllowed: finalGoNoGo?.cutoverAllowed === true,
    noAutoWaivers: finalGoNoGo?.noAutoWaivers === true,
    safetySignals,
    releaseEnvFileCutoverSafe: releaseEnvSafety.cutoverSafe,
    blockerSummary: {
      strictGateBlockers: firstInteger(finalGoNoGo?.strictGateBlockers, finalGoNoGo?.gate?.blockers),
      envOwnerBlockers: ownerBlockers,
      envOwnerCount: owners.length,
      orchestratorPreflightBlockers: orchestratorPreflight.blockers,
      orchestratorPreflightOwners: orchestratorPreflight.ownerActionSummary.length,
      blockedCutoverItems: firstInteger(finalGoNoGo?.summary?.blockedCutoverItems, blockedCutoverItemsSummary.length, blockedCutoverItems.length),
      stopReasons: firstInteger(finalGoNoGo?.summary?.stopReasons, stopReasons.length),
    },
    releaseEnvSafety,
    orchestratorPreflight,
    ownerInputReceipt: ownerInputReceiptSummary,
    blockedCutoverItems: blockedCutoverItemsSummary,
    executionWaves,
    firstOwnerAction: buildFirstOwnerAction(finalGoNoGo, nextActionQueue),
    finalOwnerQueueFastPath: buildFinalOwnerQueueFastPath(finalOwnerQueue),
    fastestSafePath: [
      "Fill only the listed owner keys in the release env file; do not paste values into chat or artifacts.",
      "Run env bootstrap, owner template merge, canonical merge, alias sync, canonical lint, and env file lint before any runtime evidence.",
      "Collect HTTPS production-equivalent runtime, migration, Docker image, manifest, and authenticated performance evidence.",
      "Run strict preflight only after env readiness and production-equivalent evidence are clean.",
    ],
    owners: firstEnvOwners,
    handoffReferences: buildHandoffReferences(),
    performanceBaseline: buildPerformanceBaselineSummary(finalGoNoGo, performanceClosure),
    nextActions: buildNextActionSummary(nextActionQueue),
    stopReasons: stopReasons.slice(0, 12),
    nextCommands: nextOwnerCommands,
  };
}

function renderMarkdown(brief) {
  const lines = [
    "# DDD Release Unblock Brief",
    "",
    `Generated at: ${brief.generatedAt}`,
    `Recommendation: ${brief.recommendation}`,
    `Cutover allowed: ${brief.cutoverAllowed}`,
    `No auto waivers: ${brief.noAutoWaivers}`,
    `releaseEnvFileCutoverSafe: ${brief.releaseEnvFileCutoverSafe}`,
    `Strict gate blockers: ${brief.blockerSummary.strictGateBlockers}`,
    `Env owner blockers: ${brief.blockerSummary.envOwnerBlockers}`,
    `Orchestrator preflight blockers: ${brief.blockerSummary.orchestratorPreflightBlockers}`,
    "",
    "## Release Env Safety",
    "",
    `Cutover safe: ${brief.releaseEnvSafety.cutoverSafe}`,
    `Ready: ${brief.releaseEnvSafety.ready}`,
    `Status: ${brief.releaseEnvSafety.status}`,
    `Input kind: ${brief.releaseEnvSafety.inputKind}`,
    `Env file present: ${brief.releaseEnvSafety.envFilePresent}`,
    `Generated missing template: ${brief.releaseEnvSafety.generatedMissingTemplate}`,
    `Security checked: ${brief.releaseEnvSafety.securityChecked}`,
    `Permission safe: ${brief.releaseEnvSafety.permissionSafe}`,
    `Permission check skipped: ${brief.releaseEnvSafety.permissionCheckSkipped}`,
    `Mode: ${brief.releaseEnvSafety.modeOctal}`,
    `Required mode: ${brief.releaseEnvSafety.requiredMode}`,
    `Blocking safe defaults available: ${brief.releaseEnvSafety.blockingSafeDefaultAvailable}`,
    `Blocking values requiring owner input: ${brief.releaseEnvSafety.blockingRequiresOwnerInput}`,
    `Safe defaults exhausted: ${brief.releaseEnvSafety.safeDefaultsExhausted}`,
    "",
    "Owner input reasons:",
    "",
    ...(Object.keys(brief.releaseEnvSafety.ownerInputReasonCounts).length > 0
      ? Object.entries(brief.releaseEnvSafety.ownerInputReasonCounts).map(([reason, count]) => `- ${reason}: ${count}`)
      : ["- none"]),
    "",
    "Owner input owners:",
    "",
    ...(brief.releaseEnvSafety.ownerInputOwners.length > 0
      ? brief.releaseEnvSafety.ownerInputOwners.map((owner) => `- ${owner.owner}: requiresOwnerInput=${owner.requiresOwnerInput}, safeDefaultAvailable=${owner.safeDefaultAvailable}`)
      : ["- none"]),
    "",
    "Pending release env actions:",
    "",
    ...(brief.releaseEnvSafety.pendingActionIds.length > 0
      ? brief.releaseEnvSafety.pendingActionIds.map((id) => `- ${id}`)
      : ["- none"]),
    "",
    "## First Owner Action",
    "",
    `Owner: ${brief.firstOwnerAction.owner}`,
    `Order: ${brief.firstOwnerAction.order}`,
    `Reason: ${brief.firstOwnerAction.reason}`,
    `Next action: ${brief.firstOwnerAction.nextAction}`,
    `Command: \`${brief.firstOwnerAction.command}\``,
    "",
    "Env keys:",
    "",
    ...brief.firstOwnerAction.envKeys.map((key) => `- ${key}`),
    "",
    "## Orchestrator Preflight",
    "",
    `Artifact: ${brief.orchestratorPreflight.artifact}`,
    `Mode: ${brief.orchestratorPreflight.mode}`,
    `Strict: ${brief.orchestratorPreflight.strict}`,
    `Status: ${brief.orchestratorPreflight.status}`,
    `Blockers: ${brief.orchestratorPreflight.blockers}`,
    `Warnings: ${brief.orchestratorPreflight.warnings}`,
    `Selected steps: ${brief.orchestratorPreflight.selectedStepCount}`,
    `Executed results: ${brief.orchestratorPreflight.executedResultCount}`,
    "",
    "First preflight action:",
    "",
    ...(brief.orchestratorPreflight.firstAction ? [
      `- Owner: ${brief.orchestratorPreflight.firstAction.owner}`,
      `- Check: ${brief.orchestratorPreflight.firstAction.checkId || brief.orchestratorPreflight.firstAction.id}`,
      `- Reason: ${brief.orchestratorPreflight.firstAction.reason}`,
      `- Command: \`${brief.orchestratorPreflight.firstAction.command}\``,
      `- Env keys: ${brief.orchestratorPreflight.firstAction.envKeys.join(",") || "none"}`,
    ] : ["- none"]),
    "",
    "| Owner | Actions | Env keys | First check | First reason |",
    "|---|---:|---|---|---|",
    ...brief.orchestratorPreflight.ownerActionSummary.map((owner) => {
      const firstAction = owner.actions[0] || {};
      return `| ${owner.owner} | ${owner.actions.length} | ${owner.envKeys.join(",") || "none"} | ${firstAction.checkId || firstAction.id || ""} | ${firstAction.reason || ""} |`;
    }),
    "",
    "## Owner Input Receipt",
    "",
    `Status: ${brief.ownerInputReceipt.status}`,
    `Cutover ready: ${brief.ownerInputReceipt.cutoverReady}`,
    `Required owner inputs: ${brief.ownerInputReceipt.requiredOwnerInputs}`,
    `Owners: ${brief.ownerInputReceipt.ownerCount}`,
    `Ready owners: ${brief.ownerInputReceipt.readyOwnerCount}`,
    `Pending owners: ${brief.ownerInputReceipt.pendingOwnerCount}`,
    `Artifact: ${brief.ownerInputReceipt.artifact}`,
    `Markdown: ${brief.ownerInputReceipt.markdown}`,
    "",
    "Missing criteria:",
    "",
    ...(brief.ownerInputReceipt.missingCriteria.length > 0
      ? brief.ownerInputReceipt.missingCriteria.map((criteria) => `- ${criteria}`)
      : ["- none"]),
    "",
    "| Owner | Required inputs | Remaining placeholders | Remaining missing | Packet | Handoff |",
    "|---|---:|---:|---:|---|---|",
    ...brief.ownerInputReceipt.pendingOwners.map((owner) => `| ${owner.owner} | ${owner.requiredOwnerInputs} | ${owner.remainingPlaceholders} | ${owner.remainingMissing} | ${owner.packetPath || "n/a"} | ${owner.handoffPath || "n/a"} |`),
    "",
    "## Blocked Cutover Items",
    "",
    "| Item | Pending | Ready batches | Blocked batches | Title |",
    "|---|---:|---|---|---|",
    ...brief.blockedCutoverItems.map((item) => `| ${item.id} | ${item.pendingItems} | ${item.readyBatchIds.join(",") || "none"} | ${item.blockedBatchIds.join(",") || "none"} | ${item.title} |`),
    "",
    "Cutover batch details:",
    "",
    "| Cutover item | Batch | Owner | Priority | Runnable | Depends on | Commands |",
    "|---|---|---|---|---|---|---|",
    ...brief.blockedCutoverItems.flatMap((item) => [
      ...item.readyBatches.map((batch) => `| ${item.id} | ${batch.id} | ${batch.owner} | ${batch.priority} | ${batch.canRunImmediately} | ${batch.dependsOn.join(",") || "none"} | ${batch.commands.join("<br>") || "none"} |`),
      ...item.blockedBatches.map((batch) => `| ${item.id} | ${batch.id} | ${batch.owner} | ${batch.priority} | ${batch.canRunImmediately} | ${batch.dependsOn.join(",") || "none"} | ${batch.commands.join("<br>") || "none"} |`),
    ]),
    "",
    "Execution waves:",
    "",
    "| Wave | Batches | Runnable | Blocked | Owners | Depends on | Commands |",
    "|---|---:|---|---|---|---|---:|",
    ...brief.executionWaves.map((wave) => `| ${wave.priority} | ${wave.batchCount} | ${wave.runnableBatchIds.join(",") || "none"} | ${wave.blockedBatchIds.join(",") || "none"} | ${wave.owners.join(",") || "none"} | ${wave.dependsOn.join(",") || "none"} | ${wave.commandCount} |`),
    "",
    "Wave operator commands:",
    "",
    ...brief.executionWaves.flatMap((wave) => (wave.operatorCommands.length > 0
      ? [`- ${wave.priority}:`, ...wave.operatorCommands.map((command) => `  - \`${command}\``)]
      : [`- ${wave.priority}: blocked until ${wave.dependsOn.join(",") || "upstream evidence"} is complete`])),
    "",
    "## Final Owner Queue Fast Path",
    "",
    `Owner: ${brief.finalOwnerQueueFastPath.owner}`,
    `Queue order: ${brief.finalOwnerQueueFastPath.queueOrder}`,
    `Objective: ${brief.finalOwnerQueueFastPath.objective}`,
    `Blocked until: ${brief.finalOwnerQueueFastPath.blockedUntil}`,
    `First command: \`${brief.finalOwnerQueueFastPath.firstCommand}\``,
    `Final gate command: \`${brief.finalOwnerQueueFastPath.finalGateCommand}\``,
    `Release env file required: ${brief.finalOwnerQueueFastPath.releaseEnvFileRequired}`,
    `Env keys: ${brief.finalOwnerQueueFastPath.envKeyCount}`,
    `Missing artifacts: ${brief.finalOwnerQueueFastPath.missingArtifactCount}`,
    "",
    "Commands:",
    "",
    ...brief.finalOwnerQueueFastPath.commands.map((command) => `- \`${command}\``),
    "",
    "## Fastest Safe Path",
    "",
    ...brief.fastestSafePath.map((item, index) => `${index + 1}. ${item}`),
    "",
    "## Owner Env Handoff",
    "",
    "| Owner | Blockers | Placeholders | Secret keys | Handoff |",
    "|---|---:|---:|---:|---|",
    ...brief.owners.map((owner) => `| ${owner.owner} | ${owner.blockers} | ${owner.placeholders} | ${owner.secretKeys} | ${owner.handoffPath || ""} |`),
    "",
    "## Evidence Handoffs",
    "",
    "| Handoff | Present | Path | Command | Purpose |",
    "|---|---|---|---|---|",
    ...brief.handoffReferences.map((reference) => `| ${reference.label} | ${reference.present} | ${reference.path} | \`${reference.command}\` | ${reference.purpose} |`),
    "",
    "## Performance Baseline",
    "",
    `Status: ${brief.performanceBaseline.status}`,
    `Ready to promote: ${brief.performanceBaseline.readyToPromote}`,
    `Blockers: ${brief.performanceBaseline.blockerCount}`,
    "",
    "Required env keys:",
    "",
    ...brief.performanceBaseline.requiredEnvKeys.map((key) => `- ${key}`),
    "",
    "Performance blockers:",
    "",
    ...brief.performanceBaseline.blockers.map((blocker) => `- ${blocker}`),
    "",
    "Performance commands:",
    "",
    ...brief.performanceBaseline.commands.map((command) => `- \`${command}\``),
    "",
    "## Next Action Queue",
    "",
    "| Order | Owner | Status | Receipt | Next action |",
    "|---:|---|---|---|---|",
    ...brief.nextActions.map((action) => `| ${action.order} | ${action.owner} | ${action.queueStatus} | ${action.receiptStatus} | ${action.nextAction} |`),
    "",
    "Next action commands:",
    "",
    ...brief.nextActions.flatMap((action) => action.executableCommands.map((command) => `- ${action.owner}: \`${command}\``)),
    "",
    "## Stop Reasons",
    "",
    ...brief.stopReasons.map((reason) => `- ${reason}`),
    "",
    "## Next Commands",
    "",
    ...brief.nextCommands.map((command) => `- \`${command}\``),
    "",
  ];
  return `${lines.join("\n")}\n`;
}

export { buildUnblockBrief, renderMarkdown };

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const finalGoNoGo = readJson("release-final-go-no-go.json", {});
  const envHandoff = readJson("release-env-owner-handoff-redacted.json", {});
  const performanceClosure = readJson("release-performance-baseline-closure.json", {});
  const nextActionQueue = readJson("release-next-action-queue.json", {});
  const finalOwnerQueue = readJson("release-final-owner-queue.json", {});
  const actionBatches = readJson("release-action-batches.json", {});
  const ownerInputReceipt = readJson("release-owner-input-receipt.json", {});
  const brief = buildUnblockBrief({ finalGoNoGo, envHandoff, performanceClosure, nextActionQueue, finalOwnerQueue, actionBatches, ownerInputReceipt });
  fs.mkdirSync(path.dirname(outputJson), { recursive: true });
  fs.writeFileSync(outputJson, `${JSON.stringify(brief, null, 2)}\n`);
  fs.mkdirSync(path.dirname(outputMarkdown), { recursive: true });
  fs.writeFileSync(outputMarkdown, renderMarkdown(brief));
  console.log(`[ddd-release-unblock-brief] recommendation=${brief.recommendation} cutoverAllowed=${brief.cutoverAllowed} output=${outputMarkdown}`);
}
