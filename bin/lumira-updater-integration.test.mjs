import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');
const digest = (name) => `ghcr.io/elexvx/lumira/${name}@sha256:${'b'.repeat(64)}`;

test('updater v2 exposes capabilities, preflight, and persistent task state', { timeout: 20_000 }, async (context) => {
  const deployDir = mkdtempSync(path.join(os.tmpdir(), 'lumira-updater-test-'));
  mkdirSync(path.join(deployDir, '.generated', 'api-proxy'), { recursive: true });
  writeFileSync(path.join(deployDir, '.env'), 'LUMIRA_ACTIVE_SLOT=blue\n');
  const port = 19_000 + Math.floor(Math.random() * 10_000);
  const token = 'integration-test-token';
  const child = spawn(process.execPath, [path.join(repoRoot, 'bin', 'lumira-updater.mjs'), '--dry-run'], {
    cwd: repoRoot,
    env: { ...process.env, LUMIRA_DEPLOY_DIR: deployDir, LUMIRA_UPDATER_PORT: String(port), PLATFORM_UPDATE_AGENT_TOKEN: token },
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
  assert.equal(capabilities.body.protocolVersion, 2);
  assert.equal(capabilities.body.activeSlot, 'blue');

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

  const install = await call('/v1/update/install', { method: 'POST', body: JSON.stringify({ preflightId: preflight.body.preflightId }) });
  assert.equal(install.response.status, 202);
  let task;
  for (let attempt = 0; attempt < 50; attempt += 1) {
    task = (await call(`/v1/update/tasks/${install.body.taskId}`)).body;
    if (['SUCCEEDED', 'FAILED', 'ROLLED_BACK'].includes(task.status)) break;
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  assert.equal(task.status, 'SUCCEEDED');
  assert.equal(task.progressPercent, 100);
  assert.equal(task.targetSlot, 'green');
});
