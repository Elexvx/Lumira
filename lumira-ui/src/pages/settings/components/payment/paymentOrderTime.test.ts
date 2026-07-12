import { describe, expect, it } from 'vitest';
import { getPaymentOrderCreatedAtMillis, sortPaymentOrdersNewestFirst } from './paymentOrderTime';

describe('paymentOrderTime', () => {
  it('uses the embedded sandbox timestamp before a stale database timestamp', () => {
    expect(getPaymentOrderCreatedAtMillis(
      'SIM-1783798153558-81030F',
      '2026-07-12T03:29:13',
    )).toBe(1783798153558);
  });

  it('sorts mixed Alipay and local sandbox orders by actual creation time descending', () => {
    const orders = [
      { orderNo: 'SBX-1783827505988-T0G9FC', createdAt: '2026-07-12T11:38:29' },
      { orderNo: 'SIM-1783830619206-2569C2', createdAt: '2026-07-12T12:30:19' },
      { orderNo: 'SIM-1783830650013-A6ABA9', createdAt: '2026-07-12T12:30:50' },
    ];

    expect(sortPaymentOrdersNewestFirst(orders).map((order) => order.orderNo)).toEqual([
      'SIM-1783830650013-A6ABA9',
      'SIM-1783830619206-2569C2',
      'SBX-1783827505988-T0G9FC',
    ]);
  });
});
