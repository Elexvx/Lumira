import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

import {
  assertReadOnlySql,
  buildCoreAuditSql,
  buildMysqlInvocation,
  buildOptionalAuditSql,
  compareTableGrowth,
  parseAuditMarkers,
  parseCliArgs,
  parseJdbcMysqlUrl,
  parseTabularOutput,
  redactSensitiveText,
  renderMarkdown,
  runProductionAudit,
  summarizeGrants,
  summarizeReplicaStatus,
} from './database-production-audit.mjs';

const marker = '__LUMIRA_MYSQL_AUDIT__';

test('single-host JDBC parser accepts DNS, IPv4, and bracketed IPv6 without retaining the URL', () => {
  assert.deepEqual(
    parseJdbcMysqlUrl('jdbc:mysql://db.internal:3307/saas?sslMode=VERIFY_IDENTITY&serverTimezone=UTC'),
    {
      host: 'db.internal',
      port: 3307,
      database: 'saas',
      tlsMode: 'VERIFY_IDENTITY',
      hostClass: 'dns',
      hostFingerprint: parseJdbcMysqlUrl('jdbc:mysql://db.internal/saas').hostFingerprint,
    },
  );
  assert.equal(parseJdbcMysqlUrl('jdbc:mysql://10.20.30.40/saas?useSSL=false').tlsMode, 'DISABLED');
  assert.deepEqual(
    parseJdbcMysqlUrl('jdbc:mysql://[2001:db8::1]:3308/lumira?requireSSL=true'),
    {
      host: '2001:db8::1',
      port: 3308,
      database: 'lumira',
      tlsMode: 'REQUIRED',
      hostClass: 'public-ip',
      hostFingerprint: parseJdbcMysqlUrl('jdbc:mysql://[2001:db8::1]/lumira').hostFingerprint,
    },
  );
});

test('JDBC parser rejects unsupported or dangerous endpoint forms', () => {
  for (const url of [
    'jdbc:mysql:replication://db-a:3306,db-b:3306/saas',
    'jdbc:mysql://db-a:3306,db-b:3306/saas',
    'jdbc:mysql://user@db.internal/saas',
    'jdbc:mysql://0.0.0.0/saas',
    'jdbc:mysql://db.internal/',
    'jdbc:mysql://db.internal/saas/extra',
    'jdbc:mysql://db.internal/saas?allowLoadLocalInfile=true',
    'jdbc:mysql://db.internal/saas?password=secret',
    'jdbc:mysql://db.internal/saas#fragment',
  ]) {
    assert.throws(() => parseJdbcMysqlUrl(url), /DB_URL/);
  }
});

test('CLI parser validates client mode, network names, and statement timeout', () => {
  const options = parseCliArgs([
    '--client', 'docker',
    '--docker-network', 'lumira-prod_net',
    '--statement-timeout-ms', '5000',
    '--output-dir', 'tmp/audit',
  ]);
  assert.equal(options.client, 'docker');
  assert.equal(options.dockerNetwork, 'lumira-prod_net');
  assert.equal(options.statementTimeoutMs, 5000);
  assert.ok(path.isAbsolute(options.outputDir));
  assert.throws(() => parseCliArgs(['--client', 'remote']), /auto, local, or docker/);
  assert.throws(() => parseCliArgs(['--docker-network', 'bad network']), /unsupported/);
  assert.throws(() => parseCliArgs(['--statement-timeout-ms', '999']), /1000 and 60000/);
});

