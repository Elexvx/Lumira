import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPaymentOrder, createSandboxPaymentOrder } from './api';

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
});
