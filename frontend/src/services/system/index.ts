import { request, type RequestOptions } from '@/services/common/request';
import type {
  CaptchaChallenge,
  CaptchaVerifyResult,
  AgreementSettings,
  BrandingSettings,
  OnlineSessionRecord,
  PagedResult,
  SecuritySettings,
  SmtpSettings,
  SmtpSettingsPayload,
  SmtpTestPayload,
  SmtpTestResult,
  WatermarkSettings,
} from '@/types/api';

export interface SecuritySettingsPayload extends SecuritySettings {}
export interface BrandingSettingsPayload extends BrandingSettings {}
export interface AgreementSettingsPayload extends AgreementSettings {}
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
  agreementSettings: (options: RequestOptions = {}) =>
    request<AgreementSettings>('/v1/system/agreement-settings', {
      method: 'GET',
      ...options,
    }),
  publicAgreementSettings: (options: RequestOptions = {}) =>
    request<AgreementSettings>('/v1/public/agreement-settings', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      ...options,
    }),
  publicSecuritySettings: (options: RequestOptions = {}) =>
    request<SecuritySettings>('/v1/public/security-settings', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      ...options,
    }),
  updateAgreementSettings: (payload: AgreementSettingsPayload, options: RequestOptions = {}) =>
    request<AgreementSettings>('/v1/system/agreement-settings', {
      method: 'PUT',
      data: payload,
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
  captchaChallenge: (captchaType: SecuritySettings['captchaType'], options: RequestOptions = {}) =>
    request<CaptchaChallenge>('/v1/public/captcha/challenge', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      params: { captchaType },
      ...options,
    }),
  captchaSliderVerify: (
    payload: {
      captchaId: string;
      x: number;
      y: number;
      sliderOffsetX: number;
      duration: number;
      trail: Array<[number, number]>;
      targetType?: string;
      errorCount?: number;
    },
    options: RequestOptions = {},
  ) =>
    request<CaptchaVerifyResult>('/v1/public/captcha/slider/verify', {
      method: 'POST',
      skipAuth: true,
      silent: true,
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
  smtpSettings: (options: RequestOptions = {}) =>
    request<SmtpSettings>('/v1/system/smtp-settings', {
      method: 'GET',
      ...options,
    }),
  updateSmtpSettings: (payload: SmtpSettingsPayload, options: RequestOptions = {}) =>
    request<SmtpSettings>('/v1/system/smtp-settings', {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  testSmtpSettings: (payload: SmtpTestPayload, options: RequestOptions = {}) =>
    request<SmtpTestResult>('/v1/system/smtp-settings/test', {
      method: 'POST',
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
