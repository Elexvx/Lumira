const placeholderPattern = /(^__REQUIRED__$|replace-with|link-or-path|link or path|todo|tbd|example\.com|example\.internal|\.example\.|\.test(?::|\/|$)|\.invalid(?::|\/|$)|^https?:\/\/[^/]*example)/i;
const localHostPattern = /(^|\/\/)(localhost|127\.0\.0\.1|\[::1\]|0\.0\.0\.0)(:|\/|$)/i;

export function redactLocalPaths(value, { repoRoot = "", homeDir = "" } = {}) {
  if (typeof value !== "string") {
    return value;
  }
  let redacted = value;
  if (repoRoot) {
    redacted = redacted.replaceAll(repoRoot, ".");
  }
  if (homeDir) {
    redacted = redacted.replaceAll(homeDir, "~");
  }
  return redacted;
}

export function evidenceValueIssue(value) {
  if (typeof value !== "string" || value.trim().length === 0) {
    return "is required";
  }
  if (placeholderPattern.test(value)) {
    return "must not contain placeholder text";
  }
  return null;
}

export function collectProvenanceIssues(provenance) {
  return Object.entries(provenance)
    .map(([field, value]) => {
      const issue = evidenceValueIssue(value);
      return issue ? `${field} ${issue}` : null;
    })
    .filter(Boolean);
}

export function requireRuntimeProvenanceWhenStrict({ strict, sourceEnvironment, releaseCandidate, evidenceOperator }) {
  if (!strict) {
    return [];
  }
  return collectProvenanceIssues({ sourceEnvironment, releaseCandidate, evidenceOperator });
}

export function buildProductionEquivalenceEvidence({ strict, baseUrl, deploymentEvidence, evidenceName }) {
  const https = isHttpsUrl(baseUrl);
  const localOnly = isLocalUrlLike(baseUrl);
  const label = evidenceName || "runtime evidence";
  const issues = [];
  if (strict && !https) {
    issues.push(`strict ${label} requires HTTPS baseUrl evidence`);
  }
  if (strict && localOnly) {
    issues.push(`strict ${label} requires non-local baseUrl, got ${baseUrl}`);
  }
  const baseUrlIssue = baseUrl ? evidenceValueIssue(String(baseUrl)) : null;
  if (strict && baseUrlIssue === "must not contain placeholder text") {
    issues.push(`strict ${label} baseUrl ${baseUrlIssue}`);
  }
  const deploymentEvidenceIssue = evidenceValueIssue(deploymentEvidence);
  if (strict && deploymentEvidenceIssue) {
    issues.push(`strict ${label} deploymentEvidence ${deploymentEvidenceIssue}`);
  }
  return {
    strict,
    https,
    localOnly,
    deploymentEvidence: deploymentEvidence || null,
    issues,
  };
}

export function validateProductionEquivalenceEvidence(label, artifact, { strict = false, issuesMustBeStrings = false } = {}) {
  const issues = [];
  const productionEquivalence = artifact?.productionEquivalence;
  if (!productionEquivalence) {
    if (strict) {
      issues.push(`${label} productionEquivalence is required for strict release evidence`);
    }
    return issues;
  }
  if (typeof productionEquivalence !== "object" || Array.isArray(productionEquivalence)) {
    issues.push(`${label} productionEquivalence must be an object`);
    return issues;
  }
  for (const field of ["strict", "https", "localOnly"]) {
    if (typeof productionEquivalence[field] !== "boolean") {
      issues.push(`${label} productionEquivalence.${field} must be boolean`);
    }
  }
  if (strict && productionEquivalence.strict !== true) {
    issues.push(`${label} productionEquivalence.strict must be true for strict release evidence`);
  }
  if (strict && productionEquivalence.https !== true) {
    issues.push(`${label} productionEquivalence.https must be true for strict release evidence`);
  }
  if (strict && productionEquivalence.localOnly !== false) {
    issues.push(`${label} productionEquivalence.localOnly must be false for strict release evidence`);
  }
  if (productionEquivalence.deploymentEvidence !== null
    && productionEquivalence.deploymentEvidence !== undefined
    && typeof productionEquivalence.deploymentEvidence !== "string") {
    issues.push(`${label} productionEquivalence.deploymentEvidence must be string or null`);
  }
  const deploymentEvidenceIssue = evidenceValueIssue(productionEquivalence.deploymentEvidence);
  if (strict && deploymentEvidenceIssue) {
    issues.push(`${label} productionEquivalence.deploymentEvidence ${deploymentEvidenceIssue}`);
  }
  if (!Array.isArray(productionEquivalence.issues)) {
    issues.push(`${label} productionEquivalence.issues must be an array${issuesMustBeStrings ? " of strings" : ""}`);
  } else if (issuesMustBeStrings && !productionEquivalence.issues.every((issue) => typeof issue === "string")) {
    issues.push(`${label} productionEquivalence.issues must be an array of strings`);
  } else if (strict && productionEquivalence.issues.length > 0) {
    issues.push(`${label} productionEquivalence.issues must be empty for strict release evidence`);
  }
  return issues;
}

export function isHttpsUrl(value) {
  try {
    return new URL(value).protocol === "https:";
  } catch {
    return false;
  }
}

export function isLocalUrlLike(value) {
  return localHostPattern.test(String(value || ""));
}

export function isIsoTimestamp(value) {
  const time = Date.parse(value || "");
  return Number.isFinite(time);
}
