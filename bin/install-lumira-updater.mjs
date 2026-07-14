#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';

import { parseEnvFile, randomSecret, setEnvValue } from './lib/env-utils.mjs';
import { commandExists, output, resolveRepoRoot, run } from './lib/exec-utils.mjs';
import { probeHttp } from './lib/http-utils.mjs';
import { createInitialDeploymentState, renderActiveUpstreams } from './lib/platform-update-contract.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const argumentValue = (name) => {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : null;
};
const deployDir = path.resolve(argumentValue('--deploy-dir') || process.env.LUMIRA_DEPLOY_DIR || path.join(repoRoot, 'deploy'));
const envPath = path.join(deployDir, '.env');
const buildIdentityPath = path.join(deployDir, 'build-identity.env');
const updaterPath = path.join(repoRoot, 'bin', 'lumira-updater.mjs');
const servicePath = '/etc/systemd/system/lumira-updater.service';
const dryRun = process.argv.includes('--dry-run');

if (process.platform !== 'linux') {
  console.log('[updater-install] Skipped: systemd installation is only supported on Linux.');
  process.exit(0);
}
if (!commandExists('systemctl')) {
  throw new Error('systemctl is required to install lumira-updater');
}
if (!existsSync(envPath)) {
  throw new Error('deploy/.env must exist before installing lumira-updater');
}

const gateway = output('docker', ['network', 'inspect', 'bridge', '--format', '{{(index .IPAM.Config 0).Gateway}}'], {
  cwd: repoRoot,
}).trim();
if (!gateway) {
  throw new Error('Unable to resolve the Docker host gateway');
}

let envContent = readFileSync(envPath, 'utf8');
const env = {
  ...parseEnvFile(envPath),
  ...(existsSync(buildIdentityPath) ? parseEnvFile(buildIdentityPath) : {}),
};
const token = !env.PLATFORM_UPDATE_AGENT_TOKEN || env.PLATFORM_UPDATE_AGENT_TOKEN.startsWith('change-me')
  ? randomSecret('updater-token')
  : env.PLATFORM_UPDATE_AGENT_TOKEN;
for (const [key, value] of Object.entries({
  PLATFORM_UPDATE_MANIFEST_URL: env.PLATFORM_UPDATE_MANIFEST_URL || 'https://api.github.com/repos/Elexvx/Lumira/releases/tags/continuous',
  PLATFORM_UPDATE_AGENT_URL: 'http://host.docker.internal:9788',
  PLATFORM_UPDATE_AGENT_ALLOWED_HOSTS: 'host.docker.internal',
  PLATFORM_UPDATE_AGENT_TOKEN: token,
  LUMIRA_UPDATER_HOST: gateway,
  LUMIRA_UPDATER_PORT: env.LUMIRA_UPDATER_PORT || '9788',
  LUMIRA_DEPLOY_DIR: deployDir,
  LUMIRA_ACTIVE_SLOT: env.LUMIRA_ACTIVE_SLOT || 'blue',
  LUMIRA_SERVER_BLUE_IMAGE: env.LUMIRA_SERVER_BLUE_IMAGE || env.LUMIRA_SERVER_IMAGE,
  LUMIRA_SERVER_GREEN_IMAGE: env.LUMIRA_SERVER_GREEN_IMAGE || env.LUMIRA_SERVER_IMAGE,
  LUMIRA_SERVER_BLUE_APP_VERSION: env.LUMIRA_SERVER_BLUE_APP_VERSION || env.APP_VERSION,
  LUMIRA_SERVER_GREEN_APP_VERSION: env.LUMIRA_SERVER_GREEN_APP_VERSION || env.APP_VERSION,
  LUMIRA_SERVER_BLUE_BUILD_VERSION: env.LUMIRA_SERVER_BLUE_BUILD_VERSION || env.BUILD_VERSION,
  LUMIRA_SERVER_GREEN_BUILD_VERSION: env.LUMIRA_SERVER_GREEN_BUILD_VERSION || env.BUILD_VERSION,
  LUMIRA_SERVER_BLUE_BUILD_TIME: env.LUMIRA_SERVER_BLUE_BUILD_TIME || env.BUILD_TIME,
  LUMIRA_SERVER_GREEN_BUILD_TIME: env.LUMIRA_SERVER_GREEN_BUILD_TIME || env.BUILD_TIME,
  LUMIRA_SERVER_BLUE_GIT_COMMIT: env.LUMIRA_SERVER_BLUE_GIT_COMMIT || env.GIT_COMMIT,
  LUMIRA_SERVER_GREEN_GIT_COMMIT: env.LUMIRA_SERVER_GREEN_GIT_COMMIT || env.GIT_COMMIT,
  LUMIRA_SERVER_BLUE_DATABASE_VERSION: env.LUMIRA_SERVER_BLUE_DATABASE_VERSION || env.DATABASE_VERSION,
  LUMIRA_SERVER_GREEN_DATABASE_VERSION: env.LUMIRA_SERVER_GREEN_DATABASE_VERSION || env.DATABASE_VERSION,
})) {
  envContent = setEnvValue(envContent, key, value);
}

