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
