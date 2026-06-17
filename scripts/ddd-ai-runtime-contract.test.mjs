#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  buildAiRuntimeSummary,
  validateAiRuntimeArtifact,
} from "./ddd-ai-runtime-contract.mjs";

function validArtifact() {
  const artifact = {
    baseUrl: "https://api.staging.lumira.app",
    checkedAt: "2026-06-14T00:00:00.000Z",
    status: "PASS",
    productionEquivalence: {
      strict: true,
      https: true,
      localOnly: false,
      deploymentEvidence: "ci://deploy/123",
      issues: [],
    },
    expectations: {
      providerRemote: true,
      ownerGatewayRemote: true,
    },
    endpoints: {
      readiness: {
        status: 200,
        elapsedMs: 12,
        data: {
          apiContracts: ["/api/v2/ai/chat"],
        },
      },
      health: {
        status: 200,
        elapsedMs: 15,
        data: {
          healthChecks: [
            {
              name: "ai.provider-runtime",
              status: "UP",
              description: "provider=openai-compatible, chatModel=gpt-4.1, embeddingModel=text-embedding-3-large, remoteConfigured=true",
            },
            {
              name: "ai.remote-owner-gateway",
              status: "UP",
              description: "Configured owners=[file,payment]",
            },
          ],
        },
      },
      metrics: {
        status: 200,
        elapsedMs: 10,
        data: {
          metrics: [
            { name: "ai.provider.remote_configured", value: 1 },
            { name: "ai.owner_gateway.configured", value: 2 },
          ],
        },
      },
    },
    remoteEvidence: {
      provider: {
        status: "UP",
        provider: "openai-compatible",
        chatModel: "gpt-4.1",
        embeddingModel: "text-embedding-3-large",
        remoteConfigured: true,
        localFallbackProvider: false,
      },
      ownerGateway: {
        status: "UP",
        configuredOwners: ["file", "payment"],
        configuredOwnerCount: 2,
      },
    },
    failures: [],
    failureDetails: [],
  };
  artifact.summary = buildAiRuntimeSummary(artifact);
  return artifact;
}

assert.deepEqual(validateAiRuntimeArtifact(validArtifact(), { strict: true }), []);

assert(
  validateAiRuntimeArtifact({
    ...validArtifact(),
    productionEquivalence: undefined,
  }, { strict: true }).includes("AI runtime productionEquivalence is required for strict release evidence"),
);

{
  const artifact = validArtifact();
  artifact.productionEquivalence = {
    strict: "true",
    https: true,
    localOnly: false,
    deploymentEvidence: 42,
    issues: "none",
  };
  const issues = validateAiRuntimeArtifact(artifact, { strict: true });
  assert(issues.includes("AI runtime productionEquivalence.strict must be boolean"));
  assert(issues.includes("AI runtime productionEquivalence.strict must be true for strict release evidence"));
  assert(issues.includes("AI runtime productionEquivalence.deploymentEvidence must be string or null"));
  assert(issues.includes("AI runtime productionEquivalence.deploymentEvidence is required"));
  assert(issues.includes("AI runtime productionEquivalence.issues must be an array of strings"));
}

{
  const artifact = validArtifact();
  artifact.productionEquivalence = {
    strict: true,
    https: true,
    localOnly: false,
    deploymentEvidence: "https://example.com/deployments/123",
    issues: [],
  };
  const issues = validateAiRuntimeArtifact(artifact, { strict: true });
  assert(issues.includes("AI runtime productionEquivalence.deploymentEvidence must not contain placeholder text"));
}

{
  const artifact = validArtifact();
  artifact.productionEquivalence = {
    strict: false,
    https: true,
    localOnly: false,
    deploymentEvidence: "ci://deploy/123",
    issues: [],
  };
  const issues = validateAiRuntimeArtifact(artifact, { strict: true });
  assert(issues.includes("AI runtime productionEquivalence.strict must be true for strict release evidence"));
}

