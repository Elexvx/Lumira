import { request, type RequestOptions } from '@/services/common/request';
import type { CurrentTenantResponse, MyTenant, PagedResult, SwitchTenantResponse, TenantSummary } from '@/types/api';

export interface SwitchTenantPayload {
  tenantId: number;
}

export interface TenantListQuery extends Record<string, unknown> {
  tenantCode?: string;
  tenantName?: string;
  status?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface TenantMutationPayload {
  tenantCode: string;
  tenantName: string;
  tenantShortName?: string;
  status: string;
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
  list: (params: TenantListQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<TenantSummary>>('/v1/tenant/tenants', {
      method: 'GET',
      params,
      ...options,
    }),
  detail: (id: number, options: RequestOptions = {}) =>
    request<TenantSummary>(`/v1/tenant/tenants/${id}`, {
      method: 'GET',
      ...options,
    }),
  create: (payload: TenantMutationPayload, options: RequestOptions = {}) =>
    request<TenantSummary>('/v1/tenant/tenants', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  update: (id: number, payload: TenantMutationPayload, options: RequestOptions = {}) =>
    request<TenantSummary>(`/v1/tenant/tenants/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  delete: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/tenant/tenants/${id}`, {
      method: 'DELETE',
      ...options,
    }),
  switchTenant: (payload: SwitchTenantPayload, options: RequestOptions = {}) =>
    request<SwitchTenantResponse>('/v1/tenant/switch', {
      method: 'POST',
      data: payload,
      ...options,
    }),
};
