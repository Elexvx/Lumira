#!/usr/bin/env node
import { mkdirSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import {
  buildProductionEquivalenceEvidence,
  requireRuntimeProvenanceWhenStrict,
} from './ddd-release-evidence-utils.mjs';
import { requiredJobSmokeEndpoints } from './ddd-outbox-job-evidence-contract.mjs';

const baseUrl = (process.env.LUMIRA_BASE_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '');
const jobToken = process.env.SAAS_JOB_INTERNAL_TOKEN || process.env.DDD_JOB_INTERNAL_TOKEN || '';
const outputDir = process.env.DDD_JOB_SMOKE_DIR || path.join('artifacts', 'ddd', 'jobs');
const outputFile = path.join(outputDir, 'job-e2e-smoke.json');
const sourceEnvironment = process.env.DDD_JOB_SMOKE_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || '';
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || '';
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || '';
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === 'true' || process.env.DDD_JOB_SMOKE_STRICT === 'true';
const deploymentEvidence = process.env.DDD_JOB_SMOKE_DEPLOYMENT_EVIDENCE || process.env.DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || '';
const mysqlConfig = {
  host: process.env.MYSQL_HOST || '',
  port: process.env.MYSQL_PORT || '',
  user: process.env.MYSQL_USER || '',
  password: process.env.MYSQL_PASSWORD || '',
  database: process.env.DDD_EXPLAIN_DATABASE || process.env.MYSQL_DATABASE || '',
};
const dbCheckEnabled = process.env.DDD_JOB_SMOKE_DB_CHECK === 'true' || Boolean(mysqlConfig.host && mysqlConfig.user && mysqlConfig.database);

const endpoints = requiredJobSmokeEndpoints;

const productionEquivalence = () => buildProductionEquivalenceEvidence({
  strict: strictEvidence,
  baseUrl,
  deploymentEvidence,
  evidenceName: 'job E2E',
});

const finalizeArtifactStatus = (artifact) => {
  const blockers = [
    ...(artifact.productionEquivalence?.issues || []),
  ];
  return {
    ...artifact,
    status: blockers.length === 0 ? (artifact.status || 'PASS') : 'FAIL',
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

const writeFailure = (error) => {
  const artifact = finalizeArtifactStatus({
    status: 'FAIL',
    baseUrl,
    checkedAt: new Date().toISOString(),
    sourceEnvironment: sourceEnvironment || null,
    releaseCandidate: releaseCandidate || null,
    evidenceOperator: evidenceOperator || null,
    productionEquivalence: productionEquivalence(),
    unauthorized: null,
    endpoints: [],
    summary: {
      total: endpoints.length,
      failed: endpoints.length,
      maxElapsedMs: null,
    },
    diagnostics: {
      dbCheckEnabled,
      outboxOwnership: null,
    },
    error: error instanceof Error ? error.message : String(error),
  });
  mkdirSync(outputDir, { recursive: true });
  writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);
};

const post = async ({ path: requestPath, token }) => {
  const headers = { Accept: 'application/json' };
  if (token) {
    headers['X-Job-Token'] = token;
  }
  const startedAt = performance.now();
  const response = await fetch(`${baseUrl}${requestPath}`, {
    method: 'POST',
    headers,
  });
  const elapsedMs = Math.round((performance.now() - startedAt) * 100) / 100;
  return {
    status: response.status,
    ok: response.ok,
    elapsedMs,
    body: await readJson(response),
  };
};

const assertSuccessEnvelope = (endpoint, result) => {
  if (!result.ok) {
    throw new Error(`${endpoint.name} failed with HTTP ${result.status}: ${JSON.stringify(result.body)}`);
  }
  const successCode = result.body?.code === '0' || result.body?.code === 'SUCCESS';
  if (!result.body || !successCode) {
    throw new Error(`${endpoint.name} returned a non-success envelope: ${JSON.stringify(result.body)}`);
  }
  if (endpoint.dataType === 'boolean' && typeof result.body.data !== 'boolean') {
    throw new Error(`${endpoint.name} expected boolean data but got ${typeof result.body.data}`);
  }
  if (endpoint.dataType === 'number' && typeof result.body.data !== 'number') {
    throw new Error(`${endpoint.name} expected numeric data but got ${typeof result.body.data}`);
  }
};

const mysqlArgs = (sql) => {
  const args = ['--batch', '--raw', '--skip-column-names', '--execute', sql];
  if (mysqlConfig.host) {
    args.push('--host', mysqlConfig.host);
  }
  if (mysqlConfig.port) {
    args.push('--port', mysqlConfig.port);
  }
  if (mysqlConfig.user) {
    args.push('--user', mysqlConfig.user);
  }
  if (mysqlConfig.database) {
    args.push(mysqlConfig.database);
  }
  return args;
};

const queryJson = (sql) => {
  const result = spawnSync(process.env.MYSQL_CLI || 'mysql', mysqlArgs(sql), {
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
  if (!text) {
    return [];
  }
  return text.split('\n').map((line) => JSON.parse(line));
};

const crossOwnerFailureCount = () => {
  const rows = queryJson(`
    select json_object('count', count(1))
    from platform_event_outbox
    where deleted = 0
      and source_type <> 'MESSAGE'
      and last_error like '%payload 反序列化失败%'
  `);
  return Number(rows[0]?.count || 0);
};

const crossOwnerOutboxDiagnostics = (beforeCount) => {
  if (beforeCount === null || beforeCount === undefined) {
    return null;
  }
  const afterCount = crossOwnerFailureCount();
  const diagnostics = {
    crossOwnerPayloadFailuresBefore: beforeCount,
    crossOwnerPayloadFailuresAfter: afterCount,
    crossOwnerPayloadFailuresDelta: afterCount - beforeCount,
  };
  if (diagnostics.crossOwnerPayloadFailuresDelta > 0) {
    throw new Error(`Message relay touched non-message outbox payloads during job smoke: ${JSON.stringify(diagnostics)}`);
  }
  return diagnostics;
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
  if (!jobToken) {
    throw new Error('SAAS_JOB_INTERNAL_TOKEN or DDD_JOB_INTERNAL_TOKEN must be set for job E2E smoke.');
  }
  const crossOwnerFailuresBefore = dbCheckEnabled ? crossOwnerFailureCount() : null;

  const unauthorized = await post({ path: endpoints[0].path, token: '' });
  if (unauthorized.ok) {
    throw new Error('Internal job endpoint accepted a request without X-Job-Token.');
  }

  const results = [];
  for (const endpoint of endpoints) {
    const result = await post({ path: endpoint.path, token: jobToken });
    assertSuccessEnvelope(endpoint, result);
    results.push({
      name: endpoint.name,
      path: endpoint.path,
      status: result.status,
      elapsedMs: result.elapsedMs,
      data: result.body.data,
      requestId: result.body.requestId || null,
    });
  }

  const artifact = finalizeArtifactStatus({
    status: 'PASS',
    baseUrl,
    checkedAt: new Date().toISOString(),
    sourceEnvironment: sourceEnvironment || null,
    releaseCandidate: releaseCandidate || null,
    evidenceOperator: evidenceOperator || null,
    productionEquivalence: productionEquivalence(),
    unauthorized: {
      path: endpoints[0].path,
      status: unauthorized.status,
      code: unauthorized.body?.code || null,
    },
    endpoints: results,
    summary: {
      total: results.length,
      failed: 0,
      maxElapsedMs: Math.max(...results.map((item) => item.elapsedMs)),
    },
    diagnostics: {
      dbCheckEnabled,
      outboxOwnership: dbCheckEnabled ? crossOwnerOutboxDiagnostics(crossOwnerFailuresBefore) : null,
    },
  });

  mkdirSync(outputDir, { recursive: true });
  writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);
  console.log(JSON.stringify(artifact, null, 2));
  console.log(`Wrote ${outputFile}`);
};

main().catch((error) => {
  writeFailure(error);
  console.error(error instanceof Error ? error.message : String(error));
  console.error(`Wrote ${outputFile}`);
  process.exit(1);
});
