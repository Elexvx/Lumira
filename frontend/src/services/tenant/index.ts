import { request, type RequestOptions } from '@/services/common/request';
import type { CurrentTenantResponse, MyTenant, SwitchTenantResponse } from '@/types/api';

export interface SwitchTenantPayload {
  tenantId: number;
}

export const tenantService = {
  currentTenant: (options: RequestOptions = {}) =>
    request<CurrentTenantResponse>('/v1/tenant/current', {
      method: 'GET',
      ...options,
    }),
  myTenants: (options: RequestOptions = {}) =>
    request<MyTenant[]>('/v1/tenant/my-tenants', {
      method: 'GET',
      ...options,
    }),
  switchTenant: (payload: SwitchTenantPayload, options: RequestOptions = {}) =>
    request<SwitchTenantResponse>('/v1/tenant/switch', {
      method: 'POST',
      data: payload,
      ...options,
    }),
};
