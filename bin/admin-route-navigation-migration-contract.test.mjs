import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

test('administrator route navigation is complete for fresh and upgraded databases', () => {
  const baseline = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202608050002__repair_admin_route_navigation.sql');

  for (const source of [baseline, migration]) {
    assert.match(source, /'competition\.registration'[\s\S]*?'aiadc:registration:view'/);
    assert.match(source, /'activity\.registration'[\s\S]*?'aiadc:activity:create'/);
    assert.match(source, /'workflow\.root'[\s\S]*?'\/workflows'/);
    assert.match(source, /'workflow\.tasks'[\s\S]*?'workflow:approve'/);
    assert.match(source, /'workflow\.config'[\s\S]*?'workflow:config'/);
  }

  assert.match(migration, /LOWER\(administrator_role\.`role_code`\) = 'admin'/);
  assert.match(migration, /permission_row\.`deleted` = 0/);
  assert.match(migration, /'platform', 'menu-tree'/);
  assert.match(migration, /'IAM', 'permission-snapshot'/);
  assert.match(migration, /migration:V202608050002:admin-route-navigation/);
  assert.doesNotMatch(migration, /\bDELETE\s+FROM\b/i);
  assert.doesNotMatch(migration, /\bDROP\s+(?:TABLE|COLUMN|INDEX)\b/i);
});
