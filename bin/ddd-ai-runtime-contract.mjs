import { validateProductionEquivalenceEvidence } from "./ddd-release-evidence-utils.mjs";

export function buildAiRuntimeSummary(artifact = {}) {
  const failureDetails = Array.isArray(artifact?.failureDetails) ? artifact.failureDetails : [];
  const failures = Array.isArray(artifact?.failures) ? artifact.failures : [];
  const healthChecks = artifact?.endpoints?.health?.data?.healthChecks || [];
  const providerHealth = byName(healthChecks, "ai.provider-runtime");
  const ownerGatewayHealth = byName(healthChecks, "ai.remote-owner-gateway");

  return {
    failed: Math.max(failures.length, failureDetails.length),
    failureCategories: failureDetails.reduce((counts, detail) => {
      const category = detail?.category || "unknown";
      counts[category] = (counts[category] || 0) + 1;
      return counts;
    }, {}),
    providerRuntimeStatus: providerHealth?.status || artifact?.summary?.providerRuntimeStatus || null,
    providerRuntimeDescription: providerHealth?.description || artifact?.summary?.providerRuntimeDescription || null,
    ownerGatewayStatus: ownerGatewayHealth?.status || artifact?.summary?.ownerGatewayStatus || null,
    ownerGatewayDescription: ownerGatewayHealth?.description || artifact?.summary?.ownerGatewayDescription || null,
  };
}

export function validateAiRuntimeArtifact(artifact = {}, { strict = false, validateRemoteExpectations = strict } = {}) {
  const issues = [];
  if (!artifact || typeof artifact !== "object") {
    return ["AI runtime artifact must be a JSON object"];
  }
  issues.push(...validateProductionEquivalenceEvidence("AI runtime", artifact, {
    strict,
    issuesMustBeStrings: true,
  }));
  if (!artifact.baseUrl) {
    issues.push("baseUrl is required");
  }
  if (!artifact.checkedAt || Number.isNaN(Date.parse(artifact.checkedAt))) {
    issues.push("checkedAt must be an ISO-like datetime");
  }
  if (!artifact.summary || typeof artifact.summary !== "object" || Array.isArray(artifact.summary)) {
    issues.push("summary must be an object");
    return issues;
  }

  const failures = Array.isArray(artifact.failures) ? artifact.failures : [];
  const failureDetails = Array.isArray(artifact.failureDetails) ? artifact.failureDetails : [];
  if (failures.length !== failureDetails.length) {
    issues.push(`failures length must match failureDetails length, got ${failures.length}/${failureDetails.length}`);
  }
  for (const [index, detail] of failureDetails.entries()) {
    if (!detail?.message) {
      issues.push(`failureDetails[${index}].message is required`);
    }
    if (!detail?.category) {
      issues.push(`failureDetails[${index}].category is required`);
    }
    if (!detail?.owner) {
      issues.push(`failureDetails[${index}].owner is required`);
    }
    if (failures[index] && detail?.message && failures[index] !== detail.message) {
      issues.push(`failures[${index}] must match failureDetails[${index}].message`);
    }
  }

  const expectedStatus = failures.length > 0 || failureDetails.length > 0 ? "FAIL" : "PASS";
  if (artifact.status !== expectedStatus) {
    issues.push(`status must be ${expectedStatus}, got ${artifact.status ?? "missing"}`);
  }

  const expectedSummary = buildAiRuntimeSummary(artifact);
  if (artifact.summary.failed !== expectedSummary.failed) {
    issues.push(`summary.failed must be ${expectedSummary.failed}, got ${artifact.summary.failed ?? "missing"}`);
  }
  if (JSON.stringify(artifact.summary.failureCategories || {}) !== JSON.stringify(expectedSummary.failureCategories)) {
    issues.push("summary.failureCategories must match failureDetails categories");
  }

  const endpoints = artifact.endpoints || {};
  for (const suffix of ["readiness", "health", "metrics"]) {
    const endpoint = endpoints[suffix];
    if (!endpoint || typeof endpoint !== "object") {
      issues.push(`endpoints.${suffix} must be an object`);
      continue;
    }
    if (endpoint.status !== null && endpoint.status !== 200) {
      issues.push(`endpoints.${suffix}.status must be 200 or null, got ${endpoint.status}`);
    }
    if (endpoint.status === 200) {
      if (!Number.isFinite(endpoint.elapsedMs) || endpoint.elapsedMs <= 0) {
        issues.push(`endpoints.${suffix}.elapsedMs must be positive`);
      }
      validateEndpointPayload(suffix, endpoint.data, issues);
    }
    if (expectedStatus === "PASS" && endpoint.status !== 200) {
      issues.push(`PASS artifact requires endpoints.${suffix}.status=200`);
    }
  }

  const provider = artifact.remoteEvidence?.provider || {};
  if (provider.provider === "lumira-local" && provider.localFallbackProvider !== true) {
    issues.push("remoteEvidence.provider.localFallbackProvider must be true when provider=lumira-local");
  }
  if (provider.remoteConfigured === true && (!provider.provider || provider.provider === "lumira-local")) {
    issues.push("remoteEvidence.provider.remoteConfigured=true requires a non-local provider");
  }

  const ownerGateway = artifact.remoteEvidence?.ownerGateway || {};
  const configuredOwners = Array.isArray(ownerGateway.configuredOwners) ? ownerGateway.configuredOwners : [];
  const duplicateOwners = duplicates(configuredOwners);
  for (const owner of duplicateOwners) {
    issues.push(`remoteEvidence.ownerGateway configured owner duplicated: ${owner}`);
  }
  if (ownerGateway.configuredOwnerCount !== configuredOwners.length) {
    issues.push(`remoteEvidence.ownerGateway.configuredOwnerCount must be ${configuredOwners.length}, got ${ownerGateway.configuredOwnerCount ?? "missing"}`);
  }

  if (validateRemoteExpectations && artifact.expectations?.providerRemote === true) {
    if (provider.status !== "UP" || provider.remoteConfigured !== true || provider.localFallbackProvider === true || provider.provider === "lumira-local") {
      issues.push("strict provider remote evidence requires UP, remoteConfigured=true, and non-local provider");
    }
  }
  if (validateRemoteExpectations && artifact.expectations?.ownerGatewayRemote === true) {
    if (ownerGateway.status !== "UP" || configuredOwners.length === 0) {
      issues.push("strict owner gateway remote evidence requires UP with configured owners");
    }
  }

  return issues;
}

