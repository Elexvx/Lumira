const allowedGateCheckStatuses = new Set(["present", "warning", "blocker"]);

export function validateReleaseGateArtifact(artifact = {}) {
  const issues = [];
  if (!artifact || typeof artifact !== "object" || Array.isArray(artifact)) {
    return ["release gate artifact must be a JSON object"];
  }
  if (!artifact.summary || typeof artifact.summary !== "object" || Array.isArray(artifact.summary)) {
    issues.push("release gate summary must be an object");
  }
  const checks = Array.isArray(artifact.checks) ? artifact.checks : [];
  const blockers = Array.isArray(artifact.blockers) ? artifact.blockers : [];
  const blockerDetails = Array.isArray(artifact.blockerDetails) ? artifact.blockerDetails : [];
  const warnings = Array.isArray(artifact.warnings) ? artifact.warnings : [];
  const warningDetails = Array.isArray(artifact.warningDetails) ? artifact.warningDetails : [];
  if (!Array.isArray(artifact.checks)) {
    issues.push("release gate checks must be an array");
  }
  if (!Array.isArray(artifact.blockers)) {
    issues.push("release gate blockers must be an array");
  }
  if (artifact.blockerDetails !== undefined && !Array.isArray(artifact.blockerDetails)) {
    issues.push("release gate blockerDetails must be an array");
  }
  if (!Array.isArray(artifact.warnings)) {
    issues.push("release gate warnings must be an array");
  }
  if (artifact.warningDetails !== undefined && !Array.isArray(artifact.warningDetails)) {
    issues.push("release gate warningDetails must be an array");
  }

  const expectedBlockers = [];
  const expectedBlockerDetails = [];
  const expectedWarnings = [];
  const expectedWarningDetails = [];
  for (const [index, check] of checks.entries()) {
    if (!check?.name) {
      issues.push(`release gate checks[${index}].name is required`);
    }
    if (!allowedGateCheckStatuses.has(check?.status)) {
      issues.push(`release gate check ${check?.name || index} has invalid status ${check?.status ?? "missing"}`);
    }
    if (!check?.detail) {
      issues.push(`release gate check ${check?.name || index} detail is required`);
    }
    if (check?.file !== null && check?.file !== undefined && typeof check.file !== "string") {
      issues.push(`release gate check ${check?.name || index} file must be a string or null`);
    }
    if (check?.status === "blocker") {
      expectedBlockers.push(`${check.name}: ${check.detail}`);
      expectedBlockerDetails.push({
        check: check.name,
        detail: check.detail,
        file: check.file ?? null,
      });
    }
    if (check?.status === "warning") {
      expectedWarnings.push(`${check.name}: ${check.detail}`);
      expectedWarningDetails.push({
        check: check.name,
        detail: check.detail,
        file: check.file ?? null,
      });
    }
  }

  if (artifact.summary) {
    compareSummary("checks", artifact.summary.checks, checks.length, issues);
    compareSummary("blockers", artifact.summary.blockers, expectedBlockers.length, issues);
    compareSummary("warnings", artifact.summary.warnings, expectedWarnings.length, issues);
  }
  compareStringArrays("release gate blockers", blockers, expectedBlockers, issues);
  compareDetailArrays("release gate blockerDetails", blockerDetails, expectedBlockerDetails, issues);
  compareStringArrays("release gate warnings", warnings, expectedWarnings, issues);
  compareDetailArrays("release gate warningDetails", warningDetails, expectedWarningDetails, issues);
  return issues;
}

function compareSummary(field, declared, actual, issues) {
  if (declared !== actual) {
    issues.push(`release gate summary ${field} mismatch: declared=${declared ?? "missing"}, actual=${actual}`);
  }
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

function compareDetailArrays(label, declared, expected, issues) {
  if (declared.length !== expected.length) {
    issues.push(`${label} length mismatch: declared=${declared.length}, actual=${expected.length}`);
    return;
  }
  for (let index = 0; index < expected.length; index += 1) {
    const declaredItem = declared[index] || {};
    const expectedItem = expected[index] || {};
    for (const field of ["check", "detail", "file"]) {
      if ((declaredItem[field] ?? null) !== (expectedItem[field] ?? null)) {
        issues.push(
          `${label}[${index}].${field} mismatch: declared=${declaredItem[field] ?? "missing"}, actual=${expectedItem[field] ?? "missing"}`,
        );
      }
    }
  }
}
