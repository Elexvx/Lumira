#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { buildUnblockBrief, renderMarkdown } from "./ddd-release-unblock-brief.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");

const finalGoNoGo = {
  status: "NOT_READY",
  finalRecommendation: "NO_GO_STRICT",
  cutoverAllowed: false,
  noAutoWaivers: true,
  gate: {
    strict: true,
    blockers: 74,
    warnings: 7,
  },
  summary: {
    blockedCutoverItems: 2,
    stopReasons: 3,
  },
  blockedCutoverItems: [
    {
      id: "release-environment",
      title: "Completed release env file and config matrix are valid.",
      pendingItems: 3,
      readyBatchIds: ["p0-release-env-lint-release-infra"],
      blockedBatchIds: [],
    },
    {
      id: "production-equivalence",
      title: "Runtime acceptance evidence is production-equivalent.",
      pendingItems: 5,
      readyBatchIds: [],
      blockedBatchIds: ["p1-ai-runtime-ai", "p1-frontend-smoke-frontend"],
    },
  ],
  currentStopReasons: [
    "authenticated performance baseline not ready: BLOCKED",
    "strict release gate blockers=74",
  ],
  stopReasons: ["legacy duplicate should remain available"],
  ciSummary: {
    firstOwnerAction: {
      order: 1,
      owner: "database",
      nextAction: "Run migration evidence environment check and fill the generated handoff.",
      reason: "strictGate=migration-evidence status=FAIL",
      envKeys: ["DDD_MIGRATION_PREVIOUS_SCHEMA_EVIDENCE", "DDD_MIGRATION_FRESH_DB_EVIDENCE"],
    },
    firstOwnerActionCommand: "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
    orchestratorPreflight: {
      artifact: "artifacts/ddd/release/orchestrator-report.json",
      mode: "plan",
      strict: true,
      status: "FAIL",
      blockers: 4,
      warnings: 0,
      selectedStepCount: 26,
      executedResultCount: 0,
      ownerActionSummary: [
        {
          owner: "ai",
          pendingItems: 1,
          envKeys: ["LUMIRA_AI_BASE_URL", "LUMIRA_BASE_URL", "BASE_URL"],
          actions: [
            {
              id: "orchestrator-preflight-ai-runtime-base-url",
              checkId: "ai-runtime-base-url",
              reason: "missing AI runtime base URL",
              envKeys: ["LUMIRA_AI_BASE_URL", "LUMIRA_BASE_URL", "BASE_URL"],
              action: "Resolve the orchestrator preflight blocker, then rerun strict release evidence.",
            },
          ],
        },
        {
          owner: "database",
          pendingItems: 1,
          envKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_VALIDATED"],
          actions: [
            {
              id: "orchestrator-preflight-migration-runtime-evidence",
              checkId: "migration-runtime-evidence",
              reason: `missing migration evidence ${path.join(repoRoot, "artifacts/ddd/migration/runtime.json")}`,
              envKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_VALIDATED"],
              action: "Resolve the orchestrator preflight blocker, then rerun strict release evidence.",
            },
          ],
        },
      ],
    },
    firstOrchestratorPreflightAction: {
      owner: "ai",
      id: "orchestrator-preflight-ai-runtime-base-url",
      checkId: "ai-runtime-base-url",
      reason: "missing AI runtime base URL",
      envKeys: ["LUMIRA_AI_BASE_URL", "LUMIRA_BASE_URL", "BASE_URL"],
      command: "DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict",
    },
    ownerInputReceipt: {
      artifact: "artifacts/ddd/release/release-owner-input-receipt.json",
      markdown: "artifacts/ddd/release/release-owner-input-receipt.md",
      status: "PENDING_OWNER_INPUT",
      cutoverReady: false,
      requiredOwnerInputs: 12,
      ownerCount: 2,
      readyOwnerCount: 0,
      pendingOwnerCount: 2,
      missingCriteria: ["releaseEnvReadinessStatus"],
    },
  },
  releaseEnvFileCutoverSafe: false,
  safetySignals: {
    releaseEnvFile: {
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
      pendingActionIds: ["release-env-lint-placeholders", "release-env-lint-status", "release-env-lint-placeholders"],
      blockingSafeDefaultAvailable: 1,
      blockingRequiresOwnerInput: 2,
      safeDefaultsExhausted: false,
      ownerInputReasonCounts: {
        "production-endpoint": 1,
        "secret-manager": 1,
      },
      ownerInputOwners: [
        { owner: "release-infra", requiresOwnerInput: 2, safeDefaultAvailable: 1 },
      ],
    },
  },
  nextCommands: [
    "DDD_RELEASE_ENV_FILE=/secure/.env.release.local node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env",
    `DDD_RELEASE_ENV_FILE=/secure/.env.release.local node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env ${path.join(repoRoot, ".env.release.local")}`,
    "JWT_SECRET=super-secret node scripts/ddd-release-evidence-manifest.mjs",
    "node scripts/ddd-authenticated-performance-smoke.mjs",
  ],
};

