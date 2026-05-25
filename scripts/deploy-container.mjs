#!/usr/bin/env node

import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..');
const envExamplePath = path.join(repoRoot, 'deploy', '.env.example');
const envPath = path.join(repoRoot, 'deploy', '.env');
const composeFile = path.join(repoRoot, 'deploy', 'docker-compose.prod.yml');

const args = new Set(process.argv.slice(2));
const rebuild = args.has('--rebuild');
const stop = args.has('--stop');
const logs = args.has('--logs');
const ps = args.has('--ps');
const reset = args.has('--reset');
const help = args.has('--help') || args.has('-h');
const skipCheck = args.has('--skip-check');
const observability = args.has('--observability');

function log(message) {
  console.log(`[deploy] ${message}`);
}

function run(command, commandArgs, options = {}) {
  const result = spawnSync(command, commandArgs, {
    cwd: repoRoot,
    stdio: 'inherit',
    shell: false,
    ...options,
  });

  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

function runWithRetry(command, commandArgs, retries = 1) {
  for (let attempt = 0; attempt <= retries; attempt += 1) {
    const result = spawnSync(command, commandArgs, {
      cwd: repoRoot,
      stdio: 'inherit',
      shell: false,
    });

    if (result.status === 0) {
      return;
    }

    if (attempt === retries) {
      process.exit(result.status ?? 1);
    }

    log('Command failed, retrying once. This often recovers transient registry or Maven network errors.');
  }
}

function output(command, commandArgs) {
  return spawnSync(command, commandArgs, {
    cwd: repoRoot,
    encoding: 'utf8',
    shell: false,
  });
}

function optionalOutput(command, commandArgs) {
  const result = output(command, commandArgs);
  return result.status === 0 ? result.stdout.trim() : '';
}

function configureBuildIdentity() {
  const env = parseEnvFile(envPath);
  const appVersion = process.env.APP_VERSION || env.APP_VERSION || '0.1.0';
  const gitCommit = process.env.GIT_COMMIT || env.GIT_COMMIT || optionalOutput('git', ['rev-parse', '--short=12', 'HEAD']);
  const gitBranch = process.env.GIT_BRANCH || env.GIT_BRANCH || optionalOutput('git', ['rev-parse', '--abbrev-ref', 'HEAD']);
  const buildTime = process.env.BUILD_TIME || env.BUILD_TIME || new Date().toISOString();
  const buildVersion = process.env.BUILD_VERSION || env.BUILD_VERSION || (gitCommit ? `${appVersion}+${gitCommit}` : appVersion);

  process.env.APP_VERSION = appVersion;
  process.env.BUILD_VERSION = buildVersion;
  process.env.BUILD_TIME = buildTime;
  process.env.GIT_COMMIT = gitCommit;
  process.env.GIT_BRANCH = gitBranch;
  if (observability) {
    process.env.OBSERVABILITY_ENVIRONMENT = process.env.OBSERVABILITY_ENVIRONMENT || env.OBSERVABILITY_ENVIRONMENT || 'prod';
    process.env.OTEL_JAVAAGENT_ENABLED = 'true';
    process.env.OTEL_EXPORTER_OTLP_ENDPOINT = process.env.OTEL_EXPORTER_OTLP_ENDPOINT || env.OTEL_EXPORTER_OTLP_ENDPOINT || 'http://alloy:4318';
  }
  log(`Build identity: version=${buildVersion}, commit=${gitCommit || 'unknown'}, branch=${gitBranch || 'unknown'}`);
}

function printHelp() {
  console.log(`Usage: node scripts/deploy-container.mjs [options]

Options:
  --rebuild   Force image rebuild.
  --stop      Stop the deployment.
  --reset     Stop and remove volumes. This deletes database and uploaded data.
  --logs      Follow service logs.
  --ps        Show container status.
  --skip-check Skip deployment health checks after startup.
  --observability Start Prometheus, Grafana, Loki, Tempo, and Alloy.
  -h, --help  Show this help message.
`);
}

function ensureDockerReady() {
  const result = output('docker', ['info']);
  if (result.status !== 0) {
    console.error('Docker is not running. Start Docker Desktop or enable Docker in 1Panel, then run this command again.');
    process.exit(1);
  }
}

function randomSecret(prefix) {
  return `${prefix}-${randomBytes(24).toString('hex')}`;
}

function randomBase64Secret(byteLength = 48) {
  return randomBytes(byteLength).toString('base64');
}

function generatedEnvDefaults() {
  return {
    DB_PASSWORD: randomSecret('mysql'),
    NACOS_AUTH_TOKEN: randomBase64Secret(),
    NACOS_AUTH_IDENTITY_KEY: randomSecret('nacos-key'),
    NACOS_AUTH_IDENTITY_VALUE: randomSecret('nacos-value'),
    JWT_SECRET: randomSecret('jwt'),
    FIELD_SECRET: randomSecret('field'),
    PLUGIN_SIGNATURE_SECRET: randomSecret('plugin-signature'),
    SAAS_JOB_INTERNAL_TOKEN: randomSecret('job-token'),
    XXL_JOB_ADMIN_ACCESS_TOKEN: randomSecret('xxl-token'),
    XXL_JOB_ACCESS_TOKEN: randomSecret('xxl-token'),
    XXL_JOB_LOGIN_PASSWORD: randomSecret('xxl-password'),
    OBSERVABILITY_ENVIRONMENT: 'prod',
    OTEL_JAVAAGENT_ENABLED: 'false',
    OTEL_EXPORTER_OTLP_ENDPOINT: 'http://alloy:4318',
    GRAFANA_ADMIN_USER: 'admin',
    GRAFANA_ADMIN_PASSWORD: randomSecret('grafana'),
    CORS_ALLOWED_ORIGIN_PATTERNS: 'http://localhost:*,http://127.0.0.1:*',
  };
}

function ensureEnvFile() {
  const generatedValues = generatedEnvDefaults();

  if (existsSync(envPath)) {
    let content = readFileSync(envPath, 'utf8');
    const missingEntries = Object.entries(generatedValues)
      .filter(([key]) => !new RegExp(`^${key}=`, 'm').test(content));

    if (missingEntries.length > 0) {
      content = `${content.trimEnd()}\n${missingEntries.map(([key, value]) => `${key}=${value}`).join('\n')}\n`;
      writeFileSync(envPath, content);
      log(`Backfilled deploy/.env keys: ${missingEntries.map(([key]) => key).join(', ')}`);
    }
    return;
  }

  let content = readFileSync(envExamplePath, 'utf8');
  for (const [key, value] of Object.entries(generatedValues)) {
    content = content.replace(new RegExp(`^${key}=.*$`, 'm'), `${key}=${value}`);
  }

  writeFileSync(envPath, content);
  log('Generated deploy/.env with random local deployment secrets.');
}

function parseEnvFile(filePath) {
  if (!existsSync(filePath)) {
    return {};
  }

  return Object.fromEntries(
    readFileSync(filePath, 'utf8')
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith('#') && line.includes('='))
      .map((line) => {
        const separatorIndex = line.indexOf('=');
        const key = line.slice(0, separatorIndex).trim();
        const value = line.slice(separatorIndex + 1).trim().replace(/^['"]|['"]$/g, '');
        return [key, value];
      })
  );
}

function ensureWritableDirectory(hostPath, label) {
  run('mkdir', ['-p', hostPath]);
  run('chmod', ['-R', 'a+rwX', hostPath]);
  log(`${label} is writable at ${hostPath}`);
}

function ensureHostMountedDirectories() {
  const env = parseEnvFile(envPath);
  const xxlJobLogPath = env.XXL_JOB_EXECUTOR_LOG_HOST_PATH || '/opt/legendary-invention/data/xxl-job/logs';
  const resolvedXxlJobLogPath = path.isAbsolute(xxlJobLogPath) ? xxlJobLogPath : path.resolve(repoRoot, xxlJobLogPath);
  ensureWritableDirectory(resolvedXxlJobLogPath, 'XXL-Job executor log directory');
}

function composeArgs(...extraArgs) {
  const profileArgs = observability ? ['--profile', 'observability'] : [];
  return ['compose', '--env-file', 'deploy/.env', '-f', composeFile, ...profileArgs, ...extraArgs];
}

async function probeHttp(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? 5_000);

  try {
    const response = await fetch(url, { signal: controller.signal });
    return {
      ok: response.ok,
      status: response.status,
      text: await response.text(),
    };
  } catch (err) {
    return {
      ok: false,
      status: 0,
      text: err instanceof Error ? err.message : String(err),
    };
  } finally {
    clearTimeout(timeout);
  }
}

async function waitForHttp(url, label, options = {}) {
  const timeoutMs = options.timeoutMs ?? 240_000;
  const intervalMs = options.intervalMs ?? 3_000;
  const startedAt = Date.now();
  let lastResult = null;

  while (Date.now() - startedAt <= timeoutMs) {
    // eslint-disable-next-line no-await-in-loop
    const result = await probeHttp(url, { timeoutMs: options.requestTimeoutMs ?? 5_000 });
    lastResult = result;

    const body = result.text.toLowerCase();
    const expected = options.includes?.toLowerCase();
    const expectedStatus = options.expectedStatus;
    const statusMatches = expectedStatus ? result.status === expectedStatus : result.ok;
    if (statusMatches && (!expected || body.includes(expected))) {
      log(`${label} is ready at ${url}`);
      return;
    }

    // eslint-disable-next-line no-await-in-loop
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }

  const status = lastResult?.status ? `status=${lastResult.status}` : 'no HTTP response';
  throw new Error(`${label} is not ready at ${url} (${status}).`);
}

async function checkDeployment() {
  const baseUrl = process.env.DEPLOY_CHECK_BASE_URL || 'http://127.0.0.1:8000';
  const gatewayUrl = process.env.DEPLOY_CHECK_GATEWAY_URL || 'http://127.0.0.1:8081';

  log('Running deployment health checks...');
  await waitForHttp(`${baseUrl}/health`, 'API proxy');
  await waitForHttp(`${baseUrl}/api/health`, 'system API through API proxy');
  await waitForHttp(`${baseUrl}/api/version`, 'gateway version API');
  await waitForHttp(`${baseUrl}/api/v1/system/version`, 'system-service version API');
  await waitForHttp(`${gatewayUrl}/actuator/health`, 'gateway actuator');
  await waitForHttp(`${baseUrl}/api/v1/public/login-capabilities`, 'public login capabilities API');
  await waitForHttp(`${baseUrl}/api/v1/localization/languages`, 'protected localization management API is routed', { expectedStatus: 401 });
  if (observability) {
    await checkObservability();
  }
  log('Deployment health checks passed.');
}

async function waitForPrometheusTargets() {
  const queryUrl = 'http://127.0.0.1:9090/api/v1/query?query=up%7Bjob%3D%22legendary-services%22%7D';
  const timeoutMs = 240_000;
  const intervalMs = 3_000;
  const startedAt = Date.now();
  let lastSummary = 'no Prometheus response';

  while (Date.now() - startedAt <= timeoutMs) {
    // eslint-disable-next-line no-await-in-loop
    const result = await probeHttp(queryUrl, { timeoutMs: 5_000 });
    if (result.ok) {
      try {
        const payload = JSON.parse(result.text);
        const series = payload.data?.result ?? [];
        const upSeries = series.filter((item) => item.value?.[1] === '1');
        lastSummary = `${upSeries.length}/${series.length} legendary targets are UP`;
        if (series.length >= 8 && upSeries.length >= 8) {
          log(`Prometheus targets are ready: ${lastSummary}`);
          return;
        }
      } catch (err) {
        lastSummary = err instanceof Error ? err.message : String(err);
      }
    } else {
      lastSummary = `status=${result.status}`;
    }

    // eslint-disable-next-line no-await-in-loop
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }

  throw new Error(`Prometheus did not report all legendary service targets as UP (${lastSummary}).`);
}

async function checkObservability() {
  log('Running observability health checks...');
  await waitForHttp('http://127.0.0.1:9090/-/ready', 'Prometheus');
  await waitForHttp('http://127.0.0.1:3001/api/health', 'Grafana');
  await waitForHttp('http://127.0.0.1:3100/ready', 'Loki');
  await waitForHttp('http://127.0.0.1:3200/ready', 'Tempo');
  await waitForHttp('http://127.0.0.1:12345/-/ready', 'Alloy');
  await waitForPrometheusTargets();
  log('Observability health checks passed.');
}

if (help) {
  printHelp();
  process.exit(0);
}

ensureEnvFile();
configureBuildIdentity();
ensureDockerReady();
ensureHostMountedDirectories();

if (reset) {
  run('docker', composeArgs('down', '-v', '--remove-orphans'));
  process.exit(0);
}

if (stop) {
  run('docker', composeArgs('down', '--remove-orphans'));
  process.exit(0);
}

if (ps) {
  run('docker', composeArgs('ps'));
  process.exit(0);
}

if (logs) {
  run('docker', composeArgs('logs', '-f', '--tail=200'));
  process.exit(0);
}

run('docker', composeArgs('config'), { stdio: 'ignore' });

const upArgs = ['up', '-d'];
if (rebuild) {
  upArgs.push('--build');
}

runWithRetry('docker', composeArgs(...upArgs), 1);
run('docker', composeArgs('ps'));

if (!skipCheck) {
  await checkDeployment();
}

log('Complete deployment started.');
log('API proxy: http://127.0.0.1:8000');
log('Gateway health: http://127.0.0.1:8081/actuator/health');
