import { validateProductionEquivalenceEvidence } from "./ddd-release-evidence-utils.mjs";

export const requiredFileProcessingTasks = ["SECURITY_SCAN", "TEXT_EXTRACT", "AI_PARSE"];
export const requiredFileProcessingArtifacts = ["SECURITY_SCAN_RESULT", "TEXT_CONTENT", "AI_PARSE_READY"];

export function validateFileProcessingArtifact(artifact, { strict = false } = {}) {
  const issues = [];
  if (artifact?.status !== "PASS") {
    issues.push(`status=${artifact?.status ?? "missing"}`);
  }
  validateRunTiming("file processing", artifact, issues);
  issues.push(...validateProductionEquivalenceEvidence("file processing", artifact, { strict }));

  const tasks = Array.isArray(artifact?.finalState?.tasks) ? artifact.finalState.tasks : [];
  const taskByType = new Map(tasks.map((task) => [task.taskType, task]));
  if (taskByType.size !== tasks.length) {
    issues.push("file processing tasks contain duplicate taskType");
  }
  for (const task of tasks) {
    if (!requiredFileProcessingTasks.includes(task.taskType)) {
      issues.push(`unknown task ${task.taskType ?? "missing"}`);
    }
    if (!task.createdAt) {
      issues.push(`${task.taskType || "unknown"} missing createdAt`);
    }
    if (!task.claimedAt) {
      issues.push(`${task.taskType || "unknown"} missing claimedAt`);
    }
    if ((task.retryCount || 0) !== 0) {
      issues.push(`${task.taskType || "unknown"} retryCount=${task.retryCount}`);
    }
    if (task.lastError) {
      issues.push(`${task.taskType || "unknown"} lastError=${task.lastError}`);
    }
  }
  for (const taskType of requiredFileProcessingTasks) {
    const task = taskByType.get(taskType);
    if (!task) {
      issues.push(`missing task ${taskType}`);
    } else if (task.status !== "SUCCEEDED") {
      issues.push(`${taskType} status=${task.status}`);
    } else if (!task.completedAt) {
      issues.push(`${taskType} missing completedAt`);
    }
  }

  const artifacts = Array.isArray(artifact?.finalState?.artifacts) ? artifact.finalState.artifacts : [];
  const artifactTypes = new Set(artifacts.map((item) => item.artifactType));
  if (artifactTypes.size !== artifacts.length) {
    issues.push("file processing artifacts contain duplicate artifactType");
  }
  for (const item of artifacts) {
    if (!requiredFileProcessingArtifacts.includes(item.artifactType)) {
      issues.push(`unknown artifact ${item.artifactType ?? "missing"}`);
    }
    if (!item.createdAt) {
      issues.push(`${item.artifactType || "unknown"} missing createdAt`);
    }
    if (!item.updatedAt) {
      issues.push(`${item.artifactType || "unknown"} missing updatedAt`);
    }
  }
  for (const artifactType of requiredFileProcessingArtifacts) {
    const producedArtifact = artifacts.find((item) => item.artifactType === artifactType);
    if (!producedArtifact) {
      issues.push(`missing artifact ${artifactType}`);
      continue;
    }
    const expectedTaskType = taskTypeForArtifact(artifactType);
    if (producedArtifact.taskType !== expectedTaskType) {
      issues.push(`${artifactType} taskType=${producedArtifact.taskType}`);
    }
    if (!Number.isFinite(producedArtifact.contentLength) || producedArtifact.contentLength <= 0) {
      issues.push(`${artifactType} missing positive contentLength`);
    }
  }

  if (!artifact?.upload?.fileId) {
    issues.push("upload fileId is missing");
  } else {
    if (!artifact.upload.originalFileName) {
      issues.push("upload originalFileName is missing");
    }
    if (artifact.upload.fileExtension !== "txt") {
      issues.push(`upload fileExtension=${artifact.upload.fileExtension ?? "missing"}`);
    }
    if (artifact.upload.mimeType !== "text/plain") {
      issues.push(`upload mimeType=${artifact.upload.mimeType ?? "missing"}`);
    }
    if (!Number.isFinite(artifact.upload.elapsedMs) || artifact.upload.elapsedMs <= 0) {
      issues.push("upload elapsedMs must be positive");
    }
  }
  if (!Array.isArray(artifact?.jobRuns) || artifact.jobRuns.length === 0) {
    issues.push("missing file processing job run evidence");
  } else if (!artifact.jobRuns.some((run) => Number.isFinite(run.processed) && run.processed > 0)) {
    issues.push("file processing job run did not process any task");
  }
  for (const [index, run] of (artifact.jobRuns || []).entries()) {
    if (!Number.isFinite(run.elapsedMs) || run.elapsedMs <= 0) {
      issues.push(`file processing jobRuns[${index}] elapsedMs must be positive`);
    }
  }
  const beforeBacklog = artifact?.metrics?.before?.["file.processing_task.pending_backlog"];
  const afterBacklog = artifact?.metrics?.after?.["file.processing_task.pending_backlog"];
  if (Number.isFinite(beforeBacklog) && Number.isFinite(afterBacklog) && afterBacklog > beforeBacklog) {
    issues.push(`file processing pending backlog increased: before=${beforeBacklog}, after=${afterBacklog}`);
  }
  const failedBacklog = artifact?.metrics?.after?.["file.processing_task.failed_backlog"];
  const deadLetterCount = artifact?.metrics?.after?.["file.processing_task.dead_letter_count"];
  if (Number.isFinite(failedBacklog) && failedBacklog > 0) {
    issues.push(`file processing failed backlog=${failedBacklog}`);
  }
  if (Number.isFinite(deadLetterCount) && deadLetterCount > 0) {
    issues.push(`file processing dead letter count=${deadLetterCount}`);
  }

  return issues;
}

