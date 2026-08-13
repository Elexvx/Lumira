import type { QueryClient } from '@tanstack/react-query';

export const PAYMENT_PROVIDER_SETTINGS_QUERY_KEY = ['payment-provider-settings'] as const;

export const invalidatePaymentProviderSettingsQuery = (
  client: Pick<QueryClient, 'invalidateQueries'>,
) => client.invalidateQueries({
  queryKey: PAYMENT_PROVIDER_SETTINGS_QUERY_KEY,
  refetchType: 'all',
});
