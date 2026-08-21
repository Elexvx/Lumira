import type { SecuritySettings } from '@/types/api';

export type { SecuritySettings };

export const DEFAULT_SECURITY_SETTINGS: SecuritySettings = {
  idleTimeoutSeconds: 1800,
  accessTokenExpireSeconds: 1800,
  refreshTokenExpireSeconds: 604800,
  allowMultiDeviceLogin: true,
  captchaEnabled: false,
  captchaType: 'IMAGE',
  loginDefenseWindowMinutes: 5,
  loginMaxValidationAttempts: 100,
  loginMaxFailureCount: 10,
  verificationCodeExpireSeconds: 300,
  verificationCodeCooldownSeconds: 60,
  passwordMinLength: 6,
  passwordRequireUppercase: false,
  passwordRequireLowercase: false,
  passwordRequireSpecialCharacter: false,
  passwordAllowConsecutiveCharacters: true,
};
