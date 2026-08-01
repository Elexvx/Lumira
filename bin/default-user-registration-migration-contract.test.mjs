import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

import { resolveRepoRoot } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const migrationPath = 'deploy/migrations/V202608010002__restore_default_user_competition_registration.sql';
const migration = readFileSync(path.join(repoRoot, migrationPath), 'utf8');

test('default registered users retain the competition-registration entry and permissions', () => {
  for (const permission of [
    'aiadc:registration:view',
    'aiadc:registration:create',
    'aiadc:registration:update',
    'aiadc:registration:pay',
  ]) {
    assert.match(migration, new RegExp(permission));
  }

  assert.match(migration, /auth\.default-registration-role-code/);
  assert.match(migration, /INSERT INTO `sys_role_permission`/);
  assert.match(migration, /'competition\.registration'/);
  assert.match(migration, /'\/competitions\/register'/);
  assert.match(migration, /'IAM', 'permission-snapshot'/);
  assert.match(migration, /migration:V202608010002:default-user-registration/);
  assert.match(migration, /ON DUPLICATE KEY UPDATE/g);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(migration, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
});
