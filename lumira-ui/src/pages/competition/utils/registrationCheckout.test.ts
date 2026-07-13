import { describe, expect, it } from 'vitest';
import {
  buildRegistrationPaymentResultUrl,
  calculateRegistrationPayableAmount,
  createCleanPaymentResultSearch,
  isPaymentOrderFailed,
  isRegistrationPaymentSuccessful,
  parsePaymentResultRegistrationId,
  pickEnabledCollectedValues,
  retainAvailablePaymentProvider,
} from './registrationCheckout';

describe('registration checkout helpers', () => {
  it('removes disabled or deleted dynamic field values from the submission', () => {
    expect(pickEnabledCollectedValues({ enabled: 'yes', deleted: 'stale', empty: '' }, ['enabled', 'empty']))
      .toEqual({ enabled: 'yes' });
  });

  it('calculates team, member and zero-fee amounts', () => {
    expect(calculateRegistrationPayableAmount(2500, 'TEAM', 3)).toBe(2500);
    expect(calculateRegistrationPayableAmount(2500, 'MEMBER', 3)).toBe(7500);
    expect(calculateRegistrationPayableAmount(0, 'MEMBER', 3)).toBe(0);
  });

  it('builds a restricted result URL and ignores third-party success parameters', () => {
    expect(buildRegistrationPaymentResultUrl('https://contest.example', 42))
      .toBe('https://contest.example/competitions/register/payment-result?registrationId=42');
    expect(parsePaymentResultRegistrationId('?registrationId=42&success=true&trade_status=SUCCESS')).toBe(42);
    expect(createCleanPaymentResultSearch(42)).toBe('?registrationId=42');
    expect(parsePaymentResultRegistrationId('?success=true')).toBeUndefined();
  });

  it('classifies only backend registration success and terminal order failures', () => {
    expect(isRegistrationPaymentSuccessful('CONFIRMED')).toBe(true);
    expect(isRegistrationPaymentSuccessful('PENDING_PAYMENT')).toBe(false);
    expect(isPaymentOrderFailed('EXPIRED')).toBe(true);
    expect(isPaymentOrderFailed('PENDING')).toBe(false);
  });

  it('keeps payment selection strictly single and never auto-selects the first channel', () => {
    expect(retainAvailablePaymentProvider(undefined, ['alipay', 'wechat_pay'])).toBeUndefined();
    expect(retainAvailablePaymentProvider('alipay', ['alipay', 'wechat_pay'])).toBe('alipay');
    expect(retainAvailablePaymentProvider('disabled-provider', ['alipay'])).toBeUndefined();
  });
});
