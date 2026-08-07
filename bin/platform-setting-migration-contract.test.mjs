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

test('maintenance countdown setting has an explicit existing-database upgrade', () => {
  const baseline = read('lumira-backend/sql/saas.sql');
  const upgrade = read('lumira-backend/sql/upgrade-maintenance-countdown-v1.sql');
  const key = 'branding.maintenance-end-at';

  assert.match(baseline, new RegExp(key));
  assert.match(upgrade, new RegExp(key));
  assert.match(upgrade, /INSERT INTO `sys_platform_setting_definition`/);
  assert.match(upgrade, /INSERT INTO `sys_config`/);
  assert.match(upgrade, /ON DUPLICATE KEY UPDATE/g);
  assert.doesNotMatch(upgrade, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(upgrade, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
});

test('creative maintenance copy upgrade preserves customized existing values', () => {
  const baseline = read('lumira-backend/sql/saas.sql');
  const upgrade = read('lumira-backend/sql/upgrade-maintenance-copy-v1.sql');
  const creativeTitle = '马上回来，精彩不掉线';
  const creativeMessage = '我们正在给系统做个小升级，报名入口很快就回来。请稍等片刻，精彩不会缺席。';

  assert.match(baseline, new RegExp(creativeTitle));
  assert.match(baseline, new RegExp(creativeMessage));
  assert.match(upgrade, /UPDATE `sys_platform_setting_definition`/g);
  assert.match(upgrade, /UPDATE `sys_config`/g);
  assert.match(upgrade, new RegExp(creativeTitle));
  assert.match(upgrade, new RegExp(creativeMessage));
  assert.match(upgrade, /`config_value`\s*=\s*'系统维护中'/);
  assert.match(upgrade, /`config_value`\s*=\s*'服务正在升级优化，请稍后再试。'/);
  assert.doesNotMatch(upgrade, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(upgrade, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
});
