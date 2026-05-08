#!/usr/bin/env node

import { createInterface } from 'node:readline';
import { spawn, spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import process from 'node:process';
import net from 'node:net';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..');
const composeFile = path.join('deploy', 'docker-compose.yml');

const argv = new Set(process.argv.slice(2));
const showHelp = argv.has('--help') || argv.has('-h');
const skipInfra = argv.has('--skip-infra') || argv.has('--no-infra');
const skipFrontend = argv.has('--skip-frontend') || argv.has('--no-frontend');
const skipServices = argv.has('--skip-services') || argv.has('--no-services');
const verboseLogs = argv.has('--verbose-logs') || argv.has('--verbose');
let runtimeEnv = process.env;

function log(message) {
  console.log(`[launcher] ${message}`);
}

function error(message) {
  console.error(`[launcher] ${message}`);
}

function printUsage() {
  console.log(`Usage: node scripts/start-platform.mjs [options]

Options:
  --skip-infra      Skip Docker Compose infrastructure startup.
  --skip-services   Skip Java backend services.
  --skip-frontend   Skip frontend dev server.
  --verbose-logs    Show full process logs without filtering.
  -h, --help        Show this help message.
`);
}

function commandExists(command, args = ['--version']) {
  const result = spawnSync(command, args, {
    cwd: repoRoot,
    stdio: 'ignore',
    shell: false,
  });
  return result.status === 0;
}

function pickPackageManager() {
  if (commandExists('corepack')) {
    return { command: 'corepack', args: ['pnpm'] };
  }

  if (commandExists('pnpm')) {
    return { command: 'pnpm', args: [] };
  }

  throw new Error('Neither corepack nor pnpm was found in PATH.');
}

function buildRuntimeEnv() {
  return {
    ...process.env,
    DB_USERNAME: process.env.DB_USERNAME ?? 'root',
    DB_PASSWORD: process.env.DB_PASSWORD ?? '123456',
    NACOS_DISCOVERY_ENABLED: process.env.NACOS_DISCOVERY_ENABLED ?? 'true',
    JWT_SECRET: process.env.JWT_SECRET ?? 'saas_foundation_jwt_secret_for_dev_env_please_change_me_2026',
    SAAS_JOB_INTERNAL_TOKEN: process.env.SAAS_JOB_INTERNAL_TOKEN ?? 'legendary-job-token',
    XXL_JOB_ADMIN_ADDRESSES: process.env.XXL_JOB_ADMIN_ADDRESSES ?? 'http://localhost:8090/xxl-job-admin',
    XXL_JOB_ACCESS_TOKEN: process.env.XXL_JOB_ACCESS_TOKEN ?? 'legendary-xxl-job-token',
    XXL_JOB_ADMIN_ACCESS_TOKEN: process.env.XXL_JOB_ADMIN_ACCESS_TOKEN ?? 'legendary-xxl-job-token',
  };
}

function frontendNodeModulesExists() {
  return existsSync(path.join(repoRoot, 'frontend', 'node_modules'));
}

function attachLinePrefix(stream, prefix, isError = false) {
  const interface_ = createInterface({ input: stream });
  const writer = isError ? error : log;

  interface_.on('line', (line) => {
    if (!isError && !verboseLogs && shouldSuppressLine(line)) {
      return;
    }
    writer(`${prefix} ${line}`);
  });
}

function shouldSuppressLine(line) {
  const noisyPatterns = [
    /^\[INFO\] Scanning for projects\.\.\.$/,
    /^\[INFO\] Building /,
    /^\[INFO\] --- .* @ .* ---$/,
    /^\[INFO\] Changes detected - recompiling the module!$/,
    /^\[INFO\] Compiling \d+ source files?/,
    /^\[INFO\] Copying \d+ resources?/,
    /^\[INFO\] Copying \d+ resource$/,
    /^\[INFO\] Nothing to compile - all classes are up to date\.$/,
    /^\[INFO\] skip non existing resourceDirectory/,
    /^Progress \(\d+\): /,
    /^\[INFO\] Downloading from /,
    /^\[INFO\] Downloaded from /,
  ];
  return noisyPatterns.some((pattern) => pattern.test(line));
}

function installSignalHandlers() {
  process.on('SIGINT', () => {
    void shutdown(0);
  });
  process.on('SIGTERM', () => {
    void shutdown(0);
  });
}

function waitForTcp({ host = '127.0.0.1', port, timeoutMs = 120_000, intervalMs = 1_000, label }) {
  return new Promise((resolve, reject) => {
    const startedAt = Date.now();

    const attempt = () => {
      const socket = net.createConnection({ host, port });

      socket.once('connect', () => {
        socket.end();
        log(`${label} is ready on ${host}:${port}`);
        resolve();
      });

      socket.once('error', () => {
        socket.destroy();
        if (Date.now() - startedAt >= timeoutMs) {
          reject(new Error(`Timed out waiting for ${label} on ${host}:${port}`));
          return;
        }

        setTimeout(attempt, intervalMs);
      });
    };

    attempt();
  });
}

function probeTcp({ host = '127.0.0.1', port, timeoutMs = 1_000 }) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host, port });
    const timer = setTimeout(() => {
      socket.destroy();
      resolve(false);
    }, timeoutMs);

    socket.once('connect', () => {
      clearTimeout(timer);
      socket.end();
      resolve(true);
    });

    socket.once('error', () => {
      clearTimeout(timer);
      socket.destroy();
      resolve(false);
    });
  });
}

