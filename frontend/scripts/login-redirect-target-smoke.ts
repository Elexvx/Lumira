import assert from 'node:assert/strict';
import { DEFAULT_HOME_PATH } from '../src/app.constants';
import { resolveLoginRedirectTarget } from '../src/auth/loginRedirect';

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

  console.log('login-redirect-target-smoke: ok');
};

run();
