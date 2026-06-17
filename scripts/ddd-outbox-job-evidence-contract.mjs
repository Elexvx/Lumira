import { validateProductionEquivalenceEvidence } from "./ddd-release-evidence-utils.mjs";

export const requiredOutboxReplayTestClasses = [
  "com.lumira.message.app.PlatformEventOutboxServiceTest",
  "com.lumira.saas.infrastructure.event.PlatformEventOutboxServiceTest",
  "com.lumira.saas.infrastructure.event.PlatformEventOutboxRelayTest",
  "com.lumira.file.event.PlatformEventOutboxRelayServiceTest",
  "com.lumira.file.event.FileOutboxRelayTest",
  "com.lumira.payment.service.PaymentOutboxServiceTest",
  "com.lumira.payment.service.PaymentOutboxRelayTest",
  "com.lumira.saas.modules.plugin.event.PluginOutboxServiceTest",
];

export const requiredOutboxReplayContracts = [
  "outbox claim prevents duplicate dispatch",
  "successful dispatch marks delivered",
  "dispatcher failure increments retry_count",
  "retry_count >= 8 moves event to DEAD_LETTER",
  "manual replay resets event to pending/recorded state before redispatch",
  "relay disabled still allows explicit replay",
];

export const requiredJobSmokeEndpoints = [
  { name: "platform-outbox-relay", path: "/internal/jobs/outbox/relay", dataType: "boolean" },
  { name: "platform-online-session-heartbeat", path: "/internal/jobs/online-session/heartbeat", dataType: "boolean" },
  { name: "ai-knowledge-index", path: "/internal/jobs/ai/knowledge-index?limit=5", dataType: "number" },
  { name: "message-heartbeat", path: "/message/internal/jobs/message/heartbeat", dataType: "boolean" },
  { name: "message-outbox-relay", path: "/message/internal/jobs/outbox/relay", dataType: "number" },
  { name: "file-outbox-relay", path: "/file/internal/jobs/outbox/relay", dataType: "number" },
  { name: "file-processing-run", path: "/file/internal/jobs/processing/run?limit=5", dataType: "number" },
  { name: "payment-outbox-relay", path: "/payment/internal/jobs/outbox/relay", dataType: "number" },
  { name: "plugin-outbox-relay", path: "/plugin/internal/jobs/outbox/relay", dataType: "number" },
];

export function validateOutboxReplayArtifact(artifact) {
  const issues = [];
  if (artifact?.status !== "PASS") {
    issues.push(`status=${artifact?.status ?? "missing"}`);
  }
  if (!artifact?.command) {
    issues.push("outbox replay command is required");
  }
  if (!Number.isFinite(artifact?.elapsedMs) || artifact.elapsedMs <= 0) {
    issues.push("outbox replay elapsedMs must be positive");
  }
  if (!artifact?.startedAt || Number.isNaN(Date.parse(artifact.startedAt))) {
    issues.push("outbox replay startedAt must be an ISO-like datetime");
  }
  if (!artifact?.finishedAt || Number.isNaN(Date.parse(artifact.finishedAt))) {
    issues.push("outbox replay finishedAt must be an ISO-like datetime");
  }

  const reports = Array.isArray(artifact?.reports) ? artifact.reports : [];
  const reportByClass = new Map(reports.map((report) => [report.className, report]));
  if (reportByClass.size !== reports.length) {
    issues.push("outbox replay reports contain duplicate className");
  }
  for (const report of reports) {
    if (!requiredOutboxReplayTestClasses.includes(report.className)) {
      issues.push(`unknown owner relay report ${report.className ?? "missing"}`);
    }
    if (!report.reportPath) {
      issues.push(`${report.className || "unknown"} reportPath is required`);
    }
    if (!Number.isFinite(report.timeSeconds) || report.timeSeconds < 0) {
      issues.push(`${report.className || "unknown"} timeSeconds must be non-negative`);
    }
  }
  const testedContracts = Array.isArray(artifact?.testedContracts) ? artifact.testedContracts : [];
  const duplicateContracts = duplicates(testedContracts);
  for (const duplicate of duplicateContracts) {
    issues.push(`duplicate tested outbox contract ${duplicate}`);
  }
  for (const contract of requiredOutboxReplayContracts) {
    if (!testedContracts.includes(contract)) {
      issues.push(`missing tested outbox contract ${contract}`);
    }
  }
  for (const contract of testedContracts) {
    if (!requiredOutboxReplayContracts.includes(contract)) {
      issues.push(`unknown tested outbox contract ${contract}`);
    }
  }
  for (const className of requiredOutboxReplayTestClasses) {
    const report = reportByClass.get(className);
    if (!report) {
      issues.push(`missing owner relay report ${className}`);
      continue;
    }
    if (!report.present) {
      issues.push(`${className} report is not present`);
    }
    if (!Number.isFinite(report.tests) || report.tests <= 0) {
      issues.push(`${className} tests must be positive`);
    }
    if ((report.failures || 0) > 0 || (report.errors || 0) > 0) {
      issues.push(`${className} failures=${report.failures || 0}, errors=${report.errors || 0}`);
    }
    if ((report.skipped || 0) > 0) {
      issues.push(`${className} skipped=${report.skipped}`);
    }
  }

  return issues;
}

