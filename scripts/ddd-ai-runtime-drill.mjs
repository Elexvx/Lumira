#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import {
  buildProductionEquivalenceEvidence,
  requireRuntimeProvenanceWhenStrict,
} from "./ddd-release-evidence-utils.mjs";

const baseUrl = (process.env.LUMIRA_AI_BASE_URL || process.env.LUMIRA_BASE_URL || "http://127.0.0.1:8080").replace(/\/+$/, "");
const outputDir = path.resolve(process.env.DDD_AI_RUNTIME_DRILL_DIR || path.join("artifacts", "ddd", "ai"));
const outputFile = path.join(outputDir, "ai-runtime-drill.json");
const timeoutMs = Number(process.env.DDD_AI_RUNTIME_DRILL_TIMEOUT_MS || "5000");
const authToken = process.env.LUMIRA_AUTH_TOKEN || "";
const expectProviderRemote = process.env.DDD_AI_EXPECT_PROVIDER_REMOTE === "true";
const expectOwnerGatewayRemote = process.env.DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE === "true";
const sourceEnvironment = process.env.DDD_AI_RUNTIME_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === "true" || process.env.DDD_AI_RUNTIME_STRICT === "true";
const deploymentEvidence = process.env.DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || "";

const failures = [];
const failureDetails = [];

function fail(message) {
  failures.push(message);
  failureDetails.push({
    message,
    category: categorizeFailure(message),
    owner: ownerForFailure(message),
  });
}

function categorizeFailure(message) {
  if (message.includes("runtime provenance")) {
    return "provenance";
  }
  if (message.includes("endpoint request failed") || message.includes("returned HTTP") || message.includes("ApiResponse envelope")) {
    return "endpoint";
  }
  if (message.includes("readiness is missing contract")) {
    return "api-contract";
  }
  if (message.includes("health is missing")) {
    return "health";
  }
  if (message.includes("metrics is missing")) {
    return "metrics";
  }
  if (message.includes("provider remote drill") || message.includes("provider-runtime")) {
    return "provider-runtime";
  }
  if (message.includes("owner gateway remote drill") || message.includes("remote-owner-gateway")) {
    return "owner-gateway";
  }
  return "unknown";
}

function ownerForFailure(message) {
  if (message.includes("runtime provenance")) {
    return "release-infra";
  }
  if (message.includes("provider") || message.includes("provider-runtime")) {
    return "ai-provider";
  }
  if (message.includes("owner gateway") || message.includes("owner_gateway") || message.includes("remote-owner-gateway")) {
    return "ai-owner-integrations";
  }
  return "ai";
}

function productionEquivalence() {
  return buildProductionEquivalenceEvidence({
    strict: strictEvidence,
    baseUrl,
    deploymentEvidence,
    evidenceName: "AI runtime drill",
  });
}

for (const issue of requireRuntimeProvenanceWhenStrict({
  strict: strictEvidence,
  sourceEnvironment,
  releaseCandidate,
  evidenceOperator,
})) {
  fail(`runtime provenance ${issue}`);
}

async function fetchJson(endpoint) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const startedAt = performance.now();
    const response = await fetch(`${baseUrl}${endpoint}`, {
      signal: controller.signal,
      headers: {
        Accept: "application/json",
        ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
      },
    });
    const elapsedMs = Math.round((performance.now() - startedAt) * 100) / 100;
    const text = await response.text();
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      // Caller reports invalid body shape.
    }
    return { endpoint, status: response.status, ok: response.ok, elapsedMs, text, json };
  } finally {
    clearTimeout(timer);
  }
}

function assertEnvelope(result) {
  if (!result) {
    return false;
  }
  let valid = true;
  if (!result.ok) {
    fail(`${result.endpoint} returned HTTP ${result.status}`);
    valid = false;
  }
  if (!result.json || !result.json.data || result.json.httpStatus !== 200) {
    fail(`${result.endpoint} did not return a successful ApiResponse envelope`);
    valid = false;
  }
  return valid;
}

function byName(items, name) {
  return (items || []).find((item) => item.name === name);
}

function descriptionHas(check, text) {
  return String(check?.description || "").includes(text);
}

function parseProviderDescription(description) {
  const text = String(description || "");
  return {
    provider: text.match(/provider=([^,]+)/)?.[1] || null,
    chatModel: text.match(/chatModel=([^,]+)/)?.[1] || null,
    embeddingModel: text.match(/embeddingModel=([^,]+)/)?.[1] || null,
    remoteConfigured: text.includes("remoteConfigured=true"),
  };
}

function parseConfiguredOwners(description) {
  const match = String(description || "").match(/Configured owners=\[([^\]]*)\]/);
  if (!match) {
    return [];
  }
  return match[1]
    .split(",")
    .map((owner) => owner.trim())
    .filter(Boolean);
}

