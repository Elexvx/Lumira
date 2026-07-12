import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorCode } from '@/enums/errorCode';
import { ApiRequestError } from '@/services/common/requestInternalsTypes';
import { requestPaymentApi } from './paymentAuthenticatedRequest';

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  clearAuthSession: vi.fn(),
  historyReplace: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: { replace: mocks.historyReplace },
}));

vi.mock('@/auth/sessionLifecycle', () => ({
  clearAuthSession: mocks.clearAuthSession,
}));

vi.mock('@/services/common/request', () => ({
  request: mocks.request,
}));

describe('requestPaymentApi', () => {
  beforeEach(() => {
    mocks.request.mockReset();
    mocks.clearAuthSession.mockReset();
    mocks.historyReplace.mockReset();
    vi.stubGlobal('window', {
      location: {
        pathname: '/settings/payment',
        search: '?tab=providers',
        hash: '#alipay',
      },
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('clears an expired session and redirects back to the current payment page after login', async () => {
    const error = new ApiRequestError(ErrorCode.UNAUTHORIZED, '未登录', {
      httpStatus: 401,
      userMessage: '请先登录后再继续操作',
    });
    mocks.request.mockRejectedValue(error);

    await expect(requestPaymentApi('/v1/payment/providers', { method: 'GET' })).rejects.toBe(error);

    expect(mocks.request).toHaveBeenCalledWith('/v1/payment/providers', {
      method: 'GET',
      autoRedirectOnUnauthorized: false,
    });
    expect(mocks.clearAuthSession).toHaveBeenCalledTimes(1);
    expect(mocks.historyReplace).toHaveBeenCalledWith(
      '/user/login?redirect=%2Fsettings%2Fpayment%3Ftab%3Dproviders%23alipay',
    );
  });

  it('leaves the session intact for non-authentication save errors', async () => {
    const error = new ApiRequestError(ErrorCode.VALIDATION_ERROR, '参数错误', {
      httpStatus: 400,
    });
    mocks.request.mockRejectedValue(error);

    await expect(requestPaymentApi('/v1/payment/providers/alipay', { method: 'PUT' })).rejects.toBe(error);

    expect(mocks.clearAuthSession).not.toHaveBeenCalled();
    expect(mocks.historyReplace).not.toHaveBeenCalled();
  });
});
