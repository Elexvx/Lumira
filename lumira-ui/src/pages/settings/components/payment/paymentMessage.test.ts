import { describe, expect, it } from 'vitest';
import { localizePaymentMessage } from './paymentMessage';

describe('localizePaymentMessage', () => {
  it('translates known payment messages for Chinese', () => {
    expect(localizePaymentMessage('Payment provider is disabled', false)).toBe('支付服务商已停用');
    expect(localizePaymentMessage('Payment provider is ready', false)).toBe('支付服务商配置可用');
    expect(localizePaymentMessage('Payment connectivity test passed', false)).toBe('支付连通性测试通过');
  });

  it('keeps English messages in the English locale', () => {
    expect(localizePaymentMessage('Payment provider is disabled', true)).toBe('Payment provider is disabled');
  });

  it('translates a known prefix without hiding diagnostic details', () => {
    expect(localizePaymentMessage('Missing required payment fields: 私钥', false)).toBe('缺少必填支付字段：私钥');
    expect(localizePaymentMessage('Payment connectivity test failed: timeout', false)).toBe('支付连通性测试失败：timeout');
  });

  it('keeps unknown server messages unchanged', () => {
    expect(localizePaymentMessage('Unexpected gateway response', false)).toBe('Unexpected gateway response');
  });
});