export function validateJobE2eArtifact(artifact, { strict = false } = {}) {
  const issues = [];
  if (!artifact?.baseUrl) {
    issues.push("job E2E baseUrl is required");
  }
  if (!artifact?.checkedAt || Number.isNaN(Date.parse(artifact.checkedAt))) {
    issues.push("job E2E checkedAt must be an ISO-like datetime");
  }
  issues.push(...validateProductionEquivalenceEvidence("job E2E", artifact, { strict }));
  if ((artifact?.summary?.failed || 0) !== 0) {
    issues.push(`failed=${artifact?.summary?.failed}`);
  }
  if (!artifact?.unauthorized || artifact.unauthorized.status < 400) {
    issues.push("internal endpoint accepted unauthenticated job request");
  }
  if (artifact?.unauthorized && artifact.unauthorized.path !== requiredJobSmokeEndpoints[0].path) {
    issues.push(`unauthorized probe path must be ${requiredJobSmokeEndpoints[0].path}, got ${artifact.unauthorized.path ?? "missing"}`);
  }

  const endpoints = Array.isArray(artifact?.endpoints) ? artifact.endpoints : [];
  const endpointByName = new Map(endpoints.map((endpoint) => [endpoint.name, endpoint]));
  if (endpointByName.size !== endpoints.length) {
    issues.push("job endpoints contain duplicate name");
  }
  const requiredNames = new Set(requiredJobSmokeEndpoints.map((endpoint) => endpoint.name));
  for (const endpoint of endpoints) {
    if (!requiredNames.has(endpoint.name)) {
      issues.push(`unknown job endpoint result ${endpoint.name ?? "missing"}`);
    }
    if (endpoint.status !== 200) {
      issues.push(`${endpoint.name || "unknown"} status=${endpoint.status}`);
    }
    if (!Number.isFinite(endpoint.elapsedMs) || endpoint.elapsedMs <= 0) {
      issues.push(`${endpoint.name || "unknown"} missing positive elapsedMs`);
    }
  }
  if (endpoints.length !== requiredJobSmokeEndpoints.length) {
    issues.push(`job endpoint result count must be ${requiredJobSmokeEndpoints.length}, got ${endpoints.length}`);
  }
  if (artifact?.summary?.total !== endpoints.length) {
    issues.push(`summary.total must be ${endpoints.length}, got ${artifact?.summary?.total ?? "missing"}`);
  }
  const failedEndpoints = endpoints.filter((endpoint) => endpoint.status !== 200).length;
  if ((artifact?.summary?.failed ?? 0) !== failedEndpoints) {
    issues.push(`summary.failed must be ${failedEndpoints}, got ${artifact?.summary?.failed ?? "missing"}`);
  }
  const maxElapsedMs = endpoints.reduce((max, endpoint) => (
    Number.isFinite(endpoint.elapsedMs) ? Math.max(max, endpoint.elapsedMs) : max
  ), 0);
  if (!Number.isFinite(artifact?.summary?.maxElapsedMs)) {
    issues.push("summary.maxElapsedMs is required");
  } else if (Math.abs(artifact.summary.maxElapsedMs - maxElapsedMs) > 0.01) {
    issues.push(`summary.maxElapsedMs must be ${maxElapsedMs}, got ${artifact.summary.maxElapsedMs}`);
  }
  for (const required of requiredJobSmokeEndpoints) {
    const endpoint = endpointByName.get(required.name);
    if (!endpoint) {
      issues.push(`missing job endpoint result ${required.name}`);
      continue;
    }
    if (endpoint.path !== required.path) {
      issues.push(`${required.name} path=${endpoint.path}`);
    }
    if (endpoint.status !== 200) {
      issues.push(`${required.name} status=${endpoint.status}`);
    }
    if (required.dataType === "boolean" && typeof endpoint.data !== "boolean") {
      issues.push(`${required.name} expected boolean data`);
    }
    if (required.dataType === "number" && typeof endpoint.data !== "number") {
      issues.push(`${required.name} expected numeric data`);
    }
  }

  if (artifact?.diagnostics?.outboxOwnership?.crossOwnerPayloadFailuresDelta > 0) {
    issues.push("cross-owner outbox payload failure count increased");
  }

  return issues;
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
