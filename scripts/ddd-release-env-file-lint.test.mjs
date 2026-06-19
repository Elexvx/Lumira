#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-env-lint-"));
const missingReport = path.join(tmpDir, "release-env-missing.json");
const lintReport = path.join(tmpDir, "release-env-lint.json");

fs.writeFileSync(missingReport, `${JSON.stringify({
  status: "NOT_READY",
  uniqueEnvKeyCount: 2,
  groupCount: 1,
  uniqueEnvKeys: ["LUMIRA_BASE_URL", "DB_URL"],
}, null, 2)}\n`);

function runLint(envFile, extraEnv = {}) {
  return spawnSync("node", ["scripts/ddd-release-env-file-lint.mjs", envFile].filter(Boolean), {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_MISSING_ENV_REPORT: missingReport,
      DDD_RELEASE_ENV_LINT_REPORT: lintReport,
      DDD_EVIDENCE_ENVIRONMENT: "lint-test",
      DDD_RELEASE_CANDIDATE: "lint-test-sha",
      DDD_EVIDENCE_OPERATOR: "lint-test-runner",
      ...extraEnv,
    },
  });
}

const missing = runLint(path.join(tmpDir, "missing.env"));
assert.notEqual(missing.status, 0);
let report = JSON.parse(fs.readFileSync(lintReport, "utf8"));
assert.equal(report.status, "FAIL");
assert.equal(report.inputKind, "release-env-file");
assert.equal(report.generatedMissingTemplate, false);
assert.equal(report.envFileSecurity.checked, false);
assert.equal(report.envFileSecurity.reason, "missing-env-file");
assert.equal(report.envFileSecurity.permissionSafe, null);
assert.equal(report.summary.envFileSecurityChecked, false);
assert(report.blockers.some((blocker) => blocker.includes("env file does not exist")));

const processEnvOnly = runLint();
assert.notEqual(processEnvOnly.status, 0);
report = JSON.parse(fs.readFileSync(lintReport, "utf8"));
assert.equal(report.status, "FAIL");
assert.equal(report.inputKind, "process-environment-only");
assert.equal(report.envFile, null);
assert.equal(report.generatedMissingTemplate, false);
assert.equal(report.envFileSecurity.checked, false);
assert.equal(report.envFileSecurity.reason, "process-environment-only");
assert.equal(report.envFileSecurity.permissionSafe, null);
assert.equal(report.summary.envFileSecurityChecked, false);
assert.equal(report.sourceEnvironment, "lint-test");
assert.equal(report.releaseCandidate, "lint-test-sha");
assert.equal(report.evidenceOperator, "lint-test-runner");
assert(!report.blockers.some((blocker) => blocker.includes("DDD_RELEASE_ENV_FILE or positional env file path is required")));

const inferredProvenanceReport = path.join(tmpDir, "release-env-lint-inferred-provenance.json");
const inferredProvenance = spawnSync("node", ["scripts/ddd-release-env-file-lint.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    HOME: process.env.HOME,
    DDD_RELEASE_MISSING_ENV_REPORT: missingReport,
    DDD_RELEASE_ENV_LINT_REPORT: inferredProvenanceReport,
  },
});
assert.notEqual(inferredProvenance.status, 0);
report = JSON.parse(fs.readFileSync(inferredProvenanceReport, "utf8"));
assert.equal(report.inputKind, "process-environment-only");
assert.equal(report.sourceEnvironment, "local-dev");
assert.match(report.releaseCandidate, /^([0-9a-f]{12}|local-worktree)$/);
assert.equal(typeof report.evidenceOperator, "string");
assert(report.evidenceOperator.length > 0);

