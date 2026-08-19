#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { existsSync, lstatSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { isIP } from 'node:net';
import path from 'node:path';
import process from 'node:process';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const REPORT_VERSION = 1;
const MYSQL_DOCKER_IMAGE = 'mysql:8.4';
const AUDIT_MARKER = '__LUMIRA_MYSQL_AUDIT__';
const DEFAULT_STATEMENT_TIMEOUT_MS = 8_000;
const DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
const MAX_REPORT_BYTES = 32 * 1024 * 1024;
const TRACKED_TABLES = Object.freeze([
  'platform_event_outbox',
  'payment_event_outbox',
  'plugin_event_outbox',
  'competition_review_notification_outbox',
  'event_consumer_receipt',
  'async_task',
  'platform_update_task',
  'audit_login_log',
  'audit_operation_log',
  'security_audit_event',
  'sys_plugin_schema_history',
  'flyway_schema_history',
]);

const UNSAFE_JDBC_OPTIONS = new Set([
  'allowloadlocalinfile',
  'allowmultiqueries',
  'allowurlinlocalinfile',
  'autodeserialize',
  'detectcustomcollations',
  'failoverreadonly',
  'loadbalanceautocommitstatementregex',
  'loadbalancesqlstatefailover',
  'password',
  'socketfactory',
  'socketfactoryarg',
  'user',
]);

const DANGEROUS_PRIVILEGES = new Set([
  'ALL PRIVILEGES',
  'ALTER',
  'ALTER ROUTINE',
  'CREATE',
  'CREATE ROLE',
  'CREATE ROUTINE',
  'CREATE TABLESPACE',
  'CREATE TEMPORARY TABLES',
  'CREATE USER',
  'DELETE',
  'DROP',
  'DROP ROLE',
  'EVENT',
  'EXECUTE',
  'FILE',
  'GRANT OPTION',
  'INDEX',
  'INSERT',
  'LOCK TABLES',
  'PROCESS_ADMIN',
  'REFERENCES',
  'RELOAD',
  'REPLICATION_APPLIER',
  'REPLICATION_SLAVE_ADMIN',
  'SET_USER_ID',
  'SHUTDOWN',
  'SUPER',
  'SYSTEM_USER',
  'SYSTEM_VARIABLES_ADMIN',
  'TRIGGER',
  'UPDATE',
]);
const AUDIT_ALLOWED_PRIVILEGES = new Set([
  'PROCESS',
  'REPLICATION CLIENT',
  'SELECT',
  'SHOW VIEW',
  'USAGE',
]);

const HELP = `Lumira production MySQL read-only audit

Usage:
  node bin/database-production-audit.mjs [options]

Options:
  --env-file <path>          Read DB_URL, DB_USERNAME and DB_PASSWORD from an env file.
  --password-file <path>     Read the database password from a regular file.
  --client <auto|local|docker>
                             Prefer a local MySQL 8.4 client or mysql:8.4 container.
  --docker-network <name>    Attach the client container to an existing Docker network.
  --ssl-ca <path>            CA file used by the audit client.
  --output-dir <path>        Report directory (default: artifacts/mysql-audit).
  --baseline <json-path>     Compare table sizes with an earlier audit JSON report.
  --statement-timeout-ms <n> Per-SELECT server timeout, 1000..60000 (default: 8000).
  --help                     Show this help.

The script never accepts SQL from the command line. It sends only validated SELECT,
SHOW and read-only transaction-control statements. Passwords are never placed in argv
or written to reports.
`;

function fail(message) {
  throw new Error(message);
}

function normalizeBoolean(value) {
  if (value === true || value === 1 || value === '1') {
    return true;
  }
  if (value === false || value === 0 || value === '0') {
    return false;
  }
  return value;
}

function normalizeNumber(value) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function classifyHost(host) {
  const normalized = host.toLowerCase();
  if (normalized === 'localhost' || normalized === '::1' || normalized.startsWith('127.')) {
    return 'loopback';
  }
  if (isIP(normalized) === 4) {
    const octets = normalized.split('.').map(Number);
    if (octets[0] === 10
      || (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31)
      || (octets[0] === 192 && octets[1] === 168)) {
      return 'private-ip';
    }
    return 'public-ip';
  }
  if (isIP(normalized) === 6) {
    return /^(?:fc|fd|fe8|fe9|fea|feb)/i.test(normalized) ? 'private-ip' : 'public-ip';
  }
  return normalized.includes('.') ? 'dns' : 'container-or-private-dns';
}

function resolveTlsMode(parameters) {
  const lower = new Map();
  for (const [key, value] of parameters.entries()) {
    lower.set(key.toLowerCase(), value);
  }

  const explicit = lower.get('sslmode');
  if (explicit !== undefined) {
    const mode = explicit.toUpperCase();
    if (!['DISABLED', 'PREFERRED', 'REQUIRED', 'VERIFY_CA', 'VERIFY_IDENTITY'].includes(mode)) {
      fail('DB_URL has an unsupported sslMode value.');
    }
    return mode;
  }

  const useSsl = lower.get('usessl')?.toLowerCase();
  const requireSsl = lower.get('requiressl')?.toLowerCase();
  const verifyServerCertificate = lower.get('verifyservercertificate')?.toLowerCase();
  if (useSsl === 'false') {
    return 'DISABLED';
  }
  if (verifyServerCertificate === 'true') {
    return 'VERIFY_CA';
  }
  if (requireSsl === 'true') {
    return 'REQUIRED';
  }
  return 'PREFERRED';
}

export function parseJdbcMysqlUrl(value) {
  const raw = String(value ?? '');
  if (/\p{Cc}/u.test(raw)) {
    fail('DB_URL contains control characters.');
  }
  if (!raw.toLowerCase().startsWith('jdbc:mysql://')) {
    fail('DB_URL must use the single-host jdbc:mysql:// scheme.');
  }

  const endpoint = raw.slice('jdbc:mysql://'.length);
  if (!endpoint || endpoint.includes('#')) {
    fail('DB_URL must not contain fragments and must include an endpoint.');
  }
  const slashIndex = endpoint.indexOf('/');
  if (slashIndex <= 0) {
    fail('DB_URL must include one host and a database name.');
  }

  const authority = endpoint.slice(0, slashIndex);
  const databaseAndQuery = endpoint.slice(slashIndex + 1);
  if (/[,@()\\]/.test(authority) || authority.includes('..')) {
    fail('DB_URL must be a single-host endpoint without credentials or failover syntax.');
  }

  let host;
  let portText;
  if (authority.startsWith('[')) {
    const ipv6 = authority.match(/^\[([^\]]+)](?::(\d+))?$/);
    if (!ipv6 || isIP(ipv6[1]) !== 6) {
      fail('DB_URL contains an invalid bracketed IPv6 endpoint.');
    }
    host = ipv6[1];
    portText = ipv6[2];
  } else {
    const hostPort = authority.match(/^([^:]+)(?::(\d+))?$/);
    if (!hostPort) {
      fail('DB_URL must use one hostname or a bracketed IPv6 address.');
    }
    host = hostPort[1];
    portText = hostPort[2];
    if (isIP(host) === 0 && !/^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$/.test(host)) {
      fail('DB_URL hostname contains unsupported characters.');
    }
  }

  if (host === '0.0.0.0' || host === '::' || host === '*') {
    fail('DB_URL cannot target a wildcard listener address.');
  }
  const port = Number(portText || 3306);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    fail('DB_URL port must be an integer between 1 and 65535.');
  }

  const queryIndex = databaseAndQuery.indexOf('?');
  const database = queryIndex === -1 ? databaseAndQuery : databaseAndQuery.slice(0, queryIndex);
  const query = queryIndex === -1 ? '' : databaseAndQuery.slice(queryIndex + 1);
  if (!/^[A-Za-z0-9_$-]{1,64}$/.test(database)) {
    fail('DB_URL database name is missing or contains unsupported characters.');
  }
  const parameters = new URLSearchParams(query);
  for (const key of parameters.keys()) {
    if (UNSAFE_JDBC_OPTIONS.has(key.toLowerCase())) {
      fail(`DB_URL option ${key} is not permitted by the audit tool.`);
    }
  }

  return {
    host,
    port,
    database,
    tlsMode: resolveTlsMode(parameters),
    hostClass: classifyHost(host),
    hostFingerprint: createHash('sha256').update(host.toLowerCase()).digest('hex').slice(0, 12),
  };
}

