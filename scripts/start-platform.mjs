#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import process from 'node:process';

const args = process.argv.slice(2);
const skipBuild = args.includes('--no-build') || args.includes('--skip-build');
const translatedArgs = skipBuild
  ? args.filter((arg) => arg !== '--no-build' && arg !== '--skip-build')
  : ['--rebuild', ...args];

const result = spawnSync(
  process.execPath,
  ['scripts/deploy-container.mjs', ...translatedArgs],
  {
    cwd: new URL('..', import.meta.url),
    stdio: 'inherit',
  },
);

process.exit(result.status ?? 1);
