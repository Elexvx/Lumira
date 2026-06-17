#!/usr/bin/env node

import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import net from 'node:net';
import { arch, cpus, platform, release, totalmem } from 'node:os';
import path from 'node:path';
import process from 'node:process';
import readline from 'node:readline/promises';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

import { parseEnvFile, setEnvValue, randomSecret, randomBase64Secret, defaultCapacityProfiles } from './lib/env-utils.mjs';
import { run as execRun, output as execOutput, commandExists, createLogger, resolveRepoRoot } from './lib/exec-utils.mjs';
import { waitForHttp, probeHttp } from './lib/http-utils.mjs';
const log = createLogger('install');
const repoRoot = resolveRepoRoot(import.meta.url);
const envExamplePath = path.join(repoRoot, 'deploy', '.env.example');
const envPath = path.join(repoRoot, 'deploy', '.env');
const composeFile = path.join(repoRoot, 'deploy', 'docker-compose.prod.yml');

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
    GRAFANA_ADMIN_PASSWORD: randomSecret('grafana'),
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

  for (const command of ['curl', 'tar', 'gzip', 'sh']) {
    addEnvironmentCheck(checks, commandExists(command) ? 'pass' : 'fail', `Command ${command}`, commandExists(command) ? 'available' : 'missing');
  }

  addEnvironmentCheck(checks, existsSync(composeFile) ? 'pass' : 'fail', 'Compose file', composeFile);
  addEnvironmentCheck(checks, existsSync(envExamplePath) ? 'pass' : 'fail', 'Env example', envExamplePath);
  addEnvironmentCheck(checks, existsSync(envPath) ? 'pass' : 'warn', 'Env file', existsSync(envPath) ? envPath : 'deploy/.env is not created yet');

  const requiredEnvKeys = [
    'DB_PASSWORD',
    'JWT_SECRET',
    'FIELD_SECRET',
    'PLUGIN_SIGNATURE_SECRET',
    'SAAS_JOB_INTERNAL_TOKEN',
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
  const explicitOrigin = argMap.get('frontend-origin');
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
  return normalizeOrigin(fromCors || 'https://saas.elexvx.com');
}

async function collectInstallOptions(existingEnv, capacity) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  try {
    const apiDomainDefault = argMap.get('api-domain') || existingEnv.API_DOMAIN || 'saas.elexvx.com';
    const frontendOriginDefault = defaultFrontendOrigin(existingEnv);
    const apiDomain = await ask(rl, '后端 API 域名', apiDomainDefault);
    const frontendOrigin = normalizeOrigin(await ask(rl, '前端访问域名或 Origin', frontendOriginDefault));
    const useLocalMysql = await askBoolean(rl, '是否启动内置 MySQL（已有 1Panel/MySQL 时选否）', argMap.has('local-mysql'));
    const useNacos = await askBoolean(
      rl,
      '是否启动内置 Nacos（默认配置不需要）',
      argMap.has('nacos') || existingEnv.NACOS_CONFIG_ENABLED === 'true' || existingEnv.NACOS_DISCOVERY_ENABLED === 'true'
    );
    const useFrontendContainer = await askBoolean(rl, '是否启动内置前端容器（Vercel 托管时选否）', argMap.has('frontend'));
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
      useNacos,
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
    REDIS_MAXMEMORY: tunable('REDIS_MAXMEMORY', profile.redisMaxmemory),
    REDIS_MEM_LIMIT: tunable('REDIS_MEM_LIMIT', '384m'),
    DOCKER_LOG_MAX_SIZE: tunable('DOCKER_LOG_MAX_SIZE', profile.dockerLogMaxSize),
    DOCKER_LOG_MAX_FILE: tunable('DOCKER_LOG_MAX_FILE', profile.dockerLogMaxFile),
    SERVER_TOMCAT_THREADS_MAX: tunable('SERVER_TOMCAT_THREADS_MAX', profile.tomcatThreadsMax),
    SERVER_TOMCAT_THREADS_MIN_SPARE: tunable('SERVER_TOMCAT_THREADS_MIN_SPARE', '8'),
    SERVER_TOMCAT_ACCEPT_COUNT: tunable('SERVER_TOMCAT_ACCEPT_COUNT', '120'),
    SERVER_TOMCAT_MAX_CONNECTIONS: tunable('SERVER_TOMCAT_MAX_CONNECTIONS', '4096'),
    SPRING_THREADS_VIRTUAL_ENABLED: tunable('SPRING_THREADS_VIRTUAL_ENABLED', 'true'),
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: tunable('SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE', profile.hikariMaxPoolSize),
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: tunable('SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE', '1'),
    NACOS_CONFIG_ENABLED: yesNo(options.useNacos && existingEnv.NACOS_CONFIG_ENABLED === 'true'),
    NACOS_DISCOVERY_ENABLED: yesNo(options.useNacos && existingEnv.NACOS_DISCOVERY_ENABLED === 'true'),
    ...profile.serviceLimits,
    ...profile.gatewayQps,
    SAAS_TRAFFIC_AUTH_LOGIN_QPS: existingEnv.SAAS_TRAFFIC_AUTH_LOGIN_QPS || '20',
    SAAS_TRAFFIC_AUTH_REFRESH_TOKEN_QPS: existingEnv.SAAS_TRAFFIC_AUTH_REFRESH_TOKEN_QPS || '80',
    SAAS_TRAFFIC_AUTH_CURRENT_USER_QPS: existingEnv.SAAS_TRAFFIC_AUTH_CURRENT_USER_QPS || '160',
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
    ...(options.useLocalMysql ? ['--profile', 'local-mysql'] : []),
    ...(options.useNacos ? ['--profile', 'nacos'] : []),
    ...(options.useObservability ? ['--profile', 'observability'] : []),
    ...(options.useFrontendContainer ? ['--profile', 'local-frontend'] : []),
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

function installContainers(options) {
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
    'redis',
    ...(options.useNacos ? ['nacos'] : []),
  ]);
  composeUp(options, 'job admin', ['xxl-job-admin']);
  composeUp(options, 'monolith backend', ['lumira-server']);
  composeUp(options, 'API proxy', ['api-proxy']);
  composeUp(options, 'frontend container', options.useFrontendContainer ? ['frontend'] : []);
  composeUp(options, 'observability', options.useObservability ? ['prometheus', 'loki', 'tempo', 'alloy', 'grafana'] : []);
}

function runVerification(options, profile) {
  if (noStart) {
    return;
  }
  const baseUrl = process.env.DEPLOY_CHECK_BASE_URL || `https://${options.apiDomain}`;
  const backendUrl = process.env.DEPLOY_CHECK_BACKEND_URL || process.env.DEPLOY_CHECK_GATEWAY_URL || 'http://127.0.0.1:8080';
  run('node', ['scripts/check-deployment.mjs'], {
    env: {
      DEPLOY_CHECK_BASE_URL: baseUrl,
      DEPLOY_CHECK_BACKEND_URL: backendUrl,
    },
  });
  if (!skipSmoke) {
    run('node', ['scripts/load-smoke.mjs'], {
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
  ensureDocker();
  installContainers(options);
  runVerification(options, profile);
  log('Installation finished.');
}

main().catch((error) => {
  console.error(`[install] ${error.message}`);
  process.exit(1);
});
