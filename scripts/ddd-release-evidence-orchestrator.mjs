#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { redactLocalPaths } from "./ddd-release-evidence-utils.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const args = new Set(process.argv.slice(2));
const runMode = args.has("--run");
const strict = args.has("--strict") || process.env.DDD_RELEASE_EVIDENCE_STRICT === "true";
const outputDir = process.env.DDD_RELEASE_ORCHESTRATOR_DIR
  ? path.resolve(process.env.DDD_RELEASE_ORCHESTRATOR_DIR)
  : path.join(repoRoot, "artifacts", "ddd", "release");
const outputFile = path.join(outputDir, "orchestrator-report.json");
const sourceEnvironment = process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";
const promoteAuthenticatedBaseline = process.env.DDD_AUTH_PERF_BASELINE_PROMOTE === "true";
const dockerCommand = process.env.DDD_DOCKER_COMMAND || "docker";

const steps = [
  {
    id: "release-env-file-lint",
    label: "Release env file lint",
    command: ["node", "scripts/ddd-release-env-file-lint.mjs"],
  },
  {
    id: "release-config-evidence",
    label: "Production-equivalent config evidence",
    command: ["node", "scripts/ddd-release-config-evidence.mjs"],
  },
  {
    id: "backend-tests",
    label: "Backend Maven tests",
    command: ["./mvnw", "test"],
    heavy: true,
  },
  {
    id: "backend-test-evidence",
    label: "Backend test evidence",
    command: ["node", "scripts/ddd-backend-test-evidence.mjs"],
  },
  {
    id: "backend-build-evidence",
    label: "Backend build evidence",
    command: ["node", "scripts/ddd-backend-build-evidence.mjs"],
    heavy: true,
  },
  {
    id: "docker-build-evidence",
    label: "Docker image evidence",
    command: ["node", "scripts/ddd-docker-build-evidence.mjs"],
    heavy: true,
  },
  {
    id: "frontend-static-evidence",
    label: "Frontend lint/typecheck/unit evidence",
    command: ["node", "scripts/ddd-frontend-static-evidence.mjs"],
    heavy: true,
  },
  {
    id: "frontend-build-evidence",
    label: "Frontend production build evidence",
    command: ["node", "scripts/ddd-frontend-build-evidence.mjs"],
    heavy: true,
  },
  {
    id: "migration-evidence",
    label: "Flyway migration evidence",
    command: ["node", "scripts/ddd-migration-evidence.mjs"],
  },
  {
    id: "runtime-readiness",
    label: "Runtime readiness smoke",
    command: ["node", "scripts/ddd-runtime-readiness-smoke.mjs"],
    runtime: true,
  },
  {
    id: "authenticated-performance",
    label: "Authenticated runtime performance smoke",
    command: ["node", "scripts/ddd-authenticated-performance-smoke.mjs"],
    runtime: true,
  },
  {
    id: "authenticated-performance-baseline",
    label: "Promote authenticated runtime performance baseline",
    command: ["node", "scripts/ddd-promote-performance-baseline.mjs"],
    optional: true,
    enabled: promoteAuthenticatedBaseline,
    env: {
      DDD_AUTH_PERF_BASELINE_PROMOTE: promoteAuthenticatedBaseline ? "true" : "false",
      DDD_AUTH_PERF_BASELINE_ENVIRONMENT: process.env.DDD_AUTH_PERF_BASELINE_ENVIRONMENT || sourceEnvironment,
      DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT: process.env.DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT || "artifacts/ddd/performance/authenticated-runtime-actual.json",
      DDD_AUTH_PERF_BASELINE_ACCEPTED_BY: process.env.DDD_AUTH_PERF_BASELINE_ACCEPTED_BY || evidenceOperator,
    },
  },
  {
    id: "file-processing",
    label: "File processing E2E smoke",
    command: ["node", "scripts/ddd-file-processing-e2e-smoke.mjs"],
    runtime: true,
  },
  {
    id: "payment-webhook",
    label: "Payment webhook E2E smoke",
    command: ["node", "scripts/ddd-payment-webhook-e2e-smoke.mjs"],
    runtime: true,
  },
  {
    id: "outbox-replay-dead-letter",
    label: "Outbox replay/dead-letter smoke",
    command: ["node", "scripts/ddd-outbox-replay-dead-letter-smoke.mjs"],
    env: strict ? { DDD_OUTBOX_SMOKE_STRICT: "true" } : {},
  },
  {
    id: "job-e2e",
    label: "Job/internal E2E smoke",
    command: ["node", "scripts/ddd-job-e2e-smoke.mjs"],
    runtime: true,
  },
  {
    id: "ai-runtime",
    label: "AI runtime drill",
    command: ["node", "scripts/ddd-ai-runtime-drill.mjs"],
    runtime: true,
  },
  {
    id: "frontend-playwright-smoke",
    label: "Frontend deployed Playwright smoke",
    command: ["node", "scripts/ddd-frontend-playwright-smoke.mjs"],
    runtime: true,
  },
  {
    id: "frontend-smoke",
    label: "Frontend deployed smoke conversion",
    command: ["node", "scripts/ddd-frontend-smoke-evidence.mjs"],
    runtime: true,
  },
  {
    id: "rollback-drill",
    label: "Rollback drill evidence validation",
    command: ["node", "scripts/ddd-rollback-drill-evidence.mjs"],
  },
  {
    id: "explain-gate",
    label: "Hot path EXPLAIN gate",
    command: ["node", "scripts/ddd-explain-gate.mjs"],
  },
  {
    id: "physical-split",
    label: "Physical split readiness gate",
    command: ["node", "scripts/ddd-physical-split-gate.mjs"],
    env: strict ? { DDD_SPLIT_STRICT: "true" } : {},
  },
  {
    id: "manifest-provenance-preflight",
    label: "Release manifest provenance preflight",
    command: ["node", "scripts/ddd-release-evidence-manifest.mjs"],
    env: { DDD_RELEASE_MANIFEST_CHECK_ENV: "true" },
    always: true,
  },
  {
    id: "manifest",
    label: "Release evidence manifest",
    command: ["node", "scripts/ddd-release-evidence-manifest.mjs"],
    always: true,
  },
  {
    id: "release-gate",
    label: "Release evidence gate",
    command: ["node", "scripts/ddd-release-evidence-gate.mjs"],
    env: strict ? { DDD_RELEASE_EVIDENCE_STRICT: "true" } : {},
    always: true,
  },
  {
    id: "readiness-summary",
    label: "Release readiness summary",
    command: ["node", "scripts/ddd-release-readiness-summary.mjs"],
    always: true,
  },
];

