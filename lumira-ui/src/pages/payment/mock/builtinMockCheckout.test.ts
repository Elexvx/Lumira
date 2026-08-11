import { describe, expect, it } from 'vitest';
import {
  isBuiltinMockCallbackPending,
  isBuiltinMockPaymentPending,
  isBuiltinMockPaymentSuccessful,
  resolveBuiltinMockReturnUrl,
} from './builtinMockCheckout';

describe('built-in mock checkout helpers', () => {
  it('maps payment and callback states without treating checkout submission as payment success', () => {
    expect(isBuiltinMockPaymentSuccessful('PAID')).toBe(true);
    expect(isBuiltinMockPaymentSuccessful('PENDING')).toBe(false);
    expect(isBuiltinMockPaymentPending('PENDING')).toBe(true);
    expect(isBuiltinMockPaymentPending('FAILED')).toBe(false);
    expect(isBuiltinMockCallbackPending('RETRY')).toBe(true);
    expect(isBuiltinMockCallbackPending('DELIVERED')).toBe(false);
  });

  it('accepts only the local competition payment result route', () => {
    expect(resolveBuiltinMockReturnUrl('/competitions/register/payment-result?registrationId=9'))
      .toBe('/competitions/register/payment-result?registrationId=9');
    expect(resolveBuiltinMockReturnUrl('https://evil.example/competitions/register/payment-result'))
      .toBe('/competitions/register');
    expect(resolveBuiltinMockReturnUrl('//evil.example/competitions/register/payment-result'))
      .toBe('/competitions/register');
    expect(resolveBuiltinMockReturnUrl('/competitions/register/payment-result#redirect'))
      .toBe('/competitions/register');
  });
});

