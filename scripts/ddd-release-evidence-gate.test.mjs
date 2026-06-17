#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { validateReleaseGateArtifact } from "./ddd-release-gate-contract.mjs";
import { requiredManifestArtifacts } from "./ddd-release-evidence-manifest-contract.mjs";
import { requiredMigrationLocations } from "./ddd-migration-evidence-contract.mjs";
import {
  buildRollbackDrillBlockers,
  requiredRollbackContexts,
} from "./ddd-rollback-drill-contract.mjs";
import {
  buildPhysicalSplitSummary,
  requiredPhysicalSplitContexts,
} from "./ddd-physical-split-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const artifactRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-gate-"));
const reportFile = path.join(artifactRoot, "release", "release-evidence-gate.json");
const now = new Date().toISOString();
const staleGeneratedAt = new Date(Date.now() - 48 * 60 * 60 * 1000).toISOString();

function writeJson(relativePath, data) {
  const file = path.join(artifactRoot, relativePath);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify(data, null, 2)}\n`);
}

writeJson("performance/authenticated-runtime-actual.json", {
  baseUrl: "http://staging-api.lumira.internal",
  checkedAt: now,
  sourceEnvironment: "staging",
  releaseCandidate: "rc-1",
  evidenceOperator: "release-owner",
  failed: 0,
  samples: 10,
  p95: 80,
  upload: {
    status: 200,
    elapsedMs: 60,
  },
  perEndpoint: {
    "GET /api/v2/auth/current-user": {
      samples: 10,
      p95: 80,
    },
  },
});

writeJson("ai/ai-runtime-drill.json", {
  baseUrl: "http://ai.staging.lumira.internal",
  checkedAt: now,
  status: "PASS",
  sourceEnvironment: "staging",
  releaseCandidate: "rc-1",
  evidenceOperator: "release-owner",
  expectations: {
    providerRemote: false,
    ownerGatewayRemote: false,
  },
  summary: {
    failed: 0,
    failureCategories: {},
  },
  remoteEvidence: {
    provider: {
      status: "UP",
      remoteConfigured: true,
      localFallbackProvider: false,
      provider: "openai-compatible",
    },
    ownerGateway: {
      status: "UP",
      configuredOwnerCount: 1,
    },
  },
  failures: [],
  failureDetails: [],
});

writeJson("frontend/frontend-smoke.json", {
  generatedAt: now,
  status: "FAIL",
  inputFile: "playwright.json",
  baseUrl: "http://app.staging.lumira.internal",
  sourceEnvironment: "staging",
  releaseCandidate: "rc-1",
  evidenceOperator: "release-owner",
  productionEquivalence: {
    strict: true,
    https: false,
    localOnly: false,
    deploymentEvidence: null,
    issues: ["strict frontend smoke requires HTTPS baseUrl evidence"],
  },
  expectDeployed: true,
  summary: {
    total: 1,
    passed: 0,
    failed: 1,
    skipped: 0,
    requiredFlows: 0,
    missingRequiredFlows: 0,
  },
  requiredFlows: [],
  missingRequiredFlows: [],
  flowCoverage: [],
  tests: [
    {
      title: "dashboard page is reachable @smoke",
      status: "failed",
      durationMs: 10,
      errors: 1,
    },
  ],
  blockers: [
    "failed=1",
    "strict release requires HTTPS frontend baseURL evidence",
  ],
});

writeJson("release/orchestrator-report.json", {
  generatedAt: now,
  sourceEnvironment: "staging",
  releaseCandidate: "rc-1",
  evidenceOperator: "release-owner",
  mode: "plan",
  strict: true,
  summary: {
    steps: 1,
    executed: 0,
    failed: 0,
  },
  preflight: {
    status: "FAIL",
    blockers: 1,
    warnings: 0,
    checks: [
      {
        id: "docker-daemon",
        status: "BLOCKER",
        detail: `Docker daemon is not available: Cannot connect to ${os.homedir()}/.docker/run/docker.sock`,
        envKeys: ["DDD_DOCKER_COMMAND"],
      },
    ],
  },
  selectedSteps: [],
  results: [],
});

writeJson("release/release-env-lint.json", {
  generatedAt: now,
  sourceEnvironment: "staging",
  releaseCandidate: "rc-1",
  evidenceOperator: "release-owner",
  status: "FAIL",
  envFile: "/secure/.env.release",
  summary: {
    keys: 2,
    duplicateKeys: 0,
    unresolvedTemplateKeys: 1,
    releaseConfigBlockers: 1,
    warnings: 0,
    blockers: 2,
  },
  keys: ["LUMIRA_BASE_URL", "DB_URL"],
  duplicateKeys: [],
  unresolvedTemplateKeys: ["LUMIRA_BASE_URL"],
  blockers: [
    "LUMIRA_BASE_URL: __REQUIRED__ placeholder must be replaced",
    "runtime.backend base url: must use HTTPS for production-equivalent evidence",
  ],
  warnings: [],
});

writeJson("release/evidence-manifest.json", {
  generatedAt: now,
  sourceEnvironment: "staging",
  releaseCandidate: "rc-1",
  evidenceOperator: "release-owner",
  status: "FAIL",
  summary: {
    requiredArtifacts: requiredManifestArtifacts.length,
    presentArtifacts: requiredManifestArtifacts.length - 1,
    invalidJsonArtifacts: 0,
    provenanceIssueArtifacts: 0,
    explainFiles: 1,
    blockers: 1,
  },
  artifacts: requiredManifestArtifacts.map((relativePath) => (
    relativePath === "performance/authenticated-runtime-baseline.json"
      ? { relativePath, present: false, status: "MISSING" }
      : {
          relativePath,
          present: true,
          status: "PRESENT",
          bytes: 10,
          sha256: "a".repeat(64),
          timestamp: {
            field: "generatedAt",
            value: now,
          },
          provenanceIssues: [],
        }
  )),
  explain: {
    directory: path.join(artifactRoot, "missing-explain"),
    present: true,
    files: [{
      relativePath: "tmp/ddd-explain/example.json",
      bytes: 10,
      sha256: "b".repeat(64),
      mtime: now,
    }],
  },
  blockers: ["missing artifact performance/authenticated-runtime-baseline.json"],
});

writeJson("build/docker-image-evidence.json", {
  generatedAt: now,
  sourceEnvironment: "staging",
  releaseCandidate: "rc-1",
  evidenceOperator: "release-owner",
  dockerCommand: "docker",
  status: "FAIL",
  summary: {
    images: 2,
    passed: 0,
    failed: 0,
    skipped: 2,
    blockers: 1,
  },
  images: [
    {
      name: "lumira-server",
      dockerfile: "deploy/docker/service.Dockerfile",
      dockerfileSha256: "a".repeat(64),
      tag: "lumira/lumira-server:test",
      expectedExposedPort: "8080/tcp",
      requireNonRootUser: true,
      staticDockerfile: {
        status: "PASS",
        exists: true,
        dockerfileSha256: "a".repeat(64),
        issues: [],
        checks: {
          exposesExpectedPort: true,
          definesEntrypointOrCmd: true,
          nonRootUser: true,
        },
      },
      status: "SKIPPED",
      skipReason: "docker daemon is not available",
      blockers: ["docker daemon is not available"],
    },
    {
      name: "frontend",
      dockerfile: "deploy/docker/frontend.Dockerfile",
      dockerfileSha256: "b".repeat(64),
      tag: "lumira/frontend:test",
      expectedExposedPort: "80/tcp",
      requireNonRootUser: false,
      staticDockerfile: {
        status: "PASS",
        exists: true,
        dockerfileSha256: "b".repeat(64),
        issues: [],
        checks: {
          exposesExpectedPort: true,
          definesEntrypointOrCmd: true,
          nonRootUser: true,
        },
      },
      status: "SKIPPED",
      skipReason: "docker daemon is not available",
      blockers: ["docker daemon is not available"],
    },
  ],
  blockers: ["docker daemon is not available"],
});

writeJson("migration/migration-evidence.json", {
  generatedAt: now,
  status: "FAIL",
  summary: {
    locations: requiredMigrationLocations.length,
    migrationFiles: requiredMigrationLocations.length,
    duplicateVersionLocations: 0,
    emptyFiles: 0,
    runtimeReady: false,
  },
  locations: requiredMigrationLocations.map((location, index) => ({
    location,
    exists: true,
    migrationCount: 1,
    duplicateVersions: [],
    emptyFiles: [],
    migrations: [{
      version: `T${index}`,
      description: "test migration",
      file: `${location}/V${index}__test.sql`,
      bytes: 1,
      sha256: "c".repeat(64),
    }],
  })),
  runtime: {
    freshDatabaseValidated: false,
    upgradeDatabaseValidated: false,
    environment: "staging",
    releaseCandidate: "rc-1",
    operator: "release-owner",
    completedAt: now,
    freshDatabaseEvidence: "",
    upgradeDatabaseEvidence: "",
  },
});

const rollbackArtifact = {
  generatedAt: staleGeneratedAt,
  checkedAt: now,
  status: "FAIL",
  environment: "staging",
  releaseVersion: "rc-1",
  operator: "release-owner",
  contexts: requiredRollbackContexts.map((context) => ({
    context,
    status: "MISSING",
    rollbackAction: null,
    drillEvidence: null,
  })),
  contextDiagnostics: requiredRollbackContexts.map((context) => ({
    context,
    status: "MISSING",
    owner: "release-owner",
    action: `Exercise ${context} rollback.`,
    evidence: null,
    ready: false,
    missingEvidence: true,
  })),
  warnings: [],
};
rollbackArtifact.blockers = buildRollbackDrillBlockers(rollbackArtifact, { strict: true });
writeJson("rollback/rollback-drill.json", rollbackArtifact);

const physicalSplitArtifact = {
  generatedAt: now,
  sourceEnvironment: "staging",
  releaseCandidate: "rc-1",
  evidenceOperator: "release-owner",
  strict: false,
  globalChecks: [
    { name: "split-gate-document", status: "pass", detail: "docs/29-ddd-physical-split-readiness.md present" },
    { name: "architecture-boundary-test", status: "pass", detail: "DddArchitectureBoundaryTest guards owner writes and dependency boundaries" },
  ],
  contexts: requiredPhysicalSplitContexts.map((context) => ({
    ...context,
    standaloneBootApplication: context.physicalServiceTarget,
    businessController: context.physicalServiceTarget ? `${context.module}/src/main/java/Controller.java` : null,
    migrationFiles: [],
    missingBusinessEndpoints: [],
    checks: [
      { name: "module", status: "pass", detail: context.module },
      { name: "owner-manifest", status: "pass", detail: `${context.ownerContext}: owner tables declared` },
      { name: "readiness-endpoint", status: "pass", detail: `${context.route}/readiness` },
      { name: "health-endpoint", status: "pass", detail: `${context.route}/health` },
      { name: "metrics-endpoint", status: "pass", detail: `${context.route}/metrics` },
      { name: "cross-service-pom-dependency", status: "pass", detail: "no direct service module dependency" },
    ],
    blockers: [],
    warnings: [],
  })),
};
physicalSplitArtifact.summary = buildPhysicalSplitSummary(physicalSplitArtifact);
writeJson("split/physical-split-readiness.json", physicalSplitArtifact);

const result = spawnSync("node", ["scripts/ddd-release-evidence-gate.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_RELEASE_EVIDENCE_DIR: artifactRoot,
    DDD_RELEASE_EVIDENCE_REPORT: reportFile,
    DDD_EXPLAIN_DIR: path.join(artifactRoot, "missing-explain"),
  },
});

assert.notEqual(result.status, 0);
const report = JSON.parse(fs.readFileSync(reportFile, "utf8"));
assert.deepEqual(validateReleaseGateArtifact(report), []);
assert.doesNotMatch(fs.readFileSync(reportFile, "utf8"), new RegExp(escapeRegExp(repoRoot)));
assert.doesNotMatch(fs.readFileSync(reportFile, "utf8"), new RegExp(escapeRegExp(os.homedir())));

assert(report.blockers.includes(
  "authenticated-performance-production-equivalence: strict authenticated performance actual requires HTTPS baseUrl evidence",
));
assert(report.blockers.includes(
  "authenticated-performance-production-equivalence: strict authenticated performance actual deploymentEvidence is required",
));
assert(report.blockers.includes(
  "ai-runtime-production-equivalence: strict AI runtime drill requires HTTPS baseUrl evidence",
));
assert(report.blockers.includes(
  "ai-runtime-production-equivalence: strict AI runtime drill deploymentEvidence is required",
));
assert(report.blockers.includes(
  "frontend-smoke-production-equivalence: strict frontend smoke requires HTTPS baseUrl evidence",
));
assert(report.blockers.includes(
  "frontend-smoke-production-equivalence: strict frontend smoke deploymentEvidence is required",
));
assert(report.blockers.includes("frontend-smoke: failed=1"));
assert(!report.blockers.includes("frontend-smoke: status=FAIL"));
assert(report.blockers.includes(
  "release-evidence-orchestrator-preflight-docker-daemon: Docker daemon is not available: Cannot connect to ~/.docker/run/docker.sock",
));
assert(report.blockers.includes(
  "release-env-lint: status=FAIL, blockers=2",
));
assert(report.blockers.includes(
  "release-env-lint-placeholders: unresolvedTemplateKeys=1",
));
assert(report.blockers.includes(
  "release-env-lint-config: releaseConfigBlockers=1",
));
assert(report.blockers.includes(
  "docker-build-evidence: docker daemon is not available",
));
assert(!report.blockers.includes("docker-build-evidence: status=FAIL"));
assert(!report.blockers.includes("docker-build-evidence: skipped images=2"));
assert(report.blockers.includes(
  "migration-evidence-fresh-db: strict release requires fresh database migration validation",
));
assert(!report.blockers.includes("migration-evidence: status=FAIL"));
assert(report.blockers.includes("rollback-drill: IAM status must be PASS or DEFERRED"));
assert(!report.blockers.some((blocker) => blocker.startsWith("rollback-drill-freshness:")));
assert(!report.blockers.includes("rollback-drill: status must be PASS for strict release, got FAIL"));
assert(report.blockers.includes(
  "release-evidence-manifest: missing artifact performance/authenticated-runtime-baseline.json",
));
assert(!report.blockers.some((blocker) => blocker === "release-evidence-manifest: status=FAIL, blockers=1"));
assert(report.blockers.includes(
  "physical-split-readiness: strict release requires physical split artifact generated with DDD_SPLIT_STRICT=true",
));

const advisoryArtifactRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-gate-advisory-"));
const advisoryReportFile = path.join(advisoryArtifactRoot, "release", "release-evidence-gate.json");
const advisoryFrontendFile = path.join(advisoryArtifactRoot, "frontend", "frontend-smoke.json");
fs.mkdirSync(path.dirname(advisoryFrontendFile), { recursive: true });
fs.writeFileSync(advisoryFrontendFile, `${JSON.stringify({
  generatedAt: now,
  status: "FAIL",
  inputFile: "missing-playwright.json",
  baseUrl: null,
  sourceEnvironment: "staging",
  releaseCandidate: "rc-1",
  evidenceOperator: "release-owner",
  productionEquivalence: {
    strict: true,
    https: false,
    localOnly: false,
    deploymentEvidence: null,
    issues: ["strict frontend smoke requires HTTPS baseUrl evidence"],
  },
  expectDeployed: false,
  summary: {
    total: 0,
    passed: 0,
    failed: 0,
    skipped: 0,
    requiredFlows: 1,
    missingRequiredFlows: 1,
  },
  requiredFlows: ["dashboard page is reachable"],
  missingRequiredFlows: ["dashboard page is reachable"],
  flowCoverage: [{
    flow: "dashboard page is reachable",
    status: "missing",
    reason: "no passed Playwright @smoke test matched this required flow",
  }],
  tests: [],
  diagnostics: {
    playwrightReport: {
      present: false,
      reason: "missing Playwright JSON report /tmp/missing-playwright.json",
    },
  },
  blockers: [
    "missing Playwright JSON report: missing Playwright JSON report /tmp/missing-playwright.json",
    "missing required flows=1",
    "missing baseUrl",
    "strict release requires DDD_FRONTEND_EXPECT_DEPLOYED=true evidence",
  ],
}, null, 2)}\n`);

const advisoryResult = spawnSync("node", ["scripts/ddd-release-evidence-gate.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_EVIDENCE_STRICT: "false",
    DDD_RELEASE_EVIDENCE_DIR: advisoryArtifactRoot,
    DDD_RELEASE_EVIDENCE_REPORT: advisoryReportFile,
    DDD_EXPLAIN_DIR: path.join(advisoryArtifactRoot, "missing-explain"),
  },
});
assert.equal(advisoryResult.status, 0);
const advisoryReport = JSON.parse(fs.readFileSync(advisoryReportFile, "utf8"));
assert(!advisoryReport.warnings.includes("frontend-smoke: frontend smoke blockers length mismatch: declared=4, actual=3"));
assert(advisoryReport.warnings.includes("frontend-smoke-environment: strict release requires DDD_FRONTEND_EXPECT_DEPLOYED=true evidence"));

console.log("[ddd-release-evidence-gate.test] ok");

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
