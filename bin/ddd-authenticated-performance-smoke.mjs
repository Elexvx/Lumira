#!/usr/bin/env node
import crypto from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { performance } from 'node:perf_hooks';
import {
  buildProductionEquivalenceEvidence,
  requireRuntimeProvenanceWhenStrict,
} from './ddd-release-evidence-utils.mjs';

const baseUrl = (process.env.LUMIRA_BASE_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '');
const username = process.env.DDD_AUTH_USERNAME || process.env.PLAYWRIGHT_ADMIN_USER || 'admin';
const durationMs = Number(process.env.DDD_AUTH_PERF_DURATION_MS || '15000');
const concurrency = Number(process.env.DDD_AUTH_PERF_CONCURRENCY || '8');
const outputDir = process.env.DDD_AUTH_PERF_DIR || path.join('artifacts', 'ddd', 'performance');
const outputFile = path.join(outputDir, 'authenticated-runtime-actual.json');
const sourceEnvironment = process.env.DDD_AUTH_PERF_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || '';
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || '';
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || '';
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === 'true' || process.env.DDD_AUTH_PERF_STRICT === 'true';
const deploymentEvidence = process.env.DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || '';

const passwordCandidates = [
  process.env.DDD_AUTH_PASSWORD,
  process.env.AUTH_LOAD_PASSWORD,
  process.env.PLAYWRIGHT_NEW_PASSWORD,
  'E2eAdmin123!',
  process.env.PLAYWRIGHT_ADMIN_PASSWORD,
  '123456',
]
  .filter(Boolean)
  .filter((value, index, values) => values.indexOf(value) === index);

const endpoints = [
  { method: 'GET', path: '/api/v2/auth/current-user' },
  { method: 'GET', path: '/api/v2/iam/tenants/current' },
  { method: 'GET', path: '/api/v2/iam/permissions' },
  { method: 'GET', path: '/api/v2/message/unread-count' },
  { method: 'GET', path: '/api/v2/message/messages?pageNo=1&pageSize=20' },
  { method: 'GET', path: '/api/v2/files?pageNo=1&pageSize=20' },
  { method: 'GET', path: '/api/v2/plugins/current/bootstrap' },
  { method: 'GET', path: '/api/v2/localization/runtime/zh-CN' },
  { method: 'GET', path: '/api/v2/payment/providers' },
];

let cursor = 0;
let ok = 0;
let failed = 0;
const samples = [];
const endpointSamples = new Map();
const endpointStatusCounts = new Map();
const endpointErrorCounts = new Map();
const deadline = Date.now() + durationMs;

const url = (pathname) => new URL(pathname, baseUrl);

const productionEquivalence = () => buildProductionEquivalenceEvidence({
  strict: strictEvidence,
  baseUrl,
  deploymentEvidence,
  evidenceName: 'authenticated performance actual',
});

const readJson = async (response) => {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
};

const api = async (pathname, init = {}) => {
  const response = await fetch(url(pathname), {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init.body && !init.headers?.['Content-Type'] ? { 'Content-Type': 'application/json' } : {}),
      ...(init.headers || {}),
    },
  });
  const body = await readJson(response);
  const successCode = body?.code === '0' || body?.code === 'SUCCESS';
  if (!response.ok || (body?.code && !successCode)) {
    const message = body?.userMessage || body?.message || response.statusText || 'request failed';
    const error = new Error(`${pathname} failed: HTTP ${response.status} ${message}`);
    error.status = response.status;
    error.body = body;
    throw error;
  }
  return body?.data ?? body;
};

const encryptedPassword = async (plainTextPassword) => {
  const key = await api('/api/v2/auth/login-encryption-key');
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
};

const tryLogin = async (plainTextPassword) => {
  const passwordCiphertext = await encryptedPassword(plainTextPassword);
  return api('/api/v2/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      username,
      account: username,
      password: passwordCiphertext,
    }),
  });
};

const login = async () => {
  const errors = [];
  for (const password of passwordCandidates) {
    try {
      const response = await tryLogin(password);
      if (response?.requiresSecondFactor || response?.requiresPasswordChange) {
        throw new Error('login requires second factor or password change; provide a ready smoke account');
      }
      if (!response?.accessToken) {
        throw new Error('login did not return an accessToken');
      }
      return response.accessToken;
    } catch (error) {
      errors.push(error?.message || String(error));
    }
  }
  throw new Error(`Unable to authenticate ${username}. Tried ${passwordCandidates.length} password candidate(s): ${errors.join(' | ')}`);
};

const endpointKey = (endpoint) => `${endpoint.method} ${endpoint.path}`;

const recordStatus = (key, status) => {
  if (!endpointStatusCounts.has(key)) {
    endpointStatusCounts.set(key, new Map());
  }
  const counts = endpointStatusCounts.get(key);
  counts.set(status, (counts.get(status) || 0) + 1);
};

const recordError = (key, error) => {
  if (!endpointErrorCounts.has(key)) {
    endpointErrorCounts.set(key, new Map());
  }
  const counts = endpointErrorCounts.get(key);
  const errorName = error?.name || 'Error';
  counts.set(errorName, (counts.get(errorName) || 0) + 1);
};

const isSuccessEnvelope = (result) => {
  const successCode = result.body?.code === '0' || result.body?.code === 'SUCCESS';
  return result.response.ok && successCode;
};

const probe = async (endpoint, accessToken) => {
  const startedAt = performance.now();
  const response = await fetch(url(endpoint.path), {
    method: endpoint.method,
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
      ...(endpoint.method === 'GET' ? {} : { 'Content-Type': 'application/json' }),
    },
  });
  const body = await readJson(response);
  return {
    response,
    body,
    elapsedMs: performance.now() - startedAt,
  };
};

