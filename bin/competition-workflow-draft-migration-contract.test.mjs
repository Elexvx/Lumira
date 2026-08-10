import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const migrationPath = fileURLToPath(new URL(
  '../deploy/migrations/V202608100003__create_competition_approval_workflow_draft.sql',
  import.meta.url,
));

test('competition workflow migration creates an unpublished approval draft', () => {
  const migration = fs.readFileSync(migrationPath, 'utf8');

  assert.match(migration, /COMPETITION_APPROVAL/);
  assert.match(migration, /赛事审批流程/);
  assert.match(migration, /'DRAFT'/);
  assert.match(migration, /'START'/);
  assert.match(migration, /'APPROVAL'/);
  assert.match(migration, /'END'/);
  assert.match(migration, /approver_role_ids_json/);
  assert.doesNotMatch(migration, /'ACTIVE'/);
});
