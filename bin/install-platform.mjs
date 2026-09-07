#!/usr/bin/env node

import { randomBytes } from 'node:crypto';
import { chmodSync, existsSync, lstatSync, mkdirSync, readFileSync, renameSync, rmSync, statfsSync, writeFileSync } from 'node:fs';
import net from 'node:net';
import { arch, cpus, platform, release, totalmem } from 'node:os';
import path from 'node:path';
import process from 'node:process';
import readline from 'node:readline/promises';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

import { parseEnvFile, setEnvValue, randomSecret, defaultCapacityProfiles } from './lib/env-utils.mjs';
import { run as execRun, output as execOutput, commandExists, createLogger, resolveRepoRoot } from './lib/exec-utils.mjs';
import { waitForHttp, probeHttp } from './lib/http-utils.mjs';
import { renderActiveUpstreams } from './lib/platform-update-contract.mjs';
import { assertProductionDataPlaneEnvironment } from './lib/production-data-plane-policy.mjs';
const log = createLogger('install');
const repoRoot = resolveRepoRoot(import.meta.url);
const envExamplePath = path.join(repoRoot, 'deploy', '.env.example');
const envPath = path.join(repoRoot, 'deploy', '.env');
const composeFile = path.join(repoRoot, 'deploy', 'docker-compose.prod.yml');
const edgeTlsDir = path.join(repoRoot, 'deploy', 'data', 'tls');
const edgeTlsFiles = ['fullchain.pem', 'privkey.pem'];
const defaultMysqlExporterSecretPath = path.join(repoRoot, 'deploy', '.generated', 'secrets', 'mysql-exporter-password');
const generatedBackupMetricsDir = path.join(repoRoot, 'deploy', '.generated', 'backup-metrics');

const rawArgs = process.argv.slice(2);
const argMap = parseArgs(rawArgs);
const auto = argMap.has('yes') || argMap.has('auto') || process.env.CI === 'true';
const dryRun = argMap.has('dry-run');
const skipDockerInstall = argMap.has('skip-docker-install');
const skipBuild = argMap.has('skip-build');
const skipSmoke = argMap.has('skip-smoke');
const noStart = argMap.has('no-start');
const checkOnly = argMap.has('check-only') || argMap.has('check');
const jsonOutput = argMap.has('json');
const strict = argMap.has('strict');
const skipNetwork = argMap.has('skip-network');

const environmentMinimums = {
  cpu: 4,
  memoryGb: 3.5,
  diskGb: 15,
  nodeMajor: 20,
  dockerMajor: 24,
};


function parseArgs(argv) {
  const values = new Map();
  for (const arg of argv) {
    if (!arg.startsWith('--')) {
      continue;
    }
    const normalized = arg.slice(2);
    const separator = normalized.indexOf('=');
    if (separator === -1) {
      values.set(normalized, 'true');
      continue;
    }
    values.set(normalized.slice(0, separator), normalized.slice(separator + 1));
  }
  return values;
}

function run(command, commandArgs, options = {}) {
  try {
    return execRun(command, commandArgs, { cwd: repoRoot, ...options });
  } catch (err) {
    process.exit(err.status ?? 1);
  }
}
function output(command, commandArgs, options = {}) {
  return execOutput(command, commandArgs, { cwd: repoRoot, check: false, ...options });
}





function generatedSecrets() {
  return {
    MYSQL_ROOT_PASSWORD: randomSecret('mysql-root'),
    DB_PASSWORD: randomSecret('mysql'),
    DB_MIGRATION_PASSWORD: randomSecret('mysql-migrator'),
    MYSQL_BACKUP_PASSWORD: randomSecret('mysql-backup'),
    MYSQL_RESTORE_PASSWORD: randomSecret('mysql-restore'),
    XXL_JOB_DB_PASSWORD: randomSecret('xxl-database'),
    REDIS_CACHE_PASSWORD: randomSecret('redis-cache'),
    REDIS_RUNTIME_PASSWORD: randomSecret('redis-runtime'),
    JWT_SECRET: randomSecret('jwt'),
    FIELD_SECRET: randomSecret('field'),
    PLUGIN_SIGNATURE_SECRET: randomSecret('plugin-signature'),
    SAAS_INTERNAL_SYSTEM_TOKEN: randomSecret('system-token'),
    SAAS_INTERNAL_AUTH_TOKEN: randomSecret('auth-token'),
    SAAS_INTERNAL_AUTH_SYSTEM_TOKEN: randomSecret('auth-system-token'),
    SAAS_INTERNAL_FILE_TOKEN: randomSecret('file-token'),
    SAAS_INTERNAL_MESSAGE_TOKEN: randomSecret('message-token'),
    SAAS_INTERNAL_PAYMENT_TOKEN: randomSecret('payment-token'),
    SAAS_INTERNAL_PLUGIN_TOKEN: randomSecret('plugin-token'),
    SAAS_INTERNAL_TEAM_TOKEN: randomSecret('team-token'),
    SAAS_INTERNAL_JOB_TOKEN: randomSecret('job-token'),
    PLATFORM_UPDATE_AGENT_TOKEN: randomSecret('updater-token'),
    XXL_JOB_ADMIN_ACCESS_TOKEN: randomSecret('xxl-token'),
    XXL_JOB_ACCESS_TOKEN: randomSecret('xxl-token'),
    XXL_JOB_LOGIN_PASSWORD: randomSecret('xxl-password'),
    GRAFANA_ADMIN_PASSWORD: randomSecret('grafana'),
    MYSQLD_EXPORTER_PASSWORD: randomSecret('mysql-exporter'),
  };
}

function detectCapacity() {
  const cpuCount = cpus().length;
  const memoryGb = totalmem() / 1024 / 1024 / 1024;
  const diskGb = diskFreeGb(repoRoot);
  const profileName = memoryGb <= 5 || cpuCount <= 4 ? 'tiny' : 'standard';
  return {
    cpuCount,
    memoryGb,
    diskGb,
    platform: platform(),
    arch: arch(),
    profileName,
  };
}

