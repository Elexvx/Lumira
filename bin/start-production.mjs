#!/usr/bin/env node

import { existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { parseEnvFile } from './lib/env-utils.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const deployEnvPath = path.join(repoRoot, 'deploy', '.env');
const composePath = path.join(repoRoot, 'deploy', 'docker-compose.prod.yml');
const args = process.argv.slice(2);
const help = args.includes('--help') || args.includes('-h');
const checkOnly = args.includes('--check');

function printHelp() {
  console.log(`Usage: node bin/start-production.mjs [options]

Starts the configured production topology with deploy/docker-compose.prod.yml.
Application services always receive the prod Spring profile. Existing images
are reused by default; this command does not implicitly pull or rebuild them.

Options:
  --check      Validate deploy/.env and the resolved Compose model, then exit.
  -h, --help   Show this help message.

All other options are forwarded to bin/deploy-container.mjs. Common examples:
  --ps         Show container status.
  --logs       Follow container logs.
  --stop       Stop the production topology.
  --pull       Explicitly pull configured images before startup.
  --rebuild    Explicitly rebuild images from the current source tree.
`);
}

function validateProductionEnvironment(env) {
  const requiredKeys = [
    'API_DOMAIN',
    'FRONTEND_ORIGIN',
    'DB_URL',
    'DB_USERNAME',
    'DB_PASSWORD',
    'REDIS_PASSWORD',
    'JWT_SECRET',
    'FIELD_SECRET',
    'PLUGIN_SIGNATURE_SECRET',
    'CORS_ALLOWED_ORIGIN_PATTERNS',
    'PLATFORM_UPDATE_AGENT_TOKEN',
    'SAAS_INTERNAL_SYSTEM_TOKEN',
    'SAAS_INTERNAL_AUTH_TOKEN',
    'SAAS_INTERNAL_AUTH_SYSTEM_TOKEN',
    'SAAS_INTERNAL_FILE_TOKEN',
    'SAAS_INTERNAL_MESSAGE_TOKEN',
    'SAAS_INTERNAL_PAYMENT_TOKEN',
    'SAAS_INTERNAL_PLUGIN_TOKEN',
    'SAAS_INTERNAL_TEAM_TOKEN',
    'SAAS_INTERNAL_JOB_TOKEN',
    'XXL_JOB_ADMIN_ACCESS_TOKEN',
    'XXL_JOB_ACCESS_TOKEN',
    'XXL_JOB_LOGIN_PASSWORD',
  ];
  const invalidKeys = requiredKeys.filter((key) => {
    const value = String(env[key] ?? '').trim();
    return !value || /^change-me(?:-|$)/i.test(value);
  });
  if (invalidKeys.length > 0) {
    throw new Error(`deploy/.env has missing or placeholder production values: ${invalidKeys.join(', ')}`);
  }

  const activeSlot = String(env.LUMIRA_ACTIVE_SLOT || 'blue').toLowerCase();
  if (!['blue', 'green'].includes(activeSlot)) {
    throw new Error('LUMIRA_ACTIVE_SLOT must be either blue or green.');
  }
  return activeSlot;
}

function runComposeCheck(activeSlot) {
  const dockerArgs = [
    'compose',
    '--env-file', 'deploy/.env',
    '-f', path.relative(repoRoot, composePath).replaceAll(path.sep, '/'),
    '--profile', activeSlot,
    '--profile', 'edge',
    'config',
    '--quiet',
  ];
  let command = 'docker';
  let commandArgs = dockerArgs;
  if (process.platform === 'win32') {
    const where = spawnSync('where.exe', ['docker'], { encoding: 'utf8', stdio: 'pipe' });
    const dockerPath = where.status === 0
      ? where.stdout.split(/\r?\n/).map((line) => line.trim()).find(Boolean)
      : '';
    if (dockerPath && /\.cmd$/i.test(dockerPath)) {
      command = 'cmd.exe';
      commandArgs = ['/d', '/c', dockerPath, ...dockerArgs];
    } else if (dockerPath) {
      command = dockerPath;
    }
  }
  const result = spawnSync(command, commandArgs, {
    cwd: repoRoot,
    stdio: 'inherit',
    env: {
      ...process.env,
      SPRING_PROFILES_ACTIVE: 'prod',
      ASYNC_RUNTIME_PROFILES_ACTIVE: 'prod',
      JOB_EXECUTOR_PROFILES_ACTIVE: 'prod',
      OBSERVABILITY_ENVIRONMENT: 'prod',
    },
  });
  return result.status ?? 1;
}

if (help) {
  printHelp();
  process.exit(0);
}

if (args.includes('--local-mysql')) {
  console.error('[production] --local-mysql belongs to a local container topology and is not allowed in production mode.');
  process.exit(1);
}

const localOnlyOptions = [
  '--full',
  '--workers',
  '--backend-only',
  '--frontend-only',
  '--skip-build',
  '--no-build',
  '--backend-port',
  '--frontend-port',
  '--async-port',
  '--job-port',
  '--env-file',
  '--allow-remote-services',
];
const invalidLocalOption = args.find((arg) => localOnlyOptions.some((option) => arg === option || arg.startsWith(`${option}=`)));
if (invalidLocalOption) {
  console.error(`[production] ${invalidLocalOption} is only valid in native local mode.`);
  process.exit(1);
}

if (!existsSync(deployEnvPath)) {
  console.error('[production] deploy/.env is missing. Complete the production installation/configuration first.');
  console.error('[production] Template: deploy/.env.example');
  process.exit(1);
}

let activeSlot;
try {
  activeSlot = validateProductionEnvironment(parseEnvFile(deployEnvPath));
} catch (error) {
  console.error(`[production] ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}

if (checkOnly) {
  const status = runComposeCheck(activeSlot);
  if (status === 0) {
    console.log(`[production] Production configuration is valid (active slot: ${activeSlot}).`);
  }
  process.exit(status);
}

const forwardedArgs = args.filter((arg) => arg !== '--check');
const result = spawnSync(process.execPath, [path.join(repoRoot, 'bin', 'deploy-container.mjs'), ...forwardedArgs], {
  cwd: repoRoot,
  stdio: 'inherit',
  env: {
    ...process.env,
    SPRING_PROFILES_ACTIVE: 'prod',
    ASYNC_RUNTIME_PROFILES_ACTIVE: 'prod',
    JOB_EXECUTOR_PROFILES_ACTIVE: 'prod',
    OBSERVABILITY_ENVIRONMENT: 'prod',
  },
});

process.exit(result.status ?? 1);
