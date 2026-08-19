#!/usr/bin/env node

import { spawn, spawnSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import {
  existsSync,
  mkdirSync,
  readFileSync,
  watch,
  writeFileSync,
} from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { parseEnvFile } from './lib/env-utils.mjs';
import {
  ensureLocalAdminCredential,
  formatLocalAdminNotice,
  parseJdbcEndpoint,
} from './lib/local-admin-bootstrap.mjs';
import { createChangeBatcher } from './lib/native-backend-watch.mjs';
import {
  createLocalReadinessTargets,
  waitForLocalReadiness,
} from './lib/local-readiness.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const backendRoot = path.join(repoRoot, 'lumira-backend');
const frontendRoot = path.join(repoRoot, 'lumira-ui');
const bootstrapAdminRoot = path.join(repoRoot, 'deploy', 'bootstrap-admin');
const bootstrapAdminJar = path.join(bootstrapAdminRoot, 'target', 'lumira-bootstrap-admin.jar');
const runtimeSecretsRoot = path.join(repoRoot, 'runtime-secrets');
const localFieldSecretPath = path.join(runtimeSecretsRoot, 'local-field-secret');
const defaultEnvPath = path.join(backendRoot, '.env');
const rawArgs = process.argv.slice(2);

function printHelp() {
  console.log(`Usage: node bin/start-local.mjs [options]

Runs Lumira directly on the host. Docker is never invoked by this command.
MySQL and Redis-compatible services must already be available.

Options:
  --check                    Run native dependency and port checks, then exit.
  --full, --workers          Also run lumira-async and lumira-job-executor.
  --backend-only             Start only the native Java runtime(s).
  --frontend-only            Start only Umi and require an existing backend.
  --skip-build, --no-build   Skip the Maven reactor install step.
  --watch                    Watch backend sources (enabled by default).
  --no-watch                 Disable backend automatic compile/restart.
  --backend-port <port>      Main backend port (default: 8080).
  --frontend-port <port>     Umi development port (default: 8000).
  --async-port <port>        Async worker port in --full mode (default: 8081).
  --job-port <port>          Job worker port in --full mode (default: 8082).
  --ready-timeout <seconds>  Business-readiness timeout (default: 180).
  --env-file <path>          Local backend env file (default: lumira-backend/.env).
  --allow-remote-services    Allow a non-loopback MySQL or Redis endpoint.
  -h, --help                 Show this help message.

Local configuration template: lumira-backend/.env.example
`);
}

function optionValue(name) {
  const inline = rawArgs.find((arg) => arg.startsWith(`${name}=`));
  if (inline) {
    return inline.slice(name.length + 1);
  }
  const index = rawArgs.indexOf(name);
  return index >= 0 ? rawArgs[index + 1] : undefined;
}

function parsePort(value, fallback, label) {
  const port = Number(value ?? fallback);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`${label} must be an integer between 1 and 65535.`);
  }
  return port;
}

function parsePositiveSeconds(value, fallback, label) {
  const seconds = Number(value ?? fallback);
  if (!Number.isFinite(seconds) || seconds <= 0) {
    throw new Error(`${label} must be a positive number of seconds.`);
  }
  return Math.round(seconds * 1_000);
}

function localRuntimeSecret(...configuredValues) {
  const configured = configuredValues.find(
    (value) => typeof value === 'string' && value.trim().length > 0,
  );
  return configured ?? randomBytes(32).toString('base64url');
}

function persistentLocalRuntimeSecret(secretPath, ...configuredValues) {
  const configured = configuredValues.find(
    (value) => typeof value === 'string' && value.trim().length > 0,
  );
  if (configured) {
    return configured;
  }

  try {
    const persisted = readFileSync(secretPath, 'utf8').trim();
    if (persisted) {
      return persisted;
    }
  } catch (error) {
    if (error?.code !== 'ENOENT') {
      throw new Error(`Unable to read the local field-encryption secret: ${error.message}`);
    }
  }

  const generated = randomBytes(32).toString('base64url');
  mkdirSync(path.dirname(secretPath), { recursive: true });
  try {
    writeFileSync(secretPath, `${generated}\n`, { encoding: 'utf8', flag: 'wx', mode: 0o600 });
    return generated;
  } catch (error) {
    if (error?.code !== 'EEXIST') {
      throw new Error(`Unable to persist the local field-encryption secret: ${error.message}`);
    }
    const persisted = readFileSync(secretPath, 'utf8').trim();
    if (!persisted) {
      throw new Error('The local field-encryption secret file is empty. Remove it and restart local mode.');
    }
    return persisted;
  }
}

