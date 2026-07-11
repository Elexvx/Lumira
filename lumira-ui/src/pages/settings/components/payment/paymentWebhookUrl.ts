const PAYMENT_WEBHOOK_PATH = '/api/v2/payment/webhooks';

const currentOrigin = () => typeof window === 'undefined' ? '' : window.location.origin;

export const buildSystemPaymentWebhookUrl = (providerCode: string, origin = currentOrigin()) => {
  const normalizedOrigin = origin.trim().replace(/\/+$/, '');
  const normalizedProviderCode = providerCode.trim();
  if (!normalizedOrigin || !normalizedProviderCode) {
    return '';
  }
  return `${normalizedOrigin}${PAYMENT_WEBHOOK_PATH}/${encodeURIComponent(normalizedProviderCode)}`;
};
