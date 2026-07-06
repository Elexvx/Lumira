#!/usr/bin/env node

import path from 'node:path';
import process from 'node:process';

import { parseEnvFile } from './lib/env-utils.mjs';
import { optionalOutput, resolveRepoRoot } from './lib/exec-utils.mjs';
import { probeHttp } from './lib/http-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const deployEnvPath = path.join(repoRoot, 'deploy', '.env');
const buildIdentityPath = process.env.BUILD_IDENTITY_FILE || path.join(repoRoot, 'deploy', 'build-identity.env');
const deployEnv = parseEnvFile(deployEnvPath);
const buildIdentity = parseEnvFile(buildIdentityPath);
const rawArgs = process.argv.slice(2);
const args = parseArgs(rawArgs);
const baseUrl = resolveBaseUrl();
const endpoint = args.endpoint || process.env.RUNTIME_VERSION_ENDPOINT || '/api/v2/runtime/version';
const timeoutMs = Number.parseInt(args.timeout || process.env.RUNTIME_VERSION_TIMEOUT_MS || '8000', 10);
const jsonOutput = args.json === true || process.env.RUNTIME_VERSION_JSON === 'true';

function parseArgs(argv) {
  const parsed = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--help' || arg === '-h') {
      parsed.help = true;
      continue;
    }
    if (arg === '--json') {
      parsed.json = true;
      continue;
    }
    if (arg.startsWith('--base-url=')) {
      parsed.baseUrl = arg.slice('--base-url='.length);
      continue;
    }
    if (arg === '--base-url') {
      parsed.baseUrl = argv[index + 1];
      index += 1;
      continue;
    }
    if (arg.startsWith('--endpoint=')) {
      parsed.endpoint = arg.slice('--endpoint='.length);
      continue;
    }
    if (arg === '--endpoint') {
      parsed.endpoint = argv[index + 1];
      index += 1;
      continue;
    }
    if (arg.startsWith('--timeout=')) {
      parsed.timeout = arg.slice('--timeout='.length);
      continue;
    }
    if (arg === '--timeout') {
      parsed.timeout = argv[index + 1];
      index += 1;
    }
  }
  return parsed;
}

function printHelp() {
  console.log(`Usage: node bin/check-runtime-version.mjs [options]

Options:
  --base-url <url>   Target origin. Defaults to RUNTIME_VERSION_BASE_URL,
                     DEPLOY_CHECK_BASE_URL, API_DOMAIN, or https://bm.aiadc.org.cn.
  --endpoint <path>  Runtime version API path. Defaults to /api/v2/runtime/version.
  --timeout <ms>     Request timeout. Defaults to 8000.
  --json             Print machine-readable result.
  -h, --help         Show this help.

Examples:
  node bin/check-runtime-version.mjs
  node bin/check-runtime-version.mjs --base-url https://bm.aiadc.org.cn
`);
}

function resolveBaseUrl() {
  const explicit = args.baseUrl
    || process.env.RUNTIME_VERSION_BASE_URL
    || process.env.DEPLOY_CHECK_BASE_URL;
  if (hasText(explicit)) {
    return trimTrailingSlash(explicit);
  }

  const apiDomain = firstText(process.env.API_DOMAIN, deployEnv.API_DOMAIN);
  if (apiDomain) {
    return trimTrailingSlash(apiDomain.startsWith('http') ? apiDomain : `https://${apiDomain}`);
  }

  return 'https://bm.aiadc.org.cn';
}

