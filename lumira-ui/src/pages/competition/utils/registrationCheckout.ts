import type { CompetitionFeeMode } from '@/services/competition/types';

export const pickEnabledCollectedValues = (
  values: Record<string, unknown> | null | undefined,
  enabledKeys: string[],
) => {
  const allowed = new Set(enabledKeys);
  return Object.fromEntries(
    Object.entries(values || {}).filter(([key, value]) => allowed.has(key) && value !== undefined && value !== null && value !== ''),
  );
};

export const calculateRegistrationPayableAmount = (
  entryFeeMinor: number | null | undefined,
  feeMode: CompetitionFeeMode | null | undefined,
  memberCount: number,
) => Math.max(0, entryFeeMinor || 0) * (feeMode === 'MEMBER' ? Math.max(0, memberCount) : 1);

export const buildRegistrationPaymentResultUrl = (origin: string, registrationId: number) => {
  const url = new URL('/competitions/register/payment-result', origin);
  url.searchParams.set('registrationId', String(registrationId));
  return url.toString();
};

export const parsePaymentResultRegistrationId = (search: string) => {
  const value = Number(new URLSearchParams(search).get('registrationId'));
  return Number.isSafeInteger(value) && value > 0 ? value : undefined;
};

export const createCleanPaymentResultSearch = (registrationId: number) =>
  `?${new URLSearchParams({ registrationId: String(registrationId) }).toString()}`;

export const isRegistrationPaymentSuccessful = (status?: string | null) =>
  status === 'PAID' || status === 'CONFIRMED';

export const isPaymentOrderFailed = (status?: string | null) =>
  ['FAILED', 'CANCELLED', 'CLOSED', 'EXPIRED'].includes((status || '').toUpperCase());

export const retainAvailablePaymentProvider = (current: string | undefined, availableProviderCodes: string[]) =>
  current && availableProviderCodes.includes(current) ? current : undefined;
