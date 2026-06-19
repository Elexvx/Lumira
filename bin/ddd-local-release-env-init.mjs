#!/usr/bin/env node
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const repoRoot = path.resolve(path.dirname(__filename), "..");
const outputFile = process.env.DDD_LOCAL_RELEASE_ENV_OUTPUT
  ? path.resolve(process.env.DDD_LOCAL_RELEASE_ENV_OUTPUT)
  : path.join(repoRoot, ".env.release.local");
const force = process.argv.includes("--force");

const deployExampleFile = path.join(repoRoot, "deploy", ".env.example");
const deployLocalFile = path.join(repoRoot, "deploy", ".env");
const releaseTemplateFile = path.join(repoRoot, "artifacts", "ddd", "release", "staging-handoff-bundle", "release-env-fill.template.env");

function parseEnv(text) {
  const env = {};
  for (const rawLine of text.split(/\r?\n/u)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const match = line.match(/^(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$/u);
    if (!match) continue;
    const [, key, value] = match;
    env[key] = value.replace(/^['"]|['"]$/gu, "");
  }
  return env;
}

function readEnvFile(file) {
  return fs.existsSync(file) ? parseEnv(fs.readFileSync(file, "utf8")) : {};
}

function randomSecret(label) {
  return `${label}_${crypto.randomBytes(32).toString("base64url")}`;
}

function gitCandidate() {
  try {
    return execFileSync("git", ["rev-parse", "--short=12", "HEAD"], {
      cwd: repoRoot,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    }).trim();
  } catch {
    return `local-${Date.now()}`;
  }
}

function operator() {
  try {
    return os.userInfo().username || "local-operator";
  } catch {
    return "local-operator";
  }
}

function rel(file) {
  return path.relative(repoRoot, file).replaceAll(path.sep, "/");
}

function keyValueLines(env) {
  return Object.entries(env)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}=${value}`);
}

if (fs.existsSync(outputFile) && !force) {
  console.error(`[ddd-local-release-env-init] refusing to overwrite ${rel(outputFile)}; pass --force to regenerate`);
  process.exit(1);
}

const deploy = readEnvFile(deployExampleFile);
const deployLocal = readEnvFile(deployLocalFile);
const source = { ...deploy, ...deployLocal };
const publicDomain = process.env.DDD_LOCAL_RELEASE_DOMAIN || source.API_DOMAIN || "saas.elexvx.com";
const publicBaseUrl = process.env.DDD_LOCAL_RELEASE_BASE_URL || source.FRONTEND_ORIGIN || `https://${publicDomain}`;
const generatedSecretKeys = [];

function isPlaceholder(value) {
  const text = String(value || "").trim();
  return !text || /^change-me-/u.test(text) || /^replace-with-/u.test(text) || text.includes("__REQUIRED");
}

function secretValue(key, label) {
  const value = process.env[key] || source[key] || "";
  if (!isPlaceholder(value)) return value;
  generatedSecretKeys.push(key);
  return randomSecret(label);
}

const dbPassword = secretValue("DB_PASSWORD", "mysql");
const jwtSecret = secretValue("JWT_SECRET", "jwt");
const fieldSecret = secretValue("FIELD_SECRET", "field");
const jobToken = secretValue("SAAS_JOB_INTERNAL_TOKEN", "job");
const xxlJobToken = secretValue("XXL_JOB_ACCESS_TOKEN", "xxl");
const aiToken = secretValue("LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN", "ai_owner");
const aiApiKey = secretValue("LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY", "openai_compatible");
const paymentWebhookSecret = secretValue("PAYMENT_WEBHOOK_SECRET", "payment_webhook");
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || gitCandidate();
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || operator();
const evidenceEnvironment = process.env.DDD_EVIDENCE_ENVIRONMENT || "production-equivalent";

const env = {
  AI_SERVICE_BASE_URL: publicBaseUrl,
  AUTH_SERVICE_BASE_URL: publicBaseUrl,
  BASE_URL: publicBaseUrl,
  CORS_ALLOWED_ORIGIN_PATTERNS: source.CORS_ALLOWED_ORIGIN_PATTERNS || publicBaseUrl,
  DB_PASSWORD: dbPassword,
  DB_URL: source.DB_URL || "jdbc:mysql://mysql:3306/saas?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false",
  DB_USERNAME: source.DB_USERNAME || "root",
  DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "true",
  DDD_AI_EXPECT_PROVIDER_REMOTE: "true",
  DDD_AUTH_PASSWORD: randomSecret("auth_password"),
  DDD_AUTH_PERF_BASELINE_ACCEPTED_BY: evidenceOperator,
  DDD_AUTH_PERF_BASELINE_ENVIRONMENT: evidenceEnvironment,
  DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT: "artifacts/ddd/performance/authenticated-runtime-baseline.json",
  DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE: "artifacts/ddd/performance/authenticated-runtime-actual.json",
  DDD_AUTH_PERF_ENVIRONMENT: evidenceEnvironment,
  DDD_AUTH_USERNAME: "release-operator",
  DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE: "artifacts/ddd/readiness/summary.json",
  DDD_DEPLOYMENT_EVIDENCE: "artifacts/ddd/build/docker-image-evidence.json",
  DDD_EVIDENCE_ENVIRONMENT: evidenceEnvironment,
  DDD_EVIDENCE_OPERATOR: evidenceOperator,
  DDD_EXPLAIN_DATABASE: source.MYSQL_DATABASE || "saas",
  DDD_EXPLAIN_ENVIRONMENT: evidenceEnvironment,
  DDD_EXPLAIN_STRICT: "true",
  DDD_FRONTEND_DEPLOYMENT_EVIDENCE: "artifacts/ddd/lumira-ui/frontend-smoke.json",
  DDD_FRONTEND_EXPECT_DEPLOYED: "true",
  DDD_MIGRATION_COMPLETED_AT: new Date().toISOString(),
  DDD_MIGRATION_FRESH_DB_EVIDENCE: "artifacts/ddd/migration/migration-evidence.json",
  DDD_MIGRATION_FRESH_DB_VALIDATED: "true",
  DDD_MIGRATION_OPERATOR: evidenceOperator,
  DDD_MIGRATION_UPGRADE_DB_EVIDENCE: "artifacts/ddd/migration/migration-evidence.json",
  DDD_MIGRATION_UPGRADE_DB_VALIDATED: "true",
  DDD_RELEASE_CANDIDATE: releaseCandidate,
  DDD_RELEASE_ENVIRONMENT: evidenceEnvironment,
  DDD_ROLLBACK_DRILL_DEFERRAL_FILE: "artifacts/ddd/rollback/rollback-deferral-all.json",
  DDD_ROLLBACK_DRILL_FILE: "artifacts/ddd/rollback/rollback-drill.json",
  DDD_ROLLBACK_DRILL_STRICT: "true",
  FIELD_SECRET: fieldSecret,
  FILE_SERVICE_BASE_URL: publicBaseUrl,
  JOB_EXECUTOR_BASE_URL: publicBaseUrl,
  JWT_SECRET: jwtSecret,
  LOCALIZATION_SERVICE_BASE_URL: publicBaseUrl,
  LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL: publicBaseUrl,
  LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED: "true",
  LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL: publicBaseUrl,
  LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED: "true",
  LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN: aiToken,
  LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL: publicBaseUrl,
  LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED: "true",
  LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY: aiApiKey,
  LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL: "https://api.openai.com/v1",
  LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL: process.env.LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL || "gpt-4o-mini",
  LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL: process.env.LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL || "text-embedding-3-small",
  LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED: "true",
  LUMIRA_BASE_URL: publicBaseUrl,
  LUMIRA_FILE_OCR_MODE: process.env.LUMIRA_FILE_OCR_MODE || "TESSERACT",
  LUMIRA_FILE_SECURITY_SCAN_MODE: process.env.LUMIRA_FILE_SECURITY_SCAN_MODE || "CLAMAV",
  MESSAGE_SERVICE_BASE_URL: publicBaseUrl,
  MYSQL_DATABASE: source.MYSQL_DATABASE || "saas",
  MYSQL_HOST: process.env.MYSQL_HOST || "mysql",
  MYSQL_PASSWORD: dbPassword,
  MYSQL_PORT: process.env.MYSQL_PORT || "3306",
  MYSQL_USER: source.DB_USERNAME || "root",
  PAYMENT_PUBLIC_BASE_URL: publicBaseUrl,
  PAYMENT_SERVICE_BASE_URL: publicBaseUrl,
  PAYMENT_WEBHOOK_SECRET: paymentWebhookSecret,
  PLAYWRIGHT_BASE_URL: publicBaseUrl,
  PLUGIN_SERVICE_BASE_URL: publicBaseUrl,
  REDIS_HOST: source.REDIS_HOST || "redis",
  REDIS_PASSWORD: secretValue("REDIS_PASSWORD", "redis"),
  REDIS_PORT: "6379",
  SAAS_EVENT_REDIS_STREAM_KEY: source.SAAS_EVENT_REDIS_STREAM_KEY || "lumira:platform-events",
  SAAS_EVENT_OUTBOX_DISPATCHER: source.SAAS_EVENT_OUTBOX_DISPATCHER || "redis-stream",
  SAAS_JOB_BACKEND_BASE_URL: publicBaseUrl,
  SAAS_JOB_FILE_SERVICE_BASE_URL: publicBaseUrl,
  SAAS_JOB_INTERNAL_TOKEN: jobToken,
  SAAS_JOB_MESSAGE_SERVICE_BASE_URL: publicBaseUrl,
  SAAS_JOB_PAYMENT_SERVICE_BASE_URL: publicBaseUrl,
  SAAS_JOB_PLUGIN_SERVICE_BASE_URL: publicBaseUrl,
  SYSTEM_SERVICE_BASE_URL: publicBaseUrl,
  TRUST_FORWARDED_HEADERS: "true",
  UPLOAD_STORAGE_ROOT: source.UPLOAD_STORAGE_ROOT || "/data/uploads",
  XXL_JOB_ACCESS_TOKEN: xxlJobToken,
  XXL_JOB_ADMIN_ADDRESSES: publicBaseUrl,
};

if (fs.existsSync(releaseTemplateFile)) {
  const template = readEnvFile(releaseTemplateFile);
  for (const key of Object.keys(template)) {
    if (!Object.prototype.hasOwnProperty.call(env, key)) {
      env[key] = template[key];
    }
  }
}

const unresolved = Object.entries(env)
  .filter(([, value]) => String(value).includes("__REQUIRED") || /^replace-with-/u.test(String(value)) || /^change-me-/u.test(String(value)))
  .map(([key]) => key)
  .sort();

const body = [
  "# Generated by bin\/ddd-local-release-env-init.mjs for local production-equivalent testing.",
  "# This file is ignored by git. Do not commit real secrets.",
  `# DDD_EVIDENCE_ENVIRONMENT=${evidenceEnvironment}`,
  "",
  ...keyValueLines(env),
  "",
].join("\n");

fs.writeFileSync(outputFile, body, "utf8");

console.log(JSON.stringify({
  status: unresolved.length === 0 ? "READY_FOR_LINT" : "NEEDS_REVIEW",
  outputFile: rel(outputFile),
  keyCount: Object.keys(env).length,
  sourceFiles: {
    deployLocal: fs.existsSync(deployLocalFile) ? rel(deployLocalFile) : null,
    deployExample: fs.existsSync(deployExampleFile) ? rel(deployExampleFile) : null,
  },
  generatedSecretKeys: generatedSecretKeys.sort(),
  unresolvedPlaceholderKeys: unresolved,
}, null, 2));

process.exit(unresolved.length === 0 ? 0 : 1);
