import type { RegistrationPaymentRecord } from '@/services/payment/types';

const paidStatuses = new Set(['PAID', 'SUCCESS', 'SETTLED']);

export const isPaymentRegistrationMismatch = (
  record?: Pick<RegistrationPaymentRecord, 'orderNo' | 'paymentStatus' | 'registrationStatus'> | null,
) => Boolean(
  record?.orderNo
  && paidStatuses.has(String(record.paymentStatus || '').toUpperCase())
  && String(record.registrationStatus || '').toUpperCase() === 'PENDING_PAYMENT'
);