const generatedTemplateEnv = path.join(tmpDir, "release-env-missing.template.env");
fs.writeFileSync(generatedTemplateEnv, "LUMIRA_BASE_URL=__REQUIRED__\nDB_URL=__REQUIRED__\n");
fs.chmodSync(generatedTemplateEnv, 0o644);
const generatedTemplate = runLint(generatedTemplateEnv);
assert.notEqual(generatedTemplate.status, 0);
report = JSON.parse(fs.readFileSync(lintReport, "utf8"));
assert.equal(report.status, "FAIL");
assert.equal(report.inputKind, "generated-missing-template");
assert.equal(report.generatedMissingTemplate, true);
assert.equal(report.envFile, generatedTemplateEnv);
assert.equal(report.envFileSecurity.checked, true);
if (process.platform !== "win32") {
  assert.equal(report.envFileSecurity.modeOctal, "644");
}
assert.equal(report.envFileSecurity.permissionSafe, null);
assert.equal(report.envFileSecurity.permissionCheckSkipped, true);
assert.equal(report.envFileSecurity.generatedMissingTemplate, true);
assert.equal(report.summary.envFileSecurityChecked, true);
assert.equal(report.summary.envFilePermissionSafe, null);
assert.equal(report.summary.envFilePermissionCheckSkipped, true);
assert(report.unresolvedTemplateKeys.includes("LUMIRA_BASE_URL"));
assert.deepEqual(report.canonicalKeys, ["DB_URL", "LUMIRA_BASE_URL"]);
assert.deepEqual(report.canonicalMissingEnvKeys, ["DB_URL", "LUMIRA_BASE_URL"]);
assert(report.summary.releaseConfigBlockersFromPlaceholders > 0);
assert(report.summary.releaseConfigBlockersAfterPlaceholders < report.summary.releaseConfigBlockers);
assert(report.summary.primaryBlockers < report.summary.blockers);
assert(report.releaseConfigBlockerDetails.some((detail) => detail.blockedByPlaceholderKey === true));
assert.equal(report.primaryBlockers.length, report.summary.primaryBlockers);
assert.equal(report.placeholderDerivedConfigBlockers.length, report.summary.releaseConfigBlockersFromPlaceholders);
assert.match(generatedTemplate.stderr, /release config blocker\(s\) are derived from unresolved placeholder values/);
assert.match(generatedTemplate.stderr, /Full derived list is in /);
assert(report.blockers.some((blocker) => blocker === "runtime.backend base url: must use HTTPS for production-equivalent evidence"));
assert.equal(report.blockers.some((blocker) => blocker.includes("env file permissions are too broad")), false);

const placeholderEnv = path.join(tmpDir, "placeholder.env");
fs.writeFileSync(placeholderEnv, "LUMIRA_BASE_URL=__REQUIRED__\nDB_URL=jdbc:mysql://db.example.internal:3306/lumira\n");
fs.chmodSync(placeholderEnv, 0o600);
const placeholder = runLint(placeholderEnv);
assert.notEqual(placeholder.status, 0);
report = JSON.parse(fs.readFileSync(lintReport, "utf8"));
assert.equal(report.status, "FAIL");
assert.equal(report.inputKind, "release-env-file");
assert.equal(report.generatedMissingTemplate, false);
assert(report.unresolvedTemplateKeys.includes("LUMIRA_BASE_URL"));
assert(report.blockers.some((blocker) => blocker.includes("__REQUIRED__ placeholder must be replaced")));

const examplePlaceholderEnv = path.join(tmpDir, "example-placeholder.env");
fs.writeFileSync(examplePlaceholderEnv, [
  "LUMIRA_BASE_URL=https://api.example.com",
  "DB_PASSWORD=replace-with-secret-at-least-16-chars",
  "DB_URL=jdbc:mysql://db.staging.lumira.app:3306/lumira",
  "",
].join("\n"));
fs.chmodSync(examplePlaceholderEnv, 0o600);
const examplePlaceholder = runLint(examplePlaceholderEnv);
assert.notEqual(examplePlaceholder.status, 0);
report = JSON.parse(fs.readFileSync(lintReport, "utf8"));
assert.equal(report.status, "FAIL");
assert(report.unresolvedTemplateKeys.includes("LUMIRA_BASE_URL"));
assert(report.unresolvedTemplateKeys.includes("DB_PASSWORD"));
assert(report.blockers.some((blocker) => blocker === "LUMIRA_BASE_URL: placeholder value must be replaced"));
assert(report.blockers.some((blocker) => blocker === "DB_PASSWORD: placeholder value must be replaced"));

