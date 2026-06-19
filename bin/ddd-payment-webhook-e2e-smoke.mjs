#!/usr/bin/env node

import crypto from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { performance } from 'node:perf_hooks';
import {
  buildProductionEquivalenceEvidence,
  requireRuntimeProvenanceWhenStrict,
} from './ddd-release-evidence-utils.mjs';

const baseUrl = (process.env.LUMIRA_BASE_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '');
const username = process.env.DDD_AUTH_USERNAME || process.env.PLAYWRIGHT_ADMIN_USER || 'admin';
const outputDir = process.env.DDD_PAYMENT_WEBHOOK_E2E_DIR || path.join('artifacts', 'ddd', 'payment');
const outputFile = path.join(outputDir, 'payment-webhook-e2e.json');
const tenantId = Number(process.env.DDD_PAYMENT_TENANT_ID || '1001');
const providerCode = 'stripe';
const webhookSecret = process.env.DDD_PAYMENT_WEBHOOK_SECRET || `lumira-stripe-webhook-secret-${Date.now()}`;
const sourceEnvironment = process.env.DDD_PAYMENT_WEBHOOK_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || '';
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || '';
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || '';
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === 'true' || process.env.DDD_PAYMENT_WEBHOOK_STRICT === 'true';
const deploymentEvidence = process.env.DDD_PAYMENT_WEBHOOK_DEPLOYMENT_EVIDENCE || process.env.DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || '';
const mysqlConfig = {
  host: process.env.MYSQL_HOST || '127.0.0.1',
  port: process.env.MYSQL_PORT || '3307',
  user: process.env.MYSQL_USER || 'root',
  password: process.env.MYSQL_PASSWORD || '',
  database: process.env.DDD_EXPLAIN_DATABASE || process.env.MYSQL_DATABASE || 'saas',
};
const passwordCandidates = [
  process.env.DDD_AUTH_PASSWORD,
  process.env.AUTH_LOAD_PASSWORD,
  process.env.PLAYWRIGHT_NEW_PASSWORD,
  'E2eAdmin123!',
  process.env.PLAYWRIGHT_ADMIN_PASSWORD,
  '123456',
].filter(Boolean).filter((value, index, values) => values.indexOf(value) === index);

const url = (pathname) => new URL(pathname, baseUrl);

const productionEquivalence = () => buildProductionEquivalenceEvidence({
  strict: strictEvidence,
  baseUrl,
  deploymentEvidence,
  evidenceName: 'payment webhook E2E',
});

const finalizeArtifactStatus = (artifact) => {
  const blockers = [
    ...(artifact.productionEquivalence?.issues || []),
  ];
  return {
    ...artifact,
    status: blockers.length === 0 ? artifact.status : 'FAIL',
    blockers,
  };
};

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

