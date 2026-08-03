import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

import { resolveRepoRoot } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const migrationPath = 'deploy/migrations/V202608030001__repair_registration_navigation.sql';
const migration = readFileSync(path.join(repoRoot, migrationPath), 'utf8');

test('registration navigation repair restores the full participant menu safely', () => {
  assert.match(migration, /'registration\.root'/);
  assert.match(migration, /'competition\.registration'/);
  assert.match(migration, /'activity\.registration'/);
  assert.match(migration, /'competition\.review-results'/);
  assert.match(migration, /'competition\.management\.delete'/);
  assert.match(migration, /'\/competitions\/register'/);
  assert.match(migration, /auth\.default-registration-role-code/);
  assert.match(migration, /appeal_permission\.`permission_key` = 'review:appeal:submit'/);
  assert.match(migration, /migration:V202608030001:registration-navigation-repair/);
  assert.match(migration, /ON DUPLICATE KEY UPDATE/g);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(migration, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
});

test('fresh bootstrap does not reuse the delete-button id for review results', () => {
  const bootstrap = readFileSync(path.join(repoRoot, 'lumira-backend/sql/saas.sql'), 'utf8');
  assert.match(bootstrap, /\(-1074, -1071, 'competition\.management\.delete'/);
  assert.match(bootstrap, /\(-1113, -1069, 'competition\.review-results'/);
  assert.doesNotMatch(bootstrap, /\(-1074, -1069, 'competition\.review-results'/);
});
