import { describe, expect, it } from 'vitest';
import {
  EMPTY_PAYMENT_VALUE,
  PAYMENT_DETAIL_PROVIDER_ORDER_LABEL,
  PAYMENT_IDENTIFIER_LABELS,
  formatPaymentIdentifier,
  getPaymentStatusText,
} from './paymentRecordPresentation';

describe('payment record presentation', () => {
  it('keeps registration, platform order and provider transaction numbers distinct', () => {
    expect(PAYMENT_IDENTIFIER_LABELS).toEqual({
      orderNo: '订单号',
      providerOrderNo: '支付流水号',
      registrationNo: '报名号',
    });
    expect(PAYMENT_DETAIL_PROVIDER_ORDER_LABEL).toBe('支付流水号');
  });

  it('renders nullable identifiers without falling back to another identifier', () => {
    expect(formatPaymentIdentifier(null)).toBe(EMPTY_PAYMENT_VALUE);
    expect(formatPaymentIdentifier(undefined)).toBe(EMPTY_PAYMENT_VALUE);
    expect(formatPaymentIdentifier('')).toBe(EMPTY_PAYMENT_VALUE);
    expect(formatPaymentIdentifier('PAY-2026-001')).toBe('PAY-2026-001');
  });

  it('labels zero-cost registrations without an order as not requiring payment', () => {
    expect(getPaymentStatusText('NOT_REQUIRED')).toBe('无需支付');
  });
});