const quoteSystemd = (value) => `"${String(value).replace(/([\\"])/g, '\\$1')}"`;
const bareSystemdPath = (value) => {
  const normalized = String(value);
  if (/[\s"\\]/.test(normalized)) {
    throw new Error(`systemd path must not contain whitespace, quotes, or backslashes: ${normalized}`);
  }
  return normalized;
};
const unit = `[Unit]
Description=Lumira host update agent
After=docker.service network-online.target
Wants=network-online.target
Requires=docker.service

[Service]
Type=simple
WorkingDirectory=${bareSystemdPath(repoRoot)}
EnvironmentFile=${bareSystemdPath(envPath)}
Environment=LUMIRA_DEPLOY_DIR=${bareSystemdPath(deployDir)}
ExecStart=${quoteSystemd(process.execPath)} ${quoteSystemd(updaterPath)}
Restart=always
RestartSec=3
UMask=0077

[Install]
WantedBy=multi-user.target
`;

if (dryRun) {
  console.log(`[updater-install] DRY would bind lumira-updater to Docker gateway ${gateway}.`);
  console.log(`[updater-install] DRY would write ${servicePath} and enable the service.`);
  process.exit(0);
}

writeFileSync(envPath, envContent, { mode: 0o600 });

const generatedDir = path.join(deployDir, '.generated', 'api-proxy');
const statePath = path.join(deployDir, '.update-state.json');
mkdirSync(generatedDir, { recursive: true });
if (!existsSync(statePath)) {
  const activeSlot = env.LUMIRA_ACTIVE_SLOT === 'green' ? 'green' : 'blue';
  const slotPrefix = `LUMIRA_SERVER_${activeSlot.toUpperCase()}_`;
  const initialState = createInitialDeploymentState({
    activeSlot,
    serverImage: env[`${slotPrefix}IMAGE`] || env.LUMIRA_SERVER_IMAGE,
    commit: env[`${slotPrefix}GIT_COMMIT`] || env.GIT_COMMIT,
    version: env[`${slotPrefix}APP_VERSION`] || env.APP_VERSION,
    buildVersion: env[`${slotPrefix}BUILD_VERSION`] || env.BUILD_VERSION,
    buildTime: env[`${slotPrefix}BUILD_TIME`] || env.BUILD_TIME,
    databaseVersion: env[`${slotPrefix}DATABASE_VERSION`] || env.DATABASE_VERSION,
  });
  writeFileSync(statePath, `${JSON.stringify(initialState, null, 2)}\n`, { mode: 0o600 });
}
writeFileSync(path.join(generatedDir, 'active-upstreams.conf'), renderActiveUpstreams(env.LUMIRA_ACTIVE_SLOT || 'blue', env), { mode: 0o644 });

const containerRunning = (name) => output('docker', ['inspect', '-f', '{{.State.Running}}', name], { cwd: repoRoot, check: false }).trim() === 'true';
const containerNetworks = (name) => JSON.parse(output('docker', ['inspect', '-f', '{{json .NetworkSettings.Networks}}', name], { cwd: repoRoot }));
const stopBlueSlot = () => run('docker', ['stop', '--time', '10', 'lumira-server-blue'], { cwd: repoRoot, check: false });
const legacyRunning = containerRunning('lumira-server');
if (legacyRunning) {
  const composeEnvArgs = ['--env-file', envPath];
  if (existsSync(buildIdentityPath)) composeEnvArgs.push('--env-file', buildIdentityPath);
  if (!containerRunning('lumira-server-blue')) {
    run('docker', ['compose', ...composeEnvArgs, '-f', path.join(deployDir, 'docker-compose.prod.yml'), '--profile', 'blue', 'up', '-d', '--no-deps', 'lumira-server-blue'], { cwd: repoRoot });
  }
  const blueNetworks = containerNetworks('lumira-server-blue');
  const proxyNetworks = containerNetworks('lumira-api-proxy');
  const sharedNetwork = Object.keys(proxyNetworks).find((name) => blueNetworks[name]?.IPAddress);
  if (!sharedNetwork) {
    stopBlueSlot();
    throw new Error('Blue slot does not share a Docker network with lumira-api-proxy; legacy server remains active.');
  }
  const blueAddress = blueNetworks[sharedNetwork].IPAddress;
  let blueHealthy = false;
  for (let attempt = 0; attempt < 60; attempt += 1) {
    const response = await probeHttp(`http://${blueAddress}:8080/actuator/health`, { timeoutMs: 1_000 });
    if (response.ok) { blueHealthy = true; break; }
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
  if (!blueHealthy) {
    stopBlueSlot();
    throw new Error('Blue slot did not become healthy; legacy server remains active.');
  }

  const proxyConfigNames = output('docker', ['exec', 'lumira-api-proxy', 'ls', '-1', '/etc/nginx/conf.d'], { cwd: repoRoot })
    .split(/\r?\n/)
    .filter((name) => name.endsWith('.conf'));
  let liveConfigPath = null;
  let liveConfig = null;
  for (const configName of proxyConfigNames) {
    const candidatePath = path.posix.join('/etc/nginx/conf.d', configName);
    const candidate = output('docker', ['exec', 'lumira-api-proxy', 'cat', candidatePath], { cwd: repoRoot });
    if (candidate.includes('set $gateway_upstream')) {
      liveConfigPath = candidatePath;
      liveConfig = candidate;
      break;
    }
  }
  if (!liveConfigPath || !liveConfig) {
    stopBlueSlot();
    throw new Error('Active API proxy config was not found; refusing to stop the legacy server.');
  }
  const setPattern = /^\s*set \$(?:gateway_upstream|system_upstream|auth_upstream|file_upstream|message_upstream|plugin_upstream|payment_upstream|localization_upstream|team_upstream|ai_upstream)\s+[^;]+;\s*\r?\n/gm;
  const withoutStaticUpstreams = liveConfig.replace(setPattern, '');
  const patchedConfig = withoutStaticUpstreams.replace(
    /(\s*resolver\s+127\.0\.0\.11[^;]*;\s*\r?\n)/,
    '$1    include /etc/nginx/lumira-upstreams/active-upstreams.conf;\n',
  );
  if (!patchedConfig.includes('active-upstreams.conf')) {
    stopBlueSlot();
    throw new Error('Legacy API proxy upstream was not found; refusing to stop the legacy server.');
  }
  const temporaryConfig = path.join(deployDir, '.generated', 'legacy-blue-api.conf');
  writeFileSync(temporaryConfig, patchedConfig, { mode: 0o600 });
  try {
    run('docker', ['exec', 'lumira-api-proxy', 'cp', liveConfigPath, '/tmp/lumira-legacy-api.conf'], { cwd: repoRoot });
    run('docker', ['exec', 'lumira-api-proxy', 'mkdir', '-p', '/etc/nginx/lumira-upstreams'], { cwd: repoRoot });
    run('docker', ['cp', path.join(generatedDir, 'active-upstreams.conf'), 'lumira-api-proxy:/etc/nginx/lumira-upstreams/active-upstreams.conf'], { cwd: repoRoot });
    run('docker', ['cp', temporaryConfig, 'lumira-api-proxy:/tmp/lumira-blue-api.conf'], { cwd: repoRoot });
    run('docker', ['exec', 'lumira-api-proxy', 'cp', '/tmp/lumira-blue-api.conf', liveConfigPath], { cwd: repoRoot });
    run('docker', ['exec', 'lumira-api-proxy', 'nginx', '-t'], { cwd: repoRoot });
    run('docker', ['exec', 'lumira-api-proxy', 'nginx', '-s', 'reload'], { cwd: repoRoot });
    await new Promise((resolve) => setTimeout(resolve, 60_000));
    run('docker', ['stop', '--time', '10', 'lumira-server'], { cwd: repoRoot });
  } catch (error) {
    run('docker', ['exec', 'lumira-api-proxy', 'cp', '/tmp/lumira-legacy-api.conf', liveConfigPath], { cwd: repoRoot, check: false });
    run('docker', ['exec', 'lumira-api-proxy', 'nginx', '-t'], { cwd: repoRoot, check: false });
    run('docker', ['exec', 'lumira-api-proxy', 'nginx', '-s', 'reload'], { cwd: repoRoot, check: false });
    stopBlueSlot();
    throw error;
  } finally {
    rmSync(temporaryConfig, { force: true });
  }
}

writeFileSync(servicePath, unit, { mode: 0o644 });
run('systemctl', ['daemon-reload']);
run('systemctl', ['enable', 'lumira-updater.service']);
run('systemctl', ['restart', 'lumira-updater.service']);
run('systemctl', ['is-active', '--quiet', 'lumira-updater.service']);
let healthy = false;
for (let attempt = 0; attempt < 20; attempt += 1) {
  const response = await probeHttp(`http://${gateway}:${env.LUMIRA_UPDATER_PORT || '9788'}/v1/health`, {
    headers: { 'x-lumira-updater-token': token },
    timeoutMs: 1_000,
  });
  if (response.ok) {
    healthy = true;
    break;
  }
  // The service can need a moment after systemd reports it active.
  await new Promise((resolve) => setTimeout(resolve, 500));
}
if (!healthy) {
  run('systemctl', ['status', '--no-pager', 'lumira-updater.service'], { check: false });
  throw new Error('lumira-updater did not pass its authenticated health check');
}
console.log(`[updater-install] lumira-updater is active on Docker gateway ${gateway}:9788.`);
