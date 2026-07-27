import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorCode } from '@/enums/errorCode';
import { handleApiError } from './requestInternalsApiErrorHandling';
import { ApiRequestError, type RequestOptions } from './requestInternalsTypes';
import type { AuthRequestSnapshot, UnauthorizedRuntimeState } from '@/auth/unauthorizedDecision';

const mocks = vi.hoisted(() => ({
  messageInfo: vi.fn(),
  messageWarning: vi.fn(),
  messageError: vi.fn(),
  buildUnauthorizedRuntimeState: vi.fn(),
  shouldSuppressUnauthorizedSideEffects: vi.fn(),
  performLogout: vi.fn(),
}));

vi.mock('@/theme/antdFeedbackBridge', () => ({
  message: {
    info: mocks.messageInfo,
    warning: mocks.messageWarning,
    error: mocks.messageError,
  },
}));

vi.mock('@/auth/unauthorized', () => ({
  buildUnauthorizedRuntimeState: mocks.buildUnauthorizedRuntimeState,
}));

vi.mock('@/auth/unauthorizedDecision', () => ({
  shouldSuppressUnauthorizedSideEffects: mocks.shouldSuppressUnauthorizedSideEffects,
}));

vi.mock('@/auth/sessionLifecycle', () => ({
  performLogout: mocks.performLogout,
}));

const baseAuthSnapshot: AuthRequestSnapshot = {
  skipAuth: false,
  accessToken: '',
  hasAuthToken: false,
  authSessionEpoch: 1,
  tokenGeneration: 1,
};

const runtimeAt = (pathname: string): UnauthorizedRuntimeState => ({
  pathname,
  currentAccessToken: '',
  currentAuthSessionEpoch: 1,
  currentTokenGeneration: 1,
  loginInProgress: false,
  bootstrapInProgress: false,
});

const forbiddenError = () =>
  new ApiRequestError(ErrorCode.FORBIDDEN, '当前账号没有访问权限', {
    userMessage: '当前账号没有访问权限',
    httpStatus: 403,
  });

const sessionExpiredError = () =>
  new ApiRequestError(ErrorCode.SESSION_EXPIRED, 'session expired', {
    userMessage: 'session expired',
    httpStatus: 401,
  });

const handle = (pathname: string, authSnapshot: AuthRequestSnapshot = baseAuthSnapshot, options: RequestOptions = {}) => {
  mocks.buildUnauthorizedRuntimeState.mockReturnValue(runtimeAt(pathname));
  handleApiError(forbiddenError(), options, authSnapshot);
};

