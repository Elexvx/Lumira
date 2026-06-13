#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import process from 'node:process';
import { existsSync } from 'node:fs';
import path from 'node:path';

import { parseEnvFile } from './lib/env-utils.mjs';
import { createLogger, resolveRepoRoot } from './lib/exec-utils.mjs';

const log = createLogger('e2e-reset');
const repoRoot = resolveRepoRoot(import.meta.url);
const envPath = path.join(repoRoot, 'deploy', '.env');
const resetConfirmPhrase = 'DELETE_LEGENDARY_DATA';
const localBaseUrl = process.env.PLAYWRIGHT_BASE_URL || process.env.DEPLOY_CHECK_BASE_URL || 'http://127.0.0.1:8000';
const localBackendUrl = process.env.DEPLOY_CHECK_BACKEND_URL || 'http://127.0.0.1:8080';

const env = existsSync(envPath) ? parseEnvFile(envPath) : {};

function isLocalUrl(value) {
  if (!value) {
    return true;
  }
  try {
    const url = new URL(value);
    return ['localhost', '127.0.0.1', '0.0.0.0', '::1'].includes(url.hostname);
  } catch {
    return false;
  }
}

function isLocalDomain(value) {
  if (!value) {
    return true;
  }
  const normalized = String(value).trim().toLowerCase();
  return ['localhost', '127.0.0.1', '0.0.0.0', '::1'].includes(normalized);
}

function assertResetAllowed() {
  if (process.env.PLAYWRIGHT_ALLOW_DB_RESET !== 'true') {
    console.error('Refusing to reset the E2E platform without PLAYWRIGHT_ALLOW_DB_RESET=true.');
    process.exit(1);
  }

  const checks = [
    ['PLAYWRIGHT_BASE_URL/DEPLOY_CHECK_BASE_URL', localBaseUrl, isLocalUrl],
    ['DEPLOY_CHECK_BACKEND_URL', localBackendUrl, isLocalUrl],
    ['API_DOMAIN', process.env.API_DOMAIN ?? env.API_DOMAIN, isLocalDomain],
    ['FRONTEND_ORIGIN', process.env.FRONTEND_ORIGIN ?? env.FRONTEND_ORIGIN, isLocalUrl],
    ['DEPLOY_CHECK_BASE_URL', process.env.DEPLOY_CHECK_BASE_URL, isLocalUrl],
  ];
  const unsafe = checks.filter(([, value, predicate]) => value && !predicate(value));

  if (unsafe.length > 0) {
    console.error('Refusing to reset because one or more deployment targets are not local:');
    unsafe.forEach(([label, value]) => {
      console.error(`- ${label}: ${value}`);
    });
    console.error('Use a dedicated local or isolated E2E environment before running the reset suite.');
    process.exit(1);
  }
}

function run(command, args, extraEnv = {}) {
  const result = spawnSync(command, args, {
    cwd: repoRoot,
    env: {
      ...process.env,
      ...extraEnv,
    },
    stdio: 'inherit',
  });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

assertResetAllowed();

console.error('');
console.error('DANGER: resetting the Lumira E2E platform will delete database volumes, uploaded files, plugins, and job logs.');
console.error('This command is intended only for local or isolated Playwright test environments.');
console.error('');

const resetEnv = {
  DEPLOY_RESET_CONFIRM: resetConfirmPhrase,
  DEPLOY_CHECK_BASE_URL: localBaseUrl,
  DEPLOY_CHECK_BACKEND_URL: localBackendUrl,
  PLAYWRIGHT_BASE_URL: localBaseUrl,
};

log('Stopping deployment and deleting Docker volumes.');
run(process.execPath, ['scripts/deploy-container.mjs', '--reset', '--local-mysql'], resetEnv);

log('Rebuilding and starting a clean local-mysql deployment.');
run(process.execPath, ['scripts/deploy-container.mjs', '--rebuild', '--local-mysql'], resetEnv);

log('Running deployment checks against the local E2E entrypoint.');
run(process.execPath, ['scripts/check-deployment.mjs'], resetEnv);

log('Clean E2E platform is ready.');