function tail(text) {
  return String(text || "").split(/\r?\n/).slice(-80).join("\n").trim();
}

function redactOutput(text) {
  return redactLocalPaths(text, { repoRoot, homeDir: os.homedir() });
}

function runStep(step) {
  const startedAt = Date.now();
  if (step.optional === true && step.enabled !== true) {
    return {
      id: step.id,
      label: step.label,
      command: step.command.join(" "),
      status: 0,
      signal: null,
      elapsedMs: Date.now() - startedAt,
      skipped: true,
      skipReason: "optional step disabled",
      stdoutTail: "",
      stderrTail: "",
      error: null,
    };
  }
  const [command, ...commandArgs] = step.command;
  const strictEnv = strict ? { DDD_RELEASE_EVIDENCE_STRICT: "true" } : {};
  const result = spawnSync(command, commandArgs, {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 20,
    env: {
      ...process.env,
      ...strictEnv,
      ...(step.env || {}),
    },
  });
  return {
    id: step.id,
    label: step.label,
    command: step.command.join(" "),
    status: result.status,
    signal: result.signal,
    elapsedMs: Date.now() - startedAt,
    stdoutTail: redactOutput(tail(result.stdout)),
    stderrTail: redactOutput(tail(result.stderr)),
    error: result.error ? redactOutput(result.error.message) : null,
  };
}

const selected = steps;
const results = [];

function selectedStepReport(step) {
  return {
    id: step.id,
    label: step.label,
    command: step.command.join(" "),
    heavy: step.heavy === true,
    runtime: step.runtime === true,
    optional: step.optional === true,
    enabled: step.enabled !== false,
    always: step.always === true,
    strictEnv: strict,
    envKeys: Object.keys({
      ...(strict ? { DDD_RELEASE_EVIDENCE_STRICT: "true" } : {}),
      ...(step.env || {}),
    }).sort(),
  };
}