test('audit SQL contract allows only fixed read operations and read-only transaction control', () => {
  assert.equal(assertReadOnlySql(buildCoreAuditSql()), true);
  const optionalSql = buildOptionalAuditSql([
    'sys_config',
    'flyway_schema_history',
    'platform_event_outbox',
    'payment_event_outbox',
    'plugin_event_outbox',
    'async_task',
    'event_consumer_receipt',
    'audit_login_log',
    'audit_operation_log',
  ]);
  assert.equal(assertReadOnlySql(optionalSql), true);
  assert.match(optionalSql, /\)\) FROM platform_event_outbox WHERE deleted = 0;/);
  assert.match(optionalSql, /\)\) FROM audit_login_log WHERE created_at >= NOW\(\) - INTERVAL 1 DAY;/);
  assert.equal(assertReadOnlySql('SET SESSION TRANSACTION READ ONLY; START TRANSACTION READ ONLY; SHOW REPLICA STATUS; COMMIT;'), true);
  assert.equal(assertReadOnlySql('SET SESSION TRANSACTION READ ONLY; START TRANSACTION READ ONLY; SHOW GRANTS FOR CURRENT_USER; COMMIT;'), true);

  for (const sql of [
    'UPDATE sys_config SET config_value = 1',
    'SELECT * FROM sys_config FOR UPDATE',
    "SELECT 'x' INTO OUTFILE '/tmp/x'",
    'SET SESSION sql_log_bin = 0',
    'ANALYZE TABLE sys_config',
    'SELECT SLEEP(10)',
  ]) {
    assert.throws(() => assertReadOnlySql(sql), /read-only|forbidden/);
  }
});

test('MySQL invocation keeps the password out of argv and pins the container image', () => {
  const endpoint = parseJdbcMysqlUrl('jdbc:mysql://127.0.0.1:3306/saas?sslMode=VERIFY_IDENTITY');
  const password = 'Do-Not-Put-This-In-Argv';
  const local = buildMysqlInvocation({ mode: 'local', endpoint, username: 'lumira_audit', password });
  const docker = buildMysqlInvocation({
    mode: 'docker',
    endpoint,
    username: 'lumira_audit',
    password,
    dockerNetwork: 'lumira_prod',
  });

  assert.equal(local.command, 'mysql');
  assert.equal(local.envPatch.MYSQL_PWD, password);
  assert.doesNotMatch(JSON.stringify(local.args), new RegExp(password));
  assert.equal(docker.command, 'docker');
  assert.ok(docker.args.includes('mysql:8.4'));
  assert.equal(docker.args[docker.args.indexOf('mysql:8.4') + 1], 'mysql');
  assert.ok(docker.args.includes('MYSQL_PWD'));
  assert.ok(docker.args.includes('host.docker.internal:host-gateway'));
  assert.doesNotMatch(JSON.stringify(docker.args), new RegExp(password));

  const wslDocker = buildMysqlInvocation({
    mode: 'docker',
    endpoint: parseJdbcMysqlUrl('jdbc:mysql://mysql.internal/saas?sslMode=VERIFY_IDENTITY'),
    username: 'lumira_audit',
    password,
    sslCa: 'C:\\secure\\mysql-ca.pem',
    clientCommand: 'wsl.exe',
    clientPrefixArgs: ['-d', 'Ubuntu-24.04', '-u', 'root', '--', 'docker'],
    dockerPathStyle: 'wsl',
    inheritedWslEnv: 'EXISTING/u',
  });
  assert.equal(wslDocker.command, 'wsl.exe');
  assert.ok(wslDocker.args.includes('/mnt/c/secure/mysql-ca.pem:/run/lumira-mysql-audit/ca.pem:ro'));
  assert.equal(wslDocker.envPatch.WSLENV, 'EXISTING/u:MYSQL_PWD');
  assert.doesNotMatch(JSON.stringify(wslDocker.args), new RegExp(password));
});

test('marker and tabular parsers retain metrics but never require SQL text', () => {
  const parsed = parseAuditMarkers([
    `${marker}server={"version":"8.4.6","sessionTransactionReadOnly":1}`,
    `${marker}tableSizes=[{"tableName":"platform_event_outbox","totalBytes":1024}]`,
    `${marker}broken=not-json`,
  ].join('\n'));
  assert.equal(parsed.sections.server.version, '8.4.6');
  assert.equal(parsed.sections.tableSizes[0].totalBytes, 1024);
  assert.equal(parsed.warnings.length, 1);

  const replicaRows = parseTabularOutput('Replica_IO_Running\tReplica_SQL_Running\tSeconds_Behind_Source\tSource_Host\nYes\tYes\t3\tsecret.internal\n');
  assert.deepEqual(summarizeReplicaStatus(replicaRows), [{
    configured: true,
    channelName: '',
    ioRunning: 'Yes',
    sqlRunning: 'Yes',
    secondsBehindSource: 3,
    lastIoErrorNumber: null,
    lastSqlErrorNumber: null,
    sqlDelaySeconds: null,
    autoPosition: undefined,
  }]);
  assert.doesNotMatch(JSON.stringify(summarizeReplicaStatus(replicaRows)), /secret\.internal/);
});

