#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const workflowFile = path.join(repoRoot, ".github", "workflows", "ddd-release-evidence.yml");
const ciWorkflowFile = path.join(repoRoot, ".github", "workflows", "ci.yml");
const productionReadinessPlanFile = path.join(repoRoot, "docs", "37-production-readiness-execution-plan.md");

const workflowText = fs.readFileSync(workflowFile, "utf8");
const ciWorkflowText = fs.readFileSync(ciWorkflowFile, "utf8");
const productionReadinessPlanText = fs.readFileSync(productionReadinessPlanFile, "utf8");

function stepNames(text) {
  return [...text.matchAll(/^\s{6}- name:\s+(.+)$/gm)].map((match) => match[1].trim());
}

function stepIndex(name) {
  const index = workflowText.indexOf(`- name: ${name}`);
  assert(index >= 0, `release evidence workflow must include step: ${name}`);
  return index;
}

function assertBefore(left, right) {
  assert(stepIndex(left) < stepIndex(right), `${left} must run before ${right}`);
}

function assertTextBefore(text, left, right, message) {
  const leftIndex = text.indexOf(left);
  const rightIndex = text.indexOf(right);
  assert(leftIndex >= 0, `${message}: missing left marker`);
  assert(rightIndex >= 0, `${message}: missing right marker`);
  assert(leftIndex < rightIndex, message);
}

const names = stepNames(workflowText);
assert(names.length > 0, "release evidence workflow must declare named steps");
const duplicateNames = names.filter((name, index) => names.indexOf(name) !== index);
assert.deepEqual(duplicateNames, [], "release evidence workflow step names must be unique");

assertBefore("Validate release evidence dispatch inputs", "Prepare release evidence environment file");
assertBefore("Prepare release evidence environment file", "Preflight release configuration evidence");
assertBefore("Prepare release evidence environment file", "Prepare lane completion receipt input");
assertBefore("Prepare lane completion receipt input", "Validate lane completion receipt");
assertBefore("Validate lane completion receipt", "Preflight release configuration evidence");
assertBefore("Prepare lane completion receipt input", "Preflight release configuration evidence");
assertBefore("Validate release evidence dispatch inputs", "Preflight release configuration evidence");
assertBefore("Preflight release configuration evidence", "Run release evidence orchestrator");
assertBefore("Run release evidence orchestrator", "Refresh final go no-go packet");
assertBefore("Refresh final go no-go packet", "Generate release unblock brief");
assertBefore("Generate release unblock brief", "Validate release unblock brief contract");
assertBefore("Validate release unblock brief contract", "Validate final owner queue run report");
assertBefore("Validate final owner queue run report", "Validate release execution run report");
assertBefore("Validate release execution run report", "Validate release artifact integrity contract");
assertBefore("Validate release artifact integrity contract", "Validate release artifact integrity gate contract");
assertBefore("Validate release artifact integrity gate contract", "Validate release artifact path leak contract");
assertBefore("Validate release artifact path leak contract", "Validate release cutover contract");
assertBefore("Validate release cutover contract", "Validate release final go no-go gate contract");
assertBefore("Validate release final go no-go gate contract", "Validate release preflight gate contract");
assertBefore("Validate release preflight gate contract", "Validate release evidence gate contract");
assertBefore("Validate release evidence gate contract", "Validate EXPLAIN gate report");
assertBefore("Validate EXPLAIN gate report", "Capture release preflight output");
assertBefore("Capture release preflight output", "Validate release preflight capture");
assertBefore("Validate lane completion receipt", "Generate staging handoff bundle");
assertBefore("Validate release preflight capture", "Generate staging handoff bundle");
assertBefore("Refresh release manifest after preflight capture", "Upload release evidence artifacts");
assertBefore("Upload release evidence artifacts", "Append release readiness summary");
assertBefore("Append release readiness summary", "Enforce release evidence result");

