#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { validateReleaseUnblockBrief } from "./ddd-release-unblock-brief-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");

function validBrief(overrides = {}) {
  return {
    generatedAt: "2026-06-15T00:00:00.000Z",
    status: "NOT_READY",
    recommendation: "NO_GO_STRICT",
    cutoverAllowed: false,
    noAutoWaivers: true,
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
        pendingActionIds: ["release-env-lint-placeholders", "release-env-lint-status"],
      },
    },
    releaseEnvFileCutoverSafe: false,
    blockerSummary: {
      strictGateBlockers: 74,
      envOwnerBlockers: 11,
      envOwnerCount: 2,
      orchestratorPreflightBlockers: 0,
      orchestratorPreflightOwners: 0,
      blockedCutoverItems: 2,
      stopReasons: 2,
    },
    fastestSafePath: [
      "Fill only listed keys.",
      "Run env validation.",
      "Collect production-equivalent evidence.",
    ],
    releaseEnvSafety: {
      cutoverSafe: false,
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
      pendingActionIds: ["release-env-lint-placeholders", "release-env-lint-status"],
    },
    finalOwnerQueueFastPath: {
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
        "node scripts/ddd-release-readiness-summary.mjs",
        "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
      ],
    },
    firstOwnerAction: {
      order: 1,
      owner: "database",
      nextAction: "Run migration evidence environment check and fill the generated handoff.",
      reason: "strictGate=migration-evidence status=FAIL",
      command: "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
      envKeys: ["DDD_MIGRATION_FRESH_DB_EVIDENCE", "DDD_MIGRATION_PREVIOUS_SCHEMA_EVIDENCE"],
    },
    orchestratorPreflight: {
      artifact: "artifacts/ddd/release/orchestrator-report.json",
      mode: "run",
      strict: true,
      status: "PASS",
      blockers: 0,
      warnings: 0,
      selectedStepCount: 26,
      executedResultCount: 26,
      blockerChecks: [],
      ownerActionSummary: [],
      firstAction: null,
    },
    ownerInputReceipt: {
      artifact: "artifacts/ddd/release/release-owner-input-receipt.json",
      markdown: "artifacts/ddd/release/release-owner-input-receipt.md",
      status: "PENDING_OWNER_INPUT",
      cutoverReady: false,
      requiredOwnerInputs: 11,
      ownerCount: 2,
      readyOwnerCount: 0,
      pendingOwnerCount: 2,
      missingCriteria: ["releaseEnvReadinessStatus"],
      pendingOwners: [
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
          requiredOwnerInputs: 1,
          remainingPlaceholders: 1,
          remainingMissing: 0,
          ready: false,
          packetPath: "artifacts/ddd/release/release-env-owner-input-packet/06-payment-owner.json",
          handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/06-payment-owner.md",
        },
      ],
    },
    blockedCutoverItems: [
      {
        id: "release-environment",
        title: "Completed release env file and config matrix are valid.",
        pendingItems: 1,
        readyBatchIds: [],
        blockedBatchIds: [],
        readyBatches: [],
        blockedBatches: [],
      },
      {
        id: "evidence-integrity",
        title: "Release evidence integrity is verified.",
        pendingItems: 1,
        readyBatchIds: [],
        blockedBatchIds: [],
        readyBatches: [],
        blockedBatches: [],
      },
    ],
    executionWaves: [],
    owners: [
      {
        owner: "release-infra",
        blockers: 10,
        placeholders: 10,
        secretKeys: 4,
        totalKeys: 12,
        handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/02-release-infra.md",
        keys: ["DB_PASSWORD", "JWT_SECRET"],
      },
      {
        owner: "payment-owner",
        blockers: 1,
        placeholders: 1,
        secretKeys: 1,
        totalKeys: 2,
        handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/06-payment-owner.md",
        keys: ["DDD_PAYMENT_WEBHOOK_SECRET"],
      },
    ],
    handoffReferences: [
      {
        id: "migration-evidence-handoff",
        label: "Migration evidence handoff",
        path: "artifacts/ddd/migration/migration-evidence-handoff.md",
        present: true,
        purpose: "Fill migration runtime evidence.",
        command: "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
      },
      {
        id: "rollback-deferral-owner-handoff",
        label: "Rollback deferral owner handoff",
        path: "artifacts/ddd/rollback/rollback-deferrals-owner-handoff/README.md",
        present: true,
        purpose: "Coordinate rollback approval.",
        command: "node scripts/ddd-rollback-deferral-template.mjs",
      },
      {
        id: "performance-baseline-handoff",
        label: "Authenticated performance baseline handoff",
        path: "artifacts/ddd/release/release-performance-baseline-commands.sh",
        present: true,
        purpose: "Check and collect authenticated performance baseline evidence.",
        command: "DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh",
      },
      {
        id: "release-env-owner-input-packet",
        label: "Release env owner input packet",
        path: "artifacts/ddd/release/release-env-owner-input-packet.md",
        present: true,
        purpose: "Collect remaining owner values.",
        command: "node scripts/ddd-release-env-owner-input-packet-contract.mjs",
      },
      {
        id: "release-owner-input-receipt",
        label: "Release owner input receipt",
        path: "artifacts/ddd/release/release-owner-input-receipt.md",
        present: true,
        purpose: "Confirm owner values are reconciled before cutover.",
        command: "node scripts/ddd-release-owner-input-receipt-contract.mjs",
      },
    ],
    performanceBaseline: {
      status: "BLOCKED",
      readyToPromote: false,
      blockerCount: 1,
      requiredEnvKeys: ["BASE_URL", "DDD_AUTH_USERNAME"],
      blockers: ["missing authenticated performance baseline"],
      commands: ["DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-authenticated-performance-smoke.mjs"],
    },
    nextActions: [
      {
        order: 1,
        owner: "release-performance",
        queueStatus: "RUN_NOW",
        receiptStatus: "ARTIFACT_MISSING",
        nextAction: "Produce missing artifact: artifacts/ddd/performance/authenticated-runtime-baseline.json",
        reason: "missingArtifact=artifacts/ddd/performance/authenticated-runtime-baseline.json",
        envKeys: ["BASE_URL", "DDD_AUTH_USERNAME"],
        executableCommands: ["DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-authenticated-performance-smoke.mjs"],
      },
    ],
    stopReasons: [
      "authenticated performance baseline not ready: BLOCKED",
      "strict release gate blockers=74",
    ],
    nextCommands: [
      "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs",
      "JWT_SECRET=<redacted> node scripts/ddd-release-evidence-manifest.mjs",
    ],
    ...overrides,
  };
}

