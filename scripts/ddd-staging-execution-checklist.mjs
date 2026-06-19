#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";

const repoRoot = path.resolve(import.meta.dirname, "..");
const releaseDir = path.join(repoRoot, "artifacts", "ddd", "release");
const args = new Set(process.argv.slice(2));
const rawArgs = process.argv.slice(2);
const help = args.has("--help") || args.has("-h");
const jsonOnly = args.has("--json");
const markdownOnly = args.has("--markdown");
const ownerPackets = args.has("--owner-packets");
const listOwners = args.has("--list-owners");
const summaryOnly = args.has("--summary");
const dispatchCheck = args.has("--dispatch-check");
const commandsOnly = args.has("--commands");
const evidenceGapsOnly = args.has("--evidence-gaps");
const evidenceRunbookOnly = args.has("--evidence-runbook");
const evidenceRunbookMarkdownOnly = args.has("--evidence-runbook-markdown");
const evidenceAcceptanceOnly = args.has("--evidence-acceptance");
const evidenceAcceptanceMarkdownOnly = args.has("--evidence-acceptance-markdown");
const evidenceArtifactGapReportOnly = args.has("--evidence-artifact-gap-report");
const evidenceArtifactGapReportMarkdownOnly = args.has("--evidence-artifact-gap-report-markdown");
const explainArtifactPlanOnly = args.has("--explain-artifact-plan");
const explainArtifactPlanMarkdownOnly = args.has("--explain-artifact-plan-markdown");
const closurePlanOnly = args.has("--closure-plan");
const closurePlanMarkdownOnly = args.has("--closure-plan-markdown");
const nextActionQueueOnly = args.has("--next-action-queue");
const nextActionQueueMarkdownOnly = args.has("--next-action-queue-markdown");
const ownerLaneMatrixOnly = args.has("--owner-lane-matrix");
const ownerLaneMatrixMarkdownOnly = args.has("--owner-lane-matrix-markdown");
const laneCompletionReceiptTemplateOnly = args.has("--lane-completion-receipt-template");
const laneCompletionReceiptTemplateMarkdownOnly = args.has("--lane-completion-receipt-template-markdown");
const laneCompletionReceiptInitOnly = args.has("--lane-completion-receipt-init");
const laneCompletionReceiptContractOnly = args.has("--lane-completion-receipt-contract");
const laneCompletionReceiptCoverageOnly = args.has("--lane-completion-receipt-coverage");
const laneCompletionReceiptCoverageMarkdownOnly = args.has("--lane-completion-receipt-coverage-markdown");
const laneCompletionReceiptBase64Only = args.has("--lane-completion-receipt-base64");
const evidenceClosureBoardOnly = args.has("--evidence-closure-board");
const evidenceClosureBoardMarkdownOnly = args.has("--evidence-closure-board-markdown");
const evidenceClosureBoardCsvOnly = args.has("--evidence-closure-board-csv");
const laneReceiptFragmentsOnly = args.has("--lane-receipt-fragments");
const laneReceiptFragmentsMarkdownOnly = args.has("--lane-receipt-fragments-markdown");
const laneReceiptDraftOnly = args.has("--lane-receipt-draft");
const laneReceiptDraftMarkdownOnly = args.has("--lane-receipt-draft-markdown");
const ownerEvidenceIntakeOnly = args.has("--owner-evidence-intake");
const ownerEvidenceIntakeMarkdownOnly = args.has("--owner-evidence-intake-markdown");
const laneCompletionSubmissionPlanOnly = args.has("--lane-completion-submission-plan");
const laneCompletionSubmissionPlanMarkdownOnly = args.has("--lane-completion-submission-plan-markdown");
const laneCompletionSubmissionCheckOnly = args.has("--lane-completion-submission-check");
const laneCompletionSubmissionCheckMarkdownOnly = args.has("--lane-completion-submission-check-markdown");
const nextActionEnvTemplateOnly = args.has("--next-action-env-template");
const nextActionEnvCheckOnly = args.has("--next-action-env-check");
const nextActionEnvReceiptOnly = args.has("--next-action-env-receipt");
const nextActionEnvReceiptMarkdownOnly = args.has("--next-action-env-receipt-markdown");
const nextActionEnvReceiptContractOnly = args.has("--next-action-env-receipt-contract");
const nextActionVerificationPlanOnly = args.has("--next-action-verification-plan");
const nextActionVerificationPlanMarkdownOnly = args.has("--next-action-verification-plan-markdown");
const releaseEnvPlanOnly = args.has("--release-env-plan");
const releaseEnvPlanMarkdownOnly = args.has("--release-env-plan-markdown");
const releaseEnvOwnerMatrixOnly = args.has("--release-env-owner-matrix");
const releaseEnvOwnerMatrixMarkdownOnly = args.has("--release-env-owner-matrix-markdown");
const releaseEnvNextOwnerTemplateOnly = args.has("--release-env-next-owner-template");
const releaseEnvMergePlanOnly = args.has("--release-env-merge-plan");
const releaseEnvMergePlanMarkdownOnly = args.has("--release-env-merge-plan-markdown");
const releaseEnvSubmissionPlanOnly = args.has("--release-env-submission-plan");
const releaseEnvSubmissionPlanMarkdownOnly = args.has("--release-env-submission-plan-markdown");
const dockerImagePlanOnly = args.has("--docker-image-plan");
const dockerImagePlanMarkdownOnly = args.has("--docker-image-plan-markdown");
const dockerImageSubmissionPlanOnly = args.has("--docker-image-submission-plan");
const dockerImageSubmissionPlanMarkdownOnly = args.has("--docker-image-submission-plan-markdown");
const runtimeBusinessPlanOnly = args.has("--runtime-business-plan");
const runtimeBusinessPlanMarkdownOnly = args.has("--runtime-business-plan-markdown");
const runtimeSmokePlanOnly = args.has("--runtime-smoke-plan");
const runtimeSmokePlanMarkdownOnly = args.has("--runtime-smoke-plan-markdown");
const runtimeBusinessSubmissionPlanOnly = args.has("--runtime-business-submission-plan");
const runtimeBusinessSubmissionPlanMarkdownOnly = args.has("--runtime-business-submission-plan-markdown");
const dataSafetyPlanOnly = args.has("--data-safety-plan");
const dataSafetyPlanMarkdownOnly = args.has("--data-safety-plan-markdown");
const dataSafetyOwnerPlanOnly = args.has("--data-safety-owner-plan");
const dataSafetyOwnerPlanMarkdownOnly = args.has("--data-safety-owner-plan-markdown");
const dataSafetySubmissionPlanOnly = args.has("--data-safety-submission-plan");
const dataSafetySubmissionPlanMarkdownOnly = args.has("--data-safety-submission-plan-markdown");
const cutoverRehearsalPlanOnly = args.has("--cutover-rehearsal-plan");
const cutoverRehearsalPlanMarkdownOnly = args.has("--cutover-rehearsal-plan-markdown");
const blockingInputsOnly = args.has("--blocking-inputs");
const blockingInputsMarkdownOnly = args.has("--blocking-inputs-markdown");
const blockingInputsEnvTemplateOnly = args.has("--blocking-inputs-env-template");
const releaseEvidenceDispatchPlanOnly = args.has("--release-evidence-dispatch-plan");
const releaseEvidenceDispatchPlanMarkdownOnly = args.has("--release-evidence-dispatch-plan-markdown");
const releaseEvidenceDispatchInputsOnly = args.has("--release-evidence-dispatch-inputs");
const releaseEvidenceDispatchCommandOnly = args.has("--release-evidence-dispatch-command");
const releaseEvidenceDispatchInputsContractOnly = args.has("--release-evidence-dispatch-inputs-contract");
const executionStatusOnly = args.has("--execution-status");
const executionStatusMarkdownOnly = args.has("--execution-status-markdown");
const handoffSummaryMarkdownOnly = args.has("--handoff-summary-markdown");
const finalReviewOnly = args.has("--final-review");
const finalReviewMarkdownOnly = args.has("--final-review-markdown");
const finalReviewEnforce = args.has("--final-review-enforce");
const releaseOwnerCloseoutOnly = args.has("--release-owner-closeout");
const releaseOwnerCloseoutMarkdownOnly = args.has("--release-owner-closeout-markdown");
const productionCloseoutStatusOnly = args.has("--production-closeout-status");
const productionCloseoutStatusMarkdownOnly = args.has("--production-closeout-status-markdown");
const productionUnblockPlanOnly = args.has("--production-unblock-plan");
const productionUnblockPlanMarkdownOnly = args.has("--production-unblock-plan-markdown");
const productionEvidenceReadinessOnly = args.has("--production-evidence-readiness");
const productionEvidenceReadinessMarkdownOnly = args.has("--production-evidence-readiness-markdown");
const productionEvidenceReadinessEnforce = args.has("--production-evidence-readiness-enforce");
const productionCutoverAuditOnly = args.has("--production-cutover-audit");
const productionCutoverAuditMarkdownOnly = args.has("--production-cutover-audit-markdown");
const operatorProgressOnly = args.has("--operator-progress");
const operatorProgressMarkdownOnly = args.has("--operator-progress-markdown");
const releaseOwnerDailyBriefOnly = args.has("--release-owner-daily-brief");
const releaseOwnerDailyBriefMarkdownOnly = args.has("--release-owner-daily-brief-markdown");
const rollupOnly = args.has("--rollup");
const rollupMarkdownOnly = args.has("--rollup-markdown");
const rollupEnforce = args.has("--rollup-enforce");
const evidenceEnvTemplateOnly = args.has("--evidence-env-template");
const handoffBundle = args.has("--handoff-bundle");
const handoffBundleVerify = args.has("--handoff-bundle-verify");
const ownerFilter = rawArgs.find((arg) => arg.startsWith("--owner="))?.slice("--owner=".length) || "";
const nextActionEnvFile = rawArgs.find((arg) => arg.startsWith("--next-action-env-file="))?.slice("--next-action-env-file=".length)
  || process.env.DDD_NEXT_ACTION_ENV_FILE
  || "";
const nextActionEnvReceiptFile = rawArgs.find((arg) => arg.startsWith("--next-action-env-receipt-file="))?.slice("--next-action-env-receipt-file=".length)
  || process.env.DDD_NEXT_ACTION_ENV_RECEIPT_FILE
  || "";
const nextActionEnvReceiptOutput = rawArgs.find((arg) => arg.startsWith("--next-action-env-receipt-output="))?.slice("--next-action-env-receipt-output=".length)
  || process.env.DDD_NEXT_ACTION_ENV_RECEIPT_OUTPUT
  || "";
const laneCompletionReceiptFile = rawArgs.find((arg) => arg.startsWith("--lane-completion-receipt-file="))?.slice("--lane-completion-receipt-file=".length)
  || process.env.DDD_LANE_COMPLETION_RECEIPT_FILE
  || "";
const laneCompletionReceiptOutput = rawArgs.find((arg) => arg.startsWith("--lane-completion-receipt-output="))?.slice("--lane-completion-receipt-output=".length)
  || process.env.DDD_LANE_COMPLETION_RECEIPT_OUTPUT
  || "";
const releaseEvidenceDispatchInputsFile = rawArgs.find((arg) => arg.startsWith("--release-evidence-dispatch-inputs-file="))?.slice("--release-evidence-dispatch-inputs-file=".length)
  || process.env.DDD_RELEASE_EVIDENCE_DISPATCH_INPUTS_FILE
  || "";
const outputBase = process.env.DDD_STAGING_CHECKLIST_OUTPUT
  ? path.resolve(repoRoot, process.env.DDD_STAGING_CHECKLIST_OUTPUT)
  : path.join(releaseDir, "staging-execution-checklist");
const ownerPacketDir = process.env.DDD_STAGING_OWNER_PACKET_DIR
  ? path.resolve(repoRoot, process.env.DDD_STAGING_OWNER_PACKET_DIR)
  : `${outputBase}-owner-packets`;
const handoffBundleDir = process.env.DDD_STAGING_HANDOFF_BUNDLE_DIR
  ? path.resolve(repoRoot, process.env.DDD_STAGING_HANDOFF_BUNDLE_DIR)
  : path.join(releaseDir, "staging-handoff-bundle");
const laneCompletionReceiptAutofillCommand = "node scripts/ddd-lane-completion-receipt-autofill.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>";

function printHelp() {
  console.log(`DDD staging execution checklist

Usage:
  node scripts/ddd-staging-execution-checklist.mjs [options]

Options:
  --json                 Write only JSON output.
  --markdown             Write only Markdown output.
  --owner-packets        Generate per-owner staging handoff packets.
  --owner=<owner>        Filter owner packets or blocking-input outputs to one owner.
  --list-owners          Print available owner filters and exit without writing files.
  --summary              Print a short staging dispatch summary and exit without writing files.
  --dispatch-check       Check staging dispatch inputs and exit without writing files.
  --rollup               Print a compact readiness rollup from dispatch checks and exit without writing files.
  --rollup-markdown      Print the readiness rollup as a Markdown table and exit without writing files.
  --rollup-enforce       Print the readiness rollup as JSON and exit non-zero unless all rollup gates PASS.
  --commands             Print the recommended staging command sequence and exit without writing files.
  --evidence-env-template Print a focused staging evidence env template and exit without writing files.
  --evidence-gaps        Print blocked staging evidence gaps as JSON and exit without writing files.
  --evidence-runbook     Print staging evidence collection runbook as JSON and exit without writing files.
  --evidence-runbook-markdown Print staging evidence collection runbook as Markdown and exit without writing files.
  --evidence-acceptance  Print staging evidence acceptance status as JSON and exit without writing files.
  --evidence-acceptance-markdown Print staging evidence acceptance status as Markdown and exit without writing files.
  --evidence-artifact-gap-report Print missing evidence artifact reverse index as JSON and exit without writing files.
  --evidence-artifact-gap-report-markdown Print missing evidence artifact reverse index as Markdown and exit without writing files.
  --explain-artifact-plan Print focused EXPLAIN artifact collection plan as JSON and exit without writing files.
  --explain-artifact-plan-markdown Print focused EXPLAIN artifact collection plan as Markdown and exit without writing files.
  --closure-plan         Print owner-sequenced staging closure plan with ETA bands as JSON and exit without writing files.
  --closure-plan-markdown Print owner-sequenced staging closure plan with ETA bands as Markdown and exit without writing files.
  --next-action-queue   Print the immediate staging owner action queue as JSON and exit without writing files.
  --next-action-queue-markdown Print the immediate staging owner action queue as Markdown and exit without writing files.
  --owner-lane-matrix   Print owner-to-lane dispatch matrix as JSON and exit without writing files.
  --owner-lane-matrix-markdown Print owner-to-lane dispatch matrix as Markdown and exit without writing files.
  --lane-completion-receipt-template Print redacted lane completion receipt template as JSON and exit without writing files.
  --lane-completion-receipt-template-markdown Print redacted lane completion receipt template as Markdown and exit without writing files.
  --lane-completion-receipt-init Write a redacted lane completion receipt template to --lane-completion-receipt-output and validate it.
  --lane-completion-receipt-contract Validate a redacted lane completion receipt JSON file and exit non-zero on issues.
  --lane-completion-receipt-coverage Validate that a lane completion receipt covers every release lane.
  --lane-completion-receipt-coverage-markdown Print lane completion receipt coverage as Markdown.
  --lane-completion-receipt-base64 Print workflow_dispatch-safe base64 for a PASS receipt with full lane coverage.
  --evidence-closure-board Print owner lane evidence closure board as JSON and exit without writing files.
  --evidence-closure-board-markdown Print owner lane evidence closure board as Markdown and exit without writing files.
  --evidence-closure-board-csv Print owner lane evidence closure board as CSV and exit without writing files.
  --lane-receipt-fragments Print all owner lane receipt fragments as JSON and exit without writing files.
  --lane-receipt-fragments-markdown Print all owner lane receipt fragments as Markdown and exit without writing files.
  --lane-receipt-draft Print a redacted lane completion receipt draft from current fragments as JSON and exit without writing files.
  --lane-receipt-draft-markdown Print the lane completion receipt draft summary as Markdown and exit without writing files.
  --owner-evidence-intake Print owner-scoped evidence intake checklist as JSON and exit without writing files.
  --owner-evidence-intake-markdown Print owner-scoped evidence intake checklist as Markdown and exit without writing files.
  --lane-completion-submission-plan Print lane completion receipt submission plan as JSON and exit without writing files.
  --lane-completion-submission-plan-markdown Print lane completion receipt submission plan as Markdown and exit without writing files.
  --lane-completion-submission-check Print lane receipt submission readiness check as JSON and exit without writing files.
  --lane-completion-submission-check-markdown Print lane receipt submission readiness check as Markdown and exit without writing files.
  --lane-completion-receipt-file=<file> Receipt file path used by --lane-completion-receipt-contract; defaults to DDD_LANE_COMPLETION_RECEIPT_FILE.
  --lane-completion-receipt-output=<file> Output file used by --lane-completion-receipt-init; refuses to overwrite existing files.
  --next-action-env-template Print a focused env skeleton for the immediate staging owner queue and exit without writing files.
  --next-action-env-check Print focused next-action env file validation as JSON and exit without writing files.
  --next-action-env-receipt Print a redacted receipt for a populated next-action env file as JSON.
  --next-action-env-receipt-markdown Print a redacted receipt for a populated next-action env file as Markdown.
  --next-action-env-receipt-contract Validate a redacted next-action env receipt JSON file and exit non-zero on contract issues.
  --next-action-env-receipt-output=<file> Write JSON receipt to a file, validate it, and print the contract result.
  --next-action-env-file=<file> Env file path used by --next-action-env-check; defaults to DDD_NEXT_ACTION_ENV_FILE.
  --next-action-env-receipt-file=<file> Receipt file path used by --next-action-env-receipt-contract; defaults to DDD_NEXT_ACTION_ENV_RECEIPT_FILE.
  --next-action-verification-plan Print post-env-check staging verification sequence as JSON and exit without writing files.
  --next-action-verification-plan-markdown Print post-env-check staging verification sequence as Markdown and exit without writing files.
  --release-env-plan     Print P0 release-env initialization and validation plan as JSON and exit without writing files.
  --release-env-plan-markdown Print P0 release-env initialization and validation plan as Markdown and exit without writing files.
  --release-env-owner-matrix Print owner-scoped release env input matrix as JSON and exit without writing files.
  --release-env-owner-matrix-markdown Print owner-scoped release env input matrix as Markdown and exit without writing files.
  --release-env-next-owner-template Print the current top release-env owner template and exit without writing files.
  --release-env-merge-plan Print release env owner merge and validation plan as JSON and exit without writing files.
  --release-env-merge-plan-markdown Print release env owner merge and validation plan as Markdown and exit without writing files.
  --release-env-submission-plan Print release env owner submission and receipt plan as JSON and exit without writing files.
  --release-env-submission-plan-markdown Print release env owner submission and receipt plan as Markdown and exit without writing files.
  --docker-image-plan    Print Docker image evidence plan as JSON and exit without writing files.
  --docker-image-plan-markdown Print Docker image evidence plan as Markdown and exit without writing files.
  --docker-image-submission-plan Print Docker image evidence submission plan as JSON and exit without writing files.
  --docker-image-submission-plan-markdown Print Docker image evidence submission plan as Markdown and exit without writing files.
  --runtime-business-plan Print P1 runtime/business staging evidence plan as JSON and exit without writing files.
  --runtime-business-plan-markdown Print P1 runtime/business staging evidence plan as Markdown and exit without writing files.
  --runtime-smoke-plan Print owner-phased P1 runtime smoke execution plan as JSON and exit without writing files.
  --runtime-smoke-plan-markdown Print owner-phased P1 runtime smoke execution plan as Markdown and exit without writing files.
  --runtime-business-submission-plan Print P1 runtime/business owner submission plan as JSON and exit without writing files.
  --runtime-business-submission-plan-markdown Print P1 runtime/business owner submission plan as Markdown and exit without writing files.
  --data-safety-plan     Print rollback, migration, and EXPLAIN staging evidence plan as JSON and exit without writing files.
  --data-safety-plan-markdown Print rollback, migration, and EXPLAIN staging evidence plan as Markdown and exit without writing files.
  --data-safety-owner-plan Print owner-phased rollback, migration, and EXPLAIN execution plan as JSON and exit without writing files.
  --data-safety-owner-plan-markdown Print owner-phased rollback, migration, and EXPLAIN execution plan as Markdown and exit without writing files.
  --data-safety-submission-plan Print owner submission route for rollback, migration, and EXPLAIN evidence as JSON and exit without writing files.
  --data-safety-submission-plan-markdown Print owner submission route for rollback, migration, and EXPLAIN evidence as Markdown and exit without writing files.
  --cutover-rehearsal-plan Print ordered staging cutover rehearsal plan as JSON and exit without writing files.
  --cutover-rehearsal-plan-markdown Print ordered staging cutover rehearsal plan as Markdown and exit without writing files.
  --blocking-inputs      Print blocked input keys grouped across staging gates as JSON and exit without writing files.
  --blocking-inputs-markdown Print blocked input keys grouped across staging gates as Markdown and exit without writing files.
  --blocking-inputs-env-template Print a focused .env template from current blocking inputs and exit without writing files.
  --release-evidence-dispatch-plan Print formal release evidence workflow dispatch inputs as JSON and exit without writing files.
  --release-evidence-dispatch-plan-markdown Print formal release evidence workflow dispatch inputs as Markdown and exit without writing files.
  --release-evidence-dispatch-inputs Print workflow_dispatch JSON input payload template and exit without writing files.
  --release-evidence-dispatch-command Print gh workflow run command template for release evidence and exit without writing files.
  --release-evidence-dispatch-inputs-contract Validate a workflow_dispatch JSON input payload and exit non-zero on issues.
  --release-evidence-dispatch-inputs-file=<file> Input file for --release-evidence-dispatch-inputs-contract; defaults to DDD_RELEASE_EVIDENCE_DISPATCH_INPUTS_FILE.
  --execution-status     Print one-page staging execution status as JSON and exit without writing files.
  --execution-status-markdown Print one-page staging execution status as Markdown and exit without writing files.
  --handoff-summary-markdown Print CI-ready staging handoff summary as Markdown and exit without writing files.
  --final-review         Print release-owner final review as JSON and exit without writing files.
  --final-review-markdown Print release-owner final review as Markdown and exit without writing files.
  --final-review-enforce Print release-owner final review as JSON and exit non-zero unless cutoverReady is true.
  --release-owner-closeout Print single-page release-owner closeout as JSON and exit without writing files.
  --release-owner-closeout-markdown Print single-page release-owner closeout as Markdown and exit without writing files.
  --production-closeout-status Print production closeout status, ETA band, and next owner action as JSON.
  --production-closeout-status-markdown Print production closeout status, ETA band, and next owner action as Markdown.
  --production-unblock-plan Print focused production unblock plan as JSON.
  --production-unblock-plan-markdown Print focused production unblock plan as Markdown.
  --production-evidence-readiness Print aggregated production evidence readiness as JSON.
  --production-evidence-readiness-markdown Print aggregated production evidence readiness as Markdown.
  --production-evidence-readiness-enforce Print aggregated production evidence readiness as JSON and exit non-zero unless every evidence gate is PASS.
  --production-cutover-audit Print final production cutover audit matrix as JSON.
  --production-cutover-audit-markdown Print final production cutover audit matrix as Markdown.
  --lane-completion-receipt-file=<file> Also used by final review to require a passing owner lane completion receipt contract.
  --operator-progress    Print operator progress across env, bundle, verification, and final review as JSON.
  --operator-progress-markdown Print operator progress across env, bundle, verification, and final review as Markdown.
  --release-owner-daily-brief Print release-owner daily action brief as JSON.
  --release-owner-daily-brief-markdown Print release-owner daily action brief as Markdown.
  --lane-completion-receipt-file=<file> Also used by --operator-progress to include lane completion receipt contract status.
  --handoff-bundle       Write rollup, env template, and command handoff files.
  --handoff-bundle-verify Verify an existing handoff bundle manifest and exit without writing files.
  --help, -h             Show this help.

Environment:
  DDD_STAGING_CHECKLIST_OUTPUT   Override checklist output base path, without .json/.md.
  DDD_STAGING_OWNER_PACKET_DIR   Override owner packet output directory.
  DDD_STAGING_HANDOFF_BUNDLE_DIR Override handoff bundle output directory.
  DDD_NEXT_ACTION_ENV_RECEIPT_FILE Override redacted next-action env receipt contract input.
  DDD_NEXT_ACTION_ENV_RECEIPT_OUTPUT Override redacted next-action env receipt output path.
  DDD_LANE_COMPLETION_RECEIPT_FILE Override redacted lane completion receipt contract input.

Examples:
  node scripts/ddd-staging-execution-checklist.mjs
  node scripts/ddd-staging-execution-checklist.mjs --owner-packets
  node scripts/ddd-staging-execution-checklist.mjs --list-owners
  node scripts/ddd-staging-execution-checklist.mjs --summary
  node scripts/ddd-staging-execution-checklist.mjs --dispatch-check
  node scripts/ddd-staging-execution-checklist.mjs --rollup
  node scripts/ddd-staging-execution-checklist.mjs --rollup-markdown
  node scripts/ddd-staging-execution-checklist.mjs --rollup-enforce
  node scripts/ddd-staging-execution-checklist.mjs --commands
  node scripts/ddd-staging-execution-checklist.mjs --evidence-env-template
  node scripts/ddd-staging-execution-checklist.mjs --evidence-gaps
  node scripts/ddd-staging-execution-checklist.mjs --evidence-runbook
  node scripts/ddd-staging-execution-checklist.mjs --evidence-runbook-markdown
  node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance
  node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance-markdown
  node scripts/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report
  node scripts/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report-markdown
  node scripts/ddd-staging-execution-checklist.mjs --explain-artifact-plan
  node scripts/ddd-staging-execution-checklist.mjs --explain-artifact-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --closure-plan
  node scripts/ddd-staging-execution-checklist.mjs --closure-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --next-action-queue
  node scripts/ddd-staging-execution-checklist.mjs --next-action-queue-markdown
  node scripts/ddd-staging-execution-checklist.mjs --owner-lane-matrix
  node scripts/ddd-staging-execution-checklist.mjs --owner-lane-matrix-markdown
  node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template
  node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template-markdown
  node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>
  node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>
  node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage --lane-completion-receipt-file=<receipt-file>
  node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>
  node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>
  node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-plan
  node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>
  node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check-markdown --lane-completion-receipt-file=<receipt-file>
  node scripts/ddd-staging-execution-checklist.mjs --next-action-env-template
  node scripts/ddd-staging-execution-checklist.mjs --next-action-env-check --next-action-env-file=<env-file>
  node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file>
  node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file> --next-action-env-receipt-output=<receipt-file>
  node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-markdown --next-action-env-file=<env-file>
  node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>
  node scripts/ddd-staging-execution-checklist.mjs --next-action-verification-plan
  node scripts/ddd-staging-execution-checklist.mjs --next-action-verification-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --release-env-plan
  node scripts/ddd-staging-execution-checklist.mjs --release-env-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --release-env-owner-matrix
  node scripts/ddd-staging-execution-checklist.mjs --release-env-owner-matrix-markdown
  node scripts/ddd-staging-execution-checklist.mjs --release-env-next-owner-template
  node scripts/ddd-staging-execution-checklist.mjs --release-env-merge-plan
  node scripts/ddd-staging-execution-checklist.mjs --release-env-merge-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --release-env-submission-plan
  node scripts/ddd-staging-execution-checklist.mjs --release-env-submission-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --docker-image-plan
  node scripts/ddd-staging-execution-checklist.mjs --docker-image-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan
  node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --runtime-business-plan
  node scripts/ddd-staging-execution-checklist.mjs --runtime-business-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --runtime-smoke-plan
  node scripts/ddd-staging-execution-checklist.mjs --runtime-smoke-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan
  node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --data-safety-plan
  node scripts/ddd-staging-execution-checklist.mjs --data-safety-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --data-safety-owner-plan
  node scripts/ddd-staging-execution-checklist.mjs --data-safety-owner-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan
  node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --cutover-rehearsal-plan
  node scripts/ddd-staging-execution-checklist.mjs --cutover-rehearsal-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs
  node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-markdown
  node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template
  node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=release-infra
  node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-plan
  node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-inputs
  node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-command
  node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-inputs-contract --release-evidence-dispatch-inputs-file=<inputs-file>
  node scripts/ddd-staging-execution-checklist.mjs --execution-status
  node scripts/ddd-staging-execution-checklist.mjs --execution-status-markdown
  node scripts/ddd-staging-execution-checklist.mjs --handoff-summary-markdown
  node scripts/ddd-staging-execution-checklist.mjs --final-review
  node scripts/ddd-staging-execution-checklist.mjs --final-review-markdown
  node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce
  node scripts/ddd-staging-execution-checklist.mjs --release-owner-closeout
  node scripts/ddd-staging-execution-checklist.mjs --release-owner-closeout-markdown
  node scripts/ddd-staging-execution-checklist.mjs --production-closeout-status
  node scripts/ddd-staging-execution-checklist.mjs --production-closeout-status-markdown
  node scripts/ddd-staging-execution-checklist.mjs --production-unblock-plan
  node scripts/ddd-staging-execution-checklist.mjs --production-unblock-plan-markdown
  node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness
  node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-markdown
  node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-enforce
  node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit
  node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit-markdown
  node scripts/ddd-staging-execution-checklist.mjs --operator-progress
  node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown
  node scripts/ddd-staging-execution-checklist.mjs --release-owner-daily-brief
  node scripts/ddd-staging-execution-checklist.mjs --release-owner-daily-brief-markdown
  node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle
  node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle-verify
  node scripts/ddd-staging-execution-checklist.mjs --owner-packets --owner=release-infra
`);
}

if (help) {
  printHelp();
  process.exit(0);
}

function readJson(file, fallback = {}) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    return fallback;
  }
}

const releaseEnvFillGroupDefinitions = {
  runtime: [
    "LUMIRA_BASE_URL",
    "BASE_URL",
    "PLAYWRIGHT_BASE_URL",
    "FRONTEND_BASE_URL",
    "AI_SERVICE_BASE_URL",
    "AUTH_SERVICE_BASE_URL",
    "PAYMENT_SERVICE_BASE_URL",
    "FILE_SERVICE_BASE_URL",
    "JOB_EXECUTOR_BASE_URL",
    "MESSAGE_SERVICE_BASE_URL",
    "SYSTEM_SERVICE_BASE_URL",
  ],
  database: ["DB_URL", "DB_USERNAME", "DB_PASSWORD", "MYSQL_HOST", "MYSQL_PORT", "MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PASSWORD"],
  security: ["JWT_SECRET", "FIELD_SECRET", "DDD_AUTH_PASSWORD", "DDD_AUTH_USERNAME"],
  evidence: [
    "DDD_DEPLOYMENT_EVIDENCE",
    "DDD_FRONTEND_DEPLOYMENT_EVIDENCE",
    "DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE",
    "DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE",
    "DDD_MIGRATION_COMPLETED_AT",
    "DDD_MIGRATION_FRESH_DB_EVIDENCE",
    "DDD_MIGRATION_UPGRADE_DB_EVIDENCE",
    "DDD_EXPLAIN_DATABASE",
  ],
  ai: [
    "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY",
    "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL",
    "LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN",
    "LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL",
    "LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL",
    "LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL",
  ],
  jobs: ["SAAS_JOB_INTERNAL_TOKEN", "XXL_JOB_ACCESS_TOKEN", "XXL_JOB_ADMIN_ADDRESSES"],
};

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

function relative(file) {
  return path.relative(repoRoot, file).replaceAll("\\", "/");
}

function commandList(commands = []) {
  return commands.filter(Boolean).map((command) => `  - \`${command}\``).join("\n");
}

function slug(value) {
  return String(value || "unknown")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "") || "unknown";
}

function sha256Text(value) {
  return createHash("sha256").update(value).digest("hex");
}

const finalPacketPath = path.join(releaseDir, "release-final-go-no-go.json");
const evidenceGatePath = path.join(releaseDir, "release-evidence-gate.json");
const readinessPath = path.join(releaseDir, "readiness-summary.json");
const commandCatalogPath = path.join(releaseDir, "release-command-catalog.md");
const missingEnvTemplatePath = path.join(releaseDir, "release-env-missing.template.env");
const envInitScriptPath = path.join(releaseDir, "release-final-owner-queue-env-init.sh");
const envInitWrapperPath = path.join(repoRoot, "scripts", "ddd-release-env-init.mjs");
const envInitCommand = "node scripts/ddd-release-env-init.mjs";
const envInitCheckCommand = "node scripts/ddd-release-env-init.mjs --check";
const dockerEvidenceCheckCommand = "node scripts/ddd-docker-build-evidence.mjs --check";
const runtimeStagingCheckCommand = "node scripts/ddd-staging-runtime-check.mjs";
const dataSafetyCheckCommand = "node scripts/ddd-staging-data-safety-check.mjs";

const finalPacket = readJson(finalPacketPath);
const evidenceGate = readJson(evidenceGatePath);
const readiness = readJson(readinessPath);
const ownerHandoff = readJson(path.join(releaseDir, "release-env-owner-handoff-redacted.json"));
const generatedAt = new Date().toISOString();

const blockedCutoverItems = finalPacket.blockedCutoverItems || [];
const closureWaves = finalPacket.closureWaves || [];
const releaseEnv = finalPacket.safetySignals?.releaseEnvFile || {};
const orchestratorPreflight = finalPacket.ciSummary?.orchestratorPreflight || {};
const releaseEnvReadiness = finalPacket.ciSummary?.releaseEnvReadiness || {};
const ownerHandoffByOwner = new Map((ownerHandoff.owners || []).map((owner) => [owner.owner, owner]));
const ownerBlockerSummary = (releaseEnvReadiness.ownerBlockerSummary || []).map((owner) => {
  const handoff = ownerHandoffByOwner.get(owner.owner) || {};
  return {
    ...owner,
    keys: handoff.keys || [],
    ownerInputReasons: handoff.ownerInputReasons || [],
    nextCommand: handoff.nextCommand || null,
  };
});
const ownerFilterRequiresValidation = ownerPackets
  || blockingInputsOnly
  || blockingInputsMarkdownOnly
  || blockingInputsEnvTemplateOnly
  || ownerEvidenceIntakeOnly
  || ownerEvidenceIntakeMarkdownOnly;
const selectedOwnerPackets = ownerFilter
  ? ownerBlockerSummary.filter((owner) => owner.owner === ownerFilter)
  : ownerBlockerSummary;
if (ownerFilterRequiresValidation && ownerFilter && selectedOwnerPackets.length === 0) {
  console.error(`[ddd-staging-execution-checklist] unknown owner filter: ${ownerFilter}`);
  console.error(`[ddd-staging-execution-checklist] available owners: ${ownerBlockerSummary.map((owner) => owner.owner).join(", ") || "none"}`);
  process.exit(2);
}

if (listOwners) {
  for (const owner of ownerBlockerSummary) {
    console.log(`${owner.owner}\tblockers=${owner.blockers}\tsecretKeys=${owner.secretKeys}`);
  }
  process.exit(0);
}

const tracks = [
  {
    id: "p0-release-env",
    title: "P0 release env and config",
    status: releaseEnv.ready === true ? "ready" : "blocked",
    owner: "release-infra",
    reason: releaseEnv.ready === true
      ? "release env file is cutover-safe"
      : `release env file is not cutover-safe; blockers=${releaseEnv.blockingRequiresOwnerInput ?? "unknown"}`,
    envKeys: unique([
      ...(finalPacket.ciSummary?.firstOwnerAction?.envKeys || []),
      ...(orchestratorPreflight.blockerChecks || []).flatMap((check) => check.envKeys || []),
    ]),
    commands: [
      "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
      "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs",
      "node scripts/ddd-release-readiness-summary.mjs",
    ],
    artifacts: [
      "artifacts/ddd/release/release-env-lint.json",
      "artifacts/ddd/config/release-config-evidence.json",
      "artifacts/ddd/release/readiness-summary.json",
    ],
    setupCommands: [
      envInitCheckCommand,
      envInitCommand,
    ],
  },
  {
    id: "p0-images",
    title: "P0 deployable images",
    status: blockedCutoverItems.some((item) => item.id === "deployable-images") ? "blocked" : "ready",
    owner: "release-infra",
    reason: "backend and frontend images must be built or inspected from CI-produced release images",
    envKeys: ["DDD_DOCKER_BUILD_STRICT", "DDD_DOCKER_COMMAND", "DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE", "DDD_DOCKER_EXISTING_FRONTEND_IMAGE"],
    commands: [
      "DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs",
      "node scripts/ddd-release-readiness-summary.mjs",
    ],
    setupCommands: [
      dockerEvidenceCheckCommand,
    ],
    artifacts: ["artifacts/ddd/build/docker-image-evidence.json"],
  },
  {
    id: "p1-runtime-business",
    title: "P1 runtime and business acceptance",
    status: blockedCutoverItems.some((item) => item.id === "runtime-business-acceptance") ? "blocked" : "ready",
    owner: "release-infra, frontend, ai, file-owner, job-owner, payment-owner",
    reason: "local-only runtime evidence must be replaced by HTTPS staging evidence",
    envKeys: ["LUMIRA_BASE_URL", "PLAYWRIGHT_BASE_URL", "DDD_FRONTEND_EXPECT_DEPLOYED", "DDD_AI_EXPECT_PROVIDER_REMOTE", "DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE"],
    commands: [
      "node scripts/ddd-runtime-readiness-smoke.mjs",
      "DDD_AUTH_PERF_STRICT=true node scripts/ddd-authenticated-performance-smoke.mjs",
      "node scripts/ddd-ai-runtime-drill.mjs",
      "node scripts/ddd-frontend-playwright-smoke.mjs",
      "node scripts/ddd-frontend-smoke-evidence.mjs",
      "node scripts/ddd-file-processing-e2e-smoke.mjs",
      "node scripts/ddd-job-e2e-smoke.mjs",
      "node scripts/ddd-payment-webhook-e2e-smoke.mjs",
    ],
    setupCommands: [
      runtimeStagingCheckCommand,
    ],
    artifacts: [
      "artifacts/ddd/readiness/summary.json",
      "artifacts/ddd/performance/authenticated-runtime-actual.json",
      "artifacts/ddd/ai/ai-runtime-drill.json",
      "artifacts/ddd/frontend/frontend-smoke.json",
      "artifacts/ddd/file/file-processing-e2e.json",
      "artifacts/ddd/jobs/job-e2e-smoke.json",
      "artifacts/ddd/payment/payment-webhook-e2e.json",
    ],
  },
  {
    id: "p1-rollback",
    title: "P1 rollback safety",
    status: blockedCutoverItems.some((item) => item.id === "rollback-safety") ? "blocked" : "ready",
    owner: "bounded-context owners",
    reason: "every bounded context needs PASS rollback drill evidence or approved unexpired deferral",
    envKeys: ["DDD_ROLLBACK_DRILL_CHECK_ENV", "DDD_ROLLBACK_DRILL_STRICT", "DDD_ROLLBACK_DRILL_FILE"],
    commands: [
      "DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs",
      "node scripts/ddd-rollback-drill-evidence.mjs",
    ],
    setupCommands: [
      dataSafetyCheckCommand,
    ],
    artifacts: ["artifacts/ddd/rollback/rollback-drill.json"],
  },
  {
    id: "p2-database-performance",
    title: "P2 database migration and EXPLAIN",
    status: blockedCutoverItems.some((item) => item.id === "database-performance") ? "blocked" : "ready",
    owner: "database",
    reason: "fresh production-equivalent migration and hot-path EXPLAIN evidence are required",
    envKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_VALIDATED", "DDD_EXPLAIN_DATABASE", "DDD_EXPLAIN_ENVIRONMENT"],
    commands: [
      "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
      "DDD_MIGRATION_STRICT=true node scripts/ddd-migration-evidence.mjs",
      "node scripts/ddd-collect-explain.mjs",
      "DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs",
    ],
    setupCommands: [
      dataSafetyCheckCommand,
    ],
    artifacts: [
      "artifacts/ddd/migration/migration-evidence.json",
      "tmp/ddd-explain/*.json",
      "artifacts/ddd/release/explain-gate-report.json",
    ],
  },
  {
    id: "p3-final-strict",
    title: "P3 strict orchestrator and final gate",
    status: finalPacket.cutoverAllowed === true ? "ready" : "blocked",
    owner: "release-owner",
    reason: finalPacket.cutoverAllowed === true
      ? "final go/no-go allows cutover"
      : `final recommendation is ${finalPacket.finalRecommendation || finalPacket.recommendation || "unknown"}`,
    envKeys: ["DDD_RELEASE_EVIDENCE_STRICT", "DDD_FINAL_GO_NO_GO_ENFORCE"],
    commands: [
      "DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict",
      "DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs",
      "DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-gate.mjs",
      "node scripts/ddd-release-readiness-summary.mjs",
      "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    ],
    artifacts: [
      "artifacts/ddd/release/orchestrator-report.json",
      "artifacts/ddd/release/evidence-manifest.json",
      "artifacts/ddd/release/release-evidence-gate.json",
      "artifacts/ddd/release/release-final-go-no-go.json",
    ],
  },
];

const checklist = {
  generatedAt,
  status: finalPacket.cutoverAllowed === true ? "GO_CANDIDATE" : "STAGING_REQUIRED",
  finalRecommendation: finalPacket.finalRecommendation || finalPacket.recommendation || "UNKNOWN",
  cutoverAllowed: finalPacket.cutoverAllowed === true,
  gate: {
    strict: evidenceGate.strict === true,
    blockers: evidenceGate.summary?.blockers ?? evidenceGate.gate?.blockers ?? finalPacket.gate?.blockers ?? null,
    warnings: evidenceGate.summary?.warnings ?? evidenceGate.gate?.warnings ?? finalPacket.gate?.warnings ?? null,
  },
  releaseEnv: {
    ready: releaseEnv.ready === true,
    status: releaseEnv.status || "UNKNOWN",
    ownerInputRequired: releaseEnv.blockingRequiresOwnerInput ?? releaseEnvReadiness.blockers ?? null,
    ownerCount: ownerBlockerSummary.length,
    ownerHandoff: releaseEnvReadiness.ownerHandoff || null,
    ownerBlockerSummary,
  },
  orchestratorPreflight: {
    status: orchestratorPreflight.status || "UNKNOWN",
    blockers: orchestratorPreflight.blockers ?? null,
    blockerChecks: orchestratorPreflight.blockerChecks || [],
  },
  blockedCutoverItems: blockedCutoverItems.map((item) => ({
    id: item.id,
    title: item.title,
    pendingItems: item.pendingItems,
    readyBatchIds: item.readyBatchIds || [],
    blockedBatchIds: item.blockedBatchIds || [],
  })),
  immediateWaves: closureWaves
    .filter((wave) => wave.priority === "P0")
    .map((wave) => ({
      wave: wave.wave,
      owner: wave.owner,
      batchId: wave.batchId,
      receiptStatus: wave.receiptStatus,
      commands: wave.commands || [],
    })),
  tracks,
  sourceArtifacts: {
    finalPacket: relative(finalPacketPath),
    evidenceGate: relative(evidenceGatePath),
    readinessSummary: relative(readinessPath),
    commandCatalog: fs.existsSync(commandCatalogPath) ? relative(commandCatalogPath) : null,
    missingEnvTemplate: fs.existsSync(missingEnvTemplatePath) ? relative(missingEnvTemplatePath) : null,
    releaseEnvInit: fs.existsSync(envInitScriptPath) ? relative(envInitScriptPath) : null,
    releaseEnvInitWrapper: fs.existsSync(envInitWrapperPath) ? relative(envInitWrapperPath) : null,
  },
  readinessStatus: readiness.status || "UNKNOWN",
};

function renderSummary(report) {
  const blockedTracks = report.tracks.filter((track) => track.status !== "ready");
  const lines = [
    `status=${report.status}`,
    `finalRecommendation=${report.finalRecommendation}`,
    `cutoverAllowed=${report.cutoverAllowed}`,
    `gate=blockers:${report.gate.blockers ?? "unknown"} warnings:${report.gate.warnings ?? "unknown"} strict:${report.gate.strict}`,
    `releaseEnv=ready:${report.releaseEnv.ready} ownerInputRequired:${report.releaseEnv.ownerInputRequired ?? "unknown"} ownerCount:${report.releaseEnv.ownerCount ?? report.releaseEnv.ownerBlockerSummary.length}`,
    `blockedTracks=${blockedTracks.length}`,
    "",
    "Tracks:",
    ...blockedTracks.map((track) => `- ${track.id}\t${track.owner}\t${track.reason}`),
    "",
    "Owner handoff:",
    ...(report.releaseEnv.ownerBlockerSummary.length > 0
      ? report.releaseEnv.ownerBlockerSummary.map((owner) => `- ${owner.owner}\tblockers=${owner.blockers}\tsecretKeys=${owner.secretKeys}\tkeys=${owner.keys.length}`)
      : ["- none"]),
    "",
    "Next commands:",
    `- ${envInitCheckCommand}`,
    `- ${envInitCommand}`,
    "- node scripts/ddd-staging-execution-checklist.mjs --owner-packets",
    "- DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh",
  ];
  return `${lines.join("\n")}\n`;
}

function buildEvidenceGaps(report) {
  const blockedTracks = report.tracks.filter((track) => track.status !== "ready");
  const gaps = blockedTracks.map((track) => ({
    id: track.id,
    title: track.title,
    owner: track.owner,
    reason: track.reason,
    nextCommand: [...(track.setupCommands || []), ...(track.commands || [])][0] || null,
    commands: track.commands || [],
    setupCommands: track.setupCommands || [],
    artifacts: track.artifacts || [],
    envKeys: track.envKeys || [],
  }));
  return {
    status: report.status,
    finalRecommendation: report.finalRecommendation,
    cutoverAllowed: report.cutoverAllowed,
    willWriteFiles: false,
    gapCount: gaps.length,
    gaps,
  };
}

function runEvidenceGaps(report) {
  const result = buildEvidenceGaps(report);
  console.log(JSON.stringify(result, null, 2));
  process.exit(0);
}

function buildEvidenceRunbook(report) {
  const tracks = report.tracks.map((track, index) => ({
    order: index + 1,
    id: track.id,
    title: track.title,
    owner: track.owner,
    priority: track.priority || null,
    status: track.status,
    reason: track.reason,
    setupCommands: track.setupCommands || [],
    commands: track.commands || [],
    artifacts: track.artifacts || [],
    envKeys: track.envKeys || [],
    nextCommand: [...(track.setupCommands || []), ...(track.commands || [])][0] || null,
  }));
  return {
    status: report.status,
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: report.finalRecommendation,
    cutoverAllowed: report.cutoverAllowed,
    trackCount: tracks.length,
    blockedTrackCount: tracks.filter((track) => track.status !== "ready").length,
    tracks,
    nextCommand: "node scripts/ddd-staging-execution-checklist.mjs --execution-status",
  };
}

function renderEvidenceRunbookMarkdown(runbook) {
  const lines = [
    "# DDD Staging Evidence Runbook",
    "",
    `Status: ${runbook.status}`,
    `Final recommendation: ${runbook.finalRecommendation}`,
    `Cutover allowed: ${runbook.cutoverAllowed}`,
    `Blocked tracks: ${runbook.blockedTrackCount}/${runbook.trackCount}`,
    "",
  ];
  for (const track of runbook.tracks) {
    lines.push(
      `## ${track.order}. ${track.title}`,
      "",
      `- Track: \`${track.id}\``,
      `- Owner: ${track.owner}`,
      `- Status: ${track.status}`,
      `- Reason: ${track.reason}`,
      `- Next command: ${track.nextCommand ? `\`${track.nextCommand}\`` : "none"}`,
      "",
      "Setup commands:",
      ...(track.setupCommands.length > 0 ? track.setupCommands.map((command) => `- \`${command}\``) : ["- none"]),
      "",
      "Evidence commands:",
      ...(track.commands.length > 0 ? track.commands.map((command) => `- \`${command}\``) : ["- none"]),
      "",
      "Expected artifacts:",
      ...(track.artifacts.length > 0 ? track.artifacts.map((artifact) => `- \`${artifact}\``) : ["- none"]),
      "",
      "Env keys:",
      ...(track.envKeys.length > 0 ? track.envKeys.map((key) => `- \`${key}\``) : ["- none"]),
      "",
    );
  }
  lines.push(`Next: \`${runbook.nextCommand}\``, "");
  return lines.join("\n");
}

function runEvidenceRunbook({ markdown = false } = {}) {
  const runbook = buildEvidenceRunbook(checklist);
  if (markdown) {
    process.stdout.write(renderEvidenceRunbookMarkdown(runbook));
  } else {
    console.log(JSON.stringify(runbook, null, 2));
  }
  process.exit(0);
}

function verifyHandoffBundleResult() {
  const manifestPath = path.join(handoffBundleDir, "manifest.json");
  const issues = [];
  const manifest = readJson(manifestPath, null);
  if (!manifest) {
    issues.push(`missing or invalid manifest: ${relative(manifestPath)}`);
  }
  const files = Array.isArray(manifest?.files) ? manifest.files : [];
  if (manifest && !Array.isArray(manifest.files)) {
    issues.push("manifest files must be an array");
  }
  const manifestFileNames = new Set(files.map((entry) => String(entry.file || "")));
  const requiredBundleFiles = [
    "README.md",
    "rollup.json",
    "rollup.md",
    "handoff-summary.md",
    "execution-status.json",
    "execution-status.md",
    "final-review.json",
    "final-review.md",
    "release-owner-closeout.json",
    "release-owner-closeout.md",
    "production-closeout-status.json",
    "production-closeout-status.md",
    "production-unblock-quickstart.md",
    "production-unblock-plan.json",
    "production-unblock-plan.md",
    "production-evidence-readiness.json",
    "production-evidence-readiness.md",
    "production-cutover-audit.json",
    "production-cutover-audit.md",
    "operator-progress.json",
    "operator-progress.md",
    "closure-plan.json",
    "closure-plan.md",
    "next-action-queue.json",
    "next-action-queue.md",
    "owner-lane-matrix.json",
    "owner-lane-matrix.md",
    "lane-completion-receipt.template.json",
    "lane-completion-receipt.template.md",
    "lane-completion-receipt.coverage.json",
    "lane-completion-receipt.coverage.md",
    "evidence-closure-board.json",
    "evidence-closure-board.md",
    "evidence-closure-board.csv",
    "lane-receipt-fragments.json",
    "lane-receipt-fragments.md",
    "lane-receipt-draft.json",
    "lane-receipt-draft.md",
    "owner-evidence-intake.json",
    "owner-evidence-intake.md",
    "lane-completion-submission-plan.json",
    "lane-completion-submission-plan.md",
    "lane-completion-submission-check.json",
    "lane-completion-submission-check.md",
    "next-action.template.env",
    "next-action-env-receipt.sample.json",
    "next-action-env-receipt.sample.md",
    "next-action-verification-plan.json",
    "next-action-verification-plan.md",
    "release-env-plan.json",
    "release-env-plan.md",
    "release-env-owner-matrix.json",
    "release-env-owner-matrix.md",
    "release-env-next-owner.template.env",
    "release-env-merge-plan.json",
    "release-env-merge-plan.md",
    "release-env-submission-plan.json",
    "release-env-submission-plan.md",
    "release-env-fill-checklist.json",
    "release-env-fill-checklist.md",
    "release-env-fill.template.env",
    "docker-image-plan.json",
    "docker-image-plan.md",
    "docker-image-submission-plan.json",
    "docker-image-submission-plan.md",
    "runtime-business-plan.json",
    "runtime-business-plan.md",
    "runtime-smoke-plan.json",
    "runtime-smoke-plan.md",
    "runtime-business-submission-plan.json",
    "runtime-business-submission-plan.md",
    "data-safety-plan.json",
    "data-safety-plan.md",
    "data-safety-owner-plan.json",
    "data-safety-owner-plan.md",
    "data-safety-submission-plan.json",
    "data-safety-submission-plan.md",
    "cutover-rehearsal-plan.json",
    "cutover-rehearsal-plan.md",
    "evidence-gaps.json",
    "evidence-runbook.json",
    "evidence-runbook.md",
    "evidence-acceptance.json",
    "evidence-acceptance.md",
    "evidence-artifact-gaps.json",
    "evidence-artifact-gaps.md",
    "explain-artifact-plan.json",
    "explain-artifact-plan.md",
    "blocking-inputs.json",
    "blocking-inputs.md",
    "blocking-inputs.template.env",
    "release-evidence-dispatch-plan.json",
    "release-evidence-dispatch-plan.md",
    "release-evidence-dispatch-inputs.json",
    "release-evidence-dispatch-command.sh",
    "evidence-env.template.env",
    "commands.txt",
    "owner-dispatch.json",
    "owner-packets/README.md",
  ];
  for (const file of requiredBundleFiles) {
    if (!manifestFileNames.has(file)) {
      issues.push(`manifest missing required bundle file: ${file}`);
    }
  }
  const checkedFiles = [];
  for (const entry of files) {
    const file = String(entry.file || "");
    if (!file || path.isAbsolute(file) || file.includes("..")) {
      issues.push(`invalid manifest file path: ${file || "<empty>"}`);
      continue;
    }
    const filePath = path.join(handoffBundleDir, file);
    if (!fs.existsSync(filePath)) {
      issues.push(`missing bundle file: ${file}`);
      continue;
    }
    const content = fs.readFileSync(filePath, "utf8");
    const bytes = Buffer.byteLength(content);
    const sha256 = sha256Text(content);
    checkedFiles.push({ file, bytes, sha256 });
    if (entry.bytes !== bytes) {
      issues.push(`byte mismatch: ${file}; expected=${entry.bytes}; actual=${bytes}`);
    }
    if (entry.sha256 !== sha256) {
      issues.push(`sha256 mismatch: ${file}; expected=${entry.sha256}; actual=${sha256}`);
    }
  }
  const requiredTextMarkers = [
    ["README.md", "## Status Views"],
    ["README.md", "production-closeout-status.md` first"],
    ["daily-brief.md", "## Lane Routes"],
    ["operator-progress.md", "## Lane Routes"],
    ["execution-status.md", "## Lane Routes"],
    ["final-review.md", "## Owner Lane Routes"],
    ["release-owner-closeout.md", "# DDD Release Owner Closeout"],
    ["release-owner-closeout.md", "## Immediate Next Lane"],
    ["release-owner-closeout.md", "## Required Command Sequence"],
    ["production-closeout-status.md", "# DDD Production Closeout Status"],
    ["production-closeout-status.md", "## Parallel Next Actions"],
    ["production-closeout-status.md", "## Required Before Production"],
    ["production-unblock-quickstart.md", "# DDD Production Unblock Quickstart"],
    ["production-unblock-quickstart.md", "## Fast Path"],
    ["production-unblock-quickstart.md", "## Final Gate"],
    ["production-unblock-plan.md", "# DDD Production Unblock Plan"],
    ["production-unblock-plan.md", "## Parallel Workstreams"],
    ["production-unblock-plan.md", "## Exit Criteria"],
    ["production-evidence-readiness.md", "# DDD Production Evidence Readiness"],
    ["production-evidence-readiness.md", "## Evidence Gates"],
    ["production-evidence-readiness.md", "## Blocking Evidence"],
    ["production-evidence-readiness.md", "## Verification Commands"],
    ["production-cutover-audit.md", "# DDD Production Cutover Audit"],
    ["production-cutover-audit.md", "## Audit Items"],
    ["production-cutover-audit.md", "## Parallel Next Actions"],
    ["production-cutover-audit.md", "## Required Commands"],
    ["release-env-fill-checklist.md", "# P0 Release Env Fill Checklist"],
    ["release-env-fill-checklist.md", "## Required Keys By Group"],
    ["release-env-fill-checklist.md", "## Validation Commands"],
    ["release-env-fill.template.env", "# P0 release env fill template."],
    ["release-env-fill.template.env", "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs"],
    ["lane-receipt-fragments.md", "# DDD Lane Receipt Fragments"],
    ["lane-receipt-fragments.md", "## Receipt JSON Skeleton"],
    ["lane-receipt-fragments.md", "## Owner Fragment Copy Blocks"],
    ["lane-receipt-fragments.md", "## Assembly Checklist"],
    ["lane-receipt-draft.md", "# DDD Lane Receipt Draft"],
    ["lane-receipt-draft.md", "## Validation Commands"],
    ["lane-completion-submission-check.md", "# DDD Lane Completion Submission Check"],
    ["lane-completion-submission-check.md", "## Submission Commands"],
    ["owner-evidence-intake.md", "# DDD Owner Evidence Intake"],
    ["owner-evidence-intake.md", "## Owner Intake"],
    ["lane-completion-receipt.template.md", "## Fill Rules"],
    ["lane-completion-receipt.template.md", "## Edit Checklist"],
    ["lane-completion-receipt.template.md", "## Lane Details"],
    ["lane-completion-receipt.template.md", "## Submission Flow"],
    ["lane-completion-receipt.template.md", "--final-review-enforce --lane-completion-receipt-file=<receipt-file>"],
    ["evidence-closure-board.md", "# DDD Evidence Closure Board"],
    ["evidence-closure-board.md", "## Lanes"],
    ["release-evidence-dispatch-plan.md", "# DDD Release Evidence Dispatch Plan"],
    ["release-evidence-dispatch-plan.md", "## Required Before Run"],
    ["release-evidence-dispatch-command.sh", "gh workflow run ddd-release-evidence.yml"],
  ];
  for (const [file, marker] of requiredTextMarkers) {
    const filePath = path.join(handoffBundleDir, file);
    if (fs.existsSync(filePath)) {
      const content = fs.readFileSync(filePath, "utf8");
      if (!content.includes(marker)) {
        issues.push(`bundle file missing required marker: ${file}: ${marker}`);
      }
    }
  }
  const requiredJsonLaneRoutes = [
    ["daily-brief.json", "laneRoutes"],
    ["operator-progress.json", "laneRoutes"],
    ["execution-status.json", "laneRoutes"],
    ["production-cutover-audit.json", "auditItems"],
  ];
  for (const [file, key] of requiredJsonLaneRoutes) {
    const value = readJson(path.join(handoffBundleDir, file), null);
    if (value && (!Array.isArray(value[key]) || value[key].length === 0)) {
      issues.push(`bundle file missing non-empty ${key}: ${file}`);
    }
  }
  const finalReview = readJson(path.join(handoffBundleDir, "final-review.json"), null);
  const finalReviewOwners = Array.isArray(finalReview?.ownerDispatch?.owners) ? finalReview.ownerDispatch.owners : [];
  if (finalReview && !finalReviewOwners.some((owner) => Array.isArray(owner.lanes) && owner.lanes.length > 0)) {
    issues.push("final-review.json ownerDispatch must include at least one owner lane route");
  }
  const laneReceiptFragments = readJson(path.join(handoffBundleDir, "lane-receipt-fragments.json"), null);
  if (!laneReceiptFragments) {
    issues.push("missing or invalid lane receipt fragments: lane-receipt-fragments.json");
  } else {
    const fragments = Array.isArray(laneReceiptFragments.fragments) ? laneReceiptFragments.fragments : [];
    if (!Array.isArray(laneReceiptFragments.fragments)) {
      issues.push("lane-receipt-fragments fragments must be an array");
    }
    if (laneReceiptFragments.laneCount !== fragments.length) {
      issues.push(`lane-receipt-fragments laneCount mismatch: expected=${laneReceiptFragments.laneCount}; actual=${fragments.length}`);
    }
    if (fragments.length !== 5) {
      issues.push(`lane-receipt-fragments must include 5 lanes: actual=${fragments.length}`);
    }
    const keys = fragments.map((fragment) => fragment.key);
    for (const requiredKey of ["release-infra:p0-release-env", "release-infra:p0-docker-images", "release-infra:p1-runtime-business", "platform-owners:p1-p2-data-safety", "release-infra:final-review"]) {
      if (!keys.includes(requiredKey)) {
        issues.push(`lane-receipt-fragments missing required lane: ${requiredKey}`);
      }
    }
  }
  const laneReceiptDraft = readJson(path.join(handoffBundleDir, "lane-receipt-draft.json"), null);
  if (!laneReceiptDraft) {
    issues.push("missing or invalid lane receipt draft: lane-receipt-draft.json");
  } else {
    const laneReceipts = Array.isArray(laneReceiptDraft.laneReceipts) ? laneReceiptDraft.laneReceipts : [];
    if (!Array.isArray(laneReceiptDraft.laneReceipts)) {
      issues.push("lane-receipt-draft laneReceipts must be an array");
    }
    if (laneReceiptDraft.laneReceiptCount !== laneReceipts.length) {
      issues.push(`lane-receipt-draft laneReceiptCount mismatch: expected=${laneReceiptDraft.laneReceiptCount}; actual=${laneReceipts.length}`);
    }
    if (laneReceipts.length !== 5) {
      issues.push(`lane-receipt-draft must include 5 lane receipts: actual=${laneReceipts.length}`);
    }
    if (laneReceiptDraft.redacted !== true) {
      issues.push("lane-receipt-draft must be redacted");
    }
  }
  const productionCutoverAudit = readJson(path.join(handoffBundleDir, "production-cutover-audit.json"), null);
  if (!productionCutoverAudit) {
    issues.push("missing or invalid production cutover audit: production-cutover-audit.json");
  } else {
    const parallelNextActions = Array.isArray(productionCutoverAudit.parallelNextActions) ? productionCutoverAudit.parallelNextActions : [];
    if (!Array.isArray(productionCutoverAudit.parallelNextActions)) {
      issues.push("production-cutover-audit parallelNextActions must be an array");
    }
    const actionIds = parallelNextActions.map((action) => action.id);
    for (const requiredActionId of ["first-wave-env", "lane-completion-receipt", "owner-evidence"]) {
      if (!actionIds.includes(requiredActionId)) {
        issues.push(`production-cutover-audit parallelNextActions missing action: ${requiredActionId}`);
      }
    }
    for (const action of parallelNextActions) {
      if (!action.id || !action.label || !action.owner || !action.reason || !action.command) {
        issues.push(`production-cutover-audit parallelNextAction is incomplete: ${action.id || "unknown"}`);
      }
    }
    const laneReceiptAction = parallelNextActions.find((action) => action.id === "lane-completion-receipt");
    if (laneReceiptAction && !String(laneReceiptAction.command).includes("--lane-completion-receipt-init")) {
      issues.push("production-cutover-audit lane-completion-receipt action must start from receipt init");
    }
  }
  const productionUnblockPlan = readJson(path.join(handoffBundleDir, "production-unblock-plan.json"), null);
  if (!productionUnblockPlan) {
    issues.push("missing or invalid production unblock plan: production-unblock-plan.json");
  } else {
    const parallelWorkstreams = Array.isArray(productionUnblockPlan.parallelWorkstreams) ? productionUnblockPlan.parallelWorkstreams : [];
    if (!Array.isArray(productionUnblockPlan.parallelWorkstreams)) {
      issues.push("production-unblock-plan parallelWorkstreams must be an array");
    }
    const workstreamIds = parallelWorkstreams.map((workstream) => workstream.id);
    for (const requiredWorkstreamId of ["first-wave-env", "lane-completion-receipt", "owner-evidence"]) {
      if (!workstreamIds.includes(requiredWorkstreamId)) {
        issues.push(`production-unblock-plan parallelWorkstreams missing workstream: ${requiredWorkstreamId}`);
      }
    }
    for (const workstream of parallelWorkstreams) {
      if (!workstream.id || !workstream.label || !workstream.owner || !workstream.reason || !workstream.command || !workstream.verifyCommand || !workstream.completionSignal) {
        issues.push(`production-unblock-plan parallelWorkstream is incomplete: ${workstream.id || "unknown"}`);
      }
    }
    if (!Array.isArray(productionUnblockPlan.exitCriteria) || productionUnblockPlan.exitCriteria.length === 0) {
      issues.push("production-unblock-plan must include exitCriteria");
    }
    if (productionCutoverAudit && productionUnblockPlan.blockedAuditItemCount !== productionCutoverAudit.blockedAuditItemCount) {
      issues.push(`production-unblock-plan blockedAuditItemCount mismatch: expected=${productionCutoverAudit.blockedAuditItemCount}; actual=${productionUnblockPlan.blockedAuditItemCount}`);
    }
  }
  const productionEvidenceReadiness = readJson(path.join(handoffBundleDir, "production-evidence-readiness.json"), null);
  if (!productionEvidenceReadiness) {
    issues.push("missing or invalid production evidence readiness: production-evidence-readiness.json");
  } else {
    const evidenceGates = Array.isArray(productionEvidenceReadiness.evidenceGates) ? productionEvidenceReadiness.evidenceGates : [];
    if (!Array.isArray(productionEvidenceReadiness.evidenceGates)) {
      issues.push("production-evidence-readiness evidenceGates must be an array");
    }
    const gateIds = evidenceGates.map((gate) => gate.id);
    for (const requiredGateId of ["first-wave-env-receipt", "lane-completion-receipt", "owner-evidence", "production-audit", "final-go-no-go"]) {
      if (!gateIds.includes(requiredGateId)) {
        issues.push(`production-evidence-readiness missing evidence gate: ${requiredGateId}`);
      }
    }
    for (const gate of evidenceGates) {
      if (!gate.id || !gate.label || !gate.status || !gate.command || !gate.verifyCommand || !gate.evidence) {
        issues.push(`production-evidence-readiness evidence gate is incomplete: ${gate.id || "unknown"}`);
      }
    }
    if (!Array.isArray(productionEvidenceReadiness.blockingEvidence)) {
      issues.push("production-evidence-readiness blockingEvidence must be an array");
    }
    if (productionCutoverAudit && productionEvidenceReadiness.blockedAuditItemCount !== productionCutoverAudit.blockedAuditItemCount) {
      issues.push(`production-evidence-readiness blockedAuditItemCount mismatch: expected=${productionCutoverAudit.blockedAuditItemCount}; actual=${productionEvidenceReadiness.blockedAuditItemCount}`);
    }
  }
  const ownerEvidenceIntake = readJson(path.join(handoffBundleDir, "owner-evidence-intake.json"), null);
  if (!ownerEvidenceIntake) {
    issues.push("missing or invalid owner evidence intake: owner-evidence-intake.json");
  } else {
    const owners = Array.isArray(ownerEvidenceIntake.owners) ? ownerEvidenceIntake.owners : [];
    if (!Array.isArray(ownerEvidenceIntake.owners)) {
      issues.push("owner-evidence-intake owners must be an array");
    }
    if (ownerEvidenceIntake.ownerCount !== owners.length) {
      issues.push(`owner-evidence-intake ownerCount mismatch: expected=${ownerEvidenceIntake.ownerCount}; actual=${owners.length}`);
    }
    if (owners.length !== selectedOwnerPackets.length) {
      issues.push(`owner-evidence-intake must include selected owners: expected=${selectedOwnerPackets.length}; actual=${owners.length}`);
    }
    for (const owner of owners) {
      if (!owner.owner || !owner.ownerPacket || !owner.envTemplate || !Array.isArray(owner.lanes) || !Array.isArray(owner.submissionCommands)) {
        issues.push(`owner-evidence-intake owner row is incomplete: ${owner.owner || "unknown"}`);
      }
    }
  }
  const receiptTemplate = readJson(path.join(handoffBundleDir, "lane-completion-receipt.template.json"), null);
  if (receiptTemplate && (!Array.isArray(receiptTemplate.submissionFlow) || receiptTemplate.submissionFlow.length === 0)) {
    issues.push("lane-completion-receipt.template.json must include submissionFlow");
  }
  const releaseEvidenceDispatchInputs = readJson(path.join(handoffBundleDir, "release-evidence-dispatch-inputs.json"), null);
  if (!releaseEvidenceDispatchInputs) {
    issues.push("missing or invalid release evidence dispatch inputs: release-evidence-dispatch-inputs.json");
  } else {
    if (!releaseEvidenceDispatchInputs.payload || typeof releaseEvidenceDispatchInputs.payload !== "object") {
      issues.push("release-evidence-dispatch-inputs must include payload");
    }
    for (const requiredInput of ["mode", "strict", "backend_base_url", "frontend_base_url", "lane_completion_receipt_base64"]) {
      if (!Object.hasOwn(releaseEvidenceDispatchInputs.payload || {}, requiredInput)) {
        issues.push(`release-evidence-dispatch-inputs payload missing input: ${requiredInput}`);
      }
    }
    if (!Array.isArray(releaseEvidenceDispatchInputs.validationCommands) || releaseEvidenceDispatchInputs.validationCommands.length === 0) {
      issues.push("release-evidence-dispatch-inputs must include validationCommands");
    }
  }
  const ownerLaneMatrix = readJson(path.join(handoffBundleDir, "owner-lane-matrix.json"), null);
  const expectedClosureLaneCount = Array.isArray(ownerLaneMatrix?.owners)
    ? ownerLaneMatrix.owners.reduce((count, owner) => count + (Array.isArray(owner?.lanes) ? owner.lanes.length : 0), 0)
    : null;
  const evidenceClosureBoard = readJson(path.join(handoffBundleDir, "evidence-closure-board.json"), null);
  const evidenceClosureBoardCsvPath = path.join(handoffBundleDir, "evidence-closure-board.csv");
  if (!evidenceClosureBoard) {
    issues.push("missing or invalid evidence closure board: evidence-closure-board.json");
  } else {
    const closureLanes = Array.isArray(evidenceClosureBoard.lanes) ? evidenceClosureBoard.lanes : [];
    if (!Array.isArray(evidenceClosureBoard.lanes)) {
      issues.push("evidence-closure-board lanes must be an array");
    }
    if (evidenceClosureBoard.laneCount !== closureLanes.length) {
      issues.push(`evidence-closure-board laneCount mismatch: expected=${evidenceClosureBoard.laneCount}; actual=${closureLanes.length}`);
    }
    if (expectedClosureLaneCount !== null && closureLanes.length !== expectedClosureLaneCount) {
      issues.push(`evidence-closure-board owner-lane matrix mismatch: expected=${expectedClosureLaneCount}; actual=${closureLanes.length}`);
    }
    for (const lane of closureLanes) {
      const laneKey = lane?.key || "<unknown>";
      if (!lane?.owner || !lane?.lane) {
        issues.push(`evidence-closure-board lane missing owner or lane: ${laneKey}`);
      }
      if (!["PASS", "BLOCKED"].includes(lane?.status)) {
        issues.push(`evidence-closure-board lane status invalid for ${laneKey}`);
      }
      if (!lane?.sourcePlan) {
        issues.push(`evidence-closure-board lane missing sourcePlan for ${laneKey}`);
      }
    }
    if (fs.existsSync(evidenceClosureBoardCsvPath)) {
      const csv = fs.readFileSync(evidenceClosureBoardCsvPath, "utf8");
      const csvRows = csv.trimEnd().split(/\r?\n/);
      if (!csvRows[0]?.includes('"key","owner","lane","status","receiptStatus"')) {
        issues.push("evidence-closure-board.csv missing required header");
      }
      if (csvRows.length - 1 !== closureLanes.length) {
        issues.push(`evidence-closure-board.csv row count mismatch: expected=${closureLanes.length}; actual=${csvRows.length - 1}`);
      }
    }
  }
  const ownerDispatchPath = path.join(handoffBundleDir, "owner-dispatch.json");
  const ownerDispatch = readJson(ownerDispatchPath, null);
  if (!ownerDispatch) {
    issues.push("missing or invalid owner dispatch: owner-dispatch.json");
  } else {
    const owners = Array.isArray(ownerDispatch.owners) ? ownerDispatch.owners : [];
    if (!Array.isArray(ownerDispatch.owners)) {
      issues.push("owner-dispatch owners must be an array");
    }
    if (ownerDispatch.ownerCount !== owners.length) {
      issues.push(`owner-dispatch ownerCount mismatch: expected=${ownerDispatch.ownerCount}; actual=${owners.length}`);
    }
    if (!owners.some((owner) => Array.isArray(owner?.lanes) && owner.lanes.length > 0)) {
      issues.push("owner-dispatch must include at least one owner lane route");
    }
    for (const owner of owners) {
      const ownerName = owner?.owner || "<unknown>";
      const lanes = Array.isArray(owner?.lanes) ? owner.lanes : [];
      if (!Array.isArray(owner?.lanes)) {
        issues.push(`owner-dispatch lanes must be an array for ${ownerName}`);
      }
      if (owner?.laneCount !== lanes.length) {
        issues.push(`owner-dispatch laneCount mismatch for ${ownerName}: expected=${owner?.laneCount}; actual=${lanes.length}`);
      }
      for (const lane of lanes) {
        const laneName = lane?.lane || "<unknown>";
        if (!lane?.command) {
          issues.push(`owner-dispatch lane missing command for ${ownerName}:${laneName}`);
        }
        if (!lane?.sourcePlan) {
          issues.push(`owner-dispatch lane missing sourcePlan for ${ownerName}:${laneName}`);
        }
      }
      for (const key of ["markdown", "json", "blockingInputsEnvTemplate"]) {
        const file = String(owner?.[key] || "");
        if (!file || path.isAbsolute(file) || file.includes("..")) {
          issues.push(`owner-dispatch invalid ${key} path for ${ownerName}: ${file || "<empty>"}`);
          continue;
        }
        if (!manifestFileNames.has(file)) {
          issues.push(`manifest missing owner ${key} packet for ${ownerName}: ${file}`);
        }
        if (!fs.existsSync(path.join(handoffBundleDir, file))) {
          issues.push(`missing owner ${key} packet for ${ownerName}: ${file}`);
        }
      }
      const jsonFile = String(owner?.json || "");
      const jsonPath = jsonFile && !path.isAbsolute(jsonFile) && !jsonFile.includes("..")
        ? path.join(handoffBundleDir, jsonFile)
        : null;
      const ownerPacketJson = jsonPath ? readJson(jsonPath, null) : null;
      if (!ownerPacketJson) {
        issues.push(`missing or invalid owner packet JSON for ${ownerName}`);
      } else {
        for (const key of ["owner", "blockers", "placeholders", "secretKeys", "laneCount", "nextCommand"]) {
          if (ownerPacketJson?.[key] !== owner?.[key]) {
            issues.push(`owner packet JSON ${key} mismatch for ${ownerName}`);
          }
        }
        if (ownerPacketJson?.missingEvidenceArtifactCount !== owner?.missingEvidenceArtifactCount) {
          issues.push(`owner packet JSON missingEvidenceArtifactCount mismatch for ${ownerName}`);
        }
        if (ownerPacketJson?.blockingInputCount !== owner?.blockingInputCount) {
          issues.push(`owner packet JSON blockingInputCount mismatch for ${ownerName}`);
        }
      }
      if (lanes.length > 0) {
        const markdownFile = String(owner?.markdown || "");
        const markdownPath = markdownFile && !path.isAbsolute(markdownFile) && !markdownFile.includes("..")
          ? path.join(handoffBundleDir, markdownFile)
          : null;
        if (markdownPath && fs.existsSync(markdownPath)) {
          const markdown = fs.readFileSync(markdownPath, "utf8");
          for (const marker of ["## Submission Routes", "Expected artifacts:", "Currently missing artifacts:"]) {
            if (!markdown.includes(marker)) {
              issues.push(`owner packet missing required marker for ${ownerName}: ${marker}`);
            }
          }
          for (const lane of lanes) {
            const laneName = lane?.lane || "<unknown>";
            const expectedFragments = [
              lane?.lane,
              lane?.sourcePlan,
              lane?.command,
              ...(Array.isArray(lane?.expectedArtifacts) ? lane.expectedArtifacts : []),
              ...(Array.isArray(lane?.missingArtifacts) ? lane.missingArtifacts : []),
            ].filter(Boolean);
            for (const fragment of expectedFragments) {
              if (!markdown.includes(fragment)) {
                issues.push(`owner packet missing lane fragment for ${ownerName}:${laneName}: ${fragment}`);
              }
            }
          }
        }
        const queueLanes = Array.isArray(ownerPacketJson?.queueLanes) ? ownerPacketJson.queueLanes : null;
        if (!queueLanes) {
          issues.push(`owner packet JSON missing queueLanes for ${ownerName}`);
        } else if (queueLanes.length !== lanes.length) {
          issues.push(`owner packet JSON queueLanes mismatch for ${ownerName}: expected=${lanes.length}; actual=${queueLanes.length}`);
        } else {
          for (const lane of lanes) {
            const laneName = lane?.lane || "<unknown>";
            const packetLane = queueLanes.find((item) => item?.lane === lane?.lane);
            if (!packetLane) {
              issues.push(`owner packet JSON missing lane for ${ownerName}:${laneName}`);
              continue;
            }
            for (const key of ["order", "status", "command", "sourcePlan", "missingEvidenceArtifactCount"]) {
              if (packetLane?.[key] !== lane?.[key]) {
                issues.push(`owner packet JSON lane ${key} mismatch for ${ownerName}:${laneName}`);
              }
            }
            for (const key of ["acceptanceCommands", "expectedArtifacts", "missingArtifacts"]) {
              if (JSON.stringify(packetLane?.[key] || []) !== JSON.stringify(lane?.[key] || [])) {
                issues.push(`owner packet JSON lane ${key} mismatch for ${ownerName}:${laneName}`);
              }
            }
          }
        }
      }
    }
  }
  return {
    status: issues.length === 0 ? "PASS" : "BLOCKED",
    willWriteFiles: false,
    bundleDir: relative(handoffBundleDir),
    manifest: fs.existsSync(manifestPath) ? relative(manifestPath) : null,
    checkedFileCount: checkedFiles.length,
    checkedFiles,
    issues,
  };
}

if (summaryOnly) {
  process.stdout.write(renderSummary(checklist));
  process.exit(0);
}

if (evidenceGapsOnly) {
  runEvidenceGaps(checklist);
}

if (evidenceRunbookOnly) {
  runEvidenceRunbook();
}

if (evidenceRunbookMarkdownOnly) {
  runEvidenceRunbook({ markdown: true });
}

function buildEvidenceAcceptance({ rollupOverride = null } = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const trackById = new Map(checklist.tracks.map((track) => [track.id, track]));
  const items = rollup.items.map((gate, index) => {
    const track = trackById.get(gate.track) || {};
    const accepted = gate.status === "PASS";
    const artifactChecks = (track.artifacts || []).map((artifact) => checkEvidenceArtifact(artifact));
    return {
      order: index + 1,
      gate: gate.id,
      track: gate.track,
      title: track.title || gate.id,
      owner: gate.owner,
      status: gate.status,
      accepted,
      blocker: gate.issue || null,
      blockingInputs: gate.blockingInputs || [],
      acceptanceCommand: gate.nextCommand,
      evidenceCommands: track.commands || [],
      expectedArtifacts: track.artifacts || [],
      artifactChecks,
      presentArtifactCount: artifactChecks.filter((artifact) => artifact.present === true).length,
      missingArtifactCount: artifactChecks.filter((artifact) => artifact.present === false).length,
      envKeys: track.envKeys || [],
      criteria: [
        `${gate.nextCommand} returns PASS`,
        "expected artifacts are produced from HTTPS staging or production-equivalent evidence",
        "release evidence summary is regenerated after this gate passes",
      ],
    };
  });
  return {
    status: items.every((item) => item.accepted) ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: rollup.finalRecommendation,
    cutoverAllowed: rollup.cutoverAllowed,
    acceptedCount: items.filter((item) => item.accepted).length,
    itemCount: items.length,
    blockedCount: items.filter((item) => !item.accepted).length,
    presentArtifactCount: items.reduce((count, item) => count + item.presentArtifactCount, 0),
    missingArtifactCount: items.reduce((count, item) => count + item.missingArtifactCount, 0),
    items,
    nextCommand: items.every((item) => item.accepted)
      ? "node scripts/ddd-staging-execution-checklist.mjs --rollup-enforce"
      : "node scripts/ddd-staging-execution-checklist.mjs --evidence-runbook-markdown",
  };
}

function checkEvidenceArtifact(artifact) {
  const normalized = portableEnvFile(artifact);
  if (normalized.includes("*")) {
    const matches = findSimpleWildcardMatches(normalized);
    return {
      artifact: normalized,
      present: matches.length > 0,
      matchCount: matches.length,
      matches,
    };
  }
  return {
    artifact: normalized,
    present: fs.existsSync(path.join(repoRoot, normalized)),
    matchCount: fs.existsSync(path.join(repoRoot, normalized)) ? 1 : 0,
    matches: fs.existsSync(path.join(repoRoot, normalized)) ? [normalized] : [],
  };
}

function findSimpleWildcardMatches(pattern) {
  const normalized = portableEnvFile(pattern);
  const starIndex = normalized.indexOf("*");
  if (starIndex === -1) return fs.existsSync(path.join(repoRoot, normalized)) ? [normalized] : [];
  const slashBeforeStar = normalized.lastIndexOf("/", starIndex);
  const dir = slashBeforeStar === -1 ? "." : normalized.slice(0, slashBeforeStar);
  const filePattern = normalized.slice(slashBeforeStar + 1);
  const dirPath = path.join(repoRoot, dir);
  if (!fs.existsSync(dirPath)) return [];
  const regex = new RegExp(`^${filePattern.split("*").map(escapeRegExp).join(".*")}$`);
  return fs.readdirSync(dirPath)
    .filter((entry) => regex.test(entry))
    .map((entry) => portableEnvFile(path.join(dir, entry)))
    .sort();
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function renderEvidenceAcceptanceMarkdown(acceptance) {
  const lines = [
    "# DDD Staging Evidence Acceptance",
    "",
    `Status: ${acceptance.status}`,
    `Final recommendation: ${acceptance.finalRecommendation}`,
    `Cutover allowed: ${acceptance.cutoverAllowed}`,
    `Accepted: ${acceptance.acceptedCount}/${acceptance.itemCount}`,
    `Artifacts present: ${acceptance.presentArtifactCount}`,
    `Artifacts missing: ${acceptance.missingArtifactCount}`,
    "",
    "| Gate | Owner | Accepted | Current blocker | Blocking inputs | Acceptance command | Expected artifacts |",
    "| --- | --- | --- | --- | --- | --- | --- |",
    ...acceptance.items.map((item) => [
      item.gate,
      item.owner,
      item.accepted ? "yes" : "no",
      item.blocker || "none",
      item.blockingInputs.length > 0 ? item.blockingInputs.map((input) => `\`${input}\``).join(", ") : "none",
      `\`${item.acceptanceCommand}\``,
      item.expectedArtifacts.length > 0 ? item.expectedArtifacts.map((artifact) => `\`${artifact}\``).join(", ") : "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Criteria",
    "",
  ];
  for (const item of acceptance.items) {
    lines.push(
      `### ${item.order}. ${item.title}`,
      "",
      `- Gate: \`${item.gate}\``,
      `- Track: \`${item.track}\``,
      `- Owner: ${item.owner}`,
      `- Status: ${item.status}`,
      `- Acceptance command: \`${item.acceptanceCommand}\``,
      `- Blocking inputs: ${item.blockingInputs.length > 0 ? item.blockingInputs.map((input) => `\`${input}\``).join(", ") : "none"}`,
      `- Artifacts present: ${item.presentArtifactCount}/${item.artifactChecks.length}`,
      ...(item.artifactChecks || []).map((artifact) => `- ${artifact.present ? "present" : "missing"}: \`${artifact.artifact}\`${artifact.matchCount > 1 ? ` (${artifact.matchCount} matches)` : ""}`),
      ...(item.criteria || []).map((criterion) => `- ${criterion}`),
      "",
    );
  }
  lines.push(`Next: \`${acceptance.nextCommand}\``, "");
  return lines.join("\n");
}

function runEvidenceAcceptance({ markdown = false } = {}) {
  let acceptance;
  try {
    acceptance = buildEvidenceAcceptance();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderEvidenceAcceptanceMarkdown(acceptance));
  } else {
    console.log(JSON.stringify(acceptance, null, 2));
  }
  process.exit(0);
}

if (evidenceAcceptanceOnly) {
  runEvidenceAcceptance();
}

if (evidenceAcceptanceMarkdownOnly) {
  runEvidenceAcceptance({ markdown: true });
}

function buildEvidenceArtifactGapReport({ acceptanceOverride = null, rollupOverride = null } = {}) {
  const acceptance = acceptanceOverride || buildEvidenceAcceptance({ rollupOverride });
  const artifactMap = new Map();
  for (const item of acceptance.items || []) {
    for (const artifact of item.artifactChecks || []) {
      const key = artifact.artifact;
      const current = artifactMap.get(key) || {
        artifact: key,
        present: artifact.present,
        matchCount: 0,
        matches: [],
        gates: [],
        owners: new Set(),
        dispatchOwners: new Set(),
        acceptanceCommands: new Set(),
        evidenceCommands: new Set(),
      };
      current.present = current.present || artifact.present;
      current.matchCount += artifact.matchCount || 0;
      current.matches = unique([...(current.matches || []), ...(artifact.matches || [])]);
      const dispatchOwner = dispatchOwnerForRawOwner(item.owner);
      current.gates.push({
        gate: item.gate,
        track: item.track,
        owner: item.owner,
        dispatchOwner,
        accepted: item.accepted,
        blocker: item.blocker || null,
        acceptanceCommand: item.acceptanceCommand,
      });
      current.owners.add(item.owner);
      current.dispatchOwners.add(dispatchOwner);
      if (item.acceptanceCommand) current.acceptanceCommands.add(item.acceptanceCommand);
      for (const command of item.evidenceCommands || []) current.evidenceCommands.add(command);
      artifactMap.set(key, current);
    }
  }
  const artifacts = [...artifactMap.values()].map((item) => ({
    artifact: item.artifact,
    present: item.present,
    matches: item.matches,
    matchCount: item.matches.length,
    gateCount: item.gates.length,
    gates: item.gates.sort((left, right) => left.gate.localeCompare(right.gate)),
    owners: [...item.owners].sort(),
    dispatchOwners: [...item.dispatchOwners].sort(),
    acceptanceCommands: [...item.acceptanceCommands].sort(),
    evidenceCommands: [...item.evidenceCommands].sort(),
  })).sort((left, right) => Number(left.present) - Number(right.present) || left.artifact.localeCompare(right.artifact));
  const missingArtifacts = artifacts.filter((artifact) => artifact.present === false);
  const presentArtifacts = artifacts.filter((artifact) => artifact.present === true);
  return {
    status: missingArtifacts.length === 0 ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: acceptance.finalRecommendation,
    cutoverAllowed: acceptance.cutoverAllowed,
    artifactCount: artifacts.length,
    presentArtifactCount: presentArtifacts.length,
    missingArtifactCount: missingArtifacts.length,
    missingArtifacts,
    presentArtifacts,
    artifacts,
    nextCommand: missingArtifacts.length === 0
      ? "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance"
      : missingArtifacts[0]?.acceptanceCommands[0] || acceptance.nextCommand,
  };
}

function renderEvidenceArtifactGapReportMarkdown(report) {
  const lines = [
    "# DDD Evidence Artifact Gap Report",
    "",
    `Status: ${report.status}`,
    `Final recommendation: ${report.finalRecommendation}`,
    `Cutover allowed: ${report.cutoverAllowed}`,
    `Artifacts: ${report.presentArtifactCount}/${report.artifactCount} present; missing=${report.missingArtifactCount}`,
    "",
    "## Missing Artifacts",
    "",
    ...(report.missingArtifacts.length > 0
      ? [
        "| Artifact | Gates | Owners | Dispatch owners | Acceptance commands |",
        "| --- | --- | --- | --- | --- |",
        ...report.missingArtifacts.map((artifact) => [
          `\`${artifact.artifact}\``,
          artifact.gates.map((gate) => gate.gate).join(", "),
          artifact.owners.join(", "),
          artifact.dispatchOwners.join(", "),
          artifact.acceptanceCommands.map((command) => `\`${command}\``).join("<br>") || "none",
        ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
      ]
      : ["- none"]),
    "",
    "## Present Artifacts",
    "",
    ...(report.presentArtifacts.length > 0
      ? report.presentArtifacts.map((artifact) => `- \`${artifact.artifact}\`: gates=${artifact.gates.map((gate) => gate.gate).join(", ")}; matches=${artifact.matchCount}`)
      : ["- none"]),
    "",
    `Next: \`${report.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runEvidenceArtifactGapReport({ markdown = false } = {}) {
  let report;
  try {
    report = buildEvidenceArtifactGapReport();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderEvidenceArtifactGapReportMarkdown(report));
  } else {
    console.log(JSON.stringify(report, null, 2));
  }
  process.exit(0);
}

if (evidenceArtifactGapReportOnly) {
  runEvidenceArtifactGapReport();
}

if (evidenceArtifactGapReportMarkdownOnly) {
  runEvidenceArtifactGapReport({ markdown: true });
}

function buildExplainArtifactPlan({ artifactGapReportOverride = null, ownerPlanOverride = null, dataSafetyPlanOverride = null, rollupOverride = null } = {}) {
  const artifactGapReport = artifactGapReportOverride || buildEvidenceArtifactGapReport({ rollupOverride });
  const dataSafetyPlan = dataSafetyPlanOverride || buildDataSafetyPlan({ rollupOverride });
  const ownerPlan = ownerPlanOverride || buildDataSafetyOwnerPlan({ dataSafetyPlanOverride: dataSafetyPlan, rollupOverride });
  const explainArtifact = (artifactGapReport.artifacts || []).find((artifact) => artifact.artifact === "tmp/ddd-explain/*.json")
    || (artifactGapReport.missingArtifacts || [])[0]
    || null;
  const explainPhases = (ownerPlan.phases || []).filter((phase) => phase.id?.startsWith("explain-"));
  const requiredInputs = unique(explainPhases.flatMap((phase) => phase.requiredInputs || []));
  const evidenceCommands = unique([
    "node scripts/ddd-staging-data-safety-check.mjs",
    ...explainPhases.flatMap((phase) => phase.commands || []),
    ...(explainArtifact?.evidenceCommands || []),
    "DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
  ]);
  const expectedArtifacts = unique([
    "tmp/ddd-explain/*.json",
    ...explainPhases.flatMap((phase) => phase.artifacts || []),
    "artifacts/ddd/release/explain-gate-report.json",
  ]);
  const dispatchOwners = unique(explainArtifact?.dispatchOwners || ["platform-owners"]);
  const sourceOwners = unique(explainArtifact?.owners || explainPhases.map((phase) => phase.owner) || ["database"]);
  const dependentGates = unique((explainArtifact?.gates || []).map((gate) => gate.gate));
  const artifactPresent = Boolean(explainArtifact?.present);
  const status = artifactPresent ? "PASS" : "BLOCKED";
  return {
    status,
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: artifactGapReport.finalRecommendation,
    cutoverAllowed: artifactGapReport.cutoverAllowed,
    missingArtifact: artifactPresent ? null : "tmp/ddd-explain/*.json",
    artifactPresent,
    dispatchOwners,
    sourceOwners,
    dependentGates,
    requiredInputs,
    expectedArtifacts,
    commands: evidenceCommands,
    acceptanceCommands: unique(explainArtifact?.acceptanceCommands || ["node scripts/ddd-staging-data-safety-check.mjs"]),
    envTemplate: [
      "DDD_EXPLAIN_DATABASE=__REQUIRED__",
      "DDD_EXPLAIN_ENVIRONMENT=staging",
      "DDD_EXPLAIN_STRICT=true",
      "MYSQL_CLI=mysql",
      "MYSQL_HOST=__REQUIRED__",
      "MYSQL_PORT=3306",
      "MYSQL_USER=__REQUIRED_READONLY_USER__",
      "MYSQL_PASSWORD=__SECRET_REFERENCE_ONLY__",
      "DDD_RELEASE_CANDIDATE=__REQUIRED_SHA_OR_TAG__",
      "DDD_EVIDENCE_OPERATOR=__REQUIRED__",
    ],
    passCriteria: [
      "`node scripts/ddd-collect-explain.mjs` writes JSON files under `tmp/ddd-explain/`.",
      "`DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs` passes and writes the EXPLAIN gate report.",
      "`node scripts/ddd-staging-data-safety-check.mjs` accepts rollback, migration, and EXPLAIN evidence.",
      "`node scripts/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report` shows no missing EXPLAIN artifact.",
    ],
    nextCommand: artifactPresent
      ? "DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs"
      : "node scripts/ddd-collect-explain.mjs",
  };
}

function renderExplainArtifactPlanMarkdown(plan) {
  const lines = [
    "# DDD EXPLAIN Artifact Plan",
    "",
    `Status: ${plan.status}`,
    `Final recommendation: ${plan.finalRecommendation}`,
    `Cutover allowed: ${plan.cutoverAllowed}`,
    `Missing artifact: ${plan.missingArtifact ? `\`${plan.missingArtifact}\`` : "none"}`,
    `Dispatch owners: ${plan.dispatchOwners.join(", ") || "none"}`,
    `Source owners: ${plan.sourceOwners.join(", ") || "none"}`,
    `Dependent gates: ${plan.dependentGates.join(", ") || "none"}`,
    "",
    "## Required Inputs",
    "",
    ...plan.requiredInputs.map((input) => `- \`${input}\``),
    "",
    "## Env Template",
    "",
    "```env",
    ...plan.envTemplate,
    "```",
    "",
    "## Commands",
    "",
    ...plan.commands.map((command) => `- \`${command}\``),
    "",
    "## Expected Artifacts",
    "",
    ...plan.expectedArtifacts.map((artifact) => `- \`${artifact}\``),
    "",
    "## Pass Criteria",
    "",
    ...plan.passCriteria.map((item) => `- ${item}`),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runExplainArtifactPlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildExplainArtifactPlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderExplainArtifactPlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (explainArtifactPlanOnly) {
  runExplainArtifactPlan();
}

if (explainArtifactPlanMarkdownOnly) {
  runExplainArtifactPlan({ markdown: true });
}

const closureEstimateByTrack = {
  "p0-release-env": {
    phase: "P0",
    sequence: 1,
    eta: "2-4h if owner inputs are available; 0.5-1d if secrets must be collected",
    parallelGroup: "release-foundation",
  },
  "p0-images": {
    phase: "P0",
    sequence: 2,
    eta: "1-3h with CI/Docker access; 0.5-1d if image provenance must be recreated",
    parallelGroup: "release-foundation",
  },
  "p1-runtime-business": {
    phase: "P1",
    sequence: 3,
    eta: "2-4h with staging URLs and deployment evidence; 0.5-1d if deployment must be provisioned",
    parallelGroup: "runtime-validation",
  },
  "p1-rollback": {
    phase: "P1",
    sequence: 4,
    eta: "2-4h with owner drill evidence; 0.5-1d if deferrals need review",
    parallelGroup: "data-safety",
  },
  "p2-database-performance": {
    phase: "P2",
    sequence: 5,
    eta: "2-6h with database access; 0.5-1d if fresh/upgrade drills must be scheduled",
    parallelGroup: "data-safety",
  },
};

function buildClosurePlan({ rollupOverride = null } = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const acceptance = buildEvidenceAcceptance({ rollupOverride: rollup });
  const blockingInputs = buildBlockingInputs({ rollupOverride: rollup });
  const trackById = new Map(checklist.tracks.map((track) => [track.id, track]));
  const blockedGates = (rollup.items || []).filter((gate) => gate.status !== "PASS");
  const items = blockedGates
    .map((gate) => {
      const track = trackById.get(gate.track) || {};
      const estimate = closureEstimateByTrack[gate.track] || {
        phase: "P?",
        sequence: 99,
        eta: "estimate unavailable",
        parallelGroup: "unknown",
      };
      return {
        order: estimate.sequence,
        phase: estimate.phase,
        gate: gate.id,
        track: gate.track,
        title: track.title || gate.id,
        owner: gate.owner,
        status: gate.status,
        blocker: gate.issue || null,
        blockingInputs: gate.blockingInputs || [],
        setupCommands: track.setupCommands || [],
        evidenceCommands: track.commands || [],
        acceptanceCommand: gate.nextCommand || null,
        expectedArtifacts: track.artifacts || [],
        eta: estimate.eta,
        parallelGroup: estimate.parallelGroup,
      };
    })
    .sort((left, right) => left.order - right.order || left.gate.localeCompare(right.gate));
  const phases = [...new Map(items.map((item) => [item.phase, {
    phase: item.phase,
    gates: items.filter((candidate) => candidate.phase === item.phase).map((candidate) => candidate.gate),
    owners: unique(items.filter((candidate) => candidate.phase === item.phase).flatMap((candidate) => String(candidate.owner || "").split(",").map((owner) => owner.trim()))).sort(),
    parallelGroups: unique(items.filter((candidate) => candidate.phase === item.phase).map((candidate) => candidate.parallelGroup)).sort(),
  }])).values()];
  return {
    status: items.length === 0 ? "READY" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: rollup.finalRecommendation,
    cutoverAllowed: rollup.cutoverAllowed,
    cutoverReady: rollup.status === "PASS" && rollup.cutoverAllowed === true && acceptance.status === "PASS",
    blockedGateCount: items.length,
    acceptedGateCount: acceptance.acceptedCount,
    acceptedGateTotal: acceptance.itemCount,
    phases,
    items,
    topBlockingInputs: blockingInputs.inputs.slice(0, 12).map((input) => ({
      input: input.input,
      gateCount: input.gateCount,
      owners: input.owners,
      nextCommand: input.nextCommands[0] || null,
    })),
    eta: items.length === 0
      ? "ready for final go/no-go enforcement"
      : "fast path 0.5-1.5d with staging access and owner evidence ready; 1-3d if deployment, Docker, database, or approval evidence must be produced",
    criticalPath: [
      "P0 release env and image evidence must close before expensive staging validation is trusted.",
      "P1 runtime and rollback checks can run in parallel after P0 inputs are available.",
      "P2 migration and EXPLAIN checks can run in parallel with P1 when database access is ready.",
      "Final cutover requires --final-review-enforce and release-final-go-no-go-gate.sh to pass.",
    ],
    verificationCommands: [
      "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
      "node scripts/ddd-staging-execution-checklist.mjs --rollup-enforce",
      "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
      "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    ],
    nextCommand: items.length === 0
      ? "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce"
      : (items[0]?.setupCommands?.[0] || items[0]?.acceptanceCommand || "node scripts/ddd-staging-execution-checklist.mjs --commands"),
  };
}

function renderClosurePlanMarkdown(plan) {
  const lines = [
    "# DDD Staging Closure Plan",
    "",
    `Status: ${plan.status}`,
    `Final recommendation: ${plan.finalRecommendation}`,
    `Cutover allowed: ${plan.cutoverAllowed}`,
    `Accepted gates: ${plan.acceptedGateCount}/${plan.acceptedGateTotal}`,
    `ETA: ${plan.eta}`,
    "",
    "| Phase | Gate | Owner | ETA | Current blocker | Next command |",
    "| --- | --- | --- | --- | --- | --- |",
    ...plan.items.map((item) => [
      item.phase,
      `\`${item.gate}\``,
      item.owner,
      item.eta,
      item.blocker || "none",
      `\`${item.setupCommands[0] || item.acceptanceCommand || "none"}\``,
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Critical Path",
    "",
    ...plan.criticalPath.map((item) => `- ${item}`),
    "",
    "## Top Blocking Inputs",
    "",
    ...(plan.topBlockingInputs.length > 0
      ? plan.topBlockingInputs.map((input) => `- \`${input.input}\`: gates=${input.gateCount}; owners=${input.owners.join(", ") || "none"}; next=\`${input.nextCommand || "none"}\``)
      : ["- none"]),
    "",
    "## Verification",
    "",
    ...plan.verificationCommands.map((command) => `- \`${command}\``),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runClosurePlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildClosurePlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderClosurePlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (closurePlanOnly) {
  runClosurePlan();
}

if (closurePlanMarkdownOnly) {
  runClosurePlan({ markdown: true });
}

function buildNextActionQueue({ rollupOverride = null } = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const closurePlan = buildClosurePlan({ rollupOverride: rollup });
  const releaseEnvPlan = buildReleaseEnvPlan({ rollupOverride: rollup });
  const releaseEnvMatrix = buildReleaseEnvOwnerMatrix({ releaseEnvPlanOverride: releaseEnvPlan });
  const releaseEnvNextOwner = buildReleaseEnvNextOwnerTemplateReport({ rollupOverride: rollup, matrixOverride: releaseEnvMatrix });
  const dockerImagePlan = buildDockerImagePlan({ rollupOverride: rollup });
  const dockerImageSubmissionPlan = buildDockerImageSubmissionPlan({ dockerImagePlanOverride: dockerImagePlan, rollupOverride: rollup });
  const runtimeBusinessPlan = buildRuntimeBusinessPlan({ rollupOverride: rollup });
  const runtimeSmokePlan = buildRuntimeSmokePlan({ runtimeBusinessPlanOverride: runtimeBusinessPlan, rollupOverride: rollup });
  const runtimeBusinessSubmissionPlan = buildRuntimeBusinessSubmissionPlan({ runtimeBusinessPlanOverride: runtimeBusinessPlan, runtimeSmokePlanOverride: runtimeSmokePlan, rollupOverride: rollup });
  const dataSafetyPlan = buildDataSafetyPlan({ rollupOverride: rollup });
  const dataSafetyOwnerPlan = buildDataSafetyOwnerPlan({ dataSafetyPlanOverride: dataSafetyPlan, rollupOverride: rollup });
  const dataSafetySubmissionPlan = buildDataSafetySubmissionPlan({ dataSafetyPlanOverride: dataSafetyPlan, dataSafetyOwnerPlanOverride: dataSafetyOwnerPlan, rollupOverride: rollup });
  const explainArtifactPlan = buildExplainArtifactPlan({ rollupOverride: rollup });
  const queue = [
    {
      order: 1,
      lane: "p0-release-env",
      owner: releaseEnvNextOwner.owner || "release-infra",
      status: releaseEnvPlan.status,
      title: "Fill and validate the secure release env file",
      blocker: releaseEnvPlan.blocker || null,
      command: releaseEnvNextOwner.owner
        ? `node scripts/ddd-staging-execution-checklist.mjs --release-env-next-owner-template`
        : releaseEnvPlan.nextCommand,
      followUpCommands: [
        "node scripts/ddd-staging-execution-checklist.mjs --release-env-owner-matrix",
        "node scripts/ddd-staging-execution-checklist.mjs --release-env-merge-plan",
        "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
      ],
      artifacts: [
        "artifacts/ddd/release/staging-handoff-bundle/release-env-next-owner.template.env",
        "artifacts/ddd/release/staging-handoff-bundle/release-env-merge-plan.md",
      ],
      blockingInputs: releaseEnvPlan.blockingInputs || ["DDD_RELEASE_ENV_FILE"],
      sourcePlan: "release-env-plan.json",
    },
    {
      order: 2,
      lane: "p0-docker-images",
      owner: "release-infra",
      status: dockerImagePlan.status,
      title: "Produce Docker build or existing-image inspect evidence",
      blocker: dockerImagePlan.docker?.issues?.[0] || dockerImagePlan.staticDockerfiles?.issues?.[0] || null,
      command: "node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown",
      followUpCommands: dockerImageSubmissionPlan.validationCommands || [],
      artifacts: [
        "artifacts/ddd/release/staging-handoff-bundle/docker-image-submission-plan.md",
        dockerImageSubmissionPlan.evidenceArtifact,
      ],
      blockingInputs: dockerImagePlan.requiredInputs || [],
      sourcePlan: "docker-image-submission-plan.json",
    },
    {
      order: 3,
      lane: "p1-runtime-business",
      owner: runtimeSmokePlan.nextPhase?.owner || "release-infra",
      status: runtimeSmokePlan.status,
      title: runtimeSmokePlan.nextPhase?.goal || "Run runtime and business staging smokes",
      blocker: runtimeSmokePlan.nextPhase?.blocker || runtimeBusinessPlan.runtimeGate?.blocker || null,
      command: "node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown",
      followUpCommands: runtimeBusinessSubmissionPlan.validationCommands || [],
      artifacts: [
        "artifacts/ddd/release/staging-handoff-bundle/runtime-business-submission-plan.md",
        ...(runtimeSmokePlan.nextPhase?.artifacts || runtimeBusinessPlan.expectedArtifacts),
      ],
      blockingInputs: runtimeSmokePlan.nextPhase?.requiredInputs || runtimeBusinessPlan.requiredEnv?.urls || [],
      sourcePlan: "runtime-business-submission-plan.json",
    },
    {
      order: 4,
      lane: "p1-p2-data-safety",
      owner: dataSafetyOwnerPlan.nextPhase?.owner || "database",
      status: dataSafetyOwnerPlan.status,
      title: dataSafetyOwnerPlan.nextPhase?.goal || "Run rollback, migration, and EXPLAIN evidence",
      blocker: dataSafetyOwnerPlan.nextPhase?.blocker || null,
      command: "node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown",
      followUpCommands: dataSafetySubmissionPlan.validationCommands || [],
      artifacts: [
        "artifacts/ddd/release/staging-handoff-bundle/data-safety-submission-plan.md",
        ...(dataSafetyOwnerPlan.nextPhase?.artifacts || dataSafetyPlan.expectedArtifacts),
      ],
      blockingInputs: dataSafetyOwnerPlan.nextPhase?.requiredInputs || [],
      sourcePlan: "data-safety-submission-plan.json",
    },
    {
      order: 5,
      lane: "final-review",
      owner: "release-infra",
      status: rollup.status === "PASS" && rollup.cutoverAllowed === true ? "READY" : "BLOCKED",
      title: "Regenerate readiness and enforce final cutover gates",
      blocker: rollup.status === "PASS" && rollup.cutoverAllowed === true ? null : `${rollup.finalRecommendation}; blocked=${rollup.blockedCount}/${rollup.items.length}`,
      command: "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
      followUpCommands: closurePlan.verificationCommands,
      artifacts: [
        "artifacts/ddd/release/staging-handoff-bundle/final-review.json",
        "artifacts/ddd/release/release-final-go-no-go.json",
      ],
      blockingInputs: closurePlan.topBlockingInputs.map((input) => input.input),
      sourcePlan: "final-review.json",
    },
  ];
  const enrichedQueue = queue.map((item) => {
    const dispatchOwner = dispatchOwnerForRawOwner(item.owner);
    const missingEvidenceArtifacts = missingEvidenceArtifactsForOwner(dispatchOwner, rollup);
    const missingEvidenceArtifactsByPath = new Map();
    for (const artifact of missingEvidenceArtifacts) {
      const current = missingEvidenceArtifactsByPath.get(artifact.artifact) || {
        artifact: artifact.artifact,
        gates: [],
        acceptanceCommands: [],
        evidenceCommands: [],
      };
      current.gates = unique([...current.gates, ...(artifact.gates || [])]);
      current.acceptanceCommands = unique([...current.acceptanceCommands, ...(artifact.acceptanceCommands || [])]);
      current.evidenceCommands = unique([...current.evidenceCommands, ...(artifact.evidenceCommands || [])]);
      missingEvidenceArtifactsByPath.set(artifact.artifact, current);
    }
    const uniqueMissingEvidenceArtifacts = [...missingEvidenceArtifactsByPath.values()];
    const artifactPlanCommands = item.lane === "p1-p2-data-safety" && missingEvidenceArtifacts.some((artifact) => artifact.artifact === "tmp/ddd-explain/*.json")
      ? [
        "node scripts/ddd-staging-execution-checklist.mjs --explain-artifact-plan-markdown",
        ...explainArtifactPlan.commands,
      ]
      : [];
    return {
      ...item,
      dispatchOwner,
      missingEvidenceArtifacts: uniqueMissingEvidenceArtifacts,
      missingEvidenceArtifactCount: uniqueMissingEvidenceArtifacts.length,
      artifactPlanCommands: unique(artifactPlanCommands),
    };
  });
  const actionable = enrichedQueue.filter((item) => item.status !== "PASS" && item.status !== "READY");
  return {
    status: actionable.length === 0 ? "READY" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: rollup.finalRecommendation,
    cutoverAllowed: rollup.cutoverAllowed,
    queue: enrichedQueue,
    actionableCount: actionable.length,
    immediateActions: enrichedQueue.filter((item) => item.order <= 4 && item.status !== "PASS"),
    parallelNow: enrichedQueue.filter((item) => ["p0-release-env", "p0-docker-images", "p1-runtime-business", "p1-p2-data-safety"].includes(item.lane) && item.status !== "PASS").map((item) => item.lane),
    nextAction: actionable[0] || enrichedQueue.at(-1),
    nextCommand: actionable[0]?.command || "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
    verificationCommands: closurePlan.verificationCommands,
    safety: [
      "Treat this as the operator queue; use each source plan for full owner details.",
      "Do not paste populated secrets into queue artifacts or Markdown summaries.",
      "Run final review only after all lane source plans report accepted evidence.",
    ],
  };
}

function renderNextActionQueueMarkdown(report) {
  const lines = [
    "# DDD Staging Next Action Queue",
    "",
    `Status: ${report.status}`,
    `Final recommendation: ${report.finalRecommendation}`,
    `Cutover allowed: ${report.cutoverAllowed}`,
    `Actionable items: ${report.actionableCount}`,
    `Next command: \`${report.nextCommand}\``,
    "",
    "## Queue",
    "",
    "| Order | Lane | Owner | Dispatch owner | Missing artifacts | Artifact commands | Status | Action | Command | Source |",
    "| ---: | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ...report.queue.map((item) => [
      item.order,
      `\`${item.lane}\``,
      item.owner,
      item.dispatchOwner || item.owner,
      item.missingEvidenceArtifacts?.length > 0
        ? item.missingEvidenceArtifacts.map((artifact) => `\`${artifact.artifact}\``).join("<br>")
        : "none",
      item.artifactPlanCommands?.length > 0
        ? item.artifactPlanCommands.map((command) => `\`${command}\``).join("<br>")
        : "none",
      item.status,
      item.title,
      `\`${item.command || "none"}\``,
      `\`${item.sourcePlan}\``,
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Parallel Now",
    "",
    ...(report.parallelNow.length > 0 ? report.parallelNow.map((lane) => `- \`${lane}\``) : ["- none"]),
    "",
    "## Verification",
    "",
    ...report.verificationCommands.map((command) => `- \`${command}\``),
    "",
    "## Safety",
    "",
    ...report.safety.map((item) => `- ${item}`),
    "",
  ];
  return lines.join("\n");
}

function buildOwnerLaneMatrix({ queueOverride = null, ownerDispatchOverride = null, rollupOverride = null } = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const queue = queueOverride || buildNextActionQueue({ rollupOverride: rollup });
  const acceptance = buildEvidenceAcceptance({ rollupOverride: rollup });
  const ownerDispatch = ownerDispatchOverride || null;
  const dispatchOwners = Array.isArray(ownerDispatch?.owners)
    ? ownerDispatch.owners
    : selectedOwnerPackets.map((owner) => ({
      owner: owner.owner,
      evidenceGapCount: evidenceGapsForOwner(owner.owner).length,
      missingEvidenceArtifactCount: missingEvidenceArtifactsForOwner(owner.owner, rollup).length,
      blockingInputCount: blockingInputGatesForOwner(owner.owner, rollup).reduce((count, gate) => count + (gate.blockingInputs || []).length, 0),
      nextCommand: owner.nextCommand || null,
    }));
  const laneRows = dispatchOwners.map((owner) => {
    const lanes = (queue.queue || []).filter((lane) => lane.dispatchOwner === owner.owner || lane.owner === owner.owner);
    return {
      owner: owner.owner,
      laneCount: lanes.length,
      lanes: lanes.map((lane) => ({
        order: lane.order,
        lane: lane.lane,
        rawOwner: lane.owner,
        status: lane.status,
        title: lane.title,
        command: lane.command || null,
        acceptanceCommands: acceptanceCommandsForLane(lane, acceptance),
        sourcePlan: lane.sourcePlan || null,
        expectedArtifacts: artifactsForLane(lane, acceptance),
        missingArtifacts: missingArtifactsForLane(lane, acceptance),
        missingEvidenceArtifactCount: lane.missingEvidenceArtifactCount ?? 0,
      })),
      blockingInputCount: owner.blockingInputCount ?? 0,
      evidenceGapCount: owner.evidenceGapCount ?? 0,
      missingEvidenceArtifactCount: owner.missingEvidenceArtifactCount ?? 0,
      nextCommand: lanes[0]?.command || owner.nextCommand || null,
    };
  });
  return {
    status: queue.status,
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: queue.finalRecommendation,
    cutoverAllowed: queue.cutoverAllowed,
    ownerCount: laneRows.length,
    actionableOwnerCount: laneRows.filter((owner) => owner.laneCount > 0).length,
    owners: laneRows,
    nextOwner: laneRows.find((owner) => owner.laneCount > 0) || laneRows[0] || null,
    sourceQueue: "next-action-queue.json",
    sourceDispatch: "owner-dispatch.json",
  };
}

function acceptanceItemsForLane(lane, acceptance) {
  return (acceptance.items || []).filter((item) => {
    if (lane.lane === "p0-release-env") return item.track === "p0-release-env";
    if (lane.lane === "p0-docker-images") return item.track === "p0-images";
    if (lane.lane === "p1-runtime-business") return item.track === "p1-runtime-business";
    if (lane.lane === "p1-p2-data-safety") return ["p1-rollback", "p2-database-performance"].includes(item.track);
    if (lane.lane === "final-review") return true;
    return item.track === lane.lane;
  });
}

function acceptanceCommandsForLane(lane, acceptance) {
  return unique(acceptanceItemsForLane(lane, acceptance).map((item) => item.acceptanceCommand).filter(Boolean));
}

function artifactsForLane(lane, acceptance) {
  return unique(acceptanceItemsForLane(lane, acceptance)
    .flatMap((item) => (item.artifactChecks || []).map((artifact) => artifact.artifact)));
}

function missingArtifactsForLane(lane, acceptance) {
  return unique(acceptanceItemsForLane(lane, acceptance)
    .flatMap((item) => (item.artifactChecks || [])
      .filter((artifact) => artifact.present === false)
      .map((artifact) => artifact.artifact)));
}

function renderOwnerLaneMatrixMarkdown(matrix) {
  const lines = [
    "# DDD Owner Lane Matrix",
    "",
    `Status: ${matrix.status}`,
    `Final recommendation: ${matrix.finalRecommendation}`,
    `Cutover allowed: ${matrix.cutoverAllowed}`,
    `Owners with lanes: ${matrix.actionableOwnerCount}/${matrix.ownerCount}`,
    "",
    "| Owner | Lanes | Blocking inputs | Evidence gaps | Missing artifacts | Next command |",
    "| --- | ---: | ---: | ---: | ---: | --- |",
    ...matrix.owners.map((owner) => [
      owner.owner,
      owner.laneCount,
      owner.blockingInputCount,
      owner.evidenceGapCount,
      owner.missingEvidenceArtifactCount,
      owner.nextCommand ? `\`${owner.nextCommand}\`` : "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Lane Details",
    "",
    ...matrix.owners.flatMap((owner) => [
      `### ${owner.owner}`,
      "",
      ...(owner.lanes.length > 0
        ? owner.lanes.map((lane) => `- ${lane.order}. \`${lane.lane}\`: status=${lane.status}; missingArtifacts=${lane.missingEvidenceArtifactCount}; accept=${lane.acceptanceCommands.map((command) => `\`${command}\``).join(", ") || "none"}; next=\`${lane.command || "none"}\`; source=\`${lane.sourcePlan || "none"}\``)
        : ["- none"]),
      "",
    ]),
  ];
  return lines.join("\n");
}

function runOwnerLaneMatrix({ markdown = false } = {}) {
  let matrix;
  try {
    matrix = buildOwnerLaneMatrix();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderOwnerLaneMatrixMarkdown(matrix));
  } else {
    console.log(JSON.stringify(matrix, null, 2));
  }
  process.exit(0);
}

if (ownerLaneMatrixOnly) {
  runOwnerLaneMatrix();
}

if (ownerLaneMatrixMarkdownOnly) {
  runOwnerLaneMatrix({ markdown: true });
}

function buildLaneCompletionReceiptTemplate({ matrixOverride = null } = {}) {
  const matrix = matrixOverride || buildOwnerLaneMatrix();
  const laneReceipts = (matrix.owners || []).flatMap((owner) => (owner.lanes || []).map((lane) => ({
    lane: lane.lane,
    owner: owner.owner,
    rawOwner: lane.rawOwner,
    status: "BLOCKED",
    acceptanceCommands: lane.acceptanceCommands || [],
    expectedArtifacts: lane.expectedArtifacts || [],
    providedArtifacts: [],
    missingArtifacts: lane.missingArtifacts || [],
    evidenceNotes: [],
    completedAt: null,
    completedBy: null,
  })));
  return {
    status: "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    redacted: true,
    laneReceiptCount: laneReceipts.length,
    laneReceipts,
    passCriteria: [
      "redacted must be true",
      "receipt must not include sensitive values or URLs",
      "each completed lane must set status PASS and include providedArtifacts",
      "each PASS lane must include completedAt and completedBy",
      "each owner:lane key must appear at most once",
      "run each lane acceptanceCommands before marking PASS",
      "run node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>",
    ],
    submissionFlow: [
      laneCompletionReceiptAutofillCommand,
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
    ],
  };
}

function renderLaneCompletionReceiptMarkdown(receipt) {
  const lines = [
    "# DDD Lane Completion Receipt",
    "",
    `Status: ${receipt.status}`,
    `Redacted: ${receipt.redacted}`,
    `Lane receipts: ${receipt.laneReceiptCount}`,
    "",
    "| Lane | Owner | Status | Provided artifacts | Missing artifacts | Completed at | Completed by | Acceptance commands |",
    "| --- | --- | --- | ---: | ---: | --- | --- | --- |",
    ...receipt.laneReceipts.map((lane) => [
      `\`${lane.lane}\``,
      lane.owner,
      lane.status,
      (lane.providedArtifacts || []).length,
      (lane.missingArtifacts || []).length,
      lane.completedAt || "required when PASS",
      lane.completedBy || "required when PASS",
      (lane.acceptanceCommands || []).map((command) => `\`${command}\``).join("<br>") || "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Fill Rules",
    "",
    "- Keep `redacted=true` and do not paste secrets, tokens, passwords, or private URLs into the receipt.",
    "- Leave a lane `BLOCKED` until its acceptance commands pass.",
    "- To mark a lane `PASS`, copy its expected evidence paths into `providedArtifacts`, clear `missingArtifacts`, and set `completedAt` plus `completedBy`.",
    "- A full release receipt must cover every owner:lane row exactly once and pass both contract and coverage commands.",
    "",
    "## Edit Checklist",
    "",
    "- Edit only the redacted receipt JSON created by `--lane-completion-receipt-init` or `--lane-completion-receipt-template`.",
    "- Keep top-level `redacted` set to `true` for the whole receipt.",
    "- Keep top-level `status` as `BLOCKED` until every lane row is ready for `PASS`.",
    "- For each lane row, update `status`, `providedArtifacts`, `missingArtifacts`, `completedAt`, and `completedBy`; keep `owner` and `lane` unchanged.",
    "- Run the submission check before generating base64 for workflow dispatch.",
    "",
    "| Lane key | JSON row | Fields to update before PASS | Keep BLOCKED while |",
    "| --- | ---: | --- | --- |",
    ...receipt.laneReceipts.map((lane, index) => [
      `\`${lane.owner}:${lane.lane}\``,
      `laneReceipts[${index}]`,
      "`status`, `providedArtifacts`, `missingArtifacts`, `completedAt`, `completedBy`",
      (lane.missingArtifacts || []).length > 0
        ? (lane.missingArtifacts || []).map((artifact) => `\`${artifact}\``).join("<br>")
        : "acceptance commands have not passed",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Lane Details",
    "",
    ...receipt.laneReceipts.flatMap((lane) => [
      `### ${lane.owner}:${lane.lane}`,
      "",
      `Acceptance commands: ${(lane.acceptanceCommands || []).length > 0 ? (lane.acceptanceCommands || []).map((command) => `\`${command}\``).join(", ") : "none"}`,
      `Expected artifacts: ${(lane.expectedArtifacts || []).length > 0 ? (lane.expectedArtifacts || []).map((artifact) => `\`${artifact}\``).join(", ") : "none"}`,
      `Currently missing artifacts: ${(lane.missingArtifacts || []).length > 0 ? (lane.missingArtifacts || []).map((artifact) => `\`${artifact}\``).join(", ") : "none"}`,
      "",
    ]),
    "",
    "## Pass Criteria",
    "",
    ...receipt.passCriteria.map((item) => `- ${item}`),
    "",
    "## Submission Flow",
    "",
    ...receipt.submissionFlow.map((command) => `- \`${command}\``),
    "",
  ];
  return lines.join("\n");
}

function readLaneCompletionReceiptInput(file = laneCompletionReceiptFile) {
  if (!file) return { receipt: null, text: "", issues: ["DDD_LANE_COMPLETION_RECEIPT_FILE or --lane-completion-receipt-file is required"] };
  const resolved = path.resolve(repoRoot, file);
  if (!fs.existsSync(resolved)) return { receipt: null, text: "", issues: [`receipt file does not exist: ${portableEnvFile(file)}`] };
  const text = fs.readFileSync(resolved, "utf8");
  try {
    return { receipt: JSON.parse(text), text, issues: [] };
  } catch (error) {
    return { receipt: null, text, issues: [`receipt file must be valid JSON: ${error.message}`] };
  }
}

function buildLaneCompletionReceiptContract({ receiptFile = laneCompletionReceiptFile } = {}) {
  const { receipt, text, issues } = readLaneCompletionReceiptInput(receiptFile);
  const duplicateLaneKeys = [];
  const passLaneKeysMissingAudit = [];
  let passLaneCount = 0;
  let blockedLaneCount = 0;
  if (receipt) {
    if (receipt.redacted !== true) issues.push("receipt.redacted must be true");
    if (!["PASS", "BLOCKED"].includes(receipt.status)) issues.push("receipt.status must be PASS or BLOCKED");
    if (!Array.isArray(receipt.laneReceipts) || receipt.laneReceipts.length === 0) {
      issues.push("receipt.laneReceipts must be a non-empty array");
    }
    const seenLaneKeys = new Set();
    for (const [index, lane] of (receipt.laneReceipts || []).entries()) {
      if (!lane.lane) issues.push(`laneReceipts[${index}].lane is required`);
      if (!lane.owner) issues.push(`laneReceipts[${index}].owner is required`);
      const laneKey = `${lane.owner || "<missing-owner>"}:${lane.lane || "<missing-lane>"}`;
      if (seenLaneKeys.has(laneKey)) {
        duplicateLaneKeys.push(laneKey);
        issues.push(`laneReceipts[${index}] duplicate owner:lane ${laneKey}`);
      }
      seenLaneKeys.add(laneKey);
      if (!["PASS", "BLOCKED"].includes(lane.status)) issues.push(`laneReceipts[${index}].status must be PASS or BLOCKED`);
      if (lane.status === "PASS") passLaneCount += 1;
      if (lane.status === "BLOCKED") blockedLaneCount += 1;
      if (!Array.isArray(lane.acceptanceCommands)) issues.push(`laneReceipts[${index}].acceptanceCommands must be an array`);
      if (!Array.isArray(lane.expectedArtifacts)) issues.push(`laneReceipts[${index}].expectedArtifacts must be an array`);
      if (!Array.isArray(lane.providedArtifacts)) issues.push(`laneReceipts[${index}].providedArtifacts must be an array`);
      if (!Array.isArray(lane.missingArtifacts)) issues.push(`laneReceipts[${index}].missingArtifacts must be an array`);
      if (lane.status === "PASS" && (!Array.isArray(lane.providedArtifacts) || lane.providedArtifacts.length === 0)) {
        issues.push(`laneReceipts[${index}] PASS requires providedArtifacts`);
      }
      if (lane.status === "PASS" && Array.isArray(lane.missingArtifacts) && lane.missingArtifacts.length > 0) {
        issues.push(`laneReceipts[${index}] PASS requires missingArtifacts to be empty`);
      }
      if (lane.status === "PASS" && !lane.completedBy) {
        passLaneKeysMissingAudit.push(laneKey);
        issues.push(`laneReceipts[${index}] PASS requires completedBy`);
      }
      if (lane.status === "PASS" && !lane.completedAt) {
        passLaneKeysMissingAudit.push(laneKey);
        issues.push(`laneReceipts[${index}] PASS requires completedAt`);
      }
      if (lane.status === "PASS" && lane.completedAt && Number.isNaN(Date.parse(lane.completedAt))) {
        passLaneKeysMissingAudit.push(laneKey);
        issues.push(`laneReceipts[${index}] completedAt must be an ISO timestamp`);
      }
    }
    if (receipt.status === "PASS" && (receipt.laneReceipts || []).some((lane) => lane.status !== "PASS")) {
      issues.push("PASS receipt requires every lane receipt to PASS");
    }
  }
  if (/https?:\/\//i.test(text)) issues.push("receipt text must not include URLs");
  if (/(^|\n)\s*[A-Z_][A-Z0-9_]*\s*=\s*\S+/.test(text)) issues.push("receipt text must not include standalone env assignments with values");
  if (/\b(TOKEN|SECRET|PASSWORD|API_KEY)\b/i.test(text)) issues.push("receipt text must not include secret-like key names");
  return {
    status: issues.length === 0 ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    receiptFile: portableEnvFile(receiptFile),
    receiptStatus: receipt?.status || null,
    redacted: receipt?.redacted === true,
    laneCount: Array.isArray(receipt?.laneReceipts) ? receipt.laneReceipts.length : 0,
    summary: {
      passLaneCount,
      blockedLaneCount,
      duplicateLaneKeys: unique(duplicateLaneKeys),
      passLaneKeysMissingAudit: unique(passLaneKeysMissingAudit),
    },
    lanes: Array.isArray(receipt?.laneReceipts)
      ? receipt.laneReceipts.map((lane) => ({
        lane: lane.lane || null,
        owner: lane.owner || null,
        status: lane.status || null,
      }))
      : [],
    issueCount: issues.length,
    issues,
  };
}

function buildLaneReceiptCoverage({ laneReceiptContract = null, rollupOverride = null } = {}) {
  const matrix = buildOwnerLaneMatrix({ rollupOverride });
  const expected = matrix.owners.flatMap((owner) => owner.lanes.map((lane) => ({
    key: `${owner.owner}:${lane.lane}`,
    owner: owner.owner,
    lane: lane.lane,
  })));
  const provided = (laneReceiptContract?.lanes || []).map((lane) => ({
    key: `${lane.owner}:${lane.lane}`,
    owner: lane.owner,
    lane: lane.lane,
    status: lane.status,
  }));
  const providedKeys = new Set(provided.map((lane) => lane.key));
  const expectedKeys = new Set(expected.map((lane) => lane.key));
  const missing = expected.filter((lane) => !providedKeys.has(lane.key));
  const unexpected = provided.filter((lane) => !expectedKeys.has(lane.key));
  return {
    status: laneReceiptContract && laneReceiptContract.receiptStatus === "PASS" && missing.length === 0 && unexpected.length === 0
      ? "PASS"
      : "BLOCKED",
    expectedLaneCount: expected.length,
    coveredLaneCount: expected.length - missing.length,
    providedLaneCount: provided.length,
    missingLanes: missing,
    unexpectedLanes: unexpected,
  };
}

function laneReceiptCoverageBlocker(coverage) {
  if (!coverage) return "coverage unavailable";
  if (coverage.missingLanes?.length > 0) return `missing lanes=${coverage.missingLanes.map((lane) => lane.key).join(", ")}`;
  if (coverage.unexpectedLanes?.length > 0) return `unexpected lanes=${coverage.unexpectedLanes.map((lane) => lane.key).join(", ")}`;
  return null;
}

function buildLaneReceiptCoverageReport({ receiptFile = laneCompletionReceiptFile, rollupOverride = null } = {}) {
  const contract = receiptFile ? buildLaneCompletionReceiptContract({ receiptFile }) : null;
  const coverage = buildLaneReceiptCoverage({ laneReceiptContract: contract, rollupOverride });
  const issues = [
    ...(contract?.issues || (!receiptFile ? ["DDD_LANE_COMPLETION_RECEIPT_FILE or --lane-completion-receipt-file is required"] : [])),
    ...(coverage.status === "PASS" ? [] : [laneReceiptCoverageBlocker(coverage) || `receiptStatus=${contract?.receiptStatus || "missing"}`]),
  ].filter(Boolean);
  return {
    status: contract?.status === "PASS" && coverage.status === "PASS" ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    receiptFile: contract?.receiptFile || portableEnvFile(receiptFile),
    receiptStatus: contract?.receiptStatus || null,
    contractStatus: contract?.status || "BLOCKED",
    contractSummary: contract?.summary || {
      passLaneCount: 0,
      blockedLaneCount: 0,
      duplicateLaneKeys: [],
      passLaneKeysMissingAudit: [],
    },
    coverage,
    issueCount: issues.length,
    issues,
  };
}

function renderLaneReceiptCoverageMarkdown(report) {
  const lines = [
    "# DDD Lane Completion Receipt Coverage",
    "",
    `Status: ${report.status}`,
    `Receipt file: ${report.receiptFile ? `\`${report.receiptFile}\`` : "not provided"}`,
    `Receipt status: ${report.receiptStatus || "missing"}`,
    `Contract status: ${report.contractStatus}`,
    `Coverage: ${report.coverage.coveredLaneCount}/${report.coverage.expectedLaneCount}`,
    `Provided lanes: ${report.coverage.providedLaneCount}`,
    "",
    "## Contract Summary",
    "",
    `PASS lanes: ${report.contractSummary.passLaneCount}`,
    `BLOCKED lanes: ${report.contractSummary.blockedLaneCount}`,
    `Duplicate owner:lane keys: ${report.contractSummary.duplicateLaneKeys.length > 0 ? report.contractSummary.duplicateLaneKeys.map((key) => `\`${key}\``).join(", ") : "none"}`,
    `PASS lanes missing audit fields: ${report.contractSummary.passLaneKeysMissingAudit.length > 0 ? report.contractSummary.passLaneKeysMissingAudit.map((key) => `\`${key}\``).join(", ") : "none"}`,
    "",
    "## Missing Lanes",
    "",
    ...(report.coverage.missingLanes.length > 0
      ? report.coverage.missingLanes.map((lane) => `- \`${lane.key}\``)
      : ["- none"]),
    "",
    "## Unexpected Lanes",
    "",
    ...(report.coverage.unexpectedLanes.length > 0
      ? report.coverage.unexpectedLanes.map((lane) => `- \`${lane.key}\``)
      : ["- none"]),
    "",
    "## Issues",
    "",
    ...(report.issues.length > 0 ? report.issues.map((issue) => `- ${issue}`) : ["- none"]),
    "",
  ];
  return lines.join("\n");
}

function buildEvidenceClosureBoard({ receiptFile = laneCompletionReceiptFile, rollupOverride = null } = {}) {
  const matrix = buildOwnerLaneMatrix({ rollupOverride });
  const contract = receiptFile ? buildLaneCompletionReceiptContract({ receiptFile }) : null;
  const coverage = buildLaneReceiptCoverage({ laneReceiptContract: contract, rollupOverride });
  const receiptInput = receiptFile ? readLaneCompletionReceiptInput(receiptFile) : { receipt: null, issues: [] };
  const receiptLanes = Array.isArray(receiptInput.receipt?.laneReceipts) ? receiptInput.receipt.laneReceipts : [];
  const receiptByKey = new Map(receiptLanes.map((lane) => [`${lane.owner}:${lane.lane}`, lane]));
  const lanes = matrix.owners.flatMap((owner) => owner.lanes.map((lane) => {
    const key = `${owner.owner}:${lane.lane}`;
    const receiptLane = receiptByKey.get(key) || null;
    const receiptStatus = receiptLane?.status || "MISSING";
    const providedArtifacts = Array.isArray(receiptLane?.providedArtifacts) ? receiptLane.providedArtifacts : [];
    const missingArtifacts = receiptStatus === "PASS"
      ? []
      : unique([...(lane.missingArtifacts || []), ...((receiptLane?.missingArtifacts || []))]);
    const accepted = receiptStatus === "PASS" && missingArtifacts.length === 0 && providedArtifacts.length > 0;
    return {
      key,
      owner: owner.owner,
      lane: lane.lane,
      status: accepted ? "PASS" : "BLOCKED",
      receiptStatus,
      sourcePlan: lane.sourcePlan || null,
      nextCommand: accepted ? null : lane.command || null,
      acceptanceCommands: lane.acceptanceCommands || [],
      expectedArtifacts: lane.expectedArtifacts || [],
      providedArtifacts,
      missingArtifacts,
      missingEvidenceArtifactCount: lane.missingEvidenceArtifactCount ?? 0,
    };
  }));
  const openLanes = lanes.filter((lane) => lane.status !== "PASS");
  const issues = [
    ...(contract?.issues || (receiptFile ? receiptInput.issues : ["lane completion receipt file not provided"])),
    ...(coverage.status === "PASS" ? [] : [laneReceiptCoverageBlocker(coverage) || `receiptStatus=${contract?.receiptStatus || "missing"}`]),
  ].filter(Boolean);
  return {
    status: openLanes.length === 0 && contract?.status === "PASS" && coverage.status === "PASS" ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    receiptFile: contract?.receiptFile || portableEnvFile(receiptFile),
    receiptStatus: contract?.receiptStatus || null,
    contractStatus: contract?.status || "BLOCKED",
    coverage,
    laneCount: lanes.length,
    closedLaneCount: lanes.length - openLanes.length,
    openLaneCount: openLanes.length,
    lanes,
    nextLane: openLanes[0] || null,
    issueCount: issues.length,
    issues,
    nextCommand: openLanes[0]?.nextCommand
      || "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
  };
}

function renderEvidenceClosureBoardMarkdown(board) {
  const lines = [
    "# DDD Evidence Closure Board",
    "",
    `Status: ${board.status}`,
    `Receipt file: ${board.receiptFile ? `\`${board.receiptFile}\`` : "not provided"}`,
    `Receipt status: ${board.receiptStatus || "missing"}`,
    `Contract status: ${board.contractStatus}`,
    `Coverage: ${board.coverage.coveredLaneCount}/${board.coverage.expectedLaneCount}`,
    `Closed lanes: ${board.closedLaneCount}/${board.laneCount}`,
    `Next command: \`${board.nextCommand || "none"}\``,
    "",
    "## Lanes",
    "",
    "| Key | Status | Receipt | Missing artifacts | Provided artifacts | Acceptance commands | Next command | Source |",
    "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ...board.lanes.map((lane) => [
      `\`${lane.key}\``,
      lane.status,
      lane.receiptStatus,
      lane.missingArtifacts.length > 0 ? lane.missingArtifacts.map((artifact) => `\`${artifact}\``).join("<br>") : "none",
      lane.providedArtifacts.length > 0 ? lane.providedArtifacts.map((artifact) => `\`${artifact}\``).join("<br>") : "none",
      lane.acceptanceCommands.length > 0 ? lane.acceptanceCommands.map((command) => `\`${command}\``).join("<br>") : "none",
      lane.nextCommand ? `\`${lane.nextCommand}\`` : "none",
      lane.sourcePlan ? `\`${lane.sourcePlan}\`` : "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Missing Lanes",
    "",
    ...(board.coverage.missingLanes.length > 0
      ? board.coverage.missingLanes.map((lane) => `- \`${lane.key}\``)
      : ["- none"]),
    "",
    "## Issues",
    "",
    ...(board.issues.length > 0 ? board.issues.map((issue) => `- ${issue}`) : ["- none"]),
    "",
  ];
  return lines.join("\n");
}

function csvCell(value) {
  const text = Array.isArray(value) ? value.join("; ") : String(value ?? "");
  return `"${text.replaceAll('"', '""')}"`;
}

function renderEvidenceClosureBoardCsv(board) {
  const columns = [
    "key",
    "owner",
    "lane",
    "status",
    "receiptStatus",
    "sourcePlan",
    "nextCommand",
    "acceptanceCommands",
    "expectedArtifacts",
    "providedArtifacts",
    "missingArtifacts",
    "missingEvidenceArtifactCount",
  ];
  const rows = board.lanes.map((lane) => [
    lane.key,
    lane.owner,
    lane.lane,
    lane.status,
    lane.receiptStatus,
    lane.sourcePlan,
    lane.nextCommand,
    lane.acceptanceCommands,
    lane.expectedArtifacts,
    lane.providedArtifacts,
    lane.missingArtifacts,
    lane.missingEvidenceArtifactCount,
  ]);
  return [
    columns.map(csvCell).join(","),
    ...rows.map((row) => row.map(csvCell).join(",")),
    "",
  ].join("\n");
}

function buildLaneCompletionReceiptBase64({ receiptFile = laneCompletionReceiptFile } = {}) {
  const report = buildLaneReceiptCoverageReport({ receiptFile });
  if (report.status !== "PASS") {
    return {
      status: "BLOCKED",
      generatedAt,
      willWriteFiles: false,
      receiptFile: report.receiptFile,
      coverage: report.coverage,
      issueCount: report.issues.length,
      issues: report.issues,
      base64: null,
    };
  }
  const resolved = path.resolve(repoRoot, receiptFile);
  const base64 = fs.readFileSync(resolved).toString("base64");
  return {
    status: "PASS",
    generatedAt,
    willWriteFiles: false,
    receiptFile: portableEnvFile(receiptFile),
    coverage: report.coverage,
    issueCount: 0,
    issues: [],
    base64,
  };
}

function buildLaneCompletionSubmissionPlan({ rollupOverride = null } = {}) {
  const matrix = buildOwnerLaneMatrix({ rollupOverride });
  const template = buildLaneCompletionReceiptTemplate({ matrixOverride: matrix });
  const coverage = buildLaneReceiptCoverageReport({ receiptFile: "", rollupOverride });
  const lanes = template.laneReceipts.map((lane) => ({
    key: `${lane.owner}:${lane.lane}`,
    lane: lane.lane,
    owner: lane.owner,
    rawOwner: lane.rawOwner,
    status: lane.status,
    acceptanceCommands: lane.acceptanceCommands,
    expectedArtifacts: lane.expectedArtifacts,
    missingArtifacts: lane.missingArtifacts,
  }));
  return {
    status: "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    redacted: true,
    laneCount: lanes.length,
    lanes,
    workflowInput: {
      workflow: ".github/workflows/ddd-release-evidence.yml",
      fileInput: "lane_completion_receipt_file",
      base64Input: "lane_completion_receipt_base64",
      decodedPath: "artifacts/ddd/release/lane-completion-receipt.submitted.json",
      base64TakesPrecedence: true,
    },
    commands: [
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>",
      laneCompletionReceiptAutofillCommand,
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template",
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
    ],
    passCriteria: [
      "receipt.redacted must be true",
      "receipt.status must be PASS",
      "every lane receipt must be PASS",
      "every PASS lane must include providedArtifacts and empty missingArtifacts",
      "every PASS lane must include completedAt and completedBy",
      "every owner:lane key must be unique",
      "coverage must show Coverage: 5/5",
      "base64 generation must succeed before using lane_completion_receipt_base64",
      "final review must pass with the submitted receipt file",
    ],
    currentCoverage: {
      status: coverage.status,
      coveredLaneCount: coverage.coverage.coveredLaneCount,
      expectedLaneCount: coverage.coverage.expectedLaneCount,
      missingLanes: coverage.coverage.missingLanes,
      issues: coverage.issues,
    },
    nextCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>",
  };
}

function renderLaneCompletionSubmissionPlanMarkdown(plan) {
  const lines = [
    "# DDD Lane Completion Submission Plan",
    "",
    `Status: ${plan.status}`,
    `Redacted: ${plan.redacted}`,
    `Lanes: ${plan.laneCount}`,
    `Current coverage: ${plan.currentCoverage.coveredLaneCount}/${plan.currentCoverage.expectedLaneCount}`,
    `Workflow: \`${plan.workflowInput.workflow}\``,
    `Workflow file input: \`${plan.workflowInput.fileInput}\``,
    `Workflow base64 input: \`${plan.workflowInput.base64Input}\``,
    `Decoded path: \`${plan.workflowInput.decodedPath}\``,
    "",
    "## Lanes",
    "",
    "| Key | Acceptance commands | Expected artifacts | Missing artifacts |",
    "| --- | --- | --- | --- |",
    ...plan.lanes.map((lane) => [
      `\`${lane.key}\``,
      lane.acceptanceCommands.map((command) => `\`${command}\``).join("<br>") || "none",
      lane.expectedArtifacts.map((artifact) => `\`${artifact}\``).join("<br>") || "none",
      lane.missingArtifacts.map((artifact) => `\`${artifact}\``).join("<br>") || "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Commands",
    "",
    ...plan.commands.map((command) => `- \`${command}\``),
    "",
    "## Pass Criteria",
    "",
    ...plan.passCriteria.map((item) => `- ${item}`),
    "",
    "## Current Missing Lanes",
    "",
    ...(plan.currentCoverage.missingLanes.length > 0
      ? plan.currentCoverage.missingLanes.map((lane) => `- \`${lane.key}\``)
      : ["- none"]),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function buildLaneCompletionSubmissionCheck({ receiptFile = laneCompletionReceiptFile, rollupOverride = null } = {}) {
  const plan = buildLaneCompletionSubmissionPlan({ rollupOverride });
  const contract = receiptFile ? buildLaneCompletionReceiptContract({ receiptFile }) : null;
  const coverageReport = buildLaneReceiptCoverageReport({ receiptFile: receiptFile || "", rollupOverride });
  const base64Report = receiptFile ? buildLaneCompletionReceiptBase64({ receiptFile }) : null;
  const canSubmitFile = Boolean(receiptFile)
    && contract?.status === "PASS"
    && contract?.receiptStatus === "PASS"
    && coverageReport.status === "PASS";
  const canSubmitBase64 = canSubmitFile && base64Report?.status === "PASS";
  const issues = [
    ...(receiptFile ? [] : ["lane completion receipt file not provided"]),
    ...(contract?.issues || []),
    ...(coverageReport.issues || []),
    ...(base64Report?.issues || []),
  ];
  return {
    status: canSubmitFile && canSubmitBase64 ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    redacted: contract?.redacted === true,
    receiptFile: receiptFile ? portableEnvFile(receiptFile) : null,
    workflowInput: plan.workflowInput,
    contract: contract
      ? {
        status: contract.status,
        receiptStatus: contract.receiptStatus,
        laneCount: contract.laneCount,
        issueCount: contract.issueCount,
      }
      : {
        status: "MISSING",
        receiptStatus: null,
        laneCount: 0,
        issueCount: 1,
      },
    coverage: {
      status: coverageReport.status,
      coveredLaneCount: coverageReport.coverage.coveredLaneCount,
      expectedLaneCount: coverageReport.coverage.expectedLaneCount,
      missingLanes: coverageReport.coverage.missingLanes,
    },
    base64: {
      status: base64Report?.status || "SKIPPED",
      ready: canSubmitBase64,
      command: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>",
      printsValue: true,
    },
    dispatch: {
      ready: canSubmitBase64,
      preferredInput: plan.workflowInput.base64Input,
      fallbackInput: plan.workflowInput.fileInput,
      decodedPath: plan.workflowInput.decodedPath,
      commandTemplate: "gh workflow run ddd-release-evidence.yml -f mode=run -f lane_completion_receipt_base64=<base64-value>",
    },
    issues: unique(issues),
    nextCommand: canSubmitBase64
      ? "gh workflow run ddd-release-evidence.yml -f mode=run -f lane_completion_receipt_base64=<base64-value>"
      : (receiptFile
        ? "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>"
        : "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>"),
  };
}

function renderLaneCompletionSubmissionCheckMarkdown(check) {
  const lines = [
    "# DDD Lane Completion Submission Check",
    "",
    `Status: ${check.status}`,
    `Receipt file: ${check.receiptFile ? `\`${check.receiptFile}\`` : "not provided"}`,
    `Redacted: ${check.redacted}`,
    `Contract: ${check.contract.status}`,
    `Receipt status: ${check.contract.receiptStatus || "not provided"}`,
    `Coverage: ${check.coverage.coveredLaneCount}/${check.coverage.expectedLaneCount}`,
    `Base64 ready: ${check.base64.ready}`,
    `Dispatch ready: ${check.dispatch.ready}`,
    `Preferred workflow input: \`${check.dispatch.preferredInput}\``,
    `Decoded workflow path: \`${check.dispatch.decodedPath}\``,
    "",
    "## Blocking Issues",
    "",
    ...(check.issues.length > 0 ? check.issues.map((issue) => `- ${issue}`) : ["- none"]),
    "",
    "## Missing Lanes",
    "",
    ...(check.coverage.missingLanes.length > 0
      ? check.coverage.missingLanes.map((lane) => `- \`${lane.key}\``)
      : ["- none"]),
    "",
    "## Submission Commands",
    "",
    `- Check coverage: \`node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>\``,
    `- Generate base64: \`${check.base64.command}\``,
    `- Dispatch: \`${check.dispatch.commandTemplate}\``,
    "",
    `Next: \`${check.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function buildLaneCompletionReceiptInit({ outputFile = laneCompletionReceiptOutput } = {}) {
  if (!outputFile) {
    return {
      status: "BLOCKED",
      generatedAt,
      willWriteFiles: false,
      outputFile: null,
      redacted: true,
      contract: null,
      issues: ["--lane-completion-receipt-output=<file> is required"],
      nextCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>",
    };
  }
  const resolved = path.resolve(repoRoot, outputFile);
  if (fs.existsSync(resolved)) {
    return {
      status: "BLOCKED",
      generatedAt,
      willWriteFiles: false,
      outputFile: portableEnvFile(outputFile),
      redacted: true,
      contract: null,
      issues: [`refusing to overwrite existing receipt file: ${portableEnvFile(outputFile)}`],
      nextCommand: "choose a new --lane-completion-receipt-output path or move the existing receipt file",
    };
  }
  const receipt = buildLaneCompletionReceiptTemplate();
  fs.mkdirSync(path.dirname(resolved), { recursive: true });
  fs.writeFileSync(resolved, `${JSON.stringify(receipt, null, 2)}\n`, { mode: 0o600 });
  try {
    fs.chmodSync(resolved, 0o600);
  } catch {
    // Windows may ignore POSIX file modes; the receipt is still redacted.
  }
  const contract = buildLaneCompletionReceiptContract({ receiptFile: outputFile });
  return {
    status: contract.status === "PASS" ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: true,
    outputFile: portableEnvFile(outputFile),
    redacted: receipt.redacted,
    laneCount: receipt.laneReceipts.length,
    contract: {
      status: contract.status,
      receiptStatus: contract.receiptStatus,
      issueCount: contract.issueCount,
      passLaneCount: contract.summary.passLaneCount,
      blockedLaneCount: contract.summary.blockedLaneCount,
    },
    issues: contract.issues,
    nextCommand: "edit the receipt, then run node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>",
  };
}

function runLaneCompletionReceiptTemplate({ markdown = false } = {}) {
  const receipt = buildLaneCompletionReceiptTemplate();
  if (markdown) {
    process.stdout.write(renderLaneCompletionReceiptMarkdown(receipt));
  } else {
    console.log(JSON.stringify(receipt, null, 2));
  }
  process.exit(0);
}

if (laneCompletionReceiptTemplateOnly) {
  runLaneCompletionReceiptTemplate();
}

if (laneCompletionReceiptTemplateMarkdownOnly) {
  runLaneCompletionReceiptTemplate({ markdown: true });
}

function runLaneCompletionReceiptInit() {
  const report = buildLaneCompletionReceiptInit();
  console.log(JSON.stringify(report, null, 2));
  process.exit(report.status === "PASS" ? 0 : 1);
}

if (laneCompletionReceiptInitOnly) {
  runLaneCompletionReceiptInit();
}

function runLaneCompletionSubmissionPlan({ markdown = false } = {}) {
  const plan = buildLaneCompletionSubmissionPlan();
  if (markdown) {
    process.stdout.write(renderLaneCompletionSubmissionPlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (laneCompletionSubmissionPlanOnly) {
  runLaneCompletionSubmissionPlan();
}

if (laneCompletionSubmissionPlanMarkdownOnly) {
  runLaneCompletionSubmissionPlan({ markdown: true });
}

function runLaneCompletionSubmissionCheck({ markdown = false } = {}) {
  const check = buildLaneCompletionSubmissionCheck();
  if (markdown) {
    process.stdout.write(renderLaneCompletionSubmissionCheckMarkdown(check));
  } else {
    console.log(JSON.stringify(check, null, 2));
  }
  process.exit(0);
}

if (laneCompletionSubmissionCheckOnly) {
  runLaneCompletionSubmissionCheck();
}

if (laneCompletionSubmissionCheckMarkdownOnly) {
  runLaneCompletionSubmissionCheck({ markdown: true });
}

function runEvidenceClosureBoard({ markdown = false, csv = false } = {}) {
  const board = buildEvidenceClosureBoard();
  if (csv) {
    process.stdout.write(renderEvidenceClosureBoardCsv(board));
  } else if (markdown) {
    process.stdout.write(renderEvidenceClosureBoardMarkdown(board));
  } else {
    console.log(JSON.stringify(board, null, 2));
  }
  process.exit(0);
}

if (evidenceClosureBoardOnly) {
  runEvidenceClosureBoard();
}

if (evidenceClosureBoardMarkdownOnly) {
  runEvidenceClosureBoard({ markdown: true });
}

if (evidenceClosureBoardCsvOnly) {
  runEvidenceClosureBoard({ csv: true });
}

if (laneCompletionReceiptContractOnly) {
  const contract = buildLaneCompletionReceiptContract();
  console.log(JSON.stringify(contract, null, 2));
  process.exit(contract.status === "PASS" ? 0 : 1);
}

if (laneCompletionReceiptCoverageOnly || laneCompletionReceiptCoverageMarkdownOnly) {
  const report = buildLaneReceiptCoverageReport();
  if (laneCompletionReceiptCoverageMarkdownOnly) {
    process.stdout.write(renderLaneReceiptCoverageMarkdown(report));
  } else {
    console.log(JSON.stringify(report, null, 2));
  }
  process.exit(report.status === "PASS" ? 0 : 1);
}

if (laneCompletionReceiptBase64Only) {
  const result = buildLaneCompletionReceiptBase64();
  if (result.status !== "PASS") {
    console.error(JSON.stringify({
      status: result.status,
      receiptFile: result.receiptFile,
      coverage: result.coverage,
      issueCount: result.issueCount,
      issues: result.issues,
    }, null, 2));
    process.exit(1);
  }
  process.stdout.write(`${result.base64}\n`);
  process.exit(0);
}

function runNextActionQueue({ markdown = false } = {}) {
  let report;
  try {
    report = buildNextActionQueue();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderNextActionQueueMarkdown(report));
  } else {
    console.log(JSON.stringify(report, null, 2));
  }
  process.exit(0);
}

if (nextActionQueueOnly) {
  runNextActionQueue();
}

if (nextActionQueueMarkdownOnly) {
  runNextActionQueue({ markdown: true });
}

function buildReleaseEnvPlan({ rollupOverride = null } = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const dispatch = buildDispatchCheck(checklist);
  const releaseEnvTrack = checklist.tracks.find((track) => track.id === "p0-release-env") || {};
  const releaseEnvGate = (rollup.items || []).find((item) => item.id === "release-env") || {};
  const ownerPacketsCommand = "node scripts/ddd-staging-execution-checklist.mjs --owner-packets";
  const releaseInfraEnvTemplateCommand = "node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=release-infra";
  const target = dispatch.envInitCheck?.target || process.env.DDD_RELEASE_ENV_FILE || ".env.release.local";
  const validationCommands = [
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs",
    "node scripts/ddd-release-readiness-summary.mjs",
    "node scripts/ddd-staging-execution-checklist.mjs --rollup",
    "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
  ];
  const ownerSteps = (ownerBlockerSummary || []).map((owner) => ({
    owner: owner.owner,
    blockers: owner.blockers,
    placeholders: owner.placeholders,
    secretKeys: owner.secretKeys,
    handoffPath: owner.handoffPath,
    keys: owner.keys || [],
    reasons: owner.ownerInputReasons || [],
    envTemplateCommand: `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=${owner.owner}`,
  }));
  const status = releaseEnvGate.status === "PASS" ? "PASS" : "BLOCKED";
  return {
    status,
    generatedAt,
    willWriteFiles: false,
    target,
    targetExists: dispatch.envInitCheck?.targetExists === true,
    forceEnabled: dispatch.envInitCheck?.forceEnabled === true,
    envInitCheck: dispatch.envInitCheck || null,
    releaseEnvGate: {
      status: releaseEnvGate.status || status,
      blocker: releaseEnvGate.issue || releaseEnvTrack.reason || null,
      blockingInputs: releaseEnvGate.blockingInputs || ["DDD_RELEASE_ENV_FILE"],
      acceptanceCommand: releaseEnvGate.nextCommand || "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
    },
    ownerCount: ownerSteps.length,
    ownerSteps,
    commands: {
      preflight: [
        envInitCheckCommand,
        ownerPacketsCommand,
        releaseInfraEnvTemplateCommand,
      ],
      initialize: [
        envInitCommand,
      ],
      validate: validationCommands,
    },
    expectedArtifacts: releaseEnvTrack.artifacts || [],
    safety: [
      "Do not commit populated release env files.",
      "Keep the target owner-only where the filesystem supports chmod 600.",
      "Use owner packets for redacted key collection; merge values only in a secure secret store or local release runner.",
      "Rerun final review after lint, config evidence, and readiness summary are regenerated.",
    ],
    nextCommand: status === "PASS" ? validationCommands.at(-1) : envInitCheckCommand,
  };
}

function renderReleaseEnvPlanMarkdown(plan) {
  const lines = [
    "# DDD P0 Release Env Plan",
    "",
    `Status: ${plan.status}`,
    `Target: \`${plan.target}\``,
    `Target exists: ${plan.targetExists}`,
    `Force enabled: ${plan.forceEnabled}`,
    `Current blocker: ${plan.releaseEnvGate.blocker || "none"}`,
    "",
    "## Commands",
    "",
    "### Preflight",
    "",
    ...plan.commands.preflight.map((command) => `- \`${command}\``),
    "",
    "### Initialize",
    "",
    ...plan.commands.initialize.map((command) => `- \`${command}\``),
    "",
    "### Validate",
    "",
    ...plan.commands.validate.map((command) => `- \`${command}\``),
    "",
    "## Owner Inputs",
    "",
    "| Owner | Blockers | Secret keys | Handoff | Env template |",
    "| --- | ---: | ---: | --- | --- |",
    ...plan.ownerSteps.map((owner) => [
      owner.owner,
      owner.blockers,
      owner.secretKeys,
      owner.handoffPath ? `\`${owner.handoffPath}\`` : "none",
      `\`${owner.envTemplateCommand}\``,
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Safety",
    "",
    ...plan.safety.map((item) => `- ${item}`),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runReleaseEnvPlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildReleaseEnvPlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderReleaseEnvPlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (releaseEnvPlanOnly) {
  runReleaseEnvPlan();
}

if (releaseEnvPlanMarkdownOnly) {
  runReleaseEnvPlan({ markdown: true });
}

function buildReleaseEnvOwnerMatrix({ releaseEnvPlanOverride = null } = {}) {
  const plan = releaseEnvPlanOverride || buildReleaseEnvPlan();
  const owners = (plan.ownerSteps || []).map((owner) => ({
    owner: owner.owner,
    blockers: Number(owner.blockers || 0),
    placeholders: Number(owner.placeholders || 0),
    secretKeys: Number(owner.secretKeys || 0),
    keyCount: (owner.keys || []).length,
    keys: owner.keys || [],
    reasons: owner.reasons || [],
    handoffPath: owner.handoffPath || null,
    envTemplateCommand: owner.envTemplateCommand,
    firstKey: (owner.keys || [])[0] || null,
  }));
  const totals = owners.reduce((acc, owner) => {
    acc.blockers += owner.blockers;
    acc.placeholders += owner.placeholders;
    acc.secretKeys += owner.secretKeys;
    acc.keyCount += owner.keyCount;
    return acc;
  }, { blockers: 0, placeholders: 0, secretKeys: 0, keyCount: 0 });
  const topOwner = [...owners].sort((left, right) => right.blockers - left.blockers || right.secretKeys - left.secretKeys || left.owner.localeCompare(right.owner))[0] || null;
  return {
    status: plan.status,
    generatedAt,
    willWriteFiles: false,
    target: plan.target,
    ownerCount: owners.length,
    totals,
    owners,
    topOwner: topOwner
      ? {
        owner: topOwner.owner,
        blockers: topOwner.blockers,
        secretKeys: topOwner.secretKeys,
        firstKey: topOwner.firstKey,
        envTemplateCommand: topOwner.envTemplateCommand,
      }
      : null,
    commands: {
      ownerPackets: "node scripts/ddd-staging-execution-checklist.mjs --owner-packets",
      allBlockingInputsTemplate: "node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template",
      validate: plan.commands.validate,
    },
    nextCommand: topOwner?.envTemplateCommand || plan.nextCommand,
  };
}

function renderReleaseEnvOwnerMatrixMarkdown(matrix) {
  const lines = [
    "# DDD Release Env Owner Matrix",
    "",
    `Status: ${matrix.status}`,
    `Target: \`${matrix.target}\``,
    `Owners: ${matrix.ownerCount}`,
    `Blockers: ${matrix.totals.blockers}`,
    `Placeholders: ${matrix.totals.placeholders}`,
    `Secret keys: ${matrix.totals.secretKeys}`,
    "",
    "## Owners",
    "",
    "| Owner | Blockers | Placeholders | Secret keys | Keys | First key | Env template |",
    "| --- | ---: | ---: | ---: | ---: | --- | --- |",
    ...matrix.owners.map((owner) => [
      owner.owner,
      owner.blockers,
      owner.placeholders,
      owner.secretKeys,
      owner.keyCount,
      owner.firstKey ? `\`${owner.firstKey}\`` : "none",
      `\`${owner.envTemplateCommand}\``,
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Commands",
    "",
    `- Owner packets: \`${matrix.commands.ownerPackets}\``,
    `- All inputs template: \`${matrix.commands.allBlockingInputsTemplate}\``,
    ...(matrix.commands.validate || []).map((command) => `- Validate: \`${command}\``),
    "",
    `Next: \`${matrix.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runReleaseEnvOwnerMatrix({ markdown = false } = {}) {
  let matrix;
  try {
    matrix = buildReleaseEnvOwnerMatrix();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderReleaseEnvOwnerMatrixMarkdown(matrix));
  } else {
    console.log(JSON.stringify(matrix, null, 2));
  }
  process.exit(0);
}

if (releaseEnvOwnerMatrixOnly) {
  runReleaseEnvOwnerMatrix();
}

if (releaseEnvOwnerMatrixMarkdownOnly) {
  runReleaseEnvOwnerMatrix({ markdown: true });
}

function buildReleaseEnvNextOwnerTemplateReport({ rollupOverride = null, matrixOverride = null } = {}) {
  const matrix = matrixOverride || buildReleaseEnvOwnerMatrix();
  const ownerName = matrix.topOwner?.owner || matrix.owners?.[0]?.owner || "";
  const owner = (ownerBlockerSummary || []).find((candidate) => candidate.owner === ownerName);
  if (!owner) {
    return {
      status: "PASS",
      generatedAt,
      willWriteFiles: false,
      owner: null,
      reason: "no release-env owner blockers remain",
      content: renderBlockingInputsEnvTemplate(buildBlockingInputs({ rollupOverride, owner: "__none__" })),
      nextCommand: "node scripts/ddd-staging-execution-checklist.mjs --release-env-owner-matrix",
    };
  }
  const report = buildOwnerBlockingInputsEnvReport(owner, { rollupOverride });
  const content = [
    "# Current top release-env owner template.",
    `# Owner: ${owner.owner}`,
    `# Release-env blockers: ${owner.blockers}`,
    `# Release-env placeholders: ${owner.placeholders}`,
    `# Release-env secret keys: ${owner.secretKeys}`,
    `# Handoff: ${owner.handoffPath || "none"}`,
    "# Merge completed values only into a secure DDD_RELEASE_ENV_FILE.",
    "",
    renderBlockingInputsEnvTemplate(report),
  ].join("\n");
  return {
    status: report.status,
    generatedAt,
    willWriteFiles: false,
    owner: owner.owner,
    blockers: owner.blockers,
    placeholders: owner.placeholders,
    secretKeys: owner.secretKeys,
    keyCount: (owner.keys || []).length,
    handoffPath: owner.handoffPath || null,
    content,
    nextCommand: "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
  };
}

function renderReleaseEnvNextOwnerTemplate(report) {
  return report.content;
}

function runReleaseEnvNextOwnerTemplate() {
  let report;
  try {
    report = buildReleaseEnvNextOwnerTemplateReport();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  process.stdout.write(renderReleaseEnvNextOwnerTemplate(report));
  process.exit(0);
}

if (releaseEnvNextOwnerTemplateOnly) {
  runReleaseEnvNextOwnerTemplate();
}

function buildReleaseEnvMergePlan({ releaseEnvPlanOverride = null, matrixOverride = null } = {}) {
  const plan = releaseEnvPlanOverride || buildReleaseEnvPlan();
  const matrix = matrixOverride || buildReleaseEnvOwnerMatrix({ releaseEnvPlanOverride: plan });
  const canonicalTemplate = "artifacts/ddd/release/release-env-canonical-fill.template.env";
  const ownerTemplateDir = "artifacts/ddd/release/release-env-owner-templates";
  const releaseEnvFile = "<release-env-file>";
  const mergeCommands = [
    `node scripts/ddd-release-env-owner-templates-merge.mjs ${ownerTemplateDir} ${canonicalTemplate}`,
    `DDD_RELEASE_ENV_FILE=${releaseEnvFile} node scripts/ddd-release-env-canonical-merge.mjs ${canonicalTemplate} ${releaseEnvFile}`,
    `DDD_RELEASE_ENV_FILE=${releaseEnvFile} node scripts/ddd-release-env-safe-defaults.mjs`,
    `DDD_RELEASE_ENV_FILE=${releaseEnvFile} node scripts/ddd-release-provenance-defaults.mjs`,
    `DDD_RELEASE_ENV_FILE=${releaseEnvFile} node scripts/ddd-release-env-alias-sync.mjs`,
  ];
  const validateCommands = [
    `DDD_RELEASE_ENV_FILE=${releaseEnvFile} node scripts/ddd-release-env-canonical-lint.mjs ${canonicalTemplate}`,
    `DDD_RELEASE_ENV_FILE=${releaseEnvFile} node scripts/ddd-release-env-file-lint.mjs`,
    `DDD_RELEASE_ENV_FILE=${releaseEnvFile} node scripts/ddd-release-config-evidence.mjs`,
    "node scripts/ddd-release-readiness-summary.mjs",
    "node scripts/ddd-staging-execution-checklist.mjs --rollup",
    "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
  ];
  return {
    status: plan.status,
    generatedAt,
    willWriteFiles: false,
    target: plan.target,
    ownerCount: matrix.ownerCount,
    blockers: matrix.totals.blockers,
    placeholders: matrix.totals.placeholders,
    secretKeys: matrix.totals.secretKeys,
    ownerTemplateDir,
    canonicalTemplate,
    releaseEnvFile,
    phases: [
      {
        id: "collect-owner-values",
        owner: "release-owner",
        commands: [
          "node scripts/ddd-staging-execution-checklist.mjs --release-env-owner-matrix",
          "node scripts/ddd-staging-execution-checklist.mjs --release-env-next-owner-template",
          "node scripts/ddd-staging-execution-checklist.mjs --owner-packets",
        ],
        artifacts: [
          "artifacts/ddd/release/staging-handoff-bundle/release-env-next-owner.template.env",
          "artifacts/ddd/release/staging-handoff-bundle/owner-packets/*.blocking-inputs.template.env",
        ],
      },
      {
        id: "merge-owner-values",
        owner: "release-infra",
        commands: mergeCommands,
        artifacts: [
          canonicalTemplate,
          releaseEnvFile,
        ],
      },
      {
        id: "validate-release-env",
        owner: "release-infra",
        commands: validateCommands,
        artifacts: plan.expectedArtifacts,
      },
    ],
    safety: [
      "Do not use release-env-next-owner.template.env or any owner-packets/*.template.env file as DDD_RELEASE_ENV_FILE.",
      "Merge completed values into a permission-safe release env file only.",
      "Run canonical lint and env-file lint before config evidence.",
      "Rerun final review only after readiness summary is regenerated from the completed env file.",
    ],
    nextCommand: matrix.topOwner?.envTemplateCommand || "node scripts/ddd-staging-execution-checklist.mjs --release-env-owner-matrix",
  };
}

function renderReleaseEnvMergePlanMarkdown(plan) {
  const lines = [
    "# DDD Release Env Merge Plan",
    "",
    `Status: ${plan.status}`,
    `Owners: ${plan.ownerCount}`,
    `Blockers: ${plan.blockers}`,
    `Placeholders: ${plan.placeholders}`,
    `Secret keys: ${plan.secretKeys}`,
    `Canonical template: \`${plan.canonicalTemplate}\``,
    `Release env file: \`${plan.releaseEnvFile}\``,
    "",
  ];
  for (const phase of plan.phases) {
    lines.push(
      `## ${phase.id}`,
      "",
      `Owner: ${phase.owner}`,
      "",
      "Commands:",
      "",
      ...phase.commands.map((command) => `- \`${command}\``),
      "",
      "Artifacts:",
      "",
      ...phase.artifacts.map((artifact) => `- \`${artifact}\``),
      "",
    );
  }
  lines.push(
    "## Safety",
    "",
    ...plan.safety.map((item) => `- ${item}`),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  );
  return lines.join("\n");
}

function runReleaseEnvMergePlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildReleaseEnvMergePlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderReleaseEnvMergePlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (releaseEnvMergePlanOnly) {
  runReleaseEnvMergePlan();
}

if (releaseEnvMergePlanMarkdownOnly) {
  runReleaseEnvMergePlan({ markdown: true });
}

function buildReleaseEnvSubmissionPlan({ releaseEnvPlanOverride = null, matrixOverride = null, mergePlanOverride = null } = {}) {
  const releaseEnvPlan = releaseEnvPlanOverride || buildReleaseEnvPlan();
  const matrix = matrixOverride || buildReleaseEnvOwnerMatrix({ releaseEnvPlanOverride: releaseEnvPlan });
  const mergePlan = mergePlanOverride || buildReleaseEnvMergePlan({ releaseEnvPlanOverride: releaseEnvPlan, matrixOverride: matrix });
  const ownerSubmissions = matrix.owners.map((owner) => ({
    owner: owner.owner,
    blockers: owner.blockers,
    placeholders: owner.placeholders,
    secretKeys: owner.secretKeys,
    keyCount: owner.keyCount,
    keys: owner.keys,
    envTemplateCommand: owner.envTemplateCommand,
    handoffPath: owner.handoffPath,
  }));
  const receiptFile = "<receipt-file>";
  const envFile = "<release-env-file>";
  return {
    status: releaseEnvPlan.status,
    generatedAt,
    willWriteFiles: false,
    target: releaseEnvPlan.target,
    ownerCount: matrix.ownerCount,
    blockers: matrix.totals.blockers,
    placeholders: matrix.totals.placeholders,
    secretKeys: matrix.totals.secretKeys,
    ownerSubmissions,
    mergePlan: {
      ownerTemplateDir: mergePlan.ownerTemplateDir,
      canonicalTemplate: mergePlan.canonicalTemplate,
      releaseEnvFile: mergePlan.releaseEnvFile,
      phases: mergePlan.phases,
    },
    receipt: {
      envFile,
      receiptFile,
      redacted: true,
      commands: [
        `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-check --next-action-env-file=${envFile}`,
        `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=${envFile} --next-action-env-receipt-output=${receiptFile}`,
        `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=${receiptFile}`,
      ],
    },
    laneReceiptFragment: {
      owner: "release-infra",
      lane: "p0-release-env",
      status: releaseEnvPlan.status === "PASS" ? "PASS" : "BLOCKED",
      providedArtifacts: [
        "artifacts/ddd/release/release-env-lint.json",
        "artifacts/ddd/config/release-config-evidence.json",
        "artifacts/ddd/release/readiness-summary.json",
      ],
      missingArtifacts: releaseEnvPlan.status === "PASS" ? [] : [
        "artifacts/ddd/release/release-env-lint.json",
        "artifacts/ddd/config/release-config-evidence.json",
        "artifacts/ddd/release/readiness-summary.json",
      ],
      completedAt: "<ISO-8601 timestamp after validation commands pass>",
      completedBy: "<owner or workflow actor>",
      acceptanceCommands: [
        `DDD_RELEASE_ENV_FILE=${envFile} node scripts/ddd-release-env-file-lint.mjs`,
      ],
    },
    validationCommands: [
      `DDD_RELEASE_ENV_FILE=${envFile} node scripts/ddd-release-env-canonical-lint.mjs ${mergePlan.canonicalTemplate}`,
      `DDD_RELEASE_ENV_FILE=${envFile} node scripts/ddd-release-env-file-lint.mjs`,
      `DDD_RELEASE_ENV_FILE=${envFile} node scripts/ddd-release-config-evidence.mjs`,
      "node scripts/ddd-release-readiness-summary.mjs",
      `node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --next-action-env-receipt-file=${receiptFile}`,
      "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
    ],
    passCriteria: [
      "owner templates must be completed without committing populated secrets",
      "merged release env file must be permission-safe and used through DDD_RELEASE_ENV_FILE only",
      "canonical lint and env-file lint must pass",
      "next-action env receipt must be redacted and pass its contract",
      "release config evidence and readiness summary must be regenerated after env validation",
    ],
    nextCommand: matrix.topOwner?.envTemplateCommand || "node scripts/ddd-staging-execution-checklist.mjs --release-env-owner-matrix",
  };
}

function renderReleaseEnvSubmissionPlanMarkdown(plan) {
  const lines = [
    "# DDD Release Env Submission Plan",
    "",
    `Status: ${plan.status}`,
    `Owners: ${plan.ownerCount}`,
    `Blockers: ${plan.blockers}`,
    `Placeholders: ${plan.placeholders}`,
    `Secret keys: ${plan.secretKeys}`,
    `Target: \`${plan.target}\``,
    `Release env file: \`${plan.mergePlan.releaseEnvFile}\``,
    `Receipt file: \`${plan.receipt.receiptFile}\``,
    "",
    "## Owner Submissions",
    "",
    "| Owner | Blockers | Secret keys | Keys | Template command |",
    "| --- | ---: | ---: | ---: | --- |",
    ...plan.ownerSubmissions.map((owner) => [
      owner.owner,
      owner.blockers,
      owner.secretKeys,
      owner.keyCount,
      `\`${owner.envTemplateCommand}\``,
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Merge And Validate",
    "",
    ...plan.mergePlan.phases.flatMap((phase) => [
      `### ${phase.id}`,
      "",
      `Owner: ${phase.owner}`,
      "",
      ...phase.commands.map((command) => `- \`${command}\``),
      "",
    ]),
    "## Redacted Receipt",
    "",
    ...plan.receipt.commands.map((command) => `- \`${command}\``),
    "",
    "## Lane Receipt Fragment",
    "",
    "```json",
    JSON.stringify(plan.laneReceiptFragment, null, 2),
    "```",
    "",
    "## Final Validation",
    "",
    ...plan.validationCommands.map((command) => `- \`${command}\``),
    "",
    "## Pass Criteria",
    "",
    ...plan.passCriteria.map((item) => `- ${item}`),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runReleaseEnvSubmissionPlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildReleaseEnvSubmissionPlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderReleaseEnvSubmissionPlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (releaseEnvSubmissionPlanOnly) {
  runReleaseEnvSubmissionPlan();
}

if (releaseEnvSubmissionPlanMarkdownOnly) {
  runReleaseEnvSubmissionPlan({ markdown: true });
}

function dockerImageCheckFromSubprocess() {
  const result = spawnSync("node", ["scripts/ddd-docker-build-evidence.mjs", "--check"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: process.env,
  });
  try {
    return JSON.parse(result.stdout || "{}");
  } catch {
    return {
      status: "BLOCKED",
      generatedAt,
      willWriteFiles: false,
      issues: [result.stderr?.trim() || result.error?.message || "failed to parse docker image check output"],
    };
  }
}

function buildDockerImagePlan({ rollupOverride = null } = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const dockerGate = (rollup.items || []).find((item) => item.id === "docker-images") || {};
  const dockerCheck = dockerImageCheckFromSubprocess();
  const existingInputs = dockerCheck.existingImageInputs || [];
  const remediationActions = dockerCheck.remediation?.nextActions || [];
  const dockerRunnerCommand = "DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs";
  const inspectOnlyCommand = "DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs";
  return {
    status: dockerGate.status || dockerCheck.status || "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    recommendedMode: dockerCheck.recommendedMode || null,
    blocker: dockerGate.issue || (dockerCheck.issues || [])[0] || null,
    dockerAvailable: dockerCheck.dockerAvailable === true,
    staticDockerfiles: dockerCheck.staticDockerfiles || [],
    existingImageInputs: existingInputs,
    requiredInputs: unique([
      "DDD_DOCKER_BUILD_STRICT",
      "DDD_RELEASE_CANDIDATE",
      "DDD_EVIDENCE_OPERATOR",
      "DDD_EVIDENCE_ENVIRONMENT",
      ...existingInputs.map((input) => input.envKey),
      ...(dockerCheck.existingImageBuildEvidencePresent === false ? ["DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE"] : []),
    ]),
    paths: [
      {
        id: "docker-runner-build",
        owner: "release-infra",
        when: "Docker CLI and daemon are available in the evidence runner.",
        command: dockerRunnerCommand,
        artifacts: ["artifacts/ddd/build/docker-image-evidence.json"],
      },
      {
        id: "existing-image-inspect",
        owner: "release-infra",
        when: "CI already built and pushed release-candidate images.",
        command: remediationActions.find((action) => action.id === "docker-existing-image-inspect")?.exampleCommand || inspectOnlyCommand,
        artifacts: ["artifacts/ddd/build/docker-image-evidence.json"],
      },
    ],
    validationCommands: [
      "node scripts/ddd-docker-build-evidence.mjs --check",
      "node scripts/ddd-release-readiness-summary.mjs",
      "node scripts/ddd-staging-execution-checklist.mjs --rollup",
      "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
    ],
    remediationActions,
    nextCommand: dockerCheck.nextCommand || dockerEvidenceCheckCommand,
  };
}

function renderDockerImagePlanMarkdown(plan) {
  const lines = [
    "# DDD Docker Image Evidence Plan",
    "",
    `Status: ${plan.status}`,
    `Recommended mode: ${plan.recommendedMode || "unknown"}`,
    `Docker available: ${plan.dockerAvailable}`,
    `Blocker: ${plan.blocker || "none"}`,
    "",
    "## Static Dockerfiles",
    "",
    "| Image | Dockerfile | Status | SHA-256 | Issues |",
    "| --- | --- | --- | --- | --- |",
    ...plan.staticDockerfiles.map((item) => [
      item.name,
      `\`${item.dockerfile}\``,
      item.status,
      item.dockerfileSha256 || "missing",
      (item.issues || []).join("; ") || "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Evidence Paths",
    "",
  ];
  for (const item of plan.paths) {
    lines.push(
      `### ${item.id}`,
      "",
      `Owner: ${item.owner}`,
      `When: ${item.when}`,
      `Command: \`${item.command}\``,
      "",
      "Artifacts:",
      "",
      ...item.artifacts.map((artifact) => `- \`${artifact}\``),
      "",
    );
  }
  lines.push(
    "## Required Inputs",
    "",
    ...plan.requiredInputs.map((input) => `- \`${input}\``),
    "",
    "## Validation Commands",
    "",
    ...plan.validationCommands.map((command) => `- \`${command}\``),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  );
  return lines.join("\n");
}

function buildDockerImageSubmissionPlan({ dockerImagePlanOverride = null, rollupOverride = null } = {}) {
  const plan = dockerImagePlanOverride || buildDockerImagePlan({ rollupOverride });
  return {
    status: plan.status,
    generatedAt,
    willWriteFiles: false,
    owner: "release-infra",
    gate: "docker-images",
    recommendedMode: plan.recommendedMode,
    dockerAvailable: plan.dockerAvailable,
    blocker: plan.blocker,
    evidenceArtifact: "artifacts/ddd/build/docker-image-evidence.json",
    staticDockerfiles: plan.staticDockerfiles,
    requiredInputs: plan.requiredInputs,
    submissionModes: plan.paths.map((item) => ({
      id: item.id,
      owner: item.owner,
      when: item.when,
      command: item.command,
      artifacts: item.artifacts,
      prerequisites: item.id === "docker-runner-build"
        ? [
            "Docker CLI, daemon, and buildx are available on the evidence runner.",
            "Release candidate and provenance env values are populated before running strict evidence capture.",
          ]
        : [
            "Release-candidate images already exist in the registry.",
            "Image references and CI build evidence URL are supplied through the existing-image env inputs.",
          ],
      workflowInputs: item.id === "existing-image-inspect"
        ? [
            "DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE",
            "DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE",
            "DDD_DOCKER_EXISTING_FRONTEND_IMAGE",
          ]
        : [
            "DDD_DOCKER_BUILD_STRICT",
            "DDD_RELEASE_CANDIDATE",
            "DDD_EVIDENCE_OPERATOR",
            "DDD_EVIDENCE_ENVIRONMENT",
          ],
    })),
    validationCommands: [
      "node scripts/ddd-docker-build-evidence.mjs --check",
      "node scripts/ddd-release-readiness-summary.mjs",
      "node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown",
      "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
    ],
    laneReceiptFragment: {
      owner: "release-infra",
      lane: "p0-docker-images",
      status: plan.status === "PASS" ? "PASS" : "BLOCKED",
      providedArtifacts: ["artifacts/ddd/build/docker-image-evidence.json"],
      missingArtifacts: plan.status === "PASS" ? [] : ["artifacts/ddd/build/docker-image-evidence.json"],
      completedAt: "<ISO-8601 timestamp after validation commands pass>",
      completedBy: "<owner or workflow actor>",
      acceptanceCommands: ["node scripts/ddd-docker-build-evidence.mjs --check"],
    },
    passCriteria: [
      "Docker image evidence artifact exists and passes `node scripts/ddd-docker-build-evidence.mjs --check`.",
      "Image references are scoped to the release candidate and include build provenance evidence.",
      "Readiness summary and operator progress are regenerated after evidence capture.",
      "The docker-images gate no longer blocks final review.",
    ],
    nextCommand: plan.nextCommand || "node scripts/ddd-staging-execution-checklist.mjs --docker-image-plan",
  };
}

function renderDockerImageSubmissionPlanMarkdown(plan) {
  const lines = [
    "# DDD Docker Image Submission Plan",
    "",
    `Status: ${plan.status}`,
    `Owner: ${plan.owner}`,
    `Gate: ${plan.gate}`,
    `Recommended mode: ${plan.recommendedMode || "unknown"}`,
    `Docker available: ${plan.dockerAvailable}`,
    `Blocker: ${plan.blocker || "none"}`,
    `Evidence artifact: \`${plan.evidenceArtifact}\``,
    "",
    "## Static Dockerfiles",
    "",
    "| Image | Dockerfile | Status | SHA-256 |",
    "| --- | --- | --- | --- |",
    ...plan.staticDockerfiles.map((item) => [
      item.name,
      `\`${item.dockerfile}\``,
      item.status,
      item.dockerfileSha256 || "missing",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Submission Modes",
    "",
  ];
  for (const mode of plan.submissionModes) {
    lines.push(
      `### ${mode.id}`,
      "",
      `Owner: ${mode.owner}`,
      `When: ${mode.when}`,
      `Command: \`${mode.command}\``,
      "",
      "Prerequisites:",
      "",
      ...mode.prerequisites.map((item) => `- ${item}`),
      "",
      "Workflow inputs:",
      "",
      ...mode.workflowInputs.map((input) => `- \`${input}\``),
      "",
      "Artifacts:",
      "",
      ...mode.artifacts.map((artifact) => `- \`${artifact}\``),
      "",
    );
  }
  lines.push(
    "## Required Inputs",
    "",
    ...plan.requiredInputs.map((input) => `- \`${input}\``),
    "",
    "## Validation Commands",
    "",
    ...plan.validationCommands.map((command) => `- \`${command}\``),
    "",
    "## Lane Receipt Fragment",
    "",
    "```json",
    JSON.stringify(plan.laneReceiptFragment, null, 2),
    "```",
    "",
    "## Pass Criteria",
    "",
    ...plan.passCriteria.map((item) => `- ${item}`),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  );
  return lines.join("\n");
}

function runDockerImagePlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildDockerImagePlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderDockerImagePlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

function runDockerImageSubmissionPlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildDockerImageSubmissionPlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderDockerImageSubmissionPlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (dockerImagePlanOnly) {
  runDockerImagePlan();
}

if (dockerImagePlanMarkdownOnly) {
  runDockerImagePlan({ markdown: true });
}

if (dockerImageSubmissionPlanOnly) {
  runDockerImageSubmissionPlan();
}

if (dockerImageSubmissionPlanMarkdownOnly) {
  runDockerImageSubmissionPlan({ markdown: true });
}

function runtimeCheckFromSubprocess() {
  const result = spawnSync("node", ["scripts/ddd-staging-runtime-check.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  try {
    return JSON.parse(result.stdout);
  } catch {
    return {
      status: "BLOCKED",
      willWriteFiles: false,
      issues: [`runtime staging check returned non-JSON output: ${result.stderr.trim() || result.stdout.trim() || result.status}`],
    };
  }
}

function buildRuntimeBusinessPlan({ rollupOverride = null } = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const runtimeTrack = checklist.tracks.find((track) => track.id === "p1-runtime-business") || {};
  const runtimeGate = (rollup.items || []).find((item) => item.id === "runtime-business") || {};
  const runtimeCheck = runtimeCheckFromSubprocess();
  const blockingInputs = buildBlockingInputs({ rollupOverride: rollup });
  const runtimeBlockingInputs = (blockingInputs.inputs || [])
    .filter((input) => (input.gates || []).some((gate) => gate.gate === "runtime-business"))
    .map((input) => ({
      input: input.input,
      owners: input.owners,
      nextCommand: input.nextCommands[0] || null,
    }));
  const smokeSteps = [
    {
      id: "runtime-readiness",
      owner: "release-infra",
      command: "node scripts/ddd-runtime-readiness-smoke.mjs",
      artifact: "artifacts/ddd/readiness/summary.json",
    },
    {
      id: "authenticated-performance",
      owner: "release-performance",
      command: "DDD_AUTH_PERF_STRICT=true node scripts/ddd-authenticated-performance-smoke.mjs",
      artifact: "artifacts/ddd/performance/authenticated-runtime-actual.json",
    },
    {
      id: "ai-runtime",
      owner: "ai",
      command: "node scripts/ddd-ai-runtime-drill.mjs",
      artifact: "artifacts/ddd/ai/ai-runtime-drill.json",
    },
    {
      id: "frontend-smoke",
      owner: "frontend",
      command: "node scripts/ddd-frontend-playwright-smoke.mjs && node scripts/ddd-frontend-smoke-evidence.mjs",
      artifact: "artifacts/ddd/frontend/frontend-smoke.json",
    },
    {
      id: "file-processing-e2e",
      owner: "file-owner",
      command: "node scripts/ddd-file-processing-e2e-smoke.mjs",
      artifact: "artifacts/ddd/file/file-processing-e2e.json",
    },
    {
      id: "job-e2e",
      owner: "job-owner",
      command: "node scripts/ddd-job-e2e-smoke.mjs",
      artifact: "artifacts/ddd/jobs/job-e2e-smoke.json",
    },
    {
      id: "payment-webhook-e2e",
      owner: "payment-owner",
      command: "node scripts/ddd-payment-webhook-e2e-smoke.mjs",
      artifact: "artifacts/ddd/payment/payment-webhook-e2e.json",
    },
  ];
  const status = runtimeGate.status === "PASS" && runtimeCheck.status === "PASS" ? "PASS" : "BLOCKED";
  return {
    status,
    generatedAt,
    willWriteFiles: false,
    backendBaseUrl: runtimeCheck.backendBaseUrl || null,
    frontendBaseUrl: runtimeCheck.frontendBaseUrl || null,
    runtimeCheck,
    runtimeGate: {
      status: runtimeGate.status || status,
      blocker: runtimeGate.issue || runtimeTrack.reason || null,
      blockingInputs: runtimeGate.blockingInputs || runtimeBlockingInputs.map((input) => input.input),
      acceptanceCommand: runtimeGate.nextCommand || runtimeStagingCheckCommand,
    },
    owners: ["release-infra", "frontend", "ai", "file-owner", "job-owner", "payment-owner", "release-performance"],
    blockingInputs: runtimeBlockingInputs,
    requiredEnv: {
      urls: ["LUMIRA_BASE_URL", "PLAYWRIGHT_BASE_URL"],
      deploymentEvidence: [
        "DDD_DEPLOYMENT_EVIDENCE",
        "DDD_FRONTEND_DEPLOYMENT_EVIDENCE",
        "DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE",
        "DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE",
        "DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE",
      ],
      expectationFlags: [
        "DDD_FRONTEND_EXPECT_DEPLOYED=true",
        "DDD_AI_EXPECT_PROVIDER_REMOTE=true",
        "DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true",
      ],
    },
    commands: {
      preflight: [
        runtimeStagingCheckCommand,
        "node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=release-infra",
      ],
      smoke: smokeSteps.map((step) => step.command),
      validate: [
        runtimeStagingCheckCommand,
        "node scripts/ddd-release-readiness-summary.mjs",
        "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
        "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
      ],
    },
    smokeSteps,
    expectedArtifacts: runtimeTrack.artifacts || smokeSteps.map((step) => step.artifact),
    safety: [
      "Use HTTPS staging or production-equivalent URLs; localhost evidence is not accepted for strict release.",
      "Attach deployment evidence for backend, frontend, AI runtime, authenticated performance, and business E2E flows.",
      "Regenerate release readiness after every smoke artifact is refreshed.",
    ],
    nextCommand: status === "PASS" ? "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance" : runtimeStagingCheckCommand,
  };
}

function renderRuntimeBusinessPlanMarkdown(plan) {
  const lines = [
    "# DDD P1 Runtime Business Plan",
    "",
    `Status: ${plan.status}`,
    `Backend URL: ${plan.backendBaseUrl ? `\`${plan.backendBaseUrl}\`` : "missing"}`,
    `Frontend URL: ${plan.frontendBaseUrl ? `\`${plan.frontendBaseUrl}\`` : "missing"}`,
    `Current blocker: ${plan.runtimeGate.blocker || "none"}`,
    "",
    "## Required Inputs",
    "",
    "- URLs: " + plan.requiredEnv.urls.map((key) => `\`${key}\``).join(", "),
    "- Deployment evidence: " + plan.requiredEnv.deploymentEvidence.map((key) => `\`${key}\``).join(", "),
    "- Expectation flags: " + plan.requiredEnv.expectationFlags.map((item) => `\`${item}\``).join(", "),
    "",
    "## Smoke Steps",
    "",
    "| Step | Owner | Command | Artifact |",
    "| --- | --- | --- | --- |",
    ...plan.smokeSteps.map((step) => [
      step.id,
      step.owner,
      `\`${step.command}\``,
      `\`${step.artifact}\``,
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Validate",
    "",
    ...plan.commands.validate.map((command) => `- \`${command}\``),
    "",
    "## Safety",
    "",
    ...plan.safety.map((item) => `- ${item}`),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runRuntimeBusinessPlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildRuntimeBusinessPlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderRuntimeBusinessPlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (runtimeBusinessPlanOnly) {
  runRuntimeBusinessPlan();
}

if (runtimeBusinessPlanMarkdownOnly) {
  runRuntimeBusinessPlan({ markdown: true });
}

function buildRuntimeSmokePlan({ runtimeBusinessPlanOverride = null, rollupOverride = null } = {}) {
  const runtimeBusinessPlan = runtimeBusinessPlanOverride || buildRuntimeBusinessPlan({ rollupOverride });
  const smokeStepById = new Map((runtimeBusinessPlan.smokeSteps || []).map((step) => [step.id, step]));
  const phases = [
    {
      id: "runtime-deployment-evidence",
      phase: "P1",
      owner: "release-infra",
      status: runtimeBusinessPlan.runtimeCheck?.status === "PASS" ? "PASS" : "BLOCKED",
      goal: "Publish HTTPS backend/frontend staging URLs and attach deployment evidence for strict runtime checks.",
      requiredInputs: [
        "LUMIRA_BASE_URL",
        "PLAYWRIGHT_BASE_URL",
        "DDD_DEPLOYMENT_EVIDENCE",
        "DDD_FRONTEND_DEPLOYMENT_EVIDENCE",
        "DDD_FRONTEND_EXPECT_DEPLOYED=true",
      ],
      commands: [
        runtimeStagingCheckCommand,
        "node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=release-infra",
      ],
      artifacts: ["artifacts/ddd/readiness/summary.json"],
      acceptanceCommand: runtimeStagingCheckCommand,
      blocker: runtimeBusinessPlan.runtimeCheck?.issues?.[0] || runtimeBusinessPlan.runtimeGate?.blocker || null,
      dependencies: [],
    },
    {
      id: "ai-runtime-evidence",
      phase: "P1",
      owner: "ai",
      status: "BLOCKED",
      goal: "Prove AI provider and owner gateway are remote in staging, not localhost or test doubles.",
      requiredInputs: [
        "DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE",
        "DDD_AI_EXPECT_PROVIDER_REMOTE=true",
        "DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true",
      ],
      commands: [smokeStepById.get("ai-runtime")?.command].filter(Boolean),
      artifacts: [smokeStepById.get("ai-runtime")?.artifact].filter(Boolean),
      acceptanceCommand: "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
      blocker: "missing AI runtime deployment evidence or remote expectation flags",
      dependencies: ["runtime-deployment-evidence"],
    },
    {
      id: "auth-performance-evidence",
      phase: "P1",
      owner: "release-performance",
      status: "BLOCKED",
      goal: "Capture authenticated runtime performance from the staging deployment under strict thresholds.",
      requiredInputs: ["DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE"],
      commands: [smokeStepById.get("authenticated-performance")?.command].filter(Boolean),
      artifacts: [smokeStepById.get("authenticated-performance")?.artifact].filter(Boolean),
      acceptanceCommand: "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
      blocker: "missing authenticated performance staging evidence",
      dependencies: ["runtime-deployment-evidence"],
    },
    {
      id: "frontend-runtime-smoke",
      phase: "P1",
      owner: "frontend",
      status: "BLOCKED",
      goal: "Run deployed frontend smoke checks against the HTTPS Playwright base URL and write smoke evidence.",
      requiredInputs: ["PLAYWRIGHT_BASE_URL", "DDD_FRONTEND_DEPLOYMENT_EVIDENCE", "DDD_FRONTEND_EXPECT_DEPLOYED=true"],
      commands: [smokeStepById.get("frontend-smoke")?.command].filter(Boolean),
      artifacts: [smokeStepById.get("frontend-smoke")?.artifact].filter(Boolean),
      acceptanceCommand: "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
      blocker: "missing frontend staging smoke evidence",
      dependencies: ["runtime-deployment-evidence"],
    },
    {
      id: "business-file-smoke",
      phase: "P1",
      owner: "file-owner",
      status: "BLOCKED",
      goal: "Exercise production-equivalent file processing through staging and capture E2E evidence.",
      requiredInputs: ["DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE"],
      commands: [smokeStepById.get("file-processing-e2e")?.command].filter(Boolean),
      artifacts: [smokeStepById.get("file-processing-e2e")?.artifact].filter(Boolean),
      acceptanceCommand: "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
      blocker: "missing file processing E2E staging evidence",
      dependencies: ["runtime-deployment-evidence"],
    },
    {
      id: "business-job-smoke",
      phase: "P1",
      owner: "job-owner",
      status: "BLOCKED",
      goal: "Exercise staging async job execution and capture E2E evidence.",
      requiredInputs: ["DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE"],
      commands: [smokeStepById.get("job-e2e")?.command].filter(Boolean),
      artifacts: [smokeStepById.get("job-e2e")?.artifact].filter(Boolean),
      acceptanceCommand: "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
      blocker: "missing job E2E staging evidence",
      dependencies: ["runtime-deployment-evidence"],
    },
    {
      id: "business-payment-smoke",
      phase: "P1",
      owner: "payment-owner",
      status: "BLOCKED",
      goal: "Exercise staging payment webhook handling with approved non-production credentials and capture evidence.",
      requiredInputs: ["DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE"],
      commands: [smokeStepById.get("payment-webhook-e2e")?.command].filter(Boolean),
      artifacts: [smokeStepById.get("payment-webhook-e2e")?.artifact].filter(Boolean),
      acceptanceCommand: "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
      blocker: "missing payment webhook E2E staging evidence",
      dependencies: ["runtime-deployment-evidence"],
    },
    {
      id: "runtime-acceptance",
      phase: "P1",
      owner: "release-infra",
      status: runtimeBusinessPlan.status,
      goal: "Regenerate readiness and enforce evidence acceptance after all smoke artifacts are refreshed.",
      requiredInputs: [],
      commands: runtimeBusinessPlan.commands.validate,
      artifacts: runtimeBusinessPlan.expectedArtifacts,
      acceptanceCommand: "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
      blocker: runtimeBusinessPlan.status === "PASS" ? null : "runtime/business evidence is not fully accepted",
      dependencies: [
        "ai-runtime-evidence",
        "auth-performance-evidence",
        "frontend-runtime-smoke",
        "business-file-smoke",
        "business-job-smoke",
        "business-payment-smoke",
      ],
    },
  ];
  return {
    status: runtimeBusinessPlan.status,
    generatedAt,
    willWriteFiles: false,
    runtimeBusinessPlanStatus: runtimeBusinessPlan.status,
    backendBaseUrl: runtimeBusinessPlan.backendBaseUrl,
    frontendBaseUrl: runtimeBusinessPlan.frontendBaseUrl,
    ownerCount: unique(phases.map((phase) => phase.owner)).length,
    owners: unique(phases.map((phase) => phase.owner)),
    phaseCount: phases.length,
    phases,
    parallelAfterDeployment: phases
      .filter((phase) => phase.dependencies.includes("runtime-deployment-evidence"))
      .map((phase) => phase.id),
    validationCommands: runtimeBusinessPlan.commands.validate,
    safety: [
      "Run smoke commands only against HTTPS staging URLs with production-equivalent deployment evidence.",
      "Do not paste secrets into Markdown outputs; use secure env files or CI secret stores for populated values.",
      "Regenerate readiness after every owner smoke artifact is refreshed, then run final review enforcement.",
    ],
    nextPhase: phases.find((phase) => phase.status !== "PASS") || phases.at(-1),
    nextCommand: runtimeBusinessPlan.nextCommand,
  };
}

function renderRuntimeSmokePlanMarkdown(plan) {
  const lines = [
    "# DDD Runtime Smoke Owner Plan",
    "",
    `Status: ${plan.status}`,
    `Backend URL: ${plan.backendBaseUrl ? `\`${plan.backendBaseUrl}\`` : "missing"}`,
    `Frontend URL: ${plan.frontendBaseUrl ? `\`${plan.frontendBaseUrl}\`` : "missing"}`,
    `Owners: ${plan.owners.map((owner) => `\`${owner}\``).join(", ")}`,
    `Next phase: ${plan.nextPhase?.id || "none"}`,
    "",
    "## Phases",
    "",
    "| Phase | Owner | Depends On | Required Inputs | Commands | Artifacts |",
    "| --- | --- | --- | --- | --- | --- |",
    ...plan.phases.map((phase) => [
      phase.id,
      phase.owner,
      phase.dependencies.length > 0 ? phase.dependencies.map((item) => `\`${item}\``).join(", ") : "none",
      phase.requiredInputs.length > 0 ? phase.requiredInputs.map((item) => `\`${item}\``).join(", ") : "none",
      phase.commands.length > 0 ? phase.commands.map((command) => `\`${command}\``).join("<br>") : "none",
      phase.artifacts.length > 0 ? phase.artifacts.map((artifact) => `\`${artifact}\``).join("<br>") : "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Parallel After Deployment",
    "",
    ...plan.parallelAfterDeployment.map((phase) => `- \`${phase}\``),
    "",
    "## Validate",
    "",
    ...plan.validationCommands.map((command) => `- \`${command}\``),
    "",
    "## Safety",
    "",
    ...plan.safety.map((item) => `- ${item}`),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function buildRuntimeBusinessSubmissionPlan({ runtimeSmokePlanOverride = null, runtimeBusinessPlanOverride = null, rollupOverride = null } = {}) {
  const runtimeBusinessPlan = runtimeBusinessPlanOverride || buildRuntimeBusinessPlan({ rollupOverride });
  const smokePlan = runtimeSmokePlanOverride || buildRuntimeSmokePlan({ runtimeBusinessPlanOverride: runtimeBusinessPlan, rollupOverride });
  const deploymentPhase = smokePlan.phases.find((phase) => phase.id === "runtime-deployment-evidence") || null;
  const acceptancePhase = smokePlan.phases.find((phase) => phase.id === "runtime-acceptance") || null;
  const ownerSubmissions = smokePlan.phases
    .filter((phase) => !["runtime-deployment-evidence", "runtime-acceptance"].includes(phase.id))
    .map((phase) => ({
      owner: phase.owner,
      phase: phase.id,
      status: phase.status,
      goal: phase.goal,
      requiredInputs: phase.requiredInputs,
      commands: phase.commands,
      artifacts: phase.artifacts,
      acceptanceCommand: phase.acceptanceCommand,
      dependsOn: phase.dependencies,
      blocker: phase.blocker,
    }));
  return {
    status: smokePlan.status,
    generatedAt,
    willWriteFiles: false,
    gate: "runtime-business",
    owner: "release-infra",
    backendBaseUrl: smokePlan.backendBaseUrl,
    frontendBaseUrl: smokePlan.frontendBaseUrl,
    deploymentSubmission: deploymentPhase ? {
      owner: deploymentPhase.owner,
      status: deploymentPhase.status,
      requiredInputs: deploymentPhase.requiredInputs,
      commands: deploymentPhase.commands,
      artifacts: deploymentPhase.artifacts,
      acceptanceCommand: deploymentPhase.acceptanceCommand,
      blocker: deploymentPhase.blocker,
    } : null,
    ownerSubmissions,
    parallelAfterDeployment: smokePlan.parallelAfterDeployment,
    validationCommands: smokePlan.validationCommands,
    expectedArtifacts: runtimeBusinessPlan.expectedArtifacts,
    laneReceiptFragment: {
      owner: "release-infra",
      lane: "p1-runtime-business",
      status: smokePlan.status === "PASS" ? "PASS" : "BLOCKED",
      providedArtifacts: runtimeBusinessPlan.expectedArtifacts,
      missingArtifacts: smokePlan.status === "PASS" ? [] : runtimeBusinessPlan.expectedArtifacts,
      completedAt: "<ISO-8601 timestamp after validation commands pass>",
      completedBy: "<owner or workflow actor>",
      acceptanceCommands: smokePlan.validationCommands,
    },
    passCriteria: [
      "Deployment evidence phase passes with HTTPS backend and frontend URLs.",
      "AI, authenticated performance, frontend, file, job, and payment owners refresh their smoke artifacts after deployment evidence lands.",
      "Runtime staging check and evidence acceptance pass after all owner artifacts are present.",
      "Final review no longer reports the runtime-business gate as blocked.",
    ],
    acceptance: acceptancePhase ? {
      owner: acceptancePhase.owner,
      commands: acceptancePhase.commands,
      artifacts: acceptancePhase.artifacts,
      acceptanceCommand: acceptancePhase.acceptanceCommand,
      blocker: acceptancePhase.blocker,
    } : null,
    nextCommand: smokePlan.nextCommand,
  };
}

function renderRuntimeBusinessSubmissionPlanMarkdown(plan) {
  const lines = [
    "# DDD Runtime Business Submission Plan",
    "",
    `Status: ${plan.status}`,
    `Owner: ${plan.owner}`,
    `Gate: ${plan.gate}`,
    `Backend URL: ${plan.backendBaseUrl ? `\`${plan.backendBaseUrl}\`` : "missing"}`,
    `Frontend URL: ${plan.frontendBaseUrl ? `\`${plan.frontendBaseUrl}\`` : "missing"}`,
    "",
    "## Deployment Submission",
    "",
  ];
  if (plan.deploymentSubmission) {
    lines.push(
      `Owner: ${plan.deploymentSubmission.owner}`,
      `Status: ${plan.deploymentSubmission.status}`,
      `Blocker: ${plan.deploymentSubmission.blocker || "none"}`,
      "",
      "Required inputs:",
      "",
      ...plan.deploymentSubmission.requiredInputs.map((input) => `- \`${input}\``),
      "",
      "Commands:",
      "",
      ...plan.deploymentSubmission.commands.map((command) => `- \`${command}\``),
      "",
      "Artifacts:",
      "",
      ...plan.deploymentSubmission.artifacts.map((artifact) => `- \`${artifact}\``),
      "",
    );
  } else {
    lines.push("No deployment submission phase found.", "");
  }
  lines.push(
    "## Owner Submissions",
    "",
    "| Phase | Owner | Depends On | Required Inputs | Commands | Artifacts |",
    "| --- | --- | --- | --- | --- | --- |",
    ...plan.ownerSubmissions.map((item) => [
      item.phase,
      item.owner,
      item.dependsOn.length > 0 ? item.dependsOn.map((dependency) => `\`${dependency}\``).join(", ") : "none",
      item.requiredInputs.length > 0 ? item.requiredInputs.map((input) => `\`${input}\``).join(", ") : "none",
      item.commands.length > 0 ? item.commands.map((command) => `\`${command}\``).join("<br>") : "none",
      item.artifacts.length > 0 ? item.artifacts.map((artifact) => `\`${artifact}\``).join("<br>") : "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Parallel After Deployment",
    "",
    ...plan.parallelAfterDeployment.map((phase) => `- \`${phase}\``),
    "",
    "## Validation Commands",
    "",
    ...plan.validationCommands.map((command) => `- \`${command}\``),
    "",
    "## Expected Artifacts",
    "",
    ...plan.expectedArtifacts.map((artifact) => `- \`${artifact}\``),
    "",
    "## Lane Receipt Fragment",
    "",
    "```json",
    JSON.stringify(plan.laneReceiptFragment, null, 2),
    "```",
    "",
    "## Pass Criteria",
    "",
    ...plan.passCriteria.map((item) => `- ${item}`),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  );
  return lines.join("\n");
}

function runRuntimeSmokePlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildRuntimeSmokePlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderRuntimeSmokePlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

function runRuntimeBusinessSubmissionPlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildRuntimeBusinessSubmissionPlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderRuntimeBusinessSubmissionPlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (runtimeSmokePlanOnly) {
  runRuntimeSmokePlan();
}

if (runtimeSmokePlanMarkdownOnly) {
  runRuntimeSmokePlan({ markdown: true });
}

if (runtimeBusinessSubmissionPlanOnly) {
  runRuntimeBusinessSubmissionPlan();
}

if (runtimeBusinessSubmissionPlanMarkdownOnly) {
  runRuntimeBusinessSubmissionPlan({ markdown: true });
}

function dataSafetyCheckFromSubprocess() {
  const result = spawnSync("node", ["scripts/ddd-staging-data-safety-check.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  try {
    return JSON.parse(result.stdout);
  } catch {
    return {
      status: "BLOCKED",
      willWriteFiles: false,
      tracks: {},
      issues: [`data safety check returned non-JSON output: ${result.stderr.trim() || result.stdout.trim() || result.status}`],
    };
  }
}

function buildDataSafetyPlan({ rollupOverride = null } = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const dataSafetyCheck = dataSafetyCheckFromSubprocess();
  const rollbackTrack = checklist.tracks.find((track) => track.id === "p1-rollback") || {};
  const databaseTrack = checklist.tracks.find((track) => track.id === "p2-database-performance") || {};
  const gateById = new Map((rollup.items || []).map((item) => [item.id, item]));
  const trackPlans = [
    {
      id: "rollback",
      phase: "P1",
      owner: "bounded-context owners",
      status: dataSafetyCheck.tracks?.rollback?.status || gateById.get("rollback")?.status || "BLOCKED",
      blocker: dataSafetyCheck.tracks?.rollback?.issues?.[0] || gateById.get("rollback")?.issue || null,
      checks: dataSafetyCheck.tracks?.rollback?.checks || [],
      commands: dataSafetyCheck.tracks?.rollback?.nextCommands || rollbackTrack.commands || [],
      artifacts: rollbackTrack.artifacts || ["artifacts/ddd/rollback/rollback-drill.json"],
      acceptanceCommand: gateById.get("rollback")?.nextCommand || dataSafetyCheckCommand,
      requiredInputs: blockingInputsFromChecks(dataSafetyCheck.tracks?.rollback?.checks),
    },
    {
      id: "migration",
      phase: "P2",
      owner: "database",
      status: dataSafetyCheck.tracks?.migration?.status || gateById.get("migration")?.status || "BLOCKED",
      blocker: dataSafetyCheck.tracks?.migration?.issues?.[0] || gateById.get("migration")?.issue || null,
      checks: dataSafetyCheck.tracks?.migration?.checks || [],
      commands: dataSafetyCheck.tracks?.migration?.nextCommands || databaseTrack.commands || [],
      artifacts: ["artifacts/ddd/migration/migration-evidence.json"],
      acceptanceCommand: gateById.get("migration")?.nextCommand || dataSafetyCheckCommand,
      requiredInputs: blockingInputsFromChecks(dataSafetyCheck.tracks?.migration?.checks),
    },
    {
      id: "explain",
      phase: "P2",
      owner: "database",
      status: dataSafetyCheck.tracks?.explain?.status || gateById.get("explain")?.status || "BLOCKED",
      blocker: dataSafetyCheck.tracks?.explain?.issues?.[0] || gateById.get("explain")?.issue || null,
      checks: dataSafetyCheck.tracks?.explain?.checks || [],
      commands: dataSafetyCheck.tracks?.explain?.nextCommands || ["node scripts/ddd-collect-explain.mjs", "DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs"],
      artifacts: ["tmp/ddd-explain/*.json", "artifacts/ddd/release/explain-gate-report.json"],
      acceptanceCommand: gateById.get("explain")?.nextCommand || dataSafetyCheckCommand,
      requiredInputs: blockingInputsFromChecks(dataSafetyCheck.tracks?.explain?.checks),
    },
  ];
  const status = trackPlans.every((track) => track.status === "PASS") ? "PASS" : "BLOCKED";
  return {
    status,
    generatedAt,
    willWriteFiles: false,
    dataSafetyCheck,
    trackPlans,
    sharedInputs: [
      "DDD_EVIDENCE_ENVIRONMENT",
      "DDD_RELEASE_ENVIRONMENT",
      "DDD_RELEASE_CANDIDATE",
      "GITHUB_SHA",
      "DDD_EVIDENCE_OPERATOR",
      "GITHUB_ACTOR",
    ],
    commands: {
      preflight: [dataSafetyCheckCommand],
      rollback: trackPlans.find((track) => track.id === "rollback")?.commands || [],
      migration: trackPlans.find((track) => track.id === "migration")?.commands || [],
      explain: trackPlans.find((track) => track.id === "explain")?.commands || [],
      validate: [
        dataSafetyCheckCommand,
        "node scripts/ddd-release-readiness-summary.mjs",
        "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
        "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
      ],
    },
    expectedArtifacts: unique(trackPlans.flatMap((track) => track.artifacts || [])),
    safety: [
      "Rollback evidence must be PASS for each bounded context or have an approved unexpired deferral.",
      "Migration evidence must cover both fresh database and upgrade-from-previous-schema drills.",
      "EXPLAIN evidence must be collected from the production-equivalent database shape with read-only credentials.",
      "Regenerate readiness after rollback, migration, and EXPLAIN artifacts are refreshed.",
    ],
    nextCommand: status === "PASS" ? "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance" : dataSafetyCheckCommand,
  };
}

function renderDataSafetyPlanMarkdown(plan) {
  const lines = [
    "# DDD Data Safety Plan",
    "",
    `Status: ${plan.status}`,
    `Shared inputs: ${plan.sharedInputs.map((key) => `\`${key}\``).join(", ")}`,
    "",
    "| Track | Phase | Owner | Status | First blocker | Commands | Artifacts |",
    "| --- | --- | --- | --- | --- | --- | --- |",
    ...plan.trackPlans.map((track) => [
      track.id,
      track.phase,
      track.owner,
      track.status,
      track.blocker || "none",
      (track.commands || []).map((command) => `\`${command}\``).join("<br>") || "none",
      (track.artifacts || []).map((artifact) => `\`${artifact}\``).join("<br>") || "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Required Inputs",
    "",
    ...plan.trackPlans.flatMap((track) => [
      `### ${track.id}`,
      "",
      ...((track.requiredInputs || []).length > 0 ? track.requiredInputs.map((input) => `- \`${input}\``) : ["- none"]),
      "",
    ]),
    "## Validate",
    "",
    ...plan.commands.validate.map((command) => `- \`${command}\``),
    "",
    "## Safety",
    "",
    ...plan.safety.map((item) => `- ${item}`),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runDataSafetyPlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildDataSafetyPlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderDataSafetyPlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (dataSafetyPlanOnly) {
  runDataSafetyPlan();
}

if (dataSafetyPlanMarkdownOnly) {
  runDataSafetyPlan({ markdown: true });
}

function buildDataSafetyOwnerPlan({ dataSafetyPlanOverride = null, rollupOverride = null } = {}) {
  const dataSafetyPlan = dataSafetyPlanOverride || buildDataSafetyPlan({ rollupOverride });
  const trackById = new Map((dataSafetyPlan.trackPlans || []).map((track) => [track.id, track]));
  const rollbackTrack = trackById.get("rollback") || {};
  const migrationTrack = trackById.get("migration") || {};
  const explainTrack = trackById.get("explain") || {};
  const phases = [
    {
      id: "rollback-evidence-source",
      phase: "P1",
      owner: rollbackTrack.owner || "bounded-context owners",
      status: rollbackTrack.status || "BLOCKED",
      goal: "Provide rollback drill evidence or an approved rollback deferral for every affected bounded context.",
      requiredInputs: [
        "DDD_ROLLBACK_DRILL_FILE or DDD_ROLLBACK_DRILL_DEFERRAL_FILE",
        "DDD_ROLLBACK_DRILL_ENVIRONMENT or DDD_EVIDENCE_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT",
        "DDD_RELEASE_CANDIDATE or GITHUB_SHA",
        "DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR",
      ],
      commands: rollbackTrack.commands || [],
      artifacts: rollbackTrack.artifacts || [],
      acceptanceCommand: rollbackTrack.acceptanceCommand || dataSafetyCheckCommand,
      blocker: rollbackTrack.blocker || null,
      dependencies: [],
    },
    {
      id: "migration-fresh-drill",
      phase: "P2",
      owner: migrationTrack.owner || "database",
      status: migrationTrack.status || "BLOCKED",
      goal: "Validate the migration sequence against a fresh production-equivalent database and attach evidence.",
      requiredInputs: [
        "DDD_MIGRATION_FRESH_DB_VALIDATED=true",
        "DDD_MIGRATION_FRESH_DB_EVIDENCE",
        "DDD_MIGRATION_ENVIRONMENT or DDD_EVIDENCE_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT",
        "DDD_MIGRATION_OPERATOR or DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR",
      ],
      commands: migrationTrack.commands || [],
      artifacts: migrationTrack.artifacts || [],
      acceptanceCommand: migrationTrack.acceptanceCommand || dataSafetyCheckCommand,
      blocker: migrationTrack.blocker || null,
      dependencies: [],
    },
    {
      id: "migration-upgrade-drill",
      phase: "P2",
      owner: migrationTrack.owner || "database",
      status: migrationTrack.status || "BLOCKED",
      goal: "Validate upgrade migration from the previous production schema and attach evidence.",
      requiredInputs: [
        "DDD_MIGRATION_UPGRADE_DB_VALIDATED=true",
        "DDD_MIGRATION_UPGRADE_DB_EVIDENCE",
        "DDD_MIGRATION_COMPLETED_AT",
      ],
      commands: migrationTrack.commands || [],
      artifacts: migrationTrack.artifacts || [],
      acceptanceCommand: migrationTrack.acceptanceCommand || dataSafetyCheckCommand,
      blocker: migrationTrack.blocker || null,
      dependencies: ["migration-fresh-drill"],
    },
    {
      id: "explain-collect",
      phase: "P2",
      owner: explainTrack.owner || "database",
      status: explainTrack.status || "BLOCKED",
      goal: "Collect production-equivalent EXPLAIN evidence from the staging database with read-only credentials.",
      requiredInputs: [
        "DDD_EXPLAIN_DATABASE",
        "MYSQL_HOST",
        "MYSQL_PORT",
        "MYSQL_USER",
        "MYSQL_PASSWORD",
        "DDD_EXPLAIN_ENVIRONMENT or DDD_EVIDENCE_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT",
      ],
      commands: ["node scripts/ddd-collect-explain.mjs"],
      artifacts: ["tmp/ddd-explain/*.json"],
      acceptanceCommand: "DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs",
      blocker: explainTrack.blocker || null,
      dependencies: [],
    },
    {
      id: "explain-gate",
      phase: "P2",
      owner: explainTrack.owner || "database",
      status: explainTrack.status || "BLOCKED",
      goal: "Run strict EXPLAIN gate and write the release report after EXPLAIN artifacts are collected.",
      requiredInputs: [
        "DDD_RELEASE_CANDIDATE or GITHUB_SHA",
        "DDD_EVIDENCE_OPERATOR or GITHUB_ACTOR",
      ],
      commands: ["DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs"],
      artifacts: explainTrack.artifacts || ["artifacts/ddd/release/explain-gate-report.json"],
      acceptanceCommand: explainTrack.acceptanceCommand || dataSafetyCheckCommand,
      blocker: explainTrack.blocker || null,
      dependencies: ["explain-collect"],
    },
    {
      id: "data-safety-acceptance",
      phase: "P2",
      owner: "release-infra",
      status: dataSafetyPlan.status,
      goal: "Regenerate data safety status, readiness summary, evidence acceptance, and final review after all artifacts land.",
      requiredInputs: [],
      commands: dataSafetyPlan.commands.validate,
      artifacts: dataSafetyPlan.expectedArtifacts,
      acceptanceCommand: "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
      blocker: dataSafetyPlan.status === "PASS" ? null : "rollback, migration, or EXPLAIN evidence is not fully accepted",
      dependencies: [
        "rollback-evidence-source",
        "migration-upgrade-drill",
        "explain-gate",
      ],
    },
  ];
  return {
    status: dataSafetyPlan.status,
    generatedAt,
    willWriteFiles: false,
    dataSafetyPlanStatus: dataSafetyPlan.status,
    ownerCount: unique(phases.map((phase) => phase.owner)).length,
    owners: unique(phases.map((phase) => phase.owner)),
    phaseCount: phases.length,
    phases,
    parallelStart: phases.filter((phase) => phase.dependencies.length === 0 && phase.id !== "data-safety-acceptance").map((phase) => phase.id),
    validationCommands: dataSafetyPlan.commands.validate,
    safety: [
      "Use rollback deferrals only when accepted by release owners and bounded-context owners.",
      "Run migration and EXPLAIN evidence against production-equivalent staging data, never a developer-local database.",
      "Use read-only database credentials for EXPLAIN collection and keep populated credentials out of committed artifacts.",
    ],
    nextPhase: phases.find((phase) => phase.status !== "PASS") || phases.at(-1),
    nextCommand: dataSafetyPlan.nextCommand,
  };
}

function renderDataSafetyOwnerPlanMarkdown(plan) {
  const lines = [
    "# DDD Data Safety Owner Plan",
    "",
    `Status: ${plan.status}`,
    `Owners: ${plan.owners.map((owner) => `\`${owner}\``).join(", ")}`,
    `Next phase: ${plan.nextPhase?.id || "none"}`,
    "",
    "## Phases",
    "",
    "| Phase | Owner | Depends On | Required Inputs | Commands | Artifacts |",
    "| --- | --- | --- | --- | --- | --- |",
    ...plan.phases.map((phase) => [
      phase.id,
      phase.owner,
      phase.dependencies.length > 0 ? phase.dependencies.map((item) => `\`${item}\``).join(", ") : "none",
      phase.requiredInputs.length > 0 ? phase.requiredInputs.map((item) => `\`${item}\``).join(", ") : "none",
      phase.commands.length > 0 ? phase.commands.map((command) => `\`${command}\``).join("<br>") : "none",
      phase.artifacts.length > 0 ? phase.artifacts.map((artifact) => `\`${artifact}\``).join("<br>") : "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Parallel Start",
    "",
    ...plan.parallelStart.map((phase) => `- \`${phase}\``),
    "",
    "## Validate",
    "",
    ...plan.validationCommands.map((command) => `- \`${command}\``),
    "",
    "## Safety",
    "",
    ...plan.safety.map((item) => `- ${item}`),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runDataSafetyOwnerPlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildDataSafetyOwnerPlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderDataSafetyOwnerPlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (dataSafetyOwnerPlanOnly) {
  runDataSafetyOwnerPlan();
}

if (dataSafetyOwnerPlanMarkdownOnly) {
  runDataSafetyOwnerPlan({ markdown: true });
}

function buildDataSafetySubmissionPlan({ dataSafetyPlanOverride = null, dataSafetyOwnerPlanOverride = null, explainArtifactPlanOverride = null, rollupOverride = null } = {}) {
  const dataSafetyPlan = dataSafetyPlanOverride || buildDataSafetyPlan({ rollupOverride });
  const ownerPlan = dataSafetyOwnerPlanOverride || buildDataSafetyOwnerPlan({ dataSafetyPlanOverride: dataSafetyPlan, rollupOverride });
  const explainArtifactPlan = explainArtifactPlanOverride || buildExplainArtifactPlan({ rollupOverride });
  const acceptancePhase = ownerPlan.phases.find((phase) => phase.id === "data-safety-acceptance") || null;
  const ownerSubmissions = ownerPlan.phases
    .filter((phase) => phase.id !== "data-safety-acceptance")
    .map((phase) => ({
      owner: phase.owner,
      phase: phase.id,
      status: phase.status,
      goal: phase.goal,
      requiredInputs: phase.requiredInputs,
      commands: phase.commands,
      artifacts: phase.artifacts,
      acceptanceCommand: phase.acceptanceCommand,
      dependsOn: phase.dependencies,
      blocker: phase.blocker,
    }));
  return {
    status: ownerPlan.status,
    generatedAt,
    willWriteFiles: false,
    gate: "data-safety",
    owner: "platform-owners",
    owners: ownerPlan.owners,
    parallelStart: ownerPlan.parallelStart,
    ownerSubmissions,
    explainArtifact: {
      artifact: explainArtifactPlan.missingArtifact,
      artifactPresent: explainArtifactPlan.artifactPresent,
      dispatchOwners: explainArtifactPlan.dispatchOwners,
      requiredInputs: explainArtifactPlan.requiredInputs,
      envTemplate: explainArtifactPlan.envTemplate,
      commands: explainArtifactPlan.commands,
      passCriteria: explainArtifactPlan.passCriteria,
    },
    validationCommands: ownerPlan.validationCommands,
    expectedArtifacts: dataSafetyPlan.expectedArtifacts,
    laneReceiptFragment: {
      owner: "platform-owners",
      lane: "p1-p2-data-safety",
      status: ownerPlan.status === "PASS" ? "PASS" : "BLOCKED",
      providedArtifacts: dataSafetyPlan.expectedArtifacts,
      missingArtifacts: dataSafetyPlan.expectedArtifacts.filter((artifact) => artifact.includes("*")),
      completedAt: "<ISO-8601 timestamp after validation commands pass>",
      completedBy: "<owner or workflow actor>",
      acceptanceCommands: ownerPlan.validationCommands,
    },
    passCriteria: [
      "Rollback evidence or approved rollback deferral is accepted for every affected bounded context.",
      "Migration evidence covers both fresh database and upgrade-from-previous-schema drills.",
      "EXPLAIN collection writes `tmp/ddd-explain/*.json` and strict EXPLAIN gate writes `artifacts/ddd/release/explain-gate-report.json`.",
      "Data safety check and evidence acceptance pass after rollback, migration, and EXPLAIN artifacts are refreshed.",
      "Final review no longer reports rollback, migration, or explain gates as blocked.",
    ],
    acceptance: acceptancePhase ? {
      owner: acceptancePhase.owner,
      commands: acceptancePhase.commands,
      artifacts: acceptancePhase.artifacts,
      acceptanceCommand: acceptancePhase.acceptanceCommand,
      blocker: acceptancePhase.blocker,
    } : null,
    nextCommand: ownerPlan.nextCommand,
  };
}

function renderDataSafetySubmissionPlanMarkdown(plan) {
  const lines = [
    "# DDD Data Safety Submission Plan",
    "",
    `Status: ${plan.status}`,
    `Owner: ${plan.owner}`,
    `Gate: ${plan.gate}`,
    `Owners: ${plan.owners.map((owner) => `\`${owner}\``).join(", ")}`,
    "",
    "## Owner Submissions",
    "",
    "| Phase | Owner | Depends On | Required Inputs | Commands | Artifacts |",
    "| --- | --- | --- | --- | --- | --- |",
    ...plan.ownerSubmissions.map((item) => [
      item.phase,
      item.owner,
      item.dependsOn.length > 0 ? item.dependsOn.map((dependency) => `\`${dependency}\``).join(", ") : "none",
      item.requiredInputs.length > 0 ? item.requiredInputs.map((input) => `\`${input}\``).join(", ") : "none",
      item.commands.length > 0 ? item.commands.map((command) => `\`${command}\``).join("<br>") : "none",
      item.artifacts.length > 0 ? item.artifacts.map((artifact) => `\`${artifact}\``).join("<br>") : "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## EXPLAIN Artifact",
    "",
    `Artifact: \`${plan.explainArtifact.artifact}\``,
    `Present: ${plan.explainArtifact.artifactPresent}`,
    `Dispatch owners: ${plan.explainArtifact.dispatchOwners.map((owner) => `\`${owner}\``).join(", ") || "none"}`,
    "",
    "Required inputs:",
    "",
    ...plan.explainArtifact.requiredInputs.map((input) => `- \`${input}\``),
    "",
    "Env template:",
    "",
    "```env",
    ...plan.explainArtifact.envTemplate,
    "```",
    "",
    "Commands:",
    "",
    ...plan.explainArtifact.commands.map((command) => `- \`${command}\``),
    "",
    "## Validation Commands",
    "",
    ...plan.validationCommands.map((command) => `- \`${command}\``),
    "",
    "## Expected Artifacts",
    "",
    ...plan.expectedArtifacts.map((artifact) => `- \`${artifact}\``),
    "",
    "## Lane Receipt Fragment",
    "",
    "```json",
    JSON.stringify(plan.laneReceiptFragment, null, 2),
    "```",
    "",
    "## Pass Criteria",
    "",
    ...plan.passCriteria.map((item) => `- ${item}`),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runDataSafetySubmissionPlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildDataSafetySubmissionPlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderDataSafetySubmissionPlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (dataSafetySubmissionPlanOnly) {
  runDataSafetySubmissionPlan();
}

if (dataSafetySubmissionPlanMarkdownOnly) {
  runDataSafetySubmissionPlan({ markdown: true });
}

function buildCutoverRehearsalPlan({ rollupOverride = null } = {}) {
  const generatedAt = new Date().toISOString();
  const rollup = rollupOverride || loadReadinessRollup();
  const closurePlan = buildClosurePlan({ rollupOverride: rollup });
  const releaseEnvPlan = buildReleaseEnvPlan({ rollupOverride: rollup });
  const runtimeBusinessPlan = buildRuntimeBusinessPlan({ rollupOverride: rollup });
  const dataSafetyPlan = buildDataSafetyPlan({ rollupOverride: rollup });
  const acceptance = buildEvidenceAcceptance({ rollupOverride: rollup });
  const gateById = new Map((rollup.items || []).map((item) => [item.id, item]));
  const trackById = new Map((dataSafetyPlan.trackPlans || []).map((track) => [track.id, track]));
  const phase = (id, title, owner, status, blocker, commands, artifacts = [], dependsOn = []) => ({
    id,
    title,
    owner,
    status,
    blocker: blocker || null,
    dependsOn,
    commands: unique(commands),
    artifacts: unique(artifacts),
  });
  const dockerGate = gateById.get("docker-images") || {};
  const runtimeGate = gateById.get("runtime-business") || {};
  const phases = [
    phase(
      "p0-release-env",
      "Secure release env initialized and linted",
      "release-infra",
      releaseEnvPlan.status,
      releaseEnvPlan.envInitCheck?.issues?.[0] || gateById.get("release-env")?.issue || null,
      [
        "node scripts/ddd-release-env-init.mjs --check",
        "node scripts/ddd-release-env-init.mjs",
        "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
        "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs",
      ],
      ["artifacts/ddd/release/release-config-evidence.json"],
    ),
    phase(
      "p0-docker-images",
      "Deployable Docker images built or inspected",
      "release-infra",
      dockerGate.status || "BLOCKED",
      dockerGate.issue || null,
      [
        "node scripts/ddd-docker-build-evidence.mjs --check",
        "DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs",
      ],
      ["artifacts/ddd/release/docker-image-evidence.json"],
      ["p0-release-env"],
    ),
    phase(
      "p1-runtime-business",
      "HTTPS staging runtime and business smokes accepted",
      runtimeGate.owner || "release-infra, frontend, ai, file-owner, job-owner, payment-owner",
      runtimeBusinessPlan.status,
      runtimeBusinessPlan.runtimeCheck?.issues?.[0] || runtimeGate.issue || null,
      [
        "node scripts/ddd-staging-runtime-check.mjs",
        ...((runtimeBusinessPlan.smokeSteps || []).map((step) => step.command)),
      ],
      (runtimeBusinessPlan.smokeSteps || []).map((step) => step.artifact),
      ["p0-release-env", "p0-docker-images"],
    ),
    phase(
      "p1-rollback",
      "Rollback drill evidence accepted",
      "bounded-context owners",
      trackById.get("rollback")?.status || "BLOCKED",
      trackById.get("rollback")?.blocker || null,
      trackById.get("rollback")?.commands || [],
      trackById.get("rollback")?.artifacts || [],
      ["p1-runtime-business"],
    ),
    phase(
      "p2-migration",
      "Fresh and upgrade migration drills accepted",
      "database",
      trackById.get("migration")?.status || "BLOCKED",
      trackById.get("migration")?.blocker || null,
      trackById.get("migration")?.commands || [],
      trackById.get("migration")?.artifacts || [],
      ["p0-release-env"],
    ),
    phase(
      "p2-explain",
      "Production-equivalent EXPLAIN evidence accepted",
      "database",
      trackById.get("explain")?.status || "BLOCKED",
      trackById.get("explain")?.blocker || null,
      trackById.get("explain")?.commands || [],
      trackById.get("explain")?.artifacts || [],
      ["p0-release-env"],
    ),
    phase(
      "final-review",
      "Release-owner final review and strict gate",
      "release-owner",
      rollup.status === "PASS" && acceptance.acceptedCount === acceptance.itemCount ? "PASS" : "BLOCKED",
      acceptance.acceptedCount === acceptance.itemCount ? null : `accepted=${acceptance.acceptedCount}/${acceptance.itemCount}`,
      [
        "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
        "node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle-verify",
        "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
        "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
      ],
      [
        "artifacts/ddd/release/staging-handoff-bundle/manifest.json",
        "artifacts/ddd/release/staging-handoff-bundle/final-review.json",
      ],
      ["p1-runtime-business", "p1-rollback", "p2-migration", "p2-explain"],
    ),
  ];
  const blockedPhases = phases.filter((item) => item.status !== "PASS");
  const nextPhase = blockedPhases[0] || phases.at(-1);
  return {
    status: blockedPhases.length === 0 ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    cutoverReady: blockedPhases.length === 0 && rollup.cutoverAllowed === true,
    finalRecommendation: rollup.finalRecommendation,
    eta: closurePlan.eta,
    phaseCount: phases.length,
    blockedPhaseCount: blockedPhases.length,
    acceptedGateCount: acceptance.acceptedCount,
    acceptedGateTotal: acceptance.itemCount,
    phases,
    validationCommands: [
      "node scripts/ddd-production-readiness-preflight.mjs --static-only --no-report",
      "node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle",
      "node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle-verify",
      "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
    ],
    nextPhase: nextPhase
      ? {
        id: nextPhase.id,
        title: nextPhase.title,
        owner: nextPhase.owner,
        blocker: nextPhase.blocker,
        command: nextPhase.commands[0] || null,
      }
      : null,
  };
}

function renderCutoverRehearsalPlanMarkdown(plan) {
  const lines = [
    "# DDD Cutover Rehearsal Plan",
    "",
    `Status: ${plan.status}`,
    `Cutover ready: ${plan.cutoverReady}`,
    `Final recommendation: ${plan.finalRecommendation}`,
    `ETA: ${plan.eta}`,
    `Accepted gates: ${plan.acceptedGateCount}/${plan.acceptedGateTotal}`,
    `Blocked phases: ${plan.blockedPhaseCount}/${plan.phaseCount}`,
    "",
    "## Phases",
    "",
    "| Phase | Status | Owner | Depends on | First command | Blocker |",
    "| --- | --- | --- | --- | --- | --- |",
    ...plan.phases.map((phaseItem) => [
      phaseItem.title,
      phaseItem.status,
      phaseItem.owner,
      phaseItem.dependsOn.join(", ") || "none",
      phaseItem.commands[0] ? `\`${phaseItem.commands[0]}\`` : "none",
      phaseItem.blocker || "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Validation Commands",
    "",
    ...commandList(plan.validationCommands).split("\n"),
    "",
    `Next: \`${plan.nextPhase?.command || "none"}\``,
    "",
  ];
  return lines.join("\n");
}

function runCutoverRehearsalPlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildCutoverRehearsalPlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderCutoverRehearsalPlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (cutoverRehearsalPlanOnly) {
  runCutoverRehearsalPlan();
}

if (cutoverRehearsalPlanMarkdownOnly) {
  runCutoverRehearsalPlan({ markdown: true });
}

function buildBlockingInputs({ rollupOverride = null, owner = "" } = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const inputMap = new Map();
  for (const gate of rollup.items || []) {
    if (gate.status === "PASS") continue;
    if (owner && !trackMatchesOwner({ id: gate.track, owner: gate.owner }, owner)) continue;
    for (const input of gate.blockingInputs || []) {
      if (!inputMap.has(input)) {
        inputMap.set(input, {
          input,
          gates: [],
          owners: new Set(),
          tracks: new Set(),
          nextCommands: new Set(),
          firstBlockers: [],
        });
      }
      const entry = inputMap.get(input);
      entry.gates.push({
        gate: gate.id,
        track: gate.track,
        owner: gate.owner,
        status: gate.status,
        blocker: gate.issue || null,
        nextCommand: gate.nextCommand || null,
      });
      for (const owner of String(gate.owner || "").split(",").map((item) => item.trim()).filter(Boolean)) {
        entry.owners.add(owner);
      }
      if (gate.track) entry.tracks.add(gate.track);
      if (gate.nextCommand) entry.nextCommands.add(gate.nextCommand);
      if (gate.issue) entry.firstBlockers.push(gate.issue);
    }
  }
  const inputs = [...inputMap.values()]
    .map((entry) => ({
      input: entry.input,
      gateCount: entry.gates.length,
      owners: [...entry.owners].sort(),
      tracks: [...entry.tracks].sort(),
      nextCommands: [...entry.nextCommands].sort(),
      firstBlockers: unique(entry.firstBlockers),
      gates: entry.gates,
    }))
    .sort((left, right) => {
      const countDiff = right.gateCount - left.gateCount;
      return countDiff || left.input.localeCompare(right.input);
    });
  const blockedGateIds = unique(inputs.flatMap((input) => input.gates.map((gate) => gate.gate)));
  return {
    status: inputs.length === 0 ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    ownerFilter: owner || null,
    finalRecommendation: rollup.finalRecommendation,
    cutoverAllowed: rollup.cutoverAllowed,
    inputCount: inputs.length,
    blockedGateCount: blockedGateIds.length,
    blockedGates: blockedGateIds,
    inputs,
    nextCommand: inputs.length === 0
      ? "node scripts/ddd-staging-execution-checklist.mjs --rollup-enforce"
      : "node scripts/ddd-staging-execution-checklist.mjs --evidence-env-template",
  };
}

function renderBlockingInputsMarkdown(report) {
  const lines = [
    `# DDD Staging Blocking Inputs${report.ownerFilter ? `: ${report.ownerFilter}` : ""}`,
    "",
    `Status: ${report.status}`,
    `Final recommendation: ${report.finalRecommendation}`,
    `Cutover allowed: ${report.cutoverAllowed}`,
    `Blocking inputs: ${report.inputCount}`,
    `Blocked gates: ${report.blockedGateCount}`,
    "",
    "| Input | Gates | Owners | Next commands |",
    "| --- | --- | --- | --- |",
    ...report.inputs.map((input) => [
      `\`${input.input}\``,
      input.gates.map((gate) => `\`${gate.gate}\``).join(", "),
      input.owners.join(", ") || "none",
      input.nextCommands.map((command) => `\`${command}\``).join("<br>") || "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Gate Details",
    "",
  ];
  for (const input of report.inputs) {
    lines.push(
      `### ${input.input}`,
      "",
      ...(input.gates || []).map((gate) => `- ${gate.gate}: owner=${gate.owner}; blocker=${gate.blocker || "none"}; next=${gate.nextCommand || "none"}`),
      "",
    );
  }
  lines.push(`Next: \`${report.nextCommand}\``, "");
  return lines.join("\n");
}

function placeholderForBlockingInput(input) {
  if (/(_VALIDATED|_EXPECT_|EXPECT_)/.test(input)) return "true";
  if (input.endsWith("_ENVIRONMENT")) return "staging";
  if (input === "MYSQL_PORT") return "3306";
  if (input.endsWith("_BASE_URL") || input === "PLAYWRIGHT_BASE_URL" || input === "LUMIRA_BASE_URL") return "__REQUIRED_HTTPS__";
  if (input.includes("PASSWORD") || input.includes("SECRET") || input.includes("TOKEN") || input.endsWith("_KEY")) return "__REQUIRED_SECRET_REF__";
  if (input.includes("IMAGE") && !input.includes("EVIDENCE")) return "__REQUIRED_IMAGE_REF__";
  if (input === "DDD_RELEASE_CANDIDATE" || input === "GITHUB_SHA") return "__REQUIRED_SHA_OR_TAG__";
  if (input.includes("COMPLETED_AT")) return "__REQUIRED_ISO_TIMESTAMP__";
  return "__REQUIRED__";
}

function renderBlockingInputsEnvTemplate(report) {
  const lines = [
    "# Lumira DDD staging blocking input environment template.",
    "# Generated from the current staging blocking-input reverse index.",
    "# Fill values in a secure env file or CI secret store. Do not commit populated secrets.",
    `# Status: ${report.status}`,
    `# Owner filter: ${report.ownerFilter || "all"}`,
    `# Blocking inputs: ${report.inputCount}`,
    `# Blocked gates: ${report.blockedGateCount}`,
    "",
  ];
  for (const input of report.inputs) {
    lines.push(
      `# Gates: ${input.gates.map((gate) => gate.gate).join(", ")}`,
      `# Owners: ${input.owners.join(", ") || "none"}`,
      `# Next: ${input.nextCommands[0] || "none"}`,
      `${input.input}=${placeholderForBlockingInput(input.input)}`,
      "",
    );
  }
  lines.push(
    "# Validation sequence",
    "# node scripts/ddd-staging-execution-checklist.mjs --rollup",
    "# node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs",
    "",
  );
  return lines.join("\n");
}

function envKeyCandidates(input) {
  return String(input || "")
    .split(/\s+or\s+/i)
    .map((item) => item.trim())
    .filter((item) => /^[A-Z0-9_]+(?:=true)?$/.test(item))
    .map((item) => item.replace(/=true$/, ""));
}

function renderNextActionEnvTemplate(report) {
  const lines = [
    "# Lumira DDD staging next-action environment template.",
    "# Generated from the immediate staging owner action queue.",
    "# Fill values in a secure env file or CI secret store. Do not commit populated secrets.",
    `# Status: ${report.status}`,
    `# Next command: ${report.nextCommand}`,
    "",
  ];
  for (const item of report.immediateActions || []) {
    lines.push(
      `# Lane: ${item.lane}`,
      `# Owner: ${item.owner}`,
      `# Action: ${item.title}`,
      `# Command: ${item.command || "none"}`,
      `# Source: ${item.sourcePlan}`,
    );
    const seen = new Set();
    for (const input of item.blockingInputs || []) {
      const keys = envKeyCandidates(input);
      if (keys.length === 0) {
        lines.push(`# Manual input: ${input}`);
        continue;
      }
      if (keys.length > 1) {
        lines.push(`# Choose one of: ${keys.join(", ")}`);
      }
      for (const key of keys) {
        if (seen.has(key)) continue;
        seen.add(key);
        lines.push(`${key}=${placeholderForBlockingInput(key)}`);
      }
    }
    lines.push("");
  }
  lines.push(
    "# Validation sequence",
    "# node scripts/ddd-staging-execution-checklist.mjs --next-action-queue",
    "# node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
    "# node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
    "",
  );
  return lines.join("\n");
}

function portableEnvFile(file) {
  if (!file) return null;
  const absolute = path.resolve(repoRoot, file);
  if (absolute === repoRoot) return ".";
  if (absolute.startsWith(`${repoRoot}${path.sep}`)) return relative(absolute);
  return file;
}

function parseSimpleEnvFile(file) {
  const issues = [];
  const env = {};
  const duplicateKeys = [];
  const seen = new Set();
  if (!file) {
    return { env, keys: [], duplicateKeys, issues: ["DDD_NEXT_ACTION_ENV_FILE or --next-action-env-file is required"] };
  }
  const resolved = path.resolve(repoRoot, file);
  if (!fs.existsSync(resolved)) {
    return { env, keys: [], duplicateKeys, issues: [`env file does not exist: ${portableEnvFile(file)}`] };
  }
  const lines = fs.readFileSync(resolved, "utf8").split(/\r?\n/);
  for (const [index, rawLine] of lines.entries()) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const match = line.match(/^(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$/);
    if (!match) {
      issues.push(`line ${index + 1} must be KEY=value, export KEY=value, or a comment`);
      continue;
    }
    const key = match[1];
    let value = match[2].trim();
    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (seen.has(key)) duplicateKeys.push(key);
    seen.add(key);
    env[key] = value;
  }
  return { env, keys: [...seen].sort(), duplicateKeys: unique(duplicateKeys), issues };
}

function envValueIssueForKey(key, value) {
  if (!value || !String(value).trim()) return `${key} is required`;
  if (/^__REQUIRED/.test(String(value))) return `${key} still contains a placeholder`;
  if (/(_VALIDATED|_EXPECT_|EXPECT_)/.test(key) && value !== "true") return `${key} must be true`;
  if ((key.endsWith("_BASE_URL") || key === "PLAYWRIGHT_BASE_URL" || key === "LUMIRA_BASE_URL") && !/^https:\/\//i.test(value)) {
    return `${key} must be an HTTPS URL`;
  }
  return null;
}

function buildNextActionEnvCheck({ queueOverride = null, envFile = nextActionEnvFile } = {}) {
  const queue = queueOverride || buildNextActionQueue();
  const parsed = parseSimpleEnvFile(envFile);
  const laneChecks = (queue.immediateActions || []).map((item) => {
    const inputChecks = (item.blockingInputs || []).map((input) => {
      const keys = envKeyCandidates(input);
      if (keys.length === 0) {
        return {
          input,
          keys: [],
          selectedKey: null,
          pass: false,
          issues: [`manual input requires review: ${input}`],
        };
      }
      const keyChecks = keys.map((key) => ({
        key,
        valuePresent: Object.prototype.hasOwnProperty.call(parsed.env, key),
        valueAccepted: !envValueIssueForKey(key, parsed.env[key] || ""),
        issue: envValueIssueForKey(key, parsed.env[key] || ""),
      }));
      const passing = keyChecks.find((check) => !check.issue) || null;
      return {
        input,
        keys,
        selectedKey: passing?.key || null,
        pass: Boolean(passing),
        issues: passing ? [] : keyChecks.map((check) => check.issue || `${check.key} is accepted`).filter(Boolean),
      };
    });
    const issues = inputChecks.flatMap((check) => check.issues);
    return {
      lane: item.lane,
      owner: item.owner,
      sourcePlan: item.sourcePlan,
      command: item.command,
      status: issues.length === 0 ? "PASS" : "BLOCKED",
      inputChecks,
      issues,
    };
  });
  const issues = [
    ...parsed.issues,
    ...parsed.duplicateKeys.map((key) => `duplicate env key: ${key}`),
    ...laneChecks.flatMap((lane) => lane.issues.map((issue) => `${lane.lane}: ${issue}`)),
  ];
  return {
    status: issues.length === 0 ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    envFile: portableEnvFile(envFile),
    keyCount: parsed.keys.length,
    duplicateKeys: parsed.duplicateKeys,
    laneChecks,
    issues,
    nextCommand: issues.length === 0
      ? "node scripts/ddd-staging-execution-checklist.mjs --next-action-queue"
      : "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-template",
  };
}

function sha256FileIfPresent(file) {
  if (!file) return null;
  const resolved = path.resolve(repoRoot, file);
  if (!fs.existsSync(resolved)) return null;
  return createHash("sha256").update(fs.readFileSync(resolved)).digest("hex");
}

function buildNextActionEnvReceipt({ checkOverride = null, envFile = nextActionEnvFile } = {}) {
  const check = checkOverride || buildNextActionEnvCheck({ envFile });
  const laneReceipts = (check.laneChecks || []).map((lane) => {
    const inputCount = lane.inputChecks.length;
    const passedInputCount = lane.inputChecks.filter((input) => input.pass).length;
    const selectedKeys = lane.inputChecks.map((input) => input.selectedKey).filter(Boolean).sort();
    const candidateKeyCount = lane.inputChecks.reduce((count, input) => count + input.keys.length, 0);
    return {
      lane: lane.lane,
      owner: lane.owner,
      sourcePlan: lane.sourcePlan,
      status: lane.status,
      inputCount,
      passedInputCount,
      selectedKeys,
      candidateKeyCount,
      issueCount: lane.issues.length,
      firstIssue: lane.issues[0] || null,
    };
  });
  const requiredSelectedKeys = unique(laneReceipts.flatMap((lane) => lane.selectedKeys)).sort();
  const requiredSelectedKeyCount = requiredSelectedKeys.length;
  return {
    status: check.status,
    generatedAt,
    willWriteFiles: false,
    redacted: true,
    envFile: check.envFile,
    envFileSha256: sha256FileIfPresent(envFile),
    keyCount: check.keyCount,
    duplicateKeys: check.duplicateKeys,
    requiredSelectedKeyCount,
    requiredSelectedKeys,
    laneReceipts,
    issueCount: check.issues.length,
    issues: check.issues,
    passCriteria: [
      "status must be PASS",
      "duplicateKeys must be empty",
      "all laneReceipts must have status PASS",
      "envFileSha256 must be present for the validated file",
      "receipt must not include env values",
    ],
    nextCommand: check.status === "PASS"
      ? "node scripts/ddd-staging-execution-checklist.mjs --next-action-verification-plan-markdown"
      : "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-template",
  };
}

function renderNextActionEnvReceiptMarkdown(receipt) {
  const lines = [
    "# DDD Next Action Env Receipt",
    "",
    `Status: ${receipt.status}`,
    `Redacted: ${receipt.redacted}`,
    `Env file: ${receipt.envFile ? `\`${receipt.envFile}\`` : "not provided"}`,
    `Env file SHA-256: ${receipt.envFileSha256 ? `\`${receipt.envFileSha256}\`` : "missing"}`,
    `Keys present: ${receipt.keyCount}`,
    `Required selected keys: ${receipt.requiredSelectedKeyCount}`,
    `Issues: ${receipt.issueCount}`,
    "",
    "| Lane | Owner | Status | Inputs | Selected keys | First issue |",
    "| --- | --- | --- | ---: | --- | --- |",
    ...receipt.laneReceipts.map((lane) => [
      lane.lane,
      lane.owner,
      lane.status,
      `${lane.passedInputCount}/${lane.inputCount}`,
      lane.selectedKeys.length > 0 ? lane.selectedKeys.map((key) => `\`${key}\``).join(", ") : "none",
      lane.firstIssue || "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Pass Criteria",
    "",
    ...receipt.passCriteria.map((item) => `- ${item}`),
    "",
    `Next: \`${receipt.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runNextActionEnvReceipt({ markdown = false } = {}) {
  let receipt;
  try {
    receipt = buildNextActionEnvReceipt();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderNextActionEnvReceiptMarkdown(receipt));
    process.exit(receipt.status === "PASS" ? 0 : 1);
  }
  if (nextActionEnvReceiptOutput) {
    const outputPath = path.resolve(repoRoot, nextActionEnvReceiptOutput);
    fs.mkdirSync(path.dirname(outputPath), { recursive: true });
    fs.writeFileSync(outputPath, `${JSON.stringify(receipt, null, 2)}\n`);
    const contract = buildNextActionEnvReceiptContract({ receiptFile: outputPath });
    console.log(JSON.stringify(contract, null, 2));
    process.exit(receipt.status === "PASS" && contract.status === "PASS" ? 0 : 1);
  }
  console.log(JSON.stringify(receipt, null, 2));
  process.exit(receipt.status === "PASS" ? 0 : 1);
}

function readReceiptContractInput(file) {
  if (!file) {
    return { receipt: null, text: "", issues: ["DDD_NEXT_ACTION_ENV_RECEIPT_FILE or --next-action-env-receipt-file is required"] };
  }
  const resolved = path.resolve(repoRoot, file);
  if (!fs.existsSync(resolved)) {
    return { receipt: null, text: "", issues: [`receipt file does not exist: ${portableEnvFile(file)}`] };
  }
  const text = fs.readFileSync(resolved, "utf8");
  try {
    return { receipt: JSON.parse(text), text, issues: [] };
  } catch (error) {
    return { receipt: null, text, issues: [`receipt file must be valid JSON: ${error.message}`] };
  }
}

function buildNextActionEnvReceiptContract({ receiptFile = nextActionEnvReceiptFile } = {}) {
  const { receipt, text, issues } = readReceiptContractInput(receiptFile);
  if (receipt) {
    if (receipt.redacted !== true) issues.push("receipt.redacted must be true");
    if (!["PASS", "BLOCKED"].includes(receipt.status)) issues.push("receipt.status must be PASS or BLOCKED");
    if (receipt.status === "PASS" && !/^[a-f0-9]{64}$/.test(String(receipt.envFileSha256 || ""))) {
      issues.push("PASS receipt must include envFileSha256");
    }
    if (!Array.isArray(receipt.duplicateKeys)) issues.push("receipt.duplicateKeys must be an array");
    if (Array.isArray(receipt.duplicateKeys) && receipt.duplicateKeys.length > 0) issues.push("receipt.duplicateKeys must be empty");
    if (!Array.isArray(receipt.requiredSelectedKeys)) issues.push("receipt.requiredSelectedKeys must be an array");
    if (!Array.isArray(receipt.laneReceipts) || receipt.laneReceipts.length === 0) {
      issues.push("receipt.laneReceipts must be a non-empty array");
    }
    for (const [index, lane] of (receipt.laneReceipts || []).entries()) {
      if (!lane.lane) issues.push(`laneReceipts[${index}].lane is required`);
      if (!lane.owner) issues.push(`laneReceipts[${index}].owner is required`);
      if (!["PASS", "BLOCKED"].includes(lane.status)) issues.push(`laneReceipts[${index}].status must be PASS or BLOCKED`);
      if (!Number.isInteger(lane.inputCount)) issues.push(`laneReceipts[${index}].inputCount must be an integer`);
      if (!Number.isInteger(lane.passedInputCount)) issues.push(`laneReceipts[${index}].passedInputCount must be an integer`);
      if (lane.status === "PASS" && lane.passedInputCount !== lane.inputCount) {
        issues.push(`laneReceipts[${index}] PASS must have passedInputCount=inputCount`);
      }
      if (!Array.isArray(lane.selectedKeys)) issues.push(`laneReceipts[${index}].selectedKeys must be an array`);
    }
    if (receipt.status === "PASS" && (receipt.laneReceipts || []).some((lane) => lane.status !== "PASS")) {
      issues.push("PASS receipt requires every lane receipt to PASS");
    }
  }
  if (/https?:\/\//i.test(text)) issues.push("receipt text must not include URLs");
  if (/\b[A-Z][A-Z0-9_]*=(?!<redacted>|__REQUIRED|true\b|false\b)[^\s",}]+/.test(text)) {
    issues.push("receipt text must not include env assignments with values");
  }
  if (/\b(TOKEN|SECRET|PASSWORD|API_KEY)\b/i.test(text)) issues.push("receipt text must not include secret-like key names");
  return {
    status: issues.length === 0 ? "PASS" : "FAIL",
    generatedAt,
    willWriteFiles: false,
    receiptFile: portableEnvFile(receiptFile),
    receiptStatus: receipt?.status || null,
    redacted: receipt?.redacted === true,
    laneCount: Array.isArray(receipt?.laneReceipts) ? receipt.laneReceipts.length : 0,
    issueCount: issues.length,
    issues,
  };
}

function runNextActionEnvReceiptContract() {
  let report;
  try {
    report = buildNextActionEnvReceiptContract();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  console.log(JSON.stringify(report, null, 2));
  process.exit(report.status === "PASS" ? 0 : 1);
}

function runNextActionEnvCheck() {
  let report;
  try {
    report = buildNextActionEnvCheck();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  console.log(JSON.stringify(report, null, 2));
  process.exit(report.status === "PASS" ? 0 : 1);
}

function runNextActionEnvTemplate() {
  let report;
  try {
    report = buildNextActionQueue();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  process.stdout.write(renderNextActionEnvTemplate(report));
  process.exit(0);
}

if (nextActionEnvTemplateOnly) {
  runNextActionEnvTemplate();
}

if (nextActionEnvCheckOnly) {
  runNextActionEnvCheck();
}

if (nextActionEnvReceiptOnly) {
  runNextActionEnvReceipt();
}

if (nextActionEnvReceiptMarkdownOnly) {
  runNextActionEnvReceipt({ markdown: true });
}

if (nextActionEnvReceiptContractOnly) {
  runNextActionEnvReceiptContract();
}

function buildNextActionVerificationPlan({ rollupOverride = null } = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const queue = buildNextActionQueue({ rollupOverride: rollup });
  const envCheckCommand = "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-check --next-action-env-file=<env-file>";
  const envReceiptCommand = "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file> --next-action-env-receipt-output=<receipt-file>";
  const envReceiptMarkdownCommand = "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-markdown --next-action-env-file=<env-file>";
  const envReceiptContractCommand = "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>";
  const phases = [
    {
      id: "verify-first-wave-env",
      owner: "release-infra",
      status: "BLOCKED",
      command: envCheckCommand,
      followUpCommand: envReceiptCommand,
      contractCommand: envReceiptContractCommand,
      expectedStatus: "PASS",
      sourcePlan: "next-action.template.env",
      artifacts: ["artifacts/ddd/release/staging-handoff-bundle/next-action.template.env", "redacted next-action env receipt"],
      notes: ["Run this before heavier checks; it catches placeholders, duplicate keys, non-HTTPS URLs, and false flags.", "After it passes, generate the redacted receipt and run the receipt contract before release-owner evidence submission."],
    },
    {
      id: "verify-release-env",
      owner: "release-infra",
      status: rollup.items?.find((item) => item.id === "release-env")?.status || "BLOCKED",
      command: "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
      expectedStatus: "PASS",
      sourcePlan: "release-env-plan.json",
      artifacts: ["artifacts/ddd/release/release-env-lint.json", "artifacts/ddd/release/release-config-evidence.json"],
      notes: ["Run config evidence after lint passes: DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs"],
    },
    {
      id: "verify-docker-images",
      owner: "release-infra",
      status: rollup.items?.find((item) => item.id === "docker-images")?.status || "BLOCKED",
      command: "node scripts/ddd-docker-build-evidence.mjs --check",
      expectedStatus: "PASS",
      sourcePlan: "docker-image-plan.json",
      artifacts: ["artifacts/ddd/release/docker-build-evidence.json"],
      notes: ["Use a Docker-enabled runner or existing-image inspect evidence when local Docker is unavailable."],
    },
    {
      id: "verify-runtime",
      owner: "release-infra, frontend, ai",
      status: rollup.items?.find((item) => item.id === "runtime-business")?.status || "BLOCKED",
      command: "node scripts/ddd-staging-runtime-check.mjs",
      expectedStatus: "PASS",
      sourcePlan: "runtime-smoke-plan.json",
      artifacts: ["artifacts/ddd/readiness/summary.json", "artifacts/ddd/frontend/frontend-smoke.json", "artifacts/ddd/ai/ai-runtime-drill.json"],
      notes: ["After this passes, run the owner-specific runtime smoke commands listed in runtime-smoke-plan.md."],
    },
    {
      id: "verify-data-safety",
      owner: "bounded-context owners, database",
      status: (rollup.items || []).some((item) => ["rollback", "migration", "explain"].includes(item.id) && item.status !== "PASS") ? "BLOCKED" : "PASS",
      command: "node scripts/ddd-staging-data-safety-check.mjs",
      expectedStatus: "PASS",
      sourcePlan: "data-safety-owner-plan.json",
      artifacts: ["artifacts/ddd/rollback/rollback-drill.json", "artifacts/ddd/migration/migration-evidence.json", "artifacts/ddd/release/explain-gate-report.json"],
      notes: ["Run rollback, migration, and EXPLAIN owner commands from data-safety-owner-plan.md before final acceptance."],
    },
    {
      id: "verify-final-acceptance",
      owner: "release-infra",
      status: rollup.status,
      command: "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
      expectedStatus: "PASS",
      sourcePlan: "final-review.json",
      artifacts: ["artifacts/ddd/release/staging-handoff-bundle/final-review.json", "artifacts/ddd/release/release-final-go-no-go.json"],
      notes: ["Run evidence acceptance and rollup enforce before final review when all lane evidence is refreshed."],
    },
  ];
  return {
    status: phases.every((phase) => phase.status === "PASS") ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: rollup.finalRecommendation,
    cutoverAllowed: rollup.cutoverAllowed,
    queueStatus: queue.status,
    envFile: "<env-file>",
    phaseCount: phases.length,
    blockedPhaseCount: phases.filter((phase) => phase.status !== "PASS").length,
    phases,
    commands: phases.map((phase) => phase.command),
    receiptCommand: envReceiptCommand,
    receiptMarkdownCommand: envReceiptMarkdownCommand,
    receiptContractCommand: envReceiptContractCommand,
    nextPhase: phases.find((phase) => phase.status !== "PASS") || phases.at(-1),
    nextCommand: phases.find((phase) => phase.status !== "PASS")?.command || "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    safety: [
      "Do not run final review until every phase reports PASS from real staging evidence.",
      "Keep populated env files outside committed artifacts.",
      `Use \`${envReceiptMarkdownCommand}\` only for human-readable status; submit the JSON receipt written by receiptCommand.`,
      "Use source plans for owner-specific smoke and evidence commands.",
    ],
  };
}

function renderNextActionVerificationPlanMarkdown(plan) {
  const lines = [
    "# DDD Next Action Verification Plan",
    "",
    `Status: ${plan.status}`,
    `Final recommendation: ${plan.finalRecommendation}`,
    `Cutover allowed: ${plan.cutoverAllowed}`,
    `Blocked phases: ${plan.blockedPhaseCount}/${plan.phaseCount}`,
    `Next command: \`${plan.nextCommand}\``,
    "",
    "| Phase | Owner | Status | Command | Follow-up | Source |",
    "| --- | --- | --- | --- | --- | --- |",
    ...plan.phases.map((phase) => [
      phase.id,
      phase.owner,
      phase.status,
      `\`${phase.command}\``,
      [phase.followUpCommand, phase.contractCommand].filter(Boolean).map((command) => `\`${command}\``).join("<br>") || "none",
      `\`${phase.sourcePlan}\``,
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Safety",
    "",
    ...plan.safety.map((item) => `- ${item}`),
    "",
  ];
  return lines.join("\n");
}

function runNextActionVerificationPlan({ markdown = false } = {}) {
  let report;
  try {
    report = buildNextActionVerificationPlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderNextActionVerificationPlanMarkdown(report));
  } else {
    console.log(JSON.stringify(report, null, 2));
  }
  process.exit(0);
}

if (nextActionVerificationPlanOnly) {
  runNextActionVerificationPlan();
}

if (nextActionVerificationPlanMarkdownOnly) {
  runNextActionVerificationPlan({ markdown: true });
}

function buildOwnerBlockingInputsEnvReport(owner, { rollupOverride = null } = {}) {
  const report = buildBlockingInputs({ rollupOverride, owner: owner.owner });
  const existingInputs = new Set(report.inputs.map((input) => input.input));
  const ownerInputEntries = (owner.keys || [])
    .filter((input) => !existingInputs.has(input))
    .map((input) => ({
      input,
      gateCount: 1,
      owners: [owner.owner],
      tracks: ["p0-release-env"],
      nextCommands: ["DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs"],
      firstBlockers: [`owner handoff input required for ${owner.owner}`],
      gates: [{
        gate: "release-env",
        track: "p0-release-env",
        owner: owner.owner,
        status: "BLOCKED",
        blocker: `owner handoff input required for ${owner.owner}`,
        nextCommand: "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
      }],
    }));
  const inputs = [...ownerInputEntries, ...report.inputs];
  const blockedGateIds = unique(inputs.flatMap((input) => input.gates.map((gate) => gate.gate)));
  return {
    ...report,
    status: inputs.length === 0 ? "PASS" : "BLOCKED",
    inputCount: inputs.length,
    blockedGateCount: blockedGateIds.length,
    blockedGates: blockedGateIds,
    inputs,
  };
}

function runBlockingInputs({ markdown = false } = {}) {
  let report;
  try {
    report = buildBlockingInputs({ owner: ownerFilter });
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderBlockingInputsMarkdown(report));
  } else {
    console.log(JSON.stringify(report, null, 2));
  }
  process.exit(0);
}

function runBlockingInputsEnvTemplate() {
  let report;
  try {
    report = buildBlockingInputs({ owner: ownerFilter });
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  process.stdout.write(renderBlockingInputsEnvTemplate(report));
  process.exit(0);
}

if (blockingInputsOnly) {
  runBlockingInputs();
}

if (blockingInputsMarkdownOnly) {
  runBlockingInputs({ markdown: true });
}

if (blockingInputsEnvTemplateOnly) {
  runBlockingInputsEnvTemplate();
}

function buildReleaseEvidenceDispatchPlan({ rollupOverride = null, blockingInputsOverride = null } = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const blockingInputs = blockingInputsOverride || buildBlockingInputs({ rollupOverride: rollup });
  const inputNames = new Set(blockingInputs.inputs.map((input) => input.input));
  const workflowInputs = [
    ["mode", true, rollup.cutoverAllowed ? "run" : "plan", "operator", "READY", "Use plan until staging evidence and lane receipt coverage are green."],
    ["strict", true, true, "operator", "READY", "Keep strict=true for production-equivalent evidence."],
    ["github_environment", true, "staging", "operator", "READY", "Must point at the GitHub environment that stores production-equivalent secrets."],
    ["evidence_environment", true, "staging", "operator", "READY", "Copied into DDD_EVIDENCE_ENVIRONMENT and DDD_RELEASE_ENVIRONMENT checks."],
    ["backend_base_url", true, "__REQUIRED_HTTPS__", "LUMIRA_BASE_URL", inputNames.has("LUMIRA_BASE_URL") ? "BLOCKED" : "READY", "Maps to LUMIRA_BASE_URL for runtime and backend evidence."],
    ["frontend_base_url", true, "__REQUIRED_HTTPS__", "PLAYWRIGHT_BASE_URL", inputNames.has("PLAYWRIGHT_BASE_URL") ? "BLOCKED" : "READY", "Maps to PLAYWRIGHT_BASE_URL and CORS_ALLOWED_ORIGIN_PATTERNS."],
    ["ai_base_url", false, "", "LUMIRA_AI_BASE_URL", "READY", "Leave empty to reuse backend_base_url unless AI has a separate endpoint."],
    ["max_artifact_age_hours", true, "24", "operator", "READY", "Tighten only after the evidence workflow is running frequently."],
    ["expect_ai_remote", true, true, "DDD_AI_EXPECT_PROVIDER_REMOTE, DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE", inputNames.has("DDD_AI_EXPECT_PROVIDER_REMOTE") || inputNames.has("DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE") ? "BLOCKED" : "READY", "Keep true for production-equivalent remote AI evidence."],
    ["expect_frontend_deployed", true, true, "DDD_FRONTEND_EXPECT_DEPLOYED", inputNames.has("DDD_FRONTEND_EXPECT_DEPLOYED") ? "BLOCKED" : "READY", "Keep true for deployed frontend smoke evidence."],
    ["promote_authenticated_baseline", true, false, "operator", "READY", "Switch to true only when the authenticated runtime actual is approved."],
    ["baseline_accepted_by", false, "", "operator", "READY", "Leave empty to use the workflow actor."],
    ["lane_completion_receipt_file", false, "", "DDD_LANE_COMPLETION_RECEIPT_FILE", laneCompletionReceiptFile ? "READY" : "BLOCKED", "Use only when the receipt already exists in the workflow workspace."],
    ["lane_completion_receipt_base64", false, "__REQUIRED_AFTER_COVERAGE_5_OF_5__", "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>", "BLOCKED", "Preferred workflow_dispatch path after receipt contract and coverage pass."],
  ].map(([input, required, suggestedValue, source, status, note]) => ({
    input,
    required,
    suggestedValue,
    source,
    status,
    notes: [note],
  }));
  const blockedInputs = workflowInputs.filter((input) => input.status !== "READY");
  return {
    status: blockedInputs.length === 0 && rollup.cutoverAllowed ? "READY" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    workflow: ".github/workflows/ddd-release-evidence.yml",
    workflowDispatch: true,
    finalRecommendation: rollup.finalRecommendation,
    cutoverAllowed: rollup.cutoverAllowed,
    blockedInputCount: blockedInputs.length,
    inputs: workflowInputs,
    requiredBeforeRun: [
      "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
    ],
    nextCommand: "node scripts/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown",
  };
}

function renderReleaseEvidenceDispatchPlanMarkdown(plan) {
  const lines = [
    "# DDD Release Evidence Dispatch Plan",
    "",
    `Status: ${plan.status}`,
    `Workflow: \`${plan.workflow}\``,
    `Final recommendation: ${plan.finalRecommendation}`,
    `Cutover allowed: ${plan.cutoverAllowed}`,
    `Blocked inputs: ${plan.blockedInputCount}`,
    "",
    "| Input | Required | Status | Suggested value | Source |",
    "| --- | --- | --- | --- | --- |",
    ...plan.inputs.map((input) => [
      `\`${input.input}\``,
      input.required ? "yes" : "no",
      input.status,
      `\`${String(input.suggestedValue)}\``,
      `\`${input.source}\``,
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Required Before Run",
    "",
    ...commandList(plan.requiredBeforeRun).split("\n"),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function buildReleaseEvidenceDispatchInputs(plan = buildReleaseEvidenceDispatchPlan()) {
  const inputs = {};
  for (const input of plan.inputs) {
    inputs[input.input] = input.suggestedValue;
  }
  return {
    status: plan.status,
    generatedAt,
    willWriteFiles: false,
    workflow: plan.workflow,
    blockedInputCount: plan.blockedInputCount,
    payload: inputs,
    blockedInputs: plan.inputs
      .filter((input) => input.status !== "READY")
      .map((input) => ({
        input: input.input,
        suggestedValue: input.suggestedValue,
        source: input.source,
        notes: input.notes || [],
      })),
    validationCommands: plan.requiredBeforeRun,
    dispatchInputValidationCommand: "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-inputs-contract --release-evidence-dispatch-inputs-file=<inputs-file>",
    nextCommand: "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-command",
  };
}

function renderReleaseEvidenceDispatchCommand(dispatchInputs) {
  const payload = dispatchInputs.payload || {};
  const lines = [
    "gh workflow run ddd-release-evidence.yml \\",
    ...Object.entries(payload).map(([key, value], index, entries) => {
      const suffix = index === entries.length - 1 ? "" : " \\";
      return `  -f ${key}=${String(value)}${suffix}`;
    }),
    "",
  ];
  return lines.join("\n");
}

function isPlaceholderValue(value) {
  return typeof value === "string" && /__REQUIRED|<.+>/.test(value);
}

function readReleaseEvidenceDispatchInputsContractInput(file) {
  if (!file) {
    return { document: null, issues: ["DDD_RELEASE_EVIDENCE_DISPATCH_INPUTS_FILE or --release-evidence-dispatch-inputs-file is required"] };
  }
  const resolved = path.resolve(repoRoot, file);
  if (!fs.existsSync(resolved)) {
    return { document: null, issues: [`dispatch inputs file does not exist: ${portableEnvFile(file)}`] };
  }
  const text = fs.readFileSync(resolved, "utf8");
  try {
    return { document: JSON.parse(text), issues: [] };
  } catch (error) {
    return { document: null, issues: [`dispatch inputs file must be valid JSON: ${error.message}`] };
  }
}

function buildReleaseEvidenceDispatchInputsContract({ inputsFile = releaseEvidenceDispatchInputsFile } = {}) {
  const { document, issues } = readReleaseEvidenceDispatchInputsContractInput(inputsFile);
  const payload = document?.payload && typeof document.payload === "object" ? document.payload : document;
  if (!payload || typeof payload !== "object") {
    issues.push("dispatch inputs must be an object or include a payload object");
  } else {
    const requiredInputs = [
      "mode",
      "strict",
      "github_environment",
      "evidence_environment",
      "backend_base_url",
      "frontend_base_url",
      "max_artifact_age_hours",
      "expect_ai_remote",
      "expect_frontend_deployed",
      "promote_authenticated_baseline",
      "lane_completion_receipt_file",
      "lane_completion_receipt_base64",
    ];
    for (const input of requiredInputs) {
      if (!Object.hasOwn(payload, input)) {
        issues.push(`payload missing required workflow input: ${input}`);
      }
    }
    if (!["plan", "run"].includes(String(payload.mode || ""))) {
      issues.push("payload.mode must be plan or run");
    }
    for (const input of ["strict", "expect_ai_remote", "expect_frontend_deployed", "promote_authenticated_baseline"]) {
      if (!["true", "false"].includes(String(payload[input]))) {
        issues.push(`payload.${input} must be true or false`);
      }
    }
    for (const input of ["backend_base_url", "frontend_base_url"]) {
      const value = String(payload[input] || "");
      if (!value) {
        issues.push(`payload.${input} is required`);
      } else if (isPlaceholderValue(value)) {
        issues.push(`payload.${input} must replace placeholder value`);
      } else if (!/^https:\/\//i.test(value)) {
        issues.push(`payload.${input} must be HTTPS`);
      }
    }
    for (const input of ["github_environment", "evidence_environment", "max_artifact_age_hours"]) {
      const value = String(payload[input] ?? "");
      if (!value || isPlaceholderValue(value)) {
        issues.push(`payload.${input} must be populated`);
      }
    }
    if (!/^\d+$/.test(String(payload.max_artifact_age_hours || ""))) {
      issues.push("payload.max_artifact_age_hours must be an integer string");
    }
    const receiptFile = String(payload.lane_completion_receipt_file || "");
    const receiptBase64 = String(payload.lane_completion_receipt_base64 || "");
    if ((!receiptFile || isPlaceholderValue(receiptFile)) && (!receiptBase64 || isPlaceholderValue(receiptBase64))) {
      issues.push("payload must include lane_completion_receipt_file or lane_completion_receipt_base64");
    }
    if (receiptBase64 && !isPlaceholderValue(receiptBase64) && !/^[A-Za-z0-9+/=]+$/.test(receiptBase64)) {
      issues.push("payload.lane_completion_receipt_base64 must be base64 text");
    }
  }
  return {
    status: issues.length === 0 ? "PASS" : "FAIL",
    generatedAt,
    willWriteFiles: false,
    inputsFile: portableEnvFile(inputsFile),
    workflow: ".github/workflows/ddd-release-evidence.yml",
    issueCount: issues.length,
    issues,
  };
}

function runReleaseEvidenceDispatchPlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildReleaseEvidenceDispatchPlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderReleaseEvidenceDispatchPlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (releaseEvidenceDispatchPlanOnly) {
  runReleaseEvidenceDispatchPlan();
}

if (releaseEvidenceDispatchPlanMarkdownOnly) {
  runReleaseEvidenceDispatchPlan({ markdown: true });
}

function runReleaseEvidenceDispatchInputs() {
  let dispatchInputs;
  try {
    dispatchInputs = buildReleaseEvidenceDispatchInputs();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  console.log(JSON.stringify(dispatchInputs, null, 2));
  process.exit(0);
}

function runReleaseEvidenceDispatchCommand() {
  let dispatchInputs;
  try {
    dispatchInputs = buildReleaseEvidenceDispatchInputs();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  process.stdout.write(renderReleaseEvidenceDispatchCommand(dispatchInputs));
  process.exit(0);
}

if (releaseEvidenceDispatchInputsOnly) {
  runReleaseEvidenceDispatchInputs();
}

if (releaseEvidenceDispatchCommandOnly) {
  runReleaseEvidenceDispatchCommand();
}

function runReleaseEvidenceDispatchInputsContract() {
  let report;
  try {
    report = buildReleaseEvidenceDispatchInputsContract();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  console.log(JSON.stringify(report, null, 2));
  process.exit(report.status === "PASS" ? 0 : 1);
}

if (releaseEvidenceDispatchInputsContractOnly) {
  runReleaseEvidenceDispatchInputsContract();
}

function firstIssue(check) {
  return check?.issues?.[0] || null;
}

function blockingInputsFromChecks(checks = []) {
  return unique(checks.flatMap((check) => {
    if (!check?.issue) return [];
    if (Array.isArray(check.keys) && check.keys.length > 0) return check.keys;
    if (check.name) return [check.name];
    return [];
  }));
}

function blockingInputsFromRuntimeCheck(check = {}) {
  return unique([
    ...(check.urlChecks || []).flatMap((item) => (item.issues || []).length > 0 ? [item.name] : []),
    ...(check.evidenceChecks || []).flatMap((item) => item.issue ? [item.name] : []),
    ...(check.expectationChecks || []).flatMap((item) => item.issue ? [item.name] : []),
  ]);
}

function buildReadinessRollup(dispatch) {
  const releaseEnvTrack = checklist.tracks.find((track) => track.id === "p0-release-env");
  const releaseEnvStatus = dispatch.releaseEnvCheck?.status || (checklist.releaseEnv.ready ? "PASS" : "BLOCKED");
  const items = [
    {
      id: "release-env",
      track: "p0-release-env",
      owner: "release-infra",
      status: releaseEnvStatus,
      nextCommand: dispatch.releaseEnvCheck?.nextCommand || releaseEnvTrack?.commands?.[0] || envInitCheckCommand,
      issue: dispatch.releaseEnvCheck?.issue
        || (releaseEnvStatus === "PASS" ? null : releaseEnvTrack?.reason || firstIssue(dispatch.envInitCheck)),
      blockingInputs: dispatch.releaseEnvCheck?.blockingInputs || (releaseEnvStatus === "PASS" ? [] : ["DDD_RELEASE_ENV_FILE"]),
    },
    {
      id: "docker-images",
      track: "p0-images",
      owner: "release-infra",
      status: dispatch.dockerEvidenceCheck?.status || "UNKNOWN",
      nextCommand: dockerEvidenceCheckCommand,
      issue: firstIssue(dispatch.dockerEvidenceCheck),
      blockingInputs: unique([
        ...(dispatch.dockerEvidenceCheck?.existingImageBuildEvidencePresent === false ? ["DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE"] : []),
        ...(dispatch.dockerEvidenceCheck?.existingImageInputs || [])
          .filter((input) => input.valuePresent !== true)
          .map((input) => input.envKey),
      ]),
      recommendedMode: dispatch.dockerEvidenceCheck?.recommendedMode || null,
    },
    {
      id: "runtime-business",
      track: "p1-runtime-business",
      owner: "release-infra, frontend, ai, file-owner, job-owner, payment-owner",
      status: dispatch.runtimeStagingCheck?.status || "UNKNOWN",
      nextCommand: runtimeStagingCheckCommand,
      issue: firstIssue(dispatch.runtimeStagingCheck),
      blockingInputs: blockingInputsFromRuntimeCheck(dispatch.runtimeStagingCheck),
    },
    {
      id: "rollback",
      track: "p1-rollback",
      owner: "bounded-context owners",
      status: dispatch.dataSafetyCheck?.tracks?.rollback?.status || "UNKNOWN",
      nextCommand: dataSafetyCheckCommand,
      issue: firstIssue(dispatch.dataSafetyCheck?.tracks?.rollback),
      blockingInputs: blockingInputsFromChecks(dispatch.dataSafetyCheck?.tracks?.rollback?.checks),
    },
    {
      id: "migration",
      track: "p2-database-performance",
      owner: "database",
      status: dispatch.dataSafetyCheck?.tracks?.migration?.status || "UNKNOWN",
      nextCommand: dataSafetyCheckCommand,
      issue: firstIssue(dispatch.dataSafetyCheck?.tracks?.migration),
      blockingInputs: blockingInputsFromChecks(dispatch.dataSafetyCheck?.tracks?.migration?.checks),
    },
    {
      id: "explain",
      track: "p2-database-performance",
      owner: "database",
      status: dispatch.dataSafetyCheck?.tracks?.explain?.status || "UNKNOWN",
      nextCommand: dataSafetyCheckCommand,
      issue: firstIssue(dispatch.dataSafetyCheck?.tracks?.explain),
      blockingInputs: blockingInputsFromChecks(dispatch.dataSafetyCheck?.tracks?.explain?.checks),
    },
  ];
  return {
    status: items.every((item) => item.status === "PASS") ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: dispatch.finalRecommendation,
    cutoverAllowed: dispatch.cutoverAllowed,
    ownerCount: dispatch.ownerCount,
    blockedCount: items.filter((item) => item.status !== "PASS").length,
    items,
    nextCommand: dispatch.copyReadyCommand,
  };
}

function renderRollupMarkdown(rollup) {
  const lines = [
    "# DDD Staging Readiness Rollup",
    "",
    `Status: ${rollup.status}`,
    `Final recommendation: ${rollup.finalRecommendation}`,
    `Cutover allowed: ${rollup.cutoverAllowed}`,
    `Blocked: ${rollup.blockedCount}/${rollup.items.length}`,
    "",
    "| Gate | Track | Owner | Status | First blocker | Blocking inputs | Next command |",
    "| --- | --- | --- | --- | --- | --- | --- |",
    ...rollup.items.map((item) => [
      item.id,
      item.track,
      item.owner,
      item.status,
      item.issue || "none",
      item.blockingInputs?.length > 0 ? item.blockingInputs.map((input) => `\`${input}\``).join(", ") : "none",
      `\`${item.nextCommand}\``,
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    `Next: \`${rollup.nextCommand}\``,
    "",
  ];
  return `${lines.join("\n")}`;
}

function loadDispatchCheck() {
  return buildDispatchCheck(checklist);
}

function loadReadinessRollup() {
  return buildReadinessRollup(loadDispatchCheck());
}

let cachedReadinessRollup = null;

function getReadinessRollupCached() {
  if (!cachedReadinessRollup) {
    cachedReadinessRollup = loadReadinessRollup();
  }
  return cachedReadinessRollup;
}

function runRollup({ markdown = false, enforce = false } = {}) {
  let rollup;
  try {
    rollup = loadReadinessRollup();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderRollupMarkdown(rollup));
  } else {
    console.log(JSON.stringify(rollup, null, 2));
  }
  process.exit(enforce && rollup.status !== "PASS" ? 1 : 0);
}

if (rollupOnly) {
  runRollup();
}

if (rollupMarkdownOnly) {
  runRollup({ markdown: true });
}

if (rollupEnforce) {
  runRollup({ enforce: true });
}

function buildExecutionStatus({ rollupOverride = null, handoffBundleCheckOverride = null } = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const nextActionQueue = buildNextActionQueue({ rollupOverride: rollup });
  const gaps = checklist.tracks
    .filter((track) => track.status !== "ready")
    .map((track) => ({
      id: track.id,
      owner: track.owner,
      reason: track.reason,
      nextCommand: [...(track.setupCommands || []), ...(track.commands || [])][0] || null,
    }));
  const handoffBundleCheck = handoffBundleCheckOverride || verifyHandoffBundleResult();
  const status = rollup.status === "PASS" && handoffBundleCheck.status === "PASS" ? "PASS" : "BLOCKED";
  return {
    status,
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: rollup.finalRecommendation,
    cutoverAllowed: rollup.cutoverAllowed,
    blockedGateCount: rollup.blockedCount,
    gateCount: rollup.items.length,
    gapCount: gaps.length,
    handoffBundle: {
      status: handoffBundleCheck.status,
      bundleDir: handoffBundleCheck.bundleDir,
      manifest: handoffBundleCheck.manifest,
      checkedFileCount: handoffBundleCheck.checkedFileCount,
      issues: handoffBundleCheck.issues,
    },
    gates: rollup.items,
    gaps,
    laneRoutes: (nextActionQueue.queue || []).map((lane) => ({
      order: lane.order,
      lane: lane.lane,
      owner: lane.owner,
      dispatchOwner: lane.dispatchOwner || lane.owner,
      status: lane.status,
      command: lane.command || null,
      sourcePlan: lane.sourcePlan || null,
      missingEvidenceArtifactCount: lane.missingEvidenceArtifactCount ?? 0,
    })),
    nextCommand: status === "PASS"
      ? "node scripts/ddd-staging-execution-checklist.mjs --rollup-enforce"
      : "node scripts/ddd-staging-execution-checklist.mjs --commands",
  };
}

function renderExecutionStatusMarkdown(status) {
  const lines = [
    "# DDD Staging Execution Status",
    "",
    `Status: ${status.status}`,
    `Final recommendation: ${status.finalRecommendation}`,
    `Cutover allowed: ${status.cutoverAllowed}`,
    `Blocked gates: ${status.blockedGateCount}/${status.gateCount}`,
    `Evidence gaps: ${status.gapCount}`,
    `Handoff bundle: ${status.handoffBundle.status}`,
    "",
    "| Gate | Owner | Status | First blocker | Next command |",
    "| --- | --- | --- | --- | --- |",
    ...status.gates.map((gate) => [
      gate.id,
      gate.owner,
      gate.status,
      gate.issue || "none",
      `\`${gate.nextCommand}\``,
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Lane Routes",
    "",
    ...((status.laneRoutes || []).length > 0
      ? [
        "| Order | Lane | Owner | Status | Source | Command |",
        "| ---: | --- | --- | --- | --- | --- |",
        ...(status.laneRoutes || []).map((lane) => [
          lane.order,
          `\`${lane.lane}\``,
          lane.dispatchOwner || lane.owner || "unknown",
          lane.status,
          lane.sourcePlan ? `\`${lane.sourcePlan}\`` : "none",
          lane.command ? `\`${lane.command}\`` : "none",
        ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
      ]
      : ["- none"]),
    "",
    "## Handoff Bundle",
    "",
    `- Directory: \`${status.handoffBundle.bundleDir}\``,
    `- Manifest: ${status.handoffBundle.manifest ? `\`${status.handoffBundle.manifest}\`` : "missing"}`,
    `- Checked files: ${status.handoffBundle.checkedFileCount}`,
    ...(status.handoffBundle.issues.length > 0
      ? status.handoffBundle.issues.map((issue) => `- ${issue}`)
      : ["- no bundle integrity issues"]),
    "",
    `Next: \`${status.nextCommand}\``,
    "",
  ];
  return `${lines.join("\n")}`;
}

function runExecutionStatus({ markdown = false } = {}) {
  let status;
  try {
    status = buildExecutionStatus();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderExecutionStatusMarkdown(status));
  } else {
    console.log(JSON.stringify(status, null, 2));
  }
  process.exit(0);
}

function renderHandoffSummaryMarkdown(rollup, status) {
  const lines = [
    "## DDD Staging Handoff",
    "",
    "Artifact: `ddd-staging-handoff-bundle`",
    "",
    renderRollupMarkdown(rollup).trimEnd(),
    "",
    "## Execution Status",
    "",
    renderExecutionStatusMarkdown(status).trimEnd(),
    "",
  ];
  return lines.join("\n");
}

function runHandoffSummaryMarkdown() {
  let rollup;
  let status;
  try {
    rollup = loadReadinessRollup();
    status = buildExecutionStatus();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  process.stdout.write(renderHandoffSummaryMarkdown(rollup, status));
  process.exit(0);
}

if (executionStatusOnly) {
  runExecutionStatus();
}

if (executionStatusMarkdownOnly) {
  runExecutionStatus({ markdown: true });
}

if (handoffSummaryMarkdownOnly) {
  runHandoffSummaryMarkdown();
}

function buildFinalReview({
  rollupOverride = null,
  handoffBundleCheckOverride = null,
  ownerDispatchOverride = null,
  laneReceiptContractOverride = undefined,
} = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const executionStatus = buildExecutionStatus({ rollupOverride: rollup, handoffBundleCheckOverride });
  const acceptance = buildEvidenceAcceptance({ rollupOverride: rollup });
  const blockingInputs = buildBlockingInputs({ rollupOverride: rollup });
  const laneReceiptContract = laneReceiptContractOverride !== undefined
    ? laneReceiptContractOverride
    : (laneCompletionReceiptFile ? buildLaneCompletionReceiptContract({ receiptFile: laneCompletionReceiptFile }) : null);
  const laneReceiptCoverage = buildLaneReceiptCoverage({ laneReceiptContract, rollupOverride: rollup });
  const evidenceClosureBoard = buildEvidenceClosureBoard({ receiptFile: laneReceiptContract?.receiptFile || "", rollupOverride: rollup });
  const laneReceiptPassed = laneReceiptContract?.status === "PASS"
    && laneReceiptContract?.receiptStatus === "PASS"
    && laneReceiptCoverage.status === "PASS";
  const ownerDispatchPath = path.join(handoffBundleDir, "owner-dispatch.json");
  const ownerDispatch = ownerDispatchOverride || readJson(ownerDispatchPath, null);
  const ownerRows = Array.isArray(ownerDispatch?.owners)
    ? ownerDispatch.owners.map((owner) => ({
      owner: owner.owner,
      markdown: owner.markdown || null,
      json: owner.json || null,
      blockingInputsEnvTemplate: owner.blockingInputsEnvTemplate || null,
      evidenceGapCount: owner.evidenceGapCount ?? null,
      missingEvidenceArtifactCount: owner.missingEvidenceArtifactCount ?? null,
      blockingInputCount: owner.blockingInputCount ?? null,
      laneCount: owner.laneCount ?? (Array.isArray(owner.lanes) ? owner.lanes.length : 0),
      lanes: Array.isArray(owner.lanes)
        ? owner.lanes.map((lane) => ({
          order: lane.order,
          lane: lane.lane,
          status: lane.status,
          title: lane.title || null,
          command: lane.command || null,
          sourcePlan: lane.sourcePlan || null,
          acceptanceCommands: lane.acceptanceCommands || [],
          expectedArtifacts: lane.expectedArtifacts || [],
          missingArtifacts: lane.missingArtifacts || [],
          missingEvidenceArtifactCount: lane.missingEvidenceArtifactCount ?? 0,
        }))
        : [],
      nextCommand: owner.nextCommand || null,
    }))
    : [];
  const expectedOwnerCount = selectedOwnerPackets.length;
  const ownerTemplateCount = ownerRows.filter((owner) => owner.blockingInputsEnvTemplate).length;
  const ownerDispatchReady = ownerRows.length === expectedOwnerCount
    && ownerTemplateCount === expectedOwnerCount
    && ownerRows.every((owner) => owner.markdown && owner.json && owner.blockingInputsEnvTemplate);
  const checklistItems = [
    {
      id: "handoff-bundle-integrity",
      label: "Handoff bundle verifies",
      passed: executionStatus.handoffBundle.status === "PASS",
      evidence: executionStatus.handoffBundle.manifest || "manifest missing",
      blocker: executionStatus.handoffBundle.issues[0] || null,
    },
    {
      id: "owner-dispatch-templates",
      label: "Owner dispatch includes Markdown, JSON, and env templates",
      passed: ownerDispatchReady,
      evidence: relative(ownerDispatchPath),
      blocker: ownerDispatchReady ? null : `owner dispatch templates incomplete: owners=${ownerRows.length}/${expectedOwnerCount}; templates=${ownerTemplateCount}/${expectedOwnerCount}`,
    },
    {
      id: "owner-lane-completion-receipt",
      label: "Owner lane completion receipt contract passes",
      passed: laneReceiptPassed,
      evidence: laneReceiptContract?.receiptFile || "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>",
      blocker: laneReceiptPassed
        ? null
        : (laneReceiptContract?.issues[0]
          || (laneReceiptContract ? laneReceiptCoverageBlocker(laneReceiptCoverage) : null)
          || (laneReceiptContract ? `receiptStatus=${laneReceiptContract.receiptStatus || "missing"}` : "lane completion receipt file not provided")),
    },
    {
      id: "staging-evidence-accepted",
      label: "All staging evidence gates accepted",
      passed: acceptance.acceptedCount === acceptance.itemCount,
      evidence: "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
      blocker: acceptance.acceptedCount === acceptance.itemCount ? null : `accepted=${acceptance.acceptedCount}/${acceptance.itemCount}`,
    },
    {
      id: "cutover-allowed",
      label: "Final rollup allows cutover",
      passed: rollup.status === "PASS" && rollup.cutoverAllowed === true,
      evidence: "node scripts/ddd-staging-execution-checklist.mjs --rollup-enforce",
      blocker: rollup.status === "PASS" && rollup.cutoverAllowed === true ? null : `${rollup.finalRecommendation}; blocked=${rollup.blockedCount}/${rollup.items.length}`,
    },
  ];
  const cutoverReady = checklistItems.every((item) => item.passed);
  const blockers = rollup.items
    .filter((gate) => gate.status !== "PASS")
    .map((gate) => ({
      gate: gate.id,
      track: gate.track,
      owner: gate.owner,
      firstBlocker: gate.issue || null,
      blockingInputs: gate.blockingInputs || [],
      nextCommand: gate.nextCommand || null,
    }));
  const finalReviewLane = ownerRows
    .flatMap((owner) => owner.lanes || [])
    .find((lane) => lane.lane === "final-review") || null;
  const finalReviewLaneExpectedArtifacts = unique([
    ...(finalReviewLane?.expectedArtifacts || []),
    "artifacts/ddd/release/staging-handoff-bundle/final-review.json",
  ].map((artifact) => portableEnvFile(artifact) || artifact));
  return {
    status: cutoverReady ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    cutoverReady,
    finalRecommendation: rollup.finalRecommendation,
    cutoverAllowed: rollup.cutoverAllowed,
    blockedGateCount: rollup.blockedCount,
    gateCount: rollup.items.length,
    acceptedGateCount: acceptance.acceptedCount,
    acceptedGateTotal: acceptance.itemCount,
    handoffBundle: executionStatus.handoffBundle,
    ownerDispatch: {
      status: ownerDispatchReady ? "PASS" : "BLOCKED",
      ownerCount: ownerRows.length,
      expectedOwnerCount,
      ownerTemplateCount,
      owners: ownerRows,
    },
    laneCompletionReceipt: laneReceiptContract
      ? {
        status: laneReceiptContract.status,
        receiptStatus: laneReceiptContract.receiptStatus,
        receiptFile: laneReceiptContract.receiptFile,
        redacted: laneReceiptContract.redacted,
        laneCount: laneReceiptContract.laneCount,
        coverage: laneReceiptCoverage,
        issues: laneReceiptContract.issues,
      }
      : {
        status: "MISSING",
        receiptStatus: null,
        receiptFile: null,
        redacted: false,
        laneCount: 0,
        coverage: laneReceiptCoverage,
        issues: ["lane completion receipt file not provided"],
      },
    evidenceClosureBoard: {
      status: evidenceClosureBoard.status,
      laneCount: evidenceClosureBoard.laneCount,
      closedLaneCount: evidenceClosureBoard.closedLaneCount,
      openLaneCount: evidenceClosureBoard.openLaneCount,
      nextLane: evidenceClosureBoard.nextLane
        ? {
          key: evidenceClosureBoard.nextLane.key,
          status: evidenceClosureBoard.nextLane.status,
          receiptStatus: evidenceClosureBoard.nextLane.receiptStatus,
          nextCommand: evidenceClosureBoard.nextLane.nextCommand,
          sourcePlan: evidenceClosureBoard.nextLane.sourcePlan,
        }
        : null,
      issues: evidenceClosureBoard.issues,
    },
    laneReceiptFragment: {
      owner: "release-infra",
      lane: "final-review",
      status: cutoverReady ? "PASS" : "BLOCKED",
      providedArtifacts: finalReviewLaneExpectedArtifacts,
      missingArtifacts: cutoverReady ? [] : (finalReviewLane?.missingArtifacts || []),
      completedAt: "<ISO-8601 timestamp after final review enforce passes>",
      completedBy: "<owner or workflow actor>",
      acceptanceCommands: [
        "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown --lane-completion-receipt-file=<receipt-file>",
        "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>",
        "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
        "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
      ],
    },
    checklist: checklistItems,
    blockers,
    topBlockingInputs: blockingInputs.inputs.slice(0, 12).map((input) => ({
      input: input.input,
      gateCount: input.gateCount,
      owners: input.owners,
      nextCommand: input.nextCommands[0] || null,
    })),
    nextCommand: cutoverReady
      ? "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh"
      : executionStatus.nextCommand,
  };
}

function renderFinalReviewMarkdown(review) {
  const lines = [
    "# DDD Release Owner Final Review",
    "",
    `Status: ${review.status}`,
    `Cutover ready: ${review.cutoverReady}`,
    `Final recommendation: ${review.finalRecommendation}`,
    `Cutover allowed: ${review.cutoverAllowed}`,
    `Accepted gates: ${review.acceptedGateCount}/${review.acceptedGateTotal}`,
    `Blocked gates: ${review.blockedGateCount}/${review.gateCount}`,
    `Handoff bundle: ${review.handoffBundle.status}`,
    `Owner templates: ${review.ownerDispatch.ownerTemplateCount}/${review.ownerDispatch.expectedOwnerCount}`,
    `Lane receipt: ${review.laneCompletionReceipt.status}`,
    `Lane receipt file: ${review.laneCompletionReceipt.receiptFile ? `\`${review.laneCompletionReceipt.receiptFile}\`` : "not provided"}`,
    `Lane receipt coverage: ${review.laneCompletionReceipt.coverage.coveredLaneCount}/${review.laneCompletionReceipt.coverage.expectedLaneCount}`,
    `Evidence closure: ${review.evidenceClosureBoard.closedLaneCount}/${review.evidenceClosureBoard.laneCount}`,
    "",
    "## Checklist",
    "",
    "| Item | Passed | Evidence | Blocker |",
    "| --- | --- | --- | --- |",
    ...review.checklist.map((item) => [
      item.label,
      item.passed ? "yes" : "no",
      `\`${item.evidence}\``,
      item.blocker || "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Blocking Gates",
    "",
    ...(review.blockers.length > 0
      ? [
        "| Gate | Owner | First blocker | Next command |",
        "| --- | --- | --- | --- |",
        ...review.blockers.map((gate) => [
          gate.gate,
          gate.owner,
          gate.firstBlocker || "none",
          gate.nextCommand ? `\`${gate.nextCommand}\`` : "none",
        ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
      ]
      : ["- none"]),
    "",
    "## Evidence Closure",
    "",
    `Status: ${review.evidenceClosureBoard.status}`,
    `Open lanes: ${review.evidenceClosureBoard.openLaneCount}`,
    ...(review.evidenceClosureBoard.nextLane
      ? [
        `Next lane: \`${review.evidenceClosureBoard.nextLane.key}\``,
        `Next source: \`${review.evidenceClosureBoard.nextLane.sourcePlan || "none"}\``,
        `Next command: \`${review.evidenceClosureBoard.nextLane.nextCommand || "none"}\``,
      ]
      : ["Next lane: none"]),
    "",
    "## Owner Packets",
    "",
    "| Owner | Env template | Lanes | Blocking inputs | Evidence gaps | Missing artifacts |",
    "| --- | --- | ---: | ---: | ---: | ---: |",
    ...review.ownerDispatch.owners.map((owner) => [
      owner.owner,
      owner.blockingInputsEnvTemplate ? `\`${owner.blockingInputsEnvTemplate}\`` : "missing",
      owner.laneCount ?? 0,
      owner.blockingInputCount ?? "unknown",
      owner.evidenceGapCount ?? "unknown",
      owner.missingEvidenceArtifactCount ?? "unknown",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Owner Lane Routes",
    "",
    ...(review.ownerDispatch.owners.some((owner) => (owner.lanes || []).length > 0)
      ? review.ownerDispatch.owners.flatMap((owner) => [
        `### ${owner.owner}`,
        "",
        ...((owner.lanes || []).length > 0
          ? owner.lanes.map((lane) => `- ${lane.order}. \`${lane.lane}\`: status=${lane.status}; source=\`${lane.sourcePlan || "none"}\`; next=\`${lane.command || "none"}\`; accept=${(lane.acceptanceCommands || []).map((command) => `\`${command}\``).join(", ") || "none"}`)
          : ["- none"]),
        "",
      ])
      : ["- none", ""]),
    "## Lane Receipt Fragment",
    "",
    "```json",
    JSON.stringify(review.laneReceiptFragment, null, 2),
    "```",
    "",
    "## Top Blocking Inputs",
    "",
    ...(review.topBlockingInputs.length > 0
      ? review.topBlockingInputs.map((input) => `- \`${input.input}\`: gates=${input.gateCount}; owners=${input.owners.join(", ") || "none"}; next=\`${input.nextCommand || "none"}\``)
      : ["- none"]),
    "",
    `Next: \`${review.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runFinalReview({ markdown = false, enforce = false } = {}) {
  let review;
  try {
    review = buildFinalReview();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderFinalReviewMarkdown(review));
  } else {
    console.log(JSON.stringify(review, null, 2));
  }
  process.exit(enforce && !review.cutoverReady ? 1 : 0);
}

if (finalReviewOnly) {
  runFinalReview();
}

if (finalReviewMarkdownOnly) {
  runFinalReview({ markdown: true });
}

if (finalReviewEnforce) {
  runFinalReview({ enforce: true });
}

function buildLaneReceiptFragmentsIndex({
  releaseEnvSubmissionPlanOverride = null,
  dockerImageSubmissionPlanOverride = null,
  runtimeBusinessSubmissionPlanOverride = null,
  dataSafetySubmissionPlanOverride = null,
  finalReviewOverride = null,
  rollupOverride = null,
} = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const releaseEnvSubmissionPlan = releaseEnvSubmissionPlanOverride || buildReleaseEnvSubmissionPlan();
  const dockerImageSubmissionPlan = dockerImageSubmissionPlanOverride || buildDockerImageSubmissionPlan({ rollupOverride: rollup });
  const runtimeBusinessSubmissionPlan = runtimeBusinessSubmissionPlanOverride || buildRuntimeBusinessSubmissionPlan({ rollupOverride: rollup });
  const dataSafetySubmissionPlan = dataSafetySubmissionPlanOverride || buildDataSafetySubmissionPlan({ rollupOverride: rollup });
  const finalReview = finalReviewOverride || buildFinalReview({ rollupOverride: rollup });
  const fragments = [
    {
      sourcePlan: "release-env-submission-plan.json",
      sourceCommand: "node scripts/ddd-staging-execution-checklist.mjs --release-env-submission-plan-markdown",
      fragment: releaseEnvSubmissionPlan.laneReceiptFragment,
    },
    {
      sourcePlan: "docker-image-submission-plan.json",
      sourceCommand: "node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown",
      fragment: dockerImageSubmissionPlan.laneReceiptFragment,
    },
    {
      sourcePlan: "runtime-business-submission-plan.json",
      sourceCommand: "node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown",
      fragment: runtimeBusinessSubmissionPlan.laneReceiptFragment,
    },
    {
      sourcePlan: "data-safety-submission-plan.json",
      sourceCommand: "node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown",
      fragment: dataSafetySubmissionPlan.laneReceiptFragment,
    },
    {
      sourcePlan: "final-review.json",
      sourceCommand: "node scripts/ddd-staging-execution-checklist.mjs --final-review-markdown",
      fragment: finalReview.laneReceiptFragment,
    },
  ].map((item) => ({
    key: `${item.fragment.owner}:${item.fragment.lane}`,
    owner: item.fragment.owner,
    lane: item.fragment.lane,
    status: item.fragment.status,
    sourcePlan: item.sourcePlan,
    sourceCommand: item.sourceCommand,
    providedArtifacts: item.fragment.providedArtifacts,
    missingArtifacts: item.fragment.missingArtifacts,
    completedAt: item.fragment.completedAt,
    completedBy: item.fragment.completedBy,
    acceptanceCommands: item.fragment.acceptanceCommands,
  }));
  return {
    status: fragments.every((fragment) => fragment.status === "PASS") ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    redacted: true,
    laneCount: fragments.length,
    passLaneCount: fragments.filter((fragment) => fragment.status === "PASS").length,
    blockedLaneCount: fragments.filter((fragment) => fragment.status !== "PASS").length,
    fragments,
    receiptAssembly: {
      templateCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template",
      contractCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>",
      coverageCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>",
      base64Command: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>",
      finalReviewCommand: "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
    },
    passCriteria: [
      "copy all five fragments into laneReceipts",
      "set receipt.redacted=true and receipt.status=PASS only after all lane validations pass",
      "each PASS fragment must keep providedArtifacts non-empty",
      "each PASS fragment must clear missingArtifacts",
      "each PASS fragment must set completedAt and completedBy",
      "receipt coverage must show Coverage: 5/5 before final review enforce",
    ],
    nextCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>",
  };
}

function renderLaneReceiptFragmentsMarkdown(index) {
  const lines = [
    "# DDD Lane Receipt Fragments",
    "",
    `Status: ${index.status}`,
    `Redacted: ${index.redacted}`,
    `Lanes: ${index.laneCount}`,
    `PASS lanes: ${index.passLaneCount}`,
    `BLOCKED lanes: ${index.blockedLaneCount}`,
    "",
    "## Fragments",
    "",
    "| Key | Status | Source | Provided artifacts | Missing artifacts |",
    "| --- | --- | --- | --- | --- |",
    ...index.fragments.map((fragment) => [
      `\`${fragment.key}\``,
      fragment.status,
      `\`${fragment.sourcePlan}\``,
      fragment.providedArtifacts.length > 0 ? fragment.providedArtifacts.map((artifact) => `\`${artifact}\``).join("<br>") : "none",
      fragment.missingArtifacts.length > 0 ? fragment.missingArtifacts.map((artifact) => `\`${artifact}\``).join("<br>") : "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Receipt JSON Skeleton",
    "",
    "```json",
    JSON.stringify({
      redacted: true,
      status: "BLOCKED",
      laneReceipts: index.fragments.map((fragment) => ({
        owner: fragment.owner,
        lane: fragment.lane,
        status: fragment.status,
        providedArtifacts: fragment.providedArtifacts,
        missingArtifacts: fragment.missingArtifacts,
        completedAt: fragment.completedAt,
        completedBy: fragment.completedBy,
      })),
    }, null, 2),
    "```",
    "",
    "## Owner Fragment Copy Blocks",
    "",
    ...index.fragments.flatMap((fragment) => [
      `### ${fragment.key}`,
      "",
      `Source plan: \`${fragment.sourcePlan}\``,
      `Source command: \`${fragment.sourceCommand}\``,
      "",
      "```json",
      JSON.stringify({
        owner: fragment.owner,
        lane: fragment.lane,
        status: fragment.status,
        providedArtifacts: fragment.providedArtifacts,
        missingArtifacts: fragment.missingArtifacts,
        completedAt: fragment.completedAt,
        completedBy: fragment.completedBy,
        acceptanceCommands: fragment.acceptanceCommands,
      }, null, 2),
      "```",
      "",
    ]),
    "## Assembly Checklist",
    "",
    "- Keep the top-level receipt `redacted=true`; do not include secrets, tokens, passwords, or private URLs.",
    "- Copy every owner fragment into `laneReceipts`; the full receipt must include all five owner:lane pairs exactly once.",
    "- Leave a lane `BLOCKED` until its acceptance commands pass and every expected evidence artifact is available.",
    "- To mark a lane `PASS`, keep `providedArtifacts` non-empty, clear `missingArtifacts`, and set `completedAt` plus `completedBy`.",
    "- Run contract, coverage, base64, and final-review commands before submitting the workflow dispatch input.",
    "",
    "## Assembly Commands",
    "",
    ...Object.values(index.receiptAssembly).map((command) => `- \`${command}\``),
    "",
    "## Pass Criteria",
    "",
    ...index.passCriteria.map((item) => `- ${item}`),
    "",
    `Next: \`${index.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runLaneReceiptFragments({ markdown = false } = {}) {
  let index;
  try {
    index = buildLaneReceiptFragmentsIndex();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderLaneReceiptFragmentsMarkdown(index));
  } else {
    console.log(JSON.stringify(index, null, 2));
  }
  process.exit(0);
}

if (laneReceiptFragmentsOnly) {
  runLaneReceiptFragments();
}

if (laneReceiptFragmentsMarkdownOnly) {
  runLaneReceiptFragments({ markdown: true });
}

function buildLaneReceiptDraft({ fragmentsIndexOverride = null } = {}) {
  const index = fragmentsIndexOverride || buildLaneReceiptFragmentsIndex();
  const laneReceipts = (index.fragments || []).map((fragment) => ({
    lane: fragment.lane,
    owner: fragment.owner,
    status: fragment.status === "PASS" && fragment.missingArtifacts.length === 0 ? "PASS" : "BLOCKED",
    acceptanceCommands: fragment.acceptanceCommands || [],
    expectedArtifacts: fragment.providedArtifacts || [],
    providedArtifacts: fragment.providedArtifacts || [],
    missingArtifacts: fragment.missingArtifacts || [],
    evidenceNotes: [
      `sourcePlan=${fragment.sourcePlan}`,
      `sourceCommand=${fragment.sourceCommand}`,
    ],
    completedAt: fragment.status === "PASS" ? fragment.completedAt : null,
    completedBy: fragment.status === "PASS" ? fragment.completedBy : null,
  }));
  const status = laneReceipts.every((lane) => lane.status === "PASS") ? "PASS" : "BLOCKED";
  return {
    status,
    generatedAt,
    willWriteFiles: false,
    redacted: true,
    source: "lane-receipt-fragments",
    laneReceiptCount: laneReceipts.length,
    laneReceipts,
    passCriteria: [
      "keep redacted=true",
      "set receipt status to PASS only after every lane status is PASS",
      "clear missingArtifacts before marking a lane PASS",
      "keep providedArtifacts non-empty for every PASS lane",
      "set completedAt and completedBy for every PASS lane",
      "run contract and coverage before base64 submission",
    ],
    validationCommands: [
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
    ],
    nextCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>",
  };
}

function renderLaneReceiptDraftMarkdown(draft) {
  const lines = [
    "# DDD Lane Receipt Draft",
    "",
    `Status: ${draft.status}`,
    `Redacted: ${draft.redacted}`,
    `Lane receipts: ${draft.laneReceiptCount}`,
    "",
    "| Owner | Lane | Status | Provided artifacts | Missing artifacts |",
    "| --- | --- | --- | ---: | ---: |",
    ...draft.laneReceipts.map((lane) => [
      lane.owner,
      `\`${lane.lane}\``,
      lane.status,
      lane.providedArtifacts.length,
      lane.missingArtifacts.length,
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Pass Criteria",
    "",
    ...draft.passCriteria.map((item) => `- ${item}`),
    "",
    "## Validation Commands",
    "",
    ...draft.validationCommands.map((command) => `- \`${command}\``),
    "",
    `Next: \`${draft.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runLaneReceiptDraft({ markdown = false } = {}) {
  let draft;
  try {
    draft = buildLaneReceiptDraft();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderLaneReceiptDraftMarkdown(draft));
  } else {
    console.log(JSON.stringify(draft, null, 2));
  }
  process.exit(0);
}

if (laneReceiptDraftOnly) {
  runLaneReceiptDraft();
}

if (laneReceiptDraftMarkdownOnly) {
  runLaneReceiptDraft({ markdown: true });
}

function runReleaseOwnerCloseout({ markdown = false } = {}) {
  let closeout;
  try {
    const finalReview = buildFinalReview();
    const evidenceClosureBoard = buildEvidenceClosureBoard({
      receiptFile: finalReview.laneCompletionReceipt.receiptFile || "",
    });
    closeout = buildReleaseOwnerCloseout({ finalReview, evidenceClosureBoard });
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderReleaseOwnerCloseoutMarkdown(closeout));
  } else {
    console.log(JSON.stringify(closeout, null, 2));
  }
  process.exit(0);
}

if (releaseOwnerCloseoutOnly) {
  runReleaseOwnerCloseout();
}

if (releaseOwnerCloseoutMarkdownOnly) {
  runReleaseOwnerCloseout({ markdown: true });
}

function buildProductionCloseoutStatus({
  rollupOverride = null,
  finalReviewOverride = null,
  operatorProgressOverride = null,
  cutoverRehearsalPlanOverride = null,
  dailyBriefOverride = null,
} = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const finalReview = finalReviewOverride || buildFinalReview({ rollupOverride: rollup });
  const operatorProgress = operatorProgressOverride || buildOperatorProgress({ rollupOverride: rollup, finalReviewOverride: finalReview });
  const cutoverRehearsalPlan = cutoverRehearsalPlanOverride || buildCutoverRehearsalPlan({ rollupOverride: rollup });
  const dailyBrief = dailyBriefOverride || buildReleaseOwnerDailyBrief({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
  });
  const blockedStages = (operatorProgress.stages || []).filter((stage) => stage.status !== "PASS");
  const blockedPhases = (cutoverRehearsalPlan.phases || []).filter((phase) => phase.status !== "PASS");
  const nextOwnerAction = (dailyBrief.dailyPriorities || [])[0] || null;
  const laneCompletionSubmissionCheck = buildLaneCompletionSubmissionCheck({
    receiptFile: finalReview.laneCompletionReceipt.receiptFile || "",
    rollupOverride: rollup,
  });
  const nextActions = [
    {
      id: "first-wave-env",
      label: "Validate first-wave secure env file",
      owner: operatorProgress.nextStage?.owner || "release-infra",
      command: operatorProgress.nextCommand,
      reason: operatorProgress.nextStage?.detail || "first-wave env evidence must pass before downstream staging verification",
    },
    {
      id: "lane-completion-receipt",
      label: "Initialize or validate lane completion receipt",
      owner: "release-owner",
      command: laneCompletionSubmissionCheck.nextCommand,
      reason: `receipt=${laneCompletionSubmissionCheck.contract.status}; coverage=${laneCompletionSubmissionCheck.coverage.coveredLaneCount}/${laneCompletionSubmissionCheck.coverage.expectedLaneCount}; dispatchReady=${laneCompletionSubmissionCheck.dispatch.ready}`,
    },
    nextOwnerAction
      ? {
        id: "owner-evidence",
        label: "Close next owner evidence lane",
        owner: nextOwnerAction.owner,
        command: nextOwnerAction.nextCommand || null,
        reason: nextOwnerAction.reason,
      }
      : null,
  ].filter((action) => action && action.command);
  return {
    status: finalReview.cutoverReady ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: finalReview.finalRecommendation,
    cutoverReady: finalReview.cutoverReady,
    cutoverAllowed: finalReview.cutoverAllowed,
    eta: cutoverRehearsalPlan.eta,
    etaBasis: "Derived from --cutover-rehearsal-plan and current evidence gates; it is not a waiver or deployment approval.",
    acceptedGates: `${finalReview.acceptedGateCount}/${finalReview.acceptedGateTotal}`,
    blockedGates: `${finalReview.blockedGateCount}/${finalReview.gateCount}`,
    blockedStages: blockedStages.map((stage) => ({
      id: stage.id,
      label: stage.label,
      status: stage.status,
      detail: stage.detail,
      command: stage.command,
    })),
    blockedPhases: blockedPhases.map((phase) => ({
      id: phase.id,
      title: phase.title,
      owner: phase.owner,
      blocker: phase.blocker,
      firstCommand: phase.commands[0] || null,
    })),
    evidenceArtifacts: operatorProgress.evidenceArtifacts,
    laneReceipt: {
      status: finalReview.laneCompletionReceipt.status,
      coverage: `${finalReview.laneCompletionReceipt.coverage.coveredLaneCount}/${finalReview.laneCompletionReceipt.coverage.expectedLaneCount}`,
      receiptFile: finalReview.laneCompletionReceipt.receiptFile || null,
    },
    laneCompletionSubmission: {
      status: laneCompletionSubmissionCheck.status,
      contract: laneCompletionSubmissionCheck.contract.status,
      coverage: `${laneCompletionSubmissionCheck.coverage.coveredLaneCount}/${laneCompletionSubmissionCheck.coverage.expectedLaneCount}`,
      base64Ready: laneCompletionSubmissionCheck.base64.ready,
      dispatchReady: laneCompletionSubmissionCheck.dispatch.ready,
      preferredInput: laneCompletionSubmissionCheck.dispatch.preferredInput,
      decodedPath: laneCompletionSubmissionCheck.dispatch.decodedPath,
      issueCount: laneCompletionSubmissionCheck.issues.length,
      nextCommand: laneCompletionSubmissionCheck.nextCommand,
    },
    nextOwnerAction,
    nextActions,
    nextStage: operatorProgress.nextStage,
    nextCommand: operatorProgress.nextCommand,
    requiredBeforeProduction: [
      "handoff bundle verify remains PASS",
      "all owner lane receipt fragments are submitted as one redacted PASS receipt",
      "lane completion receipt contract and coverage show 5/5",
      "staging evidence artifacts are present and accepted for every gate",
      "workflow_dispatch inputs pass --release-evidence-dispatch-inputs-contract",
      "--final-review-enforce and release-final-go-no-go-gate.sh both pass",
    ],
  };
}

function renderProductionCloseoutStatusMarkdown(status) {
  const lines = [
    "# DDD Production Closeout Status",
    "",
    `Status: ${status.status}`,
    `Final recommendation: ${status.finalRecommendation}`,
    `Cutover ready: ${status.cutoverReady}`,
    `Cutover allowed: ${status.cutoverAllowed}`,
    `ETA: ${status.eta}`,
    `Accepted gates: ${status.acceptedGates}`,
    `Blocked gates: ${status.blockedGates}`,
    `Lane receipt: ${status.laneReceipt.status}`,
    `Lane receipt coverage: ${status.laneReceipt.coverage}`,
    `Evidence artifacts: ${status.evidenceArtifacts.present}/${status.evidenceArtifacts.total} present; missing=${status.evidenceArtifacts.missing}`,
    "",
    "## Lane Completion Submission",
    "",
    `Status: ${status.laneCompletionSubmission.status}`,
    `Contract: ${status.laneCompletionSubmission.contract}`,
    `Coverage: ${status.laneCompletionSubmission.coverage}`,
    `Base64 ready: ${status.laneCompletionSubmission.base64Ready}`,
    `Dispatch ready: ${status.laneCompletionSubmission.dispatchReady}`,
    `Preferred workflow input: \`${status.laneCompletionSubmission.preferredInput}\``,
    `Decoded workflow path: \`${status.laneCompletionSubmission.decodedPath}\``,
    `Blocking issues: ${status.laneCompletionSubmission.issueCount}`,
    `Next receipt command: \`${status.laneCompletionSubmission.nextCommand}\``,
    "",
    "## Parallel Next Actions",
    "",
    ...(status.nextActions.length > 0
      ? status.nextActions.map((action) => `- ${action.label}: owner=${action.owner}; reason=${action.reason}; command=\`${action.command}\``)
      : ["- none"]),
    "",
    "## Next Owner Action",
    "",
    ...(status.nextOwnerAction
      ? [
        `- Owner: ${status.nextOwnerAction.owner}`,
        `- Reason: ${status.nextOwnerAction.reason}`,
        `- Packet: \`${status.nextOwnerAction.ownerPacket}\``,
        `- Env template: \`${status.nextOwnerAction.envTemplate}\``,
        `- Command: \`${status.nextOwnerAction.nextCommand || "none"}\``,
      ]
      : ["- none"]),
    "",
    "## Blocked Stages",
    "",
    ...(status.blockedStages.length > 0
      ? status.blockedStages.map((stage) => `- ${stage.label}: ${stage.detail}; command=\`${stage.command || "none"}\``)
      : ["- none"]),
    "",
    "## Blocked Phases",
    "",
    ...(status.blockedPhases.length > 0
      ? status.blockedPhases.map((phase) => `- ${phase.title}: owner=${phase.owner}; blocker=${phase.blocker || "none"}; first=\`${phase.firstCommand || "none"}\``)
      : ["- none"]),
    "",
    "## Required Before Production",
    "",
    ...status.requiredBeforeProduction.map((item) => `- ${item}`),
    "",
    `Next: \`${status.nextCommand || "none"}\``,
    "",
  ];
  return lines.join("\n");
}

function runProductionCloseoutStatus({ markdown = false } = {}) {
  let status;
  try {
    status = buildProductionCloseoutStatus();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderProductionCloseoutStatusMarkdown(status));
  } else {
    console.log(JSON.stringify(status, null, 2));
  }
  process.exit(0);
}

if (productionCloseoutStatusOnly) {
  runProductionCloseoutStatus();
}

if (productionCloseoutStatusMarkdownOnly) {
  runProductionCloseoutStatus({ markdown: true });
}

function buildProductionUnblockPlan({
  rollupOverride = null,
  finalReviewOverride = null,
  operatorProgressOverride = null,
  closeoutStatusOverride = null,
  cutoverAuditOverride = null,
} = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const finalReview = finalReviewOverride || buildFinalReview({ rollupOverride: rollup });
  const operatorProgress = operatorProgressOverride || buildOperatorProgress({ rollupOverride: rollup, finalReviewOverride: finalReview });
  const closeoutStatus = closeoutStatusOverride || buildProductionCloseoutStatus({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
  });
  const cutoverAudit = cutoverAuditOverride || buildProductionCutoverAudit({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
    closeoutStatusOverride: closeoutStatus,
  });
  const parallelWorkstreams = (closeoutStatus.nextActions || []).map((action, index) => ({
    order: index + 1,
    id: action.id,
    label: action.label,
    owner: action.owner,
    status: action.status || "ACTION_REQUIRED",
    reason: action.reason,
    command: action.command,
    verifyCommand: {
      "first-wave-env": "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>",
      "lane-completion-receipt": "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>",
      "owner-evidence": "node scripts/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown",
    }[action.id] || action.command,
    evidence: {
      "first-wave-env": "redacted next-action env receipt",
      "lane-completion-receipt": "redacted lane completion receipt with 5/5 coverage",
      "owner-evidence": "artifacts/ddd/release/staging-handoff-bundle/owner-evidence-intake.json",
    }[action.id] || action.evidence || null,
    completionSignal: {
      "first-wave-env": "next-action env receipt contract passes",
      "lane-completion-receipt": "lane completion submission check reports dispatchReady=true",
      "owner-evidence": "owner evidence intake has 0 missing required artifacts",
    }[action.id] || "workstream command and verification command pass",
  }));
  const blockedAuditItems = (cutoverAudit.blockedItems || []).map((item) => ({
    id: item.id,
    label: item.label,
    blocker: item.blocker,
    command: item.command,
    evidence: item.evidence,
  }));
  const exitCriteria = [
    "first-wave env validation has a passing redacted receipt",
    "lane completion receipt contract and coverage pass for all 5 lanes",
    "owner evidence intake has no missing required artifacts",
    "production cutover audit has 0 blocked items",
    "final review reports GO_STRICT and cutoverAllowed=true",
  ];
  return {
    status: cutoverAudit.status === "PASS" ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: cutoverAudit.finalRecommendation,
    cutoverReady: cutoverAudit.cutoverReady,
    cutoverAllowed: cutoverAudit.cutoverAllowed,
    eta: closeoutStatus.eta,
    blockedAuditItemCount: cutoverAudit.blockedAuditItemCount,
    passedAuditItemCount: cutoverAudit.passedAuditItemCount,
    parallelWorkstreamCount: parallelWorkstreams.length,
    parallelWorkstreams,
    blockedAuditItems,
    exitCriteria,
    verificationCommands: [
      "node scripts/ddd-staging-execution-checklist.mjs --production-closeout-status-markdown",
      "node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit-markdown",
      "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
      "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    ],
    nextCommand: parallelWorkstreams[0]?.command || cutoverAudit.nextCommand,
    noAutoWaivers: true,
  };
}

function renderProductionUnblockPlanMarkdown(plan) {
  const lines = [
    "# DDD Production Unblock Plan",
    "",
    `Status: ${plan.status}`,
    `Final recommendation: ${plan.finalRecommendation}`,
    `Cutover ready: ${plan.cutoverReady}`,
    `Cutover allowed: ${plan.cutoverAllowed}`,
    `Audit items: ${plan.passedAuditItemCount} PASS; ${plan.blockedAuditItemCount} blocked`,
    `ETA: ${plan.eta}`,
    `No auto waivers: ${plan.noAutoWaivers}`,
    "",
    "## Parallel Workstreams",
    "",
    ...(plan.parallelWorkstreams.length > 0
      ? plan.parallelWorkstreams.map((workstream) => `${workstream.order}. ${workstream.label}: owner=${workstream.owner}; status=${workstream.status}; reason=${workstream.reason}; command=\`${workstream.command}\`; verify=\`${workstream.verifyCommand}\`; evidence=${workstream.evidence || "none"}; done=${workstream.completionSignal}`)
      : ["- none"]),
    "",
    "## Blocked Audit Items",
    "",
    ...(plan.blockedAuditItems.length > 0
      ? plan.blockedAuditItems.map((item) => `- ${item.id}: ${item.blocker || "blocked"}; evidence=\`${item.evidence}\`; command=\`${item.command}\``)
      : ["- none"]),
    "",
    "## Exit Criteria",
    "",
    ...plan.exitCriteria.map((item) => `- ${item}`),
    "",
    "## Verification Commands",
    "",
    ...plan.verificationCommands.map((command) => `- \`${command}\``),
    "",
    `Next: \`${plan.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function renderProductionUnblockQuickstartMarkdown(plan) {
  const firstWorkstream = plan.parallelWorkstreams[0] || null;
  const lines = [
    "# DDD Production Unblock Quickstart",
    "",
    `Status: ${plan.status}`,
    `Final recommendation: ${plan.finalRecommendation}`,
    `Cutover allowed: ${plan.cutoverAllowed}`,
    `Blocked audit items: ${plan.blockedAuditItemCount}`,
    "",
    "## Fast Path",
    "",
    "1. Fill release env values from `release-env-fill.template.env` into `.env.release.local`.",
    "2. Validate the release env file:",
    "",
    "```bash",
    "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs",
    "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-config-evidence.mjs",
    "```",
    "",
    firstWorkstream
      ? `3. Start with ${firstWorkstream.label}: \`${firstWorkstream.command}\``
      : "3. No open production unblock workstream is currently listed.",
    firstWorkstream
      ? `4. Verify with \`${firstWorkstream.verifyCommand}\`.`
      : "4. Re-run the production unblock plan to refresh the next action.",
    "5. Continue the parallel workstreams below until every completion signal is satisfied.",
    "",
    "## Parallel Workstreams",
    "",
    ...(plan.parallelWorkstreams.length > 0
      ? plan.parallelWorkstreams.map((workstream) => `- ${workstream.id}: owner=${workstream.owner}; command=\`${workstream.command}\`; verify=\`${workstream.verifyCommand}\`; done=${workstream.completionSignal}`)
      : ["- none"]),
    "",
    "## Final Gate",
    "",
    "- `node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-enforce` must exit 0.",
    "- `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh` must exit 0.",
    "- No manual waiver is allowed while `finalRecommendation` remains `NO_GO_STRICT`.",
    "",
  ];
  return lines.join("\n");
}

function runProductionUnblockPlan({ markdown = false } = {}) {
  let plan;
  try {
    plan = buildProductionUnblockPlan();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderProductionUnblockPlanMarkdown(plan));
  } else {
    console.log(JSON.stringify(plan, null, 2));
  }
  process.exit(0);
}

if (productionUnblockPlanOnly) {
  runProductionUnblockPlan();
}

if (productionUnblockPlanMarkdownOnly) {
  runProductionUnblockPlan({ markdown: true });
}

function buildProductionEvidenceReadiness({
  rollupOverride = null,
  finalReviewOverride = null,
  operatorProgressOverride = null,
  closeoutStatusOverride = null,
  cutoverAuditOverride = null,
  unblockPlanOverride = null,
} = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const finalReview = finalReviewOverride || buildFinalReview({ rollupOverride: rollup });
  const operatorProgress = operatorProgressOverride || buildOperatorProgress({ rollupOverride: rollup, finalReviewOverride: finalReview });
  const closeoutStatus = closeoutStatusOverride || buildProductionCloseoutStatus({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
  });
  const cutoverAudit = cutoverAuditOverride || buildProductionCutoverAudit({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
    closeoutStatusOverride: closeoutStatus,
  });
  const unblockPlan = unblockPlanOverride || buildProductionUnblockPlan({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
    closeoutStatusOverride: closeoutStatus,
    cutoverAuditOverride: cutoverAudit,
  });
  const envReceiptContract = nextActionEnvReceiptFile
    ? buildNextActionEnvReceiptContract({ receiptFile: nextActionEnvReceiptFile })
    : null;
  const laneSubmissionCheck = buildLaneCompletionSubmissionCheck({ receiptFile: laneCompletionReceiptFile || "", rollupOverride: rollup });
  const ownerEvidenceIntake = buildOwnerEvidenceIntake({ rollupOverride: rollup });
  const ownerEvidenceReady = ownerEvidenceIntake.status === "PASS"
    || ((ownerEvidenceIntake.missingArtifactCount || 0) === 0 && (ownerEvidenceIntake.owners || []).every((owner) => owner.status === "PASS"));
  const evidenceGates = [
    {
      id: "first-wave-env-receipt",
      label: "First-wave env receipt contract",
      status: envReceiptContract?.status || "MISSING",
      evidence: envReceiptContract?.receiptFile || "redacted next-action env receipt file not provided",
      command: "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file> --next-action-env-receipt-output=<receipt-file>",
      verifyCommand: "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>",
      blocker: envReceiptContract
        ? (envReceiptContract.issues[0] || null)
        : "next-action env receipt file not provided",
    },
    {
      id: "lane-completion-receipt",
      label: "Lane completion receipt dispatch readiness",
      status: laneSubmissionCheck.status,
      evidence: laneSubmissionCheck.receiptFile || "redacted lane completion receipt file not provided",
      command: laneSubmissionCheck.nextCommand,
      verifyCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>",
      blocker: laneSubmissionCheck.issues[0] || null,
    },
    {
      id: "owner-evidence",
      label: "Owner evidence intake",
      status: ownerEvidenceReady ? "PASS" : "BLOCKED",
      evidence: "artifacts/ddd/release/staging-handoff-bundle/owner-evidence-intake.json",
      command: ownerEvidenceIntake.nextCommand,
      verifyCommand: "node scripts/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown",
      blocker: ownerEvidenceReady
        ? null
        : `missingArtifacts=${ownerEvidenceIntake.missingArtifactCount}; blockingInputs=${ownerEvidenceIntake.blockingInputCount}`,
    },
    {
      id: "production-audit",
      label: "Production cutover audit",
      status: cutoverAudit.status,
      evidence: "artifacts/ddd/release/staging-handoff-bundle/production-cutover-audit.json",
      command: "node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit-markdown",
      verifyCommand: "node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit",
      blocker: cutoverAudit.status === "PASS" ? null : `blockedAuditItems=${cutoverAudit.blockedAuditItemCount}`,
    },
    {
      id: "final-go-no-go",
      label: "Strict final go/no-go",
      status: finalReview.cutoverAllowed === true && finalReview.finalRecommendation === "GO_STRICT" ? "PASS" : "BLOCKED",
      evidence: "artifacts/ddd/release/release-final-go-no-go.json",
      command: "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
      verifyCommand: "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
      blocker: finalReview.cutoverAllowed === true && finalReview.finalRecommendation === "GO_STRICT"
        ? null
        : `cutoverAllowed=${finalReview.cutoverAllowed}; finalRecommendation=${finalReview.finalRecommendation}`,
    },
  ];
  const blockingEvidence = evidenceGates.filter((gate) => gate.status !== "PASS");
  return {
    status: blockingEvidence.length === 0 ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: cutoverAudit.finalRecommendation,
    cutoverAllowed: cutoverAudit.cutoverAllowed,
    blockedAuditItemCount: cutoverAudit.blockedAuditItemCount,
    passedAuditItemCount: cutoverAudit.passedAuditItemCount,
    readyEvidenceCount: evidenceGates.length - blockingEvidence.length,
    evidenceGateCount: evidenceGates.length,
    evidenceGates,
    blockingEvidence,
    parallelWorkstreams: unblockPlan.parallelWorkstreams,
    verificationCommands: [
      "node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness",
      "node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-enforce",
      "node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit",
      "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    ],
    nextCommand: blockingEvidence[0]?.command || "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    noAutoWaivers: true,
  };
}

function renderProductionEvidenceReadinessMarkdown(readiness) {
  const lines = [
    "# DDD Production Evidence Readiness",
    "",
    `Status: ${readiness.status}`,
    `Final recommendation: ${readiness.finalRecommendation}`,
    `Cutover allowed: ${readiness.cutoverAllowed}`,
    `Evidence gates: ${readiness.readyEvidenceCount}/${readiness.evidenceGateCount} PASS`,
    `Audit items: ${readiness.passedAuditItemCount} PASS; ${readiness.blockedAuditItemCount} blocked`,
    `No auto waivers: ${readiness.noAutoWaivers}`,
    "",
    "## Evidence Gates",
    "",
    "| Gate | Status | Evidence | Command | Verify | Blocker |",
    "| --- | --- | --- | --- | --- | --- |",
    ...readiness.evidenceGates.map((gate) => [
      gate.label,
      gate.status,
      `\`${gate.evidence}\``,
      `\`${gate.command}\``,
      `\`${gate.verifyCommand}\``,
      gate.blocker || "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Blocking Evidence",
    "",
    ...(readiness.blockingEvidence.length > 0
      ? readiness.blockingEvidence.map((gate) => `- ${gate.id}: ${gate.blocker || "blocked"}; command=\`${gate.command}\`; verify=\`${gate.verifyCommand}\``)
      : ["- none"]),
    "",
    "## Parallel Workstreams",
    "",
    ...(readiness.parallelWorkstreams.length > 0
      ? readiness.parallelWorkstreams.map((workstream) => `- ${workstream.id}: owner=${workstream.owner}; command=\`${workstream.command}\`; verify=\`${workstream.verifyCommand}\``)
      : ["- none"]),
    "",
    "## Verification Commands",
    "",
    ...readiness.verificationCommands.map((command) => `- \`${command}\``),
    "",
    `Next: \`${readiness.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runProductionEvidenceReadiness({ markdown = false, enforce = false } = {}) {
  let readiness;
  try {
    readiness = buildProductionEvidenceReadiness();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderProductionEvidenceReadinessMarkdown(readiness));
  } else {
    console.log(JSON.stringify(readiness, null, 2));
  }
  process.exit(enforce && readiness.status !== "PASS" ? 1 : 0);
}

if (productionEvidenceReadinessOnly) {
  runProductionEvidenceReadiness();
}

if (productionEvidenceReadinessMarkdownOnly) {
  runProductionEvidenceReadiness({ markdown: true });
}

if (productionEvidenceReadinessEnforce) {
  runProductionEvidenceReadiness({ enforce: true });
}

function buildProductionCutoverAudit({
  rollupOverride = null,
  finalReviewOverride = null,
  operatorProgressOverride = null,
  closeoutStatusOverride = null,
} = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const finalReview = finalReviewOverride || buildFinalReview({ rollupOverride: rollup });
  const operatorProgress = operatorProgressOverride || buildOperatorProgress({ rollupOverride: rollup, finalReviewOverride: finalReview });
  const closeoutStatus = closeoutStatusOverride || buildProductionCloseoutStatus({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
  });
  const laneReceiptCoverage = finalReview.laneCompletionReceipt.coverage;
  const laneReceiptAuditCommand = closeoutStatus.laneCompletionSubmission?.nextCommand
    || "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>";
  const auditItems = [
    {
      id: "handoff-bundle-integrity",
      label: "Handoff bundle verifies and manifest hashes match",
      status: finalReview.handoffBundle.status,
      evidence: finalReview.handoffBundle.manifest || "artifacts/ddd/release/staging-handoff-bundle/manifest.json",
      command: "node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle-verify",
      blocker: finalReview.handoffBundle.status === "PASS" ? null : (finalReview.handoffBundle.issues[0] || "handoff bundle verifier did not pass"),
    },
    {
      id: "owner-dispatch-ready",
      label: "All owner packets and env templates are present",
      status: finalReview.ownerDispatch.status,
      evidence: "artifacts/ddd/release/staging-handoff-bundle/owner-dispatch.json",
      command: "node scripts/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown",
      blocker: finalReview.ownerDispatch.status === "PASS"
        ? null
        : `owner packets=${finalReview.ownerDispatch.ownerCount}/${finalReview.ownerDispatch.expectedOwnerCount}; templates=${finalReview.ownerDispatch.ownerTemplateCount}/${finalReview.ownerDispatch.expectedOwnerCount}`,
    },
    {
      id: "lane-receipt-coverage",
      label: "Redacted lane completion receipt covers every owner lane",
      status: finalReview.laneCompletionReceipt.status === "PASS" && laneReceiptCoverage.status === "PASS" ? "PASS" : "BLOCKED",
      evidence: finalReview.laneCompletionReceipt.receiptFile || "lane completion receipt file not provided",
      command: laneReceiptAuditCommand,
      blocker: finalReview.laneCompletionReceipt.status === "PASS" && laneReceiptCoverage.status === "PASS"
        ? null
        : `coverage=${laneReceiptCoverage.coveredLaneCount}/${laneReceiptCoverage.expectedLaneCount}; receipt=${finalReview.laneCompletionReceipt.status}`,
    },
    {
      id: "staging-evidence-accepted",
      label: "Every staging evidence gate is accepted",
      status: finalReview.acceptedGateCount === finalReview.acceptedGateTotal ? "PASS" : "BLOCKED",
      evidence: "artifacts/ddd/release/staging-handoff-bundle/evidence-acceptance.json",
      command: "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
      blocker: finalReview.acceptedGateCount === finalReview.acceptedGateTotal
        ? null
        : `accepted=${finalReview.acceptedGateCount}/${finalReview.acceptedGateTotal}`,
    },
    {
      id: "critical-path-clear",
      label: "Critical path phases are all clear",
      status: (operatorProgress.criticalPath || []).every((phase) => phase.status === "PASS") ? "PASS" : "BLOCKED",
      evidence: "artifacts/ddd/release/staging-handoff-bundle/operator-progress.json",
      command: "node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown",
      blocker: (operatorProgress.criticalPath || []).every((phase) => phase.status === "PASS")
        ? null
        : `blocked=${(operatorProgress.criticalPath || []).filter((phase) => phase.status !== "PASS").length}/${(operatorProgress.criticalPath || []).length}`,
    },
    {
      id: "final-review-enforced",
      label: "Release-owner final review enforces PASS",
      status: finalReview.status,
      evidence: "artifacts/ddd/release/staging-handoff-bundle/final-review.json",
      command: "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
      blocker: finalReview.status === "PASS" ? null : `finalReview=${finalReview.status}; recommendation=${finalReview.finalRecommendation}`,
    },
    {
      id: "strict-go-no-go",
      label: "Strict final go/no-go gate allows cutover",
      status: finalReview.cutoverAllowed === true && finalReview.finalRecommendation === "GO_STRICT" ? "PASS" : "BLOCKED",
      evidence: "artifacts/ddd/release/release-final-go-no-go.json",
      command: "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
      blocker: finalReview.cutoverAllowed === true && finalReview.finalRecommendation === "GO_STRICT"
        ? null
        : `cutoverAllowed=${finalReview.cutoverAllowed}; finalRecommendation=${finalReview.finalRecommendation}`,
    },
  ];
  const blockedItems = auditItems.filter((item) => item.status !== "PASS");
  return {
    status: blockedItems.length === 0 ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: finalReview.finalRecommendation,
    cutoverReady: finalReview.cutoverReady,
    cutoverAllowed: finalReview.cutoverAllowed,
    eta: closeoutStatus.eta,
    auditItemCount: auditItems.length,
    passedAuditItemCount: auditItems.length - blockedItems.length,
    blockedAuditItemCount: blockedItems.length,
    auditItems,
    blockedItems,
    parallelNextActions: closeoutStatus.nextActions || [],
    requiredCommands: auditItems.map((item) => item.command),
    nextCommand: blockedItems[0]?.command || "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    noAutoWaivers: true,
  };
}

function renderProductionCutoverAuditMarkdown(audit) {
  const lines = [
    "# DDD Production Cutover Audit",
    "",
    `Status: ${audit.status}`,
    `Final recommendation: ${audit.finalRecommendation}`,
    `Cutover ready: ${audit.cutoverReady}`,
    `Cutover allowed: ${audit.cutoverAllowed}`,
    `Audit items: ${audit.passedAuditItemCount}/${audit.auditItemCount} PASS`,
    `Blocked audit items: ${audit.blockedAuditItemCount}`,
    `ETA: ${audit.eta}`,
    `No auto waivers: ${audit.noAutoWaivers}`,
    "",
    "## Audit Items",
    "",
    "| Item | Status | Evidence | Command | Blocker |",
    "| --- | --- | --- | --- | --- |",
    ...audit.auditItems.map((item) => [
      item.label,
      item.status,
      `\`${item.evidence}\``,
      `\`${item.command}\``,
      item.blocker || "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Blocked Items",
    "",
    ...(audit.blockedItems.length > 0
      ? audit.blockedItems.map((item) => `- ${item.id}: ${item.blocker || "blocked"}; command=\`${item.command}\``)
      : ["- none"]),
    "",
    "## Parallel Next Actions",
    "",
    ...(audit.parallelNextActions.length > 0
      ? audit.parallelNextActions.map((action) => `- ${action.label}: owner=${action.owner}; reason=${action.reason}; command=\`${action.command}\``)
      : ["- none"]),
    "",
    "## Required Commands",
    "",
    ...audit.requiredCommands.map((command) => `- \`${command}\``),
    "",
    `Next: \`${audit.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runProductionCutoverAudit({ markdown = false } = {}) {
  let audit;
  try {
    audit = buildProductionCutoverAudit();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderProductionCutoverAuditMarkdown(audit));
  } else {
    console.log(JSON.stringify(audit, null, 2));
  }
  process.exit(0);
}

if (productionCutoverAuditOnly) {
  runProductionCutoverAudit();
}

if (productionCutoverAuditMarkdownOnly) {
  runProductionCutoverAudit({ markdown: true });
}

function buildOperatorProgress({
  rollupOverride = null,
  envCheckOverride = null,
  handoffBundleCheckOverride = null,
  verificationPlanOverride = null,
  finalReviewOverride = null,
} = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const nextActionQueue = buildNextActionQueue({ rollupOverride: rollup });
  const envCheck = envCheckOverride || buildNextActionEnvCheck({ queueOverride: nextActionQueue });
  const envReceiptContract = nextActionEnvReceiptFile
    ? buildNextActionEnvReceiptContract({ receiptFile: nextActionEnvReceiptFile })
    : null;
  const envReceiptStatus = envReceiptContract?.status || (envCheck.status === "PASS" ? "BLOCKED" : "SKIPPED");
  const laneReceiptContract = laneCompletionReceiptFile
    ? buildLaneCompletionReceiptContract({ receiptFile: laneCompletionReceiptFile })
    : null;
  const laneReceiptCoverage = buildLaneReceiptCoverage({ laneReceiptContract, rollupOverride: rollup });
  const laneReceiptStatus = laneReceiptContract
    ? (laneReceiptContract.status === "PASS" && laneReceiptContract.receiptStatus === "PASS" && laneReceiptCoverage.status === "PASS" ? "PASS" : "BLOCKED")
    : "SKIPPED";
  const handoffBundleCheck = handoffBundleCheckOverride || verifyHandoffBundleResult();
  const verificationPlan = verificationPlanOverride || buildNextActionVerificationPlan({ rollupOverride: rollup });
  const finalReview = finalReviewOverride || buildFinalReview({
    rollupOverride: rollup,
    handoffBundleCheckOverride: handoffBundleCheck,
    laneReceiptContractOverride: laneReceiptContract,
  });
  const evidenceAcceptance = buildEvidenceAcceptance({ rollupOverride: rollup });
  const missingEvidenceArtifacts = evidenceAcceptance.items
    .flatMap((item) => (item.artifactChecks || [])
      .filter((artifact) => artifact.present === false)
      .map((artifact) => ({
        gate: item.gate,
        owner: item.owner,
        artifact: artifact.artifact,
        acceptanceCommand: item.acceptanceCommand,
      })))
    .slice(0, 8);
  const missingEvidenceArtifactsByOwner = [...missingEvidenceArtifacts.reduce((owners, item) => {
    const owner = item.owner || "unassigned";
    const current = owners.get(owner) || { owner, missingCount: 0, gates: new Set(), nextCommand: item.acceptanceCommand };
    current.missingCount += 1;
    current.gates.add(item.gate);
    if (!current.nextCommand) current.nextCommand = item.acceptanceCommand;
    owners.set(owner, current);
    return owners;
  }, new Map()).values()]
    .map((item) => ({
      owner: item.owner,
      missingCount: item.missingCount,
      gates: [...item.gates].sort(),
      nextCommand: item.nextCommand,
    }))
    .sort((left, right) => right.missingCount - left.missingCount || left.owner.localeCompare(right.owner));
  const stages = [
    {
      id: "first-wave-env",
      label: "First-wave env file",
      status: envCheck.status,
      detail: envCheck.issues[0] || `keys=${envCheck.keyCount}`,
      command: "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-check --next-action-env-file=<env-file>",
    },
    {
      id: "first-wave-env-receipt",
      label: "First-wave env receipt",
      status: envReceiptStatus,
      detail: envReceiptContract
        ? (envReceiptContract.issues[0] || `receiptStatus=${envReceiptContract.receiptStatus}; lanes=${envReceiptContract.laneCount}`)
        : (envCheck.status === "PASS" ? "receipt file not provided" : "waiting for first-wave env PASS"),
      command: "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>",
    },
    {
      id: "handoff-bundle",
      label: "Handoff bundle integrity",
      status: handoffBundleCheck.status,
      detail: handoffBundleCheck.issues[0] || `checkedFiles=${handoffBundleCheck.checkedFileCount}`,
      command: "node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle-verify",
    },
    {
      id: "lane-completion-receipt",
      label: "Lane completion receipt",
      status: laneReceiptStatus,
      detail: laneReceiptContract
        ? (laneReceiptContract.issues[0]
          || laneReceiptCoverageBlocker(laneReceiptCoverage)
          || `receiptStatus=${laneReceiptContract.receiptStatus}; lanes=${laneReceiptCoverage.coveredLaneCount}/${laneReceiptCoverage.expectedLaneCount}`)
        : "receipt file not provided",
      command: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>",
    },
    {
      id: "verification-route",
      label: "Post-env verification route",
      status: verificationPlan.status,
      detail: `blockedPhases=${verificationPlan.blockedPhaseCount}/${verificationPlan.phaseCount}`,
      command: "node scripts/ddd-staging-execution-checklist.mjs --next-action-verification-plan-markdown",
    },
    {
      id: "final-review",
      label: "Release-owner final review",
      status: finalReview.status,
      detail: finalReview.cutoverReady ? "cutoverReady=true" : `accepted=${finalReview.acceptedGateCount}/${finalReview.acceptedGateTotal}`,
      command: "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
    },
  ];
  const blockedStage = stages.find((stage) => stage.status !== "PASS") || null;
  const criticalPath = (verificationPlan.phases || []).map((phase, index) => {
    const dependency = phase.id === "verify-first-wave-env"
      ? "populated next-action env file"
      : phase.id === "verify-release-env"
        ? "cutover-safe release env file"
        : phase.id === "verify-docker-images"
          ? "Docker-enabled runner or existing image evidence"
          : phase.id === "verify-runtime"
            ? "HTTPS staging URLs and runtime owner secrets"
            : phase.id === "verify-data-safety"
              ? "rollback, migration, and EXPLAIN database evidence"
              : "5/5 lane receipt and accepted release evidence";
    return {
      order: index + 1,
      phase: phase.id,
      owner: phase.owner,
      status: phase.status,
      dependency,
      command: phase.command,
      followUpCommand: phase.followUpCommand || phase.contractCommand || null,
      sourcePlan: phase.sourcePlan || null,
      expectedArtifacts: phase.artifacts || [],
    };
  });
  return {
    status: blockedStage ? "BLOCKED" : "PASS",
    generatedAt,
    willWriteFiles: false,
    envFile: envCheck.envFile,
    receiptFile: envReceiptContract?.receiptFile || null,
    laneReceiptFile: laneReceiptContract?.receiptFile || null,
    laneReceipt: laneReceiptContract
      ? {
        status: laneReceiptContract.status,
        receiptStatus: laneReceiptContract.receiptStatus,
        laneCount: laneReceiptContract.laneCount,
        coverage: laneReceiptCoverage,
        issueCount: laneReceiptContract.issueCount,
      }
      : null,
    finalRecommendation: finalReview.finalRecommendation,
    cutoverReady: finalReview.cutoverReady,
    cutoverAllowed: finalReview.cutoverAllowed,
    evidenceArtifacts: {
      present: evidenceAcceptance.presentArtifactCount,
      missing: evidenceAcceptance.missingArtifactCount,
      total: evidenceAcceptance.presentArtifactCount + evidenceAcceptance.missingArtifactCount,
      acceptedGates: evidenceAcceptance.acceptedCount,
      totalGates: evidenceAcceptance.itemCount,
      missingItems: missingEvidenceArtifacts,
      missingByOwner: missingEvidenceArtifactsByOwner,
    },
    laneRoutes: (nextActionQueue.queue || []).map((lane) => ({
      order: lane.order,
      lane: lane.lane,
      owner: lane.owner,
      dispatchOwner: lane.dispatchOwner || lane.owner,
      status: lane.status,
      command: lane.command || null,
      sourcePlan: lane.sourcePlan || null,
      acceptanceCommands: lane.acceptanceCommands || lane.followUpCommands || [],
      missingEvidenceArtifactCount: lane.missingEvidenceArtifactCount ?? 0,
    })),
    criticalPath,
    stages,
    nextStage: blockedStage,
    nextCommand: blockedStage?.command || "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    topBlockingInputs: finalReview.topBlockingInputs,
  };
}

function renderOperatorProgressMarkdown(progress) {
  const lines = [
    "# DDD Operator Progress",
    "",
    `Status: ${progress.status}`,
    `Final recommendation: ${progress.finalRecommendation}`,
    `Cutover ready: ${progress.cutoverReady}`,
    `Cutover allowed: ${progress.cutoverAllowed}`,
    `Env file: ${progress.envFile ? `\`${progress.envFile}\`` : "not provided"}`,
    `Receipt file: ${progress.receiptFile ? `\`${progress.receiptFile}\`` : "not provided"}`,
    `Lane receipt file: ${progress.laneReceiptFile ? `\`${progress.laneReceiptFile}\`` : "not provided"}`,
    `Lane receipt coverage: ${progress.laneReceipt?.coverage ? `${progress.laneReceipt.coverage.coveredLaneCount}/${progress.laneReceipt.coverage.expectedLaneCount}` : "not provided"}`,
    `Evidence artifacts: ${progress.evidenceArtifacts.present}/${progress.evidenceArtifacts.total} present; missing=${progress.evidenceArtifacts.missing}`,
    `Evidence gates: ${progress.evidenceArtifacts.acceptedGates}/${progress.evidenceArtifacts.totalGates} accepted`,
    "",
    "## Missing Evidence By Owner",
    "",
    ...(progress.evidenceArtifacts.missingByOwner.length > 0
      ? progress.evidenceArtifacts.missingByOwner.map((item) => `- ${item.owner}: missing=${item.missingCount}; gates=${item.gates.join(", ") || "none"}; next=\`${item.nextCommand || "none"}\``)
      : ["- none"]),
    "",
    "## Missing Evidence Artifacts",
    "",
    ...(progress.evidenceArtifacts.missingItems.length > 0
      ? progress.evidenceArtifacts.missingItems.map((item) => `- \`${item.artifact}\`: gate=${item.gate}; owner=${item.owner}; next=\`${item.acceptanceCommand}\``)
      : ["- none"]),
    "",
    "## Lane Routes",
    "",
    ...((progress.laneRoutes || []).length > 0
      ? [
        "| Order | Lane | Owner | Status | Source | Command |",
        "| ---: | --- | --- | --- | --- | --- |",
        ...(progress.laneRoutes || []).map((lane) => [
          lane.order,
          `\`${lane.lane}\``,
          lane.dispatchOwner || lane.owner || "unknown",
          lane.status,
          lane.sourcePlan ? `\`${lane.sourcePlan}\`` : "none",
          lane.command ? `\`${lane.command}\`` : "none",
        ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
      ]
      : ["- none"]),
    "",
    "## Critical Path",
    "",
    ...((progress.criticalPath || []).length > 0
      ? [
        "| Order | Phase | Owner | Status | Dependency | Command |",
        "| ---: | --- | --- | --- | --- | --- |",
        ...(progress.criticalPath || []).map((phase) => [
          phase.order,
          `\`${phase.phase}\``,
          phase.owner || "unknown",
          phase.status,
          phase.dependency,
          phase.command ? `\`${phase.command}\`` : "none",
        ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
      ]
      : ["- none"]),
    "",
    "| Stage | Status | Detail | Command |",
    "| --- | --- | --- | --- |",
    ...progress.stages.map((stage) => [
      stage.label,
      stage.status,
      stage.detail,
      `\`${stage.command}\``,
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Top Blocking Inputs",
    "",
    ...(progress.topBlockingInputs.length > 0
      ? progress.topBlockingInputs.map((input) => `- \`${input.input}\`: gates=${input.gateCount}; owners=${input.owners.join(", ") || "none"}; next=\`${input.nextCommand || "none"}\``)
      : ["- none"]),
    "",
    `Next: \`${progress.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runOperatorProgress({ markdown = false } = {}) {
  let progress;
  try {
    progress = buildOperatorProgress();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderOperatorProgressMarkdown(progress));
  } else {
    console.log(JSON.stringify(progress, null, 2));
  }
  process.exit(0);
}

if (operatorProgressOnly) {
  runOperatorProgress();
}

if (operatorProgressMarkdownOnly) {
  runOperatorProgress({ markdown: true });
}

function buildOwnerEvidenceIntake({
  rollupOverride = null,
  nextActionQueueOverride = null,
  laneReceiptFragmentsOverride = null,
} = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const nextActionQueue = nextActionQueueOverride || buildNextActionQueue({ rollupOverride: rollup });
  const laneReceiptFragments = laneReceiptFragmentsOverride || buildLaneReceiptFragmentsIndex({ rollupOverride: rollup });
  const fragmentByOwner = new Map();
  for (const fragment of laneReceiptFragments.fragments || []) {
    const fragments = fragmentByOwner.get(fragment.owner) || [];
    fragments.push(fragment);
    fragmentByOwner.set(fragment.owner, fragments);
  }
  const owners = selectedOwnerPackets.map((owner) => {
    const ownerName = owner.owner;
    const lanes = (nextActionQueue.queue || []).filter((lane) => lane.dispatchOwner === ownerName || lane.owner === ownerName);
    const blockingInputGates = blockingInputGatesForOwner(ownerName, rollup);
    const blockingInputs = unique(blockingInputGates.flatMap((gate) => gate.blockingInputs || []));
    const missingArtifacts = missingEvidenceArtifactsForOwner(ownerName, rollup);
    const fragments = fragmentByOwner.get(ownerName) || [];
    const status = lanes.every((lane) => lane.status === "PASS")
      && blockingInputs.length === 0
      && missingArtifacts.length === 0
      && fragments.every((fragment) => fragment.status === "PASS")
      ? "PASS"
      : "ACTION_REQUIRED";
    return {
      owner: ownerName,
      status,
      ownerPacket: `owner-packets/${slug(ownerName)}.md`,
      ownerPacketJson: `owner-packets/${slug(ownerName)}.json`,
      envTemplate: `owner-packets/${slug(ownerName)}.blocking-inputs.template.env`,
      laneCount: lanes.length,
      lanes: lanes.map((lane) => ({
        order: lane.order,
        lane: lane.lane,
        status: lane.status,
        command: lane.command || null,
        sourcePlan: lane.sourcePlan || null,
        expectedArtifacts: lane.expectedArtifacts || lane.artifacts || [],
        missingArtifacts: lane.missingArtifacts || [],
        acceptanceCommands: lane.acceptanceCommands || lane.followUpCommands || [],
      })),
      blockingInputCount: blockingInputs.length,
      blockingInputs,
      missingArtifactCount: missingArtifacts.length,
      missingArtifacts,
      receiptFragments: fragments.map((fragment) => ({
        key: fragment.key,
        status: fragment.status,
        sourcePlan: fragment.sourcePlan,
        missingArtifacts: fragment.missingArtifacts || [],
        providedArtifacts: fragment.providedArtifacts || [],
      })),
      receiptWorkflow: {
        laneKeys: fragments.map((fragment) => fragment.key),
        initCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>",
        autofillCommand: laneCompletionReceiptAutofillCommand,
        editRule: "update only this owner's laneReceipts entries, then leave unrelated owner/lane pairs unchanged",
        checkCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>",
        coverageCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>",
      },
      nextCommand: lanes.find((lane) => lane.status !== "PASS")?.command
        || lanes[0]?.command
        || owner.nextCommand
        || (blockingInputs.length > 0 ? `node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template --owner=${ownerName}` : null),
      submissionCommands: unique([
        ...lanes.map((lane) => lane.command).filter(Boolean),
        "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>",
        fragments.length > 0 ? "node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-fragments-markdown" : null,
        "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>",
        "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>",
        "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>",
      ].filter(Boolean)),
    };
  });
  const actionRequiredOwners = owners.filter((owner) => owner.status !== "PASS");
  const nextOwner = actionRequiredOwners.find((owner) => owner.laneCount > 0 || owner.missingArtifactCount > 0)
    || actionRequiredOwners[0]
    || null;
  return {
    status: actionRequiredOwners.length === 0 ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    ownerFilter: ownerFilter || null,
    ownerCount: owners.length,
    actionRequiredOwnerCount: actionRequiredOwners.length,
    laneCount: owners.reduce((count, owner) => count + owner.laneCount, 0),
    missingArtifactCount: owners.reduce((count, owner) => count + owner.missingArtifactCount, 0),
    blockingInputCount: owners.reduce((count, owner) => count + owner.blockingInputCount, 0),
    owners,
    passCriteria: [
      "each owner fills only its env template placeholders through an approved secret store or permission-safe runner",
      "each owner runs the lane source plan command and attaches expected artifacts",
      "each owner clears missingArtifacts before marking a lane PASS",
      "all receipt fragments are copied into the submitted lane completion receipt",
      "receipt contract and coverage must pass before final review",
    ],
    nextCommand: nextOwner?.nextCommand || "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
  };
}

function renderOwnerEvidenceIntakeMarkdown(intake) {
  const lines = [
    "# DDD Owner Evidence Intake",
    "",
    `Status: ${intake.status}`,
    `Owner filter: ${intake.ownerFilter || "all"}`,
    `Owners: ${intake.ownerCount}`,
    `Action required owners: ${intake.actionRequiredOwnerCount}`,
    `Lanes: ${intake.laneCount}`,
    `Blocking inputs: ${intake.blockingInputCount}`,
    `Missing artifacts: ${intake.missingArtifactCount}`,
    "",
    "## Owner Intake",
    "",
    "| Owner | Status | Lanes | Blocking inputs | Missing artifacts | Receipt fragments | Packet | Env template | Next command |",
    "| --- | --- | ---: | ---: | ---: | ---: | --- | --- | --- |",
    ...intake.owners.map((owner) => [
      owner.owner,
      owner.status,
      owner.laneCount,
      owner.blockingInputCount,
      owner.missingArtifactCount,
      owner.receiptFragments.length,
      `\`${owner.ownerPacket}\``,
      `\`${owner.envTemplate}\``,
      owner.nextCommand ? `\`${owner.nextCommand}\`` : "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Owner Details",
    "",
  ];
  for (const owner of intake.owners) {
    lines.push(
      `### ${owner.owner}`,
      "",
      `Status: ${owner.status}`,
      `Packet: \`${owner.ownerPacket}\``,
      `JSON: \`${owner.ownerPacketJson}\``,
      `Env template: \`${owner.envTemplate}\``,
      "",
      "Lanes:",
      ...(owner.lanes.length > 0
        ? owner.lanes.map((lane) => `- \`${lane.lane}\`: status=${lane.status}; source=\`${lane.sourcePlan || "none"}\`; command=\`${lane.command || "none"}\`; missing=${lane.missingArtifacts.length > 0 ? lane.missingArtifacts.map((artifact) => `\`${artifact}\``).join(", ") : "none"}`)
        : ["- none"]),
      "",
      "Receipt fragments:",
      ...(owner.receiptFragments.length > 0
        ? owner.receiptFragments.map((fragment) => `- \`${fragment.key}\`: status=${fragment.status}; source=\`${fragment.sourcePlan}\`; missing=${fragment.missingArtifacts.length > 0 ? fragment.missingArtifacts.map((artifact) => `\`${artifact}\``).join(", ") : "none"}`)
        : ["- none"]),
      "",
      "Receipt workflow:",
      `- Init: \`${owner.receiptWorkflow.initCommand}\``,
      `- Autofill: \`${owner.receiptWorkflow.autofillCommand}\``,
      `- Edit rule: ${owner.receiptWorkflow.editRule}`,
      `- Lane keys: ${owner.receiptWorkflow.laneKeys.length > 0 ? owner.receiptWorkflow.laneKeys.map((key) => `\`${key}\``).join(", ") : "none"}`,
      `- Check: \`${owner.receiptWorkflow.checkCommand}\``,
      `- Coverage: \`${owner.receiptWorkflow.coverageCommand}\``,
      "",
      "Blocking inputs:",
      ...(owner.blockingInputs.length > 0 ? owner.blockingInputs.map((input) => `- \`${input}\``) : ["- none"]),
      "",
      "Missing artifacts:",
      ...(owner.missingArtifacts.length > 0
        ? owner.missingArtifacts.map((artifact) => `- \`${artifact.artifact}\`: gate=${artifact.gate}; sourceOwner=${artifact.owner}; next=\`${artifact.acceptanceCommand}\``)
        : ["- none"]),
      "",
      "Submission commands:",
      ...owner.submissionCommands.map((command) => `- \`${command}\``),
      "",
    );
  }
  lines.push(
    "## Pass Criteria",
    "",
    ...intake.passCriteria.map((item) => `- ${item}`),
    "",
    `Next: \`${intake.nextCommand}\``,
    "",
  );
  return lines.join("\n");
}

function runOwnerEvidenceIntake({ markdown = false } = {}) {
  let intake;
  try {
    intake = buildOwnerEvidenceIntake();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderOwnerEvidenceIntakeMarkdown(intake));
  } else {
    console.log(JSON.stringify(intake, null, 2));
  }
  process.exit(0);
}

if (ownerEvidenceIntakeOnly) {
  runOwnerEvidenceIntake();
}

if (ownerEvidenceIntakeMarkdownOnly) {
  runOwnerEvidenceIntake({ markdown: true });
}

function buildReleaseOwnerDailyBrief({
  rollupOverride = null,
  finalReviewOverride = null,
  operatorProgressOverride = null,
} = {}) {
  const rollup = rollupOverride || loadReadinessRollup();
  const finalReview = finalReviewOverride || buildFinalReview({ rollupOverride: rollup });
  const operatorProgress = operatorProgressOverride || buildOperatorProgress({ rollupOverride: rollup, finalReviewOverride: finalReview });
  const nextActionQueue = buildNextActionQueue({ rollupOverride: rollup });
  const evidenceAcceptance = buildEvidenceAcceptance({ rollupOverride: rollup });
  const ownerActions = selectedOwnerPackets.map((owner) => {
    const ownerName = owner.owner;
    const queueLanes = (nextActionQueue.queue || []).filter((lane) => lane.dispatchOwner === ownerName || lane.owner === ownerName);
    const missingArtifacts = missingEvidenceArtifactsForOwner(ownerName, rollup);
    const blockingInputGates = blockingInputGatesForOwner(ownerName, rollup);
    const sourceOwners = unique([
      ...queueLanes.map((lane) => lane.owner).filter(Boolean),
      ...missingArtifacts.map((artifact) => artifact.owner).filter(Boolean),
    ]);
    const blockingInputOwners = unique(blockingInputGates.map((gate) => gate.owner).filter(Boolean));
    const blockingInputs = unique(blockingInputGates.flatMap((gate) => gate.blockingInputs || []));
    const evidenceGaps = evidenceGapsForOwner(ownerName);
    const firstLane = queueLanes.find((lane) => lane.status !== "PASS") || queueLanes[0] || null;
    return {
      owner: ownerName,
      sourceOwners,
      blockingInputOwners,
      status: blockingInputs.length === 0 && missingArtifacts.length === 0 && queueLanes.every((lane) => lane.status === "PASS") ? "PASS" : "ACTION_REQUIRED",
      ownerPacket: `owner-packets/${slug(ownerName)}.md`,
      envTemplate: `owner-packets/${slug(ownerName)}.blocking-inputs.template.env`,
      laneCount: queueLanes.length,
      lanes: queueLanes.map((lane) => ({
        lane: lane.lane,
        status: lane.status,
        acceptanceCommand: lane.acceptanceCommand || lane.command || null,
        expectedArtifacts: lane.expectedArtifacts || [],
        missingArtifacts: lane.missingArtifacts || [],
        sourcePlan: lane.sourcePlan || null,
      })),
      blockingInputCount: blockingInputs.length,
      blockingInputs,
      evidenceGapCount: evidenceGaps.length,
      missingEvidenceArtifactCount: missingArtifacts.length,
      missingEvidenceArtifacts: missingArtifacts.slice(0, 8),
      nextCommand: firstLane?.acceptanceCommand || firstLane?.command || owner.nextCommand || null,
    };
  });
  const dailyPriorities = [
    ...ownerActions
      .filter((owner) => owner.status !== "PASS")
      .sort((left, right) => right.missingEvidenceArtifactCount - left.missingEvidenceArtifactCount
        || right.blockingInputCount - left.blockingInputCount
        || left.owner.localeCompare(right.owner))
      .slice(0, 5)
      .map((owner, index) => ({
        order: index + 1,
        owner: owner.owner,
        reason: `missingArtifacts=${owner.missingEvidenceArtifactCount}; blockingInputs=${owner.blockingInputCount}; lanes=${owner.laneCount}`,
        ownerPacket: owner.ownerPacket,
        envTemplate: owner.envTemplate,
        nextCommand: owner.nextCommand,
      })),
  ];
  const laneReceipt = finalReview.laneCompletionReceipt;
  return {
    status: finalReview.cutoverReady ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    cutoverReady: finalReview.cutoverReady,
    finalRecommendation: finalReview.finalRecommendation,
    cutoverAllowed: finalReview.cutoverAllowed,
    blockedGateCount: finalReview.blockedGateCount,
    gateCount: finalReview.gateCount,
    acceptedGateCount: finalReview.acceptedGateCount,
    acceptedGateTotal: finalReview.acceptedGateTotal,
    evidenceArtifacts: operatorProgress.evidenceArtifacts,
    laneReceipt: {
      status: laneReceipt.status,
      receiptStatus: laneReceipt.receiptStatus,
      coverage: laneReceipt.coverage,
      receiptFile: laneReceipt.receiptFile,
    },
    laneRoutes: (nextActionQueue.queue || []).map((lane) => ({
      order: lane.order,
      lane: lane.lane,
      owner: lane.owner,
      dispatchOwner: lane.dispatchOwner,
      status: lane.status,
      command: lane.command,
      sourcePlan: lane.sourcePlan,
      artifactPlanCommands: lane.artifactPlanCommands || [],
      missingEvidenceArtifactCount: lane.missingEvidenceArtifactCount || 0,
    })),
    dailyPriorities,
    ownerActions,
    topBlockingInputs: finalReview.topBlockingInputs,
    acceptanceCommands: (evidenceAcceptance.items || []).map((item) => ({
      gate: item.gate,
      owner: item.owner,
      accepted: item.accepted,
      command: item.acceptanceCommand,
      missingArtifactCount: (item.artifactChecks || []).filter((artifact) => artifact.present === false).length,
    })),
    nextCommand: dailyPriorities[0]?.nextCommand || finalReview.nextCommand,
  };
}

function renderReleaseOwnerDailyBriefMarkdown(brief) {
  const lines = [
    "# DDD Release Owner Daily Brief",
    "",
    `Status: ${brief.status}`,
    `Final recommendation: ${brief.finalRecommendation}`,
    `Cutover ready: ${brief.cutoverReady}`,
    `Cutover allowed: ${brief.cutoverAllowed}`,
    `Accepted gates: ${brief.acceptedGateCount}/${brief.acceptedGateTotal}`,
    `Blocked gates: ${brief.blockedGateCount}/${brief.gateCount}`,
    `Evidence artifacts: ${brief.evidenceArtifacts.present}/${brief.evidenceArtifacts.total} present; missing=${brief.evidenceArtifacts.missing}`,
    `Lane receipt coverage: ${brief.laneReceipt.coverage.coveredLaneCount}/${brief.laneReceipt.coverage.expectedLaneCount}`,
    "",
    "## Today",
    "",
    ...(brief.dailyPriorities.length > 0
      ? brief.dailyPriorities.map((item) => `${item.order}. ${item.owner}: ${item.reason}; packet=\`${item.ownerPacket}\`; env=\`${item.envTemplate}\`; next=\`${item.nextCommand || "none"}\``)
      : ["- none"]),
    "",
    "## Lane Routes",
    "",
    "| Order | Lane | Dispatch owner | Status | Missing artifacts | Command | Source |",
    "| ---: | --- | --- | --- | ---: | --- | --- |",
    ...(brief.laneRoutes || []).map((lane) => [
      lane.order,
      `\`${lane.lane}\``,
      lane.dispatchOwner || lane.owner,
      lane.status,
      lane.missingEvidenceArtifactCount,
      lane.command ? `\`${lane.command}\`` : "none",
      lane.sourcePlan ? `\`${lane.sourcePlan}\`` : "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Owner Actions",
    "",
    "| Dispatch owner | Source owners | Status | Lanes | Blocking inputs | Missing artifacts | Packet | Next command |",
    "| --- | --- | --- | ---: | ---: | ---: | --- | --- |",
    ...brief.ownerActions.map((owner) => [
      owner.owner,
      owner.sourceOwners.join(", ") || owner.owner,
      owner.status,
      owner.laneCount,
      owner.blockingInputCount,
      owner.missingEvidenceArtifactCount,
      `\`${owner.ownerPacket}\``,
      owner.nextCommand ? `\`${owner.nextCommand}\`` : "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Acceptance Commands",
    "",
    ...(brief.acceptanceCommands.length > 0
      ? brief.acceptanceCommands.map((item) => `- ${item.gate}: accepted=${item.accepted}; owner=${item.owner}; missingArtifacts=${item.missingArtifactCount}; command=\`${item.command}\``)
      : ["- none"]),
    "",
    "## Top Blocking Inputs",
    "",
    ...(brief.topBlockingInputs.length > 0
      ? brief.topBlockingInputs.map((input) => `- \`${input.input}\`: gates=${input.gateCount}; owners=${input.owners.join(", ") || "none"}; next=\`${input.nextCommand || "none"}\``)
      : ["- none"]),
    "",
    `Next: \`${brief.nextCommand}\``,
    "",
  ];
  return lines.join("\n");
}

function runReleaseOwnerDailyBrief({ markdown = false } = {}) {
  let brief;
  try {
    brief = buildReleaseOwnerDailyBrief();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  if (markdown) {
    process.stdout.write(renderReleaseOwnerDailyBriefMarkdown(brief));
  } else {
    console.log(JSON.stringify(brief, null, 2));
  }
  process.exit(0);
}

if (releaseOwnerDailyBriefOnly) {
  runReleaseOwnerDailyBrief();
}

if (releaseOwnerDailyBriefMarkdownOnly) {
  runReleaseOwnerDailyBrief({ markdown: true });
}

function renderEvidenceEnvTemplate() {
  const lines = [
    "# Lumira DDD staging evidence environment template.",
    "# Fill values in a secure env file or CI secret store. Do not commit populated secrets.",
    "# Generated from staging readiness rollup gates; this is intentionally narrower than release-env-missing.template.env.",
    "",
    "# Shared provenance",
    "DDD_EVIDENCE_ENVIRONMENT=staging",
    "DDD_RELEASE_CANDIDATE=__REQUIRED__",
    "DDD_EVIDENCE_OPERATOR=__REQUIRED__",
    "DDD_DEPLOYMENT_EVIDENCE=__REQUIRED__",
    "",
    "# P0 Docker image evidence",
    "DDD_DOCKER_BUILD_STRICT=true",
    "DDD_DOCKER_COMMAND=docker",
    "DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=__REQUIRED_IF_USING_EXISTING_IMAGES__",
    "DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=__REQUIRED_IF_USING_EXISTING_IMAGES__",
    "DDD_DOCKER_EXISTING_FRONTEND_IMAGE=__REQUIRED_IF_USING_EXISTING_IMAGES__",
    "",
    "# P1 runtime/business staging evidence",
    "LUMIRA_BASE_URL=__REQUIRED_HTTPS__",
    "PLAYWRIGHT_BASE_URL=__REQUIRED_HTTPS__",
    "DDD_FRONTEND_DEPLOYMENT_EVIDENCE=__REQUIRED__",
    "DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE=__REQUIRED__",
    "DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE=__REQUIRED__",
    "DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=__REQUIRED__",
    "DDD_FRONTEND_EXPECT_DEPLOYED=true",
    "DDD_AI_EXPECT_PROVIDER_REMOTE=true",
    "DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true",
    "",
    "# P1 rollback safety evidence",
    "DDD_ROLLBACK_DRILL_FILE=artifacts/ddd/rollback/rollback-drill.json",
    "# DDD_ROLLBACK_DRILL_DEFERRAL_FILE=__REQUIRED_IF_DEFERRING_CONTEXTS__",
    "DDD_ROLLBACK_DRILL_ENVIRONMENT=staging",
    "DDD_ROLLBACK_DRILL_STRICT=true",
    "",
    "# P2 migration evidence",
    "DDD_MIGRATION_FRESH_DB_VALIDATED=true",
    "DDD_MIGRATION_FRESH_DB_EVIDENCE=__REQUIRED__",
    "DDD_MIGRATION_UPGRADE_DB_VALIDATED=true",
    "DDD_MIGRATION_UPGRADE_DB_EVIDENCE=__REQUIRED__",
    "DDD_MIGRATION_ENVIRONMENT=staging",
    "DDD_MIGRATION_OPERATOR=__REQUIRED__",
    "DDD_MIGRATION_COMPLETED_AT=__REQUIRED_ISO_TIMESTAMP__",
    "",
    "# P2 EXPLAIN evidence",
    "DDD_EXPLAIN_DATABASE=__REQUIRED__",
    "DDD_EXPLAIN_ENVIRONMENT=staging",
    "DDD_EXPLAIN_STRICT=true",
    "MYSQL_CLI=mysql",
    "MYSQL_HOST=__REQUIRED__",
    "MYSQL_PORT=3306",
    "MYSQL_USER=__REQUIRED_READONLY_USER__",
    "MYSQL_PASSWORD=__REQUIRED_SECRET_REF__",
    "",
    "# Validation sequence",
    "# node scripts/ddd-staging-execution-checklist.mjs --rollup",
    "# node scripts/ddd-docker-build-evidence.mjs --check",
    "# node scripts/ddd-staging-runtime-check.mjs",
    "# node scripts/ddd-staging-data-safety-check.mjs",
    "",
  ];
  return `${lines.join("\n")}`;
}

if (evidenceEnvTemplateOnly) {
  process.stdout.write(renderEvidenceEnvTemplate());
  process.exit(0);
}

function renderHandoffBundleReadme(rollup) {
  const lines = [
    "# DDD Staging Handoff Bundle",
    "",
    `Generated at: ${generatedAt}`,
    `Status: ${rollup.status}`,
    `Final recommendation: ${rollup.finalRecommendation}`,
    `Cutover allowed: ${rollup.cutoverAllowed}`,
    `Blocked gates: ${rollup.blockedCount}/${rollup.items.length}`,
    "",
    "## Operator Quick Start",
    "",
    "1. Read `production-closeout-status.md` first for the current ETA band, next owner action, production blockers, and `## Lane Completion Submission` receipt readiness.",
    "2. Follow `production-closeout-status.md` `## Parallel Next Actions`; first-wave env, lane receipt, and owner evidence are parallel blockers and none of them waive the others.",
    "3. Read `daily-brief.md` or `operator-progress.md`; both include `## Lane Routes` for the current owner lanes.",
    "4. Use `owner-evidence-intake.md` to send each owner exactly their packet, env template, missing artifacts, and submission commands.",
    "5. Use `next-action-queue.md` and `owner-lane-matrix.md` to route the first owner lane.",
    "6. Copy `next-action.template.env` to a secure local env file and replace every placeholder.",
    "7. Validate that file with `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-check --next-action-env-file=<env-file>`.",
    "8. Use `next-action-verification-plan.md` as the ordered route after the first-wave env check passes.",
    "9. Validate owner lane receipt coverage with `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>` and require `Coverage: 5/5`.",
    "10. Use `lane-receipt-fragments.md` as the 5-lane receipt assembly index before submitting the redacted receipt.",
    "11. Read `production-cutover-audit.md` before final approval; every audit item must be PASS.",
    "12. Start from `production-unblock-quickstart.md` when the audit is still `NO_GO_STRICT`.",
    "13. Use `production-unblock-plan.md` as the focused production unblock checklist when the quickstart needs detail.",
    "14. Use `production-evidence-readiness.md` to verify env receipt, lane receipt, owner evidence, production audit, and final go/no-go evidence in one table.",
    "15. Run `node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-enforce` before final go/no-go; it must exit 0.",
    "16. Re-run `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>` only after all evidence-producing checks pass.",
    "",
    "## Status Views",
    "",
    "- `production-closeout-status.md`: top-level closeout status with ETA band, next owner action, blocked stages, receipt submission readiness, and production preconditions.",
    "- `production-cutover-audit.md`: final production cutover audit matrix with evidence, commands, blockers, and no-waiver status.",
    "- `daily-brief.md`: release-owner daily triage with `## Lane Routes` and owner priorities.",
    "- `operator-progress.md`: shift handoff view with receipt coverage, missing artifacts, and `## Lane Routes`.",
    "- `execution-status.md`: compact gate and bundle status with `## Lane Routes`.",
    "- `final-review.md`: go/no-go review with owner packet status and `## Owner Lane Routes`.",
    "",
    "## Receipt Coverage Gate",
    "",
    "- `lane-completion-receipt.template.json` is a redacted starting point, not completion evidence.",
    "- `--lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>` writes that redacted starting point to a secure local file and refuses to overwrite existing receipts.",
    "- `--lane-completion-receipt-contract` validates receipt shape and PASS/BLOCKED lane status.",
    "- `--lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>` lists missing owner:lane keys and must show `Coverage: 5/5` before final review.",
    "- `--lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>` prints the single-line value for the formal release workflow `lane_completion_receipt_base64` input, and only succeeds after contract and coverage PASS.",
    "- `--operator-progress-markdown --lane-completion-receipt-file=<receipt-file>` validates release coverage and must show `Lane receipt coverage: 5/5` before final review.",
    "- `--final-review-enforce --lane-completion-receipt-file=<receipt-file>` rejects partial receipts by listing missing owner:lane keys.",
    "",
    "## Files",
    "",
    "- `rollup.json`: machine-readable readiness summary.",
    "- `rollup.md`: paste-ready release-owner triage table.",
    "- `handoff-summary.md`: same paste-ready summary that CI appends to the GitHub Step Summary.",
    "- `execution-status.json`: machine-readable staging execution status and handoff bundle integrity summary.",
    "- `execution-status.md`: paste-ready staging execution status.",
    "- `final-review.json`: release-owner final review for cutover readiness.",
    "- `final-review.md`: paste-ready release-owner final review.",
    "- `release-owner-closeout.json`: machine-readable single-page release-owner closeout status.",
    "- `release-owner-closeout.md`: paste-ready single-page release-owner closeout status.",
    "- `production-closeout-status.json`: machine-readable production closeout status with ETA band and next owner action.",
    "- `production-closeout-status.md`: paste-ready production closeout status with remaining production preconditions.",
    "- `production-unblock-quickstart.md`: one-page fastest path for env, receipts, owner evidence, and final gates.",
    "- `production-unblock-plan.json`: machine-readable focused plan for clearing the remaining production blockers.",
    "- `production-unblock-plan.md`: paste-ready focused plan for the parallel unblock workstreams and exit criteria.",
    "- `production-evidence-readiness.json`: machine-readable aggregate readiness for production evidence submission.",
    "- `production-evidence-readiness.md`: paste-ready aggregate readiness for env, lane receipt, owner evidence, audit, and final go/no-go evidence.",
    "- `production-cutover-audit.json`: machine-readable final production cutover audit matrix.",
    "- `production-cutover-audit.md`: paste-ready final production cutover audit matrix.",
    "- `operator-progress.json`: machine-readable operator progress across env, bundle, verification, and final review.",
    "- `operator-progress.md`: paste-ready operator progress across env, bundle, verification, and final review.",
    "- `daily-brief.json`: machine-readable release-owner daily action brief.",
    "- `daily-brief.md`: paste-ready release-owner daily action brief.",
    "- `closure-plan.json`: owner-sequenced staging closure plan with ETA bands.",
    "- `closure-plan.md`: paste-ready staging closure plan with critical path and verification commands.",
    "- `next-action-queue.json`: machine-readable immediate staging owner action queue.",
    "- `next-action-queue.md`: paste-ready immediate staging owner action queue.",
    "- `owner-lane-matrix.json`: machine-readable owner-to-lane dispatch matrix for standups and handoffs.",
    "- `owner-lane-matrix.md`: paste-ready owner-to-lane dispatch matrix.",
    "- `lane-completion-receipt.template.json`: redacted lane completion receipt template for owner evidence submission.",
    "- `lane-completion-receipt.template.md`: paste-ready lane completion receipt template.",
    "- `lane-completion-receipt.coverage.json`: machine-readable initial lane receipt coverage report.",
    "- `lane-completion-receipt.coverage.md`: paste-ready initial lane receipt coverage report.",
    "- `evidence-closure-board.json`: machine-readable owner lane evidence closure board.",
    "- `evidence-closure-board.md`: paste-ready owner lane evidence closure board.",
    "- `evidence-closure-board.csv`: spreadsheet-ready owner lane evidence closure board.",
    "- `lane-receipt-fragments.json`: machine-readable 5-lane receipt fragment index.",
    "- `lane-receipt-fragments.md`: paste-ready 5-lane receipt assembly skeleton.",
    "- `lane-receipt-draft.json`: redacted lane completion receipt draft assembled from current fragments.",
    "- `lane-receipt-draft.md`: paste-ready lane completion receipt draft summary.",
    "- `owner-evidence-intake.json`: machine-readable owner evidence intake checklist.",
    "- `owner-evidence-intake.md`: paste-ready owner evidence intake checklist.",
    "- `lane-completion-submission-plan.json`: machine-readable lane receipt submission route.",
    "- `lane-completion-submission-plan.md`: paste-ready lane receipt submission route.",
    "- `lane-completion-submission-check.json`: machine-readable lane receipt submission readiness verdict.",
    "- `lane-completion-submission-check.md`: paste-ready lane receipt submission readiness verdict.",
    "- `next-action.template.env`: focused env skeleton for immediate staging owner actions.",
    "- `next-action-env-receipt.sample.json`: machine-readable redacted receipt shape for first-wave env validation.",
    "- `next-action-env-receipt.sample.md`: paste-ready redacted receipt shape for first-wave env validation.",
    "- `next-action-verification-plan.json`: machine-readable post-env-check staging verification sequence.",
    "- `next-action-verification-plan.md`: paste-ready post-env-check staging verification sequence.",
    "- `release-env-plan.json`: machine-readable P0 release-env initialization and validation plan.",
    "- `release-env-plan.md`: paste-ready P0 release-env owner collection plan.",
    "- `release-env-owner-matrix.json`: machine-readable owner-scoped release env input matrix.",
    "- `release-env-owner-matrix.md`: paste-ready owner release env fill matrix.",
    "- `release-env-next-owner.template.env`: focused env template for the current top release-env owner.",
    "- `release-env-merge-plan.json`: machine-readable release env owner merge and validation plan.",
    "- `release-env-merge-plan.md`: paste-ready release env merge and validation plan.",
    "- `release-env-submission-plan.json`: machine-readable release env owner submission and receipt plan.",
    "- `release-env-submission-plan.md`: paste-ready release env owner submission and receipt plan.",
    "- `release-env-fill-checklist.json`: machine-readable P0 release env blocker key checklist.",
    "- `release-env-fill-checklist.md`: paste-ready P0 release env blocker key checklist.",
    "- `release-env-fill.template.env`: paste-ready P0 release env fill template generated from current blockers.",
    "- `docker-image-plan.json`: machine-readable Docker image build or inspect evidence plan.",
    "- `docker-image-plan.md`: paste-ready Docker image evidence plan.",
    "- `docker-image-submission-plan.json`: machine-readable Docker image evidence submission route.",
    "- `docker-image-submission-plan.md`: paste-ready Docker image evidence submission route.",
    "- `runtime-business-plan.json`: machine-readable P1 runtime/business staging evidence plan.",
    "- `runtime-business-plan.md`: paste-ready P1 runtime/business smoke and validation plan.",
    "- `runtime-smoke-plan.json`: machine-readable owner-phased P1 runtime smoke execution plan.",
    "- `runtime-smoke-plan.md`: paste-ready owner-phased P1 runtime smoke execution plan.",
    "- `runtime-business-submission-plan.json`: machine-readable P1 runtime/business owner submission route.",
    "- `runtime-business-submission-plan.md`: paste-ready P1 runtime/business owner submission route.",
    "- `data-safety-plan.json`: machine-readable rollback, migration, and EXPLAIN evidence plan.",
    "- `data-safety-plan.md`: paste-ready data safety drill and validation plan.",
    "- `data-safety-owner-plan.json`: machine-readable owner-phased rollback, migration, and EXPLAIN execution plan.",
    "- `data-safety-owner-plan.md`: paste-ready owner-phased rollback, migration, and EXPLAIN execution plan.",
    "- `data-safety-submission-plan.json`: machine-readable rollback, migration, and EXPLAIN owner submission route.",
    "- `data-safety-submission-plan.md`: paste-ready rollback, migration, and EXPLAIN owner submission route.",
    "- `cutover-rehearsal-plan.json`: machine-readable ordered staging cutover rehearsal plan.",
    "- `cutover-rehearsal-plan.md`: paste-ready staging cutover rehearsal route.",
    "- `evidence-gaps.json`: machine-readable blocked staging evidence gaps.",
    "- `evidence-runbook.json`: machine-readable staging evidence command, artifact, and env-key runbook.",
    "- `evidence-runbook.md`: paste-ready staging evidence runbook.",
    "- `evidence-acceptance.json`: machine-readable staging evidence acceptance checklist.",
    "- `evidence-acceptance.md`: paste-ready staging evidence acceptance checklist.",
    "- `evidence-artifact-gaps.json`: machine-readable missing evidence artifact reverse index.",
    "- `evidence-artifact-gaps.md`: paste-ready missing evidence artifact reverse index.",
    "- `explain-artifact-plan.json`: machine-readable EXPLAIN artifact collection plan.",
    "- `explain-artifact-plan.md`: paste-ready EXPLAIN artifact collection plan.",
    "- `blocking-inputs.json`: machine-readable blocking input reverse index.",
    "- `blocking-inputs.md`: paste-ready blocking input reverse index.",
    "- `blocking-inputs.template.env`: focused env skeleton generated from current blocking inputs.",
    "- `release-evidence-dispatch-plan.json`: machine-readable manual release evidence workflow dispatch input plan.",
    "- `release-evidence-dispatch-plan.md`: paste-ready manual release evidence workflow dispatch input plan.",
    "- `release-evidence-dispatch-inputs.json`: machine-readable workflow_dispatch input payload template.",
    "- `release-evidence-dispatch-command.sh`: paste-ready `gh workflow run` command template.",
    "- `evidence-env.template.env`: focused staging evidence env skeleton.",
    "- `commands.txt`: recommended command sequence.",
    "- `owner-dispatch.json`: machine-readable owner packet routing index.",
    "- `owner-packets/`: per-owner env, blocking input, evidence gap, and owner-scoped missing artifact handoff packets in Markdown and JSON.",
    "",
    "## First Commands",
    "",
    "```sh",
    "node scripts/ddd-staging-execution-checklist.mjs --rollup-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-runbook-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --explain-artifact-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --closure-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-queue-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --owner-lane-matrix-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-csv",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-csv --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check-markdown --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-template",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file> --next-action-env-receipt-output=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-markdown --next-action-env-file=<env-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-verification-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --next-action-env-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --release-owner-daily-brief-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-owner-matrix-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-next-owner-template",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-merge-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-submission-plan-markdown",
    "node scripts/ddd-release-env-fill-checklist.mjs --markdown",
    "node scripts/ddd-release-env-fill-checklist.mjs --env-template",
    "node scripts/ddd-staging-execution-checklist.mjs --docker-image-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --runtime-business-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --runtime-smoke-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --data-safety-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --data-safety-owner-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --cutover-rehearsal-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template",
    "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-inputs",
    "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-command",
    "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-inputs-contract --release-evidence-dispatch-inputs-file=<inputs-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --final-review-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-env-template",
    "node scripts/ddd-staging-execution-checklist.mjs --owner-packets",
    "```",
    "",
  ];
  return `${lines.join("\n")}`;
}

function releaseEnvFillBlockerKey(blocker) {
  return String(blocker).split(":")[0]?.trim();
}

function groupReleaseEnvFillKeys(keys) {
  const groupedKeys = new Set(Object.values(releaseEnvFillGroupDefinitions).flat());
  const groups = {};
  for (const [group, groupKeys] of Object.entries(releaseEnvFillGroupDefinitions)) {
    const present = groupKeys.filter((key) => keys.includes(key));
    if (present.length > 0) {
      groups[group] = present;
    }
  }
  groups.other = keys.filter((key) => !groupedKeys.has(key));
  return groups;
}

function buildReleaseEnvFillChecklist() {
  const lintFile = "artifacts/ddd/release/release-env-lint.json";
  const configEvidenceFile = "artifacts/ddd/config/release-config-evidence.json";
  const lint = readJson(lintFile, {});
  const configEvidence = readJson(configEvidenceFile, {});
  const blockers = lint.primaryBlockers || lint.blockers || [];
  const keys = [...new Set(blockers.map(releaseEnvFillBlockerKey).filter(Boolean))];
  return {
    generatedAt,
    status: lint.status || "UNKNOWN",
    envFile: lint.envFile || ".env.release.local",
    lintFile,
    configEvidenceFile,
    primaryBlockerCount: lint.summary?.primaryBlockers ?? blockers.length,
    configBlockerCount: configEvidence.summary?.blockers ?? (configEvidence.blockers || []).length,
    keyCount: keys.length,
    keys,
    groups: groupReleaseEnvFillKeys(keys),
    validationCommands: [
      "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs",
      "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-config-evidence.mjs",
      "node scripts/ddd-release-readiness-summary.mjs",
      "node scripts/ddd-staging-execution-checklist.mjs --release-env-submission-plan",
    ],
  };
}

function renderReleaseEnvFillChecklistMarkdown(checklist) {
  const lines = [
    "# P0 Release Env Fill Checklist",
    "",
    `Generated at: ${checklist.generatedAt}`,
    "",
    `Lint status: ${checklist.status}`,
    `Env file: ${checklist.envFile}`,
    `Primary blockers: ${checklist.primaryBlockerCount}`,
    `Config blocker count: ${checklist.configBlockerCount}`,
    "",
    "## Required Keys By Group",
    "",
  ];

  for (const [group, keys] of Object.entries(checklist.groups)) {
    if (keys.length === 0) continue;
    lines.push(`### ${group}`, "", ...keys.map((key) => `- ${key}`), "");
  }

  lines.push(
    "## Validation Commands",
    "",
    "```bash",
    ...checklist.validationCommands,
    "```",
    "",
    "## Acceptance Rule",
    "",
    "Do not mark `release-infra:p0-release-env` PASS until release env lint and release config evidence are PASS, and the resulting artifacts are attached to the lane receipt.",
    "",
  );
  return lines.join("\n");
}

function releaseEnvFillPlaceholderForKey(key) {
  if (key.endsWith("_BASE_URL") || key === "PLAYWRIGHT_BASE_URL" || key === "LUMIRA_BASE_URL" || key.includes("ORIGIN")) {
    return "__REQUIRED_HTTPS__";
  }
  if (key.includes("PASSWORD") || key.includes("SECRET") || key.includes("TOKEN") || key.endsWith("_KEY")) {
    return "__REQUIRED_SECRET_REF__";
  }
  if (key.includes("COMPLETED_AT")) return "__REQUIRED_ISO_TIMESTAMP__";
  if (key.includes("VALIDATED")) return "__REQUIRED_TRUE__";
  if (key.includes("PORT")) return "__REQUIRED_PORT__";
  if (key.includes("EVIDENCE") || key.includes("ARTIFACT")) return "__REQUIRED_ARTIFACT_PATH_OR_URL__";
  return "__REQUIRED__";
}

function renderReleaseEnvFillTemplate(checklist) {
  const lines = [
    "# P0 release env fill template.",
    "# Fill this into .env.release.local, then run the validation commands at the bottom.",
    "# Do not commit real values. Secret-like values should be secret references, not plaintext.",
    "",
    `# Source lint file: ${checklist.lintFile}`,
    `# Source config evidence file: ${checklist.configEvidenceFile}`,
    `# Primary blockers: ${checklist.primaryBlockerCount}`,
    `# Config blockers: ${checklist.configBlockerCount}`,
    "",
  ];

  for (const [group, keys] of Object.entries(checklist.groups)) {
    if (keys.length === 0) continue;
    lines.push(`# ${group}`, ...keys.map((key) => `${key}=${releaseEnvFillPlaceholderForKey(key)}`), "");
  }

  lines.push(
    "# Validation commands",
    ...checklist.validationCommands.map((command) => `# ${command}`),
    "",
  );
  return lines.join("\n");
}

function buildReleaseOwnerCloseout({ finalReview, evidenceClosureBoard }) {
  return {
    status: finalReview.status,
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: finalReview.finalRecommendation,
    cutoverReady: finalReview.cutoverReady,
    cutoverAllowed: finalReview.cutoverAllowed,
    handoffBundleStatus: finalReview.handoffBundle.status,
    acceptedGates: `${finalReview.acceptedGateCount}/${finalReview.acceptedGateTotal}`,
    blockedGates: `${finalReview.blockedGateCount}/${finalReview.gateCount}`,
    laneReceipt: {
      status: finalReview.laneCompletionReceipt.status,
      coverage: `${finalReview.laneCompletionReceipt.coverage.coveredLaneCount}/${finalReview.laneCompletionReceipt.coverage.expectedLaneCount}`,
      receiptFile: finalReview.laneCompletionReceipt.receiptFile || null,
    },
    evidenceClosure: {
      status: evidenceClosureBoard.status,
      closed: `${evidenceClosureBoard.closedLaneCount}/${evidenceClosureBoard.laneCount}`,
      nextLane: evidenceClosureBoard.nextLane ? {
        key: evidenceClosureBoard.nextLane.key,
        sourcePlan: evidenceClosureBoard.nextLane.sourcePlan,
        nextCommand: evidenceClosureBoard.nextLane.nextCommand,
      } : null,
    },
    blockers: finalReview.blockers.map((blocker) => ({
      gate: blocker.gate,
      owner: blocker.owner,
      firstBlocker: blocker.firstBlocker,
      nextCommand: blocker.nextCommand,
    })),
    requiredCommandSequence: [
      "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
      "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
    ],
    nextCommand: evidenceClosureBoard.nextLane?.nextCommand || finalReview.nextCommand,
  };
}

function renderReleaseOwnerCloseoutMarkdown(closeout) {
  const lines = [
    "# DDD Release Owner Closeout",
    "",
    `Status: ${closeout.status}`,
    `Final recommendation: ${closeout.finalRecommendation}`,
    `Cutover ready: ${closeout.cutoverReady}`,
    `Cutover allowed: ${closeout.cutoverAllowed}`,
    `Handoff bundle: ${closeout.handoffBundleStatus}`,
    `Accepted gates: ${closeout.acceptedGates}`,
    `Blocked gates: ${closeout.blockedGates}`,
    `Lane receipt: ${closeout.laneReceipt.status}`,
    `Lane receipt coverage: ${closeout.laneReceipt.coverage}`,
    `Evidence closure: ${closeout.evidenceClosure.closed}`,
    "",
    "## Immediate Next Lane",
    "",
    closeout.evidenceClosure.nextLane
      ? `- Lane: \`${closeout.evidenceClosure.nextLane.key}\``
      : "- Lane: none",
    closeout.evidenceClosure.nextLane
      ? `- Source: \`${closeout.evidenceClosure.nextLane.sourcePlan || "none"}\``
      : "- Source: none",
    `- Command: \`${closeout.nextCommand || "none"}\``,
    "",
    "## Blocking Gates",
    "",
    "| Gate | Owner | First blocker | Next command |",
    "| --- | --- | --- | --- |",
    ...closeout.blockers.map((blocker) => [
      blocker.gate,
      blocker.owner,
      blocker.firstBlocker,
      blocker.nextCommand ? `\`${blocker.nextCommand}\`` : "none",
    ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
    "",
    "## Required Command Sequence",
    "",
    ...closeout.requiredCommandSequence.map((command) => `- \`${command}\``),
    "",
  ];
  return lines.join("\n");
}

function writeHandoffBundle() {
  let rollup;
  try {
    rollup = loadReadinessRollup();
  } catch (error) {
    console.error(`[ddd-staging-execution-checklist] ${error.message}`);
    process.exit(1);
  }
  fs.mkdirSync(handoffBundleDir, { recursive: true });
  const evidenceGaps = buildEvidenceGaps(checklist);
  const evidenceRunbook = buildEvidenceRunbook(checklist);
  const evidenceAcceptance = buildEvidenceAcceptance({ rollupOverride: rollup });
  const evidenceArtifactGapReport = buildEvidenceArtifactGapReport({ acceptanceOverride: evidenceAcceptance });
  const explainArtifactPlan = buildExplainArtifactPlan({ artifactGapReportOverride: evidenceArtifactGapReport, rollupOverride: rollup });
  const closurePlan = buildClosurePlan({ rollupOverride: rollup });
  const nextActionQueue = buildNextActionQueue({ rollupOverride: rollup });
  const laneCompletionReceiptTemplate = buildLaneCompletionReceiptTemplate({
    matrixOverride: buildOwnerLaneMatrix({ queueOverride: nextActionQueue, rollupOverride: rollup }),
  });
  const laneCompletionReceiptCoverage = buildLaneReceiptCoverageReport({ receiptFile: "", rollupOverride: rollup });
  const evidenceClosureBoard = buildEvidenceClosureBoard({ receiptFile: "", rollupOverride: rollup });
  const laneCompletionSubmissionPlan = buildLaneCompletionSubmissionPlan({ rollupOverride: rollup });
  const laneCompletionSubmissionCheck = buildLaneCompletionSubmissionCheck({ receiptFile: "", rollupOverride: rollup });
  const nextActionEnvReceiptSample = buildNextActionEnvReceipt({
    checkOverride: buildNextActionEnvCheck({ queueOverride: nextActionQueue, envFile: "" }),
    envFile: "",
  });
  const nextActionVerificationPlan = buildNextActionVerificationPlan({ rollupOverride: rollup });
  const releaseEnvPlan = buildReleaseEnvPlan({ rollupOverride: rollup });
  const releaseEnvOwnerMatrix = buildReleaseEnvOwnerMatrix({ releaseEnvPlanOverride: releaseEnvPlan });
  const releaseEnvNextOwnerTemplate = buildReleaseEnvNextOwnerTemplateReport({ rollupOverride: rollup, matrixOverride: releaseEnvOwnerMatrix });
  const releaseEnvMergePlan = buildReleaseEnvMergePlan({ releaseEnvPlanOverride: releaseEnvPlan, matrixOverride: releaseEnvOwnerMatrix });
  const releaseEnvSubmissionPlan = buildReleaseEnvSubmissionPlan({ releaseEnvPlanOverride: releaseEnvPlan, matrixOverride: releaseEnvOwnerMatrix, mergePlanOverride: releaseEnvMergePlan });
  const releaseEnvFillChecklist = buildReleaseEnvFillChecklist();
  const dockerImagePlan = buildDockerImagePlan({ rollupOverride: rollup });
  const dockerImageSubmissionPlan = buildDockerImageSubmissionPlan({ dockerImagePlanOverride: dockerImagePlan, rollupOverride: rollup });
  const runtimeBusinessPlan = buildRuntimeBusinessPlan({ rollupOverride: rollup });
  const runtimeSmokePlan = buildRuntimeSmokePlan({ runtimeBusinessPlanOverride: runtimeBusinessPlan, rollupOverride: rollup });
  const runtimeBusinessSubmissionPlan = buildRuntimeBusinessSubmissionPlan({ runtimeBusinessPlanOverride: runtimeBusinessPlan, runtimeSmokePlanOverride: runtimeSmokePlan, rollupOverride: rollup });
  const dataSafetyPlan = buildDataSafetyPlan({ rollupOverride: rollup });
  const dataSafetyOwnerPlan = buildDataSafetyOwnerPlan({ dataSafetyPlanOverride: dataSafetyPlan, rollupOverride: rollup });
  const dataSafetySubmissionPlan = buildDataSafetySubmissionPlan({ dataSafetyPlanOverride: dataSafetyPlan, dataSafetyOwnerPlanOverride: dataSafetyOwnerPlan, explainArtifactPlanOverride: explainArtifactPlan, rollupOverride: rollup });
  const cutoverRehearsalPlan = buildCutoverRehearsalPlan({ rollupOverride: rollup });
  const blockingInputs = buildBlockingInputs({ rollupOverride: rollup });
  const releaseEvidenceDispatchPlan = buildReleaseEvidenceDispatchPlan({ rollupOverride: rollup, blockingInputsOverride: blockingInputs });
  const releaseEvidenceDispatchInputs = buildReleaseEvidenceDispatchInputs(releaseEvidenceDispatchPlan);
  const files = {
    "README.md": renderHandoffBundleReadme(rollup),
    "rollup.json": `${JSON.stringify(rollup, null, 2)}\n`,
    "rollup.md": renderRollupMarkdown(rollup),
    "evidence-gaps.json": `${JSON.stringify(evidenceGaps, null, 2)}\n`,
    "evidence-runbook.json": `${JSON.stringify(evidenceRunbook, null, 2)}\n`,
    "evidence-runbook.md": renderEvidenceRunbookMarkdown(evidenceRunbook),
    "evidence-acceptance.json": `${JSON.stringify(evidenceAcceptance, null, 2)}\n`,
    "evidence-acceptance.md": renderEvidenceAcceptanceMarkdown(evidenceAcceptance),
    "evidence-artifact-gaps.json": `${JSON.stringify(evidenceArtifactGapReport, null, 2)}\n`,
    "evidence-artifact-gaps.md": renderEvidenceArtifactGapReportMarkdown(evidenceArtifactGapReport),
    "explain-artifact-plan.json": `${JSON.stringify(explainArtifactPlan, null, 2)}\n`,
    "explain-artifact-plan.md": renderExplainArtifactPlanMarkdown(explainArtifactPlan),
    "closure-plan.json": `${JSON.stringify(closurePlan, null, 2)}\n`,
    "closure-plan.md": renderClosurePlanMarkdown(closurePlan),
    "next-action-queue.json": `${JSON.stringify(nextActionQueue, null, 2)}\n`,
    "next-action-queue.md": renderNextActionQueueMarkdown(nextActionQueue),
    "lane-completion-receipt.template.json": `${JSON.stringify(laneCompletionReceiptTemplate, null, 2)}\n`,
    "lane-completion-receipt.template.md": renderLaneCompletionReceiptMarkdown(laneCompletionReceiptTemplate),
    "lane-completion-receipt.coverage.json": `${JSON.stringify(laneCompletionReceiptCoverage, null, 2)}\n`,
    "lane-completion-receipt.coverage.md": renderLaneReceiptCoverageMarkdown(laneCompletionReceiptCoverage),
    "evidence-closure-board.json": `${JSON.stringify(evidenceClosureBoard, null, 2)}\n`,
    "evidence-closure-board.md": renderEvidenceClosureBoardMarkdown(evidenceClosureBoard),
    "evidence-closure-board.csv": renderEvidenceClosureBoardCsv(evidenceClosureBoard),
    "lane-completion-submission-plan.json": `${JSON.stringify(laneCompletionSubmissionPlan, null, 2)}\n`,
    "lane-completion-submission-plan.md": renderLaneCompletionSubmissionPlanMarkdown(laneCompletionSubmissionPlan),
    "lane-completion-submission-check.json": `${JSON.stringify(laneCompletionSubmissionCheck, null, 2)}\n`,
    "lane-completion-submission-check.md": renderLaneCompletionSubmissionCheckMarkdown(laneCompletionSubmissionCheck),
    "next-action.template.env": renderNextActionEnvTemplate(nextActionQueue),
    "next-action-env-receipt.sample.json": `${JSON.stringify(nextActionEnvReceiptSample, null, 2)}\n`,
    "next-action-env-receipt.sample.md": renderNextActionEnvReceiptMarkdown(nextActionEnvReceiptSample),
    "next-action-verification-plan.json": `${JSON.stringify(nextActionVerificationPlan, null, 2)}\n`,
    "next-action-verification-plan.md": renderNextActionVerificationPlanMarkdown(nextActionVerificationPlan),
    "release-env-plan.json": `${JSON.stringify(releaseEnvPlan, null, 2)}\n`,
    "release-env-plan.md": renderReleaseEnvPlanMarkdown(releaseEnvPlan),
    "release-env-owner-matrix.json": `${JSON.stringify(releaseEnvOwnerMatrix, null, 2)}\n`,
    "release-env-owner-matrix.md": renderReleaseEnvOwnerMatrixMarkdown(releaseEnvOwnerMatrix),
    "release-env-next-owner.template.env": renderReleaseEnvNextOwnerTemplate(releaseEnvNextOwnerTemplate),
    "release-env-merge-plan.json": `${JSON.stringify(releaseEnvMergePlan, null, 2)}\n`,
    "release-env-merge-plan.md": renderReleaseEnvMergePlanMarkdown(releaseEnvMergePlan),
    "release-env-submission-plan.json": `${JSON.stringify(releaseEnvSubmissionPlan, null, 2)}\n`,
    "release-env-submission-plan.md": renderReleaseEnvSubmissionPlanMarkdown(releaseEnvSubmissionPlan),
    "release-env-fill-checklist.json": `${JSON.stringify(releaseEnvFillChecklist, null, 2)}\n`,
    "release-env-fill-checklist.md": renderReleaseEnvFillChecklistMarkdown(releaseEnvFillChecklist),
    "release-env-fill.template.env": renderReleaseEnvFillTemplate(releaseEnvFillChecklist),
    "docker-image-plan.json": `${JSON.stringify(dockerImagePlan, null, 2)}\n`,
    "docker-image-plan.md": renderDockerImagePlanMarkdown(dockerImagePlan),
    "docker-image-submission-plan.json": `${JSON.stringify(dockerImageSubmissionPlan, null, 2)}\n`,
    "docker-image-submission-plan.md": renderDockerImageSubmissionPlanMarkdown(dockerImageSubmissionPlan),
    "runtime-business-plan.json": `${JSON.stringify(runtimeBusinessPlan, null, 2)}\n`,
    "runtime-business-plan.md": renderRuntimeBusinessPlanMarkdown(runtimeBusinessPlan),
    "runtime-smoke-plan.json": `${JSON.stringify(runtimeSmokePlan, null, 2)}\n`,
    "runtime-smoke-plan.md": renderRuntimeSmokePlanMarkdown(runtimeSmokePlan),
    "runtime-business-submission-plan.json": `${JSON.stringify(runtimeBusinessSubmissionPlan, null, 2)}\n`,
    "runtime-business-submission-plan.md": renderRuntimeBusinessSubmissionPlanMarkdown(runtimeBusinessSubmissionPlan),
    "data-safety-plan.json": `${JSON.stringify(dataSafetyPlan, null, 2)}\n`,
    "data-safety-plan.md": renderDataSafetyPlanMarkdown(dataSafetyPlan),
    "data-safety-owner-plan.json": `${JSON.stringify(dataSafetyOwnerPlan, null, 2)}\n`,
    "data-safety-owner-plan.md": renderDataSafetyOwnerPlanMarkdown(dataSafetyOwnerPlan),
    "data-safety-submission-plan.json": `${JSON.stringify(dataSafetySubmissionPlan, null, 2)}\n`,
    "data-safety-submission-plan.md": renderDataSafetySubmissionPlanMarkdown(dataSafetySubmissionPlan),
    "cutover-rehearsal-plan.json": `${JSON.stringify(cutoverRehearsalPlan, null, 2)}\n`,
    "cutover-rehearsal-plan.md": renderCutoverRehearsalPlanMarkdown(cutoverRehearsalPlan),
    "blocking-inputs.json": `${JSON.stringify(blockingInputs, null, 2)}\n`,
    "blocking-inputs.md": renderBlockingInputsMarkdown(blockingInputs),
    "blocking-inputs.template.env": renderBlockingInputsEnvTemplate(blockingInputs),
    "release-evidence-dispatch-plan.json": `${JSON.stringify(releaseEvidenceDispatchPlan, null, 2)}\n`,
    "release-evidence-dispatch-plan.md": renderReleaseEvidenceDispatchPlanMarkdown(releaseEvidenceDispatchPlan),
    "release-evidence-dispatch-inputs.json": `${JSON.stringify(releaseEvidenceDispatchInputs, null, 2)}\n`,
    "release-evidence-dispatch-command.sh": renderReleaseEvidenceDispatchCommand(releaseEvidenceDispatchInputs),
    "evidence-env.template.env": renderEvidenceEnvTemplate(),
    "commands.txt": renderCommands(),
  };
  const bundleOwnerPacketDir = path.join(handoffBundleDir, "owner-packets");
  fs.mkdirSync(bundleOwnerPacketDir, { recursive: true });
  const ownerPacketFiles = {
    "owner-packets/README.md": renderOwnerPacketIndex(selectedOwnerPackets, { rollup }),
  };
  const ownerMissingEvidenceArtifacts = new Map(
    selectedOwnerPackets.map((owner) => [owner.owner, missingEvidenceArtifactsForOwner(owner.owner, rollup)]),
  );
  for (const owner of selectedOwnerPackets) {
    const file = `owner-packets/${slug(owner.owner)}.md`;
    ownerPacketFiles[file] = renderOwnerPacket(owner, { rollup });
    const jsonFile = `owner-packets/${slug(owner.owner)}.json`;
    ownerPacketFiles[jsonFile] = `${JSON.stringify(buildOwnerPacket(owner, { rollup }), null, 2)}\n`;
    const envTemplateFile = `owner-packets/${slug(owner.owner)}.blocking-inputs.template.env`;
    ownerPacketFiles[envTemplateFile] = renderBlockingInputsEnvTemplate(buildOwnerBlockingInputsEnvReport(owner, { rollupOverride: rollup }));
  }
  const ownerDispatch = {
    generatedAt,
    status: checklist.status,
    finalRecommendation: checklist.finalRecommendation,
    cutoverAllowed: checklist.cutoverAllowed,
    ownerCount: selectedOwnerPackets.length,
    owners: selectedOwnerPackets.map((owner) => {
      const lanes = queueLanesForOwner(owner.owner, rollup);
      return {
        owner: owner.owner,
        blockers: owner.blockers,
        placeholders: owner.placeholders,
        secretKeys: owner.secretKeys,
        markdown: `owner-packets/${slug(owner.owner)}.md`,
        json: `owner-packets/${slug(owner.owner)}.json`,
        blockingInputsEnvTemplate: `owner-packets/${slug(owner.owner)}.blocking-inputs.template.env`,
        evidenceGapCount: evidenceGapsForOwner(owner.owner).length,
        missingEvidenceArtifactCount: (ownerMissingEvidenceArtifacts.get(owner.owner) || []).length,
        blockingInputCount: blockingInputGatesForOwner(owner.owner, rollup).reduce((count, gate) => count + (gate.blockingInputs || []).length, 0),
        laneCount: lanes.length,
        lanes: lanes.map((lane) => ({
          order: lane.order,
          lane: lane.lane,
          status: lane.status,
          title: lane.title,
          command: lane.command || null,
          sourcePlan: lane.sourcePlan || null,
          acceptanceCommands: lane.acceptanceCommands || [],
          expectedArtifacts: lane.expectedArtifacts || [],
          missingArtifacts: lane.missingArtifacts || [],
          missingEvidenceArtifactCount: lane.missingEvidenceArtifactCount ?? 0,
        })),
        nextCommand: lanes[0]?.command || owner.nextCommand || null,
      };
    }),
  };
  files["owner-dispatch.json"] = `${JSON.stringify(ownerDispatch, null, 2)}\n`;
  const ownerLaneMatrix = buildOwnerLaneMatrix({
    queueOverride: nextActionQueue,
    ownerDispatchOverride: ownerDispatch,
    rollupOverride: rollup,
  });
  files["owner-lane-matrix.json"] = `${JSON.stringify(ownerLaneMatrix, null, 2)}\n`;
  files["owner-lane-matrix.md"] = renderOwnerLaneMatrixMarkdown(ownerLaneMatrix);
  const handoffBundleCheckOverride = {
    status: "PASS",
    bundleDir: relative(handoffBundleDir),
    manifest: relative(path.join(handoffBundleDir, "manifest.json")),
    checkedFileCount: 0,
    issues: [],
  };
  const status = buildExecutionStatus({
    rollupOverride: rollup,
    handoffBundleCheckOverride,
  });
  const finalReview = buildFinalReview({
    rollupOverride: rollup,
    handoffBundleCheckOverride,
    ownerDispatchOverride: ownerDispatch,
  });
  const operatorProgress = buildOperatorProgress({
    rollupOverride: rollup,
    handoffBundleCheckOverride,
    verificationPlanOverride: nextActionVerificationPlan,
    finalReviewOverride: finalReview,
  });
  const releaseOwnerDailyBrief = buildReleaseOwnerDailyBrief({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
  });
  const releaseOwnerCloseout = buildReleaseOwnerCloseout({
    finalReview,
    evidenceClosureBoard,
  });
  const productionCloseoutStatus = buildProductionCloseoutStatus({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
    cutoverRehearsalPlanOverride: cutoverRehearsalPlan,
    dailyBriefOverride: releaseOwnerDailyBrief,
  });
  const productionCutoverAudit = buildProductionCutoverAudit({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
    closeoutStatusOverride: productionCloseoutStatus,
  });
  const productionUnblockPlan = buildProductionUnblockPlan({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
    closeoutStatusOverride: productionCloseoutStatus,
    cutoverAuditOverride: productionCutoverAudit,
  });
  const productionEvidenceReadiness = buildProductionEvidenceReadiness({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
    closeoutStatusOverride: productionCloseoutStatus,
    cutoverAuditOverride: productionCutoverAudit,
    unblockPlanOverride: productionUnblockPlan,
  });
  const laneReceiptFragments = buildLaneReceiptFragmentsIndex({
    releaseEnvSubmissionPlanOverride: releaseEnvSubmissionPlan,
    dockerImageSubmissionPlanOverride: dockerImageSubmissionPlan,
    runtimeBusinessSubmissionPlanOverride: runtimeBusinessSubmissionPlan,
    dataSafetySubmissionPlanOverride: dataSafetySubmissionPlan,
    finalReviewOverride: finalReview,
    rollupOverride: rollup,
  });
  const ownerEvidenceIntake = buildOwnerEvidenceIntake({
    rollupOverride: rollup,
    nextActionQueueOverride: nextActionQueue,
    laneReceiptFragmentsOverride: laneReceiptFragments,
  });
  const laneReceiptDraft = buildLaneReceiptDraft({ fragmentsIndexOverride: laneReceiptFragments });
  files["handoff-summary.md"] = renderHandoffSummaryMarkdown(rollup, status);
  files["execution-status.json"] = `${JSON.stringify(status, null, 2)}\n`;
  files["execution-status.md"] = renderExecutionStatusMarkdown(status);
  files["final-review.json"] = `${JSON.stringify(finalReview, null, 2)}\n`;
  files["final-review.md"] = renderFinalReviewMarkdown(finalReview);
  files["release-owner-closeout.json"] = `${JSON.stringify(releaseOwnerCloseout, null, 2)}\n`;
  files["release-owner-closeout.md"] = renderReleaseOwnerCloseoutMarkdown(releaseOwnerCloseout);
  files["production-closeout-status.json"] = `${JSON.stringify(productionCloseoutStatus, null, 2)}\n`;
  files["production-closeout-status.md"] = renderProductionCloseoutStatusMarkdown(productionCloseoutStatus);
  files["production-unblock-quickstart.md"] = renderProductionUnblockQuickstartMarkdown(productionUnblockPlan);
  files["production-unblock-plan.json"] = `${JSON.stringify(productionUnblockPlan, null, 2)}\n`;
  files["production-unblock-plan.md"] = renderProductionUnblockPlanMarkdown(productionUnblockPlan);
  files["production-evidence-readiness.json"] = `${JSON.stringify(productionEvidenceReadiness, null, 2)}\n`;
  files["production-evidence-readiness.md"] = renderProductionEvidenceReadinessMarkdown(productionEvidenceReadiness);
  files["production-cutover-audit.json"] = `${JSON.stringify(productionCutoverAudit, null, 2)}\n`;
  files["production-cutover-audit.md"] = renderProductionCutoverAuditMarkdown(productionCutoverAudit);
  files["lane-receipt-fragments.json"] = `${JSON.stringify(laneReceiptFragments, null, 2)}\n`;
  files["lane-receipt-fragments.md"] = renderLaneReceiptFragmentsMarkdown(laneReceiptFragments);
  files["lane-receipt-draft.json"] = `${JSON.stringify(laneReceiptDraft, null, 2)}\n`;
  files["lane-receipt-draft.md"] = renderLaneReceiptDraftMarkdown(laneReceiptDraft);
  files["owner-evidence-intake.json"] = `${JSON.stringify(ownerEvidenceIntake, null, 2)}\n`;
  files["owner-evidence-intake.md"] = renderOwnerEvidenceIntakeMarkdown(ownerEvidenceIntake);
  files["operator-progress.json"] = `${JSON.stringify(operatorProgress, null, 2)}\n`;
  files["operator-progress.md"] = renderOperatorProgressMarkdown(operatorProgress);
  files["daily-brief.json"] = `${JSON.stringify(releaseOwnerDailyBrief, null, 2)}\n`;
  files["daily-brief.md"] = renderReleaseOwnerDailyBriefMarkdown(releaseOwnerDailyBrief);
  const checkedFileCount = Object.keys(files).length + Object.keys(ownerPacketFiles).length;
  status.handoffBundle.checkedFileCount = checkedFileCount;
  finalReview.handoffBundle.checkedFileCount = checkedFileCount;
  operatorProgress.stages = operatorProgress.stages.map((stage) => stage.id === "handoff-bundle" ? {
    ...stage,
    detail: `checkedFiles=${checkedFileCount}`,
  } : stage);
  files["execution-status.json"] = `${JSON.stringify(status, null, 2)}\n`;
  files["execution-status.md"] = renderExecutionStatusMarkdown(status);
  files["final-review.json"] = `${JSON.stringify(finalReview, null, 2)}\n`;
  files["final-review.md"] = renderFinalReviewMarkdown(finalReview);
  files["operator-progress.json"] = `${JSON.stringify(operatorProgress, null, 2)}\n`;
  files["operator-progress.md"] = renderOperatorProgressMarkdown(operatorProgress);
  const productionCutoverAuditWithCheckedFiles = buildProductionCutoverAudit({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
    closeoutStatusOverride: productionCloseoutStatus,
  });
  const productionUnblockPlanWithCheckedFiles = buildProductionUnblockPlan({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
    closeoutStatusOverride: productionCloseoutStatus,
    cutoverAuditOverride: productionCutoverAuditWithCheckedFiles,
  });
  const productionEvidenceReadinessWithCheckedFiles = buildProductionEvidenceReadiness({
    rollupOverride: rollup,
    finalReviewOverride: finalReview,
    operatorProgressOverride: operatorProgress,
    closeoutStatusOverride: productionCloseoutStatus,
    cutoverAuditOverride: productionCutoverAuditWithCheckedFiles,
    unblockPlanOverride: productionUnblockPlanWithCheckedFiles,
  });
  files["production-unblock-plan.json"] = `${JSON.stringify(productionUnblockPlanWithCheckedFiles, null, 2)}\n`;
  files["production-unblock-quickstart.md"] = renderProductionUnblockQuickstartMarkdown(productionUnblockPlanWithCheckedFiles);
  files["production-unblock-plan.md"] = renderProductionUnblockPlanMarkdown(productionUnblockPlanWithCheckedFiles);
  files["production-evidence-readiness.json"] = `${JSON.stringify(productionEvidenceReadinessWithCheckedFiles, null, 2)}\n`;
  files["production-evidence-readiness.md"] = renderProductionEvidenceReadinessMarkdown(productionEvidenceReadinessWithCheckedFiles);
  files["production-cutover-audit.json"] = `${JSON.stringify(productionCutoverAuditWithCheckedFiles, null, 2)}\n`;
  files["production-cutover-audit.md"] = renderProductionCutoverAuditMarkdown(productionCutoverAuditWithCheckedFiles);
  for (const [file, content] of Object.entries(files)) {
    fs.writeFileSync(path.join(handoffBundleDir, file), content);
  }
  fs.writeFileSync(path.join(bundleOwnerPacketDir, "README.md"), ownerPacketFiles["owner-packets/README.md"]);
  for (const [file, content] of Object.entries(ownerPacketFiles).filter(([file]) => file !== "owner-packets/README.md")) {
    fs.writeFileSync(path.join(handoffBundleDir, file), content);
  }
  const manifestFiles = Object.entries({ ...files, ...ownerPacketFiles }).map(([file, content]) => ({
    file,
    bytes: Buffer.byteLength(content),
    sha256: sha256Text(content),
  }));
  const manifest = {
    generatedAt,
    status: rollup.status,
    finalRecommendation: rollup.finalRecommendation,
    cutoverAllowed: rollup.cutoverAllowed,
    blockedCount: rollup.blockedCount,
    ownerPackets: selectedOwnerPackets.length,
    files: manifestFiles,
  };
  fs.writeFileSync(path.join(handoffBundleDir, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);
  console.log(`[ddd-staging-execution-checklist] handoffBundle=${handoffBundleDir}; files=${manifestFiles.length + 1}; ownerPackets=${selectedOwnerPackets.length}; status=${rollup.status}`);
}

function verifyHandoffBundle() {
  const result = verifyHandoffBundleResult();
  console.log(JSON.stringify(result, null, 2));
  process.exit(result.status === "PASS" ? 0 : 1);
}

if (handoffBundle) {
  writeHandoffBundle();
  process.exit(0);
}

if (handoffBundleVerify) {
  verifyHandoffBundle();
}

function buildDispatchCheck(report) {
  const expectedOwners = ["platform-events", "platform-owners", "release-infra", "ai-owner", "payment-owner"];
  const ownerNames = report.releaseEnv.ownerBlockerSummary.map((owner) => owner.owner);
  const trackIds = report.tracks.map((track) => track.id);
  const issues = [];
  for (const owner of expectedOwners) {
    if (!ownerNames.includes(owner)) issues.push(`missing owner handoff: ${owner}`);
  }
  for (const trackId of ["p0-release-env", "p0-images", "p1-runtime-business", "p1-rollback", "p2-database-performance", "p3-final-strict"]) {
    if (!trackIds.includes(trackId)) issues.push(`missing execution track: ${trackId}`);
  }
  if (!fs.existsSync(envInitWrapperPath)) issues.push("missing env init wrapper: scripts/ddd-release-env-init.mjs");
  if (!fs.existsSync(envInitScriptPath)) issues.push("missing generated env init script: artifacts/ddd/release/release-final-owner-queue-env-init.sh");
  if (!fs.existsSync(path.join(releaseDir, "release-env-owner-handoff-redacted.json"))) {
    issues.push("missing owner handoff artifact: artifacts/ddd/release/release-env-owner-handoff-redacted.json");
  }
  const releaseEnvTrack = report.tracks.find((track) => track.id === "p0-release-env");
  if (!releaseEnvTrack?.setupCommands?.includes(envInitCheckCommand)) {
    issues.push(`p0-release-env missing setup command: ${envInitCheckCommand}`);
  }
  if (!releaseEnvTrack?.setupCommands?.includes(envInitCommand)) {
    issues.push(`p0-release-env missing setup command: ${envInitCommand}`);
  }
  const imageTrack = report.tracks.find((track) => track.id === "p0-images");
  if (!imageTrack?.setupCommands?.includes(dockerEvidenceCheckCommand)) {
    issues.push(`p0-images missing setup command: ${dockerEvidenceCheckCommand}`);
  }
  const runtimeTrack = report.tracks.find((track) => track.id === "p1-runtime-business");
  if (!runtimeTrack?.setupCommands?.includes(runtimeStagingCheckCommand)) {
    issues.push(`p1-runtime-business missing setup command: ${runtimeStagingCheckCommand}`);
  }
  const rollbackTrack = report.tracks.find((track) => track.id === "p1-rollback");
  if (!rollbackTrack?.setupCommands?.includes(dataSafetyCheckCommand)) {
    issues.push(`p1-rollback missing setup command: ${dataSafetyCheckCommand}`);
  }
  const databaseTrack = report.tracks.find((track) => track.id === "p2-database-performance");
  if (!databaseTrack?.setupCommands?.includes(dataSafetyCheckCommand)) {
    issues.push(`p2-database-performance missing setup command: ${dataSafetyCheckCommand}`);
  }
  const envInitCheckResult = spawnSync("node", ["scripts/ddd-release-env-init.mjs", "--check"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_FINAL_OWNER_QUEUE_ENV_TARGET: process.env.DDD_FINAL_OWNER_QUEUE_ENV_TARGET || "tmp/ddd-dispatch-check-env-init.env",
      DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT: process.env.DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT || "tmp/ddd-dispatch-check-env-init-receipt.json",
    },
  });
  let envInitCheck = null;
  if (envInitCheckResult.status === 0) {
    try {
      envInitCheck = JSON.parse(envInitCheckResult.stdout);
    } catch {
      issues.push("env init check returned non-JSON output");
    }
  } else {
    issues.push(`env init check failed: ${envInitCheckResult.stderr.trim() || envInitCheckResult.stdout.trim() || envInitCheckResult.status}`);
  }
  const dockerEvidenceCheckResult = spawnSync("node", ["scripts/ddd-docker-build-evidence.mjs", "--check"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  let dockerEvidenceCheck = null;
  try {
    dockerEvidenceCheck = JSON.parse(dockerEvidenceCheckResult.stdout);
  } catch {
    issues.push("docker evidence check returned non-JSON output");
  }
  const runtimeStagingCheckResult = spawnSync("node", ["scripts/ddd-staging-runtime-check.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  let runtimeStagingCheck = null;
  try {
    runtimeStagingCheck = JSON.parse(runtimeStagingCheckResult.stdout);
  } catch {
    issues.push("runtime staging check returned non-JSON output");
  }
  const dataSafetyCheckResult = spawnSync("node", ["scripts/ddd-staging-data-safety-check.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  let dataSafetyCheck = null;
  try {
    dataSafetyCheck = JSON.parse(dataSafetyCheckResult.stdout);
  } catch {
    issues.push("data safety check returned non-JSON output");
  }
  const releaseEnvCheck = buildReleaseEnvCheck(report, releaseEnvTrack);
  const result = {
    status: issues.length === 0 ? "PASS" : "BLOCKED",
    generatedAt,
    willWriteFiles: false,
    finalRecommendation: report.finalRecommendation,
    cutoverAllowed: report.cutoverAllowed,
    ownerCount: report.releaseEnv.ownerBlockerSummary.length,
    expectedOwners,
    availableOwners: ownerNames,
    blockedTracks: report.tracks.filter((track) => track.status !== "ready").map((track) => track.id),
    releaseEnvCheck,
    envInitCheck,
    dockerEvidenceCheck,
    runtimeStagingCheck,
    dataSafetyCheck,
    readinessRollupCommand: "node scripts/ddd-staging-execution-checklist.mjs --rollup",
    copyReadyCommand: "node scripts/ddd-staging-execution-checklist.mjs --commands",
    nextCommands: [
      envInitCheckCommand,
      envInitCommand,
      dockerEvidenceCheckCommand,
      runtimeStagingCheckCommand,
      dataSafetyCheckCommand,
      "node scripts/ddd-staging-execution-checklist.mjs --owner-packets",
      "DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh",
    ],
    issues,
  };
  return result;
}

function runDispatchCheck(report) {
  const result = buildDispatchCheck(report);
  console.log(JSON.stringify(result, null, 2));
  process.exit(result.issues.length === 0 ? 0 : 1);
}

if (dispatchCheck) {
  runDispatchCheck(checklist);
}

function buildReleaseEnvCheck(report, releaseEnvTrack) {
  const displayCommand = releaseEnvTrack?.commands?.[0] || "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs";
  const envFile = process.env.DDD_RELEASE_ENV_FILE || "";
  if (!envFile) {
    return {
      status: report.releaseEnv.ready === true ? "PASS" : "BLOCKED",
      ready: report.releaseEnv.ready === true,
      dynamic: false,
      willWriteFiles: false,
      nextCommand: displayCommand,
      setupCommands: releaseEnvTrack?.setupCommands || [],
      issue: report.releaseEnv.ready === true ? null : releaseEnvTrack?.reason || "DDD_RELEASE_ENV_FILE is required",
    };
  }

  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-env-check-"));
  const tempReport = path.join(tempDir, "release-env-lint.json");
  try {
    const result = spawnSync("node", ["scripts/ddd-release-env-file-lint.mjs"], {
      cwd: repoRoot,
      encoding: "utf8",
      env: {
        ...process.env,
        DDD_RELEASE_ENV_LINT_REPORT: tempReport,
      },
    });
    const lintReport = readJson(tempReport, null);
    const issue = lintReport?.primaryBlockers?.[0]
      || lintReport?.blockers?.[0]
      || result.stderr.trim().split(/\r?\n/).find(Boolean)
      || result.stdout.trim().split(/\r?\n/).find(Boolean)
      || null;
    return {
      status: result.status === 0 && lintReport?.status === "PASS" ? "PASS" : "BLOCKED",
      ready: result.status === 0 && lintReport?.status === "PASS",
      dynamic: true,
      willWriteFiles: false,
      nextCommand: displayCommand,
      setupCommands: releaseEnvTrack?.setupCommands || [],
      envFilePresent: true,
      lintStatus: lintReport?.status || null,
      blockerCount: lintReport?.summary?.primaryBlockers ?? lintReport?.summary?.blockers ?? null,
      blockingInputs: unique([
        ...(lintReport?.canonicalUnresolvedTemplateKeys || []),
        ...(lintReport?.canonicalMissingEnvKeys || []),
        ...(lintReport?.canonicalReleaseConfigBlockerKeys || []),
      ]),
      issue,
    };
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
}

function renderCommands() {
  const commands = [
    "node scripts/ddd-production-readiness-preflight.mjs --quick --no-report --list",
    "node scripts/ddd-production-readiness-preflight.mjs --quick --no-report",
    "node scripts/ddd-staging-execution-checklist.mjs --dispatch-check",
    "node scripts/ddd-staging-execution-checklist.mjs --rollup",
    "node scripts/ddd-staging-execution-checklist.mjs --rollup-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --rollup-enforce",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-gaps",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-runbook",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-runbook-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-acceptance-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --explain-artifact-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --explain-artifact-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --closure-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --closure-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-queue",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-queue-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --owner-lane-matrix",
    "node scripts/ddd-staging-execution-checklist.mjs --owner-lane-matrix-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>",
    laneCompletionReceiptAutofillCommand,
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-csv",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-csv --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-fragments",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-fragments-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-draft",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-draft-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --owner-evidence-intake",
    "node scripts/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check-markdown --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-template",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-check --next-action-env-file=<env-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file> --next-action-env-receipt-output=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-markdown --next-action-env-file=<env-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-verification-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --next-action-verification-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-owner-matrix",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-owner-matrix-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-next-owner-template",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-merge-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-merge-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-submission-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-submission-plan-markdown",
    "node scripts/ddd-release-env-fill-checklist.mjs",
    "node scripts/ddd-release-env-fill-checklist.mjs --markdown",
    "node scripts/ddd-release-env-fill-checklist.mjs --env-template",
    "node scripts/ddd-staging-execution-checklist.mjs --docker-image-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --docker-image-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --runtime-business-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --runtime-business-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --runtime-smoke-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --runtime-smoke-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --data-safety-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --data-safety-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --data-safety-owner-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --data-safety-owner-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --cutover-rehearsal-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --cutover-rehearsal-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs",
    "node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-env-template",
    "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-inputs",
    "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-command",
    "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-inputs-contract --release-evidence-dispatch-inputs-file=<inputs-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --execution-status",
    "node scripts/ddd-staging-execution-checklist.mjs --execution-status-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --handoff-summary-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --final-review",
    "node scripts/ddd-staging-execution-checklist.mjs --final-review-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
    "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --release-owner-closeout",
    "node scripts/ddd-staging-execution-checklist.mjs --release-owner-closeout-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --release-owner-closeout-markdown --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --production-closeout-status",
    "node scripts/ddd-staging-execution-checklist.mjs --production-closeout-status-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --production-unblock-plan",
    "node scripts/ddd-staging-execution-checklist.mjs --production-unblock-plan-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness",
    "node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-enforce",
    "node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit",
    "node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --operator-progress",
    "node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --next-action-env-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --release-owner-daily-brief",
    "node scripts/ddd-staging-execution-checklist.mjs --release-owner-daily-brief-markdown",
    "node scripts/ddd-staging-execution-checklist.mjs --evidence-env-template",
    "node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle",
    "node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle-verify",
    envInitCheckCommand,
    envInitCommand,
    dockerEvidenceCheckCommand,
    runtimeStagingCheckCommand,
    dataSafetyCheckCommand,
    "node scripts/ddd-staging-execution-checklist.mjs --list-owners",
    "node scripts/ddd-staging-execution-checklist.mjs --owner-packets",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs",
    "DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh",
  ];
  return `${commands.join("\n")}\n`;
}

if (commandsOnly) {
  process.stdout.write(renderCommands());
  process.exit(0);
}

function trackMatchesOwner(track, ownerName) {
  if (track.id === "p0-release-env") return true;
  const ownerAliases = {
    "ai-owner": ["ai"],
    "payment-owner": ["payment-owner", "payment"],
    "platform-events": ["platform-events"],
    "platform-owners": ["platform-owners", "bounded-context owners", "database"],
    "release-infra": ["release-infra"],
  }[ownerName] || [ownerName];
  const trackOwners = String(track.owner || "")
    .split(",")
    .map((owner) => owner.trim())
    .filter(Boolean);
  return ownerAliases.some((alias) => trackOwners.includes(alias));
}

function dispatchOwnerForRawOwner(ownerName) {
  const owners = String(ownerName || "")
    .split(",")
    .map((owner) => owner.trim())
    .filter(Boolean);
  if (owners.some((owner) => ["database", "bounded-context owners", "platform-owners"].includes(owner))) return "platform-owners";
  if (owners.some((owner) => ["ai", "ai-owner"].includes(owner))) return "ai-owner";
  if (owners.some((owner) => ["payment", "payment-owner"].includes(owner))) return "payment-owner";
  if (owners.includes("platform-events")) return "platform-events";
  if (owners.includes("release-infra")) return "release-infra";
  return owners[0] || "release-infra";
}

function evidenceGapsForOwner(ownerName) {
  return checklist.tracks
    .filter((track) => track.status !== "ready" && trackMatchesOwner(track, ownerName))
    .map((track) => ({
      id: track.id,
      title: track.title,
      reason: track.reason,
      nextCommand: [...(track.setupCommands || []), ...(track.commands || [])][0] || null,
      artifacts: track.artifacts || [],
      envKeys: track.envKeys || [],
    }));
}

function blockingInputGatesForOwner(ownerName, rollup = null) {
  const readinessRollup = rollup || getReadinessRollupCached();
  return (readinessRollup.items || [])
    .filter((gate) => gate.status !== "PASS")
    .filter((gate) => trackMatchesOwner({ id: gate.track, owner: gate.owner }, ownerName))
    .map((gate) => ({
      gate: gate.id,
      track: gate.track,
      owner: gate.owner,
      status: gate.status,
      blocker: gate.issue || null,
      blockingInputs: gate.blockingInputs || [],
      nextCommand: gate.nextCommand || null,
    }));
}

function missingEvidenceArtifactsForOwner(ownerName, rollup = null) {
  const acceptance = buildEvidenceAcceptance({ rollupOverride: rollup });
  return (acceptance.items || [])
    .filter((item) => trackMatchesOwner({ id: item.track, owner: item.owner }, ownerName))
    .flatMap((item) => (item.artifactChecks || [])
      .filter((artifact) => artifact.present === false)
      .map((artifact) => ({
        gate: item.gate,
        track: item.track,
        owner: item.owner,
        dispatchOwner: dispatchOwnerForRawOwner(item.owner),
        artifact: artifact.artifact,
        acceptanceCommand: item.acceptanceCommand,
      })));
}

function queueLanesForOwner(ownerName, rollup = null) {
  const queue = buildNextActionQueue({ rollupOverride: rollup });
  const acceptance = buildEvidenceAcceptance({ rollupOverride: rollup });
  return (queue.queue || [])
    .filter((item) => item.dispatchOwner === ownerName || item.owner === ownerName)
    .map((item) => ({
      order: item.order,
      lane: item.lane,
      owner: item.owner,
      dispatchOwner: item.dispatchOwner,
      status: item.status,
      title: item.title,
      blocker: item.blocker || null,
      command: item.command || null,
      acceptanceCommands: acceptanceCommandsForLane(item, acceptance),
      expectedArtifacts: artifactsForLane(item, acceptance),
      missingArtifacts: missingArtifactsForLane(item, acceptance),
      sourcePlan: item.sourcePlan || null,
      missingEvidenceArtifactCount: item.missingEvidenceArtifactCount ?? 0,
    }));
}

function buildOwnerPacket(owner, { rollup = null } = {}) {
  const evidenceGaps = evidenceGapsForOwner(owner.owner);
  const blockingInputGates = blockingInputGatesForOwner(owner.owner, rollup);
  const missingEvidenceArtifacts = missingEvidenceArtifactsForOwner(owner.owner, rollup);
  const queueLanes = queueLanesForOwner(owner.owner, rollup);
  const laneKeys = queueLanes.map((lane) => `${owner.owner}:${lane.lane}`);
  const receiptWorkflow = {
    laneKeys,
    initCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>",
    autofillCommand: laneCompletionReceiptAutofillCommand,
    editRule: "update only this owner's laneReceipts entries, then leave unrelated owner/lane pairs unchanged",
    checkCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check --lane-completion-receipt-file=<receipt-file>",
    coverageCommand: "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown --lane-completion-receipt-file=<receipt-file>",
  };
  const blockingInputs = unique(blockingInputGates.flatMap((gate) => gate.blockingInputs || []));
  const blockingInputCount = blockingInputGates.reduce((count, gate) => count + (gate.blockingInputs || []).length, 0);
  const nextLane = queueLanes[0] || null;
  const commands = unique([
    owner.owner === "release-infra" ? envInitCheckCommand : null,
    owner.owner === "release-infra" ? envInitCommand : null,
    owner.nextCommand,
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
    owner.owner === "release-infra" ? "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file> --next-action-env-receipt-output=<receipt-file>" : null,
    owner.owner === "release-infra" ? "node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --next-action-env-receipt-file=<receipt-file>" : null,
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs",
    "node scripts/ddd-release-readiness-summary.mjs",
    receiptWorkflow.initCommand,
    receiptWorkflow.autofillCommand,
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template-markdown",
    receiptWorkflow.checkCommand,
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>",
    receiptWorkflow.coverageCommand,
    "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>",
    "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
  ]);
  return {
    generatedAt,
    owner: owner.owner,
    blockers: owner.blockers,
    placeholders: owner.placeholders,
    secretKeys: owner.secretKeys,
    laneCount: queueLanes.length,
    nextCommand: nextLane?.command || owner.nextCommand || null,
    missingEvidenceArtifactCount: missingEvidenceArtifacts.length,
    blockingInputCount,
    handoffPath: owner.handoffPath,
    keys: owner.keys || [],
    ownerInputReasons: owner.ownerInputReasons || [],
    commands,
    evidenceGaps,
    blockingInputGates,
    blockingInputs,
    missingEvidenceArtifacts,
    queueLanes,
    receiptWorkflow,
    safety: [
      "Do not commit populated secrets.",
      "Use the secure release env file referenced by DDD_RELEASE_ENV_FILE.",
      "Re-run the staging checklist after owner inputs are merged.",
    ],
  };
}

function renderOwnerPacket(owner, { rollup = null } = {}) {
  const packet = buildOwnerPacket(owner, { rollup });
  const lines = [
    `# DDD Staging Owner Packet: ${packet.owner}`,
    "",
    `Generated at: ${packet.generatedAt}`,
    `Owner: ${packet.owner}`,
    `Blockers: ${packet.blockers}`,
    `Placeholders: ${packet.placeholders}`,
    `Secret keys: ${packet.secretKeys}`,
    `Handoff: ${packet.handoffPath}`,
    "",
    "## Required Keys",
    "",
    ...(packet.keys.length > 0 ? packet.keys.map((key) => `- ${key}`) : ["- none"]),
    "",
    "## Input Reasons",
    "",
    ...(packet.ownerInputReasons.length > 0 ? packet.ownerInputReasons.map((reason) => `- ${reason}`) : ["- none"]),
    "",
    "## Post-Fill Validation",
    "",
    commandList(packet.commands) || "- none",
    "",
    "## Queue Lanes",
    "",
    ...(packet.queueLanes.length > 0
      ? [
        "| Order | Lane | Status | Missing artifacts | Command | Source |",
        "| ---: | --- | --- | ---: | --- | --- |",
        ...packet.queueLanes.map((lane) => [
          lane.order,
          `\`${lane.lane}\``,
          lane.status,
          lane.missingEvidenceArtifactCount,
          `\`${lane.command || "none"}\``,
          `\`${lane.sourcePlan || "none"}\``,
        ].map((value) => String(value).replaceAll("|", "\\|")).join(" | ")).map((row) => `| ${row} |`),
        "",
      ]
      : ["- none", ""]),
    "## Submission Routes",
    "",
    ...(packet.queueLanes.length > 0
      ? packet.queueLanes.flatMap((lane) => [
        `### ${lane.lane}`,
        "",
        `Source plan: \`${lane.sourcePlan || "none"}\``,
        `Next command: \`${lane.command || "none"}\``,
        `Acceptance commands: ${lane.acceptanceCommands.length > 0 ? lane.acceptanceCommands.map((command) => `\`${command}\``).join(", ") : "none"}`,
        `Expected artifacts: ${(lane.expectedArtifacts || []).length > 0 ? (lane.expectedArtifacts || []).map((artifact) => `\`${artifact}\``).join(", ") : "none"}`,
        `Currently missing artifacts: ${(lane.missingArtifacts || []).length > 0 ? (lane.missingArtifacts || []).map((artifact) => `\`${artifact}\``).join(", ") : "none"}`,
        "",
      ])
      : ["- none", ""]),
    "## Current Blocking Inputs",
    "",
    ...(packet.blockingInputGates.length > 0
      ? packet.blockingInputGates.flatMap((gate) => [
        `### ${gate.gate}`,
        "",
        `Status: ${gate.status}`,
        `First blocker: ${gate.blocker || "none"}`,
        `Next command: \`${gate.nextCommand || "none"}\``,
        `Blocking inputs: ${gate.blockingInputs.length > 0 ? gate.blockingInputs.join(", ") : "none"}`,
        "",
      ])
      : ["- none", ""]),
    "## Staging Evidence Gaps",
    "",
    ...(packet.evidenceGaps.length > 0
      ? packet.evidenceGaps.flatMap((gap) => [
        `### ${gap.id}: ${gap.title}`,
        "",
        `Reason: ${gap.reason}`,
        `Next command: \`${gap.nextCommand || "none"}\``,
        `Artifacts: ${gap.artifacts.join(", ") || "none"}`,
        `Env keys: ${gap.envKeys.join(", ") || "none"}`,
        "",
      ])
      : ["- none", ""]),
    "## Missing Evidence Artifacts",
    "",
    ...(packet.missingEvidenceArtifacts.length > 0
      ? packet.missingEvidenceArtifacts.map((artifact) => `- \`${artifact.artifact}\`: gate=${artifact.gate}; next=\`${artifact.acceptanceCommand || "none"}\``)
      : ["- none"]),
    "",
    "## Owner Completion Receipt",
    "",
    "- Fill `lane-completion-receipt.template.json` with redacted PASS/BLOCKED results for this owner's queue lanes.",
    "- A lane is complete only after its acceptance commands pass and expected artifacts are listed in providedArtifacts.",
    "- Submit the redacted receipt file to release-infra, then re-run final review with that receipt.",
    `- Edit rule: ${packet.receiptWorkflow.editRule}`,
    `- Lane keys: ${packet.receiptWorkflow.laneKeys.length > 0 ? packet.receiptWorkflow.laneKeys.map((key) => `\`${key}\``).join(", ") : "none"}`,
    "",
    "Commands:",
    commandList([
      packet.receiptWorkflow.initCommand,
      packet.receiptWorkflow.autofillCommand,
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-template-markdown",
      packet.receiptWorkflow.checkCommand,
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract --lane-completion-receipt-file=<receipt-file>",
      packet.receiptWorkflow.coverageCommand,
      "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown --lane-completion-receipt-file=<receipt-file>",
      "node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>",
    ]),
    "",
    "## Safety",
    "",
    ...packet.safety.map((item) => `- ${item}`),
    "",
  ];
  return `${lines.join("\n")}\n`;
}

function renderOwnerPacketIndex(owners, { rollup = null } = {}) {
  const lines = [
    "# DDD Staging Owner Packets",
    "",
    `Generated at: ${generatedAt}`,
    `Status: ${checklist.status}`,
    `Final recommendation: ${checklist.finalRecommendation}`,
    "",
    "## Dispatch Order",
    "",
    "| Owner | Blockers | Secret keys | Missing artifacts | Markdown | JSON | Env template |",
    "| --- | ---: | ---: | ---: | --- | --- | --- |",
    ...owners.map((owner) => {
      const mdFileName = `${slug(owner.owner)}.md`;
      const jsonFileName = `${slug(owner.owner)}.json`;
      const envFileName = `${slug(owner.owner)}.blocking-inputs.template.env`;
      const missingArtifactCount = missingEvidenceArtifactsForOwner(owner.owner, rollup).length;
      return `| ${owner.owner} | ${owner.blockers} | ${owner.secretKeys} | ${missingArtifactCount} | [${mdFileName}](${mdFileName}) | [${jsonFileName}](${jsonFileName}) | [${envFileName}](${envFileName}) |`;
    }),
    "",
    "## Packet Contents",
    "",
    "- Each owner packet includes owner-scoped required keys, input reasons, post-fill validation commands, current blocking inputs, staging evidence gaps, and missing evidence artifacts.",
    "- Use the per-owner env template for value collection only; merge populated values into a secure release env file outside committed artifacts.",
    "",
    "## Validation",
    "",
    "- After owner values are merged into the secure release env file, run `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`.",
    "- Then run `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh` before expensive evidence collection.",
    "- Do not cut over until `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh` exits cleanly.",
    "",
  ];
  return `${lines.join("\n")}\n`;
}

function renderMarkdown(report) {
  const lines = [
    "# DDD Staging Execution Checklist",
    "",
    `Generated at: ${report.generatedAt}`,
    `Status: ${report.status}`,
    `Final recommendation: ${report.finalRecommendation}`,
    `Cutover allowed: ${report.cutoverAllowed}`,
    `Evidence gate: blockers=${report.gate.blockers ?? "unknown"} warnings=${report.gate.warnings ?? "unknown"} strict=${report.gate.strict}`,
    "",
    "## First Move",
    "",
    report.releaseEnv.ready
      ? "- Release env is marked ready; continue with deployable image evidence and staging runtime smokes."
      : `- Complete the release env owner handoff first: ${report.releaseEnv.ownerHandoff || "artifacts/ddd/release/release-env-owner-handoff-redacted.json"}.`,
    `- If a populated env file does not exist yet, check the initializer with \`${envInitCheckCommand}\`, then initialize it with \`${envInitCommand}\`.`,
    "- Run `DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh` before expensive evidence collection.",
    "- Do not cut over until `DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh` exits cleanly.",
    "",
    "## Release Env Owner Handoff",
    "",
    ...(report.releaseEnv.ownerBlockerSummary?.length > 0
      ? report.releaseEnv.ownerBlockerSummary.flatMap((owner) => [
        `- ${owner.owner}: blockers=${owner.blockers} placeholders=${owner.placeholders} secretKeys=${owner.secretKeys} handoff=${owner.handoffPath}`,
        `  - reasons: ${owner.ownerInputReasons?.join(", ") || "none"}`,
        `  - keys: ${owner.keys?.join(", ") || "none"}`,
      ])
      : ["- no owner handoff blockers reported"]),
    "",
    "## Blocked Cutover Items",
    "",
    ...(report.blockedCutoverItems.length > 0
      ? report.blockedCutoverItems.map((item) => `- ${item.id}: ${item.title} pending=${item.pendingItems}`)
      : ["- none"]),
    "",
    "## Immediate P0 Waves",
    "",
    ...(report.immediateWaves.length > 0
      ? report.immediateWaves.flatMap((wave) => [
        `### ${wave.batchId}`,
        "",
        `Owner: ${wave.owner}`,
        `Receipt status: ${wave.receiptStatus}`,
        "",
        commandList(wave.commands) || "- no commands listed",
        "",
      ])
      : ["- none", ""]),
    "## Execution Tracks",
    "",
    ...report.tracks.flatMap((track) => [
      `### ${track.id}: ${track.title}`,
      "",
      `Status: ${track.status}`,
      `Owner: ${track.owner}`,
      `Reason: ${track.reason}`,
      `Env keys: ${track.envKeys.join(", ") || "none"}`,
      "",
      ...(track.setupCommands?.length > 0
        ? [
          "Setup:",
          commandList(track.setupCommands),
          "",
        ]
        : []),
      "Commands:",
      commandList(track.commands) || "- none",
      "",
      `Artifacts: ${track.artifacts.join(", ")}`,
      "",
    ]),
    "## Source Artifacts",
    "",
    ...Object.entries(report.sourceArtifacts)
      .filter(([, value]) => value)
      .map(([key, value]) => `- ${key}: ${value}`),
    "",
  ];
  return `${lines.join("\n")}\n`;
}

fs.mkdirSync(path.dirname(outputBase), { recursive: true });

if (!markdownOnly) {
  fs.writeFileSync(`${outputBase}.json`, `${JSON.stringify(checklist, null, 2)}\n`);
}
if (!jsonOnly) {
  fs.writeFileSync(`${outputBase}.md`, renderMarkdown(checklist));
}
if (ownerPackets) {
  fs.mkdirSync(ownerPacketDir, { recursive: true });
  const rollup = loadReadinessRollup();
  fs.writeFileSync(path.join(ownerPacketDir, "README.md"), renderOwnerPacketIndex(selectedOwnerPackets, { rollup }));
  for (const owner of selectedOwnerPackets) {
    fs.writeFileSync(path.join(ownerPacketDir, `${slug(owner.owner)}.md`), renderOwnerPacket(owner, { rollup }));
    fs.writeFileSync(
      path.join(ownerPacketDir, `${slug(owner.owner)}.blocking-inputs.template.env`),
      renderBlockingInputsEnvTemplate(buildOwnerBlockingInputsEnvReport(owner, { rollupOverride: rollup })),
    );
  }
}

console.log(`[ddd-staging-execution-checklist] status=${checklist.status}; tracks=${checklist.tracks.length}; output=${outputBase}`);
if (ownerPackets) {
  console.log(`[ddd-staging-execution-checklist] ownerPackets=${selectedOwnerPackets.length}; dir=${ownerPacketDir}`);
  if (ownerFilter && selectedOwnerPackets.length === 0) {
    console.log(`[ddd-staging-execution-checklist] ownerFilter=${ownerFilter}; matched=0`);
  }
}