function isLoopback(host) {
  return ['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1'].includes(String(host).toLowerCase());
}

function portableCommand(command, args) {
  if (process.platform === 'win32' && /\.cmd$/i.test(command)) {
    return { command: 'cmd.exe', args: ['/d', '/c', command, ...args] };
  }
  return { command, args };
}

function runSync(command, args, options = {}) {
  const resolved = portableCommand(command, args);
  return spawnSync(resolved.command, resolved.args, {
    cwd: repoRoot,
    stdio: 'inherit',
    ...options,
  });
}

function commandOutput(command, args) {
  const resolved = portableCommand(command, args);
  const result = spawnSync(resolved.command, resolved.args, {
    cwd: repoRoot,
    encoding: 'utf8',
    stdio: 'pipe',
  });
  return {
    status: result.status,
    output: `${result.stdout || ''}\n${result.stderr || ''}`.trim(),
  };
}

function isPortOpen(host, port, timeoutMs = 1_500) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host, port });
    const finish = (open) => {
      socket.removeAllListeners();
      socket.destroy();
      resolve(open);
    };
    socket.setTimeout(timeoutMs);
    socket.once('connect', () => finish(true));
    socket.once('timeout', () => finish(false));
    socket.once('error', () => finish(false));
  });
}

function assertToolchain({ startBackend, startFrontend }) {
  const nodeMajor = Number(process.versions.node.split('.')[0]);
  if (nodeMajor < 22) {
    throw new Error(`Node.js 22 or newer is required (current: ${process.version}).`);
  }

  if (startBackend) {
    const java = commandOutput('java', ['-version']);
    const javaMajor = Number(java.output.match(/version "(\d+)/)?.[1]);
    if (java.status !== 0 || !Number.isFinite(javaMajor) || javaMajor < 21) {
      throw new Error('JDK 21 or newer is required for the native backend.');
    }
    if (!existsSync(path.join(backendRoot, process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw'))) {
      throw new Error('The Maven wrapper is missing from lumira-backend.');
    }
  }

  if (startFrontend) {
    const corepack = commandOutput(process.platform === 'win32' ? 'corepack.cmd' : 'corepack', ['pnpm', '--version']);
    if (corepack.status !== 0) {
      throw new Error('Corepack/pnpm is required for the native frontend.');
    }
    if (!existsSync(path.join(frontendRoot, 'node_modules', '@umijs', 'max', 'package.json'))) {
      throw new Error('Frontend dependencies are missing. Run "corepack pnpm --dir lumira-ui install" first.');
    }
  }
}

async function assertPortFree(port, label) {
  if (await isPortOpen('127.0.0.1', port)) {
    throw new Error(`${label} port ${port} is already in use. Stop the other environment or choose another port.`);
  }
}

function startProcess(label, command, args, options) {
  const resolved = portableCommand(command, args);
  console.log(`[local] Starting ${label}...`);
  const child = spawn(resolved.command, resolved.args, {
    stdio: 'inherit',
    ...options,
  });
  child.lumiraLabel = label;
  return child;
}

function terminateProcessTree(child) {
  if (!child?.pid || child.exitCode !== null) {
    return;
  }
  if (process.platform === 'win32') {
    spawnSync('taskkill.exe', ['/pid', String(child.pid), '/t', '/f'], { stdio: 'ignore' });
    return;
  }
  child.kill('SIGTERM');
}

if (rawArgs.includes('--help') || rawArgs.includes('-h')) {
  printHelp();
  process.exit(0);
}

const productionOnlyOptions = [
  '--pull',
  '--rebuild',
  '--services',
  '--service',
  '--stop',
  '--logs',
  '--ps',
  '--reset',
  '--observability',
  '--local-mysql',
  '--skip-migrations',
  '--skip-docker-prune',
];
const invalidProductionOption = rawArgs.find((arg) => productionOnlyOptions.some((option) => arg === option || arg.startsWith(`${option}=`)));
if (invalidProductionOption) {
  console.error(`[local] ${invalidProductionOption} is only valid in the production container mode.`);
  process.exit(1);
}

const full = rawArgs.includes('--full') || rawArgs.includes('--workers');
const backendOnly = rawArgs.includes('--backend-only');
const frontendOnly = rawArgs.includes('--frontend-only');
const checkOnly = rawArgs.includes('--check');
const skipBuild = rawArgs.includes('--skip-build') || rawArgs.includes('--no-build');
const allowRemoteServices = rawArgs.includes('--allow-remote-services');
const explicitWatch = rawArgs.includes('--watch');
const watchBackend = !rawArgs.includes('--no-watch');

if (backendOnly && frontendOnly) {
  console.error('[local] --backend-only and --frontend-only cannot be used together.');
  process.exit(1);
}
if (frontendOnly && full) {
  console.error('[local] --full requires the native backend and cannot be combined with --frontend-only.');
  process.exit(1);
}
if (frontendOnly && explicitWatch) {
  console.error('[local] --watch requires the native backend and cannot be combined with --frontend-only.');
  process.exit(1);
}
if (explicitWatch && rawArgs.includes('--no-watch')) {
  console.error('[local] --watch and --no-watch cannot be used together.');
  process.exit(1);
}

const startBackend = !frontendOnly;
const startFrontend = !backendOnly;
const envPathValue = optionValue('--env-file');
const localEnvPath = envPathValue
  ? path.resolve(repoRoot, envPathValue)
  : defaultEnvPath;
const fileEnv = existsSync(localEnvPath) ? parseEnvFile(localEnvPath) : {};
const configuredDbPassword = process.env.LUMIRA_LOCAL_DB_PASSWORD ?? fileEnv.DB_PASSWORD;
const configuredBootstrapSecretValue =
  process.env.LUMIRA_LOCAL_BOOTSTRAP_ADMIN_PASSWORD_FILE ||
  fileEnv.LUMIRA_LOCAL_BOOTSTRAP_ADMIN_PASSWORD_FILE;
const configuredBootstrapSecretPath = configuredBootstrapSecretValue
  ? path.resolve(repoRoot, configuredBootstrapSecretValue)
  : undefined;

let backendPort;
let frontendPort;
let asyncPort;
let jobPort;
let readinessTimeoutMs;
try {
  backendPort = parsePort(optionValue('--backend-port') || process.env.LUMIRA_LOCAL_BACKEND_PORT || fileEnv.SERVER_PORT, 8080, 'Backend port');
  frontendPort = parsePort(optionValue('--frontend-port') || process.env.LUMIRA_LOCAL_FRONTEND_PORT || fileEnv.LUMIRA_UI_PORT, 8000, 'Frontend port');
  asyncPort = parsePort(optionValue('--async-port') || process.env.LUMIRA_LOCAL_ASYNC_PORT || fileEnv.LUMIRA_ASYNC_PORT, 8081, 'Async port');
  jobPort = parsePort(optionValue('--job-port') || process.env.LUMIRA_LOCAL_JOB_PORT || fileEnv.LUMIRA_JOB_PORT, 8082, 'Job port');
  readinessTimeoutMs = parsePositiveSeconds(
    optionValue('--ready-timeout')
      || process.env.LUMIRA_LOCAL_READINESS_TIMEOUT_SECONDS
      || fileEnv.LUMIRA_LOCAL_READINESS_TIMEOUT_SECONDS,
    180,
    'Readiness timeout',
  );
} catch (error) {
  console.error(`[local] ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}

const localProfile = process.env.LUMIRA_LOCAL_SPRING_PROFILES || fileEnv.SPRING_PROFILES_ACTIVE || 'dev';
if (localProfile.split(',').map((item) => item.trim().toLowerCase()).includes('prod')) {
  console.error('[local] The prod Spring profile is forbidden in native local mode.');
  process.exit(1);
}

const backendUrl = `http://127.0.0.1:${backendPort}`;
const asyncUrl = `http://127.0.0.1:${asyncPort}`;
const readinessTargets = createLocalReadinessTargets({
  backendPort,
  frontendPort,
  asyncPort,
  jobPort,
  includeFrontend: startFrontend,
  includeWorkers: full,
});
const localEnv = {
  ...process.env,
  ...fileEnv,
  SPRING_PROFILES_ACTIVE: localProfile,
  SERVER_ADDRESS: '127.0.0.1',
  DB_URL: process.env.LUMIRA_LOCAL_DB_URL || fileEnv.DB_URL || 'jdbc:mysql://127.0.0.1:3306/lumira?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai',
  DB_USERNAME: process.env.LUMIRA_LOCAL_DB_USERNAME || fileEnv.DB_USERNAME || 'root',
  DB_PASSWORD: configuredDbPassword ?? '',
  REDIS_HOST: process.env.LUMIRA_LOCAL_REDIS_HOST || fileEnv.REDIS_HOST || '127.0.0.1',
  REDIS_PORT: process.env.LUMIRA_LOCAL_REDIS_PORT || fileEnv.REDIS_PORT || '6379',
  REDIS_PASSWORD: process.env.LUMIRA_LOCAL_REDIS_PASSWORD ?? fileEnv.REDIS_PASSWORD ?? '',
  JWT_SECRET: localRuntimeSecret(process.env.JWT_SECRET, fileEnv.JWT_SECRET),
  FIELD_SECRET: persistentLocalRuntimeSecret(
    localFieldSecretPath,
    process.env.FIELD_SECRET,
    fileEnv.FIELD_SECRET,
  ),
  PLUGIN_SIGNATURE_SECRET: localRuntimeSecret(process.env.PLUGIN_SIGNATURE_SECRET, fileEnv.PLUGIN_SIGNATURE_SECRET),
  SPRING_SECURITY_USER_PASSWORD: localRuntimeSecret(process.env.SPRING_SECURITY_USER_PASSWORD, fileEnv.SPRING_SECURITY_USER_PASSWORD),
  PLATFORM_UPDATE_CHECK_INITIAL_DELAY_MS: fileEnv.PLATFORM_UPDATE_CHECK_INITIAL_DELAY_MS || '86400000',
  PLATFORM_UPDATE_TASK_RECONCILE_INITIAL_DELAY_MS: fileEnv.PLATFORM_UPDATE_TASK_RECONCILE_INITIAL_DELAY_MS || '86400000',
  SAAS_INTERNAL_SYSTEM_TOKEN: localRuntimeSecret(process.env.SAAS_INTERNAL_SYSTEM_TOKEN, fileEnv.SAAS_INTERNAL_SYSTEM_TOKEN),
  SAAS_INTERNAL_AUTH_TOKEN: localRuntimeSecret(process.env.SAAS_INTERNAL_AUTH_TOKEN, fileEnv.SAAS_INTERNAL_AUTH_TOKEN),
  SAAS_INTERNAL_AUTH_SYSTEM_TOKEN: localRuntimeSecret(process.env.SAAS_INTERNAL_AUTH_SYSTEM_TOKEN, fileEnv.SAAS_INTERNAL_AUTH_SYSTEM_TOKEN),
  SAAS_INTERNAL_FILE_TOKEN: localRuntimeSecret(process.env.SAAS_INTERNAL_FILE_TOKEN, fileEnv.SAAS_INTERNAL_FILE_TOKEN),
  SAAS_INTERNAL_MESSAGE_TOKEN: localRuntimeSecret(process.env.SAAS_INTERNAL_MESSAGE_TOKEN, fileEnv.SAAS_INTERNAL_MESSAGE_TOKEN),
  SAAS_INTERNAL_PAYMENT_TOKEN: localRuntimeSecret(process.env.SAAS_INTERNAL_PAYMENT_TOKEN, fileEnv.SAAS_INTERNAL_PAYMENT_TOKEN),
  SAAS_INTERNAL_PLUGIN_TOKEN: localRuntimeSecret(process.env.SAAS_INTERNAL_PLUGIN_TOKEN, fileEnv.SAAS_INTERNAL_PLUGIN_TOKEN),
  SAAS_INTERNAL_TEAM_TOKEN: localRuntimeSecret(process.env.SAAS_INTERNAL_TEAM_TOKEN, fileEnv.SAAS_INTERNAL_TEAM_TOKEN),
  SAAS_INTERNAL_JOB_TOKEN: localRuntimeSecret(process.env.SAAS_INTERNAL_JOB_TOKEN, fileEnv.SAAS_INTERNAL_JOB_TOKEN),
  UMI_APP_API_BASE_URL: '',
  UMI_APP_API_PREFIX: '/api',
  UMI_APP_LOCAL_NATIVE_MODE: 'true',
  UMI_DEV_API_TARGET: backendUrl,
  UMI_DEV_WS_TARGET: `ws://127.0.0.1:${backendPort}`,
  LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL: backendUrl,
  SAAS_JOB_CONTROL_PLANE_BASE_URL: backendUrl,
  SAAS_JOB_ASYNC_RUNTIME_BASE_URL: asyncUrl,
  SAAS_JOB_BACKEND_BASE_URL: asyncUrl,
  SAAS_JOB_SYSTEM_SERVICE_BASE_URL: backendUrl,
  SAAS_JOB_MESSAGE_SERVICE_BASE_URL: backendUrl,
  SAAS_JOB_FILE_SERVICE_BASE_URL: backendUrl,
  SAAS_JOB_PAYMENT_SERVICE_BASE_URL: backendUrl,
  SAAS_JOB_PLUGIN_SERVICE_BASE_URL: backendUrl,
  SAAS_JOB_ADAPTIVE_RELAY_MESSAGE_ENABLED: 'false',
  SAAS_JOB_ADAPTIVE_RELAY_FILE_ENABLED: 'false',
  SAAS_JOB_ADAPTIVE_RELAY_PAYMENT_ENABLED: 'false',
  SAAS_JOB_ADAPTIVE_RELAY_PLUGIN_ENABLED: 'false',
};
delete localEnv.LUMIRA_LOCAL_BOOTSTRAP_ADMIN_PASSWORD_FILE;
delete localEnv.LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE;
delete localEnv.LUMIRA_BOOTSTRAP_ADMIN_INITIALIZATION_SOURCE;

try {
  assertToolchain({ startBackend, startFrontend });

  if (startBackend) {
    if (configuredDbPassword === undefined) {
      throw new Error('DB_PASSWORD must be explicitly configured in lumira-backend/.env (use DB_PASSWORD= only for passwordless local MySQL).');
    }
    const db = parseJdbcEndpoint(localEnv.DB_URL);
    const redisPort = parsePort(localEnv.REDIS_PORT, 6379, 'Redis port');
    if (!allowRemoteServices && (!isLoopback(db.host) || !isLoopback(localEnv.REDIS_HOST))) {
      throw new Error('Native local mode only accepts loopback MySQL/Redis endpoints. Use --allow-remote-services to opt in explicitly.');
    }
    if (!await isPortOpen(db.host, db.port)) {
      throw new Error(`MySQL is not reachable at ${db.host}:${db.port}. Start the native database service first.`);
    }
    if (!await isPortOpen(localEnv.REDIS_HOST, redisPort)) {
      throw new Error(`Redis-compatible service is not reachable at ${localEnv.REDIS_HOST}:${redisPort}. Start it first.`);
    }
    await assertPortFree(backendPort, 'Backend');
    if (full) {
      await assertPortFree(asyncPort, 'Async worker');
      await assertPortFree(jobPort, 'Job worker');
    }
  } else if (!await isPortOpen('127.0.0.1', backendPort)) {
    throw new Error(`Frontend-only mode requires a backend at ${backendUrl}.`);
  }

  if (startFrontend) {
    await assertPortFree(frontendPort, 'Frontend');
  }
} catch (error) {
  console.error(`[local] ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}

const selectedEndpoints = [
  ...(startBackend ? [`backend=${backendUrl}`] : []),
  ...(full ? [`async=${asyncUrl}`, `job=http://127.0.0.1:${jobPort}`] : []),
  ...(startFrontend ? [`frontend=http://127.0.0.1:${frontendPort}`] : []),
];
console.log(`[local] Native preflight passed (profile=${localProfile}, ${selectedEndpoints.join(', ')}).`);
console.log(`[local] Configuration source: ${existsSync(localEnvPath) ? path.relative(repoRoot, localEnvPath) : 'built-in local defaults'}.`);
console.log('[local] Docker was not invoked.');

if (checkOnly) {
  process.exit(0);
}

const mavenCommand = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
const backendModules = full
  ? 'services/lumira-admin,services/lumira-async,services/lumira-quartz'
  : 'services/lumira-admin';
if (startBackend && !skipBuild) {
  console.log(`[local] Installing native backend reactor dependencies for ${backendModules}...`);
  const build = runSync(mavenCommand, ['-Dmaven.test.skip=true', '-pl', backendModules, '-am', 'install'], {
    cwd: backendRoot,
    env: localEnv,
  });
  if (build.status !== 0) {
    process.exit(build.status ?? 1);
  }
}

if (startBackend) {
  const databaseEndpoint = parseJdbcEndpoint(localEnv.DB_URL);
  const activeProfiles = localProfile.split(',').map((item) => item.trim().toLowerCase());
  const automaticBootstrapAllowed = activeProfiles.includes('dev') && isLoopback(databaseEndpoint.host);
  if (configuredBootstrapSecretPath && !automaticBootstrapAllowed) {
    console.error('[local] LUMIRA_LOCAL_BOOTSTRAP_ADMIN_PASSWORD_FILE is only allowed with the dev profile and loopback MySQL.');
    process.exit(1);
  }
  if (automaticBootstrapAllowed) {
    console.log('[local] Building the local administrator credential bootstrap tool...');
    const bootstrapBuild = runSync(
      mavenCommand,
      ['-f', path.join('..', 'deploy', 'bootstrap-admin', 'pom.xml'), '-Dmaven.test.skip=true', 'package'],
      { cwd: backendRoot, env: localEnv },
    );
    if (bootstrapBuild.status !== 0 || !existsSync(bootstrapAdminJar)) {
      console.error('[local] Administrator credential bootstrap tool could not be built.');
      process.exit(bootstrapBuild.status && bootstrapBuild.status !== 0 ? bootstrapBuild.status : 1);
    }

    try {
      const result = ensureLocalAdminCredential({
        repoRoot,
        jarPath: bootstrapAdminJar,
        databaseEnv: {
          DB_URL: localEnv.DB_URL,
          DB_USERNAME: localEnv.DB_USERNAME,
          DB_PASSWORD: localEnv.DB_PASSWORD,
        },
        configuredSecretPath: configuredBootstrapSecretPath,
      });
      for (const line of formatLocalAdminNotice(result, repoRoot)) {
        console.log(line);
      }
    } catch (error) {
      console.error(`[local] ${error instanceof Error ? error.message : String(error)}`);
      process.exit(1);
    }
  } else {
    console.log('[local] Automatic administrator bootstrap skipped: it only runs with the dev profile and loopback MySQL.');
  }
}

const backendSpecs = startBackend ? [
  {
    label: 'lumira-server',
    command: mavenCommand,
    args: [
      '-f', 'services/lumira-admin/pom.xml',
      'spring-boot:run',
      `-Dspring-boot.run.profiles=${localProfile}`,
      `-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=${backendPort}`,
    ],
    options: { cwd: backendRoot, env: localEnv },
    port: backendPort,
  },
  ...(full ? [
    {
      label: 'lumira-async',
      command: mavenCommand,
      args: [
        '-f', 'services/lumira-async/pom.xml',
        'spring-boot:run',
        `-Dspring-boot.run.profiles=${localProfile}`,
        `-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=${asyncPort}`,
      ],
      options: { cwd: backendRoot, env: localEnv },
      port: asyncPort,
    },
    {
      label: 'lumira-job-executor',
      command: mavenCommand,
      args: [
        '-f', 'services/lumira-quartz/pom.xml',
        'spring-boot:run',
        `-Dspring-boot.run.profiles=${localProfile}`,
        `-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=${jobPort}`,
      ],
      options: { cwd: backendRoot, env: { ...localEnv, SERVER_PORT: String(jobPort) } },
      port: jobPort,
    },
  ] : []),
] : [];

const frontendSpec = startFrontend ? {
  label: 'lumira-ui',
  command: process.platform === 'win32' ? 'corepack.cmd' : 'corepack',
  args: ['pnpm', 'run', 'dev', '--', '--host', '127.0.0.1', '--port', String(frontendPort)],
  options: { cwd: frontendRoot, env: localEnv },
} : undefined;

const managedProcesses = new Map();
let shuttingDown = false;
let sourceWatcher;
let changeBatcher;
let activeBuildChild;
let rebuildRunning = false;
let rebuildQueued = false;
let backendWatchArmed = false;
const queuedChangedFiles = new Set();
let resolveMain;
let mainResolved = false;

function finishMain(code) {
  if (mainResolved) {
    return;
  }
  mainResolved = true;
  resolveMain(code);
}

function stopAll() {
  if (shuttingDown) {
    return;
  }
  shuttingDown = true;
  sourceWatcher?.close();
  changeBatcher?.close();
  terminateProcessTree(activeBuildChild);
  for (const record of managedProcesses.values()) {
    record.intentional = true;
    terminateProcessTree(record.child);
  }
}

function startManagedProcess(spec) {
  const child = startProcess(spec.label, spec.command, spec.args, spec.options);
  const record = { child, intentional: false, spec };
  managedProcesses.set(spec.label, record);

  child.once('error', (error) => {
    if (managedProcesses.get(spec.label) !== record || record.intentional || shuttingDown) {
      return;
    }
    if (watchBackend && backendSpecs.some((backendSpec) => backendSpec.label === spec.label)) {
      backendWatchArmed = true;
      console.error(`[local:watch] ${spec.label} failed to start: ${error.message}; Umi HMR and backend source watching remain online.`);
      return;
    }
    console.error(`[local] ${spec.label} failed to start: ${error.message}`);
    stopAll();
    finishMain(1);
  });
  child.once('exit', (code, signal) => {
    if (managedProcesses.get(spec.label) === record) {
      managedProcesses.delete(spec.label);
    }
    if (record.intentional || shuttingDown) {
      return;
    }
    if (watchBackend && backendSpecs.some((backendSpec) => backendSpec.label === spec.label)) {
      backendWatchArmed = true;
      console.error(`[local:watch] ${spec.label} exited unexpectedly (${signal || code || 0}); Umi HMR and backend source watching remain online.`);
      return;
    }
    console.error(`[local] ${spec.label} exited unexpectedly (${signal || code || 0}); stopping the remaining processes.`);
    stopAll();
    finishMain(code && code !== 0 ? code : 1);
  });

  return record;
}

function waitForProcessExit(child, timeoutMs = 15_000) {
  if (!child || child.exitCode !== null || child.signalCode !== null) {
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new Error(`Timed out waiting for process ${child.pid} to exit.`));
    }, timeoutMs);
    child.once('exit', () => {
      clearTimeout(timer);
      resolve();
    });
  });
}

async function waitForPortClosed(port, timeoutMs = 15_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (!await isPortOpen('127.0.0.1', port, 300)) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  throw new Error(`Backend port ${port} did not close after the previous runtime stopped.`);
}

function runBackendBuild() {
  const resolved = portableCommand(mavenCommand, [
    '-Dmaven.test.skip=true',
    '-pl', backendModules,
    '-am',
    'install',
  ]);
  console.log(`[local:watch] Compiling ${backendModules}...`);
  const child = spawn(resolved.command, resolved.args, {
    cwd: backendRoot,
    env: localEnv,
    stdio: 'inherit',
  });
  activeBuildChild = child;
  return new Promise((resolve) => {
    child.once('error', (error) => {
      if (!shuttingDown) {
        console.error(`[local:watch] Maven failed to start: ${error.message}`);
      }
      if (activeBuildChild === child) {
        activeBuildChild = undefined;
      }
      resolve(1);
    });
    child.once('exit', (code, signal) => {
      if (activeBuildChild === child) {
        activeBuildChild = undefined;
      }
      resolve(code ?? (signal ? 1 : 0));
    });
  });
}

async function stopBackendProcessesForBuild() {
  console.log('[local:watch] Stopping native Java runtime(s) before compilation; Umi HMR remains online...');
  const records = backendSpecs
    .map((spec) => managedProcesses.get(spec.label))
    .filter(Boolean);

  for (const record of records) {
    record.intentional = true;
    terminateProcessTree(record.child);
  }
  await Promise.all(records.map((record) => waitForProcessExit(record.child)));
  await Promise.all(backendSpecs.map((spec) => waitForPortClosed(spec.port)));
}

async function startBackendProcessesAfterBuild() {
  if (shuttingDown) {
    return;
  }
  for (const spec of backendSpecs) {
    startManagedProcess(spec);
  }
  const deadline = Date.now() + 120_000;
  while (!shuttingDown && Date.now() < deadline) {
    const backendExited = backendSpecs.some((spec) => !managedProcesses.has(spec.label));
    if (backendExited) {
      throw new Error('A restarted backend runtime exited before its port became ready.');
    }
    const ready = (await Promise.all(
      backendSpecs.map((spec) => isPortOpen('127.0.0.1', spec.port, 500)),
    )).every(Boolean);
    if (ready) {
      await waitForLocalReadiness({
        targets: readinessTargets.filter((target) => target.label !== 'lumira-ui'),
        timeoutMs: Math.min(readinessTimeoutMs, 120_000),
        cancelled: () => shuttingDown,
      });
      console.log('[local:watch] Native Java runtime(s) restarted. Umi HMR remained online.');
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  if (!shuttingDown) {
    throw new Error('Timed out waiting for the restarted backend runtime(s) to listen.');
  }
}

async function runQueuedBackendRebuilds() {
  if (rebuildRunning || shuttingDown) {
    rebuildQueued = true;
    return;
  }
  rebuildRunning = true;
  try {
    do {
      rebuildQueued = false;
      const files = [...queuedChangedFiles].sort();
      queuedChangedFiles.clear();
      const preview = files.slice(0, 3).join(', ');
      console.log(`[local:watch] Detected ${files.length} backend change(s)${preview ? `: ${preview}` : ''}${files.length > 3 ? ', ...' : ''}`);

      await stopBackendProcessesForBuild();
      if (shuttingDown) {
        return;
      }
      const status = await runBackendBuild();
      if (shuttingDown) {
        return;
      }
      if (status === 0) {
        console.log('[local:watch] Compilation succeeded; starting native Java runtime(s)...');
        await startBackendProcessesAfterBuild();
      } else {
        console.error('[local:watch] Compilation failed; Umi HMR remains online and Java will recover after the next successful save.');
      }
    } while (!shuttingDown && (rebuildQueued || queuedChangedFiles.size > 0));
  } catch (error) {
    backendWatchArmed = true;
    console.error(`[local:watch] Automatic restart failed: ${error instanceof Error ? error.message : String(error)} Umi HMR and backend source watching remain online.`);
  } finally {
    rebuildRunning = false;
    if (!shuttingDown && backendWatchArmed && (rebuildQueued || queuedChangedFiles.size > 0)) {
      rebuildQueued = false;
      setTimeout(() => void runQueuedBackendRebuilds(), 0);
    }
  }
}

async function armBackendWatchWhenReady() {
  while (!shuttingDown && !backendWatchArmed) {
    const ready = (await Promise.all(
      backendSpecs.map((spec) => isPortOpen('127.0.0.1', spec.port, 500)),
    )).every(Boolean);
    if (ready) {
      backendWatchArmed = true;
      console.log('[local:watch] Backend runtime is ready; live compile is now armed.');
      if (queuedChangedFiles.size > 0) {
        void runQueuedBackendRebuilds();
      }
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
}

const exitCodePromise = new Promise((resolve) => {
  resolveMain = resolve;
});

process.once('SIGINT', () => {
  stopAll();
  finishMain(130);
});
process.once('SIGTERM', () => {
  stopAll();
  finishMain(143);
});

for (const spec of backendSpecs) {
  startManagedProcess(spec);
}
if (frontendSpec) {
  startManagedProcess(frontendSpec);
}

if (startBackend && watchBackend) {
  changeBatcher = createChangeBatcher({
    delayMs: 5_000,
    onBatch: (files) => {
      for (const file of files) {
        queuedChangedFiles.add(file);
      }
      if (!backendWatchArmed) {
        console.log(`[local:watch] Queued ${files.length} backend change(s) until the initial Java runtime is ready.`);
        return;
      }
      if (rebuildRunning) {
        rebuildQueued = true;
        return;
      }
      void runQueuedBackendRebuilds();
    },
  });
  try {
    sourceWatcher = watch(backendRoot, { recursive: true }, (_eventType, fileName) => {
      changeBatcher.add(String(fileName || ''));
    });
    sourceWatcher.once('error', (error) => {
      if (shuttingDown) {
        return;
      }
      console.error(`[local:watch] Backend source watcher failed: ${error.message}`);
      stopAll();
      finishMain(1);
    });
    console.log('[local:watch] Backend live compile is enabled for src/main/java, src/main/resources, and pom.xml changes.');
    void armBackendWatchWhenReady();
  } catch (error) {
    console.error(`[local:watch] Backend source watcher failed: ${error instanceof Error ? error.message : String(error)}`);
    stopAll();
    process.exit(1);
  }
}

try {
  console.log(`[local] Waiting up to ${Math.round(readinessTimeoutMs / 1_000)}s for business readiness...`);
  await waitForLocalReadiness({
    targets: readinessTargets,
    timeoutMs: readinessTimeoutMs,
    cancelled: () => shuttingDown || mainResolved,
    onProgress: ({ label, url, detail }) => {
      console.log(`[local] Ready: ${label} (${detail}) ${url}`);
    },
  });
  if (!mainResolved) {
    console.log('[local] Local native environment is business-ready. Umi HMR is enabled. Press Ctrl+C to stop every process.');
  }
} catch (error) {
  if (!mainResolved) {
    console.error(`[local] ${error instanceof Error ? error.message : String(error)}`);
    stopAll();
    finishMain(1);
  }
}

const exitCode = await exitCodePromise;
process.exit(exitCode);
