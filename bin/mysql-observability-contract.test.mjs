import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');
const compose = readFileSync(path.join(repoRoot, 'deploy', 'docker-compose.prod.yml'), 'utf8');
const prometheus = readFileSync(path.join(repoRoot, 'deploy', 'observability', 'prometheus.yml'), 'utf8');
const alertRules = readFileSync(
  path.join(repoRoot, 'deploy', 'observability', 'grafana', 'provisioning', 'alerting', 'rules.yml'),
  'utf8',
);
const dashboard = JSON.parse(readFileSync(
  path.join(repoRoot, 'deploy', 'observability', 'grafana', 'dashboards', 'lumira-mysql.json'),
  'utf8',
));
const runbook = readFileSync(path.join(repoRoot, 'doc', 'runbooks', 'mysql-observability.md'), 'utf8');

function serviceBlock(name) {
  const escaped = name.replaceAll('-', '\\-');
  const match = compose.match(new RegExp(`^  ${escaped}:\\r?\\n[\\s\\S]*?(?=^  [a-zA-Z0-9_-]+:(?:\\s*&[^\\r\\n]+)?\\r?$|^volumes:|^networks:|^secrets:)`, 'm'));
  assert.ok(match, `service ${name} must exist`);
  return match[0];
}

function allStrings(value) {
  if (typeof value === 'string') return [value];
  if (Array.isArray(value)) return value.flatMap(allStrings);
  if (value && typeof value === 'object') return Object.values(value).flatMap(allStrings);
  return [];
}