function validateEndpointPayload(suffix, data, issues) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    issues.push(`endpoints.${suffix}.data must be an object`);
    return;
  }
  if (suffix === "readiness") {
    if (!nonEmptyStringArray(data.apiContracts)) {
      issues.push("endpoints.readiness.data.apiContracts must be a non-empty string array");
    }
    if (!data.apiContracts?.includes("/api/v2/ai/chat")) {
      issues.push("endpoints.readiness.data.apiContracts must include /api/v2/ai/chat");
    }
  }
  if (suffix === "health") {
    const healthChecks = Array.isArray(data.healthChecks) ? data.healthChecks : [];
    if (healthChecks.length === 0) {
      issues.push("endpoints.health.data.healthChecks must be non-empty");
    }
    for (const name of ["ai.provider-runtime", "ai.remote-owner-gateway"]) {
      if (!byName(healthChecks, name)) {
        issues.push(`endpoints.health.data.healthChecks missing ${name}`);
      }
    }
  }
  if (suffix === "metrics") {
    const metrics = Array.isArray(data.metrics) ? data.metrics : [];
    if (metrics.length === 0) {
      issues.push("endpoints.metrics.data.metrics must be non-empty");
    }
    for (const name of ["ai.provider.remote_configured", "ai.owner_gateway.configured"]) {
      if (!byName(metrics, name)) {
        issues.push(`endpoints.metrics.data.metrics missing ${name}`);
      }
    }
  }
}

function nonEmptyStringArray(value) {
  return Array.isArray(value) && value.length > 0 && value.every((item) => typeof item === "string" && item.trim());
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

function byName(items, name) {
  return Array.isArray(items) ? items.find((item) => item?.name === name) : null;
}