const worker = async (accessToken) => {
  while (Date.now() < deadline) {
    const endpoint = endpoints[cursor++ % endpoints.length];
    const key = endpointKey(endpoint);
    const startedAt = performance.now();
    try {
      const result = await probe(endpoint, accessToken);
      const elapsedMs = result.elapsedMs;
      samples.push(elapsedMs);
      if (!endpointSamples.has(key)) {
        endpointSamples.set(key, []);
      }
      endpointSamples.get(key).push(elapsedMs);
      recordStatus(key, result.response.status);
      if (isSuccessEnvelope(result)) {
        ok += 1;
      } else {
        failed += 1;
        recordError(key, new Error(`${key} returned HTTP ${result.response.status}: ${JSON.stringify(result.body)}`));
      }
    } catch (error) {
      failed += 1;
      const elapsedMs = performance.now() - startedAt;
      samples.push(elapsedMs);
      if (!endpointSamples.has(key)) {
        endpointSamples.set(key, []);
      }
      endpointSamples.get(key).push(elapsedMs);
      recordError(key, error);
    }
  }
};

const oneShot = async (endpoint, accessToken) => {
  const result = await probe(endpoint, accessToken);
  if (!isSuccessEnvelope(result)) {
    throw new Error(`${endpointKey(endpoint)} one-shot failed with HTTP ${result.response.status}: ${JSON.stringify(result.body)}`);
  }
  return {
    name: endpointKey(endpoint),
    status: result.response.status,
    elapsedMs: Math.round(result.elapsedMs * 100) / 100,
  };
};

const uploadOnce = async (accessToken) => {
  const form = new FormData();
  const unique = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  form.append('category', 'performance-smoke');
  form.append('scope', 'tenant');
  form.append('file', new Blob([`Lumira DDD authenticated upload smoke ${unique}`], { type: 'text/plain' }), `ddd-auth-smoke-${unique}.txt`);
  const startedAt = performance.now();
  const response = await fetch(url('/api/v2/files/upload'), {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: form,
  });
  const body = await readJson(response);
  const elapsedMs = Math.round((performance.now() - startedAt) * 100) / 100;
  const successCode = body?.code === '0' || body?.code === 'SUCCESS';
  if (!response.ok || !successCode) {
    throw new Error(`upload smoke failed with HTTP ${response.status}: ${JSON.stringify(body)}`);
  }
  return {
    path: '/api/v2/files/upload',
    status: response.status,
    elapsedMs,
    fileId: body?.data?.id ?? body?.data?.fileId ?? null,
  };
};

const percentile = (values, pct) => {
  if (values.length === 0) {
    return 0;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.ceil((pct / 100) * sorted.length) - 1);
  return sorted[index];
};

const readIamMetrics = async (accessToken) => {
  try {
    return await api('/api/v2/iam/metrics', {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });
  } catch (error) {
    return {
      endpoint: '/api/v2/iam/metrics',
      error: error instanceof Error ? error.message : String(error),
    };
  }
};

const summarize = (upload, iamMetrics) => {
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
    iamMetrics,
    baseUrl,
    username,
    checkedAt: new Date().toISOString(),
    sourceEnvironment: sourceEnvironment || null,
    releaseCandidate: releaseCandidate || null,
    evidenceOperator: evidenceOperator || null,
    productionEquivalence: productionEquivalence(),
    durationMs,
    concurrency,
    endpoints,
    ok,
    failed,
    samples: samples.length,
    p50: Math.round(percentile(samples, 50)),
    p95: Math.round(percentile(samples, 95)),
    p99: Math.round(percentile(samples, 99)),
    upload,
    perEndpoint,
  };
};

const main = async () => {
  const provenanceIssues = requireRuntimeProvenanceWhenStrict({
    strict: strictEvidence,
    sourceEnvironment,
    releaseCandidate,
    evidenceOperator,
  });
  if (provenanceIssues.length > 0) {
    const summary = {
      baseUrl,
      username,
      checkedAt: new Date().toISOString(),
      sourceEnvironment: sourceEnvironment || null,
      releaseCandidate: releaseCandidate || null,
      evidenceOperator: evidenceOperator || null,
      productionEquivalence: productionEquivalence(),
      durationMs,
      concurrency,
      endpoints,
      ok: 0,
      failed: provenanceIssues.length,
      samples: 0,
      p50: 0,
      p95: 0,
      p99: 0,
      upload: null,
      perEndpoint: {},
      errors: provenanceIssues.map((issue) => `runtime provenance ${issue}`),
    };
    mkdirSync(outputDir, { recursive: true });
    writeFileSync(outputFile, `${JSON.stringify(summary, null, 2)}\n`);
    for (const issue of provenanceIssues) {
      console.error(`runtime provenance ${issue}`);
    }
    console.error(`Wrote ${outputFile}`);
    process.exit(1);
  }
  const accessToken = await login();
  const oneShots = [
    await oneShot({ method: 'POST', path: '/api/v2/auth/session/keepalive' }, accessToken),
  ];
  const iamMetrics = await readIamMetrics(accessToken);
  const upload = await uploadOnce(accessToken);
  await Promise.all(Array.from({ length: concurrency }, () => worker(accessToken)));
  const summary = summarize(upload, iamMetrics);
  summary.oneShots = oneShots;
  mkdirSync(outputDir, { recursive: true });
  writeFileSync(outputFile, `${JSON.stringify(summary, null, 2)}\n`);
  console.log(JSON.stringify(summary, null, 2));
  console.log(`Wrote ${outputFile}`);
  if (summary.failed > 0) {
    process.exit(1);
  }
};

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  if (error?.body?.requestId) {
    console.error(`requestId: ${error.body.requestId}`);
  }
  process.exit(1);
});
