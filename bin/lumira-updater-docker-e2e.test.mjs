import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { once } from 'node:events';
import { appendFileSync, mkdirSync, mkdtempSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

const enabled = process.env.LUMIRA_DOCKER_E2E === 'true';
const repoRoot = path.resolve(import.meta.dirname, '..');
const fixtureDir = path.join(repoRoot, 'bin', 'test-fixtures', 'updater-e2e');
const oldCommit = '1111111111111111111111111111111111111111';
const newCommit = '2222222222222222222222222222222222222222';
const rejectedCommit = '3333333333333333333333333333333333333333';
const cancelledCommit = '4444444444444444444444444444444444444444';
const interruptedCommit = '5555555555555555555555555555555555555555';

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));
const toWslPath = (value) => {
  const match = String(value).match(/^([a-zA-Z]):[\\/](.*)$/);
  return match ? `/mnt/${match[1].toLowerCase()}/${match[2].replaceAll('\\', '/')}` : value;
};
const command = (file, args, options = {}) => {
  const useWslDocker = process.platform === 'win32' && file === 'docker';
  const executable = useWslDocker ? 'wsl' : file;
  const commandArgs = useWslDocker
    ? ['-d', process.env.LUMIRA_WSL_DISTRO || 'Ubuntu-24.04', '-u', 'root', '--', 'docker', ...args.map(toWslPath)]
    : args;
  const result = spawnSync(executable, commandArgs, {
    cwd: options.cwd || repoRoot,
    encoding: 'utf8',
    env: options.env || process.env,
    maxBuffer: 32 * 1024 * 1024,
    shell: false,
  });
  if (result.status !== 0 && options.check !== false) {
    throw new Error(`${file} ${args.join(' ')} failed (${result.status})${result.error ? `: ${result.error.message}` : ''}\n${result.stdout || ''}\n${result.stderr || ''}`);
  }
  const output = String(result.stdout || '').trim();
  return options.includeStatus ? { status: result.status, output } : output;
};

async function freePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      server.close(() => resolve(address.port));
    });
  });
}

async function waitFor(predicate, { timeoutMs = 120_000, intervalMs = 100, description = 'condition' } = {}) {
  const startedAt = Date.now();
  let lastError;
  while (Date.now() - startedAt < timeoutMs) {
    try {
      const result = await predicate();
      if (result) return result;
    } catch (error) {
      lastError = error;
    }
    await delay(intervalMs);
  }
  throw new Error(`Timed out waiting for ${description}${lastError ? `: ${lastError.message}` : ''}`);
}

function seedQueue(queueRoot, prefix, count) {
  for (const worker of ['async', 'job']) {
    const pending = path.join(queueRoot, worker, 'pending');
    mkdirSync(pending, { recursive: true });
    for (let index = 0; index < count; index += 1) {
      writeFileSync(path.join(pending, `${prefix}-${String(index).padStart(4, '0')}.task`), 'work');
    }
  }
}

function queueCount(queueRoot, worker, state) {
  const directory = path.join(queueRoot, worker, state);
  return readdirSync(directory, { withFileTypes: true }).filter((entry) => entry.isFile()).length;
}

