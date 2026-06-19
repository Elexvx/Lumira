import {
  evidenceValueIssue,
  isIsoTimestamp,
  validateProductionEquivalenceEvidence,
} from "./ddd-release-evidence-utils.mjs";

export const requiredAuthenticatedPerformanceEndpoints = [
  "GET /api/v2/auth/current-user",
  "GET /api/v2/iam/tenants/current",
  "GET /api/v2/iam/permissions",
  "GET /api/v2/message/unread-count",
  "GET /api/v2/message/messages?pageNo=1&pageSize=20",
  "GET /api/v2/files?pageNo=1&pageSize=20",
  "GET /api/v2/plugins/current/bootstrap",
  "GET /api/v2/localization/runtime/zh-CN",
  "GET /api/v2/payment/providers",
];

export const requiredPerformanceBaselineEvidenceChecklist = [
  {
    id: "authenticated-runtime-actual-evidence",
    description: "Production-equivalent authenticated hot-path runtime artifact captured from the real release candidate backend.",
    requiredArtifacts: [
      "artifacts/ddd/performance/authenticated-runtime-actual.json",
    ],
    requiredFields: [
      "baseUrl",
      "checkedAt",
      "concurrency",
      "durationMs",
      "samples",
      "failed",
      "p95",
      "upload.fileId",
      "upload.elapsedMs",
      "oneShots[POST /api/v2/auth/session/keepalive]",
      "perEndpoint[*].samples",
      "perEndpoint[*].p95",
      "productionEquivalence.strict",
      "productionEquivalence.https",
      "productionEquivalence.localOnly",
      "productionEquivalence.deploymentEvidence",
      "productionEquivalence.issues",
    ],
    requiredEnvKeys: [
      "LUMIRA_BASE_URL",
      "BASE_URL",
      "DDD_AUTH_USERNAME",
      "DDD_AUTH_PASSWORD",
      "DDD_AUTH_PERF_ENVIRONMENT",
      "DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE",
      "DDD_EVIDENCE_OPERATOR",
      "DDD_RELEASE_CANDIDATE",
    ],
    acceptanceCriteria: [
      "Base URL is HTTPS and non-local.",
      "productionEquivalence.strict=true, https=true, localOnly=false, deploymentEvidence is non-placeholder, and issues is empty.",
      "All required authenticated endpoints have positive sample counts and p95 metrics.",
      "failed=0 and upload plus keepalive timing evidence succeeded.",
    ],
  },
  {
    id: "authenticated-runtime-baseline-promotion-evidence",
    description: "Accepted runtime actual promoted to the release baseline with operator, environment, release candidate, and checksum provenance.",
    requiredArtifacts: [
      "artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json",
      "artifacts/ddd/performance/authenticated-runtime-baseline.json",
    ],
    requiredFields: [
      "status",
      "sourceFile",
      "sourceSha256",
      "outputFile",
      "sourceArtifact",
      "sourceEnvironment",
      "releaseCandidate",
      "acceptedBy",
      "baseline.baselineType",
      "baseline.acceptedAt",
      "baseline.acceptedBy",
      "baseline.sourceEnvironment",
      "baseline.sourceArtifact",
      "baseline.sourceSha256",
      "baseline.releaseCandidate",
      "baseline.evidenceOperator",
    ],
    requiredEnvKeys: [
      "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY",
      "DDD_AUTH_PERF_BASELINE_ENVIRONMENT",
      "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT",
      "DDD_RELEASE_CANDIDATE",
      "DDD_EVIDENCE_OPERATOR",
    ],
    acceptanceCriteria: [
      "Promotion status is PASS.",
      "Baseline sourceSha256 matches the accepted actual artifact.",
      "Strict baseline metadata has no placeholder or missing provenance fields.",
      "Baseline shape is valid under strict authenticated performance contract.",
    ],
  },
  {
    id: "baseline-release-gate-acceptance-evidence",
    description: "Post-promotion release gates rerun with the accepted baseline and no authenticated performance regression blockers.",
    requiredArtifacts: [
      "artifacts/ddd/release/evidence-manifest.json",
      "artifacts/ddd/release/readiness-summary.json",
      "artifacts/ddd/release/release-final-go-no-go.json",
    ],
    requiredFields: [
      "evidence-manifest.performance.authenticated-runtime-baseline",
      "readiness-summary.diagnostics.authenticatedPerformance.regressionIssues",
      "release-final-go-no-go.recommendation",
      "release-final-go-no-go.cutoverAllowed",
    ],
    requiredEnvKeys: [
      "DDD_RELEASE_MANIFEST_STRICT",
      "DDD_FINAL_GO_NO_GO_ENFORCE",
    ],
    acceptanceCriteria: [
      "Evidence manifest includes the authenticated runtime baseline with checksum.",
      "Regression issue list is empty or only contains reviewed non-blocking context.",
      "Final go/no-go gate has been rerun after baseline promotion.",
      "No manual waiver is used for missing production-equivalent performance evidence.",
    ],
  },
];

