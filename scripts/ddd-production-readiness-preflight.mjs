#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const rawArgs = process.argv.slice(2);
const args = new Set(rawArgs);
const help = args.has("--help") || args.has("-h");
const quick = args.has("--quick");
const staticOnly = args.has("--static-only");
const skipFrontendBuild = args.has("--skip-frontend-build");
const skipBackendPackage = args.has("--skip-backend-package");
const failFast = args.has("--fail-fast");
const includeBackendArchitectureTests = args.has("--include-backend-architecture-tests");
const includeStagingChecklistContract = args.has("--include-staging-checklist-contract");
const noReport = args.has("--no-report");
const listOnly = args.has("--list");
const outputPath = process.env.DDD_PRODUCTION_PREFLIGHT_REPORT
  ? path.resolve(repoRoot, process.env.DDD_PRODUCTION_PREFLIGHT_REPORT)
  : path.join(repoRoot, "artifacts", "ddd", "release", "production-readiness-preflight.json");
const stepTimeoutMs = Number(process.env.DDD_PRODUCTION_PREFLIGHT_STEP_TIMEOUT_MS || 240000);

const mvnw = process.platform === "win32" ? ".\\mvnw.cmd" : "./mvnw";
const corepack = process.platform === "win32" ? "corepack.cmd" : "corepack";

function printHelp() {
  console.log(`DDD production readiness preflight

Usage:
  node scripts/ddd-production-readiness-preflight.mjs [options]

Options:
  --quick                               Run local operator checks; skips heavyweight backend architecture tests and frontend production build.
  --static-only                         Run only script/config/static release contracts.
  --skip-frontend-build                 Skip frontend production build.
  --skip-backend-package                Skip backend package build.
  --include-backend-architecture-tests  Include the slow backend DDD architecture slice in --quick mode.
  --include-staging-checklist-contract  Include the slow staging checklist bundle contract test.
  --fail-fast                           Stop after the first failing step.
  --no-report                           Do not write the JSON report; useful for clean CI/static checks.
  --list                                Print the planned steps and exit without running checks or writing a report.
  --help, -h                            Show this help.

Environment:
  DDD_PRODUCTION_PREFLIGHT_REPORT          Override report path.
  DDD_PRODUCTION_PREFLIGHT_STEP_TIMEOUT_MS Override default per-step timeout in milliseconds.

Examples:
  node scripts/ddd-production-readiness-preflight.mjs --quick
  node scripts/ddd-production-readiness-preflight.mjs --static-only
  node scripts/ddd-production-readiness-preflight.mjs --static-only --no-report
  node scripts/ddd-production-readiness-preflight.mjs --quick --no-report --list
  node scripts/ddd-production-readiness-preflight.mjs --quick --include-backend-architecture-tests
  node scripts/ddd-production-readiness-preflight.mjs --static-only --include-staging-checklist-contract
`);
}

if (help) {
  printHelp();
  process.exit(0);
}

const backendArchitectureTests = [
  "DddArchitectureBoundaryTest",
  "DddArchitectureCatalogControllerTest",
  "OwnerReadModelMetricsServiceTest",
  "IamV2ControllerTest",
  "IamReadinessV2ControllerTest",
  "PlatformReadinessV2ControllerTest",
  "PlatformV2ControllerTest",
  "AiReadinessV2ControllerTest",
  "AiOwnerMetricsServiceTest",
  "AiKnowledgeBaseAppServiceTest",
  "AiKnowledgeVectorServiceTest",
  "RuntimeSecurityPropertiesValidatorTest",
].join(",");

const commands = [
  {
    id: "backend-ddd-architecture-tests",
    command: mvnw,
    args: [
      "-pl",
      "services/system-service",
      "-am",
      `-Dtest=${backendArchitectureTests}`,
      "-Dsurefire.failIfNoSpecifiedTests=false",
      "-Dsurefire.exitTimeout=60",
      "test",
    ],
    skip: staticOnly || (quick && !includeBackendArchitectureTests),
    skipReason: staticOnly ? "static-only" : "quick",
    timeoutMs: 900000,
  },
  {
    id: "backend-lumira-server-package",
    command: mvnw,
    args: ["-pl", "services/lumira-server", "-am", "-DskipTests", "package"],
    skip: staticOnly || skipBackendPackage,
    skipReason: staticOnly ? "static-only" : "skip-backend-package",
    timeoutMs: 600000,
  },
  {
    id: "frontend-lint",
    command: corepack,
    args: ["pnpm", "--dir", "frontend", "run", "lint"],
    skip: staticOnly,
    skipReason: "static-only",
  },
  {
    id: "frontend-typecheck",
    command: corepack,
    args: ["pnpm", "--dir", "frontend", "run", "typecheck"],
    skip: staticOnly,
    skipReason: "static-only",
  },
  {
    id: "frontend-unit-tests",
    command: corepack,
    args: ["pnpm", "--dir", "frontend", "run", "test"],
    skip: staticOnly,
    skipReason: "static-only",
  },
  {
    id: "frontend-production-build",
    command: corepack,
    args: ["pnpm", "--dir", "frontend", "run", "build"],
    skip: staticOnly || quick || skipFrontendBuild,
    skipReason: staticOnly ? "static-only" : quick ? "quick" : "skip-frontend-build",
  },
  {
    id: "migration-evidence-sync",
    command: "node",
    args: ["scripts/ddd-migration-evidence-sync.test.mjs"],
  },
  {
    id: "release-config-sync",
    command: "node",
    args: ["scripts/ddd-release-config-sync.test.mjs"],
  },
  {
    id: "dockerfile-contract",
    command: "node",
    args: ["scripts/ddd-dockerfile-contract.test.mjs"],
  },
  {
    id: "final-go-no-go-contract",
    command: "node",
    args: ["scripts/ddd-release-final-go-no-go-gate-contract.test.mjs"],
  },
  {
    id: "staging-execution-checklist-contract",
    command: "node",
    args: ["scripts/ddd-staging-execution-checklist.test.mjs"],
    skip: !includeStagingChecklistContract,
    skipReason: "heavy-contract",
  },
  {
    id: "staging-runtime-check-contract",
    command: "node",
    args: ["scripts/ddd-staging-runtime-check.test.mjs"],
  },
  {
    id: "staging-data-safety-check-contract",
    command: "node",
    args: ["scripts/ddd-staging-data-safety-check.test.mjs"],
  },
  {
    id: "staging-dispatch-check",
    command: "node",
    args: ["scripts/ddd-staging-execution-checklist.mjs", "--dispatch-check"],
    skip: staticOnly,
    skipReason: "static-only",
  },
];

