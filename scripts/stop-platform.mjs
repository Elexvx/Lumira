#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import process from 'node:process';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..');
const composeFile = path.join('deploy', 'docker-compose.yml');

const argv = new Set(process.argv.slice(2));
const showHelp = argv.has('--help') || argv.has('-h');
const skipInfra = argv.has('--skip-infra') || argv.has('--no-infra');
const skipServices = argv.has('--skip-services') || argv.has('--no-services');
const skipFrontend = argv.has('--skip-frontend') || argv.has('--no-frontend');

function log(message) {
  console.log(`[stopper] ${message}`);
}

function error(message) {
  console.error(`[stopper] ${message}`);
}

function printUsage() {
  console.log(`Usage: node scripts/stop-platform.mjs [options]

Options:
  --skip-infra      Skip Docker Compose infrastructure shutdown.
  --skip-services   Skip Java backend services shutdown.
  --skip-frontend   Skip frontend shutdown.
  -h, --help        Show this help message.
`);
}

function runCommand(command, args, options = {}) {
  return spawnSync(command, args, {
    cwd: options.cwd ?? repoRoot,
    stdio: options.stdio ?? 'inherit',
    shell: false,
    env: options.env ?? process.env,
  });
}

function commandExists(command, args = ['--version']) {
  const result = spawnSync(command, args, {
    cwd: repoRoot,
    stdio: 'ignore',
    shell: false,
  });
  return result.status === 0;
}

function listProcesses() {
  const result = spawnSync('ps', ['-axo', 'pid=,command='], {
    cwd: repoRoot,
    encoding: 'utf8',
    shell: false,
  });

  if (result.status !== 0) {
    throw new Error('Unable to inspect running processes.');
  }

  return result.stdout
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const match = line.match(/^(\d+)\s+(.*)$/);
      if (!match) {
        return null;
      }
      return { pid: Number(match[1]), command: match[2] };
    })
    .filter(Boolean);
}

function terminatePid(pid, signal = 'SIGTERM') {
  try {
    process.kill(pid, signal);
    return true;
  } catch {
    return false;
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForExit(pid, timeoutMs = 5_000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    try {
      process.kill(pid, 0);
    } catch {
      return true;
    }

    // Give the process a moment to exit after SIGTERM.
    // eslint-disable-next-line no-await-in-loop
    await sleep(150);
  }
  return false;
}

async function killMatchingProcesses({ name, patterns }) {
  const processes = listProcesses();
  const matched = processes.filter((entry) => patterns.some((pattern) => pattern.test(entry.command)));

  if (matched.length === 0) {
    log(`No running ${name} processes found.`);
    return;
  }

  log(`Stopping ${name}: ${matched.map((entry) => entry.pid).join(', ')}`);

  for (const entry of matched) {
    terminatePid(entry.pid, 'SIGTERM');
  }

  for (const entry of matched) {
    if (await waitForExit(entry.pid, 3_000)) {
      continue;
    }
    terminatePid(entry.pid, 'SIGKILL');
  }
}

function stopDockerInfrastructure() {
  if (!commandExists('docker', ['--version'])) {
    log('Docker was not found in PATH. Skipping infrastructure shutdown.');
    return;
  }

  log(`Stopping Docker Compose infrastructure from ${composeFile}...`);
  const result = runCommand('docker', ['compose', '-f', composeFile, 'down']);
  if (result.status !== 0) {
    throw new Error('docker compose down failed.');
  }
}

async function main() {
  if (showHelp) {
    printUsage();
    return;
  }

  if (!skipFrontend) {
    await killMatchingProcesses({
      name: 'frontend',
      patterns: [
        /node .*frontend\/scripts\/console-banner\.mjs/,
        /max dev/,
        /max preview/,
        /pnpm .*--dir frontend dev/,
        /pnpm .*--dir frontend start/,
        /utoopack-dev-server/,
      ],
    });
  }

  if (!skipServices) {
    await killMatchingProcesses({
      name: 'backend services',
      patterns: [
        /mvn .*backend\/pom\.xml .*spring-boot:run/,
        /mvn .*services\/gateway-service\/pom\.xml .*spring-boot:run/,
        /mvn .*services\/auth-service\/pom\.xml .*spring-boot:run/,
        /mvn .*services\/file-service\/pom\.xml .*spring-boot:run/,
        /mvn .*services\/message-service\/pom\.xml .*spring-boot:run/,
        /mvn .*services\/plugin-service\/pom\.xml .*spring-boot:run/,
        /mvn .*services\/localization-service\/pom\.xml .*spring-boot:run/,
        /mvn .*services\/job-executor\/pom\.xml .*spring-boot:run/,
        /com\.legendary\.invention\.saas\.SaasApplication/,
        /com\.legendary\.invention\.gateway\.GatewayServiceApplication/,
        /com\.legendary\.invention\.auth\.AuthServiceApplication/,
        /com\.legendary\.invention\.file\.FileServiceApplication/,
        /com\.legendary\.invention\.message\.MessageServiceApplication/,
        /com\.legendary\.invention\.plugin\.PluginServiceApplication/,
        /com\.legendary\.invention\.localization\.LocalizationServiceApplication/,
        /com\.legendary\.invention\.job\.JobExecutorApplication/,
      ],
    });
  }

  if (!skipInfra) {
    stopDockerInfrastructure();
  }

  log('Shutdown complete.');
}

try {
  await main();
} catch (err) {
  error(err instanceof Error ? err.message : String(err));
  process.exit(1);
}