export function validateAuthenticatedPerformanceShape(label, artifact, { strict = false } = {}) {
  const issues = [];
  if (!artifact || typeof artifact !== "object") {
    return [`${label} must be a JSON object`];
  }
  issues.push(...validateProductionEquivalenceEvidence(label, artifact, { strict }));
  if (!artifact.checkedAt || !isIsoTimestamp(artifact.checkedAt)) {
    issues.push(`${label} checkedAt must be an ISO timestamp`);
  }
  if (!Number.isFinite(artifact.durationMs) || artifact.durationMs <= 0) {
    issues.push(`${label} durationMs must be positive`);
  }
  if (!Number.isFinite(artifact.concurrency) || artifact.concurrency <= 0) {
    issues.push(`${label} concurrency must be positive`);
  }
  if (!Number.isFinite(artifact.p95) || artifact.p95 <= 0) {
    issues.push(`${label} is missing positive p95`);
  }
  if (Number.isFinite(artifact.p50) && Number.isFinite(artifact.p95) && artifact.p50 > artifact.p95) {
    issues.push(`${label} p50 must be <= p95`);
  }
  if (Number.isFinite(artifact.p99) && Number.isFinite(artifact.p95) && artifact.p95 > artifact.p99) {
    issues.push(`${label} p95 must be <= p99`);
  }
  if (!artifact.upload || artifact.upload.status !== 200 || !Number.isFinite(artifact.upload.elapsedMs) || artifact.upload.elapsedMs <= 0) {
    issues.push(`${label} is missing successful upload timing`);
  } else {
    if (artifact.upload.path !== "/api/v2/files/upload") {
      issues.push(`${label} upload path must be /api/v2/files/upload`);
    }
    if (!artifact.upload.fileId) {
      issues.push(`${label} upload fileId is required`);
    }
  }
  if (!artifact.perEndpoint || typeof artifact.perEndpoint !== "object" || Object.keys(artifact.perEndpoint).length === 0) {
    issues.push(`${label} is missing perEndpoint metrics`);
    return issues;
  }
  if (Number.isFinite(artifact.ok) && Number.isFinite(artifact.failed) && Number.isFinite(artifact.samples)) {
    const total = artifact.ok + artifact.failed;
    if (total !== artifact.samples) {
      issues.push(`${label} ok + failed must equal samples`);
    }
  }
  if (Array.isArray(artifact.endpoints)) {
    const expectedEndpoints = artifact.endpoints.map((endpoint) => `${endpoint?.method} ${endpoint?.path}`);
    const duplicateEndpointDefinitions = duplicates(expectedEndpoints);
    for (const endpoint of duplicateEndpointDefinitions) {
      issues.push(`${label} duplicate endpoint definition ${endpoint}`);
    }
    const actualEndpoints = new Set(Object.keys(artifact.perEndpoint));
    for (const endpoint of expectedEndpoints) {
      if (!actualEndpoints.has(endpoint)) {
        issues.push(`${label} is missing perEndpoint metrics for ${endpoint}`);
      }
    }
    for (const endpoint of requiredAuthenticatedPerformanceEndpoints) {
      if (!expectedEndpoints.includes(endpoint)) {
        issues.push(`${label} missing required endpoint ${endpoint}`);
      }
    }
    for (const endpoint of expectedEndpoints) {
      if (!requiredAuthenticatedPerformanceEndpoints.includes(endpoint)) {
        issues.push(`${label} unknown endpoint definition ${endpoint}`);
      }
    }
  }
  const duplicateMetricNames = duplicates(Object.keys(artifact.perEndpoint));
  for (const endpoint of duplicateMetricNames) {
    issues.push(`${label} duplicate perEndpoint metrics ${endpoint}`);
  }
  for (const endpoint of requiredAuthenticatedPerformanceEndpoints) {
    if (!Object.prototype.hasOwnProperty.call(artifact.perEndpoint, endpoint)) {
      issues.push(`${label} missing required perEndpoint metrics ${endpoint}`);
    }
  }
  let endpointSampleTotal = 0;
  for (const [endpoint, metrics] of Object.entries(artifact.perEndpoint)) {
    if (!requiredAuthenticatedPerformanceEndpoints.includes(endpoint)) {
      issues.push(`${label} unknown perEndpoint metrics ${endpoint}`);
    }
    if (!metrics || typeof metrics !== "object") {
      issues.push(`${label} ${endpoint} metrics must be an object`);
      continue;
    }
    if (!Number.isFinite(metrics.samples) || metrics.samples <= 0) {
      issues.push(`${label} ${endpoint} is missing positive samples`);
    }
    if (!Number.isFinite(metrics.p95) || metrics.p95 <= 0) {
      issues.push(`${label} ${endpoint} is missing positive p95`);
    }
    if (Number.isFinite(metrics.p50) && Number.isFinite(metrics.p95) && metrics.p50 > metrics.p95) {
      issues.push(`${label} ${endpoint} p50 must be <= p95`);
    }
    if (Number.isFinite(metrics.p99) && Number.isFinite(metrics.p95) && metrics.p95 > metrics.p99) {
      issues.push(`${label} ${endpoint} p95 must be <= p99`);
    }
    if (Number.isFinite(metrics.samples) && metrics.statusCounts && typeof metrics.statusCounts === "object") {
      const statusTotal = Object.values(metrics.statusCounts)
        .reduce((sum, count) => sum + (Number.isFinite(count) ? count : 0), 0);
      if (statusTotal !== metrics.samples) {
        issues.push(`${label} ${endpoint} statusCounts total must equal samples`);
      }
      if ((artifact.failed || 0) === 0 && Object.keys(metrics.statusCounts).some((status) => status !== "200")) {
        issues.push(`${label} ${endpoint} has non-200 statusCounts despite failed=0`);
      }
    }
    if (Number.isFinite(metrics.samples)) {
      endpointSampleTotal += metrics.samples;
    }
  }
  if (Number.isFinite(artifact.samples) && endpointSampleTotal > 0 && endpointSampleTotal !== artifact.samples) {
    issues.push(`${label} perEndpoint sample total must equal samples`);
  }
  if (Array.isArray(artifact.oneShots)) {
    const oneShotNames = artifact.oneShots.map((oneShot) => oneShot?.name);
    if (!oneShotNames.includes("POST /api/v2/auth/session/keepalive")) {
      issues.push(`${label} missing keepalive oneShot timing`);
    }
    for (const oneShot of artifact.oneShots) {
      if (!oneShot?.name || oneShot.status !== 200 || !Number.isFinite(oneShot.elapsedMs) || oneShot.elapsedMs <= 0) {
        issues.push(`${label} oneShots must contain successful timing evidence`);
        break;
      }
    }
  } else {
    issues.push(`${label} missing keepalive oneShot timing`);
  }
  return issues;
}

