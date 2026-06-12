import { request } from '@/services/common/request';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettingsTypes';
import { getStoredSecuritySettings, persistSecuritySettings } from '@/auth/securitySettingsStorage';
import { normalizeSecuritySettings } from '@/auth/securitySettingsNormalize';
import type { SecuritySettings } from '@/types/api';

export const loadSecuritySettings = async (
  options: {
    allowUnauthorizedWithoutRedirect?: boolean;
    timeoutMs?: number;
  } = {},
): Promise<SecuritySettings> => {
  try {
    const requestOptions = options.allowUnauthorizedWithoutRedirect
      ? {
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
        }
      : {};
    const securitySettings = normalizeSecuritySettings(
      await request<SecuritySettings>('/v1/system/security-settings', {
        method: 'GET',
        timeoutMs: options.timeoutMs,
        ...requestOptions,
      }),
    );
    persistSecuritySettings(securitySettings);
    return securitySettings;
  } catch {
    return normalizeSecuritySettings(getStoredSecuritySettings() || DEFAULT_SECURITY_SETTINGS);
  }
};

export const saveSecuritySettings = async (securitySettings: SecuritySettings): Promise<SecuritySettings> => {
  const response = await request<SecuritySettings>('/v1/system/security-settings', {
    method: 'PUT',
    data: securitySettings,
  });
  const normalized = normalizeSecuritySettings(response);
  persistSecuritySettings(normalized);
  return normalized;
};
