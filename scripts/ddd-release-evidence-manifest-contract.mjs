import { collectProvenanceIssues } from "./ddd-release-evidence-utils.mjs";

export const requiredManifestArtifacts = [
  "build/backend-build-evidence.json",
  "build/docker-image-evidence.json",
  "tests/backend-test-evidence.json",
  "frontend/frontend-build-evidence.json",
  "frontend/frontend-static-evidence.json",
  "frontend/frontend-smoke.json",
  "migration/migration-evidence.json",
  "release/release-env-lint.json",
  "config/release-config-evidence.json",
  "readiness/summary.json",
  "performance/authenticated-runtime-actual.json",
  "performance/authenticated-runtime-baseline.json",
  "file/file-processing-e2e.json",
  "payment/payment-webhook-e2e.json",
  "outbox/outbox-replay-dead-letter-test-evidence.json",
  "jobs/job-e2e-smoke.json",
  "ai/ai-runtime-drill.json",
  "split/physical-split-readiness.json",
  "rollback/rollback-drill.json",
];

export const optionalManifestArtifacts = [
  "release/release-final-owner-queue-run-report.json",
  "release/release-next-action-run-report.json",
  "release/release-execution-run-report.json",
  "release/explain-gate-report.json",
  "release/release-unblock-brief.json",
  "release/release-artifact-path-leak-contract.json",
  "release/release-env-owner-handoff-redacted-contract.json",
  "release/evidence-manifest-preflight.json",
  "release/release-config-owner-input-reconciliation.json",
  "release/release-owner-input-receipt.json",
  "release/release-owner-input-receipt.csv",
  "release/release-owner-input-receipt-items.csv",
  "release/release-owner-input-receipt-items.md",
];

export const provenanceManifestArtifacts = new Set([
  "build/backend-build-evidence.json",
  "build/docker-image-evidence.json",
  "tests/backend-test-evidence.json",
  "frontend/frontend-build-evidence.json",
  "frontend/frontend-static-evidence.json",
  "frontend/frontend-smoke.json",
  "release/release-env-lint.json",
  "config/release-config-evidence.json",
  "readiness/summary.json",
  "performance/authenticated-runtime-actual.json",
  "file/file-processing-e2e.json",
  "payment/payment-webhook-e2e.json",
  "outbox/outbox-replay-dead-letter-test-evidence.json",
  "jobs/job-e2e-smoke.json",
  "ai/ai-runtime-drill.json",
]);

const optionalManifestArtifactSet = new Set(optionalManifestArtifacts);
const localDiagnosticProvenanceValues = {
  sourceEnvironment: new Set([
    "dev",
    "development",
    "local",
    "local-dev",
    "local-development",
    "local-test",
    "test",
    "testing",
  ]),
  releaseCandidate: new Set([
    "dev",
    "local",
    "local-worktree",
    "local-dev",
    "local-test",
    "test",
    "testing",
  ]),
  evidenceOperator: new Set([
    "local",
    "local-operator",
    "local-user",
    "test",
    "tester",
    "unknown",
  ]),
};

export function collectManifestProvenanceIssues(provenance = {}) {
  const issues = collectProvenanceIssues(provenance);
  for (const [field, value] of Object.entries(provenance)) {
    const normalized = String(value || "").trim().toLowerCase();
    if (!normalized || !localDiagnosticProvenanceValues[field]?.has(normalized)) {
      continue;
    }
    if (field === "sourceEnvironment") {
      issues.push("sourceEnvironment must identify a production-equivalent release environment");
    } else if (field === "releaseCandidate") {
      issues.push("releaseCandidate must identify a release version, commit, or build candidate");
    } else if (field === "evidenceOperator") {
      issues.push("evidenceOperator must identify a real release operator");
    }
  }
  return issues;
}

