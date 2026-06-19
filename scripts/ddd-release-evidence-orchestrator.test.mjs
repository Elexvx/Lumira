#!/usr/bin/env node

import assert from "node:assert/strict";
import { chmodSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const repoRoot = fileURLToPath(new URL("..", import.meta.url));
const outputDir = mkdtempSync(join(tmpdir(), "lumira-orchestrator-contract-"));
const fakeBinDir = mkdtempSync(join(tmpdir(), "lumira-orchestrator-docker-"));
const fakeDockerScript = join(fakeBinDir, "docker-ok.sh");
const fakeDockerNoDaemonScript = join(fakeBinDir, "docker-no-daemon.sh");
const fakeDocker = process.platform === "win32" ? join(fakeBinDir, "docker-ok.cmd") : fakeDockerScript;
const fakeDockerNoDaemon = process.platform === "win32" ? join(fakeBinDir, "docker-no-daemon.cmd") : fakeDockerNoDaemonScript;
writeFileSync(fakeDockerScript, `#!/bin/sh
if [ "$1" = "--version" ]; then
  echo "Docker version test"
  exit 0
fi
if [ "$1" = "info" ]; then
  echo '"29.4.1"'
  exit 0
fi
exit 0
`);
writeFileSync(fakeDockerNoDaemonScript, `#!/bin/sh
if [ "$1" = "--version" ]; then
  echo "Docker version test"
  exit 0
fi
if [ "$1" = "info" ]; then
  echo "Cannot connect to the Docker daemon" >&2
  exit 1
fi
exit 0
`);
if (process.platform === "win32") {
  writeFileSync(fakeDocker, `@echo off\r\nif "%1"=="--version" (echo Docker version test& exit /b 0)\r\nif "%1"=="info" (echo "29.4.1"& exit /b 0)\r\nexit /b 0\r\n`);
  writeFileSync(fakeDockerNoDaemon, `@echo off\r\nif "%1"=="--version" (echo Docker version test& exit /b 0)\r\nif "%1"=="info" (echo Cannot connect to the Docker daemon 1>&2& exit /b 1)\r\nexit /b 0\r\n`);
}
chmodSync(fakeDockerScript, 0o755);
chmodSync(fakeDockerNoDaemonScript, 0o755);
chmodSync(fakeDocker, 0o755);
chmodSync(fakeDockerNoDaemon, 0o755);

const result = spawnSync("node", ["scripts/ddd-release-evidence-orchestrator.mjs", "--strict"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ORCHESTRATOR_DIR: outputDir,
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "orchestrator-contract-test",
    DDD_RELEASE_CANDIDATE: "orchestrator-contract-sha",
    DDD_EVIDENCE_OPERATOR: "orchestrator-contract-runner",
    DDD_DOCKER_COMMAND: fakeDocker,
    DDD_RELEASE_ENV_FILE: "",
    LUMIRA_BASE_URL: "",
    DEPLOY_CHECK_BASE_URL: "",
    BASE_URL: "",
    PLAYWRIGHT_BASE_URL: "",
    FRONTEND_BASE_URL: "",
    DDD_FRONTEND_EXPECT_DEPLOYED: "",
    DDD_AI_EXPECT_PROVIDER_REMOTE: "",
    DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "",
    DDD_MIGRATION_FRESH_DB_VALIDATED: "",
    DDD_MIGRATION_UPGRADE_DB_VALIDATED: "",
    DDD_MIGRATION_FRESH_DB_EVIDENCE: "",
    DDD_MIGRATION_UPGRADE_DB_EVIDENCE: "",
  },
});

assert.equal(result.status, 0, result.stderr || result.stdout);

const report = JSON.parse(readFileSync(join(outputDir, "orchestrator-report.json"), "utf8"));
const stepIds = report.selectedSteps.map((step) => step.id);

