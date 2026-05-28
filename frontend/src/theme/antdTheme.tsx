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

const buildComponentTokens = (mode: 'light' | 'dark'): NonNullable<AntdThemeConfig>['components'] => {
  const isDark = mode === 'dark';
  const colorBgContainer = isDark ? '#151515' : '#ffffff';
  const colorBgElevated = isDark ? '#1b1b1b' : '#ffffff';
  const colorBgLayout = isDark ? '#0f1115' : '#f5f5f5';
  const colorText = isDark ? 'rgba(255, 255, 255, 0.88)' : 'rgba(0, 0, 0, 0.88)';
  const colorTextSecondary = isDark ? 'rgba(255, 255, 255, 0.65)' : 'rgba(0, 0, 0, 0.65)';
  const colorTextDisabled = isDark ? 'rgba(255, 255, 255, 0.32)' : 'rgba(0, 0, 0, 0.32)';
  const colorBorder = isDark ? 'rgba(255, 255, 255, 0.16)' : 'rgba(0, 0, 0, 0.08)';
  const colorFillSecondary = isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.04)';
  const colorFillTertiary = isDark ? 'rgba(255, 255, 255, 0.12)' : 'rgba(0, 0, 0, 0.06)';

  return {
    Button: {
      defaultBg: colorBgContainer,
      defaultBorderColor: colorBorder,
      defaultColor: colorText,
      defaultHoverBg: colorFillSecondary,
      defaultHoverBorderColor: '#1677ff',
      defaultHoverColor: '#1677ff',
    },
    Card: {
      colorBgContainer,
      colorBorderSecondary: colorBorder,
      colorText,
    },
    Empty: {
      colorTextDescription: colorTextSecondary,
    },
    Form: {
      labelColor: colorText,
    },
    Input: {
      colorBgContainer,
      colorBgContainerDisabled: colorFillSecondary,
      colorBorder,
      colorText,
      colorTextDisabled,
    },
    InputNumber: {
      colorBgContainer,
      colorBgContainerDisabled: colorFillSecondary,
      colorBorder,
      colorText,
      colorTextDisabled,
    },
    Layout: {
      bodyBg: colorBgLayout,
      headerBg: isDark ? '#111111' : '#ffffff',
      siderBg: isDark ? '#0c0c0c' : '#ffffff',
    },
    Select: {
      colorBgContainer,
      colorBgContainerDisabled: colorFillSecondary,
      colorBorder,
      colorText,
      colorTextDisabled,
    },
    Switch: {
      colorPrimary: '#1677ff',
      colorPrimaryHover: '#4096ff',
      colorTextQuaternary: colorFillSecondary,
      colorTextTertiary: colorFillTertiary,
    },
    Table: {
      borderColor: colorBorder,
      colorBgContainer,
      colorText,
      headerBg: colorBgContainer,
      headerColor: colorText,
      rowHoverBg: colorFillSecondary,
    },
    Tabs: {
      colorText,
      itemColor: colorTextSecondary,
      itemSelectedColor: '#1677ff',
      itemHoverColor: '#1677ff',
    },
    Typography: {
      colorText,
      colorTextDescription: colorTextSecondary,
    },
  };
};

export const buildAntdThemeConfig = (options?: BuildAntdThemeConfigOptions): AntdThemeConfig => {
  const runtimeSnapshot = getThemeRuntimeSnapshot();
  const themePreference = options?.themePreference ?? runtimeSnapshot.themePreference;
  const resolvedColorMode = options?.resolvedColorMode ?? runtimeSnapshot.resolvedColorMode;

  if (themePreference === 'compact') {
    return {
      cssVar: {},
      algorithm: [antdTheme.compactAlgorithm],
      components: buildComponentTokens('light'),
    };
  }

  if (resolvedColorMode !== 'dark') {
    return {
      cssVar: {},
      algorithm: [antdTheme.defaultAlgorithm],
      components: buildComponentTokens('light'),
    };
  }

  return {
    cssVar: {},
    algorithm: [antdTheme.darkAlgorithm],
    token: {
      colorBgBase: '#0f1115',
      colorBgLayout: '#0f1115',
      colorBgContainer: '#151515',
      colorBgElevated: '#1b1b1b',
      colorBorderSecondary: '#2a2a2a',
      colorText: '#f5f7fa',
    },
    components: buildComponentTokens('dark'),
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