export function validateManifestArtifact(manifest, { strict = false } = {}) {
  const issues = [];
  if (!manifest || typeof manifest !== "object") {
    return ["manifest must be a JSON object"];
  }

  if (strict) {
    for (const issue of collectManifestProvenanceIssues({
      sourceEnvironment: manifest.sourceEnvironment,
      releaseCandidate: manifest.releaseCandidate,
      evidenceOperator: manifest.evidenceOperator,
    })) {
      issues.push(`manifest provenance ${issue}`);
    }
  }

  const artifactReports = Array.isArray(manifest.artifacts) ? manifest.artifacts : [];
  const reportByPath = new Map(artifactReports.map((artifact) => [artifact.relativePath, artifact]));
  const artifactPathCounts = countBy(artifactReports.map((artifact) => artifact.relativePath).filter(Boolean));
  const requiredArtifactSet = new Set(requiredManifestArtifacts);
  const allowedArtifactSet = new Set([...requiredManifestArtifacts, ...optionalManifestArtifacts]);
  const manifestBlockers = Array.isArray(manifest.blockers) ? manifest.blockers : [];
  const expectedBlockers = expectedManifestBlockers(manifest, { strict });
  for (const [relativePath, count] of artifactPathCounts.entries()) {
    if (count > 1) {
      issues.push(`duplicate manifest artifact report ${relativePath}`);
    }
    if (!allowedArtifactSet.has(relativePath)) {
      issues.push(`unknown manifest artifact report ${relativePath}`);
    }
  }
  for (const relativePath of requiredManifestArtifacts) {
    const artifact = reportByPath.get(relativePath);
    if (!artifact) {
      issues.push(`missing manifest report for ${relativePath}`);
    } else if (!artifact.present) {
      issues.push(`missing artifact ${relativePath}`);
    } else if (artifact.status === "INVALID_JSON") {
      issues.push(`invalid JSON artifact ${relativePath}: ${artifact.parseError || "unknown parse error"}`);
    }
  }
  for (const artifact of artifactReports) {
    if (!artifact?.relativePath) {
      issues.push("manifest artifact report missing relativePath");
      continue;
    }
    if (!artifact.present) {
      continue;
    }
    if (!Number.isFinite(Number(artifact.bytes)) || Number(artifact.bytes) <= 0) {
      issues.push(`manifest artifact ${artifact.relativePath} bytes must be positive`);
    }
    if (!/^[a-f0-9]{64}$/i.test(String(artifact.sha256 || ""))) {
      issues.push(`manifest artifact ${artifact.relativePath} sha256 must be 64 hex characters`);
    }
    if (!artifact.timestamp || typeof artifact.timestamp !== "object") {
      issues.push(`manifest artifact ${artifact.relativePath} timestamp is required`);
    } else {
      if (!artifact.timestamp.field) {
        issues.push(`manifest artifact ${artifact.relativePath} timestamp.field is required`);
      }
      if (!artifact.timestamp.value) {
        issues.push(`manifest artifact ${artifact.relativePath} timestamp.value is required`);
      } else if (Number.isNaN(Date.parse(artifact.timestamp.value))) {
        issues.push(`manifest artifact ${artifact.relativePath} timestamp.value must be ISO-like datetime`);
      }
    }
    if (optionalManifestArtifactSet.has(artifact.relativePath)) {
      for (const issue of artifact.contractIssues || []) {
        issues.push(`optional manifest artifact ${artifact.relativePath}: ${issue}`);
      }
    }
  }

  const summary = manifest.summary || {};
  const actualPresentArtifacts = artifactReports.filter((artifact) => artifact.present).length;
  const actualOptionalArtifacts = artifactReports.filter((artifact) => optionalManifestArtifactSet.has(artifact.relativePath)).length;
  const actualInvalidJsonArtifacts = artifactReports.filter((artifact) => artifact.status === "INVALID_JSON").length;
  const actualProvenanceIssueArtifacts = artifactReports
    .filter((artifact) => (artifact.provenanceIssues || []).length > 0).length;
  const actualExplainFiles = Array.isArray(manifest.explain?.files) ? manifest.explain.files.length : 0;
  const declaredBlockers = Number(summary.blockers || 0);
  const expectedStatus = manifestBlockers.length === 0 ? "PASS" : "FAIL";

  if (Number(summary.requiredArtifacts || 0) !== requiredManifestArtifacts.length) {
    issues.push(`manifest summary requiredArtifacts mismatch: declared=${summary.requiredArtifacts || 0}, actual=${requiredManifestArtifacts.length}`);
  }
  if (Number(summary.presentArtifacts || 0) !== actualPresentArtifacts) {
    issues.push(`manifest summary presentArtifacts mismatch: declared=${summary.presentArtifacts || 0}, actual=${actualPresentArtifacts}`);
  }
  if (Number(summary.optionalArtifacts || 0) !== actualOptionalArtifacts) {
    issues.push(`manifest summary optionalArtifacts mismatch: declared=${summary.optionalArtifacts || 0}, actual=${actualOptionalArtifacts}`);
  }
  if (Number(summary.invalidJsonArtifacts || 0) !== actualInvalidJsonArtifacts) {
    issues.push(`manifest summary invalidJsonArtifacts mismatch: declared=${summary.invalidJsonArtifacts || 0}, actual=${actualInvalidJsonArtifacts}`);
  }
  if (Number(summary.provenanceIssueArtifacts || 0) !== actualProvenanceIssueArtifacts) {
    issues.push(`manifest summary provenanceIssueArtifacts mismatch: declared=${summary.provenanceIssueArtifacts || 0}, actual=${actualProvenanceIssueArtifacts}`);
  }
  if (Number(summary.explainFiles || 0) !== actualExplainFiles) {
    issues.push(`manifest summary explainFiles mismatch: declared=${summary.explainFiles || 0}, actual=${actualExplainFiles}`);
  }
  if (declaredBlockers !== manifestBlockers.length) {
    issues.push(`manifest summary blockers mismatch: declared=${declaredBlockers}, actual=${manifestBlockers.length}`);
  }
  compareStringArrays("manifest blockers", manifestBlockers, expectedBlockers, issues);

  if (manifest.status !== expectedStatus) {
    issues.push(`manifest status must be ${expectedStatus}, got ${manifest.status}`);
  }
  if (manifest.status !== "PASS") {
    issues.push(`status=${manifest.status}, blockers=${declaredBlockers}`);
  }
  if ((summary.invalidJsonArtifacts || 0) > 0) {
    issues.push(`invalid JSON artifacts=${summary.invalidJsonArtifacts}`);
  }
  if ((summary.provenanceIssueArtifacts || 0) > 0) {
    issues.push(`artifact provenance issues=${summary.provenanceIssueArtifacts}`);
  }
  if ((summary.explainFiles || 0) === 0) {
    issues.push("missing EXPLAIN files in evidence manifest");
  }

  return issues;
}

