export interface PageResponse<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
  hasMore?: boolean;
}

export interface RegistrationPaymentQueryParams extends Record<string, unknown> {
  keyword?: string;
  paymentStatus?: string;
  registrationStatus?: string;
  providerCode?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface RegistrationPaymentRecord {
  registrationId: number;
  registrationNo: string;
  competitionId: number;
  competitionCode?: string | null;
  competitionTitle?: string | null;
  teamId: number;
  teamName?: string | null;
  projectId: number;
  projectTitle?: string | null;
  ownerUserId: number;
  registrationStatus: string;
  participantNo?: string | null;
  memberCount?: number | null;
  payableAmountMinor?: number | null;
  orderNo?: string | null;
  providerCode?: string | null;
  providerOrderNo?: string | null;
  subject?: string | null;
  amountMinor?: number | null;
  currency?: string | null;
  paymentStatus: string;
  paymentUrl?: string | null;
  failureCode?: string | null;
  failureMessage?: string | null;
  orderCreatedAt?: string | null;
  paidAt?: string | null;
  registrationCreatedAt?: string | null;
  updatedAt?: string | null;
}

export interface PaymentRegistrationMismatch {
  registrationId: number;
  registrationNo: string;
  competitionId: number;
  competitionTitle?: string | null;
  paymentOrderNo: string;
  paymentStatus: string;
  registrationUpdatedAt?: string | null;
  ageSeconds: number;
}

export interface PaymentConsistencySnapshot {
  status: 'UP' | 'DEGRADED' | 'NOT_CHECKED';
  checkedAt?: string | null;
  graceSeconds: number;
  candidatesChecked: number;
  mismatchCount: number;
  dependencyFailureCount: number;
  mismatches: PaymentRegistrationMismatch[];
}

export interface PaymentConsistencyReplayResult {
  registrationId: number;
  registrationNo: string;
  paymentOrderNo: string;
  status: 'REPLAYED';
}
