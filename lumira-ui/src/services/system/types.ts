import type {
  AgreementSettings,
  BrandingSettings,
  CaptchaChallenge,
  CaptchaVerifyResult,
  FloatingWindowSettings,
  OnlineSessionRecord,
  PasskeySettings,
  SecuritySettings,
  SmtpSettings,
  SmtpSettingsPayload,
  SmtpTestPayload,
  SmtpTestResult,
  SmsVerificationSettings,
  SmsVerificationSettingsPayload,
  VerificationSettings,
  VerificationSettingsPayload,
  WechatLoginSettings,
  WechatLoginSettingsPayload,
  WechatOfficialAccountSettings,
  WechatOfficialAccountSettingsPayload,
  PagedResult,
} from '@/types/api';

export interface OnlineSessionListQuery extends Record<string, unknown> {
  pageNo?: number;
  pageSize?: number;
}
export interface PasskeySettingsPayload extends PasskeySettings {}

export type {
  AgreementSettings,
  BrandingSettings,
  CaptchaChallenge,
  CaptchaVerifyResult,
  FloatingWindowSettings,
  OnlineSessionRecord,
  PasskeySettings,
  SecuritySettings,
  SmtpSettings,
  SmtpSettingsPayload,
  SmtpTestPayload,
  SmtpTestResult,
  SmsVerificationSettings,
  SmsVerificationSettingsPayload,
  VerificationSettings,
  VerificationSettingsPayload,
  WechatLoginSettings,
  WechatLoginSettingsPayload,
  WechatOfficialAccountSettings,
  WechatOfficialAccountSettingsPayload,
  PagedResult,
};
