#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import net from 'node:net';
import { arch, cpus, platform, release, totalmem } from 'node:os';
import path from 'node:path';
import process from 'node:process';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..');
const envPath = path.join(repoRoot, 'deploy', '.env');
const envExamplePath = path.join(repoRoot, 'deploy', '.env.example');
const composePath = path.join(repoRoot, 'deploy', 'docker-compose.prod.yml');

const args = parseArgs(process.argv.slice(2));
const jsonOutput = args.has('json');
const strict = args.has('strict');
const installMode = args.has('install-mode');
const skipNetwork = args.has('skip-network');
const expectedProfile = args.get('profile') || '';

const minimums = {
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
    } else {
      values.set(normalized.slice(0, separator), normalized.slice(separator + 1));
    }
  }
  return values;
}

function output(command, commandArgs, options = {}) {
  const result = spawnSync(command, commandArgs, {
    cwd: options.cwd ?? repoRoot,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: false,
  });
  return {
    ok: result.status === 0,
    status: result.status ?? 1,
    stdout: result.stdout.trim(),
    stderr: result.stderr.trim(),
  };
}

function commandExists(command) {
  return spawnSync('sh', ['-lc', `command -v ${command} >/dev/null 2>&1`], { stdio: 'ignore' }).status === 0;
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
        const separator = line.indexOf('=');
        return [line.slice(0, separator).trim(), line.slice(separator + 1).trim().replace(/^['"]|['"]$/g, '')];
      })
  );
}

function diskFreeGb(targetPath) {
  const result = output('df', ['-Pk', targetPath], { cwd: '/' });
  if (!result.ok) {
    return 0;
  }
  const columns = result.stdout.split(/\r?\n/).at(-1)?.trim().split(/\s+/);
  return columns?.[3] ? Number(columns[3]) / 1024 / 1024 : 0;
}

function detectProfile(cpuCount, memoryGb) {
  return memoryGb <= 5 || cpuCount <= 4 ? 'tiny' : 'standard';
}

