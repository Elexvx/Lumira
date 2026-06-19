#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  requiredOrchestratorPreflightCheckIds,
  requiredOrchestratorStepIds,
} from "./ddd-release-orchestrator-contract.mjs";

const repoRoot = fileURLToPath(new URL("..", import.meta.url));
const outputDir = mkdtempSync(join(tmpdir(), "lumira-orchestrator-sync-"));
const result = spawnSync("node", ["scripts/ddd-release-evidence-orchestrator.mjs", "--strict"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ORCHESTRATOR_DIR: outputDir,
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "orchestrator-sync-test",
    DDD_RELEASE_CANDIDATE: "orchestrator-sync-sha",
    DDD_EVIDENCE_OPERATOR: "orchestrator-sync-runner",
  },
});

assert.equal(result.status, 0, result.stderr || result.stdout);

const report = JSON.parse(readFileSync(join(outputDir, "orchestrator-report.json"), "utf8"));
assert.deepEqual(
  report.selectedSteps.map((step) => step.id),
  requiredOrchestratorStepIds,
  "orchestrator selected step ids must stay synchronized with the release gate contract",
);
assert.deepEqual(
  report.preflight.checks.map((check) => check.id),
  requiredOrchestratorPreflightCheckIds,
  "orchestrator preflight check ids must stay synchronized with the release gate contract",
);

for (const step of report.selectedSteps) {
  assert.ok(step.command, `${step.id} must expose the command in the orchestrator report`);
  assert.equal(step.strictEnv, true, `${step.id} must receive strict evidence env in strict mode`);
}

for (const file of [
  "docs/31-ddd-operational-runbook.md",
  "docs/34-ddd-release-evidence-checklist.md",
]) {
  const text = readFileSync(join(repoRoot, file), "utf8");
  for (const checkId of requiredOrchestratorPreflightCheckIds) {
      assert.ok(text.includes(`\`${checkId}\``), `${file} must document preflight check ${checkId}`);
  }
  assert.ok(text.includes("`DDD_RELEASE_CHECK_ENV_ONLY=1`"), `${file} must document release execution env-check-only mode`);
  assert.ok(text.includes("`DDD_RELEASE_ALLOW_MISSING_ENV=1`"), `${file} must document the explicit missing-env diagnostic override`);
  assert.ok(text.includes("LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL"), `${file} must document alias-group env checks`);
  assert.ok(text.includes("release-action-batches.csv"), `${file} must document the full release action batches CSV artifact`);
  assert.ok(text.includes("release-fast-track"), `${file} must document the fast-track release decision artifact`);
  assert.ok(text.includes("release-unblock-brief"), `${file} must document the release unblock brief artifact`);
  assert.ok(text.includes("Performance Baseline"), `${file} must document release unblock brief performance baseline section`);
  assert.ok(text.includes("Next Action Queue"), `${file} must document release unblock brief next action queue section`);
  assert.ok(text.includes("前 5 个 `RUN_NOW`"), `${file} must document the release unblock brief RUN_NOW action limit`);
  assert.ok(text.includes("release-cutover-checklist.csv"), `${file} must document the cutover checklist CSV artifact`);
  assert.ok(text.includes("release-cutover-owner-matrix"), `${file} must document the cutover owner matrix artifact`);
  assert.ok(text.includes("release-sprint-board"), `${file} must document the release sprint board artifact`);
  assert.ok(text.includes("release-command-catalog"), `${file} must document the release command catalog artifact`);
  assert.ok(text.includes("release-owner-handoff"), `${file} must document the release owner handoff artifact`);
  assert.ok(text.includes("release-owner-receipts"), `${file} must document the release owner receipts artifact`);
  assert.ok(text.includes("release-next-action-queue"), `${file} must document the release next action queue artifact`);
  assert.ok(text.includes("release-next-action-commands"), `${file} must document the release next action commands script`);
  assert.ok(text.includes("release-next-action-run-report"), `${file} must document the release next action run report artifact`);
  assert.ok(text.includes("ddd-release-next-action-run-report-contract.mjs"), `${file} must document the release next action run report contract`);
  assert.ok(text.includes("ddd-release-next-action-run-report-summary.mjs"), `${file} must document the release next action run report summary`);
  assert.ok(text.includes("release-execution-run-report"), `${file} must document the release execution run report artifact`);
  assert.ok(text.includes("ddd-release-execution-run-report-contract.mjs"), `${file} must document the release execution run report contract`);
  assert.ok(text.includes("ddd-release-execution-run-report-summary.mjs"), `${file} must document the release execution run report summary`);
  assert.ok(text.includes("release-env-owner-matrix"), `${file} must document the release env owner matrix artifact`);
  assert.ok(text.includes("release-final-go-no-go"), `${file} must document the final go/no-go release packet`);
  assert.ok(text.includes("release-final-go-no-go-gate"), `${file} must document the final go/no-go hard gate`);
  assert.ok(text.includes("release-final-owner-queue"), `${file} must document the final owner queue artifact`);
  assert.ok(text.includes("release-final-owner-queue-commands"), `${file} must document the final owner queue command script`);
  assert.ok(text.includes("release-final-owner-queue-run-report"), `${file} must document the final owner queue execution report`);
  assert.ok(text.includes("ddd-final-owner-queue-run-report-contract.mjs"), `${file} must document the final owner queue run report contract`);
  assert.ok(text.includes("release-final-owner-queue-env.template.env"), `${file} must document the final owner queue env template`);
  assert.ok(text.includes("release-final-owner-queue-env-init.sh"), `${file} must document the final owner queue env initializer`);
  assert.ok(text.includes("noAutoWaivers"), `${file} must document that fast-track does not waive safety gates`);
  assert.ok(text.includes("cutoverChecklist"), `${file} must document the machine-readable cutover checklist`);
}

console.log("[ddd-release-orchestrator-sync.test] ok");
