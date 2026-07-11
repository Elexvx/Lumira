const PAYMENT_MESSAGE_TRANSLATIONS: Record<string, string> = {
  'Payment provider is disabled': '支付服务商已停用',
  'Payment provider is not configured': '支付服务商尚未完成配置',
  'Payment provider test failed': '支付服务商测试失败',
  'Payment connectivity test passed': '支付连通性测试通过',
};

const PAYMENT_MESSAGE_PREFIX_TRANSLATIONS: Array<[string, string]> = [
  ['Payment connectivity test failed: ', '支付连通性测试失败：'],
];

export const localizePaymentMessage = (value: string | null | undefined, english: boolean) => {
  if (!value || english) {
    return value || '';
  }

  const translated = PAYMENT_MESSAGE_TRANSLATIONS[value];
  if (translated) {
    return translated;
  }

  const prefixTranslation = PAYMENT_MESSAGE_PREFIX_TRANSLATIONS.find(([prefix]) => value.startsWith(prefix));
  if (!prefixTranslation) {
    return value;
  }

  const [prefix, localizedPrefix] = prefixTranslation;
  return `${localizedPrefix}${value.slice(prefix.length)}`;
};
