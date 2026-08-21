import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  destroy: vi.fn(),
  info: vi.fn(),
}));

vi.mock('@/theme/antdFeedbackBridge', () => ({
  modal: {
    info: mocks.info,
  },
}));

vi.mock('./api', () => ({
  getBuiltinMockPaymentCheckout: vi.fn(),
  simulateBuiltinMockPayment: vi.fn(),
}));

import { presentBuiltinMockPaymentCheckout } from './builtinMockPaymentCheckoutAdapter';

describe('built-in mock payment checkout adapter', () => {
  beforeEach(() => {
    mocks.destroy.mockReset();
    mocks.info.mockReset();
    mocks.info.mockReturnValue({ destroy: mocks.destroy });
  });

  it('presents the mock provider inside the current payment flow', () => {
    presentBuiltinMockPaymentCheckout(
      { orderNo: 'MOCK-ORDER-1', providerCode: 'builtin_mock', paymentUrl: null },
      {},
    );

    expect(mocks.info).toHaveBeenCalledOnce();
    expect(mocks.info.mock.calls[0][0]).toMatchObject({
      title: '模拟支付调试',
      width: 760,
      closable: true,
      okText: '关闭',
    });
    expect(mocks.info.mock.calls[0][0].content.props.orderNo).toBe('MOCK-ORDER-1');
  });

  it('does not open without an existing payment order number', () => {
    presentBuiltinMockPaymentCheckout({ providerCode: 'builtin_mock' }, {});
    expect(mocks.info).not.toHaveBeenCalled();
  });
});
