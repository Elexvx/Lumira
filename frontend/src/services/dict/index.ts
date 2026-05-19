import { request, type RequestOptions } from '@/services/common/request';
import type { DictItemRecord, DictTypeRecord, PagedResult } from '@/types/api';

export interface DictTypeListQuery extends Record<string, unknown> {
  keyword?: string;
  dictCode?: string;
  dictName?: string;
  status?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface DictTypeMutationPayload {
  dictCode: string;
  dictName: string;
  status: string;
  remark?: string;
}

export interface DictItemMutationPayload {
  itemLabel: string;
  itemValue: string;
  sortNo: number;
  status: string;
  remark?: string;
}

export const dictService = {
  types: (params: DictTypeListQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<DictTypeRecord>>('/v1/system/dict-types', {
      method: 'GET',
      params,
      ...options,
    }),
  typeDetail: (id: number, options: RequestOptions = {}) =>
    request<DictTypeRecord>(`/v1/system/dict-types/${id}`, {
      method: 'GET',
      ...options,
    }),
  createType: (payload: DictTypeMutationPayload, options: RequestOptions = {}) =>
    request<DictTypeRecord>('/v1/system/dict-types', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  updateType: (id: number, payload: DictTypeMutationPayload, options: RequestOptions = {}) =>
    request<DictTypeRecord>(`/v1/system/dict-types/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  deleteType: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/system/dict-types/${id}`, {
      method: 'DELETE',
      ...options,
    }),
  items: (dictTypeId: number, options: RequestOptions = {}) =>
    request<DictItemRecord[]>(`/v1/system/dict-types/${dictTypeId}/items`, {
      method: 'GET',
      ...options,
    }),
  createItem: (dictTypeId: number, payload: DictItemMutationPayload, options: RequestOptions = {}) =>
    request<DictItemRecord>(`/v1/system/dict-types/${dictTypeId}/items`, {
      method: 'POST',
      data: payload,
      ...options,
    }),
  updateItem: (dictTypeId: number, itemId: number, payload: DictItemMutationPayload, options: RequestOptions = {}) =>
    request<DictItemRecord>(`/v1/system/dict-types/${dictTypeId}/items/${itemId}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  deleteItem: (dictTypeId: number, itemId: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/system/dict-types/${dictTypeId}/items/${itemId}`, {
      method: 'DELETE',
      ...options,
    }),
};