assert(
  workflowText.includes("node scripts/ddd-release-execution-run-report-contract.mjs"),
  "release evidence workflow must validate release execution run reports",
);
assert(
  workflowText.includes("node scripts/ddd-release-execution-run-report-summary.mjs"),
  "release evidence workflow must append release execution run report summaries",
);
assert(
  workflowText.includes("node scripts/ddd-release-artifact-path-leak-contract.mjs"),
  "release evidence workflow must validate release artifacts for local path leaks",
);
assert(
  workflowText.includes("DDD_RELEASE_PREFLIGHT_ENFORCE=1"),
  "release evidence workflow must keep strict preflight enforcement available",
);
assert(
  workflowText.includes("node scripts/ddd-release-redact-output.mjs < artifacts/ddd/release/release-preflight-output.txt"),
  "release evidence workflow must redact captured preflight output",
);
assert(
  workflowText.includes("DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false"),
  "release evidence workflow must refresh manifest after preflight capture without failing before upload",
);
assert(
  workflowText.includes("DDD_RELEASE_MANIFEST_CHECK_ENV=true"),
  "release evidence workflow must refresh manifest provenance preflight before the final manifest checksum",
);
assert(
  workflowText.includes("node scripts/ddd-release-unblock-brief-contract.mjs"),
  "release evidence workflow must revalidate unblock brief after the final pre-upload manifest refresh",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle"),
  "release evidence workflow must generate the staging handoff bundle before uploading artifacts",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle-verify"),
  "release evidence workflow must verify the staging handoff bundle before uploading artifacts",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=artifacts/ddd/release/staging-handoff-bundle/next-action-env-receipt.sample.json"),
  "release evidence workflow must validate the bundled next-action env receipt sample",
);
assert(
  workflowText.includes("- name: Validate release evidence dispatch inputs"),
  "release evidence workflow must validate workflow_dispatch inputs before run-mode preflight",
);
assert(
  workflowText.includes("if: inputs.mode == 'run'"),
  "release evidence workflow dispatch input validation must run before run-mode evidence collection",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-inputs-contract --release-evidence-dispatch-inputs-file=\"$dispatch_inputs_file\""),
  "release evidence workflow must hard-validate actual workflow_dispatch inputs with the contract",
);
for (const [workflowInput, envKey, payloadKey] of [
  ["mode", "DDD_WORKFLOW_INPUT_MODE", "mode"],
  ["strict", "DDD_WORKFLOW_INPUT_STRICT", "strict"],
  ["github_environment", "DDD_WORKFLOW_INPUT_GITHUB_ENVIRONMENT", "github_environment"],
  ["evidence_environment", "DDD_WORKFLOW_INPUT_EVIDENCE_ENVIRONMENT", "evidence_environment"],
  ["backend_base_url", "DDD_WORKFLOW_INPUT_BACKEND_BASE_URL", "backend_base_url"],
  ["frontend_base_url", "DDD_WORKFLOW_INPUT_FRONTEND_BASE_URL", "frontend_base_url"],
  ["max_artifact_age_hours", "DDD_WORKFLOW_INPUT_MAX_ARTIFACT_AGE_HOURS", "max_artifact_age_hours"],
  ["expect_ai_remote", "DDD_WORKFLOW_INPUT_EXPECT_AI_REMOTE", "expect_ai_remote"],
  ["expect_frontend_deployed", "DDD_WORKFLOW_INPUT_EXPECT_FRONTEND_DEPLOYED", "expect_frontend_deployed"],
  ["promote_authenticated_baseline", "DDD_WORKFLOW_INPUT_PROMOTE_AUTHENTICATED_BASELINE", "promote_authenticated_baseline"],
  ["lane_completion_receipt_file", "DDD_WORKFLOW_INPUT_LANE_COMPLETION_RECEIPT_FILE", "lane_completion_receipt_file"],
  ["lane_completion_receipt_base64", "DDD_WORKFLOW_INPUT_LANE_COMPLETION_RECEIPT_BASE64", "lane_completion_receipt_base64"],
]) {
  assert(
    workflowText.includes(`${envKey}: \${{ inputs.${workflowInput} }}`),
    `release evidence workflow must expose ${workflowInput} to dispatch input validation`,
  );
  assert(
    workflowText.includes(`${payloadKey}: process.env.${envKey}`) || workflowText.includes(`${payloadKey}: process.env.${envKey} || ""`),
    `release evidence workflow dispatch validation payload must include ${payloadKey}`,
  );
}
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --handoff-summary-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append staging handoff summary to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --production-closeout-status-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append production closeout status to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append production cutover audit to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --release-owner-closeout-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append release-owner closeout to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown >> \"$GITHUB_STEP_SUMMARY\" || true"),
  "release evidence workflow must expose lane receipt coverage in release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append evidence closure board to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-fragments-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append lane receipt fragments to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-draft-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append lane receipt draft to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append lane receipt submission plan to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append lane receipt submission check to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append workflow dispatch input plan to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-command"),
  "release evidence workflow must append workflow dispatch command template to release evidence summary",
);
assertTextBefore(
  workflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --handoff-summary-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --production-closeout-status-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "release evidence workflow must show handoff summary before the production closeout status",
);
assertTextBefore(
  workflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --production-closeout-status-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "release evidence workflow must show production closeout status before the production cutover audit",
);
assertTextBefore(
  workflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --release-owner-closeout-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "release evidence workflow must show production cutover audit before the release-owner closeout",
);
assertTextBefore(
  workflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --release-owner-closeout-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown >> \"$GITHUB_STEP_SUMMARY\" || true",
  "release evidence workflow must show closeout before lane coverage",
);
assertTextBefore(
  workflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown >> \"$GITHUB_STEP_SUMMARY\" || true",
  "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "release evidence workflow must show lane coverage before the evidence closure board",
);
assertTextBefore(
  workflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-fragments-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "release evidence workflow must show evidence closure board before receipt fragments",
);
assertTextBefore(
  workflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-fragments-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-draft-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "release evidence workflow must show receipt fragments before the receipt draft",
);
assertTextBefore(
  workflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-draft-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "release evidence workflow must show receipt draft before the receipt submission plan",
);
assertTextBefore(
  workflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "release evidence workflow must show receipt submission plan before the receipt submission check",
);
assertTextBefore(
  workflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "release evidence workflow must show receipt submission check before workflow dispatch inputs",
);
assertTextBefore(
  workflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "release evidence workflow must show workflow dispatch inputs before artifact gaps",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append evidence artifact gap report to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --explain-artifact-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append EXPLAIN artifact plan to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append operator progress to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --release-owner-daily-brief-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append release-owner daily brief to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append owner evidence intake to release evidence summary",
);
assertTextBefore(
  workflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --release-owner-daily-brief-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "release evidence workflow must show daily brief before owner evidence intake",
);
assertTextBefore(
  workflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --release-env-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "release evidence workflow must show owner evidence intake before detailed owner submission plans",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --release-env-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append release-env submission plan to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append Docker image submission plan to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append runtime/business submission plan to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append data-safety submission plan to release evidence summary",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --final-review-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "release evidence workflow must append staging final review to release evidence summary",
);
assert(
  workflowText.includes("lane_completion_receipt_file:"),
  "release evidence workflow must expose an optional lane completion receipt input",
);
assert(
  workflowText.includes("DDD_LANE_COMPLETION_RECEIPT_FILE: ${{ inputs.lane_completion_receipt_file }}"),
  "release evidence workflow must pass lane completion receipt input through the staging checklist environment",
);
assert(
  workflowText.includes("lane_completion_receipt_base64:"),
  "release evidence workflow must expose an optional base64 lane completion receipt input",
);
assert(
  workflowText.includes("DDD_LANE_COMPLETION_RECEIPT_BASE64: ${{ inputs.lane_completion_receipt_base64 }}"),
  "release evidence workflow must pass base64 lane completion receipt input to the prepare step",
);
assert(
  workflowText.includes("receipt_file=\"artifacts/ddd/release/lane-completion-receipt.submitted.json\""),
  "release evidence workflow must decode base64 lane receipts beside the staging handoff bundle",
);
assert(
  workflowText.includes("printf '%s' \"$DDD_LANE_COMPLETION_RECEIPT_BASE64\" | base64 --decode > \"$receipt_file\""),
  "release evidence workflow must decode base64 lane receipt content without printing it",
);
assert(
  workflowText.includes("echo \"DDD_LANE_COMPLETION_RECEIPT_FILE=$receipt_file\" >> \"$GITHUB_ENV\""),
  "release evidence workflow must route decoded lane receipts through DDD_LANE_COMPLETION_RECEIPT_FILE",
);
assert(
  workflowText.includes("if: inputs.lane_completion_receipt_file != '' || inputs.lane_completion_receipt_base64 != ''"),
  "release evidence workflow must fail fast on invalid lane completion receipts when either optional input is provided",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-contract"),
  "release evidence workflow must hard-validate the provided lane completion receipt contract",
);
assert(
  workflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage"),
  "release evidence workflow must hard-validate the provided lane completion receipt coverage",
);

