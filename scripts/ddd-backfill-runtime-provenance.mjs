#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import {
  buildProductionEquivalenceEvidence,
  collectProvenanceIssues,
} from "./ddd-release-evidence-utils.mjs";
import { buildFrontendSmokeBlockers } from "./ddd-frontend-smoke-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const artifactRoot = process.env.DDD_RELEASE_EVIDENCE_DIR
  ? path.resolve(process.env.DDD_RELEASE_EVIDENCE_DIR)
  : path.join(repoRoot, "artifacts", "ddd");
const reportFile = process.env.DDD_RUNTIME_PROVENANCE_BACKFILL_REPORT
  ? path.resolve(process.env.DDD_RUNTIME_PROVENANCE_BACKFILL_REPORT)
  : path.join(artifactRoot, "release", "runtime-provenance-backfill.json");

const defaultArtifacts = [
  "readiness/summary.json",
  "performance/authenticated-runtime-actual.json",
  "file/file-processing-e2e.json",
  "payment/payment-webhook-e2e.json",
  "jobs/job-e2e-smoke.json",
  "ai/ai-runtime-drill.json",
  "frontend/frontend-smoke.json",
];

const requestedArtifacts = process.env.DDD_RUNTIME_PROVENANCE_BACKFILL_ARTIFACTS
  ? process.env.DDD_RUNTIME_PROVENANCE_BACKFILL_ARTIFACTS
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean)
  : defaultArtifacts;

const sourceEnvironment = process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";
const provenanceEnabled = process.env.DDD_RUNTIME_PROVENANCE_BACKFILL === "true";
const productionEquivalenceEnabled = process.env.DDD_RUNTIME_PRODUCTION_EQUIVALENCE_BACKFILL === "true";
const allowOverwrite = process.env.DDD_RUNTIME_PROVENANCE_BACKFILL_OVERWRITE === "true";
const allowProductionEquivalenceOverwrite = process.env.DDD_RUNTIME_PRODUCTION_EQUIVALENCE_BACKFILL_OVERWRITE === "true";
const backfilledAt = new Date().toISOString();
const backfillReason = "operator supplied metadata for existing local evidence artifact; result fields were not changed";

const evidenceNames = new Map([
  ["readiness/summary.json", "runtime readiness"],
  ["performance/authenticated-runtime-actual.json", "authenticated performance actual"],
  ["file/file-processing-e2e.json", "file processing E2E"],
  ["payment/payment-webhook-e2e.json", "payment webhook E2E"],
  ["jobs/job-e2e-smoke.json", "job E2E"],
  ["ai/ai-runtime-drill.json", "AI runtime drill"],
  ["frontend/frontend-smoke.json", "frontend smoke"],
]);

function portablePath(value) {
  if (!value) {
    return value;
  }
  const absolute = path.resolve(value);
  if (absolute === artifactRoot) {
    return ".";
  }
  if (absolute.startsWith(`${artifactRoot}${path.sep}`)) {
    return path.relative(artifactRoot, absolute) || ".";
  }
  if (absolute === repoRoot) {
    return ".";
  }
  if (absolute.startsWith(`${repoRoot}${path.sep}`)) {
    return path.relative(repoRoot, absolute) || ".";
  }
  const homeDir = process.env.HOME ? path.resolve(process.env.HOME) : "";
  if (homeDir && absolute === homeDir) {
    return "~";
  }
  if (homeDir && absolute.startsWith(`${homeDir}${path.sep}`)) {
    return `~/${path.relative(homeDir, absolute)}`;
  }
  return value;
}

function writeReport(report) {
  fs.mkdirSync(path.dirname(reportFile), { recursive: true });
  fs.writeFileSync(reportFile, `${JSON.stringify(report, null, 2)}\n`);
}

function readArtifact(relativePath) {
  const file = path.join(artifactRoot, relativePath);
  if (!fs.existsSync(file)) {
    return { relativePath, file, status: "MISSING", blocker: `missing artifact ${relativePath}` };
  }
  try {
    return {
      relativePath,
      file,
      status: "PRESENT",
      data: JSON.parse(fs.readFileSync(file, "utf8")),
    };
  } catch (error) {
    return {
      relativePath,
      file,
      status: "INVALID_JSON",
      blocker: `invalid JSON artifact ${relativePath}: ${error.message}`,
    };
  }
}

function conflictingProvenance(data, provenance) {
  return Object.entries(provenance)
    .filter(([field, value]) => {
      const existing = data?.[field];
      return existing !== undefined && existing !== null && String(existing).trim() !== "" && existing !== value;
    })
    .map(([field, value]) => `${field} already set to ${JSON.stringify(data[field])}, refusing overwrite with ${JSON.stringify(value)}`);
}