{
  const artifact = validArtifact();
  artifact.failures = ["AI runtime endpoint request failed: fetch failed"];
  artifact.failureDetails = [
    {
      message: "AI runtime endpoint request failed: fetch failed",
      category: "endpoint",
      owner: "ai",
    },
  ];
  artifact.status = "FAIL";
  artifact.summary = buildAiRuntimeSummary(artifact);
  assert.deepEqual(validateAiRuntimeArtifact(artifact, { strict: true }), []);
  assert.equal(artifact.summary.failureCategories.endpoint, 1);
}

{
  const artifact = validArtifact();
  artifact.summary.failed = 1;
  artifact.summary.failureCategories = { endpoint: 1 };
  artifact.remoteEvidence.ownerGateway.configuredOwners = ["file", "file"];
  assert.deepEqual(validateAiRuntimeArtifact(artifact, { strict: true }), [
    "summary.failed must be 0, got 1",
    "summary.failureCategories must match failureDetails categories",
    "remoteEvidence.ownerGateway configured owner duplicated: file",
  ]);
}

{
  const artifact = validArtifact();
  artifact.remoteEvidence.provider.provider = "lumira-local";
  artifact.remoteEvidence.provider.localFallbackProvider = false;
  artifact.remoteEvidence.provider.remoteConfigured = true;
  assert.deepEqual(validateAiRuntimeArtifact(artifact, { strict: true }), [
    "remoteEvidence.provider.localFallbackProvider must be true when provider=lumira-local",
    "remoteEvidence.provider.remoteConfigured=true requires a non-local provider",
    "strict provider remote evidence requires UP, remoteConfigured=true, and non-local provider",
  ]);
  assert.deepEqual(validateAiRuntimeArtifact(artifact, { strict: true, validateRemoteExpectations: false }), [
    "remoteEvidence.provider.localFallbackProvider must be true when provider=lumira-local",
    "remoteEvidence.provider.remoteConfigured=true requires a non-local provider",
  ]);
}

{
  const artifact = validArtifact();
  artifact.checkedAt = "bad";
  artifact.baseUrl = "";
  artifact.failures = ["network failure"];
  artifact.failureDetails = [{ message: "different", category: "", owner: "" }];
  artifact.status = "FAIL";
  artifact.summary = buildAiRuntimeSummary(artifact);
  assert.deepEqual(validateAiRuntimeArtifact(artifact, { strict: false }).filter((issue) => (
    issue.includes("baseUrl")
    || issue.includes("checkedAt")
    || issue.includes("failureDetails")
    || issue.includes("failures[0]")
  )), [
    "baseUrl is required",
    "checkedAt must be an ISO-like datetime",
    "failureDetails[0].category is required",
    "failureDetails[0].owner is required",
    "failures[0] must match failureDetails[0].message",
  ]);
}

{
  const artifact = validArtifact();
  artifact.endpoints.readiness.data = { apiContracts: [] };
  artifact.endpoints.health.data = { healthChecks: [{ name: "ai.provider-runtime", status: "UP" }] };
  artifact.endpoints.metrics.data = { metrics: [{ name: "ai.provider.remote_configured", value: 1 }] };
  artifact.endpoints.metrics.elapsedMs = 0;
  assert.deepEqual(validateAiRuntimeArtifact(artifact, { strict: true }).filter((issue) => issue.includes("endpoints.")), [
    "endpoints.readiness.data.apiContracts must be a non-empty string array",
    "endpoints.readiness.data.apiContracts must include /api/v2/ai/chat",
    "endpoints.health.data.healthChecks missing ai.remote-owner-gateway",
    "endpoints.metrics.elapsedMs must be positive",
    "endpoints.metrics.data.metrics missing ai.owner_gateway.configured",
  ]);
}

{
  const artifact = validArtifact();
  artifact.endpoints.health.status = null;
  assert(validateAiRuntimeArtifact(artifact, { strict: true })
    .includes("PASS artifact requires endpoints.health.status=200"));
}

console.log("[ddd-ai-runtime-contract.test] ok");
