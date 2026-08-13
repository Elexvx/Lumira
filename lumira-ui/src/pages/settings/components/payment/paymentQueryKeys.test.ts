import { describe, expect, it, vi } from 'vitest';
import {
  invalidatePaymentProviderSettingsQuery,
  PAYMENT_PROVIDER_SETTINGS_QUERY_KEY,
} from './paymentQueryKeys';

describe('payment provider query keys', () => {
  it('invalidates active and cached payment settings after a plugin mutation', async () => {
    const invalidateQueries = vi.fn().mockResolvedValue(undefined);

    await invalidatePaymentProviderSettingsQuery({ invalidateQueries });

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: PAYMENT_PROVIDER_SETTINGS_QUERY_KEY,
      refetchType: 'all',
    });
  });
});
