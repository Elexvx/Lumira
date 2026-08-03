import { beforeEach, describe, expect, it } from 'vitest';
import { clearDatabaseMessages, installDatabaseMessages } from '@/i18n/databaseMessage';
import { normalizePaymentEnvironment, paymentEnvironmentDisplayName, paymentProviderDisplayName } from './paymentDisplay';

describe('payment display localization', () => {
  beforeEach(() => {
    clearDatabaseMessages();
    installDatabaseMessages('zh-CN', {
      'payment.provider.alipay': '支付宝',
      'payment.environment.sandbox': '测试',
      'payment.environment.production': '正式',
    });
  });

  it('reads payment labels from the database runtime bundle', () => {
    expect(paymentProviderDisplayName('alipay', 'alipay')).toBe('支付宝');
    expect(paymentEnvironmentDisplayName('SANDBOX')).toBe('测试');
    expect(paymentEnvironmentDisplayName('TEST')).toBe('测试');
  });

  it('keeps unknown provider fallbacks readable', () => {
    expect(paymentProviderDisplayName('custom', 'Custom Pay')).toBe('Custom Pay');
  });

  it('normalizes the legacy test environment to the single test value', () => {
    expect(normalizePaymentEnvironment('TEST')).toBe('SANDBOX');
    expect(normalizePaymentEnvironment('SANDBOX')).toBe('SANDBOX');
    expect(normalizePaymentEnvironment('PRODUCTION')).toBe('PRODUCTION');
  });
});
