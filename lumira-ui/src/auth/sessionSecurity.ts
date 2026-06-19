import { request } from '@/services/common/request';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettingsTypes';
import { getStoredSecuritySettings, persistSecuritySettings } from '@/auth/securitySettingsStorage';
import { normalizeSecuritySettings } from '@/auth/securitySettingsNormalize';
import type { SecuritySettings } from '@/types/api';

type SecuritySettingsCacheKey = string;
const securitySettingsInFlight = new Map<SecuritySettingsCacheKey, Promise<SecuritySettings>>();

const buildSecuritySettingsRequestKey = (options: { allowUnauthorizedWithoutRedirect?: boolean; timeoutMs?: number }) =>
  `${options.allowUnauthorizedWithoutRedirect ? 'anon' : 'auth'}-${options.timeoutMs ?? ''}`;

export const loadSecuritySettings = async (
  options: {
    allowUnauthorizedWithoutRedirect?: boolean;
    timeoutMs?: number;
  } = {},
): Promise<SecuritySettings> => {
  const requestKey = buildSecuritySettingsRequestKey(options);
  const existing = securitySettingsInFlight.get(requestKey);
  if (existing) {
    return existing;
  }

  try {
    const requestOptions = options.allowUnauthorizedWithoutRedirect
      ? {
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
        }
      : {};
    const primaryPath = '/v2/platform/security-settings';
    const fallbackPath = options.allowUnauthorizedWithoutRedirect ? '/v1/public/security-settings' : '/v1/system/security-settings';
    const requestPromise = request<SecuritySettings>(primaryPath, {
      method: 'GET',
      timeoutMs: options.timeoutMs,
      ...requestOptions,
    })
      .catch(() =>
        request<SecuritySettings>(fallbackPath, {
          method: 'GET',
          timeoutMs: options.timeoutMs,
          ...requestOptions,
        }),
      )
      .then((value) => normalizeSecuritySettings(value))
      .then((securitySettings) => {
        persistSecuritySettings(securitySettings);
        return securitySettings;
      })
      .catch(() => normalizeSecuritySettings(getStoredSecuritySettings() || DEFAULT_SECURITY_SETTINGS))
      .finally(() => {
        if (securitySettingsInFlight.get(requestKey) === requestPromise) {
          securitySettingsInFlight.delete(requestKey);
        }
      });

    securitySettingsInFlight.set(requestKey, requestPromise);
    return requestPromise;
  } catch {
    securitySettingsInFlight.delete(requestKey);
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
