import { collectProvenanceIssues } from "./ddd-release-evidence-utils.mjs";

export const requiredPhysicalSplitContexts = Object.freeze([
  { name: "IAM", module: "services/system-service", route: "/api/v2/iam", ownerContext: "IAM", physicalServiceTarget: false },
  { name: "Auth", module: "services/auth-service", route: "/api/v2/auth", ownerContext: "AUTH", physicalServiceTarget: true },
  { name: "Platform", module: "services/system-service", route: "/api/v2/platform", ownerContext: "PLATFORM", physicalServiceTarget: false },
  { name: "Message", module: "services/message-service", route: "/api/v2/message", ownerContext: "MESSAGE", physicalServiceTarget: true },
  { name: "File", module: "services/file-service", route: "/api/v2/files", ownerContext: "FILE", physicalServiceTarget: true },
  { name: "Plugin", module: "services/plugin-service", route: "/api/v2/plugins", ownerContext: "PLUGIN", physicalServiceTarget: true },
  { name: "Localization", module: "services/localization-service", route: "/api/v2/localization", ownerContext: "LOCALIZATION", physicalServiceTarget: true },
  { name: "Payment", module: "services/payment-service", route: "/api/v2/payment", ownerContext: "PAYMENT", physicalServiceTarget: true },
  { name: "AI", module: "services/ai-service", route: "/api/v2/ai", ownerContext: "AI", physicalServiceTarget: true },
  { name: "Job", module: "services/job-executor", route: "/api/v2/job", ownerContext: "JOB", physicalServiceTarget: false },
]);

const requiredGlobalChecks = Object.freeze(["split-gate-document", "architecture-boundary-test"]);
const requiredContextChecks = Object.freeze([
  "module",
  "owner-manifest",
  "readiness-endpoint",
  "health-endpoint",
  "metrics-endpoint",
  "cross-service-pom-dependency",
]);
const allowedCheckStatuses = new Set(["pass", "fail"]);

export function buildPhysicalSplitSummary(artifact = {}) {
  const contexts = Array.isArray(artifact?.contexts) ? artifact.contexts : [];
  const globalChecks = Array.isArray(artifact?.globalChecks) ? artifact.globalChecks : [];
  const contextChecks = contexts.flatMap((context) => Array.isArray(context?.checks) ? context.checks : []);
  const provenanceFailures = artifact?.strict
    ? collectProvenanceIssues({
      sourceEnvironment: artifact.sourceEnvironment,
      releaseCandidate: artifact.releaseCandidate,
      evidenceOperator: artifact.evidenceOperator,
    }).length
    : 0;
  const globalFailures = globalChecks.filter((check) => check?.status === "fail").length;
  const contextFailures = contextChecks.filter((check) => check?.status === "fail").length;

  return {
    contexts: contexts.length,
    physicalServiceTargets: contexts.filter((context) => context?.physicalServiceTarget === true).length,
    standaloneTargetsReady: contexts.filter((context) => (
      context?.physicalServiceTarget === true && context?.standaloneBootApplication === true
    )).length,
    globalChecks: globalChecks.length,
    contextChecks: contextChecks.length,
    passedChecks: [...globalChecks, ...contextChecks].filter((check) => check?.status === "pass").length,
    failedChecks: globalFailures + contextFailures,
    failures: provenanceFailures + globalFailures + contextFailures,
    blockers: contexts.reduce((sum, context) => sum + countArray(context?.blockers), 0),
    warnings: contexts.reduce((sum, context) => sum + countArray(context?.warnings), 0),
    migrationFiles: contexts.reduce((sum, context) => sum + countArray(context?.migrationFiles), 0),
    missingBusinessEndpoints: contexts.reduce((sum, context) => sum + countArray(context?.missingBusinessEndpoints), 0),
    crossServiceDependencyFailures: contextChecks.filter((check) => (
      check?.name === "cross-service-pom-dependency" && check?.status === "fail"
    )).length,
  };
}

