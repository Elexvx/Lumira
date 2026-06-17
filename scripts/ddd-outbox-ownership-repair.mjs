#!/usr/bin/env node

import { mkdirSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';

const outputDir = process.env.DDD_OUTBOX_REPAIR_DIR || path.join('artifacts', 'ddd', 'outbox');
const outputFile = path.join(outputDir, 'outbox-ownership-repair.json');
const applyRepair = process.env.DDD_OUTBOX_REPAIR_APPLY === 'true';
const mysqlConfig = {
  host: process.env.MYSQL_HOST || '127.0.0.1',
  port: process.env.MYSQL_PORT || '3307',
  user: process.env.MYSQL_USER || 'root',
  password: process.env.MYSQL_PASSWORD || '',
  database: process.env.DDD_EXPLAIN_DATABASE || process.env.MYSQL_DATABASE || 'saas',
};

const mysqlArgs = (sql) => {
  const args = ['--batch', '--raw', '--skip-column-names', '--execute', sql];
  if (mysqlConfig.host) {
    args.push('--host', mysqlConfig.host);
  }
  if (mysqlConfig.port) {
    args.push('--port', mysqlConfig.port);
  }
  if (mysqlConfig.user) {
    args.push('--user', mysqlConfig.user);
  }
  if (mysqlConfig.database) {
    args.push(mysqlConfig.database);
  }
  return args;
};

const runMysql = (sql) => {
  const result = spawnSync(process.env.MYSQL_CLI || 'mysql', mysqlArgs(sql), {
    encoding: 'utf8',
    env: {
      ...process.env,
      ...(mysqlConfig.password ? { MYSQL_PWD: mysqlConfig.password } : {}),
    },
  });
  if (result.status !== 0) {
    throw new Error(`mysql query failed: ${result.stderr || result.stdout}`);
  }
  return result.stdout.trim();
};

const queryJson = (sql) => {
  const text = runMysql(sql);
  if (!text) {
    return [];
  }
  return text.split('\n').map((line) => JSON.parse(line));
};

const ownershipFailureSql = `
  from platform_event_outbox
  where deleted = 0
    and source_type <> 'MESSAGE'
    and last_error like '%payload 反序列化失败%'
`;

const loadRows = () => queryJson(`
  select json_object(
    'id', id,
    'tenantId', tenant_id,
    'sourceType', source_type,
    'eventType', event_type,
    'eventKey', event_key,
    'dispatchStatus', dispatch_status,
    'retryCount', retry_count,
    'lastError', last_error
  )
  ${ownershipFailureSql}
  order by id
`);

const countBySource = () => queryJson(`
  select json_object(
    'sourceType', source_type,
    'rows', count(1),
    'minId', min(id),
    'maxId', max(id)
  )
  ${ownershipFailureSql}
  group by source_type
  order by source_type
`);

const repair = () => {
  const output = runMysql(`
    update platform_event_outbox
    set dispatch_status = 'RECORDED',
        retry_count = 0,
        next_retry_at = null,
        delivered_at = null,
        last_error = null,
        updated_at = now(),
        updated_by = coalesce(updated_by, 0)
    where deleted = 0
      and source_type <> 'MESSAGE'
      and last_error like '%payload 反序列化失败%'
  `);
  const match = output.match(/Rows matched:\s*(\d+)\s+Changed:\s*(\d+)/);
  return {
    rowsMatched: match ? Number(match[1]) : null,
    rowsChanged: match ? Number(match[2]) : null,
    raw: output,
  };
};

const startedAt = new Date();
const before = {
  rows: loadRows(),
  bySource: countBySource(),
};
const repairResult = applyRepair ? repair() : null;
const after = {
  rows: loadRows(),
  bySource: countBySource(),
};
const finishedAt = new Date();

const artifact = {
  status: after.rows.length === 0 ? 'PASS' : applyRepair ? 'FAIL' : 'DRY_RUN',
  mode: applyRepair ? 'apply' : 'dry-run',
  startedAt: startedAt.toISOString(),
  finishedAt: finishedAt.toISOString(),
  elapsedMs: finishedAt.getTime() - startedAt.getTime(),
  rule: "Reset non-MESSAGE platform_event_outbox rows failed by Message payload deserialization back to RECORDED for their owner relay.",
  before,
  repairResult,
  after,
  nextSteps: applyRepair
    ? [
        "Run owner replay for repaired rows, for example POST /file/internal/jobs/outbox/{id}/replay with X-Job-Token for FILE rows.",
        "Run scripts/ddd-job-e2e-smoke.mjs with DDD_JOB_SMOKE_DB_CHECK=true to verify no new cross-owner payload failures.",
      ]
    : [
        "Review before.rows and rerun with DDD_OUTBOX_REPAIR_APPLY=true when the matched rows are expected owner events.",
      ],
};

mkdirSync(outputDir, { recursive: true });
writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);
console.log(JSON.stringify(artifact, null, 2));
console.log(`Wrote ${outputFile}`);

if (artifact.status === 'FAIL') {
  process.exit(1);
}
