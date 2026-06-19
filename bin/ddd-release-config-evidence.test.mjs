#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync, spawnSync } from "node:child_process";
import { validateReleaseConfigArtifact } from "./ddd-release-config-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const script = path.join(repoRoot, "scripts", "ddd-release-config-evidence.mjs");

function validEnv() {
  return {
    LUMIRA_BASE_URL: "https://api.staging.lumira.app",
    PLAYWRIGHT_BASE_URL: "https://app.staging.lumira.app",
    DB_URL: "jdbc:mysql://mysql.staging.lumira.app:3306/lumira?useSSL=true",
    DB_USERNAME: "lumira_app",
    DB_PASSWORD: "x".repeat(20),
    REDIS_HOST: "redis.staging.lumira.app",
    REDIS_PORT: "6379",
    REDIS_PASSWORD: "r".repeat(20),
    JWT_SECRET: "j".repeat(32),
    FIELD_SECRET: "f".repeat(32),
    CORS_ALLOWED_ORIGIN_PATTERNS: "https://app.staging.lumira.app",
    TRUST_FORWARDED_HEADERS: "true",
    LUMIRA_SYSTEM_SERVICE_BASE_URL: "https://system.staging.lumira.app",
    LUMIRA_AUTH_SERVICE_BASE_URL: "https://auth.staging.lumira.app",
    LUMIRA_FILE_SERVICE_BASE_URL: "https://file.staging.lumira.app",
    LUMIRA_MESSAGE_SERVICE_BASE_URL: "https://message.staging.lumira.app",
    LUMIRA_PLUGIN_SERVICE_BASE_URL: "https://plugin.staging.lumira.app",
    LUMIRA_LOCALIZATION_SERVICE_BASE_URL: "https://localization.staging.lumira.app",
    LUMIRA_PAYMENT_SERVICE_BASE_URL: "https://payment.staging.lumira.app",
    LUMIRA_AI_SERVICE_BASE_URL: "https://ai.staging.lumira.app",
    LUMIRA_JOB_EXECUTOR_BASE_URL: "https://job.staging.lumira.app",
    LUMIRA_JOB_INTERNAL_TOKEN: "t".repeat(32),
    LUMIRA_JOB_BACKEND_BASE_URL: "https://api.staging.lumira.app",
    LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL: "https://message.staging.lumira.app",
    LUMIRA_JOB_FILE_SERVICE_BASE_URL: "https://file.staging.lumira.app",
    LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL: "https://payment.staging.lumira.app",
    LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL: "https://plugin.staging.lumira.app",
    LUMIRA_XXL_JOB_ADMIN_ADDRESSES: "https://xxl.staging.lumira.app/admin",
    LUMIRA_XXL_JOB_ACCESS_TOKEN: "x".repeat(32),
    LUMIRA_EVENT_OUTBOX_DISPATCHER: "redis-stream",
    LUMIRA_EVENT_REDIS_STREAM_KEY: "lumira:platform-events",
    LUMIRA_UPLOAD_STORAGE_ROOT: "/opt/lumira/uploads",
    LUMIRA_FILE_SECURITY_SCAN_MODE: "CLAMAV",
    LUMIRA_FILE_OCR_MODE: "TESSERACT",
    PAYMENT_PUBLIC_BASE_URL: "https://api.staging.lumira.app",
    LUMIRA_PAYMENT_WEBHOOK_SECRET: "p".repeat(32),
    LUMIRA_AI_PROVIDER_ENABLED: "true",
    LUMIRA_AI_PROVIDER_BASE_URL: "https://ai-provider.staging.lumira.app/v1",
    LUMIRA_AI_PROVIDER_API_KEY: "a".repeat(32),
    LUMIRA_AI_CHAT_MODEL: "chat-model",
    LUMIRA_AI_EMBEDDING_MODEL: "embedding-model",
    LUMIRA_AI_OWNER_INTERNAL_TOKEN: "o".repeat(32),
    LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED: "true",
    LUMIRA_AI_OWNER_IAM_BASE_URL: "https://system.staging.lumira.app",
    LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED: "true",
    LUMIRA_AI_OWNER_PLATFORM_BASE_URL: "https://system.staging.lumira.app",
    LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED: "true",
    LUMIRA_AI_OWNER_FILE_BASE_URL: "https://file.staging.lumira.app",
  };
}

