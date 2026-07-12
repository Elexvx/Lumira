import { describe, expect, it } from 'vitest';
import { normalizePaymentEnvironment, paymentEnvironmentDisplayName, paymentProviderDisplayName } from './paymentDisplay';

describe('payment display localization', () => {
  it('uses Chinese labels in the Chinese interface', () => {
    expect(paymentProviderDisplayName('alipay', 'alipay', false)).toBe('支付宝');
    expect(paymentEnvironmentDisplayName('SANDBOX', false)).toBe('测试');
    expect(paymentEnvironmentDisplayName('TEST', false)).toBe('测试');
  });

  it('uses English labels in the English interface', () => {
    expect(paymentProviderDisplayName('alipay', '支付宝', true)).toBe('Alipay');
    expect(paymentEnvironmentDisplayName('SANDBOX', true)).toBe('Test');
  });

  it('normalizes the legacy test environment to the single test value', () => {
    expect(normalizePaymentEnvironment('TEST')).toBe('SANDBOX');
    expect(normalizePaymentEnvironment('SANDBOX')).toBe('SANDBOX');
    expect(normalizePaymentEnvironment('PRODUCTION')).toBe('PRODUCTION');
  });
});