function isMissing(value) {
  return typeof value !== "string" || value.trim() === "";
}

function isPlaceholderValue(value) {
  if (typeof value !== "string") {
    return false;
  }
  const text = value.trim();
  return text === "__REQUIRED__"
    || /^(TODO|TBD|CHANGEME)$/i.test(text)
    || /^replace[-_ ]?with[-_ ]?/i.test(text);
}

function hasUsableEnvValue(value) {
  return !isMissing(value) && !isPlaceholderValue(value);
}

function isLocalUrl(value) {
  return typeof value === "string"
    && /^(https?:\/\/)?(127\.0\.0\.1|localhost|\[::1\])(?::\d+)?/i.test(value.trim());
}

function isHttpsUrl(value) {
  try {
    return new URL(value).protocol === "https:";
  } catch {
    return false;
  }
}

function envValue(key, releaseEnvValues = new Map()) {
  if (!isMissing(process.env[key])) {
    return process.env[key];
  }
  return releaseEnvValues.get(key) || "";
}

function firstEnv(keys, releaseEnvValues = new Map()) {
  for (const key of keys) {
    const value = envValue(key, releaseEnvValues);
    if (hasUsableEnvValue(value)) {
      return { key, value };
    }
  }
  return { key: null, value: "" };
}

function preflightCheck(id, status, detail, envKeys = []) {
  return { id, status, detail, envKeys };
}

const releaseEnvTemplateNames = new Set([
  "release-env-missing.template.env",
  "release-closure-wave-env.template.env",
  "release-final-owner-queue-env.template.env",
  "release-env-canonical-fill.template.env",
]);

function formatFileMode(mode) {
  return (mode & 0o777).toString(8).padStart(3, "0");
}

function displayReleaseEnvPath(inputPath, resolvedPath) {
  const relative = path.relative(repoRoot, resolvedPath);
  if (relative && !relative.startsWith("..") && !path.isAbsolute(relative)) {
    return relative;
  }
  return redactLocalPaths(inputPath);
}

