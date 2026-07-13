import type { SecuritySettings } from '@/types/api';
import { normalizeSecuritySettings } from './securitySettingsNormalize';

let currentSecuritySettings: SecuritySettings | null = null;

// Runtime-only snapshot. Security policy is database-owned and is reloaded
// from the backend for every fresh browser session.
export const getStoredSecuritySettings = (): SecuritySettings | null => currentSecuritySettings;

export const persistSecuritySettings = (settings: SecuritySettings) => {
  currentSecuritySettings = normalizeSecuritySettings(settings);
};

export const clearSecuritySettings = () => {
  currentSecuritySettings = null;
};