test('mysqld exporter is isolated, profile-scoped and secret-backed', () => {
  const exporter = serviceBlock('mysqld-exporter');

  assert.match(exporter, /^    profiles:\r?\n    - observability$/m);
  assert.match(exporter, /prom\/mysqld-exporter@sha256:[a-f0-9]{64}/);
  assert.match(exporter, /--mysqld\.username=\$\{MYSQLD_EXPORTER_USERNAME:-exporter\}/);
  assert.match(exporter, /--mysqld\.address=\$\{MYSQLD_EXPORTER_ADDRESS:-mysql:3306\}/);
  assert.match(exporter, /mysql:3306 is only the bundled --local-mysql default/);
  assert.match(exporter, /MYSQLD_EXPORTER_ADDRESS to the DB_URL host:port/);
  assert.match(exporter, /cat \/run\/secrets\/mysql_exporter_password/);
  assert.ok(
    exporter.indexOf('cat /run/secrets/mysql_exporter_password') < exporter.indexOf("grep -Eq '^[[:space:]]*\\[client\\]"),
    'password secret must be exported before either config branch starts',
  );
  assert.match(exporter, /grep -Eq '[^']*\\\[client\\\][^']*' \/run\/secrets\/mysql_exporter_config/);
  assert.match(exporter, /^    user: "0:0"$/m);
  assert.match(exporter, /^    - \/run\/mysqld-exporter:rw,noexec,nosuid,nodev,mode=0700$/m);
  assert.match(exporter, /^    cap_add:\r?\n    - CHOWN\r?\n    - DAC_READ_SEARCH\r?\n    - SETGID\r?\n    - SETUID$/m);
  assert.match(exporter, /install -m 0400 \/run\/secrets\/mysql_exporter_ca/);
  assert.ok(
    exporter.indexOf('chmod 0400 /run/mysqld-exporter/mysql_exporter.cnf')
      < exporter.indexOf('chown 65534:65534 /run/mysqld-exporter/mysql_exporter.cnf'),
    'root must set restrictive modes before transferring ownership without CAP_FOWNER',
  );
  assert.ok(
    exporter.indexOf('chmod 0700 /run/mysqld-exporter')
      < exporter.lastIndexOf('chown 65534:65534 /run/mysqld-exporter'),
    'the tmpfs directory mode must be finalized before transferring ownership',
  );
  assert.match(exporter, /ssl-ca=\/run\/mysqld-exporter\/mysql_exporter_ca/);
  assert.match(exporter, /\/bin\/chpst -u nobody:nobody \/bin\/mysqld_exporter --config\.my-cnf=\/run\/mysqld-exporter\/mysql_exporter\.cnf/);
  assert.match(exporter, /\/bin\/chpst -u nobody:nobody \/bin\/mysqld_exporter "\$\$@"/);
  assert.match(exporter, /ssl-skip-veri\?fication/);
  assert.match(exporter, /TLS certificate verification cannot be disabled/);
  assert.match(exporter, /ssl-ca\[\[:space:\]\]\*=/);
  assert.match(exporter, /tls\[\[:space:\]\]\*=/);
  assert.match(exporter, /managed MySQL exporter config must use ssl-ca=/);
  assert.match(exporter, /managed MySQL exporter config must use tls=custom/);
  assert.match(exporter, /^    - source: mysql_exporter_config\r?\n      target: mysql_exporter_config$/m);
  assert.match(exporter, /^    - source: mysql_exporter_ca\r?\n      target: mysql_exporter_ca$/m);
  assert.match(exporter, /^    read_only: true$/m);
  assert.match(exporter, /^    - no-new-privileges:true$/m);
  assert.match(exporter, /^    cap_drop:\r?\n    - ALL$/m);
  assert.match(exporter, /^    expose:\r?\n    - '9104'$/m);
  assert.match(exporter, /grep -Eq '\^mysql_up\(\\\{\[\^}\]\*\\\}\)\?/);
  assert.match(exporter, /\[\[:space:\]\]\+1\(\[\.\]0\+\)\?/);
  assert.doesNotMatch(exporter, /grep -q '\^mysql_up '/);
  assert.doesNotMatch(exporter, /^    ports:/m, 'exporter must not publish a host port');
  assert.doesNotMatch(exporter, /--mysqld\.(?:password|dsn)|DATA_SOURCE_NAME/);

  assert.match(
    compose,
    /^secrets:\r?\n  mysql_exporter_password:\r?\n    file: \$\{MYSQLD_EXPORTER_PASSWORD_FILE:-\.\/\.generated\/secrets\/mysql-exporter-password\}$/m,
  );
  assert.match(
    compose,
    /^  mysql_exporter_config:\r?\n    file: \$\{MYSQLD_EXPORTER_CONFIG_FILE:-\$\{MYSQLD_EXPORTER_PASSWORD_FILE:-\.\/\.generated\/secrets\/mysql-exporter-password\}\}$/m,
  );
  assert.match(
    compose,
    /^  mysql_exporter_ca:\r?\n    file: \$\{MYSQLD_EXPORTER_CA_FILE:-\$\{MYSQLD_EXPORTER_PASSWORD_FILE:-\.\/\.generated\/secrets\/mysql-exporter-password\}\}$/m,
  );
  assert.match(compose, /ssl-ca=\/run\/secrets\/mysql_exporter_ca/);
  assert.match(compose, /tls=custom/);
  assert.match(compose, /host matching the certificate SAN/);
  assert.doesNotMatch(compose, /tls-server-name/);
  assert.doesNotMatch(compose, /tls-min-version/);
  assert.doesNotMatch(compose, /tls-max-version/);
  assert.doesNotMatch(compose, /\$\{MYSQLD_EXPORTER_PASSWORD(?::[^}]*)?\}/, 'password must not be interpolated into container configuration');
  assert.doesNotMatch(prometheus, /password|MYSQLD_EXPORTER_PASSWORD/i);
  assert.match(runbook, /^ssl-ca = \/run\/secrets\/mysql_exporter_ca$/m);
  assert.match(runbook, /^tls = custom$/m);
  assert.doesNotMatch(runbook, /tls-server-name|tls-min-version|tls-max-version/);
});

test('exporter collectors cover MySQL capacity, locks, statements and replication', () => {
  const exporter = serviceBlock('mysqld-exporter');
  for (const collector of [
    'info_schema.innodb_metrics',
    'info_schema.innodb_tablespaces',
    'info_schema.processlist',
    'info_schema.tables',
    'perf_schema.eventsstatements',
    'perf_schema.tablelocks',
    'perf_schema.replication_group_members',
    'perf_schema.replication_group_member_stats',
    'perf_schema.replication_applier_status_by_worker',
  ]) {
    assert.match(exporter, new RegExp(`--collect\\.${collector.replaceAll('.', '\\.')}(?:=|\\r?$)`, 'm'));
  }
});

test('backup textfile metrics are collected without host mounts or a public port', () => {
  const exporter = serviceBlock('backup-metrics-exporter');

  assert.match(exporter, /^    profiles:\r?\n    - observability$/m);
  assert.match(exporter, /prom\/node-exporter@sha256:[a-f0-9]{64}/);
  assert.match(exporter, /--collector\.disable-defaults/);
  assert.match(exporter, /--collector\.textfile/);
  assert.match(exporter, /--collector\.textfile\.directory=\/var\/lib\/node-exporter\/textfile/);
  assert.match(exporter, /\.\/\.generated\/backup-metrics:\/var\/lib\/node-exporter\/textfile:ro/);
  assert.doesNotMatch(exporter, /--collector\.filesystem|\/:\/host|^    ports:/m);
  assert.doesNotMatch(compose, /pushgateway/i);
});

