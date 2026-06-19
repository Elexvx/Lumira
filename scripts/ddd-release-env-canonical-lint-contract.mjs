#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const scriptPath = path.join(repoRoot, "scripts", "ddd-release-env-canonical-lint.mjs");
const currentReportPath = path.join(releaseDir, "release-env-canonical-lint.json");
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function runLint(sourceFile, reportFile) {
  return spawnSync("node", ["scripts/ddd-release-env-canonical-lint.mjs", sourceFile], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_ENV_CANONICAL_LINT_REPORT: reportFile },
  });
}

const source = fs.readFileSync(scriptPath, "utf8");
for (const snippet of [
  "sourceSecurity",
  "permissionSafe",
  "required canonical key is unresolved",
  "must be an HTTPS URL",
  "must not point to localhost or loopback",
  "must be at least",
  "must not be one of",
  "duplicate canonical source key",
  "invalid canonical source line",
]) {
  if (!source.includes(snippet)) addFailure(`canonical lint script must include ${snippet}`);
}
if (/^\s*source\s+/m.test(source)) addFailure("canonical lint script must not source env files");

if (fs.existsSync(currentReportPath)) {
  const current = readJson(currentReportPath);
  if (!["PASS", "FAIL"].includes(current.status)) addFailure("current canonical lint report status must be PASS or FAIL");
  if (current.summary?.canonicalRequirements !== 48) addFailure("current canonical lint report must cover 48 canonical requirements");
  if (current.summary?.sourceKeys !== 48) addFailure("current canonical lint report must cover 48 source keys");
  if (current.status === "FAIL" && (current.summary?.blockers || 0) <= 0) addFailure("current FAIL canonical lint report must include blockers");
  if (current.sourceSecurity?.checked !== true) addFailure("current canonical lint report must include sourceSecurity");
  if (current.sourceSecurity?.requiredMode !== "600") addFailure("current canonical lint sourceSecurity must require mode 600");
}

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-canonical-lint-contract-"));
try {
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
  if (validRun.status !== 0) addFailure(`canonical lint valid file must pass: ${validRun.stderr}`);
  const valid = readJson(validReport);
  if (valid.status !== "PASS" || valid.summary?.blockers !== 0) addFailure("canonical lint valid report must be PASS with zero blockers");
  if (valid.summary?.validatedKeys < 48) addFailure("canonical lint valid report must validate all canonical keys");

  const placeholderFile = path.join(tmpDir, "placeholder.env");
  const placeholderReport = path.join(tmpDir, "placeholder.json");
  fs.writeFileSync(placeholderFile, "LUMIRA_BASE_URL=__REQUIRED__\n");
  fs.chmodSync(placeholderFile, 0o600);
  const placeholderRun = runLint(placeholderFile, placeholderReport);
  if (placeholderRun.status === 0) addFailure("canonical lint must fail unresolved required placeholders");
  if (!readJson(placeholderReport).blockers.some((blocker) => blocker.includes("required canonical key is unresolved"))) {
    addFailure("canonical lint placeholder report must include unresolved blocker");
  }

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
  if (badUrlRun.status === 0) addFailure("canonical lint must fail localhost/non-HTTPS URLs");
  const badUrl = readJson(badUrlReport);
  if (!badUrl.blockers.some((blocker) => blocker.includes("must be an HTTPS URL"))) addFailure("canonical lint must report HTTPS blocker");
  if (!badUrl.blockers.some((blocker) => blocker.includes("must not point to localhost"))) addFailure("canonical lint must report non-local blocker");

  const broadFile = path.join(tmpDir, "broad.env");
  const broadReport = path.join(tmpDir, "broad.json");
  fs.writeFileSync(broadFile, "JWT_SECRET=abcdefghijklmnopqrstuvwxyz1234567890\n");
  fs.chmodSync(broadFile, 0o644);
  const broadRun = runLint(broadFile, broadReport);
  const broad = readJson(broadReport);
  if (process.platform === "win32") {
    if (broad.sourceSecurity?.permissionCheckSkipped !== true) addFailure("canonical lint broad report must mark permissionCheckSkipped=true on Windows");
  } else {
    if (broadRun.status === 0) addFailure("canonical lint must fail broad permissions with concrete secret values");
    if (broad.sourceSecurity?.permissionSafe !== false) addFailure("canonical lint broad report must mark permissionSafe=false");
    if (!broad.blockers.some((blocker) => blocker.includes("permissions are too broad"))) addFailure("canonical lint broad report must include permission blocker");
  }
} finally {
  fs.rmSync(tmpDir, { recursive: true, force: true });
}

if (failures.length > 0) {
  throw new Error(`release env canonical lint contract failed: ${failures.join("; ")}`);
}

console.log("[ddd-release-env-canonical-lint-contract] ok");
