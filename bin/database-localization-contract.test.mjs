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
  for (const loginKey of [
    'page.login.joinUs',
    'page.login.noAccount',
    'page.login.registrationUnavailable',
    'page.login.welcomeTitle',
  ]) {
    assert.ok(keys.includes(loginKey), `${loginKey} must remain database-managed`);
  }
  for (const entry of catalog.entries) {
    assert.ok(entry.translations?.['zh-CN'], `${entry.messageKey} is missing zh-CN`);
    assert.ok(entry.translations?.['en-US'], `${entry.messageKey} is missing en-US`);
    assert.doesNotMatch(
      entry.translations['zh-CN'],
      /\?{2,}/,
      `${entry.messageKey} contains a corrupted zh-CN translation`,
    );
  }
});

test('corrupted payment translations have a guarded forward repair', () => {
  const migration = read('deploy/migrations/V202608030004__repair_payment_localization_catalog.sql');
  for (const messageKey of [
    'payment.connectivity.available',
    'payment.connectivity.notTested',
    'payment.connectivity.unavailable',
    'payment.message.connectivityFailedWithReason',
    'payment.message.missingFields',
    'payment.provider.alipay',
    'payment.provider.wechatPay',
  ]) {
    assert.match(migration, new RegExp(messageKey.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }
  assert.match(migration, /translated_message` REGEXP '\^\[\?\]\+/);
  assert.match(migration, /default_message` REGEXP '\^\[\?\]\+/);
  assert.doesNotMatch(migration, /ON DUPLICATE KEY UPDATE/i);
});

test('upgraded localization tables receive the audit identity schema used by runtime reads', () => {
  const repair = read('deploy/migrations/V202608030006__repair_localization_audit_identity.sql');

  for (const table of [
    'sys_localization_language',
    'sys_localization_namespace',
    'sys_localization_entry',
    'sys_localization_translation',
    'sys_localization_usage_ref',
  ]) {
    assert.match(repair, new RegExp('ALTER TABLE `' + table + '` ADD COLUMN `created_by_uuid`'));
    assert.match(repair, new RegExp(`idx_${table}_creator_uuid`));
  }
  assert.match(repair, /ALTER TABLE `sys_localization_release` ADD COLUMN `published_by_uuid`/);
  assert.match(repair, /idx_sys_localization_release_publisher_uuid/);
  assert.match(repair, /information_schema\.columns/);
  assert.match(repair, /information_schema\.statistics/);
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
