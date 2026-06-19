#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-canonical-lint-"));

function runLint(sourceFile, reportFile) {
  return spawnSync("node", ["scripts/ddd-release-env-canonical-lint.mjs", sourceFile], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_ENV_CANONICAL_LINT_REPORT: reportFile,
    },
  });
}

const validFile = path.join(tmpDir, "valid.env");
const validReport = path.join(tmpDir, "valid.json");
fs.writeFileSync(validFile, [
  "LUMIRA_BASE_URL=https://api.lumira-prod.internal",
  "PLAYWRIGHT_BASE_URL=https://app.lumira-prod.internal",
  "DB_URL=jdbc:mysql://prod-db.internal:3306/lumira",
  "DB_USERNAME=lumira_app",
  "DB_PASSWORD=abcdefghijklmnopqrstuvwxyz123456",
  "REDIS_HOST=prod-redis.internal",
  "REDIS_PORT=6379",
  "REDIS_PASSWORD=abcdefghijklmnopqrstuvwxyz123456",
  "JWT_SECRET=abcdefghijklmnopqrstuvwxyz1234567890",
  "FIELD_SECRET=abcdefghijklmnopqrstuvwxyz1234567890",
  "CORS_ALLOWED_ORIGIN_PATTERNS=https://app.lumira-prod.internal",
  "TRUST_FORWARDED_HEADERS=true",
  "SYSTEM_SERVICE_BASE_URL=https://system.lumira-prod.internal",
  "AUTH_SERVICE_BASE_URL=https://auth.lumira-prod.internal",
  "FILE_SERVICE_BASE_URL=https://file.lumira-prod.internal",
  "MESSAGE_SERVICE_BASE_URL=https://message.lumira-prod.internal",
  "PLUGIN_SERVICE_BASE_URL=https://plugin.lumira-prod.internal",
  "LOCALIZATION_SERVICE_BASE_URL=https://localization.lumira-prod.internal",
  "PAYMENT_SERVICE_BASE_URL=https://payment.lumira-prod.internal",
  "AI_SERVICE_BASE_URL=https://ai.lumira-prod.internal",
  "JOB_EXECUTOR_BASE_URL=https://job.lumira-prod.internal",
  "SAAS_JOB_INTERNAL_TOKEN=abcdefghijklmnopqrstuvwxyz1234567890",
  "SAAS_JOB_BACKEND_BASE_URL=https://backend-job.lumira-prod.internal",
  "SAAS_JOB_MESSAGE_SERVICE_BASE_URL=https://message-job.lumira-prod.internal",
  "SAAS_JOB_FILE_SERVICE_BASE_URL=https://file-job.lumira-prod.internal",
  "SAAS_JOB_PAYMENT_SERVICE_BASE_URL=https://payment-job.lumira-prod.internal",
  "SAAS_JOB_PLUGIN_SERVICE_BASE_URL=https://plugin-job.lumira-prod.internal",
  "XXL_JOB_ADMIN_ADDRESSES=https://xxl-job.lumira-prod.internal",
  "XXL_JOB_ACCESS_TOKEN=abcdefghijklmnopqrstuvwxyz1234567890",
  "SAAS_EVENT_OUTBOX_DISPATCHER=redis-stream",
  "SAAS_EVENT_REDIS_STREAM_KEY=lumira:ddd:outbox",
  "UPLOAD_STORAGE_ROOT=/srv/lumira/uploads",
  "LUMIRA_FILE_SECURITY_SCAN_MODE=async",
  "LUMIRA_FILE_OCR_MODE=async",
  "PAYMENT_PUBLIC_BASE_URL=https://pay.lumira-prod.internal",
  "DDD_PAYMENT_WEBHOOK_SECRET=abcdefghijklmnopqrstuvwxyz1234567890",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED=true",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL=https://llm.lumira-prod.internal",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=abcdefghijklmnopqrstuvwxyz1234567890",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL=lumira-chat",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL=lumira-embed",
  "LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN=abcdefghijklmnopqrstuvwxyz1234567890",
  "LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED=true",
  "LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL=https://iam.lumira-prod.internal",
  "LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED=true",
  "LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL=https://platform.lumira-prod.internal",
  "LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED=true",
  "LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL=https://file.lumira-prod.internal",
  "",
].join("\n"));
fs.chmodSync(validFile, 0o600);

const validRun = runLint(validFile, validReport);
assert.equal(validRun.status, 0, validRun.stderr);
assert.match(validRun.stdout, /PASS/);
const validJson = JSON.parse(fs.readFileSync(validReport, "utf8"));
assert.equal(validJson.status, "PASS");
assert.equal(validJson.summary.blockers, 0);
assert.equal(validJson.summary.missingCanonicalKeys, 0);
assert(validJson.summary.validatedKeys > 40);

const placeholderFile = path.join(tmpDir, "placeholder.env");
const placeholderReport = path.join(tmpDir, "placeholder.json");
fs.writeFileSync(placeholderFile, "LUMIRA_BASE_URL=__REQUIRED__\n");
fs.chmodSync(placeholderFile, 0o600);
const placeholderRun = runLint(placeholderFile, placeholderReport);
assert.notEqual(placeholderRun.status, 0);
assert.match(placeholderRun.stderr, /required canonical key is unresolved/);
const placeholderJson = JSON.parse(fs.readFileSync(placeholderReport, "utf8"));
assert(placeholderJson.warnings.some((warning) => warning.includes("contract keys")));

const badUrlFile = path.join(tmpDir, "bad-url.env");
const badUrlReport = path.join(tmpDir, "bad-url.json");
fs.writeFileSync(badUrlFile, [
  "LUMIRA_BASE_URL=http://localhost:8080",
  "PLAYWRIGHT_BASE_URL=https://app.lumira-prod.internal",
  "DB_URL=jdbc:mysql://prod-db.internal:3306/lumira",
  "DB_USERNAME=lumira_app",
  "DB_PASSWORD=abcdefghijklmnopqrstuvwxyz123456",
  "",
].join("\n"));
fs.chmodSync(badUrlFile, 0o600);
const badUrlRun = runLint(badUrlFile, badUrlReport);
assert.notEqual(badUrlRun.status, 0);
assert.match(badUrlRun.stderr, /LUMIRA_BASE_URL: must be an HTTPS URL/);
assert.match(badUrlRun.stderr, /LUMIRA_BASE_URL: must not point to localhost or loopback/);

const broadModeFile = path.join(tmpDir, "broad.env");
const broadModeReport = path.join(tmpDir, "broad.json");
fs.writeFileSync(broadModeFile, [
  "LUMIRA_BASE_URL=https://api.lumira-prod.internal",
  "JWT_SECRET=abcdefghijklmnopqrstuvwxyz1234567890",
  "",
].join("\n"));
fs.chmodSync(broadModeFile, 0o644);
const broadModeRun = runLint(broadModeFile, broadModeReport);
if (process.platform === "win32") {
  assert.equal(broadModeRun.status, 0, broadModeRun.stderr);
  const broadModeReportJson = JSON.parse(fs.readFileSync(broadModeReport, "utf8"));
  assert.equal(broadModeReportJson.sourceSecurity.permissionCheckSkipped, true);
} else {
  assert.notEqual(broadModeRun.status, 0);
  assert.match(broadModeRun.stderr, /permissions are too broad/);
}

console.log("[ddd-release-env-canonical-lint.test] ok");
