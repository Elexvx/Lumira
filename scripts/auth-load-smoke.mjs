#!/usr/bin/env node

import crypto from 'node:crypto';

const baseUrl =
  process.env.AUTH_LOAD_BASE_URL ||
  process.env.LOAD_SMOKE_BASE_URL ||
  process.env.DEPLOY_CHECK_BASE_URL ||
  (process.env.API_DOMAIN ? `https://${process.env.API_DOMAIN}` : 'http://127.0.0.1:8000');
const username = process.env.AUTH_LOAD_USERNAME || process.env.PLAYWRIGHT_ADMIN_USER || 'admin';
const password = process.env.AUTH_LOAD_PASSWORD || process.env.PLAYWRIGHT_ADMIN_PASSWORD || '123456';
const durationMs = Number(process.env.AUTH_LOAD_DURATION_MS || 30_000);
const concurrency = Number(process.env.AUTH_LOAD_CONCURRENCY || 16);
const targetRps = Number(process.env.AUTH_LOAD_RPS || Math.max(8, concurrency * 2));
const timeoutMs = Number(process.env.AUTH_LOAD_TIMEOUT_MS || 5_000);

const endpoints = (process.env.AUTH_LOAD_ENDPOINTS || [
  '/api/v1/plugins/current/bootstrap',
  '/api/v1/system/watermark-settings',
  '/api/v1/system/floating-window-settings',
  '/api/v1/dashboard/summary',
  '/api/v1/message/unread-count',
  '/api/v1/auth/session/keepalive',
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

function url(pathname) {
  return new URL(pathname, baseUrl);
}

async function api(pathname, init = {}) {
  const response = await fetch(url(pathname), {
    ...init,
    headers: {
      'content-type': 'application/json',
      ...(init.headers || {}),
    },
  });
  const text = await response.text();
  let body;
  try {
    body = text ? JSON.parse(text) : undefined;
  } catch {
    body = text;
  }
  if (!response.ok || (body && typeof body === 'object' && body.code && body.code !== '0')) {
    const message = body?.userMessage || body?.message || response.statusText || 'request failed';
    const error = new Error(`${pathname} failed: HTTP ${response.status} ${message}`);
    error.status = response.status;
    error.body = body;
    throw error;
  }
  return body?.data ?? body;
}

async function encryptedPassword(plainTextPassword) {
  const key = await api('/api/v1/auth/login-encryption-key');
  const publicKey = crypto.createPublicKey({
    key: Buffer.from(key.publicKey, 'base64'),
    format: 'der',
    type: 'spki',
  });
  return crypto.publicEncrypt(
    {
      key: publicKey,
      padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
      oaepHash: 'sha256',
    },
    Buffer.from(plainTextPassword),
  ).toString('base64');
}

async function login() {
  const passwordCiphertext = await encryptedPassword(password);
  const loginResponse = await api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      username,
      account: username,
      password: passwordCiphertext,
    }),
  });
  if (!loginResponse?.accessToken) {
    throw new Error('login did not return an accessToken');
  }
  if (loginResponse.requiresSecondFactor || loginResponse.requiresPasswordChange) {
    throw new Error('login requires second factor or password change; provide a ready test account for auth load smoke');
  }
  return loginResponse.accessToken;
}

async function probe(pathname, accessToken) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  const startedAt = performance.now();
  const method = pathname.endsWith('/keepalive') ? 'POST' : 'GET';
  try {
    const response = await fetch(url(pathname), {
      method,
      signal: controller.signal,
      headers: {
        authorization: `Bearer ${accessToken}`,
      },
    });
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

async function main() {
  const accessToken = await login();
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
      results.push(await probe(endpoint, accessToken));
    }
  }

  console.log(`Auth load smoke: base=${baseUrl}, username=${username}, durationMs=${durationMs}, concurrency=${concurrency}, targetRps=${targetRps}, endpoints=${endpoints.join(' ')}`);
  await Promise.all(Array.from({ length: concurrency }, () => worker()));

  const latencies = results.map((result) => result.latencyMs);
  const failures = results.filter((result) => !result.ok);
  const statusCounts = results.reduce((accumulator, result) => {
    const key = String(result.status);
    accumulator[key] = (accumulator[key] || 0) + 1;
    return accumulator;
  }, {});
  const endpointStats = endpoints.map((endpoint) => {
    const endpointLatencies = results.filter((result) => result.pathname === endpoint).map((result) => result.latencyMs);
    return {
      endpoint,
      count: endpointLatencies.length,
      p50: Math.round(percentile(endpointLatencies, 50)),
      p95: Math.round(percentile(endpointLatencies, 95)),
      p99: Math.round(percentile(endpointLatencies, 99)),
      max: Math.round(Math.max(0, ...endpointLatencies)),
    };
  });

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
    endpointStats,
  }, null, 2));

  if (failures.some((result) => result.status === 'ERR' || Number(result.status) >= 500)) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  const status = error?.status ? `HTTP ${error.status} ` : '';
  const message = error instanceof Error && error.message ? error.message : String(error);
  console.error(`Auth load smoke failed: ${status}${message}`);
  if (error?.body?.requestId) {
    console.error(`requestId: ${error.body.requestId}`);
  }
  process.exitCode = 1;
});