const envHandoff = {
  owners: [
    {
      owner: "payment-owner",
      blockers: 1,
      placeholders: 1,
      secretKeys: 1,
      total: 2,
      handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/06-payment-owner.md",
      keys: ["DDD_PAYMENT_WEBHOOK_SECRET"],
    },
    {
      owner: "release-infra",
      blockers: 10,
      placeholders: 10,
      secretKeys: 4,
      total: 12,
      handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/02-release-infra.md",
      keys: ["DB_PASSWORD", "JWT_SECRET"],
    },
  ],
};

const performanceClosure = {
  status: "BLOCKED",
  readyToPromote: false,
  blockers: [
    `missing authenticated performance baseline ${path.join(repoRoot, "artifacts/ddd/performance/authenticated-runtime-baseline.json")}`,
    "source actual artifact must be production-equivalent and non-local, got http://127.0.0.1:8080",
  ],
  requiredEnvKeys: ["DDD_AUTH_USERNAME", "DDD_AUTH_PASSWORD", "BASE_URL", "DDD_AUTH_USERNAME"],
  commands: [
    "DDD_AUTH_PERF_STRICT=true DDD_RELEASE_ENV_FILE=/secure/.env.release.local node scripts/ddd-authenticated-performance-smoke.mjs",
    "node scripts/ddd-promote-performance-baseline.mjs",
  ],
};

const nextActionQueue = {
  items: [
    {
      order: 2,
      owner: "release-performance",
      queueStatus: "RUN_NOW",
      receiptStatus: "ARTIFACT_MISSING",
      nextAction: `Produce missing artifact: ${path.join(repoRoot, "artifacts/ddd/performance/authenticated-runtime-baseline.json")}`,
      reason: `missingArtifact=${path.join(repoRoot, "artifacts/ddd/performance/authenticated-runtime-baseline.json")}`,
      envKeys: ["DDD_AUTH_USERNAME", "BASE_URL", "DDD_AUTH_USERNAME"],
      executableCommands: [
        "DDD_RELEASE_ENV_FILE=/secure/.env.release.local DDD_AUTH_PERF_STRICT=true node scripts/ddd-authenticated-performance-smoke.mjs",
        `node ${path.join(repoRoot, "scripts/ddd-promote-performance-baseline.mjs")}`,
      ],
    },
    {
      order: 3,
      owner: "release-infra",
      queueStatus: "WAIT_FOR_DEPENDENCIES",
      receiptStatus: "WAITING_ON_DEPENDENCIES",
      nextAction: "Wait",
      reason: "blocked",
      envKeys: [],
      executableCommands: [],
    },
  ],
};

