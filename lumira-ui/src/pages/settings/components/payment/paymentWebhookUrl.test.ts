import { describe, expect, it } from 'vitest';
import { buildSystemPaymentWebhookUrl } from './paymentWebhookUrl';

describe('system managed payment webhook URL', () => {
  it('builds the public V2 callback URL for Alipay', () => {
    expect(buildSystemPaymentWebhookUrl('alipay', 'https://bm.aiadc.org.cn')).toBe(
      'https://bm.aiadc.org.cn/api/v2/payment/webhooks/alipay',
    );
  });

  it('normalizes trailing slashes and supports other providers', () => {
    expect(buildSystemPaymentWebhookUrl('wechat_pay', 'https://example.com/')).toBe(
      'https://example.com/api/v2/payment/webhooks/wechat_pay',
    );
  });

  it('returns an empty value when no public origin is available', () => {
    expect(buildSystemPaymentWebhookUrl('alipay', '')).toBe('');
  });
});
