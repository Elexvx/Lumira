import type { CurrentUser, LoginResponse, RefreshTokenResponse } from '@/types/api';
import { request, type RequestOptions } from '@/services/common/request';

export interface LoginPayload {
  username?: string;
  mobile?: string;
  password: string;
}

export interface RefreshTokenPayload {
  refreshToken: string;
}

export const authService = {
  login: (payload: LoginPayload, options: RequestOptions = {}) =>
    request<LoginResponse>('/v1/auth/login', {
      method: 'POST',
      data: payload,
      skipAuth: true,
      silent: true,
      ...options,
    }),
  logout: (options: RequestOptions = {}) =>
    request<boolean>('/v1/auth/logout', {
      method: 'POST',
      ...options,
    }),
  refreshToken: (payload: RefreshTokenPayload, options: RequestOptions = {}) =>
    request<RefreshTokenResponse>('/v1/auth/refresh-token', {
      method: 'POST',
      data: payload,
      skipAuth: true,
      ...options,
    }),
  currentUser: (options: RequestOptions = {}) =>
    request<CurrentUser>('/v1/auth/current-user', {
      method: 'GET',
      ...options,
    }),
};