assert.equal(report.mode, "plan");
assert.equal(report.strict, true);
assert.equal(report.sourceEnvironment, "orchestrator-contract-test");
assert.equal(report.releaseCandidate, "orchestrator-contract-sha");
assert.equal(report.evidenceOperator, "orchestrator-contract-runner");
assert.equal(report.summary.steps, 26);
assert.equal(report.summary.enabled, 25);
assert.equal(report.summary.disabled, 1);
assert.deepEqual(report.results, []);
assert.equal(report.preflight.status, "FAIL");
assert.ok(report.preflight.blockers >= 1);
assert.ok(report.preflight.checks.some((check) => check.id === "release-config-env-file" && check.status === "BLOCKER"));
assert.ok(report.preflight.checks.some((check) => check.id === "backend-runtime-base-url" && check.status === "BLOCKER"));
assert.ok(report.preflight.checks.some((check) => check.id === "ai-runtime-base-url" && check.status === "BLOCKER"));
assert.ok(report.preflight.checks.some((check) => check.id === "frontend-runtime-base-url" && check.status === "BLOCKER"));
assert.ok(report.preflight.checks.some((check) => check.id === "docker-cli" && check.status === "PASS"));
assert.ok(report.preflight.checks.some((check) => check.id === "docker-daemon" && check.status === "PASS"));
assert.equal(stepIds[0], "release-env-file-lint");
assert.equal(stepIds[1], "release-config-evidence");
assert.ok(stepIds.includes("authenticated-performance-baseline"));
assert.ok(stepIds.includes("outbox-replay-dead-letter"));
assert.ok(stepIds.indexOf("authenticated-performance-baseline") > stepIds.indexOf("authenticated-performance"));
assert.ok(stepIds.indexOf("authenticated-performance-baseline") < stepIds.indexOf("file-processing"));
assert.ok(stepIds.indexOf("outbox-replay-dead-letter") > stepIds.indexOf("payment-webhook"));
assert.ok(stepIds.indexOf("outbox-replay-dead-letter") < stepIds.indexOf("job-e2e"));
assert.ok(stepIds.indexOf("manifest-provenance-preflight") > stepIds.indexOf("physical-split"));
assert.ok(stepIds.indexOf("manifest-provenance-preflight") < stepIds.indexOf("manifest"));

const outbox = report.selectedSteps.find((step) => step.id === "outbox-replay-dead-letter");
assert.equal(outbox.command, "node scripts/ddd-outbox-replay-dead-letter-smoke.mjs");
assert.deepEqual(outbox.envKeys, ["DDD_OUTBOX_SMOKE_STRICT", "DDD_RELEASE_EVIDENCE_STRICT"]);

const envLint = report.selectedSteps.find((step) => step.id === "release-env-file-lint");
assert.equal(envLint.command, "node scripts/ddd-release-env-file-lint.mjs");
assert.deepEqual(envLint.envKeys, ["DDD_RELEASE_EVIDENCE_STRICT"]);

const baseline = report.selectedSteps.find((step) => step.id === "authenticated-performance-baseline");
assert.equal(baseline.command, "node scripts/ddd-promote-performance-baseline.mjs");
assert.equal(baseline.optional, true);
assert.equal(baseline.enabled, false);
assert.deepEqual(baseline.envKeys, [
  "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY",
  "DDD_AUTH_PERF_BASELINE_ENVIRONMENT",
  "DDD_AUTH_PERF_BASELINE_PROMOTE",
  "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT",
  "DDD_RELEASE_EVIDENCE_STRICT",
]);

const manifestPreflight = report.selectedSteps.find((step) => step.id === "manifest-provenance-preflight");
assert.equal(manifestPreflight.command, "node scripts/ddd-release-evidence-manifest.mjs");
assert.equal(manifestPreflight.always, true);
assert.deepEqual(manifestPreflight.envKeys, [
  "DDD_RELEASE_EVIDENCE_STRICT",
  "DDD_RELEASE_MANIFEST_CHECK_ENV",
]);

const physicalSplit = report.selectedSteps.find((step) => step.id === "physical-split");
assert.equal(physicalSplit.command, "node scripts/ddd-physical-split-gate.mjs");
assert.deepEqual(physicalSplit.envKeys, [
  "DDD_RELEASE_EVIDENCE_STRICT",
  "DDD_SPLIT_STRICT",
]);

for (const step of report.selectedSteps) {
  assert.equal(step.strictEnv, true, `${step.id} should record strictEnv=true`);
  assert.ok(step.envKeys.includes("DDD_RELEASE_EVIDENCE_STRICT"), `${step.id} should receive strict evidence env`);
}

