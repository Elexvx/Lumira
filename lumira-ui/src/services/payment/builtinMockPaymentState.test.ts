import { describe, expect, it } from 'vitest';
import {
  isBuiltinMockCallbackPending,
  isBuiltinMockPaymentPending,
  isBuiltinMockPaymentSuccessful,
} from './builtinMockPaymentState';

describe('built-in mock payment adapter state', () => {
  it('uses the shared payment terminal and pending semantics', () => {
    expect(isBuiltinMockPaymentSuccessful('PAID')).toBe(true);
    expect(isBuiltinMockPaymentSuccessful('PENDING')).toBe(false);
    expect(isBuiltinMockPaymentPending('PENDING')).toBe(true);
    expect(isBuiltinMockPaymentPending('FAILED')).toBe(false);
  });

  it('keeps polling while a provider callback is pending', () => {
    expect(isBuiltinMockCallbackPending('PROCESSING')).toBe(true);
    expect(isBuiltinMockCallbackPending('DELIVERED')).toBe(false);
  });
});
