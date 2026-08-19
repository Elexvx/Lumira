#!/usr/bin/env node

import http from 'node:http';
import os from 'node:os';
import { createHash, randomUUID } from 'node:crypto';
import {
  closeSync,
  existsSync,
  mkdirSync,
  openSync,
  readFileSync,
  readdirSync,
  renameSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

import { parseEnvFile, setEnvValue } from './lib/env-utils.mjs';
import { probeHttp, sleep } from './lib/http-utils.mjs';
import { runManagedCommand } from './lib/managed-command.mjs';
import { validateBackupEvidence } from './lib/platform-backup-contract.mjs';
import {
  TERMINAL_UPDATE_STATUSES,
  UPDATER_PROTOCOL_VERSION,
  UPDATE_PHASES,
  UPDATE_STRATEGY,
  buildPreflightReport,
  createInitialDeploymentState,
  inactiveSlot,
  normalizeReleaseManifest,
  normalizeSlot,
  parseRuntimeVersionIdentity,
  phaseProgress,
  renderActiveUpstreams,
} from './lib/platform-update-contract.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const deployDir = path.resolve(process.env.LUMIRA_DEPLOY_DIR || path.join(repoRoot, 'deploy'));
const envPath = path.join(deployDir, '.env');
const composePath = path.join(deployDir, 'docker-compose.prod.yml');
const tasksDir = path.join(deployDir, '.update-tasks');
const preflightDir = path.join(deployDir, '.update-preflights');
const statePath = path.join(deployDir, '.update-state.json');
const lockPath = path.join(deployDir, '.update.lock');
const upstreamDir = path.join(deployDir, '.generated', 'api-proxy');
const upstreamPath = path.join(upstreamDir, 'active-upstreams.conf');
const host = process.env.LUMIRA_UPDATER_HOST || '127.0.0.1';
const port = Number(process.env.LUMIRA_UPDATER_PORT || 9788);
const token = process.env.PLATFORM_UPDATE_AGENT_TOKEN || process.env.LUMIRA_UPDATER_TOKEN || '';
const dryRun = process.argv.includes('--dry-run') || process.env.LUMIRA_UPDATER_DRY_RUN === 'true';
const containerPrefix = process.env.LUMIRA_CONTAINER_PREFIX || 'lumira-';
const skipPullIfPresent = process.env.LUMIRA_UPDATER_SKIP_PULL_IF_PRESENT === 'true';
const rollbackDrainSeconds = Math.max(0, Math.min(600, Number(process.env.LUMIRA_UPDATER_ROLLBACK_DRAIN_SECONDS || 60)));
const pullNoProgressTimeoutMs = Math.max(30_000, Math.min(30 * 60_000, Number(process.env.LUMIRA_UPDATER_PULL_NO_PROGRESS_TIMEOUT_MS || 5 * 60_000)));
const pullTimeoutMs = Math.max(pullNoProgressTimeoutMs, Math.min(2 * 60 * 60_000, Number(process.env.LUMIRA_UPDATER_PULL_TIMEOUT_MS || 30 * 60_000)));
const allowedImagePrefixes = String(
  process.env.PLATFORM_UPDATE_ALLOWED_IMAGE_PREFIXES
    || 'ghcr.io/elexvx/lumira/,swr.cn-east-3.myhuaweicloud.com/aiadc/',
)
  .split(',').map((item) => item.trim().toLowerCase()).filter(Boolean);
const activeCommandControllers = new Map();
const bootstrapAdminSecretContainerPath = '/run/secrets/lumira_bootstrap_admin_password';

for (const directory of [tasksDir, preflightDir, upstreamDir]) mkdirSync(directory, { recursive: true });

function atomicWrite(file, content, mode = 0o600) {
  const temporary = `${file}.${process.pid}.${randomUUID()}.tmp`;
  writeFileSync(temporary, content, { mode });
  renameSync(temporary, file);
}

function json(res, statusCode, body) {
  res.writeHead(statusCode, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(body));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', (chunk) => {
      body += chunk;
      if (body.length > 1024 * 1024) {
        reject(new Error('Request body too large'));
        req.destroy();
      }
    });
    req.on('end', () => resolve(body ? JSON.parse(body) : {}));
    req.on('error', reject);
  });
}

const taskPath = (taskId) => path.join(tasksDir, `${taskId}.json`);
const preflightPath = (preflightId) => path.join(preflightDir, `${preflightId}.json`);
const readJson = (file) => existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : null;
const readTask = (taskId) => readJson(taskPath(taskId));
const findTaskByPlatformTaskId = (platformTaskId, platformTaskCreatedAt) => {
  const normalized = String(platformTaskId ?? '').trim();
  const normalizedCreatedAt = String(platformTaskCreatedAt ?? '').trim();
  if (!normalized) return null;
  return readdirSync(tasksDir)
    .filter((name) => name.endsWith('.json'))
    .map((name) => readJson(path.join(tasksDir, name)))
    .filter((task) => String(task?.platformTaskId ?? '').trim() === normalized)
    .filter((task) => !normalizedCreatedAt
      || String(task?.platformTaskCreatedAt ?? '').trim() === normalizedCreatedAt)
    .sort((left, right) => Date.parse(right.updatedAt || 0) - Date.parse(left.updatedAt || 0))[0] || null;
};
const containerName = (service) => `${containerPrefix}${String(service).replace(/^lumira-/, '')}`;
const serverContainer = (slot) => containerName(`server-${normalizeSlot(slot)}`);

function writeTask(task) {
  // Cancellation is written by the HTTP request handler while the update task
  // continues to append logs and persist phase changes. Preserve those external
  // control flags so a later write from the task cannot silently lose the
  // administrator's cancellation/rollback request.
  const persisted = readTask(task.taskId);
  if (persisted?.cancelRequested) task.cancelRequested = true;
  if (persisted?.rollbackRequested) task.rollbackRequested = true;
  atomicWrite(taskPath(task.taskId), `${JSON.stringify(task, null, 2)}\n`);
}

function appendLog(task, message) {
  if (!message) return;
  const backupMatch = String(message).match(/Backup completed:\s*(.+)$/);
  if (backupMatch) task.backupPath = backupMatch[1].trim();
  task.message = String(message).slice(-2000);
  task.log = [...(task.log || []), `${new Date().toISOString()} ${message}`].slice(-120);
  task.updatedAt = new Date().toISOString();
  writeTask(task);
}

function setPhase(task, phase, message) {
  if (!UPDATE_PHASES.includes(phase)) throw new Error(`Unknown update phase: ${phase}`);
  task.phase = phase;
  task.progressPercent = phaseProgress(phase);
  appendLog(task, message || phase);
}

function toWslPath(value) {
  const match = String(value).match(/^([a-zA-Z]):[\\/](.*)$/);
  return match ? `/mnt/${match[1].toLowerCase()}/${match[2].replaceAll('\\', '/')}` : value;
}

function toHostReadablePath(value) {
  const normalized = String(value || '').trim();
  if (process.platform !== 'win32' || !normalized.startsWith('/')) return normalized;
  const mountedDrive = normalized.match(/^\/mnt\/([a-zA-Z])\/(.*)$/u);
  if (mountedDrive) return `${mountedDrive[1].toUpperCase()}:\\${mountedDrive[2].replaceAll('/', '\\')}`;
  const distro = process.env.LUMIRA_WSL_DISTRO;
  const prefix = distro ? ['-d', distro, '-u', 'root', '--'] : ['--'];
  const conversion = spawnSync('wsl.exe', [...prefix, 'wslpath', '-w', normalized], {
    cwd: repoRoot,
    encoding: 'utf8',
    stdio: 'pipe',
    shell: false,
  });
  if (conversion.status === 0 && conversion.stdout.trim()) return conversion.stdout.trim();
  return distro ? `\\\\wsl.localhost\\${distro}${normalized.replaceAll('/', '\\')}` : normalized;
}

