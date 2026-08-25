import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8');

test('fresh and upgraded databases install the same disabled durable alerting plugin', () => {
  const bootstrap = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202608250001__add_builtin_alerting_plugin.sql');
  const baselineVersion = read('lumira-backend/sql/saas-baseline-version.txt').trim();
  const tables = [
    'alert_channel', 'alert_contact_group', 'alert_contact_member', 'alert_rule',
    'alert_instance', 'alert_event', 'alert_silence', 'alert_delivery',
    'alert_delivery_attempt', 'alert_directory_mapping', 'alert_worker_lease',
  ];

  for (const sql of [bootstrap, migration]) {
    assert.match(sql, /'builtin-alerting'/);
    assert.match(sql, /'DISABLED'/);
    assert.match(sql, /`member_type` varchar\(24\) NOT NULL/);
    for (const table of tables) {
      assert.match(sql, new RegExp('CREATE TABLE(?: IF NOT EXISTS)? `' + table + '`'));
    }
  }
  assert.equal(baselineVersion, '202608250001');
});