const finalOwnerQueue = {
  summary: {
    nextExecutableOwner: "database",
    nextExecutableQueueOrder: 1,
    nextExecutableCommand: "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
    nextExecutableEnvKeyCount: 20,
    nextExecutableMissingArtifactCount: 6,
  },
  fastPath: {
    objective: "Run the next actionable owner queue through strict evidence refresh and final go/no-go without bypassing safety gates.",
    blockedUntil: "Required owner env keys and expected evidence artifacts are available in a permission-safe release env file.",
    owner: "database",
    queueOrder: 1,
    firstCommand: "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
    envKeyCount: 20,
    missingArtifactCount: 6,
    releaseEnvFileRequired: true,
    finalGateCommand: "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    commands: [
      "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
      "node scripts/ddd-migration-evidence.mjs",
      "node scripts/ddd-release-readiness-summary.mjs",
      "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    ],
  },
};

const actionBatches = {
  batches: [
    {
      id: "p0-release-env-lint-release-infra",
      priority: "P0",
      source: "release-env-lint",
      owner: "release-infra",
      pendingItems: 3,
      canRunImmediately: true,
      dependsOn: [],
      commands: [
        "DDD_RELEASE_ENV_FILE=/secure/.env.release.local node scripts/ddd-release-env-file-lint.mjs",
        "node scripts/ddd-release-config-evidence.mjs",
      ],
      expectedArtifacts: [
        path.join(repoRoot, "artifacts/ddd/release/release-config-evidence.json"),
      ],
    },
    {
      id: "p1-ai-runtime-ai",
      priority: "P1",
      source: "ai-runtime",
      owner: "ai",
      pendingItems: 3,
      canRunImmediately: false,
      dependsOn: ["p0-release-env-lint-release-infra", "p0-docker-release-infra"],
      commands: ["node scripts/ddd-ai-runtime-drill.mjs"],
      expectedArtifacts: ["artifacts/ddd/ai/ai-runtime-drill.json"],
    },
  ],
};

const ownerInputReceipt = {
  status: "PENDING_OWNER_INPUT",
  cutoverReady: false,
  summary: {
    requiredOwnerInputs: 12,
    ownerCount: 2,
    readyOwnerCount: 0,
    pendingOwnerCount: 2,
  },
  missingCriteria: ["releaseEnvReadinessStatus", "releaseEnvReadinessBlockers"],
  ownerReceipts: [
    {
      owner: "release-infra",
      requiredOwnerInputs: 10,
      remainingPlaceholders: 10,
      remainingMissing: 0,
      ready: false,
      packetPath: "artifacts/ddd/release/release-env-owner-input-packet/02-release-infra.json",
      handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/02-release-infra.md",
    },
    {
      owner: "payment-owner",
      requiredOwnerInputs: 2,
      remainingPlaceholders: 2,
      remainingMissing: 0,
      ready: false,
      packetPath: "artifacts/ddd/release/release-env-owner-input-packet/06-payment-owner.json",
      handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/06-payment-owner.md",
    },
  ],
};