function diskFreeGb(targetPath) {
  try {
    const stats = statfsSync(targetPath);
    const availableBytes = Number(stats.bavail) * Number(stats.bsize);
    if (Number.isFinite(availableBytes) && availableBytes > 0) {
      return availableBytes / 1024 / 1024 / 1024;
    }
  } catch {
    // Fall back to df when statfs is unavailable or unsupported.
  }

  const result = output('df', ['-Pk', targetPath], { check: false });
  if (!result) {
    return 0;
  }
  const columns = result.split(/\r?\n/).at(-1)?.trim().split(/\s+/);
  return columns?.[3] ? Number(columns[3]) / 1024 / 1024 : 0;
}

function parseMajorVersion(text) {
  const match = String(text || '').match(/(\d+)\./);
  return match ? Number(match[1]) : 0;
}

function parseDbEndpoint(dbUrl) {
  if (!dbUrl) {
    return null;
  }
  const match = dbUrl.match(/^jdbc:mysql:\/\/([^:/?]+)(?::(\d+))?/);
  if (!match) {
    return null;
  }
  return {
    host: match[1],
    port: Number(match[2] || 3306),
  };
}

function probeTcp(host, port, timeoutMs = 2000) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host, port, timeout: timeoutMs });
    socket.once('connect', () => {
      socket.destroy();
      resolve(true);
    });
    socket.once('timeout', () => {
      socket.destroy();
      resolve(false);
    });
    socket.once('error', () => resolve(false));
  });
}

function checkPortAvailability(port) {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.once('error', () => resolve(false));
    server.once('listening', () => {
      server.close(() => resolve(true));
    });
    server.listen(port, '127.0.0.1');
  });
}

function addEnvironmentCheck(checks, status, name, message, details = {}) {
  checks.push({ status, name, message, details });
}

async function buildEnvironmentReport({ expectedProfile = '', installMode = false, skipNetworkChecks = false } = {}) {
  const checks = [];
  const env = parseEnvFile(envPath);
  const envExample = parseEnvFile(envExamplePath);
  const capacity = detectCapacity();
  const recommendedProfile = capacity.profileName;

  addEnvironmentCheck(checks, capacity.cpuCount >= environmentMinimums.cpu ? 'pass' : 'fail', 'CPU', `${capacity.cpuCount} cores detected`, { minimum: environmentMinimums.cpu });
  addEnvironmentCheck(checks, capacity.memoryGb >= environmentMinimums.memoryGb ? 'pass' : 'fail', 'Memory', `${capacity.memoryGb.toFixed(1)} GiB detected`, { minimumGb: environmentMinimums.memoryGb });
  addEnvironmentCheck(checks, capacity.diskGb >= environmentMinimums.diskGb ? 'pass' : 'warn', 'Disk', `${capacity.diskGb.toFixed(1)} GiB free at ${repoRoot}`, { recommendedGb: environmentMinimums.diskGb });
  addEnvironmentCheck(checks, ['linux', 'darwin'].includes(capacity.platform) ? 'pass' : 'warn', 'OS', `${capacity.platform} ${release()} ${capacity.arch}`);
  addEnvironmentCheck(checks, expectedProfile && expectedProfile !== recommendedProfile ? 'warn' : 'pass', 'Capacity profile', `recommended=${recommendedProfile}${expectedProfile ? ` requested=${expectedProfile}` : ''}`);

  const nodeMajor = parseMajorVersion(process.versions.node);
  addEnvironmentCheck(checks, nodeMajor >= environmentMinimums.nodeMajor ? 'pass' : 'fail', 'Node.js', process.version, { minimumMajor: environmentMinimums.nodeMajor });

  const requiredCommands = capacity.platform === 'win32'
    ? ['curl']
    : ['curl', 'tar', 'gzip', 'sh'];
  for (const command of requiredCommands) {
    addEnvironmentCheck(checks, commandExists(command) ? 'pass' : 'fail', `Command ${command}`, commandExists(command) ? 'available' : 'missing');
  }

  addEnvironmentCheck(checks, existsSync(composeFile) ? 'pass' : 'fail', 'Compose file', composeFile);
  addEnvironmentCheck(checks, existsSync(envExamplePath) ? 'pass' : 'fail', 'Env example', envExamplePath);
  addEnvironmentCheck(checks, existsSync(envPath) ? 'pass' : 'warn', 'Env file', existsSync(envPath) ? envPath : 'deploy/.env is not created yet');

  const requiredEnvKeys = [
    'MYSQL_ROOT_PASSWORD',
    'DB_USERNAME',
    'DB_PASSWORD',
    'DB_MIGRATION_USERNAME',
    'DB_MIGRATION_PASSWORD',
    'MYSQL_BACKUP_USERNAME',
    'MYSQL_BACKUP_PASSWORD',
    'MYSQL_RESTORE_USERNAME',
    'MYSQL_RESTORE_PASSWORD',
    'XXL_JOB_DB_URL',
    'XXL_JOB_DB_USERNAME',
    'XXL_JOB_DB_PASSWORD',
    'REDIS_CACHE_PASSWORD',
    'REDIS_RUNTIME_PASSWORD',
    'JWT_SECRET',
    'FIELD_SECRET',
    'PLUGIN_SIGNATURE_SECRET',
    'SAAS_INTERNAL_SYSTEM_TOKEN',
    'SAAS_INTERNAL_AUTH_TOKEN',
    'SAAS_INTERNAL_AUTH_SYSTEM_TOKEN',
    'SAAS_INTERNAL_FILE_TOKEN',
    'SAAS_INTERNAL_MESSAGE_TOKEN',
    'SAAS_INTERNAL_PAYMENT_TOKEN',
    'SAAS_INTERNAL_PLUGIN_TOKEN',
    'SAAS_INTERNAL_TEAM_TOKEN',
    'SAAS_INTERNAL_JOB_TOKEN',
    'XXL_JOB_ADMIN_ACCESS_TOKEN',
    'XXL_JOB_LOGIN_PASSWORD',
    'CORS_ALLOWED_ORIGIN_PATTERNS',
  ];
  const envSource = existsSync(envPath) ? env : envExample;
  for (const key of requiredEnvKeys) {
    const value = envSource[key];
    const status = value && !/^change-me/.test(value) ? 'pass' : existsSync(envPath) ? 'fail' : 'warn';
    addEnvironmentCheck(checks, status, `Env ${key}`, status === 'pass' ? 'configured' : 'missing or placeholder');
  }

  if (envSource.FRONTEND_ORIGIN) {
    addEnvironmentCheck(
      checks,
      /^https?:\/\//.test(envSource.FRONTEND_ORIGIN) && !envSource.FRONTEND_ORIGIN.includes('*') ? 'pass' : 'warn',
      'Frontend origin',
      envSource.FRONTEND_ORIGIN
    );
  }
  if (envSource.API_DOMAIN) {
    addEnvironmentCheck(checks, envSource.API_DOMAIN.includes('.') ? 'pass' : 'warn', 'API domain', envSource.API_DOMAIN);
  }

  const dockerExists = commandExists('docker');
  addEnvironmentCheck(checks, dockerExists ? 'pass' : installMode ? 'warn' : 'fail', 'Docker CLI', dockerExists ? 'available' : 'missing');
  if (dockerExists) {
    const dockerVersion = output('docker', ['--version'], { check: false });
    const dockerMajor = parseMajorVersion(dockerVersion);
    addEnvironmentCheck(checks, dockerMajor >= environmentMinimums.dockerMajor ? 'pass' : 'warn', 'Docker version', dockerVersion || 'unknown', { recommendedMajor: environmentMinimums.dockerMajor });

    const dockerInfo = spawnSync('docker', ['info'], {
      cwd: repoRoot,
      encoding: 'utf8', stdio: 'pipe',
      stdio: ['ignore', 'pipe', 'pipe'],
      shell: false,
    });
    addEnvironmentCheck(
      checks,
      dockerInfo.status === 0 ? 'pass' : installMode ? 'warn' : 'fail',
      'Docker daemon',
      dockerInfo.status === 0 ? 'running' : (dockerInfo.stderr || 'not running').trim()
    );

    const compose = output('docker', ['compose', 'version'], { check: false });
    addEnvironmentCheck(checks, compose ? 'pass' : 'fail', 'Docker Compose', compose || 'docker compose v2 missing');
  }

  if (!skipNetworkChecks) {
    const httpAvailable = await checkPortAvailability(80);
    addEnvironmentCheck(checks, httpAvailable ? 'pass' : 'warn', 'Port 80', httpAvailable ? 'available' : 'already in use');
    const httpsAvailable = await checkPortAvailability(443);
    addEnvironmentCheck(checks, httpsAvailable ? 'pass' : 'warn', 'Port 443', httpsAvailable ? 'available' : 'already in use');
    const backendAvailable = await checkPortAvailability(8080);
    addEnvironmentCheck(checks, backendAvailable ? 'pass' : 'warn', 'Port 8080', backendAvailable ? 'available' : 'already in use');

    const dbEndpoint = parseDbEndpoint(envSource.DB_URL);
    if (dbEndpoint && !['localhost', '127.0.0.1', 'mysql'].includes(dbEndpoint.host)) {
      const dbReachable = await probeTcp(dbEndpoint.host, dbEndpoint.port);
      addEnvironmentCheck(checks, dbReachable ? 'pass' : 'warn', 'External MySQL TCP', `${dbEndpoint.host}:${dbEndpoint.port} ${dbReachable ? 'reachable' : 'not reachable from here'}`);
    }
  }

  const fatalCount = checks.filter((check) => check.status === 'fail').length;
  const warnCount = checks.filter((check) => check.status === 'warn').length;
  return {
    status: fatalCount > 0 ? 'fail' : warnCount > 0 ? 'warn' : 'pass',
    recommendedProfile,
    cpuCount: capacity.cpuCount,
    memoryGb: Number(capacity.memoryGb.toFixed(1)),
    diskGb: Number(capacity.diskGb.toFixed(1)),
    checks,
  };
}

