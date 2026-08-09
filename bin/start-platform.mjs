#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import path from 'node:path';
import process from 'node:process';
import readline from 'node:readline/promises';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const rawArgs = process.argv.slice(2);

function printHelp() {
  console.log(`Usage: node bin/start-platform.mjs [local|production] [options]

Startup modes:
  local         Run the frontend and Java services as native host processes.
  production    Run the production topology through Docker Compose.

When no mode is supplied in an interactive terminal, a two-option menu is
shown. Automation must always pass the mode explicitly.

Examples:
  node bin/start-platform.mjs local
  node bin/start-platform.mjs local --check
  node bin/start-platform.mjs local --full
  node bin/start-platform.mjs production
  node bin/start-platform.mjs production --check

Run "node bin/start-local.mjs --help" or
"node bin/start-production.mjs --help" for mode-specific options.
`);
}

function normalizeMode(value) {
  const normalized = String(value ?? '').trim().toLowerCase();
  if (['1', 'local', 'dev', 'development'].includes(normalized)) {
    return 'local';
  }
  if (['2', 'prod', 'production'].includes(normalized)) {
    return 'production';
  }
  return '';
}

function parseMode(args) {
  const forwardedArgs = [...args];
  const inlineModeIndex = forwardedArgs.findIndex((arg) => arg.startsWith('--mode='));
  if (inlineModeIndex >= 0) {
    const [modeArg] = forwardedArgs.splice(inlineModeIndex, 1);
    return { mode: normalizeMode(modeArg.slice('--mode='.length)), forwardedArgs, provided: true };
  }

  const modeFlagIndex = forwardedArgs.indexOf('--mode');
  if (modeFlagIndex >= 0) {
    const modeValue = forwardedArgs[modeFlagIndex + 1];
    forwardedArgs.splice(modeFlagIndex, modeValue ? 2 : 1);
    return { mode: normalizeMode(modeValue), forwardedArgs, provided: true };
  }

  if (forwardedArgs[0] && !forwardedArgs[0].startsWith('-')) {
    return { mode: normalizeMode(forwardedArgs.shift()), forwardedArgs, provided: true };
  }

  return { mode: '', forwardedArgs, provided: false };
}

async function selectMode() {
  if (!process.stdin.isTTY || !process.stdout.isTTY) {
    console.error('[start] A startup mode is required in a non-interactive terminal.');
    console.error('[start] Use "local" or "production".');
    process.exit(1);
  }

  const terminal = readline.createInterface({ input: process.stdin, output: process.stdout });
  try {
    console.log('Select the Lumira startup environment:');
    console.log('  1. Local debugging (native Java + Node.js processes)');
    console.log('  2. Production (Docker Compose containers)');
    const answer = await terminal.question('Environment [1]: ');
    return normalizeMode(answer || '1');
  } finally {
    terminal.close();
  }
}

if (rawArgs.length === 1 && ['--help', '-h'].includes(rawArgs[0])) {
  printHelp();
  process.exit(0);
}

const parsed = parseMode(rawArgs);
if (parsed.provided && !parsed.mode) {
  console.error('[start] Unknown environment. Use "local" or "production".');
  process.exit(1);
}
const mode = parsed.mode || await selectMode();
if (!mode) {
  console.error('[start] Unknown environment. Use "local" or "production".');
  process.exit(1);
}

const script = mode === 'local' ? 'start-local.mjs' : 'start-production.mjs';
const result = spawnSync(process.execPath, [path.join(repoRoot, 'bin', script), ...parsed.forwardedArgs], {
  cwd: repoRoot,
  stdio: 'inherit',
  env: process.env,
});

process.exit(result.status ?? 1);
