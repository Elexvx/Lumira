import { describe, expect, it } from 'vitest';
import { ErrorCode } from '@/enums/errorCode';
import type { AuthRequestSnapshot, UnauthorizedRuntimeState } from '@/auth/unauthorizedDecision';
import { shouldRefreshAndRetryUnauthorized } from './requestInternalsAuth';

const authSnapshot = (overrides: Partial<AuthRequestSnapshot> = {}): AuthRequestSnapshot => ({
  skipAuth: false,
  accessToken: 'token-before-role-switch',
  hasAuthToken: true,
  authSessionEpoch: 4,
  tokenGeneration: 7,
  ...overrides,
});

const runtimeState = (overrides: Partial<UnauthorizedRuntimeState> = {}): UnauthorizedRuntimeState => ({
  pathname: '/dashboard/home',
  currentAccessToken: 'token-before-role-switch',
  currentAuthSessionEpoch: 4,
  currentTokenGeneration: 7,
  loginInProgress: false,
  bootstrapInProgress: false,
  roleSwitchInProgress: false,
  ...overrides,
});

describe('shouldRefreshAndRetryUnauthorized', () => {
  it('does not refresh an old request after a role switch rotated the auth session', () => {
    expect(
      shouldRefreshAndRetryUnauthorized(
        '/v1/business/old-role-resource',
        {},
        401,
        ErrorCode.UNAUTHORIZED,
        false,
        authSnapshot(),
        runtimeState({
          currentAccessToken: 'token-after-role-switch',
          currentAuthSessionEpoch: 5,
          currentTokenGeneration: 8,
        }),
      ),
    ).toBe(false);
  });

  it('still refreshes an active-session request that receives 401', () => {
    expect(
      shouldRefreshAndRetryUnauthorized(
        '/v1/business/current-role-resource',
        {},
        401,
        ErrorCode.UNAUTHORIZED,
        false,
        authSnapshot(),
        runtimeState(),
      ),
    ).toBe(true);
  });

  it('does not refresh a role-transition request that explicitly handles unauthorized', () => {
    expect(
      shouldRefreshAndRetryUnauthorized(
        '/v1/auth/simulated-role',
        {
          allowUnauthorizedWithoutRedirect: true,
          preserveAuthSessionOnUnauthorized: true,
        },
        401,
        ErrorCode.SESSION_EXPIRED,
        false,
        authSnapshot(),
        runtimeState(),
      ),
    ).toBe(false);
  });

  it('does not refresh an old request while a role switch is in progress', () => {
    expect(
      shouldRefreshAndRetryUnauthorized(
        '/v1/business/old-role-resource',
        {},
        401,
        ErrorCode.SESSION_EXPIRED,
        false,
        authSnapshot(),
        runtimeState({ roleSwitchInProgress: true }),
      ),
    ).toBe(false);
  });
});