const brief = buildUnblockBrief({ finalGoNoGo, envHandoff, performanceClosure, nextActionQueue, finalOwnerQueue, actionBatches, ownerInputReceipt });
assert.equal(brief.recommendation, "NO_GO_STRICT");
assert.equal(brief.cutoverAllowed, false);
assert.equal(brief.noAutoWaivers, true);
assert.equal(brief.blockerSummary.strictGateBlockers, 74);
assert.equal(brief.blockerSummary.envOwnerBlockers, 11);
assert.equal(brief.blockerSummary.orchestratorPreflightBlockers, 4);
assert.equal(brief.blockerSummary.orchestratorPreflightOwners, 2);
assert.equal(brief.blockerSummary.blockedCutoverItems, 2);
assert.equal(brief.blockerSummary.stopReasons, 3);
assert.deepEqual(brief.blockedCutoverItems, [
  {
    id: "release-environment",
    title: "Completed release env file and config matrix are valid.",
    pendingItems: 3,
    readyBatchIds: ["p0-release-env-lint-release-infra"],
    blockedBatchIds: [],
    readyBatches: [
      {
        id: "p0-release-env-lint-release-infra",
        priority: "P0",
        source: "release-env-lint",
        owner: "release-infra",
        pendingItems: 3,
        canRunImmediately: true,
        dependsOn: [],
        commands: [
          "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
          "node scripts/ddd-release-config-evidence.mjs",
        ],
        expectedArtifacts: ["artifacts/ddd/release/release-config-evidence.json"],
      },
    ],
    blockedBatches: [],
  },
  {
    id: "production-equivalence",
    title: "Runtime acceptance evidence is production-equivalent.",
    pendingItems: 5,
    readyBatchIds: [],
    blockedBatchIds: ["p1-ai-runtime-ai", "p1-frontend-smoke-frontend"],
    readyBatches: [],
    blockedBatches: [
      {
        id: "p1-ai-runtime-ai",
        priority: "P1",
        source: "ai-runtime",
        owner: "ai",
        pendingItems: 3,
        canRunImmediately: false,
        dependsOn: ["p0-docker-release-infra", "p0-release-env-lint-release-infra"],
        commands: ["node scripts/ddd-ai-runtime-drill.mjs"],
        expectedArtifacts: ["artifacts/ddd/ai/ai-runtime-drill.json"],
      },
      {
        id: "p1-frontend-smoke-frontend",
        priority: "UNKNOWN",
        source: "unknown",
        owner: "unknown",
        pendingItems: 0,
        canRunImmediately: false,
        dependsOn: [],
        commands: [],
        expectedArtifacts: [],
      },
    ],
  },
]);
assert.deepEqual(brief.executionWaves, [
  {
    priority: "P0",
    batchCount: 1,
    runnableBatchIds: ["p0-release-env-lint-release-infra"],
    blockedBatchIds: [],
    owners: ["release-infra"],
    dependsOn: [],
    commandCount: 2,
    operatorCommands: [
      "DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh",
      "DDD_RELEASE_ENV_FILE=<release-env-file> DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh",
      "DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh",
      "DDD_RELEASE_ENV_FILE=<release-env-file> DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh",
    ],
  },
  {
    priority: "P1",
    batchCount: 1,
    runnableBatchIds: [],
    blockedBatchIds: ["p1-ai-runtime-ai"],
    owners: ["ai"],
    dependsOn: ["p0-docker-release-infra", "p0-release-env-lint-release-infra"],
    commandCount: 1,
    operatorCommands: [],
  },
  {
    priority: "UNKNOWN",
    batchCount: 1,
    runnableBatchIds: [],
    blockedBatchIds: ["p1-frontend-smoke-frontend"],
    owners: ["unknown"],
    dependsOn: [],
    commandCount: 0,
    operatorCommands: [],
  },
]);
assert.deepEqual(brief.stopReasons, [
  "authenticated performance baseline not ready: BLOCKED",
  "strict release gate blockers=74",
  "legacy duplicate should remain available",
]);
assert.equal(brief.performanceBaseline.status, "BLOCKED");
assert.equal(brief.releaseEnvSafety.cutoverSafe, false);
assert.equal(brief.releaseEnvSafety.ready, false);
assert.equal(brief.releaseEnvSafety.status, "FAIL");
assert.equal(brief.releaseEnvSafety.inputKind, "release-env-file");
assert.equal(brief.releaseEnvSafety.envFilePresent, true);
assert.equal(brief.releaseEnvSafety.generatedMissingTemplate, false);
assert.equal(brief.releaseEnvSafety.securityChecked, true);
assert.equal(brief.releaseEnvSafety.permissionSafe, true);
assert.equal(brief.releaseEnvSafety.permissionCheckSkipped, false);
assert.equal(brief.releaseEnvSafety.modeOctal, "600");
assert.equal(brief.releaseEnvSafety.requiredMode, "600");
assert.deepEqual(brief.releaseEnvSafety.pendingActionIds, ["release-env-lint-placeholders", "release-env-lint-status"]);
assert.equal(brief.firstOwnerAction.owner, "database");
assert.equal(brief.firstOwnerAction.order, 1);
assert.equal(brief.firstOwnerAction.reason, "strictGate=migration-evidence status=FAIL");
assert.equal(brief.firstOwnerAction.command, "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs");
assert.deepEqual(brief.firstOwnerAction.envKeys, ["DDD_MIGRATION_FRESH_DB_EVIDENCE", "DDD_MIGRATION_PREVIOUS_SCHEMA_EVIDENCE"]);
assert.equal(brief.orchestratorPreflight.artifact, "artifacts/ddd/release/orchestrator-report.json");
assert.equal(brief.orchestratorPreflight.mode, "plan");
assert.equal(brief.orchestratorPreflight.strict, true);
assert.equal(brief.orchestratorPreflight.status, "FAIL");
assert.equal(brief.orchestratorPreflight.blockers, 4);
assert.equal(brief.orchestratorPreflight.ownerActionSummary.length, 2);
assert.equal(brief.orchestratorPreflight.ownerActionSummary[0].owner, "ai");
assert.equal(brief.orchestratorPreflight.ownerActionSummary[1].actions[0].reason, "missing migration evidence artifacts/ddd/migration/runtime.json");
assert.equal(brief.orchestratorPreflight.firstAction.owner, "ai");
assert.equal(brief.orchestratorPreflight.firstAction.checkId, "ai-runtime-base-url");
assert.deepEqual(brief.orchestratorPreflight.firstAction.envKeys, ["BASE_URL", "LUMIRA_AI_BASE_URL", "LUMIRA_BASE_URL"]);
assert.equal(brief.ownerInputReceipt.status, "PENDING_OWNER_INPUT");
assert.equal(brief.ownerInputReceipt.cutoverReady, false);
assert.equal(brief.ownerInputReceipt.requiredOwnerInputs, 12);
assert.equal(brief.ownerInputReceipt.pendingOwnerCount, 2);
assert.deepEqual(brief.ownerInputReceipt.missingCriteria, ["releaseEnvReadinessBlockers", "releaseEnvReadinessStatus"]);
assert.equal(brief.ownerInputReceipt.pendingOwners[0].owner, "release-infra");
assert.equal(brief.ownerInputReceipt.pendingOwners[0].packetPath, "artifacts/ddd/release/release-env-owner-input-packet/02-release-infra.json");
assert.equal(brief.finalOwnerQueueFastPath.owner, "database");
assert.equal(brief.finalOwnerQueueFastPath.queueOrder, 1);
assert.equal(brief.finalOwnerQueueFastPath.releaseEnvFileRequired, true);
assert.equal(brief.finalOwnerQueueFastPath.firstCommand, "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs");
assert.equal(brief.finalOwnerQueueFastPath.finalGateCommand, "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh");
assert.deepEqual(brief.finalOwnerQueueFastPath.commands.slice(-2), [
  "node scripts/ddd-release-readiness-summary.mjs",
  "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
]);
assert.deepEqual(
  brief.handoffReferences.map((reference) => reference.id),
  ["migration-evidence-handoff", "rollback-deferral-owner-handoff", "performance-baseline-handoff", "release-env-owner-input-packet", "release-owner-input-receipt"],
);
assert(brief.handoffReferences.every((reference) => !path.isAbsolute(reference.path)));
assert.equal(brief.handoffReferences[0].command, "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs");
assert.equal(brief.handoffReferences[1].command, "node scripts/ddd-rollback-deferral-template.mjs");
assert.equal(brief.handoffReferences[2].command, "DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh");
assert.equal(brief.handoffReferences[3].command, "node scripts/ddd-release-env-owner-input-packet-contract.mjs");
assert.equal(brief.handoffReferences[3].path, "artifacts/ddd/release/release-env-owner-input-packet.md");
assert.equal(brief.handoffReferences[4].command, "node scripts/ddd-release-owner-input-receipt-contract.mjs");
assert.equal(brief.handoffReferences[4].path, "artifacts/ddd/release/release-owner-input-receipt.md");
assert.equal(brief.performanceBaseline.readyToPromote, false);
assert.equal(brief.performanceBaseline.blockerCount, 2);
assert.equal(brief.performanceBaseline.blockers[0], "missing authenticated performance baseline artifacts/ddd/performance/authenticated-runtime-baseline.json");
assert.deepEqual(brief.performanceBaseline.requiredEnvKeys, ["BASE_URL", "DDD_AUTH_PASSWORD", "DDD_AUTH_USERNAME"]);
assert(brief.performanceBaseline.commands[0].includes("DDD_RELEASE_ENV_FILE=<release-env-file>"));
assert.equal(brief.nextActions.length, 1);
assert.equal(brief.nextActions[0].owner, "release-performance");
assert.equal(brief.nextActions[0].nextAction, "Produce missing artifact: artifacts/ddd/performance/authenticated-runtime-baseline.json");
assert.deepEqual(brief.nextActions[0].envKeys, ["BASE_URL", "DDD_AUTH_USERNAME"]);
assert(brief.nextActions[0].executableCommands[0].includes("DDD_RELEASE_ENV_FILE=<release-env-file>"));
assert(!brief.nextActions[0].executableCommands[1].includes(repoRoot));
assert.equal(brief.owners[0].owner, "release-infra");
assert(brief.nextCommands[0].includes("DDD_RELEASE_ENV_FILE=<release-env-file>"));
assert(brief.nextCommands[1].endsWith(".env.release.local"));
assert(!brief.nextCommands[1].includes(repoRoot));
assert(brief.nextCommands[2].includes("JWT_SECRET=<redacted>"));

