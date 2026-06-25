import { request } from '@/services/common/request';
import type { PageResponse, RegistrationPaymentQueryParams, RegistrationPaymentRecord } from './types';

export const listRegistrationPayments = (params: RegistrationPaymentQueryParams) =>
  request<PageResponse<RegistrationPaymentRecord>>('/v2/aiadc/payments', {
    method: 'GET',
    params,
  });
