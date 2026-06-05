import { ProConfigProvider, enUSIntl, zhCNIntl, type IntlType } from '@ant-design/pro-components';
import { App as AntdApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import type { ReactNode } from 'react';
import { createContext, useContext, useEffect, useLayoutEffect, useMemo, useState } from 'react';
import { resolveRuntimeLocale } from '@/i18n/locale';
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

const TABLE_TOOLBAR_LABELS: Record<string, Record<string, string>> = {
  'zh-CN': {
    'tableToolBar.density': '间距',
    'tableToolBar.columnDisplay': '设置展示字段',
    'tableToolBar.columnSetting': '设置展示字段',
  },
  'en-US': {
    'tableToolBar.density': 'Spacing',
    'tableToolBar.columnDisplay': 'Display fields',
    'tableToolBar.columnSetting': 'Display fields',
  },
};

const withTableToolbarLabels = (intl: IntlType, labels: Record<string, string>): IntlType => ({
  ...intl,
  getMessage: (id: string, defaultMessage?: string) => labels[id] || intl.getMessage(id, defaultMessage || id),
});

const resolveAntdLocale = () => (resolveRuntimeLocale().startsWith('en') ? enUS : zhCN);

const resolveProComponentsIntl = () => {
  const locale = resolveRuntimeLocale();
  const baseIntl = locale.startsWith('en') ? enUSIntl : zhCNIntl;
  const labels = locale.startsWith('en') ? TABLE_TOOLBAR_LABELS['en-US'] : TABLE_TOOLBAR_LABELS['zh-CN'];

  return withTableToolbarLabels(baseIntl, labels);
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
        <ProConfigProvider intl={resolveProComponentsIntl()}>
          <AntdApp>{children}</AntdApp>
        </ProConfigProvider>
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
