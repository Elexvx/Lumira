import { describe, expect, it } from 'vitest';
import { buildCleanSandboxOrderPath } from './sandboxPaymentReturnUrl';

describe('buildCleanSandboxOrderPath', () => {
  it('keeps the order number and removes Alipay return signature parameters', () => {
    expect(buildCleanSandboxOrderPath(
      'https://bm.aiadc.org.cn/settings/payment?tab=sandbox-orders&orderNo=SBX-123&sign=secret&trade_no=2026&total_amount=0.01',
    )).toBe('/settings/payment?tab=sandbox-orders&orderNo=SBX-123');
  });

  it('falls back to the Alipay out_trade_no parameter', () => {
    expect(buildCleanSandboxOrderPath(
      'https://bm.aiadc.org.cn/settings/payment?out_trade_no=SBX-456&sign=secret',
    )).toBe('/settings/payment?tab=sandbox-orders&orderNo=SBX-456');
  });
});
