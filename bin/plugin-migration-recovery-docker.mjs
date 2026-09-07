#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const mysqlImage = process.env.LUMIRA_PLUGIN_DRILL_MYSQL_IMAGE || 'mysql:8.4';
const javaImage = process.env.LUMIRA_PLUGIN_DRILL_JAVA_IMAGE || 'eclipse-temurin:21-jre';
const rootPassword = 'lumira-plugin-drill-root';
const migratorPassword = 'lumira-plugin-drill-password';
const databaseName = 'lumira';
const databaseUser = 'lumira_migrator';
const releaseId = 'v-plugin-drill-1';
const packageDigest = 'a'.repeat(64);
const stepName = 'V1__plugin_drill.sql';
const runToken = `${process.pid}-${Date.now()}`;
const networkName = `lumira-plugin-drill-net-${runToken}`;
const mysqlName = `lumira-plugin-drill-mysql-${runToken}`;
const javaContainerPrefix = `lumira-plugin-drill-migrator-${runToken}`;

function command(program, args, options = {}) {
  const result = spawnSync(program, args, {
    cwd: repoRoot,
    encoding: 'utf8',
    input: options.input,
    maxBuffer: 8 * 1024 * 1024,
    stdio: options.stdio || ['pipe', 'pipe', 'pipe'],
  });
  return {
    ...result,
    stdout: result.stdout || '',
    stderr: result.stderr || '',
  };
}

function docker(args, options = {}) {
  return command('docker', args, options);
}

