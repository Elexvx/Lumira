import { databaseMessage } from '@/i18n/databaseMessage';

const PROVIDER_MESSAGE_KEYS: Record<string, string> = {
  alipay: 'payment.provider.alipay',
  wechat_pay: 'payment.provider.wechatPay',
  stripe: 'payment.provider.stripe',
  paypal: 'payment.provider.paypal',
};

const ENVIRONMENT_MESSAGE_KEYS: Record<string, string> = {
  SANDBOX: 'payment.environment.sandbox',
  // Keep the legacy value readable while the UI uses SANDBOX as the single test environment.
  TEST: 'payment.environment.sandbox',
  PRODUCTION: 'payment.environment.production',
};

export const normalizePaymentEnvironment = (environment: string | null | undefined) => {
  const normalized = environment?.trim().toUpperCase() || '';
  return normalized === 'TEST' ? 'SANDBOX' : normalized;
};

export const paymentProviderDisplayName = (
  providerCode: string | null | undefined,
  fallbackName: string | null | undefined,
) => {
  const normalized = providerCode?.trim().toLowerCase() || '';
  const messageKey = PROVIDER_MESSAGE_KEYS[normalized];
  return messageKey ? databaseMessage(messageKey) : (fallbackName || providerCode || '-');
};

export const paymentEnvironmentDisplayName = (
  environment: string | null | undefined,
) => {
  const normalized = environment?.trim().toUpperCase() || '';
  const messageKey = ENVIRONMENT_MESSAGE_KEYS[normalized];
  return messageKey ? databaseMessage(messageKey) : (environment || '-');
};
