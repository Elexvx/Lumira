#!/usr/bin/env node

import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';

import { parseEnvFile, randomSecret, setEnvValue } from './lib/env-utils.mjs';
import { commandExists, output, resolveRepoRoot, run } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const envPath = path.join(repoRoot, 'deploy', '.env');
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
const env = parseEnvFile(envPath);
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
})) {
  envContent = setEnvValue(envContent, key, value);
}

const quoteSystemd = (value) => `"${String(value).replace(/([\\"])/g, '\\$1')}"`;
const unit = `[Unit]
Description=Lumira host update agent
After=docker.service network-online.target
Wants=network-online.target
Requires=docker.service

[Service]
Type=simple
WorkingDirectory=${quoteSystemd(repoRoot)}
EnvironmentFile=${quoteSystemd(envPath)}
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
writeFileSync(servicePath, unit, { mode: 0o644 });
run('systemctl', ['daemon-reload']);
run('systemctl', ['enable', '--now', 'lumira-updater.service']);
run('systemctl', ['is-active', '--quiet', 'lumira-updater.service']);
let healthy = false;
for (let attempt = 0; attempt < 20; attempt += 1) {
  try {
    const response = await fetch(`http://${gateway}:${env.LUMIRA_UPDATER_PORT || '9788'}/v1/health`, {
      headers: { 'x-lumira-updater-token': token },
      signal: AbortSignal.timeout(1_000),
    });
    if (response.ok) {
      healthy = true;
      break;
    }
  } catch {
    // The service can need a moment after systemd reports it active.
  }
  await new Promise((resolve) => setTimeout(resolve, 500));
}
if (!healthy) {
  run('systemctl', ['status', '--no-pager', 'lumira-updater.service'], { check: false });
  throw new Error('lumira-updater did not pass its authenticated health check');
}
console.log(`[updater-install] lumira-updater is active on Docker gateway ${gateway}:9788.`);
