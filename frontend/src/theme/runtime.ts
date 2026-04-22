import type { ThemePreference } from './settings';
import { resolveThemeColorMode } from './settings';

export interface ThemeRuntimeSnapshot {
  themePreference: ThemePreference;
  systemDarkMode: boolean;
  resolvedColorMode: 'light' | 'dark';
}

const buildInitialThemeRuntimeSnapshot = (): ThemeRuntimeSnapshot => ({
  themePreference: 'system',
  systemDarkMode: false,
  resolvedColorMode: 'light',
});

let themeRuntimeSnapshot = buildInitialThemeRuntimeSnapshot();

export const syncThemeRuntimeSnapshot = (themePreference: ThemePreference, systemDarkMode: boolean) => {
  // Layout config is built outside React, so it reads from this runtime bridge.
  themeRuntimeSnapshot = {
    themePreference,
    systemDarkMode,
    resolvedColorMode: resolveThemeColorMode(themePreference, systemDarkMode),
  };
};

export const getThemeRuntimeSnapshot = () => themeRuntimeSnapshot;

export const resolveLayoutNavTheme = () => {
  return themeRuntimeSnapshot.resolvedColorMode === 'dark' ? 'realDark' : 'light';
};
