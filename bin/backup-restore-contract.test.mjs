import assert from 'node:assert/strict';
import { createHash, randomBytes } from 'node:crypto';
import {
  chmodSync,
  copyFileSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

import { validateBackupEvidence } from './lib/platform-backup-contract.mjs';

const repoRoot = path.resolve(import.meta.dirname, '..');
const backupPath = path.join(repoRoot, 'deploy', 'backup-platform.sh');
const restorePath = path.join(repoRoot, 'deploy', 'restore-platform.sh');
const backupScript = readFileSync(backupPath, 'utf8');
const restoreScript = readFileSync(restorePath, 'utf8');

const sha256 = (value) => createHash('sha256').update(value).digest('hex');

function bashExecutable() {
  const candidates = process.platform === 'win32'
    ? [process.env.BASH_PATH, 'C:\\Program Files\\Git\\bin\\bash.exe', 'C:\\Program Files\\Git\\usr\\bin\\bash.exe']
    : [process.env.BASH_PATH, '/usr/bin/bash', '/bin/bash'];
  return candidates.find((candidate) => candidate && existsSync(candidate));
}

function bashPath(value) {
  if (process.platform !== 'win32') return value;
  return value.replace(/^([A-Za-z]):[\\/]/, (_, drive) => `/${drive.toLowerCase()}/`).replaceAll('\\', '/');
}

function makeBackupFixture() {
  const directory = mkdtempSync(path.join(tmpdir(), 'lumira-backup-contract-'));
  const dump = '-- Lumira contract fixture\nCREATE TABLE contract_fixture (id BIGINT PRIMARY KEY);\n';
  const dumpName = 'mysql-saas.sql';
  const dumpHash = sha256(dump);
  writeFileSync(path.join(directory, dumpName), dump, { mode: 0o600 });
  writeFileSync(path.join(directory, 'SHA256SUMS'), `${dumpHash}  ${dumpName}\n`, { mode: 0o600 });
  writeFileSync(path.join(directory, '.complete'), '', { mode: 0o600 });
  writeFileSync(path.join(directory, 'manifest.json'), `${JSON.stringify({
    schemaVersion: 1,
    status: 'complete',
    secretsIncluded: false,
    backupId: '20260819T010203Z',
    createdAt: '2026-08-19T01:02:03Z',
    databaseName: 'saas',
    mysql: {
      path: dumpName,
      sha256: dumpHash,
      size: Buffer.byteLength(dump),
      serverVersion: '8.4.6',
      serverUuid: null,
      gtidExecuted: null,
      binlogFile: null,
      binlogPosition: null,
      databaseVersion: null,
      tableCount: 1,
      schemaFingerprint: 'a'.repeat(64),
    },
    redis: null,
    fileStorage: null,
    pluginStorage: null,
  }, null, 2)}\n`, { mode: 0o600 });
  return directory;
}

test('backup script creates an atomic, secret-free, checksummed completion contract', () => {
  assert.equal(backupScript.includes('\r'), false, 'backup script must retain LF line endings for Linux hosts');
  assert.match(backupScript, /^#!\/usr\/bin\/env bash\nset -euo pipefail\n\numask 077/m);
  assert.match(backupScript, /"schemaVersion": 1/);
  assert.match(backupScript, /"status": "complete"/);
  assert.match(backupScript, /"secretsIncluded": false/);
  for (const field of ['backupId', 'createdAt', 'databaseName', 'serverVersion', 'gtidExecuted', 'binlogFile', 'binlogPosition', 'mysql']) {
    assert.match(backupScript, new RegExp(`"${field}"`), `manifest must contain ${field}`);
  }
  assert.match(backupScript, /printf '%s  %s\\n'.*SHA256SUMS/);
  assert.match(backupScript, /: > "\$\{STAGING_DIR\}\/\.complete"/);
  assert.match(backupScript, /mv -- "\$\{STAGING_DIR\}" "\$\{OUT_DIR\}"/);
  assert.match(backupScript, /echo "Backup completed: \$\{OUT_DIR\}"/);
  assert.doesNotMatch(backupScript, /deploy\.env\.snapshot|install .*ENV_FILE/);
});

test('backup credentials, readiness, GTID, retention, upload, TLS, and metrics remain explicit', () => {
  assert.match(backupScript, /MYSQL_BACKUP_USERNAME/);
  assert.match(backupScript, /MYSQL_BACKUP_PASSWORD/);
  assert.match(backupScript, /-e MYSQL_PWD/);
  assert.doesNotMatch(backupScript, /sh -c "MYSQL_PWD=.*MYSQL_PASSWORD/);
  assert.match(backupScript, /--set-gtid-purged=OFF/g);
  assert.match(backupScript, /BACKUP_ALLOW_EMPTY_DATABASE:-0/);
  assert.match(backupScript, /MySQL metadata query failed with a non-readiness error/);
  assert.match(
    backupScript,
    /mysql --batch --raw --skip-column-names --protocol=TCP --get-server-public-key -h127\.0\.0\.1 -P3306/,
    'compose backup readiness must ignore the socket-only temporary initialization server',
  );
  assert.match(
    backupScript,
    /mysqldump --single-transaction[\s\S]{0,300}--protocol=TCP --get-server-public-key -h127\.0\.0\.1 -P3306/,
    'compose dumps must stay bound to the final TCP-serving MySQL instance',
  );
  assert.match(backupScript, /Redis authentication failed; not retrying/);
  assert.match(backupScript, /"\$\{DB_HOST\}" == "\$\{MYSQL_SERVICE\}" \|\| "\$\{DB_HOST\}" == "mysql" \|\| "\$\{DB_HOST\}" == "lumira-mysql"/);
  assert.match(backupScript, /BACKUP_RETENTION_DAYS:-0/);
  assert.match(backupScript, /BACKUP_UPLOAD_HOOK:-/);
  assert.match(backupScript, /MYSQL_SSL_MODE/);
  assert.match(backupScript, /MYSQL_SSL_CA_FILE/);
  assert.match(backupScript, /mysql-ca\.pem:ro/);
  assert.match(backupScript, /lumira_mysql_backup_last_success_timestamp_seconds/);
  assert.match(backupScript, /lumira_mysql_backup_dump_bytes/);
  assert.match(backupScript, /lumira_mysql_backup_info\{backup_id=/);
  assert.match(backupScript, /BACKUP_METRICS_FILE="\$\{ROOT_DIR\}\/\$\{BACKUP_METRICS_FILE\}"/);
  assert.match(backupScript, /must not be a symbolic link or directory/);
  assert.match(backupScript, /declare -p "\$\{key\}"/, 'caller environment must win over deploy/.env');
});

test('restore fails closed before writes and verifies manifest, checksums, structure, and version', () => {
  assert.equal(restoreScript.includes('\r'), false, 'restore script must retain LF line endings for Linux hosts');
  assert.match(restoreScript, /manifest\.schemaVersion !== 1/);
  assert.match(restoreScript, /command -v node\.exe/);
  assert.match(restoreScript, /wslpath -w/);
  assert.match(restoreScript, /manifest\.status !== 'complete'/);
  assert.match(restoreScript, /manifest\.secretsIncluded !== false/);
  assert.match(restoreScript, /fs\.lstatSync\(file\)/);
  assert.match(restoreScript, /regular non-symlink file/);
  assert.match(restoreScript, /resolves outside the backup directory/);
  assert.match(restoreScript, /SHA256SUMS must list every manifest artifact exactly once/);
  assert.match(restoreScript, /fs\.readSync\(descriptor, buffer/);
  assert.doesNotMatch(restoreScript, /readFileSync\(absolutePath\)/);
  assert.match(restoreScript, /RESTORE_ISOLATED:/);
  assert.match(restoreScript, /RESTORE_PRODUCTION:/);
  assert.match(restoreScript, /RESTORE_RECREATE_TARGET/);
  assert.match(restoreScript, /RESTORE_WRITES_PAUSED/);
  assert.match(restoreScript, /Production restore target .* does not match the application database/);
  assert.match(restoreScript, /schema fingerprint does not match/);
  assert.match(restoreScript, /database version metadata does not match/);
  assert.match(restoreScript, /MYSQL_SSL_MODE/);
  assert.match(restoreScript, /MYSQL_SSL_CA_FILE/);
  assert.match(restoreScript, /mysql-ca\.pem:ro/);
  assert.match(restoreScript, /REDISCLI_AUTH="\$REDIS_PASSWORD"/);
  assert.match(restoreScript, /Legacy deploy\.env\.snapshot contains secrets and is not accepted/);
});

const bash = bashExecutable();

test('shell scripts pass bash syntax validation when bash is available', { skip: !bash }, () => {
  for (const script of [backupPath, restorePath]) {
    const result = spawnSync(bash, ['-n', bashPath(script)], { encoding: 'utf8' });
    assert.equal(result.status, 0, `${path.basename(script)} syntax error:\n${result.stderr}`);
  }
});

test('restore dry-run validates a complete backup without Docker and detects corruption', { skip: !bash }, () => {
  const fixture = makeBackupFixture();
  try {
    const environment = {
      ...process.env,
      DRY_RUN: '1',
      RESTORE_MODE: 'isolated',
      RESTORE_TARGET_DATABASE: 'saas_restore_contract',
    };
    const valid = spawnSync(bash, [bashPath(restorePath), bashPath(fixture)], {
      cwd: repoRoot,
      env: environment,
      encoding: 'utf8',
    });
    assert.equal(valid.status, 0, valid.stderr);
    assert.match(valid.stdout, /Backup validation passed/);
    assert.match(valid.stdout, /No MySQL, Redis, file-storage, plugin-storage, or environment writes were performed/);

    writeFileSync(path.join(fixture, 'mysql-saas.sql'), '-- corrupted after checksums\n', { mode: 0o600 });
    const corrupt = spawnSync(bash, [bashPath(restorePath), bashPath(fixture)], {
      cwd: repoRoot,
      env: environment,
      encoding: 'utf8',
    });
    assert.notEqual(corrupt.status, 0);
    assert.match(corrupt.stderr, /Artifact (size|SHA-256) mismatch/);
  } finally {
    chmodSync(fixture, 0o700);
    rmSync(fixture, { recursive: true, force: true });
  }
});

test('restore dry-run rejects a checksummed artifact symlink that escapes the backup directory', { skip: !bash }, (context) => {
  const fixture = makeBackupFixture();
  const dumpPath = path.join(fixture, 'mysql-saas.sql');
  const outsidePath = path.join(tmpdir(), `lumira-outside-dump-${process.pid}-${Date.now()}.sql`);
  try {
    writeFileSync(outsidePath, readFileSync(dumpPath), { mode: 0o600 });
    rmSync(dumpPath);
    try {
      symlinkSync(outsidePath, dumpPath, 'file');
    } catch (error) {
      context.skip(`filesystem symlinks are unavailable: ${error.code || error.message}`);
      return;
    }
    const result = spawnSync(bash, [bashPath(restorePath), bashPath(fixture)], {
      cwd: repoRoot,
      env: {
        ...process.env,
        DRY_RUN: '1',
        RESTORE_MODE: 'isolated',
        RESTORE_TARGET_DATABASE: 'saas_restore_symlink_contract',
      },
      encoding: 'utf8',
    });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /regular non-symlink file/);
  } finally {
    rmSync(fixture, { recursive: true, force: true });
    rmSync(outsidePath, { force: true });
  }
});

test('real MySQL 8.4 and Redis backup survives validated isolated restore', {
  skip: !bash || process.env.LUMIRA_BACKUP_DOCKER_E2E !== 'true',
  timeout: 240_000,
}, () => {
  const fixture = mkdtempSync(path.join(tmpdir(), 'lumira-backup-docker-e2e-'));
  const deployDirectory = path.join(fixture, 'deploy');
  const backupRoot = path.join(fixture, 'backups');
  const composePath = path.join(deployDirectory, 'docker-compose.prod.yml');
  const project = `lumirabackup${process.pid}${Date.now()}`.toLowerCase();
  const backupId = '20260819T010203Z';
  const databasePassword = randomBytes(18).toString('hex');
  const redisPassword = randomBytes(18).toString('hex');
  const dockerWslWrapper = process.platform === 'win32' ? 'C:\\Program Files\\DockerWSL\\docker.cmd' : '';
  const usesWslDocker = Boolean(dockerWslWrapper && existsSync(dockerWslWrapper));
  const wslDistro = usesWslDocker
    ? readFileSync(dockerWslWrapper, 'utf8').match(/wsl\s+-d\s+(\S+)/i)?.[1]
    : null;
  assert.equal(usesWslDocker && !wslDistro, false, 'Unable to determine the Docker WSL distribution.');
  const runtimePath = (value) => {
    if (!usesWslDocker) return process.platform === 'win32' ? bashPath(value) : value;
    return value.replace(/^([A-Za-z]):[\\/]/, (_, drive) => `/mnt/${drive.toLowerCase()}/`).replaceAll('\\', '/');
  };
  const withWslEnvironment = (value) => {
    if (!usesWslDocker) return value;
    const forwarded = ['MYSQL_PWD', 'RESTORE_MODE', 'RESTORE_TARGET_DATABASE', 'RESTORE_CONFIRM'];
    const existing = String(value.WSLENV || '').split(':').filter(Boolean);
    return { ...value, WSLENV: [...new Set([...existing, ...forwarded])].join(':') };
  };
  const spawnDocker = (arguments_, options = {}) => usesWslDocker
    ? spawnSync('wsl.exe', ['-d', wslDistro, '-u', 'root', '--', 'docker', ...arguments_], {
        encoding: 'utf8',
        ...options,
        env: withWslEnvironment(options.env || process.env),
      })
    : spawnSync('docker', arguments_, { encoding: 'utf8', ...options });
  const spawnRuntimeScript = (script, arguments_, options = {}) => usesWslDocker
    ? spawnSync('wsl.exe', ['-d', wslDistro, '-u', 'root', '--', 'bash', runtimePath(script), ...arguments_.map(runtimePath)], {
        encoding: 'utf8',
        ...options,
        env: withWslEnvironment(options.env || process.env),
      })
    : spawnSync(bash, [bashPath(script), ...arguments_.map(bashPath)], { encoding: 'utf8', ...options });
  const environment = {
    ...process.env,
  };
  const composeArguments = ['compose', '-f', runtimePath(composePath)];
  const docker = (arguments_, options = {}) => spawnDocker([...composeArguments, ...arguments_], {
    cwd: fixture,
    env: environment,
    timeout: 180_000,
    ...options,
  });
  const dockerVersion = spawnDocker(['version', '--format', '{{.Server.Version}}'], { env: environment });
  assert.equal(dockerVersion.status, 0, `Docker is required for the opted-in backup E2E test:\n${dockerVersion.stderr}`);
  const requireSuccess = (result, label) => {
    assert.equal(result.status, 0, `${label} failed:\n${result.stdout || ''}\n${result.stderr || ''}`);
    return result.stdout;
  };

  mkdirSync(deployDirectory, { recursive: true });
  copyFileSync(backupPath, path.join(deployDirectory, 'backup-platform.sh'));
  copyFileSync(restorePath, path.join(deployDirectory, 'restore-platform.sh'));
  writeFileSync(composePath, `services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: \${DB_PASSWORD}
      MYSQL_DATABASE: saas
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --server-id=1
      - --log-bin=lumira-bin
    volumes:
      - mysql_data:/var/lib/mysql
  redis:
    image: redis:7.4
    environment:
      REDIS_PASSWORD: \${REDIS_PASSWORD}
    command: ['sh', '-c', 'exec redis-server --appendonly yes --requirepass "$$REDIS_PASSWORD"']
    volumes:
      - redis_data:/data
volumes:
  mysql_data:
  redis_data:
`, { mode: 0o600 });
  writeFileSync(path.join(deployDirectory, '.env'), [
    `DB_PASSWORD=${databasePassword}`,
    'DB_USERNAME=root',
    'DB_URL=jdbc:mysql://mysql:3306/saas?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false',
    `REDIS_PASSWORD=${redisPassword}`,
    'DB_BACKUP_NETWORK=unused-in-compose-mode',
    'BACKUP_REDIS=1',
    `COMPOSE_PROJECT_NAME=${project}`,
    `BACKUP_ROOT=${runtimePath(backupRoot)}`,
    `BACKUP_ID=${backupId}`,
    'BACKUP_METRICS_FILE=deploy/.generated/backup-metrics/lumira-mysql.prom',
    'MYSQL_SSL_MODE=DISABLED',
    '',
  ].join('\n'), { mode: 0o600 });

  let cleanupOutput = '';
  try {
    requireSuccess(docker(['up', '-d', 'mysql', 'redis']), 'temporary MySQL/Redis startup');

    const seedSql = [
      'CREATE TABLE IF NOT EXISTS contract_fixture (id BIGINT PRIMARY KEY, name VARCHAR(64) NOT NULL)',
      "INSERT INTO contract_fixture VALUES (1, 'before-backup') ON DUPLICATE KEY UPDATE name = VALUES(name)",
      'CREATE TABLE IF NOT EXISTS sys_config (config_key VARCHAR(128) PRIMARY KEY, config_value TEXT, deleted TINYINT NOT NULL DEFAULT 0)',
      "INSERT INTO sys_config VALUES ('platform.database.version', '{\"databaseVersion\":\"contract-v1\"}', 0) ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), deleted = 0",
    ].join('; ');
    let seeded = false;
    for (let attempt = 0; attempt < 60; attempt += 1) {
      const result = docker(['exec', '-T', '-e', 'MYSQL_PWD', 'mysql', 'mysql', '--ssl-mode=DISABLED', '-uroot', '--execute', seedSql, 'saas'], {
        env: { ...environment, MYSQL_PWD: databasePassword },
        timeout: 10_000,
      });
      if (result.status === 0) {
        seeded = true;
        break;
      }
      Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1_000);
    }
    assert.equal(seeded, true, 'temporary MySQL did not become ready for seed data');

    const backup = spawnRuntimeScript(path.join(deployDirectory, 'backup-platform.sh'), [], {
      cwd: fixture,
      env: environment,
      timeout: 180_000,
    });
    requireSuccess(backup, 'real platform backup');
    const backupDirectory = path.join(backupRoot, backupId);
    const evidence = validateBackupEvidence(backupDirectory, {
      now: Date.now(),
      maxAgeMs: 15 * 60_000,
      minimumDumpBytes: 1024,
      expectedDatabaseName: 'saas',
    });
    assert.equal(evidence.databaseName, 'saas');
    const manifest = JSON.parse(readFileSync(path.join(backupDirectory, 'manifest.json'), 'utf8'));
    assert.equal(manifest.mysql.tableCount, 2);
    assert.match(manifest.mysql.schemaFingerprint, /^[a-f0-9]{64}$/);
    assert.equal(typeof manifest.redis?.sha256, 'string');

    const restoreTarget = 'saas_restore_e2e';
    const restore = spawnRuntimeScript(path.join(deployDirectory, 'restore-platform.sh'), [backupDirectory], {
      cwd: fixture,
      env: {
        ...environment,
        RESTORE_MODE: 'isolated',
        RESTORE_TARGET_DATABASE: restoreTarget,
        RESTORE_CONFIRM: `RESTORE_ISOLATED:${restoreTarget}`,
      },
      timeout: 180_000,
    });
    requireSuccess(restore, 'isolated MySQL restore');
    assert.match(restore.stdout, new RegExp(`tables=${manifest.mysql.tableCount}, schema=${manifest.mysql.schemaFingerprint}`));

    const restoredCount = requireSuccess(docker([
      'exec', '-T', '-e', 'MYSQL_PWD', 'mysql', 'mysql', '--batch', '--raw', '--skip-column-names',
      '--ssl-mode=DISABLED', '-uroot', '--execute',
      "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
      restoreTarget,
    ], { env: { ...environment, MYSQL_PWD: databasePassword } }), 'restored table-count query').trim();
    assert.equal(restoredCount, String(manifest.mysql.tableCount));

    const schemaInventory = requireSuccess(docker([
      'exec', '-T', '-e', 'MYSQL_PWD', 'mysql', 'mysql', '--batch', '--raw', '--skip-column-names',
      '--ssl-mode=DISABLED', '-uroot', '--execute',
      `SELECT CONCAT_WS(CHAR(9), table_name, ordinal_position, column_name, column_type, is_nullable, IFNULL(column_default, '<NULL>'), extra) FROM information_schema.columns WHERE table_schema = '${restoreTarget}' ORDER BY table_name, ordinal_position`,
    ], { env: { ...environment, MYSQL_PWD: databasePassword } }), 'restored schema inventory query');
    assert.equal(sha256(schemaInventory), manifest.mysql.schemaFingerprint);

    const restoredValue = requireSuccess(docker([
      'exec', '-T', '-e', 'MYSQL_PWD', 'mysql', 'mysql', '--batch', '--raw', '--skip-column-names',
      '--ssl-mode=DISABLED', '-uroot', '--execute', 'SELECT name FROM contract_fixture WHERE id = 1', restoreTarget,
    ], { env: { ...environment, MYSQL_PWD: databasePassword } }), 'restored row query').trim();
    assert.equal(restoredValue, 'before-backup');
  } finally {
    const cleanup = docker(['down', '-v', '--remove-orphans'], { timeout: 180_000 });
    cleanupOutput = `${cleanup.stdout || ''}\n${cleanup.stderr || ''}`;
    const remainingContainers = spawnDocker(['ps', '-aq', '--filter', `label=com.docker.compose.project=${project}`], { env: environment }).stdout.trim();
    const remainingVolumes = spawnDocker(['volume', 'ls', '-q', '--filter', `label=com.docker.compose.project=${project}`], { env: environment }).stdout.trim();
    rmSync(fixture, { recursive: true, force: true });
    assert.equal(cleanup.status, 0, `temporary Docker cleanup failed:\n${cleanupOutput}`);
    assert.equal(remainingContainers, '', `temporary containers remain for ${project}`);
    assert.equal(remainingVolumes, '', `temporary volumes remain for ${project}`);
  }
});