assert(
  ciWorkflowText.includes("node --check scripts/ddd-release-workflow-structure.test.mjs"),
  "CI syntax checks must cover release workflow structure tests",
);
assert(
  ciWorkflowText.includes("node --check scripts/ddd-production-evidence-readiness.test.mjs"),
  "CI syntax checks must cover production evidence readiness tests",
);
assert(
  ciWorkflowText.includes("node --check scripts/ddd-lane-completion-receipt-autofill.mjs"),
  "CI syntax checks must cover lane completion receipt autofill",
);
assert(
  ciWorkflowText.includes("node --check scripts/ddd-lane-completion-receipt-autofill.test.mjs"),
  "CI syntax checks must cover lane completion receipt autofill tests",
);
assert(
  ciWorkflowText.includes("node --check scripts/ddd-release-env-fill-checklist.mjs"),
  "CI syntax checks must cover release env fill checklist",
);
assert(
  ciWorkflowText.includes("node --check scripts/ddd-release-env-fill-checklist.test.mjs"),
  "CI syntax checks must cover release env fill checklist tests",
);
assert(
  ciWorkflowText.includes("node --check scripts/security-assessment-report-contract.test.mjs"),
  "CI syntax checks must cover security assessment report contract tests",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-release-workflow-structure.test.mjs"),
  "CI must run release workflow structure tests",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-production-evidence-readiness.test.mjs"),
  "CI must run production evidence readiness tests",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-lane-completion-receipt-autofill.test.mjs"),
  "CI must run lane completion receipt autofill tests",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-release-env-fill-checklist.test.mjs"),
  "CI must run release env fill checklist tests",
);
assert(
  ciWorkflowText.includes("node scripts/security-assessment-report-contract.test.mjs"),
  "CI must run security assessment report contract tests",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle"),
  "CI must generate the staging handoff bundle for release-infra",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle-verify"),
  "CI must verify the staging handoff bundle manifest before upload",
);
assert(
  ciWorkflowText.includes("GITHUB_STEP_SUMMARY"),
  "CI must append a staging handoff summary for release-infra",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --handoff-summary-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must use the tested summary renderer",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --production-closeout-status-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include production closeout status",
);
assert(
  ciWorkflowText.includes("cat artifacts/ddd/release/staging-handoff-bundle/production-unblock-quickstart.md >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include production unblock quickstart",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include production cutover audit",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --production-unblock-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include production unblock plan",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include production evidence readiness",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --release-owner-closeout-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include release-owner closeout",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --handoff-summary-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --production-closeout-status-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show handoff summary before production closeout status",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --production-closeout-status-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "cat artifacts/ddd/release/staging-handoff-bundle/production-unblock-quickstart.md >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show production closeout status before production unblock quickstart",
);
assertTextBefore(
  ciWorkflowText,
  "cat artifacts/ddd/release/staging-handoff-bundle/production-unblock-quickstart.md >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show production unblock quickstart before production cutover audit",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --production-unblock-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show production cutover audit before production unblock plan",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --production-unblock-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show production unblock plan before production evidence readiness",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --release-owner-closeout-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show production evidence readiness before release-owner closeout",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --final-review-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include the release-owner final review",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --operator-progress-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include operator progress",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --evidence-artifact-gap-report-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include evidence artifact gap report",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --explain-artifact-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include EXPLAIN artifact plan",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --release-owner-daily-brief-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include release-owner daily brief",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include owner evidence intake",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --release-owner-daily-brief-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show daily brief before owner evidence intake",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --release-env-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show owner evidence intake before detailed owner plans",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --release-env-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include release-env submission plan",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-release-env-fill-checklist.mjs --markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include release-env fill checklist",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-release-env-fill-checklist.mjs --env-template"),
  "CI staging handoff summary must include release-env fill template",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --release-env-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-release-env-fill-checklist.mjs --markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show release-env submission plan before release-env fill checklist",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-release-env-fill-checklist.mjs --markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-release-env-fill-checklist.mjs --env-template",
  "CI staging handoff summary must show release-env fill checklist before release-env fill template",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-release-env-fill-checklist.mjs --env-template",
  "node scripts/ddd-staging-execution-checklist.mjs --docker-image-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show release-env fill template before Docker plans",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --docker-image-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include Docker image submission plan",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --runtime-business-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include runtime/business submission plan",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --data-safety-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include data-safety submission plan",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-markdown >> \"$GITHUB_STEP_SUMMARY\" || true"),
  "CI staging handoff summary must include redacted next-action env receipt shape",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown >> \"$GITHUB_STEP_SUMMARY\" || true"),
  "CI staging handoff summary must expose owner lane receipt coverage before final review",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include evidence closure board",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-fragments-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include lane receipt fragments",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-draft-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include lane receipt draft",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include lane receipt submission plan",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include lane receipt submission check",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-plan-markdown >> \"$GITHUB_STEP_SUMMARY\""),
  "CI staging handoff summary must include release evidence dispatch input plan",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-command"),
  "CI staging handoff summary must include release evidence dispatch command template",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --handoff-summary-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --release-owner-closeout-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show handoff summary before the closeout",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --release-owner-closeout-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --closure-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show closeout before detailed closure planning",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-coverage-markdown >> \"$GITHUB_STEP_SUMMARY\" || true",
  "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show lane coverage before the evidence closure board",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --evidence-closure-board-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-fragments-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show evidence closure board before receipt fragments",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-fragments-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-draft-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show receipt fragments before the receipt draft",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --lane-receipt-draft-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show receipt draft before the receipt submission plan",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show receipt submission plan before the receipt submission check",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "node scripts/ddd-staging-execution-checklist.mjs --release-evidence-dispatch-plan-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show receipt submission check before workflow dispatch inputs",
);
assertTextBefore(
  ciWorkflowText,
  "node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-markdown >> \"$GITHUB_STEP_SUMMARY\" || true",
  "node scripts/ddd-staging-execution-checklist.mjs --lane-completion-submission-check-markdown >> \"$GITHUB_STEP_SUMMARY\"",
  "CI staging handoff summary must show receipt samples before lane submission check",
);
assert(
  ciWorkflowText.includes("node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=artifacts/ddd/release/staging-handoff-bundle/next-action-env-receipt.sample.json"),
  "CI must validate the redacted next-action env receipt sample contract",
);
assert(
  ciWorkflowText.includes("name: ddd-staging-handoff-bundle"),
  "CI must upload the staging handoff bundle artifact",
);
assert(
  ciWorkflowText.includes("if-no-files-found: error"),
  "CI staging handoff artifact upload must fail when the bundle is missing",
);
assert(
  ciWorkflowText.includes("retention-days: 14"),
  "CI staging handoff artifact retention must be explicit",
);
assert(
  productionReadinessPlanText.includes(".github/workflows/ddd-release-evidence.yml"),
  "production readiness plan must mention the formal release evidence workflow",
);
assert(
  productionReadinessPlanText.includes("ddd-release-evidence-<environment>-<run_number>"),
  "production readiness plan must document the formal release evidence artifact name shape",
);
assert(
  productionReadinessPlanText.includes("lane_completion_receipt_file"),
  "production readiness plan must document the formal release evidence lane receipt input",
);
assert(
  productionReadinessPlanText.includes("lane_completion_receipt_base64"),
  "production readiness plan must document the formal release evidence base64 lane receipt input",
);
assert(
  productionReadinessPlanText.includes("--lane-completion-receipt-base64 --lane-completion-receipt-file=<receipt-file>"),
  "production readiness plan must document how to generate the base64 lane receipt input",
);
assert(
  productionReadinessPlanText.includes("lane-completion-receipt.submitted.json"),
  "production readiness plan must document where decoded lane receipts are written",
);
assert(
  productionReadinessPlanText.includes("DDD_LANE_COMPLETION_RECEIPT_FILE"),
  "production readiness plan must document that the formal workflow exports the lane receipt input",
);
assert(
  productionReadinessPlanText.includes("hard validation step"),
  "production readiness plan must document formal workflow hard validation for supplied lane receipts",
);
assert(
  productionReadinessPlanText.includes("formal release evidence workflow generates and verifies `artifacts/ddd/release/staging-handoff-bundle`"),
  "production readiness plan must document that the release evidence workflow generates the staging handoff bundle",
);
assert(
  productionReadinessPlanText.includes("production-unblock-quickstart.md"),
  "production readiness plan must document the production unblock quickstart",
);
assert(
  productionReadinessPlanText.includes("release-env-fill.template.env"),
  "production readiness plan must document the release-env fill template",
);
assert(
  productionReadinessPlanText.includes("ddd-release-env-fill-checklist.mjs --env-template"),
  "production readiness plan must document the release-env fill template command",
);
assert(
  productionReadinessPlanText.includes("handoff summary, production closeout status, release-owner closeout, lane receipt coverage, closure board, receipt fragments, receipt draft, owner evidence intake, operator progress, daily brief, dispatch inputs, and final review"),
  "production readiness plan must document the release evidence summary handoff sections",
);
assert(
  productionReadinessPlanText.includes("--release-owner-daily-brief-markdown"),
  "production readiness plan must document the release-owner daily brief summary command",
);
assert(
  productionReadinessPlanText.includes("--owner-evidence-intake-markdown"),
  "production readiness plan must document the owner evidence intake summary command",
);
assert(
  productionReadinessPlanText.includes("owner evidence intake"),
  "production readiness plan must document owner evidence intake handoff",
);
assert(
  productionReadinessPlanText.includes("--evidence-artifact-gap-report-markdown"),
  "production readiness plan must document the evidence artifact gap report command",
);
assert(
  productionReadinessPlanText.includes("--lane-completion-submission-plan-markdown"),
  "production readiness plan must document the lane receipt submission plan command",
);
assert(
  productionReadinessPlanText.includes("--lane-receipt-fragments-markdown"),
  "production readiness plan must document the lane receipt fragments command",
);
assert(
  productionReadinessPlanText.includes("--lane-receipt-draft-markdown"),
  "production readiness plan must document the lane receipt draft command",
);
assert(
  productionReadinessPlanText.includes("--release-evidence-dispatch-plan-markdown"),
  "production readiness plan must document the release evidence dispatch plan command",
);
assert(
  productionReadinessPlanText.includes("--release-evidence-dispatch-inputs"),
  "production readiness plan must document the release evidence dispatch input payload command",
);
assert(
  productionReadinessPlanText.includes("--release-evidence-dispatch-command"),
  "production readiness plan must document the release evidence dispatch gh command template",
);
assert(
  productionReadinessPlanText.includes("--release-evidence-dispatch-inputs-contract"),
  "production readiness plan must document the release evidence dispatch inputs contract",
);
assert(
  productionReadinessPlanText.includes("actual `workflow_dispatch` input values"),
  "production readiness plan must document formal workflow run-mode dispatch input validation",
);
assert(
  productionReadinessPlanText.includes("--production-closeout-status-markdown"),
  "production readiness plan must document the production closeout status command",
);
assert(
  productionReadinessPlanText.includes("5-lane receipt assembly skeleton"),
  "production readiness plan must document the lane receipt fragments assembly skeleton",
);
assert(
  productionReadinessPlanText.includes("lane-receipt-fragments.md"),
  "production readiness plan must document the handoff bundle lane receipt fragments Markdown file",
);
assert(
  productionReadinessPlanText.includes("--evidence-closure-board-markdown"),
  "production readiness plan must document the evidence closure board command",
);
assert(
  productionReadinessPlanText.includes("--evidence-closure-board-csv"),
  "production readiness plan must document the evidence closure board CSV command",
);
assert(
  productionReadinessPlanText.includes("each PASS lane must also include `completedAt` and `completedBy`"),
  "production readiness plan must document lane completion receipt audit fields",
);
assert(
  productionReadinessPlanText.includes("each owner:lane key may appear only once"),
  "production readiness plan must document unique lane completion receipt keys",
);
assert(
  productionReadinessPlanText.includes("`summary` reports `passLaneCount`, `blockedLaneCount`, `duplicateLaneKeys`, and `passLaneKeysMissingAudit`"),
  "production readiness plan must document lane completion receipt contract summary fields",
);
assert(
  productionReadinessPlanText.includes("It also renders `## Contract Summary` with PASS/BLOCKED lane counts"),
  "production readiness plan must document lane receipt coverage Markdown contract summary",
);
assert(
  productionReadinessPlanText.includes("--release-env-submission-plan-markdown"),
  "production readiness plan must document the release-env submission plan command",
);
assert(
  productionReadinessPlanText.includes("--docker-image-submission-plan-markdown"),
  "production readiness plan must document the Docker image submission plan command",
);
assert(
  productionReadinessPlanText.includes("--runtime-business-submission-plan-markdown"),
  "production readiness plan must document the runtime/business submission plan command",
);
assert(
  productionReadinessPlanText.includes("--data-safety-submission-plan-markdown"),
  "production readiness plan must document the data-safety submission plan command",
);
assert(
  productionReadinessPlanText.includes("## Lane Routes"),
  "production readiness plan must document lane route Markdown sections",
);
assert(
  productionReadinessPlanText.includes("## Owner Lane Routes"),
  "production readiness plan must document final-review owner lane routes",
);
assert(
  productionReadinessPlanText.includes("## Evidence Closure"),
  "production readiness plan must document final-review evidence closure section",
);
assert(
  productionReadinessPlanText.includes("evidence closure board closed/open lane counts"),
  "production readiness plan must document final-review evidence closure counts",
);
assert(
  productionReadinessPlanText.includes("## Submission Flow"),
  "production readiness plan must document lane receipt submission flow",
);
assert(
  productionReadinessPlanText.includes("non-empty `laneRoutes`"),
  "production readiness plan must document handoff bundle lane route verification",
);
assert(
  productionReadinessPlanText.includes("receipt-template `submissionFlow`"),
  "production readiness plan must document handoff bundle receipt submission flow verification",
);
assert(
  productionReadinessPlanText.includes("lane receipt fragment JSON lane count and required owner:lane keys"),
  "production readiness plan must document handoff bundle lane receipt fragment verification",
);
assert(
  productionReadinessPlanText.includes("evidence closure board Markdown title and lane table"),
  "production readiness plan must document evidence closure board Markdown verification",
);
assert(
  productionReadinessPlanText.includes("evidence closure board CSV header and lane row count"),
  "production readiness plan must document evidence closure board CSV verification",
);
assert(
  productionReadinessPlanText.includes("release-owner-closeout.md` is the single-page closeout entry"),
  "production readiness plan must document release-owner closeout entry",
);
assert(
  productionReadinessPlanText.includes("release-owner closeout immediate next lane and required command sequence markers"),
  "production readiness plan must document release-owner closeout verifier markers",
);
assert(
  productionReadinessPlanText.includes("evidence closure board JSON lane count alignment with `owner-lane-matrix.json`"),
  "production readiness plan must document evidence closure board lane count verification",
);
assert(
  productionReadinessPlanText.includes("owner-dispatch lane arrays with `laneCount`, `command`, and `sourcePlan`"),
  "production readiness plan must document owner dispatch route verification",
);
assert(
  productionReadinessPlanText.includes("owner packet top-level JSON summary fields including `laneCount`, `nextCommand`, `missingEvidenceArtifactCount`, and `blockingInputCount`"),
  "production readiness plan must document owner packet top-level JSON summary verification",
);
assert(
  productionReadinessPlanText.includes("owner packet submission-route markers including `Expected artifacts:` and `Currently missing artifacts:`"),
  "production readiness plan must document owner packet submission route marker verification",
);
assert(
  productionReadinessPlanText.includes("owner packet lane fragments for each lane name, source plan, next command, expected artifact, and missing artifact"),
  "production readiness plan must document owner packet lane fragment verification",
);
assert(
  productionReadinessPlanText.includes("owner packet JSON `queueLanes` field alignment with `owner-dispatch.json`"),
  "production readiness plan must document owner packet JSON queue lane verification",
);
assert(
  productionReadinessPlanText.includes("writes status views, lane routes, receipt submission flow, lane receipt fragments, evidence closure board, submission plans, owner dispatch"),
  "production readiness plan evidence matrix must summarize the staging handoff bundle execution contents",
);
assert(
  productionReadinessPlanText.includes("lane-by-lane closeout view"),
  "production readiness plan must document the evidence closure board closeout view",
);
assert(
  productionReadinessPlanText.includes("verifier checks manifest/hash plus required Markdown markers"),
  "production readiness plan evidence matrix must summarize structural handoff bundle verification",
);
assert(
  productionReadinessPlanText.includes("owner packet top-level JSON summary alignment"),
  "production readiness plan evidence matrix must document owner packet top-level JSON summary alignment",
);
assert(
  productionReadinessPlanText.includes("evidence closure board Markdown and lane count alignment"),
  "production readiness plan evidence matrix must document evidence closure board verification",
);
assert(
  productionReadinessPlanText.includes("lane submission routes, and final review to the release evidence Step Summary"),
  "production readiness plan evidence matrix must document release evidence submission routes",
);
assert(
  productionReadinessPlanText.includes("owner packet `Expected artifacts:` / `Currently missing artifacts:` route markers"),
  "production readiness plan evidence matrix must document owner packet route marker verification",
);
assert(
  productionReadinessPlanText.includes("lane fragment alignment with `owner-dispatch.json`"),
  "production readiness plan evidence matrix must document owner packet fragment alignment",
);
assert(
  productionReadinessPlanText.includes("owner packet JSON `queueLanes` alignment with `owner-dispatch.json`"),
  "production readiness plan evidence matrix must document owner packet JSON alignment",
);

console.log("[ddd-release-workflow-structure.test] ok");
