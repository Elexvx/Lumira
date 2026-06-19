#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  requiredOrchestratorPreflightCheckIds,
  requiredOrchestratorStepIds,
  validateOrchestratorContract,
} from "./ddd-release-orchestrator-contract.mjs";

function baseStep(id) {
  return {
    id,
    label: id,
    command: `run ${id}`,
    envKeys: ["DDD_RELEASE_EVIDENCE_STRICT"],
  };
}

function validRunReport() {
  const selectedSteps = requiredOrchestratorStepIds.map((id) => baseStep(id));
  selectedSteps.find((step) => step.id === "outbox-replay-dead-letter").envKeys.push("DDD_OUTBOX_SMOKE_STRICT");
  selectedSteps.find((step) => step.id === "manifest-provenance-preflight").envKeys.push("DDD_RELEASE_MANIFEST_CHECK_ENV");
  selectedSteps.find((step) => step.id === "physical-split").envKeys.push("DDD_SPLIT_STRICT");
  const baselineStep = selectedSteps.find((step) => step.id === "authenticated-performance-baseline");
  baselineStep.optional = true;
  baselineStep.enabled = false;
  baselineStep.envKeys.push(
    "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY",
    "DDD_AUTH_PERF_BASELINE_ENVIRONMENT",
    "DDD_AUTH_PERF_BASELINE_PROMOTE",
    "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT",
  );
  const results = requiredOrchestratorStepIds.map((id) => ({
    id,
    status: 0,
    ...(id === "authenticated-performance-baseline" ? { skipped: true } : {}),
  }));
  return {
    mode: "run",
    strict: true,
    preflight: {
      status: "PASS",
      blockers: 0,
      warnings: 0,
      checks: requiredOrchestratorPreflightCheckIds.map((id) => ({
        id,
        status: "PASS",
        detail: `${id} ok`,
        envKeys: [`${id.toUpperCase().replaceAll("-", "_")}_ENV`],
      })),
    },
    selectedSteps,
    results,
  };
}

assert.deepEqual(validateOrchestratorContract(validRunReport(), { strict: true }), []);

assert.deepEqual(validateOrchestratorContract({
  ...validRunReport(),
  mode: "plan",
  results: [],
}, { strict: true }), [
  "strict release requires run mode report, got plan",
]);

{
  const report = validRunReport();
  report.selectedSteps = report.selectedSteps.filter((step) => step.id !== "outbox-replay-dead-letter");
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "missing orchestrator step outbox-replay-dead-letter",
    "expected 26 selected steps, got 25",
    "unexpected executed result outbox-replay-dead-letter",
  ]);
}

{
  const report = validRunReport();
  const first = report.selectedSteps[0];
  report.selectedSteps[0] = report.selectedSteps[1];
  report.selectedSteps[1] = first;
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "orchestrator step 1 must be release-env-file-lint, got release-config-evidence",
    "orchestrator step 2 must be release-config-evidence, got release-env-file-lint",
    "executed result 1 must be release-config-evidence, got release-env-file-lint",
    "executed result 2 must be release-env-file-lint, got release-config-evidence",
  ]);
}

{
  const report = validRunReport();
  report.selectedSteps.find((step) => step.id === "authenticated-performance-baseline").envKeys = ["DDD_RELEASE_EVIDENCE_STRICT"];
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "authenticated performance baseline step missing DDD_AUTH_PERF_BASELINE_PROMOTE env",
    "authenticated performance baseline step missing DDD_AUTH_PERF_BASELINE_ENVIRONMENT env",
    "authenticated performance baseline step missing DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT env",
    "authenticated performance baseline step missing DDD_AUTH_PERF_BASELINE_ACCEPTED_BY env",
  ]);
}

{
  const report = validRunReport();
  report.selectedSteps.find((step) => step.id === "outbox-replay-dead-letter").envKeys = ["DDD_RELEASE_EVIDENCE_STRICT"];
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "outbox step missing DDD_OUTBOX_SMOKE_STRICT env",
  ]);
}

{
  const report = validRunReport();
  report.selectedSteps.find((step) => step.id === "manifest-provenance-preflight").envKeys = ["DDD_RELEASE_EVIDENCE_STRICT"];
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "manifest provenance preflight step missing DDD_RELEASE_MANIFEST_CHECK_ENV env",
  ]);
}

{
  const report = validRunReport();
  report.selectedSteps.find((step) => step.id === "physical-split").envKeys = ["DDD_RELEASE_EVIDENCE_STRICT"];
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "physical split step missing DDD_SPLIT_STRICT env",
  ]);
}

{
  const report = validRunReport();
  report.results = report.results.filter((result) => result.id !== "payment-webhook");
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "missing executed result for payment-webhook",
  ]);
}

{
  const report = validRunReport();
  report.results = report.results.filter((result) => result.id !== "release-gate");
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "missing executed result for release-gate",
  ]);
}

{
  const report = validRunReport();
  delete report.results.find((result) => result.id === "authenticated-performance-baseline").skipped;
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "optional disabled step authenticated-performance-baseline must have skipped=true result",
  ]);
}

{
  const report = validRunReport();
  report.selectedSteps.find((step) => step.id === "authenticated-performance-baseline").enabled = true;
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "optional enabled step authenticated-performance-baseline must not be skipped",
  ]);
}

{
  const report = validRunReport();
  delete report.preflight;
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "orchestrator report missing preflight checks",
  ]);
}

{
  const report = validRunReport();
  report.preflight.checks = report.preflight.checks.filter((check) => check.id !== "docker-daemon");
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "missing orchestrator preflight check docker-daemon",
  ]);
}

