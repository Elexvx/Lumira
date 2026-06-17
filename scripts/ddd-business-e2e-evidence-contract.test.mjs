#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  requiredFileProcessingArtifacts,
  requiredFileProcessingTasks,
  validateFileProcessingArtifact,
  validatePaymentWebhookArtifact,
} from "./ddd-business-e2e-evidence-contract.mjs";

function fileArtifact(overrides = {}) {
  return {
    status: "PASS",
    startedAt: "2026-06-14T00:00:00.000Z",
    finishedAt: "2026-06-14T00:00:01.000Z",
    elapsedMs: 1000,
    upload: {
      fileId: 1,
      elapsedMs: 10,
      originalFileName: "ddd-file-processing.txt",
      fileExtension: "txt",
      mimeType: "text/plain",
    },
    jobRuns: [
      { processed: 3, elapsedMs: 10 },
    ],
    finalState: {
      tasks: requiredFileProcessingTasks.map((taskType) => ({
        taskType,
        status: "SUCCEEDED",
        createdAt: "2026-06-14T00:00:00",
        claimedAt: "2026-06-14T00:00:00",
        retryCount: 0,
        lastError: null,
        completedAt: "2026-06-14T00:00:00",
      })),
      artifacts: requiredFileProcessingArtifacts.map((artifactType) => ({
        artifactType,
        taskType: {
          SECURITY_SCAN_RESULT: "SECURITY_SCAN",
          TEXT_CONTENT: "TEXT_EXTRACT",
          AI_PARSE_READY: "AI_PARSE",
        }[artifactType],
        createdAt: "2026-06-14T00:00:00",
        updatedAt: "2026-06-14T00:00:00",
        contentLength: 10,
      })),
    },
    metrics: {
      before: {
        "file.processing_task.pending_backlog": 3,
      },
      after: {
        "file.processing_task.pending_backlog": 0,
        "file.processing_task.failed_backlog": 0,
        "file.processing_task.dead_letter_count": 0,
      },
    },
    ...overrides,
  };
}

function paymentArtifact(overrides = {}) {
  return {
    status: "PASS",
    startedAt: "2026-06-14T00:00:00.000Z",
    finishedAt: "2026-06-14T00:00:01.000Z",
    elapsedMs: 1000,
    provider: {
      elapsedMs: 10,
      enabled: true,
      configured: true,
      configuredFields: ["clientId", "secretKey", "webhookSecret", "currency", "sandboxEnabled"],
    },
    order: {
      elapsedMs: 10,
      orderNo: "DDD-PAY-1",
      status: "PENDING",
      amountMinor: 199,
      currency: "USD",
    },
    finalState: {
      order: {
        status: "PAID",
        orderNo: "DDD-PAY-1",
        amountMinor: 199,
        currency: "USD",
      },
      webhookEvents: [
        { eventId: "evt-valid", processed: 1, signatureValid: 1, processedAt: "2026-06-14T00:00:00", processMessage: "支付 webhook 已处理" },
        { eventId: "evt-replay", processed: 0, signatureValid: 0, processMessage: "请求已被重放" },
        { eventId: "evt-bad", processed: 0, signatureValid: 0, processMessage: "签名校验失败" },
      ],
    },
    webhooks: {
      first: { elapsedMs: 10, eventId: "evt-valid", eventType: "payment.succeeded", processed: true, signatureValid: true, processMessage: "支付 webhook 已处理" },
      duplicate: { elapsedMs: 10, eventId: "evt-valid", eventType: "payment.succeeded", processed: true, signatureValid: true, processMessage: "支付 webhook 已处理" },
      nonceReplay: { elapsedMs: 10, eventId: "evt-replay", eventType: "payment.succeeded", processed: false, signatureValid: false, processMessage: "请求已被重放" },
      badSignature: { elapsedMs: 10, eventId: "evt-bad", eventType: "payment.succeeded", processed: false, signatureValid: false, processMessage: "签名校验失败" },
    },
    ...overrides,
  };
}

