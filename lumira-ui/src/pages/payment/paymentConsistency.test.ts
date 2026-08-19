import { describe, expect, it } from 'vitest';
import { isPaymentRegistrationMismatch } from './paymentConsistency';

describe('payment registration consistency', () => {
  it('detects paid orders whose registration is still pending', () => {
    expect(isPaymentRegistrationMismatch({
      orderNo: 'PAY-1',
      paymentStatus: 'PAID',
      registrationStatus: 'PENDING_PAYMENT',
    })).toBe(true);
  });

  it('does not offer replay for unpaid or already confirmed registrations', () => {
    expect(isPaymentRegistrationMismatch({
      orderNo: 'PAY-1',
      paymentStatus: 'PENDING',
      registrationStatus: 'PENDING_PAYMENT',
    })).toBe(false);
    expect(isPaymentRegistrationMismatch({
      orderNo: 'PAY-1',
      paymentStatus: 'PAID',
      registrationStatus: 'CONFIRMED',
    })).toBe(false);
  });
});