function printEnvironmentReport(report) {
  console.log(`[env] status=${report.status} profile=${report.recommendedProfile} cpu=${report.cpuCount} memory=${report.memoryGb}GiB diskFree=${report.diskGb}GiB`);
  for (const check of report.checks) {
    const marker = check.status === 'pass' ? 'OK' : check.status === 'warn' ? 'WARN' : 'FAIL';
    console.log(`[env] ${marker.padEnd(4)} ${check.name}: ${check.message}`);
  }
}

function assertEnvironmentReport(report) {
  const fatalCount = report.checks.filter((check) => check.status === 'fail').length;
  const warnCount = report.checks.filter((check) => check.status === 'warn').length;
  if (fatalCount > 0 || (strict && warnCount > 0)) {
    throw new Error(`Environment check failed: ${fatalCount} failure(s), ${warnCount} warning(s).`);
  }
}

function yesNo(value) {
  return value ? 'true' : 'false';
}

async function ask(rl, question, defaultValue) {
  if (auto) {
    return defaultValue;
  }
  const suffix = defaultValue === undefined || defaultValue === '' ? '' : ` (${defaultValue})`;
  const answer = await rl.question(`${question}${suffix}: `);
  return answer.trim() || defaultValue || '';
}

async function askBoolean(rl, question, defaultValue) {
  if (auto) {
    return defaultValue;
  }
  const answer = await rl.question(`${question} (${defaultValue ? 'Y/n' : 'y/N'}): `);
  if (!answer.trim()) {
    return defaultValue;
  }
  return ['y', 'yes', 'true', '1'].includes(answer.trim().toLowerCase());
}

function normalizeOrigin(value) {
  const trimmed = String(value || '').trim();
  if (!trimmed) {
    return '';
  }
  if (/^https?:\/\//.test(trimmed)) {
    return trimmed.replace(/\/$/, '');
  }
  return `https://${trimmed.replace(/\/$/, '')}`;
}

