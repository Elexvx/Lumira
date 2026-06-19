#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const scriptPath = path.join(repoRoot, "scripts", "ddd-release-env-owner-templates-merge.mjs");
const artifactRoot = path.join(repoRoot, "artifacts", "ddd", "release");
const ownerTemplatesDir = path.join(artifactRoot, "release-env-owner-templates");
const canonicalTemplateFile = path.join(artifactRoot, "release-env-canonical-fill.template.env");
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function runMerge(sourceDir, targetFile, reportFile, env = {}) {
  return spawnSync("node", ["scripts/ddd-release-env-owner-templates-merge.mjs", sourceDir, targetFile], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_ENV_OWNER_TEMPLATES_MERGE_REPORT: reportFile,
      ...env,
    },
  });
}

function ownerTemplateHasConcreteSecret(text) {
  return text.split(/\r?\n/)
    .map((line) => line.match(/^\s*(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*?)\s*$/))
    .filter(Boolean)
    .some((match) => {
      const key = match[1];
      const value = String(match[2] || "").replace(/^["']|["']$/g, "").trim();
      return /(PASSWORD|SECRET|TOKEN|KEY|CREDENTIAL|PRIVATE|ACCESS_TOKEN)/i.test(key)
        && value !== ""
        && value !== "__REQUIRED__";
    });
}

const source = fs.readFileSync(scriptPath, "utf8");
for (const snippet of [
  "owner template dir does not exist",
  "canonical target file does not exist",
  "concrete secret values require chmod 600",
  "is not a canonical release config key",
  "conflicting owner template values",
  "canonical target already has a different concrete value",
  "DDD_RELEASE_ENV_OWNER_TEMPLATES_MERGE_DRY_RUN",
  "DDD_RELEASE_ENV_OWNER_TEMPLATES_MERGE_FORCE",
  "must be an HTTPS URL",
  "must not point to localhost or loopback",
]) {
  if (!source.includes(snippet)) addFailure(`owner templates merge script must include ${snippet}`);
}
if (/^\s*source\s+/m.test(source)) addFailure("owner templates merge script must not source env files");

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-owner-templates-merge-contract-"));
try {
  const sourceDir = path.join(tmpDir, "owners");
  const targetFile = path.join(tmpDir, "canonical.env");
  const reportFile = path.join(tmpDir, "merge.json");
  fs.mkdirSync(sourceDir);
  fs.writeFileSync(path.join(sourceDir, "01-platform.env"), [
    "LUMIRA_BASE_URL=https://api.lumira-prod.internal",
    "DB_PASSWORD=abcdefghijklmnopqrstuvwxyz123456",
    "JWT_SECRET=__REQUIRED__",
    "",
  ].join("\n"));
  fs.writeFileSync(path.join(sourceDir, "02-ai.env"), [
    "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL=lumira-chat",
    "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=__REQUIRED__",
    "",
  ].join("\n"));
  fs.chmodSync(path.join(sourceDir, "01-platform.env"), 0o600);
  fs.chmodSync(path.join(sourceDir, "02-ai.env"), 0o600);
  fs.writeFileSync(targetFile, [
    "LUMIRA_BASE_URL=__REQUIRED__",
    "DB_PASSWORD=__REQUIRED__",
    "JWT_SECRET=__REQUIRED__",
    "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL=__REQUIRED__",
    "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=__REQUIRED__",
    "",
  ].join("\n"));
  fs.chmodSync(targetFile, 0o600);

  const passRun = runMerge(sourceDir, targetFile, reportFile);
  if (passRun.status !== 0) addFailure(`owner templates merge should pass valid owner templates: ${passRun.stderr}`);
  const report = readJson(reportFile);
  if (report.status !== "PASS") addFailure("owner templates merge report must be PASS");
  if (report.summary?.ownerTemplateFiles !== 2) addFailure("owner templates merge must report two owner template files");
  if (report.summary?.concreteSourceKeys !== 3) addFailure("owner templates merge must count three concrete source keys");
  if (report.summary?.updates !== 3) addFailure("owner templates merge must update three placeholder values");
  const targetText = fs.readFileSync(targetFile, "utf8");
  if (!/^LUMIRA_BASE_URL=https:\/\/api\.lumira-prod\.internal$/m.test(targetText)) addFailure("owner templates merge must update LUMIRA_BASE_URL");
  if (!/^DB_PASSWORD=abcdefghijklmnopqrstuvwxyz123456$/m.test(targetText)) addFailure("owner templates merge must update DB_PASSWORD");
  if (!/^LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL=lumira-chat$/m.test(targetText)) addFailure("owner templates merge must update AI chat model");
  if (!/^JWT_SECRET=__REQUIRED__$/m.test(targetText)) addFailure("owner templates merge must not copy unresolved owner placeholders");

  const dryTarget = path.join(tmpDir, "dry.env");
  const dryReport = path.join(tmpDir, "dry.json");
  fs.writeFileSync(dryTarget, "DB_PASSWORD=__REQUIRED__\n");
  fs.chmodSync(dryTarget, 0o600);
  const beforeDry = fs.readFileSync(dryTarget, "utf8");
  const dryRun = runMerge(sourceDir, dryTarget, dryReport, { DDD_RELEASE_ENV_OWNER_TEMPLATES_MERGE_DRY_RUN: "1" });
  if (dryRun.status !== 0) addFailure(`owner templates merge dry-run should pass: ${dryRun.stderr}`);
  if (fs.readFileSync(dryTarget, "utf8") !== beforeDry) addFailure("owner templates merge dry-run must not mutate target file");
  if (readJson(dryReport).dryRun !== true) addFailure("owner templates merge dry-run report must set dryRun=true");

  const conflictDir = path.join(tmpDir, "conflict");
  const conflictTarget = path.join(tmpDir, "conflict.env");
  const conflictReport = path.join(tmpDir, "conflict.json");
  fs.mkdirSync(conflictDir);
  fs.writeFileSync(path.join(conflictDir, "01.env"), "DB_URL=jdbc:mysql://one.internal:3306/lumira\n");
  fs.writeFileSync(path.join(conflictDir, "02.env"), "DB_URL=jdbc:mysql://two.internal:3306/lumira\n");
  fs.writeFileSync(conflictTarget, "DB_URL=__REQUIRED__\n");
  fs.chmodSync(path.join(conflictDir, "01.env"), 0o600);
  fs.chmodSync(path.join(conflictDir, "02.env"), 0o600);
  fs.chmodSync(conflictTarget, 0o600);
  const conflictRun = runMerge(conflictDir, conflictTarget, conflictReport);
  if (conflictRun.status === 0) addFailure("owner templates merge must fail conflicting owner values");
  if (!readJson(conflictReport).blockers.some((blocker) => blocker.includes("conflicting owner template values"))) addFailure("owner templates merge conflict report must include conflict blocker");

  const unknownDir = path.join(tmpDir, "unknown");
  const unknownTarget = path.join(tmpDir, "unknown.env");
  const unknownReport = path.join(tmpDir, "unknown.json");
  fs.mkdirSync(unknownDir);
  fs.writeFileSync(path.join(unknownDir, "01.env"), "UNOWNED_RUNTIME_SECRET=abcdefghijklmnopqrstuvwxyz123456\n");
  fs.writeFileSync(unknownTarget, "UNOWNED_RUNTIME_SECRET=__REQUIRED__\n");
  fs.chmodSync(path.join(unknownDir, "01.env"), 0o600);
  fs.chmodSync(unknownTarget, 0o600);
  const unknownRun = runMerge(unknownDir, unknownTarget, unknownReport);
  if (unknownRun.status === 0) addFailure("owner templates merge must fail non-canonical owner keys");
  if (!readJson(unknownReport).blockers.some((blocker) => blocker.includes("is not a canonical release config key"))) addFailure("owner templates merge unknown-key report must include canonical key blocker");

  const existingTarget = path.join(tmpDir, "existing.env");
  const existingReport = path.join(tmpDir, "existing.json");
  fs.writeFileSync(existingTarget, "DB_PASSWORD=existing-prod-password-1234567890\n");
  fs.chmodSync(existingTarget, 0o600);
  const existingRun = runMerge(sourceDir, existingTarget, existingReport);
  if (existingRun.status === 0) addFailure("owner templates merge must fail existing concrete target conflicts without force");
  if (!readJson(existingReport).blockers.some((blocker) => blocker.includes("canonical target already has a different concrete value"))) addFailure("owner templates merge existing-target report must include overwrite blocker");

  const invalidDir = path.join(tmpDir, "invalid");
  const invalidTarget = path.join(tmpDir, "invalid.env");
  const invalidReport = path.join(tmpDir, "invalid.json");
  fs.mkdirSync(invalidDir);
  fs.writeFileSync(path.join(invalidDir, "01.env"), "LUMIRA_BASE_URL=http://localhost:8080\n");
  fs.writeFileSync(invalidTarget, "LUMIRA_BASE_URL=__REQUIRED__\n");
  fs.chmodSync(path.join(invalidDir, "01.env"), 0o600);
  fs.chmodSync(invalidTarget, 0o600);
  const invalidRun = runMerge(invalidDir, invalidTarget, invalidReport);
  if (invalidRun.status === 0) addFailure("owner templates merge must fail invalid owner URL values");
  const invalid = readJson(invalidReport);
  if (!invalid.blockers.some((blocker) => blocker.includes("must be an HTTPS URL"))) addFailure("owner templates merge invalid report must include HTTPS blocker");
  if (!invalid.blockers.some((blocker) => blocker.includes("must not point to localhost"))) addFailure("owner templates merge invalid report must include non-local blocker");

  const broadDir = path.join(tmpDir, "broad");
  const broadTarget = path.join(tmpDir, "broad.env");
  const broadReport = path.join(tmpDir, "broad.json");
  fs.mkdirSync(broadDir);
  fs.writeFileSync(path.join(broadDir, "01.env"), "JWT_SECRET=abcdefghijklmnopqrstuvwxyz1234567890\n");
  fs.writeFileSync(broadTarget, "JWT_SECRET=__REQUIRED__\n");
  fs.chmodSync(path.join(broadDir, "01.env"), 0o644);
  fs.chmodSync(broadTarget, 0o600);
  const broadRun = runMerge(broadDir, broadTarget, broadReport);
  const broad = readJson(broadReport);
  if (process.platform === "win32") {
    if (!broad.ownerFiles?.some((file) => file.permissionCheckSkipped === true)) addFailure("owner templates merge broad report must mark permissionCheckSkipped=true on Windows");
  } else {
    if (broadRun.status === 0) addFailure("owner templates merge must fail broad owner template permissions with concrete secret values");
    if (!broad.blockers.some((blocker) => blocker.includes("concrete secret values require chmod 600"))) addFailure("owner templates merge broad report must include permission blocker");
  }
} finally {
  fs.rmSync(tmpDir, { recursive: true, force: true });
}

if (!fs.existsSync(ownerTemplatesDir)) {
  addFailure(`release owner templates dir must exist: ${ownerTemplatesDir}`);
} else {
  const files = fs.readdirSync(ownerTemplatesDir).filter((name) => name.endsWith(".env")).sort();
  if (files.length < 1) addFailure("release owner templates dir must contain owner env templates");
  for (const file of files) {
    const fullPath = path.join(ownerTemplatesDir, file);
    const mode = fs.statSync(fullPath).mode & 0o777;
    const text = fs.readFileSync(fullPath, "utf8");
    if (ownerTemplateHasConcreteSecret(text) && (mode & 0o077) !== 0) {
      addFailure(`${file} permissions must not be group/world readable when it contains concrete secret values`);
    }
  }
}
if (!fs.existsSync(canonicalTemplateFile)) {
  addFailure(`release canonical fill template must exist: ${canonicalTemplateFile}`);
}

if (failures.length > 0) {
  throw new Error(`release env owner templates merge contract failed: ${failures.join("; ")}`);
}

console.log("[ddd-release-env-owner-templates-merge-contract] ok");