const enabledOutputDir = mkdtempSync(join(tmpdir(), "lumira-orchestrator-baseline-enabled-"));
const enabledResult = spawnSync("node", ["scripts/ddd-release-evidence-orchestrator.mjs", "--strict"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ORCHESTRATOR_DIR: enabledOutputDir,
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "orchestrator-contract-test",
    DDD_RELEASE_CANDIDATE: "orchestrator-contract-sha",
    DDD_EVIDENCE_OPERATOR: "orchestrator-contract-runner",
    DDD_DOCKER_COMMAND: fakeDocker,
    DDD_AUTH_PERF_BASELINE_PROMOTE: "true",
    DDD_RELEASE_ENV_FILE: "",
    LUMIRA_BASE_URL: "",
    DEPLOY_CHECK_BASE_URL: "",
    BASE_URL: "",
    PLAYWRIGHT_BASE_URL: "",
    FRONTEND_BASE_URL: "",
    DDD_FRONTEND_EXPECT_DEPLOYED: "",
    DDD_AI_EXPECT_PROVIDER_REMOTE: "",
    DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "",
    DDD_MIGRATION_FRESH_DB_VALIDATED: "",
    DDD_MIGRATION_UPGRADE_DB_VALIDATED: "",
    DDD_MIGRATION_FRESH_DB_EVIDENCE: "",
    DDD_MIGRATION_UPGRADE_DB_EVIDENCE: "",
  },
});

assert.equal(enabledResult.status, 0, enabledResult.stderr || enabledResult.stdout);
const enabledReport = JSON.parse(readFileSync(join(enabledOutputDir, "orchestrator-report.json"), "utf8"));
assert.equal(enabledReport.preflight.status, "FAIL");
assert.equal(enabledReport.summary.enabled, 26);
assert.equal(enabledReport.summary.disabled, 0);
const enabledBaseline = enabledReport.selectedSteps.find((step) => step.id === "authenticated-performance-baseline");
assert.equal(enabledBaseline.optional, true);
assert.equal(enabledBaseline.enabled, true);
assert.deepEqual(enabledBaseline.envKeys, [
  "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY",
  "DDD_AUTH_PERF_BASELINE_ENVIRONMENT",
  "DDD_AUTH_PERF_BASELINE_PROMOTE",
  "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT",
  "DDD_RELEASE_EVIDENCE_STRICT",
]);

const nonHttpsOutputDir = mkdtempSync(join(tmpdir(), "lumira-orchestrator-non-https-"));
const nonHttpsResult = spawnSync("node", ["scripts/ddd-release-evidence-orchestrator.mjs", "--strict"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ORCHESTRATOR_DIR: nonHttpsOutputDir,
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "orchestrator-contract-test",
    DDD_RELEASE_CANDIDATE: "orchestrator-contract-sha",
    DDD_EVIDENCE_OPERATOR: "orchestrator-contract-runner",
    DDD_DOCKER_COMMAND: fakeDocker,
    DDD_RELEASE_ENV_FILE: "",
    LUMIRA_BASE_URL: "http://staging.lumira.internal",
    LUMIRA_AI_BASE_URL: "http://ai.staging.lumira.internal",
    PLAYWRIGHT_BASE_URL: "https://app.staging.lumira.internal",
    DDD_FRONTEND_EXPECT_DEPLOYED: "true",
    DDD_AI_EXPECT_PROVIDER_REMOTE: "true",
    DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "true",
    DDD_MIGRATION_FRESH_DB_VALIDATED: "",
    DDD_MIGRATION_UPGRADE_DB_VALIDATED: "",
    DDD_MIGRATION_FRESH_DB_EVIDENCE: "",
    DDD_MIGRATION_UPGRADE_DB_EVIDENCE: "",
  },
});

