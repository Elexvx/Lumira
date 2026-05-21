import { request, type RequestOptions } from '@/services/common/request';
import type {
  CaptchaChallenge,
  CaptchaVerifyResult,
  AgreementSettings,
  BrandingSettings,
  FloatingWindowSettings,
  HealthResponse,
  LoginCapabilities,
  OnlineSessionRecord,
  PagedResult,
  PlatformModuleRecord,
  PlatformModuleValidationPayload,
  PlatformModuleValidationResult,
  ProfileFieldSetting,
  SecuritySettings,
  SmtpSettings,
  SmtpSettingsPayload,
  SmsVerificationSettings,
  SmsVerificationSettingsPayload,
  VerificationSettings,
  VerificationSettingsPayload,
  SmtpTestPayload,
  SmtpTestResult,
  TenantRecord,
  WatermarkSettings,
  WechatLoginSettings,
  WechatLoginSettingsPayload,
  PasskeySettings,
} from '@/types/api';

export interface SecuritySettingsPayload extends SecuritySettings {}
export interface BrandingSettingsPayload extends BrandingSettings {}
export interface AgreementSettingsPayload extends AgreementSettings {}
export interface WatermarkSettingsPayload extends WatermarkSettings {}
export interface FloatingWindowSettingsPayload extends FloatingWindowSettings {}
export interface PasskeySettingsPayload extends PasskeySettings {}
export interface ProfileFieldSettingsPayload {
  items: Array<{
    fieldKey: string;
    visible: boolean;
    weight?: number;
  }>;
}
export interface OnlineSessionListQuery extends Record<string, unknown> {
  pageNo?: number;
  pageSize?: number;
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
  status: string;
  remark?: string | null;
}

export const systemService = {
  health: (options: RequestOptions = {}) =>
    request<HealthResponse>('/health', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      ...options,
    }),
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
  publicLoginCapabilities: (options: RequestOptions = {}) =>
    request<LoginCapabilities>('/v1/public/login-capabilities', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      ...options,
    }),
  modules: (options: RequestOptions = {}) =>
    request<PlatformModuleRecord[]>('/v1/system/modules', {
      method: 'GET',
      ...options,
    }),
  module: (moduleCode: string, options: RequestOptions = {}) =>
    request<PlatformModuleRecord>(`/v1/system/modules/${encodeURIComponent(moduleCode)}`, {
      method: 'GET',
      ...options,
    }),
  validateModule: (payload: PlatformModuleValidationPayload, options: RequestOptions = {}) =>
    request<PlatformModuleValidationResult>('/v1/system/modules/validate', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  createModule: (payload: PlatformModuleValidationPayload, options: RequestOptions = {}) =>
    request<PlatformModuleRecord>('/v1/system/modules', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  tenants: (params: TenantListQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<TenantRecord>>('/v1/system/tenants', {
      method: 'GET',
      params,
      ...options,
    }),
  tenantDetail: (id: number, options: RequestOptions = {}) =>
    request<TenantRecord>(`/v1/system/tenants/${id}`, {
      method: 'GET',
      ...options,
    }),
  createTenant: (payload: TenantMutationPayload, options: RequestOptions = {}) =>
    request<TenantRecord>('/v1/system/tenants', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  updateTenant: (id: number, payload: TenantMutationPayload, options: RequestOptions = {}) =>
    request<TenantRecord>(`/v1/system/tenants/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  deleteTenant: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/system/tenants/${id}`, {
      method: 'DELETE',
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
  verificationSettings: (options: RequestOptions = {}) =>
    request<VerificationSettings>('/v1/system/verification/settings', {
      method: 'GET',
      ...options,
    }),
  updateVerificationSettings: (payload: VerificationSettingsPayload, options: RequestOptions = {}) =>
    request<VerificationSettings>('/v1/system/verification/settings', {
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
  floatingWindowSettings: (options: RequestOptions = {}) =>
    request<FloatingWindowSettings>('/v1/system/floating-window-settings', {
      method: 'GET',
      ...options,
    }),
  updateFloatingWindowSettings: (payload: FloatingWindowSettingsPayload, options: RequestOptions = {}) =>
    request<FloatingWindowSettings>('/v1/system/floating-window-settings', {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  profileFieldSettings: (options: RequestOptions = {}) =>
    request<ProfileFieldSetting[]>('/v1/system/profile-field-settings', {
      method: 'GET',
      ...options,
    }),
  updateProfileFieldSettings: (payload: ProfileFieldSettingsPayload, options: RequestOptions = {}) =>
    request<ProfileFieldSetting[]>('/v1/system/profile-field-settings', {
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
  smsVerificationSettings: (options: RequestOptions = {}) =>
    request<SmsVerificationSettings>('/v1/system/verification/sms-settings', {
      method: 'GET',
      ...options,
    }),
  updateSmsVerificationSettings: (payload: SmsVerificationSettingsPayload, options: RequestOptions = {}) =>
    request<SmsVerificationSettings>('/v1/system/verification/sms-settings', {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  wechatLoginSettings: (options: RequestOptions = {}) =>
    request<WechatLoginSettings>('/v1/system/verification/wechat-settings', {
      method: 'GET',
      ...options,
    }),
  updateWechatLoginSettings: (payload: WechatLoginSettingsPayload, options: RequestOptions = {}) =>
    request<WechatLoginSettings>('/v1/system/verification/wechat-settings', {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  passkeySettings: (options: RequestOptions = {}) =>
    request<PasskeySettings>('/v1/system/verification/passkey-settings', {
      method: 'GET',
      ...options,
    }),
  updatePasskeySettings: (payload: PasskeySettingsPayload, options: RequestOptions = {}) =>
    request<PasskeySettings>('/v1/system/verification/passkey-settings', {
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