const broadModeEnv = path.join(tmpDir, "broad-mode.env");
fs.writeFileSync(broadModeEnv, [
  "LUMIRA_BASE_URL=https://api.staging.lumira.app",
  "DB_URL=jdbc:mysql://mysql.staging.lumira.app:3306/lumira",
  "",
].join("\n"));
fs.chmodSync(broadModeEnv, 0o644);
const broadMode = runLint(broadModeEnv);
if (process.platform === "win32") {
  assert.notEqual(broadMode.status, 0);
} else {
  assert.notEqual(broadMode.status, 0);
}
report = JSON.parse(fs.readFileSync(lintReport, "utf8"));
assert.equal(report.status, "FAIL");
assert.equal(report.envFileSecurity.checked, true);
if (process.platform !== "win32") {
  assert.equal(report.envFileSecurity.modeOctal, "644");
}
assert.equal(report.envFileSecurity.permissionSafe, process.platform === "win32");
assert.equal(report.envFileSecurity.permissionCheckSkipped, process.platform === "win32");
assert.equal(report.summary.envFileSecurityChecked, true);
assert.equal(report.summary.envFilePermissionSafe, process.platform === "win32");
assert.equal(report.summary.envFilePermissionCheckSkipped, process.platform === "win32");
assert.equal(report.blockers.some((blocker) => blocker.includes("env file permissions are too broad")), process.platform !== "win32");
assert.equal(report.blockers.some((blocker) => blocker.includes("use chmod 600")), process.platform !== "win32");

const unsafeSyntaxEnv = path.join(tmpDir, "unsafe-syntax.env");
fs.writeFileSync(unsafeSyntaxEnv, [
  "LUMIRA_BASE_URL=https://api.staging.lumira.app",
  "echo SHOULD_NOT_RUN >&2",
  "",
].join("\n"));
fs.chmodSync(unsafeSyntaxEnv, 0o600);
const unsafeSyntax = runLint(unsafeSyntaxEnv);
assert.notEqual(unsafeSyntax.status, 0);
report = JSON.parse(fs.readFileSync(lintReport, "utf8"));
assert.equal(report.status, "FAIL");
assert(report.blockers.some((blocker) => blocker === "line 2 must be KEY=value, export KEY=value, or a comment"));

