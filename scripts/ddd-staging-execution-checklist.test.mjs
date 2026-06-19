#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-staging-checklist-"));
const outputBase = path.join(tmpDir, "staging-checklist");
const ownerOnlyOutputBase = path.join(tmpDir, "staging-checklist-ai-owner");
const listOwnersOutputBase = path.join(tmpDir, "staging-checklist-list-owners");
const helpOutputBase = path.join(tmpDir, "staging-checklist-help");
const summaryOutputBase = path.join(tmpDir, "staging-checklist-summary");
const evidenceGapsOutputBase = path.join(tmpDir, "staging-checklist-evidence-gaps");
const handoffBundleDir = path.join(tmpDir, "staging-handoff-bundle");
const childTimeoutMs = Number(process.env.DDD_STAGING_CHECKLIST_TEST_CHILD_TIMEOUT_MS || 420000);

function spawnSyncWithTimeout(command, args, options = {}) {
  const result = spawnSync(command, args, {
    timeout: childTimeoutMs,
    maxBuffer: 16 * 1024 * 1024,
    ...options,
  });
  if (result.status === null || result.status === -1 || result.status === 4294967295) {
    console.error(`[spawnSyncWithTimeout] status=${result.status}; signal=${result.signal || "none"}; error=${result.error ? result.error.message : "none"}; command=${command} ${args.join(" ")}`);
  }
  return result;
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

const stopAfterCheckpoint = process.env.DDD_STAGING_CHECKLIST_TEST_STOP_AFTER || "";

function maybeStopAfter(checkpoint) {
  if (stopAfterCheckpoint === checkpoint) {
    console.log(`[ddd-staging-execution-checklist.test] ok stopAfter=${checkpoint}`);
    fs.rmSync(tmpDir, { recursive: true, force: true });
    process.exit(0);
  }
}

try {
  const preflightHelpResult = spawnSyncWithTimeout("node", ["scripts/ddd-production-readiness-preflight.mjs", "--help"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_PRODUCTION_PREFLIGHT_REPORT: path.join(tmpDir, "preflight-help.json"),
    },
  });
  assert.equal(preflightHelpResult.status, 0, preflightHelpResult.stderr || preflightHelpResult.stdout);
  assert.match(preflightHelpResult.stdout, /DDD production readiness preflight/);
  assert.match(preflightHelpResult.stdout, /--quick/);
  assert.match(preflightHelpResult.stdout, /--static-only/);
  assert.match(preflightHelpResult.stdout, /--include-backend-architecture-tests/);
  assert.match(preflightHelpResult.stdout, /--no-report/);
  assert.match(preflightHelpResult.stdout, /--list/);
  assert.match(preflightHelpResult.stdout, /DDD_PRODUCTION_PREFLIGHT_REPORT/);
  assert.equal(fs.existsSync(path.join(tmpDir, "preflight-help.json")), false, "preflight help should not write a report");

  const preflightListResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-production-readiness-preflight.mjs",
    "--quick",
    "--no-report",
    "--list",
  ], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_PRODUCTION_PREFLIGHT_REPORT: path.join(tmpDir, "preflight-list.json"),
    },
  });
  assert.equal(preflightListResult.status, 0, preflightListResult.stderr || preflightListResult.stdout);
  const preflightPlan = JSON.parse(preflightListResult.stdout);
  assert.equal(preflightPlan.status, "PLAN");
  assert.equal(preflightPlan.willRunChecks, false);
  assert.equal(preflightPlan.willWriteReport, false);
  assert.equal(preflightPlan.outputPath, null);
  assert.equal(preflightPlan.mode.quick, true);
  assert.equal(preflightPlan.mode.noReport, true);
  assert(preflightPlan.steps.some((step) => step.id === "backend-lumira-server-package" && step.skip === false));
  assert(preflightPlan.steps.some((step) => step.id === "backend-ddd-architecture-tests" && step.skipReason === "quick"));
  assert(preflightPlan.steps.some((step) => step.id === "staging-dispatch-check" && step.skip === false));
  assert.equal(fs.existsSync(path.join(tmpDir, "preflight-list.json")), false, "preflight --list should not write a report");

  const checklistHelpResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--help"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: helpOutputBase,
    },
  });
  assert.equal(checklistHelpResult.status, 0, checklistHelpResult.stderr || checklistHelpResult.stdout);
  assert.match(checklistHelpResult.stdout, /DDD staging execution checklist/);
  assert.match(checklistHelpResult.stdout, /--owner-packets/);
  assert.match(checklistHelpResult.stdout, /--list-owners/);
  assert.match(checklistHelpResult.stdout, /--summary/);
  assert.match(checklistHelpResult.stdout, /--dispatch-check/);
  assert.match(checklistHelpResult.stdout, /--rollup/);
  assert.match(checklistHelpResult.stdout, /--rollup-markdown/);
  assert.match(checklistHelpResult.stdout, /--rollup-enforce/);
  assert.match(checklistHelpResult.stdout, /--commands/);
  assert.match(checklistHelpResult.stdout, /--evidence-gaps/);
  assert.match(checklistHelpResult.stdout, /--evidence-runbook/);
  assert.match(checklistHelpResult.stdout, /--evidence-acceptance/);
  assert.match(checklistHelpResult.stdout, /--evidence-artifact-gap-report/);
  assert.match(checklistHelpResult.stdout, /--explain-artifact-plan/);
  assert.match(checklistHelpResult.stdout, /--closure-plan/);
  assert.match(checklistHelpResult.stdout, /--next-action-queue/);
  assert.match(checklistHelpResult.stdout, /--owner-lane-matrix/);
  assert.match(checklistHelpResult.stdout, /--lane-completion-receipt-template/);
  assert.match(checklistHelpResult.stdout, /--lane-completion-receipt-init/);
  assert.match(checklistHelpResult.stdout, /--lane-completion-receipt-contract/);
  assert.match(checklistHelpResult.stdout, /--lane-completion-receipt-coverage/);
  assert.match(checklistHelpResult.stdout, /--evidence-closure-board/);
  assert.match(checklistHelpResult.stdout, /--evidence-closure-board-csv/);
  assert.match(checklistHelpResult.stdout, /--lane-receipt-fragments/);
  assert.match(checklistHelpResult.stdout, /--lane-receipt-draft/);
  assert.match(checklistHelpResult.stdout, /--owner-evidence-intake/);
  assert.match(checklistHelpResult.stdout, /--lane-completion-submission-plan/);
  assert.match(checklistHelpResult.stdout, /--lane-completion-submission-check/);
  assert.match(checklistHelpResult.stdout, /--next-action-env-template/);
  assert.match(checklistHelpResult.stdout, /--next-action-env-check/);
  assert.match(checklistHelpResult.stdout, /--next-action-env-receipt/);
  assert.match(checklistHelpResult.stdout, /--next-action-env-receipt-contract/);
  assert.match(checklistHelpResult.stdout, /--next-action-env-receipt-file/);
  assert.match(checklistHelpResult.stdout, /--next-action-env-receipt-output/);
  assert.match(checklistHelpResult.stdout, /--next-action-env-file/);
  assert.match(checklistHelpResult.stdout, /--next-action-verification-plan/);
  assert.match(checklistHelpResult.stdout, /--release-env-plan/);
  assert.match(checklistHelpResult.stdout, /--release-env-owner-matrix/);
  assert.match(checklistHelpResult.stdout, /--release-env-next-owner-template/);
  assert.match(checklistHelpResult.stdout, /--release-env-merge-plan/);
  assert.match(checklistHelpResult.stdout, /--release-env-submission-plan/);
  assert.match(checklistHelpResult.stdout, /--docker-image-plan/);
  assert.match(checklistHelpResult.stdout, /--docker-image-submission-plan/);
  assert.match(checklistHelpResult.stdout, /--runtime-business-plan/);
  assert.match(checklistHelpResult.stdout, /--runtime-smoke-plan/);
  assert.match(checklistHelpResult.stdout, /--runtime-business-submission-plan/);
  assert.match(checklistHelpResult.stdout, /--data-safety-plan/);
  assert.match(checklistHelpResult.stdout, /--data-safety-owner-plan/);
  assert.match(checklistHelpResult.stdout, /--data-safety-submission-plan/);
  assert.match(checklistHelpResult.stdout, /--cutover-rehearsal-plan/);
  assert.match(checklistHelpResult.stdout, /--blocking-inputs/);
  assert.match(checklistHelpResult.stdout, /--release-evidence-dispatch-plan/);
  assert.match(checklistHelpResult.stdout, /--release-evidence-dispatch-inputs/);
  assert.match(checklistHelpResult.stdout, /--release-evidence-dispatch-inputs-contract/);
  assert.match(checklistHelpResult.stdout, /--evidence-env-template/);
  assert.match(checklistHelpResult.stdout, /--handoff-bundle/);
  assert.match(checklistHelpResult.stdout, /--handoff-bundle-verify/);
  assert.match(checklistHelpResult.stdout, /--execution-status/);
  assert.match(checklistHelpResult.stdout, /--handoff-summary-markdown/);
  assert.match(checklistHelpResult.stdout, /--release-owner-closeout/);
  assert.match(checklistHelpResult.stdout, /--production-closeout-status/);
  assert.match(checklistHelpResult.stdout, /--production-unblock-plan/);
  assert.match(checklistHelpResult.stdout, /--production-evidence-readiness/);
  assert.match(checklistHelpResult.stdout, /--production-evidence-readiness-enforce/);
  assert.match(checklistHelpResult.stdout, /--production-cutover-audit/);
  assert.match(checklistHelpResult.stdout, /--final-review/);
  assert.match(checklistHelpResult.stdout, /--final-review-markdown/);
  assert.match(checklistHelpResult.stdout, /--final-review-enforce/);
  assert.match(checklistHelpResult.stdout, /--operator-progress/);
  assert.match(checklistHelpResult.stdout, /--release-owner-daily-brief/);
  assert.match(checklistHelpResult.stdout, /DDD_STAGING_CHECKLIST_OUTPUT/);
  assert.match(checklistHelpResult.stdout, /DDD_STAGING_HANDOFF_BUNDLE_DIR/);
  assert.equal(fs.existsSync(`${helpOutputBase}.json`), false, "checklist help should not write JSON");

  const envInitHelpResult = spawnSyncWithTimeout("node", ["scripts/ddd-release-env-init.mjs", "--help"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(envInitHelpResult.status, 0, envInitHelpResult.stderr || envInitHelpResult.stdout);
  assert.match(envInitHelpResult.stdout, /DDD release env initializer/);
  assert.match(envInitHelpResult.stdout, /--check/);
  assert.match(envInitHelpResult.stdout, /DDD_FINAL_OWNER_QUEUE_ENV_TARGET/);

  const runtimeCheckHelpResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-runtime-check.mjs", "--help"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(runtimeCheckHelpResult.status, 0, runtimeCheckHelpResult.stderr || runtimeCheckHelpResult.stdout);
  assert.match(runtimeCheckHelpResult.stdout, /DDD staging runtime readiness check/);
  assert.match(runtimeCheckHelpResult.stdout, /PLAYWRIGHT_BASE_URL/);

  const dataSafetyCheckHelpResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-data-safety-check.mjs", "--help"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(dataSafetyCheckHelpResult.status, 0, dataSafetyCheckHelpResult.stderr || dataSafetyCheckHelpResult.stdout);
  assert.match(dataSafetyCheckHelpResult.stdout, /DDD staging data safety check/);
  assert.match(dataSafetyCheckHelpResult.stdout, /DDD_EXPLAIN_DATABASE/);

  const envInitCheckResult = spawnSyncWithTimeout("node", ["scripts/ddd-release-env-init.mjs", "--check"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_FINAL_OWNER_QUEUE_ENV_TARGET: `tmp/staging-checklist-env-init-check-${process.pid}.env`,
      DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT: `tmp/staging-checklist-env-init-check-${process.pid}-receipt.json`,
    },
  });
  assert.equal(envInitCheckResult.status, 0, envInitCheckResult.stderr || envInitCheckResult.stdout);
  const envInitCheck = JSON.parse(envInitCheckResult.stdout);
  assert.equal(envInitCheck.status, "PASS");
  assert.equal(envInitCheck.willWriteFiles, false);
  assert.equal(envInitCheck.initializerExists, true);
  assert.match(envInitCheck.nextCommand, /node scripts\/ddd-release-env-init\.mjs/);
  assert.equal(fs.existsSync(path.join(repoRoot, `tmp/staging-checklist-env-init-check-${process.pid}.env`)), false);

  const dispatchCheckResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--dispatch-check"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-dispatch-check"),
    },
  });
  assert.equal(dispatchCheckResult.status, 0, dispatchCheckResult.stderr || dispatchCheckResult.stdout);
  const dispatchCheck = JSON.parse(dispatchCheckResult.stdout);
  assert.equal(dispatchCheck.status, "PASS");
  assert.equal(dispatchCheck.willWriteFiles, false);
  assert.equal(dispatchCheck.cutoverAllowed, false);
  assert.equal(dispatchCheck.ownerCount, 5);
  assert.equal(dispatchCheck.releaseEnvCheck.status, "BLOCKED");
  assert.equal(dispatchCheck.releaseEnvCheck.ready, false);
  assert.equal(dispatchCheck.releaseEnvCheck.nextCommand, "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs");
  assert.equal(dispatchCheck.envInitCheck.status, "PASS");
  assert.equal(dispatchCheck.envInitCheck.willWriteFiles, false);
  assert.equal(dispatchCheck.envInitCheck.target, "tmp/ddd-dispatch-check-env-init.env");
  assert.equal(dispatchCheck.dockerEvidenceCheck.willWriteFiles, false);
  assert(["PASS", "BLOCKED"].includes(dispatchCheck.dockerEvidenceCheck.status));
  assert.match(dispatchCheck.dockerEvidenceCheck.nextCommand, /ddd-docker-build-evidence\.mjs/);
  assert.equal(dispatchCheck.runtimeStagingCheck.willWriteFiles, false);
  assert(["PASS", "BLOCKED"].includes(dispatchCheck.runtimeStagingCheck.status));
  assert(dispatchCheck.runtimeStagingCheck.nextCommands.includes("node scripts/ddd-runtime-readiness-smoke.mjs"));
  assert.equal(dispatchCheck.dataSafetyCheck.willWriteFiles, false);
  assert(["PASS", "BLOCKED"].includes(dispatchCheck.dataSafetyCheck.status));
  assert(dispatchCheck.dataSafetyCheck.tracks.rollback.nextCommands.includes("DDD_ROLLBACK_DRILL_STRICT=true node scripts/ddd-rollback-drill-evidence.mjs"));
  assert.equal(dispatchCheck.readinessRollupCommand, "node scripts/ddd-staging-execution-checklist.mjs --rollup");
  assert.equal(dispatchCheck.copyReadyCommand, "node scripts/ddd-staging-execution-checklist.mjs --commands");
  assert(dispatchCheck.expectedOwners.includes("release-infra"));
  assert(dispatchCheck.availableOwners.includes("platform-events"));
  assert(dispatchCheck.blockedTracks.includes("p0-release-env"));
  assert(dispatchCheck.nextCommands.includes("node scripts/ddd-release-env-init.mjs --check"));
  assert(dispatchCheck.nextCommands.includes("node scripts/ddd-docker-build-evidence.mjs --check"));
  assert(dispatchCheck.nextCommands.includes("node scripts/ddd-staging-runtime-check.mjs"));
  assert(dispatchCheck.nextCommands.includes("node scripts/ddd-staging-data-safety-check.mjs"));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-dispatch-check.json")), false);

  const rollupResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--rollup"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-rollup"),
    },
  });
  assert.equal(rollupResult.status, 0, rollupResult.stderr || rollupResult.stdout);
  const rollup = JSON.parse(rollupResult.stdout);
  assert.equal(rollup.status, "BLOCKED");
  assert.equal(rollup.willWriteFiles, false);
  assert.equal(rollup.cutoverAllowed, false);
  assert.equal(rollup.items.length, 6);
  assert.equal(rollup.blockedCount, 5);
  assert(rollup.items.some((item) => item.id === "release-env" && item.status === "BLOCKED" && item.nextCommand === "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs"));
  assert(rollup.items.some((item) => item.id === "docker-images" && item.track === "p0-images"));
  assert(rollup.items.some((item) => item.id === "docker-images" && ["PASS", "BLOCKED"].includes(item.status)));
  assert(rollup.items.some((item) => item.id === "runtime-business" && item.blockingInputs.includes("LUMIRA_BASE_URL") && item.blockingInputs.includes("DDD_DEPLOYMENT_EVIDENCE")));
  assert(rollup.items.some((item) => item.id === "rollback" && item.blockingInputs.includes("DDD_ROLLBACK_DRILL_FILE")));
  assert(rollup.items.some((item) => item.id === "explain" && item.blockingInputs.includes("MYSQL_HOST")));
  assert(rollup.items.some((item) => item.id === "migration" && item.nextCommand === "node scripts/ddd-staging-data-safety-check.mjs"));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-rollup.json")), false);

  const dynamicEnvRollupResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--rollup"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_ENV_FILE: path.join(tmpDir, "missing-release.env"),
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-rollup-dynamic-env"),
    },
  });
  assert.equal(dynamicEnvRollupResult.status, 0, dynamicEnvRollupResult.stderr || dynamicEnvRollupResult.stdout);
  const dynamicEnvRollup = JSON.parse(dynamicEnvRollupResult.stdout);
  assert(dynamicEnvRollup.items.some((item) => item.id === "release-env" && item.issue.includes("env file does not exist")));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-rollup-dynamic-env.json")), false);

  const rollupMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--rollup-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-rollup-markdown"),
    },
  });
  assert.equal(rollupMarkdownResult.status, 0, rollupMarkdownResult.stderr || rollupMarkdownResult.stdout);
  assert.match(rollupMarkdownResult.stdout, /^# DDD Staging Readiness Rollup/m);
  assert.match(rollupMarkdownResult.stdout, /\| docker-images \| p0-images \| release-infra \|/);
  assert.match(rollupMarkdownResult.stdout, /`LUMIRA_BASE_URL`/);
  assert.match(rollupMarkdownResult.stdout, /Next: `node scripts\/ddd-staging-execution-checklist\.mjs --commands`/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-rollup-markdown.json")), false);

  const rollupEnforceResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--rollup-enforce"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-rollup-enforce"),
    },
  });
  assert.notEqual(rollupEnforceResult.status, 0, "rollup enforce should fail while staging gates are blocked");
  const rollupEnforce = JSON.parse(rollupEnforceResult.stdout);
  assert.equal(rollupEnforce.status, "BLOCKED");
  assert.equal(rollupEnforce.willWriteFiles, false);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-rollup-enforce.json")), false);

  const commandsResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--commands"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-commands"),
    },
  });
  assert.equal(commandsResult.status, 0, commandsResult.stderr || commandsResult.stdout);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-production-readiness-preflight\.mjs --quick --no-report --list/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --dispatch-check/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --rollup/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --rollup-markdown/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --rollup-enforce/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --evidence-gaps/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --evidence-runbook$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --evidence-runbook-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --evidence-acceptance$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --evidence-acceptance-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --evidence-artifact-gap-report$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --evidence-artifact-gap-report-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --explain-artifact-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --explain-artifact-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --closure-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --closure-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --next-action-queue$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --next-action-queue-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --owner-lane-matrix$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --owner-lane-matrix-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-completion-receipt-template$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-completion-receipt-template-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-lane-completion-receipt-autofill\.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-completion-receipt-coverage --lane-completion-receipt-file=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --evidence-closure-board$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --evidence-closure-board-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --evidence-closure-board-markdown --lane-completion-receipt-file=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --evidence-closure-board-csv$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --evidence-closure-board-csv --lane-completion-receipt-file=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-receipt-fragments$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-receipt-fragments-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-receipt-draft$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-receipt-draft-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --owner-evidence-intake$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --owner-evidence-intake-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-completion-submission-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-completion-submission-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --lane-completion-submission-check-markdown --lane-completion-receipt-file=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --next-action-env-template$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --next-action-env-check --next-action-env-file=<env-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --next-action-env-receipt --next-action-env-file=<env-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --next-action-env-receipt --next-action-env-file=<env-file> --next-action-env-receipt-output=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --next-action-env-receipt-markdown --next-action-env-file=<env-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --next-action-verification-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --next-action-verification-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-env-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-env-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-env-owner-matrix$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-env-owner-matrix-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-env-next-owner-template$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-env-merge-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-env-merge-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-env-submission-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-env-submission-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-release-env-fill-checklist\.mjs$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-release-env-fill-checklist\.mjs --markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-release-env-fill-checklist\.mjs --env-template$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --docker-image-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --docker-image-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --docker-image-submission-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --docker-image-submission-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --runtime-business-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --runtime-business-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --runtime-smoke-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --runtime-smoke-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --runtime-business-submission-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --runtime-business-submission-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --data-safety-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --data-safety-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --data-safety-owner-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --data-safety-owner-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --data-safety-submission-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --data-safety-submission-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --cutover-rehearsal-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --cutover-rehearsal-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --blocking-inputs$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --blocking-inputs-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --blocking-inputs-env-template$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-evidence-dispatch-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-evidence-dispatch-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-evidence-dispatch-inputs$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-evidence-dispatch-command$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-evidence-dispatch-inputs-contract --release-evidence-dispatch-inputs-file=<inputs-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --execution-status$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --execution-status-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --handoff-summary-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-owner-closeout$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-owner-closeout-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-owner-closeout-markdown --lane-completion-receipt-file=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --production-closeout-status$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --production-closeout-status-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --production-unblock-plan$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --production-unblock-plan-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --production-evidence-readiness$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --production-evidence-readiness-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --production-evidence-readiness-enforce$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --production-cutover-audit$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --production-cutover-audit-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --final-review$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --final-review-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --final-review-enforce$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --operator-progress$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --operator-progress-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --operator-progress-markdown --next-action-env-receipt-file=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-owner-daily-brief$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --release-owner-daily-brief-markdown$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --evidence-env-template/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --handoff-bundle$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --handoff-bundle-verify$/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-release-env-init\.mjs --check/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-docker-build-evidence\.mjs --check/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-runtime-check\.mjs/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-data-safety-check\.mjs/m);
  assert.match(commandsResult.stdout, /^node scripts\/ddd-staging-execution-checklist\.mjs --owner-packets/m);
  assert.doesNotMatch(commandsResult.stdout, /\[ddd-staging-execution-checklist\]/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-commands.json")), false);

  const releaseOwnerCloseoutResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-owner-closeout"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-owner-closeout"),
    },
  });
  assert.equal(releaseOwnerCloseoutResult.status, 0, releaseOwnerCloseoutResult.stderr || releaseOwnerCloseoutResult.stdout);
  const releaseOwnerCloseout = JSON.parse(releaseOwnerCloseoutResult.stdout);
  assert.equal(releaseOwnerCloseout.status, "BLOCKED");
  assert.equal(releaseOwnerCloseout.finalRecommendation, "NO_GO_STRICT");
  assert.equal(releaseOwnerCloseout.cutoverReady, false);
  assert.equal(releaseOwnerCloseout.evidenceClosure.nextLane.key, "platform-owners:p1-p2-data-safety");
  assert.equal(releaseOwnerCloseout.evidenceClosure.closed, "0/5");
  assert(releaseOwnerCloseout.requiredCommandSequence.some((command) => command.includes("--final-review-enforce")));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-owner-closeout.json")), false);

  const releaseOwnerCloseoutMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-owner-closeout-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-owner-closeout-markdown"),
    },
  });
  assert.equal(releaseOwnerCloseoutMarkdownResult.status, 0, releaseOwnerCloseoutMarkdownResult.stderr || releaseOwnerCloseoutMarkdownResult.stdout);
  assert.match(releaseOwnerCloseoutMarkdownResult.stdout, /^# DDD Release Owner Closeout/m);
  assert.match(releaseOwnerCloseoutMarkdownResult.stdout, /^## Immediate Next Lane/m);
  assert.match(releaseOwnerCloseoutMarkdownResult.stdout, /^## Required Command Sequence/m);
  assert.match(releaseOwnerCloseoutMarkdownResult.stdout, /Final recommendation: NO_GO_STRICT/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-owner-closeout-markdown.json")), false);

  const evidenceEnvTemplateResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--evidence-env-template"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-evidence-env-template"),
    },
  });
  assert.equal(evidenceEnvTemplateResult.status, 0, evidenceEnvTemplateResult.stderr || evidenceEnvTemplateResult.stdout);
  assert.match(evidenceEnvTemplateResult.stdout, /^# Lumira DDD staging evidence environment template\./);
  assert.match(evidenceEnvTemplateResult.stdout, /^DDD_DOCKER_BUILD_STRICT=true/m);
  assert.match(evidenceEnvTemplateResult.stdout, /^LUMIRA_BASE_URL=__REQUIRED_HTTPS__/m);
  assert.match(evidenceEnvTemplateResult.stdout, /^DDD_MIGRATION_FRESH_DB_VALIDATED=true/m);
  assert.match(evidenceEnvTemplateResult.stdout, /^DDD_EXPLAIN_DATABASE=__REQUIRED__/m);
  assert.match(evidenceEnvTemplateResult.stdout, /node scripts\/ddd-staging-data-safety-check\.mjs/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-evidence-env-template.json")), false);

  const evidenceRunbookResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--evidence-runbook"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-evidence-runbook"),
    },
  });
  assert.equal(evidenceRunbookResult.status, 0, evidenceRunbookResult.stderr || evidenceRunbookResult.stdout);
  const evidenceRunbook = JSON.parse(evidenceRunbookResult.stdout);
  assert.equal(evidenceRunbook.status, "STAGING_REQUIRED");
  assert.equal(evidenceRunbook.willWriteFiles, false);
  assert.equal(evidenceRunbook.cutoverAllowed, false);
  assert.equal(evidenceRunbook.trackCount, 6);
  assert.equal(evidenceRunbook.blockedTrackCount, 6);
  assert(evidenceRunbook.tracks.some((track) => track.id === "p0-images" && track.nextCommand === "node scripts/ddd-docker-build-evidence.mjs --check"));
  assert(evidenceRunbook.tracks.some((track) => track.id === "p1-runtime-business" && track.envKeys.includes("LUMIRA_BASE_URL")));
  assert(evidenceRunbook.tracks.some((track) => track.id === "p2-database-performance" && track.artifacts.includes("artifacts/ddd/release/explain-gate-report.json")));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-evidence-runbook.json")), false);

  const evidenceRunbookMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--evidence-runbook-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-evidence-runbook-markdown"),
    },
  });
  assert.equal(evidenceRunbookMarkdownResult.status, 0, evidenceRunbookMarkdownResult.stderr || evidenceRunbookMarkdownResult.stdout);
  assert.match(evidenceRunbookMarkdownResult.stdout, /^# DDD Staging Evidence Runbook/m);
  assert.match(evidenceRunbookMarkdownResult.stdout, /## 2\. P0 deployable images/);
  assert.match(evidenceRunbookMarkdownResult.stdout, /`node scripts\/ddd-docker-build-evidence\.mjs --check`/);
  assert.match(evidenceRunbookMarkdownResult.stdout, /`LUMIRA_BASE_URL`/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-evidence-runbook-markdown.json")), false);

  const evidenceAcceptanceResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--evidence-acceptance"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-evidence-acceptance"),
    },
  });
  assert.equal(evidenceAcceptanceResult.status, 0, evidenceAcceptanceResult.stderr || evidenceAcceptanceResult.stdout);
  const evidenceAcceptance = JSON.parse(evidenceAcceptanceResult.stdout);
  assert.equal(evidenceAcceptance.status, "BLOCKED");
  assert.equal(evidenceAcceptance.willWriteFiles, false);
  assert.equal(evidenceAcceptance.cutoverAllowed, false);
  assert.equal(evidenceAcceptance.itemCount, 6);
  assert.equal(evidenceAcceptance.acceptedCount, 1);
  assert.equal(evidenceAcceptance.blockedCount, 5);
  assert(evidenceAcceptance.missingArtifactCount > 0);
  assert(evidenceAcceptance.items.some((item) => item.gate === "release-env" && item.accepted === false && item.acceptanceCommand === "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs"));
  assert(evidenceAcceptance.items.some((item) => item.gate === "runtime-business" && item.blockingInputs.includes("PLAYWRIGHT_BASE_URL")));
  assert(evidenceAcceptance.items.some((item) => item.gate === "runtime-business" && item.acceptanceCommand === "node scripts/ddd-staging-runtime-check.mjs"));
  assert(evidenceAcceptance.items.some((item) => item.gate === "explain" && item.expectedArtifacts.includes("artifacts/ddd/release/explain-gate-report.json")));
  assert(evidenceAcceptance.items.some((item) => item.gate === "explain" && item.artifactChecks.some((artifact) => artifact.artifact === "artifacts/ddd/release/explain-gate-report.json" && typeof artifact.present === "boolean")));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-evidence-acceptance.json")), false);

  const evidenceAcceptanceMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--evidence-acceptance-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-evidence-acceptance-markdown"),
    },
  });
  assert.equal(evidenceAcceptanceMarkdownResult.status, 0, evidenceAcceptanceMarkdownResult.stderr || evidenceAcceptanceMarkdownResult.stdout);
  assert.match(evidenceAcceptanceMarkdownResult.stdout, /^# DDD Staging Evidence Acceptance/m);
  assert.match(evidenceAcceptanceMarkdownResult.stdout, /\| runtime-business \| release-infra, frontend, ai, file-owner, job-owner, payment-owner \| no \|/);
  assert.match(evidenceAcceptanceMarkdownResult.stdout, /Artifacts missing: \d+/);
  assert.match(evidenceAcceptanceMarkdownResult.stdout, /`DDD_FRONTEND_EXPECT_DEPLOYED`/);
  assert.match(evidenceAcceptanceMarkdownResult.stdout, /(present|missing): `artifacts\/ddd\/release\/explain-gate-report\.json`/);
  assert.match(evidenceAcceptanceMarkdownResult.stdout, /`artifacts\/ddd\/frontend\/frontend-smoke\.json`/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-evidence-acceptance-markdown.json")), false);

  const evidenceArtifactGapReportResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--evidence-artifact-gap-report"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-evidence-artifact-gap-report"),
    },
  });
  assert.equal(evidenceArtifactGapReportResult.status, 0, evidenceArtifactGapReportResult.stderr || evidenceArtifactGapReportResult.stdout);
  const evidenceArtifactGapReport = JSON.parse(evidenceArtifactGapReportResult.stdout);
  assert.equal(evidenceArtifactGapReport.status, "BLOCKED");
  assert(evidenceArtifactGapReport.missingArtifactCount > 0);
  assert(evidenceArtifactGapReport.missingArtifacts.some((artifact) => artifact.artifact === "tmp/ddd-explain/*.json" && artifact.dispatchOwners.includes("platform-owners")));
  assert(evidenceArtifactGapReport.missingArtifacts.some((artifact) => artifact.gates.some((gate) => gate.owner === "database" && gate.dispatchOwner === "platform-owners")));
  assert(evidenceArtifactGapReport.presentArtifacts.some((artifact) => artifact.artifact === "artifacts/ddd/release/explain-gate-report.json" && artifact.present === true));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-evidence-artifact-gap-report.json")), false);

  const evidenceArtifactGapReportMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--evidence-artifact-gap-report-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-evidence-artifact-gap-report-markdown"),
    },
  });
  assert.equal(evidenceArtifactGapReportMarkdownResult.status, 0, evidenceArtifactGapReportMarkdownResult.stderr || evidenceArtifactGapReportMarkdownResult.stdout);
  assert.match(evidenceArtifactGapReportMarkdownResult.stdout, /^# DDD Evidence Artifact Gap Report/m);
  assert.match(evidenceArtifactGapReportMarkdownResult.stdout, /tmp\/ddd-explain\/\*\.json/);
  assert.match(evidenceArtifactGapReportMarkdownResult.stdout, /platform-owners/);
  assert.match(evidenceArtifactGapReportMarkdownResult.stdout, /## Present Artifacts/);

  const explainArtifactPlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--explain-artifact-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-explain-artifact-plan"),
    },
  });
  assert.equal(explainArtifactPlanResult.status, 0, explainArtifactPlanResult.stderr || explainArtifactPlanResult.stdout);
  const explainArtifactPlan = JSON.parse(explainArtifactPlanResult.stdout);
  assert.equal(explainArtifactPlan.status, "BLOCKED");
  assert.equal(explainArtifactPlan.willWriteFiles, false);
  assert.equal(explainArtifactPlan.missingArtifact, "tmp/ddd-explain/*.json");
  assert.equal(explainArtifactPlan.artifactPresent, false);
  assert(explainArtifactPlan.dispatchOwners.includes("platform-owners"));
  assert(explainArtifactPlan.sourceOwners.includes("database"));
  assert(explainArtifactPlan.requiredInputs.includes("DDD_EXPLAIN_DATABASE"));
  assert(explainArtifactPlan.requiredInputs.includes("MYSQL_HOST"));
  assert(explainArtifactPlan.expectedArtifacts.includes("tmp/ddd-explain/*.json"));
  assert(explainArtifactPlan.commands.includes("node scripts/ddd-collect-explain.mjs"));
  assert(explainArtifactPlan.commands.includes("DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs"));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-explain-artifact-plan.json")), false);

  const explainArtifactPlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--explain-artifact-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-explain-artifact-plan-markdown"),
    },
  });
  assert.equal(explainArtifactPlanMarkdownResult.status, 0, explainArtifactPlanMarkdownResult.stderr || explainArtifactPlanMarkdownResult.stdout);
  assert.match(explainArtifactPlanMarkdownResult.stdout, /^# DDD EXPLAIN Artifact Plan/m);
  assert.match(explainArtifactPlanMarkdownResult.stdout, /tmp\/ddd-explain\/\*\.json/);
  assert.match(explainArtifactPlanMarkdownResult.stdout, /DDD_EXPLAIN_DATABASE=__REQUIRED__/);
  assert.match(explainArtifactPlanMarkdownResult.stdout, /node scripts\/ddd-collect-explain\.mjs/);

  const closurePlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--closure-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-closure-plan"),
    },
  });
  assert.equal(closurePlanResult.status, 0, closurePlanResult.stderr || closurePlanResult.stdout);
  const closurePlan = JSON.parse(closurePlanResult.stdout);
  assert.equal(closurePlan.status, "BLOCKED");
  assert.equal(closurePlan.willWriteFiles, false);
  assert.equal(closurePlan.cutoverReady, false);
  assert.equal(closurePlan.blockedGateCount, 5);
  assert.match(closurePlan.eta, /0\.5-1\.5d/);
  assert(closurePlan.items.some((item) => item.gate === "release-env" && item.phase === "P0" && item.owner === "release-infra"));
  assert(closurePlan.items.some((item) => item.gate === "runtime-business" && item.parallelGroup === "runtime-validation"));
  assert(closurePlan.topBlockingInputs.some((input) => input.input === "DDD_EVIDENCE_ENVIRONMENT"));
  assert(closurePlan.verificationCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce"));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-closure-plan.json")), false);

  const closurePlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--closure-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-closure-plan-markdown"),
    },
  });
  assert.equal(closurePlanMarkdownResult.status, 0, closurePlanMarkdownResult.stderr || closurePlanMarkdownResult.stdout);
  assert.match(closurePlanMarkdownResult.stdout, /^# DDD Staging Closure Plan/m);
  assert.match(closurePlanMarkdownResult.stdout, /Critical Path/);
  assert.match(closurePlanMarkdownResult.stdout, /`node scripts\/ddd-staging-execution-checklist\.mjs --final-review-enforce`/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-closure-plan-markdown.json")), false);

  const nextActionQueueResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--next-action-queue"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-next-action-queue"),
    },
  });
  assert.equal(nextActionQueueResult.status, 0, nextActionQueueResult.stderr || nextActionQueueResult.stdout);
  const nextActionQueue = JSON.parse(nextActionQueueResult.stdout);
  assert.equal(nextActionQueue.status, "BLOCKED");
  assert.equal(nextActionQueue.willWriteFiles, false);
  assert.equal(nextActionQueue.queue.length, 5);
  assert(nextActionQueue.queue.some((item) => item.lane === "p0-release-env" && item.sourcePlan === "release-env-plan.json"));
  assert(nextActionQueue.queue.some((item) => item.lane === "p1-runtime-business" && item.sourcePlan === "runtime-business-submission-plan.json"));
  assert(nextActionQueue.queue.some((item) => item.lane === "p1-runtime-business" && item.command === "node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown"));
  assert(nextActionQueue.queue.some((item) => item.lane === "p1-runtime-business" && item.artifacts.includes("artifacts/ddd/release/staging-handoff-bundle/runtime-business-submission-plan.md")));
  assert(nextActionQueue.queue.some((item) => item.lane === "p1-p2-data-safety" && item.sourcePlan === "data-safety-submission-plan.json"));
  assert(nextActionQueue.queue.some((item) => item.lane === "p1-p2-data-safety" && item.command === "node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown"));
  assert(nextActionQueue.queue.some((item) => item.lane === "p1-p2-data-safety" && item.artifacts.includes("artifacts/ddd/release/staging-handoff-bundle/data-safety-submission-plan.md")));
  assert(nextActionQueue.queue.some((item) => item.lane === "p1-p2-data-safety" && item.dispatchOwner === "platform-owners" && item.missingEvidenceArtifactCount > 0));
  assert(nextActionQueue.queue.some((item) => item.lane === "p1-p2-data-safety" && item.missingEvidenceArtifacts.some((artifact) => artifact.artifact === "tmp/ddd-explain/*.json")));
  assert(nextActionQueue.queue.some((item) => item.lane === "p1-p2-data-safety" && item.artifactPlanCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --explain-artifact-plan-markdown")));
  assert(nextActionQueue.queue.some((item) => item.lane === "p1-p2-data-safety" && item.artifactPlanCommands.includes("node scripts/ddd-collect-explain.mjs")));
  assert(nextActionQueue.queue.some((item) => item.lane === "p0-docker-images" && ["PASS", "BLOCKED"].includes(item.status)));
  assert(nextActionQueue.parallelNow.includes("p0-release-env"));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-next-action-queue.json")), false);

  const nextActionQueueMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--next-action-queue-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-next-action-queue-markdown"),
    },
  });
  assert.equal(nextActionQueueMarkdownResult.status, 0, nextActionQueueMarkdownResult.stderr || nextActionQueueMarkdownResult.stdout);
  assert.match(nextActionQueueMarkdownResult.stdout, /^# DDD Staging Next Action Queue/m);
  assert.match(nextActionQueueMarkdownResult.stdout, /Dispatch owner/);
  assert.match(nextActionQueueMarkdownResult.stdout, /p0-release-env/);
  assert.match(nextActionQueueMarkdownResult.stdout, /platform-owners/);
  assert.match(nextActionQueueMarkdownResult.stdout, /tmp\/ddd-explain\/\*\.json/);
  assert.match(nextActionQueueMarkdownResult.stdout, /--explain-artifact-plan-markdown/);
  assert.match(nextActionQueueMarkdownResult.stdout, /data-safety-submission-plan\.json/);
  assert.match(nextActionQueueMarkdownResult.stdout, /runtime-business-submission-plan\.json/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-next-action-queue-markdown.json")), false);

  const ownerLaneMatrixResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--owner-lane-matrix"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-owner-lane-matrix"),
    },
  });
  assert.equal(ownerLaneMatrixResult.status, 0, ownerLaneMatrixResult.stderr || ownerLaneMatrixResult.stdout);
  const ownerLaneMatrix = JSON.parse(ownerLaneMatrixResult.stdout);
  assert.equal(ownerLaneMatrix.status, "BLOCKED");
  assert(ownerLaneMatrix.owners.some((owner) => owner.owner === "platform-owners" && owner.lanes.some((lane) => lane.lane === "p1-p2-data-safety")));
  assert(ownerLaneMatrix.owners.some((owner) => owner.owner === "platform-owners" && owner.lanes.some((lane) => lane.acceptanceCommands.includes("node scripts/ddd-staging-data-safety-check.mjs"))));
  assert(ownerLaneMatrix.owners.some((owner) => owner.owner === "platform-owners" && owner.lanes.some((lane) => lane.missingArtifacts.includes("tmp/ddd-explain/*.json"))));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-owner-lane-matrix.json")), false);

  const ownerLaneMatrixMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--owner-lane-matrix-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-owner-lane-matrix-markdown"),
    },
  });
  assert.equal(ownerLaneMatrixMarkdownResult.status, 0, ownerLaneMatrixMarkdownResult.stderr || ownerLaneMatrixMarkdownResult.stdout);
  assert.match(ownerLaneMatrixMarkdownResult.stdout, /^# DDD Owner Lane Matrix/m);
  assert.match(ownerLaneMatrixMarkdownResult.stdout, /platform-owners/);
  assert.match(ownerLaneMatrixMarkdownResult.stdout, /p1-p2-data-safety/);
  assert.match(ownerLaneMatrixMarkdownResult.stdout, /ddd-staging-data-safety-check\.mjs/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-owner-lane-matrix-markdown.json")), false);

  const laneCompletionReceiptTemplateResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--lane-completion-receipt-template"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(laneCompletionReceiptTemplateResult.status, 0, laneCompletionReceiptTemplateResult.stderr || laneCompletionReceiptTemplateResult.stdout);
  const laneCompletionReceiptTemplate = JSON.parse(laneCompletionReceiptTemplateResult.stdout);
  assert.equal(laneCompletionReceiptTemplate.redacted, true);
  assert(laneCompletionReceiptTemplate.laneReceipts.some((lane) => lane.lane === "p1-p2-data-safety" && lane.acceptanceCommands.includes("node scripts/ddd-staging-data-safety-check.mjs")));

  const laneCompletionReceiptTemplateMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--lane-completion-receipt-template-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(laneCompletionReceiptTemplateMarkdownResult.status, 0, laneCompletionReceiptTemplateMarkdownResult.stderr || laneCompletionReceiptTemplateMarkdownResult.stdout);
  assert.match(laneCompletionReceiptTemplateMarkdownResult.stdout, /^# DDD Lane Completion Receipt/m);
  assert.match(laneCompletionReceiptTemplateMarkdownResult.stdout, /p1-p2-data-safety/);
  assert.match(laneCompletionReceiptTemplateMarkdownResult.stdout, /\| Lane \| Owner \| Status \| Provided artifacts \| Missing artifacts \| Completed at \| Completed by \| Acceptance commands \|/);
  assert.match(laneCompletionReceiptTemplateMarkdownResult.stdout, /## Fill Rules/);
  assert.match(laneCompletionReceiptTemplateMarkdownResult.stdout, /## Edit Checklist/);
  assert.match(laneCompletionReceiptTemplateMarkdownResult.stdout, /laneReceipts\[0\]/);
  assert.match(laneCompletionReceiptTemplateMarkdownResult.stdout, /required when PASS/);
  assert.match(laneCompletionReceiptTemplateMarkdownResult.stdout, /## Lane Details/);
  assert.match(laneCompletionReceiptTemplateMarkdownResult.stdout, /Expected artifacts:/);

  const initializedLaneCompletionReceiptFile = path.join(tmpDir, "initialized-lane-completion-receipt.json");
  const laneCompletionReceiptInitResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--lane-completion-receipt-init",
    `--lane-completion-receipt-output=${initializedLaneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(laneCompletionReceiptInitResult.status, 0, laneCompletionReceiptInitResult.stderr || laneCompletionReceiptInitResult.stdout);
  const laneCompletionReceiptInit = JSON.parse(laneCompletionReceiptInitResult.stdout);
  assert.equal(laneCompletionReceiptInit.status, "PASS");
  assert.equal(laneCompletionReceiptInit.willWriteFiles, true);
  assert.equal(laneCompletionReceiptInit.redacted, true);
  assert.equal(laneCompletionReceiptInit.contract.status, "PASS");
  assert.equal(laneCompletionReceiptInit.contract.receiptStatus, "BLOCKED");
  assert.equal(fs.existsSync(initializedLaneCompletionReceiptFile), true, "receipt init should write the output file");
  const initializedLaneCompletionReceipt = JSON.parse(fs.readFileSync(initializedLaneCompletionReceiptFile, "utf8"));
  assert.equal(initializedLaneCompletionReceipt.redacted, true);
  assert.equal(initializedLaneCompletionReceipt.laneReceipts.length, 5);

  const laneCompletionReceiptInitOverwriteResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--lane-completion-receipt-init",
    `--lane-completion-receipt-output=${initializedLaneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.notEqual(laneCompletionReceiptInitOverwriteResult.status, 0, "receipt init should refuse to overwrite an existing file");
  const laneCompletionReceiptInitOverwrite = JSON.parse(laneCompletionReceiptInitOverwriteResult.stdout);
  assert.equal(laneCompletionReceiptInitOverwrite.status, "BLOCKED");
  assert(laneCompletionReceiptInitOverwrite.issues.some((issue) => issue.includes("refusing to overwrite")));

  const laneCompletionReceiptFile = path.join(tmpDir, "lane-completion-receipt.json");
  fs.writeFileSync(laneCompletionReceiptFile, laneCompletionReceiptTemplateResult.stdout);
  const laneCompletionReceiptContractResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--lane-completion-receipt-contract",
    `--lane-completion-receipt-file=${laneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(laneCompletionReceiptContractResult.status, 0, laneCompletionReceiptContractResult.stderr || laneCompletionReceiptContractResult.stdout);
  const laneCompletionReceiptContract = JSON.parse(laneCompletionReceiptContractResult.stdout);
  assert.equal(laneCompletionReceiptContract.status, "PASS");
  assert.equal(laneCompletionReceiptContract.receiptStatus, "BLOCKED");
  assert.equal(laneCompletionReceiptContract.summary.passLaneCount, 0);
  assert.equal(laneCompletionReceiptContract.summary.blockedLaneCount, 5);
  assert.deepEqual(laneCompletionReceiptContract.summary.duplicateLaneKeys, []);
  assert.deepEqual(laneCompletionReceiptContract.summary.passLaneKeysMissingAudit, []);

  const completedLaneCompletionReceipt = {
    ...laneCompletionReceiptTemplate,
    status: "PASS",
    laneReceipts: laneCompletionReceiptTemplate.laneReceipts.map((lane) => ({
      ...lane,
      status: "PASS",
      providedArtifacts: lane.expectedArtifacts,
      missingArtifacts: [],
      completedAt: "2026-06-18T00:00:00.000Z",
      completedBy: "release-owner",
    })),
  };
  const completedLaneCompletionReceiptFile = path.join(tmpDir, "lane-completion-receipt-completed.json");
  fs.writeFileSync(completedLaneCompletionReceiptFile, `${JSON.stringify(completedLaneCompletionReceipt, null, 2)}\n`);
  const completedLaneCompletionReceiptContractResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--lane-completion-receipt-contract",
    `--lane-completion-receipt-file=${completedLaneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(completedLaneCompletionReceiptContractResult.status, 0, completedLaneCompletionReceiptContractResult.stderr || completedLaneCompletionReceiptContractResult.stdout);
  const completedLaneCompletionReceiptContract = JSON.parse(completedLaneCompletionReceiptContractResult.stdout);
  assert.equal(completedLaneCompletionReceiptContract.status, "PASS");
  assert.equal(completedLaneCompletionReceiptContract.receiptStatus, "PASS");
  assert.equal(completedLaneCompletionReceiptContract.summary.passLaneCount, 5);
  assert.equal(completedLaneCompletionReceiptContract.summary.blockedLaneCount, 0);
  assert.deepEqual(completedLaneCompletionReceiptContract.summary.duplicateLaneKeys, []);
  assert.deepEqual(completedLaneCompletionReceiptContract.summary.passLaneKeysMissingAudit, []);

  const duplicateLaneCompletionReceipt = {
    ...completedLaneCompletionReceipt,
    laneReceipts: [
      completedLaneCompletionReceipt.laneReceipts[0],
      completedLaneCompletionReceipt.laneReceipts[0],
    ],
  };
  const duplicateLaneCompletionReceiptFile = path.join(tmpDir, "lane-completion-receipt-duplicate.json");
  fs.writeFileSync(duplicateLaneCompletionReceiptFile, `${JSON.stringify(duplicateLaneCompletionReceipt, null, 2)}\n`);
  const duplicateLaneCompletionReceiptContractResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--lane-completion-receipt-contract",
    `--lane-completion-receipt-file=${duplicateLaneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(duplicateLaneCompletionReceiptContractResult.status, 1);
  const duplicateLaneCompletionReceiptContract = JSON.parse(duplicateLaneCompletionReceiptContractResult.stdout);
  assert.deepEqual(duplicateLaneCompletionReceiptContract.summary.duplicateLaneKeys, ["platform-owners:p1-p2-data-safety"]);
  assert.match(duplicateLaneCompletionReceiptContractResult.stdout, /duplicate owner:lane/);

  const incompleteAuditLaneCompletionReceipt = {
    ...completedLaneCompletionReceipt,
    laneReceipts: completedLaneCompletionReceipt.laneReceipts.map((lane, index) => (index === 0
      ? { ...lane, completedAt: null, completedBy: null }
      : lane)),
  };
  const incompleteAuditLaneCompletionReceiptFile = path.join(tmpDir, "lane-completion-receipt-incomplete-audit.json");
  fs.writeFileSync(incompleteAuditLaneCompletionReceiptFile, `${JSON.stringify(incompleteAuditLaneCompletionReceipt, null, 2)}\n`);
  const incompleteAuditLaneCompletionReceiptContractResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--lane-completion-receipt-contract",
    `--lane-completion-receipt-file=${incompleteAuditLaneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(incompleteAuditLaneCompletionReceiptContractResult.status, 1);
  const incompleteAuditLaneCompletionReceiptContract = JSON.parse(incompleteAuditLaneCompletionReceiptContractResult.stdout);
  assert.deepEqual(incompleteAuditLaneCompletionReceiptContract.summary.passLaneKeysMissingAudit, ["platform-owners:p1-p2-data-safety"]);
  assert.match(incompleteAuditLaneCompletionReceiptContractResult.stdout, /PASS requires completedBy/);
  assert.match(incompleteAuditLaneCompletionReceiptContractResult.stdout, /PASS requires completedAt/);

  const completedLaneCompletionReceiptCoverageResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--lane-completion-receipt-coverage",
    `--lane-completion-receipt-file=${completedLaneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(completedLaneCompletionReceiptCoverageResult.status, 0, completedLaneCompletionReceiptCoverageResult.stderr || completedLaneCompletionReceiptCoverageResult.stdout);
  const completedLaneCompletionReceiptCoverage = JSON.parse(completedLaneCompletionReceiptCoverageResult.stdout);
  assert.equal(completedLaneCompletionReceiptCoverage.status, "PASS");
  assert.equal(completedLaneCompletionReceiptCoverage.coverage.coveredLaneCount, 5);
  assert.equal(completedLaneCompletionReceiptCoverage.coverage.expectedLaneCount, 5);

  const completedEvidenceClosureBoardResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--evidence-closure-board",
    `--lane-completion-receipt-file=${completedLaneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(completedEvidenceClosureBoardResult.status, 0, completedEvidenceClosureBoardResult.stderr || completedEvidenceClosureBoardResult.stdout);
  const completedEvidenceClosureBoard = JSON.parse(completedEvidenceClosureBoardResult.stdout);
  assert.equal(completedEvidenceClosureBoard.status, "PASS");
  assert.equal(completedEvidenceClosureBoard.coverage.coveredLaneCount, 5);
  assert.equal(completedEvidenceClosureBoard.closedLaneCount, 5);
  assert(completedEvidenceClosureBoard.lanes.every((lane) => lane.status === "PASS"));

  const completedLaneCompletionReceiptBase64Result = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--lane-completion-receipt-base64",
    `--lane-completion-receipt-file=${completedLaneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(completedLaneCompletionReceiptBase64Result.status, 0, completedLaneCompletionReceiptBase64Result.stderr || completedLaneCompletionReceiptBase64Result.stdout);
  assert.match(completedLaneCompletionReceiptBase64Result.stdout.trim(), /^[A-Za-z0-9+/]+=*$/);
  assert.deepEqual(JSON.parse(Buffer.from(completedLaneCompletionReceiptBase64Result.stdout.trim(), "base64").toString("utf8")), completedLaneCompletionReceipt);

  const partialCompletedLaneCompletionReceipt = {
    ...completedLaneCompletionReceipt,
    laneReceipts: completedLaneCompletionReceipt.laneReceipts.slice(0, 1),
  };
  const partialCompletedLaneCompletionReceiptFile = path.join(tmpDir, "lane-completion-receipt-partial.json");
  fs.writeFileSync(partialCompletedLaneCompletionReceiptFile, `${JSON.stringify(partialCompletedLaneCompletionReceipt, null, 2)}\n`);
  const partialCompletedLaneCompletionReceiptContractResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--lane-completion-receipt-contract",
    `--lane-completion-receipt-file=${partialCompletedLaneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(partialCompletedLaneCompletionReceiptContractResult.status, 0, partialCompletedLaneCompletionReceiptContractResult.stderr || partialCompletedLaneCompletionReceiptContractResult.stdout);
  const partialCompletedLaneCompletionReceiptContract = JSON.parse(partialCompletedLaneCompletionReceiptContractResult.stdout);
  assert.equal(partialCompletedLaneCompletionReceiptContract.status, "PASS");
  assert.equal(partialCompletedLaneCompletionReceiptContract.receiptStatus, "PASS");
  assert.equal(partialCompletedLaneCompletionReceiptContract.laneCount, 1);
  assert.equal(partialCompletedLaneCompletionReceiptContract.summary.passLaneCount, 1);
  assert.equal(partialCompletedLaneCompletionReceiptContract.summary.blockedLaneCount, 0);

  const partialCompletedLaneCompletionReceiptBase64Result = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--lane-completion-receipt-base64",
    `--lane-completion-receipt-file=${partialCompletedLaneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(partialCompletedLaneCompletionReceiptBase64Result.status, 1);
  assert.match(partialCompletedLaneCompletionReceiptBase64Result.stderr, /missing lanes=/);

  const partialCompletedLaneCompletionReceiptCoverageResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--lane-completion-receipt-coverage-markdown",
    `--lane-completion-receipt-file=${partialCompletedLaneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(partialCompletedLaneCompletionReceiptCoverageResult.status, 1);
  assert.match(partialCompletedLaneCompletionReceiptCoverageResult.stdout, /^# DDD Lane Completion Receipt Coverage/m);
  assert.match(partialCompletedLaneCompletionReceiptCoverageResult.stdout, /Coverage: 1\/5/);
  assert.match(partialCompletedLaneCompletionReceiptCoverageResult.stdout, /## Contract Summary/);
  assert.match(partialCompletedLaneCompletionReceiptCoverageResult.stdout, /PASS lanes: 1/);
  assert.match(partialCompletedLaneCompletionReceiptCoverageResult.stdout, /BLOCKED lanes: 0/);
  assert.match(partialCompletedLaneCompletionReceiptCoverageResult.stdout, /Duplicate owner:lane keys: none/);
  assert.match(partialCompletedLaneCompletionReceiptCoverageResult.stdout, /PASS lanes missing audit fields: none/);
  assert.match(partialCompletedLaneCompletionReceiptCoverageResult.stdout, /platform-owners:p1-p2-data-safety|release-infra:/);

  const evidenceClosureBoardResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--evidence-closure-board"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(evidenceClosureBoardResult.status, 0, evidenceClosureBoardResult.stderr || evidenceClosureBoardResult.stdout);
  const evidenceClosureBoard = JSON.parse(evidenceClosureBoardResult.stdout);
  assert.equal(evidenceClosureBoard.status, "BLOCKED");
  assert.equal(evidenceClosureBoard.coverage.coveredLaneCount, 0);
  assert.equal(evidenceClosureBoard.coverage.expectedLaneCount, 5);
  assert.equal(evidenceClosureBoard.openLaneCount, 5);
  assert(evidenceClosureBoard.lanes.some((lane) => lane.key === "platform-owners:p1-p2-data-safety" && lane.missingArtifacts.includes("tmp/ddd-explain/*.json")));

  const evidenceClosureBoardMarkdownResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--evidence-closure-board-markdown",
    `--lane-completion-receipt-file=${partialCompletedLaneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(evidenceClosureBoardMarkdownResult.status, 0, evidenceClosureBoardMarkdownResult.stderr || evidenceClosureBoardMarkdownResult.stdout);
  assert.match(evidenceClosureBoardMarkdownResult.stdout, /^# DDD Evidence Closure Board/m);
  assert.match(evidenceClosureBoardMarkdownResult.stdout, /Coverage: 1\/5/);
  assert.match(evidenceClosureBoardMarkdownResult.stdout, /platform-owners:p1-p2-data-safety|release-infra:/);

  const evidenceClosureBoardCsvResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--evidence-closure-board-csv",
    `--lane-completion-receipt-file=${partialCompletedLaneCompletionReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(evidenceClosureBoardCsvResult.status, 0, evidenceClosureBoardCsvResult.stderr || evidenceClosureBoardCsvResult.stdout);
  assert.match(evidenceClosureBoardCsvResult.stdout, /^"key","owner","lane","status","receiptStatus"/m);
  assert.match(evidenceClosureBoardCsvResult.stdout, /"platform-owners:p1-p2-data-safety","platform-owners","p1-p2-data-safety","PASS","PASS"/);
  assert.match(evidenceClosureBoardCsvResult.stdout, /"release-infra:p0-release-env","release-infra","p0-release-env","BLOCKED","MISSING"/);

  const laneCompletionSubmissionPlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--lane-completion-submission-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(laneCompletionSubmissionPlanResult.status, 0, laneCompletionSubmissionPlanResult.stderr || laneCompletionSubmissionPlanResult.stdout);
  const laneCompletionSubmissionPlan = JSON.parse(laneCompletionSubmissionPlanResult.stdout);
  assert.equal(laneCompletionSubmissionPlan.status, "BLOCKED");
  assert.equal(laneCompletionSubmissionPlan.redacted, true);
  assert.equal(laneCompletionSubmissionPlan.laneCount, 5);
  assert.equal(laneCompletionSubmissionPlan.workflowInput.base64Input, "lane_completion_receipt_base64");
  assert.equal(laneCompletionSubmissionPlan.workflowInput.decodedPath, "artifacts/ddd/release/lane-completion-receipt.submitted.json");
  assert.equal(laneCompletionSubmissionPlan.currentCoverage.coveredLaneCount, 0);
  assert.equal(laneCompletionSubmissionPlan.currentCoverage.expectedLaneCount, 5);
  assert.equal(laneCompletionSubmissionPlan.nextCommand, "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>");
  assert(laneCompletionSubmissionPlan.lanes.some((lane) => lane.key === "platform-owners:p1-p2-data-safety" && lane.missingArtifacts.includes("tmp/ddd-explain/*.json")));
  assert(laneCompletionSubmissionPlan.commands.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>"));
  assert(laneCompletionSubmissionPlan.commands.includes("node scripts/ddd-lane-completion-receipt-autofill.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>"));
  assert(laneCompletionSubmissionPlan.commands.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>"));

  const laneCompletionSubmissionPlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--lane-completion-submission-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(laneCompletionSubmissionPlanMarkdownResult.status, 0, laneCompletionSubmissionPlanMarkdownResult.stderr || laneCompletionSubmissionPlanMarkdownResult.stdout);
  assert.match(laneCompletionSubmissionPlanMarkdownResult.stdout, /^# DDD Lane Completion Submission Plan/m);
  assert.match(laneCompletionSubmissionPlanMarkdownResult.stdout, /lane_completion_receipt_base64/);
  assert.match(laneCompletionSubmissionPlanMarkdownResult.stdout, /Coverage|Current coverage: 0\/5/);
  assert.match(laneCompletionSubmissionPlanMarkdownResult.stdout, /platform-owners:p1-p2-data-safety/);

  const laneCompletionSubmissionCheckResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--lane-completion-submission-check"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(laneCompletionSubmissionCheckResult.status, 0, laneCompletionSubmissionCheckResult.stderr || laneCompletionSubmissionCheckResult.stdout);
  const laneCompletionSubmissionCheck = JSON.parse(laneCompletionSubmissionCheckResult.stdout);
  assert.equal(laneCompletionSubmissionCheck.status, "BLOCKED");
  assert.equal(laneCompletionSubmissionCheck.contract.status, "MISSING");
  assert.equal(laneCompletionSubmissionCheck.coverage.coveredLaneCount, 0);
  assert.equal(laneCompletionSubmissionCheck.coverage.expectedLaneCount, 5);
  assert.equal(laneCompletionSubmissionCheck.base64.ready, false);
  assert.equal(laneCompletionSubmissionCheck.dispatch.ready, false);
  assert.equal(laneCompletionSubmissionCheck.dispatch.preferredInput, "lane_completion_receipt_base64");
  assert.equal(laneCompletionSubmissionCheck.nextCommand, "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>");
  assert(laneCompletionSubmissionCheck.issues.includes("lane completion receipt file not provided"));

  const laneCompletionSubmissionCheckMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--lane-completion-submission-check-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(laneCompletionSubmissionCheckMarkdownResult.status, 0, laneCompletionSubmissionCheckMarkdownResult.stderr || laneCompletionSubmissionCheckMarkdownResult.stdout);
  assert.match(laneCompletionSubmissionCheckMarkdownResult.stdout, /^# DDD Lane Completion Submission Check/m);
  assert.match(laneCompletionSubmissionCheckMarkdownResult.stdout, /Base64 ready: false/);
  assert.match(laneCompletionSubmissionCheckMarkdownResult.stdout, /Dispatch ready: false/);
  assert.match(laneCompletionSubmissionCheckMarkdownResult.stdout, /## Submission Commands/);

  const nextActionEnvTemplateResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--next-action-env-template"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-next-action-env-template"),
    },
  });
  assert.equal(nextActionEnvTemplateResult.status, 0, nextActionEnvTemplateResult.stderr || nextActionEnvTemplateResult.stdout);
  assert.match(nextActionEnvTemplateResult.stdout, /^# Lumira DDD staging next-action environment template\./);
  assert.match(nextActionEnvTemplateResult.stdout, /^# Lane: p1-runtime-business$/m);
  assert.match(nextActionEnvTemplateResult.stdout, /^LUMIRA_BASE_URL=__REQUIRED_HTTPS__$/m);
  assert.match(nextActionEnvTemplateResult.stdout, /^# Choose one of: DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_DEFERRAL_FILE$/m);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-next-action-env-template.json")), false);

  const missingNextActionEnvCheckResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--next-action-env-check",
    `--next-action-env-file=${path.join(tmpDir, "missing-next-action.env")}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(missingNextActionEnvCheckResult.status, 1);
  const missingNextActionEnvCheck = JSON.parse(missingNextActionEnvCheckResult.stdout);
  assert.equal(missingNextActionEnvCheck.status, "BLOCKED");
  assert(missingNextActionEnvCheck.issues.some((issue) => issue.includes("env file does not exist")));

  const populatedNextActionEnv = path.join(tmpDir, "populated-next-action.env");
  fs.writeFileSync(populatedNextActionEnv, [
    "DDD_RELEASE_ENV_FILE=tmp/secure-release.env",
    "DDD_DOCKER_BUILD_STRICT=true",
    "DDD_RELEASE_CANDIDATE=test-sha",
    "DDD_EVIDENCE_OPERATOR=test-operator",
    "DDD_EVIDENCE_ENVIRONMENT=staging",
    "DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=registry.example/lumira-server:test",
    "DDD_DOCKER_EXISTING_FRONTEND_IMAGE=registry.example/lumira-frontend:test",
    "DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=https://ci.example/build/1",
    "LUMIRA_BASE_URL=https://api.staging.example.com",
    "PLAYWRIGHT_BASE_URL=https://app.staging.example.com",
    "DDD_DEPLOYMENT_EVIDENCE=https://deploy.example/run/1",
    "DDD_FRONTEND_DEPLOYMENT_EVIDENCE=https://deploy.example/frontend/1",
    "DDD_FRONTEND_EXPECT_DEPLOYED=true",
    "DDD_ROLLBACK_DRILL_FILE=artifacts/ddd/rollback/rollback-drill.json",
    "DDD_ROLLBACK_DRILL_ENVIRONMENT=staging",
  ].join("\n"));
  const populatedNextActionEnvCheckResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--next-action-env-check",
    `--next-action-env-file=${populatedNextActionEnv}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(populatedNextActionEnvCheckResult.status, 0, populatedNextActionEnvCheckResult.stderr || populatedNextActionEnvCheckResult.stdout);
  const populatedNextActionEnvCheck = JSON.parse(populatedNextActionEnvCheckResult.stdout);
  assert.equal(populatedNextActionEnvCheck.status, "PASS");
  assert(populatedNextActionEnvCheck.laneChecks.every((lane) => lane.status === "PASS"));

  const populatedNextActionEnvReceiptResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--next-action-env-receipt",
    `--next-action-env-file=${populatedNextActionEnv}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(populatedNextActionEnvReceiptResult.status, 0, populatedNextActionEnvReceiptResult.stderr || populatedNextActionEnvReceiptResult.stdout);
  const populatedNextActionEnvReceipt = JSON.parse(populatedNextActionEnvReceiptResult.stdout);
  assert.equal(populatedNextActionEnvReceipt.status, "PASS");
  assert.equal(populatedNextActionEnvReceipt.redacted, true);
  assert.match(populatedNextActionEnvReceipt.envFileSha256, /^[a-f0-9]{64}$/);
  assert(populatedNextActionEnvReceipt.requiredSelectedKeys.includes("LUMIRA_BASE_URL"));
  assert(populatedNextActionEnvReceipt.laneReceipts.every((lane) => lane.status === "PASS"));
  assert.doesNotMatch(populatedNextActionEnvReceiptResult.stdout, /api\.staging\.example\.com/);
  assert.doesNotMatch(populatedNextActionEnvReceiptResult.stdout, /registry\.example\/lumira-server:test/);

  const populatedNextActionEnvReceiptMarkdownResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--next-action-env-receipt-markdown",
    `--next-action-env-file=${populatedNextActionEnv}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(populatedNextActionEnvReceiptMarkdownResult.status, 0, populatedNextActionEnvReceiptMarkdownResult.stderr || populatedNextActionEnvReceiptMarkdownResult.stdout);
  assert.match(populatedNextActionEnvReceiptMarkdownResult.stdout, /^# DDD Next Action Env Receipt/m);
  assert.match(populatedNextActionEnvReceiptMarkdownResult.stdout, /Redacted: true/);
  assert.doesNotMatch(populatedNextActionEnvReceiptMarkdownResult.stdout, /api\.staging\.example\.com/);

  const populatedNextActionEnvReceiptFile = path.join(tmpDir, "populated-next-action-receipt.json");
  fs.writeFileSync(populatedNextActionEnvReceiptFile, populatedNextActionEnvReceiptResult.stdout);
  const populatedNextActionEnvReceiptContractResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--next-action-env-receipt-contract",
    `--next-action-env-receipt-file=${populatedNextActionEnvReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(populatedNextActionEnvReceiptContractResult.status, 0, populatedNextActionEnvReceiptContractResult.stderr || populatedNextActionEnvReceiptContractResult.stdout);
  const populatedNextActionEnvReceiptContract = JSON.parse(populatedNextActionEnvReceiptContractResult.stdout);
  assert.equal(populatedNextActionEnvReceiptContract.status, "PASS");
  assert.equal(populatedNextActionEnvReceiptContract.redacted, true);

  const populatedNextActionEnvReceiptOutput = path.join(tmpDir, "populated-next-action-receipt-output.json");
  const populatedNextActionEnvReceiptOutputResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--next-action-env-receipt",
    `--next-action-env-file=${populatedNextActionEnv}`,
    `--next-action-env-receipt-output=${populatedNextActionEnvReceiptOutput}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(populatedNextActionEnvReceiptOutputResult.status, 0, populatedNextActionEnvReceiptOutputResult.stderr || populatedNextActionEnvReceiptOutputResult.stdout);
  assert.equal(fs.existsSync(populatedNextActionEnvReceiptOutput), true, "receipt output command should write JSON receipt");
  const populatedNextActionEnvReceiptOutputContract = JSON.parse(populatedNextActionEnvReceiptOutputResult.stdout);
  assert.equal(populatedNextActionEnvReceiptOutputContract.status, "PASS");
  assert.equal(populatedNextActionEnvReceiptOutputContract.receiptFile, populatedNextActionEnvReceiptOutput);
  assert.doesNotMatch(fs.readFileSync(populatedNextActionEnvReceiptOutput, "utf8"), /api\.staging\.example\.com/);

  const operatorProgressWithReceiptResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--operator-progress"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_NEXT_ACTION_ENV_FILE: populatedNextActionEnv,
      DDD_NEXT_ACTION_ENV_RECEIPT_FILE: populatedNextActionEnvReceiptOutput,
      DDD_LANE_COMPLETION_RECEIPT_FILE: completedLaneCompletionReceiptFile,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-operator-progress-with-receipt"),
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(operatorProgressWithReceiptResult.status, 0, operatorProgressWithReceiptResult.stderr || operatorProgressWithReceiptResult.stdout);
  const operatorProgressWithReceipt = JSON.parse(operatorProgressWithReceiptResult.stdout);
  assert(operatorProgressWithReceipt.stages.some((stage) => stage.id === "first-wave-env" && stage.status === "PASS"));
  assert(operatorProgressWithReceipt.stages.some((stage) => stage.id === "first-wave-env-receipt" && stage.status === "PASS"));
  assert(operatorProgressWithReceipt.stages.some((stage) => stage.id === "lane-completion-receipt" && stage.status === "PASS"));
  assert.equal(operatorProgressWithReceipt.receiptFile, populatedNextActionEnvReceiptOutput);
  assert.equal(operatorProgressWithReceipt.laneReceiptFile, completedLaneCompletionReceiptFile);
  assert.equal(operatorProgressWithReceipt.laneReceipt.coverage.status, "PASS");
  assert.equal(operatorProgressWithReceipt.laneReceipt.coverage.coveredLaneCount, 5);
  assert.equal(operatorProgressWithReceipt.laneReceipt.coverage.expectedLaneCount, 5);

  const operatorProgressWithPartialReceiptResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--operator-progress"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_NEXT_ACTION_ENV_FILE: populatedNextActionEnv,
      DDD_NEXT_ACTION_ENV_RECEIPT_FILE: populatedNextActionEnvReceiptOutput,
      DDD_LANE_COMPLETION_RECEIPT_FILE: partialCompletedLaneCompletionReceiptFile,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-operator-progress-with-partial-receipt"),
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(operatorProgressWithPartialReceiptResult.status, 0, operatorProgressWithPartialReceiptResult.stderr || operatorProgressWithPartialReceiptResult.stdout);
  const operatorProgressWithPartialReceipt = JSON.parse(operatorProgressWithPartialReceiptResult.stdout);
  assert(operatorProgressWithPartialReceipt.stages.some((stage) => stage.id === "lane-completion-receipt" && stage.status === "BLOCKED" && stage.detail.includes("missing lanes=")));
  assert.equal(operatorProgressWithPartialReceipt.laneReceipt.status, "PASS");
  assert.equal(operatorProgressWithPartialReceipt.laneReceipt.coverage.status, "BLOCKED");
  assert.equal(operatorProgressWithPartialReceipt.laneReceipt.coverage.coveredLaneCount, 1);
  assert.equal(operatorProgressWithPartialReceipt.laneReceipt.coverage.expectedLaneCount, 5);

  const operatorProgressMarkdownWithReceiptResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--operator-progress-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_NEXT_ACTION_ENV_FILE: populatedNextActionEnv,
      DDD_NEXT_ACTION_ENV_RECEIPT_FILE: populatedNextActionEnvReceiptOutput,
      DDD_LANE_COMPLETION_RECEIPT_FILE: completedLaneCompletionReceiptFile,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-operator-progress-markdown-with-receipt"),
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(operatorProgressMarkdownWithReceiptResult.status, 0, operatorProgressMarkdownWithReceiptResult.stderr || operatorProgressMarkdownWithReceiptResult.stdout);
  assert.match(operatorProgressMarkdownWithReceiptResult.stdout, new RegExp(`Receipt file: \`${escapeRegExp(populatedNextActionEnvReceiptOutput)}\``));
  assert.match(operatorProgressMarkdownWithReceiptResult.stdout, new RegExp(`Lane receipt file: \`${escapeRegExp(completedLaneCompletionReceiptFile)}\``));
  assert.match(operatorProgressMarkdownWithReceiptResult.stdout, /Lane receipt coverage: 5\/5/);

  const leakingNextActionEnvReceiptFile = path.join(tmpDir, "leaking-next-action-receipt.json");
  fs.writeFileSync(leakingNextActionEnvReceiptFile, JSON.stringify({
    ...populatedNextActionEnvReceipt,
    requiredSelectedKeys: [...populatedNextActionEnvReceipt.requiredSelectedKeys, "LUMIRA_BASE_URL=https://api.staging.example.com"],
  }, null, 2));
  const leakingNextActionEnvReceiptContractResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--next-action-env-receipt-contract",
    `--next-action-env-receipt-file=${leakingNextActionEnvReceiptFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(leakingNextActionEnvReceiptContractResult.status, 1);
  const leakingNextActionEnvReceiptContract = JSON.parse(leakingNextActionEnvReceiptContractResult.stdout);
  assert.equal(leakingNextActionEnvReceiptContract.status, "FAIL");
  assert(leakingNextActionEnvReceiptContract.issues.some((issue) => issue.includes("URLs")));

  const nextActionVerificationPlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--next-action-verification-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-next-action-verification-plan"),
    },
  });
  assert.equal(nextActionVerificationPlanResult.status, 0, nextActionVerificationPlanResult.stderr || nextActionVerificationPlanResult.stdout);
  const nextActionVerificationPlan = JSON.parse(nextActionVerificationPlanResult.stdout);
  assert.equal(nextActionVerificationPlan.status, "BLOCKED");
  assert.equal(nextActionVerificationPlan.willWriteFiles, false);
  assert(nextActionVerificationPlan.phases.some((phase) => phase.id === "verify-first-wave-env" && phase.command.includes("--next-action-env-check")));
  assert(nextActionVerificationPlan.phases.some((phase) => phase.id === "verify-first-wave-env" && phase.followUpCommand.includes("--next-action-env-receipt-output")));
  assert(nextActionVerificationPlan.phases.some((phase) => phase.id === "verify-first-wave-env" && phase.contractCommand.includes("--next-action-env-receipt-contract")));
  assert(nextActionVerificationPlan.phases.some((phase) => phase.id === "verify-runtime" && phase.sourcePlan === "runtime-smoke-plan.json"));
  assert.equal(nextActionVerificationPlan.nextPhase.id, "verify-first-wave-env");

  const nextActionVerificationPlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--next-action-verification-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-next-action-verification-plan-markdown"),
    },
  });
  assert.equal(nextActionVerificationPlanMarkdownResult.status, 0, nextActionVerificationPlanMarkdownResult.stderr || nextActionVerificationPlanMarkdownResult.stdout);
  assert.match(nextActionVerificationPlanMarkdownResult.stdout, /^# DDD Next Action Verification Plan/m);
  assert.match(nextActionVerificationPlanMarkdownResult.stdout, /verify-first-wave-env/);
  assert.match(nextActionVerificationPlanMarkdownResult.stdout, /--next-action-env-receipt-markdown/);
  assert.match(nextActionVerificationPlanMarkdownResult.stdout, /--next-action-env-receipt-contract/);
  assert.match(nextActionVerificationPlanMarkdownResult.stdout, /verify-final-acceptance/);

  const operatorProgressResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--operator-progress"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-operator-progress"),
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(operatorProgressResult.status, 0, operatorProgressResult.stderr || operatorProgressResult.stdout);
  const operatorProgress = JSON.parse(operatorProgressResult.stdout);
  assert.equal(operatorProgress.status, "BLOCKED");
  assert.equal(operatorProgress.cutoverReady, false);
  assert(operatorProgress.evidenceArtifacts.missing > 0);
  assert.equal(operatorProgress.evidenceArtifacts.totalGates, 6);
  assert(operatorProgress.evidenceArtifacts.missingItems.length > 0);
  assert(operatorProgress.evidenceArtifacts.missingItems.every((item) => item.artifact && item.gate && item.acceptanceCommand));
  assert(operatorProgress.evidenceArtifacts.missingByOwner.length > 0);
  assert(operatorProgress.evidenceArtifacts.missingByOwner.every((item) => item.owner && item.missingCount > 0 && item.gates.length > 0));
  assert(operatorProgress.laneRoutes.some((lane) => lane.lane === "p0-docker-images" && lane.sourcePlan === "docker-image-submission-plan.json"));
  assert(operatorProgress.laneRoutes.some((lane) => lane.lane === "p1-runtime-business" && lane.command === "node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown"));
  assert(operatorProgress.laneRoutes.some((lane) => lane.lane === "p1-p2-data-safety" && lane.sourcePlan === "data-safety-submission-plan.json"));
  assert(operatorProgress.stages.some((stage) => stage.id === "first-wave-env" && stage.status === "BLOCKED"));
  assert(operatorProgress.stages.some((stage) => stage.id === "lane-completion-receipt" && stage.status === "SKIPPED"));
  assert(operatorProgress.stages.some((stage) => stage.id === "final-review" && stage.command.includes("--final-review-enforce")));
  assert.equal(operatorProgress.nextStage.id, "first-wave-env");
  assert.match(operatorProgress.nextCommand, /--next-action-env-check/);

  const operatorProgressMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--operator-progress-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-operator-progress-markdown"),
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(operatorProgressMarkdownResult.status, 0, operatorProgressMarkdownResult.stderr || operatorProgressMarkdownResult.stdout);
  assert.match(operatorProgressMarkdownResult.stdout, /^# DDD Operator Progress/m);
  assert.match(operatorProgressMarkdownResult.stdout, /Evidence artifacts: \d+\/\d+ present; missing=\d+/);
  assert.match(operatorProgressMarkdownResult.stdout, /Lane receipt file: not provided/);
  assert.match(operatorProgressMarkdownResult.stdout, /Evidence gates: \d+\/6 accepted/);
  assert.match(operatorProgressMarkdownResult.stdout, /## Missing Evidence By Owner/);
  assert.match(operatorProgressMarkdownResult.stdout, /missing=\d+; gates=/);
  assert.match(operatorProgressMarkdownResult.stdout, /## Missing Evidence Artifacts/);
  assert.match(operatorProgressMarkdownResult.stdout, /gate=.*; owner=.*; next=`/);
  assert.match(operatorProgressMarkdownResult.stdout, /## Lane Routes/);
  assert.match(operatorProgressMarkdownResult.stdout, /docker-image-submission-plan\.json/);
  assert.match(operatorProgressMarkdownResult.stdout, /runtime-business-submission-plan\.json/);
  assert.match(operatorProgressMarkdownResult.stdout, /data-safety-submission-plan\.json/);
  assert.match(operatorProgressMarkdownResult.stdout, /First-wave env file/);
  assert.match(operatorProgressMarkdownResult.stdout, /Release-owner final review/);

  const productionCutoverAuditResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--production-cutover-audit"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-production-cutover-audit"),
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(productionCutoverAuditResult.status, 0, productionCutoverAuditResult.stderr || productionCutoverAuditResult.stdout);
  const productionCutoverAudit = JSON.parse(productionCutoverAuditResult.stdout);
  assert.equal(productionCutoverAudit.status, "BLOCKED");
  assert.equal(productionCutoverAudit.cutoverAllowed, false);
  assert.equal(productionCutoverAudit.noAutoWaivers, true);
  assert.equal(productionCutoverAudit.auditItemCount, 7);
  assert(productionCutoverAudit.auditItems.some((item) => item.id === "handoff-bundle-integrity"));
  assert(productionCutoverAudit.auditItems.some((item) => item.id === "strict-go-no-go" && item.status === "BLOCKED"));
  assert(productionCutoverAudit.requiredCommands.includes("DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh"));

  const productionCutoverAuditMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--production-cutover-audit-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-production-cutover-audit-markdown"),
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(productionCutoverAuditMarkdownResult.status, 0, productionCutoverAuditMarkdownResult.stderr || productionCutoverAuditMarkdownResult.stdout);
  assert.match(productionCutoverAuditMarkdownResult.stdout, /^# DDD Production Cutover Audit/m);
  assert.match(productionCutoverAuditMarkdownResult.stdout, /## Audit Items/);
  assert.match(productionCutoverAuditMarkdownResult.stdout, /## Required Commands/);
  assert.match(productionCutoverAuditMarkdownResult.stdout, /No auto waivers: true/);

  const releaseOwnerDailyBriefResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-owner-daily-brief"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-owner-daily-brief"),
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(releaseOwnerDailyBriefResult.status, 0, releaseOwnerDailyBriefResult.stderr || releaseOwnerDailyBriefResult.stdout);
  const releaseOwnerDailyBrief = JSON.parse(releaseOwnerDailyBriefResult.stdout);
  assert.equal(releaseOwnerDailyBrief.status, "BLOCKED");
  assert.equal(releaseOwnerDailyBrief.cutoverReady, false);
  assert.equal(releaseOwnerDailyBrief.acceptedGateTotal, 6);
  assert(releaseOwnerDailyBrief.dailyPriorities.length > 0);
  assert(releaseOwnerDailyBrief.dailyPriorities.every((item) => item.owner && item.ownerPacket && item.envTemplate && item.nextCommand));
  assert(releaseOwnerDailyBrief.laneRoutes.some((lane) => lane.lane === "p0-docker-images" && lane.sourcePlan === "docker-image-submission-plan.json"));
  assert(releaseOwnerDailyBrief.laneRoutes.some((lane) => lane.lane === "p1-runtime-business" && lane.sourcePlan === "runtime-business-submission-plan.json"));
  assert(releaseOwnerDailyBrief.laneRoutes.some((lane) => lane.lane === "p1-p2-data-safety" && lane.sourcePlan === "data-safety-submission-plan.json"));
  assert(releaseOwnerDailyBrief.ownerActions.some((owner) => owner.owner === "release-infra" && owner.blockingInputCount > 0));
  assert(releaseOwnerDailyBrief.ownerActions.some((owner) => owner.owner === "platform-owners" && owner.missingEvidenceArtifactCount > 0));
  assert(releaseOwnerDailyBrief.ownerActions.some((owner) => owner.owner === "platform-owners" && owner.sourceOwners.includes("database")));
  assert(releaseOwnerDailyBrief.ownerActions.some((owner) => owner.owner === "platform-owners" && owner.missingEvidenceArtifacts.some((artifact) => artifact.owner === "database" && artifact.dispatchOwner === "platform-owners")));
  assert(releaseOwnerDailyBrief.acceptanceCommands.some((item) => item.gate === "release-env" && item.command.includes("ddd-release-env-file-lint")));
  assert(releaseOwnerDailyBrief.topBlockingInputs.length > 0);
  assert.equal(typeof releaseOwnerDailyBrief.nextCommand, "string");
  assert(releaseOwnerDailyBrief.nextCommand.length > 0);

  const releaseOwnerDailyBriefMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-owner-daily-brief-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-owner-daily-brief-markdown"),
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(releaseOwnerDailyBriefMarkdownResult.status, 0, releaseOwnerDailyBriefMarkdownResult.stderr || releaseOwnerDailyBriefMarkdownResult.stdout);
  assert.match(releaseOwnerDailyBriefMarkdownResult.stdout, /^# DDD Release Owner Daily Brief/m);
  assert.match(releaseOwnerDailyBriefMarkdownResult.stdout, /## Today/);
  assert.match(releaseOwnerDailyBriefMarkdownResult.stdout, /## Lane Routes/);
  assert.match(releaseOwnerDailyBriefMarkdownResult.stdout, /docker-image-submission-plan\.json/);
  assert.match(releaseOwnerDailyBriefMarkdownResult.stdout, /runtime-business-submission-plan\.json/);
  assert.match(releaseOwnerDailyBriefMarkdownResult.stdout, /data-safety-submission-plan\.json/);
  assert.match(releaseOwnerDailyBriefMarkdownResult.stdout, /## Owner Actions/);
  assert.match(releaseOwnerDailyBriefMarkdownResult.stdout, /## Acceptance Commands/);
  assert.match(releaseOwnerDailyBriefMarkdownResult.stdout, /owner-packets\/release-infra\.md/);
  assert.match(releaseOwnerDailyBriefMarkdownResult.stdout, /platform-owners \| .*database/);

  const releaseEnvPlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-env-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-env-plan"),
    },
  });
  assert.equal(releaseEnvPlanResult.status, 0, releaseEnvPlanResult.stderr || releaseEnvPlanResult.stdout);
  const releaseEnvPlan = JSON.parse(releaseEnvPlanResult.stdout);
  assert.equal(releaseEnvPlan.status, "BLOCKED");
  assert.equal(releaseEnvPlan.willWriteFiles, false);
  assert.equal(releaseEnvPlan.target, "tmp/ddd-dispatch-check-env-init.env");
  assert.equal(releaseEnvPlan.envInitCheck.status, "PASS");
  assert.equal(releaseEnvPlan.releaseEnvGate.acceptanceCommand, "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs");
  assert(releaseEnvPlan.ownerSteps.some((owner) => owner.owner === "release-infra"));
  assert(releaseEnvPlan.commands.preflight.includes("node scripts/ddd-release-env-init.mjs --check"));
  assert(releaseEnvPlan.commands.preflight.includes("node scripts/ddd-staging-execution-checklist.mjs --owner-packets"));
  assert(releaseEnvPlan.commands.validate.includes("DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs"));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-env-plan.json")), false);

  const releaseEnvPlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-env-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-env-plan-markdown"),
    },
  });
  assert.equal(releaseEnvPlanMarkdownResult.status, 0, releaseEnvPlanMarkdownResult.stderr || releaseEnvPlanMarkdownResult.stdout);
  assert.match(releaseEnvPlanMarkdownResult.stdout, /^# DDD P0 Release Env Plan/m);
  assert.match(releaseEnvPlanMarkdownResult.stdout, /node scripts\/ddd-release-env-init\.mjs --check/);
  assert.match(releaseEnvPlanMarkdownResult.stdout, /DDD_RELEASE_ENV_FILE=<release-env-file> node scripts\/ddd-release-env-file-lint\.mjs/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-env-plan-markdown.json")), false);

  const releaseEnvOwnerMatrixResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-env-owner-matrix"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-env-owner-matrix"),
    },
  });
  assert.equal(releaseEnvOwnerMatrixResult.status, 0, releaseEnvOwnerMatrixResult.stderr || releaseEnvOwnerMatrixResult.stdout);
  const releaseEnvOwnerMatrix = JSON.parse(releaseEnvOwnerMatrixResult.stdout);
  assert.equal(releaseEnvOwnerMatrix.status, "BLOCKED");
  assert.equal(releaseEnvOwnerMatrix.willWriteFiles, false);
  assert.equal(releaseEnvOwnerMatrix.ownerCount, 5);
  assert(releaseEnvOwnerMatrix.totals.blockers > 0);
  assert(releaseEnvOwnerMatrix.owners.some((owner) => owner.owner === "release-infra" && owner.keys.includes("LUMIRA_BASE_URL")));
  assert.match(releaseEnvOwnerMatrix.nextCommand, /--blocking-inputs-env-template --owner=/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-env-owner-matrix.json")), false);

  const releaseEnvOwnerMatrixMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-env-owner-matrix-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-env-owner-matrix-markdown"),
    },
  });
  assert.equal(releaseEnvOwnerMatrixMarkdownResult.status, 0, releaseEnvOwnerMatrixMarkdownResult.stderr || releaseEnvOwnerMatrixMarkdownResult.stdout);
  assert.match(releaseEnvOwnerMatrixMarkdownResult.stdout, /^# DDD Release Env Owner Matrix/m);
  assert.match(releaseEnvOwnerMatrixMarkdownResult.stdout, /release-infra/);
  assert.match(releaseEnvOwnerMatrixMarkdownResult.stdout, /LUMIRA_BASE_URL/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-env-owner-matrix-markdown.json")), false);

  const releaseEnvNextOwnerTemplateResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-env-next-owner-template"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-env-next-owner-template"),
    },
  });
  assert.equal(releaseEnvNextOwnerTemplateResult.status, 0, releaseEnvNextOwnerTemplateResult.stderr || releaseEnvNextOwnerTemplateResult.stdout);
  assert.match(releaseEnvNextOwnerTemplateResult.stdout, /^# Current top release-env owner template\./);
  assert.match(releaseEnvNextOwnerTemplateResult.stdout, /^# Owner: release-infra$/m);
  assert.match(releaseEnvNextOwnerTemplateResult.stdout, /^LUMIRA_BASE_URL=__REQUIRED_HTTPS__$/m);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-env-next-owner-template.json")), false);

  const releaseEnvMergePlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-env-merge-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-env-merge-plan"),
    },
  });
  assert.equal(releaseEnvMergePlanResult.status, 0, releaseEnvMergePlanResult.stderr || releaseEnvMergePlanResult.stdout);
  const releaseEnvMergePlan = JSON.parse(releaseEnvMergePlanResult.stdout);
  assert.equal(releaseEnvMergePlan.status, "BLOCKED");
  assert.equal(releaseEnvMergePlan.willWriteFiles, false);
  assert(releaseEnvMergePlan.phases.some((phase) => phase.id === "merge-owner-values" && phase.commands.some((command) => command.includes("ddd-release-env-canonical-merge.mjs"))));
  assert(releaseEnvMergePlan.phases.some((phase) => phase.id === "validate-release-env" && phase.commands.includes("DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs")));
  assert(releaseEnvMergePlan.safety.some((item) => item.includes("Do not use release-env-next-owner.template.env")));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-env-merge-plan.json")), false);

  const releaseEnvMergePlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-env-merge-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-env-merge-plan-markdown"),
    },
  });
  assert.equal(releaseEnvMergePlanMarkdownResult.status, 0, releaseEnvMergePlanMarkdownResult.stderr || releaseEnvMergePlanMarkdownResult.stdout);
  assert.match(releaseEnvMergePlanMarkdownResult.stdout, /^# DDD Release Env Merge Plan/m);
  assert.match(releaseEnvMergePlanMarkdownResult.stdout, /ddd-release-env-canonical-merge\.mjs/);
  assert.match(releaseEnvMergePlanMarkdownResult.stdout, /ddd-release-env-file-lint\.mjs/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-env-merge-plan-markdown.json")), false);

  const releaseEnvSubmissionPlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-env-submission-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-env-submission-plan"),
    },
  });
  assert.equal(releaseEnvSubmissionPlanResult.status, 0, releaseEnvSubmissionPlanResult.stderr || releaseEnvSubmissionPlanResult.stdout);
  const releaseEnvSubmissionPlan = JSON.parse(releaseEnvSubmissionPlanResult.stdout);
  assert.equal(releaseEnvSubmissionPlan.status, "BLOCKED");
  assert.equal(releaseEnvSubmissionPlan.ownerCount, 5);
  assert(releaseEnvSubmissionPlan.ownerSubmissions.some((owner) => owner.owner === "release-infra" && owner.keys.includes("LUMIRA_BASE_URL")));
  assert(releaseEnvSubmissionPlan.receipt.commands.includes("node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>"));
  assert.equal(releaseEnvSubmissionPlan.laneReceiptFragment.owner, "release-infra");
  assert.equal(releaseEnvSubmissionPlan.laneReceiptFragment.lane, "p0-release-env");
  assert(releaseEnvSubmissionPlan.laneReceiptFragment.providedArtifacts.includes("artifacts/ddd/release/release-env-lint.json"));
  assert(releaseEnvSubmissionPlan.laneReceiptFragment.missingArtifacts.includes("artifacts/ddd/config/release-config-evidence.json"));
  assert(releaseEnvSubmissionPlan.validationCommands.includes("DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs"));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-env-submission-plan.json")), false);

  const releaseEnvSubmissionPlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-env-submission-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-env-submission-plan-markdown"),
    },
  });
  assert.equal(releaseEnvSubmissionPlanMarkdownResult.status, 0, releaseEnvSubmissionPlanMarkdownResult.stderr || releaseEnvSubmissionPlanMarkdownResult.stdout);
  assert.match(releaseEnvSubmissionPlanMarkdownResult.stdout, /^# DDD Release Env Submission Plan/m);
  assert.match(releaseEnvSubmissionPlanMarkdownResult.stdout, /release-infra/);
  assert.match(releaseEnvSubmissionPlanMarkdownResult.stdout, /--next-action-env-receipt-output=<receipt-file>/);
  assert.match(releaseEnvSubmissionPlanMarkdownResult.stdout, /^## Lane Receipt Fragment/m);
  assert.match(releaseEnvSubmissionPlanMarkdownResult.stdout, /"lane": "p0-release-env"/);
  assert.match(releaseEnvSubmissionPlanMarkdownResult.stdout, /ddd-release-env-file-lint\.mjs/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-env-submission-plan-markdown.json")), false);

  const dockerImagePlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--docker-image-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-docker-image-plan"),
    },
  });
  assert.equal(dockerImagePlanResult.status, 0, dockerImagePlanResult.stderr || dockerImagePlanResult.stdout);
  const dockerImagePlan = JSON.parse(dockerImagePlanResult.stdout);
  assert.equal(dockerImagePlan.status, "PASS");
  assert.equal(dockerImagePlan.willWriteFiles, false);
  assert(dockerImagePlan.paths.some((item) => item.id === "docker-runner-build" && item.command.includes("DDD_DOCKER_BUILD_STRICT=true")));
  assert(dockerImagePlan.paths.some((item) => item.id === "existing-image-inspect" && item.command.includes("DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE")));
  assert(dockerImagePlan.requiredInputs.includes("DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE"));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-docker-image-plan.json")), false);

  const dockerImagePlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--docker-image-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-docker-image-plan-markdown"),
    },
  });
  assert.equal(dockerImagePlanMarkdownResult.status, 0, dockerImagePlanMarkdownResult.stderr || dockerImagePlanMarkdownResult.stdout);
  assert.match(dockerImagePlanMarkdownResult.stdout, /^# DDD Docker Image Evidence Plan/m);
  assert.match(dockerImagePlanMarkdownResult.stdout, /docker-runner-build/);
  assert.match(dockerImagePlanMarkdownResult.stdout, /existing-image-inspect/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-docker-image-plan-markdown.json")), false);

  const dockerImageSubmissionPlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--docker-image-submission-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-docker-image-submission-plan"),
    },
  });
  assert.equal(dockerImageSubmissionPlanResult.status, 0, dockerImageSubmissionPlanResult.stderr || dockerImageSubmissionPlanResult.stdout);
  const dockerImageSubmissionPlan = JSON.parse(dockerImageSubmissionPlanResult.stdout);
  assert.equal(dockerImageSubmissionPlan.status, "PASS");
  assert.equal(dockerImageSubmissionPlan.willWriteFiles, false);
  assert.equal(dockerImageSubmissionPlan.evidenceArtifact, "artifacts/ddd/build/docker-image-evidence.json");
  assert(dockerImageSubmissionPlan.submissionModes.some((item) => item.id === "docker-runner-build" && item.command.includes("DDD_DOCKER_BUILD_STRICT=true")));
  assert(dockerImageSubmissionPlan.submissionModes.some((item) => item.id === "existing-image-inspect" && item.workflowInputs.includes("DDD_DOCKER_EXISTING_FRONTEND_IMAGE")));
  assert(dockerImageSubmissionPlan.staticDockerfiles.some((item) => item.dockerfile === "deploy/docker/frontend.Dockerfile"));
  assert(dockerImageSubmissionPlan.validationCommands.includes("node scripts/ddd-docker-build-evidence.mjs --check"));
  assert.equal(dockerImageSubmissionPlan.laneReceiptFragment.owner, "release-infra");
  assert.equal(dockerImageSubmissionPlan.laneReceiptFragment.lane, "p0-docker-images");
  assert.equal(dockerImageSubmissionPlan.laneReceiptFragment.status, "PASS");
  assert(dockerImageSubmissionPlan.laneReceiptFragment.providedArtifacts.includes("artifacts/ddd/build/docker-image-evidence.json"));
  assert.equal(dockerImageSubmissionPlan.laneReceiptFragment.missingArtifacts.length, 0);
  assert(dockerImageSubmissionPlan.passCriteria.some((item) => item.includes("docker-images gate")));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-docker-image-submission-plan.json")), false);

  const dockerImageSubmissionPlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--docker-image-submission-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-docker-image-submission-plan-markdown"),
    },
  });
  assert.equal(dockerImageSubmissionPlanMarkdownResult.status, 0, dockerImageSubmissionPlanMarkdownResult.stderr || dockerImageSubmissionPlanMarkdownResult.stdout);
  assert.match(dockerImageSubmissionPlanMarkdownResult.stdout, /^# DDD Docker Image Submission Plan/m);
  assert.match(dockerImageSubmissionPlanMarkdownResult.stdout, /existing-image-inspect/);
  assert.match(dockerImageSubmissionPlanMarkdownResult.stdout, /DDD_DOCKER_EXISTING_FRONTEND_IMAGE/);
  assert.match(dockerImageSubmissionPlanMarkdownResult.stdout, /docker-image-evidence\.json/);
  assert.match(dockerImageSubmissionPlanMarkdownResult.stdout, /^## Lane Receipt Fragment/m);
  assert.match(dockerImageSubmissionPlanMarkdownResult.stdout, /"lane": "p0-docker-images"/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-docker-image-submission-plan-markdown.json")), false);

  const runtimeBusinessPlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--runtime-business-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-runtime-business-plan"),
    },
  });
  assert.equal(runtimeBusinessPlanResult.status, 0, runtimeBusinessPlanResult.stderr || runtimeBusinessPlanResult.stdout);
  const runtimeBusinessPlan = JSON.parse(runtimeBusinessPlanResult.stdout);
  assert.equal(runtimeBusinessPlan.status, "BLOCKED");
  assert.equal(runtimeBusinessPlan.willWriteFiles, false);
  assert.equal(runtimeBusinessPlan.runtimeCheck.status, "BLOCKED");
  assert(runtimeBusinessPlan.requiredEnv.urls.includes("LUMIRA_BASE_URL"));
  assert(runtimeBusinessPlan.requiredEnv.deploymentEvidence.includes("DDD_DEPLOYMENT_EVIDENCE"));
  assert(runtimeBusinessPlan.smokeSteps.some((step) => step.id === "frontend-smoke" && step.artifact === "artifacts/ddd/frontend/frontend-smoke.json"));
  assert(runtimeBusinessPlan.commands.validate.includes("node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance"));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-runtime-business-plan.json")), false);

  const runtimeBusinessPlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--runtime-business-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-runtime-business-plan-markdown"),
    },
  });
  assert.equal(runtimeBusinessPlanMarkdownResult.status, 0, runtimeBusinessPlanMarkdownResult.stderr || runtimeBusinessPlanMarkdownResult.stdout);
  assert.match(runtimeBusinessPlanMarkdownResult.stdout, /^# DDD P1 Runtime Business Plan/m);
  assert.match(runtimeBusinessPlanMarkdownResult.stdout, /`LUMIRA_BASE_URL`/);
  assert.match(runtimeBusinessPlanMarkdownResult.stdout, /frontend-smoke/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-runtime-business-plan-markdown.json")), false);

  const runtimeSmokePlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--runtime-smoke-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-runtime-smoke-plan"),
    },
  });
  assert.equal(runtimeSmokePlanResult.status, 0, runtimeSmokePlanResult.stderr || runtimeSmokePlanResult.stdout);
  const runtimeSmokePlan = JSON.parse(runtimeSmokePlanResult.stdout);
  assert.equal(runtimeSmokePlan.status, "BLOCKED");
  assert.equal(runtimeSmokePlan.willWriteFiles, false);
  assert(runtimeSmokePlan.phases.some((phase) => phase.id === "runtime-deployment-evidence" && phase.requiredInputs.includes("LUMIRA_BASE_URL")));
  assert(runtimeSmokePlan.phases.some((phase) => phase.id === "ai-runtime-evidence" && phase.owner === "ai"));
  assert(runtimeSmokePlan.phases.some((phase) => phase.id === "business-payment-smoke" && phase.artifacts.includes("artifacts/ddd/payment/payment-webhook-e2e.json")));
  assert(runtimeSmokePlan.parallelAfterDeployment.includes("frontend-runtime-smoke"));
  assert(runtimeSmokePlan.validationCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce"));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-runtime-smoke-plan.json")), false);

  const runtimeSmokePlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--runtime-smoke-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-runtime-smoke-plan-markdown"),
    },
  });
  assert.equal(runtimeSmokePlanMarkdownResult.status, 0, runtimeSmokePlanMarkdownResult.stderr || runtimeSmokePlanMarkdownResult.stdout);
  assert.match(runtimeSmokePlanMarkdownResult.stdout, /^# DDD Runtime Smoke Owner Plan/m);
  assert.match(runtimeSmokePlanMarkdownResult.stdout, /runtime-deployment-evidence/);
  assert.match(runtimeSmokePlanMarkdownResult.stdout, /business-payment-smoke/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-runtime-smoke-plan-markdown.json")), false);

  const runtimeBusinessSubmissionPlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--runtime-business-submission-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-runtime-business-submission-plan"),
    },
  });
  assert.equal(runtimeBusinessSubmissionPlanResult.status, 0, runtimeBusinessSubmissionPlanResult.stderr || runtimeBusinessSubmissionPlanResult.stdout);
  const runtimeBusinessSubmissionPlan = JSON.parse(runtimeBusinessSubmissionPlanResult.stdout);
  assert.equal(runtimeBusinessSubmissionPlan.status, "BLOCKED");
  assert.equal(runtimeBusinessSubmissionPlan.willWriteFiles, false);
  assert.equal(runtimeBusinessSubmissionPlan.gate, "runtime-business");
  assert(runtimeBusinessSubmissionPlan.deploymentSubmission.requiredInputs.includes("LUMIRA_BASE_URL"));
  assert(runtimeBusinessSubmissionPlan.ownerSubmissions.some((item) => item.phase === "frontend-runtime-smoke" && item.owner === "frontend"));
  assert(runtimeBusinessSubmissionPlan.ownerSubmissions.some((item) => item.phase === "business-payment-smoke" && item.artifacts.includes("artifacts/ddd/payment/payment-webhook-e2e.json")));
  assert(runtimeBusinessSubmissionPlan.expectedArtifacts.includes("artifacts/ddd/frontend/frontend-smoke.json"));
  assert.equal(runtimeBusinessSubmissionPlan.laneReceiptFragment.owner, "release-infra");
  assert.equal(runtimeBusinessSubmissionPlan.laneReceiptFragment.lane, "p1-runtime-business");
  assert(runtimeBusinessSubmissionPlan.laneReceiptFragment.providedArtifacts.includes("artifacts/ddd/payment/payment-webhook-e2e.json"));
  assert(runtimeBusinessSubmissionPlan.laneReceiptFragment.missingArtifacts.includes("artifacts/ddd/frontend/frontend-smoke.json"));
  assert(runtimeBusinessSubmissionPlan.passCriteria.some((item) => item.includes("runtime-business gate")));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-runtime-business-submission-plan.json")), false);

  const runtimeBusinessSubmissionPlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--runtime-business-submission-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-runtime-business-submission-plan-markdown"),
    },
  });
  assert.equal(runtimeBusinessSubmissionPlanMarkdownResult.status, 0, runtimeBusinessSubmissionPlanMarkdownResult.stderr || runtimeBusinessSubmissionPlanMarkdownResult.stdout);
  assert.match(runtimeBusinessSubmissionPlanMarkdownResult.stdout, /^# DDD Runtime Business Submission Plan/m);
  assert.match(runtimeBusinessSubmissionPlanMarkdownResult.stdout, /runtime-deployment-evidence/);
  assert.match(runtimeBusinessSubmissionPlanMarkdownResult.stdout, /frontend-runtime-smoke/);
  assert.match(runtimeBusinessSubmissionPlanMarkdownResult.stdout, /payment-webhook-e2e\.json/);
  assert.match(runtimeBusinessSubmissionPlanMarkdownResult.stdout, /^## Lane Receipt Fragment/m);
  assert.match(runtimeBusinessSubmissionPlanMarkdownResult.stdout, /"lane": "p1-runtime-business"/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-runtime-business-submission-plan-markdown.json")), false);

  const dataSafetyPlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--data-safety-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-data-safety-plan"),
    },
  });
  assert.equal(dataSafetyPlanResult.status, 0, dataSafetyPlanResult.stderr || dataSafetyPlanResult.stdout);
  const dataSafetyPlan = JSON.parse(dataSafetyPlanResult.stdout);
  assert.equal(dataSafetyPlan.status, "BLOCKED");
  assert.equal(dataSafetyPlan.willWriteFiles, false);
  assert(dataSafetyPlan.trackPlans.some((track) => track.id === "rollback" && track.requiredInputs.includes("DDD_ROLLBACK_DRILL_FILE")));
  assert(dataSafetyPlan.trackPlans.some((track) => track.id === "migration" && track.artifacts.includes("artifacts/ddd/migration/migration-evidence.json")));
  assert(dataSafetyPlan.trackPlans.some((track) => track.id === "explain" && track.commands.includes("DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs")));
  assert(dataSafetyPlan.commands.validate.includes("node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce"));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-data-safety-plan.json")), false);

  const dataSafetyPlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--data-safety-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-data-safety-plan-markdown"),
    },
  });
  assert.equal(dataSafetyPlanMarkdownResult.status, 0, dataSafetyPlanMarkdownResult.stderr || dataSafetyPlanMarkdownResult.stdout);
  assert.match(dataSafetyPlanMarkdownResult.stdout, /^# DDD Data Safety Plan/m);
  assert.match(dataSafetyPlanMarkdownResult.stdout, /rollback/);
  assert.match(dataSafetyPlanMarkdownResult.stdout, /DDD_EXPLAIN_DATABASE/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-data-safety-plan-markdown.json")), false);

  const dataSafetyOwnerPlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--data-safety-owner-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-data-safety-owner-plan"),
    },
  });
  assert.equal(dataSafetyOwnerPlanResult.status, 0, dataSafetyOwnerPlanResult.stderr || dataSafetyOwnerPlanResult.stdout);
  const dataSafetyOwnerPlan = JSON.parse(dataSafetyOwnerPlanResult.stdout);
  assert.equal(dataSafetyOwnerPlan.status, "BLOCKED");
  assert.equal(dataSafetyOwnerPlan.willWriteFiles, false);
  assert(dataSafetyOwnerPlan.phases.some((phase) => phase.id === "rollback-evidence-source" && phase.requiredInputs.some((input) => input.includes("DDD_ROLLBACK_DRILL_FILE"))));
  assert(dataSafetyOwnerPlan.phases.some((phase) => phase.id === "migration-upgrade-drill" && phase.dependencies.includes("migration-fresh-drill")));
  assert(dataSafetyOwnerPlan.phases.some((phase) => phase.id === "explain-gate" && phase.commands.includes("DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs")));
  assert(dataSafetyOwnerPlan.parallelStart.includes("rollback-evidence-source"));
  assert(dataSafetyOwnerPlan.validationCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce"));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-data-safety-owner-plan.json")), false);

  const dataSafetyOwnerPlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--data-safety-owner-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-data-safety-owner-plan-markdown"),
    },
  });
  assert.equal(dataSafetyOwnerPlanMarkdownResult.status, 0, dataSafetyOwnerPlanMarkdownResult.stderr || dataSafetyOwnerPlanMarkdownResult.stdout);
  assert.match(dataSafetyOwnerPlanMarkdownResult.stdout, /^# DDD Data Safety Owner Plan/m);
  assert.match(dataSafetyOwnerPlanMarkdownResult.stdout, /rollback-evidence-source/);
  assert.match(dataSafetyOwnerPlanMarkdownResult.stdout, /explain-gate/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-data-safety-owner-plan-markdown.json")), false);

  const dataSafetySubmissionPlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--data-safety-submission-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-data-safety-submission-plan"),
    },
  });
  assert.equal(dataSafetySubmissionPlanResult.status, 0, dataSafetySubmissionPlanResult.stderr || dataSafetySubmissionPlanResult.stdout);
  const dataSafetySubmissionPlan = JSON.parse(dataSafetySubmissionPlanResult.stdout);
  assert.equal(dataSafetySubmissionPlan.status, "BLOCKED");
  assert.equal(dataSafetySubmissionPlan.willWriteFiles, false);
  assert.equal(dataSafetySubmissionPlan.gate, "data-safety");
  assert(dataSafetySubmissionPlan.ownerSubmissions.some((item) => item.phase === "rollback-evidence-source" && item.owner === "bounded-context owners"));
  assert(dataSafetySubmissionPlan.ownerSubmissions.some((item) => item.phase === "explain-collect" && item.artifacts.includes("tmp/ddd-explain/*.json")));
  assert(dataSafetySubmissionPlan.explainArtifact.envTemplate.includes("MYSQL_PASSWORD=__SECRET_REFERENCE_ONLY__"));
  assert(dataSafetySubmissionPlan.expectedArtifacts.includes("artifacts/ddd/release/explain-gate-report.json"));
  assert.equal(dataSafetySubmissionPlan.laneReceiptFragment.owner, "platform-owners");
  assert.equal(dataSafetySubmissionPlan.laneReceiptFragment.lane, "p1-p2-data-safety");
  assert.equal(dataSafetySubmissionPlan.laneReceiptFragment.status, "BLOCKED");
  assert(dataSafetySubmissionPlan.laneReceiptFragment.providedArtifacts.includes("artifacts/ddd/release/explain-gate-report.json"));
  assert(dataSafetySubmissionPlan.laneReceiptFragment.missingArtifacts.includes("tmp/ddd-explain/*.json"));
  assert(dataSafetySubmissionPlan.laneReceiptFragment.acceptanceCommands.includes("node scripts/ddd-staging-data-safety-check.mjs"));
  assert(dataSafetySubmissionPlan.passCriteria.some((item) => item.includes("rollback, migration, or explain gates")));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-data-safety-submission-plan.json")), false);

  const dataSafetySubmissionPlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--data-safety-submission-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-data-safety-submission-plan-markdown"),
    },
  });
  assert.equal(dataSafetySubmissionPlanMarkdownResult.status, 0, dataSafetySubmissionPlanMarkdownResult.stderr || dataSafetySubmissionPlanMarkdownResult.stdout);
  assert.match(dataSafetySubmissionPlanMarkdownResult.stdout, /^# DDD Data Safety Submission Plan/m);
  assert.match(dataSafetySubmissionPlanMarkdownResult.stdout, /rollback-evidence-source/);
  assert.match(dataSafetySubmissionPlanMarkdownResult.stdout, /MYSQL_PASSWORD=__SECRET_REFERENCE_ONLY__/);
  assert.match(dataSafetySubmissionPlanMarkdownResult.stdout, /explain-gate-report\.json/);
  assert.match(dataSafetySubmissionPlanMarkdownResult.stdout, /^## Lane Receipt Fragment/m);
  assert.match(dataSafetySubmissionPlanMarkdownResult.stdout, /"lane": "p1-p2-data-safety"/);
  assert.match(dataSafetySubmissionPlanMarkdownResult.stdout, /"completedAt": "<ISO-8601 timestamp after validation commands pass>"/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-data-safety-submission-plan-markdown.json")), false);

  const cutoverRehearsalPlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--cutover-rehearsal-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-cutover-rehearsal-plan"),
    },
  });
  assert.equal(cutoverRehearsalPlanResult.status, 0, cutoverRehearsalPlanResult.stderr || cutoverRehearsalPlanResult.stdout);
  const cutoverRehearsalPlan = JSON.parse(cutoverRehearsalPlanResult.stdout);
  assert.equal(cutoverRehearsalPlan.status, "BLOCKED");
  assert.equal(cutoverRehearsalPlan.cutoverReady, false);
  assert.equal(cutoverRehearsalPlan.willWriteFiles, false);
  assert(cutoverRehearsalPlan.phases.some((phase) => phase.id === "p0-release-env" && phase.commands.includes("node scripts/ddd-release-env-init.mjs --check")));
  assert(cutoverRehearsalPlan.phases.some((phase) => phase.id === "p2-explain" && phase.artifacts.includes("artifacts/ddd/release/explain-gate-report.json")));
  assert(cutoverRehearsalPlan.validationCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce"));
  assert.equal(cutoverRehearsalPlan.nextPhase.id, "p0-release-env");
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-cutover-rehearsal-plan.json")), false);

  const cutoverRehearsalPlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--cutover-rehearsal-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-cutover-rehearsal-plan-markdown"),
    },
  });
  assert.equal(cutoverRehearsalPlanMarkdownResult.status, 0, cutoverRehearsalPlanMarkdownResult.stderr || cutoverRehearsalPlanMarkdownResult.stdout);
  assert.match(cutoverRehearsalPlanMarkdownResult.stdout, /^# DDD Cutover Rehearsal Plan/m);
  assert.match(cutoverRehearsalPlanMarkdownResult.stdout, /Secure release env initialized and linted/);
  assert.match(cutoverRehearsalPlanMarkdownResult.stdout, /final-review-enforce/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-cutover-rehearsal-plan-markdown.json")), false);

  const blockingInputsResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--blocking-inputs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-blocking-inputs"),
    },
  });
  assert.equal(blockingInputsResult.status, 0, blockingInputsResult.stderr || blockingInputsResult.stdout);
  const blockingInputs = JSON.parse(blockingInputsResult.stdout);
  assert.equal(blockingInputs.status, "BLOCKED");
  assert.equal(blockingInputs.willWriteFiles, false);
  assert(blockingInputs.inputCount > 0);
  assert(blockingInputs.inputs.some((input) => input.input === "LUMIRA_BASE_URL" && input.gates.some((gate) => gate.gate === "runtime-business")));
  assert(blockingInputs.inputs.some((input) => input.input === "DDD_EVIDENCE_OPERATOR" && input.gateCount >= 2));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-blocking-inputs.json")), false);

  const blockingInputsMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--blocking-inputs-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-blocking-inputs-markdown"),
    },
  });
  assert.equal(blockingInputsMarkdownResult.status, 0, blockingInputsMarkdownResult.stderr || blockingInputsMarkdownResult.stdout);
  assert.match(blockingInputsMarkdownResult.stdout, /^# DDD Staging Blocking Inputs/m);
  assert.match(blockingInputsMarkdownResult.stdout, /`LUMIRA_BASE_URL`/);
  assert.match(blockingInputsMarkdownResult.stdout, /runtime-business/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-blocking-inputs-markdown.json")), false);

  const blockingInputsEnvTemplateResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--blocking-inputs-env-template"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-blocking-inputs-env-template"),
    },
  });
  assert.equal(blockingInputsEnvTemplateResult.status, 0, blockingInputsEnvTemplateResult.stderr || blockingInputsEnvTemplateResult.stdout);
  assert.match(blockingInputsEnvTemplateResult.stdout, /^# Lumira DDD staging blocking input environment template\./);
  assert.match(blockingInputsEnvTemplateResult.stdout, /^LUMIRA_BASE_URL=__REQUIRED_HTTPS__$/m);
  assert.match(blockingInputsEnvTemplateResult.stdout, /^DDD_MIGRATION_FRESH_DB_VALIDATED=true$/m);
  assert.match(blockingInputsEnvTemplateResult.stdout, /^MYSQL_PASSWORD=__REQUIRED_SECRET_REF__$/m);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-blocking-inputs-env-template.json")), false);

  const ownerBlockingInputsResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--blocking-inputs",
    "--owner=release-infra",
  ], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-blocking-inputs-release-infra"),
    },
  });
  assert.equal(ownerBlockingInputsResult.status, 0, ownerBlockingInputsResult.stderr || ownerBlockingInputsResult.stdout);
  const ownerBlockingInputs = JSON.parse(ownerBlockingInputsResult.stdout);
  assert.equal(ownerBlockingInputs.ownerFilter, "release-infra");
  assert.equal(ownerBlockingInputs.blockedGateCount, 2);
  assert.equal(ownerBlockingInputs.inputs.some((input) => input.input === "DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE"), false);
  assert(ownerBlockingInputs.inputs.some((input) => input.input === "LUMIRA_BASE_URL"));
  assert.equal(ownerBlockingInputs.inputs.some((input) => input.input === "MYSQL_PASSWORD"), false);

  const ownerBlockingInputsEnvTemplateResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--blocking-inputs-env-template",
    "--owner=release-infra",
  ], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-blocking-inputs-release-infra-env"),
    },
  });
  assert.equal(ownerBlockingInputsEnvTemplateResult.status, 0, ownerBlockingInputsEnvTemplateResult.stderr || ownerBlockingInputsEnvTemplateResult.stdout);
  assert.match(ownerBlockingInputsEnvTemplateResult.stdout, /^# Owner filter: release-infra$/m);
  assert.match(ownerBlockingInputsEnvTemplateResult.stdout, /^DDD_RELEASE_ENV_FILE=__REQUIRED__$/m);
  assert.match(ownerBlockingInputsEnvTemplateResult.stdout, /^LUMIRA_BASE_URL=__REQUIRED_HTTPS__$/m);
  assert.doesNotMatch(ownerBlockingInputsEnvTemplateResult.stdout, /^MYSQL_PASSWORD=/m);

  const releaseEvidenceDispatchPlanResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-evidence-dispatch-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-evidence-dispatch-plan"),
    },
  });
  assert.equal(releaseEvidenceDispatchPlanResult.status, 0, releaseEvidenceDispatchPlanResult.stderr || releaseEvidenceDispatchPlanResult.stdout);
  const releaseEvidenceDispatchPlan = JSON.parse(releaseEvidenceDispatchPlanResult.stdout);
  assert.equal(releaseEvidenceDispatchPlan.status, "BLOCKED");
  assert.equal(releaseEvidenceDispatchPlan.workflowDispatch, true);
  assert(releaseEvidenceDispatchPlan.inputs.some((input) => input.input === "backend_base_url" && input.source === "LUMIRA_BASE_URL"));
  assert(releaseEvidenceDispatchPlan.inputs.some((input) => input.input === "lane_completion_receipt_base64" && input.status === "BLOCKED"));
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-evidence-dispatch-plan.json")), false);

  const releaseEvidenceDispatchPlanMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-evidence-dispatch-plan-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-evidence-dispatch-plan-markdown"),
    },
  });
  assert.equal(releaseEvidenceDispatchPlanMarkdownResult.status, 0, releaseEvidenceDispatchPlanMarkdownResult.stderr || releaseEvidenceDispatchPlanMarkdownResult.stdout);
  assert.match(releaseEvidenceDispatchPlanMarkdownResult.stdout, /^# DDD Release Evidence Dispatch Plan/m);
  assert.match(releaseEvidenceDispatchPlanMarkdownResult.stdout, /## Required Before Run/);
  assert.match(releaseEvidenceDispatchPlanMarkdownResult.stdout, /lane_completion_receipt_base64/);
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-evidence-dispatch-plan-markdown.json")), false);

  const releaseEvidenceDispatchInputsResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-evidence-dispatch-inputs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-evidence-dispatch-inputs"),
    },
  });
  assert.equal(releaseEvidenceDispatchInputsResult.status, 0, releaseEvidenceDispatchInputsResult.stderr || releaseEvidenceDispatchInputsResult.stdout);
  const releaseEvidenceDispatchInputs = JSON.parse(releaseEvidenceDispatchInputsResult.stdout);
  assert.equal(releaseEvidenceDispatchInputs.status, "BLOCKED");
  assert.equal(releaseEvidenceDispatchInputs.payload.mode, "plan");
  assert.equal(releaseEvidenceDispatchInputs.payload.backend_base_url, "__REQUIRED_HTTPS__");
  assert.equal(releaseEvidenceDispatchInputs.payload.lane_completion_receipt_base64, "__REQUIRED_AFTER_COVERAGE_5_OF_5__");
  assert(releaseEvidenceDispatchInputs.validationCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>"));
  assert.equal(releaseEvidenceDispatchInputs.dispatchInputValidationCommand, "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-inputs-contract --release-evidence-dispatch-inputs-file=<inputs-file>");
  assert.equal(fs.existsSync(path.join(tmpDir, "staging-checklist-release-evidence-dispatch-inputs.json")), false);

  const releaseEvidenceDispatchCommandResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--release-evidence-dispatch-command"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-release-evidence-dispatch-command"),
    },
  });
  assert.equal(releaseEvidenceDispatchCommandResult.status, 0, releaseEvidenceDispatchCommandResult.stderr || releaseEvidenceDispatchCommandResult.stdout);
  assert.match(releaseEvidenceDispatchCommandResult.stdout, /^gh workflow run ddd-release-evidence\.yml \\/m);
  assert.match(releaseEvidenceDispatchCommandResult.stdout, /-f backend_base_url=__REQUIRED_HTTPS__/);
  assert.match(releaseEvidenceDispatchCommandResult.stdout, /-f lane_completion_receipt_base64=__REQUIRED_AFTER_COVERAGE_5_OF_5__/);

  const dispatchInputsTemplateFile = path.join(tmpDir, "release-evidence-dispatch-inputs.template.json");
  fs.writeFileSync(dispatchInputsTemplateFile, releaseEvidenceDispatchInputsResult.stdout);
  const releaseEvidenceDispatchInputsTemplateContractResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--release-evidence-dispatch-inputs-contract",
    `--release-evidence-dispatch-inputs-file=${dispatchInputsTemplateFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.notEqual(releaseEvidenceDispatchInputsTemplateContractResult.status, 0, "placeholder dispatch inputs must fail contract");
  const releaseEvidenceDispatchInputsTemplateContract = JSON.parse(releaseEvidenceDispatchInputsTemplateContractResult.stdout);
  assert.equal(releaseEvidenceDispatchInputsTemplateContract.status, "FAIL");
  assert(releaseEvidenceDispatchInputsTemplateContract.issues.some((issue) => issue.includes("backend_base_url must replace placeholder value")));

  const dispatchInputsReadyFile = path.join(tmpDir, "release-evidence-dispatch-inputs.ready.json");
  fs.writeFileSync(dispatchInputsReadyFile, JSON.stringify({
    payload: {
      ...releaseEvidenceDispatchInputs.payload,
      mode: "run",
      backend_base_url: "https://api.staging.example.com",
      frontend_base_url: "https://app.staging.example.com",
      lane_completion_receipt_base64: "eyJyZWRhY3RlZCI6dHJ1ZX0=",
    },
  }, null, 2));
  const releaseEvidenceDispatchInputsReadyContractResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--release-evidence-dispatch-inputs-contract",
    `--release-evidence-dispatch-inputs-file=${dispatchInputsReadyFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(releaseEvidenceDispatchInputsReadyContractResult.status, 0, releaseEvidenceDispatchInputsReadyContractResult.stderr || releaseEvidenceDispatchInputsReadyContractResult.stdout);
  const releaseEvidenceDispatchInputsReadyContract = JSON.parse(releaseEvidenceDispatchInputsReadyContractResult.stdout);
  assert.equal(releaseEvidenceDispatchInputsReadyContract.status, "PASS");

  const executionStatusMissingBundleResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--execution-status"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: path.join(tmpDir, "missing-staging-handoff-bundle"),
    },
  });
  assert.equal(executionStatusMissingBundleResult.status, 0, executionStatusMissingBundleResult.stderr || executionStatusMissingBundleResult.stdout);
  const executionStatusMissingBundle = JSON.parse(executionStatusMissingBundleResult.stdout);
  assert.equal(executionStatusMissingBundle.status, "BLOCKED");
  assert.equal(executionStatusMissingBundle.willWriteFiles, false);
  assert.equal(executionStatusMissingBundle.handoffBundle.status, "BLOCKED");
  assert.match(executionStatusMissingBundle.handoffBundle.issues[0], /missing or invalid manifest/);

  const handoffBundleResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(handoffBundleResult.status, 0, handoffBundleResult.stderr || handoffBundleResult.stdout);
  assert.match(handoffBundleResult.stdout, /handoffBundle=/);
  assert.match(handoffBundleResult.stdout, /ownerPackets=5/);
  for (const file of ["README.md", "rollup.json", "rollup.md", "handoff-summary.md", "execution-status.json", "execution-status.md", "final-review.json", "final-review.md", "release-owner-closeout.json", "release-owner-closeout.md", "production-closeout-status.json", "production-closeout-status.md", "production-unblock-quickstart.md", "production-unblock-plan.json", "production-unblock-plan.md", "production-evidence-readiness.json", "production-evidence-readiness.md", "production-cutover-audit.json", "production-cutover-audit.md", "operator-progress.json", "operator-progress.md", "daily-brief.json", "daily-brief.md", "closure-plan.json", "closure-plan.md", "next-action-queue.json", "next-action-queue.md", "owner-lane-matrix.json", "owner-lane-matrix.md", "lane-completion-receipt.template.json", "lane-completion-receipt.template.md", "lane-completion-receipt.coverage.json", "lane-completion-receipt.coverage.md", "evidence-closure-board.json", "evidence-closure-board.md", "evidence-closure-board.csv", "lane-receipt-fragments.json", "lane-receipt-fragments.md", "lane-receipt-draft.json", "lane-receipt-draft.md", "owner-evidence-intake.json", "owner-evidence-intake.md", "lane-completion-submission-plan.json", "lane-completion-submission-plan.md", "lane-completion-submission-check.json", "lane-completion-submission-check.md", "next-action.template.env", "next-action-env-receipt.sample.json", "next-action-env-receipt.sample.md", "next-action-verification-plan.json", "next-action-verification-plan.md", "release-env-plan.json", "release-env-plan.md", "release-env-owner-matrix.json", "release-env-owner-matrix.md", "release-env-next-owner.template.env", "release-env-merge-plan.json", "release-env-merge-plan.md", "release-env-submission-plan.json", "release-env-submission-plan.md", "release-env-fill-checklist.json", "release-env-fill-checklist.md", "release-env-fill.template.env", "docker-image-plan.json", "docker-image-plan.md", "docker-image-submission-plan.json", "docker-image-submission-plan.md", "runtime-business-plan.json", "runtime-business-plan.md", "runtime-smoke-plan.json", "runtime-smoke-plan.md", "runtime-business-submission-plan.json", "runtime-business-submission-plan.md", "data-safety-plan.json", "data-safety-plan.md", "data-safety-owner-plan.json", "data-safety-owner-plan.md", "data-safety-submission-plan.json", "data-safety-submission-plan.md", "cutover-rehearsal-plan.json", "cutover-rehearsal-plan.md", "evidence-gaps.json", "evidence-runbook.json", "evidence-runbook.md", "evidence-acceptance.json", "evidence-acceptance.md", "evidence-artifact-gaps.json", "evidence-artifact-gaps.md", "explain-artifact-plan.json", "explain-artifact-plan.md", "blocking-inputs.json", "blocking-inputs.md", "blocking-inputs.template.env", "release-evidence-dispatch-plan.json", "release-evidence-dispatch-plan.md", "release-evidence-dispatch-inputs.json", "release-evidence-dispatch-command.sh", "evidence-env.template.env", "commands.txt", "owner-dispatch.json", "manifest.json"]) {
    assert.equal(fs.existsSync(path.join(handoffBundleDir, file)), true, `handoff bundle should write ${file}`);
  }
  const bundleOwnerPacketDir = path.join(handoffBundleDir, "owner-packets");
  assert.equal(fs.existsSync(path.join(bundleOwnerPacketDir, "README.md")), true, "handoff bundle should include owner packet index");
  assert.equal(fs.existsSync(path.join(bundleOwnerPacketDir, "release-infra.md")), true, "handoff bundle should include release-infra packet");
  assert.equal(fs.existsSync(path.join(bundleOwnerPacketDir, "release-infra.json")), true, "handoff bundle should include release-infra JSON packet");
  assert.equal(fs.existsSync(path.join(bundleOwnerPacketDir, "release-infra.blocking-inputs.template.env")), true, "handoff bundle should include release-infra blocking input env template");
  const bundleOwnerDispatch = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "owner-dispatch.json"), "utf8"));
  const bundleReleaseInfraDispatch = bundleOwnerDispatch.owners.find((owner) => owner.owner === "release-infra");
  assert.equal(bundleReleaseInfraDispatch.laneCount, 4);
  assert(bundleReleaseInfraDispatch.lanes.some((lane) => lane.lane === "p0-docker-images" && lane.sourcePlan === "docker-image-submission-plan.json"));
  assert(bundleReleaseInfraDispatch.lanes.some((lane) => lane.lane === "p1-runtime-business" && lane.command === "node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown"));
  const bundlePlatformOwnersDispatch = bundleOwnerDispatch.owners.find((owner) => owner.owner === "platform-owners");
  assert.equal(bundlePlatformOwnersDispatch.laneCount, 1);
  assert(bundlePlatformOwnersDispatch.lanes.some((lane) => lane.lane === "p1-p2-data-safety" && lane.sourcePlan === "data-safety-submission-plan.json"));
  const bundleOwnerPacketIndex = fs.readFileSync(path.join(bundleOwnerPacketDir, "README.md"), "utf8");
  assert.match(bundleOwnerPacketIndex, /release-infra\.json/);
  assert.match(bundleOwnerPacketIndex, /release-infra\.blocking-inputs\.template\.env/);
  const bundlePlatformOwnersPacket = fs.readFileSync(path.join(bundleOwnerPacketDir, "platform-owners.md"), "utf8");
  assert.match(bundlePlatformOwnersPacket, /Edit rule: update only this owner's laneReceipts entries/);
  assert.match(bundlePlatformOwnersPacket, /Lane keys: `platform-owners:p1-p2-data-safety`/);
  assert.match(bundlePlatformOwnersPacket, /--lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>/);
  assert.match(bundlePlatformOwnersPacket, /--lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>/);
  const bundleReleaseInfraPacket = JSON.parse(fs.readFileSync(path.join(bundleOwnerPacketDir, "release-infra.json"), "utf8"));
  assert(bundleReleaseInfraPacket.receiptWorkflow.laneKeys.includes("release-infra:final-review"));
  assert(bundleReleaseInfraPacket.commands.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>"));
  assert(bundleReleaseInfraPacket.commands.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>"));
  const bundleReadme = fs.readFileSync(path.join(handoffBundleDir, "README.md"), "utf8");
  assert.match(bundleReadme, /owner-packets\//);
  assert.match(bundleReadme, /owner-scoped missing artifact handoff packets/);
  assert.match(bundleReadme, /handoff-summary\.md/);
  assert.match(bundleReadme, /execution-status\.json/);
  assert.match(bundleReadme, /final-review\.json/);
  assert.match(bundleReadme, /release-owner-closeout\.json/);
  assert.match(bundleReadme, /production-closeout-status\.json/);
  assert.match(bundleReadme, /production-unblock-quickstart\.md/);
  assert.match(bundleReadme, /production-unblock-plan\.json/);
  assert.match(bundleReadme, /production-evidence-readiness\.json/);
  assert.match(bundleReadme, /release-env-fill\.template\.env/);
  const bundleReleaseEnvFillTemplatePreview = fs.readFileSync(path.join(handoffBundleDir, "release-env-fill.template.env"), "utf8");
  assert.match(bundleReleaseEnvFillTemplatePreview, /^# P0 release env fill template\./m);
  assert.match(bundleReleaseEnvFillTemplatePreview, /^LUMIRA_BASE_URL=__REQUIRED_HTTPS__$/m);
  assert.match(bundleReleaseEnvFillTemplatePreview, /^JWT_SECRET=__REQUIRED_SECRET_REF__$/m);
  assert.match(bundleReleaseEnvFillTemplatePreview, /^# DDD_RELEASE_ENV_FILE=.env.release.local node scripts\/ddd-release-env-file-lint\.mjs$/m);
  assert.doesNotMatch(bundleReleaseEnvFillTemplatePreview, /real-/);
  assert.match(bundleReadme, /production-cutover-audit\.json/);
  assert.match(bundleReadme, /operator-progress\.json/);
  assert.match(bundleReadme, /daily-brief\.json/);
  assert.match(bundleReadme, /closure-plan\.json/);
  assert.match(bundleReadme, /next-action-queue\.json/);
  assert.match(bundleReadme, /owner-lane-matrix\.json/);
  assert.match(bundleReadme, /lane-completion-receipt\.template\.json/);
  assert.match(bundleReadme, /lane-completion-receipt\.coverage\.json/);
  assert.match(bundleReadme, /evidence-closure-board\.json/);
  assert.match(bundleReadme, /evidence-closure-board\.csv/);
  assert.match(bundleReadme, /lane-receipt-fragments\.json/);
  assert.match(bundleReadme, /lane-receipt-fragments\.md/);
  assert.match(bundleReadme, /5-lane receipt assembly index/);
  assert.match(bundleReadme, /lane-receipt-draft\.json/);
  assert.match(bundleReadme, /lane-receipt-draft\.md/);
  assert.match(bundleReadme, /lane completion receipt draft/);
  assert.match(bundleReadme, /owner-evidence-intake\.json/);
  assert.match(bundleReadme, /owner-evidence-intake\.md/);
  assert.match(bundleReadme, /owner evidence intake checklist/);
  assert.match(bundleReadme, /--evidence-closure-board-markdown/);
  assert.match(bundleReadme, /--evidence-closure-board-csv/);
  assert.match(bundleReadme, /lane-completion-submission-plan\.json/);
  assert.match(bundleReadme, /lane-completion-submission-check\.json/);
  assert.match(bundleReadme, /lane receipt submission readiness verdict/);
  assert.match(bundleReadme, /next-action\.template\.env/);
  assert.match(bundleReadme, /next-action-env-receipt\.sample\.json/);
  assert.match(bundleReadme, /next-action-verification-plan\.json/);
  assert.match(bundleReadme, /## Operator Quick Start/);
  assert.match(bundleReadme, /Read `production-closeout-status\.md` first/);
  assert.match(bundleReadme, /`## Lane Completion Submission` receipt readiness/);
  assert.match(bundleReadme, /`## Parallel Next Actions`/);
  assert.match(bundleReadme, /first-wave env, lane receipt, and owner evidence are parallel blockers and none of them waive the others/);
  assert.match(bundleReadme, /production-cutover-audit\.md` before final approval/);
  assert.match(bundleReadme, /production-unblock-quickstart\.md` when the audit is still `NO_GO_STRICT`/);
  assert.match(bundleReadme, /production-unblock-plan\.md` as the focused production unblock checklist/);
  assert.match(bundleReadme, /production-evidence-readiness\.md` to verify env receipt, lane receipt, owner evidence, production audit, and final go\/no-go evidence in one table/);
  assert.match(bundleReadme, /--production-evidence-readiness-enforce/);
  assert.match(bundleReadme, /## Status Views/);
  assert.match(bundleReadme, /`production-closeout-status\.md`: top-level closeout status with ETA band, next owner action, blocked stages, receipt submission readiness, and production preconditions/);
  assert.match(bundleReadme, /`production-unblock-plan\.md`: paste-ready focused plan for the parallel unblock workstreams and exit criteria/);
  assert.match(bundleReadme, /`production-evidence-readiness\.md`: paste-ready aggregate readiness for env, lane receipt, owner evidence, audit, and final go\/no-go evidence/);
  assert.match(bundleReadme, /`production-cutover-audit\.md`: final production cutover audit matrix/);
  assert.match(bundleReadme, /`daily-brief\.md`: release-owner daily triage with `## Lane Routes`/);
  assert.match(bundleReadme, /`operator-progress\.md`: shift handoff view with receipt coverage, missing artifacts, and `## Lane Routes`/);
  assert.match(bundleReadme, /`execution-status\.md`: compact gate and bundle status with `## Lane Routes`/);
  assert.match(bundleReadme, /`final-review\.md`: go\/no-go review with owner packet status and `## Owner Lane Routes`/);
  assert.match(bundleReadme, /## Receipt Coverage Gate/);
  assert.match(bundleReadme, /--lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>/);
  assert.match(bundleReadme, /Coverage: 5\/5/);
  assert.match(bundleReadme, /--next-action-env-check --next-action-env-file=<env-file>/);
  assert.match(bundleReadme, /--operator-progress-markdown --next-action-env-receipt-file=<receipt-file>/);
  assert.match(bundleReadme, /--lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>/);
  assert.match(bundleReadme, /--lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>/);
  assert.match(bundleReadme, /--lane-completion-submission-plan-markdown/);
  assert.match(bundleReadme, /--lane-completion-submission-check-markdown --lane-completion-receipt-file=<receipt-file>/);
  assert.match(bundleReadme, /--operator-progress-markdown --lane-completion-receipt-file=<receipt-file>/);
  assert.match(bundleReadme, /--release-owner-daily-brief-markdown/);
  assert.match(bundleReadme, /--final-review-enforce --lane-completion-receipt-file=<receipt-file>/);
  assert.match(bundleReadme, /--final-review-enforce/);
  assert.match(bundleReadme, /release-env-plan\.json/);
  assert.match(bundleReadme, /release-env-owner-matrix\.json/);
  assert.match(bundleReadme, /release-env-next-owner\.template\.env/);
  assert.match(bundleReadme, /release-env-merge-plan\.json/);
  assert.match(bundleReadme, /release-env-submission-plan\.json/);
  assert.match(bundleReadme, /release-env-fill-checklist\.json/);
  assert.match(bundleReadme, /release-env-fill\.template\.env/);
  assert.match(bundleReadme, /P0 release env blocker key checklist/);
  assert.match(bundleReadme, /P0 release env fill template/);
  assert.match(bundleReadme, /ddd-release-env-fill-checklist\.mjs --markdown/);
  assert.match(bundleReadme, /ddd-release-env-fill-checklist\.mjs --env-template/);
  assert.match(bundleReadme, /--release-env-submission-plan-markdown/);
  assert.match(bundleReadme, /docker-image-plan\.json/);
  assert.match(bundleReadme, /docker-image-submission-plan\.json/);
  assert.match(bundleReadme, /--docker-image-submission-plan-markdown/);
  assert.match(bundleReadme, /runtime-business-plan\.json/);
  assert.match(bundleReadme, /runtime-smoke-plan\.json/);
  assert.match(bundleReadme, /runtime-business-submission-plan\.json/);
  assert.match(bundleReadme, /--runtime-business-submission-plan-markdown/);
  assert.match(bundleReadme, /data-safety-plan\.json/);
  assert.match(bundleReadme, /data-safety-owner-plan\.json/);
  assert.match(bundleReadme, /data-safety-submission-plan\.json/);
  assert.match(bundleReadme, /--data-safety-submission-plan-markdown/);
  assert.match(bundleReadme, /cutover-rehearsal-plan\.json/);
  assert.match(bundleReadme, /evidence-gaps\.json/);
  assert.match(bundleReadme, /evidence-runbook\.json/);
  assert.match(bundleReadme, /evidence-acceptance\.json/);
  assert.match(bundleReadme, /evidence-artifact-gaps\.json/);
  assert.match(bundleReadme, /explain-artifact-plan\.json/);
  assert.match(bundleReadme, /--explain-artifact-plan-markdown/);
  assert.match(bundleReadme, /blocking-inputs\.json/);
  assert.match(bundleReadme, /blocking-inputs\.template\.env/);
  assert.match(bundleReadme, /owner-dispatch\.json/);
  const bundleRollup = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "rollup.json"), "utf8"));
  assert.equal(bundleRollup.status, "BLOCKED");
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "rollup.md"), "utf8"), /^# DDD Staging Readiness Rollup/m);
  const bundleSummary = fs.readFileSync(path.join(handoffBundleDir, "handoff-summary.md"), "utf8");
  assert.match(bundleSummary, /^## DDD Staging Handoff/m);
  assert.match(bundleSummary, /Artifact: `ddd-staging-handoff-bundle`/);
  assert.match(bundleSummary, /Handoff bundle: PASS/);
  const bundleExecutionStatus = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "execution-status.json"), "utf8"));
  assert.equal(bundleExecutionStatus.status, "BLOCKED");
  assert.equal(bundleExecutionStatus.handoffBundle.status, "PASS");
  const bundleManifestForCount = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "manifest.json"), "utf8"));
  assert.equal(bundleExecutionStatus.handoffBundle.checkedFileCount, bundleManifestForCount.files.length);
  assert(bundleExecutionStatus.laneRoutes.some((lane) => lane.lane === "p0-docker-images" && lane.sourcePlan === "docker-image-submission-plan.json"));
  assert(bundleExecutionStatus.laneRoutes.some((lane) => lane.lane === "p1-p2-data-safety" && lane.sourcePlan === "data-safety-submission-plan.json"));
  const bundleEvidenceClosureBoard = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "evidence-closure-board.json"), "utf8"));
  assert.equal(bundleEvidenceClosureBoard.status, "BLOCKED");
  assert.equal(bundleEvidenceClosureBoard.coverage.expectedLaneCount, 5);
  assert(bundleEvidenceClosureBoard.lanes.some((lane) => lane.key === "platform-owners:p1-p2-data-safety" && lane.missingArtifacts.includes("tmp/ddd-explain/*.json")));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "evidence-closure-board.md"), "utf8"), /^# DDD Evidence Closure Board/m);
  const bundleEvidenceClosureBoardCsv = fs.readFileSync(path.join(handoffBundleDir, "evidence-closure-board.csv"), "utf8");
  assert.match(bundleEvidenceClosureBoardCsv, /^"key","owner","lane","status","receiptStatus"/m);
  assert.match(bundleEvidenceClosureBoardCsv, /"platform-owners:p1-p2-data-safety","platform-owners","p1-p2-data-safety"/);
  const bundleLaneReceiptFragments = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "lane-receipt-fragments.json"), "utf8"));
  assert.equal(bundleLaneReceiptFragments.status, "BLOCKED");
  assert.equal(bundleLaneReceiptFragments.redacted, true);
  assert.equal(bundleLaneReceiptFragments.willWriteFiles, false);
  assert.equal(bundleLaneReceiptFragments.laneCount, 5);
  assert.equal(bundleLaneReceiptFragments.passLaneCount, 1);
  assert.equal(bundleLaneReceiptFragments.blockedLaneCount, 4);
  assert.equal(bundleLaneReceiptFragments.fragments.length, 5);
  assert(bundleLaneReceiptFragments.fragments.some((fragment) => fragment.key === "release-infra:p0-release-env"));
  assert(bundleLaneReceiptFragments.fragments.some((fragment) => fragment.key === "release-infra:p0-docker-images"));
  assert(bundleLaneReceiptFragments.fragments.some((fragment) => fragment.key === "release-infra:p1-runtime-business"));
  assert(bundleLaneReceiptFragments.fragments.some((fragment) => fragment.key === "platform-owners:p1-p2-data-safety" && fragment.missingArtifacts.includes("tmp/ddd-explain/*.json")));
  assert(bundleLaneReceiptFragments.fragments.some((fragment) => fragment.key === "release-infra:final-review" && fragment.providedArtifacts.includes("artifacts/ddd/release/staging-handoff-bundle/final-review.json")));
  assert.equal(bundleLaneReceiptFragments.receiptAssembly.base64Command, "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>");
  const bundleLaneReceiptFragmentsMarkdown = fs.readFileSync(path.join(handoffBundleDir, "lane-receipt-fragments.md"), "utf8");
  assert.match(bundleLaneReceiptFragmentsMarkdown, /^# DDD Lane Receipt Fragments/m);
  assert.match(bundleLaneReceiptFragmentsMarkdown, /## Receipt JSON Skeleton/);
  assert.match(bundleLaneReceiptFragmentsMarkdown, /## Owner Fragment Copy Blocks/);
  assert.match(bundleLaneReceiptFragmentsMarkdown, /## Assembly Checklist/);
  assert.match(bundleLaneReceiptFragmentsMarkdown, /acceptanceCommands/);
  assert.match(bundleLaneReceiptFragmentsMarkdown, /release-infra:final-review/);
  const bundleLaneReceiptDraft = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "lane-receipt-draft.json"), "utf8"));
  assert.equal(bundleLaneReceiptDraft.status, "BLOCKED");
  assert.equal(bundleLaneReceiptDraft.redacted, true);
  assert.equal(bundleLaneReceiptDraft.willWriteFiles, false);
  assert.equal(bundleLaneReceiptDraft.laneReceiptCount, 5);
  assert(bundleLaneReceiptDraft.laneReceipts.some((lane) => lane.owner === "release-infra" && lane.lane === "p0-docker-images" && lane.expectedArtifacts.includes("artifacts/ddd/build/docker-image-evidence.json")));
  assert(bundleLaneReceiptDraft.laneReceipts.some((lane) => lane.owner === "platform-owners" && lane.lane === "p1-p2-data-safety" && lane.missingArtifacts.includes("tmp/ddd-explain/*.json")));
  assert(bundleLaneReceiptDraft.validationCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>"));
  const bundleLaneReceiptDraftMarkdown = fs.readFileSync(path.join(handoffBundleDir, "lane-receipt-draft.md"), "utf8");
  assert.match(bundleLaneReceiptDraftMarkdown, /^# DDD Lane Receipt Draft/m);
  assert.match(bundleLaneReceiptDraftMarkdown, /## Validation Commands/);
  const bundleOwnerEvidenceIntake = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "owner-evidence-intake.json"), "utf8"));
  assert.equal(bundleOwnerEvidenceIntake.status, "BLOCKED");
  assert.equal(bundleOwnerEvidenceIntake.willWriteFiles, false);
  assert.equal(bundleOwnerEvidenceIntake.ownerCount, 5);
  assert.equal(bundleOwnerEvidenceIntake.actionRequiredOwnerCount, 5);
  assert(bundleOwnerEvidenceIntake.owners.some((owner) => owner.owner === "release-infra" && owner.laneCount === 4 && owner.receiptFragments.some((fragment) => fragment.key === "release-infra:p0-docker-images")));
  assert(bundleOwnerEvidenceIntake.owners.some((owner) => owner.owner === "platform-owners" && owner.lanes.some((lane) => lane.lane === "p1-p2-data-safety") && owner.missingArtifacts.some((artifact) => artifact.artifact === "tmp/ddd-explain/*.json")));
  assert(bundleOwnerEvidenceIntake.owners.every((owner) => owner.ownerPacket && owner.ownerPacketJson && owner.envTemplate && Array.isArray(owner.submissionCommands)));
  const bundleOwnerEvidenceIntakeMarkdown = fs.readFileSync(path.join(handoffBundleDir, "owner-evidence-intake.md"), "utf8");
  assert.match(bundleOwnerEvidenceIntakeMarkdown, /^# DDD Owner Evidence Intake/m);
  assert.match(bundleOwnerEvidenceIntakeMarkdown, /## Owner Intake/);
  assert.match(bundleOwnerEvidenceIntakeMarkdown, /owner-packets\/release-infra\.md/);
  assert.match(bundleOwnerEvidenceIntakeMarkdown, /tmp\/ddd-explain\/\*\.json/);
  const bundleExecutionStatusMarkdown = fs.readFileSync(path.join(handoffBundleDir, "execution-status.md"), "utf8");
  assert.match(bundleExecutionStatusMarkdown, /^# DDD Staging Execution Status/m);
  assert.match(bundleExecutionStatusMarkdown, /## Lane Routes/);
  assert.match(bundleExecutionStatusMarkdown, /docker-image-submission-plan\.json/);
  assert.match(bundleExecutionStatusMarkdown, /data-safety-submission-plan\.json/);
  const bundleFinalReview = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "final-review.json"), "utf8"));
  assert.equal(bundleFinalReview.status, "BLOCKED");
  assert.equal(bundleFinalReview.cutoverReady, false);
  assert.equal(bundleFinalReview.handoffBundle.status, "PASS");
  assert.equal(bundleFinalReview.ownerDispatch.status, "PASS");
  assert.equal(bundleFinalReview.evidenceClosureBoard.closedLaneCount, 0);
  assert.equal(bundleFinalReview.evidenceClosureBoard.laneCount, 5);
  assert.equal(bundleFinalReview.evidenceClosureBoard.nextLane.key, "platform-owners:p1-p2-data-safety");
  assert.equal(bundleFinalReview.ownerDispatch.ownerTemplateCount, 5);
  assert(bundleFinalReview.ownerDispatch.owners.some((owner) => owner.owner === "platform-owners" && owner.missingEvidenceArtifactCount > 0));
  assert(bundleFinalReview.ownerDispatch.owners.some((owner) => owner.owner === "release-infra" && owner.laneCount === 4));
  assert(bundleFinalReview.ownerDispatch.owners.some((owner) => owner.owner === "release-infra" && owner.lanes.some((lane) => lane.lane === "p0-docker-images" && lane.sourcePlan === "docker-image-submission-plan.json")));
  assert(bundleFinalReview.ownerDispatch.owners.some((owner) => owner.owner === "platform-owners" && owner.lanes.some((lane) => lane.lane === "p1-p2-data-safety" && lane.missingArtifacts.includes("tmp/ddd-explain/*.json"))));
  assert(bundleFinalReview.checklist.some((item) => item.id === "staging-evidence-accepted" && item.passed === false));
  assert(bundleFinalReview.blockers.some((gate) => gate.gate === "release-env" && gate.blockingInputs.includes("DDD_RELEASE_ENV_FILE")));
  assert(bundleFinalReview.topBlockingInputs.length > 0);
  const bundleFinalReviewMarkdown = fs.readFileSync(path.join(handoffBundleDir, "final-review.md"), "utf8");
  assert.match(bundleFinalReviewMarkdown, /^# DDD Release Owner Final Review/m);
  assert.match(bundleFinalReviewMarkdown, /Evidence closure: 0\/5/);
  assert.match(bundleFinalReviewMarkdown, /## Evidence Closure/);
  assert.match(bundleFinalReviewMarkdown, /## Owner Lane Routes/);
  assert.match(bundleFinalReviewMarkdown, /docker-image-submission-plan\.json/);
  const bundleReleaseOwnerCloseout = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "release-owner-closeout.json"), "utf8"));
  assert.equal(bundleReleaseOwnerCloseout.status, "BLOCKED");
  assert.equal(bundleReleaseOwnerCloseout.finalRecommendation, "NO_GO_STRICT");
  assert.equal(bundleReleaseOwnerCloseout.evidenceClosure.closed, "0/5");
  assert.equal(bundleReleaseOwnerCloseout.evidenceClosure.nextLane.key, "platform-owners:p1-p2-data-safety");
  assert(bundleReleaseOwnerCloseout.requiredCommandSequence.includes("node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>"));
  const bundleReleaseOwnerCloseoutMarkdown = fs.readFileSync(path.join(handoffBundleDir, "release-owner-closeout.md"), "utf8");
  assert.match(bundleReleaseOwnerCloseoutMarkdown, /^# DDD Release Owner Closeout/m);
  assert.match(bundleReleaseOwnerCloseoutMarkdown, /## Immediate Next Lane/);
  assert.match(bundleReleaseOwnerCloseoutMarkdown, /platform-owners:p1-p2-data-safety/);
  assert.match(bundleReleaseOwnerCloseoutMarkdown, /## Required Command Sequence/);
  assert.match(bundleFinalReviewMarkdown, /data-safety-submission-plan\.json/);
  const bundleProductionCloseoutStatus = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "production-closeout-status.json"), "utf8"));
  assert.equal(bundleProductionCloseoutStatus.status, "BLOCKED");
  assert.equal(bundleProductionCloseoutStatus.finalRecommendation, "NO_GO_STRICT");
  assert.equal(bundleProductionCloseoutStatus.cutoverReady, false);
  assert.match(bundleProductionCloseoutStatus.eta, /\d/);
  assert.equal(bundleProductionCloseoutStatus.laneReceipt.coverage, "0/5");
  assert.equal(bundleProductionCloseoutStatus.laneCompletionSubmission.status, "BLOCKED");
  assert.equal(bundleProductionCloseoutStatus.laneCompletionSubmission.coverage, "0/5");
  assert.equal(bundleProductionCloseoutStatus.laneCompletionSubmission.base64Ready, false);
  assert.equal(bundleProductionCloseoutStatus.laneCompletionSubmission.dispatchReady, false);
  assert.match(bundleProductionCloseoutStatus.laneCompletionSubmission.nextCommand, /--lane-completion-receipt-init/);
  assert(bundleProductionCloseoutStatus.nextActions.some((action) => action.id === "first-wave-env" && action.command.includes("--next-action-env-check")));
  assert(bundleProductionCloseoutStatus.nextActions.some((action) => action.id === "lane-completion-receipt" && action.command.includes("--lane-completion-receipt-init")));
  assert(bundleProductionCloseoutStatus.nextActions.some((action) => action.id === "owner-evidence" && action.owner === "platform-owners"));
  assert(bundleProductionCloseoutStatus.blockedStages.some((stage) => stage.id === "lane-completion-receipt"));
  assert(bundleProductionCloseoutStatus.blockedPhases.some((phase) => phase.id === "p1-runtime-business"));
  assert(bundleProductionCloseoutStatus.requiredBeforeProduction.includes("workflow_dispatch inputs pass --release-evidence-dispatch-inputs-contract"));
  const bundleProductionCloseoutStatusMarkdown = fs.readFileSync(path.join(handoffBundleDir, "production-closeout-status.md"), "utf8");
  assert.match(bundleProductionCloseoutStatusMarkdown, /^# DDD Production Closeout Status/m);
  assert.match(bundleProductionCloseoutStatusMarkdown, /## Lane Completion Submission/);
  assert.match(bundleProductionCloseoutStatusMarkdown, /Next receipt command: `node scripts\/ddd-staging-execution-checklist\.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>`/);
  assert.match(bundleProductionCloseoutStatusMarkdown, /## Parallel Next Actions/);
  assert.match(bundleProductionCloseoutStatusMarkdown, /Validate first-wave secure env file/);
  assert.match(bundleProductionCloseoutStatusMarkdown, /Initialize or validate lane completion receipt/);
  assert.match(bundleProductionCloseoutStatusMarkdown, /## Next Owner Action/);
  assert.match(bundleProductionCloseoutStatusMarkdown, /## Required Before Production/);
  const bundleProductionUnblockQuickstart = fs.readFileSync(path.join(handoffBundleDir, "production-unblock-quickstart.md"), "utf8");
  assert.match(bundleProductionUnblockQuickstart, /^# DDD Production Unblock Quickstart/m);
  assert.match(bundleProductionUnblockQuickstart, /## Fast Path/);
  assert.match(bundleProductionUnblockQuickstart, /release-env-fill\.template\.env/);
  assert.match(bundleProductionUnblockQuickstart, /DDD_RELEASE_ENV_FILE=.env.release.local node scripts\/ddd-release-env-file-lint\.mjs/);
  assert.match(bundleProductionUnblockQuickstart, /## Final Gate/);
  assert.match(bundleProductionUnblockQuickstart, /production-evidence-readiness-enforce/);
  const bundleProductionUnblockPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "production-unblock-plan.json"), "utf8"));
  assert.equal(bundleProductionUnblockPlan.status, "BLOCKED");
  assert.equal(bundleProductionUnblockPlan.finalRecommendation, "NO_GO_STRICT");
  assert.equal(bundleProductionUnblockPlan.cutoverAllowed, false);
  assert.equal(bundleProductionUnblockPlan.blockedAuditItemCount, 5);
  assert.equal(bundleProductionUnblockPlan.parallelWorkstreamCount, 3);
  assert(bundleProductionUnblockPlan.parallelWorkstreams.some((workstream) => workstream.id === "first-wave-env" && workstream.command.includes("--next-action-env-check")));
  assert(bundleProductionUnblockPlan.parallelWorkstreams.some((workstream) => workstream.id === "lane-completion-receipt" && workstream.command.includes("--lane-completion-receipt-init")));
  assert(bundleProductionUnblockPlan.parallelWorkstreams.some((workstream) => workstream.id === "owner-evidence" && workstream.owner === "platform-owners"));
  assert(bundleProductionUnblockPlan.parallelWorkstreams.some((workstream) => workstream.id === "first-wave-env" && workstream.verifyCommand.includes("--next-action-env-receipt-contract")));
  assert(bundleProductionUnblockPlan.parallelWorkstreams.some((workstream) => workstream.id === "lane-completion-receipt" && workstream.verifyCommand.includes("--lane-completion-submission-check")));
  assert(bundleProductionUnblockPlan.parallelWorkstreams.some((workstream) => workstream.id === "owner-evidence" && workstream.verifyCommand.includes("--owner-evidence-intake-markdown")));
  assert(bundleProductionUnblockPlan.parallelWorkstreams.every((workstream) => workstream.completionSignal));
  assert(bundleProductionUnblockPlan.exitCriteria.some((criterion) => criterion.includes("GO_STRICT")));
  assert(bundleProductionUnblockPlan.verificationCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit-markdown"));
  const bundleProductionUnblockPlanMarkdown = fs.readFileSync(path.join(handoffBundleDir, "production-unblock-plan.md"), "utf8");
  assert.match(bundleProductionUnblockPlanMarkdown, /^# DDD Production Unblock Plan/m);
  assert.match(bundleProductionUnblockPlanMarkdown, /## Parallel Workstreams/);
  assert.match(bundleProductionUnblockPlanMarkdown, /Validate first-wave secure env file/);
  assert.match(bundleProductionUnblockPlanMarkdown, /Initialize or validate lane completion receipt/);
  assert.match(bundleProductionUnblockPlanMarkdown, /verify=`node scripts\/ddd-staging-execution-checklist\.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>`/);
  assert.match(bundleProductionUnblockPlanMarkdown, /verify=`node scripts\/ddd-staging-execution-checklist\.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>`/);
  assert.match(bundleProductionUnblockPlanMarkdown, /## Exit Criteria/);
  assert.match(bundleProductionUnblockPlanMarkdown, /GO_STRICT/);
  const bundleProductionEvidenceReadiness = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "production-evidence-readiness.json"), "utf8"));
  assert.equal(bundleProductionEvidenceReadiness.status, "BLOCKED");
  assert.equal(bundleProductionEvidenceReadiness.finalRecommendation, "NO_GO_STRICT");
  assert.equal(bundleProductionEvidenceReadiness.evidenceGateCount, 5);
  assert.equal(bundleProductionEvidenceReadiness.readyEvidenceCount, 0);
  assert.equal(bundleProductionEvidenceReadiness.blockedAuditItemCount, 5);
  assert(bundleProductionEvidenceReadiness.verificationCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-enforce"));
  assert(bundleProductionEvidenceReadiness.evidenceGates.some((gate) => gate.id === "first-wave-env-receipt" && gate.status === "MISSING" && gate.verifyCommand.includes("--next-action-env-receipt-contract")));
  assert(bundleProductionEvidenceReadiness.evidenceGates.some((gate) => gate.id === "lane-completion-receipt" && gate.verifyCommand.includes("--lane-completion-submission-check")));
  assert(bundleProductionEvidenceReadiness.evidenceGates.some((gate) => gate.id === "owner-evidence" && gate.command.includes("--data-safety-submission-plan-markdown")));
  assert(bundleProductionEvidenceReadiness.evidenceGates.some((gate) => gate.id === "production-audit" && gate.verifyCommand.includes("--production-cutover-audit")));
  assert(bundleProductionEvidenceReadiness.evidenceGates.some((gate) => gate.id === "final-go-no-go" && gate.verifyCommand.includes("release-final-go-no-go-gate.sh")));
  assert(bundleProductionEvidenceReadiness.blockingEvidence.some((gate) => gate.id === "first-wave-env-receipt"));
  const bundleProductionEvidenceReadinessMarkdown = fs.readFileSync(path.join(handoffBundleDir, "production-evidence-readiness.md"), "utf8");
  assert.match(bundleProductionEvidenceReadinessMarkdown, /^# DDD Production Evidence Readiness/m);
  assert.match(bundleProductionEvidenceReadinessMarkdown, /## Evidence Gates/);
  assert.match(bundleProductionEvidenceReadinessMarkdown, /First-wave env receipt contract/);
  assert.match(bundleProductionEvidenceReadinessMarkdown, /## Blocking Evidence/);
  assert.match(bundleProductionEvidenceReadinessMarkdown, /## Verification Commands/);
  assert.match(bundleProductionEvidenceReadinessMarkdown, /--production-evidence-readiness-enforce/);
  assert.match(bundleProductionEvidenceReadinessMarkdown, /final-go-no-go/);
  const productionEvidenceReadinessEnforceResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--production-evidence-readiness-enforce"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.notEqual(productionEvidenceReadinessEnforceResult.status, 0, "production evidence readiness enforce must block while evidence gates are not all PASS");
  const productionEvidenceReadinessEnforce = JSON.parse(productionEvidenceReadinessEnforceResult.stdout);
  assert.equal(productionEvidenceReadinessEnforce.status, "BLOCKED");
  assert.equal(productionEvidenceReadinessEnforce.readyEvidenceCount, 0);
  const bundleProductionCutoverAudit = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "production-cutover-audit.json"), "utf8"));
  assert.equal(bundleProductionCutoverAudit.status, "BLOCKED");
  assert.equal(bundleProductionCutoverAudit.finalRecommendation, "NO_GO_STRICT");
  assert.equal(bundleProductionCutoverAudit.noAutoWaivers, true);
  assert.equal(bundleProductionCutoverAudit.auditItemCount, 7);
  assert(bundleProductionCutoverAudit.auditItems.some((item) => item.id === "handoff-bundle-integrity" && item.status === "PASS"));
  assert(bundleProductionCutoverAudit.auditItems.some((item) => item.id === "lane-receipt-coverage" && item.blocker.includes("coverage=0/5")));
  assert(bundleProductionCutoverAudit.auditItems.some((item) => item.id === "lane-receipt-coverage" && item.command.includes("--lane-completion-receipt-init")));
  assert(bundleProductionCutoverAudit.blockedItems.some((item) => item.id === "lane-receipt-coverage" && item.command.includes("--lane-completion-receipt-init")));
  assert(bundleProductionCutoverAudit.parallelNextActions.some((action) => action.id === "first-wave-env" && action.command.includes("--next-action-env-check")));
  assert(bundleProductionCutoverAudit.parallelNextActions.some((action) => action.id === "lane-completion-receipt" && action.command.includes("--lane-completion-receipt-init")));
  assert(bundleProductionCutoverAudit.parallelNextActions.some((action) => action.id === "owner-evidence" && action.owner === "platform-owners"));
  assert(bundleProductionCutoverAudit.auditItems.some((item) => item.id === "strict-go-no-go" && item.command.includes("release-final-go-no-go-gate.sh")));
  assert(bundleProductionCutoverAudit.requiredCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>"));
  assert(bundleProductionCutoverAudit.requiredCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>"));
  const bundleProductionCutoverAuditMarkdown = fs.readFileSync(path.join(handoffBundleDir, "production-cutover-audit.md"), "utf8");
  assert.match(bundleProductionCutoverAuditMarkdown, /^# DDD Production Cutover Audit/m);
  assert.match(bundleProductionCutoverAuditMarkdown, /## Audit Items/);
  assert.match(bundleProductionCutoverAuditMarkdown, /## Parallel Next Actions/);
  assert.match(bundleProductionCutoverAuditMarkdown, /Validate first-wave secure env file/);
  assert.match(bundleProductionCutoverAuditMarkdown, /Initialize or validate lane completion receipt/);
  assert.match(bundleProductionCutoverAuditMarkdown, /## Required Commands/);
  assert.match(bundleProductionCutoverAuditMarkdown, /--lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>/);
  assert.match(bundleProductionCutoverAuditMarkdown, /No auto waivers: true/);
  const bundleOperatorProgress = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "operator-progress.json"), "utf8"));
  assert.equal(bundleOperatorProgress.status, "BLOCKED");
  assert(bundleOperatorProgress.evidenceArtifacts.missing > 0);
  assert(bundleOperatorProgress.evidenceArtifacts.missingItems.length > 0);
  assert(bundleOperatorProgress.evidenceArtifacts.missingByOwner.length > 0);
  assert.equal(bundleOperatorProgress.criticalPath.length, 6);
  assert(bundleOperatorProgress.criticalPath.some((phase) => phase.phase === "verify-runtime" && phase.dependency.includes("HTTPS staging")));
  assert(bundleOperatorProgress.criticalPath.some((phase) => phase.phase === "verify-data-safety" && phase.dependency.includes("EXPLAIN")));
  assert(bundleOperatorProgress.laneRoutes.some((lane) => lane.lane === "p0-docker-images" && lane.sourcePlan === "docker-image-submission-plan.json"));
  assert(bundleOperatorProgress.laneRoutes.some((lane) => lane.lane === "p1-p2-data-safety" && lane.sourcePlan === "data-safety-submission-plan.json"));
  assert(bundleOperatorProgress.stages.some((stage) => stage.id === "handoff-bundle" && stage.status === "PASS"));
  const bundleOperatorProgressMarkdown = fs.readFileSync(path.join(handoffBundleDir, "operator-progress.md"), "utf8");
  assert.match(bundleOperatorProgressMarkdown, /^# DDD Operator Progress/m);
  assert.match(bundleOperatorProgressMarkdown, /Receipt file: not provided/);
  assert.match(bundleOperatorProgressMarkdown, /Evidence artifacts: \d+\/\d+ present; missing=\d+/);
  assert.match(bundleOperatorProgressMarkdown, /## Missing Evidence By Owner/);
  assert.match(bundleOperatorProgressMarkdown, /## Missing Evidence Artifacts/);
  assert.match(bundleOperatorProgressMarkdown, /## Lane Routes/);
  assert.match(bundleOperatorProgressMarkdown, /## Critical Path/);
  assert.match(bundleOperatorProgressMarkdown, /verify-data-safety/);
  assert.match(bundleOperatorProgressMarkdown, /docker-image-submission-plan\.json/);
  assert.match(bundleOperatorProgressMarkdown, /data-safety-submission-plan\.json/);
  const bundleDailyBrief = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "daily-brief.json"), "utf8"));
  assert.equal(bundleDailyBrief.status, "BLOCKED");
  assert(bundleDailyBrief.dailyPriorities.length > 0);
  assert(bundleDailyBrief.laneRoutes.some((lane) => lane.lane === "p0-docker-images" && lane.sourcePlan === "docker-image-submission-plan.json"));
  assert(bundleDailyBrief.laneRoutes.some((lane) => lane.lane === "p1-runtime-business" && lane.sourcePlan === "runtime-business-submission-plan.json"));
  assert(bundleDailyBrief.laneRoutes.some((lane) => lane.lane === "p1-p2-data-safety" && lane.sourcePlan === "data-safety-submission-plan.json"));
  assert(bundleDailyBrief.ownerActions.some((owner) => owner.owner === "release-infra"));
  assert(bundleDailyBrief.ownerActions.some((owner) => owner.owner === "platform-owners" && owner.sourceOwners.includes("database")));
  assert(bundleDailyBrief.acceptanceCommands.some((item) => item.gate === "release-env"));
  const bundleDailyBriefMarkdown = fs.readFileSync(path.join(handoffBundleDir, "daily-brief.md"), "utf8");
  assert.match(bundleDailyBriefMarkdown, /^# DDD Release Owner Daily Brief/m);
  assert.match(bundleDailyBriefMarkdown, /## Today/);
  assert.match(bundleDailyBriefMarkdown, /## Lane Routes/);
  assert.match(bundleDailyBriefMarkdown, /docker-image-submission-plan\.json/);
  assert.match(bundleDailyBriefMarkdown, /runtime-business-submission-plan\.json/);
  assert.match(bundleDailyBriefMarkdown, /data-safety-submission-plan\.json/);
  assert.match(bundleDailyBriefMarkdown, /## Owner Actions/);
  const bundleClosurePlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "closure-plan.json"), "utf8"));
  assert.equal(bundleClosurePlan.status, "BLOCKED");
  assert.equal(bundleClosurePlan.blockedGateCount, 5);
  assert.match(bundleClosurePlan.eta, /0\.5-1\.5d/);
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "closure-plan.md"), "utf8"), /^# DDD Staging Closure Plan/m);
  const bundleNextActionQueue = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "next-action-queue.json"), "utf8"));
  assert.equal(bundleNextActionQueue.status, "BLOCKED");
  assert(bundleNextActionQueue.queue.some((item) => item.lane === "p0-docker-images" && item.command === "node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown"));
  assert(bundleNextActionQueue.queue.some((item) => item.lane === "p0-docker-images" && item.sourcePlan === "docker-image-submission-plan.json"));
  assert(bundleNextActionQueue.queue.some((item) => item.lane === "p0-docker-images" && item.artifacts.includes("artifacts/ddd/build/docker-image-evidence.json")));
  assert(bundleNextActionQueue.queue.some((item) => item.lane === "p0-docker-images" && item.followUpCommands.includes("node scripts/ddd-docker-build-evidence.mjs --check")));
  assert(bundleNextActionQueue.queue.some((item) => item.lane === "p1-runtime-business" && item.sourcePlan === "runtime-business-submission-plan.json"));
  assert(bundleNextActionQueue.queue.some((item) => item.lane === "p1-runtime-business" && item.command === "node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown"));
  assert(bundleNextActionQueue.queue.some((item) => item.lane === "p1-p2-data-safety" && item.sourcePlan === "data-safety-submission-plan.json"));
  assert(bundleNextActionQueue.queue.some((item) => item.lane === "p1-p2-data-safety" && item.command === "node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown"));
  assert(bundleNextActionQueue.queue.some((item) => item.lane === "p1-p2-data-safety" && item.missingEvidenceArtifacts.some((artifact) => artifact.artifact === "tmp/ddd-explain/*.json")));
  assert(bundleNextActionQueue.queue.some((item) => item.lane === "p1-p2-data-safety" && item.artifactPlanCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --explain-artifact-plan-markdown")));
  const bundleNextActionQueueMarkdown = fs.readFileSync(path.join(handoffBundleDir, "next-action-queue.md"), "utf8");
  assert.match(bundleNextActionQueueMarkdown, /^# DDD Staging Next Action Queue/m);
  assert.match(bundleNextActionQueueMarkdown, /tmp\/ddd-explain\/\*\.json/);
  assert.match(bundleNextActionQueueMarkdown, /--explain-artifact-plan-markdown/);
  const bundleOwnerLaneMatrix = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "owner-lane-matrix.json"), "utf8"));
  assert.equal(bundleOwnerLaneMatrix.status, "BLOCKED");
  assert(bundleOwnerLaneMatrix.owners.some((owner) => owner.owner === "platform-owners" && owner.lanes.some((lane) => lane.lane === "p1-p2-data-safety")));
  assert(bundleOwnerLaneMatrix.owners.some((owner) => owner.owner === "platform-owners" && owner.lanes.some((lane) => lane.missingArtifacts.includes("tmp/ddd-explain/*.json"))));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "owner-lane-matrix.md"), "utf8"), /^# DDD Owner Lane Matrix/m);
  const bundleLaneCompletionReceiptTemplate = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "lane-completion-receipt.template.json"), "utf8"));
  assert.equal(bundleLaneCompletionReceiptTemplate.redacted, true);
  assert(bundleLaneCompletionReceiptTemplate.laneReceipts.some((lane) => lane.lane === "p1-p2-data-safety"));
  assert(bundleLaneCompletionReceiptTemplate.submissionFlow.includes("node scripts/ddd-lane-completion-receipt-autofill.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>"));
  assert(bundleLaneCompletionReceiptTemplate.submissionFlow.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>"));
  assert(bundleLaneCompletionReceiptTemplate.submissionFlow.includes("node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>"));
  const bundleLaneCompletionReceiptTemplateMarkdown = fs.readFileSync(path.join(handoffBundleDir, "lane-completion-receipt.template.md"), "utf8");
  assert.match(bundleLaneCompletionReceiptTemplateMarkdown, /^# DDD Lane Completion Receipt/m);
  assert.match(bundleLaneCompletionReceiptTemplateMarkdown, /## Fill Rules/);
  assert.match(bundleLaneCompletionReceiptTemplateMarkdown, /## Edit Checklist/);
  assert.match(bundleLaneCompletionReceiptTemplateMarkdown, /laneReceipts\[0\]/);
  assert.match(bundleLaneCompletionReceiptTemplateMarkdown, /## Lane Details/);
  assert.match(bundleLaneCompletionReceiptTemplateMarkdown, /required when PASS/);
  assert.match(bundleLaneCompletionReceiptTemplateMarkdown, /## Submission Flow/);
  assert.match(bundleLaneCompletionReceiptTemplateMarkdown, /--lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>/);
  assert.match(bundleLaneCompletionReceiptTemplateMarkdown, /--final-review-enforce --lane-completion-receipt-file=<receipt-file>/);
  const bundleLaneCompletionReceiptCoverage = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "lane-completion-receipt.coverage.json"), "utf8"));
  assert.equal(bundleLaneCompletionReceiptCoverage.status, "BLOCKED");
  assert.equal(bundleLaneCompletionReceiptCoverage.coverage.coveredLaneCount, 0);
  assert.equal(bundleLaneCompletionReceiptCoverage.coverage.expectedLaneCount, 5);
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "lane-completion-receipt.coverage.md"), "utf8"), /^# DDD Lane Completion Receipt Coverage/m);
  const bundleLaneCompletionSubmissionPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "lane-completion-submission-plan.json"), "utf8"));
  assert.equal(bundleLaneCompletionSubmissionPlan.status, "BLOCKED");
  assert.equal(bundleLaneCompletionSubmissionPlan.workflowInput.base64Input, "lane_completion_receipt_base64");
  assert.equal(bundleLaneCompletionSubmissionPlan.currentCoverage.expectedLaneCount, 5);
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "lane-completion-submission-plan.md"), "utf8"), /^# DDD Lane Completion Submission Plan/m);
  const bundleLaneCompletionSubmissionCheck = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "lane-completion-submission-check.json"), "utf8"));
  assert.equal(bundleLaneCompletionSubmissionCheck.status, "BLOCKED");
  assert.equal(bundleLaneCompletionSubmissionCheck.contract.status, "MISSING");
  assert.equal(bundleLaneCompletionSubmissionCheck.dispatch.ready, false);
  assert.equal(bundleLaneCompletionSubmissionCheck.dispatch.preferredInput, "lane_completion_receipt_base64");
  const bundleLaneCompletionSubmissionCheckMarkdown = fs.readFileSync(path.join(handoffBundleDir, "lane-completion-submission-check.md"), "utf8");
  assert.match(bundleLaneCompletionSubmissionCheckMarkdown, /^# DDD Lane Completion Submission Check/m);
  assert.match(bundleLaneCompletionSubmissionCheckMarkdown, /## Submission Commands/);
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "next-action.template.env"), "utf8"), /^# Lane: p0-release-env$/m);
  const bundleNextActionEnvReceiptSample = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "next-action-env-receipt.sample.json"), "utf8"));
  assert.equal(bundleNextActionEnvReceiptSample.redacted, true);
  assert.equal(bundleNextActionEnvReceiptSample.status, "BLOCKED");
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "next-action-env-receipt.sample.md"), "utf8"), /^# DDD Next Action Env Receipt/m);
  const bundleNextActionEnvReceiptSampleContractResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--next-action-env-receipt-contract",
    `--next-action-env-receipt-file=${path.join(handoffBundleDir, "next-action-env-receipt.sample.json")}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.equal(bundleNextActionEnvReceiptSampleContractResult.status, 0, bundleNextActionEnvReceiptSampleContractResult.stderr || bundleNextActionEnvReceiptSampleContractResult.stdout);
  const bundleNextActionVerificationPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "next-action-verification-plan.json"), "utf8"));
  assert.equal(bundleNextActionVerificationPlan.status, "BLOCKED");
  assert(bundleNextActionVerificationPlan.phases.some((phase) => phase.id === "verify-data-safety"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "next-action-verification-plan.md"), "utf8"), /^# DDD Next Action Verification Plan/m);
  const bundleReleaseEnvPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "release-env-plan.json"), "utf8"));
  assert.equal(bundleReleaseEnvPlan.status, "BLOCKED");
  assert.equal(bundleReleaseEnvPlan.envInitCheck.status, "PASS");
  assert(bundleReleaseEnvPlan.commands.validate.includes("DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "release-env-plan.md"), "utf8"), /^# DDD P0 Release Env Plan/m);
  const bundleReleaseEnvOwnerMatrix = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "release-env-owner-matrix.json"), "utf8"));
  assert.equal(bundleReleaseEnvOwnerMatrix.status, "BLOCKED");
  assert.equal(bundleReleaseEnvOwnerMatrix.ownerCount, 5);
  assert(bundleReleaseEnvOwnerMatrix.owners.some((owner) => owner.owner === "release-infra"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "release-env-owner-matrix.md"), "utf8"), /^# DDD Release Env Owner Matrix/m);
  const bundleReleaseEnvNextOwnerTemplate = fs.readFileSync(path.join(handoffBundleDir, "release-env-next-owner.template.env"), "utf8");
  assert.match(bundleReleaseEnvNextOwnerTemplate, /^# Owner: release-infra$/m);
  assert.match(bundleReleaseEnvNextOwnerTemplate, /^LUMIRA_BASE_URL=__REQUIRED_HTTPS__$/m);
  const bundleReleaseEnvMergePlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "release-env-merge-plan.json"), "utf8"));
  assert.equal(bundleReleaseEnvMergePlan.status, "BLOCKED");
  assert(bundleReleaseEnvMergePlan.phases.some((phase) => phase.id === "validate-release-env"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "release-env-merge-plan.md"), "utf8"), /^# DDD Release Env Merge Plan/m);
  const bundleReleaseEnvSubmissionPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "release-env-submission-plan.json"), "utf8"));
  assert.equal(bundleReleaseEnvSubmissionPlan.status, "BLOCKED");
  assert.equal(bundleReleaseEnvSubmissionPlan.ownerCount, 5);
  assert(bundleReleaseEnvSubmissionPlan.receipt.commands.includes("node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "release-env-submission-plan.md"), "utf8"), /^# DDD Release Env Submission Plan/m);
  const bundleReleaseEnvFillChecklist = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "release-env-fill-checklist.json"), "utf8"));
  assert.equal(bundleReleaseEnvFillChecklist.status, "FAIL");
  assert(bundleReleaseEnvFillChecklist.primaryBlockerCount > 0);
  assert(bundleReleaseEnvFillChecklist.groups.runtime.includes("LUMIRA_BASE_URL"));
  assert(bundleReleaseEnvFillChecklist.validationCommands.includes("DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "release-env-fill-checklist.md"), "utf8"), /^# P0 Release Env Fill Checklist/m);
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "release-env-fill-checklist.md"), "utf8"), /## Required Keys By Group/);
  const bundleReleaseEnvFillTemplate = fs.readFileSync(path.join(handoffBundleDir, "release-env-fill.template.env"), "utf8");
  assert.match(bundleReleaseEnvFillTemplate, /^# P0 release env fill template\./m);
  assert.match(bundleReleaseEnvFillTemplate, /^LUMIRA_BASE_URL=__REQUIRED_HTTPS__$/m);
  assert.match(bundleReleaseEnvFillTemplate, /^DB_PASSWORD=__REQUIRED_SECRET_REF__$/m);
  assert.match(bundleReleaseEnvFillTemplate, /^DDD_DEPLOYMENT_EVIDENCE=__REQUIRED_ARTIFACT_PATH_OR_URL__$/m);
  const bundleDockerImagePlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "docker-image-plan.json"), "utf8"));
  assert.equal(bundleDockerImagePlan.status, "PASS");
  assert(bundleDockerImagePlan.paths.some((item) => item.id === "existing-image-inspect"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "docker-image-plan.md"), "utf8"), /^# DDD Docker Image Evidence Plan/m);
  const bundleDockerImageSubmissionPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "docker-image-submission-plan.json"), "utf8"));
  assert.equal(bundleDockerImageSubmissionPlan.status, "PASS");
  assert.equal(bundleDockerImageSubmissionPlan.laneReceiptFragment.status, "PASS");
  assert(bundleDockerImageSubmissionPlan.submissionModes.some((item) => item.id === "existing-image-inspect"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "docker-image-submission-plan.md"), "utf8"), /^# DDD Docker Image Submission Plan/m);
  const bundleRuntimeBusinessPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "runtime-business-plan.json"), "utf8"));
  assert.equal(bundleRuntimeBusinessPlan.status, "BLOCKED");
  assert(bundleRuntimeBusinessPlan.requiredEnv.urls.includes("PLAYWRIGHT_BASE_URL"));
  assert(bundleRuntimeBusinessPlan.smokeSteps.some((step) => step.id === "payment-webhook-e2e"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "runtime-business-plan.md"), "utf8"), /^# DDD P1 Runtime Business Plan/m);
  const bundleRuntimeSmokePlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "runtime-smoke-plan.json"), "utf8"));
  assert.equal(bundleRuntimeSmokePlan.status, "BLOCKED");
  assert(bundleRuntimeSmokePlan.phases.some((phase) => phase.id === "frontend-runtime-smoke"));
  assert(bundleRuntimeSmokePlan.phases.some((phase) => phase.id === "runtime-acceptance" && phase.dependencies.includes("business-payment-smoke")));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "runtime-smoke-plan.md"), "utf8"), /^# DDD Runtime Smoke Owner Plan/m);
  const bundleRuntimeBusinessSubmissionPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "runtime-business-submission-plan.json"), "utf8"));
  assert.equal(bundleRuntimeBusinessSubmissionPlan.status, "BLOCKED");
  assert(bundleRuntimeBusinessSubmissionPlan.ownerSubmissions.some((item) => item.phase === "business-payment-smoke"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "runtime-business-submission-plan.md"), "utf8"), /^# DDD Runtime Business Submission Plan/m);
  const bundleDataSafetyPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "data-safety-plan.json"), "utf8"));
  assert.equal(bundleDataSafetyPlan.status, "BLOCKED");
  assert(bundleDataSafetyPlan.trackPlans.some((track) => track.id === "rollback"));
  assert(bundleDataSafetyPlan.trackPlans.some((track) => track.id === "explain" && track.requiredInputs.includes("DDD_EXPLAIN_DATABASE")));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "data-safety-plan.md"), "utf8"), /^# DDD Data Safety Plan/m);
  const bundleDataSafetyOwnerPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "data-safety-owner-plan.json"), "utf8"));
  assert.equal(bundleDataSafetyOwnerPlan.status, "BLOCKED");
  assert(bundleDataSafetyOwnerPlan.phases.some((phase) => phase.id === "migration-fresh-drill"));
  assert(bundleDataSafetyOwnerPlan.phases.some((phase) => phase.id === "data-safety-acceptance" && phase.dependencies.includes("explain-gate")));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "data-safety-owner-plan.md"), "utf8"), /^# DDD Data Safety Owner Plan/m);
  const bundleDataSafetySubmissionPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "data-safety-submission-plan.json"), "utf8"));
  assert.equal(bundleDataSafetySubmissionPlan.status, "BLOCKED");
  assert(bundleDataSafetySubmissionPlan.ownerSubmissions.some((item) => item.phase === "explain-collect"));
  assert(bundleDataSafetySubmissionPlan.explainArtifact.envTemplate.includes("MYSQL_PASSWORD=__SECRET_REFERENCE_ONLY__"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "data-safety-submission-plan.md"), "utf8"), /^# DDD Data Safety Submission Plan/m);
  const bundleCutoverRehearsalPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "cutover-rehearsal-plan.json"), "utf8"));
  assert.equal(bundleCutoverRehearsalPlan.status, "BLOCKED");
  assert.equal(bundleCutoverRehearsalPlan.cutoverReady, false);
  assert(bundleCutoverRehearsalPlan.phases.some((phase) => phase.id === "final-review"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "cutover-rehearsal-plan.md"), "utf8"), /^# DDD Cutover Rehearsal Plan/m);
  const bundleEvidenceGaps = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "evidence-gaps.json"), "utf8"));
  assert.equal(bundleEvidenceGaps.gapCount, 6);
  assert(bundleEvidenceGaps.gaps.some((gap) => gap.id === "p1-runtime-business" && gap.envKeys.includes("LUMIRA_BASE_URL")));
  const bundleRunbook = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "evidence-runbook.json"), "utf8"));
  assert.equal(bundleRunbook.trackCount, 6);
  assert(bundleRunbook.tracks.some((track) => track.id === "p1-runtime-business" && track.envKeys.includes("LUMIRA_BASE_URL")));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "evidence-runbook.md"), "utf8"), /^# DDD Staging Evidence Runbook/m);
  const bundleAcceptance = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "evidence-acceptance.json"), "utf8"));
  assert.equal(bundleAcceptance.itemCount, 6);
  assert.equal(bundleAcceptance.blockedCount, 5);
  assert(bundleAcceptance.missingArtifactCount > 0);
  assert(bundleAcceptance.items.some((item) => item.gate === "docker-images" && item.accepted === true));
  assert(bundleAcceptance.items.some((item) => item.gate === "explain" && item.blockingInputs.includes("MYSQL_PASSWORD")));
  assert(bundleAcceptance.items.some((item) => item.gate === "explain" && item.artifactChecks.some((artifact) => typeof artifact.present === "boolean")));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "evidence-acceptance.md"), "utf8"), /^# DDD Staging Evidence Acceptance/m);
  const bundleArtifactGaps = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "evidence-artifact-gaps.json"), "utf8"));
  assert.equal(bundleArtifactGaps.status, "BLOCKED");
  assert(bundleArtifactGaps.missingArtifacts.some((artifact) => artifact.artifact === "tmp/ddd-explain/*.json" && artifact.dispatchOwners.includes("platform-owners")));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "evidence-artifact-gaps.md"), "utf8"), /^# DDD Evidence Artifact Gap Report/m);
  const bundleExplainArtifactPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "explain-artifact-plan.json"), "utf8"));
  assert.equal(bundleExplainArtifactPlan.status, "BLOCKED");
  assert.equal(bundleExplainArtifactPlan.missingArtifact, "tmp/ddd-explain/*.json");
  assert(bundleExplainArtifactPlan.dispatchOwners.includes("platform-owners"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "explain-artifact-plan.md"), "utf8"), /^# DDD EXPLAIN Artifact Plan/m);
  const bundleBlockingInputs = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "blocking-inputs.json"), "utf8"));
  assert(bundleBlockingInputs.inputs.some((input) => input.input === "DDD_RELEASE_ENV_FILE"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "blocking-inputs.md"), "utf8"), /^# DDD Staging Blocking Inputs/m);
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "blocking-inputs.template.env"), "utf8"), /^LUMIRA_BASE_URL=__REQUIRED_HTTPS__$/m);
  const bundleReleaseEvidenceDispatchPlan = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "release-evidence-dispatch-plan.json"), "utf8"));
  assert.equal(bundleReleaseEvidenceDispatchPlan.status, "BLOCKED");
  assert.equal(bundleReleaseEvidenceDispatchPlan.workflow, ".github/workflows/ddd-release-evidence.yml");
  assert(bundleReleaseEvidenceDispatchPlan.inputs.some((input) => input.input === "backend_base_url" && input.source === "LUMIRA_BASE_URL"));
  assert(bundleReleaseEvidenceDispatchPlan.requiredBeforeRun.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "release-evidence-dispatch-plan.md"), "utf8"), /^# DDD Release Evidence Dispatch Plan/m);
  const bundleReleaseEvidenceDispatchInputs = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "release-evidence-dispatch-inputs.json"), "utf8"));
  assert.equal(bundleReleaseEvidenceDispatchInputs.payload.mode, "plan");
  assert.equal(bundleReleaseEvidenceDispatchInputs.payload.frontend_base_url, "__REQUIRED_HTTPS__");
  assert(bundleReleaseEvidenceDispatchInputs.blockedInputs.some((input) => input.input === "lane_completion_receipt_base64"));
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "release-evidence-dispatch-command.sh"), "utf8"), /^gh workflow run ddd-release-evidence\.yml \\/m);
  const releaseInfraEnvTemplate = fs.readFileSync(path.join(bundleOwnerPacketDir, "release-infra.blocking-inputs.template.env"), "utf8");
  assert.match(releaseInfraEnvTemplate, /^# Owner filter: release-infra$/m);
  assert.match(releaseInfraEnvTemplate, /^DDD_RELEASE_ENV_FILE=__REQUIRED__$/m);
  assert.match(releaseInfraEnvTemplate, /^LUMIRA_BASE_URL=__REQUIRED_HTTPS__$/m);
  assert.doesNotMatch(releaseInfraEnvTemplate, /^MYSQL_PASSWORD=/m);
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "evidence-env.template.env"), "utf8"), /^DDD_DOCKER_BUILD_STRICT=true/m);
  assert.match(fs.readFileSync(path.join(handoffBundleDir, "commands.txt"), "utf8"), /node scripts\/ddd-staging-execution-checklist\.mjs --rollup-markdown/);
  const ownerDispatch = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "owner-dispatch.json"), "utf8"));
  assert.equal(ownerDispatch.ownerCount, 5);
  assert(ownerDispatch.owners.some((owner) => owner.owner === "release-infra" && owner.json === "owner-packets/release-infra.json" && owner.blockingInputsEnvTemplate === "owner-packets/release-infra.blocking-inputs.template.env" && owner.blockingInputCount > 0));
  assert(ownerDispatch.owners.some((owner) => owner.owner === "platform-owners" && owner.missingEvidenceArtifactCount > 0));
  assert.match(fs.readFileSync(path.join(bundleOwnerPacketDir, "README.md"), "utf8"), /Missing artifacts/);
  const bundledReleaseInfraPacket = fs.readFileSync(path.join(bundleOwnerPacketDir, "release-infra.md"), "utf8");
  const bundledPlatformOwnersPacket = fs.readFileSync(path.join(bundleOwnerPacketDir, "platform-owners.md"), "utf8");
  assert.match(bundledReleaseInfraPacket, /## Current Blocking Inputs/);
  assert.match(bundledPlatformOwnersPacket, /## Queue Lanes/);
  assert.match(bundledPlatformOwnersPacket, /p1-p2-data-safety/);
  assert.match(bundledReleaseInfraPacket, /\| 2 \| `p0-docker-images` \| PASS \| 0 \|/);
  assert.match(bundledReleaseInfraPacket, /### p0-docker-images[\s\S]*Currently missing artifacts: none/);
  assert.match(bundledReleaseInfraPacket, /## Staging Evidence Gaps/);
  assert.match(bundledReleaseInfraPacket, /## Missing Evidence Artifacts/);
  assert.match(bundledReleaseInfraPacket, /## Owner Completion Receipt/);
  assert.match(bundledPlatformOwnersPacket, /## Owner Completion Receipt/);
  assert.match(bundledReleaseInfraPacket, /--next-action-env-receipt-output=<receipt-file>/);
  assert.match(bundledReleaseInfraPacket, /--operator-progress-markdown --next-action-env-receipt-file=<receipt-file>/);
  assert.match(bundledReleaseInfraPacket, /--final-review-enforce --lane-completion-receipt-file=<receipt-file>/);
  assert.match(bundledPlatformOwnersPacket, /--lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>/);
  assert.match(bundledPlatformOwnersPacket, /--lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>/);
  assert.match(bundledPlatformOwnersPacket, /--lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>/);
  const releaseInfraJsonPacket = JSON.parse(fs.readFileSync(path.join(bundleOwnerPacketDir, "release-infra.json"), "utf8"));
  const platformOwnersJsonPacket = JSON.parse(fs.readFileSync(path.join(bundleOwnerPacketDir, "platform-owners.json"), "utf8"));
  assert.equal(releaseInfraJsonPacket.owner, "release-infra");
  assert(platformOwnersJsonPacket.queueLanes.some((lane) => lane.lane === "p1-p2-data-safety" && lane.dispatchOwner === "platform-owners" && lane.missingEvidenceArtifactCount > 0));
  assert(releaseInfraJsonPacket.commands.includes("node scripts/ddd-release-env-init.mjs --check"));
  assert(releaseInfraJsonPacket.commands.includes("node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file> --next-action-env-receipt-output=<receipt-file>"));
  assert(releaseInfraJsonPacket.commands.includes("node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --next-action-env-receipt-file=<receipt-file>"));
  assert(releaseInfraJsonPacket.commands.includes("node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>"));
  assert(platformOwnersJsonPacket.commands.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>"));
  assert(platformOwnersJsonPacket.commands.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>"));
  assert(platformOwnersJsonPacket.commands.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>"));
  assert(platformOwnersJsonPacket.commands.includes("node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>"));
  assert(releaseInfraJsonPacket.evidenceGaps.some((gap) => gap.id === "p0-images" && gap.envKeys.includes("DDD_DOCKER_BUILD_STRICT")));
  assert(releaseInfraJsonPacket.blockingInputGates.some((gate) => gate.gate === "runtime-business" && gate.blockingInputs.includes("LUMIRA_BASE_URL")));
  assert(releaseInfraJsonPacket.blockingInputs.includes("DDD_RELEASE_ENV_FILE"));
  assert(Array.isArray(releaseInfraJsonPacket.missingEvidenceArtifacts));
  assert(releaseInfraJsonPacket.missingEvidenceArtifacts.every((artifact) => artifact.artifact && artifact.gate && artifact.acceptanceCommand));
  assert(platformOwnersJsonPacket.missingEvidenceArtifacts.some((artifact) => artifact.owner === "database" && artifact.dispatchOwner === "platform-owners"));
  const bundleManifest = JSON.parse(fs.readFileSync(path.join(handoffBundleDir, "manifest.json"), "utf8"));
  assert.equal(bundleManifest.status, "BLOCKED");
  assert.equal(bundleManifest.ownerPackets, 5);
  assert(bundleManifest.files.some((item) => item.file === "rollup.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "handoff-summary.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "execution-status.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "final-review.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "final-review.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "closure-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "closure-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "next-action-queue.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "next-action-queue.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "owner-lane-matrix.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "owner-lane-matrix.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "lane-completion-receipt.template.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "lane-completion-receipt.template.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "lane-completion-receipt.coverage.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "lane-completion-receipt.coverage.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "lane-receipt-fragments.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "lane-receipt-fragments.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "lane-receipt-draft.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "lane-receipt-draft.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "owner-evidence-intake.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "owner-evidence-intake.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "lane-completion-submission-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "lane-completion-submission-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "lane-completion-submission-check.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "lane-completion-submission-check.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "next-action.template.env" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "next-action-verification-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "next-action-verification-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "next-action-env-receipt.sample.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "next-action-env-receipt.sample.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "production-closeout-status.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "production-closeout-status.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "production-unblock-quickstart.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "production-cutover-audit.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "production-cutover-audit.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "operator-progress.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "operator-progress.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "daily-brief.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "daily-brief.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-env-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-env-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-env-owner-matrix.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-env-owner-matrix.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-env-next-owner.template.env" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-env-merge-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-env-merge-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-env-submission-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-env-submission-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-env-fill-checklist.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-env-fill-checklist.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-env-fill.template.env" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "docker-image-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "docker-image-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "docker-image-submission-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "docker-image-submission-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "runtime-business-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "runtime-business-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "runtime-smoke-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "runtime-smoke-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "runtime-business-submission-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "runtime-business-submission-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "data-safety-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "data-safety-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "data-safety-owner-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "data-safety-owner-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "data-safety-submission-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "data-safety-submission-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "cutover-rehearsal-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "cutover-rehearsal-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "evidence-gaps.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "evidence-runbook.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "evidence-acceptance.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "evidence-artifact-gaps.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "evidence-artifact-gaps.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "explain-artifact-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "explain-artifact-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "blocking-inputs.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "blocking-inputs.template.env" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-evidence-dispatch-plan.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-evidence-dispatch-plan.md" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-evidence-dispatch-inputs.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "release-evidence-dispatch-command.sh" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "owner-dispatch.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "owner-packets/release-infra.md"));
  assert(bundleManifest.files.some((item) => item.file === "owner-packets/release-infra.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(bundleManifest.files.some((item) => item.file === "owner-packets/release-infra.blocking-inputs.template.env" && /^[a-f0-9]{64}$/.test(item.sha256)));

  const handoffBundleVerifyResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle-verify"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(handoffBundleVerifyResult.status, 0, handoffBundleVerifyResult.stderr || handoffBundleVerifyResult.stdout);
  const handoffBundleVerify = JSON.parse(handoffBundleVerifyResult.stdout);
  assert.equal(handoffBundleVerify.status, "PASS");
  assert.equal(handoffBundleVerify.willWriteFiles, false);
  assert.equal(handoffBundleVerify.issues.length, 0);
  assert(handoffBundleVerify.checkedFileCount >= 10);
  assert(handoffBundleVerify.checkedFiles.some((item) => item.file === "rollup.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  assert(handoffBundleVerify.checkedFiles.some((item) => item.file === "lane-receipt-fragments.json" && /^[a-f0-9]{64}$/.test(item.sha256)));
  maybeStopAfter("handoff-bundle-verify");

  const laneReceiptFragmentsResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--lane-receipt-fragments"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(laneReceiptFragmentsResult.status, 0, laneReceiptFragmentsResult.stderr || laneReceiptFragmentsResult.stdout);
  const laneReceiptFragments = JSON.parse(laneReceiptFragmentsResult.stdout);
  assert.equal(laneReceiptFragments.status, "BLOCKED");
  assert.equal(laneReceiptFragments.laneCount, 5);
  assert(laneReceiptFragments.fragments.some((fragment) => fragment.key === "release-infra:final-review"));

  const laneReceiptFragmentsMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--lane-receipt-fragments-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(laneReceiptFragmentsMarkdownResult.status, 0, laneReceiptFragmentsMarkdownResult.stderr || laneReceiptFragmentsMarkdownResult.stdout);
  assert.match(laneReceiptFragmentsMarkdownResult.stdout, /^# DDD Lane Receipt Fragments/m);
  assert.match(laneReceiptFragmentsMarkdownResult.stdout, /## Receipt JSON Skeleton/);
  assert.match(laneReceiptFragmentsMarkdownResult.stdout, /## Owner Fragment Copy Blocks/);
  assert.match(laneReceiptFragmentsMarkdownResult.stdout, /## Assembly Checklist/);
  assert.match(laneReceiptFragmentsMarkdownResult.stdout, /release-infra:p0-docker-images/);
  assert.match(laneReceiptFragmentsMarkdownResult.stdout, /acceptanceCommands/);

  const laneReceiptDraftResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--lane-receipt-draft"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(laneReceiptDraftResult.status, 0, laneReceiptDraftResult.stderr || laneReceiptDraftResult.stdout);
  const laneReceiptDraft = JSON.parse(laneReceiptDraftResult.stdout);
  assert.equal(laneReceiptDraft.status, "BLOCKED");
  assert.equal(laneReceiptDraft.laneReceiptCount, 5);
  assert(laneReceiptDraft.laneReceipts.some((lane) => lane.owner === "release-infra" && lane.lane === "final-review"));

  const laneReceiptDraftMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--lane-receipt-draft-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(laneReceiptDraftMarkdownResult.status, 0, laneReceiptDraftMarkdownResult.stderr || laneReceiptDraftMarkdownResult.stdout);
  assert.match(laneReceiptDraftMarkdownResult.stdout, /^# DDD Lane Receipt Draft/m);
  assert.match(laneReceiptDraftMarkdownResult.stdout, /## Validation Commands/);

  const ownerEvidenceIntakeResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--owner-evidence-intake"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(ownerEvidenceIntakeResult.status, 0, ownerEvidenceIntakeResult.stderr || ownerEvidenceIntakeResult.stdout);
  const ownerEvidenceIntake = JSON.parse(ownerEvidenceIntakeResult.stdout);
  assert.equal(ownerEvidenceIntake.status, "BLOCKED");
  assert.equal(ownerEvidenceIntake.ownerCount, 5);
  assert(ownerEvidenceIntake.owners.some((owner) => owner.owner === "release-infra" && owner.receiptFragments.length === 4));
  assert(ownerEvidenceIntake.owners.some((owner) => owner.owner === "release-infra" && owner.receiptWorkflow.laneKeys.includes("release-infra:final-review")));
  assert(ownerEvidenceIntake.owners.every((owner) => owner.receiptWorkflow.initCommand.includes("--lane-completion-receipt-init")));
  assert(ownerEvidenceIntake.owners.every((owner) => owner.receiptWorkflow.autofillCommand.includes("ddd-lane-completion-receipt-autofill.mjs")));
  assert(ownerEvidenceIntake.owners.every((owner) => owner.submissionCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>")));

  const ownerEvidenceIntakeMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--owner-evidence-intake-markdown", "--owner=platform-owners"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(ownerEvidenceIntakeMarkdownResult.status, 0, ownerEvidenceIntakeMarkdownResult.stderr || ownerEvidenceIntakeMarkdownResult.stdout);
  assert.match(ownerEvidenceIntakeMarkdownResult.stdout, /^# DDD Owner Evidence Intake/m);
  assert.match(ownerEvidenceIntakeMarkdownResult.stdout, /Owner filter: platform-owners/);
  assert.match(ownerEvidenceIntakeMarkdownResult.stdout, /p1-p2-data-safety/);
  assert.match(ownerEvidenceIntakeMarkdownResult.stdout, /Receipt workflow:/);
  assert.match(ownerEvidenceIntakeMarkdownResult.stdout, /--lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>/);

  const brokenBundleDir = path.join(tmpDir, "broken-staging-handoff-bundle");
  fs.cpSync(handoffBundleDir, brokenBundleDir, { recursive: true });
  const brokenManifestFile = path.join(brokenBundleDir, "manifest.json");
  const brokenManifest = JSON.parse(fs.readFileSync(brokenManifestFile, "utf8"));
  brokenManifest.files = brokenManifest.files.filter((item) => item.file !== "owner-dispatch.json");
  fs.writeFileSync(brokenManifestFile, `${JSON.stringify(brokenManifest, null, 2)}\n`);
  const brokenBundleVerifyResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle-verify"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: brokenBundleDir,
    },
  });
  assert.notEqual(brokenBundleVerifyResult.status, 0, "handoff bundle verify should fail when manifest omits required files");
  const brokenBundleVerify = JSON.parse(brokenBundleVerifyResult.stdout);
  assert.equal(brokenBundleVerify.status, "BLOCKED");
  assert(brokenBundleVerify.issues.includes("manifest missing required bundle file: owner-dispatch.json"));

  const brokenMarkerBundleDir = path.join(tmpDir, "broken-marker-staging-handoff-bundle");
  fs.cpSync(handoffBundleDir, brokenMarkerBundleDir, { recursive: true });
  const brokenOperatorProgressFile = path.join(brokenMarkerBundleDir, "operator-progress.md");
  fs.writeFileSync(
    brokenOperatorProgressFile,
    fs.readFileSync(brokenOperatorProgressFile, "utf8").replace("## Lane Routes", "## Routes Removed"),
  );
  const brokenMarkerBundleVerifyResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle-verify"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: brokenMarkerBundleDir,
    },
  });
  assert.notEqual(brokenMarkerBundleVerifyResult.status, 0, "handoff bundle verify should fail when required lane route markers are missing");
  const brokenMarkerBundleVerify = JSON.parse(brokenMarkerBundleVerifyResult.stdout);
  assert.equal(brokenMarkerBundleVerify.status, "BLOCKED");
  assert(brokenMarkerBundleVerify.issues.some((issue) => issue === "bundle file missing required marker: operator-progress.md: ## Lane Routes"));

  const brokenAuditMarkerBundleDir = path.join(tmpDir, "broken-audit-marker-staging-handoff-bundle");
  fs.cpSync(handoffBundleDir, brokenAuditMarkerBundleDir, { recursive: true });
  const brokenAuditFile = path.join(brokenAuditMarkerBundleDir, "production-cutover-audit.md");
  fs.writeFileSync(
    brokenAuditFile,
    fs.readFileSync(brokenAuditFile, "utf8").replace("## Parallel Next Actions", "## Parallel Actions Removed"),
  );
  const brokenAuditBundleVerifyResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle-verify"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: brokenAuditMarkerBundleDir,
    },
  });
  assert.notEqual(brokenAuditBundleVerifyResult.status, 0, "handoff bundle verify should fail when required cutover audit markers are missing");
  const brokenAuditBundleVerify = JSON.parse(brokenAuditBundleVerifyResult.stdout);
  assert.equal(brokenAuditBundleVerify.status, "BLOCKED");
  assert(brokenAuditBundleVerify.issues.some((issue) => issue === "bundle file missing required marker: production-cutover-audit.md: ## Parallel Next Actions"));

  const brokenAuditActionsBundleDir = path.join(tmpDir, "broken-audit-actions-staging-handoff-bundle");
  fs.cpSync(handoffBundleDir, brokenAuditActionsBundleDir, { recursive: true });
  const brokenAuditActionsFile = path.join(brokenAuditActionsBundleDir, "production-cutover-audit.json");
  const brokenAuditActions = JSON.parse(fs.readFileSync(brokenAuditActionsFile, "utf8"));
  brokenAuditActions.parallelNextActions = brokenAuditActions.parallelNextActions.filter((action) => action.id !== "lane-completion-receipt");
  fs.writeFileSync(brokenAuditActionsFile, `${JSON.stringify(brokenAuditActions, null, 2)}\n`);
  const brokenAuditActionsBundleVerifyResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle-verify"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: brokenAuditActionsBundleDir,
    },
  });
  assert.notEqual(brokenAuditActionsBundleVerifyResult.status, 0, "handoff bundle verify should fail when cutover audit parallel actions drift");
  const brokenAuditActionsBundleVerify = JSON.parse(brokenAuditActionsBundleVerifyResult.stdout);
  assert.equal(brokenAuditActionsBundleVerify.status, "BLOCKED");
  assert(brokenAuditActionsBundleVerify.issues.some((issue) => issue === "production-cutover-audit parallelNextActions missing action: lane-completion-receipt"));

  const brokenUnblockPlanBundleDir = path.join(tmpDir, "broken-unblock-plan-staging-handoff-bundle");
  fs.cpSync(handoffBundleDir, brokenUnblockPlanBundleDir, { recursive: true });
  const brokenUnblockPlanFile = path.join(brokenUnblockPlanBundleDir, "production-unblock-plan.json");
  const brokenUnblockPlan = JSON.parse(fs.readFileSync(brokenUnblockPlanFile, "utf8"));
  brokenUnblockPlan.parallelWorkstreams = brokenUnblockPlan.parallelWorkstreams.map((workstream) => (
    workstream.id === "lane-completion-receipt" ? { ...workstream, verifyCommand: "" } : workstream
  ));
  fs.writeFileSync(brokenUnblockPlanFile, `${JSON.stringify(brokenUnblockPlan, null, 2)}\n`);
  const brokenUnblockPlanBundleVerifyResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle-verify"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: brokenUnblockPlanBundleDir,
    },
  });
  assert.notEqual(brokenUnblockPlanBundleVerifyResult.status, 0, "handoff bundle verify should fail when production unblock workstreams are incomplete");
  const brokenUnblockPlanBundleVerify = JSON.parse(brokenUnblockPlanBundleVerifyResult.stdout);
  assert.equal(brokenUnblockPlanBundleVerify.status, "BLOCKED");
  assert(brokenUnblockPlanBundleVerify.issues.some((issue) => issue === "production-unblock-plan parallelWorkstream is incomplete: lane-completion-receipt"));

  const brokenEvidenceReadinessBundleDir = path.join(tmpDir, "broken-evidence-readiness-staging-handoff-bundle");
  fs.cpSync(handoffBundleDir, brokenEvidenceReadinessBundleDir, { recursive: true });
  const brokenEvidenceReadinessFile = path.join(brokenEvidenceReadinessBundleDir, "production-evidence-readiness.json");
  const brokenEvidenceReadiness = JSON.parse(fs.readFileSync(brokenEvidenceReadinessFile, "utf8"));
  brokenEvidenceReadiness.evidenceGates = brokenEvidenceReadiness.evidenceGates.filter((gate) => gate.id !== "final-go-no-go");
  fs.writeFileSync(brokenEvidenceReadinessFile, `${JSON.stringify(brokenEvidenceReadiness, null, 2)}\n`);
  const brokenEvidenceReadinessBundleVerifyResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle-verify"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: brokenEvidenceReadinessBundleDir,
    },
  });
  assert.notEqual(brokenEvidenceReadinessBundleVerifyResult.status, 0, "handoff bundle verify should fail when production evidence readiness gates drift");
  const brokenEvidenceReadinessBundleVerify = JSON.parse(brokenEvidenceReadinessBundleVerifyResult.stdout);
  assert.equal(brokenEvidenceReadinessBundleVerify.status, "BLOCKED");
  assert(brokenEvidenceReadinessBundleVerify.issues.some((issue) => issue === "production-evidence-readiness missing evidence gate: final-go-no-go"));

  const brokenClosureBoardDir = path.join(tmpDir, "broken-closure-board-staging-handoff-bundle");
  fs.cpSync(handoffBundleDir, brokenClosureBoardDir, { recursive: true });
  const brokenClosureBoardFile = path.join(brokenClosureBoardDir, "evidence-closure-board.json");
  const brokenClosureBoard = JSON.parse(fs.readFileSync(brokenClosureBoardFile, "utf8"));
  brokenClosureBoard.laneCount = 999;
  fs.writeFileSync(brokenClosureBoardFile, `${JSON.stringify(brokenClosureBoard, null, 2)}\n`);
  const brokenClosureBoardVerifyResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle-verify"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: brokenClosureBoardDir,
    },
  });
  assert.notEqual(brokenClosureBoardVerifyResult.status, 0, "handoff bundle verify should fail when evidence closure board lane counts drift");
  const brokenClosureBoardVerify = JSON.parse(brokenClosureBoardVerifyResult.stdout);
  assert.equal(brokenClosureBoardVerify.status, "BLOCKED");
  assert(brokenClosureBoardVerify.issues.some((issue) => issue === "evidence-closure-board laneCount mismatch: expected=999; actual=5"));

  const brokenDispatchBundleDir = path.join(tmpDir, "broken-dispatch-staging-handoff-bundle");
  fs.cpSync(handoffBundleDir, brokenDispatchBundleDir, { recursive: true });
  const brokenOwnerDispatchFile = path.join(brokenDispatchBundleDir, "owner-dispatch.json");
  const brokenOwnerDispatch = JSON.parse(fs.readFileSync(brokenOwnerDispatchFile, "utf8"));
  brokenOwnerDispatch.owners = brokenOwnerDispatch.owners.map((owner) => ({
    ...owner,
    laneCount: 0,
    lanes: [],
  }));
  fs.writeFileSync(brokenOwnerDispatchFile, `${JSON.stringify(brokenOwnerDispatch, null, 2)}\n`);
  const brokenDispatchBundleVerifyResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle-verify"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: brokenDispatchBundleDir,
    },
  });
  assert.notEqual(brokenDispatchBundleVerifyResult.status, 0, "handoff bundle verify should fail when owner dispatch lane routes are missing");
  const brokenDispatchBundleVerify = JSON.parse(brokenDispatchBundleVerifyResult.stdout);
  assert.equal(brokenDispatchBundleVerify.status, "BLOCKED");
  assert(brokenDispatchBundleVerify.issues.some((issue) => issue === "owner-dispatch must include at least one owner lane route"));

  const brokenOwnerPacketBundleDir = path.join(tmpDir, "broken-owner-packet-staging-handoff-bundle");
  fs.cpSync(handoffBundleDir, brokenOwnerPacketBundleDir, { recursive: true });
  const brokenReleaseInfraPacketFile = path.join(brokenOwnerPacketBundleDir, "owner-packets", "release-infra.md");
  fs.writeFileSync(
    brokenReleaseInfraPacketFile,
    fs.readFileSync(brokenReleaseInfraPacketFile, "utf8").replaceAll("Expected artifacts:", "Expected artifact list removed:"),
  );
  const brokenOwnerPacketBundleVerifyResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle-verify"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: brokenOwnerPacketBundleDir,
    },
  });
  assert.notEqual(brokenOwnerPacketBundleVerifyResult.status, 0, "handoff bundle verify should fail when owner packet submission route artifact lists are missing");
  const brokenOwnerPacketBundleVerify = JSON.parse(brokenOwnerPacketBundleVerifyResult.stdout);
  assert.equal(brokenOwnerPacketBundleVerify.status, "BLOCKED");
  assert(brokenOwnerPacketBundleVerify.issues.some((issue) => issue === "owner packet missing required marker for release-infra: Expected artifacts:"));

  const brokenOwnerPacketArtifactBundleDir = path.join(tmpDir, "broken-owner-packet-artifact-staging-handoff-bundle");
  fs.cpSync(handoffBundleDir, brokenOwnerPacketArtifactBundleDir, { recursive: true });
  const brokenPlatformOwnersPacketFile = path.join(brokenOwnerPacketArtifactBundleDir, "owner-packets", "platform-owners.md");
  fs.writeFileSync(
    brokenPlatformOwnersPacketFile,
    fs.readFileSync(brokenPlatformOwnersPacketFile, "utf8").replaceAll("tmp/ddd-explain/*.json", "tmp/ddd-explain/removed.json"),
  );
  const brokenOwnerPacketArtifactBundleVerifyResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle-verify"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: brokenOwnerPacketArtifactBundleDir,
    },
  });
  assert.notEqual(brokenOwnerPacketArtifactBundleVerifyResult.status, 0, "handoff bundle verify should fail when owner packet lane artifact content is missing");
  const brokenOwnerPacketArtifactBundleVerify = JSON.parse(brokenOwnerPacketArtifactBundleVerifyResult.stdout);
  assert.equal(brokenOwnerPacketArtifactBundleVerify.status, "BLOCKED");
  assert(brokenOwnerPacketArtifactBundleVerify.issues.some((issue) => issue === "owner packet missing lane fragment for platform-owners:p1-p2-data-safety: tmp/ddd-explain/*.json"));

  const brokenOwnerPacketJsonBundleDir = path.join(tmpDir, "broken-owner-packet-json-staging-handoff-bundle");
  fs.cpSync(handoffBundleDir, brokenOwnerPacketJsonBundleDir, { recursive: true });
  const brokenReleaseInfraJsonPacketFile = path.join(brokenOwnerPacketJsonBundleDir, "owner-packets", "release-infra.json");
  const brokenReleaseInfraJsonPacket = JSON.parse(fs.readFileSync(brokenReleaseInfraJsonPacketFile, "utf8"));
  brokenReleaseInfraJsonPacket.queueLanes = brokenReleaseInfraJsonPacket.queueLanes.map((lane) => (
    lane.lane === "p0-docker-images" ? { ...lane, sourcePlan: "wrong-plan.json" } : lane
  ));
  fs.writeFileSync(brokenReleaseInfraJsonPacketFile, `${JSON.stringify(brokenReleaseInfraJsonPacket, null, 2)}\n`);
  const brokenOwnerPacketJsonBundleVerifyResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle-verify"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: brokenOwnerPacketJsonBundleDir,
    },
  });
  assert.notEqual(brokenOwnerPacketJsonBundleVerifyResult.status, 0, "handoff bundle verify should fail when owner packet JSON queue lanes drift from owner dispatch");
  const brokenOwnerPacketJsonBundleVerify = JSON.parse(brokenOwnerPacketJsonBundleVerifyResult.stdout);
  assert.equal(brokenOwnerPacketJsonBundleVerify.status, "BLOCKED");
  assert(brokenOwnerPacketJsonBundleVerify.issues.some((issue) => issue === "owner packet JSON lane sourcePlan mismatch for release-infra:p0-docker-images"));

  const brokenOwnerPacketSummaryBundleDir = path.join(tmpDir, "broken-owner-packet-summary-staging-handoff-bundle");
  fs.cpSync(handoffBundleDir, brokenOwnerPacketSummaryBundleDir, { recursive: true });
  const brokenPlatformEventsJsonPacketFile = path.join(brokenOwnerPacketSummaryBundleDir, "owner-packets", "platform-events.json");
  const brokenPlatformEventsJsonPacket = JSON.parse(fs.readFileSync(brokenPlatformEventsJsonPacketFile, "utf8"));
  brokenPlatformEventsJsonPacket.nextCommand = "node scripts/ddd-staging-execution-checklist.mjs --wrong-command";
  fs.writeFileSync(brokenPlatformEventsJsonPacketFile, `${JSON.stringify(brokenPlatformEventsJsonPacket, null, 2)}\n`);
  const brokenOwnerPacketSummaryBundleVerifyResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-bundle-verify"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: brokenOwnerPacketSummaryBundleDir,
    },
  });
  assert.notEqual(brokenOwnerPacketSummaryBundleVerifyResult.status, 0, "handoff bundle verify should fail when owner packet JSON summary fields drift from owner dispatch");
  const brokenOwnerPacketSummaryBundleVerify = JSON.parse(brokenOwnerPacketSummaryBundleVerifyResult.stdout);
  assert.equal(brokenOwnerPacketSummaryBundleVerify.status, "BLOCKED");
  assert(brokenOwnerPacketSummaryBundleVerify.issues.some((issue) => issue === "owner packet JSON nextCommand mismatch for platform-events"));

  const executionStatusResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--execution-status"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(executionStatusResult.status, 0, executionStatusResult.stderr || executionStatusResult.stdout);
  const executionStatus = JSON.parse(executionStatusResult.stdout);
  assert.equal(executionStatus.status, "BLOCKED");
  assert.equal(executionStatus.cutoverAllowed, false);
  assert.equal(executionStatus.blockedGateCount, 5);
  assert.equal(executionStatus.handoffBundle.status, "PASS");
  assert(executionStatus.laneRoutes.some((lane) => lane.lane === "p0-docker-images" && lane.sourcePlan === "docker-image-submission-plan.json"));
  assert(executionStatus.laneRoutes.some((lane) => lane.lane === "p1-runtime-business" && lane.command === "node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown"));
  assert(executionStatus.laneRoutes.some((lane) => lane.lane === "p1-p2-data-safety" && lane.sourcePlan === "data-safety-submission-plan.json"));
  assert.equal(executionStatus.nextCommand, "node scripts/ddd-staging-execution-checklist.mjs --commands");

  const executionStatusMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--execution-status-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(executionStatusMarkdownResult.status, 0, executionStatusMarkdownResult.stderr || executionStatusMarkdownResult.stdout);
  assert.match(executionStatusMarkdownResult.stdout, /^# DDD Staging Execution Status/m);
  assert.match(executionStatusMarkdownResult.stdout, /Handoff bundle: PASS/);
  assert.match(executionStatusMarkdownResult.stdout, /\| docker-images \| release-infra \| PASS \|/);
  assert.match(executionStatusMarkdownResult.stdout, /## Lane Routes/);
  assert.match(executionStatusMarkdownResult.stdout, /docker-image-submission-plan\.json/);
  assert.match(executionStatusMarkdownResult.stdout, /runtime-business-submission-plan\.json/);
  assert.match(executionStatusMarkdownResult.stdout, /data-safety-submission-plan\.json/);

  const handoffSummaryMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--handoff-summary-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(handoffSummaryMarkdownResult.status, 0, handoffSummaryMarkdownResult.stderr || handoffSummaryMarkdownResult.stdout);
  assert.match(handoffSummaryMarkdownResult.stdout, /^## DDD Staging Handoff/m);
  assert.match(handoffSummaryMarkdownResult.stdout, /Artifact: `ddd-staging-handoff-bundle`/);
  assert.match(handoffSummaryMarkdownResult.stdout, /^# DDD Staging Readiness Rollup/m);
  assert.match(handoffSummaryMarkdownResult.stdout, /^# DDD Staging Execution Status/m);
  assert.match(handoffSummaryMarkdownResult.stdout, /Handoff bundle: PASS/);

  const finalReviewResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--final-review"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(finalReviewResult.status, 0, finalReviewResult.stderr || finalReviewResult.stdout);
  const finalReview = JSON.parse(finalReviewResult.stdout);
  assert.equal(finalReview.status, "BLOCKED");
  assert.equal(finalReview.cutoverReady, false);
  assert.equal(finalReview.handoffBundle.status, "PASS");
  assert.equal(finalReview.ownerDispatch.status, "PASS");
  assert.equal(finalReview.ownerDispatch.ownerTemplateCount, 5);
  assert.equal(finalReview.laneCompletionReceipt.status, "MISSING");
  assert.equal(finalReview.laneCompletionReceipt.coverage.expectedLaneCount, 5);
  assert.equal(finalReview.laneCompletionReceipt.coverage.coveredLaneCount, 0);
  assert.equal(finalReview.evidenceClosureBoard.closedLaneCount, 0);
  assert.equal(finalReview.evidenceClosureBoard.laneCount, 5);
  assert.equal(finalReview.evidenceClosureBoard.nextLane.key, "platform-owners:p1-p2-data-safety");
  assert.equal(finalReview.laneReceiptFragment.owner, "release-infra");
  assert.equal(finalReview.laneReceiptFragment.lane, "final-review");
  assert.equal(finalReview.laneReceiptFragment.status, "BLOCKED");
  assert(finalReview.laneReceiptFragment.providedArtifacts.includes("artifacts/ddd/release/staging-handoff-bundle/final-review.json"));
  assert(finalReview.laneReceiptFragment.missingArtifacts.includes("tmp/ddd-explain/*.json"));
  assert(finalReview.laneReceiptFragment.acceptanceCommands.includes("node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>"));
  assert(finalReview.checklist.some((item) => item.id === "handoff-bundle-integrity" && item.passed === true));
  assert(finalReview.checklist.some((item) => item.id === "owner-lane-completion-receipt" && item.passed === false));
  assert(finalReview.checklist.some((item) => item.id === "cutover-allowed" && item.passed === false));
  assert.equal(finalReview.blockers.some((gate) => gate.gate === "docker-images"), false);
  assert.equal(finalReview.nextCommand, "node scripts/ddd-staging-execution-checklist.mjs --commands");

  const finalReviewWithPartialLaneReceiptResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--final-review"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_LANE_COMPLETION_RECEIPT_FILE: partialCompletedLaneCompletionReceiptFile,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(finalReviewWithPartialLaneReceiptResult.status, 0, finalReviewWithPartialLaneReceiptResult.stderr || finalReviewWithPartialLaneReceiptResult.stdout);
  const finalReviewWithPartialLaneReceipt = JSON.parse(finalReviewWithPartialLaneReceiptResult.stdout);
  assert.equal(finalReviewWithPartialLaneReceipt.status, "BLOCKED");
  assert.equal(finalReviewWithPartialLaneReceipt.laneCompletionReceipt.status, "PASS");
  assert.equal(finalReviewWithPartialLaneReceipt.laneCompletionReceipt.coverage.status, "BLOCKED");
  assert.equal(finalReviewWithPartialLaneReceipt.laneCompletionReceipt.coverage.coveredLaneCount, 1);
  assert.equal(finalReviewWithPartialLaneReceipt.evidenceClosureBoard.closedLaneCount, 1);
  assert.equal(finalReviewWithPartialLaneReceipt.evidenceClosureBoard.openLaneCount, 4);
  assert(finalReviewWithPartialLaneReceipt.laneCompletionReceipt.coverage.missingLanes.length > 0);
  assert(finalReviewWithPartialLaneReceipt.checklist.some((item) => item.id === "owner-lane-completion-receipt" && item.passed === false && item.blocker.includes("missing lanes=")));

  const finalReviewWithLaneReceiptResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--final-review"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_LANE_COMPLETION_RECEIPT_FILE: completedLaneCompletionReceiptFile,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(finalReviewWithLaneReceiptResult.status, 0, finalReviewWithLaneReceiptResult.stderr || finalReviewWithLaneReceiptResult.stdout);
  const finalReviewWithLaneReceipt = JSON.parse(finalReviewWithLaneReceiptResult.stdout);
  assert.equal(finalReviewWithLaneReceipt.status, "BLOCKED");
  assert.equal(finalReviewWithLaneReceipt.cutoverReady, false);
  assert.equal(finalReviewWithLaneReceipt.laneCompletionReceipt.status, "PASS");
  assert.equal(finalReviewWithLaneReceipt.laneCompletionReceipt.receiptFile, completedLaneCompletionReceiptFile);
  assert.equal(finalReviewWithLaneReceipt.laneCompletionReceipt.coverage.status, "PASS");
  assert.equal(finalReviewWithLaneReceipt.laneCompletionReceipt.coverage.coveredLaneCount, finalReviewWithLaneReceipt.laneCompletionReceipt.coverage.expectedLaneCount);
  assert.equal(finalReviewWithLaneReceipt.evidenceClosureBoard.status, "PASS");
  assert.equal(finalReviewWithLaneReceipt.evidenceClosureBoard.closedLaneCount, 5);
  assert.equal(finalReviewWithLaneReceipt.evidenceClosureBoard.openLaneCount, 0);
  assert(finalReviewWithLaneReceipt.checklist.some((item) => item.id === "owner-lane-completion-receipt" && item.passed === true));
  assert(finalReviewWithLaneReceipt.checklist.some((item) => item.id === "cutover-allowed" && item.passed === false));

  const finalReviewMarkdownResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--final-review-markdown"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.equal(finalReviewMarkdownResult.status, 0, finalReviewMarkdownResult.stderr || finalReviewMarkdownResult.stdout);
  assert.match(finalReviewMarkdownResult.stdout, /^# DDD Release Owner Final Review/m);
  assert.match(finalReviewMarkdownResult.stdout, /Cutover ready: false/);
  assert.match(finalReviewMarkdownResult.stdout, /Owner templates: 5\/5/);
  assert.match(finalReviewMarkdownResult.stdout, /Lane receipt: MISSING/);
  assert.match(finalReviewMarkdownResult.stdout, /Lane receipt coverage: 0\/5/);
  assert.match(finalReviewMarkdownResult.stdout, /Evidence closure: 0\/5/);
  assert.match(finalReviewMarkdownResult.stdout, /## Evidence Closure/);
  assert.match(finalReviewMarkdownResult.stdout, /\| Owner lane completion receipt contract passes \| no \|/);
  assert.match(finalReviewMarkdownResult.stdout, /\| Final rollup allows cutover \| no \|/);
  assert.match(finalReviewMarkdownResult.stdout, /^## Lane Receipt Fragment/m);
  assert.match(finalReviewMarkdownResult.stdout, /"lane": "final-review"/);
  assert.match(finalReviewMarkdownResult.stdout, /--final-review-enforce --lane-completion-receipt-file=<receipt-file>/);

  const finalReviewEnforceResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--final-review-enforce"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_HANDOFF_BUNDLE_DIR: handoffBundleDir,
    },
  });
  assert.notEqual(finalReviewEnforceResult.status, 0, "final review enforce must block while cutoverReady is false");
  const finalReviewEnforce = JSON.parse(finalReviewEnforceResult.stdout);
  assert.equal(finalReviewEnforce.status, "BLOCKED");
  assert.equal(finalReviewEnforce.cutoverReady, false);
  assert.equal(finalReviewEnforce.handoffBundle.status, "PASS");

  const evidenceGapsResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--evidence-gaps"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: evidenceGapsOutputBase,
    },
  });
  assert.equal(evidenceGapsResult.status, 0, evidenceGapsResult.stderr || evidenceGapsResult.stdout);
  const evidenceGaps = JSON.parse(evidenceGapsResult.stdout);
  assert.equal(evidenceGaps.status, "STAGING_REQUIRED");
  assert.equal(evidenceGaps.cutoverAllowed, false);
  assert.equal(evidenceGaps.willWriteFiles, false);
  assert.equal(evidenceGaps.gapCount, 6);
  assert(evidenceGaps.gaps.some((gap) => gap.id === "p0-release-env" && gap.nextCommand === "node scripts/ddd-release-env-init.mjs --check"));
  assert(evidenceGaps.gaps.some((gap) => gap.id === "p1-runtime-business" && gap.nextCommand === "node scripts/ddd-staging-runtime-check.mjs"));
  assert(evidenceGaps.gaps.some((gap) => gap.id === "p1-rollback" && gap.nextCommand === "node scripts/ddd-staging-data-safety-check.mjs"));
  assert(evidenceGaps.gaps.some((gap) => gap.id === "p2-database-performance" && gap.nextCommand === "node scripts/ddd-staging-data-safety-check.mjs"));
  assert(evidenceGaps.gaps.some((gap) => gap.id === "p1-runtime-business" && gap.artifacts.includes("artifacts/ddd/frontend/frontend-smoke.json")));
  assert.equal(fs.existsSync(`${evidenceGapsOutputBase}.json`), false, "evidence gaps mode should not write checklist JSON");

  const bashCheck = spawnSyncWithTimeout("bash", ["--version"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  if (bashCheck.status === 0) {
    const envInitTarget = `tmp/staging-checklist-env-init-${process.pid}.env`;
    const envInitReceipt = `tmp/staging-checklist-env-init-${process.pid}-receipt.json`;
    const envInitResult = spawnSyncWithTimeout("node", ["scripts/ddd-release-env-init.mjs"], {
      cwd: repoRoot,
      encoding: "utf8",
      env: {
        ...process.env,
        DDD_FINAL_OWNER_QUEUE_ENV_TEMPLATE: "artifacts\\ddd\\release\\release-final-owner-queue-env.template.env",
        DDD_FINAL_OWNER_QUEUE_ENV_TARGET: envInitTarget,
        DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT: envInitReceipt,
      },
    });
    assert.equal(envInitResult.status, 0, envInitResult.stderr || envInitResult.stdout);
    assert.equal(fs.existsSync(path.join(repoRoot, envInitTarget)), true, "env init wrapper should create requested env target");
    assert.equal(fs.existsSync(path.join(repoRoot, envInitReceipt)), true, "env init wrapper should write requested receipt");
    const envInitReceiptJson = JSON.parse(fs.readFileSync(envInitReceipt, "utf8"));
    assert.match(envInitReceiptJson.targetModeOctal, /^[0-7]{3}$/);
    if (envInitReceiptJson.permissionSafe === false) {
      assert.match(envInitResult.stderr, /initialized env target is not owner-only/);
      assert.match(envInitResult.stderr, /local fill template only/);
    }
    assert.match(fs.readFileSync(envInitTarget, "utf8"), /# Lumira DDD final owner queue environment template\./);
    fs.rmSync(path.join(repoRoot, envInitTarget), { force: true });
    fs.rmSync(path.join(repoRoot, envInitReceipt), { force: true });
  }

  const summaryResult = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--summary"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: summaryOutputBase,
    },
  });
  assert.equal(summaryResult.status, 0, summaryResult.stderr || summaryResult.stdout);
  assert.match(summaryResult.stdout, /^status=STAGING_REQUIRED/m);
  assert.match(summaryResult.stdout, /^finalRecommendation=NO_GO_STRICT/m);
  assert.match(summaryResult.stdout, /^cutoverAllowed=false/m);
  assert.match(summaryResult.stdout, /^blockedTracks=6/m);
  assert.match(summaryResult.stdout, /- p0-release-env\trelease-infra\t/);
  assert.match(summaryResult.stdout, /- platform-events\tblockers=9\tsecretKeys=3\tkeys=10/);
  assert.match(summaryResult.stdout, /node scripts\/ddd-release-env-init\.mjs/);
  assert.match(summaryResult.stdout, /node scripts\/ddd-release-env-init\.mjs --check/);
  assert.match(summaryResult.stdout, /node scripts\/ddd-staging-execution-checklist\.mjs --owner-packets/);
  assert.equal(fs.existsSync(`${summaryOutputBase}.json`), false, "summary mode should not write checklist JSON");

  const result = spawnSyncWithTimeout("node", ["scripts/ddd-staging-execution-checklist.mjs", "--owner-packets"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: outputBase,
    },
  });

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /\[ddd-staging-execution-checklist\] status=/);
  assert.match(result.stdout, /ownerPackets=/);

  const jsonPath = `${outputBase}.json`;
  const markdownPath = `${outputBase}.md`;
  const ownerPacketDir = `${outputBase}-owner-packets`;
  const ownerPacketIndexPath = path.join(ownerPacketDir, "README.md");
  assert.equal(fs.existsSync(jsonPath), true, "checklist must write JSON output");
  assert.equal(fs.existsSync(markdownPath), true, "checklist must write Markdown output");
  assert.equal(fs.existsSync(ownerPacketDir), true, "checklist should write owner packet directory when requested");
  assert.equal(fs.existsSync(ownerPacketIndexPath), true, "checklist should write owner packet index when requested");

  const checklist = JSON.parse(fs.readFileSync(jsonPath, "utf8"));
  const markdown = fs.readFileSync(markdownPath, "utf8");

  assert.equal(checklist.status, "STAGING_REQUIRED");
  assert.equal(checklist.finalRecommendation, "NO_GO_STRICT");
  assert.equal(checklist.cutoverAllowed, false);
  assert(checklist.releaseEnv.ownerBlockerSummary.length >= 1, "checklist should expose release env owner blockers");
  assert.equal(checklist.releaseEnv.ownerCount, checklist.releaseEnv.ownerBlockerSummary.length);
  const platformEventsOwner = checklist.releaseEnv.ownerBlockerSummary.find((owner) => owner.owner === "platform-events");
  assert(platformEventsOwner, "checklist should include platform-events owner handoff");
  assert(platformEventsOwner.keys.includes("SAAS_EVENT_REDIS_STREAM_KEY"), "owner handoff should include concrete key names");
  assert(platformEventsOwner.ownerInputReasons.includes("secret-manager"), "owner handoff should include input reasons");
  assert.equal(checklist.tracks.length, 6);
  assert(checklist.immediateWaves.length >= 1, "checklist should expose immediate P0 waves");

  const trackIds = checklist.tracks.map((track) => track.id);
  assert.deepEqual(trackIds, [
    "p0-release-env",
    "p0-images",
    "p1-runtime-business",
    "p1-rollback",
    "p2-database-performance",
    "p3-final-strict",
  ]);

  const releaseEnvTrack = checklist.tracks.find((track) => track.id === "p0-release-env");
  assert(releaseEnvTrack.envKeys.includes("LUMIRA_BASE_URL"), "release env track should surface backend URL env keys");
  assert(
    releaseEnvTrack.commands.includes("DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs"),
    "release env track should include lint command",
  );
  assert(
    releaseEnvTrack.setupCommands.includes("node scripts/ddd-release-env-init.mjs --check")
      && releaseEnvTrack.setupCommands.includes("node scripts/ddd-release-env-init.mjs"),
    "release env track should include env init check and setup commands",
  );
  const imageTrack = checklist.tracks.find((track) => track.id === "p0-images");
  assert(
    imageTrack.setupCommands.includes("node scripts/ddd-docker-build-evidence.mjs --check"),
    "image track should include Docker evidence check setup command",
  );

  const finalTrack = checklist.tracks.find((track) => track.id === "p3-final-strict");
  const runtimeTrack = checklist.tracks.find((track) => track.id === "p1-runtime-business");
  assert(
    runtimeTrack.setupCommands.includes("node scripts/ddd-staging-runtime-check.mjs"),
    "runtime track should include staging runtime check setup command",
  );
  const rollbackTrack = checklist.tracks.find((track) => track.id === "p1-rollback");
  assert(
    rollbackTrack.setupCommands.includes("node scripts/ddd-staging-data-safety-check.mjs"),
    "rollback track should include staging data safety check setup command",
  );
  const databaseTrack = checklist.tracks.find((track) => track.id === "p2-database-performance");
  assert(
    databaseTrack.setupCommands.includes("node scripts/ddd-staging-data-safety-check.mjs"),
    "database track should include staging data safety check setup command",
  );
  assert(
    finalTrack.commands.includes("DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh"),
    "final track should include enforced final gate command",
  );

  assert.match(markdown, /^# DDD Staging Execution Checklist/m);
  assert.match(markdown, /Status: STAGING_REQUIRED/);
  assert.match(markdown, /## First Move/);
  assert.match(markdown, /node scripts\/ddd-release-env-init\.mjs/);
  assert.match(markdown, /## Release Env Owner Handoff/);
  assert.match(markdown, /platform-events: blockers=/);
  assert.match(markdown, /keys: .*SAAS_EVENT_REDIS_STREAM_KEY/);
  assert.match(markdown, /reasons: .*secret-manager/);
  assert.match(markdown, /## Immediate P0 Waves/);
  assert.match(markdown, /## Execution Tracks/);
  assert.match(markdown, /p0-release-env: P0 release env and config/);
  assert.match(markdown, /p3-final-strict: P3 strict orchestrator and final gate/);

  const platformEventsPacketPath = path.join(ownerPacketDir, "platform-events.md");
  const platformEventsEnvTemplatePath = path.join(ownerPacketDir, "platform-events.blocking-inputs.template.env");
  assert.equal(fs.existsSync(platformEventsPacketPath), true, "platform-events owner packet should be generated");
  assert.equal(fs.existsSync(platformEventsEnvTemplatePath), true, "platform-events owner env template should be generated");
  const ownerPacketIndex = fs.readFileSync(ownerPacketIndexPath, "utf8");
  assert.match(ownerPacketIndex, /^# DDD Staging Owner Packets/m);
  assert.match(ownerPacketIndex, /## Packet Contents/);
  assert.match(ownerPacketIndex, /missing evidence artifacts/);
  assert.match(ownerPacketIndex, /\| Owner \| Blockers \| Secret keys \| Missing artifacts \| Markdown \| JSON \| Env template \|/);
  assert.match(ownerPacketIndex, /\| platform-events \| 9 \| 3 \| 0 \| \[platform-events\.md\]\(platform-events\.md\) \|/);
  assert.match(ownerPacketIndex, /platform-events\.blocking-inputs\.template\.env/);
  assert.match(ownerPacketIndex, /DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts\/ddd\/release\/release-execution-commands\.sh/);
  const platformEventsEnvTemplate = fs.readFileSync(platformEventsEnvTemplatePath, "utf8");
  assert.match(platformEventsEnvTemplate, /^# Owner filter: platform-events$/m);
  assert.match(platformEventsEnvTemplate, /^SAAS_EVENT_REDIS_STREAM_KEY=__REQUIRED_SECRET_REF__$/m);
  const platformEventsPacket = fs.readFileSync(platformEventsPacketPath, "utf8");
  assert.match(platformEventsPacket, /^# DDD Staging Owner Packet: platform-events/m);
  assert.match(platformEventsPacket, /## Required Keys/);
  assert.match(platformEventsPacket, /SAAS_EVENT_REDIS_STREAM_KEY/);
  assert.match(platformEventsPacket, /## Post-Fill Validation/);
  assert.match(platformEventsPacket, /DDD_RELEASE_ENV_FILE=<release-env-file> node scripts\/ddd-release-env-file-lint\.mjs/);
  assert.match(platformEventsPacket, /## Staging Evidence Gaps/);
  assert.match(platformEventsPacket, /p0-release-env: P0 release env and config/);
  const releaseInfraPacket = fs.readFileSync(path.join(ownerPacketDir, "release-infra.md"), "utf8");
  assert.match(releaseInfraPacket, /node scripts\/ddd-release-env-init\.mjs --check/);
  assert.match(releaseInfraPacket, /node scripts\/ddd-release-env-init\.mjs/);
  assert.match(releaseInfraPacket, /--next-action-env-receipt-output=<receipt-file>/);
  assert.match(releaseInfraPacket, /--operator-progress-markdown --next-action-env-receipt-file=<receipt-file>/);
  assert.match(releaseInfraPacket, /## Current Blocking Inputs/);
  assert.match(releaseInfraPacket, /\| 2 \| `p0-docker-images` \| PASS \| 0 \|/);
  assert.match(releaseInfraPacket, /### p0-docker-images[\s\S]*Currently missing artifacts: none/);
  assert.match(releaseInfraPacket, /## Submission Routes/);
  assert.match(releaseInfraPacket, /Source plan: `docker-image-submission-plan\.json`/);
  assert.match(releaseInfraPacket, /Next command: `node scripts\/ddd-staging-execution-checklist\.mjs --docker-image-submission-plan-markdown`/);
  assert.match(releaseInfraPacket, /Expected artifacts: `artifacts\/ddd\/build\/docker-image-evidence\.json`/);
  assert.match(releaseInfraPacket, /Source plan: `runtime-business-submission-plan\.json`/);
  assert.match(releaseInfraPacket, /Next command: `node scripts\/ddd-staging-execution-checklist\.mjs --runtime-business-submission-plan-markdown`/);
  assert.match(releaseInfraPacket, /Expected artifacts: .*`artifacts\/ddd\/frontend\/frontend-smoke\.json`/);
  assert.match(releaseInfraPacket, /p0-images: P0 deployable images/);
  assert.match(releaseInfraPacket, /Next command: `node scripts\/ddd-docker-build-evidence\.mjs --check`/);
  assert.match(releaseInfraPacket, /p1-runtime-business: P1 runtime and business acceptance/);
  assert.match(releaseInfraPacket, /Next command: `node scripts\/ddd-staging-runtime-check\.mjs`/);
  const platformOwnersPacket = fs.readFileSync(path.join(ownerPacketDir, "platform-owners.md"), "utf8");
  assert.match(platformOwnersPacket, /Source plan: `data-safety-submission-plan\.json`/);
  assert.match(platformOwnersPacket, /Expected artifacts: .*`tmp\/ddd-explain\/\*\.json`/);
  assert.match(platformOwnersPacket, /Currently missing artifacts: `tmp\/ddd-explain\/\*\.json`/);
  const aiOwnerPacket = fs.readFileSync(path.join(ownerPacketDir, "ai-owner.md"), "utf8");
  assert.match(aiOwnerPacket, /p0-release-env: P0 release env and config/);
  assert.match(aiOwnerPacket, /p1-runtime-business: P1 runtime and business acceptance/);

  const ownerOnlyResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--owner-packets",
    "--owner=ai-owner",
  ], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: ownerOnlyOutputBase,
    },
  });
  assert.equal(ownerOnlyResult.status, 0, ownerOnlyResult.stderr || ownerOnlyResult.stdout);
  assert.match(ownerOnlyResult.stdout, /ownerPackets=1/);
  const ownerOnlyDir = `${ownerOnlyOutputBase}-owner-packets`;
  assert.equal(fs.existsSync(path.join(ownerOnlyDir, "ai-owner.md")), true, "owner filter should generate requested owner packet");
  assert.equal(fs.existsSync(path.join(ownerOnlyDir, "ai-owner.blocking-inputs.template.env")), true, "owner filter should generate requested owner env template");
  assert.equal(fs.existsSync(path.join(ownerOnlyDir, "platform-events.md")), false, "owner filter should not generate unrelated owner packet");
  assert.equal(fs.existsSync(path.join(ownerOnlyDir, "platform-events.blocking-inputs.template.env")), false, "owner filter should not generate unrelated owner env template");
  const ownerOnlyIndex = fs.readFileSync(path.join(ownerOnlyDir, "README.md"), "utf8");
  assert.match(ownerOnlyIndex, /\| ai-owner \| 6 \| 2 \| 0 \| \[ai-owner\.md\]\(ai-owner\.md\) \|/);
  assert.doesNotMatch(ownerOnlyIndex, /platform-events\.md/);

  const unknownOwnerResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--owner-packets",
    "--owner=unknown-owner",
  ], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: path.join(tmpDir, "staging-checklist-unknown-owner"),
    },
  });
  assert.equal(unknownOwnerResult.status, 2, "unknown owner filter should fail fast");
  assert.match(unknownOwnerResult.stderr, /unknown owner filter: unknown-owner/);
  assert.match(unknownOwnerResult.stderr, /available owners: .*ai-owner/);

  const listOwnersResult = spawnSyncWithTimeout("node", [
    "scripts/ddd-staging-execution-checklist.mjs",
    "--list-owners",
  ], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_STAGING_CHECKLIST_OUTPUT: listOwnersOutputBase,
    },
  });
  assert.equal(listOwnersResult.status, 0, listOwnersResult.stderr || listOwnersResult.stdout);
  assert.match(listOwnersResult.stdout, /platform-events\tblockers=9\tsecretKeys=3/);
  assert.match(listOwnersResult.stdout, /ai-owner\tblockers=6\tsecretKeys=2/);
  assert.equal(fs.existsSync(`${listOwnersOutputBase}.json`), false, "list owners mode should not write checklist JSON");

  console.log("[ddd-staging-execution-checklist.test] ok");
} finally {
  fs.rmSync(tmpDir, { recursive: true, force: true });
}
