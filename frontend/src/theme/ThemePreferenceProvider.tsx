import { App as AntdApp, ConfigProvider } from 'antd';
import type { ReactNode } from 'react';
import { createContext, useContext, useEffect, useLayoutEffect, useMemo, useState } from 'react';
import { resolveAntdLocale } from '@/i18n/antdLocale';
import { buildAntdThemeConfig, syncAntdStaticThemeHolder } from '@/theme/antdTheme';
import { commitThemePreference, getSystemDarkMode, syncThemePreferenceRuntime } from '@/theme/apply';
import {
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

  // Keep the non-React layout config in sync with the current theme snapshot.
  const themeSnapshot = syncThemePreferenceRuntime(themePreference, systemDarkMode);
  const resolvedColorMode = themeSnapshot.resolvedColorMode;

  const themeConfig = useMemo(
    () =>
      buildAntdThemeConfig({
        themePreference,
        resolvedColorMode,
      }),
    [resolvedColorMode, themePreference],
  );

  useLayoutEffect(() => {
    commitThemePreference(themePreference, { systemDarkMode, persist: false });
  }, [systemDarkMode, themePreference]);

  const setThemePreference = (value: ThemePreference) => {
    const nextThemePreference = normalizeThemePreference(value);
    commitThemePreference(nextThemePreference);
    setThemePreferenceState(nextThemePreference);
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
      <ConfigProvider locale={resolveAntdLocale()} theme={themeConfig} variant="filled">
        <AntdApp>{children}</AntdApp>
      </ConfigProvider>
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
