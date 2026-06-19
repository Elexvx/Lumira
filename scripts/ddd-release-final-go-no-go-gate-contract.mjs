#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const releaseDir = path.join(repoRoot, "artifacts", "ddd", "release");
const repoRootPattern = repoRoot.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const packetPath = process.env.DDD_FINAL_GO_NO_GO_CONTRACT_PACKET
  ? path.resolve(repoRoot, process.env.DDD_FINAL_GO_NO_GO_CONTRACT_PACKET)
  : path.join(releaseDir, "release-final-go-no-go.json");
const markdownPath = process.env.DDD_FINAL_GO_NO_GO_CONTRACT_MARKDOWN
  ? path.resolve(repoRoot, process.env.DDD_FINAL_GO_NO_GO_CONTRACT_MARKDOWN)
  : path.join(releaseDir, "release-final-go-no-go.md");
const gatePath = path.join(releaseDir, "release-final-go-no-go-gate.sh");
const failures = [];
const tempFiles = [];
const requiredHandoffCommands = [
  "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
  "DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs",
  "DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh",
];
const unsafeDisplayCommandPatterns = [
  /DDD_RELEASE_ENV_FILE=(?!<release-env-file>)(?:"[^"`\s|]+"|'[^'`\s|]+'|[^\s`|]+)/,
  /(^|\s)(?:[^\s`|]*\/)?\.env\.release(?:\.[A-Za-z0-9_-]+)?(?=\s|`|\)|,|$)/,
  new RegExp(repoRootPattern),
];

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

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function toBashPath(file) {
  const resolved = path.resolve(file);
  if (process.platform !== "win32") {
    return resolved;
  }
  return resolved.replace(/^([A-Za-z]):\\/, (_, drive) => `/mnt/${drive.toLowerCase()}/`).replaceAll("\\", "/");
}

function executableGatePath() {
  if (process.platform !== "win32") {
    return gatePath;
  }
  const normalizedGatePath = path.join(
    os.tmpdir(),
    `lumira-release-final-go-no-go-gate-${process.pid}.sh`,
  );
  const nodeForBash = `'${toBashPath(process.execPath).replaceAll("'", "'\\''")}'`;
  const source = fs.readFileSync(gatePath, "utf8")
    .replace(/\r\n/g, "\n")
    .replace(/\bnode --input-type=module\b/g, `${nodeForBash} --input-type=module`);
  fs.writeFileSync(normalizedGatePath, source, { mode: 0o700 });
  tempFiles.push(normalizedGatePath);
  return normalizedGatePath;
}

function runGate(packetFile, env = {}) {
  return spawnSync("bash", [toBashPath(executableGatePath())], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      LUMIRA_REPO_ROOT: toBashPath(repoRoot),
      DDD_FINAL_GO_NO_GO_PACKET: toBashPath(packetFile),
      DDD_NODE_BIN: toBashPath(process.execPath),
      ...env,
    },
  });
}

