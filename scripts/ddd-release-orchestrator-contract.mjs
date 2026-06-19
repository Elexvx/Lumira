export const requiredOrchestratorStepIds = [
  "release-env-file-lint",
  "release-config-evidence",
  "backend-tests",
  "backend-test-evidence",
  "backend-build-evidence",
  "docker-build-evidence",
  "frontend-static-evidence",
  "frontend-build-evidence",
  "migration-evidence",
  "runtime-readiness",
  "authenticated-performance",
  "authenticated-performance-baseline",
  "file-processing",
  "payment-webhook",
  "outbox-replay-dead-letter",
  "job-e2e",
  "ai-runtime",
  "frontend-playwright-smoke",
  "frontend-smoke",
  "rollback-drill",
  "explain-gate",
  "physical-split",
  "manifest-provenance-preflight",
  "manifest",
  "release-gate",
  "readiness-summary",
];

export const requiredOrchestratorPreflightCheckIds = [
  "release-config-env-file",
  "release-provenance",
  "backend-runtime-base-url",
  "ai-runtime-base-url",
  "frontend-runtime-base-url",
  "frontend-deployed-expectation",
  "docker-cli",
  "docker-daemon",
  "ai-provider-remote-expectation",
  "ai-owner-gateway-remote-expectation",
  "migration-runtime-evidence",
];

const allowedPreflightStatuses = new Set(["PASS", "WARNING", "BLOCKER"]);

