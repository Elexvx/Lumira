import { request, type RequestOptions } from '@/services/common/request';
import type { BrandingSettings, SecuritySettings, WatermarkSettings } from '@/types/api';

export interface SecuritySettingsPayload extends SecuritySettings {}
export interface BrandingSettingsPayload extends BrandingSettings {}
export interface WatermarkSettingsPayload extends WatermarkSettings {}

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

};