function configuredDatabaseName(environment) {
  const jdbcUrl = String(environment.DB_URL || '');
  const pathMatch = jdbcUrl.match(/^jdbc:mysql:\/\/[^/]+\/([^?;]+)/iu);
  if (pathMatch) {
    try {
      return decodeURIComponent(pathMatch[1]);
    } catch {
      return pathMatch[1];
    }
  }
  return String(environment.MYSQL_DATABASE || environment.DB_NAME || '').trim() || null;
}

function configuredDatabaseEndpoint(environment) {
  const match = String(environment.DB_URL || '').match(
    /^jdbc:mysql:\/\/(?:\[([^\]]+)\]|([^/:?]+))(?::(\d+))?\//iu,
  );
  const host = String(match ? match[1] || match[2] : environment.DB_HOST || 'mysql').trim();
  const port = String(match?.[3] || environment.DB_PORT || '3306').trim();
  if (!host || /[\s,]/u.test(host) || !/^\d{1,5}$/u.test(port) || Number(port) > 65_535) {
    throw new Error('Unable to derive a safe MySQL host and port for backup identity verification.');
  }
  return { host, port };
}

function configuredMysqlSslMode(environment) {
  const explicit = String(environment.MYSQL_SSL_MODE || '').trim().toUpperCase();
  if (explicit) return explicit;
  const url = String(environment.DB_URL || '');
  const configured = url.match(/[?&]sslMode=([^&]+)/iu)?.[1];
  if (configured) return decodeURIComponent(configured).toUpperCase();
  if (/[?&]useSSL=false(?:&|$)/iu.test(url)) return 'DISABLED';
  if (/[?&]useSSL=true(?:&|$)/iu.test(url)) return 'REQUIRED';
  return 'PREFERRED';
}

function wslForwardedEnvironment(variableNames, pathVariableNames = []) {
  const existing = String(process.env.WSLENV || '').split(':').map((entry) => entry.trim()).filter(Boolean);
  const indexes = new Map(existing.map((entry, index) => [entry.split('/')[0].toUpperCase(), index]));
  const translatedPaths = new Set(pathVariableNames.map((name) => name.toUpperCase()));
  for (const variableName of variableNames) {
    const normalizedName = variableName.toUpperCase();
    const entry = `${variableName}/${translatedPaths.has(normalizedName) ? 'p' : 'u'}`;
    const existingIndex = indexes.get(normalizedName);
    if (existingIndex === undefined) {
      indexes.set(normalizedName, existing.length);
      existing.push(entry);
    } else if (translatedPaths.has(normalizedName)) {
      existing[existingIndex] = entry;
    }
  }
  return existing.join(':');
}

function commandInvocation(command, args) {
  if (process.platform !== 'win32' || !['docker', 'bash'].includes(command)) return { command, args };
  const distro = process.env.LUMIRA_WSL_DISTRO;
  const prefix = distro ? ['-d', distro, '-u', 'root', '--'] : ['--'];
  return { command: 'wsl', args: [...prefix, command, ...args.map(toWslPath)] };
}

function runCommand(task, command, args, options = {}) {
  appendLog(task, `$ ${command} ${args.join(' ')}`);
  if (dryRun) {
    appendLog(task, `[dry-run] skipped ${command}`);
    return Promise.resolve('');
  }
  const invocation = commandInvocation(command, args);
  let controller;
  const promise = runManagedCommand({
    command: invocation.command,
    args: invocation.args,
    cwd: repoRoot,
    env: { ...process.env, ...options.env },
    noProgressTimeoutMs: options.noProgressTimeoutMs,
    timeoutMs: options.timeoutMs,
    onOutput: (text) => appendLog(task, text.trim()),
    onController: (value) => {
      controller = value;
      activeCommandControllers.set(task.taskId, value);
    },
  });
  return promise.finally(() => {
    if (activeCommandControllers.get(task.taskId) === controller) activeCommandControllers.delete(task.taskId);
  });
}

function composeArgs(...args) {
  return ['compose', '--env-file', envPath, '-f', composePath, ...args];
}

function runCompose(task, ...args) {
  return runCommand(task, 'docker', composeArgs(...args), { env: parseEnvFile(envPath) });
}

function updateEnv(values) {
  if (!existsSync(envPath)) throw new Error('deploy/.env not found');
  let next = readFileSync(envPath, 'utf8');
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && String(value).length > 0) next = setEnvValue(next, key, String(value));
  }
  if (!dryRun) atomicWrite(envPath, next);
}

function deploymentState() {
  const existing = readJson(statePath);
  if (existing) return existing;
  const env = parseEnvFile(envPath);
  const activeSlot = normalizeSlot(env.LUMIRA_ACTIVE_SLOT || 'blue');
  const slotPrefix = `LUMIRA_SERVER_${activeSlot.toUpperCase()}_`;
  const initial = createInitialDeploymentState({
    activeSlot,
    commit: env[`${slotPrefix}GIT_COMMIT`] || env.GIT_COMMIT,
    version: env[`${slotPrefix}APP_VERSION`] || env.APP_VERSION,
    buildVersion: env[`${slotPrefix}BUILD_VERSION`] || env.BUILD_VERSION,
    frontendVersion: env[`${slotPrefix}FRONTEND_VERSION`] || env.FRONTEND_VERSION,
    backendVersion: env[`${slotPrefix}BACKEND_VERSION`] || env.BACKEND_VERSION,
    buildTime: env[`${slotPrefix}BUILD_TIME`] || env.BUILD_TIME,
    branch: env[`${slotPrefix}GIT_BRANCH`] || env.GIT_BRANCH,
    databaseVersion: env[`${slotPrefix}DATABASE_VERSION`] || env.DATABASE_VERSION,
    serverImage: env[`${slotPrefix}IMAGE`] || env.LUMIRA_SERVER_IMAGE,
    asyncImage: env.LUMIRA_ASYNC_IMAGE,
    jobExecutorImage: env.LUMIRA_JOB_EXECUTOR_IMAGE,
  });
  if (!dryRun) atomicWrite(statePath, `${JSON.stringify(initial, null, 2)}\n`);
  return initial;
}

function writeDeploymentState(state) {
  state.updatedAt = new Date().toISOString();
  if (!dryRun) atomicWrite(statePath, `${JSON.stringify(state, null, 2)}\n`);
}

function acquireLock(taskId) {
  try {
    const descriptor = openSync(lockPath, 'wx', 0o600);
    writeFileSync(descriptor, `${JSON.stringify({ taskId, pid: process.pid, createdAt: new Date().toISOString() })}\n`);
    closeSync(descriptor);
  } catch (error) {
    if (error?.code === 'EEXIST') throw new Error('Another platform update task is already running.');
    throw error;
  }
}

function releaseLock(taskId) {
  const lock = readJson(lockPath);
  if (!lock || lock.taskId === taskId) rmSync(lockPath, { force: true });
}

function processIsAlive(pid) {
  if (!Number.isInteger(Number(pid)) || Number(pid) <= 0) return false;
  try {
    process.kill(Number(pid), 0);
    return true;
  } catch {
    return false;
  }
}