function buildArtifact({ readiness = null, health = null, metrics = null }) {
  const readinessData = readiness?.json?.data || {};
  const healthData = health?.json?.data || {};
  const metricsData = metrics?.json?.data || {};
  const healthChecks = healthData.healthChecks || [];
  const providerHealth = byName(healthChecks, "ai.provider-runtime");
  const ownerGatewayHealth = byName(healthChecks, "ai.remote-owner-gateway");
  const provider = parseProviderDescription(providerHealth?.description);
  const configuredOwners = parseConfiguredOwners(ownerGatewayHealth?.description);
  return {
  baseUrl,
  checkedAt: new Date().toISOString(),
  status: failures.length === 0 ? "PASS" : "FAIL",
  sourceEnvironment: sourceEnvironment || null,
  releaseCandidate: releaseCandidate || null,
  evidenceOperator: evidenceOperator || null,
  productionEquivalence: productionEquivalence(),
  expectations: {
    providerRemote: expectProviderRemote,
    ownerGatewayRemote: expectOwnerGatewayRemote,
  },
  endpoints: {
    readiness: {
      status: readiness?.status || null,
      elapsedMs: readiness?.elapsedMs || null,
      data: readinessData,
    },
    health: {
      status: health?.status || null,
      elapsedMs: health?.elapsedMs || null,
      data: healthData,
    },
    metrics: {
      status: metrics?.status || null,
      elapsedMs: metrics?.elapsedMs || null,
      data: metricsData,
    },
  },
  summary: {
    failed: failures.length,
    failureCategories: failureDetails.reduce((counts, detail) => {
      counts[detail.category] = (counts[detail.category] || 0) + 1;
      return counts;
    }, {}),
    providerRuntimeStatus: providerHealth?.status || null,
    providerRuntimeDescription: providerHealth?.description || null,
    ownerGatewayStatus: ownerGatewayHealth?.status || null,
    ownerGatewayDescription: ownerGatewayHealth?.description || null,
  },
  remoteEvidence: {
    provider: {
      status: providerHealth?.status || null,
      provider: provider.provider,
      chatModel: provider.chatModel,
      embeddingModel: provider.embeddingModel,
      remoteConfigured: provider.remoteConfigured,
      localFallbackProvider: provider.provider === "lumira-local",
    },
    ownerGateway: {
      status: ownerGatewayHealth?.status || null,
      configuredOwners,
      configuredOwnerCount: configuredOwners.length,
    },
  },
  failures,
  failureDetails,
};
}

async function main() {
  if (failures.length > 0) {
    const artifact = buildArtifact({});
    fs.mkdirSync(outputDir, { recursive: true });
    fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);
    for (const failure of failures) {
      console.error(`[ddd-ai-runtime-drill] ${failure}`);
    }
    console.error(`[ddd-ai-runtime-drill] wrote artifact to ${outputFile}`);
    process.exit(1);
  }
  let readiness = null;
  let health = null;
  let metrics = null;
  try {
    readiness = await fetchJson("/api/v2/ai/readiness");
    health = await fetchJson("/api/v2/ai/health");
    metrics = await fetchJson("/api/v2/ai/metrics");
  } catch (error) {
    fail(`AI runtime endpoint request failed: ${error instanceof Error ? error.message : String(error)}`);
  }

  const readinessEnvelopeOk = assertEnvelope(readiness);
  const healthEnvelopeOk = assertEnvelope(health);
  const metricsEnvelopeOk = assertEnvelope(metrics);

  const readinessData = readinessEnvelopeOk ? readiness.json.data : {};
  const healthData = healthEnvelopeOk ? health.json.data : {};
  const metricsData = metricsEnvelopeOk ? metrics.json.data : {};
  const apiContracts = readinessData.apiContracts || [];
  const healthChecks = healthData.healthChecks || [];
  const metricRows = metricsData.metrics || [];
  const providerHealth = byName(healthChecks, "ai.provider-runtime");
  const ownerGatewayHealth = byName(healthChecks, "ai.remote-owner-gateway");
  const providerMetric = byName(metricRows, "ai.provider.remote_configured");
  const ownerGatewayMetric = byName(metricRows, "ai.owner_gateway.configured");

  if (readinessEnvelopeOk) {
    for (const contract of [
      "/api/v2/ai/chat",
      "/api/v2/ai/knowledge-bases/search",
      "/api/v2/ai/tools/execute",
    ]) {
      if (!apiContracts.includes(contract)) {
        fail(`AI readiness is missing contract ${contract}`);
      }
    }
  }

  if (healthEnvelopeOk) {
    if (!providerHealth) {
      fail("AI health is missing ai.provider-runtime");
    }
    if (!ownerGatewayHealth) {
      fail("AI health is missing ai.remote-owner-gateway");
    }
  }
  if (metricsEnvelopeOk) {
    if (!providerMetric) {
      fail("AI metrics is missing ai.provider.remote_configured");
    }
    if (!ownerGatewayMetric) {
      fail("AI metrics is missing ai.owner_gateway.configured");
    }
  }

  if (expectProviderRemote && healthEnvelopeOk) {
    if (providerHealth?.status !== "UP" || !descriptionHas(providerHealth, "remoteConfigured=true")) {
      fail("AI provider remote drill expected ai.provider-runtime UP with remoteConfigured=true");
    }
  }
  if (expectOwnerGatewayRemote && healthEnvelopeOk) {
    if (ownerGatewayHealth?.status !== "UP") {
      fail("AI owner gateway remote drill expected ai.remote-owner-gateway UP");
    }
  }

  const artifact = buildArtifact({ readiness, health, metrics });
  fs.mkdirSync(outputDir, { recursive: true });
  fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);

  if (failures.length > 0) {
    for (const failure of failures) {
      console.error(`[ddd-ai-runtime-drill] ${failure}`);
    }
    console.error(`[ddd-ai-runtime-drill] wrote artifact to ${outputFile}`);
    process.exit(1);
  }

  console.log(`[ddd-ai-runtime-drill] validated AI runtime; provider=${artifact.summary.providerRuntimeStatus}; ownerGateway=${artifact.summary.ownerGatewayStatus}; artifact=${outputFile}`);
}

main().catch((error) => {
  fail(`AI runtime drill failed unexpectedly: ${error instanceof Error ? error.message : String(error)}`);
  const artifact = buildArtifact({});
  fs.mkdirSync(outputDir, { recursive: true });
  fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);
  console.error(`[ddd-ai-runtime-drill] ${failures[failures.length - 1]}`);
  console.error(`[ddd-ai-runtime-drill] wrote artifact to ${outputFile}`);
  process.exit(1);
});
