import { App as AntdApp, ConfigProvider, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import type { ReactNode } from 'react';
import { resolveRuntimeLocale } from '@/i18n/locale';
import { getThemeRuntimeSnapshot } from '@/theme/runtime';
import type { ThemePreference } from '@/theme/settings';
import { APP_SPACING } from '@/theme/spacing';

type AntdThemeConfig = NonNullable<Parameters<typeof ConfigProvider>[0]>['theme'];

interface BuildAntdThemeConfigOptions {
  themePreference?: ThemePreference;
  resolvedColorMode?: 'light' | 'dark';
  isMobile?: boolean;
}

type ResponsiveSpaceSize = NonNullable<NonNullable<Parameters<typeof ConfigProvider>[0]>['space']>;

export const resolveResponsiveSpaceSize = (isMobile: boolean): NonNullable<ResponsiveSpaceSize>['size'] =>
  isMobile ? 'small' : 'middle';

const baseThemeToken: NonNullable<AntdThemeConfig>['token'] = {
  colorPrimary: '#0f172a',
  colorInfo: '#0f172a',
  colorLink: '#2563eb',
  colorSuccess: '#16a34a',
  colorWarning: '#d97706',
  colorError: '#dc2626',
  colorBgLayout: '#ffffff',
  colorBgContainer: '#ffffff',
  colorBgElevated: '#ffffff',
  colorBgSpotlight: '#ffffff',
  colorBorder: '#e2e8f0',
  colorBorderSecondary: '#e2e8f0',
  colorFillQuaternary: '#f8fafc',
  colorFillTertiary: '#f8fafc',
  colorText: '#0f172a',
  colorTextSecondary: '#475569',
  colorTextTertiary: '#64748b',
  colorTextQuaternary: '#94a3b8',
  borderRadius: 12,
  borderRadiusLG: 16,
  borderRadiusSM: 10,
};

const buildGlobalSpacingToken = (isMobile: boolean): NonNullable<AntdThemeConfig>['token'] =>
  isMobile ? APP_SPACING.antdMobileTokens : APP_SPACING.antdDesktopTokens;

const resolveIsMobile = () => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false;
  }

  return window.matchMedia('(max-width: 767px)').matches;
};

const resolveCssVarKey = (themePreference: ThemePreference, resolvedColorMode: 'light' | 'dark') => {
  if (themePreference === 'compact') {
    return 'legendary-compact';
  }

  return `legendary-${resolvedColorMode}`;
};

const resolveAntdLocale = () => (resolveRuntimeLocale().startsWith('en') ? enUS : zhCN);

export const buildAntdThemeConfig = (options?: BuildAntdThemeConfigOptions): AntdThemeConfig => {
  const runtimeSnapshot = getThemeRuntimeSnapshot();
  const themePreference = options?.themePreference ?? runtimeSnapshot.themePreference;
  const resolvedColorMode = options?.resolvedColorMode ?? runtimeSnapshot.resolvedColorMode;
  const isMobile = options?.isMobile ?? resolveIsMobile();
  const globalSpacingToken = buildGlobalSpacingToken(isMobile);
  const cssVar = {
    key: resolveCssVarKey(themePreference, resolvedColorMode),
  };

  if (themePreference === 'compact') {
    return {
      cssVar,
      token: {
        ...baseThemeToken,
        ...globalSpacingToken,
      },
      algorithm: [antdTheme.defaultAlgorithm, antdTheme.compactAlgorithm],
    };
  }

  if (resolvedColorMode !== 'dark') {
    return {
      cssVar,
      token: {
        ...baseThemeToken,
        ...globalSpacingToken,
      },
      algorithm: [antdTheme.defaultAlgorithm],
    };
  }

  return {
    cssVar,
    algorithm: [antdTheme.darkAlgorithm],
    token: {
      ...globalSpacingToken,
      ...baseThemeToken,
      colorPrimary: '#f8fafc',
      colorInfo: '#f8fafc',
      colorLink: '#93c5fd',
      colorBgBase: '#09090b',
      colorBgLayout: '#09090b',
      colorBgContainer: '#111113',
      colorBgElevated: '#18181b',
      colorBgSpotlight: '#18181b',
      colorBorder: '#27272a',
      colorBorderSecondary: '#27272a',
      colorFillQuaternary: '#161618',
      colorFillTertiary: '#161618',
      colorText: '#f4f4f5',
      colorTextSecondary: '#d4d4d8',
      colorTextTertiary: '#a1a1aa',
      colorTextQuaternary: '#71717a',
    },
  };
};

export const syncAntdStaticThemeHolder = (options?: BuildAntdThemeConfigOptions) => {
  if (typeof window === 'undefined') {
    return;
  }

  const isMobile = options?.isMobile ?? resolveIsMobile();
  const themeConfig = buildAntdThemeConfig({
    themePreference: options?.themePreference,
    resolvedColorMode: options?.resolvedColorMode,
    isMobile,
  });
  ConfigProvider.config({
    holderRender: (children: ReactNode) => (
      <ConfigProvider
        locale={resolveAntdLocale()}
        theme={themeConfig}
        space={{ size: resolveResponsiveSpaceSize(isMobile) }}
      >
        <AntdApp>{children}</AntdApp>
      </ConfigProvider>
    ),
  });
};