async function recoverInterruptedTask() {
  const existingLock = readJson(lockPath);
  if (existingLock && processIsAlive(existingLock.pid)) return;
  if (existingLock) rmSync(lockPath, { force: true });
  const interrupted = readdirSync(tasksDir)
    .filter((name) => name.endsWith('.json'))
    .map((name) => readJson(path.join(tasksDir, name)))
    .filter((task) => task?.status === 'RUNNING' || task?.status === 'PENDING')
    .sort((left, right) => Date.parse(right.updatedAt || 0) - Date.parse(left.updatedAt || 0))[0];
  if (!interrupted) return;
  acquireLock(interrupted.taskId);
  try {
    const switched = UPDATE_PHASES.indexOf(interrupted.phase) >= UPDATE_PHASES.indexOf('SWITCHING_TRAFFIC');
    if (switched && interrupted.activeSlot) {
      const liveState = deploymentState();
      const finalizedBeforeCrash = normalizeSlot(liveState.activeSlot) === normalizeSlot(interrupted.targetSlot);
      const recoveryState = {
        ...liveState,
        activeSlot: normalizeSlot(interrupted.activeSlot),
        workers: finalizedBeforeCrash ? liveState.previousWorkers : liveState.workers,
      };
      appendLog(interrupted, 'Recovering interrupted update by restoring the original active slot.');
      await rollbackTraffic(interrupted, recoveryState, interrupted.targetSlot);
      if (finalizedBeforeCrash) {
        liveState.activeSlot = normalizeSlot(interrupted.activeSlot);
        liveState.previousSlot = normalizeSlot(interrupted.targetSlot);
        liveState.workers = liveState.previousWorkers || liveState.workers;
        liveState.rollbackDeadline = new Date(Date.now() + 1800_000).toISOString();
        writeDeploymentState(liveState);
        updateEnv({ LUMIRA_ACTIVE_SLOT: liveState.activeSlot, LUMIRA_SERVER_IMAGE: liveState.slots[liveState.activeSlot]?.serverImage });
      }
      interrupted.status = 'ROLLED_BACK';
    } else {
      if (interrupted.targetSlot) {
        await runCommand(interrupted, 'docker', ['rm', '-f', serverContainer(interrupted.targetSlot)]).catch(() => {});
      }
      interrupted.status = 'FAILED';
      interrupted.errorMessage = 'Updater restarted before traffic switch; the inactive slot was cleaned up safely.';
    }
  } catch (error) {
    interrupted.status = 'FAILED';
    interrupted.errorMessage = `Interrupted update recovery failed: ${error instanceof Error ? error.message : String(error)}`;
  } finally {
    interrupted.finishedAt = new Date().toISOString();
    interrupted.updatedAt = new Date().toISOString();
    writeTask(interrupted);
    releaseLock(interrupted.taskId);
  }
}

async function cleanupExpiredRollbackSlot() {
  if (existsSync(lockPath)) return;
  const state = deploymentState();
  if (!state.previousSlot || !state.rollbackDeadline || Date.parse(state.rollbackDeadline) > Date.now()) return;
  const housekeeping = { taskId: 'housekeeping', status: 'RUNNING', log: [], updatedAt: new Date().toISOString() };
  await runCommand(housekeeping, 'docker', ['rm', '-f', serverContainer(state.previousSlot)]).catch(() => {});
  rmSync(taskPath(housekeeping.taskId), { force: true });
  state.previousSlot = null;
  state.rollbackDeadline = null;
  writeDeploymentState(state);
}

async function resolveManifest(request) {
  if (request.manifest) return normalizeReleaseManifest(request.manifest);
  const env = parseEnvFile(envPath);
  const sourceUrl = env.PLATFORM_UPDATE_MANIFEST_URL || process.env.PLATFORM_UPDATE_MANIFEST_URL;
  if (sourceUrl) {
    const response = await probeHttp(sourceUrl, {
      timeoutMs: 10_000,
      headers: { Accept: 'application/vnd.github+json, application/json', 'User-Agent': 'lumira-updater-v2' },
    });
    if (!response.ok) throw new Error(`Unable to fetch release manifest: HTTP ${response.status}`);
    const root = JSON.parse(response.text);
    return normalizeReleaseManifest(typeof root.body === 'string' ? JSON.parse(root.body) : root);
  }
  return normalizeReleaseManifest({
    schemaVersion: 1,
    version: request.targetVersion,
    commit: request.targetCommit,
    serverImage: request.serverImage,
    frontendImage: request.frontendImage,
  });
}

function imageHostAllowed(image) {
  if (!image) return true;
  const normalized = image.toLowerCase();
  return allowedImagePrefixes.some((prefix) => normalized.startsWith(prefix));
}

async function commandAvailable(command, args) {
  try {
    await runCommand({ taskId: 'preflight', log: [], updatedAt: '' }, command, args);
    rmSync(taskPath('preflight'), { force: true });
    return true;
  } catch {
    rmSync(taskPath('preflight'), { force: true });
    return false;
  }
}

async function freeDiskBytes() {
  if (process.platform === 'win32' || dryRun) return 10 * 1024 ** 3;
  const task = { taskId: 'disk-probe', log: [], updatedAt: '' };
  try {
    const output = await runCommand(task, 'df', ['-Pk', deployDir]);
    const columns = output.trim().split(/\r?\n/).at(-1).trim().split(/\s+/);
    return Number(columns[3] || 0) * 1024;
  } finally {
    rmSync(taskPath(task.taskId), { force: true });
  }
}

async function proxyAvailable() {
  if (dryRun) return true;
  const task = { taskId: 'proxy-probe', log: [], updatedAt: '' };
  try {
    const output = await runCommand(task, 'docker', ['inspect', '-f', '{{.State.Running}}', containerName('api-proxy')]);
    return output.trim() === 'true';
  } catch {
    return false;
  } finally {
    rmSync(taskPath(task.taskId), { force: true });
  }
}

function backupDirectoryWritable() {
  if (dryRun) return true;
  const env = parseEnvFile(envPath);
  const directory = path.resolve(env.BACKUP_ROOT || process.env.BACKUP_ROOT || '/var/backups/lumira');
  const probe = path.join(directory, `.write-probe-${process.pid}`);
  try {
    mkdirSync(directory, { recursive: true });
    writeFileSync(probe, 'ok', { mode: 0o600 });
    rmSync(probe, { force: true });
    return true;
  } catch {
    rmSync(probe, { force: true });
    return false;
  }
}

async function runtimeTopologyStatus(state) {
  if (dryRun) return { slotRunning: true, databaseReachable: true, nginxValid: true, upstreamConsistent: true };
  const activeSlot = normalizeSlot(state.activeSlot);
  const container = serverContainer(activeSlot);
  const task = { taskId: 'topology-probe', log: [], updatedAt: '' };
  let slotRunning = false;
  let databaseReachable = false;
  let nginxValid = false;
  try {
    slotRunning = (await runCommand(task, 'docker', ['inspect', '-f', '{{.State.Running}}', container])).trim() === 'true';
    if (slotRunning) {
      const address = await containerAddress(task, container);
      databaseReachable = await slotHealthy(address, activeSlot);
    }
    await runCommand(task, 'docker', ['exec', containerName('api-proxy'), 'nginx', '-t']);
    nginxValid = true;
  } catch {
    // Individual flags become actionable blockers below.
  } finally {
    rmSync(taskPath(task.taskId), { force: true });
  }
  const upstreamConsistent = existsSync(upstreamPath)
    && readFileSync(upstreamPath, 'utf8').includes(`lumira-server-${activeSlot}:8080`);
  return { slotRunning, databaseReachable, nginxValid, upstreamConsistent };
}

