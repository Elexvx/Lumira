const SUCCESS_STATUSES = new Set(['PAID', 'SUCCESS', 'SETTLED']);
const TERMINAL_STATUSES = new Set([
  ...SUCCESS_STATUSES,
  'FAILED',
  'CANCELLED',
  'CLOSED',
  'EXPIRED',
  'REFUNDED',
]);

export const isBuiltinMockPaymentSuccessful = (status?: string | null) =>
  SUCCESS_STATUSES.has(status?.trim().toUpperCase() || '');

export const isBuiltinMockPaymentPending = (status?: string | null) =>
  !TERMINAL_STATUSES.has(status?.trim().toUpperCase() || '');

export const isBuiltinMockCallbackPending = (status?: string | null) =>
  ['PENDING', 'PROCESSING', 'RETRY'].includes(status?.trim().toUpperCase() || '');

export const resolveBuiltinMockReturnUrl = (value?: string | null) => {
  if (!value) return '/competitions/register';
  try {
    const parsed = new URL(value, 'https://lumira.invalid');
    if (
      parsed.origin !== 'https://lumira.invalid'
      || parsed.pathname !== '/competitions/register/payment-result'
      || parsed.username
      || parsed.password
      || parsed.hash
    ) {
      return '/competitions/register';
    }
    return `${parsed.pathname}${parsed.search}`;
  } catch {
    return '/competitions/register';
  }
};

