import { request } from '@/services/common/request';
import type { PageResponse, RegistrationPaymentQueryParams, RegistrationPaymentRecord } from './types';
import type { PaymentCreateOrderRequest, PaymentOrderRecord, SandboxSimulationOrderRecord } from '@/types/api';

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

export const listSandboxSimulationOrders = () =>
  request<SandboxSimulationOrderRecord[]>('/v1/payment/sandbox/simulations', {
    method: 'GET',
    params: { _t: Date.now() },
  });

export const createSandboxSimulationOrder = (data: { targetUserId: number; amountMinor: number }) =>
  request<SandboxSimulationOrderRecord>('/v1/payment/sandbox/simulations', {
    method: 'POST',
    data,
  });