async function migrationNetworkReachable(manifest, state) {
  if (dryRun || manifest.database.mode === 'none' || !manifest.images.migrator) return true;
  const env = parseEnvFile(envPath);
  const network = env.DB_MIGRATION_NETWORK || env.DB_BACKUP_NETWORK || 'deploy_default';
  const databaseHost = env.DB_URL?.match(/^jdbc:mysql:\/\/([^/:?]+)/i)?.[1];
  if (!databaseHost) return false;
  const task = { taskId: 'migration-network-probe', log: [], updatedAt: '' };
  try {
    const activeContainer = serverContainer(state.activeSlot);
    const activeImage = (await runCommand(task, 'docker', ['inspect', '-f', '{{.Image}}', activeContainer])).trim();
    const output = await runCommand(task, 'docker', [
      'run', '--rm', '--network', network, '--entrypoint', 'getent', activeImage, 'hosts', databaseHost,
    ]);
    return output.trim().length > 0;
  } catch {
    return false;
  } finally {
    rmSync(taskPath(task.taskId), { force: true });
  }
}

async function createPreflight(request) {
  const manifest = await resolveManifest(request);
  const state = deploymentState();
  const topology = await runtimeTopologyStatus(state);
  const report = buildPreflightReport({
    manifest,
    state,
    freeMemoryBytes: dryRun ? 2 * 1024 ** 3 : os.freemem(),
    freeDiskBytes: await freeDiskBytes(),
    dockerAvailable: await commandAvailable('docker', ['version', '--format', '{{.Server.Version}}']),
    composeAvailable: await commandAvailable('docker', ['compose', 'version']),
    proxyAvailable: await proxyAvailable(),
  });
  for (const image of Object.values(manifest.images)) {
    if (image && !imageHostAllowed(image)) report.blockers.push(`Image registry is not allowed: ${image}`);
  }
  if (!backupDirectoryWritable()) report.blockers.push('The backup directory is not writable.');
  const deploymentEnv = parseEnvFile(envPath);
  const databaseUsername = deploymentEnv.DB_USERNAME || 'root';
  if (databaseUsername.toLowerCase() === 'root') {
    report.warnings.push('The application database account is root; provision a least-privilege DB_USERNAME before the next credential rotation.');
  }
  if (!deploymentEnv.DB_MIGRATION_USERNAME || !deploymentEnv.DB_MIGRATION_PASSWORD) {
    report.warnings.push('Dedicated DB_MIGRATION_USERNAME/DB_MIGRATION_PASSWORD are not configured; migrations will fall back to the application database account.');
  }
  const databaseUrl = String(deploymentEnv.DB_URL || '');
  const databaseHost = databaseUrl.match(/^jdbc:mysql:\/\/([^/:?]+)/iu)?.[1]?.toLowerCase();
  const localDatabaseHosts = new Set(['mysql', 'localhost', '127.0.0.1', 'host.docker.internal']);
  if (databaseHost && !localDatabaseHosts.has(databaseHost)
      && !/[?&]sslMode=VERIFY_IDENTITY(?:&|$)/iu.test(databaseUrl)) {
    report.warnings.push('The external DB_URL does not enforce sslMode=VERIFY_IDENTITY; enable verified database TLS before HA cutover.');
  }
  if (!topology.slotRunning) report.blockers.push(`The active ${normalizeSlot(state.activeSlot)} slot is not running.`);
  if (!topology.databaseReachable) report.blockers.push('Database connectivity could not be verified through active-slot health.');
  if (!await migrationNetworkReachable(manifest, state)) report.blockers.push('The migration network cannot resolve the configured database host.');
  if (!topology.nginxValid) report.blockers.push('The API proxy Nginx configuration is invalid.');
  if (!topology.upstreamConsistent) report.blockers.push('The persisted active slot and Nginx upstream configuration are inconsistent.');
  report.ready = report.blockers.length === 0;
  const preflight = {
    ...report,
    preflightId: randomUUID(),
    manifest,
    manifestHash: createHash('sha256').update(JSON.stringify(manifest)).digest('hex'),
    expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
  };
  atomicWrite(preflightPath(preflight.preflightId), `${JSON.stringify(preflight, null, 2)}\n`);
  return preflight;
}

function requirePreflight(preflightId) {
  const preflight = readJson(preflightPath(preflightId));
  if (!preflight) throw new Error('Preflight result not found.');
  if (Date.parse(preflight.expiresAt) <= Date.now()) throw new Error('Preflight result has expired.');
  if (!preflight.ready) throw new Error(`Preflight failed: ${preflight.blockers.join(' ')}`);
  return preflight;
}

async function containerAddress(task, targetContainerName) {
  const targetOutput = await runCommand(task, 'docker', ['inspect', '-f', '{{json .NetworkSettings.Networks}}', targetContainerName]);
  const proxyOutput = await runCommand(task, 'docker', ['inspect', '-f', '{{json .NetworkSettings.Networks}}', containerName('api-proxy')]);
  const targetNetworks = JSON.parse(targetOutput);
  const proxyNetworks = JSON.parse(proxyOutput);
  const sharedNetwork = Object.keys(proxyNetworks).find((name) => targetNetworks[name]?.IPAddress);
  if (!sharedNetwork) throw new Error(`${targetContainerName} does not share a Docker network with ${containerName('api-proxy')}.`);
  return targetNetworks[sharedNetwork].IPAddress;
}

function slotBaseUrl(address, slot) {
  return process.env[`LUMIRA_SLOT_PROBE_URL_${normalizeSlot(slot).toUpperCase()}`] || `http://${address}:8080`;
}

async function slotHealthy(address, slot) {
  const baseUrl = slotBaseUrl(address, slot);
  for (const healthPath of ['/actuator/health/readiness', '/actuator/health']) {
    const health = await probeHttp(`${baseUrl}${healthPath}`, { timeoutMs: 3_000 });
    if (health.ok && health.text.includes('UP')) return true;
  }
  return false;
}

async function waitForSlot(task, slot, expectedCommit, timeoutMs = 240_000) {
  if (dryRun) return;
  const address = await containerAddress(task, serverContainer(slot));
  const baseUrl = slotBaseUrl(address, slot);
  const startedAt = Date.now();
  let lastIdentity = null;
  while (Date.now() - startedAt < timeoutMs) {
    if (await slotHealthy(address, slot)) {
      const version = await probeHttp(`${baseUrl}/api/v2/runtime/version`, { timeoutMs: 5_000 });
      lastIdentity = version.ok ? parseRuntimeVersionIdentity(version.text) : null;
      const commitMatches = !expectedCommit
        || (lastIdentity?.commitId
          && (lastIdentity.commitId.startsWith(expectedCommit) || expectedCommit.startsWith(lastIdentity.commitId)));
      if (version.ok && lastIdentity?.serviceName === 'lumira-server' && commitMatches) return;
    }
    await sleep(2_000);
  }
  const observed = lastIdentity
    ? `${lastIdentity.serviceName || 'unknown-service'}@${lastIdentity.commitId || 'unknown-commit'}`
    : 'no valid runtime identity';
  throw new Error(`lumira-server-${slot} did not become ready as lumira-server@${expectedCommit}; observed ${observed}.`);
}

function writeActiveUpstreams(slot) {
  const content = renderActiveUpstreams(slot);
  if (!dryRun) atomicWrite(upstreamPath, content, 0o644);
}

async function reloadProxy(task, slot) {
  const previous = existsSync(upstreamPath) ? readFileSync(upstreamPath, 'utf8') : null;
  writeActiveUpstreams(slot);
  try {
    await runCommand(task, 'docker', ['cp', upstreamPath, `${containerName('api-proxy')}:/etc/nginx/lumira-upstreams/active-upstreams.conf`])
      .catch(() => appendLog(task, 'API proxy uses the read-only bind-mounted upstream file.'));
    await runCommand(task, 'docker', ['exec', containerName('api-proxy'), 'nginx', '-t']);
    await runCommand(task, 'docker', ['exec', containerName('api-proxy'), 'nginx', '-s', 'reload']);
  } catch (error) {
    if (!dryRun && previous !== null) atomicWrite(upstreamPath, previous, 0o644);
    throw error;
  }
}

