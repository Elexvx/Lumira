import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

test('sensitive words purge capability is enabled for bootstrap and existing databases', () => {
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202608050003__enable_sensitive_words_data_purge.sql');

  assert.match(
    bootstrap,
    /'sensitive-words',[\s\S]*?'SHARED',\s*1,\s*1,[\s\S]*?JSON_ARRAY\('routes', 'menus', 'permissions', 'importers', 'interceptors'\)/,
  );
  assert.match(migration, /SET\s+`supports_data_purge`\s*=\s*1/i);
  assert.match(migration, /WHERE\s+`plugin_code`\s*=\s*'sensitive-words'/i);
  assert.match(migration, /AND\s+`builtin_flag`\s*=\s*1/i);
  assert.match(migration, /migration:V202608050003:sensitive-words-data-purge/);
});

test('sensitive words cannot ship in-process up or destructive down migrations', () => {
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const pluginMigrationService = read('lumira-backend/services/lumira-plugin/src/main/java/com/lumira/saas/modules/plugin/service/PluginMigrationService.java');

  assert.match(bootstrap, /CREATE TABLE(?: IF NOT EXISTS)? `sys_sensitive_word`/i);
  assert.match(pluginMigrationService, /Only EXPAND plugin migrations are allowed/);
  for (const destructiveKeyword of ['DROP', 'TRUNCATE', 'RENAME', 'CHANGE', 'MODIFY']) {
    assert.match(pluginMigrationService, new RegExp(destructiveKeyword));
  }
});