describe('handleApiError', () => {
  beforeEach(() => {
    mocks.messageInfo.mockReset();
    mocks.messageWarning.mockReset();
    mocks.messageError.mockReset();
    mocks.buildUnauthorizedRuntimeState.mockReset();
    mocks.shouldSuppressUnauthorizedSideEffects.mockReset();
    mocks.performLogout.mockReset();
    mocks.shouldSuppressUnauthorizedSideEffects.mockReturnValue(false);
  });

  it('suppresses pre-auth 403 feedback while the login page is rendering', () => {
    handle('/user/login');

    expect(mocks.messageWarning).not.toHaveBeenCalled();
  });

  it('keeps 403 feedback on protected business pages', () => {
    handle('/dashboard/home');

    expect(mocks.messageWarning).toHaveBeenCalledWith('当前账号没有访问权限');
  });

  it('suppresses stale 403 feedback after a role switch rotates the authenticated token', () => {
    mocks.shouldSuppressUnauthorizedSideEffects.mockReturnValue(true);
    mocks.buildUnauthorizedRuntimeState.mockReturnValue({
      ...runtimeAt('/dashboard/home'),
      currentAccessToken: 'token-after-role-switch',
      currentAuthSessionEpoch: 2,
      currentTokenGeneration: 2,
    });

    handleApiError(forbiddenError(), {}, {
      ...baseAuthSnapshot,
      accessToken: 'token-before-role-switch',
      hasAuthToken: true,
    });

    expect(mocks.messageWarning).not.toHaveBeenCalled();
  });

  it('suppresses 403 feedback during an active login flow', () => {
    mocks.buildUnauthorizedRuntimeState.mockReturnValue({
      ...runtimeAt('/dashboard/home'),
      loginInProgress: true,
    });

    handleApiError(forbiddenError(), {}, baseAuthSnapshot);

    expect(mocks.messageWarning).not.toHaveBeenCalled();
  });

  it('forces logout when the active session is expired', async () => {
    const authSnapshot: AuthRequestSnapshot = {
      skipAuth: false,
      accessToken: 'token-a',
      hasAuthToken: true,
      authSessionEpoch: 1,
      tokenGeneration: 1,
    };
    mocks.buildUnauthorizedRuntimeState.mockReturnValue({
      ...runtimeAt('/dashboard/home'),
      currentAccessToken: 'token-a',
    });

    handleApiError(sessionExpiredError(), {}, authSnapshot);

    expect(mocks.messageInfo).toHaveBeenCalledWith('session expired');
    await vi.waitFor(() => {
      expect(mocks.performLogout).toHaveBeenCalledWith({ reason: 'forced_expired' });
    });
  });

  it('does not force logout when unauthorized side effects are suppressed', () => {
    mocks.shouldSuppressUnauthorizedSideEffects.mockReturnValue(true);

    handleApiError(sessionExpiredError(), {}, {
      ...baseAuthSnapshot,
      accessToken: 'stale-token',
      hasAuthToken: true,
    });

    expect(mocks.messageInfo).not.toHaveBeenCalled();
    expect(mocks.performLogout).not.toHaveBeenCalled();
  });

  it('still forces logout for an expired authenticated request that disabled ordinary redirects', async () => {
    mocks.buildUnauthorizedRuntimeState.mockReturnValue({
      ...runtimeAt('/dashboard/home'),
      currentAccessToken: 'token-a',
    });

    handleApiError(sessionExpiredError(), { autoRedirectOnUnauthorized: false }, {
      ...baseAuthSnapshot,
      accessToken: 'token-a',
      hasAuthToken: true,
    });

    expect(mocks.messageInfo).toHaveBeenCalledWith('session expired');
    await vi.waitFor(() => {
      expect(mocks.performLogout).toHaveBeenCalledWith({ reason: 'forced_expired' });
    });
  });

  it('preserves explicit unauthorized opt-out for requests without an authenticated session', () => {
    handleApiError(sessionExpiredError(), { allowUnauthorizedWithoutRedirect: true }, baseAuthSnapshot);

    expect(mocks.messageInfo).not.toHaveBeenCalled();
    expect(mocks.performLogout).not.toHaveBeenCalled();
  });

  it('forces logout for a silent expired request when an authenticated session still exists', async () => {
    mocks.buildUnauthorizedRuntimeState.mockReturnValue({
      ...runtimeAt('/dashboard/home'),
      currentAccessToken: 'token-a',
    });

    handleApiError(sessionExpiredError(), {
      allowUnauthorizedWithoutRedirect: true,
      silent: true,
    }, {
      ...baseAuthSnapshot,
      accessToken: 'token-a',
      hasAuthToken: true,
    });

    expect(mocks.messageInfo).not.toHaveBeenCalled();
    await vi.waitFor(() => {
      expect(mocks.performLogout).toHaveBeenCalledWith({ reason: 'forced_expired' });
    });
  });

  it('does not destroy the platform session when a business service still returns 401 after refresh', () => {
    handleApiError(sessionExpiredError(), {}, {
      ...baseAuthSnapshot,
      accessToken: 'token-after-refresh',
      hasAuthToken: true,
    }, { authenticatedRefreshSucceeded: true });

    expect(mocks.messageWarning).toHaveBeenCalledWith('session expired');
    expect(mocks.performLogout).not.toHaveBeenCalled();
  });

  it('does not destroy the platform session when refresh is temporarily unavailable', () => {
    handleApiError(sessionExpiredError(), {}, {
      ...baseAuthSnapshot,
      accessToken: 'token-a',
      hasAuthToken: true,
    }, { refreshTemporarilyUnavailable: true });

    expect(mocks.messageWarning).toHaveBeenCalledWith('session expired');
    expect(mocks.performLogout).not.toHaveBeenCalled();
  });
});
