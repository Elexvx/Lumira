import { storage } from '@/cache/storage';
import type { SecuritySettings } from '@/types/api';

const SECURITY_SETTINGS_KEY = 'security_settings';

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
  passwordMinLength: 6,
  passwordRequireUppercase: false,
  passwordRequireLowercase: false,
  passwordRequireSpecialCharacter: false,
  passwordAllowConsecutiveCharacters: true,
};

export const normalizeSecuritySettings = (settings?: Partial<SecuritySettings> | null): SecuritySettings => {
  const idleTimeoutSeconds = toPositiveNumber(settings?.idleTimeoutSeconds, DEFAULT_SECURITY_SETTINGS.idleTimeoutSeconds);
  const accessTokenExpireSeconds = toPositiveNumber(
    settings?.accessTokenExpireSeconds,
    DEFAULT_SECURITY_SETTINGS.accessTokenExpireSeconds,
  );
  const refreshTokenExpireSeconds = toPositiveNumber(
    settings?.refreshTokenExpireSeconds,
    DEFAULT_SECURITY_SETTINGS.refreshTokenExpireSeconds,
  );
  const allowMultiDeviceLogin = toBoolean(settings?.allowMultiDeviceLogin, DEFAULT_SECURITY_SETTINGS.allowMultiDeviceLogin);
  const captchaEnabled = toBoolean(settings?.captchaEnabled, DEFAULT_SECURITY_SETTINGS.captchaEnabled);
  const captchaType = toCaptchaType(settings?.captchaType, DEFAULT_SECURITY_SETTINGS.captchaType);
  const loginDefenseWindowMinutes = toPositiveNumber(
    settings?.loginDefenseWindowMinutes,
    DEFAULT_SECURITY_SETTINGS.loginDefenseWindowMinutes,
  );
  const loginMaxValidationAttempts = toPositiveNumber(
    settings?.loginMaxValidationAttempts,
    DEFAULT_SECURITY_SETTINGS.loginMaxValidationAttempts,
  );
  const loginMaxFailureCount = toPositiveNumber(settings?.loginMaxFailureCount, DEFAULT_SECURITY_SETTINGS.loginMaxFailureCount);
  const passwordMinLength = toPositiveNumber(settings?.passwordMinLength, DEFAULT_SECURITY_SETTINGS.passwordMinLength);
  const passwordRequireUppercase = toBoolean(
    settings?.passwordRequireUppercase,
    DEFAULT_SECURITY_SETTINGS.passwordRequireUppercase,
  );
  const passwordRequireLowercase = toBoolean(
    settings?.passwordRequireLowercase,
    DEFAULT_SECURITY_SETTINGS.passwordRequireLowercase,
  );
  const passwordRequireSpecialCharacter = toBoolean(
    settings?.passwordRequireSpecialCharacter,
    DEFAULT_SECURITY_SETTINGS.passwordRequireSpecialCharacter,
  );
  const passwordAllowConsecutiveCharacters = toBoolean(
    settings?.passwordAllowConsecutiveCharacters,
    DEFAULT_SECURITY_SETTINGS.passwordAllowConsecutiveCharacters,
  );
  return {
    idleTimeoutSeconds,
    accessTokenExpireSeconds,
    refreshTokenExpireSeconds,
    allowMultiDeviceLogin,
    captchaEnabled,
    captchaType,
    loginDefenseWindowMinutes,
    loginMaxValidationAttempts,
    loginMaxFailureCount,
    passwordMinLength,
    passwordRequireUppercase,
    passwordRequireLowercase,
    passwordRequireSpecialCharacter,
    passwordAllowConsecutiveCharacters,
  };
};

export const getStoredSecuritySettings = (): SecuritySettings | null => storage.get<SecuritySettings>(SECURITY_SETTINGS_KEY);

export const persistSecuritySettings = (settings: SecuritySettings) => {
  storage.set(SECURITY_SETTINGS_KEY, normalizeSecuritySettings(settings));
};

export const clearSecuritySettings = () => {
  storage.remove(SECURITY_SETTINGS_KEY);
};

const toPositiveNumber = (value: unknown, fallback: number) => {
  const numericValue = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(numericValue) && numericValue > 0 ? Math.floor(numericValue) : fallback;
};

const toBoolean = (value: unknown, fallback: boolean) => {
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'number') {
    return value === 1;
  }
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (['true', '1', 'yes', 'on'].includes(normalized)) {
      return true;
    }
    if (['false', '0', 'no', 'off'].includes(normalized)) {
      return false;
    }
  }
  return fallback;
};

const toCaptchaType = (value: unknown, fallback: SecuritySettings['captchaType']): SecuritySettings['captchaType'] => {
  if (typeof value === 'string') {
    const normalized = value.trim().toUpperCase();
    if (normalized === 'IMAGE' || normalized === 'SLIDER') {
      return normalized;
    }
  }
  return fallback;
};
