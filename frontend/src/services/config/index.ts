import { request, type RequestOptions } from '@/services/common/request';
import type { PagedResult, SystemConfigRecord } from '@/types/api';

export interface ConfigListQuery extends Record<string, unknown> {
  keyword?: string;
  configKey?: string;
  configName?: string;
  configScope?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface ConfigMutationPayload {
  configKey: string;
  configName: string;
  configValue: string;
  configScope: string;
  remark?: string;
}

export const configService = {
  list: (params: ConfigListQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<SystemConfigRecord>>('/v1/system/configs', {
      method: 'GET',
      params,
      ...options,
    }),
  detail: (id: number, options: RequestOptions = {}) =>
    request<SystemConfigRecord>(`/v1/system/configs/${id}`, {
      method: 'GET',
      ...options,
    }),
  create: (payload: ConfigMutationPayload, options: RequestOptions = {}) =>
    request<SystemConfigRecord>('/v1/system/configs', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  update: (id: number, payload: ConfigMutationPayload, options: RequestOptions = {}) =>
    request<SystemConfigRecord>(`/v1/system/configs/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
};