function runCommand(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: options.cwd ?? repoRoot,
    stdio: options.stdio ?? 'inherit',
    shell: false,
    env: options.env ?? runtimeEnv,
  });

  return result;
}

function startComposeServices(services, extraEnv = {}, options = {}) {
  if (services.length === 0) {
    return { status: 0 };
  }

  const args = ['compose', '-f', composeFile, 'up', '-d'];
  if (options.noDeps) {
    args.push('--no-deps');
  }
  args.push(...services);

  return spawnSync('docker', args, {
    cwd: repoRoot,
    stdio: 'inherit',
    shell: false,
    env: {
      ...runtimeEnv,
      ...extraEnv,
    },
  });
}

async function ensureFrontendDependencies() {
  if (frontendNodeModulesExists()) {
    log('Reusing existing frontend dependencies.');
    return;
  }

  const packageManager = pickPackageManager();
  const installCommand = packageManager.command;
  const installArgs = [...packageManager.args, 'install', '--frozen-lockfile'];
  log('Installing frontend dependencies...');
  const installResult = runCommand(installCommand, installArgs, { cwd: path.join(repoRoot, 'frontend') });
  if (installResult.status !== 0) {
    throw new Error('Frontend dependency installation failed.');
  }
}

function ensureSharedLibraries() {
  log('Installing shared library modules from source...');
  const installResult = runCommand('mvn', ['-f', 'libs/common-web/pom.xml', 'install', '-DskipTests']);
  if (installResult.status !== 0) {
    throw new Error('Shared library installation failed.');
  }
}

async function probeBackendServices(services) {
  const availability = [];

  for (const service of services) {
    // eslint-disable-next-line no-await-in-loop
    const running = await probeTcp({ port: service.port });
    availability.push({ ...service, running });
  }

  return availability;
}

function spawnTask(task) {
  log(`Starting ${task.name}...`);

  const child = spawn(task.command, task.args, {
    cwd: task.cwd ?? repoRoot,
    env: runtimeEnv,
    shell: false,
    stdio: ['ignore', 'pipe', 'pipe'],
  });

  attachLinePrefix(child.stdout, `[${task.name}]`);
  attachLinePrefix(child.stderr, `[${task.name}][err]`, true);

  child.on('error', (err) => {
    error(`${task.name} failed to start: ${err.message}`);
    void shutdown(1);
  });

  child.on('exit', (code, signal) => {
    if (isShuttingDown) {
      return;
    }

    const exitCode = typeof code === 'number' && code !== 0 ? code : 1;
    error(`${task.name} exited unexpectedly (code=${code ?? 'null'}, signal=${signal ?? 'null'})`);
    void shutdown(exitCode);
  });

  runningChildren.add(child);
}

function formatServiceRef(service) {
  if (service.url) {
    return `${service.name} (${service.url})`;
  }
  return `${service.name} (127.0.0.1:${service.port})`;
}

async function waitForTaskReadiness(tasks) {
  const readyChecks = tasks.filter((task) => typeof task.port === 'number');

  if (readyChecks.length === 0) {
    return;
  }

  log(`Waiting for readiness: ${readyChecks.map((task) => task.name).join(', ')}`);

  await Promise.all(
    readyChecks.map(async (task) => {
      await waitForTcp({
        port: task.port,
        label: `${task.name} startup`,
        timeoutMs: task.startupTimeoutMs ?? 300_000,
        intervalMs: 1_000,
      });
    }),
  );
}

