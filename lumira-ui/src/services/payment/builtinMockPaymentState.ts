export const isBuiltinMockPaymentSuccessful = (status?: string | null) =>
  ['PAID', 'SUCCESS', 'SETTLED'].includes(status?.toUpperCase() || '');

export const isBuiltinMockPaymentPending = (status?: string | null) =>
  ['CREATED', 'PENDING'].includes(status?.toUpperCase() || '');

export const isBuiltinMockCallbackPending = (status?: string | null) =>
  ['PENDING', 'PROCESSING', 'RETRY'].includes(status?.toUpperCase() || '');
