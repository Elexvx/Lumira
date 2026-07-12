const PROVIDER_NAMES: Record<string, { zh: string; en: string }> = {
  alipay: { zh: '支付宝', en: 'Alipay' },
  wechat_pay: { zh: '微信支付', en: 'WeChat Pay' },
  stripe: { zh: 'Stripe', en: 'Stripe' },
  paypal: { zh: 'PayPal', en: 'PayPal' },
};

const ENVIRONMENT_NAMES: Record<string, { zh: string; en: string }> = {
  SANDBOX: { zh: '测试', en: 'Test' },
  // Keep the legacy value readable while the UI uses SANDBOX as the single test environment.
  TEST: { zh: '测试', en: 'Test' },
  PRODUCTION: { zh: '正式', en: 'Production' },
};

export const normalizePaymentEnvironment = (environment: string | null | undefined) => {
  const normalized = environment?.trim().toUpperCase() || '';
  return normalized === 'TEST' ? 'SANDBOX' : normalized;
};

export const paymentProviderDisplayName = (
  providerCode: string | null | undefined,
  fallbackName: string | null | undefined,
  english: boolean,
) => {
  const normalized = providerCode?.trim().toLowerCase() || '';
  const translated = PROVIDER_NAMES[normalized];
  return translated ? (english ? translated.en : translated.zh) : (fallbackName || providerCode || '-');
};

export const paymentEnvironmentDisplayName = (
  environment: string | null | undefined,
  english: boolean,
) => {
  const normalized = environment?.trim().toUpperCase() || '';
  const translated = ENVIRONMENT_NAMES[normalized];
  return translated ? (english ? translated.en : translated.zh) : (environment || '-');
};
