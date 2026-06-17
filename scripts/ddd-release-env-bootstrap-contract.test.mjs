#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function baseArtifacts() {
  const script = [
    "#!/usr/bin/env bash",
    "set -euo pipefail",
    "trap on_bootstrap_exit EXIT",
    "write_bootstrap_receipt FAIL",
    "release-env-missing.template.env",
    "release-closure-wave-env.template.env",
    "release-final-owner-queue-env.template.env",
    "release-env-canonical-fill.template.env",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"owner-templates-merge\"",
    "node scripts/ddd-release-env-owner-templates-merge.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"canonical-merge\"",
    "node scripts/ddd-release-env-canonical-merge.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"alias-sync\"",
    "node scripts/ddd-release-env-alias-sync.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"canonical-lint\"",
    "node scripts/ddd-release-env-canonical-lint.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"env-readiness-gate\"",
    "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"release-env-lint\"",
    "node scripts/ddd-release-env-file-lint.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"release-config-evidence\"",
    "node scripts/ddd-release-config-evidence.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"manifest-provenance-env\"",
    "DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"readiness-summary\"",
    "node scripts/ddd-release-readiness-summary.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"final-go-no-go\"",
    "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"complete\"",
    "write_bootstrap_receipt PASS 0",
    "",
  ].join("\n");
  return {
    script,
    receipt: {
      status: "FAIL",
      exitCode: 1,
      step: "canonical-lint",
      failedStep: "canonical-lint",
      completedStep: null,
      envFile: ".env.release.local",
      canonicalEnvFile: "artifacts/ddd/release/release-env-canonical-fill.template.env",
      ownerTemplateDir: "artifacts/ddd/release/release-env-owner-templates",
      receiptPath: "artifacts/ddd/release/release-env-bootstrap-receipt.json",
      artifactIntegrityGateCommand: "bash artifacts/ddd/release/release-artifact-integrity-gate.sh",
      artifactIntegrityArtifact: "artifacts/ddd/release/release-artifact-integrity.json",
      artifactIntegrityMarkdown: "artifacts/ddd/release/release-artifact-integrity.md",
      envReadinessGateCommand: "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
      envReadinessArtifact: "artifacts/ddd/release/release-env-readiness-redacted.json",
      envReadinessCsv: "artifacts/ddd/release/release-env-readiness-redacted.csv",
      ownerHandoffArtifact: "artifacts/ddd/release/release-env-owner-handoff-redacted.json",
      ownerHandoffCsv: "artifacts/ddd/release/release-env-owner-handoff-redacted.csv",
      ownerHandoffDir: "artifacts/ddd/release/release-env-owner-handoff-redacted",
      finalGoNoGoGateCommand: "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
      finalGoNoGoPacket: "artifacts/ddd/release/release-final-go-no-go.json",
      finalGoNoGoMarkdown: "artifacts/ddd/release/release-final-go-no-go.md",
      nextCommand: "DDD_RELEASE_ENV_FILE=.env.release.local bash artifacts/ddd/release/release-env-bootstrap.sh",
    },
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-env-bootstrap.sh"), artifacts.script);
  fs.writeFileSync(path.join(directory, "release-env-bootstrap-receipt.json"), `${JSON.stringify(artifacts.receipt, null, 2)}\n`);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-bootstrap-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-env-bootstrap-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_DIR: directory },
  });
}

const passResult = runContract();
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /status=FAIL/);

const sourceResult = runContract((artifacts) => {
  artifacts.script += "source .env.release.local\n";
});
assert.notEqual(sourceResult.status, 0);
assert.match(sourceResult.stderr, /must not source/);

const wrongStepResult = runContract((artifacts) => {
  artifacts.receipt.failedStep = "alias-sync";
});
assert.notEqual(wrongStepResult.status, 0);
assert.match(wrongStepResult.stderr, /failedStep/);

const templateEnvResult = runContract((artifacts) => {
  artifacts.receipt.envFile = "artifacts/ddd/release/release-env-canonical-fill.template.env";
});
assert.notEqual(templateEnvResult.status, 0);
assert.match(templateEnvResult.stderr, /not a generated template/);

const leakResult = runContract((artifacts) => {
  artifacts.receipt.leaked = "DB_PASSWORD=super-secret";
});
assert.notEqual(leakResult.status, 0);
assert.match(leakResult.stderr, /env assignments/);

const commandResult = runContract((artifacts) => {
  artifacts.receipt.envReadinessGateCommand = "bash artifacts/ddd/release/release-env-readiness-gate.sh";
});
assert.notEqual(commandResult.status, 0);
assert.match(commandResult.stderr, /envReadinessGateCommand/);

console.log("[ddd-release-env-bootstrap-contract.test] ok");
