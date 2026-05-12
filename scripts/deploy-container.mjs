#!/usr/bin/env node

import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..');
const envExamplePath = path.join(repoRoot, 'deploy', '.env.example');
const envPath = path.join(repoRoot, 'deploy', '.env');
const composeFile = path.join(repoRoot, 'deploy', 'docker-compose.prod.yml');

const args = new Set(process.argv.slice(2));
const rebuild = args.has('--rebuild');
const stop = args.has('--stop');
const logs = args.has('--logs');
const ps = args.has('--ps');
const reset = args.has('--reset');
const help = args.has('--help') || args.has('-h');
const skipCheck = args.has('--skip-check');

function log(message) {
  console.log(`[deploy] ${message}`);
}

function run(command, commandArgs, options = {}) {
  const result = spawnSync(command, commandArgs, {
    cwd: repoRoot,
    stdio: 'inherit',
    shell: false,
    ...options,
  });

  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

function runWithRetry(command, commandArgs, retries = 1) {
  for (let attempt = 0; attempt <= retries; attempt += 1) {
    const result = spawnSync(command, commandArgs, {
      cwd: repoRoot,
      stdio: 'inherit',
      shell: false,
    });

    if (result.status === 0) {
      return;
    }

    if (attempt === retries) {
      process.exit(result.status ?? 1);
    }

    log('Command failed, retrying once. This often recovers transient registry or Maven network errors.');
  }
}

function output(command, commandArgs) {
  return spawnSync(command, commandArgs, {
    cwd: repoRoot,
    encoding: 'utf8',
    shell: false,
  });
}

function printHelp() {
  console.log(`Usage: node scripts/deploy-container.mjs [options]

Options:
  --rebuild   Force image rebuild.
  --stop      Stop the deployment.
  --reset     Stop and remove volumes. This deletes database and uploaded data.
  --logs      Follow service logs.
  --ps        Show container status.
  --skip-check Skip deployment health checks after startup.
  -h, --help  Show this help message.
`);
}

function ensureDockerReady() {
  const result = output('docker', ['info']);
  if (result.status !== 0) {
    console.error('Docker is not running. Start Docker Desktop or enable Docker in 1Panel, then run this command again.');
    process.exit(1);
  }
}

function randomSecret(prefix) {
  return `${prefix}-${randomBytes(24).toString('hex')}`;
}

function randomBase64Secret(byteLength = 48) {
  return randomBytes(byteLength).toString('base64');
}

function ensureEnvFile() {
  if (existsSync(envPath)) {
    return;
  }

  const generatedValues = {
    DB_PASSWORD: randomSecret('mysql'),
    NACOS_AUTH_TOKEN: randomBase64Secret(),
    NACOS_AUTH_IDENTITY_KEY: randomSecret('nacos-key'),
    NACOS_AUTH_IDENTITY_VALUE: randomSecret('nacos-value'),
    JWT_SECRET: randomSecret('jwt'),
    PLUGIN_SIGNATURE_SECRET: randomSecret('plugin-signature'),
    SAAS_JOB_INTERNAL_TOKEN: randomSecret('job-token'),
    XXL_JOB_ADMIN_ACCESS_TOKEN: randomSecret('xxl-token'),
    XXL_JOB_ACCESS_TOKEN: randomSecret('xxl-token'),
    XXL_JOB_LOGIN_PASSWORD: randomSecret('xxl-password'),
  };

  let content = readFileSync(envExamplePath, 'utf8');
  for (const [key, value] of Object.entries(generatedValues)) {
    content = content.replace(new RegExp(`^${key}=.*$`, 'm'), `${key}=${value}`);
  }

  writeFileSync(envPath, content);
  log('Generated deploy/.env with random local deployment secrets.');
}

function composeArgs(...extraArgs) {
  return ['compose', '--env-file', 'deploy/.env', '-f', composeFile, ...extraArgs];
}

async function probeHttp(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? 5_000);

  try {
    const response = await fetch(url, { signal: controller.signal });
    return {
      ok: response.ok,
      status: response.status,
      text: await response.text(),
    };
  } catch (err) {
    return {
      ok: false,
      status: 0,
      text: err instanceof Error ? err.message : String(err),
    };
  } finally {
    clearTimeout(timeout);
  }
}

async function waitForHttp(url, label, options = {}) {
  const timeoutMs = options.timeoutMs ?? 240_000;
  const intervalMs = options.intervalMs ?? 3_000;
  const startedAt = Date.now();
  let lastResult = null;

  while (Date.now() - startedAt <= timeoutMs) {
    // eslint-disable-next-line no-await-in-loop
    const result = await probeHttp(url, { timeoutMs: options.requestTimeoutMs ?? 5_000 });
    lastResult = result;

    const body = result.text.toLowerCase();
    const expected = options.includes?.toLowerCase();
    const expectedStatus = options.expectedStatus;
    const statusMatches = expectedStatus ? result.status === expectedStatus : result.ok;
    if (statusMatches && (!expected || body.includes(expected))) {
      log(`${label} is ready at ${url}`);
      return;
    }

    // eslint-disable-next-line no-await-in-loop
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }

  const status = lastResult?.status ? `status=${lastResult.status}` : 'no HTTP response';
  throw new Error(`${label} is not ready at ${url} (${status}).`);
}

async function checkDeployment() {
  const baseUrl = process.env.DEPLOY_CHECK_BASE_URL || 'http://127.0.0.1:8000';
  const gatewayUrl = process.env.DEPLOY_CHECK_GATEWAY_URL || 'http://127.0.0.1:8081';

  log('Running deployment health checks...');
  await waitForHttp(`${baseUrl}/health`, 'API proxy');
  await waitForHttp(`${baseUrl}/api/health`, 'system API through API proxy');
  await waitForHttp(`${gatewayUrl}/actuator/health`, 'gateway actuator');
  await waitForHttp(`${baseUrl}/api/v1/public/login-capabilities`, 'public login capabilities API');
  await waitForHttp(`${baseUrl}/api/v1/localization/languages`, 'protected localization management API is routed', { expectedStatus: 401 });
  log('Deployment health checks passed.');
}

if (help) {
  printHelp();
  process.exit(0);
}

ensureDockerReady();
ensureEnvFile();

if (reset) {
  run('docker', composeArgs('down', '-v', '--remove-orphans'));
  process.exit(0);
}

if (stop) {
  run('docker', composeArgs('down', '--remove-orphans'));
  process.exit(0);
}

if (ps) {
  run('docker', composeArgs('ps'));
  process.exit(0);
}

if (logs) {
  run('docker', composeArgs('logs', '-f', '--tail=200'));
  process.exit(0);
}

run('docker', composeArgs('config'), { stdio: 'ignore' });

const upArgs = ['up', '-d'];
if (rebuild) {
  upArgs.push('--build');
}

runWithRetry('docker', composeArgs(...upArgs), 1);
run('docker', composeArgs('ps'));

if (!skipCheck) {
  await checkDeployment();
}

log('Complete deployment started.');
log('API proxy: http://127.0.0.1:8000');
log('Gateway health: http://127.0.0.1:8081/actuator/health');