function expectedManifestBlockers(manifest, { strict = false } = {}) {
  const blockers = [];
  if (strict) {
    for (const issue of collectManifestProvenanceIssues({
      sourceEnvironment: manifest.sourceEnvironment,
      releaseCandidate: manifest.releaseCandidate,
      evidenceOperator: manifest.evidenceOperator,
    })) {
      blockers.push(`manifest provenance ${issue}`);
    }
  }
  const artifactReports = Array.isArray(manifest.artifacts) ? manifest.artifacts : [];
  const reportByPath = new Map(artifactReports.map((artifact) => [artifact.relativePath, artifact]));
  for (const relativePath of requiredManifestArtifacts) {
    const artifact = reportByPath.get(relativePath);
    if (!artifact || !artifact.present) {
      blockers.push(`missing artifact ${relativePath}`);
      continue;
    }
    if (artifact.status === "INVALID_JSON") {
      blockers.push(`invalid JSON artifact ${relativePath}: ${artifact.parseError || "unknown parse error"}`);
    }
    if (strict && Array.isArray(artifact.provenanceIssues)) {
      for (const issue of artifact.provenanceIssues) {
        blockers.push(`artifact provenance ${relativePath}: ${issue}`);
      }
    }
  }
  for (const artifact of artifactReports) {
    if (!optionalManifestArtifactSet.has(artifact?.relativePath) || !artifact.present) {
      continue;
    }
    if (artifact.status === "INVALID_JSON") {
      blockers.push(`invalid JSON artifact ${artifact.relativePath}: ${artifact.parseError || "unknown parse error"}`);
      continue;
    }
    if (optionalManifestArtifactSet.has(artifact.relativePath)) {
      for (const issue of artifact.contractIssues || []) {
        blockers.push(`optional artifact ${artifact.relativePath}: ${issue}`);
      }
    }
  }
  if (manifest.explain?.present !== true) {
    blockers.push(`missing explain directory ${manifest.explain?.directory || "missing"}`);
  } else if (!Array.isArray(manifest.explain.files) || manifest.explain.files.length === 0) {
    blockers.push(`no explain JSON files in ${manifest.explain.directory || "missing"}`);
  }
  return blockers;
}

function compareStringArrays(label, declared, expected, issues) {
  if (declared.length !== expected.length) {
    issues.push(`${label} length mismatch: declared=${declared.length}, actual=${expected.length}`);
    return;
  }
  for (let index = 0; index < expected.length; index += 1) {
    if (declared[index] !== expected[index]) {
      issues.push(`${label}[${index}] mismatch: declared=${declared[index] ?? "missing"}, actual=${expected[index]}`);
    }
  }
}

function countBy(items) {
  const counts = new Map();
  for (const item of items) {
    counts.set(item, (counts.get(item) || 0) + 1);
  }
  return counts;
}
