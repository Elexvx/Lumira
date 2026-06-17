#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function baseArtifacts() {
  const readinessItem = {
    fillOrder: 1,
    owner: "release-infra",
    owners: ["release-infra"],
    group: "runtime",
    requirement: "database password",
    canonicalKey: "DB_PASSWORD",
    status: "PLACEHOLDER",
    required: true,
    secret: true,
    valueClass: "secret",
    safeToPreFill: false,
    blocker: true,
    safeDefaultAvailable: false,
    requiresOwnerInput: true,
    ownerInputReason: "secret-manager",
    validation: {
      https: false,
      nonLocal: false,
      minLength: 16,
      expectedValues: [],
      pattern: null,
      disallowValues: [],
    },
    aliases: ["DB_PASSWORD", "MYSQL_PASSWORD"],
    fillGuidance: "Provide via approved secret manager or secure release channel; never commit.",
  };
  const packetItem = {
    inputOrder: 1,
    ...readinessItem,
    collectionGuidance: "Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.",
  };
  const postCollectionReceipt = {
    redacted: true,
    purpose: "Verify that collected owner values removed release env placeholders without exposing concrete values.",
    commands: [
      "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs",
      "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs",
      "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-alias-sync.mjs",
      "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env",
      "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
      "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
      "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs",
      "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-readiness-summary.mjs",
      "node scripts/ddd-release-config-owner-input-reconciliation.mjs",
      "DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts/ddd/release/release-preflight-gate.sh",
      "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    ],
    passCriteria: {
      releaseEnvReadinessStatus: "PASS",
      releaseEnvReadinessBlockers: 0,
      releaseEnvReadinessPlaceholders: 0,
      releaseEnvReadinessMissing: 0,
      configOwnerInputReconciliationStatus: "PASS",
      configOwnerInputReconciliationUnmappedKeys: 0,
    },
  };
  return {
    readiness: {
      redacted: true,
      envFile: ".env.release.local",
      summary: {
        blockingSafeDefaultAvailable: 0,
        safeDefaultsExhausted: true,
      },
      items: [readinessItem],
    },
    packet: {
      generatedAt: "2026-06-16T00:00:00.000Z",
      status: "ADVISORY",
      redacted: true,
      valuePolicy: "No concrete environment values are emitted; this packet lists only owner, key, validation, reason, and redacted collection guidance.",
      sourceArtifact: "artifacts/ddd/release/release-env-readiness-redacted.json",
      envFile: ".env.release.local",
      summary: {
        requiredOwnerInputs: 1,
        ownerCount: 1,
        secretInputs: 1,
        productionEndpointInputs: 0,
        ownerProductionValueInputs: 0,
        blockingSafeDefaultAvailable: 0,
        safeDefaultsExhausted: true,
        ownerInputReasonCounts: {
          "secret-manager": 1,
        },
      },
      validationCommands: [
        "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
        "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
      ],
      postCollectionReceipt,
      owners: [{
        owner: "release-infra",
        totalInputs: 1,
        secretInputs: 1,
        productionEndpointInputs: 0,
        ownerProductionValueInputs: 0,
        keys: ["DB_PASSWORD"],
        reasons: ["secret-manager"],
        fileName: "01-release-infra",
        packetPath: "artifacts/ddd/release/release-env-owner-input-packet/01-release-infra.json",
        packetMarkdownPath: "artifacts/ddd/release/release-env-owner-input-packet/01-release-infra.md",
        handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/01-release-infra.md",
      }],
      items: [packetItem],
    },
    csv: [
      "inputOrder,owner,canonicalKey,aliases,group,requirement,status,valueClass,ownerInputReason,secret,safeDefaultAvailable,required,validationHttps,validationNonLocal,validationMinLength,validationExpectedValues,collectionGuidance",
      "1,release-infra,DB_PASSWORD,DB_PASSWORD;MYSQL_PASSWORD,runtime,database password,PLACEHOLDER,secret,secret-manager,true,false,true,false,false,16,,Collect through the approved secret manager or secure release channel; do not paste values into chat commits or artifacts.",
      "",
    ].join("\n"),
    markdown: [
      "# DDD Release Env Owner Input Packet",
      "",
      "Concrete values are intentionally omitted from this artifact.",
      "- `DB_PASSWORD` owner=release-infra reason=secret-manager",
      "- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`",
      "## Receipt Gate",
      "- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`",
      "- `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`",
      "- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs`",
      "- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-readiness-summary.mjs`",
      "- `node scripts/ddd-release-config-owner-input-reconciliation.mjs`",
      "- `DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts/ddd/release/release-preflight-gate.sh`",
      "- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`",
      "",
    ].join("\n"),
    ownerPackets: {
      "01-release-infra.json": {
        generatedAt: "2026-06-16T00:00:00.000Z",
        status: "ADVISORY",
        redacted: true,
        valuePolicy: "No concrete environment values are emitted; this packet lists only owner, key, validation, reason, and redacted collection guidance.",
        owner: "release-infra",
        summary: {
          totalInputs: 1,
          secretInputs: 1,
          productionEndpointInputs: 0,
          ownerProductionValueInputs: 0,
          reasons: ["secret-manager"],
          keys: ["DB_PASSWORD"],
        },
        packetPath: "artifacts/ddd/release/release-env-owner-input-packet/01-release-infra.json",
        packetMarkdownPath: "artifacts/ddd/release/release-env-owner-input-packet/01-release-infra.md",
        handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/01-release-infra.md",
        validationCommands: [
          "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
          "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
        ],
        postCollectionReceipt,
        items: [packetItem],
      },
      "01-release-infra.md": [
        "# DDD Release Env Owner Input Packet: release-infra",
        "",
        "Concrete values are intentionally omitted from this artifact.",
        "- `DB_PASSWORD`: class=secret; reason=secret-manager",
        "- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`",
        "## Receipt Gate",
        "- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`",
        "- `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`",
        "- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs`",
        "- `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-readiness-summary.mjs`",
        "- `node scripts/ddd-release-config-owner-input-reconciliation.mjs`",
        "- `DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts/ddd/release/release-preflight-gate.sh`",
        "- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh`",
        "",
      ].join("\n"),
    },
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-env-readiness-redacted.json"), `${JSON.stringify(artifacts.readiness, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-owner-input-packet.json"), `${JSON.stringify(artifacts.packet, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-owner-input-packet.csv"), artifacts.csv);
  fs.writeFileSync(path.join(directory, "release-env-owner-input-packet.md"), artifacts.markdown);
  const ownerPacketDirectory = path.join(directory, "release-env-owner-input-packet");
  fs.mkdirSync(ownerPacketDirectory, { recursive: true });
  for (const [fileName, artifact] of Object.entries(artifacts.ownerPackets || {})) {
    const filePath = path.join(ownerPacketDirectory, fileName);
    const contents = typeof artifact === "string" ? artifact : `${JSON.stringify(artifact, null, 2)}\n`;
    fs.writeFileSync(filePath, contents);
  }
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-owner-input-packet-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-env-owner-input-packet-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_DIR: directory },
  });
}

const passResult = runContract();
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /inputs=1/);

const leakResult = runContract((artifacts) => {
  artifacts.markdown += "DB_PASSWORD=super-secret\n";
});
assert.notEqual(leakResult.status, 0);
assert.match(leakResult.stderr, /must not expose/);

const summaryMismatchResult = runContract((artifacts) => {
  artifacts.packet.summary.requiredOwnerInputs = 2;
});
assert.notEqual(summaryMismatchResult.status, 0);
assert.match(summaryMismatchResult.stderr, /requiredOwnerInputs/);

const readinessMismatchResult = runContract((artifacts) => {
  artifacts.packet.items[0].ownerInputReason = "production-endpoint";
});
assert.notEqual(readinessMismatchResult.status, 0);
assert.match(readinessMismatchResult.stderr, /ownerInputReason/);

const concreteEnvFileResult = runContract((artifacts) => {
  artifacts.packet.validationCommands[0] = "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs";
});
assert.notEqual(concreteEnvFileResult.status, 0);
assert.match(concreteEnvFileResult.stderr, /validationCommands|concrete env/);

const missingReceiptCommandResult = runContract((artifacts) => {
  artifacts.packet.postCollectionReceipt.commands = artifacts.packet.postCollectionReceipt.commands.filter((command) => !command.includes("release-preflight-gate.sh"));
  artifacts.ownerPackets["01-release-infra.json"].postCollectionReceipt = artifacts.packet.postCollectionReceipt;
  artifacts.ownerPackets["01-release-infra.md"] = artifacts.ownerPackets["01-release-infra.md"].replace(/- `DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts\/ddd\/release\/release-preflight-gate\.sh`\n/, "");
});
assert.notEqual(missingReceiptCommandResult.status, 0);
assert.match(missingReceiptCommandResult.stderr, /postCollectionReceipt\.commands|receipt command/);

const missingOwnerPacketResult = runContract((artifacts) => {
  delete artifacts.ownerPackets["01-release-infra.md"];
});
assert.notEqual(missingOwnerPacketResult.status, 0);
assert.match(missingOwnerPacketResult.stderr, /owner packet directory files|markdown packet file/);

console.log("[ddd-release-env-owner-input-packet-contract.test] ok");