test('grant summary reports privilege names without account identity and flags write access', () => {
  const summary = summarizeGrants([
    "GRANT SELECT, PROCESS, REPLICATION CLIENT ON *.* TO `lumira_audit`@`10.%`",
    "GRANT BACKUP_ADMIN, INSERT, UPDATE ON `saas`.* TO `lumira_audit`@`10.%` WITH GRANT OPTION",
  ].join('\n'));
  assert.deepEqual(summary.dangerousPrivileges, ['BACKUP_ADMIN', 'GRANT OPTION', 'INSERT', 'UPDATE']);
  assert.equal(summary.leastPrivilege, false);
  assert.doesNotMatch(JSON.stringify(summary), /lumira_audit|10\.%/);
});

test('capacity baseline compares only matching redacted targets', () => {
  const target = { hostFingerprint: 'abc123def456', database: 'saas' };
  const baseline = {
    generatedAt: '2026-08-18T00:00:00.000Z',
    target,
    capacity: { tables: [{ tableName: 'audit_login_log', totalBytes: 1024, approximateRows: 100 }] },
  };
  const growth = compareTableGrowth(
    [{ tableName: 'audit_login_log', totalBytes: 3072, approximateRows: 125 }],
    baseline,
    '2026-08-19T00:00:00.000Z',
    target,
  );
  assert.equal(growth.status, 'COMPARED');
  assert.equal(growth.tables[0].bytesDelta, 2048);
  assert.equal(growth.tables[0].estimatedBytesPerDay, 2048);
  assert.equal(growth.tables[0].approximateRowsDelta, 25);
  assert.throws(() => compareTableGrowth([], baseline, '2026-08-19T00:00:00.000Z', { ...target, database: 'other' }), /different/);
});