function writeEnvFile(file, env) {
  fs.writeFileSync(file, Object.entries(env).map(([key, value]) => `${key}=${value}`).join("\n"));
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-config-"));
  const envFile = path.join(directory, ".env.release");
  const report = path.join(directory, "release-config-evidence.json");
  writeEnvFile(envFile, validEnv());
  execFileSync("node", [script], {
    cwd: repoRoot,
    env: {
      ...process.env,
      DDD_RELEASE_ENV_FILE: envFile,
      DDD_RELEASE_CONFIG_REPORT: report,
      DDD_RELEASE_CONFIG_STRICT: "true",
      DDD_EVIDENCE_ENVIRONMENT: "staging",
      DDD_RELEASE_CANDIDATE: "rc-20260614",
      DDD_EVIDENCE_OPERATOR: "ci",
    },
    stdio: "pipe",
  });
  const artifact = JSON.parse(fs.readFileSync(report, "utf8"));
  assert.equal(artifact.status, "PASS");
  assert.equal(artifact.inputKind, "release-env-file");
  assert.equal(artifact.envFileExists, true);
  assert.equal(artifact.sourceEnvironment, "staging");
  assert.equal(artifact.releaseCandidate, "rc-20260614");
  assert.equal(artifact.evidenceOperator, "ci");
  assert.deepEqual(artifact.blockersByOwner, {});
  assert.equal(artifact.summary.requiredChecks, 46);
  assert.equal(artifact.summary.runtimePresentRequiredChecks, artifact.summary.requiredChecks);
  assert.equal(artifact.summary.envFileCoveredRequiredChecks, artifact.summary.requiredChecks);
  assert.equal(artifact.summary.templateCoveredRequiredChecks, artifact.summary.requiredChecks);
  assert.equal(artifact.summary.workflowCoveredRequiredChecks, artifact.summary.requiredChecks);
  assert.equal(artifact.summary.primaryBlockers, 0);
  assert.equal(artifact.summary.releaseConfigBlockersFromPlaceholders, 0);
  assert.equal(artifact.summary.releaseConfigBlockersAfterPlaceholders, 0);
  assert.deepEqual(artifact.primaryBlockers, []);
  assert.deepEqual(artifact.placeholderDerivedConfigBlockers, []);
  assert.equal(artifact.coverageMatrix.find((entry) => entry.check === "backend base url").envFileCovered, true);
  assert.deepEqual(validateReleaseConfigArtifact(artifact), []);
  const drifted = {
    ...artifact,
    status: "PASS",
    summary: {
      ...artifact.summary,
      groups: 0,
      blockers: 1,
      warnings: 1,
    },
    blockers: ["forced blocker"],
    warnings: [],
    blockerDetails: [],
    blockersByGroup: {},
    blockersByOwner: {},
  };
  const issues = validateReleaseConfigArtifact(drifted);
  assert(issues.includes("release config status must be FAIL, got PASS"));
  assert(issues.includes(`release config summary groups mismatch: declared=0, actual=${artifact.groups.length}`));
  assert(issues.includes("release config summary warnings mismatch: declared=1, actual=0"));
  assert(issues.includes("release config blockerDetails mismatch: details=0, blockers=1"));
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-config-env-file-provenance-"));
  const envFile = path.join(directory, ".env.release");
  const report = path.join(directory, "release-config-evidence.json");
  writeEnvFile(envFile, {
    ...validEnv(),
    DDD_EVIDENCE_ENVIRONMENT: "staging",
    DDD_RELEASE_CANDIDATE: "rc-from-env-file",
    DDD_EVIDENCE_OPERATOR: "release-owner",
  });
  execFileSync("node", [script], {
    cwd: repoRoot,
    env: {
      DDD_RELEASE_ENV_FILE: envFile,
      DDD_RELEASE_CONFIG_REPORT: report,
      DDD_RELEASE_CONFIG_STRICT: "true",
      PATH: process.env.PATH,
    },
    stdio: "pipe",
  });
  const artifact = JSON.parse(fs.readFileSync(report, "utf8"));
  assert.equal(artifact.status, "PASS");
  assert.equal(artifact.sourceEnvironment, "staging");
  assert.equal(artifact.releaseCandidate, "rc-from-env-file");
  assert.equal(artifact.evidenceOperator, "release-owner");
  assert.deepEqual(artifact.primaryBlockers, []);
  assert.deepEqual(validateReleaseConfigArtifact(artifact), []);
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-config-"));
  const envFile = path.join(directory, ".env.release");
  const report = path.join(directory, "release-config-evidence.json");
  writeEnvFile(envFile, validEnv());
  const result = spawnSync("node", [script], {
    cwd: repoRoot,
    env: {
      DDD_RELEASE_ENV_FILE: envFile,
      DDD_RELEASE_CONFIG_REPORT: report,
      DDD_RELEASE_CONFIG_STRICT: "true",
      PATH: process.env.PATH,
    },
    encoding: "utf8",
  });
  assert.notEqual(result.status, 0);
  const artifact = JSON.parse(fs.readFileSync(report, "utf8"));
  assert(artifact.blockers.includes("provenance.sourceEnvironment is required"));
  assert(artifact.blockers.includes("provenance.releaseCandidate is required"));
  assert(artifact.blockers.includes("provenance.evidenceOperator is required"));
  assert.equal(artifact.blockersByOwner["release-infra"], 3);
  assert.equal(artifact.blockerDetails[0].group, "provenance");
  assert.deepEqual(artifact.blockerDetails[0].envKeys, ["DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_CANDIDATE", "DDD_EVIDENCE_OPERATOR"]);
  assert.equal(artifact.coverageMatrix.every((entry) => entry.templateCovered), true);
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-config-"));
  const envFile = path.join(directory, ".env.release");
  const report = path.join(directory, "release-config-evidence.json");
  writeEnvFile(envFile, validEnv());
  const result = spawnSync("node", [script], {
    cwd: repoRoot,
    env: {
      DDD_RELEASE_ENV_FILE: envFile,
      DDD_RELEASE_CONFIG_REPORT: report,
      DDD_RELEASE_EVIDENCE_STRICT: "true",
      PATH: process.env.PATH,
    },
    encoding: "utf8",
  });
  assert.notEqual(result.status, 0);
  const artifact = JSON.parse(fs.readFileSync(report, "utf8"));
  assert.equal(artifact.strict, true);
  assert(artifact.blockers.includes("provenance.sourceEnvironment is required"));
}