function defaultFrontendOrigin(existingEnv) {
  const explicitOrigin = argMap.get('lumira-ui-origin');
  if (explicitOrigin) {
    return normalizeOrigin(explicitOrigin);
  }
  if (existingEnv.FRONTEND_ORIGIN && !/localhost|127\.0\.0\.1|\*/.test(existingEnv.FRONTEND_ORIGIN)) {
    return normalizeOrigin(existingEnv.FRONTEND_ORIGIN);
  }
  const fromCors = (existingEnv.CORS_ALLOWED_ORIGIN_PATTERNS || '')
    .split(',')
    .map((item) => item.trim())
    .find((item) => /^https?:\/\//.test(item) && !item.includes('localhost') && !item.includes('127.0.0.1'));
  return normalizeOrigin(fromCors || 'https://bm.aiadc.org.cn');
}

async function collectInstallOptions(existingEnv, capacity) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  try {
    const apiDomainDefault = argMap.get('api-domain') || existingEnv.API_DOMAIN || 'bm.aiadc.org.cn';
    const frontendOriginDefault = defaultFrontendOrigin(existingEnv);
    const apiDomain = await ask(rl, '后端 API 域名', apiDomainDefault);
    const frontendOrigin = normalizeOrigin(await ask(rl, '前端访问域名或 Origin', frontendOriginDefault));
    const useLocalMysql = await askBoolean(rl, '是否启动内置 MySQL（已有 1Panel/MySQL 时选否）', argMap.has('local-mysql'));
    const useFrontendContainer = await askBoolean(rl, '是否启动内置前端容器（Vercel 托管时选否）', argMap.has('lumira-ui'));
    const useObservability = await askBoolean(
      rl,
      '是否启动完整观测栈（4G 机器建议只排查时临时开启）',
      argMap.has('observability') || existingEnv.OBSERVABILITY_ENVIRONMENT === 'prod-observability'
    );
    const profileDefault = argMap.get('profile') || capacity.profileName;
    const profileName = await ask(rl, '资源配置档 tiny/standard', profileDefault);
    return {
      apiDomain,
      frontendOrigin,
      useLocalMysql,
      useFrontendContainer,
      useObservability,
      profileName: defaultCapacityProfiles[profileName] ? profileName : capacity.profileName,
    };
  } finally {
    rl.close();
  }
}

function ensureEnvFile(options, profile) {
  let content = existsSync(envPath) ? readFileSync(envPath, 'utf8') : readFileSync(envExamplePath, 'utf8');
  const existingEnv = parseEnvFile(envPath);
  const secrets = generatedSecrets();

  for (const [key, value] of Object.entries(secrets)) {
    if (!existingEnv[key] || existingEnv[key].startsWith('change-me')) {
      content = setEnvValue(content, key, value);
    }
  }

  const corsOrigins = Array.from(new Set([
    options.frontendOrigin,
    options.apiDomain ? `https://${options.apiDomain}` : '',
    existingEnv.CORS_ALLOWED_ORIGIN_PATTERNS,
  ].filter(Boolean).flatMap((value) => value.split(',').map((item) => item.trim()).filter(Boolean))));

  const generatedDefaults = defaultGeneratedValues();
  const tunable = (key, value) => {
    const current = existingEnv[key];
    return !current || generatedDefaults.has(current) ? value : current;
  };

  const updates = {
    API_DOMAIN: options.apiDomain,
    FRONTEND_ORIGIN: options.frontendOrigin,
    API_PROXY_BIND: existingEnv.API_PROXY_BIND || '127.0.0.1:8000',
    FRONTEND_BIND: existingEnv.FRONTEND_BIND || '127.0.0.1:8001',
    CORS_ALLOWED_ORIGIN_PATTERNS: corsOrigins.join(','),
    JAVA_OPTS: tunable('JAVA_OPTS', profile.javaOpts),
    REDIS_CACHE_MAXMEMORY: tunable('REDIS_CACHE_MAXMEMORY', '128mb'),
    REDIS_CACHE_MEM_LIMIT: tunable('REDIS_CACHE_MEM_LIMIT', '192m'),
    REDIS_RUNTIME_MAXMEMORY: tunable('REDIS_RUNTIME_MAXMEMORY', profile.redisMaxmemory),
    REDIS_RUNTIME_MEM_LIMIT: tunable('REDIS_RUNTIME_MEM_LIMIT', '384m'),
    DOCKER_LOG_MAX_SIZE: tunable('DOCKER_LOG_MAX_SIZE', profile.dockerLogMaxSize),
    DOCKER_LOG_MAX_FILE: tunable('DOCKER_LOG_MAX_FILE', profile.dockerLogMaxFile),
    SERVER_TOMCAT_THREADS_MAX: tunable('SERVER_TOMCAT_THREADS_MAX', profile.tomcatThreadsMax),
    SERVER_TOMCAT_THREADS_MIN_SPARE: tunable('SERVER_TOMCAT_THREADS_MIN_SPARE', '8'),
    SERVER_TOMCAT_ACCEPT_COUNT: tunable('SERVER_TOMCAT_ACCEPT_COUNT', '120'),
    SERVER_TOMCAT_MAX_CONNECTIONS: tunable('SERVER_TOMCAT_MAX_CONNECTIONS', '4096'),
    SPRING_THREADS_VIRTUAL_ENABLED: tunable('SPRING_THREADS_VIRTUAL_ENABLED', 'true'),
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: tunable('SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE', profile.hikariMaxPoolSize),
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: tunable('SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE', '1'),
    ...profile.serviceLimits,
    ...profile.gatewayQps,
    SAAS_TRAFFIC_AUTH_LOGIN_QPS: existingEnv.SAAS_TRAFFIC_AUTH_LOGIN_QPS || '20',
    SAAS_TRAFFIC_AUTH_REFRESH_TOKEN_QPS: existingEnv.SAAS_TRAFFIC_AUTH_REFRESH_TOKEN_QPS || '80',
    SAAS_TRAFFIC_AUTH_CURRENT_USER_QPS: existingEnv.SAAS_TRAFFIC_AUTH_CURRENT_USER_QPS || '160',
    PLATFORM_UPDATE_SOURCE_URL: existingEnv.PLATFORM_UPDATE_SOURCE_URL || 'https://api.github.com/repos/Elexvx/lumira/commits/main',
    PLATFORM_UPDATE_MANIFEST_URL: existingEnv.PLATFORM_UPDATE_MANIFEST_URL || 'https://api.github.com/repos/Elexvx/Lumira/releases/tags/continuous',
    PLATFORM_UPDATE_AGENT_URL: existingEnv.PLATFORM_UPDATE_AGENT_URL === 'http://127.0.0.1:9788'
      ? 'http://host.docker.internal:9788'
      : (existingEnv.PLATFORM_UPDATE_AGENT_URL || 'http://host.docker.internal:9788'),
    PLATFORM_UPDATE_AGENT_ALLOWED_HOSTS: existingEnv.PLATFORM_UPDATE_AGENT_ALLOWED_HOSTS || 'host.docker.internal',
    LUMIRA_UPDATER_PORT: existingEnv.LUMIRA_UPDATER_PORT || '9788',
  };

  for (const [key, value] of Object.entries(updates)) {
    content = setEnvValue(content, key, value);
  }

  if (dryRun) {
    log('DRY would write deploy/.env with detected capacity profile and deployment options.');
    return;
  }
  writeFileSync(envPath, content);
  log(`deploy/.env is ready for ${profile.label}.`);
}

