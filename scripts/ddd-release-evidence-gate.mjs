#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { validateAiRuntimeArtifact } from "./ddd-ai-runtime-contract.mjs";
import {
  buildProductionEquivalenceEvidence,
  evidenceValueIssue,
  redactLocalPaths,
} from "./ddd-release-evidence-utils.mjs";
import { validateOrchestratorContract } from "./ddd-release-orchestrator-contract.mjs";
import { validateRollbackDrillContract } from "./ddd-rollback-drill-contract.mjs";
import { validateMigrationEvidenceContract } from "./ddd-migration-evidence-contract.mjs";
import { validatePhysicalSplitContract } from "./ddd-physical-split-contract.mjs";
import {
  compareAuthenticatedPerformance,
  validateAuthenticatedPerformanceBaselineMetadata,
  validateAuthenticatedPerformanceShape,
} from "./ddd-performance-evidence-contract.mjs";
import { validateFrontendSmokeArtifact } from "./ddd-frontend-smoke-contract.mjs";
import { validateReleaseConfigArtifact } from "./ddd-release-config-contract.mjs";
import {
  validateBackendBuildArtifact,
  validateBackendTestArtifact,
} from "./ddd-backend-evidence-contract.mjs";
import {
  missingRequiredExplainFiles,
  validateExplainArtifact,
} from "./ddd-explain-evidence-contract.mjs";
import { validateManifestArtifact } from "./ddd-release-evidence-manifest-contract.mjs";
import { validateDockerBuildArtifact } from "./ddd-docker-evidence-contract.mjs";
import {
  validateFrontendBuildArtifact,
  validateFrontendStaticArtifact,
} from "./ddd-frontend-evidence-contract.mjs";
import { validateRuntimeReadinessArtifact } from "./ddd-runtime-readiness-contract.mjs";
import { validateReleaseGateArtifact } from "./ddd-release-gate-contract.mjs";
import {
  validateFileProcessingArtifact,
  validatePaymentWebhookArtifact,
} from "./ddd-business-e2e-evidence-contract.mjs";
import {
  validateJobE2eArtifact,
  validateOutboxReplayArtifact,
} from "./ddd-outbox-job-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const strict = process.env.DDD_RELEASE_EVIDENCE_STRICT === "true";
const artifactRoot = process.env.DDD_RELEASE_EVIDENCE_DIR
  ? path.resolve(process.env.DDD_RELEASE_EVIDENCE_DIR)
  : path.join(repoRoot, "artifacts", "ddd");
const outputFile = process.env.DDD_RELEASE_EVIDENCE_REPORT
  ? path.resolve(process.env.DDD_RELEASE_EVIDENCE_REPORT)
  : path.join(artifactRoot, "release", "release-evidence-gate.json");
const maxP95RegressionRatio = Number(process.env.DDD_RELEASE_MAX_P95_REGRESSION_RATIO || "0.10");
const maxArtifactAgeHours = Number(process.env.DDD_RELEASE_MAX_ARTIFACT_AGE_HOURS || "24");
const checks = [];
const blockers = [];
const warnings = [];
const blockerDetails = [];
const warningDetails = [];

function portableValue(value) {
  if (typeof value === "string") {
    return redactLocalPaths(value.split(`${repoRoot}${path.sep}`).join(""), {
      repoRoot,
      homeDir: os.homedir(),
    });
  }
  if (Array.isArray(value)) {
    return value.map((item) => portableValue(item));
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, portableValue(item)]));
  }
  return value;
}

function artifactPath(relativePath) {
  return path.join(artifactRoot, relativePath);
}

function readJson(relativePath) {
  const file = artifactPath(relativePath);
  if (!fs.existsSync(file)) {
    return { file, missing: true, data: null };
  }
  try {
    return { file, missing: false, data: JSON.parse(fs.readFileSync(file, "utf8")) };
  } catch (error) {
    return { file, missing: false, invalid: error.message, data: null };
  }
}