export function validateAuthenticatedPerformanceBaselineMetadata(baseline, { strict = false } = {}) {
  if (!strict) {
    return [];
  }
  const issues = [];
  if (baseline?.baselineType !== "authenticated-runtime") {
    issues.push("strict release baseline requires baselineType=authenticated-runtime");
  }
  if (!isIsoTimestamp(baseline?.acceptedAt)) {
    issues.push("acceptedAt must be an ISO timestamp");
  }
  for (const [label, value] of Object.entries({
    acceptedBy: baseline?.acceptedBy,
    sourceEnvironment: baseline?.sourceEnvironment,
    sourceArtifact: baseline?.sourceArtifact,
    releaseCandidate: baseline?.releaseCandidate,
    evidenceOperator: baseline?.evidenceOperator,
  })) {
    const issue = evidenceValueIssue(value);
    if (issue) {
      issues.push(`${label} ${issue}`);
    }
  }
  if (!/^[a-f0-9]{64}$/i.test(baseline?.sourceSha256 || "")) {
    issues.push("sourceSha256 must be a SHA-256 hex digest");
  }
  return issues;
}

export function compareAuthenticatedPerformance(actual, baseline, { maxRegressionRatio = 0.10 } = {}) {
  const issues = [];
  recordRegression(issues, "authenticated-performance-regression", actual?.p95, baseline?.p95, "p95", maxRegressionRatio);
  recordRegression(issues, "authenticated-performance-upload-regression", actual?.upload?.elapsedMs, baseline?.upload?.elapsedMs, "upload elapsed", maxRegressionRatio);

  const actualEndpoints = actual?.perEndpoint || {};
  const baselineEndpoints = baseline?.perEndpoint || {};
  for (const [endpoint, expected] of Object.entries(baselineEndpoints)) {
    if (!actualEndpoints[endpoint]) {
      issues.push({
        name: `authenticated-performance-regression ${endpoint}`,
        detail: "actual artifact is missing endpoint present in baseline",
      });
      continue;
    }
    recordRegression(
      issues,
      `authenticated-performance-regression ${endpoint}`,
      actualEndpoints[endpoint]?.p95,
      expected?.p95,
      "p95",
      maxRegressionRatio,
    );
  }
  return issues;
}

function recordRegression(issues, name, actual, baseline, metric, maxRegressionRatio) {
  if (!Number.isFinite(actual) || !Number.isFinite(baseline) || baseline <= 0) {
    return;
  }
  const limit = baseline * (1 + maxRegressionRatio);
  if (actual > limit) {
    issues.push({
      name,
      detail: `${metric} ${actual}ms exceeds baseline ${baseline}ms by more than ${Math.round(maxRegressionRatio * 100)}%`,
    });
  }
}

function duplicates(values) {
  const seen = new Set();
  const duplicated = new Set();
  for (const value of values) {
    if (seen.has(value)) {
      duplicated.add(value);
    }
    seen.add(value);
  }
  return [...duplicated];
}
