import type { VerificationSettings, SmtpSettings, SmtpTestPayload } from '@/types/api';

export type SmsProviderCode = 'aliyun' | 'tencent' | 'mock' | 'custom';
export type AuthenticatorCode = 'passkey_login' | 'sms_login' | 'email_login' | 'wechat_login' | 'password_login';
export type LoginModeCode = 'passkey' | 'sms' | 'email' | 'wechat' | 'password';
export type ConfigDrawerMode = 'totp' | 'sms' | 'email' | 'wechat' | 'passkey' | 'basic';

export interface SmsProviderFieldConfig {
  name: string;
  label: string;
  placeholder?: string;
  required?: boolean;
  password?: boolean;
}

export interface SmsProviderSchema {
  fields: SmsProviderFieldConfig[];
}

export interface AuthenticatorRecord {
  key: AuthenticatorCode;
  order: number;
  identifier: string;
  type: string;
  title: string;
  description: string;
  enabled: boolean;
}

export const SMS_PROVIDER_OPTIONS: Array<{ label: string; value: SmsProviderCode }> = [
  { label: '阿里云短信', value: 'aliyun' },
  { label: '腾讯云短信', value: 'tencent' },
  { label: '本地模拟', value: 'mock' },
  { label: '自定义网关', value: 'custom' },
];

export const SMS_PROVIDER_SCHEMAS: Record<SmsProviderCode, SmsProviderSchema> = {
  aliyun: {
    fields: [
      { name: 'signName', label: '短信签名', placeholder: '例如：宏翔商道', required: true },
      { name: 'templateCode', label: '模板编码', placeholder: '例如：SMS_123456789', required: true },
      { name: 'accessKeyId', label: 'Access Key ID', placeholder: '短信服务访问密钥 ID', required: true },
      { name: 'accessKeySecret', label: 'Access Key Secret', placeholder: '留空则保持现有密钥', password: true },
      { name: 'endpoint', label: '服务地址', placeholder: '例如：https://dysmsapi.aliyuncs.com' },
      { name: 'region', label: '地域', placeholder: '例如：cn-hangzhou' },
    ],
  },
  tencent: {
    fields: [
      { name: 'signName', label: '短信签名', placeholder: '例如：宏翔商道', required: true },
      { name: 'templateCode', label: '模板 ID', placeholder: '例如：1234567', required: true },
      { name: 'accessKeyId', label: 'SecretId', placeholder: '腾讯云 SecretId', required: true },
      { name: 'accessKeySecret', label: 'SecretKey', placeholder: '留空则保持现有密钥', password: true, required: true },
      { name: 'endpoint', label: 'API 地址', placeholder: '例如：https://sms.tencentcloudapi.com' },
      { name: 'region', label: '地域', placeholder: '例如：ap-guangzhou' },
    ],
  },
  mock: {
    fields: [
      { name: 'signName', label: '模拟签名', placeholder: '例如：测试短信' },
      { name: 'templateCode', label: '模拟模板编码', placeholder: '例如：MOCK_SMS_001' },
    ],
  },
  custom: {
    fields: [
      { name: 'endpoint', label: '网关地址', placeholder: '例如：https://sms.example.com/api', required: true },
      { name: 'accessKeyId', label: '网关账号', placeholder: '例如：gateway-user', required: true },
      { name: 'accessKeySecret', label: '网关密钥', placeholder: '留空则保持现有密钥', password: true, required: true },
      { name: 'signName', label: '签名', placeholder: '例如：宏翔商道', required: true },
      { name: 'templateCode', label: '模板编码', placeholder: '例如：SMS_123456789', required: true },
      { name: 'region', label: '地域', placeholder: '按网关要求填写' },
    ],
  },
};

export const normalizeProviderCode = (value?: string | null): SmsProviderCode => {
  if (value === 'tencent' || value === 'mock' || value === 'custom') {
    return value;
  }
  return 'aliyun';
};

export const normalizeDrawerMode = (value?: string | null): ConfigDrawerMode | null => {
  if (value === 'basic') {
    return 'basic';
  }
  if (value === 'totp' || value === 'sms' || value === 'email' || value === 'wechat' || value === 'passkey') {
    return value;
  }
  return null;
};

export const resolveDrawerTitle = (mode: ConfigDrawerMode | null) => {
  if (mode === 'sms') {
    return '配置短信认证器';
  }
  if (mode === 'email') {
    return '配置邮箱认证';
  }
  if (mode === 'wechat') {
    return '配置微信登录';
  }
  if (mode === 'passkey') {
    return '配置通行密钥';
  }
  if (mode === 'totp') {
    return '配置 2FA';
  }
  return '配置密码认证器';
};

export const verificationFormInitialValues: VerificationSettings = {
  enabled: true,
  emailLoginEnabled: false,
  passwordLoginEnabled: true,
  loginModeOrder: ['passkey', 'sms', 'email', 'wechat', 'password'],
};

export const resolveLoginModeFromAuthenticatorKey = (key: AuthenticatorCode): LoginModeCode => {
  if (key === 'passkey_login') {
    return 'passkey';
  }
  if (key === 'sms_login') {
    return 'sms';
  }
  if (key === 'email_login') {
    return 'email';
  }
  if (key === 'wechat_login') {
    return 'wechat';
  }
  return 'password';
};

export const smtpFormInitialValues: SmtpSettings = {
  host: '',
  port: 25,
  username: '',
  password: '',
  from: '',
  authEnabled: true,
  startTlsEnabled: true,
  sslEnabled: false,
};

export const smtpTestInitialValues: SmtpTestPayload = {
  subject: 'SMTP 测试邮件',
  content: '这是一封来自系统的 SMTP 测试邮件。',
  toEmail: '',
};

export const SMS_ACCESS_KEY_SECRET_MASK = '********';
export const SMTP_PASSWORD_MASK = '********';
export const WECHAT_APP_SECRET_MASK = '********';