export function validatePaymentWebhookArtifact(artifact, { strict = false } = {}) {
  const issues = [];
  if (artifact?.status !== "PASS") {
    issues.push(`status=${artifact?.status ?? "missing"}`);
  }
  validateRunTiming("payment webhook", artifact, issues);
  issues.push(...validateProductionEquivalenceEvidence("payment webhook", artifact, { strict }));
  if (artifact?.finalState?.order?.status !== "PAID") {
    issues.push(`final order status=${artifact?.finalState?.order?.status}`);
  }
  if (artifact?.provider?.enabled !== true || artifact?.provider?.configured !== true) {
    issues.push("payment provider is not enabled and configured");
  }
  if (!Array.isArray(artifact?.provider?.configuredFields) || !artifact.provider.configuredFields.includes("webhookSecret")) {
    issues.push("payment provider configuredFields must include webhookSecret");
  }
  if (!Number.isFinite(artifact?.provider?.elapsedMs) || artifact.provider.elapsedMs <= 0) {
    issues.push("payment provider elapsedMs must be positive");
  }
  if (artifact?.order?.status !== "PENDING") {
    issues.push(`created order status=${artifact?.order?.status ?? "missing"}`);
  }
  if (!Number.isFinite(artifact?.order?.elapsedMs) || artifact.order.elapsedMs <= 0) {
    issues.push("payment order elapsedMs must be positive");
  }
  if (artifact?.order?.orderNo && artifact?.finalState?.order?.orderNo !== artifact.order.orderNo) {
    issues.push("final orderNo does not match created order");
  }
  if (Number.isFinite(artifact?.order?.amountMinor) && artifact?.finalState?.order?.amountMinor !== artifact.order.amountMinor) {
    issues.push("final amountMinor does not match created order");
  }
  if (artifact?.order?.currency && artifact?.finalState?.order?.currency !== artifact.order.currency) {
    issues.push("final currency does not match created order");
  }

  const first = artifact?.webhooks?.first || {};
  const duplicate = artifact?.webhooks?.duplicate || {};
  const nonceReplay = artifact?.webhooks?.nonceReplay || {};
  const badSignature = artifact?.webhooks?.badSignature || {};
  validateWebhookScenario("first", first, "payment.succeeded", "支付 webhook 已处理", issues);
  validateWebhookScenario("duplicate", duplicate, "payment.succeeded", "支付 webhook 已处理", issues);
  validateWebhookScenario("nonceReplay", nonceReplay, "payment.succeeded", "请求已被重放", issues);
  validateWebhookScenario("badSignature", badSignature, "payment.succeeded", "签名校验失败", issues);
  if (first.processed !== true || first.signatureValid !== true) {
    issues.push("valid webhook was not processed with valid signature");
  }
  if (duplicate.eventId !== first.eventId || duplicate.processed !== true || duplicate.signatureValid !== true) {
    issues.push("duplicate webhook did not return the existing processed event");
  }
  if (nonceReplay.processed !== false || nonceReplay.signatureValid !== false) {
    issues.push("nonce replay webhook was not rejected before processing");
  }
  if (badSignature.processed !== false || badSignature.signatureValid !== false) {
    issues.push("bad signature webhook was not rejected");
  }

  const rows = Array.isArray(artifact?.finalState?.webhookEvents) ? artifact.finalState.webhookEvents : [];
  const rowsById = new Map(rows.map((row) => [row.eventId, row]));
  if (rowsById.size !== rows.length) {
    issues.push("webhookEvents contain duplicate eventId rows");
  }
  if (first.eventId && !truthyDatabaseBoolean(rowsById.get(first.eventId)?.processed)) {
    issues.push("valid webhook row is not processed");
  }
  if (first.eventId && !truthyDatabaseBoolean(rowsById.get(first.eventId)?.signatureValid)) {
    issues.push("valid webhook row is not signatureValid");
  }
  if (first.eventId && !rowsById.get(first.eventId)?.processedAt) {
    issues.push("valid webhook row missing processedAt");
  }
  if (first.eventId && rowsById.get(first.eventId)?.processMessage !== "支付 webhook 已处理") {
    issues.push("valid webhook row missing processed message");
  }
  if (nonceReplay.eventId && rowsById.get(nonceReplay.eventId)?.processMessage !== "请求已被重放") {
    issues.push("nonce replay row missing expected rejection message");
  }
  if (badSignature.eventId && rowsById.get(badSignature.eventId)?.processMessage !== "签名校验失败") {
    issues.push("bad signature row missing expected rejection message");
  }
  if (first.eventId && duplicate.eventId && nonceReplay.eventId && badSignature.eventId) {
    const distinctScenarioEventIds = new Set([first.eventId, nonceReplay.eventId, badSignature.eventId]);
    if (distinctScenarioEventIds.size !== 3 || duplicate.eventId !== first.eventId) {
      issues.push("webhook scenario eventIds do not match idempotency contract");
    }
  }

  return issues;
}