function printReadyBanner(readyEntries, reusedEntries) {
  if (readyEntries.length === 0 && reusedEntries.length === 0) {
    return;
  }

  log('Platform is ready.');
  for (const entry of readyEntries) {
    log(`Ready: ${formatServiceRef(entry)}`);
  }
  for (const entry of reusedEntries) {
    log(`Reused: ${formatServiceRef(entry)}`);
  }
}

let isShuttingDown = false;
let forcedExitTimer = null;
const runningChildren = new Set();

async function shutdown(exitCode) {
  if (isShuttingDown) {
    return;
  }

  isShuttingDown = true;
  process.exitCode = exitCode;
  log('Stopping running processes...');

  for (const child of runningChildren) {
    try {
      child.kill('SIGTERM');
    } catch {
      // Ignore cleanup errors.
    }
  }

  if (forcedExitTimer) {
    clearTimeout(forcedExitTimer);
  }

  if (runningChildren.size === 0) {
    process.exit(exitCode);
    return;
  }

  forcedExitTimer = setTimeout(() => {
    for (const child of runningChildren) {
      try {
        child.kill('SIGKILL');
      } catch {
        // Ignore cleanup errors.
      }
    }
    process.exit(exitCode);
  }, 5_000);
  forcedExitTimer.unref?.();
}