const validEnv = path.join(tmpDir, "valid.env");
fs.writeFileSync(validEnv, [
  "LUMIRA_BASE_URL=https://api.staging.lumira.app",
  "PLAYWRIGHT_BASE_URL=https://app.staging.lumira.app",
  "DB_URL=jdbc:mysql://mysql.staging.lumira.app:3306/lumira",
  "export DB_USERNAME=lumira_app",
  "export DB_PASSWORD=valid-database-password-123",
  "REDIS_HOST=redis.staging.lumira.app",
  "REDIS_PORT=6379",
  "REDIS_PASSWORD=valid-redis-password-1234",
  "JWT_SECRET=valid-jwt-secret-value-at-least-32-chars",
  "FIELD_SECRET=valid-field-secret-value-at-least-32-chars",
  "CORS_ALLOWED_ORIGIN_PATTERNS=https://app.staging.lumira.app",
  "TRUST_FORWARDED_HEADERS=true",
  "SYSTEM_SERVICE_BASE_URL=https://system.staging.lumira.app",
  "AUTH_SERVICE_BASE_URL=https://auth.staging.lumira.app",
  "FILE_SERVICE_BASE_URL=https://file.staging.lumira.app",
  "MESSAGE_SERVICE_BASE_URL=https://message.staging.lumira.app",
  "PLUGIN_SERVICE_BASE_URL=https://plugin.staging.lumira.app",
  "LOCALIZATION_SERVICE_BASE_URL=https://localization.staging.lumira.app",
  "PAYMENT_SERVICE_BASE_URL=https://payment.staging.lumira.app",
  "AI_SERVICE_BASE_URL=https://ai.staging.lumira.app",
  "JOB_EXECUTOR_BASE_URL=https://job.staging.lumira.app",
  "SAAS_JOB_INTERNAL_TOKEN=valid-job-internal-token-at-least-32",
  "SAAS_JOB_BACKEND_BASE_URL=https://api.staging.lumira.app",
  "SAAS_JOB_MESSAGE_SERVICE_BASE_URL=https://message.staging.lumira.app",
  "SAAS_JOB_FILE_SERVICE_BASE_URL=https://file.staging.lumira.app",
  "SAAS_JOB_PAYMENT_SERVICE_BASE_URL=https://payment.staging.lumira.app",
  "SAAS_JOB_PLUGIN_SERVICE_BASE_URL=https://plugin.staging.lumira.app",
  "XXL_JOB_ADMIN_ADDRESSES=https://xxl-job.staging.lumira.app",
  "XXL_JOB_ACCESS_TOKEN=valid-xxl-job-access-token-at-least-32",
  "SAAS_EVENT_OUTBOX_DISPATCHER=redis-stream",
  "SAAS_EVENT_REDIS_STREAM_KEY=lumira:platform-events",
  "UPLOAD_STORAGE_ROOT=/opt/lumira/data/uploads",
  "LUMIRA_FILE_SECURITY_SCAN_MODE=CLAMAV",
  "LUMIRA_FILE_OCR_MODE=TESSERACT",
  "PAYMENT_PUBLIC_BASE_URL=https://api.staging.lumira.app",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED=true",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL=https://ai-provider.staging.lumira.app/v1",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=valid-ai-provider-api-key-at-least-32",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL=gpt-4o-mini",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL=text-embedding-3-small",
  "LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN=valid-ai-owner-token-at-least-32",
  "LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED=true",
  "LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL=https://system.staging.lumira.app",
  "LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED=true",
  "LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL=https://system.staging.lumira.app",
  "LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED=true",
  "LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL=https://file.staging.lumira.app",
  "",
].join("\n"));
fs.chmodSync(validEnv, 0o600);
const valid = runLint(validEnv);
assert.equal(valid.status, 0, valid.stderr || valid.stdout);
report = JSON.parse(fs.readFileSync(lintReport, "utf8"));
assert.equal(report.status, "PASS");
assert.equal(report.inputKind, "release-env-file");
assert.equal(report.generatedMissingTemplate, false);
assert.equal(report.sourceEnvironment, "lint-test");
assert.equal(report.releaseCandidate, "lint-test-sha");
assert.equal(report.evidenceOperator, "lint-test-runner");
assert.equal(report.envFileSecurity.checked, true);
if (process.platform !== "win32") {
  assert.equal(report.envFileSecurity.modeOctal, "600");
}
assert.equal(report.envFileSecurity.permissionSafe, true);
assert.equal(report.envFileSecurity.permissionCheckSkipped, process.platform === "win32");
assert.equal(report.envFileSecurity.requiredMode, "600");
assert.equal(report.summary.envFileSecurityChecked, true);
assert.equal(report.summary.envFilePermissionSafe, true);
if (process.platform !== "win32") {
  assert.equal(report.summary.envFileModeOctal, "600");
}
assert.equal(report.summary.unresolvedTemplateKeys, 0);
assert.equal(report.summary.releaseConfigBlockers, 0);
assert.equal(report.summary.releaseConfigBlockersFromPlaceholders, 0);
assert.equal(report.summary.releaseConfigBlockersAfterPlaceholders, 0);
assert.equal(report.summary.primaryBlockers, 0);
assert.equal(report.missingEnv.uniqueEnvKeyCount, 2);