{
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-config-generated-template-"));
  const releaseDir = path.join(directory, "release");
  fs.mkdirSync(releaseDir, { recursive: true });
  const missingEnvReport = path.join(releaseDir, "release-env-missing.json");
  const generatedTemplate = path.join(releaseDir, "release-env-missing.template.env");
  const report = path.join(directory, "release-config-evidence.json");
  fs.writeFileSync(missingEnvReport, `${JSON.stringify({ status: "NOT_READY" }, null, 2)}\n`);
  fs.writeFileSync(generatedTemplate, [
    "LUMIRA_BASE_URL=__REQUIRED__",
    "PLAYWRIGHT_BASE_URL=__REQUIRED__",
    "DB_URL=__REQUIRED__",
    "DB_USERNAME=__REQUIRED__",
    "DB_PASSWORD=__REQUIRED__",
    "",
  ].join("\n"));
  const result = spawnSync("node", [script], {
    cwd: repoRoot,
    env: {
      DDD_RELEASE_ENV_FILE: generatedTemplate,
      DDD_RELEASE_MISSING_ENV_REPORT: missingEnvReport,
      DDD_RELEASE_CONFIG_REPORT: report,
      PATH: process.env.PATH,
    },
    encoding: "utf8",
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const artifact = JSON.parse(fs.readFileSync(report, "utf8"));
  assert.equal(artifact.status, "FAIL");
  assert.equal(artifact.inputKind, "generated-missing-template");
  assert.equal(artifact.generatedMissingTemplate, true);
  assert.equal(artifact.envFile, generatedTemplate);
  assert.equal(artifact.envFileExists, true);
  assert(artifact.summary.blockers > 0);
  assert(artifact.summary.releaseConfigBlockersFromPlaceholders > 0);
  assert(artifact.summary.primaryBlockers < artifact.summary.blockers);
  assert.equal(artifact.placeholderDerivedConfigBlockers.length, artifact.summary.releaseConfigBlockersFromPlaceholders);
  assert(artifact.blockerDetails.some((detail) => detail.matchedKey === "LUMIRA_BASE_URL"));
  assert(artifact.blockerDetails.some((detail) => detail.blockedByPlaceholderKey === true));
}

console.log("[ddd-release-config-evidence.test] ok");
