#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import process from 'node:process';

const result = spawnSync(
  process.execPath,
  ['scripts/deploy-container.mjs', '--stop', ...process.argv.slice(2)],
  {
    cwd: new URL('..', import.meta.url),
    stdio: 'inherit',
  },
);

process.exit(result.status ?? 1);
