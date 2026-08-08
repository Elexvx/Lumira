import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const migrationPath = fileURLToPath(new URL('../deploy/migrations/V202608080002__repair_route_catalog_navigation.sql', import.meta.url));

test('route catalog navigation repair keeps upgraded databases aligned', () => {
  const migration = fs.readFileSync(migrationPath, 'utf8');

  for (const menuCode of [
    'registration.root',
    'certificate.root',
    'expert.root',
    'expert.review.root',
    'workflow.root',
    'expert.application',
    'competition.registrations',
  ]) {
    assert.match(migration, new RegExp(menuCode.replace('.', '\\.')));
  }
  assert.match(migration, /@\/pages\/DataManagementLandingPage/);
  assert.match(migration, /redirect:\/certificates\/mine/);
  assert.match(migration, /migration:V202608080002:route-catalog-navigation/);
});
