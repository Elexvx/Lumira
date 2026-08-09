#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, unlinkSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const statePath = path.join(repoRoot, '.lumira-local.pid');

if (!existsSync(statePath)) {
  console.log('[local] No background native environment state was found.');
  process.exit(0);
}

let state;
try {
  state = JSON.parse(readFileSync(statePath, 'utf8'));
} catch {
  console.error('[local] The background state file is malformed; refusing to stop an unknown process.');
  process.exit(1);
}

const pid = Number(state.pid);
if (!Number.isInteger(pid) || pid < 1) {
  console.error('[local] The background state file does not contain a valid PID.');
  process.exit(1);
}

let alive = true;
try {
  process.kill(pid, 0);
} catch {
  alive = false;
}
if (!alive) {
  unlinkSync(statePath);
  console.log(`[local] Removed stale background state for PID ${pid}.`);
  process.exit(0);
}

if (process.platform === 'win32') {
  const query = spawnSync('powershell.exe', [
    '-NoProfile',
    '-Command',
    `$item = Get-CimInstance Win32_Process -Filter 'ProcessId = ${pid}'; if ($item) { $item.CommandLine }`,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
  const commandLine = String(query.stdout || '');
  if (query.status !== 0 || !/bin[\\/]start-local\.mjs/i.test(commandLine)) {
    console.error(`[local] PID ${pid} is not the recorded Lumira native launcher; refusing to terminate it.`);
    process.exit(1);
  }
  const stopped = spawnSync('taskkill.exe', ['/pid', String(pid), '/t', '/f'], { stdio: 'inherit' });
  if (stopped.status !== 0) {
    process.exit(stopped.status ?? 1);
  }
} else {
  process.kill(-pid, 'SIGTERM');
}

unlinkSync(statePath);
console.log(`[local] Stopped the native environment process tree rooted at PID ${pid}.`);