assert.deepEqual(validateFileProcessingArtifact(fileArtifact()), []);
assert.deepEqual(validatePaymentWebhookArtifact(paymentArtifact()), []);
assert(validateFileProcessingArtifact(fileArtifact(), { strict: true })
  .includes("file processing productionEquivalence is required for strict release evidence"));
assert(validatePaymentWebhookArtifact(paymentArtifact(), { strict: true })
  .includes("payment webhook productionEquivalence is required for strict release evidence"));

{
  const issues = validateFileProcessingArtifact(fileArtifact({
    productionEquivalence: {
      strict: false,
      https: "yes",
      localOnly: "no",
      deploymentEvidence: 42,
      issues: "none",
    },
  }), { strict: true });
  assert(issues.includes("file processing productionEquivalence.strict must be true for strict release evidence"));
  assert(issues.includes("file processing productionEquivalence.https must be boolean"));
  assert(issues.includes("file processing productionEquivalence.localOnly must be boolean"));
  assert(issues.includes("file processing productionEquivalence.deploymentEvidence must be string or null"));
  assert(issues.includes("file processing productionEquivalence.deploymentEvidence is required"));
  assert(issues.includes("file processing productionEquivalence.issues must be an array"));
}

{
  const issues = validatePaymentWebhookArtifact(paymentArtifact({
    productionEquivalence: {
      strict: false,
      https: "yes",
      localOnly: "no",
      deploymentEvidence: 42,
      issues: "none",
    },
  }), { strict: true });
  assert(issues.includes("payment webhook productionEquivalence.strict must be true for strict release evidence"));
  assert(issues.includes("payment webhook productionEquivalence.https must be boolean"));
  assert(issues.includes("payment webhook productionEquivalence.localOnly must be boolean"));
  assert(issues.includes("payment webhook productionEquivalence.deploymentEvidence must be string or null"));
  assert(issues.includes("payment webhook productionEquivalence.deploymentEvidence is required"));
  assert(issues.includes("payment webhook productionEquivalence.issues must be an array"));
}

{
  const issues = validateFileProcessingArtifact(fileArtifact({
    productionEquivalence: {
      strict: true,
      https: true,
      localOnly: false,
      deploymentEvidence: "https://example.com/deployments/123",
      issues: [],
    },
  }), { strict: true });
  assert(issues.includes("file processing productionEquivalence.deploymentEvidence must not contain placeholder text"));
}

assert.deepEqual(validateFileProcessingArtifact(fileArtifact({
  status: "FAIL",
  upload: {},
  jobRuns: [{ processed: 0 }],
  finalState: {
    tasks: [
      { taskType: "SECURITY_SCAN", status: "FAILED" },
      { taskType: "TEXT_EXTRACT", status: "SUCCEEDED", completedAt: "2026-06-14T00:00:00" },
    ],
    artifacts: [{ artifactType: "TEXT_CONTENT", taskType: "TEXT_EXTRACT", contentLength: 10 }],
  },
})), [
  "status=FAIL",
  "SECURITY_SCAN missing createdAt",
  "SECURITY_SCAN missing claimedAt",
  "TEXT_EXTRACT missing createdAt",
  "TEXT_EXTRACT missing claimedAt",
  "SECURITY_SCAN status=FAILED",
  "missing task AI_PARSE",
  "TEXT_CONTENT missing createdAt",
  "TEXT_CONTENT missing updatedAt",
  "missing artifact SECURITY_SCAN_RESULT",
  "missing artifact AI_PARSE_READY",
  "upload fileId is missing",
  "file processing job run did not process any task",
  "file processing jobRuns[0] elapsedMs must be positive",
]);

assert(
  validatePaymentWebhookArtifact(paymentArtifact({
    finalState: {
      order: { status: "CREATED" },
      webhookEvents: [],
    },
  })).includes("final order status=CREATED"),
);

assert(
  validatePaymentWebhookArtifact(paymentArtifact({
    webhooks: {
      first: { eventId: "evt-valid", processed: false, signatureValid: true },
      duplicate: { eventId: "evt-other", processed: true, signatureValid: true },
      nonceReplay: { eventId: "evt-replay", processed: true, signatureValid: false },
      badSignature: { eventId: "evt-bad", processed: false, signatureValid: true },
    },
  })).includes("valid webhook was not processed with valid signature"),
);

