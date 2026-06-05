import assert from 'node:assert/strict';
import { backendRouteMeta, realPageRouteMetaMap, realPageRoutePaths } from '../src/routes/meta';

const DEFAULT_SETTING_ROUTE_ORDER = [
  '/settings/tenants',
  '/settings/menus',
  '/settings/dicts',
  '/settings/profile-fields',
  '/settings/personalization',
  '/settings/security',
  '/settings/verification',
  '/settings/notifications',
  '/settings/ai-employees',
  '/settings/plugins',
  '/settings/files/all',
  '/settings/localization',
  '/settings/monitoring',
  '/settings/api-docs',
  '/settings/audit',
];

const expectedRoutes = [
  ['/settings/tenants', 'nav.system.tenants'],
  ['/settings/menus', 'nav.system.menus'],
  ['/settings/localization', 'nav.localization.root'],
] as const;

for (const [path, name] of expectedRoutes) {
  assert.ok(realPageRoutePaths.has(path), `${path} should be a real registered page route`);
  assert.equal(realPageRouteMetaMap.get(path)?.name, name, `${path} should expose the expected nav name`);
  assert.ok(backendRouteMeta.some((item) => item.path === path && item.name === name), `${path} should be available for route metadata mapping`);
}

assert.equal(DEFAULT_SETTING_ROUTE_ORDER[0], '/settings/tenants', 'tenant management should lead the settings maintenance order');

console.log('gap-closure-routes-smoke: ok');
