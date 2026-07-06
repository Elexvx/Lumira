import assert from 'node:assert/strict';
import { DEFAULT_HOME_PATH } from '../src/app.constants';
import { resolveAuthorizedLoginRedirectTarget, resolveLoginPageRuntimeRedirectTarget, resolveLoginRedirectTarget, resolveRouteAccessStatus } from '../src/auth/loginRedirect';
import { resolveCanonicalRoutePath } from '../src/routes/meta';
import type { CurrentUser } from '../src/types/api';

const trustedUser = (overrides: Partial<CurrentUser> = {}): CurrentUser => ({
  userId: 10,
  userUuid: 'user-uuid-10',
  username: 'ordinary',
  sessionId: 'session-ordinary',
  sessionVersion: 1,
  permissionsVersion: 'permissions-1',
  permissions: [],
  availableRoles: [{ id: 1, roleCode: 'operator', roleName: 'Operator', roleType: 'FUNCTIONAL' }],
  ...overrides,
});

const run = () => {
  assert.equal(
    resolveLoginRedirectTarget('?redirect=%2Fuser-center%2Fusers'),
    '/user-center/users',
    'login redirect should decode the target route',
  );

  assert.equal(
    resolveLoginRedirectTarget('?redirect=%2Fuser%2Flogin'),
    DEFAULT_HOME_PATH,
    'redirecting back to the login page should fall back to the default home route',
  );

  assert.equal(
    resolveLoginRedirectTarget(''),
    DEFAULT_HOME_PATH,
    'missing redirect should fall back to the default home route',
  );

  assert.equal(
    resolveLoginPageRuntimeRedirectTarget({
      pathname: '/user/login',
      search: '?redirect=%2Fcompetitions%2Fregister',
      isAuthenticated: false,
    }),
    DEFAULT_HOME_PATH,
    'unauthenticated login-page runtime should defer redirect consumption until after login succeeds',
  );

  assert.equal(
    resolveLoginPageRuntimeRedirectTarget({
      pathname: '/user/login',
      search: '?redirect=%2Fcompetitions%2Fregister',
      isAuthenticated: true,
    }),
    '/competitions/register',
    'authenticated login-page runtime should preserve the redirect target',
  );

  const ordinaryUser = trustedUser();

  assert.equal(
    resolveRouteAccessStatus('/settings/localization', ordinaryUser),
    'denied',
    'known routes without matching permission should be treated as denied',
  );

  assert.equal(
    resolveRouteAccessStatus('/not-a-real-route', ordinaryUser),
    'unknown',
    'unknown routes should remain available for the 404 route',
  );

  const dashboardUser = trustedUser({
    userId: 11,
    userUuid: 'user-uuid-11',
    username: 'dashboard',
    sessionId: 'session-dashboard',
    permissions: ['dashboard:view'],
    defaultHomePath: '/dashboard/home',
  });

  assert.equal(
    resolveAuthorizedLoginRedirectTarget('?redirect=%2Fsettings%2Fprofile-fields', dashboardUser, []),
    '/dashboard/home',
    'denied redirect should fall back to the role default home when it is accessible',
  );

  const settingsUser = trustedUser({
    userId: 13,
    userUuid: 'user-uuid-13',
    username: 'settings-operator',
    sessionId: 'session-settings-operator',
    permissions: ['system:config:view'],
    defaultHomePath: '/settings/security',
  });

  assert.equal(
    resolveRouteAccessStatus('/settings/security', settingsUser),
    'allowed',
    'settings routes should stay accessible for non-admin users with matching system permissions',
  );

  assert.equal(
    resolveAuthorizedLoginRedirectTarget('?redirect=%2Fsettings%2Fsecurity', settingsUser, []),
    '/settings/security',
    'authorized redirects should preserve settings targets for users with matching system permissions',
  );

  assert.equal(
    resolveAuthorizedLoginRedirectTarget('', { ...dashboardUser, defaultHomePath: '/dashboard' }, []),
    '/dashboard/home',
    'legacy dashboard shortcut path should normalize to canonical dashboard home',
  );

  assert.equal(
    resolveAuthorizedLoginRedirectTarget('', { ...dashboardUser, defaultHomePath: '/dashboard/' }, []),
    '/dashboard/home',
    'trailing slash dashboard shortcut path should normalize to canonical dashboard home',
  );

  const registrationUser = trustedUser({
    userId: 12,
    userUuid: 'user-uuid-12',
    username: 'registration',
    sessionId: 'session-registration',
    permissions: ['aiadc:registration:view'],
    availableRoles: [{ id: 1002, roleCode: 'commonuser', roleName: 'Common User', roleType: 'FUNCTIONAL' }],
    defaultHomePath: '/competitions/register',
  });

  assert.equal(
    resolveAuthorizedLoginRedirectTarget(
      '?redirect=%2Fsettings%2Fmenus',
      registrationUser,
      [{ id: 12, menuCode: 'competition.registration', name: 'Competition registration', path: '/competitions/register' }],
    ),
    '/competitions/register',
    'registration role should ignore inaccessible admin redirects and land on its configured page',
  );

  const menuRoleUser = trustedUser({
    userId: 14,
    userUuid: 'user-uuid-14',
    username: 'menu-role',
    sessionId: 'session-menu-role',
    permissions: ['system:menu:view'],
    defaultHomePath: '/settings/menus',
  });
  const dictRoleUser = trustedUser({
    ...menuRoleUser,
    username: 'dict-role',
    permissions: ['system:dict:view'],
    permissionsVersion: 'permissions-2',
    defaultHomePath: '/settings/dicts',
  });

  assert.equal(
    resolveAuthorizedLoginRedirectTarget(
      '?redirect=%2Fsettings%2Fdicts',
      menuRoleUser,
      [{ id: 14, menuCode: 'settings.menus', name: 'Menus', path: '/settings/menus' }],
    ),
    '/settings/menus',
    'menu role should keep only menu management visible when dict access is absent',
  );

  assert.equal(
    resolveAuthorizedLoginRedirectTarget(
      '?redirect=%2Fsettings%2Fmenus',
      dictRoleUser,
      [{ id: 15, menuCode: 'settings.dicts', name: 'Dicts', path: '/settings/dicts' }],
    ),
    '/settings/dicts',
    'role page adjustment should switch the default landing page to the newly visible page',
  );

  assert.equal(
    resolveAuthorizedLoginRedirectTarget(
      '?redirect=%2Fdashboard%2F%2F',
      dashboardUser,
      [],
    ),
    '/dashboard/home',
    'redirect to dashed dashboard path should normalize to canonical dashboard home',
  );

  assert.equal(
    resolveCanonicalRoutePath('/dashboard//'),
    '/dashboard/home',
    'dashboard alias with extra trailing slashes should normalize to canonical dashboard home',
  );

  console.log('login-redirect-target-smoke: ok');
};

run();