{
  const report = validRunReport();
  report.preflight = {
    status: "FAIL",
    blockers: 1,
    warnings: 0,
    checks: [
      ...requiredOrchestratorPreflightCheckIds
        .filter((id) => id !== "backend-runtime-base-url")
        .map((id) => ({
          id,
          status: "PASS",
          detail: `${id} ok`,
          envKeys: [`${id.toUpperCase().replaceAll("-", "_")}_ENV`],
        })),
      {
        id: "backend-runtime-base-url",
        status: "BLOCKER",
        detail: "missing backend runtime base URL",
        envKeys: ["LUMIRA_BASE_URL"],
      },
    ],
  };
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "orchestrator preflight has blockers=1",
  ]);
}

{
  const report = validRunReport();
  report.preflight = {
    status: "FAIL",
    blockers: 0,
    warnings: 0,
    checks: [
      ...requiredOrchestratorPreflightCheckIds
        .filter((id) => id !== "docker-daemon" && id !== "release-provenance")
        .map((id) => ({
          id,
          status: "PASS",
          detail: `${id} ok`,
          envKeys: [`${id.toUpperCase().replaceAll("-", "_")}_ENV`],
        })),
      {
        id: "docker-daemon",
        status: "BLOCKER",
        detail: "Docker daemon is not available",
        envKeys: ["DDD_DOCKER_COMMAND"],
      },
      {
        id: "release-provenance",
        status: "WARNING",
        detail: "missing optional provenance in advisory mode",
        envKeys: ["DDD_EVIDENCE_ENVIRONMENT"],
      },
    ],
  };
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "orchestrator preflight blockers count mismatch: declared=0, actual=1",
    "orchestrator preflight warnings count mismatch: declared=0, actual=1",
  ]);
}

{
  const report = validRunReport();
  report.preflight = {
    status: "PASS",
    blockers: 1,
    warnings: 0,
    checks: [
      ...requiredOrchestratorPreflightCheckIds
        .filter((id) => id !== "docker-daemon")
        .map((id) => ({
          id,
          status: "PASS",
          detail: `${id} ok`,
          envKeys: [`${id.toUpperCase().replaceAll("-", "_")}_ENV`],
        })),
      {
        id: "docker-daemon",
        status: "BLOCKER",
        detail: "Docker daemon is not available",
        envKeys: ["DDD_DOCKER_COMMAND"],
      },
    ],
  };
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "orchestrator preflight status must be FAIL, got PASS",
    "orchestrator preflight has blockers=1",
  ]);
}

{
  const report = validRunReport();
  report.results[2] = { id: "unknown-step", status: 0 };
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "missing executed result for backend-tests",
    "unexpected executed result unknown-step",
    "executed result 3 must be backend-tests, got unknown-step",
  ]);
}

{
  const report = validRunReport();
  const first = report.results[0];
  report.results[0] = report.results[1];
  report.results[1] = first;
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }), [
    "executed result 1 must be release-env-file-lint, got release-config-evidence",
    "executed result 2 must be release-config-evidence, got release-env-file-lint",
  ]);
}

{
  const report = validRunReport();
  report.preflight.checks.push({ ...report.preflight.checks[0] });
  assert(validateOrchestratorContract(report, { strict: true })
    .includes(`duplicate orchestrator preflight check ${requiredOrchestratorPreflightCheckIds[0]}`));
}

{
  const report = validRunReport();
  report.preflight.checks.push({
    id: "unexpected-check",
    status: "PASS",
    detail: "unexpected",
    envKeys: ["UNEXPECTED_ENV"],
  });
  assert(validateOrchestratorContract(report, { strict: true })
    .includes("unexpected orchestrator preflight check unexpected-check"));
}

{
  const report = validRunReport();
  const check = report.preflight.checks.find((entry) => entry.id === "release-provenance");
  check.status = "SKIPPED";
  check.detail = "";
  check.envKeys = [];
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }).filter((issue) => issue.includes("release-provenance")), [
    "orchestrator preflight check release-provenance has invalid status SKIPPED",
    "orchestrator preflight check release-provenance detail is required",
    "orchestrator preflight check release-provenance envKeys must be a non-empty array",
  ]);
}

{
  const report = validRunReport();
  report.selectedSteps.push({ ...report.selectedSteps[0] });
  assert(validateOrchestratorContract(report, { strict: true })
    .includes("duplicate orchestrator selected step release-env-file-lint"));
}

{
  const report = validRunReport();
  report.selectedSteps[0] = {
    id: "unknown-step",
    label: "unknown",
    command: "run unknown",
    envKeys: ["DDD_RELEASE_EVIDENCE_STRICT"],
  };
  const issues = validateOrchestratorContract(report, { strict: true });
  assert(issues.includes("unexpected orchestrator selected step unknown-step"));
  assert(issues.includes("missing orchestrator step release-env-file-lint"));
}

{
  const report = validRunReport();
  report.selectedSteps.find((step) => step.id === "release-config-evidence").envKeys = [];
  assert(validateOrchestratorContract(report, { strict: true })
    .includes("orchestrator step release-config-evidence envKeys must be a non-empty array"));
}

{
  const report = validRunReport();
  report.results.push({ ...report.results[0] });
  assert(validateOrchestratorContract(report, { strict: true })
    .includes("duplicate orchestrator executed result release-env-file-lint"));
}

{
  const report = validRunReport();
  const result = report.results.find((entry) => entry.id === "release-config-evidence");
  result.status = "0";
  result.skipped = "false";
  assert.deepEqual(validateOrchestratorContract(report, { strict: true }).filter((issue) => issue.includes("release-config-evidence")), [
    "orchestrator executed result release-config-evidence status must be a number",
    "orchestrator executed result release-config-evidence skipped must be boolean",
  ]);
}

console.log("[ddd-release-orchestrator-contract.test] ok");