function defaultGeneratedValues() {
  const values = new Set([
    '-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom',
  ]);
  for (const profile of Object.values(defaultCapacityProfiles)) {
    values.add(profile.javaOpts);
    values.add(profile.redisMaxmemory);
    values.add(profile.dockerLogMaxSize);
    values.add(profile.dockerLogMaxFile);
    values.add(profile.hikariMaxPoolSize);
    values.add(profile.tomcatThreadsMax);
    for (const value of Object.values(profile.serviceLimits)) {
      values.add(value);
    }
    for (const value of Object.values(profile.gatewayQps)) {
      values.add(value);
    }
  }
  for (const value of ['1', '2', '3', '4', '8', '50m', '100m', '120', '4096', 'true', '384m']) {
    values.add(value);
  }
  return values;
}

function resolveMysqlExporterSecretPath(environment) {
  const configuredPath = String(environment.MYSQLD_EXPORTER_PASSWORD_FILE || '').trim();
  return configuredPath
    ? path.resolve(path.dirname(composeFile), configuredPath)
    : defaultMysqlExporterSecretPath;
}

function readMysqlExporterPassword(secretPath) {
  if (!existsSync(secretPath)) {
    throw new Error(`MySQL exporter password file does not exist: ${secretPath}`);
  }
  const metadata = lstatSync(secretPath);
  if (metadata.isSymbolicLink() || !metadata.isFile()) {
    throw new Error('MySQL exporter password file must be a regular non-symlink file.');
  }
  if (metadata.size > 4096) {
    throw new Error('MySQL exporter password file is unexpectedly large.');
  }
  const raw = readFileSync(secretPath, 'utf8');
  if (raw.includes('\0') || raw.includes('\r')) {
    throw new Error('MySQL exporter password file must contain one UTF-8 line without NUL or CR characters.');
  }
  const password = raw.endsWith('\n') ? raw.slice(0, -1) : raw;
  if (!password || password.includes('\n') || password.startsWith('change-me')) {
    throw new Error('MySQL exporter password file must contain exactly one non-placeholder password line.');
  }
  return password;
}

function wslForwardedEnvironment(variableNames) {
  const existing = String(process.env.WSLENV || '').split(':').map((entry) => entry.trim()).filter(Boolean);
  const forwardedNames = new Set(existing.map((entry) => entry.split('/')[0].toUpperCase()));
  for (const variableName of variableNames) {
    if (!forwardedNames.has(variableName.toUpperCase())) existing.push(`${variableName}/u`);
  }
  return existing.join(':');
}

function prepareObservabilityFiles(options) {
  if (!options.useObservability) return;
  const environment = parseEnvFile(envPath);
  const configuredPath = String(environment.MYSQLD_EXPORTER_PASSWORD_FILE || '').trim();
  const secretPath = resolveMysqlExporterSecretPath(environment);
  if (configuredPath) {
    readMysqlExporterPassword(secretPath);
  } else {
    const password = String(environment.MYSQLD_EXPORTER_PASSWORD || '');
    if (!password || password.startsWith('change-me')) {
      throw new Error('MYSQLD_EXPORTER_PASSWORD must be a non-placeholder secret when observability is enabled.');
    }
    mkdirSync(path.dirname(secretPath), { recursive: true });
    const temporaryPath = path.join(path.dirname(secretPath), `.${path.basename(secretPath)}.${process.pid}.${randomBytes(6).toString('hex')}.tmp`);
    try {
      writeFileSync(temporaryPath, `${password}\n`, { mode: 0o600, flag: 'wx' });
      chmodSync(temporaryPath, 0o600);
      renameSync(temporaryPath, secretPath);
      chmodSync(secretPath, 0o600);
    } finally {
      rmSync(temporaryPath, { force: true });
    }
  }
  readMysqlExporterPassword(secretPath);
  mkdirSync(generatedBackupMetricsDir, { recursive: true });
  const address = String(environment.MYSQLD_EXPORTER_ADDRESS || 'mysql:3306').trim();
  const addressMatch = address.match(/^(?:\[([^\]]+)\]|([^:]+)):(\d+)$/u);
  if (!addressMatch) throw new Error('MYSQLD_EXPORTER_ADDRESS must use host:port syntax.');
  const host = (addressMatch[1] || addressMatch[2]).toLowerCase();
  const port = Number(addressMatch[3]);
  if (options.useLocalMysql) {
    if (!['mysql', 'lumira-mysql'].includes(host) || port !== 3306) {
      throw new Error('Bundled local MySQL observability requires MYSQLD_EXPORTER_ADDRESS=mysql:3306.');
    }
  } else {
    const databaseEndpoint = parseDbEndpoint(environment.DB_URL);
    if (!databaseEndpoint) {
      throw new Error('External MySQL observability requires a single-host DB_URL.');
    }
    if (host !== databaseEndpoint.host.toLowerCase() || port !== databaseEndpoint.port) {
      throw new Error(
        `External MYSQLD_EXPORTER_ADDRESS must exactly match the DB_URL target ${databaseEndpoint.host}:${databaseEndpoint.port}.`,
      );
    }
    const configSetting = String(environment.MYSQLD_EXPORTER_CONFIG_FILE || '').trim();
    const caSetting = String(environment.MYSQLD_EXPORTER_CA_FILE || '').trim();
    if (!configSetting || !caSetting) {
      throw new Error('External MySQL observability requires MYSQLD_EXPORTER_CONFIG_FILE and MYSQLD_EXPORTER_CA_FILE for verified TLS.');
    }
    const configPath = path.resolve(path.dirname(composeFile), configSetting);
    const caPath = path.resolve(path.dirname(composeFile), caSetting);
    for (const [variableName, sourcePath] of [
      ['MYSQLD_EXPORTER_CONFIG_FILE', configPath],
      ['MYSQLD_EXPORTER_CA_FILE', caPath],
    ]) {
      if (!existsSync(sourcePath)) throw new Error(`${variableName} does not exist: ${sourcePath}`);
      const metadata = lstatSync(sourcePath);
      if (metadata.isSymbolicLink() || !metadata.isFile()) throw new Error(`${variableName} must be a regular non-symlink file.`);
    }
    const config = readFileSync(configPath, 'utf8');
    const ca = readFileSync(caPath, 'utf8');
    const directives = config
      .split(/\r?\n/u)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith('#') && !line.startsWith(';'));
    if (directives.length !== 3
        || directives.filter((line) => /^\[client\]$/iu.test(line)).length !== 1
        || directives.filter((line) => /^ssl-ca\s*=\s*"?\/run\/secrets\/mysql_exporter_ca"?$/iu.test(line)).length !== 1
        || directives.filter((line) => /^tls\s*=\s*"?custom"?$/iu.test(line)).length !== 1) {
      throw new Error(
        'External MySQL exporter config may contain only [client], ssl-ca=/run/secrets/mysql_exporter_ca, and tls=custom; address, username and password come from validated deployment settings.',
      );
    }
    if (!/-----BEGIN CERTIFICATE-----[\s\S]+-----END CERTIFICATE-----/u.test(ca)) {
      throw new Error('MYSQLD_EXPORTER_CA_FILE does not contain a PEM certificate.');
    }
  }
  log(`MySQL exporter secret and backup textfile directory are ready for observability.`);
}

