import assert from 'node:assert/strict';
import { DEFAULT_HOME_PATH } from '../src/app.constants';
import { resolveLoginRedirectTarget, resolveRouteAccessStatus } from '../src/auth/loginRedirect';
import type { CurrentUser } from '../src/types/api';

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

  const ordinaryUser: CurrentUser = {
    userId: 10,
    username: 'ordinary',
    sessionId: 'session-ordinary',
    permissions: [],
  };

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

  console.log('login-redirect-target-smoke: ok');
};

run();