function passingOwnerInputReceipt() {
  return {
    artifact: "artifacts/ddd/release/release-owner-input-receipt.json",
    markdown: "artifacts/ddd/release/release-owner-input-receipt.md",
    status: "PASS",
    cutoverReady: true,
    requiredOwnerInputs: 0,
    ownerCount: 0,
    readyOwnerCount: 0,
    pendingOwnerCount: 0,
    missingCriteria: [],
    pendingOwners: [],
  };
}

const validMarkdown = [
  "# DDD Release Unblock Brief",
  "",
  "releaseEnvFileCutoverSafe: false",
  "## Release Env Safety",
  "",
  "Cutover safe: false",
  "",
  "## First Owner Action",
  "",
  "## Orchestrator Preflight",
  "",
  "Orchestrator preflight blockers: 0",
  "",
  "## Owner Input Receipt",
  "",
  "Required owner inputs: 11",
  "",
  "Missing criteria:",
  "",
  "## Blocked Cutover Items",
  "",
  "Cutover batch details:",
  "",
  "Execution waves:",
  "",
  "Wave operator commands:",
  "",
  "## Final Owner Queue Fast Path",
  "",
  "`node scripts/ddd-release-readiness-summary.mjs`",
  "`DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`",
  "",
  "## Fastest Safe Path",
  "",
  "1. Fill only listed keys.",
  "",
  "## Owner Env Handoff",
  "",
  "## Evidence Handoffs",
  "",
  "`DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs`",
  "`node scripts/ddd-rollback-deferral-template.mjs`",
  "`DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh`",
  "`node scripts/ddd-release-env-owner-input-packet-contract.mjs`",
  "`node scripts/ddd-release-owner-input-receipt-contract.mjs`",
  "",
  "## Performance Baseline",
  "",
  "## Next Action Queue",
  "",
].join("\n");