function add(checks, status, name, message, details = {}) {
  checks.push({ status, name, message, details });
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

async function main() {
  const checks = [];
  const env = parseEnvFile(envPath);
  const envExample = parseEnvFile(envExamplePath);
  const cpuCount = cpus().length;
  const memoryGb = totalmem() / 1024 / 1024 / 1024;
  const diskGb = diskFreeGb(repoRoot);
  const recommendedProfile = detectProfile(cpuCount, memoryGb);

  add(checks, cpuCount >= minimums.cpu ? 'pass' : 'fail', 'CPU', `${cpuCount} cores detected`, { minimum: minimums.cpu });
  add(checks, memoryGb >= minimums.memoryGb ? 'pass' : 'fail', 'Memory', `${memoryGb.toFixed(1)} GiB detected`, { minimumGb: minimums.memoryGb });
  add(checks, diskGb >= minimums.diskGb ? 'pass' : 'warn', 'Disk', `${diskGb.toFixed(1)} GiB free at ${repoRoot}`, { recommendedGb: minimums.diskGb });
  add(checks, ['linux', 'darwin'].includes(platform()) ? 'pass' : 'warn', 'OS', `${platform()} ${release()} ${arch()}`);
  add(checks, expectedProfile && expectedProfile !== recommendedProfile ? 'warn' : 'pass', 'Capacity profile', `recommended=${recommendedProfile}${expectedProfile ? ` requested=${expectedProfile}` : ''}`);

  const nodeMajor = parseMajorVersion(process.versions.node);
  add(checks, nodeMajor >= minimums.nodeMajor ? 'pass' : 'fail', 'Node.js', process.version, { minimumMajor: minimums.nodeMajor });

  for (const command of ['curl', 'tar', 'gzip', 'sh']) {
    add(checks, commandExists(command) ? 'pass' : 'fail', `Command ${command}`, commandExists(command) ? 'available' : 'missing');
  }

  add(checks, existsSync(composePath) ? 'pass' : 'fail', 'Compose file', composePath);
  add(checks, existsSync(envExamplePath) ? 'pass' : 'fail', 'Env example', envExamplePath);
  add(checks, existsSync(envPath) ? 'pass' : 'warn', 'Env file', existsSync(envPath) ? envPath : 'deploy/.env is not created yet');

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
    add(checks, status, `Env ${key}`, status === 'pass' ? 'configured' : 'missing or placeholder');
  }

  if (envSource.FRONTEND_ORIGIN) {
    add(checks, /^https?:\/\//.test(envSource.FRONTEND_ORIGIN) && !envSource.FRONTEND_ORIGIN.includes('*') ? 'pass' : 'warn', 'Frontend origin', envSource.FRONTEND_ORIGIN);
  }
  if (envSource.API_DOMAIN) {
    add(checks, envSource.API_DOMAIN.includes('.') ? 'pass' : 'warn', 'API domain', envSource.API_DOMAIN);
  }

  const dockerExists = commandExists('docker');
  add(checks, dockerExists ? 'pass' : (installMode ? 'warn' : 'fail'), 'Docker CLI', dockerExists ? 'available' : 'missing');
  if (dockerExists) {
    const dockerVersion = output('docker', ['--version']);
    const dockerMajor = parseMajorVersion(dockerVersion.stdout);
    add(checks, dockerMajor >= minimums.dockerMajor ? 'pass' : 'warn', 'Docker version', dockerVersion.stdout || 'unknown', { recommendedMajor: minimums.dockerMajor });

    const dockerInfo = output('docker', ['info'], { check: false });
    add(checks, dockerInfo.ok ? 'pass' : (installMode ? 'warn' : 'fail'), 'Docker daemon', dockerInfo.ok ? 'running' : (dockerInfo.stderr || 'not running'));

    const compose = output('docker', ['compose', 'version'], { check: false });
    add(checks, compose.ok ? 'pass' : 'fail', 'Docker Compose', compose.ok ? compose.stdout : 'docker compose v2 missing');
  }

  if (!skipNetwork) {
    const apiProxyAvailable = await checkPortAvailability(8000);
    add(checks, apiProxyAvailable ? 'pass' : 'warn', 'Port 8000', apiProxyAvailable ? 'available' : 'already in use');
    const gatewayAvailable = await checkPortAvailability(8081);
    add(checks, gatewayAvailable ? 'pass' : 'warn', 'Port 8081', gatewayAvailable ? 'available' : 'already in use');

    const dbEndpoint = parseDbEndpoint(envSource.DB_URL);
    if (dbEndpoint && !['localhost', '127.0.0.1', 'mysql'].includes(dbEndpoint.host)) {
      const dbReachable = await probeTcp(dbEndpoint.host, dbEndpoint.port);
      add(checks, dbReachable ? 'pass' : 'warn', 'External MySQL TCP', `${dbEndpoint.host}:${dbEndpoint.port} ${dbReachable ? 'reachable' : 'not reachable from here'}`);
    }
  }

  const fatalCount = checks.filter((check) => check.status === 'fail').length;
  const warnCount = checks.filter((check) => check.status === 'warn').length;
  const summary = {
    status: fatalCount > 0 ? 'fail' : warnCount > 0 ? 'warn' : 'pass',
    recommendedProfile,
    cpuCount,
    memoryGb: Number(memoryGb.toFixed(1)),
    diskGb: Number(diskGb.toFixed(1)),
    checks,
  };

  if (jsonOutput) {
    console.log(JSON.stringify(summary, null, 2));
  } else {
    printSummary(summary);
  }

  if (fatalCount > 0 || (strict && warnCount > 0)) {
    process.exit(1);
  }
}

function printSummary(summary) {
  console.log(`[env] status=${summary.status} profile=${summary.recommendedProfile} cpu=${summary.cpuCount} memory=${summary.memoryGb}GiB diskFree=${summary.diskGb}GiB`);
  for (const check of summary.checks) {
    const marker = check.status === 'pass' ? 'OK' : check.status === 'warn' ? 'WARN' : 'FAIL';
    console.log(`[env] ${marker.padEnd(4)} ${check.name}: ${check.message}`);
  }
}

main().catch((error) => {
  console.error(`[env] FAIL ${error.message}`);
  process.exit(1);
});