async function main() {
  if (showHelp) {
    printUsage();
    return;
  }

  installSignalHandlers();
  runtimeEnv = buildRuntimeEnv();

  if (!process.env.JWT_SECRET) {
    log('JWT_SECRET is not set. Using the built-in local development fallback.');
  }
  if (!process.env.SAAS_JOB_INTERNAL_TOKEN) {
    log('SAAS_JOB_INTERNAL_TOKEN is not set. Using the built-in local development fallback.');
  }

  if (!skipInfra) {
    if (!commandExists('docker', ['--version'])) {
      throw new Error('Docker was not found in PATH. Install Docker or rerun with --skip-infra.');
    }

    const [mysqlReady, redisReady, nacosReady, nacosGrpcReady, xxlReady] = await Promise.all([
      probeTcp({ port: 3306 }),
      probeTcp({ port: 6379 }),
      probeTcp({ port: 8848 }),
      probeTcp({ port: 9848 }),
      probeTcp({ port: 8090 }),
    ]);

    const startedServices = [];

    if (!mysqlReady) {
      log(`Starting mysql from ${composeFile}...`);
      const mysqlCompose = startComposeServices(['mysql']);
      if (mysqlCompose.status !== 0) {
        throw new Error('docker compose up -d mysql failed.');
      }
      startedServices.push('mysql');
      await waitForTcp({ port: 3306, label: 'mysql', timeoutMs: 180_000 });
    } else {
      log('Reusing existing MySQL on 127.0.0.1:3306.');
    }

    const needsRedis = !redisReady;
    const needsNacos = !(nacosReady && nacosGrpcReady);
    const redisAndNacos = [];
    if (needsRedis) {
      redisAndNacos.push('redis');
    } else {
      log('Reusing existing Redis on 127.0.0.1:6379.');
    }
    if (needsNacos) {
      redisAndNacos.push('nacos');
    } else {
      log('Reusing existing Nacos on 127.0.0.1:8848/9848.');
    }
    if (redisAndNacos.length > 0) {
      log(`Starting ${redisAndNacos.join(', ')} from ${composeFile}...`);
      const compose = startComposeServices(redisAndNacos, {}, { noDeps: true });
      if (compose.status !== 0) {
        throw new Error(`docker compose up -d ${redisAndNacos.join(' ')} failed.`);
      }
      if (needsRedis) {
        await waitForTcp({ port: 6379, label: 'redis', timeoutMs: 120_000 });
      }
      if (needsNacos) {
        await waitForTcp({ port: 8848, label: 'nacos', timeoutMs: 180_000 });
        await waitForTcp({ port: 9848, label: 'nacos grpc', timeoutMs: 180_000 });
      }
    }

    if (!xxlReady) {
      const xxlDbHost = mysqlReady ? 'host.docker.internal' : 'mysql';
      log(`Starting xxl-job-admin from ${composeFile} using ${xxlDbHost} as the database host...`);
      const xxlCompose = startComposeServices(['xxl-job-admin'], { XXL_JOB_DB_HOST: xxlDbHost }, { noDeps: true });
      if (xxlCompose.status !== 0) {
        throw new Error('docker compose up -d xxl-job-admin failed.');
      }
      await waitForTcp({ port: 8090, label: 'xxl-job-admin', timeoutMs: 180_000 });
    } else {
      log('Reusing existing XXL-Job Admin on 127.0.0.1:8090.');
    }
  }

  const tasks = [];
  const reusedEntries = [];
  const backendServices = [
    { name: 'system-service', port: 8080, command: 'mvn', args: ['-f', 'backend/pom.xml', 'spring-boot:run'] },
    { name: 'gateway-service', port: 8081, command: 'mvn', args: ['-f', 'services/gateway-service/pom.xml', 'spring-boot:run'] },
    { name: 'auth-service', port: 8082, command: 'mvn', args: ['-f', 'services/auth-service/pom.xml', 'spring-boot:run'] },
    { name: 'tenant-service', port: 8083, command: 'mvn', args: ['-f', 'services/tenant-service/pom.xml', 'spring-boot:run'] },
    { name: 'file-service', port: 8084, command: 'mvn', args: ['-f', 'services/file-service/pom.xml', 'spring-boot:run'] },
    { name: 'message-service', port: 8085, command: 'mvn', args: ['-f', 'services/message-service/pom.xml', 'spring-boot:run'] },
    { name: 'plugin-service', port: 8086, command: 'mvn', args: ['-f', 'services/plugin-service/pom.xml', 'spring-boot:run'] },
    { name: 'localization-service', port: 8088, command: 'mvn', args: ['-f', 'services/localization-service/pom.xml', 'spring-boot:run'] },
    { name: 'job-executor', port: 8089, command: 'mvn', args: ['-f', 'services/job-executor/pom.xml', 'spring-boot:run'] },
  ];

  const backendAvailability = skipServices ? [] : await probeBackendServices(backendServices);
  const missingBackendServices = backendAvailability.filter((service) => !service.running);

  if (!skipServices) {
    for (const service of backendAvailability) {
      // Skip services that are already listening locally.
      // The launcher is meant to be idempotent on a shared dev machine.
      // If the port is free, we start just that missing service.
      // If the port is occupied, we assume the service is already up.
      // This keeps the script safe on machines with existing local infra.
      if (service.running) {
        log(`Reusing existing ${service.name} on 127.0.0.1:${service.port}.`);
        reusedEntries.push(service);
        continue;
      }
      tasks.push(service);
    }
  }

  if (missingBackendServices.length > 0) {
    ensureSharedLibraries();
  }

  if (!skipFrontend) {
    const frontendRunning = await probeTcp({ port: 8000 });

    if (frontendRunning) {
      log('Reusing existing frontend on 127.0.0.1:8000.');
      reusedEntries.push({ name: 'frontend', port: 8000, url: 'http://localhost:8000' });
    } else {
      // Install frontend dependencies only when the frontend workspace is still cold.
      // That gives us a smoother first-run experience on a fresh clone.
      await ensureFrontendDependencies();

      const packageManager = pickPackageManager();
      tasks.push({
        name: 'frontend',
        port: 8000,
        url: 'http://localhost:8000',
        startupTimeoutMs: 180_000,
        command: packageManager.command,
        args: [...packageManager.args, '--dir', 'frontend', 'dev'],
      });
    }
  }

  if (tasks.length === 0) {
    if (reusedEntries.length > 0) {
      printReadyBanner([], reusedEntries);
      log('All requested parts are already running.');
      return;
    }
    log('Nothing to start. All requested parts were skipped.');
    return;
  }

  for (const task of tasks) {
    spawnTask(task);
  }

  log('All requested processes have been launched.');
  if (!verboseLogs) {
    log('Log filtering is enabled. Use --verbose-logs to print full build logs.');
  }
  await waitForTaskReadiness(tasks);
  printReadyBanner(tasks, reusedEntries);
  log('Press Ctrl+C to stop the platform.');

  await new Promise(() => {});
}

try {
  await main();
} catch (err) {
  error(err instanceof Error ? err.message : String(err));
  process.exit(1);
}
