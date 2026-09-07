import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');
const runtimeDigestCharacters = { server: 'a', frontend: 'b', async: 'c', job: 'd', migrator: 'e' };
const domesticDigestCharacters = {
  'lumira-server': '1',
  'lumira-ui': '2',
  'lumira-async': '3',
  'lumira-job-executor': '4',
  'lumira-migrator': '5',
};
const digest = (name) => `ghcr.io/elexvx/lumira/${name}@sha256:${(runtimeDigestCharacters[name] || 'f').repeat(64)}`;
const domesticDigest = (name) => `swr.cn-east-3.myhuaweicloud.com/aiadc/${name}@sha256:${(domesticDigestCharacters[name] || '6').repeat(64)}`;
const safeDataPlaneEnvironment = (dbUrl = 'jdbc:mysql://mysql:3306/saas?useSSL=false') => [
  'LUMIRA_ACTIVE_SLOT=blue',
  'DB_USERNAME=lumira_app',
  'DB_PASSWORD=application-secret',
  'DB_MIGRATION_USERNAME=lumira_migrator',
  'DB_MIGRATION_PASSWORD=migration-secret',
  'MYSQL_BACKUP_USERNAME=lumira_backup',
  'MYSQL_BACKUP_PASSWORD=backup-secret',
  'MYSQL_RESTORE_USERNAME=lumira_restore',
  'MYSQL_RESTORE_PASSWORD=restore-secret',
  'XXL_JOB_DB_URL=jdbc:mysql://mysql:3306/xxl_job',
  'XXL_JOB_DB_USERNAME=xxl_job',
  'XXL_JOB_DB_PASSWORD=xxl-secret',
  'MYSQLD_EXPORTER_USERNAME=exporter',
  'MYSQLD_EXPORTER_PASSWORD=exporter-secret',
  'REDIS_CACHE_HOST=redis-cache',
  'REDIS_CACHE_PORT=6379',
  'REDIS_CACHE_PASSWORD=cache-secret',
  'REDIS_RUNTIME_HOST=redis-runtime',
  'REDIS_RUNTIME_PORT=6379',
  'REDIS_RUNTIME_PASSWORD=runtime-secret',
  `DB_URL=${dbUrl}`,
  '',
].join('\n');