async function verifyPublicTraffic(task, expectedCommit) {
  if (dryRun) return;
  const env = parseEnvFile(envPath);
  const baseUrl = process.env.DEPLOY_CHECK_BASE_URL || (env.API_DOMAIN ? `https://${env.API_DOMAIN}` : 'http://127.0.0.1:8000');
  const startedAt = Date.now();
  while (Date.now() - startedAt < 10_000) {
    const health = await probeHttp(`${baseUrl}/api/health`, { timeoutMs: 3_000 });
    const version = await probeHttp(`${baseUrl}/api/v2/runtime/version`, { timeoutMs: 3_000 });
    if (health.ok && version.ok && version.text.includes(expectedCommit.slice(0, 12))) return;
    await sleep(500);
  }
  throw new Error(`Public traffic did not switch to ${expectedCommit}.`);
}

function checkCancellation(task, { afterSwitch = false } = {}) {
  const current = readTask(task.taskId);
  if (!current?.cancelRequested) return;
  if (afterSwitch) {
    task.rollbackRequested = true;
    appendLog(task, 'Cancellation was requested after traffic switch; rolling back instead.');
    return;
  }
  const error = new Error('Update cancelled before traffic switch.');
  error.code = 'UPDATE_CANCELLED';
  throw error;
}

async function migrate(task, manifest) {
  if (manifest.database.mode === 'none' || !manifest.images.migrator) return;
  const env = parseEnvFile(envPath);
  const network = env.DB_MIGRATION_NETWORK || env.DB_BACKUP_NETWORK || 'deploy_default';
  const configuredSecretPath = String(env.LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE || '').trim();
  const secretArgs = [];
  if (configuredSecretPath) {
    const absoluteSecretPath = path.resolve(deployDir, configuredSecretPath);
    if (!existsSync(absoluteSecretPath)) {
      appendLog(task, `Administrator bootstrap secret is not mounted because the configured file is absent: ${absoluteSecretPath}`);
    } else {
      secretArgs.push(
        '--volume', `${absoluteSecretPath}:${bootstrapAdminSecretContainerPath}:ro`,
        '-e', `LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE=${bootstrapAdminSecretContainerPath}`,
      );
    }
  }
  await runCommand(task, 'docker', [
    'run', '--rm', '--network', network,
    '-e', 'DB_URL', '-e', 'DB_USERNAME', '-e', 'DB_PASSWORD',
    ...secretArgs,
    '-e', `DATABASE_TARGET_VERSION=${manifest.database.targetVersion}`,
    manifest.images.migrator,
  ], {
    env: {
      DB_URL: env.DB_URL || '',
      DB_USERNAME: env.DB_MIGRATION_USERNAME || env.DB_USERNAME || 'root',
      DB_PASSWORD: env.DB_MIGRATION_PASSWORD || env.DB_PASSWORD || '',
      WSLENV: wslForwardedEnvironment(['DB_URL', 'DB_USERNAME', 'DB_PASSWORD']),
    },
  });
}

async function verifyBackupMatchesMigrationTarget(task, evidence, environment) {
  const backupServerUuid = String(evidence?.serverUuid || '').trim().toLowerCase();
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u.test(backupServerUuid)) {
    throw new Error('Verified backup evidence does not contain a valid MySQL server UUID.');
  }
  const { host: databaseHost, port: databasePort } = configuredDatabaseEndpoint(environment);
  const network = environment.DB_MIGRATION_NETWORK || environment.DB_BACKUP_NETWORK || 'deploy_default';
  const username = environment.DB_MIGRATION_USERNAME || environment.DB_USERNAME || 'root';
  const password = environment.DB_MIGRATION_PASSWORD || environment.DB_PASSWORD || '';
  const clientImage = environment.MYSQL_CLIENT_IMAGE || 'mysql:8.4';
  const sslMode = configuredMysqlSslMode(environment);
  const dockerArgs = ['run', '--rm', '--network', network, '-e', 'MYSQL_PWD'];
  const configuredCaPath = String(environment.MYSQL_SSL_CA_FILE || '').trim();
  if (configuredCaPath) {
    const absoluteCaPath = path.resolve(repoRoot, configuredCaPath);
    if (!existsSync(absoluteCaPath)) throw new Error(`MYSQL_SSL_CA_FILE does not exist: ${absoluteCaPath}`);
    dockerArgs.push('-v', `${absoluteCaPath}:/run/lumira/mysql-ca.pem:ro`);
  }
  dockerArgs.push(
    clientImage,
    'mysql', '--batch', '--skip-column-names', '--connect-timeout=10',
    `--host=${databaseHost}`, `--port=${databasePort}`, `--user=${username}`, `--ssl-mode=${sslMode}`,
    ...(configuredCaPath ? ['--ssl-ca=/run/lumira/mysql-ca.pem'] : []),
    '--execute=SELECT @@server_uuid;',
  );
  const output = await runCommand(task, 'docker', dockerArgs, {
    env: {
      MYSQL_PWD: password,
      WSLENV: wslForwardedEnvironment(['MYSQL_PWD']),
    },
  });
  const targetServerUuid = String(output || '').trim().split(/\r?\n/u).filter(Boolean).at(-1)?.toLowerCase() || '';
  if (targetServerUuid !== backupServerUuid) {
    throw new Error(
      `Backup server UUID ${backupServerUuid} does not match migration target UUID ${targetServerUuid || 'unknown'}; migrations were not started.`,
    );
  }
  appendLog(task, `Backup evidence is bound to migration target MySQL server UUID ${targetServerUuid}.`);
}

async function updateWorker(task, service, imageKey, image) {
  if (!image) return;
  appendLog(task, `Pausing new work and allowing ${service} in-flight work to drain.`);
  const workerContainer = containerName(service);
  await runCommand(task, 'docker', ['stop', '--time', '60', workerContainer]).catch((error) => appendLog(task, `${service} was already stopped: ${error.message}`));
  // Fixed container names may have been created by an older Compose project.
  // Removing the stopped container avoids a name conflict while preserving the
  // locally cached image as the fast rollback target.
  await runCommand(task, 'docker', ['rm', '-f', workerContainer]).catch((error) => appendLog(task, `${service} cleanup warning: ${error.message}`));
  updateEnv({ [imageKey]: image });
  await runCompose(task, 'up', '-d', '--no-deps', '--force-recreate', service);
}

async function containerIsRunning(task, containerName) {
  try {
    return (await runCommand(task, 'docker', ['inspect', '-f', '{{.State.Running}}', containerName])).trim() === 'true';
  } catch {
    return false;
  }
}

async function imageIsPresent(task, image) {
  try {
    await runCommand(task, 'docker', ['image', 'inspect', image]);
    return true;
  } catch {
    return false;
  }
}

