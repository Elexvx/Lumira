#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { createHash } from "node:crypto";
import { validateExplainGateReport } from "./ddd-explain-gate-report-contract.mjs";
import { validateFinalOwnerQueueRunReport } from "./ddd-final-owner-queue-run-report-contract.mjs";
import { validateReleaseExecutionRunReport } from "./ddd-release-execution-run-report-contract.mjs";
import { validateReleaseNextActionRunReport } from "./ddd-release-next-action-run-report-contract.mjs";
import { validateReleaseUnblockBrief } from "./ddd-release-unblock-brief-contract.mjs";
import { validateReleaseOwnerInputReceipt } from "./ddd-release-owner-input-receipt-contract.mjs";
import { collectProvenanceIssues } from "./ddd-release-evidence-utils.mjs";
import {
  collectManifestProvenanceIssues,
  optionalManifestArtifacts,
  provenanceManifestArtifacts,
  requiredManifestArtifacts,
} from "./ddd-release-evidence-manifest-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const artifactRoot = process.env.DDD_RELEASE_EVIDENCE_DIR
  ? path.resolve(process.env.DDD_RELEASE_EVIDENCE_DIR)
  : path.join(repoRoot, "artifacts", "ddd");
const outputFile = process.env.DDD_RELEASE_MANIFEST_REPORT
  ? path.resolve(process.env.DDD_RELEASE_MANIFEST_REPORT)
  : path.join(artifactRoot, "release", "evidence-manifest.json");
const preflightReportFile = process.env.DDD_RELEASE_MANIFEST_PREFLIGHT_REPORT
  ? path.resolve(process.env.DDD_RELEASE_MANIFEST_PREFLIGHT_REPORT)
  : path.join(artifactRoot, "release", "evidence-manifest-preflight.json");
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === "true" || process.env.DDD_RELEASE_MANIFEST_STRICT === "true";
const exitOnBlockers = process.env.DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS !== "false";
const checkEnvOnly = process.env.DDD_RELEASE_MANIFEST_CHECK_ENV === "true" || process.env.DDD_RELEASE_MANIFEST_CHECK_ENV === "1";
const releaseEnvFile = process.env.DDD_RELEASE_ENV_FILE ? path.resolve(process.env.DDD_RELEASE_ENV_FILE) : "";
const releaseEnvEntries = readReleaseEnvEntries(releaseEnvFile);
const sourceEnvironment = process.env.DDD_RELEASE_MANIFEST_ENVIRONMENT
  || process.env.DDD_EVIDENCE_ENVIRONMENT
  || process.env.DDD_RELEASE_ENVIRONMENT
  || releaseEnvEntries.DDD_RELEASE_MANIFEST_ENVIRONMENT
  || releaseEnvEntries.DDD_EVIDENCE_ENVIRONMENT
  || releaseEnvEntries.DDD_RELEASE_ENVIRONMENT
  || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || releaseEnvEntries.DDD_RELEASE_CANDIDATE || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || releaseEnvEntries.DDD_EVIDENCE_OPERATOR || "";

function unquoteEnvValue(value) {
  const text = String(value || "").trim();
  if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
    return text.slice(1, -1);
  }
  return text;
}

