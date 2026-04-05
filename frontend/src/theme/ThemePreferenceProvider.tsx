import { ConfigProvider, theme as antdTheme } from 'antd';
import type { ReactNode } from 'react';
import { createContext, useContext, useEffect, useLayoutEffect, useMemo, useState } from 'react';
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

  const algorithm = useMemo(() => {
    if (themePreference === 'compact') {
      return [antdTheme.compactAlgorithm];
    }

    return resolvedColorMode === 'dark' ? [antdTheme.darkAlgorithm] : [antdTheme.defaultAlgorithm];
  }, [resolvedColorMode, themePreference]);

  const themeTokens = useMemo(() => {
    if (resolvedColorMode !== 'dark') {
      return undefined;
    }

    return {
      colorBgBase: '#0f1115',
      colorBgLayout: '#0f1115',
      colorBgContainer: '#151515',
      colorBgElevated: '#1b1b1b',
      colorBorderSecondary: '#2a2a2a',
      colorTextBase: '#f5f7fa',
    };
  }, [resolvedColorMode]);

  const themeComponents = useMemo(() => {
    if (resolvedColorMode !== 'dark') {
      return undefined;
    }

    return {
      Layout: {
        bodyBg: '#0f1115',
        headerBg: '#111111',
        siderBg: '#0c0c0c',
      },
    };
  }, [resolvedColorMode]);

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
      <ConfigProvider theme={{ algorithm, token: themeTokens, components: themeComponents }}>{children}</ConfigProvider>
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
