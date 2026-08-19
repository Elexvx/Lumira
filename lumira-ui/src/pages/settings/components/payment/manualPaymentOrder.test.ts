import { describe, expect, it } from 'vitest';
import type { PaymentOrderRecord, PaymentProviderSettings } from '@/types/api';
import {
  buildManualPaymentRequest,
  isManualPaymentOrderCancellable,
  listManualPaymentProviders,
  listWechatManualScenes,
  resolveManualOrderEnvironment,
  resolveManualOrderScene,
} from './manualPaymentOrder';

const setting = (overrides: Partial<PaymentProviderSettings>): PaymentProviderSettings => ({
  providerCode: 'alipay',
  providerName: 'Alipay',
  enabled: true,
  configured: true,
  environment: 'PRODUCTION',
  configuredFields: [],
  ...overrides,
});

describe('manualPaymentOrder', () => {
  it('lists enabled and configured providers with a manual or simulated checkout flow', () => {
    expect(listManualPaymentProviders([
      setting({ providerCode: 'alipay' }),
      setting({ providerCode: 'wechat_pay' }),
      setting({ providerCode: 'builtin_mock', providerName: '内置模拟支付', environment: 'SANDBOX' }),
      setting({ providerCode: 'stripe' }),
      setting({ providerCode: 'alipay', configured: false }),
    ]).map((item) => item.providerCode)).toEqual(['alipay', 'wechat_pay', 'builtin_mock']);
  });

  it('uses only enabled WeChat scenes and falls back to NATIVE', () => {
    expect(listWechatManualScenes(setting({
      providerCode: 'wechat_pay',
      enabledScenes: ['H5', 'JSAPI'],
    }))).toEqual(['H5', 'JSAPI']);
    expect(listWechatManualScenes(setting({ providerCode: 'wechat_pay' }))).toEqual(['NATIVE']);
  });

  it('builds a production WeChat NATIVE request with callback metadata', () => {
    const request = buildManualPaymentRequest({
      values: {
        providerCode: 'wechat_pay',
        amountYuan: 0.01,
        subject: ' 微信支付验收 ',
        scene: 'NATIVE',
        productionConfirmed: true,
      },
      settings: setting({
        providerCode: 'wechat_pay',
        environment: 'PRODUCTION',
        currency: 'cny',
      }),
      origin: 'https://bm.aiadc.org.cn',
      timestamp: 1785100000000,
      randomSuffix: 'ABC123',
    });

    expect(request).toMatchObject({
      providerCode: 'wechat_pay',
      orderNo: 'MAN-WX-P-1785100000000-ABC123',
      subject: '微信支付验收',
      amountMinor: 1,
      currency: 'CNY',
      metadata: {
        bizType: 'manual_payment_verification',
        paymentEnvironment: 'PRODUCTION',
        paymentScene: 'NATIVE',
      },
      idempotencyKey: 'MAN-WX-P-1785100000000-ABC123',
    });
    expect(request.returnUrl).toContain('tab=sandbox-orders');
  });

  it('adds H5 payer data and JSAPI OpenID only for the selected scene', () => {
    const h5 = buildManualPaymentRequest({
      values: {
        providerCode: 'wechat_pay',
        amountYuan: 0.01,
        subject: 'H5',
        scene: 'H5',
        clientIp: '203.0.113.10',
      },
      settings: setting({ providerCode: 'wechat_pay' }),
      origin: 'https://bm.aiadc.org.cn',
      timestamp: 1,
      randomSuffix: 'H5',
    });
    expect(h5.clientIp).toBe('203.0.113.10');
    expect(h5.metadata).toMatchObject({ paymentScene: 'H5', h5Type: 'Wap' });

    const jsapi = buildManualPaymentRequest({
      values: {
        providerCode: 'wechat_pay',
        amountYuan: 0.01,
        subject: 'JSAPI',
        scene: 'JSAPI',
        openid: ' user-openid ',
      },
      settings: setting({ providerCode: 'wechat_pay' }),
      origin: 'https://bm.aiadc.org.cn',
      timestamp: 2,
      randomSuffix: 'JS',
    });
    expect(jsapi.clientIp).toBeUndefined();
    expect(jsapi.metadata).toMatchObject({ paymentScene: 'JSAPI', openid: 'user-openid' });
  });

  it('recovers provider environment and scene from an order response', () => {
    const order = {
      orderNo: 'MAN-WX-P-1-A',
      providerCode: 'wechat_pay',
      providerOrderNo: '',
      subject: 'test',
      amountMinor: 1,
      currency: 'CNY',
      status: 'PENDING',
      metadata: {
        providerCode: 'wechat_pay',
        metadata: {
          paymentEnvironment: 'PRODUCTION',
          paymentScene: 'NATIVE',
        },
      },
    } satisfies PaymentOrderRecord;

    expect(resolveManualOrderEnvironment(order, [])).toBe('PRODUCTION');
    expect(resolveManualOrderScene(order)).toBe('NATIVE');
  });

  it('allows only created or pending manual orders to be cancelled', () => {
    const order = {
      orderNo: 'MAN-ALI-P-1-CANCEL',
      providerCode: 'alipay',
      providerOrderNo: '',
      subject: 'test',
      amountMinor: 1,
      currency: 'CNY',
      status: 'PENDING',
    } satisfies PaymentOrderRecord;

    expect(isManualPaymentOrderCancellable(order)).toBe(true);
    expect(isManualPaymentOrderCancellable({ ...order, status: 'CREATED' })).toBe(true);
    expect(isManualPaymentOrderCancellable({ ...order, status: 'PAID' })).toBe(false);
    expect(isManualPaymentOrderCancellable({ ...order, status: 'CANCELLED' })).toBe(false);
  });
});