assert.deepEqual(validateReleaseUnblockBrief(validBrief(), validMarkdown), []);

assert(validateReleaseUnblockBrief(null).includes("release unblock brief must be a JSON object"));

{
  const issues = validateReleaseUnblockBrief(validBrief({
    releaseEnvSafety: {
      cutoverSafe: true,
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
      pendingActionIds: [],
    },
  }), validMarkdown);
  assert(issues.includes("releaseEnvSafety.cutoverSafe=true must imply completed release env file with checked chmod 600 permissions"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    generatedAt: "bad",
    recommendation: "MAYBE",
    cutoverAllowed: "false",
    noAutoWaivers: "true",
    releaseEnvFileCutoverSafe: "false",
    blockerSummary: {
      strictGateBlockers: -1,
      envOwnerBlockers: 0.5,
      envOwnerCount: "2",
      blockedCutoverItems: -2,
      stopReasons: null,
    },
    fastestSafePath: ["too short"],
  }), "");
  assert(issues.includes("generatedAt must be an ISO-like datetime"));
  assert(issues.includes("recommendation must be GO_STRICT, GO, NO_GO_STRICT, NO_GO, or UNKNOWN, got MAYBE"));
  assert(issues.includes("cutoverAllowed must be boolean"));
  assert(issues.includes("noAutoWaivers must be boolean"));
  assert(issues.includes("releaseEnvFileCutoverSafe must be boolean"));
  assert(issues.includes("blockerSummary.strictGateBlockers must be a non-negative integer"));
  assert(issues.includes("fastestSafePath must include at least three safe next steps"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    releaseEnvFileCutoverSafe: true,
  }), validMarkdown);
  assert(issues.includes("releaseEnvFileCutoverSafe must match safetySignals.releaseEnvFile cutover predicate"));
  assert(issues.includes("releaseEnvSafety.cutoverSafe must match releaseEnvFileCutoverSafe"));
}

{
  const brief = validBrief();
  brief.safetySignals.releaseEnvFile.permissionSafe = false;
  brief.releaseEnvSafety.permissionSafe = true;
  const issues = validateReleaseUnblockBrief(brief, validMarkdown);
  assert(issues.includes("releaseEnvSafety.permissionSafe must match safetySignals.releaseEnvFile.permissionSafe"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    cutoverAllowed: true,
    recommendation: "NO_GO_STRICT",
    ownerInputReceipt: passingOwnerInputReceipt(),
  }), validMarkdown);
  assert(issues.includes("cutoverAllowed=true requires recommendation=GO_STRICT or GO"));
}

{
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
    pendingActionIds: [],
  };
  const goBrief = validBrief({
    status: "READY",
    recommendation: "GO_STRICT",
    cutoverAllowed: true,
    ownerInputReceipt: passingOwnerInputReceipt(),
    releaseEnvFileCutoverSafe: true,
    safetySignals: { releaseEnvFile },
    releaseEnvSafety: {
      cutoverSafe: true,
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
      pendingActionIds: [],
    },
    blockerSummary: {
      strictGateBlockers: 0,
      envOwnerBlockers: 0,
      envOwnerCount: 0,
      orchestratorPreflightBlockers: 0,
      orchestratorPreflightOwners: 0,
      blockedCutoverItems: 0,
      stopReasons: 0,
    },
    blockedCutoverItems: [],
    executionWaves: [],
    owners: [],
    stopReasons: [],
    nextActions: [],
  });
  assert.deepEqual(validateReleaseUnblockBrief(goBrief, validMarkdown), []);
}

