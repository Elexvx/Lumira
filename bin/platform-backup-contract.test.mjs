import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { validateBackupEvidence } from './lib/platform-backup-contract.mjs';

function fixture(overrides = {}) {
  const directory = mkdtempSync(path.join(os.tmpdir(), 'lumira-backup-contract-'));
  const relativeDumpPath = 'mysql-saas.sql';
  const dump = `${'CREATE TABLE example (id bigint);\n'.repeat(64)}`;
  const hash = createHash('sha256').update(dump).digest('hex');
  writeFileSync(path.join(directory, relativeDumpPath), dump);
  const mysql = {
    path: relativeDumpPath,
    sha256: hash,
    size: Buffer.byteLength(dump),
    serverVersion: '8.4.6',
    serverUuid: '3de21476-65fc-11f0-9128-0242ac120002',
    gtidExecuted: null,
    binlogFile: null,
    binlogPosition: null,
    databaseVersion: null,
    tableCount: 1,
    schemaFingerprint: 'b'.repeat(64),
    ...(overrides.mysql || {}),
  };
  const manifest = {
    schemaVersion: 1,
    status: 'complete',
    secretsIncluded: false,
    backupId: 'backup-20260819-010203',
    createdAt: '2026-08-19T01:02:03.000Z',
    databaseName: 'saas',
    ...overrides,
    mysql,
  };
  writeFileSync(path.join(directory, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
  writeFileSync(path.join(directory, 'SHA256SUMS'), `${hash}  ${relativeDumpPath}\n`);
  writeFileSync(path.join(directory, '.complete'), '');
  return { directory, manifest, dumpPath: path.join(directory, relativeDumpPath) };
}

test('accepts complete, secret-free, recent backup evidence', (context) => {
  const value = fixture();
  context.after(() => rmSync(value.directory, { recursive: true, force: true }));
  const evidence = validateBackupEvidence(value.directory, {
    now: Date.parse('2026-08-19T01:10:00.000Z'),
    maxAgeMs: 15 * 60_000,
  });
  assert.equal(evidence.backupId, value.manifest.backupId);
  assert.equal(evidence.databaseName, 'saas');
  assert.equal(evidence.dumpSha256, value.manifest.mysql.sha256);
});

test('rejects changed dump content even when completion marker exists', (context) => {
  const value = fixture();
  context.after(() => rmSync(value.directory, { recursive: true, force: true }));
  writeFileSync(value.dumpPath, 'tampered');
  assert.throws(() => validateBackupEvidence(value.directory), /size does not match|SHA-256/);
});

test('rejects plaintext deployment secrets inside a backup', (context) => {
  const value = fixture();
  context.after(() => rmSync(value.directory, { recursive: true, force: true }));
  writeFileSync(path.join(value.directory, 'deploy.env.snapshot'), 'DB_PASSWORD=secret\n');
  assert.throws(() => validateBackupEvidence(value.directory), /plaintext deploy\.env\.snapshot/);
});

test('rejects dump paths that escape the backup directory', (context) => {
  const value = fixture({
    mysql: {
      path: '../outside.sql',
      sha256: 'a'.repeat(64),
      size: 1024,
    },
  });
  context.after(() => rmSync(value.directory, { recursive: true, force: true }));
  assert.throws(() => validateBackupEvidence(value.directory), /escapes the backup directory/);
});

test('rejects stale backup evidence for online database migration', (context) => {
  const value = fixture();
  context.after(() => rmSync(value.directory, { recursive: true, force: true }));
  assert.throws(() => validateBackupEvidence(value.directory, {
    now: Date.parse('2026-08-19T02:02:03.000Z'),
    maxAgeMs: 15 * 60_000,
  }), /older than the allowed/);
});

test('rejects a valid backup for a different database', (context) => {
  const value = fixture();
  context.after(() => rmSync(value.directory, { recursive: true, force: true }));
  assert.throws(() => validateBackupEvidence(value.directory, {
    expectedDatabaseName: 'lumira_production',
  }), /does not match the deployment database/);
});

test('requires restore-grade schema evidence, not only a dump checksum', (context) => {
  const value = fixture({
    mysql: {
      tableCount: undefined,
    },
  });
  context.after(() => rmSync(value.directory, { recursive: true, force: true }));
  assert.throws(() => validateBackupEvidence(value.directory), /tableCount/);
});

test('requires all three durable evidence files', (context) => {
  const directory = mkdtempSync(path.join(os.tmpdir(), 'lumira-backup-incomplete-'));
  context.after(() => rmSync(directory, { recursive: true, force: true }));
  mkdirSync(path.join(directory, 'nested'));
  assert.throws(() => validateBackupEvidence(directory), /Backup manifest is missing/);
});
