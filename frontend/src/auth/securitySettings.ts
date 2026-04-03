import { storage } from '@/cache/storage';
import type { SecuritySettings } from '@/types/api';

const SECURITY_SETTINGS_KEY = 'security_settings';

export const DEFAULT_SECURITY_SETTINGS: SecuritySettings = {
  idleTimeoutSeconds: 1800,
  accessTokenExpireSeconds: 1800,
  refreshTokenExpireSeconds: 604800,
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
  return {
    idleTimeoutSeconds,
    accessTokenExpireSeconds,
    refreshTokenExpireSeconds,
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