{
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
    pendingActionIds: [],
  };
  const issues = validateReleaseUnblockBrief(validBrief({
    recommendation: "GO_STRICT",
    cutoverAllowed: true,
    ownerInputReceipt: passingOwnerInputReceipt(),
    releaseEnvFileCutoverSafe: true,
    safetySignals: { releaseEnvFile },
    releaseEnvSafety: {
      cutoverSafe: true,
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
      pendingActionIds: [],
    },
    blockerSummary: {
      strictGateBlockers: 1,
      envOwnerBlockers: 1,
      envOwnerCount: 1,
      orchestratorPreflightBlockers: 1,
      orchestratorPreflightOwners: 1,
      blockedCutoverItems: 1,
      stopReasons: 1,
    },
    stopReasons: ["cutover checklist blocked: release-environment"],
  }), validMarkdown);
  assert(issues.includes("cutoverAllowed=true requires blockerSummary.strictGateBlockers=0"));
  assert(issues.includes("cutoverAllowed=true requires blockerSummary.envOwnerBlockers=0"));
  assert(issues.includes("cutoverAllowed=true requires blockerSummary.orchestratorPreflightBlockers=0"));
  assert(issues.includes("cutoverAllowed=true requires blockerSummary.blockedCutoverItems=0"));
  assert(issues.includes("cutoverAllowed=true requires blockerSummary.stopReasons=0"));
  assert(issues.includes("cutoverAllowed=true requires stopReasons to be empty"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    ownerInputReceipt: {
      artifact: "artifacts/ddd/release/release-owner-input-receipt.json",
      markdown: "artifacts/ddd/release/release-owner-input-receipt.md",
      status: "PENDING_OWNER_INPUT",
      cutoverReady: false,
      requiredOwnerInputs: 1,
      ownerCount: 1,
      readyOwnerCount: 0,
      pendingOwnerCount: 1,
      missingCriteria: [],
      pendingOwners: [],
    },
  }), validMarkdown);
  assert(issues.includes("ownerInputReceipt pending status must include missing criteria"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    ownerInputReceipt: {
      ...validBrief().ownerInputReceipt,
      artifact: path.join(repoRoot, "artifacts/ddd/release/release-owner-input-receipt.json"),
    },
  }), validMarkdown);
  assert(issues.includes("ownerInputReceipt.artifact must be relative and traversal-free"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    recommendation: "GO_STRICT",
    cutoverAllowed: true,
    ownerInputReceipt: passingOwnerInputReceipt(),
    releaseEnvFileCutoverSafe: false,
  }), validMarkdown);
  assert(issues.includes("cutoverAllowed=true requires releaseEnvFileCutoverSafe=true"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    finalOwnerQueueFastPath: {
      objective: "",
      blockedUntil: "",
      owner: "",
      queueOrder: 0,
      firstCommand: "",
      envKeyCount: -1,
      missingArtifactCount: 0.5,
      releaseEnvFileRequired: "true",
      finalGateCommand: "bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
      commands: ["DDD_RELEASE_ENV_FILE=/secure/.env.release.local node scripts/ddd-release-readiness-summary.mjs"],
    },
  }), validMarkdown);
  assert(issues.includes("finalOwnerQueueFastPath.owner must be a non-empty string"));
  assert(issues.includes("finalOwnerQueueFastPath.queueOrder must be a positive integer"));
  assert(issues.includes("finalOwnerQueueFastPath.objective must be a non-empty string"));
  assert(issues.includes("finalOwnerQueueFastPath.blockedUntil must be a non-empty string"));
  assert(issues.includes("finalOwnerQueueFastPath.firstCommand must be a non-empty string"));
  assert(issues.includes("finalOwnerQueueFastPath.envKeyCount must be a non-negative integer"));
  assert(issues.includes("finalOwnerQueueFastPath.missingArtifactCount must be a non-negative integer"));
  assert(issues.includes("finalOwnerQueueFastPath.releaseEnvFileRequired must be boolean"));
  assert(issues.includes("finalOwnerQueueFastPath.finalGateCommand must run strict final go/no-go gate"));
  assert(issues.includes("finalOwnerQueueFastPath.commands must include strict final go/no-go gate"));
  assert(issues.includes("finalOwnerQueueFastPath.commands[0] must redact DDD_RELEASE_ENV_FILE"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    firstOwnerAction: {
      order: 0,
      owner: "",
      nextAction: "",
      reason: "",
      command: "DDD_RELEASE_ENV_FILE=/secure/.env.release.local node scripts/ddd-release-env-file-lint.mjs",
      envKeys: "DDD_MIGRATION_FRESH_DB_EVIDENCE",
    },
  }), validMarkdown);
  assert(issues.includes("firstOwnerAction.order must be a positive integer"));
  assert(issues.includes("firstOwnerAction.owner must be a non-empty string"));
  assert(issues.includes("firstOwnerAction.nextAction must be a non-empty string"));
  assert(issues.includes("firstOwnerAction.reason must be a non-empty string"));
  assert(issues.includes("firstOwnerAction.envKeys must be an array"));
  assert(issues.includes("firstOwnerAction.command must redact DDD_RELEASE_ENV_FILE"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    owners: [...validBrief().owners].reverse(),
  }), validMarkdown);
  assert(issues.includes("owners must be sorted by blockers desc, placeholders desc, owner asc"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    blockerSummary: {
      ...validBrief().blockerSummary,
      stopReasons: 1,
    },
    stopReasons: [
      "authenticated performance baseline not ready: BLOCKED",
      "strict release gate blockers=74",
    ],
  }), validMarkdown);
  assert(issues.includes("blockerSummary.stopReasons must be greater than or equal to emitted stopReasons length"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    performanceBaseline: {
      status: "",
      readyToPromote: "false",
      blockerCount: 2,
      requiredEnvKeys: "BASE_URL",
      blockers: ["one"],
      commands: ["DDD_RELEASE_ENV_FILE=/secure/.env.release.local node scripts/ddd-authenticated-performance-smoke.mjs"],
    },
  }), validMarkdown);
  assert(issues.includes("performanceBaseline.status must be a non-empty string"));
  assert(issues.includes("performanceBaseline.readyToPromote must be boolean"));
  assert(issues.includes("performanceBaseline.requiredEnvKeys must be an array"));
  assert(issues.includes("performanceBaseline.blockerCount must match blockers length"));
  assert(issues.includes("performanceBaseline.commands[0] must redact DDD_RELEASE_ENV_FILE"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief(), "# DDD Release Unblock Brief\n\n## Fastest Safe Path\n\n## Owner Env Handoff\n");
  assert(issues.includes("markdown release env safety section is required"));
  assert(issues.includes("markdown release env safety must include cutover safe status"));
  assert(issues.includes("markdown first owner action section is required"));
  assert(issues.includes("markdown final owner queue fast path section is required"));
  assert(issues.includes("markdown final owner queue fast path must include strict final go/no-go gate command"));
  assert(issues.includes("markdown evidence handoffs section is required"));
  assert(issues.includes("markdown evidence handoffs must include required command for migration-evidence-handoff"));
  assert(issues.includes("markdown evidence handoffs must include required command for rollback-deferral-owner-handoff"));
  assert(issues.includes("markdown evidence handoffs must include required command for performance-baseline-handoff"));
  assert(issues.includes("markdown performance baseline section is required"));
  assert(issues.includes("markdown next action queue section is required"));
}

