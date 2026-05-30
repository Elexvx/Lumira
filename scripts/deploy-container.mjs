#!/usr/bin/env node

import { randomBytes } from 'node:crypto';
import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

import { parseEnvFile, randomSecret, randomBase64Secret } from './lib/env-utils.mjs';
import { run as execRun, output as execOutput, optionalOutput as execOptionalOutput, createLogger, resolveRepoRoot } from './lib/exec-utils.mjs';
import { waitForHttp, probeHttp } from './lib/http-utils.mjs';
const log = createLogger('deploy');
const repoRoot = resolveRepoRoot(import.meta.url);
const envExamplePath = path.join(repoRoot, 'deploy', '.env.example');
const envPath = path.join(repoRoot, 'deploy', '.env');
const buildIdentityPath = process.env.BUILD_IDENTITY_FILE || path.join(repoRoot, 'deploy', 'build-identity.env');
const composeFile = path.join(repoRoot, 'deploy', 'docker-compose.prod.yml');
const alertRulesPath = path.join(repoRoot, 'deploy', 'observability', 'grafana', 'provisioning', 'alerting', 'rules.yml');
const generatedAlertingDir = path.join(repoRoot, 'deploy', '.generated', 'grafana-alerting');

const rawArgs = process.argv.slice(2);
const args = new Set(rawArgs);
const rebuild = args.has('--rebuild');
const stop = args.has('--stop');
const logs = args.has('--logs');
const ps = args.has('--ps');
const reset = args.has('--reset');
const help = args.has('--help') || args.has('-h');
const skipCheck = args.has('--skip-check');
const skipReadiness = args.has('--skip-readiness');
const observability = args.has('--observability');
const skipDockerPrune = args.has('--skip-docker-prune');
const serviceNames = parseServiceNames(rawArgs);
const allowedServices = new Set([
  'system-service',
  'mysql',
  'redis',
  'nacos',
  'xxl-job-admin',
  'gateway-service',
  'auth-service',
  'file-service',
  'message-service',
  'plugin-service',
  'localization-service',
  'job-executor',
  'api-proxy',
  'frontend',
  'prometheus',
  'loki',
  'tempo',
  'alloy',
  'grafana',
]);


function parseServiceNames(argv) {
  const values = [];
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--services' || arg === '--service') {
      const nextValue = argv[index + 1];
      if (!nextValue || nextValue.startsWith('--')) {
        console.error(`${arg} requires a comma-separated service list.`);
        process.exit(1);
      }
      values.push(nextValue);
      index += 1;
      continue;
    }
    if (arg.startsWith('--services=')) {
      values.push(arg.slice('--services='.length));
      continue;
    }
    if (arg.startsWith('--service=')) {
      values.push(arg.slice('--service='.length));
    }
  }

  return values
    .flatMap((value) => value.split(','))
    .map((value) => value.trim())
    .filter(Boolean);
}

function validateServiceNames() {
  const invalidServices = serviceNames.filter((serviceName) => !allowedServices.has(serviceName));
  if (invalidServices.length > 0) {
    console.error(`Unknown service(s): ${invalidServices.join(', ')}`);
    console.error(`Allowed services: ${Array.from(allowedServices).sort().join(', ')}`);
    process.exit(1);
  }
}

