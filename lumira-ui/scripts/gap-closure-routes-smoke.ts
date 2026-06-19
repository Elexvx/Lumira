import assert from 'node:assert/strict';
import { backendRouteMeta, realPageRouteMetaMap, realPageRoutePaths } from '../src/routes/meta';
import { resolveRouteAccessStatus } from '../src/auth/loginRedirect';
import type { CurrentUser } from '../src/types/api';

const expectedRoutes = [
  ['/settings/menus', 'nav.system.menus'],
  ['/settings/localization', 'nav.localization.root'],
] as const;

for (const [path, name] of expectedRoutes) {
  assert.ok(realPageRoutePaths.has(path), `${path} should be a real registered page route`);
  assert.equal(realPageRouteMetaMap.get(path)?.name, name, `${path} should expose the expected nav name`);
  assert.ok(backendRouteMeta.some((item) => item.path === path && item.name === name), `${path} should be available for route metadata mapping`);
}

const userWithoutManagementPermissions: CurrentUser = {
  userId: 12,
  username: 'route-shell-only',
  sessionId: 'session-route-shell-only',
  permissions: [],
};

assert.equal(resolveRouteAccessStatus('/settings', userWithoutManagementPermissions), 'allowed', 'settings shell should resolve its landing route before page permissions run');
assert.equal(resolveRouteAccessStatus('/user-center', userWithoutManagementPermissions), 'allowed', 'user center shell should resolve its landing route before page permissions run');
assert.equal(
  resolveRouteAccessStatus('/settings/menus', userWithoutManagementPermissions),
  'denied',
  'protected settings pages should still reject users without matching permission',
);
assert.equal(
  resolveRouteAccessStatus('/user-center/users', userWithoutManagementPermissions),
  'denied',
  'protected user center pages should still reject users without matching permission',
);

console.log('gap-closure-routes-smoke: ok');
