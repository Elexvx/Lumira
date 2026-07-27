import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  cancelPaymentOrder,
  createPaymentOrder,
  createSandboxPaymentOrder,
  listManualPaymentOrders,
} from './api';

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
}));

vi.mock('@/services/common/request', () => ({
  request: mocks.request,
}));

const order = {
  providerCode: 'alipay',
  orderNo: 'MAN-ALI-P-1-ABC123',
  subject: 'manual verification',
  amountMinor: 1,
  currency: 'CNY',
};

describe('payment order API', () => {
  beforeEach(() => {
    mocks.request.mockReset();
  });

  it('uses the normal order endpoint for production', () => {
    createPaymentOrder(order);
    expect(mocks.request).toHaveBeenCalledWith('/v1/payment/orders', {
      method: 'POST',
      data: order,
    });
  });

  it('uses the sandbox-only endpoint for sandbox orders', () => {
    createSandboxPaymentOrder(order);
    expect(mocks.request).toHaveBeenCalledWith('/v1/payment/sandbox/orders', {
      method: 'POST',
      data: order,
    });
  });

  it('lists persisted manual orders for the current user', () => {
    listManualPaymentOrders({ pageNo: 1, pageSize: 50 });
    expect(mocks.request).toHaveBeenCalledWith('/v1/payment/manual/orders', {
      method: 'GET',
      params: {
        pageNo: 1,
        pageSize: 50,
        _t: expect.any(Number),
      },
      autoRedirectOnUnauthorized: false,
    });
  });

  it('cancels a pending order through the user payment endpoint', () => {
    cancelPaymentOrder('MAN-ALI-P-1-CANCEL');
    expect(mocks.request).toHaveBeenCalledWith(
      '/v1/payment/orders/MAN-ALI-P-1-CANCEL/cancel',
      { method: 'POST' },
    );
  });
});