function nextArgument(argv, index, option) {
  if (index + 1 >= argv.length || argv[index + 1].startsWith('--')) {
    fail(`${option} requires a value.`);
  }
  return argv[index + 1];
}

export function parseCliArgs(argv) {
  const options = {
    client: 'auto',
    outputDir: path.resolve('artifacts', 'mysql-audit'),
    statementTimeoutMs: DEFAULT_STATEMENT_TIMEOUT_MS,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const option = argv[index];
    if (option === '--help') {
      options.help = true;
      continue;
    }
    const value = nextArgument(argv, index, option);
    index += 1;
    switch (option) {
      case '--env-file':
        options.envFile = path.resolve(value);
        break;
      case '--password-file':
        options.passwordFile = path.resolve(value);
        break;
      case '--client':
        if (!['auto', 'local', 'docker'].includes(value)) {
          fail('--client must be auto, local, or docker.');
        }
        options.client = value;
        break;
      case '--docker-network':
        if (!/^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$/.test(value)) {
          fail('--docker-network contains unsupported characters.');
        }
        options.dockerNetwork = value;
        break;
      case '--ssl-ca':
        options.sslCa = path.resolve(value);
        break;
      case '--output-dir':
        options.outputDir = path.resolve(value);
        break;
      case '--baseline':
        options.baseline = path.resolve(value);
        break;
      case '--statement-timeout-ms': {
        const timeout = Number(value);
        if (!Number.isInteger(timeout) || timeout < 1_000 || timeout > 60_000) {
          fail('--statement-timeout-ms must be an integer between 1000 and 60000.');
        }
        options.statementTimeoutMs = timeout;
        break;
      }
      default:
        fail(`Unknown option: ${option}`);
    }
  }
  return options;
}

function parseEnvFile(filePath) {
  const values = {};
  const contents = readFileSync(filePath, 'utf8').replace(/^\uFEFF/, '');
  for (const sourceLine of contents.split(/\r?\n/)) {
    const line = sourceLine.trim();
    if (!line || line.startsWith('#')) {
      continue;
    }
    const match = line.match(/^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$/);
    if (!match) {
      continue;
    }
    let value = match[2].trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    } else {
      value = value.replace(/\s+#.*$/, '').trim();
    }
    values[match[1]] = value;
  }
  return values;
}

function readRegularSecret(filePath) {
  const metadata = lstatSync(filePath);
  if (!metadata.isFile() || metadata.isSymbolicLink()) {
    fail('Database password path must be a regular file, not a symlink.');
  }
  const value = readFileSync(filePath, 'utf8').replace(/[\r\n]+$/, '');
  if (!value) {
    fail('Database password file is empty.');
  }
  return value;
}

function loadDatabaseConfiguration(options, inheritedEnv = process.env) {
  const fileEnv = options.envFile ? parseEnvFile(options.envFile) : {};
  const readValue = (name) => inheritedEnv[name] || fileEnv[name];
  const url = readValue('DB_URL');
  const username = readValue('DB_USERNAME');
  const configuredPasswordFile = options.passwordFile || readValue('DB_PASSWORD_FILE');
  const password = configuredPasswordFile
    ? readRegularSecret(path.resolve(configuredPasswordFile))
    : readValue('DB_PASSWORD');
  if (!url || !username || !password) {
    fail('DB_URL, DB_USERNAME and DB_PASSWORD (or DB_PASSWORD_FILE) are required.');
  }
  if (/\p{Cc}/u.test(username) || username.length > 128) {
    fail('DB_USERNAME contains unsupported characters.');
  }
  return {
    endpoint: parseJdbcMysqlUrl(url),
    url,
    username,
    password,
  };
}

function statementList(sql) {
  return String(sql)
    .split(';')
    .map((statement) => statement.trim())
    .filter(Boolean);
}

