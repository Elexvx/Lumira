#!/usr/bin/env node

import crypto from 'node:crypto';

const baseUrl = process.env.COMPETITION_SMOKE_BASE_URL || 'http://127.0.0.1:8000';
const username = process.env.COMPETITION_SMOKE_USERNAME || process.env.PLAYWRIGHT_ADMIN_USER;
const password = process.env.COMPETITION_SMOKE_PASSWORD || process.env.PLAYWRIGHT_ADMIN_PASSWORD;
const durationMs = positiveNumber('COMPETITION_SMOKE_DURATION_MS', 30_000);
const concurrency = positiveNumber('COMPETITION_SMOKE_CONCURRENCY', 8);
const targetRps = positiveNumber('COMPETITION_SMOKE_RPS', 16);
const timeoutMs = positiveNumber('COMPETITION_SMOKE_TIMEOUT_MS', 5_000);
const maximumP95Ms = positiveNumber('COMPETITION_SMOKE_MAX_P95_MS', 1_000);
const maximumP99Ms = positiveNumber('COMPETITION_SMOKE_MAX_P99_MS', 2_000);
const maximumErrorRate = Number(process.env.COMPETITION_SMOKE_MAX_ERROR_RATE || 0.01);
const endpoints = (process.env.COMPETITION_SMOKE_ENDPOINTS || [
  '/api/v2/aiadc/registrations?pageNo=1&pageSize=20',
  '/api/v2/reviews/plans',
  '/api/v2/reviews/batches',
  '/api/v2/reviews/my-tasks',
  '/api/v2/reviews/my-results',
].join(',')).split(',').map((value) => value.trim()).filter(Boolean);
const writeScenarios = parseWriteScenarios(process.env.COMPETITION_SMOKE_WRITE_SCENARIOS);
const allowWrites = process.env.COMPETITION_SMOKE_ALLOW_WRITES === 'true';
const runId = process.env.COMPETITION_SMOKE_RUN_ID || crypto.randomUUID();

if (!username || !password) {
  throw new Error('Set COMPETITION_SMOKE_USERNAME and COMPETITION_SMOKE_PASSWORD');
}
if (!Number.isFinite(maximumErrorRate) || maximumErrorRate < 0 || maximumErrorRate > 1) {
  throw new Error('COMPETITION_SMOKE_MAX_ERROR_RATE must be between 0 and 1');
}
if (writeScenarios.length > 0 && !allowWrites) {
  throw new Error('Set COMPETITION_SMOKE_ALLOW_WRITES=true to execute configured write scenarios');
}
if (writeScenarios.length > 0) {
  const base = new URL(baseUrl);
  if (!['localhost', '127.0.0.1', '::1'].includes(base.hostname)) {
    throw new Error(`Write-path load smoke only accepts a loopback base URL, got ${base.origin}`);
  }
}