function assertEdgeTlsFiles(options) {
  if (options.useLocalMysql) {
    return;
  }

  const missingFiles = edgeTlsFiles
    .map((fileName) => path.join(edgeTlsDir, fileName))
    .filter((filePath) => !existsSync(filePath));
  if (missingFiles.length === 0) {
    return;
  }

  throw new Error(`edge-proxy requires TLS files before startup: ${missingFiles.join(', ')}`);
}

function ensureDocker() {
  if (dryRun) {
    log('DRY would check or install Docker.');
    return;
  }
  if (commandExists('docker')) {
    const info = spawnSync('docker', ['info'], { stdio: 'ignore' });
    if (info.status === 0) {
      log('Docker daemon is available.');
      return;
    }
    if (platform() === 'linux' && commandExists('systemctl')) {
      run('systemctl', ['start', 'docker'], { check: false });
      if (spawnSync('docker', ['info'], { stdio: 'ignore' }).status === 0) {
        log('Docker daemon started.');
        return;
      }
    }
  }

  if (skipDockerInstall) {
    throw new Error('Docker is not available and --skip-docker-install was passed.');
  }
  if (platform() !== 'linux') {
    throw new Error('Docker is not available. Install/start Docker Desktop, then rerun this script.');
  }
  run('sh', ['-lc', 'curl -fsSL https://get.docker.com | sh']);
  if (commandExists('systemctl')) {
    run('systemctl', ['enable', '--now', 'docker'], { check: false });
  }
  run('docker', ['info'], { stdio: 'ignore' });
  log('Docker installed and running.');
}

async function checkEnvironment(profileName) {
  const report = await buildEnvironmentReport({
    expectedProfile: profileName,
    installMode: true,
    skipNetworkChecks: skipNetwork,
  });
  printEnvironmentReport(report);
  assertEnvironmentReport(report);
}

function composeProfiles(options) {
  return [
    '--profile', 'blue',
    ...(!options.useLocalMysql ? ['--profile', 'edge'] : []),
    ...(options.useLocalMysql ? ['--profile', 'local-mysql'] : []),
    ...(options.useObservability ? ['--profile', 'observability'] : []),
    ...(options.useFrontendContainer ? ['--profile', 'local-lumira-ui'] : []),
  ];
}

function composeArgs(options, ...extraArgs) {
  return ['compose', '--env-file', 'deploy/.env', '-f', composeFile, ...composeProfiles(options), ...extraArgs];
}

function composeUp(options, label, services, upOptions = []) {
  if (services.length === 0) {
    return;
  }
  log(`Starting ${label}: ${services.join(', ')}`);
  run('docker', composeArgs(options, 'up', '-d', ...upOptions, ...services));
}

async function waitForComposeServicesRunning(options, expectedServices, label, timeoutMs = 240_000, intervalMs = 3_000) {
  const uniqueServices = Array.from(new Set(expectedServices.filter(Boolean)));
  if (uniqueServices.length === 0) {
    return;
  }

  const startedAt = Date.now();
  let lastMissing = uniqueServices;

  while (Date.now() - startedAt <= timeoutMs) {
    const stdout = output('docker', composeArgs(options, 'ps', '--services', '--status', 'running', ...uniqueServices), { check: false });
    const running = new Set(
      String(stdout || '')
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean)
    );
    const missing = uniqueServices.filter((serviceName) => !running.has(serviceName));
    if (missing.length === 0) {
      log(`${label} are running: ${uniqueServices.join(', ')}`);
      return;
    }

    lastMissing = missing;
    // eslint-disable-next-line no-await-in-loop
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }

  throw new Error(`${label} did not reach running state: ${lastMissing.join(', ')}`);
}

function databaseNameFromEnvironment(environment) {
  const explicitName = String(environment.MYSQL_DATABASE || environment.DB_NAME || '').trim();
  if (explicitName) return explicitName;
  const match = String(environment.DB_URL || '').match(/^jdbc:mysql:\/\/[^/]+\/([^?;]+)/iu);
  if (!match) return null;
  try {
    return decodeURIComponent(match[1]);
  } catch {
    return match[1];
  }
}

