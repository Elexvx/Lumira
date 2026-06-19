import {
  applyThemePreferenceToDocument,
  normalizeThemePreference,
  persistThemePreference,
  type ThemePreference,
} from '@/theme/settings';
import { syncThemeRuntimeSnapshot, type ThemeRuntimeSnapshot } from '@/theme/runtime';

export const getSystemDarkMode = () => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false;
  }

  return window.matchMedia('(prefers-color-scheme: dark)').matches;
};

export const syncThemePreferenceRuntime = (
  value: ThemePreference,
  systemDarkMode = getSystemDarkMode(),
): ThemeRuntimeSnapshot => syncThemeRuntimeSnapshot(normalizeThemePreference(value), systemDarkMode);

export const commitThemePreference = (
  value: ThemePreference,
  options: {
    systemDarkMode?: boolean;
    persist?: boolean;
  } = {},
): ThemeRuntimeSnapshot => {
  const themePreference = normalizeThemePreference(value);
  const systemDarkMode = options.systemDarkMode ?? getSystemDarkMode();
  const snapshot = syncThemeRuntimeSnapshot(themePreference, systemDarkMode);

  if (typeof document !== 'undefined') {
    applyThemePreferenceToDocument(document.documentElement, themePreference, systemDarkMode, document.body);
  }

  if (options.persist !== false && typeof localStorage !== 'undefined') {
    persistThemePreference(themePreference);
  }

  return snapshot;
};