const aliasPrunedEnv = path.join(tmpDir, "alias-pruned.env");
fs.writeFileSync(aliasPrunedEnv, [
  "LUMIRA_BASE_URL=https://api.staging.lumira.app",
  "FRONTEND_BASE_URL=https://app.staging.lumira.app",
  "DB_URL=jdbc:mysql://mysql.staging.lumira.app:3306/lumira",
  "MYSQL_USER=lumira_app",
  "MYSQL_PASSWORD=valid-database-password-123",
  "REDIS_HOST=redis.staging.lumira.app",
  "REDIS_PORT=6379",
  "JWT_SECRET=valid-jwt-secret-value-at-least-32-chars",
  "FIELD_SECRET=valid-field-secret-value-at-least-32-chars",
  "CORS_ALLOWED_ORIGIN_PATTERNS=https://app.staging.lumira.app",
  "TRUST_FORWARDED_HEADERS=true",
  "LUMIRA_SYSTEM_SERVICE_BASE_URL=https://system.staging.lumira.app",
  "LUMIRA_AUTH_SERVICE_BASE_URL=https://auth.staging.lumira.app",
  "LUMIRA_FILE_SERVICE_BASE_URL=https://file.staging.lumira.app",
  "LUMIRA_MESSAGE_SERVICE_BASE_URL=https://message.staging.lumira.app",
  "LUMIRA_PLUGIN_SERVICE_BASE_URL=https://plugin.staging.lumira.app",
  "LUMIRA_LOCALIZATION_SERVICE_BASE_URL=https://localization.staging.lumira.app",
  "LUMIRA_PAYMENT_SERVICE_BASE_URL=https://payment.staging.lumira.app",
  "LUMIRA_AI_BASE_URL=https://ai.staging.lumira.app",
  "LUMIRA_JOB_EXECUTOR_BASE_URL=https://job.staging.lumira.app",
  "DDD_JOB_INTERNAL_TOKEN=valid-job-internal-token-at-least-32",
  "LUMIRA_JOB_BACKEND_BASE_URL=https://api.staging.lumira.app",
  "LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL=https://message.staging.lumira.app",
  "LUMIRA_JOB_FILE_SERVICE_BASE_URL=https://file.staging.lumira.app",
  "LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL=https://payment.staging.lumira.app",
  "LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL=https://plugin.staging.lumira.app",
  "LUMIRA_XXL_JOB_ADMIN_ADDRESSES=https://xxl-job.staging.lumira.app",
  "LUMIRA_XXL_JOB_ACCESS_TOKEN=valid-xxl-job-access-token-at-least-32",
  "LUMIRA_EVENT_OUTBOX_DISPATCHER=redis-stream",
  "LUMIRA_EVENT_REDIS_STREAM_KEY=lumira:platform-events",
  "LUMIRA_UPLOAD_STORAGE_ROOT=/opt/lumira/data/uploads",
  "LUMIRA_FILE_SECURITY_SCAN_MODE=CLAMAV",
  "LUMIRA_FILE_OCR_MODE=TESSERACT",
  "PAYMENT_PUBLIC_BASE_URL=https://api.staging.lumira.app",
  "LUMIRA_AI_PROVIDER_ENABLED=true",
  "LUMIRA_AI_PROVIDER_BASE_URL=https://ai-provider.staging.lumira.app/v1",
  "LUMIRA_AI_PROVIDER_API_KEY=valid-ai-provider-api-key-at-least-32",
  "LUMIRA_AI_CHAT_MODEL=gpt-4o-mini",
  "LUMIRA_AI_EMBEDDING_MODEL=text-embedding-3-small",
  "LUMIRA_AI_OWNER_INTERNAL_TOKEN=valid-ai-owner-token-at-least-32",
  "LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED=true",
  "LUMIRA_AI_OWNER_IAM_BASE_URL=https://system.staging.lumira.app",
  "LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED=true",
  "LUMIRA_AI_OWNER_PLATFORM_BASE_URL=https://system.staging.lumira.app",
  "LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED=true",
  "LUMIRA_AI_OWNER_FILE_BASE_URL=https://file.staging.lumira.app",
  "",
].join("\n"));
fs.chmodSync(aliasPrunedEnv, 0o600);
const aliasPruned = runLint(aliasPrunedEnv);
assert.equal(aliasPruned.status, 0, aliasPruned.stderr || aliasPruned.stdout);
report = JSON.parse(fs.readFileSync(lintReport, "utf8"));
assert.equal(report.status, "PASS");
assert.equal(report.summary.releaseConfigBlockers, 0);
assert.equal(report.keys.includes("DEPLOY_CHECK_BASE_URL"), false);
assert.equal(report.keys.includes("SPRING_DATASOURCE_USERNAME"), false);
assert(report.canonicalKeys.includes("DB_USERNAME"));
assert.equal(report.canonicalKeys.includes("MYSQL_USER"), false);
assert(report.canonicalKeys.includes("LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"));
assert.equal(report.canonicalKeys.includes("LUMIRA_AI_PROVIDER_API_KEY"), false);

console.log("[ddd-release-env-file-lint.test] ok");