function positiveNumber(name, fallback) {
  const value = Number(process.env[name] || fallback);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${name} must be positive`);
  }
  return value;
}

function parseWriteScenarios(raw) {
  if (!raw) return [];
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    throw new Error(`COMPETITION_SMOKE_WRITE_SCENARIOS must be valid JSON: ${error.message}`);
  }
  if (!Array.isArray(parsed)) {
    throw new Error('COMPETITION_SMOKE_WRITE_SCENARIOS must be a JSON array');
  }
  return parsed.map((scenario, index) => {
    const method = String(scenario?.method || 'POST').trim().toUpperCase();
    const pathname = String(scenario?.pathname || scenario?.path || '').trim();
    if (!pathname.startsWith('/api/') || !['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
      throw new Error(`Write scenario ${index + 1} must use an /api/ path and POST, PUT, PATCH, or DELETE`);
    }
    return {
      pathname,
      method,
      body: scenario.body,
      name: String(scenario.name || `${method} ${pathname}`),
    };
  });
}

function percentile(values, p) {
  if (!values.length) return 0;
  const ordered = [...values].sort((left, right) => left - right);
  return ordered[Math.min(ordered.length - 1, Math.ceil((p / 100) * ordered.length) - 1)];
}

async function request(pathname, init = {}) {
  const response = await fetch(new URL(pathname, baseUrl), {
    ...init,
    headers: { 'content-type': 'application/json', ...(init.headers || {}) },
  });
  const text = await response.text();
  let body;
  try {
    body = text ? JSON.parse(text) : undefined;
  } catch {
    body = text;
  }
  return { response, body };
}

async function login() {
  const keyResult = await request('/api/v1/auth/login-encryption-key');
  const publicKeyText = keyResult.body?.data?.publicKey;
  if (!keyResult.response.ok || !publicKeyText) {
    throw new Error(`login encryption key failed: HTTP ${keyResult.response.status}`);
  }
  const publicKey = crypto.createPublicKey({
    key: Buffer.from(publicKeyText, 'base64'),
    format: 'der',
    type: 'spki',
  });
  const encryptedPassword = crypto.publicEncrypt({
    key: publicKey,
    padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
    oaepHash: 'sha256',
  }, Buffer.from(password)).toString('base64');
  const loginResult = await request('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      username,
      account: username,
      password: encryptedPassword,
    }),
  });
  const loginData = loginResult.body?.data;
  if (!loginResult.response.ok || !loginData?.accessToken) {
    throw new Error(`login failed: HTTP ${loginResult.response.status}`);
  }
  if (loginData.requiresSecondFactor || loginData.requiresPasswordChange) {
    throw new Error('The smoke-test account requires an interactive login step');
  }
  return loginData.accessToken;
}

async function probe(scenario, token, sequence) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  const startedAt = performance.now();
  try {
    const { response, body } = await request(scenario.pathname, {
      method: scenario.method,
      body: scenario.body === undefined ? undefined : JSON.stringify(scenario.body),
      signal: controller.signal,
      headers: {
        authorization: `Bearer ${token}`,
        'idempotency-key': `${runId}-${sequence}`,
        'x-request-id': crypto.randomUUID(),
      },
    });
    const applicationCode = body && typeof body === 'object' ? body.code : undefined;
    const applicationOk = applicationCode === undefined || String(applicationCode) === '0';
    return {
      pathname: scenario.pathname,
      method: scenario.method,
      name: scenario.name,
      status: response.status,
      latencyMs: performance.now() - startedAt,
      ok: response.ok && applicationOk,
    };
  } catch (error) {
    return {
      pathname: scenario.pathname,
      method: scenario.method,
      name: scenario.name,
      status: 'ERR',
      latencyMs: performance.now() - startedAt,
      ok: false,
      error: error instanceof Error ? error.name : String(error),
    };
  } finally {
    clearTimeout(timeout);
  }
}

const token = await login();
const results = [];
const scenarios = [
  ...endpoints.map((pathname) => ({ pathname, method: 'GET', name: `GET ${pathname}` })),
  ...writeScenarios,
];
if (scenarios.length === 0) {
  throw new Error('Configure at least one read endpoint or write scenario');
}
const stopAt = Date.now() + durationMs;
let cursor = 0;
let nextRequestAt = Date.now();

async function pace() {
  const now = Date.now();
  const delay = Math.max(0, nextRequestAt - now);
  nextRequestAt = Math.max(now, nextRequestAt) + Math.ceil(1000 / targetRps);
  if (delay > 0) {
    await new Promise((resolve) => setTimeout(resolve, delay));
  }
}

async function worker() {
  while (Date.now() < stopAt) {
    await pace();
    const sequence = cursor;
    const scenario = scenarios[sequence % scenarios.length];
    cursor += 1;
    results.push(await probe(scenario, token, sequence));
  }
}

console.log(`Competition workflow load smoke: base=${baseUrl}, durationMs=${durationMs}, concurrency=${concurrency}, rps=${targetRps}`);
await Promise.all(Array.from({ length: concurrency }, () => worker()));

const failures = results.filter((result) => !result.ok);
const latencies = results.map((result) => result.latencyMs);
const p95 = Math.round(percentile(latencies, 95));
const p99 = Math.round(percentile(latencies, 99));
const errorRate = results.length ? failures.length / results.length : 1;
const statusCounts = results.reduce((counts, result) => {
  const status = String(result.status);
  counts[status] = (counts[status] || 0) + 1;
  return counts;
}, {});
const report = {
  total: results.length,
  reads: results.filter((result) => result.method === 'GET').length,
  writes: results.filter((result) => result.method !== 'GET').length,
  failed: failures.length,
  errorRate,
  latencyMs: {
    p50: Math.round(percentile(latencies, 50)),
    p95,
    p99,
  },
  statusCounts,
};
console.log(JSON.stringify(report, null, 2));

if (p95 > maximumP95Ms || p99 > maximumP99Ms || errorRate > maximumErrorRate) {
  console.error(`Threshold failed: p95<=${maximumP95Ms}ms, p99<=${maximumP99Ms}ms, and errorRate<=${maximumErrorRate}`);
  process.exitCode = 1;
}
