#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  evaluateReleaseConfig,
  validateReleaseConfigArtifact,
} from "./ddd-release-config-contract.mjs";

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

function artifactFromEnv(env = validEnv(), overrides = {}) {
  const result = evaluateReleaseConfig(env, { envFile: ".env.release" });
  const groups = result.groups;
  const coverageMatrix = groups.flatMap((group) => group.checks.map((check) => ({
    group: group.name,
    owner: group.owner,
    check: check.name,
    required: check.required !== false,
    keys: check.keys || [],
    runtimePresent: check.present === true,
    matchedKey: check.matchedKey || null,
    envFileCovered: true,
    templateCovered: true,
    workflowCovered: true,
  })));
  const requiredCoverage = coverageMatrix.filter((entry) => entry.required);
  const blockerDetails = result.blockers.map((blocker) => ({
    blocker,
    group: blocker.split(".")[0],
    owner: "release-infra",
    check: "unknown",
    reason: blocker,
    envKeys: [],
    matchedKey: null,
    blockedByPlaceholderKey: false,
    required: true,
  }));
  return {
    status: result.blockers.length === 0 ? "PASS" : "FAIL",
    inputKind: "release-env-file",
    generatedMissingTemplate: false,
    envFile: ".env.release",
    envFileExists: true,
    summary: {
      groups: groups.length,
      checks: groups.reduce((sum, group) => sum + group.checks.length, 0),
      requiredChecks: requiredCoverage.length,
      runtimePresentRequiredChecks: requiredCoverage.filter((entry) => entry.runtimePresent).length,
      envFileCoveredRequiredChecks: requiredCoverage.filter((entry) => entry.envFileCovered).length,
      templateCoveredRequiredChecks: requiredCoverage.filter((entry) => entry.templateCovered).length,
      workflowCoveredRequiredChecks: requiredCoverage.filter((entry) => entry.workflowCovered).length,
      blockers: result.blockers.length,
      primaryBlockers: result.blockers.length,
      releaseConfigBlockersFromPlaceholders: 0,
      releaseConfigBlockersAfterPlaceholders: result.blockers.length,
      warnings: result.warnings.length,
    },
    groups,
    coverageMatrix,
    blockers: result.blockers,
    primaryBlockers: result.blockers,
    placeholderDerivedConfigBlockers: [],
    blockerDetails,
    blockersByGroup: {},
    blockersByOwner: {},
    warnings: result.warnings,
    ...overrides,
  };
}

assert.deepEqual(evaluateReleaseConfig(validEnv(), { envFile: ".env.release" }).blockers, []);
assert.equal(evaluateReleaseConfig(validEnv(), { envFile: ".env.release" }).groups[0].owner, "release-infra");
assert.deepEqual(validateReleaseConfigArtifact(artifactFromEnv()), []);

{
  const artifact = artifactFromEnv(undefined, {
    inputKind: "generated-missing-template",
    generatedMissingTemplate: true,
    envFile: "/tmp/release-env-missing.template.env",
    envFileExists: true,
  });
  assert.deepEqual(validateReleaseConfigArtifact(artifact), []);
}

{
  const artifact = artifactFromEnv(undefined, {
    inputKind: "generated-missing-template",
    generatedMissingTemplate: false,
  });
  const issues = validateReleaseConfigArtifact(artifact);
  assert(issues.includes("release config generatedMissingTemplate must match inputKind=generated-missing-template"));
}

{
  const artifact = artifactFromEnv(undefined, {
    inputKind: "release-env-file",
    generatedMissingTemplate: true,
  });
  const issues = validateReleaseConfigArtifact(artifact);
  assert(issues.includes("release config generatedMissingTemplate must match inputKind=generated-missing-template"));
}

{
  const artifact = artifactFromEnv(undefined, {
    inputKind: "generated-missing-template",
    generatedMissingTemplate: true,
    envFileExists: false,
  });
  const issues = validateReleaseConfigArtifact(artifact);
  assert(issues.includes("release config generated missing template input requires an existing envFile"));
}

{
  const artifact = artifactFromEnv(undefined, {
    inputKind: "unknown-input",
  });
  const issues = validateReleaseConfigArtifact(artifact);
  assert(issues.some((issue) => issue.startsWith("release config inputKind must be one of ")));
}

{
  const result = evaluateReleaseConfig({ ...validEnv(), LUMIRA_BASE_URL: "http://localhost:8080" }, { envFile: ".env.release" });
  assert.ok(result.blockers.includes("runtime.backend base url: must not point at localhost for production-equivalent evidence"));
  assert.ok(result.blockers.includes("runtime.backend base url: must use HTTPS for production-equivalent evidence"));
}

{
  const result = evaluateReleaseConfig({ ...validEnv(), LUMIRA_SYSTEM_SERVICE_BASE_URL: "http://system.staging.lumira.app" }, { envFile: ".env.release" });
  assert.deepEqual(result.blockers, [
    "owner-services.system service: must use HTTPS for production-equivalent evidence",
  ]);
}