assert.equal(nonHttpsResult.status, 0, nonHttpsResult.stderr || nonHttpsResult.stdout);
const nonHttpsReport = JSON.parse(readFileSync(join(nonHttpsOutputDir, "orchestrator-report.json"), "utf8"));
assert.ok(nonHttpsReport.preflight.checks.some((check) => (
  check.id === "backend-runtime-base-url"
    && check.status === "BLOCKER"
    && check.detail.includes("HTTPS non-local backend URL")
)));
assert.ok(nonHttpsReport.preflight.checks.some((check) => (
  check.id === "ai-runtime-base-url"
    && check.status === "BLOCKER"
    && check.detail.includes("HTTPS non-local AI runtime URL")
)));

const dockerBlockedOutputDir = mkdtempSync(join(tmpdir(), "lumira-orchestrator-docker-blocked-"));
const dockerBlockedResult = spawnSync("node", ["scripts/ddd-release-evidence-orchestrator.mjs", "--strict"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ORCHESTRATOR_DIR: dockerBlockedOutputDir,
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "orchestrator-contract-test",
    DDD_RELEASE_CANDIDATE: "orchestrator-contract-sha",
    DDD_EVIDENCE_OPERATOR: "orchestrator-contract-runner",
    DDD_DOCKER_COMMAND: fakeDockerNoDaemon,
    DDD_RELEASE_ENV_FILE: "",
    LUMIRA_BASE_URL: "https://staging.lumira.internal",
    LUMIRA_AI_BASE_URL: "https://ai.staging.lumira.internal",
    PLAYWRIGHT_BASE_URL: "https://app.staging.lumira.internal",
    DDD_FRONTEND_EXPECT_DEPLOYED: "true",
    DDD_AI_EXPECT_PROVIDER_REMOTE: "true",
    DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "true",
    DDD_MIGRATION_FRESH_DB_VALIDATED: "",
    DDD_MIGRATION_UPGRADE_DB_VALIDATED: "",
    DDD_MIGRATION_FRESH_DB_EVIDENCE: "",
    DDD_MIGRATION_UPGRADE_DB_EVIDENCE: "",
  },
});

assert.equal(dockerBlockedResult.status, 0, dockerBlockedResult.stderr || dockerBlockedResult.stdout);
const dockerBlockedReport = JSON.parse(readFileSync(join(dockerBlockedOutputDir, "orchestrator-report.json"), "utf8"));
assert.ok(dockerBlockedReport.preflight.checks.some((check) => (
  check.id === "docker-daemon"
    && check.status === "BLOCKER"
    && check.detail.includes("Docker daemon is not available")
)));

const unsafeEnvFileOutputDir = mkdtempSync(join(tmpdir(), "lumira-orchestrator-unsafe-env-file-"));
const unsafeEnvFile = join(unsafeEnvFileOutputDir, ".env.release.unsafe");
writeFileSync(unsafeEnvFile, "LUMIRA_ENV=release\n");
chmodSync(unsafeEnvFile, 0o644);
const unsafeEnvFileResult = spawnSync("node", ["scripts/ddd-release-evidence-orchestrator.mjs", "--strict"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ORCHESTRATOR_DIR: unsafeEnvFileOutputDir,
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "orchestrator-contract-test",
    DDD_RELEASE_CANDIDATE: "orchestrator-contract-sha",
    DDD_EVIDENCE_OPERATOR: "orchestrator-contract-runner",
    DDD_DOCKER_COMMAND: fakeDocker,
    DDD_RELEASE_ENV_FILE: unsafeEnvFile,
    LUMIRA_BASE_URL: "https://staging.lumira.internal",
    LUMIRA_AI_BASE_URL: "https://ai.staging.lumira.internal",
    PLAYWRIGHT_BASE_URL: "https://app.staging.lumira.internal",
    DDD_FRONTEND_EXPECT_DEPLOYED: "true",
    DDD_AI_EXPECT_PROVIDER_REMOTE: "true",
    DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "true",
    DDD_MIGRATION_FRESH_DB_VALIDATED: "true",
    DDD_MIGRATION_UPGRADE_DB_VALIDATED: "true",
    DDD_MIGRATION_FRESH_DB_EVIDENCE: "artifacts/ddd/migration/fresh-db-drill.json",
    DDD_MIGRATION_UPGRADE_DB_EVIDENCE: "artifacts/ddd/migration/upgrade-db-drill.json",
  },
});

