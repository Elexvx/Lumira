import { ConfigProvider } from 'antd';
import type { ReactNode } from 'react';
import { createContext, useContext, useEffect, useLayoutEffect, useMemo, useState } from 'react';
import { buildAntdThemeConfig, syncAntdStaticThemeHolder } from '@/theme/antdTheme';
import { syncThemeRuntimeSnapshot } from '@/theme/runtime';
import {
  applyThemePreferenceToDocument,
  getStoredThemePreference,
  normalizeThemePreference,
  persistThemePreference,
  type ThemePreference,
} from './settings';

interface ThemePreferenceContextValue {
  themePreference: ThemePreference;
  resolvedColorMode: 'light' | 'dark';
  isCompact: boolean;
  setThemePreference: (value: ThemePreference) => void;
}

const ThemePreferenceContext = createContext<ThemePreferenceContextValue | null>(null);

const getSystemDarkMode = () => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false;
  }

  return window.matchMedia('(prefers-color-scheme: dark)').matches;
};

export const ThemePreferenceProvider = ({ children }: { children: ReactNode }) => {
  const [themePreference, setThemePreferenceState] = useState<ThemePreference>(() =>
    normalizeThemePreference(getStoredThemePreference()),
  );
  const [systemDarkMode, setSystemDarkMode] = useState(getSystemDarkMode);

  useEffect(() => {
    persistThemePreference(themePreference);
  }, [themePreference]);

  useEffect(() => {
    syncAntdStaticThemeHolder();
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return;
    }

    const mediaQueryList = window.matchMedia('(prefers-color-scheme: dark)');
    const updateSystemMode = () => {
      setSystemDarkMode(mediaQueryList.matches);
    };

    updateSystemMode();

    if (typeof mediaQueryList.addEventListener === 'function') {
      mediaQueryList.addEventListener('change', updateSystemMode);
      return () => mediaQueryList.removeEventListener('change', updateSystemMode);
    }

    mediaQueryList.addListener(updateSystemMode);
    return () => mediaQueryList.removeListener(updateSystemMode);
  }, []);

  const resolvedColorMode = themePreference === 'dark' || (themePreference === 'system' && systemDarkMode) ? 'dark' : 'light';

  const themeConfig = useMemo(() => buildAntdThemeConfig(), [resolvedColorMode, themePreference]);

  // Keep the non-React layout config in sync with the current theme snapshot.
  syncThemeRuntimeSnapshot(themePreference, systemDarkMode);

  useLayoutEffect(() => {
    if (typeof document === 'undefined') {
      return;
    }

    const root = document.documentElement;
    applyThemePreferenceToDocument(root, themePreference, systemDarkMode, document.body);
  }, [systemDarkMode, themePreference]);

  const setThemePreference = (value: ThemePreference) => {
    setThemePreferenceState(normalizeThemePreference(value));
  };

  const contextValue = useMemo<ThemePreferenceContextValue>(
    () => ({
      themePreference,
      resolvedColorMode,
      isCompact: themePreference === 'compact',
      setThemePreference,
    }),
    [resolvedColorMode, themePreference],
  );

  return (
    <ThemePreferenceContext.Provider value={contextValue}>
      <ConfigProvider theme={themeConfig}>{children}</ConfigProvider>
    </ThemePreferenceContext.Provider>
  );
};

export const useThemePreference = () => {
  const context = useContext(ThemePreferenceContext);
  if (!context) {
    throw new Error('useThemePreference must be used within ThemePreferenceProvider');
  }

  return context;
};