function readReleaseEnvEntries(file) {
  if (!file || !fs.existsSync(file)) {
    return {};
  }
  const entries = {};
  for (const rawLine of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) {
      continue;
    }
    const match = rawLine.match(/^\s*(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (!match) {
      continue;
    }
    entries[match[1]] = unquoteEnvValue(match[2]);
  }
  return entries;
}

function portablePath(filePath) {
  const absolutePath = path.resolve(filePath);
  return absolutePath === repoRoot || absolutePath.startsWith(`${repoRoot}${path.sep}`)
    ? path.relative(repoRoot, absolutePath) || "."
    : filePath;
}

function sha256(file) {
  return createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function timestampOf(data, stat) {
  if (data && typeof data === "object") {
    for (const field of ["generatedAt", "checkedAt", "finishedAt", "startedAt", "acceptedAt"]) {
      if (data[field]) {
        return { field, value: data[field] };
      }
    }
  }
  return { field: "mtime", value: stat.mtime.toISOString() };
}

function withoutGeneratedAt(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return value;
  }
  const { generatedAt, ...rest } = value;
  return rest;
}

function stableGeneratedAt(outputPath, body) {
  if (!fs.existsSync(outputPath)) {
    return new Date().toISOString();
  }
  try {
    const existing = JSON.parse(fs.readFileSync(outputPath, "utf8"));
    if (JSON.stringify(withoutGeneratedAt(existing)) === JSON.stringify(body)) {
      return existing.generatedAt || new Date().toISOString();
    }
  } catch {
    // Regenerate the timestamp when the previous report is unreadable.
  }
  return new Date().toISOString();
}

function inspectArtifact(relativePath) {
  const file = path.join(artifactRoot, relativePath);
  if (!fs.existsSync(file)) {
    return {
      relativePath,
      present: false,
      status: "MISSING",
    };
  }
  const stat = fs.statSync(file);
  let data = null;
  let parseError = null;
  if (!relativePath.endsWith(".csv") && !relativePath.endsWith(".md")) {
    try {
      data = JSON.parse(fs.readFileSync(file, "utf8"));
    } catch (error) {
      parseError = error.message;
    }
  }
  const provenanceIssues = !parseError && provenanceManifestArtifacts.has(relativePath)
    ? collectProvenanceIssues({
        sourceEnvironment: data?.sourceEnvironment,
        releaseCandidate: data?.releaseCandidate,
        evidenceOperator: data?.evidenceOperator,
      })
    : [];
  const contractIssues = !parseError && relativePath === "release/release-final-owner-queue-run-report.json"
    ? validateFinalOwnerQueueRunReport(data)
    : !parseError && relativePath === "release/release-next-action-run-report.json"
      ? validateReleaseNextActionRunReport(data)
      : !parseError && relativePath === "release/release-execution-run-report.json"
        ? validateReleaseExecutionRunReport(data)
        : !parseError && relativePath === "release/explain-gate-report.json"
          ? validateExplainGateReport(data)
            : !parseError && relativePath === "release/release-unblock-brief.json"
              ? validateReleaseUnblockBrief(data, fs.existsSync(path.join(artifactRoot, "release", "release-unblock-brief.md"))
                ? fs.readFileSync(path.join(artifactRoot, "release", "release-unblock-brief.md"), "utf8")
                : "")
              : !parseError && relativePath === "release/release-artifact-path-leak-contract.json"
                ? validatePathLeakReport(data, artifactRoot)
                : !parseError && relativePath === "release/release-env-owner-handoff-redacted-contract.json"
                  ? validateEnvOwnerHandoffRedactedContractReport(data)
                  : !parseError && relativePath === "release/release-config-owner-input-reconciliation.json"
                    ? validateConfigOwnerInputReconciliationReport(data)
                    : !parseError && relativePath === "release/release-owner-input-receipt.json"
                      ? validateReleaseOwnerInputReceipt(data)
                      : !parseError && relativePath === "release/evidence-manifest-preflight.json"
                        ? validateEvidenceManifestPreflightReport(data)
                        : [];
  return {
    relativePath,
    present: true,
    status: parseError ? "INVALID_JSON" : "PRESENT",
    bytes: stat.size,
    sha256: sha256(file),
    timestamp: timestampOf(data, stat),
    provenanceIssues,
    contractIssues,
    parseError,
  };
}

function explainArtifacts() {
  const explainDir = process.env.DDD_EXPLAIN_DIR
    ? path.resolve(process.env.DDD_EXPLAIN_DIR)
    : path.join(repoRoot, "tmp", "ddd-explain");
  if (!fs.existsSync(explainDir)) {
    return {
      directory: portablePath(explainDir),
      present: false,
      files: [],
    };
  }
  const files = fs.readdirSync(explainDir)
    .filter((file) => file.endsWith(".json"))
    .sort()
    .map((file) => {
      const fullPath = path.join(explainDir, file);
      const stat = fs.statSync(fullPath);
      return {
        relativePath: path.relative(repoRoot, fullPath),
        bytes: stat.size,
        sha256: sha256(fullPath),
        mtime: stat.mtime.toISOString(),
      };
    });
  return {
    directory: portablePath(explainDir),
    present: true,
    files,
  };
}

function validatePathLeakReport(data, artifactRoot) {
  const issues = [];
  if (!data || typeof data !== "object") {
    return ["path leak report must be a JSON object"];
  }
  if (data.status !== "PASS") {
    issues.push(`path leak report status must be PASS, got ${data.status || "missing"}`);
  }
  if (data.leakCount !== 0) {
    issues.push(`path leak report leakCount must be 0, got ${data.leakCount ?? "missing"}`);
  }
  if (!Number.isInteger(data.scannedFiles) || data.scannedFiles <= 0) {
    issues.push("path leak report scannedFiles must be a positive integer");
  }
  if (!Array.isArray(data.releaseEnvDisplayScanned)) {
    issues.push("path leak report releaseEnvDisplayScanned must be an array");
  } else if (data.releaseEnvDisplayScannedFiles !== data.releaseEnvDisplayScanned.length) {
    issues.push(`path leak report releaseEnvDisplayScannedFiles mismatch: declared=${data.releaseEnvDisplayScannedFiles ?? "missing"}, actual=${data.releaseEnvDisplayScanned.length}`);
  }
  const ownerPacketDir = path.join(artifactRoot, "release", "release-env-owner-input-packet");
  if (fs.existsSync(ownerPacketDir) && fs.statSync(ownerPacketDir).isDirectory()) {
    const expectedOwnerPacketFiles = fs.readdirSync(ownerPacketDir)
      .filter((file) => file.endsWith(".json") || file.endsWith(".md"))
      .sort()
      .map((file) => `release/release-env-owner-input-packet/${file}`);
    const scannedFiles = new Set((data.releaseEnvDisplayScanned || []).map((entry) => String(entry?.file || "").replaceAll("\\", "/")));
    for (const expectedFile of expectedOwnerPacketFiles) {
      if (![...scannedFiles].some((file) => file.endsWith(expectedFile))) {
        issues.push(`path leak report missing owner input packet scan: ${expectedFile}`);
      }
    }
  }
  return issues;
}

function validateEnvOwnerHandoffRedactedContractReport(data) {
  const issues = [];
  if (!data || typeof data !== "object") {
    return ["redacted owner handoff contract report must be a JSON object"];
  }
  if (data.status !== "PASS") {
    issues.push(`redacted owner handoff contract status must be PASS, got ${data.status || "missing"}`);
  }
  if (data.redacted !== true) {
    issues.push("redacted owner handoff contract report must set redacted=true");
  }
  if (data.issueCount !== 0) {
    issues.push(`redacted owner handoff contract issueCount must be 0, got ${data.issueCount ?? "missing"}`);
  }
  if (!Number.isInteger(data.ownerCount) || data.ownerCount <= 0) {
    issues.push("redacted owner handoff contract ownerCount must be a positive integer");
  }
  if (!Array.isArray(data.expectedMarkdownFiles) || !Array.isArray(data.actualMarkdownFiles)) {
    issues.push("redacted owner handoff contract must include expectedMarkdownFiles and actualMarkdownFiles");
  } else if (JSON.stringify([...data.expectedMarkdownFiles].sort()) !== JSON.stringify([...data.actualMarkdownFiles].sort())) {
    issues.push("redacted owner handoff contract expectedMarkdownFiles must match actualMarkdownFiles");
  }
  return issues;
}

function validateConfigOwnerInputReconciliationReport(data) {
  const issues = [];
  if (!data || typeof data !== "object") {
    return ["config owner input reconciliation report must be a JSON object"];
  }
  if (data.status !== "PASS") {
    issues.push(`config owner input reconciliation status must be PASS, got ${data.status || "missing"}`);
  }
  if (data.redacted !== true) {
    issues.push("config owner input reconciliation report must set redacted=true");
  }
  if (data.contract !== "ddd-release-config-owner-input-reconciliation") {
    issues.push("config owner input reconciliation contract must be ddd-release-config-owner-input-reconciliation");
  }
  const summary = data.summary || {};
  if (Number(summary.unmappedConfigPlaceholderKeys || 0) !== 0) {
    issues.push(`config owner input reconciliation unmappedConfigPlaceholderKeys must be 0, got ${summary.unmappedConfigPlaceholderKeys ?? "missing"}`);
  }
  if (Number(summary.mappedConfigPlaceholderKeys ?? -1) !== Number(summary.uniqueConfigPlaceholderKeys ?? -2)) {
    issues.push("config owner input reconciliation mappedConfigPlaceholderKeys must equal uniqueConfigPlaceholderKeys");
  }
  if (Number(data.issueCount || 0) !== 0) {
    issues.push(`config owner input reconciliation issueCount must be 0, got ${data.issueCount ?? "missing"}`);
  }
  if (!Array.isArray(data.mappedConfigPlaceholderKeys)) {
    issues.push("config owner input reconciliation mappedConfigPlaceholderKeys must be an array");
  }
  if (!Array.isArray(data.unmappedConfigPlaceholderKeys)) {
    issues.push("config owner input reconciliation unmappedConfigPlaceholderKeys must be an array");
  } else if (data.unmappedConfigPlaceholderKeys.length !== Number(summary.unmappedConfigPlaceholderKeys || 0)) {
    issues.push("config owner input reconciliation unmappedConfigPlaceholderKeys array length must match summary");
  }
  return issues;
}

function validateEvidenceManifestPreflightReport(data) {
  const issues = [];
  if (!data || typeof data !== "object") {
    return ["evidence manifest preflight report must be a JSON object"];
  }
  if (data.status !== "PASS") {
    issues.push(`evidence manifest preflight status must be PASS, got ${data.status || "missing"}`);
  }
  if (data.redacted !== true) {
    issues.push("evidence manifest preflight report must set redacted=true");
  }
  if (!Array.isArray(data.requiredEnv) || data.requiredEnv.length !== 3) {
    issues.push("evidence manifest preflight report must include three requiredEnv entries");
  } else {
    const requiredFields = ["sourceEnvironment", "releaseCandidate", "evidenceOperator"];
    const fields = data.requiredEnv.map((entry) => entry.field);
    if (JSON.stringify(fields) !== JSON.stringify(requiredFields)) {
      issues.push(`evidence manifest preflight requiredEnv order mismatch: ${fields.join(",")}`);
    }
    for (const entry of data.requiredEnv) {
      if (!Array.isArray(entry.envKeys) || entry.envKeys.length === 0) {
        issues.push(`evidence manifest preflight ${entry.field || "unknown"} envKeys must be non-empty`);
      }
      if (typeof entry.present !== "boolean" || typeof entry.valid !== "boolean") {
        issues.push(`evidence manifest preflight ${entry.field || "unknown"} present/valid must be boolean`);
      }
    }
  }
  const summary = data.summary || {};
  const blockers = Array.isArray(data.blockers) ? data.blockers : [];
  if (!Number.isInteger(summary.blockers) || summary.blockers !== blockers.length) {
    issues.push(`evidence manifest preflight summary.blockers mismatch: declared=${summary.blockers ?? "missing"}, actual=${blockers.length}`);
  }
  if (!Number.isInteger(summary.requiredArtifacts) || summary.requiredArtifacts !== requiredManifestArtifacts.length) {
    issues.push(`evidence manifest preflight summary.requiredArtifacts mismatch: declared=${summary.requiredArtifacts ?? "missing"}, actual=${requiredManifestArtifacts.length}`);
  }
  if (!Number.isInteger(summary.missingArtifacts) || summary.missingArtifacts < 0) {
    issues.push("evidence manifest preflight summary.missingArtifacts must be a non-negative integer");
  }
  if (!Number.isInteger(summary.invalidJsonArtifacts) || summary.invalidJsonArtifacts < 0) {
    issues.push("evidence manifest preflight summary.invalidJsonArtifacts must be a non-negative integer");
  }
  if (!Number.isInteger(summary.explainFiles) || summary.explainFiles < 0) {
    issues.push("evidence manifest preflight summary.explainFiles must be a non-negative integer");
  }
  if (!Array.isArray(data.nextActions) || data.nextActions.length === 0) {
    issues.push("evidence manifest preflight must include nextActions");
  } else {
    for (const action of data.nextActions) {
      if (!action.owner || !action.command || !action.reason) {
        issues.push("evidence manifest preflight nextActions must include owner, command, and reason");
      }
      if (String(action.command || "").includes("=") && /SECRET|PASSWORD|TOKEN|KEY=.*[^<\s]/i.test(String(action.command || ""))) {
        issues.push("evidence manifest preflight nextActions command must not include concrete secret assignments");
      }
    }
  }
  return issues;
}

function provenanceEnvEntries() {
  return [
    {
      field: "sourceEnvironment",
      envKeys: ["DDD_RELEASE_MANIFEST_ENVIRONMENT", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_ENVIRONMENT"],
      value: sourceEnvironment || null,
    },
    {
      field: "releaseCandidate",
      envKeys: ["DDD_RELEASE_CANDIDATE", "GITHUB_SHA"],
      value: releaseCandidate || null,
    },
    {
      field: "evidenceOperator",
      envKeys: ["DDD_EVIDENCE_OPERATOR", "GITHUB_ACTOR"],
      value: evidenceOperator || null,
    },
  ].map((entry) => {
    const issue = collectManifestProvenanceIssues({ [entry.field]: entry.value })[0] || null;
    return {
      ...entry,
      present: Boolean(entry.value),
      valid: !issue,
      issue,
    };
  });
}

function writePreflightReport({ envBlockers, requiredArtifacts, explain }) {
  const missingArtifacts = requiredArtifacts.filter((artifact) => !artifact.present).length;
  const invalidJsonArtifacts = requiredArtifacts.filter((artifact) => artifact.status === "INVALID_JSON").length;
  const reportBody = {
    status: envBlockers.length === 0 ? "PASS" : "FAIL",
    redacted: true,
    sourceEnvironment: sourceEnvironment || null,
    releaseCandidate: releaseCandidate || null,
    evidenceOperator: evidenceOperator || null,
    artifactRoot: portablePath(artifactRoot),
    explainDirectory: explain.directory,
    requiredEnv: provenanceEnvEntries(),
    summary: {
      requiredArtifacts: requiredManifestArtifacts.length,
      presentArtifacts: requiredArtifacts.filter((artifact) => artifact.present).length,
      missingArtifacts,
      invalidJsonArtifacts,
      explainFiles: explain.files.length,
      blockers: envBlockers.length,
    },
    blockers: envBlockers,
    nextActions: envBlockers.length === 0
      ? [{
          owner: "release-owner",
          reason: "Manifest provenance, required artifacts, and EXPLAIN inputs are present; generate the checksum manifest.",
          command: "DDD_RELEASE_MANIFEST_STRICT=true node scripts/ddd-release-evidence-manifest.mjs",
        }]
      : [{
          owner: "release-owner",
          reason: "Set manifest provenance env and generate all required evidence artifacts before strict checksum manifest generation.",
          command: "DDD_EVIDENCE_ENVIRONMENT=<environment> DDD_RELEASE_CANDIDATE=<sha-or-version> DDD_EVIDENCE_OPERATOR=<operator> DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs",
        }],
  };
  const report = {
    generatedAt: stableGeneratedAt(preflightReportFile, reportBody),
    ...reportBody,
  };
  fs.mkdirSync(path.dirname(preflightReportFile), { recursive: true });
  fs.writeFileSync(preflightReportFile, `${JSON.stringify(report, null, 2)}\n`);
  return report;
}

if (checkEnvOnly) {
  const provenanceIssues = collectManifestProvenanceIssues({ sourceEnvironment, releaseCandidate, evidenceOperator });
  const envBlockers = [];
  if (provenanceIssues.length > 0) {
    for (const issue of provenanceIssues) {
      envBlockers.push(`manifest provenance ${issue}`);
    }
  }
  const requiredArtifacts = requiredManifestArtifacts.map(inspectArtifact);
  for (const artifact of requiredArtifacts) {
    if (!artifact.present) {
      envBlockers.push(`missing artifact ${artifact.relativePath}`);
    } else if (artifact.status === "INVALID_JSON") {
      envBlockers.push(`invalid JSON artifact ${artifact.relativePath}: ${artifact.parseError}`);
    }
  }
  const explain = explainArtifacts();
  if (!explain.present) {
    envBlockers.push(`missing explain directory ${explain.directory}`);
  } else if (explain.files.length === 0) {
    envBlockers.push(`no explain JSON files in ${explain.directory}`);
  }
  writePreflightReport({ envBlockers, requiredArtifacts, explain });
  if (envBlockers.length > 0) {
    for (const blocker of envBlockers) {
      const tag = blocker.startsWith("manifest provenance")
        ? "env-missing"
        : blocker.startsWith("missing artifact")
          ? "artifact-missing"
          : blocker.startsWith("invalid JSON")
            ? "artifact-invalid"
            : "explain-missing";
      console.error(`[ddd-release-evidence-manifest][${tag}] ${blocker}`);
    }
    console.error(`[ddd-release-evidence-manifest][preflight-report] ${preflightReportFile}`);
    process.exit(1);
  }
  console.log("[ddd-release-evidence-manifest][env-ok] manifest provenance env is populated");
  console.log(`[ddd-release-evidence-manifest][artifact-ok] requiredArtifacts=${requiredArtifacts.length} explainFiles=${explain.files.length}`);
  console.log(`[ddd-release-evidence-manifest][preflight-report] ${preflightReportFile}`);
  process.exit(0);
}

const artifacts = [
  ...requiredManifestArtifacts.map(inspectArtifact),
  ...optionalManifestArtifacts
    .map(inspectArtifact)
    .filter((artifact) => artifact.present),
];
const explain = explainArtifacts();
const blockers = [];

if (strictEvidence) {
  for (const issue of collectManifestProvenanceIssues({ sourceEnvironment, releaseCandidate, evidenceOperator })) {
    blockers.push(`manifest provenance ${issue}`);
  }
}

for (const artifact of artifacts) {
  if (!artifact.present) {
    blockers.push(`missing artifact ${artifact.relativePath}`);
  }
  if (artifact.status === "INVALID_JSON") {
    blockers.push(`invalid JSON artifact ${artifact.relativePath}: ${artifact.parseError}`);
  }
  if (strictEvidence && artifact.provenanceIssues?.length > 0) {
    for (const issue of artifact.provenanceIssues) {
      blockers.push(`artifact provenance ${artifact.relativePath}: ${issue}`);
    }
  }
  if (artifact.contractIssues?.length > 0) {
    for (const issue of artifact.contractIssues) {
      blockers.push(`optional artifact ${artifact.relativePath}: ${issue}`);
    }
  }
}
if (!explain.present) {
  blockers.push(`missing explain directory ${explain.directory}`);
}
if (explain.present && explain.files.length === 0) {
  blockers.push(`no explain JSON files in ${explain.directory}`);
}

const manifest = {
  generatedAt: new Date().toISOString(),
  sourceEnvironment: sourceEnvironment || null,
  releaseCandidate: releaseCandidate || null,
  evidenceOperator: evidenceOperator || null,
  artifactRoot: portablePath(artifactRoot),
  status: blockers.length === 0 ? "PASS" : "FAIL",
  summary: {
    requiredArtifacts: requiredManifestArtifacts.length,
    presentArtifacts: artifacts.filter((artifact) => artifact.present).length,
    optionalArtifacts: artifacts.filter((artifact) => optionalManifestArtifacts.includes(artifact.relativePath)).length,
    invalidJsonArtifacts: artifacts.filter((artifact) => artifact.status === "INVALID_JSON").length,
    explainFiles: explain.files.length,
    provenanceIssueArtifacts: artifacts.filter((artifact) => (artifact.provenanceIssues || []).length > 0).length,
    blockers: blockers.length,
  },
  artifacts,
  explain,
  blockers,
};

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(manifest, null, 2)}\n`);

if (blockers.length > 0) {
  for (const blocker of blockers) {
    console.error(`[ddd-release-evidence-manifest] ${blocker}`);
  }
  console.error(`[ddd-release-evidence-manifest] wrote manifest to ${outputFile}`);
  if (exitOnBlockers) {
    process.exit(1);
  }
  console.error("[ddd-release-evidence-manifest] continuing because DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false");
  process.exit(0);
}

console.log(`[ddd-release-evidence-manifest] manifest passed; artifacts=${artifacts.length}; explainFiles=${explain.files.length}; report=${outputFile}`);
