import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');
const read = (file) => readFileSync(path.join(repoRoot, file), 'utf8');

test('one-shot migrator executes the privileged plugin adapter between Flyway and bootstrap', () => {
  const entrypoint = read('deploy/docker/migrator-entrypoint.sh');
  const dockerfile = read('deploy/docker/migrator.Dockerfile');
  const flyway = entrypoint.indexOf('flyway');
  const plugin = entrypoint.lastIndexOf('lumira-plugin-migrator.jar execute');
  const bootstrap = entrypoint.lastIndexOf('lumira-bootstrap-admin.jar');

  assert.ok(flyway >= 0 && plugin > flyway && bootstrap > plugin);
  assert.match(entrypoint, /PLUGIN_MIGRATION_MODE:-platform/);
  assert.match(entrypoint, /plugin-approve[\s\S]*?lumira-plugin-migrator\.jar approve/);
  assert.match(entrypoint, /plugin-execute[\s\S]*?lumira-plugin-migrator\.jar execute/);
  assert.match(dockerfile, /COPY --from=plugin-migrator-builder .*lumira-plugin-migrator\.jar/);
});

test('approval CLI requires full fence and keeps migration credentials out of Docker argv', () => {
  const cli = read('bin/approve-plugin-migration.mjs');

  for (const field of [
    'request-id', 'operation-epoch', 'package-digest', 'migration-digest',
    'confirm-migration-digest', 'release-id', 'approver', 'reason',
  ]) {
    assert.match(cli, new RegExp(`['\"]${field}['\"]`, 'u'));
  }
  assert.match(cli, /DB_MIGRATION_USERNAME/);
  assert.match(cli, /DB_MIGRATION_PASSWORD/);
  assert.match(cli, /names\.flatMap\(\(name\) => \['-e', name\]\)/);
  assert.doesNotMatch(cli, /'-e',\s*`DB_PASSWORD=/);
  assert.match(cli, /@sha256:\[a-f0-9\]\{64\}/);
});

test('central adapter revalidates payload digest, expand phase, namespace and destructive SQL', () => {
  const validator = read('deploy/plugin-migrator/src/main/java/com/lumira/deploy/pluginmigration/PluginMigrationSafetyValidator.java');
  const repository = read('deploy/plugin-migrator/src/main/java/com/lumira/deploy/pluginmigration/PluginMigrationRepository.java');

  assert.match(validator, /only EXPAND plugin migrations are allowed/);
  assert.match(validator, /migration digest does not match payload/);
  assert.match(validator, /DROP\|TRUNCATE\|RENAME/);
  assert.match(validator, /plugin_.*normalized/s);
  assert.match(repository, /request_status = 'APPROVED'/);
  assert.match(repository, /request_status = 'RUNNING'/);
  assert.match(repository, /lease_until/);
  assert.match(repository, /request_status = 'RECOVERING'/);
  assert.match(repository, /NEEDS_MANUAL_REVIEW/);
  assert.match(repository, /sys_plugin_migration_audit/);
});

test('updater passes the central migration recovery lease into the one-shot container', () => {
  const updater = read('bin/lumira-updater.mjs');
  const envExample = read('deploy/.env.example');

  assert.match(updater, /PLUGIN_MIGRATION_LEASE_SECONDS/);
  assert.match(envExample, /^PLUGIN_MIGRATION_LEASE_SECONDS=900$/m);
});
