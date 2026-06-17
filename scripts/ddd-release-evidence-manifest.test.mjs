#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createHash } from "node:crypto";
import { requiredManifestArtifacts } from "./ddd-release-evidence-manifest-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");

function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify(data, null, 2)}\n`);
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function sha256(file) {
  return createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function runManifest(root, explainDir) {
  return spawnSync("node", ["scripts/ddd-release-evidence-manifest.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_EVIDENCE_DIR: root,
      DDD_EXPLAIN_DIR: explainDir,
      DDD_RELEASE_MANIFEST_STRICT: "true",
      DDD_RELEASE_MANIFEST_ENVIRONMENT: "staging",
      DDD_RELEASE_CANDIDATE: "rc-1",
      DDD_EVIDENCE_OPERATOR: "release-owner",
    },
  });
}

function runManifestAllowBlockers(root, explainDir) {
  return spawnSync("node", ["scripts/ddd-release-evidence-manifest.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_EVIDENCE_DIR: root,
      DDD_EXPLAIN_DIR: explainDir,
      DDD_RELEASE_MANIFEST_STRICT: "true",
      DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS: "false",
      DDD_RELEASE_MANIFEST_ENVIRONMENT: "staging",
      DDD_RELEASE_CANDIDATE: "rc-1",
      DDD_EVIDENCE_OPERATOR: "release-owner",
    },
  });
}

function runManifestReportOnly(root, explainDir) {
  return spawnSync("node", ["scripts/ddd-release-evidence-manifest.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_EVIDENCE_DIR: root,
      DDD_EXPLAIN_DIR: explainDir,
      DDD_RELEASE_MANIFEST_STRICT: "true",
      DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS: "false",
    },
  });
}

function runManifestEnvCheck(root, env = {}) {
  return spawnSync("node", ["scripts/ddd-release-evidence-manifest.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_EVIDENCE_DIR: root,
      DDD_RELEASE_MANIFEST_CHECK_ENV: "true",
      GITHUB_SHA: "",
      GITHUB_ACTOR: "",
      ...env,
    },
  });
}

const root = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-"));
const explainDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-explain-"));

{
  const envCheckRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-env-check-"));
  const result = runManifestEnvCheck(envCheckRoot);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /\[ddd-release-evidence-manifest\]\[env-missing\] manifest provenance sourceEnvironment is required/);
  assert.match(result.stderr, /\[ddd-release-evidence-manifest\]\[env-missing\] manifest provenance releaseCandidate is required/);
  assert.match(result.stderr, /\[ddd-release-evidence-manifest\]\[env-missing\] manifest provenance evidenceOperator is required/);
  assert.equal(fs.existsSync(path.join(envCheckRoot, "release", "evidence-manifest.json")), false);
  const preflight = readJson(path.join(envCheckRoot, "release", "evidence-manifest-preflight.json"));
  assert.equal(preflight.status, "FAIL");
  assert.equal(preflight.redacted, true);
  assert.equal(preflight.summary.blockers, preflight.blockers.length);
  assert.deepEqual(preflight.requiredEnv.map((entry) => entry.field), ["sourceEnvironment", "releaseCandidate", "evidenceOperator"]);
  assert(preflight.nextActions[0].command.includes("DDD_EVIDENCE_ENVIRONMENT=<environment>"));
}

{
  const envCheckRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-env-ok-"));
  const result = runManifestEnvCheck(envCheckRoot, {
    DDD_EVIDENCE_ENVIRONMENT: "staging",
    DDD_RELEASE_CANDIDATE: "rc-1",
    DDD_EVIDENCE_OPERATOR: "release-owner",
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /\[ddd-release-evidence-manifest\]\[artifact-missing\] missing artifact build\/backend-build-evidence\.json/);
  assert.equal(fs.existsSync(path.join(envCheckRoot, "release", "evidence-manifest.json")), false);
  const preflight = readJson(path.join(envCheckRoot, "release", "evidence-manifest-preflight.json"));
  assert.equal(preflight.status, "FAIL");
  assert.equal(preflight.sourceEnvironment, "staging");
  assert.equal(preflight.releaseCandidate, "rc-1");
  assert.equal(preflight.evidenceOperator, "release-owner");
  assert(preflight.summary.missingArtifacts > 0);
}

{
  const envCheckRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-env-file-"));
  const envFile = path.join(envCheckRoot, ".env.release.local");
  fs.writeFileSync(envFile, [
    "DDD_EVIDENCE_ENVIRONMENT=staging",
    "DDD_RELEASE_CANDIDATE=rc-file-1",
    "DDD_EVIDENCE_OPERATOR=release-owner-file",
    "",
  ].join("\n"));
  fs.chmodSync(envFile, 0o600);
  const result = runManifestEnvCheck(envCheckRoot, {
    DDD_RELEASE_ENV_FILE: envFile,
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /\[ddd-release-evidence-manifest\]\[artifact-missing\] missing artifact build\/backend-build-evidence\.json/);
  const preflight = readJson(path.join(envCheckRoot, "release", "evidence-manifest-preflight.json"));
  assert.equal(preflight.sourceEnvironment, "staging");
  assert.equal(preflight.releaseCandidate, "rc-file-1");
  assert.equal(preflight.evidenceOperator, "release-owner-file");
  assert.deepEqual(preflight.requiredEnv.map((entry) => [entry.field, entry.present, entry.valid]), [
    ["sourceEnvironment", true, true],
    ["releaseCandidate", true, true],
    ["evidenceOperator", true, true],
  ]);
}

{
  const envCheckRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-env-file-override-"));
  const envFile = path.join(envCheckRoot, ".env.release.local");
  fs.writeFileSync(envFile, [
    "DDD_EVIDENCE_ENVIRONMENT=staging",
    "DDD_RELEASE_CANDIDATE=rc-file-1",
    "DDD_EVIDENCE_OPERATOR=release-owner-file",
    "",
  ].join("\n"));
  fs.chmodSync(envFile, 0o600);
  const result = runManifestEnvCheck(envCheckRoot, {
    DDD_RELEASE_ENV_FILE: envFile,
    DDD_EVIDENCE_ENVIRONMENT: "production",
    DDD_RELEASE_CANDIDATE: "rc-process-1",
    DDD_EVIDENCE_OPERATOR: "release-owner-process",
  });
  assert.equal(result.status, 1);
  const preflight = readJson(path.join(envCheckRoot, "release", "evidence-manifest-preflight.json"));
  assert.equal(preflight.sourceEnvironment, "production");
  assert.equal(preflight.releaseCandidate, "rc-process-1");
  assert.equal(preflight.evidenceOperator, "release-owner-process");
}

{
  const stablePreflightRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-stable-preflight-"));
  const stableExplainDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-stable-preflight-explain-"));
  for (const relativePath of requiredManifestArtifacts) {
    writeJson(path.join(stablePreflightRoot, relativePath), {
      generatedAt: "2026-06-14T00:00:00.000Z",
      status: "PASS",
      relativePath,
    });
  }
  writeJson(path.join(stableExplainDir, "runtime-appearance.json"), {
    generatedAt: "2026-06-14T00:00:00.000Z",
    queryName: "runtime-appearance",
  });
  const firstRun = runManifestEnvCheck(stablePreflightRoot, {
    DDD_EXPLAIN_DIR: stableExplainDir,
    DDD_EVIDENCE_ENVIRONMENT: "staging",
    DDD_RELEASE_CANDIDATE: "rc-1",
    DDD_EVIDENCE_OPERATOR: "release-owner",
  });
  assert.equal(firstRun.status, 0, firstRun.stderr || firstRun.stdout);
  const preflightPath = path.join(stablePreflightRoot, "release", "evidence-manifest-preflight.json");
  const firstSha = sha256(preflightPath);
  const secondRun = runManifestEnvCheck(stablePreflightRoot, {
    DDD_EXPLAIN_DIR: stableExplainDir,
    DDD_EVIDENCE_ENVIRONMENT: "staging",
    DDD_RELEASE_CANDIDATE: "rc-1",
    DDD_EVIDENCE_OPERATOR: "release-owner",
  });
  assert.equal(secondRun.status, 0, secondRun.stderr || secondRun.stdout);
  assert.equal(sha256(preflightPath), firstSha);
}

{
  const envCheckRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-env-ok-"));
  const envExplainDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-env-ok-explain-"));
  for (const relativePath of requiredManifestArtifacts) {
    writeJson(path.join(envCheckRoot, relativePath), {
      generatedAt: "2026-06-14T00:00:00.000Z",
      status: "PASS",
      relativePath,
    });
  }
  writeJson(path.join(envExplainDir, "runtime-appearance.json"), {
    generatedAt: "2026-06-14T00:00:00.000Z",
    queryName: "runtime-appearance",
  });
  const result = runManifestEnvCheck(envCheckRoot, {
    DDD_EXPLAIN_DIR: envExplainDir,
    DDD_EVIDENCE_ENVIRONMENT: "staging",
    DDD_RELEASE_CANDIDATE: "rc-1",
    DDD_EVIDENCE_OPERATOR: "release-owner",
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /\[ddd-release-evidence-manifest\]\[env-ok\]/);
  assert.match(result.stdout, /\[ddd-release-evidence-manifest\]\[artifact-ok\] requiredArtifacts=/);
  assert.equal(fs.existsSync(path.join(envCheckRoot, "release", "evidence-manifest.json")), false);
  const preflight = readJson(path.join(envCheckRoot, "release", "evidence-manifest-preflight.json"));
  assert.equal(preflight.status, "PASS");
  assert.equal(preflight.summary.blockers, 0);
  assert.equal(preflight.summary.requiredArtifacts, requiredManifestArtifacts.length);
  assert.equal(preflight.summary.explainFiles, 1);
  assert.equal(preflight.nextActions[0].command, "DDD_RELEASE_MANIFEST_STRICT=true node scripts/ddd-release-evidence-manifest.mjs");
}

{
  const envCheckRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-local-provenance-"));
  const envExplainDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-local-provenance-explain-"));
  for (const relativePath of requiredManifestArtifacts) {
    writeJson(path.join(envCheckRoot, relativePath), {
      generatedAt: "2026-06-14T00:00:00.000Z",
      status: "PASS",
      relativePath,
    });
  }
  writeJson(path.join(envExplainDir, "runtime-appearance.json"), {
    generatedAt: "2026-06-14T00:00:00.000Z",
    queryName: "runtime-appearance",
  });
  const result = runManifestEnvCheck(envCheckRoot, {
    DDD_EXPLAIN_DIR: envExplainDir,
    DDD_EVIDENCE_ENVIRONMENT: "local-dev",
    DDD_RELEASE_CANDIDATE: "local-worktree",
    DDD_EVIDENCE_OPERATOR: "local-operator",
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /manifest provenance sourceEnvironment must identify a production-equivalent release environment/);
  assert.match(result.stderr, /manifest provenance releaseCandidate must identify a release version, commit, or build candidate/);
  assert.match(result.stderr, /manifest provenance evidenceOperator must identify a real release operator/);
  assert.equal(fs.existsSync(path.join(envCheckRoot, "release", "evidence-manifest.json")), false);
  const preflight = readJson(path.join(envCheckRoot, "release", "evidence-manifest-preflight.json"));
  assert.equal(preflight.status, "FAIL");
  assert.equal(preflight.summary.missingArtifacts, 0);
  assert.equal(preflight.summary.explainFiles, 1);
  assert.deepEqual(preflight.requiredEnv.map((entry) => [entry.field, entry.present, entry.valid]), [
    ["sourceEnvironment", true, false],
    ["releaseCandidate", true, false],
    ["evidenceOperator", true, false],
  ]);
}

for (const relativePath of requiredManifestArtifacts) {
  writeJson(path.join(root, relativePath), {
    generatedAt: "2026-06-14T00:00:00.000Z",
    sourceEnvironment: "staging",
    releaseCandidate: "rc-1",
    evidenceOperator: "release-owner",
    status: "PASS",
    relativePath,
  });
}
writeJson(path.join(root, "release/release-final-owner-queue-run-report.json"), {
  generatedAt: "2026-06-14T00:00:00.000Z",
  reportStatus: "FAIL",
  exitCode: 1,
  ownerFilter: "release-performance",
  statusFilter: "ACTIONABLE",
  summary: {
    totalEntries: 0,
    succeededEntries: 0,
    failedEntries: 0,
  },
  entries: [],
});
writeJson(path.join(root, "release/release-next-action-run-report.json"), {
  generatedAt: "2026-06-14T00:00:00.000Z",
  reportStatus: "FAIL",
  exitCode: 1,
  ownerFilter: "release-performance",
  orderFilter: "2",
  summary: {
    totalEntries: 1,
    succeededEntries: 0,
    failedEntries: 1,
  },
  entries: [{
    order: 2,
    owner: "release-performance",
    receiptStatus: "ARTIFACT_MISSING",
    command: "node scripts/ddd-authenticated-performance-smoke.mjs",
    status: 1,
    durationMs: 10,
    finishedAt: "2026-06-14T00:00:01.000Z",
  }],
});
writeJson(path.join(root, "release/release-execution-run-report.json"), {
  generatedAt: "2026-06-14T00:00:00.000Z",
  reportStatus: "FAIL",
  exitCode: 1,
  batchFilter: "p0-docker-release-infra",
  ownerFilter: "release-infra",
  priorityFilter: "P0",
  summary: {
    totalEntries: 1,
    succeededEntries: 0,
    failedEntries: 1,
  },
  entries: [{
    batchId: "p0-docker-release-infra",
    owner: "release-infra",
    priority: "P0",
    command: "DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs",
    status: 1,
    durationMs: 10,
    finishedAt: "2026-06-14T00:00:01.000Z",
  }],
});
writeJson(path.join(root, "release/explain-gate-report.json"), {
  generatedAt: "2026-06-14T00:00:00.000Z",
  status: "FAIL",
  strict: true,
  explainDir,
  scannedExplainFileCount: 1,
  blockerCount: 1,
  issues: [{ scope: "metadata", detail: "legacyPlanImport must be false" }],
});
writeJson(path.join(root, "release/release-config-owner-input-reconciliation.json"), {
  generatedAt: "2026-06-14T00:00:00.000Z",
  status: "PASS",
  redacted: true,
  contract: "ddd-release-config-owner-input-reconciliation",
  configArtifact: "artifacts/ddd/config/release-config-evidence.json",
  ownerInputPacket: "artifacts/ddd/release/release-env-owner-input-packet.json",
  summary: {
    configPlaceholderBlockers: 2,
    uniqueConfigPlaceholderKeys: 1,
    ownerInputKeys: 1,
    mappedConfigPlaceholderKeys: 1,
    unmappedConfigPlaceholderKeys: 0,
    duplicateConfigPlaceholderBlockers: 1,
    ownerInputsWithoutConfigPlaceholder: 0,
  },
  mappedConfigPlaceholderKeys: [{
    key: "DB_PASSWORD",
    owner: "release-infra",
    canonicalKey: "DB_PASSWORD",
    ownerInputReason: "secret-manager",
    valueClass: "secret",
    duplicateConfigBlockers: 2,
  }],
  unmappedConfigPlaceholderKeys: [],
  ownerInputsWithoutConfigPlaceholder: [],
  issueCount: 0,
  issues: [],
});
writeJson(path.join(root, "release/release-owner-input-receipt.json"), {
  generatedAt: "2026-06-14T00:00:00.000Z",
  status: "PENDING_OWNER_INPUT",
  redacted: true,
  contract: "ddd-release-owner-input-receipt",
  envFile: "<release-env-file>",
  sourceArtifacts: {
    ownerInputPacket: "artifacts/ddd/release/release-env-owner-input-packet.json",
    releaseEnvReadiness: "artifacts/ddd/release/release-env-readiness-redacted.json",
    configOwnerInputReconciliation: "artifacts/ddd/release/release-config-owner-input-reconciliation.json",
  },
  cutoverReady: false,
  summary: {
    requiredOwnerInputs: 1,
    ownerCount: 1,
    readyOwnerCount: 0,
    pendingOwnerCount: 1,
    missingCriteria: 3,
    cutoverReady: false,
  },
  observed: {
    releaseEnvReadinessStatus: "ADVISORY",
    releaseEnvReadinessBlockers: 1,
    releaseEnvReadinessPlaceholders: 1,
    releaseEnvReadinessMissing: 0,
    configOwnerInputReconciliationStatus: "PASS",
    configOwnerInputReconciliationUnmappedKeys: 0,
  },
  criteria: {
    releaseEnvReadinessStatus: { expected: "PASS", actual: "ADVISORY", met: false },
    releaseEnvReadinessBlockers: { expected: 0, actual: 1, met: false },
    releaseEnvReadinessPlaceholders: { expected: 0, actual: 1, met: false },
    releaseEnvReadinessMissing: { expected: 0, actual: 0, met: true },
    configOwnerInputReconciliationStatus: { expected: "PASS", actual: "PASS", met: true },
    configOwnerInputReconciliationUnmappedKeys: { expected: 0, actual: 0, met: true },
  },
  missingCriteria: [
    "releaseEnvReadinessStatus",
    "releaseEnvReadinessBlockers",
    "releaseEnvReadinessPlaceholders",
  ],
  ownerReceipts: [{
    owner: "release-infra",
    requiredOwnerInputs: 1,
    secretInputs: 1,
    productionEndpointInputs: 0,
    ownerProductionValueInputs: 0,
    remainingPlaceholders: 1,
    remainingMissing: 0,
    ready: false,
    packetPath: "artifacts/ddd/release/release-env-owner-input-packet/01-release-infra.json",
    handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/01-release-infra.md",
  }],
  validationCommands: [
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
    "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
  ],
});
fs.writeFileSync(
  path.join(root, "release/release-owner-input-receipt.csv"),
  [
    "owner,ready,requiredOwnerInputs,secretInputs,productionEndpointInputs,ownerProductionValueInputs,remainingPlaceholders,remainingMissing,packetPath,handoffPath,receiptStatus,cutoverReady,missingCriteria",
    "release-infra,false,1,1,0,0,1,0,artifacts/ddd/release/release-env-owner-input-packet/01-release-infra.json,artifacts/ddd/release/release-env-owner-handoff-redacted/01-release-infra.md,PENDING_OWNER_INPUT,false,releaseEnvReadinessStatus;releaseEnvReadinessBlockers;releaseEnvReadinessPlaceholders",
    "",
  ].join("\n"),
);
fs.writeFileSync(
  path.join(root, "release/release-owner-input-receipt-items.csv"),
  [
    "inputOrder,fillOrder,owner,ownerReady,canonicalKey,aliases,group,requirement,status,valueClass,ownerInputReason,secret,requiresOwnerInput,required,httpsRequired,nonLocalRequired,minLength,safeDefaultAvailable,packetPath,handoffPath,collectionGuidance",
    "1,1,release-infra,false,DB_PASSWORD,DB_PASSWORD,database,password,PLACEHOLDER,secret,secret-manager,true,true,true,false,false,32,false,artifacts/ddd/release/release-env-owner-input-packet/01-release-infra.json,artifacts/ddd/release/release-env-owner-handoff-redacted/01-release-infra.md,Collect through approved secret manager.",
    "",
  ].join("\n"),
);
fs.writeFileSync(
  path.join(root, "release/release-owner-input-receipt-items.md"),
  [
    "# DDD Release Owner Input Receipt Items",
    "",
    "- [ ] 1. `DB_PASSWORD` owner=release-infra; status=PLACEHOLDER; class=secret",
    "",
    "Concrete values are intentionally omitted from this artifact.",
    "",
  ].join("\n"),
);
writeJson(path.join(root, "release/release-unblock-brief.json"), {
  generatedAt: "2026-06-14T00:00:00.000Z",
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
      pendingActionIds: [
        "release-env-lint-pass-mode",
        "release-env-lint-status",
      ],
    },
  },
  releaseEnvFileCutoverSafe: false,
  blockerSummary: {
    strictGateBlockers: 74,
    envOwnerBlockers: 1,
    envOwnerCount: 1,
    orchestratorPreflightBlockers: 1,
    orchestratorPreflightOwners: 1,
    blockedCutoverItems: 1,
    stopReasons: 1,
  },
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
    pendingActionIds: [
      "release-env-lint-pass-mode",
      "release-env-lint-status",
    ],
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
  fastestSafePath: [
    "Fill only listed keys.",
    "Run env validation.",
    "Collect production-equivalent evidence.",
  ],
  owners: [{
    owner: "release-infra",
    blockers: 1,
    placeholders: 1,
    secretKeys: 1,
    totalKeys: 1,
    handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/01-release-infra.md",
    keys: ["JWT_SECRET"],
  }],
  firstOwnerAction: {
    order: 1,
    owner: "database",
    nextAction: "Run migration evidence check-env and attach fresh/upgrade Flyway evidence.",
    reason: "strictGate=migration-evidence status=FAIL",
    command: "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
    envKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_VALIDATED"],
  },
  orchestratorPreflight: {
    artifact: "artifacts/ddd/release/orchestrator-report.json",
    mode: "plan",
    strict: true,
    status: "FAIL",
    blockers: 1,
    warnings: 0,
    selectedStepCount: 26,
    executedResultCount: 0,
    ownerActionSummary: [{
      owner: "database",
      pendingItems: 1,
      envKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_VALIDATED"],
      actions: [{
        id: "orchestrator-preflight-migration-runtime-evidence",
        checkId: "migration-runtime-evidence",
        reason: "missing migration runtime evidence",
        envKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_VALIDATED"],
        action: "Resolve the orchestrator preflight blocker, then rerun strict release evidence.",
      }],
    }],
    firstAction: {
      owner: "database",
      id: "orchestrator-preflight-migration-runtime-evidence",
      checkId: "migration-runtime-evidence",
      reason: "missing migration runtime evidence",
      envKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_VALIDATED"],
      command: "DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict",
    },
  },
  ownerInputReceipt: {
    artifact: "artifacts/ddd/release/release-owner-input-receipt.json",
    markdown: "artifacts/ddd/release/release-owner-input-receipt.md",
    status: "PENDING_OWNER_INPUT",
    cutoverReady: false,
    requiredOwnerInputs: 1,
    ownerCount: 1,
    readyOwnerCount: 0,
    pendingOwnerCount: 1,
    missingCriteria: ["releaseEnvReadinessStatus"],
    pendingOwners: [{
      owner: "release-infra",
      requiredOwnerInputs: 1,
      remainingPlaceholders: 1,
      remainingMissing: 0,
      ready: false,
      packetPath: "artifacts/ddd/release/release-env-owner-input-packet/01-release-infra.json",
      handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/01-release-infra.md",
    }],
  },
  blockedCutoverItems: [{
    id: "release-environment",
    title: "Completed release env file and config matrix are valid.",
    pendingItems: 1,
    readyBatchIds: ["p0-release-env-lint-release-infra"],
    blockedBatchIds: [],
    readyBatches: [{
      id: "p0-release-env-lint-release-infra",
      priority: "P0",
      source: "release-env-lint",
      owner: "release-infra",
      pendingItems: 1,
      canRunImmediately: true,
      dependsOn: [],
      commands: ["DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs"],
      expectedArtifacts: ["artifacts/ddd/release/release-env-lint.json"],
    }],
    blockedBatches: [],
  }],
  executionWaves: [{
    priority: "P0",
    batchCount: 1,
    commandCount: 1,
    runnableBatchIds: ["p0-release-env-lint-release-infra"],
    blockedBatchIds: [],
    owners: ["release-infra"],
    dependsOn: [],
    operatorCommands: ["DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs"],
  }],
  handoffReferences: [{
    id: "migration-evidence-handoff",
    label: "Migration evidence handoff",
    purpose: "Collect fresh DB and old-schema upgrade Flyway drill evidence.",
    command: "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
    present: true,
    path: "artifacts/ddd/migration/migration-evidence-handoff.md",
  }, {
    id: "rollback-deferral-owner-handoff",
    label: "Rollback deferral owner handoff",
    purpose: "Prepare rollback drill PASS or approved DEFERRED records.",
    command: "node scripts/ddd-rollback-deferral-template.mjs",
    present: true,
    path: "artifacts/ddd/rollback/rollback-drill-handoff.md",
  }, {
    id: "performance-baseline-handoff",
    label: "Performance baseline handoff",
    purpose: "Collect authenticated HTTPS production-equivalent performance baseline.",
    command: "DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh",
    present: true,
    path: "artifacts/ddd/release/release-performance-baseline-closure.md",
  }, {
    id: "release-env-owner-input-packet",
    label: "Release env owner input packet",
    purpose: "Collect remaining owner values without exposing concrete values.",
    command: "node scripts/ddd-release-env-owner-input-packet-contract.mjs",
    present: true,
    path: "artifacts/ddd/release/release-env-owner-input-packet.md",
  }, {
    id: "release-owner-input-receipt",
    label: "Release owner input receipt",
    purpose: "Confirm owner inputs are reconciled before cutover.",
    command: "node scripts/ddd-release-owner-input-receipt-contract.mjs",
    present: true,
    path: "artifacts/ddd/release/release-owner-input-receipt.md",
  }],
  performanceBaseline: {
    status: "BLOCKED",
    readyToPromote: false,
    blockerCount: 1,
    requiredEnvKeys: ["BASE_URL", "DDD_AUTH_USERNAME"],
    blockers: ["missing authenticated performance baseline"],
    commands: ["DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-authenticated-performance-smoke.mjs"],
  },
  nextActions: [{
    order: 1,
    owner: "release-performance",
    queueStatus: "RUN_NOW",
    receiptStatus: "ARTIFACT_MISSING",
    nextAction: "Produce missing artifact: artifacts/ddd/performance/authenticated-runtime-baseline.json",
    reason: "missingArtifact=artifacts/ddd/performance/authenticated-runtime-baseline.json",
    envKeys: ["BASE_URL"],
    executableCommands: ["DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-authenticated-performance-smoke.mjs"],
  }],
  stopReasons: ["strict release gate blockers=74"],
  nextCommands: ["DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs"],
});
fs.writeFileSync(path.join(root, "release/release-unblock-brief.md"), [
  "# DDD Release Unblock Brief",
  "",
  "releaseEnvFileCutoverSafe: false",
  "",
  "## Release Env Safety",
  "",
  "Cutover safe: false",
  "",
  "## First Owner Action",
  "",
  "Run migration evidence check-env.",
  "",
  "## Orchestrator Preflight",
  "",
  "Orchestrator preflight blockers: 1",
  "",
  "## Owner Input Receipt",
  "",
  "Required owner inputs: 1",
  "",
  "Missing criteria:",
  "- releaseEnvReadinessStatus",
  "",
  "## Blocked Cutover Items",
  "",
  "Cutover batch details:",
  "- p0-release-env-lint-release-infra",
  "",
  "Execution waves:",
  "- P0",
  "",
  "Wave operator commands:",
  "- DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
  "",
  "## Final Owner Queue Fast Path",
  "",
  "- DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
  "- node scripts/ddd-release-readiness-summary.mjs",
  "- DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
  "",
  "## Fastest Safe Path",
  "",
  "1. Fill only listed keys.",
  "",
  "## Owner Env Handoff",
  "",
  "## Evidence Handoffs",
  "",
  "- DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
  "- node scripts/ddd-rollback-deferral-template.mjs",
  "- DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh",
  "- node scripts/ddd-release-env-owner-input-packet-contract.mjs",
  "- node scripts/ddd-release-owner-input-receipt-contract.mjs",
  "",
  "## Performance Baseline",
  "",
  "## Next Action Queue",
  "",
].join("\n"));
writeJson(path.join(root, "release/release-artifact-path-leak-contract.json"), {
  generatedAt: "2026-06-14T00:00:00.000Z",
  status: "PASS",
  leakCount: 0,
  scannedFiles: 9,
  releaseEnvDisplayScannedFiles: 2,
  releaseEnvDisplayScanned: [
    {
      file: path.join(root, "release/release-env-owner-input-packet/01-release-infra.json"),
      present: true,
      leaks: 0,
    },
    {
      file: path.join(root, "release/release-env-owner-input-packet/01-release-infra.md"),
      present: true,
      leaks: 0,
    },
  ],
});
writeJson(path.join(root, "release/release-env-owner-input-packet/01-release-infra.json"), {
  owner: "release-infra",
  redacted: true,
});
fs.writeFileSync(path.join(root, "release/release-env-owner-input-packet/01-release-infra.md"), "# release-infra\n");
writeJson(path.join(explainDir, "runtime-appearance.json"), {
  generatedAt: "2026-06-14T00:00:00.000Z",
  queryName: "runtime-appearance",
});

{
  const result = runManifest(root, explainDir);
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const manifest = readJson(path.join(root, "release", "evidence-manifest.json"));
  assert.equal(manifest.status, "PASS");
  assert.equal(manifest.summary.requiredArtifacts, requiredManifestArtifacts.length);
  assert.equal(manifest.summary.presentArtifacts, requiredManifestArtifacts.length + 11);
  assert.equal(manifest.summary.optionalArtifacts, 11);
  assert.equal(manifest.summary.blockers, 0);
  assert.equal(manifest.artifacts.some((artifact) => artifact.relativePath === "release/evidence-manifest-preflight.json"), false);
  const optionalReport = manifest.artifacts.find((artifact) => artifact.relativePath === "release/release-final-owner-queue-run-report.json");
  assert(optionalReport);
  assert.equal(optionalReport.status, "PRESENT");
  assert.deepEqual(optionalReport.contractIssues, []);
  assert.equal(optionalReport.sha256, sha256(path.join(root, "release/release-final-owner-queue-run-report.json")));
  const nextActionRunReport = manifest.artifacts.find((artifact) => artifact.relativePath === "release/release-next-action-run-report.json");
  assert(nextActionRunReport);
  assert.equal(nextActionRunReport.status, "PRESENT");
  assert.deepEqual(nextActionRunReport.contractIssues, []);
  assert.equal(nextActionRunReport.sha256, sha256(path.join(root, "release/release-next-action-run-report.json")));
  const releaseExecutionRunReport = manifest.artifacts.find((artifact) => artifact.relativePath === "release/release-execution-run-report.json");
  assert(releaseExecutionRunReport);
  assert.equal(releaseExecutionRunReport.status, "PRESENT");
  assert.deepEqual(releaseExecutionRunReport.contractIssues, []);
  assert.equal(releaseExecutionRunReport.sha256, sha256(path.join(root, "release/release-execution-run-report.json")));
  const explainGateReport = manifest.artifacts.find((artifact) => artifact.relativePath === "release/explain-gate-report.json");
  assert(explainGateReport);
  assert.equal(explainGateReport.status, "PRESENT");
  assert.deepEqual(explainGateReport.contractIssues, []);
  assert.equal(explainGateReport.sha256, sha256(path.join(root, "release/explain-gate-report.json")));
  const unblockBrief = manifest.artifacts.find((artifact) => artifact.relativePath === "release/release-unblock-brief.json");
  assert(unblockBrief);
  assert.equal(unblockBrief.status, "PRESENT");
  assert.deepEqual(unblockBrief.contractIssues, []);
  assert.equal(unblockBrief.sha256, sha256(path.join(root, "release/release-unblock-brief.json")));
  const pathLeakReport = manifest.artifacts.find((artifact) => artifact.relativePath === "release/release-artifact-path-leak-contract.json");
  assert(pathLeakReport);
  assert.equal(pathLeakReport.status, "PRESENT");
  assert.deepEqual(pathLeakReport.contractIssues, []);
  assert.equal(pathLeakReport.sha256, sha256(path.join(root, "release/release-artifact-path-leak-contract.json")));
  const configOwnerInputReconciliation = manifest.artifacts.find((artifact) => artifact.relativePath === "release/release-config-owner-input-reconciliation.json");
  assert(configOwnerInputReconciliation);
  assert.equal(configOwnerInputReconciliation.status, "PRESENT");
  assert.deepEqual(configOwnerInputReconciliation.contractIssues, []);
  assert.equal(configOwnerInputReconciliation.sha256, sha256(path.join(root, "release/release-config-owner-input-reconciliation.json")));
  const ownerInputReceipt = manifest.artifacts.find((artifact) => artifact.relativePath === "release/release-owner-input-receipt.json");
  assert(ownerInputReceipt);
  assert.equal(ownerInputReceipt.status, "PRESENT");
  assert.deepEqual(ownerInputReceipt.contractIssues, []);
  assert.equal(ownerInputReceipt.sha256, sha256(path.join(root, "release/release-owner-input-receipt.json")));
  const ownerInputReceiptCsv = manifest.artifacts.find((artifact) => artifact.relativePath === "release/release-owner-input-receipt.csv");
  assert(ownerInputReceiptCsv);
  assert.equal(ownerInputReceiptCsv.status, "PRESENT");
  assert.equal(ownerInputReceiptCsv.parseError, null);
  assert.deepEqual(ownerInputReceiptCsv.contractIssues, []);
  assert.equal(ownerInputReceiptCsv.sha256, sha256(path.join(root, "release/release-owner-input-receipt.csv")));
  const ownerInputReceiptItemsCsv = manifest.artifacts.find((artifact) => artifact.relativePath === "release/release-owner-input-receipt-items.csv");
  assert(ownerInputReceiptItemsCsv);
  assert.equal(ownerInputReceiptItemsCsv.status, "PRESENT");
  assert.equal(ownerInputReceiptItemsCsv.parseError, null);
  assert.deepEqual(ownerInputReceiptItemsCsv.contractIssues, []);
  assert.equal(ownerInputReceiptItemsCsv.sha256, sha256(path.join(root, "release/release-owner-input-receipt-items.csv")));
  const ownerInputReceiptItemsMarkdown = manifest.artifacts.find((artifact) => artifact.relativePath === "release/release-owner-input-receipt-items.md");
  assert(ownerInputReceiptItemsMarkdown);
  assert.equal(ownerInputReceiptItemsMarkdown.status, "PRESENT");
  assert.equal(ownerInputReceiptItemsMarkdown.parseError, null);
  assert.deepEqual(ownerInputReceiptItemsMarkdown.contractIssues, []);
  assert.equal(ownerInputReceiptItemsMarkdown.sha256, sha256(path.join(root, "release/release-owner-input-receipt-items.md")));
}

{
  const ownerPacketLeakManifestRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-owner-packet-path-leak-"));
  fs.cpSync(root, ownerPacketLeakManifestRoot, { recursive: true });
  const pathLeakReportPath = path.join(ownerPacketLeakManifestRoot, "release/release-artifact-path-leak-contract.json");
  const pathLeakReport = readJson(pathLeakReportPath);
  pathLeakReport.releaseEnvDisplayScannedFiles = 1;
  pathLeakReport.releaseEnvDisplayScanned = pathLeakReport.releaseEnvDisplayScanned.filter((entry) => !String(entry.file).endsWith("01-release-infra.md"));
  writeJson(pathLeakReportPath, pathLeakReport);
  const result = runManifestAllowBlockers(ownerPacketLeakManifestRoot, explainDir);
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const manifest = readJson(path.join(ownerPacketLeakManifestRoot, "release", "evidence-manifest.json"));
  const pathLeakReportArtifact = manifest.artifacts.find((artifact) => artifact.relativePath === "release/release-artifact-path-leak-contract.json");
  assert(pathLeakReportArtifact);
  assert(pathLeakReportArtifact.contractIssues.some((issue) => issue.includes("path leak report missing owner input packet scan: release/release-env-owner-input-packet/01-release-infra.md")));
  assert(manifest.blockers.some((blocker) => blocker.includes("optional artifact release/release-artifact-path-leak-contract.json")));
}

{
  const failedConfigOwnerInputRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-config-owner-input-fail-"));
  fs.cpSync(root, failedConfigOwnerInputRoot, { recursive: true });
  const reconciliationPath = path.join(failedConfigOwnerInputRoot, "release/release-config-owner-input-reconciliation.json");
  const reconciliation = readJson(reconciliationPath);
  reconciliation.status = "FAIL";
  reconciliation.summary.mappedConfigPlaceholderKeys = 0;
  reconciliation.summary.unmappedConfigPlaceholderKeys = 1;
  reconciliation.unmappedConfigPlaceholderKeys = ["DB_PASSWORD"];
  reconciliation.issueCount = 1;
  reconciliation.issues = ["config placeholder keys must be covered by owner input packet: DB_PASSWORD"];
  writeJson(reconciliationPath, reconciliation);
  const result = runManifestAllowBlockers(failedConfigOwnerInputRoot, explainDir);
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const manifest = readJson(path.join(failedConfigOwnerInputRoot, "release", "evidence-manifest.json"));
  const configOwnerInputArtifact = manifest.artifacts.find((artifact) => artifact.relativePath === "release/release-config-owner-input-reconciliation.json");
  assert(configOwnerInputArtifact);
  assert(configOwnerInputArtifact.contractIssues.some((issue) => issue.includes("config owner input reconciliation status must be PASS")));
  assert(configOwnerInputArtifact.contractIssues.some((issue) => issue.includes("unmappedConfigPlaceholderKeys must be 0")));
  assert(manifest.blockers.some((blocker) => blocker.includes("optional artifact release/release-config-owner-input-reconciliation.json")));
}

const targetRelativePath = "readiness/summary.json";
const targetFile = path.join(root, targetRelativePath);
const beforeManifest = readJson(path.join(root, "release", "evidence-manifest.json"));
const beforeReport = beforeManifest.artifacts.find((artifact) => artifact.relativePath === targetRelativePath);

writeJson(targetFile, {
  generatedAt: "2026-06-14T00:00:01.000Z",
  sourceEnvironment: "staging",
  releaseCandidate: "rc-1",
  evidenceOperator: "release-owner",
  status: "PASS",
  relativePath: targetRelativePath,
  regenerated: true,
});

{
  const result = runManifest(root, explainDir);
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const manifest = readJson(path.join(root, "release", "evidence-manifest.json"));
  const afterReport = manifest.artifacts.find((artifact) => artifact.relativePath === targetRelativePath);
  assert.notEqual(afterReport.sha256, beforeReport.sha256);
  assert.equal(afterReport.sha256, sha256(targetFile));
  assert.notEqual(afterReport.bytes, beforeReport.bytes);
  assert.equal(afterReport.timestamp.field, "generatedAt");
  assert.equal(afterReport.timestamp.value, "2026-06-14T00:00:01.000Z");
}

{
  const failingRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-report-only-"));
  const failingExplainDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-manifest-report-only-explain-"));
  const result = runManifestReportOnly(failingRoot, failingExplainDir);
  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stderr, /continuing because DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false/);
  const manifest = readJson(path.join(failingRoot, "release", "evidence-manifest.json"));
  assert.equal(manifest.status, "FAIL");
  assert(manifest.blockers.length > 1);
  assert.equal(manifest.summary.blockers, manifest.blockers.length);
  assert(manifest.blockers.some((blocker) => blocker.includes("manifest provenance sourceEnvironment")));
  assert(manifest.blockers.some((blocker) => blocker.includes("missing artifact build/backend-build-evidence.json")));
}

console.log("[ddd-release-evidence-manifest.test] ok");