const login = async () => {
  const errors = [];
  for (const password of passwordCandidates) {
    try {
      const passwordCiphertext = await encryptedPassword(password);
      const response = await api('/api/v2/auth/login', {
        method: 'POST',
        body: JSON.stringify({
          username,
          account: username,
          password: passwordCiphertext,
        }),
      });
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

const authorizedApi = async (pathname, accessToken, init = {}) => api(pathname, {
  ...init,
  headers: {
    Authorization: `Bearer ${accessToken}`,
    ...(init.headers || {}),
  },
});

const configureStripeProvider = async (accessToken) => {
  const startedAt = performance.now();
  const data = await authorizedApi('/api/v2/payment/providers/stripe', accessToken, {
    method: 'PUT',
    body: JSON.stringify({
      providerCode,
      providerName: 'Stripe',
      enabled: true,
      environment: 'SANDBOX',
      currency: 'USD',
      clientId: 'lumira-stripe-client-smoke',
      secretKey: 'sk_test_lumira_smoke',
      webhookSecret,
      sandboxEnabled: true,
    }),
  });
  return {
    elapsedMs: round(performance.now() - startedAt),
    enabled: data.enabled,
    configured: data.configured,
    configuredFields: data.configuredFields || [],
  };
};

const createOrder = async (accessToken, unique) => {
  const startedAt = performance.now();
  const orderNo = `DDD-PAY-${unique}`;
  const data = await authorizedApi('/api/v2/payment/orders', accessToken, {
    method: 'POST',
    body: JSON.stringify({
      providerCode,
      orderNo,
      subject: 'DDD payment webhook smoke',
      amountMinor: 199,
      currency: 'USD',
      clientIp: '127.0.0.1',
      notifyUrl: `${baseUrl}/api/v2/payment/webhooks/stripe`,
      returnUrl: `${baseUrl}/payment/smoke/return`,
      metadata: {
        source: 'ddd-payment-webhook-e2e-smoke',
      },
      idempotencyKey: `idem-${orderNo}`,
    }),
  });
  return {
    elapsedMs: round(performance.now() - startedAt),
    order: data,
  };
};

const signStripe = (timestamp, payload) => crypto
  .createHmac('sha256', webhookSecret)
  .update(`${timestamp}.${payload}`)
  .digest('base64');

const webhook = async ({ eventId, eventType, payload, signature, timestamp, nonce }) => {
  const body = JSON.stringify(payload);
  const startedAt = performance.now();
  const data = await api('/api/v2/payment/webhooks/stripe', {
    method: 'POST',
    headers: {
      'X-Tenant-Id': String(tenantId),
      'X-Event-Id': eventId,
      'X-Event-Type': eventType,
      'X-Timestamp': String(timestamp),
      'X-Nonce': nonce,
      'Stripe-Signature': signature,
    },
    body,
  });
  return {
    elapsedMs: round(performance.now() - startedAt),
    response: data,
  };
};

const queryJson = (sql) => {
  const result = spawnSync('mysql', [
    '--protocol=TCP',
    '-h',
    mysqlConfig.host,
    '-P',
    mysqlConfig.port,
    '-u',
    mysqlConfig.user,
    mysqlConfig.database,
    '-N',
    '-B',
    '-r',
    '-e',
    sql,
  ], {
    encoding: 'utf8',
    env: {
      ...process.env,
      ...(mysqlConfig.password ? { MYSQL_PWD: mysqlConfig.password } : {}),
    },
  });
  if (result.status !== 0) {
    throw new Error(`mysql query failed: ${result.stderr || result.stdout}`);
  }
  const text = result.stdout.trim();
  if (!text || text === 'NULL') {
    return null;
  }
  return JSON.parse(text);
};

const paymentState = (orderNo, eventIds) => ({
  order: queryJson(`
    select json_object(
      'orderNo', order_no,
      'providerCode', provider_code,
      'status', status,
      'amountMinor', amount_minor,
      'currency', currency,
      'paidAt', date_format(paid_at, '%Y-%m-%dT%H:%i:%s'),
      'updatedAt', date_format(updated_at, '%Y-%m-%dT%H:%i:%s')
    )
    from payment_order
    where tenant_id = ${tenantId} and order_no = '${escapeSql(orderNo)}' and deleted = 0
    limit 1
  `),
  webhookEvents: queryJson(`
    select coalesce(json_arrayagg(json_object(
      'eventId', event_id,
      'eventType', event_type,
      'nonce', nonce,
      'signatureValid', signature_valid,
      'processed', processed,
      'processMessage', process_message,
      'receivedAt', date_format(received_at, '%Y-%m-%dT%H:%i:%s'),
      'processedAt', date_format(processed_at, '%Y-%m-%dT%H:%i:%s')
    )), json_array())
    from payment_webhook_event
    where tenant_id = ${tenantId}
      and provider_code = '${providerCode}'
      and event_id in (${eventIds.map((eventId) => `'${escapeSql(eventId)}'`).join(',')})
      and deleted = 0
    order by id asc
  `) ?? [],
});

const assertCompleted = ({ state, firstWebhook, duplicateWebhook, replayWebhook, badSignatureWebhook, validEventId, replayEventId, badEventId }) => {
  if (state.order?.status !== 'PAID') {
    throw new Error(`expected order to be PAID, got ${JSON.stringify(state.order)}`);
  }
  if (!firstWebhook.response?.signatureValid || !firstWebhook.response?.processed) {
    throw new Error(`expected first webhook to be valid and processed: ${JSON.stringify(firstWebhook.response)}`);
  }
  if (duplicateWebhook.response?.eventId !== validEventId || !duplicateWebhook.response?.processed) {
    throw new Error(`expected duplicate webhook to return existing processed event: ${JSON.stringify(duplicateWebhook.response)}`);
  }
  if (replayWebhook.response?.eventId !== replayEventId || replayWebhook.response?.processed || replayWebhook.response?.signatureValid) {
    throw new Error(`expected nonce replay webhook to be rejected before processing: ${JSON.stringify(replayWebhook.response)}`);
  }
  if (badSignatureWebhook.response?.eventId !== badEventId || badSignatureWebhook.response?.processed || badSignatureWebhook.response?.signatureValid) {
    throw new Error(`expected bad signature webhook to be rejected: ${JSON.stringify(badSignatureWebhook.response)}`);
  }
  const eventsById = new Map(state.webhookEvents.map((event) => [event.eventId, event]));
  if (eventsById.get(validEventId)?.processed !== 1 || eventsById.get(validEventId)?.signatureValid !== 1) {
    throw new Error(`valid webhook row is not processed/signatureValid: ${JSON.stringify(eventsById.get(validEventId))}`);
  }
  if (eventsById.get(replayEventId)?.processMessage !== '请求已被重放') {
    throw new Error(`nonce replay row missing expected message: ${JSON.stringify(eventsById.get(replayEventId))}`);
  }
  if (eventsById.get(badEventId)?.processMessage !== '签名校验失败') {
    throw new Error(`bad signature row missing expected message: ${JSON.stringify(eventsById.get(badEventId))}`);
  }
};

const main = async () => {
  const provenanceIssues = requireRuntimeProvenanceWhenStrict({
    strict: strictEvidence,
    sourceEnvironment,
    releaseCandidate,
    evidenceOperator,
  });
  if (provenanceIssues.length > 0) {
    throw new Error(provenanceIssues.map((issue) => `runtime provenance ${issue}`).join('; '));
  }
  const startedAt = new Date();
  const accessToken = await login();
  const unique = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
  const provider = await configureStripeProvider(accessToken);
  if (!provider.enabled || !provider.configured) {
    throw new Error(`provider was not enabled/configured: ${JSON.stringify(provider)}`);
  }
  const createdOrder = await createOrder(accessToken, unique);
  const orderNo = createdOrder.order.orderNo || createdOrder.order.order_no || `DDD-PAY-${unique}`;
  const timestamp = Math.floor(Date.now() / 1000);
  const nonce = `nonce-${unique}`;
  const validEventId = `evt-valid-${unique}`;
  const replayEventId = `evt-replay-${unique}`;
  const badEventId = `evt-bad-${unique}`;
  const eventType = 'payment.succeeded';
  const validPayload = {
    eventId: validEventId,
    eventType,
    orderNo,
    providerTxnId: `txn-${unique}`,
  };
  const validBody = JSON.stringify(validPayload);
  const firstWebhook = await webhook({
    eventId: validEventId,
    eventType,
    payload: validPayload,
    timestamp,
    nonce,
    signature: signStripe(timestamp, validBody),
  });
  const duplicateWebhook = await webhook({
    eventId: validEventId,
    eventType,
    payload: validPayload,
    timestamp,
    nonce,
    signature: signStripe(timestamp, validBody),
  });
  const replayPayload = {
    eventId: replayEventId,
    eventType,
    orderNo,
  };
  const replayBody = JSON.stringify(replayPayload);
  const replayWebhook = await webhook({
    eventId: replayEventId,
    eventType,
    payload: replayPayload,
    timestamp,
    nonce,
    signature: signStripe(timestamp, replayBody),
  });
  const badPayload = {
    eventId: badEventId,
    eventType,
    orderNo,
  };
  const badSignatureWebhook = await webhook({
    eventId: badEventId,
    eventType,
    payload: badPayload,
    timestamp,
    nonce: `nonce-bad-${unique}`,
    signature: 'bad-signature',
  });
  const state = paymentState(orderNo, [validEventId, replayEventId, badEventId]);
  assertCompleted({
    state,
    firstWebhook,
    duplicateWebhook,
    replayWebhook,
    badSignatureWebhook,
    validEventId,
    replayEventId,
    badEventId,
  });
  const finishedAt = new Date();
  const summary = finalizeArtifactStatus({
    status: 'PASS',
    baseUrl,
    username,
    tenantId,
    providerCode,
    sourceEnvironment: sourceEnvironment || null,
    releaseCandidate: releaseCandidate || null,
    evidenceOperator: evidenceOperator || null,
    productionEquivalence: productionEquivalence(),
    startedAt: startedAt.toISOString(),
    finishedAt: finishedAt.toISOString(),
    elapsedMs: finishedAt.getTime() - startedAt.getTime(),
    provider,
    order: {
      elapsedMs: createdOrder.elapsedMs,
      orderNo,
      status: createdOrder.order.status,
      amountMinor: createdOrder.order.amountMinor,
      currency: createdOrder.order.currency,
    },
    webhooks: {
      first: summarizeWebhook(firstWebhook),
      duplicate: summarizeWebhook(duplicateWebhook),
      nonceReplay: summarizeWebhook(replayWebhook),
      badSignature: summarizeWebhook(badSignatureWebhook),
    },
    finalState: state,
  });
  mkdirSync(outputDir, { recursive: true });
  writeSummary(summary);
  console.log(JSON.stringify(summary, null, 2));
  console.log(`Wrote ${outputFile}`);
};

const summarizeWebhook = (result) => ({
  elapsedMs: result.elapsedMs,
  eventId: result.response?.eventId,
  eventType: result.response?.eventType,
  signatureValid: result.response?.signatureValid,
  processed: result.response?.processed,
  processMessage: result.response?.processMessage,
});

const escapeSql = (value) => String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
const round = (value) => Math.round(value * 100) / 100;
const writeSummary = (summary) => writeFileSync(outputFile, `${JSON.stringify(summary, null, 2)}\n`);

main().catch((error) => {
  const summary = finalizeArtifactStatus({
    status: 'FAIL',
    baseUrl,
    username,
    tenantId,
    providerCode,
    sourceEnvironment: sourceEnvironment || null,
    releaseCandidate: releaseCandidate || null,
    evidenceOperator: evidenceOperator || null,
    productionEquivalence: productionEquivalence(),
    failedAt: new Date().toISOString(),
    error: error instanceof Error ? error.message : String(error),
  });
  mkdirSync(outputDir, { recursive: true });
  writeSummary(summary);
  console.error(summary.error);
  console.error(`Wrote ${outputFile}`);
  process.exit(1);
});
