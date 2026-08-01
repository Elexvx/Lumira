import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

import { resolveRepoRoot } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const read = (file) => readFileSync(path.join(repoRoot, file), 'utf8');

test('maintenance-mode settings are available to fresh and upgraded databases', () => {
  const baseline = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202608010001__seed_maintenance_mode_platform_settings.sql');

  for (const key of [
    'branding.maintenance-mode-enabled',
    'branding.maintenance-title',
    'branding.maintenance-message',
  ]) {
    assert.match(baseline, new RegExp(key));
    assert.match(migration, new RegExp(key));
  }

  assert.match(migration, /INSERT INTO `sys_platform_setting_definition`/);
  assert.match(migration, /INSERT INTO `sys_config`/);
  assert.match(migration, /ON DUPLICATE KEY UPDATE/g);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(migration, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
});
