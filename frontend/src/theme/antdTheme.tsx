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

const baseThemeToken: NonNullable<AntdThemeConfig>['token'] = {
  colorPrimary: '#1677ff',
};

export const buildAntdThemeConfig = (options?: BuildAntdThemeConfigOptions): AntdThemeConfig => {
  const runtimeSnapshot = getThemeRuntimeSnapshot();
  const themePreference = options?.themePreference ?? runtimeSnapshot.themePreference;
  const resolvedColorMode = options?.resolvedColorMode ?? runtimeSnapshot.resolvedColorMode;

  if (themePreference === 'compact') {
    return {
      cssVar: {},
      token: baseThemeToken,
      algorithm: [antdTheme.defaultAlgorithm, antdTheme.compactAlgorithm],
    };
  }

  if (resolvedColorMode !== 'dark') {
    return {
      cssVar: {},
      token: baseThemeToken,
      algorithm: [antdTheme.defaultAlgorithm],
    };
  }

  return {
    cssVar: {},
    algorithm: [antdTheme.darkAlgorithm],
    token: {
      ...baseThemeToken,
      colorBgBase: '#0f1115',
      colorBgLayout: '#0f1115',
      colorBgContainer: '#151515',
      colorBgElevated: '#1b1b1b',
      colorBorderSecondary: '#2a2a2a',
      colorText: '#f5f7fa',
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