async function rollbackTraffic(task, state, failedSlot) {
  const previousSlot = normalizeSlot(state.activeSlot);
  const previousRelease = state.slots?.[previousSlot] || {};
  updateEnv({
    APP_VERSION: previousRelease.version,
    BUILD_VERSION: previousRelease.buildVersion || previousRelease.version,
    FRONTEND_VERSION: previousRelease.frontendVersion || previousRelease.version,
    BACKEND_VERSION: previousRelease.backendVersion || previousRelease.buildVersion || previousRelease.version,
    BUILD_TIME: previousRelease.buildTime,
    GIT_COMMIT: previousRelease.commit,
    GIT_BRANCH: previousRelease.branch,
    DATABASE_VERSION: previousRelease.databaseVersion,
  });
  await runCompose(task, '--profile', previousSlot, 'up', '-d', '--no-deps', `lumira-server-${previousSlot}`);
  await waitForSlot(task, previousSlot, state.slots?.[previousSlot]?.commit || '');
  await reloadProxy(task, previousSlot);
  if (UPDATE_PHASES.indexOf(task.phase) >= UPDATE_PHASES.indexOf('UPDATING_WORKERS')) {
    await updateWorker(task, 'lumira-async', 'LUMIRA_ASYNC_IMAGE', state.workers?.asyncImage);
    await updateWorker(task, 'lumira-job-executor', 'LUMIRA_JOB_EXECUTOR_IMAGE', state.workers?.jobExecutorImage);
  }
  if (failedSlot) {
    appendLog(task, `Draining Nginx workers from failed ${failedSlot} slot before stopping it.`);
    if (!dryRun) await sleep(rollbackDrainSeconds * 1000);
    await runCommand(task, 'docker', ['stop', '--time', '60', serverContainer(failedSlot)]).catch(() => {});
  }
}

async function honorPostSwitchCancellation(task, state, failedSlot) {
  checkCancellation(task, { afterSwitch: true });
  if (!task.rollbackRequested) return false;
  await rollbackTraffic(task, state, failedSlot);
  task.status = 'ROLLED_BACK';
  return true;
}

async function runInstall(task, request) {
  const preflight = request.preflightId ? requirePreflight(request.preflightId) : await createPreflight(request);
  if (!preflight.ready) throw new Error(`Preflight failed: ${preflight.blockers.join(' ')}`);
  const manifest = preflight.manifest;
  const targetBuildTime = manifest.releasedAt || new Date().toISOString();
  const targetFrontendVersion = `${manifest.version}+${manifest.commit.slice(0, 12)}`;
  const targetBackendVersion = `${manifest.version}+${manifest.commit.slice(0, 12)}`;
  const state = deploymentState();
  const activeSlot = normalizeSlot(state.activeSlot);
  const targetSlot = inactiveSlot(activeSlot);
  task.preflightId = preflight.preflightId;
  task.manifestHash = preflight.manifestHash;
  task.activeSlot = activeSlot;
  task.targetSlot = targetSlot;
  task.targetCommit = manifest.commit;
  task.targetVersion = manifest.version;
  task.serverImage = manifest.images.server;
  writeTask(task);

  let switched = false;
  let localFrontendRunning = false;
  try {
    setPhase(task, 'BACKUP', 'Creating a platform backup before the online update.');
    const deploymentEnv = parseEnvFile(envPath);
    const backupEndpoint = configuredDatabaseEndpoint(deploymentEnv);
    const backupDatabaseName = configuredDatabaseName(deploymentEnv);
    const effectiveBackupEnvironment = {
      ...deploymentEnv,
      DB_HOST: backupEndpoint.host,
      DB_PORT: backupEndpoint.port,
      ...(backupDatabaseName ? { MYSQL_DATABASE: backupDatabaseName, DB_NAME: backupDatabaseName } : {}),
    };
    const backupForwardedVariables = [
      'BACKUP_ROOT', 'BACKUP_ID', 'BACKUP_RETENTION_DAYS', 'BACKUP_UPLOAD_HOOK', 'BACKUP_METRICS_FILE',
      'BACKUP_ALLOW_EMPTY_DATABASE', 'BACKUP_REDIS', 'BACKUP_MYSQL_READY_ATTEMPTS',
      'BACKUP_MYSQL_READY_INTERVAL_SECONDS', 'BACKUP_REDIS_READY_ATTEMPTS', 'BACKUP_REDIS_READY_INTERVAL_SECONDS',
      'MYSQL_BACKUP_USERNAME', 'MYSQL_BACKUP_PASSWORD', 'MYSQL_CLIENT_IMAGE', 'MYSQL_SERVICE', 'REDIS_SERVICE',
      'DB_BACKUP_NETWORK', 'DB_URL', 'DB_HOST', 'DB_PORT', 'MYSQL_DATABASE', 'DB_NAME', 'DB_USERNAME', 'DB_USER',
      'DB_PASSWORD', 'MYSQL_SSL_MODE', 'MYSQL_SSL_CA_FILE', 'REDIS_HOST', 'REDIS_PORT', 'REDIS_PASSWORD',
    ];
    const backupOutput = await runCommand(task, 'bash', [path.join(deployDir, 'backup-platform.sh')], {
      env: {
        ...effectiveBackupEnvironment,
        WSLENV: wslForwardedEnvironment(
          backupForwardedVariables,
          ['BACKUP_ROOT', 'BACKUP_UPLOAD_HOOK', 'BACKUP_METRICS_FILE', 'MYSQL_SSL_CA_FILE'],
        ),
      },
    });
    if (!dryRun) {
      const reportedPath = task.backupPath
        || String(backupOutput || '').match(/Backup completed:\s*(.+)$/mu)?.[1]?.trim();
      const configuredEvidenceAgeMs = Number(deploymentEnv.BACKUP_EVIDENCE_MAX_AGE_MS || 6 * 60 * 60_000);
      const maximumEvidenceAgeMs = Number.isFinite(configuredEvidenceAgeMs) && configuredEvidenceAgeMs > 0
        ? Math.max(15 * 60_000, configuredEvidenceAgeMs)
        : 6 * 60 * 60_000;
      const backupEvidence = validateBackupEvidence(toHostReadablePath(reportedPath), {
        maxAgeMs: maximumEvidenceAgeMs,
        expectedDatabaseName: configuredDatabaseName(deploymentEnv),
      });
      task.backupPath = path.dirname(backupEvidence.manifestPath);
      task.backupEvidence = {
        backupId: backupEvidence.backupId,
        createdAt: backupEvidence.createdAt,
        databaseName: backupEvidence.databaseName,
        dumpSize: backupEvidence.dumpSize,
        dumpSha256: backupEvidence.dumpSha256,
        serverVersion: backupEvidence.serverVersion,
        serverUuid: backupEvidence.serverUuid,
        gtidExecuted: backupEvidence.gtidExecuted,
        binlogFile: backupEvidence.binlogFile,
        binlogPosition: backupEvidence.binlogPosition,
        databaseVersion: backupEvidence.databaseVersion,
        tableCount: backupEvidence.tableCount,
        schemaFingerprint: backupEvidence.schemaFingerprint,
      };
      appendLog(task, `Backup evidence verified: ${backupEvidence.backupId} (${backupEvidence.dumpSize} bytes).`);
      if (manifest.database.mode !== 'none' && manifest.images.migrator) {
        await verifyBackupMatchesMigrationTarget(task, backupEvidence, deploymentEnv);
      }
    }
    checkCancellation(task);

    setPhase(task, 'PULLING', 'Pulling digest-pinned release images.');
    localFrontendRunning = Boolean(manifest.images.frontend) && await containerIsRunning(task, containerName('ui'));
    const releaseImages = [manifest.images.server, manifest.images.async, manifest.images.jobExecutor, manifest.images.migrator];
    if (localFrontendRunning) releaseImages.push(manifest.images.frontend);
    for (const image of new Set(releaseImages.filter(Boolean))) {
      if (skipPullIfPresent && await imageIsPresent(task, image)) appendLog(task, `Using locally cached digest-pinned image ${image}.`);
      else await runCommand(task, 'docker', ['pull', image], {
        noProgressTimeoutMs: pullNoProgressTimeoutMs,
        timeoutMs: pullTimeoutMs,
      });
    }
    checkCancellation(task);

    setPhase(task, 'MIGRATING', 'Applying expand-only database migrations.');
    await migrate(task, manifest);
    checkCancellation(task);

    setPhase(task, 'STARTING_INACTIVE', `Starting inactive ${targetSlot} slot.`);
    updateEnv({
      [`LUMIRA_SERVER_${targetSlot.toUpperCase()}_IMAGE`]: manifest.images.server,
      [`LUMIRA_SERVER_${targetSlot.toUpperCase()}_APP_VERSION`]: manifest.version,
      [`LUMIRA_SERVER_${targetSlot.toUpperCase()}_BUILD_VERSION`]: `${manifest.version}+${manifest.commit}`,
      [`LUMIRA_SERVER_${targetSlot.toUpperCase()}_FRONTEND_VERSION`]: targetFrontendVersion,
      [`LUMIRA_SERVER_${targetSlot.toUpperCase()}_BACKEND_VERSION`]: targetBackendVersion,
      [`LUMIRA_SERVER_${targetSlot.toUpperCase()}_BUILD_TIME`]: targetBuildTime,
      [`LUMIRA_SERVER_${targetSlot.toUpperCase()}_GIT_COMMIT`]: manifest.commit,
      [`LUMIRA_SERVER_${targetSlot.toUpperCase()}_GIT_BRANCH`]: 'main',
      [`LUMIRA_SERVER_${targetSlot.toUpperCase()}_DATABASE_VERSION`]: manifest.database.targetVersion,
      APP_VERSION: manifest.version,
      BUILD_VERSION: `${manifest.version}+${manifest.commit}`,
      FRONTEND_VERSION: targetFrontendVersion,
      BACKEND_VERSION: targetBackendVersion,
      BUILD_TIME: targetBuildTime,
      GIT_COMMIT: manifest.commit,
      GIT_BRANCH: 'main',
      DATABASE_VERSION: manifest.database.targetVersion,
    });
    await runCompose(task, '--profile', targetSlot, 'up', '-d', '--no-deps', '--force-recreate', `lumira-server-${targetSlot}`);

    setPhase(task, 'VERIFYING_INACTIVE', `Verifying ${targetSlot} readiness and build identity.`);
    await waitForSlot(task, targetSlot, manifest.commit);
    checkCancellation(task);

    setPhase(task, 'SWITCHING_TRAFFIC', `Hot switching API traffic from ${activeSlot} to ${targetSlot}.`);
    await reloadProxy(task, targetSlot);
    switched = true;

    setPhase(task, 'VERIFYING_ACTIVE', 'Verifying public traffic after the Nginx reload.');
    await verifyPublicTraffic(task, manifest.commit);
    if (await honorPostSwitchCancellation(task, state, targetSlot)) return;

    setPhase(task, 'DRAINING_OLD', `Gracefully draining old ${activeSlot} slot.`);
    if (!dryRun) await sleep(manifest.drainTimeoutSeconds * 1000);
    await runCommand(task, 'docker', ['stop', '--time', '10', serverContainer(activeSlot)]).catch((error) => appendLog(task, `Old slot stop warning: ${error.message}`));
    if (await honorPostSwitchCancellation(task, state, targetSlot)) return;

    setPhase(task, 'UPDATING_WORKERS', 'Replacing async and job workers serially.');
    await updateWorker(task, 'lumira-async', 'LUMIRA_ASYNC_IMAGE', manifest.images.async);
    if (await honorPostSwitchCancellation(task, state, targetSlot)) return;
    await updateWorker(task, 'lumira-job-executor', 'LUMIRA_JOB_EXECUTOR_IMAGE', manifest.images.jobExecutor);
    if (localFrontendRunning) {
      await updateWorker(task, 'lumira-ui', 'LUMIRA_FRONTEND_IMAGE', manifest.images.frontend);
    }
    if (await honorPostSwitchCancellation(task, state, targetSlot)) return;

    setPhase(task, 'FINALIZING', 'Persisting the active slot and rollback window.');
    state.previousSlot = activeSlot;
    state.activeSlot = targetSlot;
    state.rollbackDeadline = new Date(Date.now() + manifest.rollbackWindowSeconds * 1000).toISOString();
    state.slots[targetSlot] = {
      commit: manifest.commit,
      version: manifest.version,
      buildVersion: `${manifest.version}+${manifest.commit}`,
      frontendVersion: targetFrontendVersion,
      backendVersion: targetBackendVersion,
      buildTime: targetBuildTime,
      branch: 'main',
      databaseVersion: manifest.database.targetVersion,
      serverImage: manifest.images.server,
      activatedAt: new Date().toISOString(),
    };
    state.previousWorkers = state.workers || null;
    state.workers = { asyncImage: manifest.images.async, jobExecutorImage: manifest.images.jobExecutor };
    state.lastSuccessfulTaskId = task.taskId;
    state.lastSuccessfulPlatformTaskId = task.platformTaskId || null;
    writeDeploymentState(state);
    updateEnv({ LUMIRA_ACTIVE_SLOT: targetSlot, LUMIRA_SERVER_IMAGE: manifest.images.server });
  } catch (error) {
    if (switched) {
      try {
        await rollbackTraffic(task, state, targetSlot);
        task.status = 'ROLLED_BACK';
        task.errorMessage = `Update failed after traffic switch and was rolled back: ${error instanceof Error ? error.message : String(error)}`;
        appendLog(task, task.errorMessage);
        return;
      } catch (rollbackError) {
        appendLog(task, `Automatic traffic rollback failed: ${rollbackError.message}`);
      }
    } else {
      await runCommand(task, 'docker', ['rm', '-f', serverContainer(targetSlot)]).catch(() => {});
    }
    throw error;
  }
}

