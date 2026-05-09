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

function ensureEnvFile() {
  if (existsSync(envPath)) {
    return;
  }

  const generatedValues = {
    DB_PASSWORD: randomSecret('mysql'),
    NACOS_AUTH_TOKEN: randomSecret('nacos-token'),
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

log('Backend deployment started.');
log('Gateway health: http://127.0.0.1:8081/actuator/health');
log('Optional frontend container: docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml --profile frontend up -d --build frontend');
