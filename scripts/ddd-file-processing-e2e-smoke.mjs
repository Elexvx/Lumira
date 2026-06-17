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
const outputDir = process.env.DDD_FILE_PROCESSING_E2E_DIR || path.join('artifacts', 'ddd', 'file');
const outputFile = path.join(outputDir, 'file-processing-e2e.json');
const internalToken = process.env.SAAS_JOB_INTERNAL_TOKEN || process.env.DDD_JOB_TOKEN || '';
const sourceEnvironment = process.env.DDD_FILE_PROCESSING_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || '';
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || '';
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || '';
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === 'true' || process.env.DDD_FILE_PROCESSING_STRICT === 'true';
const deploymentEvidence = process.env.DDD_FILE_PROCESSING_DEPLOYMENT_EVIDENCE || process.env.DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || '';
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

const expectedTaskTypes = ['SECURITY_SCAN', 'TEXT_EXTRACT', 'AI_PARSE'];
const expectedArtifactTypes = ['SECURITY_SCAN_RESULT', 'TEXT_CONTENT', 'AI_PARSE_READY'];

const url = (pathname) => new URL(pathname, baseUrl);

const productionEquivalence = () => buildProductionEquivalenceEvidence({
  strict: strictEvidence,
  baseUrl,
  deploymentEvidence,
  evidenceName: 'file processing E2E',
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

const authorizedApi = async (pathname, accessToken, init = {}) => api(pathname, {
  ...init,
  headers: {
    Authorization: `Bearer ${accessToken}`,
    ...(init.headers || {}),
  },
});

const uploadTextFile = async (accessToken) => {
  const unique = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const form = new FormData();
  form.append('category', 'processing-smoke');
  form.append('scope', 'tenant');
  form.append('file', new Blob([
    [
      'Lumira DDD file processing smoke',
      `unique=${unique}`,
      'This text should produce TEXT_CONTENT and AI_PARSE_READY artifacts.',
    ].join('\n'),
  ], { type: 'text/plain' }), `ddd-file-processing-${unique}.txt`);
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
  const elapsedMs = round(performance.now() - startedAt);
  const successCode = body?.code === '0' || body?.code === 'SUCCESS';
  if (!response.ok || !successCode) {
    throw new Error(`upload failed with HTTP ${response.status}: ${JSON.stringify(body)}`);
  }
  const file = body?.data ?? {};
  if (!file.id || !file.tenantId) {
    throw new Error(`upload response did not contain file id and tenant id: ${JSON.stringify(file)}`);
  }
  return {
    elapsedMs,
    file,
  };
};

const runProcessingJob = async (limit = 20) => {
  if (!internalToken) {
    throw new Error('SAAS_JOB_INTERNAL_TOKEN or DDD_JOB_TOKEN is required for file processing E2E smoke');
  }
  const startedAt = performance.now();
  const data = await api(`/file/internal/jobs/processing/run?limit=${limit}`, {
    method: 'POST',
    headers: {
      'X-Job-Token': internalToken,
    },
  });
  return {
    elapsedMs: round(performance.now() - startedAt),
    processed: Number(data ?? 0),
  };
};

const metrics = async (accessToken) => {
  const startedAt = performance.now();
  const data = await authorizedApi('/api/v2/files/metrics', accessToken);
  return {
    elapsedMs: round(performance.now() - startedAt),
    data,
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

const fileState = (tenantId, fileId) => ({
  tasks: queryJson(`
    select coalesce(json_arrayagg(json_object(
      'taskType', task_type,
      'status', status,
      'retryCount', retry_count,
      'createdAt', date_format(created_at, '%Y-%m-%dT%H:%i:%s'),
      'claimedAt', date_format(claimed_at, '%Y-%m-%dT%H:%i:%s'),
      'completedAt', date_format(completed_at, '%Y-%m-%dT%H:%i:%s'),
      'lastError', last_error
    )), json_array())
    from file_processing_task
    where tenant_id = ${Number(tenantId)} and file_id = ${Number(fileId)} and deleted = 0
    order by priority desc, id asc
  `) ?? [],
  artifacts: queryJson(`
    select coalesce(json_arrayagg(json_object(
      'artifactType', artifact_type,
      'taskType', task_type,
      'contentLength', content_length,
      'createdAt', date_format(created_at, '%Y-%m-%dT%H:%i:%s'),
      'updatedAt', date_format(updated_at, '%Y-%m-%dT%H:%i:%s')
    )), json_array())
    from file_processing_artifact
    where tenant_id = ${Number(tenantId)} and file_id = ${Number(fileId)} and deleted = 0
    order by id asc
  `) ?? [],
});

const waitForTasks = async (tenantId, fileId) => {
  const deadline = Date.now() + 5000;
  let state = fileState(tenantId, fileId);
  while (Date.now() < deadline && state.tasks.length === 0) {
    await sleep(200);
    state = fileState(tenantId, fileId);
  }
  return state;
};

const hasPendingOrRetryable = (tasks) => tasks.some((task) => ['PENDING', 'FAILED'].includes(task.status));

const assertCompleted = (state) => {
  const taskTypes = state.tasks.map((task) => task.taskType).sort();
  const artifactTypes = state.artifacts.map((artifact) => artifact.artifactType).sort();
  const missingTasks = expectedTaskTypes.filter((taskType) => !taskTypes.includes(taskType));
  const missingArtifacts = expectedArtifactTypes.filter((artifactType) => !artifactTypes.includes(artifactType));
  const nonSucceeded = state.tasks.filter((task) => task.status !== 'SUCCEEDED');
  if (missingTasks.length || missingArtifacts.length || nonSucceeded.length) {
    throw new Error(`file processing did not complete: ${JSON.stringify({ missingTasks, missingArtifacts, nonSucceeded })}`);
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
  const beforeMetrics = await metrics(accessToken);
  const upload = await uploadTextFile(accessToken);
  const { id: fileId, tenantId } = upload.file;
  let state = await waitForTasks(tenantId, fileId);
  const jobRuns = [];
  for (let attempt = 0; attempt < 4 && hasPendingOrRetryable(state.tasks); attempt += 1) {
    jobRuns.push(await runProcessingJob(20));
    state = fileState(tenantId, fileId);
  }
  const afterMetrics = await metrics(accessToken);
  assertCompleted(state);
  const finishedAt = new Date();
  const summary = finalizeArtifactStatus({
    status: 'PASS',
    baseUrl,
    username,
    sourceEnvironment: sourceEnvironment || null,
    releaseCandidate: releaseCandidate || null,
    evidenceOperator: evidenceOperator || null,
    productionEquivalence: productionEquivalence(),
    startedAt: startedAt.toISOString(),
    finishedAt: finishedAt.toISOString(),
    elapsedMs: finishedAt.getTime() - startedAt.getTime(),
    upload: {
      elapsedMs: upload.elapsedMs,
      fileId,
      tenantId,
      originalFileName: upload.file.originalFileName,
      fileExtension: upload.file.fileExtension,
      mimeType: upload.file.mimeType,
    },
    jobRuns,
    finalState: state,
    metrics: {
      before: summarizeMetricValues(beforeMetrics.data),
      after: summarizeMetricValues(afterMetrics.data),
      beforeElapsedMs: beforeMetrics.elapsedMs,
      afterElapsedMs: afterMetrics.elapsedMs,
    },
  });
  mkdirSync(outputDir, { recursive: true });
  writeFile(outputFile, summary);
  console.log(JSON.stringify(summary, null, 2));
  console.log(`Wrote ${outputFile}`);
};

const writeFile = (target, data) => {
  writeFileSync(target, `${JSON.stringify(data, null, 2)}\n`);
};

const summarizeMetricValues = (data) => {
  const metricsList = data?.metrics || [];
  return Object.fromEntries(metricsList
    .filter((metric) => metric?.name?.startsWith('file.processing_task.') || metric?.name?.startsWith('file.outbox.'))
    .map((metric) => [metric.name, metric.value ?? null]));
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const round = (value) => Math.round(value * 100) / 100;

main().catch((error) => {
  const summary = finalizeArtifactStatus({
    status: 'FAIL',
    baseUrl,
    username,
    sourceEnvironment: sourceEnvironment || null,
    releaseCandidate: releaseCandidate || null,
    evidenceOperator: evidenceOperator || null,
    productionEquivalence: productionEquivalence(),
    failedAt: new Date().toISOString(),
    error: error instanceof Error ? error.message : String(error),
  });
  mkdirSync(outputDir, { recursive: true });
  writeFile(outputFile, summary);
  console.error(summary.error);
  console.error(`Wrote ${outputFile}`);
  process.exit(1);
});