assert.deepEqual(validateFileProcessingArtifact(fileArtifact({
  finalState: {
    tasks: [
      { taskType: "SECURITY_SCAN", status: "SUCCEEDED", completedAt: "2026-06-14T00:00:00" },
      { taskType: "SECURITY_SCAN", status: "SUCCEEDED", completedAt: "2026-06-14T00:00:00" },
      { taskType: "TEXT_EXTRACT", status: "SUCCEEDED", completedAt: "2026-06-14T00:00:00" },
      { taskType: "AI_PARSE", status: "SUCCEEDED", completedAt: "2026-06-14T00:00:00" },
    ],
    artifacts: [
      { artifactType: "SECURITY_SCAN_RESULT", taskType: "TEXT_EXTRACT", contentLength: 10 },
      { artifactType: "SECURITY_SCAN_RESULT", taskType: "SECURITY_SCAN", contentLength: 10 },
      { artifactType: "TEXT_CONTENT", taskType: "TEXT_EXTRACT", contentLength: 0 },
      { artifactType: "AI_PARSE_READY", taskType: "AI_PARSE", contentLength: 10 },
    ],
  },
})), [
  "file processing tasks contain duplicate taskType",
  "SECURITY_SCAN missing createdAt",
  "SECURITY_SCAN missing claimedAt",
  "SECURITY_SCAN missing createdAt",
  "SECURITY_SCAN missing claimedAt",
  "TEXT_EXTRACT missing createdAt",
  "TEXT_EXTRACT missing claimedAt",
  "AI_PARSE missing createdAt",
  "AI_PARSE missing claimedAt",
  "file processing artifacts contain duplicate artifactType",
  "SECURITY_SCAN_RESULT missing createdAt",
  "SECURITY_SCAN_RESULT missing updatedAt",
  "SECURITY_SCAN_RESULT missing createdAt",
  "SECURITY_SCAN_RESULT missing updatedAt",
  "TEXT_CONTENT missing createdAt",
  "TEXT_CONTENT missing updatedAt",
  "AI_PARSE_READY missing createdAt",
  "AI_PARSE_READY missing updatedAt",
  "SECURITY_SCAN_RESULT taskType=TEXT_EXTRACT",
  "TEXT_CONTENT missing positive contentLength",
]);

assert.deepEqual(validatePaymentWebhookArtifact(paymentArtifact({
  provider: {
    enabled: false,
    configured: true,
    configuredFields: ["clientId"],
  },
  order: {
    orderNo: "DDD-PAY-1",
    status: "CREATED",
    amountMinor: 199,
    currency: "USD",
  },
  finalState: {
    order: {
      status: "PAID",
      orderNo: "DDD-PAY-2",
      amountMinor: 200,
      currency: "EUR",
    },
    webhookEvents: [
      { eventId: "evt-valid", processed: 1, signatureValid: 1, processedAt: "2026-06-14T00:00:00", processMessage: "支付 webhook 已处理" },
      { eventId: "evt-valid", processed: 1, signatureValid: 1, processedAt: "2026-06-14T00:00:00", processMessage: "支付 webhook 已处理" },
      { eventId: "evt-replay", processed: 0, signatureValid: 0, processMessage: "请求已被重放" },
      { eventId: "evt-bad", processed: 0, signatureValid: 0, processMessage: "签名校验失败" },
    ],
  },
})).filter((issue) => issue.includes("provider")
  || issue.includes("created order")
  || issue.includes("orderNo")
  || issue.includes("amountMinor")
  || issue.includes("currency")
  || issue.includes("duplicate eventId")), [
    "payment provider is not enabled and configured",
    "payment provider configuredFields must include webhookSecret",
    "payment provider elapsedMs must be positive",
    "created order status=CREATED",
    "final orderNo does not match created order",
    "final amountMinor does not match created order",
    "final currency does not match created order",
    "webhookEvents contain duplicate eventId rows",
  ]);