function runLocalMysqlRootSql(options, environment, sql) {
  const rootPassword = String(environment.MYSQL_ROOT_PASSWORD || '');
  if (!rootPassword) throw new Error('Local MySQL exporter provisioning requires MYSQL_ROOT_PASSWORD.');
  return execRun(
    'docker',
    [
      ...composeArgs(options, 'exec', '-T', '-e', 'MYSQL_PWD', 'mysql'),
      'mysql', '--batch', '--skip-column-names', '-uroot',
    ],
    {
      cwd: repoRoot,
      check: false,
      input: sql,
      encoding: 'utf8',
      stdio: ['pipe', 'pipe', 'pipe'],
      env: {
        ...process.env,
        MYSQL_PWD: rootPassword,
        WSLENV: wslForwardedEnvironment(['MYSQL_PWD']),
      },
    },
  );
}

async function waitForLocalMysqlRoot(options, environment, timeoutMs = 180_000) {
  const startedAt = Date.now();
  let lastError = 'MySQL has not accepted an authenticated query yet.';
  while (Date.now() - startedAt <= timeoutMs) {
    const result = runLocalMysqlRootSql(options, environment, 'SELECT 1;\n');
    if (result.status === 0 && String(result.stdout || '').trim() === '1') return;
    lastError = String(result.stderr || result.error?.message || lastError).trim();
    // eslint-disable-next-line no-await-in-loop
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }
  throw new Error(`Local MySQL did not become ready for exporter provisioning: ${lastError}`);
}

async function provisionLocalMysqlExporterAccount(options) {
  if (!options.useLocalMysql || !options.useObservability) return;
  const environment = parseEnvFile(envPath);
  await waitForLocalMysqlRoot(options, environment);
  const username = String(environment.MYSQLD_EXPORTER_USERNAME || 'exporter').trim();
  const databaseName = databaseNameFromEnvironment(environment);
  const password = readMysqlExporterPassword(resolveMysqlExporterSecretPath(environment));
  if (!/^[A-Za-z0-9_]{1,64}$/u.test(username)) {
    throw new Error('MYSQLD_EXPORTER_USERNAME must contain only letters, digits, or underscores for local provisioning.');
  }
  if (!databaseName || !/^[A-Za-z0-9_]{1,64}$/u.test(databaseName)) {
    throw new Error('Local MySQL exporter provisioning requires a safe database name.');
  }

  const passwordBase64 = Buffer.from(password, 'utf8').toString('base64');
  const sql = [
    `SET @lumira_exporter_password = CONVERT(FROM_BASE64('${passwordBase64}') USING utf8mb4);`,
    "SET @lumira_create_exporter = CONCAT('CREATE USER IF NOT EXISTS `" + username + "`@''%'' IDENTIFIED BY ', QUOTE(@lumira_exporter_password));",
    'PREPARE lumira_create_exporter_stmt FROM @lumira_create_exporter;',
    'EXECUTE lumira_create_exporter_stmt;',
    'DEALLOCATE PREPARE lumira_create_exporter_stmt;',
    "SET @lumira_alter_exporter = CONCAT('ALTER USER `" + username + "`@''%'' IDENTIFIED BY ', QUOTE(@lumira_exporter_password));",
    'PREPARE lumira_alter_exporter_stmt FROM @lumira_alter_exporter;',
    'EXECUTE lumira_alter_exporter_stmt;',
    'DEALLOCATE PREPARE lumira_alter_exporter_stmt;',
    "REVOKE ALL PRIVILEGES, GRANT OPTION FROM `" + username + "`@'%';",
    "GRANT PROCESS, REPLICATION CLIENT ON *.* TO `" + username + "`@'%';",
    "GRANT SELECT ON `" + databaseName + "`.* TO `" + username + "`@'%';",
    "GRANT SELECT ON performance_schema.* TO `" + username + "`@'%';",
    "GRANT SELECT ON sys.* TO `" + username + "`@'%';",
    "ALTER USER `" + username + "`@'%' WITH MAX_USER_CONNECTIONS 3;",
    "SHOW GRANTS FOR `" + username + "`@'%';",
    '',
  ].join('\n');
  const result = runLocalMysqlRootSql(options, environment, sql);
  if (result.status !== 0) {
    throw new Error(`Unable to provision the least-privilege local MySQL exporter account: ${String(result.stderr || '').trim()}`);
  }
  const grantee = `\`${username}\`@\`%\``;
  const scopePatterns = [
    new RegExp(`^GRANT (?:PROCESS, REPLICATION CLIENT|REPLICATION CLIENT, PROCESS) ON \\*\\.\\* TO ${grantee}(?: WITH MAX_USER_CONNECTIONS 3)?$`, 'u'),
    new RegExp('^GRANT SELECT ON `' + databaseName + '`\\.\\* TO ' + grantee + '$', 'u'),
    new RegExp('^GRANT SELECT ON `performance_schema`\\.\\* TO ' + grantee + '$', 'u'),
    new RegExp('^GRANT SELECT ON `sys`\\.\\* TO ' + grantee + '$', 'u'),
  ];
  const grants = String(result.stdout || '').split(/\r?\n/u).map((line) => line.trim()).filter(Boolean);
  if (grants.length !== scopePatterns.length
      || grants.some((line) => !scopePatterns.some((pattern) => pattern.test(line)))) {
    throw new Error('Local MySQL exporter account grants did not converge to the approved read-only set.');
  }
  log(`Local MySQL exporter account ${username} is provisioned with read-only monitoring privileges.`);
}