function requireSuccess(result, action) {
  if (result.status !== 0) {
    const detail = `${result.stderr}\n${result.stdout}`.trim().slice(-4000);
    throw new Error(`${action} failed${detail ? `: ${detail}` : ''}`);
  }
  return result.stdout.trim();
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function waitFor(description, predicate, timeoutMs = 60_000, intervalMs = 500) {
  const deadline = Date.now() + timeoutMs;
  let lastError;
  while (Date.now() < deadline) {
    try {
      const value = await predicate();
      if (value) return value;
    } catch (error) {
      lastError = error;
    }
    await sleep(intervalMs);
  }
  throw new Error(`${description} timed out${lastError ? `: ${lastError.message}` : ''}`);
}

function mysqlExec(sql) {
  return requireSuccess(docker([
    'exec', '-i', mysqlName, 'mysql', '--protocol=socket', '--batch', '--skip-column-names',
    '-uroot', `-p${rootPassword}`, databaseName,
  ], { input: `${sql.trim()}\n` }), 'mysql command');
}

function mysqlRows(sql) {
  const output = mysqlExec(sql);
  if (!output) return [];
  return output.split('\n').filter(Boolean).map((line) => line.split('\t'));
}

function mysqlScalar(sql) {
  const rows = mysqlRows(sql);
  return rows[0]?.[0] ?? null;
}

function sqlLiteral(value) {
  if (value === null || value === undefined) return 'NULL';
  return `'${String(value).replaceAll("'", "''")}'`;
}

function migrationDigest(pluginCode, sql) {
  const values = [
    pluginCode, '1.0.0', packageDigest, '1', 'EXPAND', 'APPLICATION_ONLY',
    releaseId, `plugin_${pluginCode}_`, stepName, sql,
  ];
  const hash = createHash('sha256');
  for (const value of values) hash.update(`${value}\0`, 'utf8');
  return hash.digest('hex');
}

function resetSchema() {
  mysqlExec(`
    SET FOREIGN_KEY_CHECKS = 0;
    DROP TABLE IF EXISTS plugin_sms_message, plugin_recovery_message, plugin_manual_message, plugin_mismatch_message;
    DROP TABLE IF EXISTS plugin_migration_execution_log, plugin_schema_snapshot, sys_plugin_migration_audit,
      sys_plugin_migration_request, sys_plugin_version;
    SET FOREIGN_KEY_CHECKS = 1;
    CREATE TABLE sys_plugin_version (
      id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
      plugin_code varchar(64) NOT NULL,
      version varchar(32) NOT NULL,
      lifecycle_status varchar(32) NOT NULL,
      schema_status varchar(32) NOT NULL,
      deleted tinyint NOT NULL DEFAULT 0,
      updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB;
    CREATE TABLE sys_plugin_migration_request (
      id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
      plugin_code varchar(64) NOT NULL,
      plugin_version varchar(32) NOT NULL,
      schema_version varchar(64) NOT NULL,
      expected_schema_digest char(64),
      phase varchar(16) NOT NULL,
      rollback_mode varchar(32) NOT NULL,
      compatible_readers varchar(1024) NOT NULL,
      table_namespace varchar(128) NOT NULL,
      operation_epoch bigint NOT NULL,
      package_digest char(64) NOT NULL,
      migration_digest char(64) NOT NULL,
      release_id varchar(128) NOT NULL,
      request_status varchar(32) NOT NULL,
      lifecycle_status varchar(32) NOT NULL,
      script_payload longtext NOT NULL,
      failure_reason varchar(1024),
      recovery_action varchar(1024),
      approved_by varchar(128),
      approval_reason varchar(512),
      approved_at datetime,
      started_at datetime,
      finished_at datetime,
      updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB;
    CREATE TABLE plugin_migration_execution_log (
      id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
      migration_request_id bigint NOT NULL,
      plugin_code varchar(64) NOT NULL,
      release_id varchar(128) NOT NULL,
      migration_digest char(64) NOT NULL,
      schema_version varchar(64) NOT NULL,
      executor_type varchar(32) NOT NULL,
      executor_id varchar(128) NOT NULL,
      fence_token varchar(128) NOT NULL,
      lease_until datetime,
      status varchar(32) NOT NULL,
      active_request_id bigint GENERATED ALWAYS AS (CASE WHEN status = 'STARTED' THEN migration_request_id ELSE NULL END) STORED,
      actual_schema_digest char(64),
      started_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
      finished_at datetime,
      error_code varchar(64),
      error_message varchar(1024),
      created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      UNIQUE KEY uk_plugin_migration_execution_active (active_request_id)
    ) ENGINE=InnoDB;
    CREATE TABLE plugin_schema_snapshot (
      id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
      migration_request_id bigint NOT NULL,
      plugin_code varchar(64) NOT NULL,
      schema_version varchar(64) NOT NULL,
      object_type varchar(16) NOT NULL,
      object_name varchar(255) NOT NULL,
      definition_hash char(64) NOT NULL,
      captured_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
      release_id varchar(128) NOT NULL,
      UNIQUE KEY uk_plugin_schema_snapshot_object (migration_request_id, object_type, object_name)
    ) ENGINE=InnoDB;
    CREATE TABLE sys_plugin_migration_audit (
      id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
      request_id bigint NOT NULL,
      plugin_code varchar(64) NOT NULL,
      plugin_version varchar(32) NOT NULL,
      event_type varchar(32) NOT NULL,
      operation_epoch bigint NOT NULL,
      package_digest char(64) NOT NULL,
      migration_digest char(64) NOT NULL,
      release_id varchar(128) NOT NULL,
      actor varchar(128) NOT NULL,
      detail_message varchar(1024),
      created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB;
  `);
}

function payloadFor(sql) {
  return JSON.stringify([{ stepName, scriptPath: `migrations/up/${stepName}`, sql }]);
}

function insertRequest(pluginCode, sql, options = {}) {
  const digest = options.migrationDigest || migrationDigest(pluginCode, sql);
  const payload = payloadFor(sql);
  const status = options.status || 'APPROVED';
  const approvedFields = status === 'APPROVED'
    ? `${sqlLiteral('drill-operator')}, ${sqlLiteral('approved for recovery drill')}`
    : 'NULL, NULL';
  mysqlExec(`
    INSERT INTO sys_plugin_version (plugin_code, version, lifecycle_status, schema_status)
    VALUES (${sqlLiteral(pluginCode)}, '1.0.0', 'MIGRATION_PENDING', 'PENDING');
    INSERT INTO sys_plugin_migration_request (
      plugin_code, plugin_version, schema_version, expected_schema_digest, phase,
      rollback_mode, compatible_readers, table_namespace, operation_epoch,
      package_digest, migration_digest, release_id, request_status, lifecycle_status,
      script_payload, approved_by, approval_reason, started_at
    ) VALUES (
      ${sqlLiteral(pluginCode)}, '1.0.0', '1', ${sqlLiteral(options.expectedSchemaDigest)}, 'EXPAND',
      'APPLICATION_ONLY', ${sqlLiteral(releaseId)}, ${sqlLiteral(`plugin_${pluginCode}_`)}, 1,
      ${sqlLiteral(packageDigest)}, ${sqlLiteral(digest)}, ${sqlLiteral(releaseId)},
      ${sqlLiteral(status)}, 'MIGRATION_PENDING', ${sqlLiteral(payload)}, ${approvedFields},
      ${status === 'RUNNING' ? 'DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE)' : 'NULL'}
    );
  `);
  return Number(mysqlScalar(`
    SELECT id FROM sys_plugin_migration_request
    WHERE plugin_code = ${sqlLiteral(pluginCode)} AND migration_digest = ${sqlLiteral(digest)}
    ORDER BY id DESC LIMIT 1
  `));
}

function insertExpiredExecution(requestId, pluginCode, digest) {
  mysqlExec(`
    INSERT INTO plugin_migration_execution_log (
      migration_request_id, plugin_code, release_id, migration_digest, schema_version,
      executor_type, executor_id, fence_token, lease_until, status
    ) VALUES (
      ${requestId}, ${sqlLiteral(pluginCode)}, ${sqlLiteral(releaseId)}, ${sqlLiteral(digest)}, '1',
      'CENTRAL_MIGRATOR', 'crashed-migrator', 'stale-fence',
      DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE), 'STARTED'
    );
  `);
}

function migratorState(containerName) {
  const output = docker(['inspect', '-f', '{{.State.Status}}|{{.State.ExitCode}}', containerName]);
  if (output.status !== 0) return null;
  const [status, exitCode] = output.stdout.trim().split('|');
  return { status, exitCode: Number(exitCode) };
}

function startMigrator(containerName, executorId, faultPoint = 'NONE') {
  const args = [
    'run', '-d', '--name', containerName, '--network', networkName,
    '-v', `${path.join(repoRoot, 'deploy/plugin-migrator/target/lumira-plugin-migrator.jar')}:/opt/lumira/lumira-plugin-migrator.jar:ro`,
    '-e', `DB_URL=jdbc:mysql://${mysqlName}:3306/lumira?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`,
    '-e', `DB_USERNAME=${databaseUser}`,
    '-e', `DB_PASSWORD=${migratorPassword}`,
    '-e', `PLUGIN_MIGRATION_RELEASE_ID=${releaseId}`,
    '-e', `PLUGIN_MIGRATION_EXECUTOR_ID=${executorId}`,
    '-e', 'PLUGIN_MIGRATION_LEASE_SECONDS=30',
  ];
  if (faultPoint !== 'NONE') {
    args.push('-e', `PLUGIN_MIGRATION_FAULT_INJECTION=${faultPoint}`);
    args.push('-e', 'PLUGIN_MIGRATION_FAULT_INJECTION_DELAY_MS=30000');
  }
  args.push(javaImage, 'java', '-jar', '/opt/lumira/lumira-plugin-migrator.jar', 'execute');
  return requireSuccess(docker(args), `start migrator ${containerName}`);
}

async function waitForMigrator(containerName, timeoutMs = 60_000) {
  return waitFor(`migrator ${containerName}`, () => {
    const state = migratorState(containerName);
    return state && state.status === 'exited' ? state : false;
  }, timeoutMs, 250);
}

function migratorLogs(containerName) {
  return docker(['logs', containerName]).stdout.trim().slice(-4000);
}

async function runMigrator(executorId, options = {}) {
  const containerName = `${javaContainerPrefix}-${executorId}`;
  startMigrator(containerName, executorId, options.faultPoint || 'NONE');
  const state = await waitForMigrator(containerName, options.timeoutMs || 60_000);
  const logs = migratorLogs(containerName);
  if (!options.allowFailure && state.exitCode !== 0) {
    throw new Error(`migrator ${executorId} exited ${state.exitCode}: ${logs}`);
  }
  return { containerName, state, logs };
}

async function killMigrator(containerName) {
  requireSuccess(docker(['kill', containerName]), `kill migrator ${containerName}`);
  await waitForMigrator(containerName, 15_000);
}

function queryRequest(requestId) {
  const row = mysqlRows(`
    SELECT request_status, lifecycle_status, expected_schema_digest
    FROM sys_plugin_migration_request WHERE id = ${requestId}
  `)[0] || [];
  return { requestStatus: row[0], lifecycleStatus: row[1], expectedDigest: row[2] || null };
}

function queryExecution(requestId) {
  const row = mysqlRows(`
    SELECT GROUP_CONCAT(status ORDER BY id SEPARATOR ','),
           SUBSTRING_INDEX(GROUP_CONCAT(actual_schema_digest ORDER BY id DESC SEPARATOR ','), ',', 1)
    FROM plugin_migration_execution_log WHERE migration_request_id = ${requestId}
  `)[0] || [];
  return { statuses: row[0] || '', actualSchemaDigest: row[1] || null };
}

function countTable(tableName) {
  return Number(mysqlScalar(`
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = ${sqlLiteral(tableName)}
  `));
}

async function runDrill() {
  const jarPath = path.join(repoRoot, 'deploy/plugin-migrator/target/lumira-plugin-migrator.jar');
  const buildResult = command('./lumira-backend/mvnw', [
    '-f', 'deploy/plugin-migrator/pom.xml', '-DskipTests', 'package',
  ]);
  if (buildResult.status !== 0 || !existsSync(jarPath)) {
    throw new Error(`plugin migrator package failed: ${(buildResult.stderr || buildResult.stdout).trim().slice(-4000)}`);
  }

  requireSuccess(docker(['network', 'create', networkName]), 'create drill network');
  requireSuccess(docker([
    'run', '-d', '--name', mysqlName, '--network', networkName,
    '-e', 'MYSQL_DATABASE=lumira', '-e', `MYSQL_USER=${databaseUser}`,
    '-e', `MYSQL_PASSWORD=${migratorPassword}`, '-e', `MYSQL_ROOT_PASSWORD=${rootPassword}`,
    mysqlImage,
  ]), 'start drill mysql');
  await waitFor('mysql readiness', () => {
    const result = docker(['exec', mysqlName, 'mysqladmin', 'ping', '-h127.0.0.1', '-uroot', `-p${rootPassword}`, '--silent']);
    return result.status === 0;
  }, 90_000, 1000);
  resetSchema();

  const normalSql = 'CREATE TABLE plugin_sms_message (id bigint NOT NULL PRIMARY KEY)';
  const normalRequestId = insertRequest('sms', normalSql);
  await runMigrator('normal');
  const normal = { requestId: normalRequestId, ...queryRequest(normalRequestId), ...queryExecution(normalRequestId) };
  if (normal.requestStatus !== 'SUCCEEDED' || normal.lifecycleStatus !== 'MIGRATED') {
    throw new Error(`normal migration did not succeed: ${JSON.stringify(normal)}`);
  }

  await runMigrator('idempotent-repeat');
  const idempotent = { requestId: normalRequestId, ...queryRequest(normalRequestId), ...queryExecution(normalRequestId) };
  if (idempotent.requestStatus !== 'SUCCEEDED' || idempotent.statuses !== 'SUCCESS') {
    throw new Error(`repeat execution was not idempotent: ${JSON.stringify(idempotent)}`);
  }

  const recoverySql = 'CREATE TABLE plugin_recovery_message (id bigint NOT NULL PRIMARY KEY)';
  const recoveryProbeId = insertRequest('recovery', recoverySql);
  await runMigrator('recovery-digest-probe');
  const recoveryProbe = queryExecution(recoveryProbeId);
  if (!recoveryProbe.actualSchemaDigest) throw new Error('recovery digest probe did not record a schema digest');
  mysqlExec(`
    DROP TABLE plugin_recovery_message;
    DELETE FROM plugin_schema_snapshot WHERE migration_request_id = ${recoveryProbeId};
    DELETE FROM plugin_migration_execution_log WHERE migration_request_id = ${recoveryProbeId};
    DELETE FROM sys_plugin_migration_audit WHERE request_id = ${recoveryProbeId};
    DELETE FROM sys_plugin_migration_request WHERE id = ${recoveryProbeId};
    DELETE FROM sys_plugin_version WHERE plugin_code = 'recovery';
  `);
  const recoveryRequestId = insertRequest('recovery', recoverySql, { expectedSchemaDigest: recoveryProbe.actualSchemaDigest });
  const faultContainerName = `${javaContainerPrefix}-kill-in-progress`;
  startMigrator(faultContainerName, 'kill-in-progress', 'AFTER_DDL_BEFORE_VERIFY');
  await waitFor('DDL committed before process kill', () => {
    const state = queryRequest(recoveryRequestId);
    return state.requestStatus === 'RUNNING' && countTable('plugin_recovery_message') === 1;
  }, 30_000, 250);
  await killMigrator(faultContainerName);
  mysqlExec(`
    UPDATE plugin_migration_execution_log
    SET lease_until = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 SECOND)
    WHERE migration_request_id = ${recoveryRequestId} AND status = 'STARTED';
  `);
  await runMigrator('recovery-after-kill');
  const recovered = { requestId: recoveryRequestId, ...queryRequest(recoveryRequestId), ...queryExecution(recoveryRequestId) };
  const recoveryAudit = mysqlRows(`
    SELECT GROUP_CONCAT(event_type ORDER BY id SEPARATOR ',')
    FROM sys_plugin_migration_audit WHERE request_id = ${recoveryRequestId}
  `)[0]?.[0] || '';
  if (recovered.requestStatus !== 'SUCCEEDED' || !recoveryAudit.includes('RECOVERING')) {
    throw new Error(`killed migration was not recovered: ${JSON.stringify({ recovered, recoveryAudit })}`);
  }

  const manualSql = 'CREATE TABLE plugin_manual_message (id bigint NOT NULL PRIMARY KEY)';
  const manualRequestId = insertRequest('manual', manualSql, { status: 'RUNNING', expectedSchemaDigest: 'f'.repeat(64) });
  mysqlExec('CREATE TABLE plugin_manual_message (id bigint NOT NULL PRIMARY KEY);');
  insertExpiredExecution(manualRequestId, 'manual', migrationDigest('manual', manualSql));
  await runMigrator('manual-review');
  const manual = { requestId: manualRequestId, ...queryRequest(manualRequestId), ...queryExecution(manualRequestId) };
  if (manual.requestStatus !== 'NEEDS_MANUAL_REVIEW' || manual.lifecycleStatus !== 'ROLLBACK_BLOCKED') {
    throw new Error(`expired lease did not stop on schema drift: ${JSON.stringify(manual)}`);
  }

  const mismatchSql = 'CREATE TABLE plugin_mismatch_message (id bigint NOT NULL PRIMARY KEY)';
  const mismatchRequestId = insertRequest('mismatch', mismatchSql, { expectedSchemaDigest: 'f'.repeat(64) });
  await runMigrator('schema-mismatch', { allowFailure: true });
  const mismatch = { requestId: mismatchRequestId, ...queryRequest(mismatchRequestId), ...queryExecution(mismatchRequestId) };
  if (mismatch.requestStatus !== 'FAILED' || mismatch.lifecycleStatus !== 'ROLLBACK_BLOCKED') {
    throw new Error(`schema mismatch did not block rollback: ${JSON.stringify(mismatch)}`);
  }

  return {
    status: 'PASS',
    releaseGate: {
      status: 'PASS',
      requiredForRelease: true,
      releaseId,
      faultPoint: 'AFTER_DDL_BEFORE_VERIFY',
    },
    docker: { mysqlImage, javaImage },
    cases: {
      normalSuccess: {
        migrationId: normalRequestId,
        releaseId,
        faultPoint: 'NONE',
        beforeDigest: null,
        afterDigest: normal.actualSchemaDigest,
        expectedDigest: normal.expectedDigest,
        finalState: normal.requestStatus,
        recoveryCount: 0,
      },
      idempotentRepeat: {
        migrationId: normalRequestId,
        releaseId,
        faultPoint: 'NONE',
        beforeDigest: null,
        afterDigest: normal.actualSchemaDigest,
        finalState: idempotent.requestStatus,
        executionStatuses: idempotent.statuses,
        recoveryCount: 0,
      },
      killedInProgressRecovery: {
        migrationId: recoveryRequestId,
        releaseId,
        faultPoint: 'AFTER_DDL_BEFORE_VERIFY',
        beforeDigest: null,
        afterDigest: recovered.actualSchemaDigest,
        expectedDigest: recovered.expectedDigest,
        recovered: true,
        finalState: recovered.requestStatus,
        recoveryCount: 1,
      },
      expiredLeaseSchemaDrift: {
        migrationId: manualRequestId,
        releaseId,
        faultPoint: 'NONE',
        beforeDigest: null,
        afterDigest: manual.actualSchemaDigest,
        finalState: manual.requestStatus,
        lifecycleStatus: manual.lifecycleStatus,
        recoveryCount: 1,
      },
      schemaDigestMismatch: {
        migrationId: mismatchRequestId,
        releaseId,
        faultPoint: 'NONE',
        beforeDigest: null,
        afterDigest: mismatch.actualSchemaDigest,
        expectedDigest: mismatch.expectedDigest,
        finalState: mismatch.requestStatus,
        lifecycleStatus: mismatch.lifecycleStatus,
        recoveryCount: 0,
      },
    },
  };
}

function cleanup() {
  for (const name of [
    `${javaContainerPrefix}-normal`, `${javaContainerPrefix}-idempotent-repeat`,
    `${javaContainerPrefix}-recovery-digest-probe`, `${javaContainerPrefix}-kill-in-progress`,
    `${javaContainerPrefix}-recovery-after-kill`, `${javaContainerPrefix}-manual-review`,
    `${javaContainerPrefix}-schema-mismatch`, mysqlName,
  ]) {
    docker(['rm', '-f', name]);
  }
  docker(['network', 'rm', networkName]);
}

async function main() {
  if (process.argv.includes('--help')) {
    console.log('Runs the Docker-backed plugin migration recovery drill. Docker unavailable is reported as SKIPPED.');
    return 0;
  }
  const dockerInfo = docker(['info']);
  if (dockerInfo.status !== 0) {
    console.log(JSON.stringify({
      status: 'SKIPPED',
      classification: 'SKIPPED_ENVIRONMENT',
      reason: 'docker daemon unavailable',
      requiredForRelease: true,
      releaseId,
    }));
    return 0;
  }
  try {
    const evidence = await runDrill();
    console.log(JSON.stringify(evidence, null, 2));
    return 0;
  } catch (error) {
    console.error(JSON.stringify({ status: 'FAIL', reason: error.message }));
    return 1;
  } finally {
    cleanup();
  }
}

process.exitCode = await main();
