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

  it('suppresses 403 feedback during an active login flow', () => {
    mocks.buildUnauthorizedRuntimeState.mockReturnValue({
      ...runtimeAt('/dashboard/home'),
      loginInProgress: true,
    });

    handleApiError(forbiddenError(), {}, baseAuthSnapshot);

    expect(mocks.messageWarning).not.toHaveBeenCalled();
  });
});
