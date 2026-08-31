import assert from 'node:assert/strict';
import { chmodSync, copyFileSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const enabled = process.env.LUMIRA_RUN_DATA_PLANE_DOCKER_E2E === 'true';
const repoRoot = path.resolve(import.meta.dirname, '..');

function docker(cwd, args, options = {}) {
  return spawnSync('docker', args, { cwd, encoding: 'utf8', timeout: 180_000, ...options });
}

test('real MySQL roles deny app DDL, isolate XXL schema, and Redis planes preserve runtime data', { skip: !enabled, timeout: 300_000 }, () => {
  const fixture = mkdtempSync(path.join(os.tmpdir(), 'lumira-data-plane-'));
  const project = `lumira-data-plane-${process.pid}`;
  const roleScript = path.join(fixture, 'init-database-roles.sh');
  copyFileSync(path.join(repoRoot, 'deploy', 'database', 'init-database-roles.sh'), roleScript);
  chmodSync(roleScript, 0o700);
  const environment = {
    ...process.env,
    COMPOSE_PROJECT_NAME: project,
    MYSQL_ROOT_PASSWORD: 'root-test-secret',
    DB_PASSWORD: 'app-test-secret',
    DB_MIGRATION_PASSWORD: 'migrator-test-secret',
    MYSQL_BACKUP_PASSWORD: 'backup-test-secret',
    MYSQL_RESTORE_PASSWORD: 'restore-test-secret',
    XXL_JOB_DB_PASSWORD: 'xxl-test-secret',
    MYSQLD_EXPORTER_PASSWORD: 'exporter-test-secret',
    REDIS_CACHE_PASSWORD: 'cache-test-secret',
    REDIS_RUNTIME_PASSWORD: 'runtime-test-secret',
  };
  writeFileSync(path.join(fixture, 'compose.yml'), `services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: \${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: saas
      DB_PASSWORD: \${DB_PASSWORD}
      DB_MIGRATION_PASSWORD: \${DB_MIGRATION_PASSWORD}
      MYSQL_BACKUP_PASSWORD: \${MYSQL_BACKUP_PASSWORD}
      MYSQL_RESTORE_PASSWORD: \${MYSQL_RESTORE_PASSWORD}
      XXL_JOB_DB_PASSWORD: \${XXL_JOB_DB_PASSWORD}
      MYSQLD_EXPORTER_PASSWORD: \${MYSQLD_EXPORTER_PASSWORD}
    volumes:
      - ./init-database-roles.sh:/docker-entrypoint-initdb.d/003-database-roles.sh:ro
  redis-cache:
    image: redis:7.4
    command: [sh, -ec, 'exec redis-server --save "" --appendonly no --maxmemory 3mb --maxmemory-policy allkeys-lru --requirepass "$$REDIS_CACHE_PASSWORD"']
    environment:
      REDIS_CACHE_PASSWORD: \${REDIS_CACHE_PASSWORD}
  redis-runtime:
    image: redis:7.4
    command: [sh, -ec, 'exec redis-server --appendonly yes --appendfsync everysec --maxmemory 3mb --maxmemory-policy noeviction --requirepass "$$REDIS_RUNTIME_PASSWORD"']
    environment:
      REDIS_RUNTIME_PASSWORD: \${REDIS_RUNTIME_PASSWORD}
`, { mode: 0o600 });

  const compose = (...args) => docker(fixture, ['compose', '-f', 'compose.yml', ...args], { env: environment });
  try {
    assert.equal(compose('up', '-d').status, 0);
    let mysqlReady = false;
    for (let attempt = 0; attempt < 60; attempt += 1) {
      const result = compose('exec', '-T', '-e', 'MYSQL_PWD=root-test-secret', 'mysql', 'mysql', '-uroot', '--execute=SELECT 1');
      if (result.status === 0) { mysqlReady = true; break; }
      Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1_000);
    }
    assert.equal(mysqlReady, true, 'MySQL role fixture did not become ready');

    assert.equal(compose('exec', '-T', '-e', 'MYSQL_PWD=root-test-secret', 'mysql', 'mysql', '-uroot', 'saas', '--execute=CREATE TABLE role_contract(id BIGINT PRIMARY KEY)').status, 0);
    assert.equal(compose('exec', '-T', '-e', 'MYSQL_PWD=app-test-secret', 'mysql', 'mysql', '-ulumira_app', 'saas', '--execute=INSERT INTO role_contract VALUES (1)').status, 0);
    assert.notEqual(compose('exec', '-T', '-e', 'MYSQL_PWD=app-test-secret', 'mysql', 'mysql', '-ulumira_app', 'saas', '--execute=CREATE TABLE forbidden_ddl(id BIGINT)').status, 0);
    assert.equal(compose('exec', '-T', '-e', 'MYSQL_PWD=xxl-test-secret', 'mysql', 'mysql', '-uxxl_job', 'xxl_job', '--execute=CREATE TABLE xxl_contract(id BIGINT)').status, 0);
    assert.notEqual(compose('exec', '-T', '-e', 'MYSQL_PWD=xxl-test-secret', 'mysql', 'mysql', '-uxxl_job', 'saas', '--execute=SELECT * FROM role_contract').status, 0);

    assert.equal(compose('exec', '-T', 'redis-runtime', 'redis-cli', '-a', 'runtime-test-secret', 'SET', 'runtime:guard', 'preserved').status, 0);
    const fillCache = compose('exec', '-T', 'redis-cache', 'sh', '-ec', 'for i in $(seq 1 100); do head -c 65536 /dev/zero | redis-cli -a "$REDIS_CACHE_PASSWORD" -x SET "cache:$i" >/dev/null; done');
    assert.equal(fillCache.status, 0);
    const evicted = compose('exec', '-T', 'redis-cache', 'redis-cli', '-a', 'cache-test-secret', 'INFO', 'stats');
    assert.match(evicted.stdout, /evicted_keys:[1-9][0-9]*/u);
    const fillRuntime = compose('exec', '-T', 'redis-runtime', 'sh', '-ec', 'failed=0; for i in $(seq 1 100); do result=$(head -c 65536 /dev/zero | redis-cli -a "$REDIS_RUNTIME_PASSWORD" -x SET "runtime:$i"); case "$result" in OOM*) failed=1; break;; esac; done; test "$failed" = 1');
    assert.equal(fillRuntime.status, 0);
    const guard = compose('exec', '-T', 'redis-runtime', 'redis-cli', '-a', 'runtime-test-secret', 'GET', 'runtime:guard');
    assert.match(guard.stdout, /preserved/u);
  } finally {
    compose('down', '-v', '--remove-orphans');
    rmSync(fixture, { recursive: true, force: true });
  }
});