async function runRollback(task) {
  const state = deploymentState();
  const currentSlot = normalizeSlot(state.activeSlot);
  const previousSlot = state.previousSlot ? normalizeSlot(state.previousSlot) : null;
  if (!previousSlot || !state.slots?.[previousSlot]) throw new Error('No previous blue-green slot is available for rollback.');
  if (state.rollbackDeadline && Date.parse(state.rollbackDeadline) <= Date.now()) throw new Error('The fast rollback window has expired.');
  task.activeSlot = currentSlot;
  task.targetSlot = previousSlot;
  task.targetVersion = state.slots[previousSlot].version;
  task.targetCommit = state.slots[previousSlot].commit;
  task.serverImage = state.slots[previousSlot].serverImage;
  task.rollbackOfTaskId = state.lastSuccessfulPlatformTaskId || null;
  updateEnv({
    APP_VERSION: state.slots[previousSlot].version,
    BUILD_VERSION: state.slots[previousSlot].buildVersion || state.slots[previousSlot].version,
    FRONTEND_VERSION: state.slots[previousSlot].frontendVersion || state.slots[previousSlot].version,
    BACKEND_VERSION: state.slots[previousSlot].backendVersion || state.slots[previousSlot].buildVersion || state.slots[previousSlot].version,
    BUILD_TIME: state.slots[previousSlot].buildTime,
    GIT_COMMIT: state.slots[previousSlot].commit,
    GIT_BRANCH: state.slots[previousSlot].branch,
    DATABASE_VERSION: state.slots[previousSlot].databaseVersion,
  });
  setPhase(task, 'STARTING_INACTIVE', `Restarting previous ${previousSlot} slot.`);
  await runCompose(task, '--profile', previousSlot, 'up', '-d', '--no-deps', `lumira-server-${previousSlot}`);
  setPhase(task, 'VERIFYING_INACTIVE', `Verifying previous ${previousSlot} slot.`);
  await waitForSlot(task, previousSlot, state.slots[previousSlot].commit);
  setPhase(task, 'SWITCHING_TRAFFIC', `Hot switching traffic back to ${previousSlot}.`);
  await reloadProxy(task, previousSlot);
  setPhase(task, 'VERIFYING_ACTIVE', 'Verifying public traffic after rollback.');
  await verifyPublicTraffic(task, state.slots[previousSlot].commit);
  setPhase(task, 'DRAINING_OLD', `Draining rolled-back ${currentSlot} slot.`);
  if (!dryRun) await sleep(rollbackDrainSeconds * 1000);
  await runCommand(task, 'docker', ['stop', '--time', '10', serverContainer(currentSlot)]).catch(() => {});
  setPhase(task, 'UPDATING_WORKERS', 'Restoring the previous compatible worker images.');
  const currentWorkers = state.workers || null;
  await updateWorker(task, 'lumira-async', 'LUMIRA_ASYNC_IMAGE', state.previousWorkers?.asyncImage);
  await updateWorker(task, 'lumira-job-executor', 'LUMIRA_JOB_EXECUTOR_IMAGE', state.previousWorkers?.jobExecutorImage);
  state.activeSlot = previousSlot;
  state.previousSlot = currentSlot;
  state.workers = state.previousWorkers || state.workers;
  state.previousWorkers = currentWorkers;
  state.rollbackDeadline = new Date(Date.now() + 1800_000).toISOString();
  writeDeploymentState(state);
  updateEnv({ LUMIRA_ACTIVE_SLOT: previousSlot, LUMIRA_SERVER_IMAGE: state.slots[previousSlot].serverImage });
}