test('updater v3 exposes capabilities, preflight, and persistent task state', { timeout: 20_000 }, async (context) => {
  const deployDir = mkdtempSync(path.join(os.tmpdir(), 'lumira-updater-test-'));
  mkdirSync(path.join(deployDir, '.generated', 'api-proxy'), { recursive: true });
  writeFileSync(path.join(deployDir, '.env'), safeDataPlaneEnvironment());
  const port = 19_000 + Math.floor(Math.random() * 10_000);
  const token = 'integration-test-token';
  const child = spawn(process.execPath, [path.join(repoRoot, 'bin', 'lumira-updater.mjs'), '--dry-run'], {
    cwd: repoRoot,
    env: { ...process.env, LUMIRA_DEPLOY_DIR: deployDir, LUMIRA_UPDATER_PORT: String(port), PLATFORM_UPDATE_AGENT_TOKEN: token, LUMIRA_UPDATER_ALLOW_INLINE_MANIFEST: 'true' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  context.after(() => {
    child.kill();
    rmSync(deployDir, { recursive: true, force: true });
  });

  const call = async (pathname, options = {}) => {
    const response = await fetch(`http://127.0.0.1:${port}${pathname}`, {
      ...options,
      headers: { 'x-lumira-updater-token': token, 'content-type': 'application/json', ...(options.headers || {}) },
    });
    return { response, body: await response.json() };
  };
  for (let attempt = 0; attempt < 40; attempt += 1) {
    try {
      const health = await call('/v1/health');
      if (health.response.ok) break;
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  const capabilities = await call('/v1/capabilities');
  assert.equal(capabilities.body.protocolVersion, 3);
  assert.equal(capabilities.body.acceptsInlineManifest, true);
  assert.equal(capabilities.body.activeSlot, 'blue');
  assert.equal(capabilities.body.supportsPlatformTaskLookup, true);

  const manifest = {
    schemaVersion: 2,
    version: '2.0.0',
    commit: 'abcdef0123456789',
    images: { server: digest('server'), frontend: digest('frontend'), async: digest('async'), jobExecutor: digest('job'), migrator: digest('migrator') },
    update: { strategy: 'single-host-blue-green', minUpdaterProtocol: 2, database: { mode: 'expand-only', targetVersion: '202607140001' } },
  };
  const preflight = await call('/v1/update/preflight', { method: 'POST', body: JSON.stringify({ manifest }) });
  assert.equal(preflight.body.ready, true);
  assert.equal(preflight.body.targetSlot, 'green');
  assert.equal(preflight.body.migrationMode, 'expand-only');
  assert.equal(preflight.body.databaseTargetVersion, '202607140001');
  writeFileSync(path.join(deployDir, '.env'), safeDataPlaneEnvironment('jdbc:mysql://ha-db.internal:3306/saas?useSSL=false'));
  const insecureExternalPreflight = await call('/v1/update/preflight', {
    method: 'POST',
    body: JSON.stringify({ manifest }),
  });
  assert.equal(insecureExternalPreflight.body.ready, true);
  assert.match(insecureExternalPreflight.body.warnings.join(' '), /sslMode=VERIFY_IDENTITY/);

  const domesticManifest = {
    ...manifest,
    commit: 'fedcba9876543210',
    images: {
      server: domesticDigest('lumira-server'),
      frontend: domesticDigest('lumira-ui'),
      async: domesticDigest('lumira-async'),
      jobExecutor: domesticDigest('lumira-job-executor'),
      migrator: domesticDigest('lumira-migrator'),
    },
  };
  const domesticPreflight = await call('/v1/update/preflight', {
    method: 'POST',
    body: JSON.stringify({ manifest: domesticManifest }),
  });
  assert.equal(domesticPreflight.body.ready, true, JSON.stringify(domesticPreflight.body.blockers));

  const untrustedPreflight = await call('/v1/update/preflight', {
    method: 'POST',
    body: JSON.stringify({
      manifest: {
        ...manifest,
        images: { ...manifest.images, server: `untrusted.example/lumira-server@sha256:${'7'.repeat(64)}` },
      },
    }),
  });
  assert.equal(untrustedPreflight.body.ready, false);
  assert.match(untrustedPreflight.body.blockers.join(' '), /Image registry is not allowed/);

  const platformTaskCreatedAt = '2026-08-08T00:00:00';
  const installRequest = {
    preflightId: preflight.body.preflightId,
    platformTaskId: 4242,
    platformTaskCreatedAt,
    targetCommit: manifest.commit,
  };
  const install = await call('/v1/update/install', { method: 'POST', body: JSON.stringify(installRequest) });
  assert.equal(install.response.status, 202);
  const taskByPlatformId = await call(`/v1/update/platform-tasks/4242?createdAt=${encodeURIComponent(platformTaskCreatedAt)}`);
  assert.equal(taskByPlatformId.response.status, 200);
  assert.equal(taskByPlatformId.body.taskId, install.body.taskId);
  const replay = await call('/v1/update/install', { method: 'POST', body: JSON.stringify(installRequest) });
  assert.equal(replay.response.status, 202);
  assert.equal(replay.body.taskId, install.body.taskId);
  const staleLookup = await call('/v1/update/platform-tasks/4242?createdAt=2026-08-07T23%3A59%3A59');
  assert.equal(staleLookup.response.status, 404);
  let task;
  for (let attempt = 0; attempt < 50; attempt += 1) {
    task = (await call(`/v1/update/tasks/${install.body.taskId}`)).body;
    if (['SUCCEEDED', 'FAILED', 'ROLLED_BACK'].includes(task.status)) break;
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  assert.equal(task.status, 'SUCCEEDED');
  assert.equal(task.progressPercent, 100);
  assert.equal(task.targetSlot, 'green');
  assert.equal(task.log.some((line) => line.includes(manifest.images.frontend)), false);
  assert.equal(task.log.some((line) => line.includes(manifest.images.server)), true);
  assert.equal(task.log.some((line) => line.includes('$ docker rm -f lumira-async')), true);
  assert.equal(task.log.some((line) => line.includes('pull lumira-async')), false);
  assert.equal(task.log.some((line) => line.includes('up -d --no-deps --force-recreate lumira-async')), true);
});

test('updater production default rejects inline manifests with a clear 4xx response', { timeout: 10_000 }, async (context) => {
  const deployDir = mkdtempSync(path.join(os.tmpdir(), 'lumira-updater-inline-'));
  mkdirSync(path.join(deployDir, '.generated', 'api-proxy'), { recursive: true });
  writeFileSync(path.join(deployDir, '.env'), 'LUMIRA_ACTIVE_SLOT=blue\n');
  const port = 29_000 + Math.floor(Math.random() * 3_000);
  const token = 'inline-default-test-token';
  const child = spawn(process.execPath, [path.join(repoRoot, 'bin', 'lumira-updater.mjs'), '--dry-run'], {
    cwd: repoRoot,
    env: { ...process.env, LUMIRA_DEPLOY_DIR: deployDir, LUMIRA_UPDATER_PORT: String(port), PLATFORM_UPDATE_AGENT_TOKEN: token, LUMIRA_UPDATER_ALLOW_INLINE_MANIFEST: 'false' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  context.after(() => {
    child.kill();
    rmSync(deployDir, { recursive: true, force: true });
  });
  for (let attempt = 0; attempt < 40; attempt += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/v1/health`, { headers: { 'x-lumira-updater-token': token } });
      if (response.ok) break;
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  const response = await fetch(`http://127.0.0.1:${port}/v1/update/preflight`, {
    method: 'POST',
    headers: { 'x-lumira-updater-token': token, 'content-type': 'application/json' },
    body: JSON.stringify({ manifest: { schemaVersion: 3 } }),
  });
  assert.equal(response.status, 400);
  assert.match((await response.json()).errorMessage, /Inline release manifests are disabled/);
});
