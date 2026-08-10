#!/usr/bin/env node

import { spawn, spawnSync } from 'node:child_process';
import { closeSync, existsSync, mkdirSync, openSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const runtimeDirectory = path.join(repoRoot, 'runtime-logs');
const statePath = path.join(repoRoot, '.lumira-local.pid');
const logPath = path.join(runtimeDirectory, 'local-native.log');
const rawArgs = process.argv.slice(2);

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

console.log(`[local] Native environment started in the background (launcher PID ${child.pid}).`);
console.log(`[local] Log: ${path.relative(repoRoot, logPath)}`);
console.log('[local] Stop: npm run stop:local');