function startTask(type, request) {
  const existing = findTaskByPlatformTaskId(request.platformTaskId, request.platformTaskCreatedAt);
  if (existing) {
    if (existing.type !== type) {
      throw new Error('Platform task id is already bound to a different update operation.');
    }
    const requestedTargetCommit = String(request.targetCommit || request.manifest?.commit || '').trim();
    if (type === 'INSTALL' && requestedTargetCommit && existing.targetCommit
      && requestedTargetCommit.toLowerCase() !== String(existing.targetCommit).trim().toLowerCase()) {
      throw new Error('Platform task id is already bound to a different target commit.');
    }
    return existing;
  }
  const task = {
    taskId: randomUUID(),
    platformTaskId: request.platformTaskId,
    platformTaskCreatedAt: request.platformTaskCreatedAt,
    type,
    strategy: UPDATE_STRATEGY,
    status: 'RUNNING',
    phase: 'PREFLIGHT',
    progressPercent: phaseProgress('PREFLIGHT'),
    message: 'Task accepted',
    targetCommit: request.targetCommit || request.manifest?.commit || null,
    targetVersion: request.targetVersion || request.manifest?.version || null,
    log: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
  acquireLock(task.taskId);
  writeTask(task);
  setImmediate(async () => {
    try {
      if (type === 'INSTALL') await runInstall(task, request);
      else await runRollback(task);
      if (!TERMINAL_UPDATE_STATUSES.has(task.status)) task.status = type === 'ROLLBACK' ? 'ROLLED_BACK' : 'SUCCEEDED';
      task.progressPercent = 100;
      appendLog(task, 'Task completed');
    } catch (error) {
      task.status = error?.code === 'UPDATE_CANCELLED' ? 'CANCELLED' : 'FAILED';
      task.errorMessage = error instanceof Error ? error.message : String(error);
      appendLog(task, task.errorMessage);
    } finally {
      task.finishedAt = new Date().toISOString();
      task.updatedAt = new Date().toISOString();
      writeTask(task);
      releaseLock(task.taskId);
    }
  });
  return task;
}

function cancelTask(taskId) {
  const task = readTask(taskId);
  if (!task) return null;
  if (TERMINAL_UPDATE_STATUSES.has(task.status)) return task;
  task.cancelRequested = true;
  task.message = UPDATE_PHASES.indexOf(task.phase) >= UPDATE_PHASES.indexOf('SWITCHING_TRAFFIC')
    ? 'Rollback requested after traffic switch'
    : 'Cancellation requested';
  writeTask(task);
  const error = new Error('Update cancelled by administrator.');
  error.code = 'UPDATE_CANCELLED';
  activeCommandControllers.get(taskId)?.cancel(error);
  return task;
}

function authorized(req) {
  if (!token) {
    return false;
  }
  return req.headers['x-lumira-updater-token'] === token;
}

const server = http.createServer(async (req, res) => {
  try {
    if (!authorized(req)) return json(res, 401, { errorMessage: 'Unauthorized' });
    if (req.method === 'GET' && req.url === '/v1/health') return json(res, 200, { status: 'UP', dryRun, protocolVersion: UPDATER_PROTOCOL_VERSION });
    if (req.method === 'GET' && req.url === '/v1/capabilities') return json(res, 200, {
      status: 'UP',
      protocolVersion: UPDATER_PROTOCOL_VERSION,
      strategy: UPDATE_STRATEGY,
      activeSlot: deploymentState().activeSlot,
      supportsPreflight: true,
      supportsCancel: true,
      supportsPlatformTaskLookup: true,
      supportsExpandOnlyMigration: true,
    });
    if (req.method === 'POST' && req.url === '/v1/update/preflight') return json(res, 200, await createPreflight(await readBody(req)));
    if (req.method === 'POST' && req.url === '/v1/update/install') return json(res, 202, startTask('INSTALL', await readBody(req)));
    if (req.method === 'POST' && req.url === '/v1/update/rollback') return json(res, 202, startTask('ROLLBACK', await readBody(req)));
    const requestUrl = new URL(req.url || '/', 'http://localhost');
    const platformTaskMatch = requestUrl.pathname.match(/^\/v1\/update\/platform-tasks\/([1-9][0-9]*)$/);
    if (req.method === 'GET' && platformTaskMatch) {
      const platformTaskCreatedAt = requestUrl.searchParams.get('createdAt');
      if (!platformTaskCreatedAt) {
        return json(res, 400, { errorMessage: 'createdAt is required' });
      }
      const task = findTaskByPlatformTaskId(platformTaskMatch[1], platformTaskCreatedAt);
      return task ? json(res, 200, task) : json(res, 404, { errorMessage: 'Task not found' });
    }
    const cancelMatch = req.url?.match(/^\/v1\/update\/tasks\/([^/]+)\/cancel$/);
    if (req.method === 'POST' && cancelMatch) {
      const task = cancelTask(cancelMatch[1]);
      return task ? json(res, 202, task) : json(res, 404, { errorMessage: 'Task not found' });
    }
    const taskMatch = req.url?.match(/^\/v1\/update\/tasks\/([^/]+)$/);
    if (req.method === 'GET' && taskMatch) {
      const task = readTask(taskMatch[1]);
      return task ? json(res, 200, task) : json(res, 404, { errorMessage: 'Task not found' });
    }
    return json(res, 404, { errorMessage: 'Not found' });
  } catch (error) {
    const conflict = String(error?.message || '').includes('already running');
    return json(res, conflict ? 409 : 500, { errorMessage: error instanceof Error ? error.message : String(error) });
  }
});

server.listen(port, host, () => {
  console.log(`[lumira-updater] listening on http://${host}:${port} protocol=${UPDATER_PROTOCOL_VERSION} strategy=${UPDATE_STRATEGY} dryRun=${dryRun}`);
  if (!dryRun) atomicWrite(upstreamPath, renderActiveUpstreams(deploymentState().activeSlot), 0o644);
  void recoverInterruptedTask();
  void cleanupExpiredRollbackSlot();
  const cleanupTimer = setInterval(() => void cleanupExpiredRollbackSlot(), 60_000);
  cleanupTimer.unref();
});
