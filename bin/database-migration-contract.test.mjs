import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

import { resolveRepoRoot } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const read = (file) => readFileSync(path.join(repoRoot, file), 'utf8');

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
