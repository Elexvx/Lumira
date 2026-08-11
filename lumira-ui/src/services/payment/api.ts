import { request } from '@/services/common/request';
import type { PageResponse, RegistrationPaymentQueryParams, RegistrationPaymentRecord } from './types';
import type {
  BuiltinMockPaymentCheckout,
  BuiltinMockPaymentSimulationRequest,
  BuiltinMockPaymentSimulationResult,
  PaymentCreateOrderRequest,
  PaymentOrderRecord,
} from '@/types/api';

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

export const createPaymentOrder = (data: PaymentCreateOrderRequest) =>
  request<PaymentOrderRecord>('/v1/payment/orders', {
    method: 'POST',
    data,
  });

export const listManualPaymentOrders = (params: { pageNo: number; pageSize: number }) =>
  request<PageResponse<PaymentOrderRecord>>('/v1/payment/manual/orders', {
    method: 'GET',
    params: { ...params, _t: Date.now() },
    autoRedirectOnUnauthorized: false,
  });

export const getPaymentOrder = (orderNo: string) =>
  request<PaymentOrderRecord>(`/v1/payment/orders/${encodeURIComponent(orderNo)}`, {
    method: 'GET',
    params: { _t: Date.now() },
  });

export const cancelPaymentOrder = (orderNo: string) =>
  request<PaymentOrderRecord>(`/v1/payment/orders/${encodeURIComponent(orderNo)}/cancel`, {
    method: 'POST',
  });

export const getBuiltinMockPaymentCheckout = (orderNo: string) =>
  request<BuiltinMockPaymentCheckout>(
    `/v2/payment/builtin-mock/orders/${encodeURIComponent(orderNo)}/checkout`,
    {
      method: 'GET',
      params: { _t: Date.now() },
    },
  );

export const simulateBuiltinMockPayment = (
  orderNo: string,
  data: BuiltinMockPaymentSimulationRequest,
) =>
  request<BuiltinMockPaymentSimulationResult>(
    `/v2/payment/builtin-mock/orders/${encodeURIComponent(orderNo)}/simulate`,
    {
      method: 'POST',
      data,
    },
  );

export const listSandboxPaymentOrders = (params: { pageNo: number; pageSize: number }) =>
  request<PageResponse<PaymentOrderRecord>>('/v1/payment/sandbox/orders', {
    method: 'GET',
    params: { ...params, _t: Date.now() },
    autoRedirectOnUnauthorized: false,
  });
