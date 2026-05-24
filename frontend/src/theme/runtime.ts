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
let themeRuntimeSnapshotSynced = false;

const getDocumentResolvedColorMode = () => {
  if (typeof document === 'undefined') {
    return null;
  }

  const theme = document.documentElement.dataset.theme;
  if (theme === 'dark' || theme === 'light') {
    return theme;
  }

  return null;
};

export const syncThemeRuntimeSnapshot = (themePreference: ThemePreference, systemDarkMode: boolean): ThemeRuntimeSnapshot => {
  // Layout config is built outside React, so it reads from this runtime bridge.
  themeRuntimeSnapshotSynced = true;
  themeRuntimeSnapshot = {
    themePreference,
    systemDarkMode,
    resolvedColorMode: resolveThemeColorMode(themePreference, systemDarkMode),
  };

  return themeRuntimeSnapshot;
};

export const getThemeRuntimeSnapshot = () => themeRuntimeSnapshot;

export const resolveThemeRuntimeSnapshot = (): ThemeRuntimeSnapshot => {
  if (themeRuntimeSnapshotSynced) {
    return themeRuntimeSnapshot;
  }

  const documentResolvedColorMode = getDocumentResolvedColorMode();
  if (!documentResolvedColorMode) {
    return themeRuntimeSnapshot;
  }

  return {
    ...themeRuntimeSnapshot,
    resolvedColorMode: documentResolvedColorMode,
  };
};
