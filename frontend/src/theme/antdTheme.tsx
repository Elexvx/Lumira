import { App as AntdApp, ConfigProvider, theme as antdTheme } from 'antd';
import type { ReactNode } from 'react';
import { resolveAntdLocale } from '@/i18n/antdLocale';
import { getThemeRuntimeSnapshot } from '@/theme/runtime';
import type { ThemePreference } from '@/theme/settings';

type AntdThemeConfig = NonNullable<Parameters<typeof ConfigProvider>[0]>['theme'];

interface BuildAntdThemeConfigOptions {
  themePreference?: ThemePreference;
  resolvedColorMode?: 'light' | 'dark';
}

const buildCssVarThemeKey = (themePreference: ThemePreference, resolvedColorMode: 'light' | 'dark') =>
  `saas-${themePreference}-${resolvedColorMode}`;

export const buildAntdThemeConfig = (options?: BuildAntdThemeConfigOptions): AntdThemeConfig => {
  const runtimeSnapshot = getThemeRuntimeSnapshot();
  const themePreference = options?.themePreference ?? runtimeSnapshot.themePreference;
  const resolvedColorMode = options?.resolvedColorMode ?? runtimeSnapshot.resolvedColorMode;
  const cssVarKey = buildCssVarThemeKey(themePreference, resolvedColorMode);

  if (themePreference === 'compact') {
    return {
      cssVar: {
        key: cssVarKey,
      },
      algorithm: [antdTheme.compactAlgorithm],
    };
  }

  if (resolvedColorMode !== 'dark') {
    return {
      cssVar: {
        key: cssVarKey,
      },
      algorithm: [antdTheme.defaultAlgorithm],
    };
  }

  return {
    cssVar: {
      key: cssVarKey,
    },
    algorithm: [antdTheme.darkAlgorithm],
    token: {
      colorBgBase: '#0f1115',
      colorBgLayout: '#0f1115',
      colorBgContainer: '#151515',
      colorBgElevated: '#1b1b1b',
      colorBorderSecondary: '#2a2a2a',
      colorText: '#f5f7fa',
    },
    components: {
      Layout: {
        bodyBg: '#0f1115',
        headerBg: '#111111',
        siderBg: '#0c0c0c',
      },
    },
  };
};

let staticThemeHolderConfigured = false;

export const syncAntdStaticThemeHolder = () => {
  if (staticThemeHolderConfigured || typeof window === 'undefined') {
    return;
  }

  staticThemeHolderConfigured = true;
  ConfigProvider.config({
    holderRender: (children: ReactNode) => (
      <ConfigProvider locale={resolveAntdLocale()} theme={buildAntdThemeConfig()}>
        <AntdApp>{children}</AntdApp>
      </ConfigProvider>
    ),
  });
};
