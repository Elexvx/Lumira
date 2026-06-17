#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const bootstrapPath = path.join(releaseDir, "release-env-bootstrap.sh");
const receiptPath = path.join(releaseDir, "release-env-bootstrap-receipt.json");

function readText(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env bootstrap artifact: ${file}`);
  }
  return fs.readFileSync(file, "utf8");
}

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env bootstrap artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const bootstrap = readText(bootstrapPath);
const receipt = readJson(receiptPath);
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function runBootstrap(env) {
  return spawnSync("bash", [bootstrapPath], {
    cwd: process.cwd(),
    encoding: "utf8",
    env: { ...process.env, ...env },
  });
}

function readOptionalJson(file) {
  return fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, "utf8")) : null;
}

const requiredScriptSnippets = [
  "set -euo pipefail",
  "trap on_bootstrap_exit EXIT",
  "write_bootstrap_receipt FAIL",
  "release-env-missing.template.env",
  "release-closure-wave-env.template.env",
  "release-final-owner-queue-env.template.env",
  "release-env-canonical-fill.template.env",
  "node scripts/ddd-release-env-owner-templates-merge.mjs",
  "node scripts/ddd-release-env-canonical-merge.mjs",
  "node scripts/ddd-release-env-alias-sync.mjs",
  "node scripts/ddd-release-env-canonical-lint.mjs",
  "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
  "node scripts/ddd-release-env-file-lint.mjs",
  "node scripts/ddd-release-config-evidence.mjs",
  "DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs",
  "node scripts/ddd-release-readiness-summary.mjs",
  "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
  "write_bootstrap_receipt PASS 0",
];

for (const snippet of requiredScriptSnippets) {
  if (!bootstrap.includes(snippet)) addFailure(`bootstrap script must include ${snippet}`);
}
if (/\bsource\s+/.test(bootstrap)) addFailure("bootstrap script must not source env files into the shell");

const orderedSteps = [
  "owner-templates-merge",
  "canonical-merge",
  "alias-sync",
  "canonical-lint",
  "env-readiness-gate",
  "release-env-lint",
  "release-config-evidence",
  "manifest-provenance-env",
  "readiness-summary",
  "final-go-no-go",
  "complete",
];
let previousIndex = -1;
for (const step of orderedSteps) {
  const index = bootstrap.indexOf(`DDD_RELEASE_ENV_BOOTSTRAP_STEP="${step}"`);
  if (index < 0) addFailure(`bootstrap script must declare step ${step}`);
  if (index <= previousIndex) addFailure("bootstrap steps must be ordered");
  previousIndex = index;
}

if (!["PASS", "FAIL"].includes(receipt.status)) addFailure("receipt.status must be PASS or FAIL");
if (!Number.isInteger(receipt.exitCode)) addFailure("receipt.exitCode must be an integer");
if (!orderedSteps.includes(receipt.step) && receipt.step !== "init") addFailure("receipt.step must be a known bootstrap step");
if (receipt.status === "FAIL" && receipt.failedStep !== receipt.step) addFailure("FAIL receipt failedStep must match step");
if (receipt.status === "FAIL" && receipt.completedStep !== null) addFailure("FAIL receipt completedStep must be null");
if (receipt.status === "PASS" && receipt.completedStep !== receipt.step) addFailure("PASS receipt completedStep must match step");
if (receipt.status === "PASS" && receipt.exitCode !== 0) addFailure("PASS receipt exitCode must be 0");
if (!receipt.envFile || /release-env-(missing|canonical-fill|closure-wave)|release-final-owner-queue-env/.test(receipt.envFile)) {
  addFailure("receipt.envFile must point to a real release env file, not a generated template");
}
if (receipt.canonicalEnvFile !== "artifacts/ddd/release/release-env-canonical-fill.template.env") addFailure("receipt.canonicalEnvFile must be the canonical fill template");
if (receipt.ownerTemplateDir !== "artifacts/ddd/release/release-env-owner-templates") addFailure("receipt.ownerTemplateDir must be the owner template directory");
if (receipt.receiptPath !== "artifacts/ddd/release/release-env-bootstrap-receipt.json" && !receipt.receiptPath.endsWith("release-env-bootstrap-test-receipt.json")) {
  addFailure("receipt.receiptPath must be the release bootstrap receipt path or test receipt path");
}

const expectedReceiptFields = {
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
};
for (const [field, expected] of Object.entries(expectedReceiptFields)) {
  if (Object.hasOwn(receipt, field) && receipt[field] !== expected) addFailure(`receipt.${field} must be ${expected}`);
}
if (receipt.nextCommand !== `DDD_RELEASE_ENV_FILE=${receipt.envFile} bash artifacts/ddd/release/release-env-bootstrap.sh`) {
  addFailure("receipt.nextCommand must rerun bootstrap with the same env file");
}

const receiptText = JSON.stringify(receipt);
if (receiptText.includes("__REQUIRED__")) addFailure("receipt must not expose template placeholders");
if (/\b(?!(DDD_RELEASE_ENV_FILE|DDD_RELEASE_ENV_READINESS_ENFORCE|DDD_RELEASE_MANIFEST_CHECK_ENV|DDD_FINAL_GO_NO_GO_ENFORCE)\b)[A-Z][A-Z0-9_]*=/.test(receiptText)) {
  addFailure("receipt must not expose concrete env assignments");
}
if (/\b(?:mysql|postgres|jdbc|redis|amqp):\/\/[^\s,;")]+/i.test(receiptText)) addFailure("receipt must not expose DSNs");
if (/\b(?:sk-[A-Za-z0-9_-]{12,}|eyJ[A-Za-z0-9_-]{20,})\b/.test(receiptText)) addFailure("receipt must not expose token-like values");

if (!process.env.DDD_RELEASE_DIR) {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-bootstrap-contract-runtime-"));
  try {
    const runtimeReceiptPath = path.join(tmpDir, "bootstrap-receipt.json");
    const missingEnvPath = path.join(tmpDir, "missing.env");
    const missingRun = runBootstrap({
      DDD_RELEASE_ENV_FILE: missingEnvPath,
      DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT: runtimeReceiptPath,
    });
    if (missingRun.status === 0) addFailure("bootstrap must fail when DDD_RELEASE_ENV_FILE is missing");
    if (!missingRun.stderr.includes("DDD_RELEASE_ENV_FILE does not exist")) addFailure("missing env failure must explain DDD_RELEASE_ENV_FILE");
    const missingReceipt = readOptionalJson(runtimeReceiptPath);
    if (!missingReceipt || missingReceipt.status !== "FAIL" || missingReceipt.step !== "init") {
      addFailure("missing env failure must write FAIL receipt at init step");
    }

    const generatedTemplatePath = path.join(tmpDir, "release-env-missing.template.env");
    fs.writeFileSync(generatedTemplatePath, "LUMIRA_BASE_URL=__REQUIRED__\n");
    const templateReceiptPath = path.join(tmpDir, "template-receipt.json");
    const templateRun = runBootstrap({
      DDD_RELEASE_ENV_FILE: generatedTemplatePath,
      DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT: templateReceiptPath,
    });
    if (templateRun.status === 0) addFailure("bootstrap must reject generated template env files");
    if (!templateRun.stderr.includes("Refusing to use a generated template as DDD_RELEASE_ENV_FILE")) {
      addFailure("generated template env failure must explain refusal");
    }
    const templateReceipt = readOptionalJson(templateReceiptPath);
    if (!templateReceipt || templateReceipt.status !== "FAIL" || templateReceipt.step !== "init") {
      addFailure("generated template failure must write FAIL receipt at init step");
    }

    const envFilePath = path.join(tmpDir, ".env.release.local");
    const nonCanonicalPath = path.join(tmpDir, "custom-canonical.env");
    const nonCanonicalReceiptPath = path.join(tmpDir, "non-canonical-receipt.json");
    fs.writeFileSync(envFilePath, "LUMIRA_BASE_URL=https://api.example.invalid\n");
    fs.writeFileSync(nonCanonicalPath, "LUMIRA_BASE_URL=https://api.example.invalid\n");
    const nonCanonicalRun = runBootstrap({
      DDD_RELEASE_ENV_FILE: envFilePath,
      DDD_RELEASE_CANONICAL_ENV_FILE: nonCanonicalPath,
      DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT: nonCanonicalReceiptPath,
    });
    if (nonCanonicalRun.status === 0) addFailure("bootstrap must reject non-canonical DDD_RELEASE_CANONICAL_ENV_FILE");
    if (!nonCanonicalRun.stderr.includes("Refusing to use a non-canonical generated env file")) {
      addFailure("non-canonical env failure must explain refusal");
    }
    const nonCanonicalReceipt = readOptionalJson(nonCanonicalReceiptPath);
    if (!nonCanonicalReceipt || nonCanonicalReceipt.status !== "FAIL" || nonCanonicalReceipt.step !== "init") {
      addFailure("non-canonical env failure must write FAIL receipt at init step");
    }
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
}

if (failures.length > 0) {
  throw new Error(`release env bootstrap contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-env-bootstrap-contract] ok status=${receipt.status} step=${receipt.step}`);