function canRunGateWithBashNode() {
  const probe = spawnSync("bash", ["-lc", "command -v node >/dev/null 2>&1"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  return probe.status === 0;
}

if (!fs.existsSync(gatePath)) {
  addFailure(`final go/no-go gate script must exist: ${gatePath}`);
} else {
  const mode = fs.statSync(gatePath).mode & 0o777;
  if (process.platform !== "win32" && (mode & 0o111) === 0) {
    addFailure("final go/no-go gate script must be executable");
  }
  const source = fs.readFileSync(gatePath, "utf8");
  for (const snippet of [
    "set -euo pipefail",
    "DDD_FINAL_GO_NO_GO_ENFORCE",
    "DDD_STAGING_FINAL_REVIEW_ENFORCE",
    "DDD_NODE_BIN",
    "DDD_FINAL_GO_NO_GO_PACKET",
    "\"${DDD_NODE_BIN}\" scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
    "staging-final-review-blocked",
    "finalRecommendation",
    "cutoverAllowed",
    "noAutoWaivers",
    "gate.blockers",
    "goWithStopReasons",
    "cutoverAllowedWithStopReasons",
    "cutoverAllowedWithGateBlockers",
    "configOwnerInputReconciliation",
    "ownerInputReceipt",
    "configOwnerInputUnmapped",
    "configOwnerInputMappedCount",
    "gateBlockers=",
    "releaseEnvFile.ready must be true before cutoverAllowed can be true",
    "releaseEnvFileCutoverSafe",
    "releaseEnvFileCutoverSafeMismatch",
    "releaseEnvFile must be PASS release-env-file with checked chmod 600 permissions before cutoverAllowed can be true",
    "exit 10",
    "exit 0",
    "Run: node scripts/ddd-release-readiness-summary.mjs",
  ]) {
    if (!source.includes(snippet)) addFailure(`final go/no-go gate script must include ${snippet}`);
  }
}

if (!fs.existsSync(packetPath)) {
  addFailure(`final go/no-go packet must exist: ${packetPath}`);
} else {
  const packet = readJson(packetPath);
  if (!["GO_STRICT", "NO_GO_STRICT"].includes(packet.finalRecommendation || packet.recommendation)) {
    addFailure("final go/no-go packet must declare GO_STRICT or NO_GO_STRICT");
  }
  if (packet.noAutoWaivers !== true) addFailure("final go/no-go packet must forbid automatic waivers");
  if (typeof packet.cutoverAllowed !== "boolean") addFailure("final go/no-go packet must declare cutoverAllowed boolean");
  if (typeof packet.releaseEnvFileCutoverSafe !== "boolean") {
    addFailure("final go/no-go packet must declare releaseEnvFileCutoverSafe boolean");
  }
  if (!packet.gate || typeof packet.gate.blockers !== "number") addFailure("final go/no-go packet must include gate.blockers");
  const releaseEnvFile = packet.safetySignals?.releaseEnvFile || {};
  const releaseEnvFileCutoverSafe = releaseEnvFile.ready === true
    && releaseEnvFile.status === "PASS"
    && releaseEnvFile.inputKind === "release-env-file"
    && releaseEnvFile.envFilePresent === true
    && releaseEnvFile.generatedMissingTemplate !== true
    && releaseEnvFile.securityChecked === true
    && releaseEnvFile.permissionSafe === true
    && releaseEnvFile.permissionCheckSkipped !== true
    && releaseEnvFile.modeOctal === (releaseEnvFile.requiredMode || "600")
    && (releaseEnvFile.requiredMode || "600") === "600";
  if (typeof packet.releaseEnvFileCutoverSafe === "boolean"
    && packet.releaseEnvFileCutoverSafe !== releaseEnvFileCutoverSafe) {
    addFailure("final go/no-go packet releaseEnvFileCutoverSafe must match safe release env file predicate");
  }
  const currentStopReasons = Array.isArray(packet.currentStopReasons) ? packet.currentStopReasons : [];
  if ((packet.finalRecommendation || packet.recommendation) === "GO_STRICT" && currentStopReasons.length > 0) {
    addFailure("final go/no-go packet cannot declare GO_STRICT while stop reasons remain");
  }
  const expectedCutoverAllowed = (packet.finalRecommendation || packet.recommendation) === "GO_STRICT"
    && releaseEnvFileCutoverSafe
    && currentStopReasons.length === 0
    && Number(packet.gate?.blockers ?? 0) === 0;
  if (packet.cutoverAllowed !== expectedCutoverAllowed) {
    addFailure("final go/no-go cutoverAllowed must require GO_STRICT, safe release env file, zero gate blockers, and zero stop reasons");
  }
  if (packet.cutoverAllowed === false && (!Array.isArray(packet.currentStopReasons) || packet.currentStopReasons.length === 0)) {
    addFailure("NO-GO final packet must include stop reasons");
  }
  if (!Array.isArray(packet.nextCommands) || !packet.nextCommands.includes("node scripts/ddd-release-readiness-summary.mjs")) {
    addFailure("final go/no-go packet must include readiness summary rerun command");
  }
  for (const [index, command] of (packet.nextCommands || []).entries()) {
    assertSafeDisplayCommand(`final go/no-go nextCommands[${index}]`, command);
  }
  for (const [waveIndex, wave] of (packet.closureWaves || []).entries()) {
    for (const [commandIndex, command] of (wave.commands || []).entries()) {
      assertSafeDisplayCommand(`final go/no-go closureWaves[${waveIndex}].commands[${commandIndex}]`, command);
    }
    for (const [commandIndex, command] of (wave.rerunCommands || []).entries()) {
      assertSafeDisplayCommand(`final go/no-go closureWaves[${waveIndex}].rerunCommands[${commandIndex}]`, command);
    }
  }
  if (!String(packet.ciSummary?.finalGoNoGoEnforceCommand || "").includes("DDD_FINAL_GO_NO_GO_ENFORCE=1")) {
    addFailure("final go/no-go packet must publish enforce command");
  }
  const configOwnerInputReconciliation = packet.ciSummary?.configOwnerInputReconciliation || {};
  if (!configOwnerInputReconciliation || typeof configOwnerInputReconciliation !== "object") {
    addFailure("final go/no-go packet must include ciSummary.configOwnerInputReconciliation");
  } else {
    if (configOwnerInputReconciliation.status !== "PASS") {
      addFailure("final go/no-go packet config owner input reconciliation must be PASS");
    }
    if (Number(configOwnerInputReconciliation.unmappedConfigPlaceholderKeys ?? 0) !== 0) {
      addFailure("final go/no-go packet config owner input reconciliation must have zero unmapped config placeholders");
    }
    if (Number(configOwnerInputReconciliation.mappedConfigPlaceholderKeys ?? -1)
      !== Number(configOwnerInputReconciliation.uniqueConfigPlaceholderKeys ?? -2)) {
      addFailure("final go/no-go packet config owner input reconciliation mapped count must match unique placeholder keys");
    }
  }
  const ownerInputReceipt = packet.ciSummary?.ownerInputReceipt || {};
  if (!ownerInputReceipt || typeof ownerInputReceipt !== "object") {
    addFailure("final go/no-go packet must include ciSummary.ownerInputReceipt");
  } else {
    if (!["PASS", "PENDING_OWNER_INPUT"].includes(ownerInputReceipt.status)) {
      addFailure("final go/no-go packet owner input receipt status must be PASS or PENDING_OWNER_INPUT");
    }
    if (ownerInputReceipt.status === "PASS" && ownerInputReceipt.cutoverReady !== true) {
      addFailure("final go/no-go packet PASS owner input receipt must be cutoverReady=true");
    }
    if (ownerInputReceipt.status === "PENDING_OWNER_INPUT" && ownerInputReceipt.cutoverReady !== false) {
      addFailure("final go/no-go packet pending owner input receipt must be cutoverReady=false");
    }
    if (!Number.isInteger(ownerInputReceipt.requiredOwnerInputs) || ownerInputReceipt.requiredOwnerInputs < 0) {
      addFailure("final go/no-go packet owner input receipt must include requiredOwnerInputs");
    }
    if (!Array.isArray(ownerInputReceipt.missingCriteria)) {
      addFailure("final go/no-go packet owner input receipt must include missingCriteria array");
    }
    if (ownerInputReceipt.status === "PENDING_OWNER_INPUT" && ownerInputReceipt.missingCriteria?.length === 0) {
      addFailure("final go/no-go packet pending owner input receipt must include missing criteria");
    }
  }
  if (!fs.existsSync(markdownPath)) {
    addFailure(`final go/no-go markdown must exist: ${markdownPath}`);
  } else {
    const markdown = fs.readFileSync(markdownPath, "utf8");
    if (!markdown.includes("# DDD Final Go/No-Go Packet")) {
      addFailure("final go/no-go markdown must include packet heading");
    }
    assertSafeDisplayCommand("final go/no-go markdown", markdown);
    for (const command of packet.nextCommands || []) {
      if (!markdown.includes(command)) {
        addFailure(`final go/no-go markdown must include next command: ${command}`);
      }
    }
    for (const command of requiredHandoffCommands) {
      if ((packet.nextCommands || []).includes(command) && !markdown.includes(command)) {
        addFailure(`final go/no-go markdown must include required handoff command: ${command}`);
      }
    }
  }
}

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-final-go-no-go-contract-"));
try {
  const basePacket = {
    recommendation: "NO_GO_STRICT",
    finalRecommendation: "NO_GO_STRICT",
    cutoverAllowed: false,
    releaseEnvFileCutoverSafe: false,
    noAutoWaivers: true,
    gate: { blockers: 1, warnings: 0 },
    currentStopReasons: ["strict release gate blockers=1"],
    nextCommands: ["node scripts/ddd-release-readiness-summary.mjs"],
    ciSummary: {
      stopOwners: ["release-owner"],
      blockedArtifactPaths: [],
      blockedContentHints: [],
      nonGoExitCode: 10,
      exitCodeMap: {
        finalNoGo: 10,
        finalPacketInvalid: 11,
        releaseEnvUnresolved: 21,
        releaseEnvInvalidPacket: 22,
      },
      releaseEnvReadiness: {
        blockers: 1,
        placeholders: 1,
        missing: 0,
        filledRedacted: 0,
        ownerCount: 1,
      },
      configOwnerInputReconciliation: {
        status: "PASS",
        configPlaceholderBlockers: 1,
        uniqueConfigPlaceholderKeys: 1,
        ownerInputKeys: 1,
        mappedConfigPlaceholderKeys: 1,
        unmappedConfigPlaceholderKeys: 0,
        duplicateConfigPlaceholderBlockers: 0,
        ownerInputsWithoutConfigPlaceholder: 0,
        issueCount: 0,
      },
      ownerInputReceipt: {
        status: "PENDING_OWNER_INPUT",
        cutoverReady: false,
        requiredOwnerInputs: 1,
        ownerCount: 1,
        readyOwnerCount: 0,
        pendingOwnerCount: 1,
        missingCriteria: ["releaseEnvReadinessStatus"],
      },
      firstNextCommand: "node scripts/ddd-release-readiness-summary.mjs",
    },
    safetySignals: {
      releaseEnvFile: {
        ready: false,
        status: "FAIL",
        inputKind: "release-env-file",
        envFilePresent: true,
        securityChecked: true,
        permissionSafe: true,
        modeOctal: "600",
        requiredMode: "600",
      },
    },
  };
  const noGoPacket = path.join(tmpDir, "no-go.json");
  fs.writeFileSync(noGoPacket, `${JSON.stringify(basePacket, null, 2)}\n`);
  if (canRunGateWithBashNode()) {
  const noGoDefault = runGate(noGoPacket);
  if (noGoDefault.status !== 0) addFailure(`NO-GO default mode must print and exit 0: ${noGoDefault.stderr || noGoDefault.stdout}`);
  if (!noGoDefault.stderr.includes("[ddd-final-go-no-go][no-go]")) addFailure("NO-GO default mode must print no-go status");

  const noGoEnforce = runGate(noGoPacket, { DDD_FINAL_GO_NO_GO_ENFORCE: "1" });
  if (noGoEnforce.status !== 10) addFailure(`NO-GO enforce mode must exit 10, got ${noGoEnforce.status}`);

  const goPacket = path.join(tmpDir, "go.json");
  fs.writeFileSync(goPacket, `${JSON.stringify({
    ...basePacket,
    recommendation: "GO_STRICT",
    finalRecommendation: "GO_STRICT",
    cutoverAllowed: true,
    releaseEnvFileCutoverSafe: true,
    gate: { blockers: 0, warnings: 0 },
    currentStopReasons: [],
    safetySignals: {
      releaseEnvFile: {
        ready: true,
        status: "PASS",
        inputKind: "release-env-file",
        envFilePresent: true,
        securityChecked: true,
        permissionSafe: true,
        modeOctal: "600",
        requiredMode: "600",
      },
    },
  }, null, 2)}\n`);
  const goRunBlockedByStaging = runGate(goPacket, { DDD_FINAL_GO_NO_GO_ENFORCE: "1" });
  if (goRunBlockedByStaging.status !== 10) {
    addFailure(`GO enforce mode must still exit 10 when staging final review is blocked, got ${goRunBlockedByStaging.status}`);
  }
  if (!goRunBlockedByStaging.stderr.includes("[ddd-final-go-no-go][staging-final-review-blocked]")) {
    addFailure("GO enforce mode must name staging-final-review-blocked when staging evidence is incomplete");
  }

  const goRun = runGate(goPacket, {
    DDD_FINAL_GO_NO_GO_ENFORCE: "1",
    DDD_STAGING_FINAL_REVIEW_ENFORCE: "0",
  });
  if (goRun.status !== 0) addFailure(`GO enforce mode with staging review bypass for packet-only contract must exit 0: ${goRun.stderr || goRun.stdout}`);
  if (!goRun.stdout.includes("[ddd-final-go-no-go][go] cutover allowed")) addFailure("GO mode must print cutover allowed");

  const unsafeGoPacket = path.join(tmpDir, "unsafe-go.json");
  fs.writeFileSync(unsafeGoPacket, `${JSON.stringify({
    ...basePacket,
    recommendation: "GO_STRICT",
    finalRecommendation: "GO_STRICT",
    cutoverAllowed: true,
    gate: { blockers: 0, warnings: 0 },
    currentStopReasons: [],
  }, null, 2)}\n`);
  const unsafeGoRun = runGate(unsafeGoPacket);
  if (unsafeGoRun.status !== 4) addFailure(`cutoverAllowed=true with releaseEnvFile.ready=false must exit 4, got ${unsafeGoRun.status}`);

  const unsafePermissionPacket = path.join(tmpDir, "unsafe-permission-go.json");
  fs.writeFileSync(unsafePermissionPacket, `${JSON.stringify({
    ...basePacket,
    recommendation: "GO_STRICT",
    finalRecommendation: "GO_STRICT",
    cutoverAllowed: true,
    gate: { blockers: 0, warnings: 0 },
    currentStopReasons: [],
    safetySignals: {
      releaseEnvFile: {
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
      },
    },
  }, null, 2)}\n`);
  const unsafePermissionRun = runGate(unsafePermissionPacket);
  if (unsafePermissionRun.status !== 4) addFailure(`cutoverAllowed=true with unsafe release env permissions must exit 4, got ${unsafePermissionRun.status}`);
  if (!unsafePermissionRun.stderr.includes("checked chmod 600 permissions")) {
    addFailure("unsafe release env permission failure must explain chmod 600 requirement");
  }

  const goWithStopReasonsPacket = path.join(tmpDir, "go-with-stop-reasons.json");
  fs.writeFileSync(goWithStopReasonsPacket, `${JSON.stringify({
    ...basePacket,
    recommendation: "GO_STRICT",
    finalRecommendation: "GO_STRICT",
    cutoverAllowed: true,
    releaseEnvFileCutoverSafe: true,
    gate: { blockers: 0, warnings: 0 },
    currentStopReasons: ["cutover checklist blocked: release-environment"],
    safetySignals: {
      releaseEnvFile: {
        ready: true,
        status: "PASS",
        inputKind: "release-env-file",
        envFilePresent: true,
        securityChecked: true,
        permissionSafe: true,
        modeOctal: "600",
        requiredMode: "600",
      },
    },
  }, null, 2)}\n`);
  const goWithStopReasonsRun = runGate(goWithStopReasonsPacket);
  if (goWithStopReasonsRun.status !== 11) {
    addFailure(`GO packet with stop reasons must exit 11, got ${goWithStopReasonsRun.status}`);
  }
  if (!goWithStopReasonsRun.stderr.includes("goWithStopReasons")) {
    addFailure("GO packet with stop reasons must name goWithStopReasons");
  }

  const mismatchedEnvSafetyPacket = path.join(tmpDir, "mismatched-env-safety.json");
  fs.writeFileSync(mismatchedEnvSafetyPacket, `${JSON.stringify({
    ...basePacket,
    releaseEnvFileCutoverSafe: true,
  }, null, 2)}\n`);
  const mismatchedEnvSafetyRun = runGate(mismatchedEnvSafetyPacket);
  if (mismatchedEnvSafetyRun.status !== 11) {
    addFailure(`releaseEnvFileCutoverSafe mismatch must exit 11, got ${mismatchedEnvSafetyRun.status}`);
  }
  if (!mismatchedEnvSafetyRun.stderr.includes("releaseEnvFileCutoverSafeMismatch")) {
    addFailure("releaseEnvFileCutoverSafe mismatch must name releaseEnvFileCutoverSafeMismatch");
  }

  const failedConfigOwnerInputPacket = path.join(tmpDir, "failed-config-owner-input.json");
  fs.writeFileSync(failedConfigOwnerInputPacket, `${JSON.stringify({
    ...basePacket,
    ciSummary: {
      ...basePacket.ciSummary,
      configOwnerInputReconciliation: {
        ...basePacket.ciSummary.configOwnerInputReconciliation,
        status: "FAIL",
        mappedConfigPlaceholderKeys: 0,
        unmappedConfigPlaceholderKeys: 1,
        issueCount: 1,
      },
    },
  }, null, 2)}\n`);
  const failedConfigOwnerInputRun = runGate(failedConfigOwnerInputPacket);
  if (failedConfigOwnerInputRun.status !== 11) {
    addFailure(`failed config owner input reconciliation must exit 11, got ${failedConfigOwnerInputRun.status}`);
  }
  if (!failedConfigOwnerInputRun.stderr.includes("configOwnerInputReconciliation.status")
    || !failedConfigOwnerInputRun.stderr.includes("configOwnerInputUnmapped")) {
    addFailure("failed config owner input reconciliation must name status and unmapped failures");
  }

  const invalidPacket = path.join(tmpDir, "invalid.json");
  fs.writeFileSync(invalidPacket, `${JSON.stringify({ recommendation: "GO_STRICT" }, null, 2)}\n`);
  const invalidRun = runGate(invalidPacket);
  if (invalidRun.status !== 11) addFailure(`invalid final packet must exit 11, got ${invalidRun.status}`);
  }
} finally {
  fs.rmSync(tmpDir, { recursive: true, force: true });
  for (const file of tempFiles) {
    fs.rmSync(file, { force: true });
  }
}

if (failures.length > 0) {
  throw new Error(`release final go/no-go gate contract failed: ${failures.join("; ")}`);
}

console.log("[ddd-release-final-go-no-go-gate-contract] ok");
