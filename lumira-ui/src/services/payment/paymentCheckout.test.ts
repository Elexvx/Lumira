import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  canPresentPaymentCheckout,
  hasInlinePaymentCheckout,
  presentPaymentCheckout,
  registerPaymentCheckoutAdapter,
} from './paymentCheckout';

describe('payment checkout adapters', () => {
  const unregister: Array<() => void> = [];

  afterEach(() => {
    unregister.splice(0).forEach((dispose) => dispose());
    vi.restoreAllMocks();
  });

  it('adapts an inline provider without navigating to a second checkout route', () => {
    const present = vi.fn();
    unregister.push(registerPaymentCheckoutAdapter({ providerCode: 'builtin_mock', present }));
    const preopenedWindow = { close: vi.fn() } as unknown as Window;
    const order = { orderNo: 'ORDER-1', providerCode: 'builtin_mock', paymentUrl: null };

    expect(hasInlinePaymentCheckout(order.providerCode)).toBe(true);
    expect(canPresentPaymentCheckout(order)).toBe(true);
    expect(presentPaymentCheckout(order, { preopenedWindow })).toBe(true);
    expect(preopenedWindow.close).toHaveBeenCalledOnce();
    expect(present).toHaveBeenCalledWith(order, { preopenedWindow });
  });

  it('keeps real providers on the existing redirect checkout behavior', () => {
    const checkoutWindow = { location: { href: 'about:blank' } } as unknown as Window;
    const order = { orderNo: 'ORDER-2', providerCode: 'alipay', paymentUrl: 'https://pay.example.test' };

    expect(presentPaymentCheckout(order, { preopenedWindow: checkoutWindow })).toBe(true);
    expect(checkoutWindow.location.href).toBe(order.paymentUrl);
  });

  it('rejects an order with neither an adapter nor a payment url', () => {
    expect(presentPaymentCheckout({ orderNo: 'ORDER-3', providerCode: 'unknown' })).toBe(false);
  });
});