const markdown = renderMarkdown(brief);
assert.match(markdown, /# DDD Release Unblock Brief/);
assert.match(markdown, /Recommendation: NO_GO_STRICT/);
assert.match(markdown, /## Release Env Safety/);
assert.match(markdown, /Cutover safe: false/);
assert.match(markdown, /Status: FAIL/);
assert.match(markdown, /Permission safe: true/);
assert.match(markdown, /Blocking safe defaults available: 1/);
assert.match(markdown, /Blocking values requiring owner input: 2/);
assert.match(markdown, /Safe defaults exhausted: false/);
assert.match(markdown, /production-endpoint: 1/);
assert.match(markdown, /release-infra: requiresOwnerInput=2, safeDefaultAvailable=1/);
assert.match(markdown, /release-env-lint-placeholders/);
assert.match(markdown, /## First Owner Action/);
assert.match(markdown, /Owner: database/);
assert.match(markdown, /DDD_MIGRATION_CHECK_ENV=true node scripts\/ddd-migration-evidence\.mjs/);
assert.match(markdown, /Orchestrator preflight blockers: 4/);
assert.match(markdown, /## Orchestrator Preflight/);
assert.match(markdown, /Mode: plan/);
assert.match(markdown, /First preflight action:/);
assert.match(markdown, /Check: ai-runtime-base-url/);
assert.match(markdown, /DDD_RELEASE_EVIDENCE_STRICT=true node scripts\/ddd-release-evidence-orchestrator\.mjs --run --strict/);
assert.match(markdown, /\| ai \| 1 \| BASE_URL,LUMIRA_AI_BASE_URL,LUMIRA_BASE_URL \| ai-runtime-base-url \| missing AI runtime base URL \|/);
assert.match(markdown, /missing migration evidence artifacts\/ddd\/migration\/runtime\.json/);
assert.match(markdown, /## Owner Input Receipt/);
assert.match(markdown, /Status: PENDING_OWNER_INPUT/);
assert.match(markdown, /Required owner inputs: 12/);
assert.match(markdown, /releaseEnvReadinessBlockers/);
assert.match(markdown, /\| release-infra \| 10 \| 10 \| 0 \| artifacts\/ddd\/release\/release-env-owner-input-packet\/02-release-infra\.json \| artifacts\/ddd\/release\/release-env-owner-handoff-redacted\/02-release-infra\.md \|/);
assert.match(markdown, /## Blocked Cutover Items/);
assert.match(markdown, /\| release-environment \| 3 \| p0-release-env-lint-release-infra \| none \| Completed release env file and config matrix are valid\. \|/);
assert.match(markdown, /\| production-equivalence \| 5 \| none \| p1-ai-runtime-ai,p1-frontend-smoke-frontend \| Runtime acceptance evidence is production-equivalent\. \|/);
assert.match(markdown, /Cutover batch details:/);
assert.match(markdown, /\| release-environment \| p0-release-env-lint-release-infra \| release-infra \| P0 \| true \| none \| DDD_RELEASE_ENV_FILE=<release-env-file> node scripts\/ddd-release-env-file-lint\.mjs<br>node scripts\/ddd-release-config-evidence\.mjs \|/);
assert.match(markdown, /\| production-equivalence \| p1-ai-runtime-ai \| ai \| P1 \| false \| p0-docker-release-infra,p0-release-env-lint-release-infra \| node scripts\/ddd-ai-runtime-drill\.mjs \|/);
assert.match(markdown, /Execution waves:/);
assert.match(markdown, /\| P0 \| 1 \| p0-release-env-lint-release-infra \| none \| release-infra \| none \| 2 \|/);
assert.match(markdown, /\| P1 \| 1 \| none \| p1-ai-runtime-ai \| ai \| p0-docker-release-infra,p0-release-env-lint-release-infra \| 1 \|/);
assert.match(markdown, /Wave operator commands:/);
assert.match(markdown, /DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts\/ddd\/release\/release-execution-commands\.sh/);
assert.match(markdown, /DDD_RELEASE_ENV_FILE=<release-env-file> DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts\/ddd\/release\/release-execution-commands\.sh/);
assert.match(markdown, /DDD_RELEASE_PRIORITY=P1: blocked until p0-docker-release-infra,p0-release-env-lint-release-infra|P1: blocked until p0-docker-release-infra,p0-release-env-lint-release-infra/);
assert.match(markdown, /## Final Owner Queue Fast Path/);
assert.match(markdown, /Final gate command: `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts\/ddd\/release\/release-final-go-no-go-gate\.sh`/);
assert.match(markdown, /node scripts\/ddd-release-readiness-summary\.mjs/);
assert.match(markdown, /\| release-infra \| 10 \| 10 \| 4 \|/);
assert.match(markdown, /## Performance Baseline/);
assert.match(markdown, /## Evidence Handoffs/);
assert.match(markdown, /Migration evidence handoff/);
assert.match(markdown, /Rollback deferral owner handoff/);
assert.match(markdown, /Authenticated performance baseline handoff/);
assert.match(markdown, /Release owner input receipt/);
assert.match(markdown, /DDD_MIGRATION_CHECK_ENV=true node scripts\/ddd-migration-evidence\.mjs/);
assert.match(markdown, /node scripts\/ddd-rollback-deferral-template\.mjs/);
assert.match(markdown, /DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts\/ddd\/release\/release-performance-baseline-commands\.sh/);
assert.match(markdown, /Status: BLOCKED/);
assert.match(markdown, /## Next Action Queue/);
assert.match(markdown, /release-performance/);
assert.doesNotMatch(markdown, /super-secret/);
assert.doesNotMatch(markdown, /\/secure\/\.env\.release\.local/);

{
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-unblock-brief-"));
  fs.writeFileSync(path.join(tempDir, "release-final-go-no-go.json"), `${JSON.stringify(finalGoNoGo, null, 2)}\n`);
  fs.writeFileSync(path.join(tempDir, "release-env-owner-handoff-redacted.json"), `${JSON.stringify(envHandoff, null, 2)}\n`);
  fs.writeFileSync(path.join(tempDir, "release-performance-baseline-closure.json"), `${JSON.stringify(performanceClosure, null, 2)}\n`);
  fs.writeFileSync(path.join(tempDir, "release-next-action-queue.json"), `${JSON.stringify(nextActionQueue, null, 2)}\n`);
  fs.writeFileSync(path.join(tempDir, "release-final-owner-queue.json"), `${JSON.stringify(finalOwnerQueue, null, 2)}\n`);
  fs.writeFileSync(path.join(tempDir, "release-action-batches.json"), `${JSON.stringify(actionBatches, null, 2)}\n`);
  fs.writeFileSync(path.join(tempDir, "release-owner-input-receipt.json"), `${JSON.stringify(ownerInputReceipt, null, 2)}\n`);
  const jsonOutput = path.join(tempDir, "brief.json");
  const markdownOutput = path.join(tempDir, "brief.md");
  const result = spawnSync("node", ["scripts/ddd-release-unblock-brief.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_DIR: tempDir,
      DDD_RELEASE_UNBLOCK_BRIEF_JSON: jsonOutput,
      DDD_RELEASE_UNBLOCK_BRIEF_MD: markdownOutput,
    },
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /recommendation=NO_GO_STRICT/);
  const generated = JSON.parse(fs.readFileSync(jsonOutput, "utf8"));
  assert.equal(generated.blockerSummary.envOwnerCount, 2);
  assert.equal(generated.releaseEnvSafety.cutoverSafe, false);
  assert.equal(generated.releaseEnvSafety.status, "FAIL");
  assert.equal(generated.firstOwnerAction.owner, "database");
  assert.equal(generated.orchestratorPreflight.blockers, 4);
  assert.equal(generated.orchestratorPreflight.firstAction.owner, "ai");
  assert.equal(generated.ownerInputReceipt.status, "PENDING_OWNER_INPUT");
  assert.equal(generated.ownerInputReceipt.pendingOwners[0].owner, "release-infra");
  assert.equal(generated.blockedCutoverItems.length, 2);
  assert.equal(generated.blockedCutoverItems[0].id, "release-environment");
  assert.equal(generated.blockedCutoverItems[0].readyBatches[0].owner, "release-infra");
  assert.equal(generated.executionWaves[0].priority, "P0");
  assert.deepEqual(generated.executionWaves[0].runnableBatchIds, ["p0-release-env-lint-release-infra"]);
  assert.equal(generated.executionWaves[0].operatorCommands.length, 4);
  assert.equal(generated.executionWaves[1].operatorCommands.length, 0);
  assert.equal(generated.finalOwnerQueueFastPath.owner, "database");
  assert.equal(generated.finalOwnerQueueFastPath.finalGateCommand, "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh");
  assert.equal(generated.handoffReferences.length, 5);
  assert.equal(generated.performanceBaseline.status, "BLOCKED");
  assert.equal(generated.nextActions[0].owner, "release-performance");
  const generatedMarkdown = fs.readFileSync(markdownOutput, "utf8");
  assert.match(generatedMarkdown, /No auto waivers: true/);
  assert.match(generatedMarkdown, /## Release Env Safety/);
  assert.match(generatedMarkdown, /Blocking safe defaults available: 1/);
  assert.match(generatedMarkdown, /Safe defaults exhausted: false/);
  assert.match(generatedMarkdown, /## Orchestrator Preflight/);
  assert.match(generatedMarkdown, /## Blocked Cutover Items/);
  assert.match(generatedMarkdown, /Execution waves:/);
  assert.match(generatedMarkdown, /Wave operator commands:/);
}

console.log("[ddd-release-unblock-brief.test] ok");