function endpointUrl() {
  if (/^https?:\/\//i.test(endpoint)) {
    return endpoint;
  }
  return `${baseUrl}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;
}

function expectedValues() {
  const gitCommit = optionalOutput('git', ['rev-parse', '--short=12', 'HEAD'], { cwd: repoRoot });
  const gitBranch = optionalOutput('git', ['rev-parse', '--abbrev-ref', 'HEAD'], { cwd: repoRoot });
  const dirtyStatus = optionalOutput('git', ['status', '--porcelain=v1', '--untracked-files=all'], { cwd: repoRoot });
  return {
    frontendVersion: firstText(process.env.FRONTEND_VERSION, buildIdentity.FRONTEND_VERSION),
    backendVersion: firstText(process.env.BACKEND_VERSION, buildIdentity.BACKEND_VERSION, process.env.BUILD_VERSION, buildIdentity.BUILD_VERSION),
    databaseVersion: firstText(process.env.DATABASE_VERSION, buildIdentity.DATABASE_VERSION),
    version: firstText(process.env.APP_VERSION, buildIdentity.APP_VERSION, process.env.BUILD_VERSION, buildIdentity.BUILD_VERSION),
    commitId: firstText(process.env.GIT_COMMIT, buildIdentity.GIT_COMMIT, gitCommit),
    branch: firstText(process.env.GIT_BRANCH, buildIdentity.GIT_BRANCH, gitBranch),
    buildTime: firstText(process.env.BUILD_TIME, buildIdentity.BUILD_TIME),
    dirty: Boolean(dirtyStatus.trim()),
  };
}

function normalizeCommit(value) {
  const text = String(value ?? '').trim();
  if (/^[0-9a-f]{40}$/i.test(text)) {
    return text.slice(0, 12).toLowerCase();
  }
  return text.toLowerCase();
}

function comparableValue(field, value) {
  if (field === 'commitId') {
    return normalizeCommit(value);
  }
  return String(value ?? '').trim();
}

function compareField(field, remote, expected, label = field) {
  const expectedText = comparableValue(field, expected);
  const remoteText = comparableValue(field, remote);
  if (!hasText(expectedText)) {
    return {
      field,
      label,
      status: 'skip',
      expected: expected || '',
      remote: remote || '',
      message: 'local expected value is missing',
    };
  }
  if (!hasText(remoteText) || remoteText === 'unknown') {
    return {
      field,
      label,
      status: 'fail',
      expected,
      remote: remote || '',
      message: 'remote value is missing',
    };
  }
  return {
    field,
    label,
    status: remoteText === expectedText ? 'pass' : 'fail',
    expected,
    remote,
    message: remoteText === expectedText ? 'matched' : 'remote does not match local',
  };
}

function firstText(...values) {
  for (const value of values) {
    const text = String(value ?? '').trim();
    if (text && text.toLowerCase() !== 'unknown') {
      return text;
    }
  }
  return '';
}

function hasText(value) {
  return String(value ?? '').trim().length > 0;
}

function trimTrailingSlash(value) {
  return String(value ?? '').trim().replace(/\/+$/, '');
}

function parseVersionPayload(text) {
  const payload = JSON.parse(text);
  return payload?.data && typeof payload.data === 'object' ? payload.data : payload;
}

function printHumanReport(report) {
  console.log(`[version] Target: ${report.url}`);
  console.log(`[version] Remote service: ${report.remote.serviceName || 'unknown'} (${report.remote.artifact || 'unknown'})`);
  for (const item of report.comparisons) {
    const mark = item.status === 'pass' ? 'OK' : item.status === 'skip' ? 'SKIP' : 'FAIL';
    console.log(`[version] ${mark} ${item.label}: local=${item.expected || 'missing'} remote=${item.remote || 'missing'}`);
  }
  if (report.expected.dirty) {
    console.log('[version] WARN local working tree has uncommitted changes; commit comparison only proves the deployed commit, not local edits.');
  }
  console.log(report.latest ? '[version] Runtime is up to date.' : '[version] Runtime is NOT up to date.');
}

async function main() {
  if (args.help) {
    printHelp();
    return 0;
  }

  const url = endpointUrl();
  const result = await probeHttp(url, { timeoutMs });
  if (!result.ok) {
    const report = {
      latest: false,
      url,
      error: result.status ? `HTTP ${result.status}` : result.text,
    };
    if (jsonOutput) {
      console.log(JSON.stringify(report, null, 2));
    } else {
      console.error(`[version] FAIL ${url}: ${report.error}`);
    }
    return 1;
  }

  let remote;
  try {
    remote = parseVersionPayload(result.text);
  } catch (err) {
    const report = {
      latest: false,
      url,
      error: err instanceof Error ? err.message : String(err),
      raw: result.text,
    };
    if (jsonOutput) {
      console.log(JSON.stringify(report, null, 2));
    } else {
      console.error(`[version] FAIL ${url}: response is not valid JSON`);
    }
    return 1;
  }

  const expected = expectedValues();
  const comparisons = [
    compareField('frontendVersion', remote.frontendVersion, expected.frontendVersion, 'frontendVersion'),
    compareField('backendVersion', remote.backendVersion, expected.backendVersion, 'backendVersion'),
    compareField('databaseVersion', remote.databaseVersion, expected.databaseVersion, 'databaseVersion'),
    compareField('version', remote.version, expected.version, 'appVersion'),
    compareField('commitId', remote.commitId, expected.commitId, 'commitId'),
    compareField('branch', remote.branch, expected.branch, 'branch'),
  ];
  const compared = comparisons.filter((item) => item.status !== 'skip');
  const latest = compared.length > 0 && compared.every((item) => item.status === 'pass');
  const report = {
    latest,
    url,
    remote,
    expected,
    comparisons,
  };

  if (jsonOutput) {
    console.log(JSON.stringify(report, null, 2));
  } else {
    printHumanReport(report);
  }

  return latest ? 0 : 1;
}

process.exitCode = await main();
