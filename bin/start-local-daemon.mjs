#!/usr/bin/env node

import { spawn, spawnSync } from 'node:child_process';
import { closeSync, existsSync, mkdirSync, openSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { parseEnvFile } from './lib/env-utils.mjs';
import {
  createLocalReadinessTargets,
  waitForLocalReadiness,
} from './lib/local-readiness.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const runtimeDirectory = path.join(repoRoot, 'runtime-logs');
const statePath = path.join(repoRoot, '.lumira-local.pid');
const logPath = path.join(runtimeDirectory, 'local-native.log');
const rawArgs = process.argv.slice(2);

function optionValue(name) {
  const inline = rawArgs.find((arg) => arg.startsWith(`${name}=`));
  if (inline) return inline.slice(name.length + 1);
  const index = rawArgs.indexOf(name);
  return index >= 0 ? rawArgs[index + 1] : undefined;
}

function numberValue(value, fallback) {
  const parsed = Number(value ?? fallback);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function stopChild(pid) {
  if (!Number.isInteger(pid) || pid <= 0) return;
  if (process.platform === 'win32') {
    spawnSync('taskkill.exe', ['/pid', String(pid), '/t', '/f'], { stdio: 'ignore' });
    return;
  }
  try {
    process.kill(-pid, 'SIGTERM');
  } catch {
    // The launcher already exited.
  }
}

function isAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

if (existsSync(statePath)) {
  try {
    const state = JSON.parse(readFileSync(statePath, 'utf8'));
    if (Number.isInteger(state.pid) && isAlive(state.pid)) {
      console.error(`[local] Native environment is already running (launcher PID ${state.pid}).`);
      console.error(`[local] Log: ${path.relative(repoRoot, state.logPath || logPath)}`);
      process.exit(1);
    }
  } catch {
    // A malformed or stale state file is replaced after the native preflight passes.
  }
}

const preflight = spawnSync(process.execPath, [
  path.join(repoRoot, 'bin', 'start-local.mjs'),
  ...rawArgs.filter((arg) => arg !== '--check'),
  '--check',
], {
  cwd: repoRoot,
  env: process.env,
  stdio: 'inherit',
});
if (preflight.status !== 0 || rawArgs.includes('--check')) {
  process.exit(preflight.status ?? 1);
}

mkdirSync(runtimeDirectory, { recursive: true });
const logFd = openSync(logPath, 'a');
writeFileSync(logFd, `\n[local] ===== detached start ${new Date().toISOString()} =====\n`);

const child = spawn(process.execPath, [
  path.join(repoRoot, 'bin', 'start-local.mjs'),
  ...rawArgs,
], {
  cwd: repoRoot,
  env: { ...process.env, LUMIRA_LOCAL_DAEMON: '1' },
  detached: true,
  windowsHide: true,
  stdio: ['ignore', logFd, logFd],
});

writeFileSync(statePath, `${JSON.stringify({
  pid: child.pid,
  startedAt: new Date().toISOString(),
  logPath,
  args: rawArgs,
}, null, 2)}\n`);
closeSync(logFd);
child.unref();

const envPathValue = optionValue('--env-file');
const envPath = envPathValue
  ? path.resolve(repoRoot, envPathValue)
  : path.join(repoRoot, 'lumira-backend', '.env');
const fileEnv = existsSync(envPath) ? parseEnvFile(envPath) : {};
const full = rawArgs.includes('--full') || rawArgs.includes('--workers');
const includeFrontend = !rawArgs.includes('--backend-only');
const targets = createLocalReadinessTargets({
  backendPort: numberValue(optionValue('--backend-port') || process.env.LUMIRA_LOCAL_BACKEND_PORT || fileEnv.SERVER_PORT, 8080),
  frontendPort: numberValue(optionValue('--frontend-port') || process.env.LUMIRA_LOCAL_FRONTEND_PORT || fileEnv.LUMIRA_UI_PORT, 8000),
  asyncPort: numberValue(optionValue('--async-port') || process.env.LUMIRA_LOCAL_ASYNC_PORT || fileEnv.LUMIRA_ASYNC_PORT, 8081),
  jobPort: numberValue(optionValue('--job-port') || process.env.LUMIRA_LOCAL_JOB_PORT || fileEnv.LUMIRA_JOB_PORT, 8082),
  includeFrontend,
  includeWorkers: full,
});
const readinessTimeoutMs = numberValue(
  optionValue('--ready-timeout')
    || process.env.LUMIRA_LOCAL_READINESS_TIMEOUT_SECONDS
    || fileEnv.LUMIRA_LOCAL_READINESS_TIMEOUT_SECONDS,
  180,
) * 1_000;

try {
  console.log(`[local] Native environment launcher started in the background (PID ${child.pid}); waiting for business readiness...`);
  await waitForLocalReadiness({
    targets,
    timeoutMs: readinessTimeoutMs,
    cancelled: () => !isAlive(child.pid),
  });
  console.log('[local] Native environment is business-ready.');
  console.log(`[local] Log: ${path.relative(repoRoot, logPath)}`);
  console.log('[local] Stop: npm run stop:local');
} catch (error) {
  stopChild(child.pid);
  try {
    unlinkSync(statePath);
  } catch {
    // The state file may already have been removed by a concurrent stop.
  }
  console.error(`[local] Startup failed before business readiness: ${error instanceof Error ? error.message : String(error)}`);
  console.error(`[local] Log: ${path.relative(repoRoot, logPath)}`);
  process.exit(1);
}
