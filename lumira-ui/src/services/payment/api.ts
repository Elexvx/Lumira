import { request } from '@/services/common/request';
import type { PageResponse, RegistrationPaymentQueryParams, RegistrationPaymentRecord } from './types';
import type { PaymentCreateOrderRequest, PaymentOrderRecord } from '@/types/api';

export const listRegistrationPayments = (params: RegistrationPaymentQueryParams) =>
  request<PageResponse<RegistrationPaymentRecord>>('/v2/aiadc/payments', {
    method: 'GET',
    params,
  });

export const createSandboxPaymentOrder = (data: PaymentCreateOrderRequest) =>
  request<PaymentOrderRecord>('/v1/payment/sandbox/orders', {
    method: 'POST',
    data,
  });

export const getPaymentOrder = (orderNo: string) =>
  request<PaymentOrderRecord>(`/v1/payment/orders/${encodeURIComponent(orderNo)}`, {
    method: 'GET',
    params: { _t: Date.now() },
  });

export const listSandboxPaymentOrders = (params: { pageNo: number; pageSize: number }) =>
  request<PageResponse<PaymentOrderRecord>>('/v1/payment/sandbox/orders', {
    method: 'GET',
    params: { ...params, _t: Date.now() },
    autoRedirectOnUnauthorized: false,
  });