test('end-to-end audit runner validates every SQL batch and produces a redacted report', () => {
  const secret = 'Sup3r-Secret-Password!';
  const jdbcUrl = 'jdbc:mysql://prod-db.secret.internal:3306/saas?sslMode=VERIFY_IDENTITY';
  const calls = [];
  const runner = (command, args, options = {}) => {
    calls.push({ command, args, options });
    if (args.includes('--version')) {
      return { status: 0, stdout: 'mysql  Ver 8.4.6', stderr: '' };
    }
    const sql = options.input || '';
    assertReadOnlySql(sql);
    assert.equal(options.env.MYSQL_PWD, secret);
    assert.doesNotMatch(JSON.stringify(args), new RegExp(secret));
    if (sql.includes('@@version')) {
      return {
        status: 0,
        stderr: '',
        stdout: [
          `${marker}server={"version":"8.4.6","characterSetServer":"utf8mb4","collationServer":"utf8mb4_0900_ai_ci","globalTimeZone":"SYSTEM","sessionTimeZone":"SYSTEM","sessionTransactionReadOnly":1}`,
          `${marker}durability={"logBin":1,"gtidMode":"ON","binlogFormat":"ROW","syncBinlog":1,"innodbFlushLogAtTrxCommit":1,"binlogExpireLogsSeconds":1209600}`,
          `${marker}connections={"threadsConnected":10,"threadsRunning":2,"maxConnections":200}`,
          `${marker}innodb={"deadlocksSinceStartup":0}`,
          `${marker}lockWaits={"currentDataLockWaits":0,"transactionsOver60Seconds":0}`,
          `${marker}slowQueryConfiguration={"slowQueryLog":"ON","longQueryTimeSeconds":1}`,
          `${marker}slowQueryDigests=[]`,
          `${marker}replicationChannels=[]`,
          `${marker}replicationAppliers=[]`,
          `${marker}tableNames=["sys_config","platform_event_outbox"]`,
          `${marker}tableSizes=[{"tableName":"platform_event_outbox","approximateRows":8,"dataBytes":1024,"indexBytes":512,"totalBytes":1536}]`,
        ].join('\n'),
      };
    }
    if (sql.includes('FROM sys_config')) {
      return {
        status: 0,
        stderr: '',
        stdout: [
          `${marker}databaseVersion={"databaseVersion":"202608190001","recordedAt":"2026-08-19T01:00:00"}`,
          `${marker}platformOutbox={"tableName":"platform_event_outbox","pending":0,"failed":0,"deadLetter":0,"inFlight":0}`,
        ].join('\n'),
      };
    }
    if (sql.includes('SHOW REPLICA STATUS')) {
      return { status: 0, stdout: '', stderr: '' };
    }
    if (sql.includes('SHOW GRANTS')) {
      return { status: 0, stdout: "GRANT SELECT, PROCESS, REPLICATION CLIENT ON *.* TO `lumira_audit`@`10.%`\n", stderr: '' };
    }
    throw new Error(`unexpected SQL: ${sql}`);
  };

  const { report } = runProductionAudit({
    options: {
      client: 'local',
      outputDir: path.resolve('tmp', 'unused-audit'),
      statementTimeoutMs: 8_000,
    },
    inheritedEnv: {
      DB_URL: jdbcUrl,
      DB_USERNAME: 'lumira_audit',
      DB_PASSWORD: secret,
    },
    commandRunner: runner,
    now: () => new Date('2026-08-19T02:00:00.000Z'),
    writeOutput: false,
  });

  assert.equal(report.auditMode, 'READ_ONLY');
  assert.equal(report.safety.sessionTransactionReadOnly, true);
  assert.equal(report.safety.collectionComplete, true);
  assert.equal(report.target.host, '<redacted>');
  assert.equal(report.schema.databaseVersion.databaseVersion, '202608190001');
  assert.equal(report.businessQueues[0].deadLetter, 0);
  assert.equal(report.capacity.growth.status, 'BASELINE_CAPTURED');
  assert.doesNotMatch(JSON.stringify(report), new RegExp(secret.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  assert.doesNotMatch(JSON.stringify(report), /prod-db\.secret\.internal|lumira_audit/);
  assert.ok(calls.every((call) => !JSON.stringify(call.args).includes(secret)));
  assert.doesNotMatch(renderMarkdown(report), /prod-db\.secret\.internal|lumira_audit|Sup3r-Secret/);
});

test('redaction removes JDBC URLs, passwords, usernames, and hosts from failures', () => {
  const redacted = redactSensitiveText(
    'connect jdbc:mysql://db.secret/saas username=audit password=secret-value host db.secret',
    { url: 'jdbc:mysql://db.secret/saas', password: 'secret-value', username: 'audit', host: 'db.secret' },
  );
  assert.doesNotMatch(redacted, /secret-value|db\.secret|username=audit/);
});

test('MySQL runbooks contain explicit gates, rollback boundaries, and no automatic destructive command', () => {
  const repoRoot = path.resolve(import.meta.dirname, '..');
  const runbooks = [
    'mysql-production-audit.md',
    'mysql-high-availability-switch.md',
    'mysql-pitr-recovery.md',
    'mysql-quarterly-recovery-drill.md',
    'mysql-access-and-tls.md',
    'mysql-capacity-and-archival.md',
  ].map((name) => readFileSync(path.join(repoRoot, 'doc', 'runbooks', name), 'utf8'));
  const combined = runbooks.join('\n');
  assert.match(combined, /RPO/);
  assert.match(combined, /RTO/);
  assert.match(combined, /GTID/);
  assert.match(combined, /回退边界/);
  assert.match(combined, /VERIFY_IDENTITY/);
  assert.match(combined, /--baseline/);
  assert.match(combined, /禁止.*直接.*旧库|禁止.*盲目.*切回/);
  assert.doesNotMatch(combined, /DROP DATABASE|TRUNCATE TABLE/);
});
