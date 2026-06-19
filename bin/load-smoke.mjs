#!/usr/bin/env node

const baseUrl =
  process.env.LOAD_SMOKE_BASE_URL ||
  process.env.DEPLOY_CHECK_BASE_URL ||
  (process.env.API_DOMAIN ? `https://${process.env.API_DOMAIN}` : 'http://127.0.0.1:8000');
const durationMs = Number(process.env.LOAD_SMOKE_DURATION_MS || 30_000);
const concurrency = Number(process.env.LOAD_SMOKE_CONCURRENCY || 24);
const targetRps = Number(process.env.LOAD_SMOKE_RPS || Math.max(8, concurrency * 2));
const timeoutMs = Number(process.env.LOAD_SMOKE_TIMEOUT_MS || 5_000);

const endpoints = (process.env.LOAD_SMOKE_ENDPOINTS || [
  '/health',
  '/api/health',
  '/api/version',
  '/api/v1/public/login-capabilities',
].join(','))
  .split(',')
  .map((endpoint) => endpoint.trim())
  .filter(Boolean);

function percentile(values, p) {
  if (values.length === 0) {
    return 0;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[index];
}

async function probe(pathname) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  const startedAt = performance.now();
  try {
    const response = await fetch(new URL(pathname, baseUrl), { signal: controller.signal });
    return {
      pathname,
      status: response.status,
      latencyMs: performance.now() - startedAt,
      ok: response.status < 500,
    };
  } catch (error) {
    return {
      pathname,
      status: 'ERR',
      latencyMs: performance.now() - startedAt,
      ok: false,
      error: error.name || error.message,
    };
  } finally {
    clearTimeout(timeout);
  }
}

const results = [];
const stopAt = Date.now() + durationMs;
let cursor = 0;
let nextRequestAt = Date.now();

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function pace() {
  if (!Number.isFinite(targetRps) || targetRps <= 0) {
    return;
  }
  const now = Date.now();
  const delay = Math.max(0, nextRequestAt - now);
  nextRequestAt = Math.max(now, nextRequestAt) + Math.ceil(1000 / targetRps);
  if (delay > 0) {
    await sleep(delay);
  }
}

async function worker() {
  while (Date.now() < stopAt) {
    await pace();
    const endpoint = endpoints[cursor % endpoints.length];
    cursor += 1;
    results.push(await probe(endpoint));
  }
}

console.log(`Load smoke: base=${baseUrl}, durationMs=${durationMs}, concurrency=${concurrency}, targetRps=${targetRps}, endpoints=${endpoints.join(' ')}`);
await Promise.all(Array.from({ length: concurrency }, () => worker()));

const latencies = results.map((result) => result.latencyMs);
const failures = results.filter((result) => !result.ok);
const statusCounts = results.reduce((accumulator, result) => {
  const key = String(result.status);
  accumulator[key] = (accumulator[key] || 0) + 1;
  return accumulator;
}, {});

console.log(JSON.stringify({
  total: results.length,
  ok: results.length - failures.length,
  failed: failures.length,
  statusCounts,
  latencyMs: {
    p50: Math.round(percentile(latencies, 50)),
    p95: Math.round(percentile(latencies, 95)),
    p99: Math.round(percentile(latencies, 99)),
    max: Math.round(Math.max(0, ...latencies)),
  },
}, null, 2));

if (failures.some((result) => result.status === 'ERR' || Number(result.status) >= 500)) {
  process.exitCode = 1;
}
