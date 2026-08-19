import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { crc32 } from 'node:zlib';

import { resolveRepoRoot } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const read = (file) => readFileSync(path.join(repoRoot, file), 'utf8');
const flywayChecksum = (source) => source
  .split(/\r?\n/u)
  .reduce((checksum, line) => crc32(Buffer.from(line, 'utf8'), checksum), 0) | 0;

test('fresh bootstrap and online migration both contain activity persistence', () => {
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202607190001__activity_registration_persistence.sql');
  for (const marker of [
    'aiadc_activity_registration',
    'aiadc_activity_locale',
    'aiadc_activity_status',
    'aiadc_activity_public_status',
  ]) {
    assert.match(bootstrap, new RegExp(marker));
    assert.match(migration, new RegExp(marker));
  }
});

test('upgraded databases receive and backfill the event catalog projection', () => {
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202608120001__repair_event_catalog_projection.sql');

  assert.match(bootstrap, /CREATE TABLE `event_catalog_item`/);
  assert.match(migration, /CREATE TABLE IF NOT EXISTS `event_catalog_item`/);
  assert.match(migration, /FROM `aiadc_activity` a/);
  assert.match(migration, /FROM `aiadc_competition` c/);
  assert.match(migration, /platform_event_outbox/);
  assert.doesNotMatch(migration, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
  assert.doesNotMatch(migration, /\bTRUNCATE\b/i);
});

test('competition homepage is absent from the fresh schema and has an explicit legacy cleanup', () => {
  const baseline = read('lumira-backend/sql/saas.sql');
  const cleanup = read('lumira-backend/sql/upgrade-competition-remove-homepage-content-v1.sql');

  assert.doesNotMatch(baseline, /`homepage_content`/);
  assert.match(cleanup, /information_schema\.columns/);
  assert.match(cleanup, /DROP COLUMN `homepage_content`/);
});

test('member grade year migration creates separate enrollment dates without touching registration snapshots', () => {
  const migration = read('deploy/migrations/V202607190002__replace_member_grade_year_with_enrollment_dates.sql');
  assert.match(migration, /'enrollmentDate', '入学时间'/);
  assert.match(migration, /'graduationDate', '毕业时间'/);
  assert.match(migration, /grade_year\.`required_flag`/);
  assert.match(migration, /item\.`deleted` = 1/);
  assert.doesNotMatch(migration, /competition_registration/);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
});

test('member enrollment date fields are relabeled as year-only fields', () => {
  const migration = read('deploy/migrations/V202607190003__rename_member_enrollment_year_fields.sql');
  assert.match(migration, /`item_key` = 'enrollmentDate'/);
  assert.match(migration, /`title` = '入学年份'/);
  assert.match(migration, /`item_key` = 'graduationDate'/);
  assert.match(migration, /`title` = '毕业年份'/);
  assert.doesNotMatch(migration, /competition_registration/);
});

test('online migration seeds the file service business dictionaries', () => {
  const migration = read('deploy/migrations/V202607190004__file_business_policy_dictionary.sql');
  const baseline = read('lumira-backend/sql/saas.sql');
  const markers = [
    'file_storage_provider',
    'file_preview_extension',
    'file_preview_content_type',
    'file_runtime_default',
    'UNSUPPORTED_PREVIEW_MODE',
  ];

  for (const marker of markers) {
    assert.match(migration, new RegExp(marker));
    assert.match(baseline, new RegExp(marker));
  }
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
});

test('built-in plugin routes match between fresh bootstrap and online migration', () => {
  const migration = read('deploy/migrations/V202607280001__relocate_builtin_plugin_routes.sql');
  const baseline = read('lumira-backend/sql/saas.sql');

  for (const marker of [
    'plugin.sensitive-words',
    '/settings/sensitive-words',
    'plugin.work-order-feedback',
    '/work-order-feedback',
    'settings.root',
  ]) {
    assert.match(migration, new RegExp(marker.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
    assert.match(baseline, new RegExp(marker.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
});

test('profile and team-member field definitions remain isolated in fresh and existing databases', () => {
  const migration = read('deploy/migrations/V202607190005__profile_field_definition_persistence.sql');
  const baseline = read('lumira-backend/sql/saas.sql');

  for (const marker of [
    'sys_profile_field_definition',
    'profile_settings_page_key',
    "'PROFILE'",
    "'TEAM_MEMBER'",
    'profile.field.real-name.visible',
    'team.member.field.member-name.visible',
  ]) {
    assert.match(migration, new RegExp(marker));
    assert.match(baseline, new RegExp(marker));
  }
  assert.match(migration, /UNIQUE KEY `uk_profile_field_page_key` \(`page_key`,`field_key`\)/);
  assert.doesNotMatch(migration, /competition_registration/);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
});

test('competition registration snapshots stay in one registration row', () => {
  const migration = read('deploy/migrations/V202607190006__isolate_competition_registration_snapshots.sql');
  const baseline = read('lumira-backend/sql/saas.sql');

  assert.match(baseline, /`registration_snapshot_json` longtext/);
  assert.match(baseline, /idx_competition_registration_export.*`competition_id`,`deleted`,`id`/);
  assert.match(migration, /information_schema\.columns/);
  assert.match(migration, /column_name = 'registration_snapshot_json'/);
  assert.match(migration, /ADD COLUMN `?registration_snapshot_json`? longtext/);
  assert.match(migration, /information_schema\.statistics/);
  assert.match(migration, /index_name = 'idx_competition_registration_export'/);
  assert.match(migration, /idx_competition_registration_export.*`?competition_id`?, `?deleted`?, `?id`?/);
  assert.match(migration, /JSON_EXTRACT\(`team_snapshot_json`, '\$\.registrationExtraValues'\)/);
  assert.match(migration, /JSON_REMOVE\(`team_snapshot_json`, '\$\.registrationExtraValues'\)/);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(migration, /\b(aiadc_project|team_member|sys_user)\b/i);
});

test('registration dataset and async export migration matches fresh bootstrap', () => {
  const migration = read('deploy/migrations/V202607290001__competition_registration_datasets.sql');
  const permissionMigration = read('deploy/migrations/V202607290002__competition_review_domain.sql');
  const baseline = read('lumira-backend/sql/saas.sql');
  for (const marker of [
    'competition_registration_dataset',
    'competition_registration_dataset_row',
    'idx_sys_export_task_module_queue',
  ]) {
    assert.match(migration, new RegExp(marker));
    assert.match(baseline, new RegExp(marker));
  }
  for (const marker of [
    'registration:dataset:view',
    'registration:dataset:export',
    'registration:material:download',
  ]) {
    assert.match(permissionMigration, new RegExp(marker));
    assert.match(baseline, new RegExp(marker));
  }
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
});

test('versioned review domain migration matches fresh bootstrap', () => {
  const migration = read('deploy/migrations/V202607290002__competition_review_domain.sql');
  const baseline = read('lumira-backend/sql/saas.sql');
  for (const marker of [
    'competition_review_plan',
    'competition_review_batch',
    'competition_review_candidate',
    'competition_review_assignment',
    'competition_review_sheet',
    'competition_review_aggregate',
    'competition_review_publication',
    'competition_review_appeal',
    'review:appeal:submit',
    'review:appeal:manage',
  ]) {
    assert.match(migration, new RegExp(marker));
    assert.match(baseline, new RegExp(marker));
  }
  assert.match(migration, /uk_competition_review_appeal_result/);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
});

test('intellectual-property opt-in migration bootstraps missing configuration templates', () => {
  const migration = read('deploy/migrations/V202607300002__make_intellectual_property_collection_opt_in.sql');
  const baseline = read('lumira-backend/sql/saas.sql');
  const createPosition = migration.indexOf('CREATE TABLE IF NOT EXISTS `competition_config_item_template`');
  const seedPosition = migration.indexOf('INSERT INTO `competition_config_item_template`');
  const updatePosition = migration.indexOf('UPDATE `competition_config_item_template`');

  assert.ok(createPosition >= 0, 'online migration must create the template table for legacy databases');
  assert.ok(seedPosition > createPosition, 'template rows must be seeded after the table exists');
  assert.ok(updatePosition > seedPosition, 'opt-in update must run after default rows are available');
  for (const marker of [
    'uk_competition_config_item_template_key',
    'intellectualPropertyType',
    'distributionRegions',
  ]) {
    assert.match(migration, new RegExp(marker));
    assert.match(baseline, new RegExp(marker));
  }
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
});

test('intellectual-property fields are enabled by default without overriding existing competition choices', () => {
  const migration = read('deploy/migrations/V202608110001__enable_intellectual_property_fields_by_default.sql');
  const baseline = read('lumira-backend/sql/saas.sql');
  const upgrade = read('lumira-backend/sql/upgrade-competition-config-item-templates-v1.sql');
  const fieldKeys = [
    'intellectualPropertyType',
    'intellectualPropertyName',
    'registrationNumber',
    'rightsHolder',
    'legalStatus',
    'grantDate',
    'distributionRegions',
  ];

  assert.match(migration, /UPDATE `competition_config_item_template`/);
  assert.match(migration, /SET `enabled` = 1/);
  assert.doesNotMatch(migration, /UPDATE `competition_config_item`(?:\s|`)/);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);

  for (const source of [baseline, upgrade]) {
    for (const fieldKey of fieldKeys) {
      const row = source.split('\n').find((line) => line.includes(`'${fieldKey}'`));
      assert.ok(row, `missing default template row for ${fieldKey}`);
      assert.match(row.trimEnd(), /,\d+,[01],1,0\)[,;]?$/, `${fieldKey} must default to enabled`);
    }
  }
});

test('built-in mock payment migration guards attempt columns with MySQL-compatible DDL', () => {
  const migration = read('deploy/migrations/V202608110002__add_builtin_mock_payment_plugin.sql');

  assert.doesNotMatch(migration, /ADD\s+COLUMN\s+IF\s+NOT\s+EXISTS/i);
  assert.match(migration, /information_schema\.columns/i);
  assert.match(migration, /column_name\s*=\s*'attempt_no'/i);
  assert.match(migration, /PREPARE builtin_mock_attempt_no_statement/i);
  assert.match(migration, /EXECUTE builtin_mock_attempt_no_statement/i);
});

test('sensitive-word policy dictionaries stay aligned across every database lifecycle', () => {
  const baseline = read('lumira-backend/sql/saas.sql');
  const manualUpgrade = read('lumira-backend/sql/upgrade-sensitive-word-policy-dictionary-v1.sql');
  const migration = read('deploy/migrations/V202608110003__seed_sensitive_word_policy_dictionary.sql');
  const dictionaryCodes = [
    'sys_sensitive_word_action',
    'sys_sensitive_word_blocking_action',
    'sys_sensitive_word_default_category',
    'sys_sensitive_word_import_category',
    'sys_sensitive_word_default_severity',
    'sys_sensitive_word_severity',
  ];

  for (const source of [baseline, manualUpgrade, migration]) {
    for (const dictionaryCode of dictionaryCodes) {
      assert.match(source, new RegExp(`'${dictionaryCode}'`));
    }
    assert.match(
      source,
      /'BLOCK',\s*'阻断',[\s\S]*?dict_code`='sys_sensitive_word_blocking_action'/,
    );
    assert.ok(
      (source.match(/ON DUPLICATE KEY UPDATE/g) || []).length >= 2,
      'dictionary type and item seeds must both be idempotent',
    );
  }

  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(migration, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
});

test('expert status dictionary localization repairs corrupted legacy labels', () => {
  const baseline = read('lumira-backend/sql/saas.sql');
  const manualUpgrade = read('lumira-backend/sql/upgrade-expert-status-dictionary-persistence-v1.sql');
  const migration = read('deploy/migrations/V202608130001__repair_expert_status_dictionary_localization.sql');
  const dictionaryNames = [
    ['aiadc_expert_status', '专家状态'],
    ['aiadc_expert_initial_status', '专家申请初始状态'],
    ['aiadc_expert_approval_status', '专家审批状态'],
  ];

  for (const source of [baseline, manualUpgrade]) {
    for (const [dictionaryCode, dictionaryName] of dictionaryNames) {
      assert.match(source, new RegExp(`'${dictionaryCode}', '${dictionaryName}'`));
    }
  }

  assert.match(manualUpgrade, /SET NAMES utf8mb4;/);
  assert.match(manualUpgrade, /`dict_name`\s*=\s*VALUES\(`dict_name`\)/);
  assert.match(migration, /SET NAMES utf8mb4;/);
  assert.match(migration, /REPEAT\('\?',\s*CHAR_LENGTH\(`dict_name`\)\)/);
  assert.match(migration, /REPEAT\('\?',\s*CHAR_LENGTH\(item\.`item_label`\)\)/);
  assert.match(migration, /'PENDING'.*'待处理'/s);
  assert.match(migration, /'REJECTED'.*'已拒绝'/s);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(migration, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
});

test('certificate template migration preserves runtime canvas placeholders', () => {
  const migration = read('deploy/migrations/V202607300003__harden_certificate_generation_and_template_defaults.sql');
  const migrationConfig = read('deploy/migrations/V202607300003__harden_certificate_generation_and_template_defaults.sql.conf');

  assert.match(migration, /\$\{recipientName\}/);
  assert.match(migration, /\$\{awardName\}/);
  assert.match(migration, /\$\{competitionTitle\}/);
  assert.match(migration, /\$\{issueDate\}/);
  assert.match(migrationConfig, /^placeholderReplacement=false\s*$/);
});

test('seed user UIDs stay random and exact in fresh and existing databases', () => {
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const repairMigration = read('deploy/migrations/V202607260001__repair_seed_user_numeric_uids.sql');
  const randomizeMigration = read('deploy/migrations/V202607260002__randomize_fixed_seed_user_uids.sql');

  assert.match(bootstrap, /VALUES \(\s*1001,\s*CONCAT\([\s\S]*?RANDOM_BYTES\(1\)[\s\S]*?'admin'/);
  assert.match(bootstrap, /VALUES \(\s*1002,\s*CONCAT\([\s\S]*?RANDOM_BYTES\(1\)[\s\S]*?'user'/);
  assert.ok((bootstrap.match(/RANDOM_BYTES\(/g) || []).length >= 6);
  assert.match(bootstrap, /REGEXP '\^\[1-9\]\[0-9\]\{17\}\$'/);
  assert.doesNotMatch(bootstrap, /100000000000000000\s*\+\s*RAND\(\)/);
  assert.doesNotMatch(bootstrap, /CAST\s*\(\s*FLOOR\s*\([^)]*RAND\(\)/i);
  assert.doesNotMatch(bootstrap, /VALUES \(\s*100[12],\s*'90000000000000100[12]'/);

  assert.match(repairMigration, /old_admin_uid VARCHAR\(36\)/);
  assert.match(repairMigration, /old_user_uid VARCHAR\(36\)/);
  assert.match(repairMigration, /'900000000000001001'/);
  assert.match(repairMigration, /'900000000000001002'/);
  assert.match(repairMigration, /START TRANSACTION;/);
  assert.match(repairMigration, /COMMIT;/);
  assert.match(repairMigration, /ROLLBACK;/);
  assert.match(repairMigration, /information_schema`\.`columns/);
  assert.match(repairMigration, /PREPARE repair_uid_statement/);

  assert.match(randomizeMigration, /RANDOM_BYTES\(1\)/);
  assert.match(randomizeMigration, /RANDOM_BYTES\(4\)/);
  assert.match(randomizeMigration, /REPEAT[\s\S]*UNTIL NOT EXISTS/);
  assert.match(randomizeMigration, /new_user_uid AS BINARY\) <> CAST\(new_admin_uid AS BINARY\)/);
  assert.match(randomizeMigration, /START TRANSACTION;/);
  assert.match(randomizeMigration, /COMMIT;/);
  assert.match(randomizeMigration, /ROLLBACK;/);
  assert.match(randomizeMigration, /PREPARE randomize_uid_statement/);
  assert.match(randomizeMigration, /CASE CAST\(`/);
  assert.match(randomizeMigration, /IN \(CAST\(\? AS BINARY\), CAST\(\? AS BINARY\)\)/);
  assert.doesNotMatch(randomizeMigration, /\bDELETE\s+FROM\b/i);
});

test('fresh bootstrap does not execute archived duplicate column migrations', () => {
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const executableSql = bootstrap.replace(/\/\*[\s\S]*?\*\//g, '');
  assert.doesNotMatch(
    executableSql,
    /ALTER TABLE `sys_config`\s+ADD COLUMN `created_by_uuid`/,
  );
  assert.doesNotMatch(executableSql, /^\+$/m);
});

test('migrator honors the release target version', () => {
  const entrypoint = read('deploy/docker/migrator-entrypoint.sh');
  assert.match(entrypoint, /DATABASE_TARGET_VERSION/);
  assert.match(entrypoint, /-target="\$DATABASE_TARGET_VERSION"/);
});

test('fresh databases baseline at the complete saas snapshot version', () => {
  const entrypoint = read('deploy/docker/migrator-entrypoint.sh');
  const dockerfile = read('deploy/docker/migrator.Dockerfile');
  const baselineVersion = read('lumira-backend/sql/saas-baseline-version.txt').trim();
  const latestMigrationVersion = readdirSync(path.join(repoRoot, 'deploy', 'migrations'))
    .map((name) => /^V(\d+)__.+\.sql$/u.exec(name)?.[1])
    .filter(Boolean)
    .sort()
    .at(-1);

  assert.match(baselineVersion, /^\d+$/u);
  assert.equal(baselineVersion, latestMigrationVersion);
  assert.match(
    dockerfile,
    /COPY lumira-backend\/sql\/saas-baseline-version\.txt \/opt\/lumira\/saas-baseline-version\.txt/,
  );
  assert.match(entrypoint, /baseline_version_file=\/opt\/lumira\/saas-baseline-version\.txt/);
  assert.match(entrypoint, /database_baseline_version=\$\(tr -d .* < "\$baseline_version_file"\)/);
  assert.match(entrypoint, /-baselineVersion="\$database_baseline_version"/);
  assert.doesNotMatch(entrypoint, /-baselineVersion=202607140000/);
});

test('retired expert query navigation is removed from fresh and upgraded databases', () => {
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202608130003__retire_expert_query_navigation.sql');

  assert.doesNotMatch(bootstrap, /'expert\.query',\s*'专家查询'/);
  assert.match(migration, /`menu_code`\s*=\s*'expert\.query'/);
  assert.match(migration, /`message_key`\s*=\s*'nav\.experts\.query'/);
  assert.match(migration, /`deleted`\s*=\s*1/);
});

test('expert navigation is consolidated under the expert review catalog', () => {
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202608130004__consolidate_expert_navigation.sql');

  assert.match(bootstrap, /\(-1060,\s*0,\s*'expert\.root'.*'DISABLED',\s*0,\s*0,\s*1\)/);
  assert.match(bootstrap, /\(-1061,\s*-1068,\s*'expert\.management'/);
  assert.match(bootstrap, /\(-1078,\s*-1068,\s*'expert\.review\.tasks'.*?,\s*2,\s*'review:task:view'/);
  assert.match(bootstrap, /\(-1077,\s*-1068,\s*'expert\.application'.*?,\s*3,\s*NULL/);
  assert.match(migration, /JOIN `sys_menu` AS review_root/);
  assert.match(migration, /`menu_code` = 'expert\.root'/);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
});

test('regular deployments run migrations before application containers', () => {
  const deploy = read('bin/deploy-container.mjs');
  const migrationCall = deploy.indexOf('await runDatabaseMigrations();');
  const applicationStart = deploy.indexOf("if (serviceNames.length > 0) {");
  assert.ok(migrationCall > 0, 'regular deployment must invoke database migrations');
  assert.ok(applicationStart > migrationCall, 'database migrations must finish before application containers start');
});

test('certificate closure migration remains immutable at the production checksum', () => {
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202607300004__add_competition_award_certificate_closure.sql');

  assert.equal(flywayChecksum(migration), 2272974);

  for (const indexName of [
    'idx_certificate_record_registration',
    'idx_certificate_record_user',
    'idx_certificate_record_team',
  ]) {
    assert.ok(!bootstrap.includes('KEY `' + indexName + '`'));
    assert.match(migration, new RegExp(`index_name = '${indexName}'`));
  }
  assert.match(migration, /information_schema\.statistics/);
  assert.match(migration, /PREPARE certificate_registration_index_statement/);
  assert.match(migration, /PREPARE certificate_user_index_statement/);
  assert.match(migration, /PREPARE certificate_team_index_statement/);
});

test('built-in navigation hierarchy has unique seed identities and an online repair', () => {
  const baseline = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202608030002__repair_builtin_navigation_hierarchy.sql');
  const dynamicParentRepair = read('deploy/migrations/V202608030003__repair_dynamic_registration_parent.sql');
  const navigationAuthorityRepair = read('deploy/migrations/V202608030005__repair_navigation_authority.sql');
  const certificateDataManagementMigration = read('deploy/migrations/V202608150004__move_certificate_management_into_data_management.sql');
  const awardSettingsMigration = read('deploy/migrations/V202608160001__add_competition_award_settings.sql');
  const menuInsertStart = baseline.indexOf('INSERT INTO `sys_menu`');
  const menuInsertEnd = baseline.indexOf('ON DUPLICATE KEY UPDATE', menuInsertStart);
  const menuInsert = baseline.slice(menuInsertStart, menuInsertEnd);
  const menuRows = Array.from(menuInsert.matchAll(
    /\((-?\d+),\s*(-?\d+),\s*'([^']+)',\s*'[^']*',\s*'([^']+)',\s*(?:NULL|'([^']*)')/g,
  )).map((match) => ({
    id: match[1],
    parentId: match[2],
    menuCode: match[3],
    menuType: match[4],
    path: match[5] || null,
  }));

  assert.ok(menuRows.length > 80, 'the sys_menu bootstrap block must be parsed');
  assert.equal(new Set(menuRows.map((row) => row.id)).size, menuRows.length, 'menu ids must be unique');
  assert.equal(new Set(menuRows.map((row) => row.menuCode)).size, menuRows.length, 'menu codes must be unique');

  const byCode = new Map(menuRows.map((row) => [row.menuCode, row]));
  assert.equal(byCode.get('competition.review-results')?.id, '-1113');
  assert.equal(byCode.get('competition.review-results')?.parentId, '-1069');
  assert.equal(byCode.get('certificate.mine')?.id, '-1114');
  assert.equal(byCode.get('certificate.mine')?.parentId, '-1069');
  for (const certificateMenuCode of ['certificate.templates', 'certificate.records']) {
    assert.equal(byCode.get(certificateMenuCode)?.parentId, '-1100');
  }
  assert.equal(byCode.has('certificate.generate'), false);
  assert.equal(byCode.get('expert.application')?.parentId, '-1068');

  for (const marker of [
    'competition.review-results',
    'certificate.mine',
    'expert.application',
    'competition.root',
    'certificate.root',
    'certificate.templates',
    "'platform', 'menu-tree'",
  ]) {
    assert.match(migration, new RegExp(marker.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(migration, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);

  assert.match(dynamicParentRepair, /registration_root\.`menu_code` = 'registration\.root'/);
  assert.match(dynamicParentRepair, /child_menu\.`parent_id` = registration_root\.`id`/);
  assert.match(dynamicParentRepair, /'competition\.review-results'/);
  assert.match(dynamicParentRepair, /'certificate\.mine'/);
  assert.match(dynamicParentRepair, /migration:V202608030003:dynamic-registration-parent/);
  assert.doesNotMatch(dynamicParentRepair, /child_menu\.`parent_id`\s*=\s*-1069/);
  assert.doesNotMatch(dynamicParentRepair, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(dynamicParentRepair, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);

  for (const parentCode of ['registration.root', 'expert.review.root', 'certificate.root']) {
    assert.match(navigationAuthorityRepair, new RegExp(parentCode.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }
  assert.match(navigationAuthorityRepair, /custom_child\.`parent_id` = competition_root\.`id`/);
  assert.match(navigationAuthorityRepair, /custom_child\.`status` = 'ENABLED'/);
  assert.match(navigationAuthorityRepair, /migration:V202608030005:navigation-authority/);
  assert.doesNotMatch(navigationAuthorityRepair, /child_menu\.`status`\s*=/);
  assert.doesNotMatch(navigationAuthorityRepair, /child_menu\.`deleted`\s*=/);
  assert.doesNotMatch(navigationAuthorityRepair, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(navigationAuthorityRepair, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);

  assert.match(certificateDataManagementMigration, /JOIN `sys_menu` AS data_root/);
  assert.match(certificateDataManagementMigration, /certificate\.templates/);
  assert.match(certificateDataManagementMigration, /certificate\.generate/);
  assert.match(certificateDataManagementMigration, /certificate\.records/);
  assert.match(certificateDataManagementMigration, /certificate\.root/);
  assert.match(certificateDataManagementMigration, /migration:V202608150004:certificate-data-management/);
  assert.doesNotMatch(certificateDataManagementMigration, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(certificateDataManagementMigration, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
  assert.match(awardSettingsMigration, /AWARD_SETTINGS/);
  assert.match(awardSettingsMigration, /certificate\.generate/);
  assert.match(awardSettingsMigration, /一等奖/);
  assert.match(awardSettingsMigration, /优秀奖/);
  assert.doesNotMatch(awardSettingsMigration, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(awardSettingsMigration, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
});

test('platform event outbox audit identity repair matches the fresh bootstrap', () => {
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202607310001__repair_platform_event_outbox_audit_identity.sql');

  assert.equal(flywayChecksum(migration), -827839598);

  assert.match(bootstrap, /CREATE TABLE `platform_event_outbox`[\s\S]*?`created_by_uuid` char\(36\) DEFAULT NULL/);
  assert.match(bootstrap, /CREATE TABLE `platform_event_outbox`[\s\S]*?`updated_by_uuid` char\(36\) DEFAULT NULL/);
  assert.match(bootstrap, /KEY `idx_platform_event_outbox_creator_uuid` \(`created_by`,`created_by_uuid`,`created_at`\)/);
  assert.match(migration, /information_schema\.columns/);
  assert.match(migration, /information_schema\.statistics/);
  assert.match(migration, /ADD COLUMN `created_by_uuid` char\(36\) DEFAULT NULL AFTER `created_by`/);
  assert.match(migration, /ADD COLUMN `updated_by_uuid` char\(36\) DEFAULT NULL AFTER `updated_by`/);
  assert.match(migration, /ADD INDEX `idx_platform_event_outbox_creator_uuid` \(`created_by`,`created_by_uuid`,`created_at`\)/);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(migration, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
});

test('platform event outbox audit identity repair matches the fresh bootstrap', () => {
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202607310001__repair_platform_event_outbox_audit_identity.sql');

  assert.match(bootstrap, /CREATE TABLE `platform_event_outbox`[\s\S]*?`created_by_uuid` char\(36\) DEFAULT NULL/);
  assert.match(bootstrap, /CREATE TABLE `platform_event_outbox`[\s\S]*?`updated_by_uuid` char\(36\) DEFAULT NULL/);
  assert.match(bootstrap, /KEY `idx_platform_event_outbox_creator_uuid` \(`created_by`,`created_by_uuid`,`created_at`\)/);
  assert.match(migration, /information_schema\.columns/);
  assert.match(migration, /information_schema\.statistics/);
  assert.match(migration, /ADD COLUMN `created_by_uuid` char\(36\) DEFAULT NULL AFTER `created_by`/);
  assert.match(migration, /ADD COLUMN `updated_by_uuid` char\(36\) DEFAULT NULL AFTER `updated_by`/);
  assert.match(migration, /ADD INDEX `idx_platform_event_outbox_creator_uuid` \(`created_by`,`created_by_uuid`,`created_at`\)/);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(migration, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
});

test('built-in administrator bootstrap is secret-driven and migration-backed', () => {
  const baseline = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202607300001__secure_builtin_admin_bootstrap.sql');
  const entrypoint = read('deploy/docker/migrator-entrypoint.sh');
  // Reject any complete bcrypt hash literal so tests cannot preserve a reusable credential artifact.
  const bcryptHashPattern = /\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}/;

  assert.match(baseline, /`password_change_required` tinyint NOT NULL DEFAULT '0'/);
  assert.match(baseline, /CREATE TABLE `platform_bootstrap_credential`/);
  assert.doesNotMatch(baseline, bcryptHashPattern);
  assert.match(
    baseline,
    /'admin', 'Administrator', 'Administrator', '', 'DISABLED'/,
    'fresh databases must leave the administrator disabled until the secret bootstrap runs',
  );
  assert.match(
    baseline,
    /'user', 'Common User', 'Common User', '', 'DISABLED'/,
    'the repository-provided ordinary account must not be a production credential',
  );

  assert.match(migration, /CREATE TABLE IF NOT EXISTS `platform_bootstrap_credential`/);
  assert.match(migration, /'EXISTING_CREDENTIAL'/);
  assert.match(migration, /WHERE `user_id` = 1002[\s\S]*?`status` = 'DISABLED'/);
  assert.match(migration, /CONCAT\(\s*'\$2a\$',\s*'10\$',\s*'VBwFJkc\./);
  assert.doesNotMatch(migration, bcryptHashPattern);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);

  const flywayCall = entrypoint.indexOf('flyway');
  const bootstrapCall = entrypoint.indexOf('exec java -jar /opt/lumira/lumira-bootstrap-admin.jar');
  assert.ok(flywayCall >= 0);
  assert.ok(bootstrapCall > flywayCall, 'credential bootstrap must run only after schema migration succeeds');
});

test('payment transaction numbers and legacy competition order ownership remain readable', () => {
  const baseline = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202608190001__correct_payment_provider_transaction_number.sql');
  const manualUpgrade = read('lumira-backend/sql/upgrade-payment-provider-transaction-number-v1.sql');

  assert.match(
    baseline,
    /CREATE TABLE `payment_order`[\s\S]*?`provider_order_no` varchar\(128\) NOT NULL/,
  );
  for (const source of [migration, manualUpgrade]) {
    assert.doesNotMatch(source, /\bMODIFY\s+(?:COLUMN\s+)?`?provider_order_no`?/i);
    assert.match(source, /SET `provider_order_no` = ''/);
    assert.match(source, /LEFT\([\s\S]*?CONCAT\(`provider_code`, '-', `order_no`, '-'\)/);
    assert.match(source, /RIGHT\(`provider_order_no`, 12\) REGEXP '\^\[0-9a-f\]\{12\}\$'/);
    assert.match(source, /JOIN `competition_registration` AS `registration`/);
    assert.match(source, /`registration`\.`owner_user_id` = `payment`\.`created_by`/);
    assert.match(source, /SET `payment`\.`created_by_uuid` = `registration`\.`owner_user_uuid`/);
    assert.match(source, /WHERE `payment`\.`created_by_uuid` IS NULL/);
    assert.doesNotMatch(source, /`request_json`|`response_json`/);
    assert.doesNotMatch(source, /\bDELETE\s+FROM\b/i);
    assert.doesNotMatch(source, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
  }
});

test('participant role settings migrate existing competitions without losing legacy team limits', () => {
  const baseline = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202608190002__add_competition_participant_role_settings.sql');
  const manualUpgrade = read('lumira-backend/sql/upgrade-competition-participant-role-settings-v1.sql');

  for (const source of [baseline, migration, manualUpgrade]) {
    assert.match(source, /'TEACHER_FIELD','memberName','指导老师姓名'/);
    assert.match(source, /studentMinMembers/);
    assert.match(source, /studentMaxMembers/);
    assert.match(source, /teacherMinMembers/);
    assert.match(source, /teacherMaxMembers/);
  }
  for (const source of [migration, manualUpgrade]) {
    assert.match(source, /JSON_EXTRACT\(`content_json`, '\$\.teamMinMembers'\)/);
    assert.match(source, /JSON_EXTRACT\(`content_json`, '\$\.teamMaxMembers'\)/);
    assert.match(source, /NOT EXISTS[\s\S]*?'TEACHER_FIELD'[\s\S]*?'memberName'/);
    assert.doesNotMatch(source, /\bDELETE\s+FROM\b/i);
    assert.doesNotMatch(source, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
  }
});