test('Prometheus scrapes both deployment slots and both database exporters', () => {
  for (const target of [
    'lumira-server-blue:8080',
    'lumira-server-green:8080',
    'mysqld-exporter:9104',
    'backup-metrics-exporter:9100',
  ]) {
    assert.match(prometheus, new RegExp(`- ${target.replaceAll('.', '\\.')}`));
  }
  assert.doesNotMatch(prometheus, /^\s+- lumira-server:8080$/m);

  const prometheusService = serviceBlock('prometheus');
  assert.match(prometheusService, /^      mysqld-exporter:$/m);
  assert.match(prometheusService, /^      backup-metrics-exporter:$/m);
  assert.doesNotMatch(prometheusService, /^\s+- lumira-server$/m);
});

test('local MySQL has configurable durable binlog and observability defaults', () => {
  const mysql = serviceBlock('mysql');
  const expectedArguments = [
    '--server-id=${MYSQL_SERVER_ID:-1}',
    '--log-bin=${MYSQL_LOG_BIN_BASENAME:-mysql-bin}',
    '--binlog-format=${MYSQL_BINLOG_FORMAT:-ROW}',
    '--gtid-mode=${MYSQL_GTID_MODE:-ON}',
    '--enforce-gtid-consistency=${MYSQL_ENFORCE_GTID_CONSISTENCY:-ON}',
    '--binlog-expire-logs-seconds=${MYSQL_BINLOG_EXPIRE_LOGS_SECONDS:-1209600}',
    '--sync-binlog=${MYSQL_SYNC_BINLOG:-1}',
    '--innodb-flush-log-at-trx-commit=${MYSQL_INNODB_FLUSH_LOG_AT_TRX_COMMIT:-1}',
    '--performance-schema=${MYSQL_PERFORMANCE_SCHEMA:-ON}',
  ];
  for (const argument of expectedArguments) assert.ok(mysql.includes(argument), `${argument} must be configured`);
});

test('Grafana rules cover MySQL, Hikari, backup and Outbox failure modes', () => {
  const requiredRuleIds = [
    'lumira-mysql-down',
    'lumira-mysql-connections-high',
    'lumira-mysql-storage-high',
    'lumira-mysql-slow-queries',
    'lumira-mysql-lock-waits',
    'lumira-mysql-deadlocks',
    'lumira-mysql-replication-unhealthy',
    'lumira-mysql-replication-lag',
    'lumira-mysql-backup-stale',
    'lumira-hikari-pending',
    'lumira-hikari-timeouts',
    'lumira-outbox-oldest-pending',
    'lumira-platform-event-outbox-dead-letter',
  ];
  for (const id of requiredRuleIds) assert.match(alertRules, new RegExp(`^      - uid: ${id}$`, 'm'));

  for (const metric of [
    'mysql_up',
    'mysql_global_status_threads_connected',
    'lumira_mysql_storage_used_ratio',
    'mysql_global_status_slow_queries',
    'mysql_global_status_innodb_row_lock_current_waits',
    'mysql_global_status_innodb_deadlocks',
    'mysql_slave_status_seconds_behind_source',
    'lumira_mysql_backup_last_success_timestamp_seconds',
    'hikaricp_connections_pending',
    'hikaricp_connections_timeout_total',
    'lumira_outbox_oldest_pending_age_seconds',
    'platform_event_outbox_dead_letter',
  ]) {
    assert.match(alertRules, new RegExp(`\\b${metric}\\b`));
  }

  const uids = [...alertRules.matchAll(/^      - uid: ([a-z0-9-]+)$/gm)].map((match) => match[1]);
  assert.equal(new Set(uids).size, uids.length, 'Grafana alert UIDs must be unique');
});

test('MySQL dashboard is provisionable and documents optional metric interfaces', () => {
  assert.equal(dashboard.uid, 'lumira-mysql-reliability');
  assert.equal(dashboard.title, 'Lumira MySQL and Data Reliability');
  assert.ok(dashboard.panels.length >= 12);

  const strings = allStrings(dashboard);
  for (const expected of [
    'MySQL UP',
    'Connection usage',
    'Backup age',
    'Storage used',
    'Replication lag and queue',
    'Outbox reliability',
    'lumira_mysql_backup_last_success_timestamp_seconds',
    'lumira_mysql_storage_used_ratio',
    'lumira_outbox_oldest_pending_age_seconds',
  ]) {
    assert.ok(strings.some((value) => value.includes(expected)), `dashboard must include ${expected}`);
  }
});
