import { evidenceValueIssue, isIsoTimestamp } from "./ddd-release-evidence-utils.mjs";

export const requiredRollbackContexts = [
  "IAM",
  "Auth",
  "Platform",
  "Message",
  "File",
  "Plugin",
  "Localization",
  "Payment",
  "AI",
  "Job",
];

export const requiredRollbackEvidenceChecklist = [
  {
    id: "pass-rollback-drill-evidence",
    status: "PASS",
    requiredFields: ["rollbackAction", "drillEvidence", "validatedAt"],
    requiredArtifacts: [
      "Rollback command, job output, or operator log for the bounded context.",
      "Post-rollback readiness, health, metrics, or smoke evidence.",
      "Audit entry, ticket, or artifact path proving the rollback action was reviewed.",
    ],
    acceptanceCriteria: [
      "Context status is PASS only after rollback behavior is exercised.",
      "drillEvidence references a concrete evidence link, artifact path, log path, object URI, or ticket id.",
      "validatedAt is an ISO timestamp from the completed drill.",
    ],
  },
  {
    id: "deferred-risk-acceptance-evidence",
    status: "DEFERRED",
    requiredFields: ["notExercisableReason", "riskAcceptedBy", "deferralEvidence", "expiresAt"],
    requiredArtifacts: [
      "Approved risk acceptance record for the bounded context.",
      "Reason the rollback drill cannot be exercised before release.",
      "Expiry timestamp for the deferral window.",
    ],
    acceptanceCriteria: [
      "Context status is DEFERRED only with explicit risk acceptance.",
      "deferralEvidence references a concrete change ticket, approval artifact, or evidence link.",
      "expiresAt is in the future for strict release evaluation.",
    ],
  },
];

const rollbackContextRemediations = {
  IAM: {
    owner: "iam-owner",
    action: "Exercise permission snapshot rollback, cache invalidation, and IAM v2-to-v1 adapter fallback; attach readiness, audit, and cache evidence.",
    evidenceRequirements: [
      "permission snapshot version before and after rollback",
      "cache invalidation or version bump evidence",
      "IAM v2 readiness/health response after rollback",
      "audit entry or command log for the rollback action",
    ],
  },
  Auth: {
    owner: "auth-owner",
    action: "Exercise auth adapter rollback with session TTL compatibility, login smoke, and forced logout/keepalive evidence.",
    evidenceRequirements: [
      "login smoke result after adapter rollback",
      "session TTL compatibility evidence",
      "forced logout or keepalive behavior evidence",
      "auth readiness/health response after rollback",
    ],
  },
  Platform: {
    owner: "platform-owner",
    action: "Exercise platform config/runtime appearance rollback and cache clear; attach bootstrap/config version and audit evidence.",
    evidenceRequirements: [
      "runtime appearance/config version before and after rollback",
      "cache clear or version invalidation evidence",
      "bootstrap response using the rolled-back config",
      "platform audit entry for the rollback action",
    ],
  },
  Message: {
    owner: "message-owner",
    action: "Exercise message relay pause, monolith-compatible delivery fallback, and idempotent replay; attach relay and message state evidence.",
    evidenceRequirements: [
      "message relay pause/resume command or job output",
      "delivery fallback evidence for at least one notice",
      "idempotent replay result with duplicate-safe state",
      "message readiness/metrics response after rollback",
    ],
  },
  File: {
    owner: "file-owner",
    action: "Exercise file processing pause, stable object-key access, and task rerun by id; attach upload, processing row, and storage evidence.",
    evidenceRequirements: [
      "file processing pause/resume command or job output",
      "stable object-key read evidence after rollback",
      "processing task rerun by id with final state",
      "storage artifact or upload row proving access continuity",
    ],
  },
  Plugin: {
    owner: "plugin-owner",
    action: "Exercise tenant plugin disable/version rollback and bootstrap projection rebuild; attach audit and tenant projection evidence.",
    evidenceRequirements: [
      "tenant plugin disable or version rollback command output",
      "bootstrap projection rebuild evidence",
      "tenant plugin projection row before and after rollback",
      "plugin audit entry for the rollback action",
    ],
  },
  Localization: {
    owner: "localization-owner",
    action: "Exercise localization release rollback and runtime bundle cache clear; attach release id, bundle metrics, and audit evidence.",
    evidenceRequirements: [
      "localization release id before and after rollback",
      "runtime bundle cache clear evidence",
      "bundle request or metrics proving rolled-back release is served",
      "localization audit entry for the rollback action",
    ],
  },
  Payment: {
    owner: "payment-owner",
    action: "Exercise payment webhook route fallback and idempotent event replay; attach provider routing, webhook metrics, and order trace evidence.",
    evidenceRequirements: [
      "payment provider route fallback configuration evidence",
      "webhook idempotent replay result",
      "order status trace before and after replay",
      "webhook metrics or audit entry for the rollback action",
    ],
  },
  AI: {
    owner: "ai-owner",
    action: "Exercise AI provider disablement, knowledge index job pause, and document index rebuild/degraded chat transcript evidence.",
    evidenceRequirements: [
      "AI provider disablement or fallback configuration evidence",
      "knowledge index job pause/resume command or job output",
      "document index rebuild or retry evidence",
      "degraded chat/search transcript after rollback",
    ],
  },
  Job: {
    owner: "job-owner",
    action: "Exercise XXL-JOB handler disablement and manual owner internal endpoint fallback; attach dashboard, token, and endpoint evidence.",
    evidenceRequirements: [
      "XXL-JOB handler disablement or dashboard evidence",
      "manual owner internal endpoint fallback result",
      "internal job token provenance or redacted request evidence",
      "job readiness/metrics response after rollback",
    ],
  },
};