export function assertReadOnlySql(sql) {
  const statements = statementList(sql);
  if (statements.length === 0) {
    fail('Audit SQL cannot be empty.');
  }
  for (const statement of statements) {
    const normalized = statement.replace(/\s+/g, ' ').trim();
    const allowed = /^SELECT\b/i.test(normalized)
      || /^SHOW (?:REPLICA STATUS|GRANTS FOR CURRENT_USER(?:\(\))?|BINARY LOG STATUS)$/i.test(normalized)
      || /^SET SESSION TRANSACTION READ ONLY$/i.test(normalized)
      || /^SET SESSION MAX_EXECUTION_TIME\s*=\s*\d+$/i.test(normalized)
      || /^START TRANSACTION (?:READ ONLY|WITH CONSISTENT SNAPSHOT, READ ONLY|READ ONLY, WITH CONSISTENT SNAPSHOT)$/i.test(normalized)
      || /^COMMIT$/i.test(normalized);
    if (!allowed) {
      fail(`Audit SQL contains a non-read-only statement: ${normalized.split(/\s+/)[0] || '<unknown>'}.`);
    }
    const isShowGrants = /^SHOW GRANTS FOR CURRENT_USER(?:\(\))?$/i.test(normalized);
    if ((!isShowGrants && /\b(?:INSERT|UPDATE|DELETE|REPLACE|ALTER|CREATE|DROP|TRUNCATE|RENAME|GRANT|REVOKE|CALL|DO|HANDLER|LOAD|LOCK|UNLOCK|FLUSH|RESET|PURGE|KILL|SHUTDOWN|INSTALL|UNINSTALL|ANALYZE|OPTIMIZE|REPAIR)\b/i.test(normalized))
      || /\bINTO\s+(?:OUTFILE|DUMPFILE)\b|\bFOR\s+UPDATE\b|\bLOCK\s+IN\s+SHARE\s+MODE\b|\b(?:GET_LOCK|RELEASE_LOCK|SLEEP|BENCHMARK)\s*\(/i.test(normalized)) {
      fail('Audit SQL contains a forbidden mutating or side-effecting construct.');
    }
  }
  return true;
}

function markerQuery(name, jsonExpression, fromClause = '') {
  return `SELECT CONCAT('${AUDIT_MARKER}${name}=', ${jsonExpression})${fromClause ? ` ${fromClause}` : ''}`;
}

function transactionSql(statements, statementTimeoutMs) {
  const sql = [
    'SET SESSION TRANSACTION READ ONLY',
    `SET SESSION MAX_EXECUTION_TIME = ${statementTimeoutMs}`,
    'START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY',
    ...statements,
    'COMMIT',
  ].join(';\n') + ';\n';
  assertReadOnlySql(sql);
  return sql;
}

export function buildCoreAuditSql(statementTimeoutMs = DEFAULT_STATEMENT_TIMEOUT_MS) {
  return transactionSql([
    markerQuery('server', `JSON_OBJECT(
      'version', @@version,
      'versionComment', @@version_comment,
      'characterSetServer', @@character_set_server,
      'collationServer', @@collation_server,
      'characterSetDatabase', @@character_set_database,
      'collationDatabase', @@collation_database,
      'systemTimeZone', @@system_time_zone,
      'globalTimeZone', @@global.time_zone,
      'sessionTimeZone', @@session.time_zone,
      'globalReadOnly', @@global.read_only,
      'superReadOnly', @@global.super_read_only,
      'sessionTransactionReadOnly', @@session.transaction_read_only
    )`),
    markerQuery('durability', `JSON_OBJECT(
      'logBin', @@global.log_bin,
      'gtidMode', @@global.gtid_mode,
      'enforceGtidConsistency', @@global.enforce_gtid_consistency,
      'binlogFormat', @@global.binlog_format,
      'binlogRowImage', @@global.binlog_row_image,
      'binlogExpireLogsSeconds', @@global.binlog_expire_logs_seconds,
      'syncBinlog', @@global.sync_binlog,
      'innodbFlushLogAtTrxCommit', @@global.innodb_flush_log_at_trx_commit,
      'innodbDoublewrite', @@global.innodb_doublewrite,
      'innodbFlushMethod', @@global.innodb_flush_method
    )`),
    markerQuery('connections', `JSON_OBJECT(
      'maxConnections', @@global.max_connections,
      'threadsConnected', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Threads_connected'),
      'threadsRunning', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Threads_running'),
      'maxUsedConnections', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Max_used_connections'),
      'connectionErrorsMaxConnections', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Connection_errors_max_connections'),
      'abortedConnects', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Aborted_connects'),
      'uptimeSeconds', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Uptime')
    )`),
    markerQuery('innodb', `JSON_OBJECT(
      'bufferPoolPagesTotal', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Innodb_buffer_pool_pages_total'),
      'bufferPoolPagesFree', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Innodb_buffer_pool_pages_free'),
      'bufferPoolPagesDirty', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Innodb_buffer_pool_pages_dirty'),
      'bufferPoolReadRequests', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Innodb_buffer_pool_read_requests'),
      'bufferPoolReads', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Innodb_buffer_pool_reads'),
      'rowLockCurrentWaits', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Innodb_row_lock_current_waits'),
      'rowLockTimeMs', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Innodb_row_lock_time'),
      'rowLockTimeMaxMs', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Innodb_row_lock_time_max'),
      'deadlocksSinceStartup', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Innodb_deadlocks')
    )`),
    markerQuery('lockWaits', `JSON_OBJECT(
      'currentDataLockWaits', (SELECT COUNT(*) FROM performance_schema.data_lock_waits),
      'activeTransactions', (SELECT COUNT(*) FROM information_schema.innodb_trx),
      'transactionsOver60Seconds', (SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_started < NOW() - INTERVAL 60 SECOND),
      'oldestTransactionSeconds', (SELECT COALESCE(MAX(TIMESTAMPDIFF(SECOND, trx_started, NOW())), 0) FROM information_schema.innodb_trx)
    )`),
    markerQuery('slowQueryConfiguration', `JSON_OBJECT(
      'slowQueryLog', @@global.slow_query_log,
      'longQueryTimeSeconds', @@global.long_query_time,
      'minExaminedRowLimit', @@global.min_examined_row_limit,
      'logOutput', @@global.log_output,
      'slowQueriesSinceStartup', (SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Slow_queries')
    )`),
    markerQuery('slowQueryDigests', `COALESCE((
      SELECT JSON_ARRAYAGG(JSON_OBJECT(
        'digest', digest,
        'executionCount', execution_count,
        'totalLatencyMs', total_latency_ms,
        'averageLatencyMs', average_latency_ms,
        'rowsExamined', rows_examined,
        'rowsSent', rows_sent,
        'lastSeenAt', last_seen_at
      ))
      FROM (
        SELECT DIGEST AS digest,
               COUNT_STAR AS execution_count,
               ROUND(SUM_TIMER_WAIT / 1000000000, 3) AS total_latency_ms,
               ROUND(IF(COUNT_STAR = 0, 0, AVG_TIMER_WAIT / 1000000000), 3) AS average_latency_ms,
               SUM_ROWS_EXAMINED AS rows_examined,
               SUM_ROWS_SENT AS rows_sent,
               DATE_FORMAT(LAST_SEEN, '%Y-%m-%dT%H:%i:%s') AS last_seen_at
        FROM performance_schema.events_statements_summary_by_digest
        WHERE SCHEMA_NAME = DATABASE() AND DIGEST IS NOT NULL
        ORDER BY SUM_TIMER_WAIT DESC
        LIMIT 10
      ) AS digest_summary
    ), JSON_ARRAY())`),
    markerQuery('replicationChannels', `COALESCE((
      SELECT JSON_ARRAYAGG(JSON_OBJECT(
        'channelName', CHANNEL_NAME,
        'receiverState', SERVICE_STATE,
        'lastErrorNumber', LAST_ERROR_NUMBER,
        'lastErrorAt', DATE_FORMAT(LAST_ERROR_TIMESTAMP, '%Y-%m-%dT%H:%i:%s')
      ))
      FROM performance_schema.replication_connection_status
    ), JSON_ARRAY())`),
    markerQuery('replicationAppliers', `COALESCE((
      SELECT JSON_ARRAYAGG(JSON_OBJECT(
        'channelName', CHANNEL_NAME,
        'applierState', SERVICE_STATE,
        'lastErrorNumber', LAST_ERROR_NUMBER,
        'lastErrorAt', DATE_FORMAT(LAST_ERROR_TIMESTAMP, '%Y-%m-%dT%H:%i:%s')
      ))
      FROM performance_schema.replication_applier_status_by_coordinator
    ), JSON_ARRAY())`),
    markerQuery('tableNames', `COALESCE((
      SELECT JSON_ARRAYAGG(TABLE_NAME)
      FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
    ), JSON_ARRAY())`),
    markerQuery('tableSizes', `COALESCE((
      SELECT JSON_ARRAYAGG(JSON_OBJECT(
        'tableName', table_name,
        'engine', engine,
        'approximateRows', approximate_rows,
        'dataBytes', data_bytes,
        'indexBytes', index_bytes,
        'freeBytes', free_bytes,
        'totalBytes', total_bytes,
        'updatedAt', updated_at
      ))
      FROM (
        SELECT TABLE_NAME AS table_name,
               ENGINE AS engine,
               COALESCE(TABLE_ROWS, 0) AS approximate_rows,
               COALESCE(DATA_LENGTH, 0) AS data_bytes,
               COALESCE(INDEX_LENGTH, 0) AS index_bytes,
               COALESCE(DATA_FREE, 0) AS free_bytes,
               COALESCE(DATA_LENGTH, 0) + COALESCE(INDEX_LENGTH, 0) AS total_bytes,
               DATE_FORMAT(UPDATE_TIME, '%Y-%m-%dT%H:%i:%s') AS updated_at
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
        ORDER BY total_bytes DESC, table_name
      ) AS table_summary
    ), JSON_ARRAY())`),
  ], statementTimeoutMs);
}

function queueMetricQuery(section, tableName, statusColumn, pendingStatus) {
  return markerQuery(section, `JSON_OBJECT(
    'tableName', '${tableName}',
    'pending', COALESCE(SUM(CASE WHEN ${statusColumn} = '${pendingStatus}' THEN 1 ELSE 0 END), 0),
    'failed', COALESCE(SUM(CASE WHEN ${statusColumn} = 'FAILED' THEN 1 ELSE 0 END), 0),
    'deadLetter', COALESCE(SUM(CASE WHEN ${statusColumn} = 'DEAD_LETTER' THEN 1 ELSE 0 END), 0),
    'inFlight', COALESCE(SUM(CASE WHEN ${statusColumn} = 'DISPATCHING' THEN 1 ELSE 0 END), 0),
    'oldestActionableAt', DATE_FORMAT(MIN(CASE WHEN ${statusColumn} IN ('${pendingStatus}', 'FAILED') THEN created_at END), '%Y-%m-%dT%H:%i:%s'),
    'newestAt', DATE_FORMAT(MAX(created_at), '%Y-%m-%dT%H:%i:%s')
  )`, `FROM ${tableName} WHERE deleted = 0`);
}

export function buildOptionalAuditSql(tableNames, statementTimeoutMs = DEFAULT_STATEMENT_TIMEOUT_MS) {
  const present = new Set(Array.isArray(tableNames) ? tableNames : []);
  const statements = [];
  if (present.has('sys_config')) {
    statements.push(markerQuery('databaseVersion', `COALESCE((
      SELECT JSON_OBJECT(
        'databaseVersion', IF(JSON_VALID(config_value), JSON_UNQUOTE(JSON_EXTRACT(config_value, '$.databaseVersion')), NULL),
        'recordedAt', DATE_FORMAT(updated_at, '%Y-%m-%dT%H:%i:%s')
      )
      FROM sys_config
      WHERE config_key = 'platform.database.version' AND deleted = 0
      LIMIT 1
    ), JSON_OBJECT('databaseVersion', NULL, 'recordedAt', NULL))`));
  }
  if (present.has('flyway_schema_history')) {
    statements.push(markerQuery('flywayVersion', `COALESCE((
      SELECT JSON_OBJECT(
        'installedRank', installed_rank,
        'version', version,
        'description', description,
        'installedAt', DATE_FORMAT(installed_on, '%Y-%m-%dT%H:%i:%s'),
        'success', success
      )
      FROM flyway_schema_history
      ORDER BY installed_rank DESC
      LIMIT 1
    ), JSON_OBJECT('version', NULL))`));
  }
  if (present.has('platform_event_outbox')) {
    statements.push(queueMetricQuery('platformOutbox', 'platform_event_outbox', 'dispatch_status', 'RECORDED'));
  }
  if (present.has('payment_event_outbox')) {
    statements.push(queueMetricQuery('paymentOutbox', 'payment_event_outbox', 'status', 'PENDING'));
  }
  if (present.has('plugin_event_outbox')) {
    statements.push(queueMetricQuery('pluginOutbox', 'plugin_event_outbox', 'status', 'PENDING'));
  }
  if (present.has('competition_review_notification_outbox')) {
    statements.push(queueMetricQuery(
      'competitionReviewOutbox',
      'competition_review_notification_outbox',
      'status',
      'PENDING',
    ));
  }
  if (present.has('async_task')) {
    statements.push(markerQuery('asyncTasks', `COALESCE((
      SELECT JSON_ARRAYAGG(JSON_OBJECT('status', status, 'rowCount', row_count, 'oldestAt', oldest_at))
      FROM (
        SELECT status,
               COUNT(*) AS row_count,
               DATE_FORMAT(MIN(created_at), '%Y-%m-%dT%H:%i:%s') AS oldest_at
        FROM async_task
        GROUP BY status
      ) AS async_status
    ), JSON_ARRAY())`));
  }
  if (present.has('event_consumer_receipt')) {
    statements.push(markerQuery('consumerReceipts', `JSON_OBJECT(
      'last24Hours', COUNT(*),
      'nonSucceededLast24Hours', COALESCE(SUM(CASE WHEN result_status <> 'SUCCEEDED' THEN 1 ELSE 0 END), 0),
      'newestAt', DATE_FORMAT(MAX(processed_at), '%Y-%m-%dT%H:%i:%s')
    )`, 'FROM event_consumer_receipt WHERE processed_at >= NOW() - INTERVAL 1 DAY'));
  }
  if (present.has('audit_login_log')) {
    statements.push(markerQuery('loginAudit', `JSON_OBJECT(
      'last24Hours', COUNT(*),
      'failedLast24Hours', COALESCE(SUM(CASE WHEN login_result NOT IN ('SUCCESS', 'SUCCEEDED') THEN 1 ELSE 0 END), 0),
      'newestAt', DATE_FORMAT(MAX(created_at), '%Y-%m-%dT%H:%i:%s')
    )`, 'FROM audit_login_log WHERE created_at >= NOW() - INTERVAL 1 DAY'));
  }
  if (present.has('audit_operation_log')) {
    statements.push(markerQuery('operationAudit', `JSON_OBJECT(
      'last24Hours', COUNT(*),
      'failedLast24Hours', COALESCE(SUM(CASE WHEN result_status NOT IN ('SUCCESS', 'SUCCEEDED') THEN 1 ELSE 0 END), 0),
      'newestAt', DATE_FORMAT(MAX(created_at), '%Y-%m-%dT%H:%i:%s')
    )`, 'FROM audit_operation_log WHERE deleted = 0 AND created_at >= NOW() - INTERVAL 1 DAY'));
  }
  if (present.has('security_audit_event')) {
    statements.push(markerQuery('securityAudit', `JSON_OBJECT(
      'last24Hours', COUNT(*),
      'highSeverityLast24Hours', COALESCE(SUM(CASE WHEN severity IN ('HIGH', 'CRITICAL') THEN 1 ELSE 0 END), 0),
      'newestAt', DATE_FORMAT(MAX(created_at), '%Y-%m-%dT%H:%i:%s')
    )`, 'FROM security_audit_event WHERE created_at >= NOW() - INTERVAL 1 DAY'));
  }
  if (present.has('platform_update_task')) {
    statements.push(markerQuery('platformUpdateTasks', `COALESCE((
      SELECT JSON_ARRAYAGG(JSON_OBJECT('status', status, 'rowCount', row_count, 'oldestAt', oldest_at))
      FROM (
        SELECT status,
               COUNT(*) AS row_count,
               DATE_FORMAT(MIN(created_at), '%Y-%m-%dT%H:%i:%s') AS oldest_at
        FROM platform_update_task
        GROUP BY status
      ) AS platform_update_status
    ), JSON_ARRAY())`));
  }
  if (statements.length === 0) {
    return null;
  }
  return transactionSql(statements, statementTimeoutMs);
}

function mysqlClientArgs(endpoint, username, options, skipColumnNames) {
  const sslCa = options.sslCa;
  return [
    '--batch',
    '--raw',
    ...(skipColumnNames ? ['--skip-column-names'] : []),
    '--force',
    '--protocol=TCP',
    `--connect-timeout=${DEFAULT_CONNECT_TIMEOUT_SECONDS}`,
    '--default-character-set=utf8mb4',
    `--host=${endpoint.host}`,
    `--port=${endpoint.port}`,
    `--user=${username}`,
    `--database=${endpoint.database}`,
    `--ssl-mode=${endpoint.tlsMode}`,
    ...(sslCa ? [`--ssl-ca=${sslCa}`] : []),
  ];
}

function appendWslenv(existing, variableName) {
  const entries = String(existing || '').split(':').filter(Boolean);
  if (!entries.some((entry) => entry.split('/')[0] === variableName)) {
    entries.push(variableName);
  }
  return entries.join(':');
}

function toWslPath(filePath) {
  const match = String(filePath).match(/^([A-Za-z]):[\\/](.*)$/);
  if (!match) {
    fail('The WSL Docker wrapper requires --ssl-ca to use an absolute drive-letter path.');
  }
  return `/mnt/${match[1].toLowerCase()}/${match[2].replace(/\\/g, '/')}`;
}

export function buildMysqlInvocation({
  mode,
  endpoint,
  username,
  password,
  dockerNetwork,
  sslCa,
  skipColumnNames = true,
  clientCommand,
  clientPrefixArgs = [],
  dockerPathStyle,
  inheritedWslEnv,
}) {
  if (!['local', 'docker'].includes(mode)) {
    fail('Resolved MySQL client mode must be local or docker.');
  }
  const options = { sslCa };
  if (mode === 'local') {
    return {
      command: 'mysql',
      args: mysqlClientArgs(endpoint, username, options, skipColumnNames),
      envPatch: { MYSQL_PWD: password },
      displayMode: 'local mysql client',
    };
  }

  let containerHost = endpoint.host;
  const dockerArgs = ['run', '--rm', '-i', '--env', 'MYSQL_PWD'];
  if (dockerNetwork) {
    dockerArgs.push('--network', dockerNetwork);
  }
  if (endpoint.hostClass === 'loopback') {
    containerHost = 'host.docker.internal';
    dockerArgs.push('--add-host', 'host.docker.internal:host-gateway');
  }
  let containerCa;
  if (sslCa) {
    containerCa = '/run/lumira-mysql-audit/ca.pem';
    const dockerCa = dockerPathStyle === 'wsl' ? toWslPath(sslCa) : sslCa;
    dockerArgs.push('--volume', `${dockerCa}:${containerCa}:ro`);
  }
  dockerArgs.push(MYSQL_DOCKER_IMAGE, 'mysql');
  dockerArgs.push(...mysqlClientArgs(
    { ...endpoint, host: containerHost },
    username,
    { sslCa: containerCa },
    skipColumnNames,
  ));
  return {
    command: clientCommand || 'docker',
    args: [...clientPrefixArgs, ...dockerArgs],
    envPatch: {
      MYSQL_PWD: password,
      ...(dockerPathStyle === 'wsl' ? { WSLENV: appendWslenv(inheritedWslEnv, 'MYSQL_PWD') } : {}),
    },
    displayMode: `container ${MYSQL_DOCKER_IMAGE}${dockerPathStyle === 'wsl' ? ' via WSL Docker' : ''}`,
  };
}

function probeCommand(command, args, commandRunner) {
  const result = commandRunner(command, args, {
    encoding: 'utf8',
    stdio: 'pipe',
    timeout: 5_000,
    windowsHide: true,
  });
  return result;
}

function findPathEntry(fileName, inheritedEnv) {
  const searchPath = inheritedEnv.PATH || inheritedEnv.Path || inheritedEnv.path || '';
  for (const rawDirectory of searchPath.split(path.delimiter)) {
    const directory = rawDirectory.trim().replace(/^"|"$/g, '');
    if (!directory) {
      continue;
    }
    const candidate = path.join(directory, fileName);
    if (existsSync(candidate) && lstatSync(candidate).isFile()) {
      return candidate;
    }
  }
  return null;
}

function resolveDockerClient(commandRunner, inheritedEnv) {
  for (const command of process.platform === 'win32' ? ['docker', 'docker.exe'] : ['docker']) {
    const result = probeCommand(command, ['version', '--format', '{{.Server.Version}}'], commandRunner);
    if (!result.error && result.status === 0) {
      return { command, prefixArgs: [], pathStyle: null };
    }
  }

  if (process.platform !== 'win32') {
    return null;
  }
  const wrapperPath = findPathEntry('docker.cmd', inheritedEnv);
  if (!wrapperPath) {
    return null;
  }
  const wrapper = readFileSync(wrapperPath, 'utf8');
  const wslWrapper = wrapper.match(/(?:^|\r?\n)\s*(?:@)?wsl(?:\.exe)?\s+-d\s+([A-Za-z0-9_.-]+)\s+-u\s+([A-Za-z0-9_.-]+)\s+--\s+docker\s+%\*\s*(?:\r?\n|$)/i);
  if (!wslWrapper) {
    return null;
  }
  const prefixArgs = ['-d', wslWrapper[1], '-u', wslWrapper[2], '--', 'docker'];
  const result = probeCommand('wsl.exe', [...prefixArgs, 'version', '--format', '{{.Server.Version}}'], commandRunner);
  if (result.error || result.status !== 0) {
    return null;
  }
  return { command: 'wsl.exe', prefixArgs, pathStyle: 'wsl' };
}

function resolveClientMode(requested, commandRunner, inheritedEnv) {
  const localProbe = () => {
    const result = probeCommand('mysql', ['--version'], commandRunner);
    const output = `${result.stdout || ''}\n${result.stderr || ''}`;
    return !result.error && result.status === 0 && /(?:\bVer|\bDistrib)\s+8\.4(?:\.|\b)/i.test(output);
  };
  if (requested === 'local') {
    if (!localProbe()) {
      fail('A local MySQL 8.4 client is unavailable. Install it or use --client docker.');
    }
    return { mode: 'local', command: 'mysql', prefixArgs: [], pathStyle: null };
  }
  if (requested === 'docker') {
    const docker = resolveDockerClient(commandRunner, inheritedEnv);
    if (!docker) {
      fail('Docker is unavailable. Start Docker or use --client local.');
    }
    return { mode: 'docker', ...docker };
  }
  if (localProbe()) {
    return { mode: 'local', command: 'mysql', prefixArgs: [], pathStyle: null };
  }
  const docker = resolveDockerClient(commandRunner, inheritedEnv);
  if (docker) {
    return { mode: 'docker', ...docker };
  }
  fail('Neither a local mysql client nor Docker is available.');
}

export function redactSensitiveText(value, secrets = {}) {
  let redacted = String(value ?? '');
  for (const secret of [secrets.url, secrets.password, secrets.username, secrets.host]) {
    if (typeof secret === 'string' && secret.length >= 3) {
      redacted = redacted.split(secret).join('<redacted>');
    }
  }
  redacted = redacted.replace(/(?:jdbc:mysql:\/\/)[^\s'"<>]+/gi, 'jdbc:mysql://<redacted>');
  redacted = redacted.replace(/(password\s*[=:]\s*)[^\s,;]+/gi, '$1<redacted>');
  return redacted;
}

function executeSql({
  sql,
  client,
  databaseConfiguration,
  options,
  commandRunner,
  inheritedEnv,
  skipColumnNames = true,
}) {
  assertReadOnlySql(sql);
  const invocation = buildMysqlInvocation({
    mode: client.mode,
    endpoint: databaseConfiguration.endpoint,
    username: databaseConfiguration.username,
    password: databaseConfiguration.password,
    dockerNetwork: options.dockerNetwork,
    sslCa: options.sslCa,
    skipColumnNames,
    clientCommand: client.command,
    clientPrefixArgs: client.prefixArgs,
    dockerPathStyle: client.pathStyle,
    inheritedWslEnv: inheritedEnv.WSLENV,
  });
  const childEnv = { ...inheritedEnv, ...invocation.envPatch };
  const result = commandRunner(invocation.command, invocation.args, {
    encoding: 'utf8',
    env: childEnv,
    input: sql,
    maxBuffer: MAX_REPORT_BYTES,
    stdio: ['pipe', 'pipe', 'pipe'],
    timeout: Math.max(30_000, options.statementTimeoutMs * 8),
    windowsHide: true,
  });
  const secrets = {
    url: databaseConfiguration.url,
    password: databaseConfiguration.password,
    username: databaseConfiguration.username,
    host: databaseConfiguration.endpoint.host,
  };
  if (result.error) {
    fail(`MySQL audit client failed: ${redactSensitiveText(result.error.message, secrets)}`);
  }
  if (result.status !== 0) {
    const detail = redactSensitiveText(result.stderr || result.stdout || `exit ${result.status}`, secrets).trim();
    fail(`MySQL audit client returned status ${result.status}: ${detail}`);
  }
  return {
    stdout: redactSensitiveText(result.stdout || '', secrets),
    warnings: redactSensitiveText(result.stderr || '', secrets)
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean),
    displayMode: invocation.displayMode,
  };
}

export function parseAuditMarkers(output) {
  const sections = {};
  const parseWarnings = [];
  for (const line of String(output ?? '').split(/\r?\n/)) {
    if (!line.startsWith(AUDIT_MARKER)) {
      continue;
    }
    const separator = line.indexOf('=', AUDIT_MARKER.length);
    if (separator < 0) {
      parseWarnings.push('Ignored a malformed audit result marker.');
      continue;
    }
    const name = line.slice(AUDIT_MARKER.length, separator);
    try {
      sections[name] = JSON.parse(line.slice(separator + 1));
    } catch {
      parseWarnings.push(`Could not parse audit section ${name}.`);
    }
  }
  return { sections, warnings: parseWarnings };
}

export function parseTabularOutput(output) {
  const lines = String(output ?? '').trim().split(/\r?\n/).filter(Boolean);
  if (lines.length < 2) {
    return [];
  }
  const headers = lines[0].split('\t');
  return lines.slice(1).map((line) => Object.fromEntries(
    headers.map((header, index) => [header, line.split('\t')[index] ?? null]),
  ));
}

export function summarizeReplicaStatus(rows) {
  return rows.map((row) => ({
    configured: true,
    channelName: row.Channel_Name || row.Connection_name || '',
    ioRunning: row.Replica_IO_Running ?? row.Slave_IO_Running ?? null,
    sqlRunning: row.Replica_SQL_Running ?? row.Slave_SQL_Running ?? null,
    secondsBehindSource: normalizeNumber(row.Seconds_Behind_Source ?? row.Seconds_Behind_Master),
    lastIoErrorNumber: normalizeNumber(row.Last_IO_Errno),
    lastSqlErrorNumber: normalizeNumber(row.Last_SQL_Errno),
    sqlDelaySeconds: normalizeNumber(row.SQL_Delay),
    autoPosition: normalizeBoolean(row.Auto_Position),
  }));
}

export function summarizeGrants(output) {
  const privileges = new Set();
  let roleGrantCount = 0;
  for (const line of String(output ?? '').split(/\r?\n/).map((value) => value.trim()).filter(Boolean)) {
    const grant = line.match(/^GRANT\s+(.+?)\s+ON\s+/i);
    if (!grant) {
      if (/^GRANT\s+/i.test(line)) {
        roleGrantCount += 1;
      }
      continue;
    }
    for (const privilege of grant[1].split(',').map((value) => value.trim().toUpperCase())) {
      if (privilege) {
        privileges.add(privilege);
      }
    }
    if (/\bWITH GRANT OPTION\b/i.test(line)) {
      privileges.add('GRANT OPTION');
    }
  }
  const sorted = [...privileges].sort();
  const dangerousPrivileges = sorted.filter((privilege) => (
    DANGEROUS_PRIVILEGES.has(privilege) || !AUDIT_ALLOWED_PRIVILEGES.has(privilege)
  ));
  return {
    analyzed: sorted.length > 0 || roleGrantCount > 0,
    privileges: sorted,
    dangerousPrivileges,
    roleGrantCount,
    leastPrivilege: sorted.length > 0 && roleGrantCount === 0 && dangerousPrivileges.length === 0,
  };
}

function tableByName(tables) {
  return new Map((Array.isArray(tables) ? tables : []).map((table) => [table.tableName, table]));
}

export function compareTableGrowth(currentTables, baselineReport, currentGeneratedAt, currentTarget) {
  if (!baselineReport) {
    return {
      status: 'BASELINE_CAPTURED',
      message: 'Use this JSON report with --baseline on the next audit to calculate growth.',
      tables: [],
    };
  }
  if (baselineReport?.target?.hostFingerprint !== currentTarget.hostFingerprint
    || baselineReport?.target?.database !== currentTarget.database) {
    fail('Baseline report belongs to a different redacted host or database.');
  }
  const baselineAt = Date.parse(baselineReport.generatedAt);
  const currentAt = Date.parse(currentGeneratedAt);
  if (!Number.isFinite(baselineAt) || !Number.isFinite(currentAt) || currentAt <= baselineAt) {
    fail('Baseline report timestamp must be earlier than the current audit.');
  }
  const elapsedHours = (currentAt - baselineAt) / 3_600_000;
  const previous = tableByName(baselineReport?.capacity?.tables);
  const growth = [];
  for (const table of Array.isArray(currentTables) ? currentTables : []) {
    const old = previous.get(table.tableName);
    if (!old) {
      continue;
    }
    const bytesDelta = (normalizeNumber(table.totalBytes) || 0) - (normalizeNumber(old.totalBytes) || 0);
    const approximateRowsDelta = (normalizeNumber(table.approximateRows) || 0) - (normalizeNumber(old.approximateRows) || 0);
    growth.push({
      tableName: table.tableName,
      bytesDelta,
      approximateRowsDelta,
      estimatedBytesPerDay: Math.round(bytesDelta * 24 / elapsedHours),
    });
  }
  growth.sort((left, right) => Math.abs(right.bytesDelta) - Math.abs(left.bytesDelta));
  return {
    status: 'COMPARED',
    baselineGeneratedAt: baselineReport.generatedAt,
    elapsedHours: Number(elapsedHours.toFixed(2)),
    tables: growth,
  };
}

function normalizeSections(sections) {
  for (const key of ['globalReadOnly', 'superReadOnly', 'sessionTransactionReadOnly']) {
    if (sections.server && key in sections.server) {
      sections.server[key] = normalizeBoolean(sections.server[key]);
    }
  }
  for (const key of ['logBin', 'enforceGtidConsistency']) {
    if (sections.durability && key in sections.durability) {
      sections.durability[key] = normalizeBoolean(sections.durability[key]);
    }
  }
  return sections;
}

function buildChecks(report) {
  const checks = [];
  const add = (id, passed, severity, detail) => checks.push({ id, passed, severity, detail });
  add('session-read-only', report.safety.sessionTransactionReadOnly === true, 'critical', 'Audit transaction must be read-only.');
  add('collection-complete', report.safety.collectionComplete === true, 'critical', 'All applicable audit sections must be collected without SQL errors.');
  add('mysql-8.4', /^8\.4(?:\.|$)/.test(report.server?.version || ''), 'warning', 'Production baseline is MySQL 8.4.');
  add('tls-verified', report.target.tlsMode === 'VERIFY_IDENTITY', 'high', 'Use sslMode=VERIFY_IDENTITY and a trusted CA.');
  add('binary-log', report.durability?.logBin === true || report.durability?.logBin === 'ON', 'critical', 'Binary logging is required for PITR.');
  add('gtid', report.durability?.gtidMode === 'ON', 'high', 'GTID must be ON for controlled failover and recovery.');
  add('row-binlog', report.durability?.binlogFormat === 'ROW', 'high', 'ROW binlog format is required.');
  add('sync-binlog', normalizeNumber(report.durability?.syncBinlog) === 1, 'high', 'sync_binlog should be 1 after workload validation.');
  add('flush-log-at-commit', normalizeNumber(report.durability?.innodbFlushLogAtTrxCommit) === 1, 'high', 'innodb_flush_log_at_trx_commit should be 1.');
  add('least-privilege-auditor', report.privileges.leastPrivilege, 'critical', 'The audit account must not have DML, DDL, FILE, SUPER, or grant privileges.');
  add('slow-query-log', report.slowQueries?.configuration?.slowQueryLog === 'ON'
    || report.slowQueries?.configuration?.slowQueryLog === true
    || report.slowQueries?.configuration?.slowQueryLog === 1, 'warning', 'Enable slow query collection with an approved retention policy.');
  for (const queue of report.businessQueues) {
    add(`${queue.tableName}-dead-letter`, normalizeNumber(queue.deadLetter) === 0, 'high', `${queue.tableName} must have no unresolved dead letters.`);
  }
  return checks;
}

function targetForReport(endpoint) {
  return {
    host: '<redacted>',
    hostFingerprint: endpoint.hostFingerprint,
    hostClass: endpoint.hostClass,
    port: endpoint.port,
    database: endpoint.database,
    tlsMode: endpoint.tlsMode,
  };
}

function trackedTableMetrics(tables) {
  const metrics = tableByName(tables);
  return TRACKED_TABLES.map((tableName) => metrics.get(tableName) || { tableName, present: false });
}

function loadBaseline(filePath) {
  if (!filePath) {
    return null;
  }
  const metadata = lstatSync(filePath);
  if (!metadata.isFile() || metadata.isSymbolicLink() || metadata.size > MAX_REPORT_BYTES) {
    fail('Baseline must be a regular, non-symlink JSON report no larger than 32 MiB.');
  }
  const baseline = JSON.parse(readFileSync(filePath, 'utf8'));
  if (baseline?.reportVersion !== REPORT_VERSION || baseline?.auditMode !== 'READ_ONLY') {
    fail('Baseline is not a compatible Lumira read-only MySQL audit report.');
  }
  return baseline;
}

function formatBytes(value) {
  const bytes = normalizeNumber(value);
  if (bytes === null) {
    return 'n/a';
  }
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB'];
  let amount = Math.abs(bytes);
  let unit = 0;
  while (amount >= 1024 && unit < units.length - 1) {
    amount /= 1024;
    unit += 1;
  }
  return `${bytes < 0 ? '-' : ''}${amount.toFixed(unit === 0 ? 0 : 2)} ${units[unit]}`;
}

function mdCell(value) {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  return String(value).replace(/\|/g, '\\|').replace(/[\r\n]+/g, ' ');
}

export function renderMarkdown(report) {
  const failedChecks = report.checks.filter((check) => !check.passed);
  const topTables = [...report.capacity.tables]
    .sort((left, right) => (normalizeNumber(right.totalBytes) || 0) - (normalizeNumber(left.totalBytes) || 0))
    .slice(0, 20);
  const lines = [
    '# Lumira MySQL 生产只读体检报告',
    '',
    `- 生成时间：${mdCell(report.generatedAt)}`,
    `- 审计模式：${report.auditMode}（固定 SQL；会话与事务均为只读）`,
    `- 客户端：${mdCell(report.client.mode)}`,
    `- 目标：${report.target.host} / 指纹 \`${mdCell(report.target.hostFingerprint)}\` / 端口 ${report.target.port} / 数据库 \`${mdCell(report.target.database)}\``,
    `- TLS：${mdCell(report.target.tlsMode)}`,
    '',
    '## 门禁结论',
    '',
  ];
  if (failedChecks.length === 0) {
    lines.push('所有自动门禁通过。仍需按运行手册完成人工恢复演练。', '');
  } else {
    lines.push('| 级别 | 门禁 | 结论 | 说明 |', '| --- | --- | --- | --- |');
    for (const check of failedChecks) {
      lines.push(`| ${mdCell(check.severity)} | ${mdCell(check.id)} | 未通过 | ${mdCell(check.detail)} |`);
    }
    lines.push('');
  }
  lines.push(
    '## 数据库与耐久性',
    '',
    '| 项目 | 值 |',
    '| --- | --- |',
    `| MySQL | ${mdCell(report.server?.version)} |`,
    `| 字符集 / 排序规则 | ${mdCell(report.server?.characterSetServer)} / ${mdCell(report.server?.collationServer)} |`,
    `| 时区 | global=${mdCell(report.server?.globalTimeZone)}, session=${mdCell(report.server?.sessionTimeZone)} |`,
    `| log_bin / GTID / binlog_format | ${mdCell(report.durability?.logBin)} / ${mdCell(report.durability?.gtidMode)} / ${mdCell(report.durability?.binlogFormat)} |`,
    `| binlog 保留（秒） | ${mdCell(report.durability?.binlogExpireLogsSeconds)} |`,
    `| sync_binlog / flush_at_commit | ${mdCell(report.durability?.syncBinlog)} / ${mdCell(report.durability?.innodbFlushLogAtTrxCommit)} |`,
    `| Lumira database version | ${mdCell(report.schema?.databaseVersion?.databaseVersion)} |`,
    `| Flyway version | ${mdCell(report.schema?.flywayVersion?.version)} |`,
    `| 业务 schema 表数 | ${mdCell(report.schema?.tableCount)} |`,
    '',
    '## 连接、锁与慢查询',
    '',
    '| 项目 | 值 |',
    '| --- | --- |',
    `| 连接 / 上限 | ${mdCell(report.connections?.threadsConnected)} / ${mdCell(report.connections?.maxConnections)} |`,
    `| Running threads | ${mdCell(report.connections?.threadsRunning)} |`,
    `| 当前锁等待 | ${mdCell(report.innodb?.lockWaits?.currentDataLockWaits)} |`,
    `| 超过 60 秒事务 | ${mdCell(report.innodb?.lockWaits?.transactionsOver60Seconds)} |`,
    `| 启动后死锁数 | ${mdCell(report.innodb?.status?.deadlocksSinceStartup)} |`,
    `| slow_query_log / long_query_time | ${mdCell(report.slowQueries?.configuration?.slowQueryLog)} / ${mdCell(report.slowQueries?.configuration?.longQueryTimeSeconds)}s |`,
    '',
    '## 业务队列',
    '',
    '| 表 | 待处理 | 失败 | 死信 | 最早可处理时间 |',
    '| --- | ---: | ---: | ---: | --- |',
  );
  if (report.businessQueues.length === 0) {
    lines.push('| — | — | — | — | 未采集 |');
  } else {
    for (const queue of report.businessQueues) {
      lines.push(`| ${mdCell(queue.tableName)} | ${mdCell(queue.pending)} | ${mdCell(queue.failed)} | ${mdCell(queue.deadLetter)} | ${mdCell(queue.oldestActionableAt)} |`);
    }
  }
  lines.push('', '## 容量快照（前 20）', '', '| 表 | 估算行数 | 数据 | 索引 | 总量 |', '| --- | ---: | ---: | ---: | ---: |');
  for (const table of topTables) {
    lines.push(`| ${mdCell(table.tableName)} | ${mdCell(table.approximateRows)} | ${formatBytes(table.dataBytes)} | ${formatBytes(table.indexBytes)} | ${formatBytes(table.totalBytes)} |`);
  }
  lines.push('', '## 增长基线', '');
  if (report.capacity.growth.status === 'BASELINE_CAPTURED') {
    lines.push(report.capacity.growth.message);
  } else {
    lines.push(`基线：${mdCell(report.capacity.growth.baselineGeneratedAt)}；间隔：${mdCell(report.capacity.growth.elapsedHours)} 小时。`, '');
    lines.push('| 表 | 字节变化 | 估算日增长 | 估算行数变化 |', '| --- | ---: | ---: | ---: |');
    for (const growth of report.capacity.growth.tables.slice(0, 20)) {
      lines.push(`| ${mdCell(growth.tableName)} | ${formatBytes(growth.bytesDelta)} | ${formatBytes(growth.estimatedBytesPerDay)} | ${mdCell(growth.approximateRowsDelta)} |`);
    }
  }
  lines.push(
    '',
    '## 复制与权限',
    '',
    `- 复制通道：${report.replication.replicaStatus.length || report.replication.channels.length}`,
    `- 审计账号权限：${report.privileges.privileges.map((value) => `\`${value}\``).join(', ') || '未采集'}`,
    `- 危险权限：${report.privileges.dangerousPrivileges.map((value) => `\`${value}\``).join(', ') || '无'}`,
    '',
    '## 采集限制与警告',
    '',
  );
  if (report.warnings.length === 0) {
    lines.push('- 无。');
  } else {
    for (const warning of report.warnings) {
      lines.push(`- ${mdCell(warning)}`);
    }
  }
  lines.push('', '> 表行数来自 information_schema 的 InnoDB 估算值；状态聚合受单条 SELECT 超时限制。该报告不能替代真实故障切换与 PITR 恢复演练。', '');
  return lines.join('\n');
}

function writeReports(report, outputDirectory) {
  mkdirSync(outputDirectory, { recursive: true });
  const stamp = report.generatedAt.replace(/[-:.]/g, '');
  const base = `mysql-production-audit-${stamp}`;
  const jsonPath = path.join(outputDirectory, `${base}.json`);
  const markdownPath = path.join(outputDirectory, `${base}.md`);
  const json = JSON.stringify(report, null, 2) + '\n';
  writeFileSync(jsonPath, json, { encoding: 'utf8', flag: 'wx', mode: 0o600 });
  writeFileSync(markdownPath, renderMarkdown(report), { encoding: 'utf8', flag: 'wx', mode: 0o600 });
  return { jsonPath, markdownPath };
}

function sanitizedWarningList(warnings) {
  return [...new Set(warnings.filter(Boolean))].slice(0, 100);
}

function expectedAuditSections(tableNames) {
  const expected = [
    'server',
    'durability',
    'connections',
    'innodb',
    'lockWaits',
    'slowQueryConfiguration',
    'slowQueryDigests',
    'replicationChannels',
    'replicationAppliers',
    'tableNames',
    'tableSizes',
  ];
  const byTable = {
    sys_config: 'databaseVersion',
    flyway_schema_history: 'flywayVersion',
    platform_event_outbox: 'platformOutbox',
    payment_event_outbox: 'paymentOutbox',
    plugin_event_outbox: 'pluginOutbox',
    competition_review_notification_outbox: 'competitionReviewOutbox',
    async_task: 'asyncTasks',
    event_consumer_receipt: 'consumerReceipts',
    audit_login_log: 'loginAudit',
    audit_operation_log: 'operationAudit',
    security_audit_event: 'securityAudit',
    platform_update_task: 'platformUpdateTasks',
  };
  const present = new Set(Array.isArray(tableNames) ? tableNames : []);
  for (const [tableName, section] of Object.entries(byTable)) {
    if (present.has(tableName)) {
      expected.push(section);
    }
  }
  return expected;
}

export function runProductionAudit({
  options,
  commandRunner = spawnSync,
  inheritedEnv = process.env,
  now = () => new Date(),
  writeOutput = true,
}) {
  if (options.sslCa) {
    if (!existsSync(options.sslCa) || !lstatSync(options.sslCa).isFile()) {
      fail('--ssl-ca must reference an existing regular file.');
    }
  }
  const databaseConfiguration = loadDatabaseConfiguration(options, inheritedEnv);
  const client = resolveClientMode(options.client, commandRunner, inheritedEnv);
  const warnings = [];

  const core = executeSql({
    sql: buildCoreAuditSql(options.statementTimeoutMs),
    client,
    databaseConfiguration,
    options,
    commandRunner,
    inheritedEnv,
  });
  warnings.push(...core.warnings);
  const parsedCore = parseAuditMarkers(core.stdout);
  warnings.push(...parsedCore.warnings);
  const sections = normalizeSections(parsedCore.sections);
  if (sections.server?.sessionTransactionReadOnly !== true) {
    fail('The server did not confirm a read-only audit transaction; no report was written.');
  }

  const optionalSql = buildOptionalAuditSql(sections.tableNames, options.statementTimeoutMs);
  if (optionalSql) {
    const optional = executeSql({
      sql: optionalSql,
      client,
      databaseConfiguration,
      options,
      commandRunner,
      inheritedEnv,
    });
    warnings.push(...optional.warnings);
    const parsedOptional = parseAuditMarkers(optional.stdout);
    warnings.push(...parsedOptional.warnings);
    Object.assign(sections, parsedOptional.sections);
  }

  const replica = executeSql({
    sql: transactionSql(['SHOW REPLICA STATUS'], options.statementTimeoutMs),
    client,
    databaseConfiguration,
    options,
    commandRunner,
    inheritedEnv,
    skipColumnNames: false,
  });
  warnings.push(...replica.warnings);

  const grants = executeSql({
    sql: transactionSql(['SHOW GRANTS FOR CURRENT_USER'], options.statementTimeoutMs),
    client,
    databaseConfiguration,
    options,
    commandRunner,
    inheritedEnv,
  });
  warnings.push(...grants.warnings);

  const generatedAt = now().toISOString();
  const target = targetForReport(databaseConfiguration.endpoint);
  const tables = Array.isArray(sections.tableSizes) ? sections.tableSizes : [];
  const baseline = loadBaseline(options.baseline);
  const missingSections = expectedAuditSections(sections.tableNames).filter((section) => !(section in sections));
  for (const section of missingSections) {
    warnings.push(`Expected audit section ${section} was not collected.`);
  }
  const collectionComplete = missingSections.length === 0
    && !warnings.some((warning) => /^ERROR\s+\d+/i.test(warning));
  const businessQueues = [
    sections.platformOutbox,
    sections.paymentOutbox,
    sections.pluginOutbox,
    sections.competitionReviewOutbox,
  ].filter(Boolean);
  const report = {
    reportVersion: REPORT_VERSION,
    generatedAt,
    auditMode: 'READ_ONLY',
    client: { mode: core.displayMode, dockerImage: client.mode === 'docker' ? MYSQL_DOCKER_IMAGE : null },
    target,
    safety: {
      fixedStatementsValidated: true,
      sessionTransactionReadOnly: true,
      collectionComplete,
      passwordInArgv: false,
      credentialsWrittenToReport: false,
    },
    server: sections.server || null,
    durability: sections.durability || null,
    connections: sections.connections || null,
    innodb: {
      status: sections.innodb || null,
      lockWaits: sections.lockWaits || null,
    },
    slowQueries: {
      configuration: sections.slowQueryConfiguration || null,
      topDigests: sections.slowQueryDigests || [],
      note: 'Digest text and SQL literals are deliberately excluded from the report.',
    },
    replication: {
      channels: sections.replicationChannels || [],
      appliers: sections.replicationAppliers || [],
      replicaStatus: summarizeReplicaStatus(parseTabularOutput(replica.stdout)),
    },
    schema: {
      databaseVersion: sections.databaseVersion || null,
      flywayVersion: sections.flywayVersion || null,
      tableCount: Array.isArray(sections.tableNames) ? sections.tableNames.length : null,
    },
    capacity: {
      tables,
      trackedTables: trackedTableMetrics(tables),
      growth: compareTableGrowth(tables, baseline, generatedAt, target),
    },
    businessQueues,
    businessActivity: {
      asyncTasks: sections.asyncTasks || [],
      consumerReceipts: sections.consumerReceipts || null,
      loginAudit: sections.loginAudit || null,
      operationAudit: sections.operationAudit || null,
      securityAudit: sections.securityAudit || null,
      platformUpdateTasks: sections.platformUpdateTasks || [],
    },
    privileges: summarizeGrants(grants.stdout),
    warnings: sanitizedWarningList(warnings),
    checks: [],
  };
  report.checks = buildChecks(report);

  const serialized = JSON.stringify(report);
  if (serialized.includes(databaseConfiguration.password) || serialized.includes(databaseConfiguration.url)) {
    fail('Secret redaction invariant failed; no report was written.');
  }
  const paths = writeOutput ? writeReports(report, options.outputDir) : null;
  return { report, paths };
}

function main() {
  try {
    const options = parseCliArgs(process.argv.slice(2));
    if (options.help) {
      process.stdout.write(HELP);
      return;
    }
    const result = runProductionAudit({ options });
    process.stdout.write(`Read-only MySQL audit completed with ${result.report.checks.filter((check) => !check.passed).length} unmet gates.\n`);
    process.stdout.write(`JSON report: ${result.paths.jsonPath}\n`);
    process.stdout.write(`Markdown report: ${result.paths.markdownPath}\n`);
  } catch (error) {
    process.stderr.write(`MySQL audit failed: ${error.message}\n`);
    process.exitCode = 1;
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
