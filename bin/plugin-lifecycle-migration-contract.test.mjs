import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

import { resolveRepoRoot } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const read = (relativePath) => readFileSync(path.join(repoRoot, relativePath), 'utf8');
const migration = read('deploy/migrations/V202608050001__repair_enabled_builtin_plugin_lifecycle.sql');
const baseline = read('lumira-backend/sql/saas.sql');

test('enabled built-in plugins start with an enabled active version', () => {
  for (const pluginCode of ['sensitive-words', 'work-order-feedback']) {
    const seedPattern = new RegExp(
      `'${pluginCode}', '1\\.0\\.0', '1\\.0\\.0', 'INSTALLED', 'LOADED', 'HEALTHY',\\s*` +
        `'ENABLED', 'READY', 1, 0`,
    );
    assert.match(baseline, seedPattern);
  }
});

test('existing enabled built-in plugins receive a guarded lifecycle repair', () => {
  assert.match(migration, /JOIN `sys_plugin_definition`/);
  assert.match(migration, /definition_row\.`status` = 'ENABLED'/);
  assert.match(migration, /version_row\.`plugin_code` IN \('sensitive-words', 'work-order-feedback'\)/);
  assert.match(migration, /version_row\.`lifecycle_status` = 'INSTALLED'/);
  assert.match(migration, /version_row\.`is_active` = 1/);
  assert.match(migration, /version_row\.`load_status` = 'LOADED'/);
  assert.match(migration, /version_row\.`schema_status` = 'READY'/);
  assert.match(migration, /SET version_row\.`lifecycle_status` = 'ENABLED'/);
  assert.match(migration, /'plugin', 'bootstrap'/);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(migration, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
});
