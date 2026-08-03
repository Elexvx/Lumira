import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

import { resolveRepoRoot } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const read = (file) => readFileSync(path.join(repoRoot, file), 'utf8');

test('UI localization is database-owned and frontend catalogs are empty', () => {
  for (const localeFile of ['lumira-ui/src/locales/zh-CN.ts', 'lumira-ui/src/locales/en-US.ts']) {
    assert.match(read(localeFile), /export default \{\} as Record<string, string>;/);
  }

  for (const localeDirectory of ['lumira-ui/src/locales/zh-CN', 'lumira-ui/src/locales/en-US']) {
    const absolute = path.join(repoRoot, localeDirectory);
    if (existsSync(absolute)) {
      assert.deepEqual(readdirSync(absolute, { withFileTypes: true }).filter((entry) => entry.isFile()), []);
    }
  }

  const databaseMessage = read('lumira-ui/src/i18n/databaseMessage.ts');
  const runtimeLocalization = read('lumira-ui/src/i18n/runtimeLocalization.ts');
  assert.doesNotMatch(databaseMessage, /@\/locales/);
  assert.match(runtimeLocalization, /installDatabaseMessages\(normalizedLocale, bundle\.messages\)/);
});

test('database catalog has unique, complete Chinese and English entries', () => {
  const catalog = JSON.parse(read('lumira-backend/services/lumira-localization/src/main/resources/localization/ui-catalog.json'));
  assert.ok(catalog.entries.length > 2000, 'database catalog should contain the migrated application UI');
  const keys = catalog.entries.map((entry) => entry.messageKey);
  assert.equal(new Set(keys).size, keys.length, 'database localization keys must be unique');
  for (const entry of catalog.entries) {
    assert.ok(entry.translations?.['zh-CN'], `${entry.messageKey} is missing zh-CN`);
    assert.ok(entry.translations?.['en-US'], `${entry.messageKey} is missing en-US`);
  }
});

test('catalog initialization preserves database-managed edits', () => {
  const initializer = read('lumira-backend/services/lumira-localization/src/main/java/com/lumira/localization/app/DatabaseLocalizationCatalogInitializer.java');
  const persistenceAdapter = read('lumira-backend/services/lumira-localization/src/main/java/com/lumira/localization/infrastructure/persistence/JdbcLocalizationCatalogRepository.java');
  assert.match(initializer, /catalogRepository\.initialize\(entries\)/);
  assert.doesNotMatch(initializer, /JdbcTemplate|INSERT INTO|INSERT IGNORE/);
  assert.match(persistenceAdapter, /INSERT IGNORE INTO sys_localization_entry/);
  assert.match(persistenceAdapter, /INSERT IGNORE INTO sys_localization_translation/);
  assert.doesNotMatch(persistenceAdapter, /ON DUPLICATE KEY UPDATE/);

  const localizationPage = read('lumira-ui/src/pages/settings/localization/LocalizationPage.tsx');
  assert.doesNotMatch(localizationPage, /@\/locales/);
  assert.doesNotMatch(localizationPage, /\/v1\/localization\/sync/);
});
