#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { performance } from "node:perf_hooks";

const baseUrl = process.env.LUMIRA_BASE_URL || "http://127.0.0.1:8080";
const durationMs = Number(process.env.DDD_SMOKE_DURATION_MS || "15000");
const concurrency = Number(process.env.DDD_SMOKE_CONCURRENCY || "8");
const maxP95RegressionRatio = Number(process.env.DDD_PERF_MAX_P95_REGRESSION_RATIO || "0.10");
const baselineFile = process.env.DDD_PERF_BASELINE_FILE ? path.resolve(process.env.DDD_PERF_BASELINE_FILE) : "";
const actualFile = process.env.DDD_PERF_ACTUAL_FILE ? path.resolve(process.env.DDD_PERF_ACTUAL_FILE) : "";
const scenariosFile = process.env.DDD_SMOKE_SCENARIOS_FILE ? path.resolve(process.env.DDD_SMOKE_SCENARIOS_FILE) : "";

const authToken = process.env.LUMIRA_AUTH_TOKEN || "";
const samples = [];
const endpointSamples = new Map();
const endpointStatusCounts = new Map();
const endpointErrorCounts = new Map();
let ok = 0;
let failed = 0;
let next = 0;
const deadline = Date.now() + durationMs;

function defaultEndpoints() {
  return (process.env.DDD_SMOKE_ENDPOINTS || [
    "GET /api/v1/public/bootstrap",
    "GET /api/v1/auth/current-user",
    "GET /api/v1/system/runtime-appearance-settings",
    "GET /api/v1/plugins/current/bootstrap",
    "GET /api/v1/messages/unread-count",
    "GET /api/v1/localization/bundles?locale=zh-CN&namespace=common",
    "GET /api/v1/files",
    "GET /api/v1/payments/providers",
  ].join(","))
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => {
      const [method, ...pathParts] = entry.split(/\s+/);
      return normalizeEndpoint({ method, path: pathParts.join(" ") });
    });
}

