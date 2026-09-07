import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { normalizeReleaseManifest } from './lib/platform-update-contract.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (relativePath) => readFileSync(path.join(repoRoot, relativePath), 'utf8');
const digest = (character) => `sha256:${character.repeat(64)}`;

test('plugin migration evidence is part of the signed Release Set contract', () => {
  const manifest = normalizeReleaseManifest({
    schemaVersion: 3,
    releaseId: 'v2026.09.07',
    commit: 'abcdef0123456789abcdef0123456789abcdef01',
    images: {
      server: `ghcr.io/elexvx/lumira/server@${digest('a')}`,
      frontend: `ghcr.io/elexvx/lumira/frontend@${digest('b')}`,
      async: `ghcr.io/elexvx/lumira/async@${digest('c')}`,
      jobExecutor: `ghcr.io/elexvx/lumira/job@${digest('d')}`,
      migrator: `ghcr.io/elexvx/lumira/migrator@${digest('e')}`,
    },
    plugins: [{
      pluginCode: 'payment-extension',
      pluginVersion: '1.3.0',
      migrationVersion: '5',
      schemaDigest: digest('f'),
      migrationDigest: digest('0'),
      compatibleReaders: ['1.2.x', '1.3.x'],
      phase: 'expand',
      rollbackMode: 'APPLICATION_ONLY',
    }],
  });

  assert.equal(manifest.plugins[0].migrationVersion, '5');
  assert.match(manifest.plugins[0].schemaDigest, /^sha256:[0-9a-f]{64}$/);
  assert.equal(manifest.plugins[0].phase, 'expand');
  assert.equal(manifest.plugins[0].rollbackMode, 'APPLICATION_ONLY');
});

test('central migrator and fresh schema expose execution and schema evidence tables', () => {
  const migration = read('deploy/migrations/V202609070001__add_plugin_migration_execution_evidence.sql');
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const executor = read('deploy/plugin-migrator/src/main/java/com/lumira/deploy/pluginmigration/PluginMigrationExecutor.java');
  const repository = read('deploy/plugin-migrator/src/main/java/com/lumira/deploy/pluginmigration/PluginMigrationRepository.java');

  for (const source of [migration, bootstrap]) {
    assert.match(source, /plugin_migration_execution_log/);
    assert.match(source, /plugin_schema_snapshot/);
    assert.match(source, /active_request_id/);
  }
  assert.match(executor, /startExecutionLog/);
  assert.match(executor, /captureSchemaSnapshot/);
  assert.match(executor, /expectedSchemaDigest/);
  assert.match(repository, /executor_type/);
  assert.match(repository, /CENTRAL_MIGRATOR/);
  assert.match(repository, /SHOW CREATE TABLE/i);
});
