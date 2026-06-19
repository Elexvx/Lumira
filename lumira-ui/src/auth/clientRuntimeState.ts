import type { AppInitialState } from '@/app.types';
import { clearBrandingSettings, DEFAULT_BRANDING_SETTINGS } from '@/branding/settings';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettingsTypes';
import { clearSecuritySettings } from '@/auth/securitySettingsStorage';
import { DEFAULT_WATERMARK_SETTINGS } from '@/watermark/settingsTypes';
import { clearWatermarkSettings } from '@/watermark/settingsStorage';
import { clearStoredSessionState } from '@/auth/sessionState';

export const clearClientRuntimeState = () => {
  clearStoredSessionState();
  clearSecuritySettings();
  clearBrandingSettings();
  clearWatermarkSettings();
};

export const buildLoggedOutInitialState = (): AppInitialState => ({
  currentUser: undefined,
  menuTree: [],
  menuVersion: 0,
  availablePlugins: [],
  securitySettings: DEFAULT_SECURITY_SETTINGS,
  brandingSettings: DEFAULT_BRANDING_SETTINGS,
  watermarkSettings: DEFAULT_WATERMARK_SETTINGS,
});
