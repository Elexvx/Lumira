import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorCode } from '@/enums/errorCode';
import { ApiRequestError } from '@/services/common/requestInternalsTypes';
import { requestPaymentApi } from './paymentAuthenticatedRequest';

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
}));

vi.mock('@/services/common/request', () => ({
  request: mocks.request,
}));

describe('requestPaymentApi', () => {
  beforeEach(() => {
    mocks.request.mockReset();
  });

  it('delegates expired-session handling to the shared authenticated request lifecycle', async () => {
    const error = new ApiRequestError(ErrorCode.UNAUTHORIZED, '未登录', {
      httpStatus: 401,
      userMessage: '请先登录后再继续操作',
    });
    mocks.request.mockRejectedValue(error);

    await expect(requestPaymentApi('/v1/payment/providers', { method: 'GET' })).rejects.toBe(error);

    expect(mocks.request).toHaveBeenCalledWith('/v1/payment/providers', {
      method: 'GET',
    });
  });

  it('propagates non-authentication save errors without changing request options', async () => {
    const error = new ApiRequestError(ErrorCode.VALIDATION_ERROR, '参数错误', {
      httpStatus: 400,
    });
    mocks.request.mockRejectedValue(error);

    await expect(requestPaymentApi('/v1/payment/providers/alipay', { method: 'PUT' })).rejects.toBe(error);

    expect(mocks.request).toHaveBeenCalledWith('/v1/payment/providers/alipay', { method: 'PUT' });
  });
});