function mode() {
  return {
    quick,
    staticOnly,
    skipFrontendBuild,
    skipBackendPackage,
    failFast,
    includeStagingChecklistContract,
    noReport,
    listOnly,
  };
}

if (listOnly) {
  const plan = {
    status: "PLAN",
    willRunChecks: false,
    willWriteReport: false,
    outputPath: noReport ? null : outputPath,
    mode: mode(),
    steps: commands.map((step) => ({
      id: step.id,
      command: [step.command, ...step.args].join(" "),
      skip: step.skip === true,
      skipReason: step.skip ? step.skipReason : null,
      timeoutMs: step.timeoutMs ?? stepTimeoutMs,
    })),
  };
  console.log(JSON.stringify(plan, null, 2));
  process.exit(0);
}

function nowIso() {
  return new Date().toISOString();
}

function runCommand(step) {
  if (step.skip) {
    return {
      id: step.id,
      status: "SKIPPED",
      skipReason: step.skipReason,
      command: [step.command, ...step.args].join(" "),
      startedAt: nowIso(),
      finishedAt: nowIso(),
      durationMs: 0,
      exitCode: null,
    };
  }

  const startedAt = nowIso();
  const started = Date.now();
  console.log(`[preflight] ${step.id}`);
  const childCommand = process.platform === "win32" && step.command.endsWith(".cmd")
    ? "cmd.exe"
    : step.command;
  const childArgs = process.platform === "win32" && step.command.endsWith(".cmd")
    ? ["/d", "/s", "/c", step.command, ...step.args]
    : step.args;
  const result = spawnSync(childCommand, childArgs, {
    cwd: repoRoot,
    stdio: "inherit",
    timeout: step.timeoutMs ?? stepTimeoutMs,
    env: process.env,
  });
  const durationMs = Date.now() - started;
  const finishedAt = nowIso();
  const status = result.status === 0 ? "PASS" : "FAIL";
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  return {
    id: step.id,
    status,
    command: [step.command, ...step.args].join(" "),
    startedAt,
    finishedAt,
    durationMs,
    exitCode: result.status,
    signal: result.signal,
    error: result.error?.message,
    timedOut: result.error?.code === "ETIMEDOUT",
  };
}

function gitValue(args) {
  const result = spawnSync("git", args, {
    cwd: repoRoot,
    encoding: "utf8",
  });
  return result.status === 0 ? result.stdout.trim() : "";
}

const startedAt = nowIso();
const results = [];
for (const step of commands) {
  const result = runCommand(step);
  results.push(result);
  if (failFast && result.status === "FAIL") {
    break;
  }
}

const summary = {
  total: results.length,
  passed: results.filter((result) => result.status === "PASS").length,
  failed: results.filter((result) => result.status === "FAIL").length,
  skipped: results.filter((result) => result.status === "SKIPPED").length,
};

const report = {
  status: summary.failed === 0 ? "PASS" : "FAIL",
  generatedAt: nowIso(),
  startedAt,
  finishedAt: nowIso(),
  mode: {
    ...mode(),
  },
  provenance: {
    repoRoot,
    gitCommit: gitValue(["rev-parse", "HEAD"]),
    gitBranch: gitValue(["branch", "--show-current"]),
    operator: os.userInfo().username,
    platform: process.platform,
    nodeVersion: process.version,
  },
  summary,
  results,
};

if (!noReport) {
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`);
}

console.log(`[preflight] status=${report.status}; passed=${summary.passed}; failed=${summary.failed}; skipped=${summary.skipped}; report=${noReport ? "skipped" : outputPath}`);

if (summary.failed > 0) {
  process.exit(1);
}
