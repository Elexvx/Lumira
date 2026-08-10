import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const migrationPath = fileURLToPath(new URL(
  '../deploy/migrations/V202608100002__relocate_workflow_configuration_to_settings.sql',
  import.meta.url,
));

test('workflow configuration migration moves the designer into settings', () => {
  const migration = fs.readFileSync(migrationPath, 'utf8');

  assert.match(migration, /workflow\.config/);
  assert.match(migration, /settings\.root/);
  assert.match(migration, /\/settings\/workflows/);
  assert.match(migration, /redirect:\/workflows\/tasks/);
  assert.match(migration, /migration:V202608100002:workflow-settings-navigation/);
});
