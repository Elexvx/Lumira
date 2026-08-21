export type SmsProviderCode = 'aliyun' | 'builtin_mock_sms';

export const resolveVisibleSmsProviderCodes = (mockProviderAvailable?: boolean): SmsProviderCode[] =>
  mockProviderAvailable ? ['aliyun', 'builtin_mock_sms'] : ['aliyun'];

export const resolveSelectedSmsProviderCode = (
  provider?: string | null,
  mockProviderAvailable?: boolean,
): SmsProviderCode | undefined => {
  if (provider === 'builtin_mock_sms') {
    return mockProviderAvailable ? 'builtin_mock_sms' : undefined;
  }
  return 'aliyun';
};