assert.equal(unsafeEnvFileResult.status, 0, unsafeEnvFileResult.stderr || unsafeEnvFileResult.stdout);
const unsafeEnvFileReport = JSON.parse(readFileSync(join(unsafeEnvFileOutputDir, "orchestrator-report.json"), "utf8"));
if (process.platform === "win32") {
  assert.ok(unsafeEnvFileReport.preflight.checks.some((check) => (
    check.id === "release-config-env-file"
      && check.status === "PASS"
      && check.detail.includes("permission check skipped on Windows")
  )));
} else {
  assert.ok(unsafeEnvFileReport.preflight.checks.some((check) => (
    check.id === "release-config-env-file"
      && check.status === "BLOCKER"
      && check.detail.includes("permissions are too broad (644)")
      && check.detail.includes("chmod 600")
  )));
}

const templateEnvFileOutputDir = mkdtempSync(join(tmpdir(), "lumira-orchestrator-template-env-file-"));
const templateEnvFile = join(templateEnvFileOutputDir, "release-env-missing.template.env");
writeFileSync(templateEnvFile, "LUMIRA_ENV=release\n");
chmodSync(templateEnvFile, 0o600);
const templateEnvFileResult = spawnSync("node", ["scripts/ddd-release-evidence-orchestrator.mjs", "--strict"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ORCHESTRATOR_DIR: templateEnvFileOutputDir,
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "orchestrator-contract-test",
    DDD_RELEASE_CANDIDATE: "orchestrator-contract-sha",
    DDD_EVIDENCE_OPERATOR: "orchestrator-contract-runner",
    DDD_DOCKER_COMMAND: fakeDocker,
    DDD_RELEASE_ENV_FILE: templateEnvFile,
    LUMIRA_BASE_URL: "https://staging.lumira.internal",
    LUMIRA_AI_BASE_URL: "https://ai.staging.lumira.internal",
    PLAYWRIGHT_BASE_URL: "https://app.staging.lumira.internal",
    DDD_FRONTEND_EXPECT_DEPLOYED: "true",
    DDD_AI_EXPECT_PROVIDER_REMOTE: "true",
    DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "true",
    DDD_MIGRATION_FRESH_DB_VALIDATED: "true",
    DDD_MIGRATION_UPGRADE_DB_VALIDATED: "true",
    DDD_MIGRATION_FRESH_DB_EVIDENCE: "artifacts/ddd/migration/fresh-db-drill.json",
    DDD_MIGRATION_UPGRADE_DB_EVIDENCE: "artifacts/ddd/migration/upgrade-db-drill.json",
  },
});

assert.equal(templateEnvFileResult.status, 0, templateEnvFileResult.stderr || templateEnvFileResult.stdout);
const templateEnvFileReport = JSON.parse(readFileSync(join(templateEnvFileOutputDir, "orchestrator-report.json"), "utf8"));
assert.ok(templateEnvFileReport.preflight.checks.some((check) => (
  check.id === "release-config-env-file"
    && check.status === "BLOCKER"
    && check.detail.includes("not template")
)));

const safeEnvFileOutputDir = mkdtempSync(join(tmpdir(), "lumira-orchestrator-safe-env-file-"));
const safeEnvFile = join(safeEnvFileOutputDir, ".env.release.safe");
writeFileSync(safeEnvFile, "LUMIRA_ENV=release\n");
chmodSync(safeEnvFile, 0o600);
const safeEnvFileResult = spawnSync("node", ["scripts/ddd-release-evidence-orchestrator.mjs", "--strict"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ORCHESTRATOR_DIR: safeEnvFileOutputDir,
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "orchestrator-contract-test",
    DDD_RELEASE_CANDIDATE: "orchestrator-contract-sha",
    DDD_EVIDENCE_OPERATOR: "orchestrator-contract-runner",
    DDD_DOCKER_COMMAND: fakeDocker,
    DDD_RELEASE_ENV_FILE: safeEnvFile,
    LUMIRA_BASE_URL: "https://staging.lumira.internal",
    LUMIRA_AI_BASE_URL: "https://ai.staging.lumira.internal",
    PLAYWRIGHT_BASE_URL: "https://app.staging.lumira.internal",
    DDD_FRONTEND_EXPECT_DEPLOYED: "true",
    DDD_AI_EXPECT_PROVIDER_REMOTE: "true",
    DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "true",
    DDD_MIGRATION_FRESH_DB_VALIDATED: "true",
    DDD_MIGRATION_UPGRADE_DB_VALIDATED: "true",
    DDD_MIGRATION_FRESH_DB_EVIDENCE: "artifacts/ddd/migration/fresh-db-drill.json",
    DDD_MIGRATION_UPGRADE_DB_EVIDENCE: "artifacts/ddd/migration/upgrade-db-drill.json",
  },
});