{
  const markdownWithoutPerformanceCommand = validMarkdown.replace(
    "`DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh`",
    "`node scripts/ddd-promote-performance-baseline.mjs`",
  );
  const issues = validateReleaseUnblockBrief(validBrief(), markdownWithoutPerformanceCommand);
  assert(issues.includes("markdown evidence handoffs must include required command for performance-baseline-handoff"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    handoffReferences: [
      {
        id: "migration-evidence-handoff",
        label: "Migration evidence handoff",
        path: "/tmp/migration-evidence-handoff.md",
        present: "true",
        purpose: "",
        command: "",
      },
      {
        id: "migration-evidence-handoff",
        label: "Duplicate",
        path: "../escape.md",
        present: true,
        purpose: "Duplicate id",
        command: "DDD_RELEASE_ENV_FILE=/secure/.env.release.local node scripts/ddd-release-env-file-lint.mjs",
      },
    ],
  }), validMarkdown);
  assert(issues.includes("handoffReferences[0].purpose is required"));
  assert(issues.includes("handoffReferences[0].command is required"));
  assert(issues.includes("handoffReferences[0].present must be boolean"));
  assert(issues.includes("handoffReferences[0].path must be relative and traversal-free"));
  assert(issues.includes("handoffReferences[1].id must be unique"));
  assert(issues.includes("handoffReferences[1].path must be relative and traversal-free"));
  assert(issues.includes("handoffReferences[1].command must redact DDD_RELEASE_ENV_FILE"));
  assert(issues.includes("handoffReferences must include required handoff rollback-deferral-owner-handoff"));
  assert(issues.includes("handoffReferences must include required handoff performance-baseline-handoff"));
}

