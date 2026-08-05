import type { AppInitialState } from '@/app.types';
import { DEFAULT_BRANDING_SETTINGS, getStoredBrandingSettings, normalizeBrandingSettings } from '@/branding/settings';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettingsTypes';
import { clearSecuritySettings } from '@/auth/securitySettingsStorage';
import { DEFAULT_WATERMARK_SETTINGS } from '@/watermark/settingsTypes';
import { clearWatermarkSettings } from '@/watermark/settingsStorage';
import { clearStoredSessionState } from '@/auth/sessionState';
import { queryClient } from '@/query/queryClient';

const USER_SCOPED_SESSION_STORAGE_KEYS = ['lumira_wechat_contact_bind_required'] as const;

const clearUserScopedSessionStorage = () => {
  if (typeof window === 'undefined') {
    return;
  }
  USER_SCOPED_SESSION_STORAGE_KEYS.forEach((key) => {
    try {
      window.sessionStorage.removeItem(key);
    } catch {
      // Runtime snapshots below remain authoritative when browser storage is restricted.
    }
  });
};

export const clearClientRuntimeState = () => {
  queryClient.clear();
  clearStoredSessionState();
  clearSecuritySettings();
  clearWatermarkSettings();
  clearUserScopedSessionStorage();
};

export const buildLoggedOutInitialState = (): AppInitialState => ({
  currentUser: undefined,
  menuTree: [],
  menuVersion: 0,
  availablePlugins: [],
  securitySettings: DEFAULT_SECURITY_SETTINGS,
  brandingSettings: normalizeBrandingSettings(getStoredBrandingSettings() || DEFAULT_BRANDING_SETTINGS),
  watermarkSettings: DEFAULT_WATERMARK_SETTINGS,
});