assert.equal(safeEnvFileResult.status, 0, safeEnvFileResult.stderr || safeEnvFileResult.stdout);
const safeEnvFileReport = JSON.parse(readFileSync(join(safeEnvFileOutputDir, "orchestrator-report.json"), "utf8"));
assert.equal(safeEnvFileReport.preflight.status, "PASS");
assert.ok(safeEnvFileReport.preflight.checks.some((check) => (
  check.id === "release-config-env-file"
    && check.status === "PASS"
    && (process.platform === "win32"
      ? check.detail.includes("permission check skipped on Windows")
      : check.detail.includes("private permissions (600)"))
)));

const fileDrivenEnvOutputDir = mkdtempSync(join(tmpdir(), "lumira-orchestrator-file-driven-env-"));
const fileDrivenEnv = join(fileDrivenEnvOutputDir, ".env.release.file-driven");
writeFileSync(fileDrivenEnv, [
  "DDD_EVIDENCE_ENVIRONMENT=orchestrator-contract-test",
  "DDD_RELEASE_CANDIDATE=orchestrator-contract-sha",
  "DDD_EVIDENCE_OPERATOR=orchestrator-contract-runner",
  `DDD_DOCKER_COMMAND=${fakeDocker}`,
  "LUMIRA_BASE_URL=https://staging.lumira.internal",
  "LUMIRA_AI_BASE_URL=https://ai.staging.lumira.internal",
  "PLAYWRIGHT_BASE_URL=https://app.staging.lumira.internal",
  "DDD_FRONTEND_EXPECT_DEPLOYED=true",
  "DDD_AI_EXPECT_PROVIDER_REMOTE=true",
  "DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true",
  "DDD_MIGRATION_FRESH_DB_VALIDATED=true",
  "DDD_MIGRATION_UPGRADE_DB_VALIDATED=true",
  "DDD_MIGRATION_FRESH_DB_EVIDENCE=artifacts/ddd/migration/fresh-db-drill.json",
  "DDD_MIGRATION_UPGRADE_DB_EVIDENCE=artifacts/ddd/migration/upgrade-db-drill.json",
  "",
].join("\n"));
chmodSync(fileDrivenEnv, 0o600);
const fileDrivenEnvResult = spawnSync("node", ["scripts/ddd-release-evidence-orchestrator.mjs", "--strict"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ORCHESTRATOR_DIR: fileDrivenEnvOutputDir,
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_RELEASE_ENV_FILE: fileDrivenEnv,
    DDD_EVIDENCE_ENVIRONMENT: "",
    DDD_RELEASE_CANDIDATE: "",
    DDD_EVIDENCE_OPERATOR: "",
    DDD_DOCKER_COMMAND: "",
    LUMIRA_BASE_URL: "",
    LUMIRA_AI_BASE_URL: "",
    DEPLOY_CHECK_BASE_URL: "",
    BASE_URL: "",
    PLAYWRIGHT_BASE_URL: "",
    FRONTEND_BASE_URL: "",
    DDD_FRONTEND_EXPECT_DEPLOYED: "",
    DDD_AI_EXPECT_PROVIDER_REMOTE: "",
    DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "",
    DDD_MIGRATION_FRESH_DB_VALIDATED: "",
    DDD_MIGRATION_UPGRADE_DB_VALIDATED: "",
    DDD_MIGRATION_FRESH_DB_EVIDENCE: "",
    DDD_MIGRATION_UPGRADE_DB_EVIDENCE: "",
  },
});