{
  const issues = validateFileProcessingArtifact(fileArtifact({
    upload: {
      fileId: 1,
      elapsedMs: 0,
      originalFileName: "",
      fileExtension: "pdf",
      mimeType: "application/pdf",
    },
    finalState: {
      tasks: [
        ...fileArtifact().finalState.tasks,
        { taskType: "UNKNOWN_TASK", status: "SUCCEEDED", createdAt: "2026-06-14T00:00:00", claimedAt: "2026-06-14T00:00:00", completedAt: "2026-06-14T00:00:00", retryCount: 1, lastError: "boom" },
      ],
      artifacts: [
        ...fileArtifact().finalState.artifacts,
        { artifactType: "UNKNOWN_ARTIFACT", taskType: "UNKNOWN_TASK", createdAt: "2026-06-14T00:00:00", updatedAt: "2026-06-14T00:00:00", contentLength: 1 },
      ],
    },
    metrics: {
      before: { "file.processing_task.pending_backlog": 1 },
      after: {
        "file.processing_task.pending_backlog": 2,
        "file.processing_task.failed_backlog": 1,
        "file.processing_task.dead_letter_count": 1,
      },
    },
  }));
  assert(issues.includes("unknown task UNKNOWN_TASK"));
  assert(issues.includes("UNKNOWN_TASK retryCount=1"));
  assert(issues.includes("UNKNOWN_TASK lastError=boom"));
  assert(issues.includes("unknown artifact UNKNOWN_ARTIFACT"));
  assert(issues.includes("upload originalFileName is missing"));
  assert(issues.includes("upload fileExtension=pdf"));
  assert(issues.includes("upload mimeType=application/pdf"));
  assert(issues.includes("upload elapsedMs must be positive"));
  assert(issues.includes("file processing pending backlog increased: before=1, after=2"));
  assert(issues.includes("file processing failed backlog=1"));
  assert(issues.includes("file processing dead letter count=1"));
}

{
  const issues = validatePaymentWebhookArtifact(paymentArtifact({
    startedAt: "bad",
    finishedAt: "",
    elapsedMs: 0,
    provider: {
      enabled: true,
      configured: true,
      configuredFields: ["webhookSecret"],
      elapsedMs: 0,
    },
    order: {
      elapsedMs: 0,
      orderNo: "DDD-PAY-1",
      status: "FAILED",
      amountMinor: 199,
      currency: "USD",
    },
    webhooks: {
      first: { eventId: "", eventType: "payment.failed", processed: true, signatureValid: true, processMessage: "wrong", elapsedMs: 0 },
      duplicate: { eventId: "evt-valid", eventType: "payment.failed", processed: true, signatureValid: true, processMessage: "wrong", elapsedMs: 0 },
      nonceReplay: { eventId: "evt-replay", eventType: "payment.failed", processed: false, signatureValid: false, processMessage: "wrong", elapsedMs: 0 },
      badSignature: { eventId: "evt-bad", eventType: "payment.failed", processed: false, signatureValid: false, processMessage: "wrong", elapsedMs: 0 },
    },
  }));
  assert(issues.includes("payment webhook startedAt must be an ISO-like datetime"));
  assert(issues.includes("payment webhook finishedAt must be an ISO-like datetime"));
  assert(issues.includes("payment webhook elapsedMs must be positive"));
  assert(issues.includes("payment provider elapsedMs must be positive"));
  assert(issues.includes("created order status=FAILED"));
  assert(issues.includes("payment order elapsedMs must be positive"));
  assert(issues.includes("first webhook eventId is required"));
  assert(issues.includes("first webhook eventType=payment.failed"));
  assert(issues.includes("first webhook processMessage=wrong"));
  assert(issues.includes("first webhook elapsedMs must be positive"));
  assert(issues.includes("nonceReplay webhook processMessage=wrong"));
  assert(issues.includes("badSignature webhook processMessage=wrong"));
}

console.log("[ddd-business-e2e-evidence-contract.test] ok");