async function installContainers(options) {
  const deployEnv = parseEnvFile(envPath);
  assertProductionDataPlaneEnvironment(deployEnv);
  prepareObservabilityFiles(options);
  const upstreamDirectory = path.join(repoRoot, 'deploy', '.generated', 'api-proxy');
  mkdirSync(upstreamDirectory, { recursive: true });
  writeFileSync(
    path.join(upstreamDirectory, 'active-upstreams.conf'),
    renderActiveUpstreams(deployEnv.LUMIRA_ACTIVE_SLOT || 'blue'),
    { mode: 0o644 },
  );
  run('docker', composeArgs(options, 'config'), { stdio: 'ignore' });
  run('docker', composeArgs(options, 'pull', '--ignore-buildable'), { check: false });
  if (!skipBuild) {
    run('docker', composeArgs(options, 'build'));
  }
  if (noStart) {
    log('--no-start passed; containers were not started.');
    return;
  }

  composeUp(options, 'infrastructure', [
    ...(options.useLocalMysql ? ['mysql'] : []),
    'redis-cache',
    'redis-runtime',
  ]);
  await provisionLocalMysqlExporterAccount(options);
  composeUp(options, 'job admin', ['xxl-job-admin']);
  composeUp(options, 'monolith backend blue slot', ['lumira-server-blue']);
  composeUp(options, 'owner async runtime', ['lumira-async']);
  composeUp(options, 'job executor', ['lumira-job-executor']);
  composeUp(options, 'API proxy', ['api-proxy']);
  composeUp(options, 'edge proxy', !options.useLocalMysql ? ['edge-proxy'] : []);
  composeUp(options, 'lumira-ui container', options.useFrontendContainer ? ['lumira-ui'] : []);
  composeUp(options, 'observability', options.useObservability
    ? ['mysqld-exporter', 'redis-exporter', 'redis-runtime-exporter', 'backup-metrics-exporter', 'prometheus', 'loki', 'tempo', 'alloy', 'grafana']
    : []);
}

function installUpdaterService() {
  if (noStart || platform() !== 'linux' || !commandExists('systemctl')) {
    return;
  }
  const args = ['bin/install-lumira-updater.mjs'];
  if (dryRun) {
    args.push('--dry-run');
  }
  run('node', args);
}

async function waitForMysqlExporterAuthenticated(timeoutMs = 240_000, intervalMs = 3_000) {
  const queryUrl = 'http://127.0.0.1:9090/api/v1/query?query=mysql_up%7Bjob%3D%22mysql%22%7D';
  const startedAt = Date.now();
  let lastSummary = 'no Prometheus response';
  while (Date.now() - startedAt <= timeoutMs) {
    // eslint-disable-next-line no-await-in-loop
    const result = await probeHttp(queryUrl, { timeoutMs: 5_000 });
    if (result.ok) {
      try {
        const payload = JSON.parse(result.text);
        const values = payload?.data?.result ?? [];
        if (values.some((item) => item.value?.[1] === '1')) {
          log('MySQL exporter authenticated successfully (mysql_up=1).');
          return;
        }
        lastSummary = values.length === 0 ? 'mysql_up series is absent' : 'mysql_up is not 1';
      } catch (error) {
        lastSummary = error instanceof Error ? error.message : String(error);
      }
    } else {
      lastSummary = `Prometheus query status=${result.status || 'unreachable'}`;
    }
    // eslint-disable-next-line no-await-in-loop
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new Error(`MySQL observability is not ready: ${lastSummary}. Verify the external account/password/TLS or local exporter provisioning.`);
}

async function runVerification(options, profile) {
  if (noStart) {
    return;
  }
  await waitForComposeServicesRunning(
    options,
    [
      'redis-cache',
      'redis-runtime',
      'xxl-job-admin',
      'lumira-server-blue',
      'lumira-async',
      'lumira-job-executor',
      'api-proxy',
      ...(!options.useLocalMysql ? ['edge-proxy'] : []),
      ...(options.useLocalMysql ? ['mysql'] : []),
      ...(options.useObservability
        ? ['mysqld-exporter', 'redis-exporter', 'redis-runtime-exporter', 'backup-metrics-exporter', 'prometheus', 'loki', 'tempo', 'alloy', 'grafana']
        : []),
      ...(options.useFrontendContainer ? ['lumira-ui'] : []),
    ],
    'installed platform services'
  );
  if (options.useObservability) await waitForMysqlExporterAuthenticated();
  const baseUrl = process.env.DEPLOY_CHECK_BASE_URL || (options.useLocalMysql ? 'http://127.0.0.1:8000' : `https://${options.apiDomain}`);
  const backendUrl = process.env.DEPLOY_CHECK_BACKEND_URL || process.env.DEPLOY_CHECK_GATEWAY_URL || 'http://127.0.0.1:8080';
  run('node', ['bin\/check-deployment.mjs'], {
    env: {
      DEPLOY_CHECK_BASE_URL: baseUrl,
      DEPLOY_CHECK_BACKEND_URL: backendUrl,
    },
  });
  if (!skipSmoke) {
    run('node', ['bin\/load-smoke.mjs'], {
      env: {
        LOAD_SMOKE_BASE_URL: baseUrl,
        LOAD_SMOKE_DURATION_MS: process.env.LOAD_SMOKE_DURATION_MS || '15000',
        LOAD_SMOKE_CONCURRENCY: process.env.LOAD_SMOKE_CONCURRENCY || String(profile.smokeConcurrency),
      },
    });
  }
  if (options.apiDomain) {
    run('curl', ['-fsS', '--max-time', '15', `https://${options.apiDomain}/api/health`], { check: false });
  }
}

async function main() {
  const capacity = detectCapacity();
  if (!(checkOnly && jsonOutput)) {
    log(`Server: ${capacity.cpuCount} CPU, ${capacity.memoryGb.toFixed(1)} GiB RAM, ${capacity.diskGb.toFixed(1)} GiB free, ${capacity.platform}/${capacity.arch}`);
  }
  if (checkOnly) {
    const report = await buildEnvironmentReport({
      expectedProfile: argMap.get('profile') || capacity.profileName,
      installMode: true,
      skipNetworkChecks: skipNetwork,
    });
    if (jsonOutput) {
      console.log(JSON.stringify(report, null, 2));
    } else {
      printEnvironmentReport(report);
    }
    assertEnvironmentReport(report);
    return;
  }
  const existingEnv = parseEnvFile(envPath);
  const options = await collectInstallOptions(existingEnv, capacity);
  const profile = defaultCapacityProfiles[options.profileName] || defaultCapacityProfiles[capacity.profileName];
  log(`Using capacity profile: ${profile.label}`);
  ensureEnvFile(options, profile);
  await checkEnvironment(options.profileName);
  assertEdgeTlsFiles(options);
  ensureDocker();
  installUpdaterService();
  await installContainers(options);
  await runVerification(options, profile);
  log('Installation finished.');
}

main().catch((error) => {
  console.error(`[install] ${error.message}`);
  process.exit(1);
});