export function validatePhysicalSplitContract(artifact = {}) {
  const issues = [];
  if (!artifact?.summary || typeof artifact.summary !== "object" || Array.isArray(artifact.summary)) {
    issues.push("summary must be an object");
    return issues;
  }
  if (!Array.isArray(artifact.contexts)) {
    issues.push("contexts must be an array");
  }
  if (!Array.isArray(artifact.globalChecks)) {
    issues.push("globalChecks must be an array");
  }
  validateGlobalChecks(artifact, issues);
  validateContexts(artifact, issues);
  const expected = buildPhysicalSplitSummary(artifact);
  for (const [field, expectedValue] of Object.entries(expected)) {
    const actualValue = artifact.summary[field];
    if (actualValue !== expectedValue) {
      issues.push(`summary.${field} must be ${expectedValue}, got ${actualValue ?? "missing"}`);
    }
  }
  return issues;
}

function validateGlobalChecks(artifact, issues) {
  const globalChecks = Array.isArray(artifact?.globalChecks) ? artifact.globalChecks : [];
  const seen = new Set();
  for (const check of globalChecks) {
    const name = check?.name;
    if (!name) {
      issues.push("global check name is required");
      continue;
    }
    if (seen.has(name)) {
      issues.push(`duplicate global check ${name}`);
    }
    seen.add(name);
    if (!allowedCheckStatuses.has(check?.status)) {
      issues.push(`global check ${name} has invalid status ${check?.status ?? "missing"}`);
    }
    if (!check?.detail) {
      issues.push(`global check ${name} detail is required`);
    }
  }
  for (const required of requiredGlobalChecks) {
    if (!seen.has(required)) {
      issues.push(`missing global check ${required}`);
    }
  }
}

function validateContexts(artifact, issues) {
  const contexts = Array.isArray(artifact?.contexts) ? artifact.contexts : [];
  const requiredByName = new Map(requiredPhysicalSplitContexts.map((context) => [context.name, context]));
  const seen = new Set();
  for (const context of contexts) {
    const name = context?.name;
    if (!name) {
      issues.push("context name is required");
      continue;
    }
    if (seen.has(name)) {
      issues.push(`duplicate physical split context ${name}`);
    }
    seen.add(name);
    const required = requiredByName.get(name);
    if (!required) {
      issues.push(`unknown physical split context ${name}`);
      continue;
    }
    validateContextShape(context, required, issues);
    validateContextChecks(context, issues);
  }
  for (const required of requiredPhysicalSplitContexts) {
    if (!seen.has(required.name)) {
      issues.push(`missing physical split context ${required.name}`);
    }
  }
}

function validateContextShape(context, required, issues) {
  for (const field of ["module", "route", "ownerContext", "physicalServiceTarget"]) {
    if (context?.[field] !== required[field]) {
      issues.push(`${required.name} ${field} must be ${required[field]}, got ${context?.[field] ?? "missing"}`);
    }
  }
  if (context?.standaloneBootApplication !== true && context?.standaloneBootApplication !== false) {
    issues.push(`${required.name} standaloneBootApplication must be boolean`);
  }
  for (const arrayField of ["migrationFiles", "missingBusinessEndpoints", "checks", "blockers", "warnings"]) {
    if (!Array.isArray(context?.[arrayField])) {
      issues.push(`${required.name} ${arrayField} must be an array`);
    }
  }
}

function validateContextChecks(context, issues) {
  const checks = Array.isArray(context?.checks) ? context.checks : [];
  const seenNames = new Set();
  const seenSignatures = new Set();
  for (const check of checks) {
    const name = check?.name;
    if (!name) {
      issues.push(`${context.name} check name is required`);
      continue;
    }
    const signature = `${name}:${check?.detail ?? ""}`;
    if (seenSignatures.has(signature)) {
      issues.push(`${context.name} duplicate check ${signature}`);
    }
    seenNames.add(name);
    seenSignatures.add(signature);
    if (!allowedCheckStatuses.has(check?.status)) {
      issues.push(`${context.name} check ${name} has invalid status ${check?.status ?? "missing"}`);
    }
    if (!check?.detail) {
      issues.push(`${context.name} check ${name} detail is required`);
    }
  }
  for (const required of requiredContextChecks) {
    if (!seenNames.has(required)) {
      issues.push(`${context.name} missing context check ${required}`);
    }
  }
}

function countArray(value) {
  return Array.isArray(value) ? value.length : 0;
}
