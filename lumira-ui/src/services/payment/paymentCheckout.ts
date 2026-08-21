export interface PaymentCheckoutOrder {
  orderNo?: string | null;
  providerCode?: string | null;
  subject?: string | null;
  amountMinor?: number | null;
  currency?: string | null;
  status?: string | null;
  paymentUrl?: string | null;
  returnUrl?: string | null;
}

export interface PaymentCheckoutPresentationOptions {
  preopenedWindow?: Window | null;
  onOrderUpdated?: (order: PaymentCheckoutOrder) => void;
}

export interface PaymentCheckoutAdapter {
  providerCode: string;
  present: (
    order: PaymentCheckoutOrder,
    options: PaymentCheckoutPresentationOptions,
  ) => void;
}

const checkoutAdapters = new Map<string, PaymentCheckoutAdapter>();

const normalizeProviderCode = (providerCode?: string | null) => providerCode?.trim().toLowerCase() || '';

export const registerPaymentCheckoutAdapter = (adapter: PaymentCheckoutAdapter) => {
  const providerCode = normalizeProviderCode(adapter.providerCode);
  checkoutAdapters.set(providerCode, adapter);
  return () => {
    if (checkoutAdapters.get(providerCode) === adapter) {
      checkoutAdapters.delete(providerCode);
    }
  };
};

export const hasInlinePaymentCheckout = (providerCode?: string | null) => (
  checkoutAdapters.has(normalizeProviderCode(providerCode))
);

export const canPresentPaymentCheckout = (order?: PaymentCheckoutOrder | null) => Boolean(
  order?.orderNo && (hasInlinePaymentCheckout(order.providerCode) || order.paymentUrl),
);

export const presentPaymentCheckout = (
  order: PaymentCheckoutOrder,
  options: PaymentCheckoutPresentationOptions = {},
) => {
  const adapter = checkoutAdapters.get(normalizeProviderCode(order.providerCode));
  if (adapter) {
    options.preopenedWindow?.close();
    adapter.present(order, options);
    return true;
  }

  if (!order.paymentUrl) {
    options.preopenedWindow?.close();
    return false;
  }

  if (options.preopenedWindow) {
    options.preopenedWindow.location.href = order.paymentUrl;
  } else if (typeof window !== 'undefined') {
    window.open(order.paymentUrl, '_blank', 'noopener,noreferrer');
  }
  return true;
};
