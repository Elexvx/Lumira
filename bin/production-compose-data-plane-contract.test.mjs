import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');
const compose = readFileSync(path.join(repoRoot, 'deploy', 'docker-compose.prod.yml'), 'utf8');
const envExample = readFileSync(path.join(repoRoot, 'deploy', '.env.example'), 'utf8');
const alloy = readFileSync(path.join(repoRoot, 'deploy', 'observability', 'alloy.alloy'), 'utf8');
const roleBootstrap = readFileSync(path.join(repoRoot, 'deploy', 'database', 'init-database-roles.sh'), 'utf8');

function serviceBlock(name, nextName) {
  const startMarker = `\n  ${name}:`;
  const start = compose.indexOf(startMarker) + 1;
  const end = nextName ? compose.indexOf(`\n  ${nextName}:`, start + 1) + 1 : compose.indexOf('\nvolumes:', start + 1);
  assert.notEqual(start, -1, `missing ${name}`);
  assert.notEqual(end, -1, `missing boundary after ${name}`);
  return compose.slice(start, end);
}

test('production Compose physically splits cache and runtime Redis with independent safety policies', () => {
  const cache = serviceBlock('redis-cache', 'redis-runtime');
  const runtime = serviceBlock('redis-runtime', 'xxl-job-admin');
  assert.match(cache, /--appendonly no[\s\S]*--maxmemory-policy allkeys-lru/u);
  assert.match(cache, /REDIS_CACHE_PASSWORD/);
  assert.match(cache, /redis_cache_data:\/data/);
  assert.match(cache, /healthcheck:/);
  assert.match(runtime, /--appendonly yes --appendfsync everysec[\s\S]*--maxmemory-policy noeviction/u);
  assert.match(runtime, /REDIS_RUNTIME_PASSWORD/);
  assert.match(runtime, /redis_runtime_data:\/data/);
  assert.match(runtime, /healthcheck:/);
  assert.doesNotMatch(`${cache}\n${runtime}`, /REDIS_DATABASE/);
});

test('runtime services use runtime Redis while both planes expose separate metrics', () => {
  const server = serviceBlock('lumira-server-blue', 'lumira-server-green');
  const async = serviceBlock('lumira-async', 'lumira-job-executor');
  assert.match(server, /REDIS_HOST: \$\{REDIS_RUNTIME_HOST:-redis-runtime}/);
  assert.match(server, /REDIS_CACHE_HOST: \$\{REDIS_CACHE_HOST:-redis-cache}/);
  assert.match(async, /REDIS_HOST: \$\{REDIS_RUNTIME_HOST:-redis-runtime}/);
  assert.match(serviceBlock('redis-exporter', 'redis-runtime-exporter'), /REDIS_CACHE_PASSWORD/);
  assert.match(serviceBlock('redis-runtime-exporter', 'backup-metrics-exporter'), /REDIS_RUNTIME_PASSWORD/);
});

test('Alloy reads only the host log directory and never receives Docker control-plane access', () => {
  const alloyService = serviceBlock('alloy', 'grafana');
  assert.doesNotMatch(alloyService, /docker\.sock/);
  assert.match(alloyService, /\/var\/lib\/docker\/containers:ro/);
  assert.doesNotMatch(alloy, /discovery\.docker|loki\.source\.docker|docker\.sock/);
  assert.match(alloy, /loki\.source\.file "docker_json_logs"/);
});

test('only edge and API proxies join the external 1Panel network', () => {
  const allowed = new Set(['api-proxy', 'edge-proxy']);
  const servicesSection = compose.slice(compose.indexOf('services:\n') + 'services:\n'.length, compose.indexOf('\nvolumes:'));
  const serviceNames = [...servicesSection.matchAll(/^  ([a-z0-9-]+):(?:\s*&[^\n]+)?$/gmu)].map((match) => match[1]);
  for (let index = 0; index < serviceNames.length; index += 1) {
    const name = serviceNames[index];
    const block = serviceBlock(name, serviceNames[index + 1]);
    if (block.includes('- 1panel-network')) assert.ok(allowed.has(name), `${name} must not join 1panel-network`);
  }
  assert.match(serviceBlock('api-proxy', 'lumira-ui-blue'), /- 1panel-network/);
  assert.match(serviceBlock('edge-proxy', 'prometheus'), /- 1panel-network/);
  for (const network of ['edge-network', 'app-network', 'data-network', 'observability-network']) {
    assert.match(compose, new RegExp(`^  ${network}:`, 'mu'));
  }
});

test('XXL-Job and operational database identities are dedicated and fail closed', () => {
  const xxl = serviceBlock('xxl-job-admin', 'api-proxy');
  assert.match(xxl, /XXL_JOB_DB_URL:\?XXL_JOB_DB_URL is required/);
  assert.match(xxl, /XXL_JOB_DB_USERNAME:\?XXL_JOB_DB_USERNAME is required/);
  assert.doesNotMatch(xxl, /spring\.datasource\.username=root|MYSQL_DATABASE:-saas/);
  for (const assignment of [
    'DB_USERNAME=lumira_app',
    'DB_MIGRATION_USERNAME=lumira_migrator',
    'MYSQL_BACKUP_USERNAME=lumira_backup',
    'MYSQL_RESTORE_USERNAME=lumira_restore',
    'XXL_JOB_DB_USERNAME=xxl_job',
    'MYSQLD_EXPORTER_USERNAME=exporter',
  ]) assert.match(envExample, new RegExp(`^${assignment}$`, 'mu'));
});

test('database role bootstrap is idempotent, scoped, and contains no repository password', () => {
  assert.match(roleBootstrap, /CREATE USER IF NOT EXISTS/u);
  assert.match(roleBootstrap, /ALTER USER/u);
  assert.match(roleBootstrap, /REVOKE ALL PRIVILEGES, GRANT OPTION/u);
  assert.match(roleBootstrap, /GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE/u);
  assert.match(roleBootstrap, /GRANT ALL PRIVILEGES ON .*lumira_migrator/u);
  assert.match(roleBootstrap, /GRANT ALL PRIVILEGES ON .*xxl_job/u);
  assert.doesNotMatch(roleBootstrap, /IDENTIFIED BY '[A-Za-z0-9]/u);
  assert.doesNotMatch(roleBootstrap, /change-me/u);
});
