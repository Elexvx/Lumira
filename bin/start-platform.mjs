#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import process from 'node:process';

const args = process.argv.slice(2);
const unsupportedLegacyArgs = ['--skip-infra', '--skip-services', '--skip-lumira-ui'];
const requestedLegacyArgs = unsupportedLegacyArgs.filter((arg) => args.includes(arg));
const skipBuild = args.includes('--no-build') || args.includes('--skip-build');
const help = args.includes('--help') || args.includes('-h');
const useLocalMysql = args.includes('--local-mysql');

if (help) {
  console.log(`Usage: node bin/start-platform.mjs [options]

Options:
  --no-build, --skip-build  Reuse existing images/containers and skip the rebuild step.
  -h, --help                Show this help message.

Notes:
  start-platform delegates to bin/deploy-container.mjs and now brings up the
  local runtime topology including lumira-server, lumira-async,
  lumira-job-executor, api-proxy, and optional local profiles from deploy/.env.
  It defaults to --local-mysql so local startup stays on the localhost API
  proxy path instead of requiring production TLS assets.
`);
  process.exit(0);
}

if (requestedLegacyArgs.length > 0) {
  console.error(`[start] Unsupported legacy option(s): ${requestedLegacyArgs.join(', ')}`);
  console.error('[start] start-platform now delegates to bin/deploy-container.mjs and no longer supports the old skip-* flags.');
  console.error('[start] Use --no-build to skip the rebuild step, or call bin/deploy-container.mjs directly for advanced deployment flags.');
  process.exit(1);
}

const translatedArgs = skipBuild
  ? args.filter((arg) => arg !== '--no-build' && arg !== '--skip-build')
  : ['--rebuild', ...args];
if (!useLocalMysql) {
  translatedArgs.unshift('--local-mysql');
}

const result = spawnSync(
  process.execPath,
  ['bin\/deploy-container.mjs', ...translatedArgs],
  {
    cwd: new URL('..', import.meta.url),
    stdio: 'inherit',
  },
);

process.exit(result.status ?? 1);
