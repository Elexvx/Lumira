import { request, type RequestOptions } from '@/services/common/request';
import type { BrandingSettings, OnlineSessionRecord, PagedResult, SecuritySettings, WatermarkSettings } from '@/types/api';

export interface SecuritySettingsPayload extends SecuritySettings {}
export interface BrandingSettingsPayload extends BrandingSettings {}
export interface WatermarkSettingsPayload extends WatermarkSettings {}
export interface OnlineSessionListQuery extends Record<string, unknown> {
  pageNo?: number;
  pageSize?: number;
}

export const systemService = {
  brandingSettings: (options: RequestOptions = {}) =>
    request<BrandingSettings>('/v1/system/branding-settings', {
      method: 'GET',
      ...options,
    }),
  publicBrandingSettings: (options: RequestOptions = {}) =>
    request<BrandingSettings>('/v1/public/branding-settings', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      ...options,
    }),
  updateBrandingSettings: (payload: BrandingSettingsPayload, options: RequestOptions = {}) =>
    request<BrandingSettings>('/v1/system/branding-settings', {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  uploadImage: (file: File, options: RequestOptions = {}) => {
    const formData = new FormData();
    formData.append('file', file);
    return request<string>('/v1/system/uploads/image', {
      method: 'POST',
      headers: {},
      data: formData,
      ...options,
    });
  },
  securitySettings: (options: RequestOptions = {}) =>
    request<SecuritySettings>('/v1/system/security-settings', {
      method: 'GET',
      ...options,
    }),
  updateSecuritySettings: (payload: SecuritySettingsPayload, options: RequestOptions = {}) =>
    request<SecuritySettings>('/v1/system/security-settings', {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  watermarkSettings: (options: RequestOptions = {}) =>
    request<WatermarkSettings>('/v1/system/watermark-settings', {
      method: 'GET',
      ...options,
    }),
  updateWatermarkSettings: (payload: WatermarkSettingsPayload, options: RequestOptions = {}) =>
    request<WatermarkSettings>('/v1/system/watermark-settings', {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  onlineUsers: (params: OnlineSessionListQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<OnlineSessionRecord>>('/v1/system/online-users', {
      method: 'GET',
      params,
      ...options,
    }),
  kickOnlineUser: (sessionId: string, options: RequestOptions = {}) =>
    request<boolean>(`/v1/system/online-users/${sessionId}`, {
      method: 'DELETE',
      ...options,
    }),
  banOnlineUser: (userId: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/system/online-users/${userId}/ban`, {
      method: 'PATCH',
      ...options,
    }),
};