function validateRunTiming(label, artifact, issues) {
  if (!artifact?.startedAt || Number.isNaN(Date.parse(artifact.startedAt))) {
    issues.push(`${label} startedAt must be an ISO-like datetime`);
  }
  if (!artifact?.finishedAt || Number.isNaN(Date.parse(artifact.finishedAt))) {
    issues.push(`${label} finishedAt must be an ISO-like datetime`);
  }
  if (!Number.isFinite(artifact?.elapsedMs) || artifact.elapsedMs <= 0) {
    issues.push(`${label} elapsedMs must be positive`);
  }
}

function validateWebhookScenario(name, scenario, expectedType, expectedMessage, issues) {
  if (!scenario.eventId) {
    issues.push(`${name} webhook eventId is required`);
  }
  if (scenario.eventType !== expectedType) {
    issues.push(`${name} webhook eventType=${scenario.eventType ?? "missing"}`);
  }
  if (scenario.processMessage !== expectedMessage) {
    issues.push(`${name} webhook processMessage=${scenario.processMessage ?? "missing"}`);
  }
  if (!Number.isFinite(scenario.elapsedMs) || scenario.elapsedMs <= 0) {
    issues.push(`${name} webhook elapsedMs must be positive`);
  }
}

function truthyDatabaseBoolean(value) {
  return value === true || value === 1 || value === "1";
}

function taskTypeForArtifact(artifactType) {
  return {
    SECURITY_SCAN_RESULT: "SECURITY_SCAN",
    TEXT_CONTENT: "TEXT_EXTRACT",
    AI_PARSE_READY: "AI_PARSE",
  }[artifactType];
}
