import fs from "node:fs";
import { validateProductionEquivalenceEvidence } from "./ddd-release-evidence-utils.mjs";

export const runtimeReadinessContexts = [
  ["iam", "/api/v2/iam"],
  ["auth", "/api/v2/auth"],
  ["platform", "/api/v2/platform"],
  ["message", "/api/v2/message"],
  ["files", "/api/v2/files"],
  ["plugins", "/api/v2/plugins"],
  ["localization", "/api/v2/localization"],
  ["payment", "/api/v2/payment"],
  ["ai", "/api/v2/ai"],
  ["job", "/api/v2/job"],
];

export const runtimeReadinessSuffixes = ["readiness", "health", "metrics"];

export const runtimeReadinessContextLabels = new Map([
  ["iam", "IAM"],
  ["auth", "Auth"],
  ["platform", "Platform"],
  ["message", "Message"],
  ["files", "File"],
  ["plugins", "Plugin"],
  ["localization", "Localization"],
  ["payment", "Payment"],
  ["ai", "AI"],
  ["job", "Job"],
]);

export function expectedRuntimeReadinessChecks() {
  return runtimeReadinessContexts.flatMap(([context]) => (
    runtimeReadinessSuffixes.map((suffix) => ({ context, suffix }))
  ));
}

export function validateRuntimeReadinessArtifact(artifact, { strict = false } = {}) {
  const issues = [];
  const failures = Array.isArray(artifact?.failures) ? artifact.failures : [];
  if (failures.length > 0) {
    issues.push(`failures=${failures.length}`);
  }
  issues.push(...validateProductionEquivalenceEvidence("runtime readiness", artifact, {
    strict,
    issuesMustBeStrings: true,
  }));

  const summary = Array.isArray(artifact?.summary) ? artifact.summary : [];
  const expectedChecks = expectedRuntimeReadinessChecks();
  if (summary.length < expectedChecks.length) {
    issues.push(`expected at least ${expectedChecks.length} endpoint checks, got ${summary.length}`);
  }
  if (summary.length > expectedChecks.length) {
    issues.push(`expected ${expectedChecks.length} endpoint checks, got ${summary.length}`);
  }

  const expectedKeys = new Set(expectedChecks.map((check) => `${check.context}:${check.suffix}`));
  const present = new Set();
  const artifactReferences = new Set();
  for (const item of summary) {
    const key = `${item?.context}:${item?.suffix}`;
    if (!expectedKeys.has(key)) {
      issues.push(`unknown readiness check ${item?.context || "missing"}/${item?.suffix || "missing"}`);
      continue;
    }
    if (present.has(key)) {
      issues.push(`duplicate readiness check ${item.context}/${item.suffix}`);
    }
    present.add(key);
    if (item?.artifact) {
      if (artifactReferences.has(item.artifact)) {
        issues.push(`${item.context}/${item.suffix} duplicate artifact reference`);
      }
      artifactReferences.add(item.artifact);
    }
  }
  for (const check of expectedChecks) {
    if (!present.has(`${check.context}:${check.suffix}`)) {
      issues.push(`missing readiness check ${check.context}/${check.suffix}`);
    }
  }

  for (const item of summary) {
    if (item.status !== 200) {
      issues.push(`${item.context}/${item.suffix} status=${item.status}`);
    }
    if (!item.artifact) {
      issues.push(`${item.context}/${item.suffix} missing artifact reference`);
      continue;
    }
    issues.push(...validateRuntimeEndpointArtifact(item));
  }

  return issues;
}

export function validateRuntimeEndpointArtifact(item) {
  const issues = [];
  if (!item?.artifact) {
    return issues;
  }
  if (!fs.existsSync(item.artifact)) {
    return [`${item.context}/${item.suffix} artifact file is missing: ${item.artifact}`];
  }
  let parsed;
  try {
    parsed = JSON.parse(fs.readFileSync(item.artifact, "utf8"));
  } catch (error) {
    return [`${item.context}/${item.suffix} artifact JSON is invalid: ${error.message}`];
  }

  if (parsed?.httpStatus !== 200) {
    issues.push(`${item.context}/${item.suffix} artifact httpStatus=${parsed?.httpStatus ?? "missing"}`);
  }
  if (!parsed?.data || typeof parsed.data !== "object") {
    issues.push(`${item.context}/${item.suffix} artifact missing data object`);
    return issues;
  }

  const data = parsed.data;
  const expectedContext = runtimeReadinessContextLabels.get(item.context);
  if (expectedContext && data.context !== expectedContext) {
    issues.push(`${item.context}/${item.suffix} artifact context must be ${expectedContext}, got ${data.context ?? "missing"}`);
  }
  if (!data.ownerModule) {
    issues.push(`${item.context}/${item.suffix} artifact ownerModule is required`);
  }
  if (!data.status) {
    issues.push(`${item.context}/${item.suffix} artifact status is required`);
  }

  if (item.suffix === "readiness") {
    for (const field of ["ownerTablePatterns", "apiContracts", "healthChecks", "metrics", "dependencies", "rollbackSteps"]) {
      if (!nonEmptyStringArray(data[field])) {
        issues.push(`${item.context}/${item.suffix} artifact ${field} must be a non-empty string array`);
      }
    }
    if (!Array.isArray(data.blockers)) {
      issues.push(`${item.context}/${item.suffix} artifact blockers must be an array`);
    }
  }

  if (item.suffix === "health" || item.suffix === "metrics") {
    if (!data.observedAt || Number.isNaN(Date.parse(data.observedAt))) {
      issues.push(`${item.context}/${item.suffix} artifact observedAt must be an ISO-like datetime`);
    }
    if (item.suffix === "health") {
      if (!Array.isArray(data.healthChecks) || data.healthChecks.length === 0) {
        issues.push(`${item.context}/${item.suffix} artifact healthChecks must be non-empty`);
      } else {
        issues.push(...validateHealthChecks(item, data.healthChecks));
      }
    }
    if (!Array.isArray(data.metrics) || data.metrics.length === 0) {
      issues.push(`${item.context}/${item.suffix} artifact metrics must be non-empty`);
    } else {
      issues.push(...validateMetrics(item, data.metrics));
    }
  }

  return issues;
}

function nonEmptyStringArray(value) {
  return Array.isArray(value) && value.length > 0 && value.every((item) => typeof item === "string" && item.trim());
}

function validateHealthChecks(item, healthChecks) {
  const issues = [];
  for (const [index, check] of healthChecks.entries()) {
    if (!check?.name) {
      issues.push(`${item.context}/${item.suffix} artifact healthChecks[${index}].name is required`);
    }
    if (!check?.status) {
      issues.push(`${item.context}/${item.suffix} artifact healthChecks[${index}].status is required`);
    }
    if (!check?.description) {
      issues.push(`${item.context}/${item.suffix} artifact healthChecks[${index}].description is required`);
    }
  }
  return issues;
}

function validateMetrics(item, metrics) {
  const issues = [];
  for (const [index, metric] of metrics.entries()) {
    if (!metric?.name) {
      issues.push(`${item.context}/${item.suffix} artifact metrics[${index}].name is required`);
    }
    if (!metric?.type) {
      issues.push(`${item.context}/${item.suffix} artifact metrics[${index}].type is required`);
    }
    if (!metric?.unit) {
      issues.push(`${item.context}/${item.suffix} artifact metrics[${index}].unit is required`);
    }
    if (!metric?.description) {
      issues.push(`${item.context}/${item.suffix} artifact metrics[${index}].description is required`);
    }
  }
  return issues;
}
