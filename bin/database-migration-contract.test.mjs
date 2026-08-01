import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
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

test('regular deployments run migrations before application containers', () => {
  const deploy = read('bin/deploy-container.mjs');
  const migrationCall = deploy.indexOf('await runDatabaseMigrations();');
  const applicationStart = deploy.indexOf("if (serviceNames.length > 0) {");
  assert.ok(migrationCall > 0, 'regular deployment must invoke database migrations');
  assert.ok(applicationStart > migrationCall, 'database migrations must finish before application containers start');
});

test('certificate closure migration remains immutable and owns its certificate indexes', () => {
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202607300004__add_competition_award_certificate_closure.sql');

  assert.equal(flywayChecksum(migration), 638071010);

  for (const indexName of [
    'idx_certificate_record_registration',
    'idx_certificate_record_user',
    'idx_certificate_record_team',
  ]) {
    assert.ok(!bootstrap.includes('KEY `' + indexName + '`'));
    assert.match(migration, new RegExp(`ADD INDEX ${indexName}`));
  }
  assert.doesNotMatch(migration, /information_schema\.statistics/);
  assert.doesNotMatch(migration, /PREPARE certificate_/);
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
  // This revoked public fixture is asserted absent from both production SQL paths.
  const legacyFixedHash = '$2a$10$VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te'; // nosemgrep: generic.secrets.security.detected-bcrypt-hash.detected-bcrypt-hash

  assert.match(baseline, /`password_change_required` tinyint NOT NULL DEFAULT '0'/);
  assert.match(baseline, /CREATE TABLE `platform_bootstrap_credential`/);
  assert.doesNotMatch(baseline, new RegExp(legacyFixedHash.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
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
  assert.doesNotMatch(migration, new RegExp(legacyFixedHash.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);

  const flywayCall = entrypoint.indexOf('flyway');
  const bootstrapCall = entrypoint.indexOf('exec java -jar /opt/lumira/lumira-bootstrap-admin.jar');
  assert.ok(flywayCall >= 0);
  assert.ok(bootstrapCall > flywayCall, 'credential bootstrap must run only after schema migration succeeds');
});
