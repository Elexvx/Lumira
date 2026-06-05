import { storage } from '@/cache/storage';
import type { SecuritySettings } from '@/types/api';
import { normalizeSecuritySettings } from './securitySettingsNormalize';

const SECURITY_SETTINGS_KEY = 'security_settings';

export const getStoredSecuritySettings = (): SecuritySettings | null => storage.get<SecuritySettings>(SECURITY_SETTINGS_KEY);

export const persistSecuritySettings = (settings: SecuritySettings) => {
  storage.set(SECURITY_SETTINGS_KEY, normalizeSecuritySettings(settings));
};

export const clearSecuritySettings = () => {
  storage.remove(SECURITY_SETTINGS_KEY);
};