{
  const handoffs = validBrief().handoffReferences.map((reference) => ({ ...reference }));
  handoffs.find((reference) => reference.id === "performance-baseline-handoff").command = "node scripts/ddd-promote-performance-baseline.mjs";
  const issues = validateReleaseUnblockBrief(validBrief({
    handoffReferences: handoffs,
  }), validMarkdown);
  assert(issues.includes("handoffReferences performance-baseline-handoff command must be DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    nextActions: [
      {
        order: 2,
        owner: "release-performance",
        queueStatus: "WAIT_FOR_DEPENDENCIES",
        receiptStatus: "ARTIFACT_MISSING",
        nextAction: "",
        reason: 123,
        envKeys: "BASE_URL",
        executableCommands: ["DDD_RELEASE_ENV_FILE=/secure/.env.release.local node scripts/ddd-authenticated-performance-smoke.mjs"],
      },
      {
        order: 1,
        owner: "database",
        queueStatus: "RUN_NOW",
        receiptStatus: "ARTIFACT_MISSING",
        nextAction: "Produce explain artifact",
        reason: "",
        envKeys: [],
        executableCommands: [],
      },
    ],
  }), validMarkdown);
  assert(issues.includes("nextActions[0].queueStatus must be RUN_NOW"));
  assert(issues.includes("nextActions[0].nextAction must be a non-empty string"));
  assert(issues.includes("nextActions[0].reason must be a string"));
  assert(issues.includes("nextActions[0].envKeys must be an array"));
  assert(issues.includes("nextActions[0].executableCommands[0] must redact DDD_RELEASE_ENV_FILE"));
  assert(issues.includes("nextActions must be sorted by order asc"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    nextActions: Array.from({ length: 6 }, (_, index) => ({
      order: index + 1,
      owner: `owner-${index}`,
      queueStatus: "RUN_NOW",
      receiptStatus: "ARTIFACT_MISSING",
      nextAction: "Produce artifact",
      reason: "",
      envKeys: [],
      executableCommands: [],
    })),
  }), validMarkdown);
  assert(issues.includes("nextActions must contain at most five RUN_NOW actions"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    nextCommands: ["DDD_RELEASE_ENV_FILE=/secure/.env.release.local node scripts/ddd-release-env-canonical-lint.mjs"],
  }), validMarkdown);
  assert(issues.includes("nextCommands[0] must redact DDD_RELEASE_ENV_FILE"));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    performanceBaseline: {
      ...validBrief().performanceBaseline,
      blockers: [`missing authenticated performance baseline ${path.join(repoRoot, "artifacts/ddd/performance/authenticated-runtime-baseline.json")}`],
    },
  }), validMarkdown);
  assert(issues.some((issue) => issue.startsWith("brief must not expose sensitive or concrete release values")));
}

{
  const issues = validateReleaseUnblockBrief(validBrief({
    nextCommands: ["JWT_SECRET=super-secret node scripts/ddd-release-evidence-manifest.mjs"],
  }), validMarkdown);
  assert(issues.some((issue) => issue.startsWith("brief must not expose sensitive or concrete release values")));
}

{
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-unblock-contract-"));
  const jsonPath = path.join(tempDir, "release-unblock-brief.json");
  const markdownPath = path.join(tempDir, "release-unblock-brief.md");
  fs.writeFileSync(jsonPath, `${JSON.stringify(validBrief(), null, 2)}\n`);
  fs.writeFileSync(markdownPath, validMarkdown);
  const result = spawnSync("node", ["scripts/ddd-release-unblock-brief-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_UNBLOCK_BRIEF_JSON: jsonPath,
      DDD_RELEASE_UNBLOCK_BRIEF_MD: markdownPath,
    },
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /ddd-release-unblock-brief-contract\] ok/);
}

console.log("[ddd-release-unblock-brief-contract.test] ok");