function productionEquivalenceFor(artifact) {
  return buildProductionEquivalenceEvidence({
    strict: true,
    baseUrl: artifact.data?.baseUrl,
    deploymentEvidence: process.env.DDD_RUNTIME_PRODUCTION_EQUIVALENCE_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || "",
    evidenceName: evidenceNames.get(artifact.relativePath) || "runtime evidence",
  });
}

function refreshDerivedFields(relativePath, data) {
  if (relativePath !== "frontend/frontend-smoke.json") {
    return data;
  }
  const strict = data?.productionEquivalence?.strict === true || data?.expectDeployed === true;
  const blockers = buildFrontendSmokeBlockers(data, { strict });
  return {
    ...data,
    blockers,
    status: blockers.length === 0 ? "PASS" : "FAIL",
  };
}

const blockers = [];
if (!provenanceEnabled && !productionEquivalenceEnabled) {
  blockers.push("DDD_RUNTIME_PROVENANCE_BACKFILL=true or DDD_RUNTIME_PRODUCTION_EQUIVALENCE_BACKFILL=true is required");
}
if (provenanceEnabled) {
  for (const issue of collectProvenanceIssues({ sourceEnvironment, releaseCandidate, evidenceOperator })) {
    blockers.push(`runtime provenance ${issue}`);
  }
}

const provenance = { sourceEnvironment, releaseCandidate, evidenceOperator };
const artifacts = requestedArtifacts.map(readArtifact);
for (const artifact of artifacts) {
  if (artifact.blocker) {
    blockers.push(artifact.blocker);
  }
  if (provenanceEnabled && artifact.data && !allowOverwrite) {
    for (const issue of conflictingProvenance(artifact.data, provenance)) {
      blockers.push(`${artifact.relativePath}: ${issue}`);
    }
  }
}

if (blockers.length === 0) {
  for (const artifact of artifacts) {
    const next = {
      ...artifact.data,
      provenanceBackfilledAt: backfilledAt,
      provenanceBackfillReason: backfillReason,
    };
    if (provenanceEnabled) {
      Object.assign(next, provenance);
    }
    if (productionEquivalenceEnabled
      && (allowProductionEquivalenceOverwrite || !next.productionEquivalence)) {
      next.productionEquivalence = productionEquivalenceFor(artifact);
      next.productionEquivalenceBackfilledAt = backfilledAt;
      next.productionEquivalenceBackfillReason = "baseUrl-derived production equivalence metadata; result fields were not changed";
    }
    const refreshed = refreshDerivedFields(artifact.relativePath, next);
    fs.writeFileSync(artifact.file, `${JSON.stringify(refreshed, null, 2)}\n`);
    artifact.status = "BACKFILLED";
    artifact.provenanceBackfilled = provenanceEnabled;
    artifact.productionEquivalenceBackfilled = Boolean(productionEquivalenceEnabled
      && (allowProductionEquivalenceOverwrite || !artifact.data.productionEquivalence));
  }
}

const report = {
  generatedAt: backfilledAt,
  sourceEnvironment: sourceEnvironment || null,
  releaseCandidate: releaseCandidate || null,
  evidenceOperator: evidenceOperator || null,
  artifactRoot: portablePath(artifactRoot),
  status: blockers.length === 0 ? "PASS" : "FAIL",
  summary: {
    requestedArtifacts: requestedArtifacts.length,
    backfilledArtifacts: blockers.length === 0 ? artifacts.length : 0,
    provenanceBackfilledArtifacts: blockers.length === 0 && provenanceEnabled ? artifacts.length : 0,
    productionEquivalenceBackfilledArtifacts: blockers.length === 0
      ? artifacts.filter((artifact) => artifact.productionEquivalenceBackfilled).length
      : 0,
    blockers: blockers.length,
    allowOverwrite,
    allowProductionEquivalenceOverwrite,
  },
  artifacts: artifacts.map(({ relativePath, file, status }) => ({
    relativePath,
    file: portablePath(file),
    status,
    provenanceBackfilled: status === "BACKFILLED" ? provenanceEnabled : false,
    productionEquivalenceBackfilled: status === "BACKFILLED"
      ? Boolean(artifacts.find((artifact) => artifact.relativePath === relativePath)?.productionEquivalenceBackfilled)
      : false,
  })),
  blockers,
};

writeReport(report);

if (blockers.length > 0) {
  for (const blocker of blockers) {
    console.error(`[ddd-backfill-runtime-provenance] ${blocker}`);
  }
  console.error(`[ddd-backfill-runtime-provenance] wrote report to ${reportFile}`);
  process.exit(1);
}

console.log(`[ddd-backfill-runtime-provenance] backfilled ${artifacts.length} artifacts; report=${reportFile}`);