export function rollbackContextRemediation(context) {
  return rollbackContextRemediations[context] || {
    owner: "release-owner",
    action: "Attach context-specific rollback evidence or a justified deferred drill record.",
    evidenceRequirements: [
      "context-specific rollback command output",
      "post-rollback readiness/health evidence",
      "audit, ticket, or artifact path proving the action was reviewed",
    ],
  };
}

export function rollbackContextEvidenceRequirements(context) {
  return rollbackContextRemediation(context).evidenceRequirements;
}

export function buildRollbackDrillSummary(artifact = {}) {
  const entries = Array.isArray(artifact?.contexts) ? artifact.contexts : [];
  const requiredContextSet = new Set(requiredRollbackContexts);
  const seen = new Set();
  const duplicateContexts = new Set();
  let passContexts = 0;
  let deferredContexts = 0;
  let missingContexts = 0;
  let todoContexts = 0;
  let invalidStatusContexts = 0;
  let unknownContexts = 0;

  for (const entry of entries) {
    const context = String(entry?.context || "");
    if (seen.has(context)) {
      duplicateContexts.add(context);
    }
    seen.add(context);
    if (!requiredContextSet.has(context)) {
      unknownContexts += 1;
    }
    if (entry?.status === "PASS") {
      passContexts += 1;
    } else if (entry?.status === "DEFERRED") {
      deferredContexts += 1;
    } else if (entry?.status === "MISSING") {
      missingContexts += 1;
    } else if (entry?.status === "TODO") {
      todoContexts += 1;
    } else {
      invalidStatusContexts += 1;
    }
  }

  const missingRequiredContexts = requiredRollbackContexts.filter((context) => !seen.has(context));
  const diagnostics = Array.isArray(artifact?.contextDiagnostics) ? artifact.contextDiagnostics : [];

  return {
    requiredContexts: requiredRollbackContexts.length,
    contexts: entries.length,
    passContexts,
    deferredContexts,
    readyContexts: passContexts + deferredContexts,
    missingContexts,
    todoContexts,
    invalidStatusContexts,
    missingRequiredContexts,
    unknownContexts,
    duplicateContexts: duplicateContexts.size,
    diagnostics: diagnostics.length,
    readyDiagnostics: diagnostics.filter((entry) => entry?.ready === true).length,
    missingEvidenceDiagnostics: diagnostics.filter((entry) => entry?.missingEvidence === true).length,
    appliedDeferrals: Array.isArray(artifact?.appliedDeferrals) ? artifact.appliedDeferrals.length : 0,
    blockers: Array.isArray(artifact?.blockers) ? artifact.blockers.length : 0,
    warnings: Array.isArray(artifact?.warnings) ? artifact.warnings.length : 0,
  };
}