function run(command, commandArgs, options = {}) {
  try {
    return execRun(command, commandArgs, { cwd: repoRoot, ...options });
  } catch (err) {
    process.exit(err.status ?? 1);
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
  return execRun(command, commandArgs, { cwd: repoRoot, check: false, encoding: "utf8", stdio: "pipe" });
}

function optionalOutput(command, commandArgs) {
  return execOptionalOutput(command, commandArgs, { cwd: repoRoot });
}

function configureBuildIdentity() {
  const env = parseEnvFile(envPath);
  const buildIdentity = parseEnvFile(buildIdentityPath);
  const appVersion = process.env.APP_VERSION || env.APP_VERSION || '0.1.0';
  const gitCommit = process.env.GIT_COMMIT || buildIdentity.GIT_COMMIT || env.GIT_COMMIT || optionalOutput('git', ['rev-parse', '--short=12', 'HEAD']);
  const gitBranch = process.env.GIT_BRANCH || buildIdentity.GIT_BRANCH || env.GIT_BRANCH || optionalOutput('git', ['rev-parse', '--abbrev-ref', 'HEAD']);
  const buildTime = process.env.BUILD_TIME || buildIdentity.BUILD_TIME || env.BUILD_TIME || new Date().toISOString();
  const buildVersion = process.env.BUILD_VERSION || buildIdentity.BUILD_VERSION || env.BUILD_VERSION || (gitCommit ? `${appVersion}+${gitCommit}` : appVersion);

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
  --services  Deploy only selected services, comma-separated. Example: --services system-service
  --stop      Stop the deployment.
  --reset     Stop and remove volumes. This deletes database and uploaded data.
  --logs      Follow service logs.
  --ps        Show container status.
  --skip-check Skip deployment health checks after startup.
  --skip-readiness Skip selected-service readiness waits.
  --skip-docker-prune Skip automatic Docker build cache cleanup before rebuilds.
  --observability Start Prometheus, Grafana, Loki, Tempo, and Alloy.
  --nacos     Start the bundled Nacos container. This is also enabled when Nacos config or discovery is enabled in deploy/.env.
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

function availableDiskMb(mountPath = '/') {
  const result = output('df', ['-Pm', mountPath]);
  if (result.status !== 0) {
    return null;
  }
  const lines = result.stdout.trim().split(/\r?\n/);
  const columns = lines.at(-1)?.trim().split(/\s+/) ?? [];
  const available = Number.parseInt(columns[3] ?? '', 10);
  return Number.isFinite(available) ? available : null;
}

function maybePruneDockerBuildCache(stage) {
  if (!rebuild || skipDockerPrune) {
    return;
  }
  const minimumFreeMb = Number.parseInt(process.env.DEPLOY_MIN_FREE_MB || '10240', 10);
  const pruneMode = (process.env.DEPLOY_DOCKER_PRUNE_MODE || 'auto').toLowerCase();
  if (pruneMode === 'off') {
    return;
  }
  const freeBefore = availableDiskMb('/');
  const shouldPrune = pruneMode === 'always' || (freeBefore !== null && freeBefore < minimumFreeMb);
  if (!shouldPrune) {
    return;
  }
  log(`Docker cleanup (${stage}) started: free=${freeBefore ?? 'unknown'}MB, threshold=${minimumFreeMb}MB.`);
  run('docker', ['builder', 'prune', '-af']);
  run('docker', ['image', 'prune', '-f']);
  const freeAfter = availableDiskMb('/');
  log(`Docker cleanup (${stage}) finished: free=${freeAfter ?? 'unknown'}MB.`);
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
    PROMETHEUS_IMAGE: 'prom/prometheus@sha256:e4254400b85610324913f0dc4acf92603d9984e7519414c5a12811aa6146acc3',
    GRAFANA_IMAGE: 'grafana/grafana@sha256:2d1f9ae67c1778d33e291d4c3c759cd8b650e67491f02533499eb950e075eeb5',
    LOKI_IMAGE: 'grafana/loki@sha256:191d4fdfb7264f16989f0a57f320872620a5a7c2ceeec6229212c4190ec49b86',
    TEMPO_IMAGE: 'grafana/tempo@sha256:2513658c41faa9197dc7373599bb6119eb27bcb4232cc83779e2bc87cbc34299',
    ALLOY_IMAGE: 'grafana/alloy@sha256:51aeb9d829239345070619dad3edd6873186f913c84f45b365b74574fcb38ec0',
    GRAFANA_ADMIN_USER: 'admin',
    GRAFANA_ADMIN_PASSWORD: randomSecret('grafana'),
    GRAFANA_ALERT_EMAIL_ENABLED: 'false',
    GRAFANA_ALERT_EMAIL_TO: '',
    GRAFANA_SMTP_HOST: '',
    GRAFANA_SMTP_USER: '',
    GRAFANA_SMTP_PASSWORD: '',
    GRAFANA_SMTP_FROM_ADDRESS: 'alerts@legendary-invention.local',
    GRAFANA_SMTP_FROM_NAME: 'Legendary Observability',
    GRAFANA_ALERT_WEBHOOK_ENABLED: 'false',
    GRAFANA_ALERT_WEBHOOK_URL: '',
    CORS_ALLOWED_ORIGIN_PATTERNS: 'http://localhost:*,http://127.0.0.1:*',
    REDIS_MAXMEMORY: '256mb',
    REDIS_MEM_LIMIT: '384m',
    JAVA_OPTS: '-XX:MaxRAMPercentage=58 -XX:InitialRAMPercentage=18 -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=96m -Xss512k -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.security.egd=file:/dev/./urandom',
    SERVER_TOMCAT_THREADS_MAX: '80',
    SERVER_TOMCAT_THREADS_MIN_SPARE: '8',
    SERVER_TOMCAT_ACCEPT_COUNT: '120',
    SERVER_TOMCAT_MAX_CONNECTIONS: '4096',
    SPRING_THREADS_VIRTUAL_ENABLED: 'true',
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: '4',
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: '1',
    DOCKER_LOG_MAX_SIZE: '50m',
    DOCKER_LOG_MAX_FILE: '2',
    SYSTEM_SERVICE_MEM_LIMIT: '768m',
    GATEWAY_SERVICE_MEM_LIMIT: '512m',
    AUTH_SERVICE_MEM_LIMIT: '384m',
    FILE_SERVICE_MEM_LIMIT: '384m',
    MESSAGE_SERVICE_MEM_LIMIT: '384m',
    PLUGIN_SERVICE_MEM_LIMIT: '384m',
    LOCALIZATION_SERVICE_MEM_LIMIT: '320m',
    JOB_EXECUTOR_MEM_LIMIT: '320m',
    XXL_JOB_ADMIN_MEM_LIMIT: '384m',
    API_PROXY_MEM_LIMIT: '128m',
    SAAS_TRAFFIC_GATEWAY_AUTH_SERVICE_QPS: '120',
    SAAS_TRAFFIC_GATEWAY_FILE_SERVICE_QPS: '80',
    SAAS_TRAFFIC_GATEWAY_MESSAGE_SERVICE_QPS: '80',
    SAAS_TRAFFIC_GATEWAY_PLUGIN_SERVICE_QPS: '50',
    SAAS_TRAFFIC_GATEWAY_LOCALIZATION_SERVICE_QPS: '80',
    SAAS_TRAFFIC_GATEWAY_SYSTEM_SERVICE_QPS: '160',
    SAAS_TRAFFIC_AUTH_LOGIN_QPS: '20',
    SAAS_TRAFFIC_AUTH_REFRESH_TOKEN_QPS: '80',
    SAAS_TRAFFIC_AUTH_CURRENT_USER_QPS: '160',
  };
}

function protectEnvFile() {
  chmodSync(envPath, 0o600);
}

function writeProtectedEnvFile(content) {
  writeFileSync(envPath, content, { mode: 0o600 });
  protectEnvFile();
}

function ensureEnvFile() {
  const generatedValues = generatedEnvDefaults();
  const legacyGeneratedValues = new Map([
    ['JAVA_OPTS', '-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom'],
  ]);

  if (existsSync(envPath)) {
    protectEnvFile();
    let content = readFileSync(envPath, 'utf8');
    const missingEntries = Object.entries(generatedValues)
      .filter(([key]) => !new RegExp(`^${key}=`, 'm').test(content));

    if (missingEntries.length > 0) {
      content = `${content.trimEnd()}\n${missingEntries.map(([key, value]) => `${key}=${value}`).join('\n')}\n`;
      writeProtectedEnvFile(content);
      log(`Backfilled deploy/.env keys: ${missingEntries.map(([key]) => key).join(', ')}`);
    }

    let migratedKeys = [];
    content = content.replace(/^([A-Z0-9_]+)=(.*)$/gm, (line, key, value) => {
      if (legacyGeneratedValues.get(key) === value && generatedValues[key]) {
        migratedKeys.push(key);
        return `${key}=${generatedValues[key]}`;
      }
      return line;
    });
    if (migratedKeys.length > 0) {
      writeProtectedEnvFile(content);
      log(`Migrated deploy/.env generated defaults: ${migratedKeys.join(', ')}`);
    }
    return;
  }

  let content = readFileSync(envExamplePath, 'utf8');
  for (const [key, value] of Object.entries(generatedValues)) {
    content = content.replace(new RegExp(`^${key}=.*$`, 'm'), `${key}=${value}`);
  }

  writeProtectedEnvFile(content);
  log('Generated deploy/.env with random local deployment secrets.');
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

function mergedEnv() {
  return {
    ...parseEnvFile(envPath),
    ...Object.fromEntries(Object.entries(process.env).filter(([, value]) => value !== undefined)),
  };
}

function isEnabled(value) {
  return String(value ?? '').trim().toLowerCase() === 'true';
}

function yamlQuote(value) {
  return `'${String(value ?? '').replaceAll("'", "''")}'`;
}

function renderReceiver(receiver) {
  const settings = Object.entries(receiver.settings)
    .map(([key, value]) => `              ${key}: ${yamlQuote(value)}`)
    .join('\n');
  return `          - uid: ${receiver.uid}
            type: ${receiver.type}
            settings:
${settings}
            disableResolveMessage: false`;
}

function ensureObservabilityProvisioning() {
  if (!observability) {
    return;
  }

  if (!existsSync(alertRulesPath)) {
    throw new Error(`Missing Grafana alert rules provisioning file: ${alertRulesPath}`);
  }

  const env = mergedEnv();
  const receivers = [];
  if (isEnabled(env.GRAFANA_ALERT_EMAIL_ENABLED) && env.GRAFANA_ALERT_EMAIL_TO) {
    receivers.push({
      uid: 'legendary-email',
      type: 'email',
      settings: {
        addresses: env.GRAFANA_ALERT_EMAIL_TO,
      },
    });
  }
  if (isEnabled(env.GRAFANA_ALERT_WEBHOOK_ENABLED) && env.GRAFANA_ALERT_WEBHOOK_URL) {
    receivers.push({
      uid: 'legendary-webhook',
      type: 'webhook',
      settings: {
        url: env.GRAFANA_ALERT_WEBHOOK_URL,
      },
    });
  }
  if (receivers.length === 0) {
    receivers.push({
      uid: 'legendary-noop',
      type: 'webhook',
      settings: {
        url: 'http://127.0.0.1:9/legendary-alerts-disabled',
      },
    });
  }

  mkdirSync(generatedAlertingDir, { recursive: true });
  writeFileSync(path.join(generatedAlertingDir, 'rules.yml'), readFileSync(alertRulesPath, 'utf8'));
  writeFileSync(
    path.join(generatedAlertingDir, 'contact-points.yml'),
    `apiVersion: 1

contactPoints:
  - orgId: 1
    name: legendary-alerts
    receivers:
${receivers.map(renderReceiver).join('\n')}
`
  );
  writeFileSync(
    path.join(generatedAlertingDir, 'notification-policies.yml'),
    `apiVersion: 1

policies:
  - orgId: 1
    receiver: legendary-alerts
    group_by:
      - grafana_folder
      - alertname
    group_wait: 30s
    group_interval: 5m
    repeat_interval: 4h
`
  );
  log(`Grafana alert provisioning is ready with ${receivers.map((receiver) => receiver.uid).join(', ')}.`);
}

function composeArgs(...extraArgs) {
  const env = parseEnvFile(envPath);
  const useNacos = args.has('--nacos') || isEnabled(env.NACOS_CONFIG_ENABLED) || isEnabled(env.NACOS_DISCOVERY_ENABLED);
  const profileArgs = [
    ...(observability ? ['--profile', 'observability'] : []),
    ...(useNacos ? ['--profile', 'nacos'] : []),
  ];
  return ['compose', '--env-file', 'deploy/.env', '-f', composeFile, ...profileArgs, ...extraArgs];
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

async function checkSelectedServiceReadiness() {
  if (skipReadiness || serviceNames.length === 0) {
    return;
  }

  const baseUrl = process.env.DEPLOY_CHECK_BASE_URL || 'http://127.0.0.1:8000';
  const gatewayUrl = process.env.DEPLOY_CHECK_GATEWAY_URL || 'http://127.0.0.1:8081';
  const checks = {
    'system-service': [
      [`${baseUrl}/api/v1/public/security-settings`, 'system-service public settings API'],
      [`${baseUrl}/api/health`, 'system-service health API'],
    ],
    'auth-service': [
      [`${baseUrl}/api/v1/public/login-capabilities`, 'auth-service login capabilities API'],
    ],
    'gateway-service': [
      [`${gatewayUrl}/actuator/health`, 'gateway actuator'],
    ],
    'localization-service': [
      [`${baseUrl}/api/v1/localization/languages`, 'localization-service protected route', { expectedStatus: 401 }],
    ],
  };

  const selectedChecks = serviceNames.flatMap((serviceName) => checks[serviceName] ?? []);
  if (selectedChecks.length === 0) {
    return;
  }

  log('Waiting for selected service readiness...');
  for (const [url, label, options] of selectedChecks) {
    // eslint-disable-next-line no-await-in-loop
    await waitForHttp(url, label, options ?? {});
  }
  log('Selected service readiness checks passed.');
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

async function checkGrafanaAlertingProvisioning() {
  const env = mergedEnv();
  const user = env.GRAFANA_ADMIN_USER || 'admin';
  const password = env.GRAFANA_ADMIN_PASSWORD || 'change-me-grafana-admin-password';
  const headers = {
    Authorization: `Basic ${Buffer.from(`${user}:${password}`).toString('base64')}`,
  };
  const checks = [
    {
      url: 'http://127.0.0.1:3001/api/v1/provisioning/alert-rules',
      label: 'Grafana alert rules provisioning API',
      includes: 'legendary-service-down',
    },
    {
      url: 'http://127.0.0.1:3001/api/v1/provisioning/contact-points',
      label: 'Grafana contact points provisioning API',
      includes: 'legendary-alerts',
    },
    {
      url: 'http://127.0.0.1:3001/api/v1/provisioning/policies',
      label: 'Grafana notification policies provisioning API',
      includes: 'legendary-alerts',
    },
  ];

  for (const check of checks) {
    // eslint-disable-next-line no-await-in-loop
    const result = await probeHttp(check.url, { headers, timeoutMs: 5_000 });
    if (!result.ok || !result.text.includes(check.includes)) {
      const status = result.status ? `status=${result.status}` : 'no HTTP response';
      throw new Error(`${check.label} did not include ${check.includes} (${status}).`);
    }
    log(`${check.label} is ready.`);
  }
}

async function checkObservability() {
  log('Running observability health checks...');
  await waitForHttp('http://127.0.0.1:9090/-/ready', 'Prometheus');
  await waitForHttp('http://127.0.0.1:3001/api/health', 'Grafana');
  await waitForHttp('http://127.0.0.1:3100/ready', 'Loki');
  await waitForHttp('http://127.0.0.1:3200/ready', 'Tempo');
  await waitForHttp('http://127.0.0.1:12345/-/ready', 'Alloy');
  await waitForPrometheusTargets();
  await checkGrafanaAlertingProvisioning();
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
ensureObservabilityProvisioning();

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
validateServiceNames();
maybePruneDockerBuildCache('before-build');

if (serviceNames.length > 0) {
  log(`Deploying selected service(s) without dependency restart: ${serviceNames.join(', ')}`);
  if (rebuild) {
    runWithRetry('docker', composeArgs('build', ...serviceNames), 1);
  }
  runWithRetry('docker', composeArgs('up', '-d', '--no-deps', ...serviceNames), 1);
  await checkSelectedServiceReadiness();
} else {
  const upArgs = ['up', '-d'];
  if (rebuild) {
    upArgs.push('--build');
  }
  runWithRetry('docker', composeArgs(...upArgs), 1);
}
maybePruneDockerBuildCache('after-build');
run('docker', composeArgs('ps'));

if (!skipCheck) {
  await checkDeployment();
}

log('Complete deployment started.');
log('API proxy: http://127.0.0.1:8000');
log('Gateway health: http://127.0.0.1:8081/actuator/health');
