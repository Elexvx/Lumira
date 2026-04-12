import { request, type RequestOptions } from '@/services/common/request';
import type { PagedResult, RoleRecord, UserDetail, UserRecord } from '@/types/api';

export interface UserListQuery extends Record<string, unknown> {
  keyword?: string;
  username?: string;
  mobile?: string;
  status?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface UserMutationPayload {
  username: string;
  mobile?: string;
  idCardNumber?: string;
  nickname?: string;
  realName?: string;
  birthMonth?: string;
  gender?: string;
  region?: string;
  availableTime?: string;
  email?: string;
  avatarUrl?: string;
  status: string;
  password?: string;
  roleIds?: number[];
}

export interface UserStatusPayload {
  status: string;
}

export const userService = {
  list: (params: UserListQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<UserRecord>>('/v1/system/users', {
      method: 'GET',
      params,
      ...options,
    }),
  detail: (id: number, options: RequestOptions = {}) =>
    request<UserDetail>(`/v1/system/users/${id}`, {
      method: 'GET',
      ...options,
    }),
  create: (payload: UserMutationPayload, options: RequestOptions = {}) =>
    request<UserDetail>('/v1/system/users', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  update: (id: number, payload: UserMutationPayload, options: RequestOptions = {}) =>
    request<UserDetail>(`/v1/system/users/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  changeStatus: (id: number, payload: UserStatusPayload, options: RequestOptions = {}) =>
    request<boolean>(`/v1/system/users/${id}/status`, {
      method: 'PATCH',
      data: payload,
      ...options,
    }),
  roles: (id: number, options: RequestOptions = {}) =>
    request<RoleRecord[]>(`/v1/system/users/${id}/roles`, {
      method: 'GET',
      ...options,
    }),
};
