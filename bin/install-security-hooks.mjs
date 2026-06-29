#!/usr/bin/env node

import { existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import process from 'node:process';

const repoRoot = path.resolve(import.meta.dirname, '..');
const hooksPath = '.githooks';
const requiredHooks = ['pre-commit', 'pre-push'];

function fail(message) {
  console.error(`[security-hooks] ${message}`);
  process.exit(1);
}

for (const hookName of requiredHooks) {
  const hookPath = path.join(repoRoot, hooksPath, hookName);
  if (!existsSync(hookPath)) {
    fail(`Missing required hook template: ${path.join(hooksPath, hookName).replaceAll('\\', '/')}`);
  }
}

const result = spawnSync('git', ['config', 'core.hooksPath', hooksPath], {
  cwd: repoRoot,
  encoding: 'utf8',
});

if (result.status !== 0) {
  const detail = result.stderr || result.stdout || 'git config failed';
  fail(detail.trim());
}

const verify = spawnSync('git', ['config', '--get', 'core.hooksPath'], {
  cwd: repoRoot,
  encoding: 'utf8',
});

if (verify.status !== 0 || verify.stdout.trim() !== hooksPath) {
  fail(`Expected core.hooksPath=${hooksPath}, got ${verify.stdout.trim() || '<unset>'}`);
}

console.log(JSON.stringify({
  configured: true,
  hooksPath,
  hooks: requiredHooks.map((hookName) => `${hooksPath}/${hookName}`),
}, null, 2));

