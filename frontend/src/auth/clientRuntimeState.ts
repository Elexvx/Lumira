import type { AppInitialState } from '@/app.types';
import { storage } from '@/cache/storage';
import { clearBrandingSettings, DEFAULT_BRANDING_SETTINGS } from '@/branding/settings';
import { clearSecuritySettings, DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettings';
import { clearWatermarkSettings, DEFAULT_WATERMARK_SETTINGS } from '@/watermark/settings';

export const clearClientRuntimeState = () => {
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