test('real Docker blue-green update and rollback keep HTTP available and drain workers without task loss', {
  skip: enabled ? false : 'set LUMIRA_DOCKER_E2E=true to run the Docker integration rehearsal',
  timeout: 420_000,
}, async (context) => {
  const containerPrefix = `lumira-e2e-${process.pid}-`;
  const containerNames = ['server-blue', 'server-green', 'api-proxy', 'async', 'job-executor']
    .map((service) => `${containerPrefix}${service}`);
  for (const name of containerNames) {
    const existing = command('docker', ['inspect', '-f', '{{.Name}}', name], { check: false });
    assert.equal(existing, '', `refusing to replace existing container ${name}`);
  }

  const runtimeImageTags = {
    server: 'node:22-alpine',
    async: 'node:20-alpine',
    jobExecutor: 'node:24-alpine',
  };
  for (const image of [...Object.values(runtimeImageTags), 'nginx:1.29-alpine']) {
    const inspected = command('docker', ['image', 'inspect', image], { check: false, includeStatus: true });
    if (inspected.status !== 0) command('docker', ['pull', image]);
  }
  const runtimeImages = Object.fromEntries(Object.entries(runtimeImageTags).map(([role, image]) => [
    role,
    JSON.parse(command('docker', ['image', 'inspect', image]))[0].RepoDigests[0],
  ]));
  for (const image of Object.values(runtimeImages)) {
    assert.match(image, /^node@sha256:[0-9a-f]{64}$/);
  }
  assert.equal(new Set(Object.values(runtimeImages).map((image) => image.split('@')[1])).size, 3);

  const deployDir = mkdtempSync(path.join(os.tmpdir(), 'lumira-updater-docker-e2e-'));
  const queueRoot = path.join(deployDir, 'queue');
  const projectName = `lumirae2e${process.pid}`.toLowerCase();
  const updaterPort = await freePort();
  const proxyPort = await freePort();
  const bluePort = await freePort();
  const greenPort = await freePort();
  const token = `docker-e2e-${process.pid}`;
  const composeFile = path.join(deployDir, 'docker-compose.prod.yml');
  const envFile = path.join(deployDir, '.env');
  const updaterOutput = [];
  let updater;
  let hammering = false;
  let hammerPromise;
  let trafficStage = 'initial';

  const compose = (...args) => command('docker', ['compose', '--env-file', envFile, '-f', composeFile, '--profile', 'blue', '--profile', 'green', ...args], { cwd: repoRoot, check: false });
  context.after(async () => {
    hammering = false;
    await hammerPromise?.catch(() => {});
    updater?.kill('SIGTERM');
    await delay(100);
    compose('down', '-v', '--remove-orphans');
    for (const name of containerNames) {
      command('docker', ['rm', '-f', name], { check: false });
    }
    rmSync(deployDir, { recursive: true, force: true });
  });

  mkdirSync(path.join(deployDir, '.generated', 'api-proxy'), { recursive: true });
  for (const worker of ['async', 'job']) {
    for (const state of ['pending', 'processing', 'done', 'duplicates']) mkdirSync(path.join(queueRoot, worker, state), { recursive: true });
  }
  writeFileSync(path.join(deployDir, 'backup-platform.sh'), '#!/bin/sh\nset -eu\necho "Backup completed: /tmp/lumira-e2e-backup"\n', { mode: 0o755 });
  writeFileSync(path.join(deployDir, 'nginx.conf'), `events {}\nhttp {\n  keepalive_timeout 1s;\n  keepalive_time 2s;\n  keepalive_requests 20;\n  server {\n    listen 80;\n    resolver 127.0.0.11 valid=1s ipv6=off;\n    include /etc/nginx/lumira-upstreams/active-upstreams.conf;\n    location / { proxy_http_version 1.1; proxy_set_header X-E2E-Public true; proxy_pass http://$gateway_upstream$request_uri; }\n  }\n}\n`);
  writeFileSync(composeFile, `services:
  lumira-server-blue: &server
    profiles: [blue]
    image: \${LUMIRA_SERVER_BLUE_IMAGE}
    container_name: ${containerPrefix}server-blue
    ports: ["127.0.0.1:${bluePort}:8080"]
    command: ["node", "/fixtures/server.mjs"]
    environment:
      GIT_COMMIT: \${LUMIRA_SERVER_BLUE_GIT_COMMIT}
      APP_VERSION: \${LUMIRA_SERVER_BLUE_APP_VERSION}
      FAIL_PUBLIC_VERSION: \${LUMIRA_SERVER_BLUE_FAIL_PUBLIC_VERSION:-false}
    volumes: ["\${E2E_FIXTURE_DIR}:/fixtures:ro"]
    networks: [default, slot_aux]
  lumira-server-green:
    <<: *server
    profiles: [green]
    image: \${LUMIRA_SERVER_GREEN_IMAGE}
    container_name: ${containerPrefix}server-green
    ports: ["127.0.0.1:${greenPort}:8080"]
    environment:
      GIT_COMMIT: \${LUMIRA_SERVER_GREEN_GIT_COMMIT}
      APP_VERSION: \${LUMIRA_SERVER_GREEN_APP_VERSION}
      FAIL_PUBLIC_VERSION: \${LUMIRA_SERVER_GREEN_FAIL_PUBLIC_VERSION:-false}
  api-proxy:
    image: nginx:1.29-alpine
    container_name: ${containerPrefix}api-proxy
    ports: ["127.0.0.1:\${E2E_PROXY_PORT}:80"]
    volumes:
      - "./nginx.conf:/etc/nginx/nginx.conf:ro"
      - "./.generated/api-proxy:/etc/nginx/lumira-upstreams:ro"
    networks: [default]
  lumira-async:
    image: \${LUMIRA_ASYNC_IMAGE}
    container_name: ${containerPrefix}async
    command: ["node", "/fixtures/worker.mjs"]
    environment: { WORKER_QUEUE: /queue/async }
    volumes: ["\${E2E_FIXTURE_DIR}:/fixtures:ro", "\${E2E_QUEUE_DIR}:/queue"]
    networks: [default]
  lumira-job-executor:
    image: \${LUMIRA_JOB_EXECUTOR_IMAGE}
    container_name: ${containerPrefix}job-executor
    command: ["node", "/fixtures/worker.mjs"]
    environment: { WORKER_QUEUE: /queue/job }
    volumes: ["\${E2E_FIXTURE_DIR}:/fixtures:ro", "\${E2E_QUEUE_DIR}:/queue"]
    networks: [default]
networks:
  slot_aux:
`);
  writeFileSync(envFile, [
    `COMPOSE_PROJECT_NAME=${projectName}`,
    `E2E_FIXTURE_DIR=${toWslPath(fixtureDir)}`,
    `E2E_QUEUE_DIR=${toWslPath(queueRoot)}`,
    `E2E_PROXY_PORT=${proxyPort}`,
    'LUMIRA_ACTIVE_SLOT=blue',
    `LUMIRA_SERVER_IMAGE=${runtimeImages.server}`,
    `LUMIRA_SERVER_BLUE_IMAGE=${runtimeImages.server}`,
    `LUMIRA_SERVER_GREEN_IMAGE=${runtimeImages.server}`,
    'LUMIRA_SERVER_BLUE_APP_VERSION=old',
    'LUMIRA_SERVER_GREEN_APP_VERSION=old',
    `LUMIRA_SERVER_BLUE_GIT_COMMIT=${oldCommit}`,
    `LUMIRA_SERVER_GREEN_GIT_COMMIT=${oldCommit}`,
    'LUMIRA_SERVER_BLUE_FAIL_PUBLIC_VERSION=false',
    'LUMIRA_SERVER_GREEN_FAIL_PUBLIC_VERSION=false',
    `APP_VERSION=old`,
    `BUILD_VERSION=old+${oldCommit}`,
    `GIT_COMMIT=${oldCommit}`,
    'DATABASE_VERSION=baseline',
    `LUMIRA_ASYNC_IMAGE=${runtimeImages.async}`,
    `LUMIRA_JOB_EXECUTOR_IMAGE=${runtimeImages.jobExecutor}`,
    'DB_MIGRATION_NETWORK=unused',
    'BACKUP_ROOT=/tmp/lumira-e2e-backups',
    '',
  ].join('\n'));

  const updaterEnvironment = {
      ...process.env,
      LUMIRA_DEPLOY_DIR: deployDir,
      LUMIRA_UPDATER_HOST: '127.0.0.1',
      LUMIRA_UPDATER_PORT: String(updaterPort),
      LUMIRA_UPDATER_ROLLBACK_DRAIN_SECONDS: '3',
      LUMIRA_UPDATER_SKIP_PULL_IF_PRESENT: 'true',
      LUMIRA_CONTAINER_PREFIX: containerPrefix,
      LUMIRA_WSL_DISTRO: process.env.LUMIRA_WSL_DISTRO || 'Ubuntu-24.04',
      PLATFORM_UPDATE_AGENT_TOKEN: token,
      PLATFORM_UPDATE_ALLOWED_IMAGE_PREFIXES: 'node@sha256:',
      DEPLOY_CHECK_BASE_URL: `http://127.0.0.1:${proxyPort}`,
      LUMIRA_SLOT_PROBE_URL_BLUE: `http://127.0.0.1:${bluePort}`,
      LUMIRA_SLOT_PROBE_URL_GREEN: `http://127.0.0.1:${greenPort}`,
      LUMIRA_SERVER_GREEN_GIT_COMMIT: 'stale-process-environment',
      LUMIRA_SERVER_GREEN_APP_VERSION: 'stale-process-environment',
  };
  const startUpdater = () => {
    updater = spawn(process.execPath, [path.join(repoRoot, 'bin', 'lumira-updater.mjs')], {
      cwd: repoRoot,
      env: updaterEnvironment,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    updater.stdout.on('data', (chunk) => updaterOutput.push(chunk.toString()));
    updater.stderr.on('data', (chunk) => updaterOutput.push(chunk.toString()));
    return updater;
  };
  startUpdater();

  const call = async (pathname, options = {}) => {
    const response = await fetch(`http://127.0.0.1:${updaterPort}${pathname}`, {
      ...options,
      headers: { 'x-lumira-updater-token': token, 'content-type': 'application/json', ...(options.headers || {}) },
    });
    return { response, body: await response.json() };
  };
  await waitFor(async () => (await call('/v1/health')).response.ok, { description: 'updater health' });
  const initialUp = command('docker', ['compose', '--env-file', envFile, '-f', composeFile, '--profile', 'blue', 'up', '-d', 'lumira-server-blue', 'api-proxy', 'lumira-async', 'lumira-job-executor']);
  assert.doesNotMatch(initialUp, /error/i);
  await waitFor(async () => (await fetch(`http://127.0.0.1:${proxyPort}/probe`)).ok, { description: 'initial proxy traffic' });

  const traffic = [];
  hammering = true;
  hammerPromise = (async () => {
    while (hammering) {
      try {
        const response = await fetch(`http://127.0.0.1:${proxyPort}/probe`, { headers: { connection: 'close' } });
        const text = await response.text();
        let body;
        try { body = JSON.parse(text); } catch { body = {}; }
        traffic.push({ status: response.status, commit: body.commitId, body: text.slice(0, 120), stage: trafficStage });
      } catch (error) {
        traffic.push({ status: 0, error: error.message, stage: trafficStage });
      }
      await delay(20);
    }
  })();

  seedQueue(queueRoot, 'install', 500);
  trafficStage = 'install';
  const manifest = {
    schemaVersion: 2,
    version: 'new',
    commit: newCommit,
    images: {
      server: runtimeImages.server,
      frontend: '',
      async: runtimeImages.async,
      jobExecutor: runtimeImages.jobExecutor,
      migrator: runtimeImages.server,
    },
    update: {
      strategy: 'single-host-blue-green',
      minUpdaterProtocol: 2,
      drainTimeoutSeconds: 5,
      rollbackWindowSeconds: 60,
      database: { mode: 'none', targetVersion: 'baseline' },
    },
  };
  const preflight = await call('/v1/update/preflight', { method: 'POST', body: JSON.stringify({ manifest }) });
  assert.equal(preflight.body.ready, true, JSON.stringify(preflight.body.blockers));
  const install = await call('/v1/update/install', { method: 'POST', body: JSON.stringify({ preflightId: preflight.body.preflightId }) });
  assert.equal(install.response.status, 202);
  const installed = await waitFor(async () => {
    const result = await call(`/v1/update/tasks/${install.body.taskId}`);
    return ['SUCCEEDED', 'FAILED', 'ROLLED_BACK', 'CANCELLED'].includes(result.body.status) ? result.body : false;
  }, { description: 'blue-green install task' });
  assert.equal(installed.status, 'SUCCEEDED', `${installed.errorMessage || ''}\n${updaterOutput.join('')}`);
  assert.equal(installed.activeSlot, 'blue');
  assert.equal(installed.targetSlot, 'green');

  seedQueue(queueRoot, 'rollback', 200);
  trafficStage = 'explicit-rollback';
  const rollback = await call('/v1/update/rollback', { method: 'POST', body: '{}' });
  assert.equal(rollback.response.status, 202);
  const rolledBack = await waitFor(async () => {
    const result = await call(`/v1/update/tasks/${rollback.body.taskId}`);
    return ['ROLLED_BACK', 'FAILED'].includes(result.body.status) ? result.body : false;
  }, { description: 'hot rollback task' });
  assert.equal(rolledBack.status, 'ROLLED_BACK', `${rolledBack.errorMessage || ''}\n${updaterOutput.join('')}`);

  appendFileSync(envFile, '\nLUMIRA_SERVER_GREEN_FAIL_PUBLIC_VERSION=true\n');
  trafficStage = 'automatic-rollback';
  const rejectedManifest = {
    ...manifest,
    version: 'rejected',
    commit: rejectedCommit,
  };
  const rejectedPreflight = await call('/v1/update/preflight', { method: 'POST', body: JSON.stringify({ manifest: rejectedManifest }) });
  assert.equal(rejectedPreflight.body.ready, true, JSON.stringify(rejectedPreflight.body.blockers));
  const rejectedInstall = await call('/v1/update/install', { method: 'POST', body: JSON.stringify({ preflightId: rejectedPreflight.body.preflightId }) });
  const automaticallyRolledBack = await waitFor(async () => {
    const result = await call(`/v1/update/tasks/${rejectedInstall.body.taskId}`);
    return ['ROLLED_BACK', 'FAILED'].includes(result.body.status) ? result.body : false;
  }, { description: 'automatic rollback after public verification failure' });
  assert.equal(automaticallyRolledBack.status, 'ROLLED_BACK', `${automaticallyRolledBack.errorMessage || ''}\n${updaterOutput.join('')}`);
  assert.match(automaticallyRolledBack.errorMessage, /failed after traffic switch and was rolled back/i);

  appendFileSync(envFile, '\nLUMIRA_SERVER_GREEN_FAIL_PUBLIC_VERSION=false\n');
  trafficStage = 'post-switch-cancel';
  const cancelledManifest = { ...manifest, version: 'cancelled', commit: cancelledCommit };
  const cancelledPreflight = await call('/v1/update/preflight', { method: 'POST', body: JSON.stringify({ manifest: cancelledManifest }) });
  assert.equal(cancelledPreflight.body.ready, true, JSON.stringify(cancelledPreflight.body.blockers));
  const cancelledInstall = await call('/v1/update/install', { method: 'POST', body: JSON.stringify({ preflightId: cancelledPreflight.body.preflightId }) });
  assert.equal(cancelledInstall.response.status, 202);
  await waitFor(async () => {
    const result = await call(`/v1/update/tasks/${cancelledInstall.body.taskId}`);
    return result.body.phase === 'DRAINING_OLD' && result.body.status === 'RUNNING';
  }, { description: 'post-switch drain phase before cancellation' });
  const cancel = await call(`/v1/update/tasks/${cancelledInstall.body.taskId}/cancel`, { method: 'POST', body: '{}' });
  assert.equal(cancel.response.status, 202);
  assert.equal(cancel.body.cancelRequested, true);
  assert.match(cancel.body.message, /rollback requested after traffic switch/i);
  const cancelled = await waitFor(async () => {
    const result = await call(`/v1/update/tasks/${cancelledInstall.body.taskId}`);
    return ['ROLLED_BACK', 'FAILED', 'SUCCEEDED'].includes(result.body.status) ? result.body : false;
  }, { description: 'post-switch cancellation rollback' });
  assert.equal(cancelled.status, 'ROLLED_BACK', `${cancelled.errorMessage || ''}\n${updaterOutput.join('')}`);
  assert.equal(cancelled.cancelRequested, true);

  trafficStage = 'crash-recovery';
  const interruptedManifest = { ...manifest, version: 'interrupted', commit: interruptedCommit };
  const interruptedPreflight = await call('/v1/update/preflight', { method: 'POST', body: JSON.stringify({ manifest: interruptedManifest }) });
  assert.equal(interruptedPreflight.body.ready, true, JSON.stringify(interruptedPreflight.body.blockers));
  const interruptedInstall = await call('/v1/update/install', { method: 'POST', body: JSON.stringify({ preflightId: interruptedPreflight.body.preflightId }) });
  assert.equal(interruptedInstall.response.status, 202, JSON.stringify(interruptedInstall.body));
  await waitFor(async () => {
    const result = await call(`/v1/update/tasks/${interruptedInstall.body.taskId}`);
    if (['FAILED', 'ROLLED_BACK', 'CANCELLED', 'SUCCEEDED'].includes(result.body.status)) {
      throw new Error(`interrupted task reached ${result.body.status} before crash point: ${result.body.errorMessage || result.body.message}\n${updaterOutput.join('')}`);
    }
    return result.body.phase === 'DRAINING_OLD' && result.body.status === 'RUNNING';
  }, { description: 'post-switch drain phase before simulated updater crash' });
  const crashedUpdater = updater;
  crashedUpdater.kill('SIGKILL');
  await once(crashedUpdater, 'close');
  startUpdater();
  await waitFor(async () => (await call('/v1/health')).response.ok, { description: 'restarted updater health' });
  const recovered = await waitFor(async () => {
    const result = await call(`/v1/update/tasks/${interruptedInstall.body.taskId}`);
    return ['ROLLED_BACK', 'FAILED'].includes(result.body.status) ? result.body : false;
  }, { description: 'persistent task recovery after updater crash' });
  assert.equal(recovered.status, 'ROLLED_BACK', `${recovered.errorMessage || ''}\n${updaterOutput.join('')}`);

  await waitFor(() => ['async', 'job'].every((worker) => queueCount(queueRoot, worker, 'done') === 700), {
    timeoutMs: 60_000,
    description: 'all queued worker tasks to finish',
  });
  hammering = false;
  await hammerPromise;

  assert.ok(traffic.length > 100, `expected sustained traffic samples, got ${traffic.length}`);
  assert.equal(traffic.filter((sample) => sample.status !== 200).length, 0, JSON.stringify(traffic.filter((sample) => sample.status !== 200).slice(0, 10)));
  const commits = traffic.map((sample) => sample.commit);
  const firstNew = commits.indexOf(newCommit);
  const lastOld = commits.lastIndexOf(oldCommit);
  assert.ok(firstNew > 0, 'traffic never switched to the new slot');
  assert.ok(lastOld > firstNew, 'traffic never switched back to the original slot');
  for (const worker of ['async', 'job']) {
    assert.equal(queueCount(queueRoot, worker, 'pending'), 0);
    assert.equal(queueCount(queueRoot, worker, 'processing'), 0);
    assert.equal(queueCount(queueRoot, worker, 'done'), 700);
    assert.equal(queueCount(queueRoot, worker, 'duplicates'), 0);
  }
});
