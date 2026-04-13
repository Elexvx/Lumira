import assert from 'node:assert/strict';
import {
  beginBootstrapFlow,
  beginLoginFlow,
  bumpAuthSessionEpoch,
  endBootstrapFlow,
  endLoginFlow,
  getAuthSessionEpoch,
  isBootstrapInProgress,
  isLoginInProgress,
} from '../src/auth/loginFlowState';
import {
  shouldSuppressUnauthorizedSideEffects,
  type AuthRequestSnapshot,
  type UnauthorizedRuntimeState,
} from '../src/auth/unauthorizedDecision';

const makeSnapshot = (overrides: Partial<AuthRequestSnapshot> = {}): AuthRequestSnapshot => ({
  skipAuth: false,
  accessToken: 'token-a',
  hasAuthToken: true,
  authSessionEpoch: 1,
  tokenGeneration: 1,
  ...overrides,
});

const makeRuntime = (overrides: Partial<UnauthorizedRuntimeState> = {}): UnauthorizedRuntimeState => ({
  pathname: '/dashboard/home',
  currentAccessToken: 'token-a',
  currentAuthSessionEpoch: 1,
  currentTokenGeneration: 1,
  loginInProgress: false,
  bootstrapInProgress: false,
  ...overrides,
});

const run = () => {
  endLoginFlow();
  const startEpoch = getAuthSessionEpoch();

  beginLoginFlow();
  assert.equal(isLoginInProgress(), true, 'login flow should be marked in progress');
  beginBootstrapFlow();
  assert.equal(isBootstrapInProgress(), true, 'bootstrap flow should be marked in progress');
  assert.equal(
    shouldSuppressUnauthorizedSideEffects(
      makeSnapshot({ authSessionEpoch: startEpoch }),
      makeRuntime({ pathname: '/user/login', loginInProgress: true, bootstrapInProgress: true }),
    ),
    true,
    'login page bootstrap 401 should be suppressed',
  );
  endBootstrapFlow();
  endLoginFlow();
  assert.equal(isLoginInProgress(), false, 'login flow should clear after bootstrap');
  assert.equal(isBootstrapInProgress(), false, 'bootstrap flow should clear after bootstrap');

  bumpAuthSessionEpoch();
  const nextEpoch = getAuthSessionEpoch();
  assert.equal(
    shouldSuppressUnauthorizedSideEffects(
      makeSnapshot({ accessToken: 'old-token', authSessionEpoch: startEpoch, tokenGeneration: 0 }),
      makeRuntime({ currentAccessToken: 'new-token', currentAuthSessionEpoch: nextEpoch, currentTokenGeneration: 2 }),
    ),
    true,
    'old request 401 should not clear a newer token',
  );

  assert.equal(
    shouldSuppressUnauthorizedSideEffects(
      makeSnapshot({ skipAuth: true, hasAuthToken: false, accessToken: '' }),
      makeRuntime({ pathname: '/user/login', loginInProgress: false }),
    ),
    true,
    'skip-auth requests should never trigger global logout side effects',
  );

  assert.equal(
    shouldSuppressUnauthorizedSideEffects(
      makeSnapshot({ authSessionEpoch: nextEpoch, accessToken: 'token-a' }),
      makeRuntime({ pathname: '/dashboard/home', currentAccessToken: 'token-a', currentAuthSessionEpoch: nextEpoch }),
    ),
    false,
    'matched active-session 401 should still surface to the caller',
  );

  console.log('auth-login-flow smoke passed');
};

run();