function readScenarios(file) {
  let parsed;
  try {
    parsed = JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (error) {
    console.error(`[ddd-performance-smoke] failed to read scenarios file ${file}: ${error.message}`);
    process.exit(1);
  }
  const entries = Array.isArray(parsed) ? parsed : parsed.endpoints;
  if (!Array.isArray(entries) || entries.length === 0) {
    console.error(`[ddd-performance-smoke] scenarios file must be an array or { "endpoints": [...] }`);
    process.exit(1);
  }
  return entries.map(normalizeEndpoint);
}

function normalizeEndpoint(endpoint) {
  const method = String(endpoint.method || "GET").toUpperCase();
  const endpointPath = String(endpoint.path || "");
  if (!endpointPath.startsWith("/")) {
    console.error(`[ddd-performance-smoke] endpoint path must start with "/": ${endpointPath}`);
    process.exit(1);
  }
  return {
    method,
    path: endpointPath,
    headers: endpoint.headers && typeof endpoint.headers === "object" ? endpoint.headers : {},
    body: endpoint.body,
    multipart: endpoint.multipart && typeof endpoint.multipart === "object" ? endpoint.multipart : null,
    expectedStatuses: Array.isArray(endpoint.expectedStatuses) && endpoint.expectedStatuses.length > 0
      ? endpoint.expectedStatuses.map(Number)
      : [200, 401, 403, 404],
  };
}

const endpoints = scenariosFile ? readScenarios(scenariosFile) : defaultEndpoints();

function endpointKey(endpoint) {
  return `${endpoint.method} ${endpoint.path}`;
}

async function worker() {
  while (Date.now() < deadline) {
    const endpoint = endpoints[next++ % endpoints.length];
    const key = endpointKey(endpoint);
    const start = performance.now();
    try {
      const requestBody = buildBody(endpoint);
      const headers = {
        ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
        ...(!endpoint.multipart ? { "Content-Type": "application/json" } : {}),
        ...endpoint.headers,
      };
      const response = await fetch(new URL(endpoint.path, baseUrl), {
        method: endpoint.method,
        headers,
        ...(requestBody === undefined ? {} : { body: requestBody }),
      });
      const elapsed = performance.now() - start;
      samples.push(elapsed);
      if (!endpointSamples.has(key)) {
        endpointSamples.set(key, []);
      }
      endpointSamples.get(key).push(elapsed);
      recordStatus(key, response.status);
      if (endpoint.expectedStatuses.includes(response.status)) {
        ok += 1;
      } else {
        failed += 1;
      }
      await response.arrayBuffer();
    } catch (error) {
      failed += 1;
      const elapsed = performance.now() - start;
      samples.push(elapsed);
      if (!endpointSamples.has(key)) {
        endpointSamples.set(key, []);
      }
      endpointSamples.get(key).push(elapsed);
      recordError(key, error);
    }
  }
}

function recordStatus(key, status) {
  if (!endpointStatusCounts.has(key)) {
    endpointStatusCounts.set(key, new Map());
  }
  const counts = endpointStatusCounts.get(key);
  counts.set(status, (counts.get(status) || 0) + 1);
}

function recordError(key, error) {
  if (!endpointErrorCounts.has(key)) {
    endpointErrorCounts.set(key, new Map());
  }
  const counts = endpointErrorCounts.get(key);
  const errorName = error?.name || "Error";
  counts.set(errorName, (counts.get(errorName) || 0) + 1);
}

function buildBody(endpoint) {
  if (endpoint.multipart) {
    const form = new FormData();
    for (const [name, value] of Object.entries(endpoint.multipart.fields || {})) {
      form.append(name, String(value));
    }
    for (const file of endpoint.multipart.files || []) {
      const fieldName = file.fieldName || "file";
      const filename = file.filename || "upload.txt";
      const contentType = file.contentType || "application/octet-stream";
      const content = file.contentBase64
        ? Buffer.from(file.contentBase64, "base64")
        : Buffer.from(String(file.content || ""), "utf8");
      form.append(fieldName, new Blob([content], { type: contentType }), filename);
    }
    return form;
  }
  if (endpoint.body === undefined) {
    return undefined;
  }
  return typeof endpoint.body === "string" ? endpoint.body : JSON.stringify(endpoint.body);
}

function percentile(values, pct) {
  if (values.length === 0) {
    return 0;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.ceil((pct / 100) * sorted.length) - 1);
  return sorted[index];
}

function summarize() {
  const perEndpoint = {};
  for (const [key, values] of endpointSamples.entries()) {
    const statusCounts = Object.fromEntries(
      [...(endpointStatusCounts.get(key) || new Map()).entries()]
        .sort(([left], [right]) => Number(left) - Number(right))
        .map(([status, count]) => [String(status), count])
    );
    const errorCounts = Object.fromEntries(
      [...(endpointErrorCounts.get(key) || new Map()).entries()]
        .sort(([left], [right]) => String(left).localeCompare(String(right)))
    );
    perEndpoint[key] = {
      samples: values.length,
      p50: Math.round(percentile(values, 50)),
      p95: Math.round(percentile(values, 95)),
      p99: Math.round(percentile(values, 99)),
      statusCounts,
      ...(Object.keys(errorCounts).length > 0 ? { errorCounts } : {}),
    };
  }
  return {
    baseUrl,
    durationMs,
    concurrency,
    endpoints: endpoints.map((endpoint) => ({
      method: endpoint.method,
      path: endpoint.path,
      expectedStatuses: endpoint.expectedStatuses,
    })),
    ok,
    failed,
    samples: samples.length,
    p50: Math.round(percentile(samples, 50)),
    p95: Math.round(percentile(samples, 95)),
    p99: Math.round(percentile(samples, 99)),
    perEndpoint,
  };
}

function readJson(file, label) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (error) {
    console.error(`[ddd-performance-smoke] failed to read ${label} ${file}: ${error.message}`);
    process.exit(1);
  }
}

function compareP95(actual, baseline) {
  const failures = [];
  const limit = (baseline.p95 || 0) * (1 + maxP95RegressionRatio);
  if (baseline.p95 && actual.p95 > limit) {
    failures.push(`overall p95 ${actual.p95}ms exceeds baseline ${baseline.p95}ms by more than ${Math.round(maxP95RegressionRatio * 100)}%`);
  }
  const baselineEndpoints = baseline.perEndpoint || {};
  const actualEndpoints = actual.perEndpoint || {};
  for (const [key, expected] of Object.entries(baselineEndpoints)) {
    if (!expected?.p95 || !actualEndpoints[key]?.p95) {
      continue;
    }
    const endpointLimit = expected.p95 * (1 + maxP95RegressionRatio);
    if (actualEndpoints[key].p95 > endpointLimit) {
      failures.push(`${key} p95 ${actualEndpoints[key].p95}ms exceeds baseline ${expected.p95}ms by more than ${Math.round(maxP95RegressionRatio * 100)}%`);
    }
  }
  if (failures.length > 0) {
    for (const failure of failures) {
      console.error(`[ddd-performance-smoke] ${failure}`);
    }
    process.exit(1);
  }
}

let summary;
if (actualFile) {
  summary = readJson(actualFile, "actual performance report");
} else {
  await Promise.all(Array.from({ length: concurrency }, () => worker()));
  summary = summarize();
}

if (baselineFile) {
  compareP95(summary, readJson(baselineFile, "performance baseline"));
}

console.log(JSON.stringify(summary, null, 2));

process.exit((summary.failed || 0) === 0 ? 0 : 1);