export function buildRollbackDrillBlockers(artifact, { strict = false, now = new Date() } = {}) {
  const candidate = {
    ...(artifact || {}),
    status: "PASS",
  };
  return validateRollbackDrillCore(candidate, { strict, now });
}

export function validateRollbackDrillContract(artifact, { strict = false, now = new Date(), validateBlockers = false } = {}) {
  const issues = validateRollbackDrillCore(artifact, { strict, now });
  if (validateBlockers) {
    const declaredBlockers = Array.isArray(artifact?.blockers) ? artifact.blockers : [];
    compareStringArrays("rollback blockers", declaredBlockers, buildRollbackDrillBlockers(artifact, { strict, now }), issues);
  }
  return issues;
}

function validateRollbackDrillCore(artifact, { strict = false, now = new Date() } = {}) {
  const issues = [];
  parseDate(artifact?.generatedAt, "generatedAt", issues);
  requireRealText(artifact?.environment, "environment", issues);
  requireRealText(artifact?.releaseVersion, "releaseVersion", issues);
  requireRealText(artifact?.operator, "operator", issues);

  if (strict && artifact?.status !== "PASS") {
    issues.push(`status must be PASS for strict release, got ${artifact?.status ?? "missing"}`);
  }

  const entries = Array.isArray(artifact?.contexts) ? artifact.contexts : [];
  if (!Array.isArray(artifact?.contexts)) {
    issues.push("contexts must be an array");
  }

  const byContext = new Map();
  const requiredContextSet = new Set(requiredRollbackContexts);
  for (const entry of entries) {
    const context = String(entry?.context || "");
    if (!requiredContextSet.has(context)) {
      issues.push(`unknown context ${context || "missing"}`);
      continue;
    }
    if (byContext.has(context)) {
      issues.push(`duplicate context ${context}`);
      continue;
    }
    byContext.set(context, entry);
  }

  for (const context of requiredRollbackContexts) {
    const entry = byContext.get(context);
    if (!entry) {
      issues.push(`missing context ${context}`);
      continue;
    }
    if (entry.status === "PASS") {
      requireRealText(entry.rollbackAction, `${context}.rollbackAction`, issues);
      requireRealText(entry.drillEvidence, `${context}.drillEvidence`, issues);
      requireEvidenceReference(entry.drillEvidence, `${context}.drillEvidence`, issues);
      parseDate(entry.validatedAt, `${context}.validatedAt`, issues);
      continue;
    }
    if (entry.status === "DEFERRED") {
      requireRealText(entry.notExercisableReason, `${context}.notExercisableReason`, issues);
      requireRealText(entry.riskAcceptedBy, `${context}.riskAcceptedBy`, issues);
      requireRealText(entry.deferralEvidence, `${context}.deferralEvidence`, issues);
      requireEvidenceReference(entry.deferralEvidence, `${context}.deferralEvidence`, issues);
      const expiresAt = parseDate(entry.expiresAt, `${context}.expiresAt`, issues);
      if (strict && Number.isFinite(expiresAt) && expiresAt <= now.getTime()) {
        issues.push(`${context}.expiresAt must be in the future for strict release`);
      }
      continue;
    }
    issues.push(`${context} status must be PASS or DEFERRED`);
  }

  validateDiagnostics(artifact, byContext, issues);
  validateSummary(artifact, issues);

  return issues;
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

function validateDiagnostics(artifact, contextEntries, issues) {
  if (!Array.isArray(artifact?.contextDiagnostics)) {
    issues.push("contextDiagnostics must be an array");
    return;
  }
  const requiredContextSet = new Set(requiredRollbackContexts);
  const byContext = new Map();
  for (const diagnostic of artifact.contextDiagnostics) {
    const context = String(diagnostic?.context || "");
    if (!requiredContextSet.has(context)) {
      issues.push(`unknown contextDiagnostic ${context || "missing"}`);
      continue;
    }
    if (byContext.has(context)) {
      issues.push(`duplicate contextDiagnostic ${context}`);
      continue;
    }
    byContext.set(context, diagnostic);
  }
  for (const context of requiredRollbackContexts) {
    const diagnostic = byContext.get(context);
    if (!diagnostic) {
      issues.push(`missing contextDiagnostic ${context}`);
      continue;
    }
    const remediation = rollbackContextRemediation(context);
    if (diagnostic.owner !== remediation.owner) {
      issues.push(`${context}.diagnostic owner must be ${remediation.owner}, got ${diagnostic.owner ?? "missing"}`);
    }
    if (diagnostic.action !== remediation.action) {
      issues.push(`${context}.diagnostic action must match rollback remediation`);
    }
    if (JSON.stringify(diagnostic.evidenceRequirements || []) !== JSON.stringify(remediation.evidenceRequirements || [])) {
      issues.push(`${context}.diagnostic evidenceRequirements must match rollback remediation`);
    }
    const entry = contextEntries.get(context);
    const expectedStatus = entry?.status || "MISSING";
    if (diagnostic.status !== expectedStatus) {
      issues.push(`${context}.diagnostic status must be ${expectedStatus}, got ${diagnostic.status ?? "missing"}`);
    }
    const ready = expectedStatus === "PASS" || expectedStatus === "DEFERRED";
    if (diagnostic.ready !== ready) {
      issues.push(`${context}.diagnostic ready must be ${ready}`);
    }
    const missingEvidence = expectedStatus !== "PASS" && expectedStatus !== "DEFERRED";
    if (diagnostic.missingEvidence !== missingEvidence) {
      issues.push(`${context}.diagnostic missingEvidence must be ${missingEvidence}`);
    }
    const deferralApplied = expectedStatus === "DEFERRED";
    if (diagnostic.deferralApplied !== deferralApplied) {
      issues.push(`${context}.diagnostic deferralApplied must be ${deferralApplied}`);
    }
    const expectedEvidence = expectedStatus === "PASS"
      ? entry?.drillEvidence
      : expectedStatus === "DEFERRED"
        ? entry?.deferralEvidence
        : null;
    if ((diagnostic.evidence || null) !== (expectedEvidence || null)) {
      issues.push(`${context}.diagnostic evidence must match context evidence`);
    }
  }
}

function validateSummary(artifact, issues) {
  if (!artifact?.summary || typeof artifact.summary !== "object" || Array.isArray(artifact.summary)) {
    issues.push("summary must be an object");
    return;
  }
  const expected = buildRollbackDrillSummary(artifact);
  for (const [field, expectedValue] of Object.entries(expected)) {
    const actualValue = artifact.summary[field];
    if (Array.isArray(expectedValue)) {
      const actual = Array.isArray(actualValue) ? actualValue : [];
      if (JSON.stringify(actual) !== JSON.stringify(expectedValue)) {
        issues.push(`summary.${field} must match rollback context details`);
      }
      continue;
    }
    if (actualValue !== expectedValue) {
      issues.push(`summary.${field} must be ${expectedValue}, got ${actualValue ?? "missing"}`);
    }
  }
}

function requireRealText(value, label, issues) {
  const issue = evidenceValueIssue(value);
  if (issue) {
    issues.push(`${label} ${issue}`);
  }
}

function parseDate(value, label, issues) {
  if (!isIsoTimestamp(value)) {
    issues.push(`${label} must be an ISO timestamp`);
    return null;
  }
  return Date.parse(value);
}

function requireEvidenceReference(value, label, issues) {
  if (typeof value !== "string") {
    return;
  }
  const trimmed = value.trim();
  const hasReference = /^https:\/\//i.test(trimmed)
    || /^(s3|gs):\/\//i.test(trimmed)
    || /\b[A-Z][A-Z0-9]+-\d+\b/.test(trimmed)
    || /(^|[\s:])(\.?\.?\/)?(artifacts|logs|reports|tmp|docs)\//i.test(trimmed)
    || /\/[A-Za-z0-9._-]+/.test(trimmed);
  if (!hasReference) {
    issues.push(`${label} must include a concrete evidence link, artifact path, log path, object URI, or ticket id`);
  }
}
