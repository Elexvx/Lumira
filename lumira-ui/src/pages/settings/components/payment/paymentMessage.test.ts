import { beforeEach, describe, expect, it } from 'vitest';
import { clearDatabaseMessages, installDatabaseMessages } from '@/i18n/databaseMessage';
import { localizePaymentMessage, paymentConnectivityStatusDisplayName } from './paymentMessage';

describe('localizePaymentMessage', () => {
  beforeEach(() => {
    clearDatabaseMessages();
    installDatabaseMessages('zh-CN', {
      'payment.message.providerDisabled': '支付服务商已停用',
      'payment.message.providerReady': '支付服务商配置可用',
      'payment.message.connectivityPassed': '支付连通性测试通过',
      'payment.message.missingFields': '缺少必填支付字段：{reason}',
      'payment.message.connectivityFailedWithReason': '支付连通性测试失败：{reason}',
      'payment.connectivity.available': '可用',
      'payment.connectivity.unavailable': '不可用',
      'payment.connectivity.notTested': '未测试',
    });
  });

  it('reads known backend messages from the database runtime bundle', () => {
    expect(localizePaymentMessage('Payment provider is disabled')).toBe('支付服务商已停用');
    expect(localizePaymentMessage('Payment provider is ready')).toBe('支付服务商配置可用');
    expect(localizePaymentMessage('Payment connectivity test passed')).toBe('支付连通性测试通过');
  });

  it('translates a known prefix without hiding diagnostic details', () => {
    expect(localizePaymentMessage('Missing required payment fields: 私钥')).toBe('缺少必填支付字段：私钥');
    expect(localizePaymentMessage('Payment connectivity test failed: timeout')).toBe('支付连通性测试失败：timeout');
  });

  it('keeps unknown server messages unchanged', () => {
    expect(localizePaymentMessage('Unexpected gateway response')).toBe('Unexpected gateway response');
  });

  it('uses database labels for connectivity status', () => {
    expect(paymentConnectivityStatusDisplayName(true)).toBe('可用');
    expect(paymentConnectivityStatusDisplayName(false)).toBe('不可用');
    expect(paymentConnectivityStatusDisplayName(null)).toBe('未测试');
  });

  it('uses concise labels for connectivity status', () => {
    expect(paymentConnectivityStatusDisplayName(true, false)).toBe('可用');
    expect(paymentConnectivityStatusDisplayName(false, false)).toBe('不可用');
    expect(paymentConnectivityStatusDisplayName(null, false)).toBe('未测试');
    expect(paymentConnectivityStatusDisplayName(true, true)).toBe('Available');
    expect(paymentConnectivityStatusDisplayName(false, true)).toBe('Unavailable');
  });
});