{
  const result = evaluateReleaseConfig({ ...validEnv(), JWT_SECRET: "short" }, { envFile: ".env.release" });
  assert.deepEqual(result.blockers, [
    "runtime.jwt secret: must be at least 32 characters",
  ]);
}

{
  const result = evaluateReleaseConfig({ ...validEnv(), REDIS_PORT: "not-a-port" }, { envFile: ".env.release" });
  assert.deepEqual(result.blockers, [
    "runtime.redis port: must be a numeric TCP port",
  ]);
}

{
  const result = evaluateReleaseConfig({ ...validEnv(), ALLOW_UNSAFE_DEFAULT_ADMIN_LOGIN: "true" }, { envFile: ".env.release" });
  assert.deepEqual(result.blockers, [
    "runtime.unsafe default admin login must be disabled for release evidence",
  ]);
}

{
  const artifact = artifactFromEnv();
  artifact.coverageMatrix = artifact.coverageMatrix.filter((entry) => !(entry.group === "runtime" && entry.check === "backend base url"));
  const issues = validateReleaseConfigArtifact(artifact);
  assert(issues.includes("release config summary requiredChecks mismatch: declared=46, actual=45"));
  assert(issues.includes("release config coverageMatrix missing runtime.backend base url"));
}

{
  const artifact = artifactFromEnv();
  artifact.coverageMatrix.push({ ...artifact.coverageMatrix[0] });
  const issues = validateReleaseConfigArtifact(artifact);
  assert(issues.includes("release config coverageMatrix duplicate runtime.backend base url"));
}

{
  const artifact = artifactFromEnv();
  artifact.coverageMatrix.push({
    group: "runtime",
    owner: "release-infra",
    check: "unknown check",
    required: true,
    keys: ["UNKNOWN_CHECK"],
    runtimePresent: false,
    envFileCovered: false,
    templateCovered: false,
    workflowCovered: false,
  });
  const issues = validateReleaseConfigArtifact(artifact);
  assert(issues.includes("release config coverageMatrix unknown runtime.unknown check"));
}

{
  const artifact = artifactFromEnv();
  artifact.coverageMatrix[0] = { ...artifact.coverageMatrix[0], keys: [] };
  const issues = validateReleaseConfigArtifact(artifact);
  assert(issues.includes("release config coverageMatrix runtime.backend base url keys must be non-empty"));
}

{
  const artifact = artifactFromEnv();
  artifact.status = "FAIL";
  artifact.summary.blockers = 1;
  artifact.blockers = ["runtime.jwt secret: must be at least 32 characters"];
  artifact.blockerDetails = [{
    blocker: "runtime.field secret: must be at least 32 characters",
    group: "",
    owner: "release-infra",
    check: "jwt secret",
    reason: "",
    envKeys: "JWT_SECRET",
    matchedKey: "JWT_SECRET",
    required: "true",
  }];
  artifact.blockersByGroup = { unknown: 1 };
  artifact.blockersByOwner = { "release-infra": 1 };
  const issues = validateReleaseConfigArtifact(artifact);
  assert(issues.includes("release config blockerDetails[0].blocker mismatch: declared=runtime.field secret: must be at least 32 characters, actual=runtime.jwt secret: must be at least 32 characters"));
  assert(issues.includes("release config blockerDetails[0].group must be a non-empty string"));
  assert(issues.includes("release config blockerDetails[0].reason must be a non-empty string"));
  assert(issues.includes("release config blockerDetails[0].envKeys must be an array"));
  assert(issues.includes("release config blockerDetails[0].required must be boolean or null"));
}

{
  const artifact = artifactFromEnv();
  artifact.status = "FAIL";
  artifact.summary.blockers = 1;
  artifact.blockers = ["runtime.jwt secret: must be at least 32 characters"];
  artifact.blockerDetails = [{
    blocker: "runtime.jwt secret: must be at least 32 characters",
    group: "runtime",
    owner: "wrong-owner",
    check: "jwt secret",
    reason: "must be at least 32 characters",
    envKeys: ["WRONG_SECRET"],
    matchedKey: "JWT_SECRET",
    required: false,
  }];
  artifact.blockersByGroup = { runtime: 1 };
  artifact.blockersByOwner = { "wrong-owner": 1 };
  const issues = validateReleaseConfigArtifact(artifact);
  assert(issues.includes("release config blockerDetails[0].owner mismatch for runtime.jwt secret: declared=wrong-owner, actual=release-infra"));
  assert(issues.includes("release config blockerDetails[0].required mismatch for runtime.jwt secret: declared=false, actual=true"));
  assert(issues.includes("release config blockerDetails[0].envKeys mismatch for runtime.jwt secret"));
}

console.log("[ddd-release-config-contract.test] ok");