export function validateOrchestratorContract(report, { strict = false } = {}) {
  const issues = [];
  const selectedSteps = Array.isArray(report?.selectedSteps) ? report.selectedSteps : [];
  const selectedStepIds = selectedSteps.map((step) => step.id);

  if (strict && report?.mode !== "run") {
    issues.push(`strict release requires run mode report, got ${report?.mode}`);
  }
  if (strict && report?.strict !== true) {
    issues.push("strict release requires orchestrator strict=true");
  }
  if (!report?.preflight || typeof report.preflight !== "object") {
    issues.push("orchestrator report missing preflight checks");
  } else {
    if (!Array.isArray(report.preflight.checks)) {
      issues.push("orchestrator preflight checks must be an array");
    } else {
      const preflightCheckIds = report.preflight.checks.map((check) => check?.id);
      const preflightCheckIdSet = new Set(preflightCheckIds);
      const requiredPreflightCheckIdSet = new Set(requiredOrchestratorPreflightCheckIds);
      const seenPreflightCheckIds = new Set();
      for (const check of report.preflight.checks) {
        const checkId = check?.id;
        if (!checkId) {
          issues.push("orchestrator preflight check id is required");
          continue;
        }
        if (seenPreflightCheckIds.has(checkId)) {
          issues.push(`duplicate orchestrator preflight check ${checkId}`);
        }
        seenPreflightCheckIds.add(checkId);
        if (!requiredPreflightCheckIdSet.has(checkId)) {
          issues.push(`unexpected orchestrator preflight check ${checkId}`);
        }
        if (!allowedPreflightStatuses.has(check?.status)) {
          issues.push(`orchestrator preflight check ${checkId} has invalid status ${check?.status ?? "missing"}`);
        }
        if (!check?.detail) {
          issues.push(`orchestrator preflight check ${checkId} detail is required`);
        }
        if (!Array.isArray(check?.envKeys) || check.envKeys.length === 0) {
          issues.push(`orchestrator preflight check ${checkId} envKeys must be a non-empty array`);
        }
      }
      for (const checkId of requiredOrchestratorPreflightCheckIds) {
        if (!preflightCheckIdSet.has(checkId)) {
          issues.push(`missing orchestrator preflight check ${checkId}`);
        }
      }
      const actualBlockers = report.preflight.checks.filter((check) => check?.status === "BLOCKER").length;
      const actualWarnings = report.preflight.checks.filter((check) => check?.status === "WARNING").length;
      const declaredBlockers = Number(report.preflight.blockers || 0);
      const declaredWarnings = Number(report.preflight.warnings || 0);
      const expectedStatus = actualBlockers > 0 ? "FAIL" : "PASS";
      if (declaredBlockers !== actualBlockers) {
        issues.push(`orchestrator preflight blockers count mismatch: declared=${declaredBlockers}, actual=${actualBlockers}`);
      }
      if (declaredWarnings !== actualWarnings) {
        issues.push(`orchestrator preflight warnings count mismatch: declared=${declaredWarnings}, actual=${actualWarnings}`);
      }
      if (report.preflight.status !== expectedStatus) {
        issues.push(`orchestrator preflight status must be ${expectedStatus}, got ${report.preflight.status}`);
      }
    }
    if (strict && report?.mode === "run" && Number(report.preflight.blockers || 0) > 0) {
      issues.push(`orchestrator preflight has blockers=${report.preflight.blockers}`);
    }
  }

  const requiredStepIdSet = new Set(requiredOrchestratorStepIds);
  const seenSelectedStepIds = new Set();
  for (const step of selectedSteps) {
    const stepId = step?.id;
    if (!stepId) {
      issues.push("orchestrator selected step id is required");
      continue;
    }
    if (seenSelectedStepIds.has(stepId)) {
      issues.push(`duplicate orchestrator selected step ${stepId}`);
    }
    seenSelectedStepIds.add(stepId);
    if (!requiredStepIdSet.has(stepId)) {
      issues.push(`unexpected orchestrator selected step ${stepId}`);
    }
    if (!step?.label) {
      issues.push(`orchestrator step ${stepId} label is required`);
    }
    if (!step?.command) {
      issues.push(`orchestrator step ${stepId} command is required`);
    }
    if (!Array.isArray(step?.envKeys) || step.envKeys.length === 0) {
      issues.push(`orchestrator step ${stepId} envKeys must be a non-empty array`);
    }
  }

  for (const stepId of requiredOrchestratorStepIds) {
    if (!selectedStepIds.includes(stepId)) {
      issues.push(`missing orchestrator step ${stepId}`);
    }
  }
  const expectedStepCount = requiredOrchestratorStepIds.length;
  if (selectedSteps.length !== expectedStepCount) {
    issues.push(`expected ${requiredOrchestratorStepIds.length} selected steps, got ${selectedSteps.length}`);
  }
  if (selectedSteps.length === expectedStepCount) {
    for (let index = 0; index < expectedStepCount; index += 1) {
      const expected = requiredOrchestratorStepIds[index];
      const actual = selectedStepIds[index];
      if (actual && actual !== expected) {
        issues.push(`orchestrator step ${index + 1} must be ${expected}, got ${actual}`);
      }
    }
  }

  const outboxStep = selectedSteps.find((step) => step.id === "outbox-replay-dead-letter");
  if (outboxStep) {
    const envKeys = new Set(Array.isArray(outboxStep.envKeys) ? outboxStep.envKeys : []);
    if (!envKeys.has("DDD_RELEASE_EVIDENCE_STRICT")) {
      issues.push("outbox step missing DDD_RELEASE_EVIDENCE_STRICT env");
    }
    if (!envKeys.has("DDD_OUTBOX_SMOKE_STRICT")) {
      issues.push("outbox step missing DDD_OUTBOX_SMOKE_STRICT env");
    }
  }

  const baselineStep = selectedSteps.find((step) => step.id === "authenticated-performance-baseline");
  if (baselineStep) {
    const envKeys = new Set(Array.isArray(baselineStep.envKeys) ? baselineStep.envKeys : []);
    for (const envKey of [
      "DDD_AUTH_PERF_BASELINE_PROMOTE",
      "DDD_AUTH_PERF_BASELINE_ENVIRONMENT",
      "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT",
      "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY",
    ]) {
      if (!envKeys.has(envKey)) {
        issues.push(`authenticated performance baseline step missing ${envKey} env`);
      }
    }
  }

  const manifestPreflightStep = selectedSteps.find((step) => step.id === "manifest-provenance-preflight");
  if (manifestPreflightStep) {
    const envKeys = new Set(Array.isArray(manifestPreflightStep.envKeys) ? manifestPreflightStep.envKeys : []);
    if (!envKeys.has("DDD_RELEASE_EVIDENCE_STRICT")) {
      issues.push("manifest provenance preflight step missing DDD_RELEASE_EVIDENCE_STRICT env");
    }
    if (!envKeys.has("DDD_RELEASE_MANIFEST_CHECK_ENV")) {
      issues.push("manifest provenance preflight step missing DDD_RELEASE_MANIFEST_CHECK_ENV env");
    }
  }

  const physicalSplitStep = selectedSteps.find((step) => step.id === "physical-split");
  if (physicalSplitStep) {
    const envKeys = new Set(Array.isArray(physicalSplitStep.envKeys) ? physicalSplitStep.envKeys : []);
    if (!envKeys.has("DDD_RELEASE_EVIDENCE_STRICT")) {
      issues.push("physical split step missing DDD_RELEASE_EVIDENCE_STRICT env");
    }
    if (!envKeys.has("DDD_SPLIT_STRICT")) {
      issues.push("physical split step missing DDD_SPLIT_STRICT env");
    }
  }

  const results = Array.isArray(report?.results) ? report.results : [];
  if (strict && report?.mode === "run" && results.length === 0) {
    issues.push("run mode report has no executed step results");
  }
  if (strict && report?.mode === "run") {
    const resultIds = results.map((result) => result.id);
    const resultIdSet = new Set(resultIds);
    const seenResultIds = new Set();
    for (const result of results) {
      const resultId = result?.id;
      if (!resultId) {
        issues.push("orchestrator executed result id is required");
        continue;
      }
      if (seenResultIds.has(resultId)) {
        issues.push(`duplicate orchestrator executed result ${resultId}`);
      }
      seenResultIds.add(resultId);
      if (result.status !== undefined && typeof result.status !== "number") {
        issues.push(`orchestrator executed result ${resultId} status must be a number`);
      }
      if (result.skipped !== undefined && typeof result.skipped !== "boolean") {
        issues.push(`orchestrator executed result ${resultId} skipped must be boolean`);
      }
    }
    for (const stepId of requiredOrchestratorStepIds) {
      if (!resultIdSet.has(stepId)) {
        issues.push(`missing executed result for ${stepId}`);
      }
    }
    for (const resultId of resultIds) {
      if (!selectedStepIds.includes(resultId)) {
        issues.push(`unexpected executed result ${resultId}`);
      }
    }
    if (resultIds.length === selectedStepIds.length) {
      for (let index = 0; index < selectedStepIds.length; index += 1) {
        const expected = selectedStepIds[index];
        const actual = resultIds[index];
        if (actual && actual !== expected) {
          issues.push(`executed result ${index + 1} must be ${expected}, got ${actual}`);
        }
      }
    }
    for (const step of selectedSteps) {
      if (step.optional !== true) {
        continue;
      }
      const result = results.find((entry) => entry.id === step.id);
      if (!result) {
        continue;
      }
      if (step.enabled === false && result.skipped !== true) {
        issues.push(`optional disabled step ${step.id} must have skipped=true result`);
      }
      if (step.enabled !== false && result.skipped === true) {
        issues.push(`optional enabled step ${step.id} must not be skipped`);
      }
    }
  }

  return issues;
}
