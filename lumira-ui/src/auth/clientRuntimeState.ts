import type { AppInitialState } from '@/app.types';
import { DEFAULT_BRANDING_SETTINGS, getStoredBrandingSettings, normalizeBrandingSettings } from '@/branding/settings';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettingsTypes';
import { clearSecuritySettings } from '@/auth/securitySettingsStorage';
import { DEFAULT_WATERMARK_SETTINGS } from '@/watermark/settingsTypes';
import { clearWatermarkSettings } from '@/watermark/settingsStorage';
import { clearStoredSessionState } from '@/auth/sessionState';

export const clearClientRuntimeState = () => {
  clearStoredSessionState();
  clearSecuritySettings();
  clearWatermarkSettings();
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