function isLocalBaseUrl(value) {
  return typeof value === "string"
    && /^(http:\/\/)?(127\.0\.0\.1|localhost|\[::1\])(?::\d+)?/i.test(value.replace(/^https?:\/\//i, ""));
}

function record(name, status, detail, file = null) {
  checks.push({ name, status, detail, file });
  if (status === "blocker") {
    blockers.push(`${name}: ${detail}`);
    blockerDetails.push({
      check: name,
      detail,
      file,
    });
  } else if (status === "warning") {
    warnings.push(`${name}: ${detail}`);
    warningDetails.push({
      check: name,
      detail,
      file,
    });
  }
}

function recordLocalOnlyArtifact(name, baseUrl) {
  record(name, "warning", `artifact is local-only: ${baseUrl}`);
  if (strict) {
    record(`${name}-strict`, "blocker", "strict release requires production-equivalent non-local evidence");
  }
}

function validateProductionEquivalence(name, artifact, evidenceName) {
  if (!strict || !artifact) {
    return;
  }
  const productionEquivalence = artifact.productionEquivalence || (artifact.baseUrl
    ? buildProductionEquivalenceEvidence({
      strict,
      baseUrl: artifact.baseUrl,
      deploymentEvidence: "",
      evidenceName,
    })
    : null);
  for (const issue of productionEquivalence?.issues || []) {
    if (issue.includes("requires non-local baseUrl") && isLocalBaseUrl(artifact.baseUrl)) {
      continue;
    }
    record(`${name}-production-equivalence`, "blocker", issue);
  }
  const deploymentEvidenceIssue = evidenceValueIssue(productionEquivalence?.deploymentEvidence);
  if (deploymentEvidenceIssue) {
    const detail = `strict ${evidenceName} deploymentEvidence ${deploymentEvidenceIssue}`;
    if (!productionEquivalence?.issues?.includes(detail)) {
      record(`${name}-production-equivalence`, "blocker", detail);
    }
  }
}

function validateRuntimeProvenance(name, artifact) {
  if (!strict || !artifact) {
    return;
  }
  validateEvidenceText(`${name}-provenance`, artifact.sourceEnvironment, "sourceEnvironment");
  validateEvidenceText(`${name}-provenance`, artifact.releaseCandidate, "releaseCandidate");
  validateEvidenceText(`${name}-provenance`, artifact.evidenceOperator, "evidenceOperator");
}

function validateEvidenceText(name, value, label) {
  const issue = evidenceValueIssue(value);
  if (issue) {
    record(name, "blocker", `${label} ${issue}`);
  }
}

function artifactTimestamp(artifact) {
  if (!artifact || typeof artifact !== "object") {
    return null;
  }
  let latest = null;
  for (const field of ["generatedAt", "checkedAt", "finishedAt", "startedAt", "failedAt"]) {
    if (artifact[field]) {
      const time = Date.parse(artifact[field]);
      if (Number.isFinite(time)) {
        if (!latest || time > latest.time) {
          latest = { field, time, value: artifact[field] };
        }
      }
    }
  }
  return latest;
}

function validateFreshArtifact(name, artifact) {
  if (!strict || !artifact) {
    return;
  }
  const timestamp = artifactTimestamp(artifact);
  if (!timestamp) {
    record(`${name}-freshness`, "blocker", "strict release evidence requires generatedAt, checkedAt, finishedAt or startedAt");
    return;
  }
  const ageHours = (Date.now() - timestamp.time) / 36e5;
  if (ageHours < -0.25) {
    record(`${name}-freshness`, "blocker", `${timestamp.field} is in the future: ${timestamp.value}`);
    return;
  }
  if (Number.isFinite(maxArtifactAgeHours) && maxArtifactAgeHours > 0 && ageHours > maxArtifactAgeHours) {
    record(
      `${name}-freshness`,
      "blocker",
      `${timestamp.field} is ${Math.round(ageHours * 10) / 10}h old; limit=${maxArtifactAgeHours}h`,
    );
  }
}

function validateExplainJsonArtifact(fileName, fullPath) {
  let parsed;
  try {
    parsed = JSON.parse(fs.readFileSync(fullPath, "utf8"));
  } catch (error) {
    record("explain-evidence", "blocker", `${fileName} invalid JSON: ${error.message}`, fullPath);
    return;
  }
  for (const issue of validateExplainArtifact(fileName, parsed, { strict })) {
    const checkName = issue.scope === "metadata" ? "explain-evidence-metadata" : "explain-evidence";
    record(checkName, "blocker", issue.detail, fullPath);
  }
}

function requireJson(relativePath, name) {
  const result = readJson(relativePath);
  if (result.missing) {
    record(name, "blocker", `missing artifact ${relativePath}`, result.file);
    return null;
  }
  if (result.invalid) {
    record(name, "blocker", `invalid JSON: ${result.invalid}`, result.file);
    return null;
  }
  record(name, "present", relativePath, result.file);
  return result.data;
}

function validateRuntimeReadiness() {
  const summary = requireJson("readiness/summary.json", "runtime-readiness-summary");
  if (!summary) {
    return;
  }
  for (const issue of validateRuntimeReadinessArtifact(summary, { strict })) {
    record("runtime-readiness-summary", "blocker", issue);
  }
  validateFreshArtifact("runtime-readiness", summary);
  validateProductionEquivalence("runtime-readiness", summary, "runtime readiness");
  if (isLocalBaseUrl(summary.baseUrl)) {
    recordLocalOnlyArtifact("runtime-readiness-environment", summary.baseUrl);
  }
  validateRuntimeProvenance("runtime-readiness", summary);
}

function validateAuthenticatedPerformance() {
  const perf = requireJson("performance/authenticated-runtime-actual.json", "authenticated-performance");
  if (!perf) {
    return;
  }
  if (perf.failed !== 0) {
    record("authenticated-performance", "blocker", `failed=${perf.failed}`);
  }
  if (!Number.isFinite(perf.p95) || perf.p95 <= 0) {
    record("authenticated-performance", "blocker", "missing p95");
  }
  if (!perf.upload || perf.upload.status !== 200) {
    record("authenticated-performance", "blocker", "file upload one-shot did not return 200");
  }
  for (const issue of validateAuthenticatedPerformanceShape("authenticated performance actual", perf, { strict })) {
    record("authenticated-performance-shape", "blocker", issue);
  }
  validateFreshArtifact("authenticated-performance", perf);
  validateProductionEquivalence("authenticated-performance", perf, "authenticated performance actual");
  if (isLocalBaseUrl(perf.baseUrl)) {
    recordLocalOnlyArtifact("authenticated-performance-environment", perf.baseUrl);
  }
  validateRuntimeProvenance("authenticated-performance", perf);

  const baselineResult = readJson("performance/authenticated-runtime-baseline.json");
  if (baselineResult.missing) {
    record(
      "authenticated-performance-baseline",
      "warning",
      "missing authenticated performance baseline artifact; copy the last accepted production-equivalent actual to performance/authenticated-runtime-baseline.json",
      baselineResult.file,
    );
    if (strict) {
      record("authenticated-performance-baseline-strict", "blocker", "strict release requires authenticated performance baseline comparison", baselineResult.file);
    }
    return;
  }
  if (baselineResult.invalid) {
    record("authenticated-performance-baseline", "blocker", `invalid JSON: ${baselineResult.invalid}`, baselineResult.file);
    if (strict) {
      record("authenticated-performance-baseline-strict", "blocker", "strict release requires authenticated performance baseline comparison", baselineResult.file);
    }
    return;
  }
  record("authenticated-performance-baseline", "present", "performance/authenticated-runtime-baseline.json", baselineResult.file);
  const baseline = baselineResult.data;
  for (const issue of validateAuthenticatedPerformanceShape("authenticated performance baseline", baseline, { strict: false })) {
    record("authenticated-performance-baseline-shape", "blocker", issue);
  }
  if (strict && isLocalBaseUrl(baseline.baseUrl)) {
    record("authenticated-performance-baseline-environment", "blocker", `strict release requires a non-local baseline baseUrl, got ${baseline.baseUrl}`);
  }
  for (const issue of validateAuthenticatedPerformanceBaselineMetadata(baseline, { strict })) {
    record("authenticated-performance-baseline-metadata", "blocker", issue);
  }
  if (strict && baseline?.status !== "PASS") {
    record("authenticated-performance-baseline-strict", "blocker", "strict release requires authenticated performance baseline comparison", baselineResult.file);
  }
  for (const issue of compareAuthenticatedPerformance(perf, baseline, { maxRegressionRatio: maxP95RegressionRatio })) {
    record(issue.name, "blocker", issue.detail);
  }
}

function validateFileProcessing() {
  const artifact = requireJson("file/file-processing-e2e.json", "file-processing-e2e");
  if (!artifact) {
    return;
  }
  validateFreshArtifact("file-processing", artifact);
  validateProductionEquivalence("file-processing", artifact, "file processing E2E");
  for (const issue of validateFileProcessingArtifact(artifact, { strict })) {
    record("file-processing-e2e", "blocker", issue);
  }
  if (isLocalBaseUrl(artifact.baseUrl)) {
    recordLocalOnlyArtifact("file-processing-environment", artifact.baseUrl);
  }
  validateRuntimeProvenance("file-processing", artifact);
}

function validatePaymentWebhook() {
  const artifact = requireJson("payment/payment-webhook-e2e.json", "payment-webhook-e2e");
  if (!artifact) {
    return;
  }
  validateFreshArtifact("payment-webhook", artifact);
  validateProductionEquivalence("payment-webhook", artifact, "payment webhook E2E");
  for (const issue of validatePaymentWebhookArtifact(artifact, { strict })) {
    record("payment-webhook-e2e", "blocker", issue);
  }
  if (isLocalBaseUrl(artifact.baseUrl)) {
    recordLocalOnlyArtifact("payment-webhook-environment", artifact.baseUrl);
  }
  validateRuntimeProvenance("payment-webhook", artifact);
}

function validateOutbox() {
  const replay = requireJson("outbox/outbox-replay-dead-letter-test-evidence.json", "outbox-replay-dead-letter");
  if (replay) {
    validateFreshArtifact("outbox-replay-dead-letter", replay);
    for (const issue of validateOutboxReplayArtifact(replay)) {
      record("outbox-replay-dead-letter", "blocker", issue);
    }
    validateRuntimeProvenance("outbox-replay-dead-letter", replay);
  }
  const job = requireJson("jobs/job-e2e-smoke.json", "job-e2e-smoke");
  if (job) {
    validateFreshArtifact("job-e2e", job);
    validateProductionEquivalence("job-e2e", job, "job E2E");
    for (const issue of validateJobE2eArtifact(job, { strict })) {
      record("job-e2e-smoke", "blocker", issue);
    }
    if (isLocalBaseUrl(job.baseUrl)) {
      recordLocalOnlyArtifact("job-e2e-environment", job.baseUrl);
    }
    validateRuntimeProvenance("job-e2e", job);
  }
}

function validateAiRuntimeDrill() {
  const ai = readJson("ai/ai-runtime-drill.json");
  if (ai.missing) {
    record("ai-runtime-drill", "warning", "missing local AI runtime drill artifact; run scripts/ddd-ai-runtime-drill.mjs", ai.file);
    if (strict) {
      record("ai-runtime-drill-strict", "blocker", "strict release requires AI runtime drill artifact", ai.file);
    }
    return;
  }
  if (ai.invalid) {
    record("ai-runtime-drill", "blocker", `invalid JSON: ${ai.invalid}`, ai.file);
    return;
  }
  record("ai-runtime-drill", "present", "ai/ai-runtime-drill.json", ai.file);
  for (const issue of validateAiRuntimeArtifact(ai.data, { strict, validateRemoteExpectations: false })) {
    record("ai-runtime-drill", "blocker", issue);
  }
  validateFreshArtifact("ai-runtime", ai.data);
  validateProductionEquivalence("ai-runtime", ai.data, "AI runtime drill");
  if ((ai.data.failures || []).length > 0 || ai.data.summary?.failed > 0) {
    const categories = Object.entries(ai.data.summary?.failureCategories || {})
      .map(([category, count]) => `${category}=${count}`)
      .join(", ");
    const categorySuffix = categories ? `; categories=${categories}` : "";
    record("ai-runtime-drill", strict ? "blocker" : "warning", `failures=${ai.data.failures?.length ?? ai.data.summary?.failed}${categorySuffix}`);
  }
  if (isLocalBaseUrl(ai.data.baseUrl)) {
    recordLocalOnlyArtifact("ai-runtime-environment", ai.data.baseUrl);
  }
  validateRuntimeProvenance("ai-runtime", ai.data);
  if (strict && !ai.data.expectations?.providerRemote) {
    record("ai-runtime-drill-provider", "blocker", "strict release requires DDD_AI_EXPECT_PROVIDER_REMOTE=true evidence");
  }
  if (strict && !ai.data.expectations?.ownerGatewayRemote) {
    record("ai-runtime-drill-owner-gateway", "blocker", "strict release requires DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true evidence");
  }
  if (strict && ai.data.expectations?.providerRemote) {
    const provider = ai.data.remoteEvidence?.provider || {};
    if (provider.status !== "UP" || provider.remoteConfigured !== true) {
      record("ai-runtime-drill-provider", "blocker", "strict release requires ai.provider-runtime UP with remoteConfigured=true");
    }
    if (provider.localFallbackProvider === true || provider.provider === "lumira-local") {
      record("ai-runtime-drill-provider", "blocker", "strict release must not use the local fallback provider");
    }
  }
  if (strict && ai.data.expectations?.ownerGatewayRemote) {
    const ownerGateway = ai.data.remoteEvidence?.ownerGateway || {};
    if (ownerGateway.status !== "UP") {
      record("ai-runtime-drill-owner-gateway", "blocker", "strict release requires ai.remote-owner-gateway UP");
    }
    if (!Number.isFinite(ownerGateway.configuredOwnerCount) || ownerGateway.configuredOwnerCount <= 0) {
      record("ai-runtime-drill-owner-gateway", "blocker", "strict release requires at least one configured remote owner gateway");
    }
  }
}

function validatePhysicalSplit() {
  const split = requireJson("split/physical-split-readiness.json", "physical-split-readiness");
  if (!split) {
    return;
  }
  if (split.summary?.failures > 0 || split.summary?.blockers > 0) {
    record("physical-split-readiness", "blocker", `failures=${split.summary?.failures}, blockers=${split.summary?.blockers}`);
  }
  for (const issue of validatePhysicalSplitContract(split)) {
    record("physical-split-readiness", "blocker", issue);
  }
  validateFreshArtifact("physical-split-readiness", split);
  validateRuntimeProvenance("physical-split-readiness", split);
  if (strict && split.strict !== true) {
    record("physical-split-readiness", "blocker", "strict release requires physical split artifact generated with DDD_SPLIT_STRICT=true");
  } else if (!split.strict) {
    record("physical-split-readiness", "warning", "artifact was generated in advisory mode, not strict mode");
  }
}

function validateBackendTestEvidence() {
  const tests = readJson("tests/backend-test-evidence.json");
  if (tests.missing) {
    record("backend-test-evidence", "warning", "missing backend test evidence artifact; run scripts/ddd-backend-test-evidence.mjs after Maven tests", tests.file);
    if (strict) {
      record("backend-test-evidence-strict", "blocker", "strict release requires backend architecture and owner contract test evidence", tests.file);
    }
    return;
  }
  if (tests.invalid) {
    record("backend-test-evidence", "blocker", `invalid JSON: ${tests.invalid}`, tests.file);
    return;
  }
  record("backend-test-evidence", "present", "tests/backend-test-evidence.json", tests.file);
  validateFreshArtifact("backend-test-evidence", tests.data);
  validateRuntimeProvenance("backend-test-evidence", tests.data);
  for (const issue of validateBackendTestArtifact(tests.data)) {
    record("backend-test-evidence", "blocker", issue);
  }
}

function validateBackendBuildEvidence() {
  const build = readJson("build/backend-build-evidence.json");
  if (build.missing) {
    record("backend-build-evidence", "warning", "missing backend build evidence artifact; run scripts/ddd-backend-build-evidence.mjs", build.file);
    if (strict) {
      record("backend-build-evidence-strict", "blocker", "strict release requires backend package/build evidence", build.file);
    }
    return;
  }
  if (build.invalid) {
    record("backend-build-evidence", "blocker", `invalid JSON: ${build.invalid}`, build.file);
    return;
  }
  record("backend-build-evidence", "present", "build/backend-build-evidence.json", build.file);
  validateFreshArtifact("backend-build-evidence", build.data);
  validateRuntimeProvenance("backend-build-evidence", build.data);
  for (const issue of validateBackendBuildArtifact(build.data)) {
    record("backend-build-evidence", "blocker", issue);
  }
}

function validateDockerBuildEvidence() {
  const build = readJson("build/docker-image-evidence.json");
  if (build.missing) {
    record("docker-build-evidence", "warning", "missing Docker image evidence artifact; run scripts/ddd-docker-build-evidence.mjs", build.file);
    if (strict) {
      record("docker-build-evidence-strict", "blocker", "strict release requires deployable Docker image build evidence", build.file);
    }
    return;
  }
  if (build.invalid) {
    record("docker-build-evidence", "blocker", `invalid JSON: ${build.invalid}`, build.file);
    return;
  }
  record("docker-build-evidence", "present", "build/docker-image-evidence.json", build.file);
  validateFreshArtifact("docker-build-evidence", build.data);
  validateRuntimeProvenance("docker-build-evidence", build.data);
  const concreteBlockers = dockerConcreteBlockers(build.data);
  for (const blocker of concreteBlockers) {
    record("docker-build-evidence", strict ? "blocker" : "warning", blocker);
  }
  for (const issue of validateDockerBuildArtifact(build.data)) {
    if (concreteBlockers.length > 0 && isDockerAggregateIssue(issue)) {
      continue;
    }
    record("docker-build-evidence", strict ? "blocker" : "warning", issue);
  }
}

function dockerConcreteBlockers(artifact) {
  const blockers = [];
  const seen = new Set();
  const add = (blocker, prefix = null) => {
    if (typeof blocker !== "string" || blocker.trim().length === 0) {
      return;
    }
    const text = prefix ? `${prefix}: ${blocker}` : blocker;
    if (!seen.has(text)) {
      seen.add(text);
      blockers.push(text);
    }
  };
  for (const blocker of Array.isArray(artifact?.blockers) ? artifact.blockers : []) {
    add(blocker);
  }
  for (const image of Array.isArray(artifact?.images) ? artifact.images : []) {
    for (const blocker of Array.isArray(image?.blockers) ? image.blockers : []) {
      add(blocker, image?.status === "FAIL" ? image.name : null);
    }
  }
  return blockers;
}

function isDockerAggregateIssue(issue) {
  return issue === "status=FAIL"
    || /^failed images=\d+$/.test(issue)
    || /^skipped images=\d+$/.test(issue);
}

function validateMigrationEvidence() {
  const migration = readJson("migration/migration-evidence.json");
  if (migration.missing) {
    record("migration-evidence", "warning", "missing migration evidence artifact; run scripts/ddd-migration-evidence.mjs", migration.file);
    if (strict) {
      record("migration-evidence-strict", "blocker", "strict release requires Flyway migration evidence", migration.file);
    }
    return;
  }
  if (migration.invalid) {
    record("migration-evidence", "blocker", `invalid JSON: ${migration.invalid}`, migration.file);
    return;
  }
  record("migration-evidence", "present", "migration/migration-evidence.json", migration.file);
  validateFreshArtifact("migration-evidence", migration.data);
  const issues = validateMigrationEvidenceContract(migration.data, { strict });
  const hasConcreteMigrationIssues = issues.some((issue) => issue !== "status=FAIL");
  for (const issue of issues) {
    if (hasConcreteMigrationIssues && issue === "status=FAIL") {
      continue;
    }
    if (issue === "strict release requires fresh database migration validation") {
      record("migration-evidence-fresh-db", "blocker", issue);
    } else if (issue === "strict release requires old database upgrade migration validation") {
      record("migration-evidence-upgrade-db", "blocker", issue);
    } else if (issue.startsWith("runtime.")) {
      record("migration-evidence-runtime", "blocker", issue);
    } else {
      record("migration-evidence", "blocker", issue);
    }
  }
}

function validateReleaseConfigEvidence() {
  const config = readJson("config/release-config-evidence.json");
  if (config.missing) {
    record("release-config-evidence", "warning", "missing release config evidence artifact; run scripts/ddd-release-config-evidence.mjs with DDD_RELEASE_ENV_FILE", config.file);
    if (strict) {
      record("release-config-evidence-strict", "blocker", "strict release requires production-equivalent configuration evidence", config.file);
    }
    return;
  }
  if (config.invalid) {
    record("release-config-evidence", "blocker", `invalid JSON: ${config.invalid}`, config.file);
    return;
  }
  record("release-config-evidence", "present", "config/release-config-evidence.json", config.file);
  validateFreshArtifact("release-config-evidence", config.data);
  validateRuntimeProvenance("release-config-evidence", config.data);
  for (const issue of validateReleaseConfigArtifact(config.data)) {
    record("release-config-evidence", strict ? "blocker" : "warning", issue);
  }
  if (config.data.status !== "PASS") {
    const detail = `status=${config.data.status}, blockers=${config.data.summary?.blockers || 0}`;
    record("release-config-evidence", strict ? "blocker" : "warning", detail);
  }
  if (strict && !config.data.envFile) {
    record("release-config-evidence-env-file", "blocker", "strict release requires DDD_RELEASE_ENV_FILE-backed configuration evidence");
  }
}

function validateReleaseEnvLintEvidence() {
  const lint = readJson("release/release-env-lint.json");
  if (lint.missing) {
    record("release-env-lint", "warning", "missing release env lint artifact; run scripts/ddd-release-env-file-lint.mjs with DDD_RELEASE_ENV_FILE", lint.file);
    if (strict) {
      record("release-env-lint-strict", "blocker", "strict release requires release env lint evidence", lint.file);
    }
    return;
  }
  if (lint.invalid) {
    record("release-env-lint", "blocker", `invalid JSON: ${lint.invalid}`, lint.file);
    return;
  }
  record("release-env-lint", "present", "release/release-env-lint.json", lint.file);
  validateFreshArtifact("release-env-lint", lint.data);
  validateRuntimeProvenance("release-env-lint", lint.data);
  if (lint.data.generatedMissingTemplate === true || lint.data.inputKind === "generated-missing-template") {
    record(
      "release-env-lint-real-env-file",
      strict ? "blocker" : "warning",
      "release env lint was run against generated missing-env template; strict release requires a completed DDD_RELEASE_ENV_FILE",
      lint.file,
    );
    return;
  }
  if (lint.data.status !== "PASS") {
    const detail = `status=${lint.data.status}, blockers=${lint.data.summary?.blockers || 0}`;
    record("release-env-lint", strict ? "blocker" : "warning", detail, lint.file);
  }
  const unresolved = Number(lint.data.summary?.unresolvedTemplateKeys || 0);
  if (unresolved > 0) {
    record("release-env-lint-placeholders", strict ? "blocker" : "warning", `unresolvedTemplateKeys=${unresolved}`, lint.file);
  }
  const configBlockers = Number(lint.data.summary?.releaseConfigBlockers || 0);
  if (configBlockers > 0) {
    record("release-env-lint-config", strict ? "blocker" : "warning", `releaseConfigBlockers=${configBlockers}`, lint.file);
  }
}

function validateEvidenceManifest() {
  const manifest = readJson("release/evidence-manifest.json");
  if (manifest.missing) {
    record("release-evidence-manifest", "warning", "missing evidence manifest; run scripts/ddd-release-evidence-manifest.mjs before strict release gate", manifest.file);
    if (strict) {
      record("release-evidence-manifest-strict", "blocker", "strict release requires checksummed evidence manifest", manifest.file);
    }
    return;
  }
  if (manifest.invalid) {
    record("release-evidence-manifest", "blocker", `invalid JSON: ${manifest.invalid}`, manifest.file);
    return;
  }
  record("release-evidence-manifest", "present", "release/evidence-manifest.json", manifest.file);
  validateFreshArtifact("release-evidence-manifest", manifest.data);
  for (const issue of validateManifestArtifact(manifest.data, { strict })) {
    if (issue.startsWith("status=FAIL")) {
      continue;
    }
    const checkName = issue.startsWith("manifest provenance")
      ? "release-evidence-manifest-provenance"
      : "release-evidence-manifest";
    record(checkName, strict ? "blocker" : "warning", issue);
  }
}

function validateReleaseOrchestrator() {
  const orchestrator = readJson("release/orchestrator-report.json");
  if (orchestrator.missing) {
    record("release-evidence-orchestrator", "warning", "missing orchestrator report; run scripts/ddd-release-evidence-orchestrator.mjs --run --strict", orchestrator.file);
    if (strict) {
      record("release-evidence-orchestrator-strict", "blocker", "strict release requires orchestrator execution report", orchestrator.file);
    }
    return;
  }
  if (orchestrator.invalid) {
    record("release-evidence-orchestrator", "blocker", `invalid JSON: ${orchestrator.invalid}`, orchestrator.file);
    return;
  }

  const report = orchestrator.data;
  record("release-evidence-orchestrator", "present", "release/orchestrator-report.json", orchestrator.file);
  validateFreshArtifact("release-evidence-orchestrator", report);
  validateRuntimeProvenance("release-evidence-orchestrator", report);

  for (const check of report.preflight?.checks || []) {
    if (check?.status !== "BLOCKER") {
      continue;
    }
    const detail = check.detail || check.id || "orchestrator preflight blocker";
    record(
      `release-evidence-orchestrator-preflight-${check.id || "blocker"}`,
      strict ? "blocker" : "warning",
      detail,
      orchestrator.file,
    );
  }

  for (const issue of validateOrchestratorContract(report, { strict })) {
    record("release-evidence-orchestrator", strict ? "blocker" : "warning", issue);
  }
}

function validateFrontendSmoke() {
  const frontend = readJson("frontend/frontend-smoke.json");
  if (frontend.missing) {
    record("frontend-smoke", "warning", "missing frontend smoke artifact; run scripts/ddd-frontend-smoke-evidence.mjs", frontend.file);
    if (strict) {
      record("frontend-smoke-strict", "blocker", "strict release requires deployed frontend smoke evidence", frontend.file);
    }
    return;
  }
  if (frontend.invalid) {
    record("frontend-smoke", "blocker", `invalid JSON: ${frontend.invalid}`, frontend.file);
    return;
  }
  record("frontend-smoke", "present", "frontend/frontend-smoke.json", frontend.file);
  validateFreshArtifact("frontend-smoke", frontend.data);
  validateProductionEquivalence("frontend-smoke", frontend.data, "frontend smoke");
  if (isLocalBaseUrl(frontend.data?.baseUrl)) {
    recordLocalOnlyArtifact("frontend-smoke-environment", frontend.data.baseUrl);
  }
  const frontendHasConcreteBlockers = Array.isArray(frontend.data?.blockers) && frontend.data.blockers.length > 0;
  const frontendContractStrict = strict
    || frontend.data?.expectDeployed === true
    || frontend.data?.productionEquivalence?.strict === true;
  for (const issue of validateFrontendSmokeArtifact(frontend.data, { strict: frontendContractStrict, validateBlockers: true })) {
    if (frontendHasConcreteBlockers && issue === "status=FAIL") {
      continue;
    }
    const checkName = issue.includes("baseURL")
      || issue.includes("baseUrl")
      || issue.includes("local-only")
      || issue.includes("EXPECT_DEPLOYED")
      || issue.includes("provenance")
      || issue.includes("productionEquivalence")
      ? "frontend-smoke-environment"
      : "frontend-smoke";
    record(checkName, strict ? "blocker" : "warning", issue);
  }
}

function validateFrontendBuildEvidence() {
  const frontend = readJson("frontend/frontend-build-evidence.json");
  if (frontend.missing) {
    record("frontend-build-evidence", "warning", "missing frontend build evidence artifact; run scripts/ddd-frontend-build-evidence.mjs", frontend.file);
    if (strict) {
      record("frontend-build-evidence-strict", "blocker", "strict release requires frontend production build evidence", frontend.file);
    }
    return;
  }
  if (frontend.invalid) {
    record("frontend-build-evidence", "blocker", `invalid JSON: ${frontend.invalid}`, frontend.file);
    return;
  }
  record("frontend-build-evidence", "present", "frontend/frontend-build-evidence.json", frontend.file);
  validateFreshArtifact("frontend-build-evidence", frontend.data);
  validateRuntimeProvenance("frontend-build-evidence", frontend.data);
  for (const issue of validateFrontendBuildArtifact(frontend.data)) {
    record("frontend-build-evidence", "blocker", issue);
  }
}

function validateFrontendStaticEvidence() {
  const frontend = readJson("frontend/frontend-static-evidence.json");
  if (frontend.missing) {
    record("frontend-static-evidence", "warning", "missing frontend static evidence artifact; run scripts/ddd-frontend-static-evidence.mjs", frontend.file);
    if (strict) {
      record("frontend-static-evidence-strict", "blocker", "strict release requires frontend lint/typecheck/unit evidence", frontend.file);
    }
    return;
  }
  if (frontend.invalid) {
    record("frontend-static-evidence", "blocker", `invalid JSON: ${frontend.invalid}`, frontend.file);
    return;
  }
  record("frontend-static-evidence", "present", "frontend/frontend-static-evidence.json", frontend.file);
  validateFreshArtifact("frontend-static-evidence", frontend.data);
  validateRuntimeProvenance("frontend-static-evidence", frontend.data);
  for (const issue of validateFrontendStaticArtifact(frontend.data)) {
    record("frontend-static-evidence", "blocker", issue);
  }
}

function validateRollbackDrill() {
  const rollback = readJson("rollback/rollback-drill.json");
  if (rollback.missing) {
    record("rollback-drill", "warning", "missing rollback drill artifact; create rollback/rollback-drill.json from the runbook drill matrix", rollback.file);
    if (strict) {
      record("rollback-drill-strict", "blocker", "strict release requires rollback drill evidence for every bounded context", rollback.file);
    }
    return;
  }
  if (rollback.invalid) {
    record("rollback-drill", "blocker", `invalid JSON: ${rollback.invalid}`, rollback.file);
    return;
  }
  record("rollback-drill", "present", "rollback/rollback-drill.json", rollback.file);
  validateFreshArtifact("rollback-drill", rollback.data);
  const issues = validateRollbackDrillContract(rollback.data, { strict, validateBlockers: true });
  const hasConcreteRollbackIssues = issues.some((issue) => issue !== `status must be PASS for strict release, got ${rollback.data?.status ?? "missing"}`);
  for (const issue of issues) {
    if (hasConcreteRollbackIssues && issue === `status must be PASS for strict release, got ${rollback.data?.status ?? "missing"}`) {
      continue;
    }
    record("rollback-drill", "blocker", issue);
  }
}

function validateExplainEvidence() {
  const explainDir = process.env.DDD_EXPLAIN_DIR
    ? path.resolve(process.env.DDD_EXPLAIN_DIR)
    : path.join(repoRoot, "tmp", "ddd-explain");
  if (!fs.existsSync(explainDir)) {
    record("explain-evidence", "warning", `missing explain directory ${explainDir}`);
    if (strict) {
      record("explain-evidence-strict", "blocker", "strict release requires production-scale EXPLAIN artifacts", explainDir);
    }
    return;
  }
  const explainFiles = fs.readdirSync(explainDir).filter((file) => file.endsWith(".json"));
  if (explainFiles.length === 0) {
    record("explain-evidence", "warning", `no explain JSON files in ${explainDir}`);
    if (strict) {
      record("explain-evidence-strict", "blocker", "strict release requires production-scale EXPLAIN artifacts", explainDir);
    }
  } else {
    record("explain-evidence", "present", `${explainFiles.length} EXPLAIN file(s)`, explainDir);
    if (strict) {
      for (const fileName of missingRequiredExplainFiles(explainFiles)) {
        record("explain-evidence", "blocker", `missing required explain JSON file ${fileName}`, explainDir);
      }
    }
    if (strict && Number.isFinite(maxArtifactAgeHours) && maxArtifactAgeHours > 0) {
      for (const file of explainFiles) {
        const fullPath = path.join(explainDir, file);
        const ageHours = (Date.now() - fs.statSync(fullPath).mtimeMs) / 36e5;
        if (ageHours > maxArtifactAgeHours) {
          record("explain-evidence-freshness", "blocker", `${file} is ${Math.round(ageHours * 10) / 10}h old; limit=${maxArtifactAgeHours}h`, fullPath);
        }
      }
    }
    for (const file of explainFiles) {
      validateExplainJsonArtifact(file, path.join(explainDir, file));
    }
  }
}

validateRuntimeReadiness();
validateAuthenticatedPerformance();
validateFileProcessing();
validatePaymentWebhook();
validateOutbox();
validateAiRuntimeDrill();
validatePhysicalSplit();
validateBackendTestEvidence();
validateBackendBuildEvidence();
validateDockerBuildEvidence();
validateMigrationEvidence();
validateReleaseEnvLintEvidence();
validateReleaseConfigEvidence();
validateReleaseOrchestrator();
validateEvidenceManifest();
validateFrontendBuildEvidence();
validateFrontendStaticEvidence();
validateFrontendSmoke();
validateRollbackDrill();
validateExplainEvidence();

const report = {
  generatedAt: new Date().toISOString(),
  strict,
  artifactRoot,
  summary: {
    checks: checks.length,
    blockers: blockers.length,
    warnings: warnings.length,
  },
  checks,
  blockers,
  blockerDetails,
  warnings,
  warningDetails,
};

const selfIssues = validateReleaseGateArtifact(report);
if (selfIssues.length > 0) {
  for (const issue of selfIssues) {
    record("release-evidence-gate-contract", "blocker", issue);
  }
  report.summary.checks = checks.length;
  report.summary.blockers = blockers.length;
  report.summary.warnings = warnings.length;
}

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(portableValue(report), null, 2)}\n`);

if (blockers.length > 0 && strict) {
  for (const blocker of blockers) {
    console.error(`[ddd-release-evidence-gate] ${blocker}`);
  }
  console.error(`[ddd-release-evidence-gate] wrote report to ${outputFile}`);
  process.exit(1);
}

const mode = strict ? "strict" : "advisory";
console.log(`[ddd-release-evidence-gate] ${mode} evidence checks completed; blockers=${blockers.length}; warnings=${warnings.length}; report=${outputFile}`);