assert.equal(fileDrivenEnvResult.status, 0, fileDrivenEnvResult.stderr || fileDrivenEnvResult.stdout);
const fileDrivenEnvReport = JSON.parse(readFileSync(join(fileDrivenEnvOutputDir, "orchestrator-report.json"), "utf8"));
assert.equal(fileDrivenEnvReport.preflight.status, "PASS");
assert.ok(fileDrivenEnvReport.preflight.checks.some((check) => (
  check.id === "release-config-env-file"
    && check.status === "PASS"
    && check.detail.includes("parsedKeys=")
)));
assert.ok(fileDrivenEnvReport.preflight.checks.some((check) => (
  check.id === "backend-runtime-base-url"
    && check.status === "PASS"
)));
assert.ok(fileDrivenEnvReport.preflight.checks.some((check) => (
  check.id === "docker-daemon"
    && check.status === "PASS"
)));

const placeholderEnvOutputDir = mkdtempSync(join(tmpdir(), "lumira-orchestrator-placeholder-env-"));
const placeholderEnv = join(placeholderEnvOutputDir, ".env.release.placeholder");
writeFileSync(placeholderEnv, [
  "DDD_EVIDENCE_ENVIRONMENT=orchestrator-contract-test",
  "DDD_RELEASE_CANDIDATE=orchestrator-contract-sha",
  "DDD_EVIDENCE_OPERATOR=orchestrator-contract-runner",
  `DDD_DOCKER_COMMAND=${fakeDocker}`,
  "LUMIRA_BASE_URL=https://staging.lumira.internal",
  "LUMIRA_AI_BASE_URL=https://ai.staging.lumira.internal",
  "PLAYWRIGHT_BASE_URL=https://app.staging.lumira.internal",
  "DDD_FRONTEND_EXPECT_DEPLOYED=true",
  "DDD_AI_EXPECT_PROVIDER_REMOTE=true",
  "DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true",
  "DDD_MIGRATION_FRESH_DB_VALIDATED=__REQUIRED__",
  "DDD_MIGRATION_UPGRADE_DB_VALIDATED=true",
  "DDD_MIGRATION_FRESH_DB_EVIDENCE=artifacts/ddd/migration/fresh-db-drill.json",
  "DDD_MIGRATION_UPGRADE_DB_EVIDENCE=artifacts/ddd/migration/upgrade-db-drill.json",
  "",
].join("\n"));
chmodSync(placeholderEnv, 0o600);
const placeholderEnvResult = spawnSync("node", ["scripts/ddd-release-evidence-orchestrator.mjs", "--strict"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ORCHESTRATOR_DIR: placeholderEnvOutputDir,
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_RELEASE_ENV_FILE: placeholderEnv,
    DDD_EVIDENCE_ENVIRONMENT: "",
    DDD_RELEASE_CANDIDATE: "",
    DDD_EVIDENCE_OPERATOR: "",
    DDD_DOCKER_COMMAND: "",
    LUMIRA_BASE_URL: "",
    LUMIRA_AI_BASE_URL: "",
    DEPLOY_CHECK_BASE_URL: "",
    BASE_URL: "",
    PLAYWRIGHT_BASE_URL: "",
    FRONTEND_BASE_URL: "",
    DDD_FRONTEND_EXPECT_DEPLOYED: "",
    DDD_AI_EXPECT_PROVIDER_REMOTE: "",
    DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE: "",
    DDD_MIGRATION_FRESH_DB_VALIDATED: "",
    DDD_MIGRATION_UPGRADE_DB_VALIDATED: "",
    DDD_MIGRATION_FRESH_DB_EVIDENCE: "",
    DDD_MIGRATION_UPGRADE_DB_EVIDENCE: "",
  },
});

assert.equal(placeholderEnvResult.status, 0, placeholderEnvResult.stderr || placeholderEnvResult.stdout);
const placeholderEnvReport = JSON.parse(readFileSync(join(placeholderEnvOutputDir, "orchestrator-report.json"), "utf8"));
assert.equal(placeholderEnvReport.preflight.status, "FAIL");
assert.ok(placeholderEnvReport.preflight.checks.some((check) => (
  check.id === "migration-runtime-evidence"
    && check.status === "BLOCKER"
    && check.detail.includes("DDD_MIGRATION_FRESH_DB_VALIDATED")
)));

console.log("[ddd-release-evidence-orchestrator.test] ok");