function parseReleaseEnvFileValues(file) {
  const values = new Map();
  const issues = [];
  const seen = new Set();
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  for (const [index, rawLine] of lines.entries()) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) {
      continue;
    }
    const match = line.match(/^(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$/);
    if (!match) {
      issues.push(`line ${index + 1} must be KEY=value, export KEY=value, or a comment`);
      continue;
    }
    const key = match[1];
    if (seen.has(key)) {
      issues.push(`duplicate env key: ${key}`);
    }
    seen.add(key);
    let value = match[2].trim();
    const quoted = value.match(/^(['"])(.*)\1$/s);
    if (quoted) {
      value = quoted[2];
    }
    values.set(key, value);
  }
  return { values, issues };
}

function runPreflightCommand(command, commandArgs) {
  const result = spawnSync(command, commandArgs, {
    cwd: repoRoot,
    encoding: "utf8",
    shell: process.platform === "win32" && /\.(?:cmd|bat)$/i.test(command),
    maxBuffer: 1024 * 1024,
  });
  return {
    status: result.status,
    stdoutTail: redactOutput(tail(result.stdout || "")),
    stderrTail: redactOutput(tail(result.stderr || "")),
    error: result.error ? redactOutput(result.error.message) : null,
  };
}

function buildPreflightChecks() {
  const checks = [];
  let releaseEnvValues = new Map();

  const releaseEnvFile = process.env.DDD_RELEASE_ENV_FILE;
  if (isMissing(releaseEnvFile)) {
    checks.push(preflightCheck(
      "release-config-env-file",
      strict ? "BLOCKER" : "WARNING",
      "DDD_RELEASE_ENV_FILE is required for strict release configuration evidence",
      ["DDD_RELEASE_ENV_FILE"],
    ));
  } else {
    const releaseEnvPath = path.resolve(repoRoot, releaseEnvFile);
    const releaseEnvDisplayPath = displayReleaseEnvPath(releaseEnvFile, releaseEnvPath);
    const releaseEnvName = path.basename(releaseEnvPath);
    if (releaseEnvTemplateNames.has(releaseEnvName)) {
      checks.push(preflightCheck(
        "release-config-env-file",
        "BLOCKER",
        `DDD_RELEASE_ENV_FILE must point to a real checked release env file, not template ${releaseEnvDisplayPath}`,
        ["DDD_RELEASE_ENV_FILE"],
      ));
    } else if (!fs.existsSync(releaseEnvPath)) {
      checks.push(preflightCheck(
        "release-config-env-file",
        "BLOCKER",
        `DDD_RELEASE_ENV_FILE does not exist: ${releaseEnvDisplayPath}`,
        ["DDD_RELEASE_ENV_FILE"],
      ));
    } else {
      const releaseEnvStat = fs.statSync(releaseEnvPath);
      const releaseEnvMode = formatFileMode(releaseEnvStat.mode);
      if (process.platform !== "win32" && (releaseEnvStat.mode & 0o077) !== 0) {
        checks.push(preflightCheck(
          "release-config-env-file",
          "BLOCKER",
          `DDD_RELEASE_ENV_FILE permissions are too broad (${releaseEnvMode}); use chmod 600 for ${releaseEnvDisplayPath}`,
          ["DDD_RELEASE_ENV_FILE"],
        ));
      } else {
        const parsedReleaseEnv = parseReleaseEnvFileValues(releaseEnvPath);
        releaseEnvValues = parsedReleaseEnv.values;
        if (parsedReleaseEnv.issues.length > 0) {
          checks.push(preflightCheck(
            "release-config-env-file",
            "BLOCKER",
            `DDD_RELEASE_ENV_FILE has invalid env syntax: ${parsedReleaseEnv.issues.slice(0, 3).join("; ")}`,
            ["DDD_RELEASE_ENV_FILE"],
          ));
        } else {
          checks.push(preflightCheck(
            "release-config-env-file",
            "PASS",
            process.platform === "win32"
              ? `env file present; permission check skipped on Windows (mode=${releaseEnvMode}); parsedKeys=${releaseEnvValues.size}: ${releaseEnvDisplayPath}`
              : `env file present with private permissions (${releaseEnvMode}); parsedKeys=${releaseEnvValues.size}: ${releaseEnvDisplayPath}`,
            ["DDD_RELEASE_ENV_FILE"],
          ));
        }
      }
    }
  }

  const provenanceKeys = ["DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_CANDIDATE", "DDD_EVIDENCE_OPERATOR"];
  const missingProvenance = [];
  if (!hasUsableEnvValue(envValue("DDD_EVIDENCE_ENVIRONMENT", releaseEnvValues)) && !hasUsableEnvValue(envValue("DDD_RELEASE_ENVIRONMENT", releaseEnvValues))) {
    missingProvenance.push("DDD_EVIDENCE_ENVIRONMENT");
  }
  if (!hasUsableEnvValue(envValue("DDD_RELEASE_CANDIDATE", releaseEnvValues)) && !hasUsableEnvValue(envValue("GITHUB_SHA", releaseEnvValues))) {
    missingProvenance.push("DDD_RELEASE_CANDIDATE");
  }
  if (!hasUsableEnvValue(envValue("DDD_EVIDENCE_OPERATOR", releaseEnvValues)) && !hasUsableEnvValue(envValue("GITHUB_ACTOR", releaseEnvValues))) {
    missingProvenance.push("DDD_EVIDENCE_OPERATOR");
  }
  if (missingProvenance.length > 0) {
    checks.push(preflightCheck(
      "release-provenance",
      strict ? "BLOCKER" : "WARNING",
      `missing provenance env: ${missingProvenance.join(", ")}`,
      provenanceKeys,
    ));
  } else {
    checks.push(preflightCheck("release-provenance", "PASS", "release provenance env is available", provenanceKeys));
  }

  const backendBase = firstEnv(["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL"], releaseEnvValues);
  if (isMissing(backendBase.value)) {
    checks.push(preflightCheck(
      "backend-runtime-base-url",
      strict ? "BLOCKER" : "WARNING",
      "missing backend runtime base URL",
      ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL"],
    ));
  } else if (strict && (!isHttpsUrl(backendBase.value) || isLocalUrl(backendBase.value))) {
    checks.push(preflightCheck(
      "backend-runtime-base-url",
      "BLOCKER",
      `strict release requires HTTPS non-local backend URL, got ${backendBase.key}=${backendBase.value}`,
      ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL"],
    ));
  } else {
    checks.push(preflightCheck("backend-runtime-base-url", "PASS", `${backendBase.key} is set`, ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL"]));
  }

  const aiBase = firstEnv(["LUMIRA_AI_BASE_URL", "LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL"], releaseEnvValues);
  if (isMissing(aiBase.value)) {
    checks.push(preflightCheck(
      "ai-runtime-base-url",
      strict ? "BLOCKER" : "WARNING",
      "missing AI runtime base URL",
      ["LUMIRA_AI_BASE_URL", "LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL"],
    ));
  } else if (strict && (!isHttpsUrl(aiBase.value) || isLocalUrl(aiBase.value))) {
    checks.push(preflightCheck(
      "ai-runtime-base-url",
      "BLOCKER",
      `strict release requires HTTPS non-local AI runtime URL, got ${aiBase.key}=${aiBase.value}`,
      ["LUMIRA_AI_BASE_URL", "LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL"],
    ));
  } else {
    checks.push(preflightCheck("ai-runtime-base-url", "PASS", `${aiBase.key} is set`, ["LUMIRA_AI_BASE_URL", "LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL"]));
  }

  const frontendBase = firstEnv(["PLAYWRIGHT_BASE_URL", "FRONTEND_BASE_URL"], releaseEnvValues);
  if (isMissing(frontendBase.value)) {
    checks.push(preflightCheck(
      "frontend-runtime-base-url",
      strict ? "BLOCKER" : "WARNING",
      "missing deployed frontend base URL",
      ["PLAYWRIGHT_BASE_URL", "FRONTEND_BASE_URL"],
    ));
  } else if (strict && (!frontendBase.value.startsWith("https://") || isLocalUrl(frontendBase.value))) {
    checks.push(preflightCheck(
      "frontend-runtime-base-url",
      "BLOCKER",
      `strict release requires HTTPS non-local frontend URL, got ${frontendBase.key}=${frontendBase.value}`,
      ["PLAYWRIGHT_BASE_URL", "FRONTEND_BASE_URL"],
    ));
  } else {
    checks.push(preflightCheck("frontend-runtime-base-url", "PASS", `${frontendBase.key} is set`, ["PLAYWRIGHT_BASE_URL", "FRONTEND_BASE_URL"]));
  }

  if (strict && envValue("DDD_FRONTEND_EXPECT_DEPLOYED", releaseEnvValues) !== "true") {
    checks.push(preflightCheck(
      "frontend-deployed-expectation",
      "BLOCKER",
      "DDD_FRONTEND_EXPECT_DEPLOYED=true is required for strict frontend smoke",
      ["DDD_FRONTEND_EXPECT_DEPLOYED"],
    ));
  } else {
    checks.push(preflightCheck("frontend-deployed-expectation", "PASS", "frontend deployed expectation is satisfied", ["DDD_FRONTEND_EXPECT_DEPLOYED"]));
  }

  const effectiveDockerCommand = envValue("DDD_DOCKER_COMMAND", releaseEnvValues) || dockerCommand;
  const dockerVersion = runPreflightCommand(effectiveDockerCommand, ["--version"]);
  if (dockerVersion.status !== 0) {
    checks.push(preflightCheck(
      "docker-cli",
      strict ? "BLOCKER" : "WARNING",
      `Docker CLI is not available: ${dockerVersion.error || dockerVersion.stderrTail || "docker --version failed"}`,
      ["DDD_DOCKER_COMMAND"],
    ));
    checks.push(preflightCheck(
      "docker-daemon",
      strict ? "BLOCKER" : "WARNING",
      "Docker daemon was not checked because Docker CLI is unavailable",
      ["DDD_DOCKER_COMMAND"],
    ));
  } else {
    checks.push(preflightCheck("docker-cli", "PASS", dockerVersion.stdoutTail || `${effectiveDockerCommand} --version passed`, ["DDD_DOCKER_COMMAND"]));
  }

  const dockerInfo = dockerVersion.status === 0
    ? runPreflightCommand(effectiveDockerCommand, ["info", "--format", "{{json .ServerVersion}}"])
    : null;
  if (dockerInfo && dockerInfo.status !== 0) {
    checks.push(preflightCheck(
      "docker-daemon",
      strict ? "BLOCKER" : "WARNING",
      `Docker daemon is not available: ${dockerInfo.stderrTail || dockerInfo.error || "docker info failed"}`,
      ["DDD_DOCKER_COMMAND"],
    ));
  } else if (dockerInfo) {
    checks.push(preflightCheck("docker-daemon", "PASS", dockerInfo.stdoutTail || "docker daemon is available", ["DDD_DOCKER_COMMAND"]));
  }

  if (strict && envValue("DDD_AI_EXPECT_PROVIDER_REMOTE", releaseEnvValues) !== "true") {
    checks.push(preflightCheck(
      "ai-provider-remote-expectation",
      "BLOCKER",
      "DDD_AI_EXPECT_PROVIDER_REMOTE=true is required for strict AI runtime evidence",
      ["DDD_AI_EXPECT_PROVIDER_REMOTE"],
    ));
  } else {
    checks.push(preflightCheck("ai-provider-remote-expectation", "PASS", "AI provider remote expectation is satisfied", ["DDD_AI_EXPECT_PROVIDER_REMOTE"]));
  }

  if (strict && envValue("DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE", releaseEnvValues) !== "true") {
    checks.push(preflightCheck(
      "ai-owner-gateway-remote-expectation",
      "BLOCKER",
      "DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true is required for strict AI runtime evidence",
      ["DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE"],
    ));
  } else {
    checks.push(preflightCheck("ai-owner-gateway-remote-expectation", "PASS", "AI owner gateway expectation is satisfied", ["DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE"]));
  }

  const migrationEvidenceKeys = [
    "DDD_MIGRATION_FRESH_DB_VALIDATED",
    "DDD_MIGRATION_UPGRADE_DB_VALIDATED",
    "DDD_MIGRATION_FRESH_DB_EVIDENCE",
    "DDD_MIGRATION_UPGRADE_DB_EVIDENCE",
  ];
  const missingMigrationKeys = migrationEvidenceKeys.filter((key) => !hasUsableEnvValue(envValue(key, releaseEnvValues)));
  if (strict && missingMigrationKeys.length > 0) {
    checks.push(preflightCheck(
      "migration-runtime-evidence",
      "BLOCKER",
      `missing migration drill env: ${missingMigrationKeys.join(", ")}`,
      migrationEvidenceKeys,
    ));
  } else {
    checks.push(preflightCheck("migration-runtime-evidence", "PASS", "migration drill env is available", migrationEvidenceKeys));
  }

  return checks;
}

function buildPreflightReport() {
  const checks = buildPreflightChecks();
  return {
    status: checks.some((check) => check.status === "BLOCKER") ? "FAIL" : "PASS",
    blockers: checks.filter((check) => check.status === "BLOCKER").length,
    warnings: checks.filter((check) => check.status === "WARNING").length,
    checks,
  };
}

function buildReport() {
  const enabledSteps = selected.filter((step) => step.enabled !== false);
  return {
    generatedAt: new Date().toISOString(),
    mode: runMode ? "run" : "plan",
    strict,
    sourceEnvironment,
    releaseCandidate,
    evidenceOperator,
    preflight: buildPreflightReport(),
    selectedSteps: selected.map(selectedStepReport),
    results,
    summary: {
      steps: selected.length,
      enabled: enabledSteps.length,
      disabled: selected.length - enabledSteps.length,
      executed: results.length,
      failed: results.filter((result) => result.status !== 0).length,
    },
  };
}

function writeReport() {
  const report = buildReport();
  fs.mkdirSync(outputDir, { recursive: true });
  fs.writeFileSync(outputFile, `${JSON.stringify(report, null, 2)}\n`);
  return report;
}

if (runMode) {
  writeReport();
  for (const step of selected) {
    console.log(`[ddd-release-evidence-orchestrator] running ${step.id}: ${step.command.join(" ")}`);
    results.push(runStep(step));
    writeReport();
  }
} else {
  console.log("[ddd-release-evidence-orchestrator] plan mode; pass --run to execute evidence scripts.");
}

const report = writeReport();

if (runMode && report.summary.failed > 0) {
  console.error(`[ddd-release-evidence-orchestrator] completed with failed=${report.summary.failed}; report=${outputFile}`);
  process.exit(1);
}

console.log(`[ddd-release-evidence-orchestrator] ${report.mode} complete; steps=${report.summary.steps}; report=${outputFile}`);
