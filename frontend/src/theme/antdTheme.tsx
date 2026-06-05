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
  colorPrimary: '#1677ff',
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
  const isMobile = resolveIsMobile();
  ConfigProvider.config({
    holderRender: (children: ReactNode) => (
      <ConfigProvider
        locale={resolveAntdLocale()}
        theme={buildAntdThemeConfig({ isMobile })}
        space={{ size: resolveResponsiveSpaceSize(isMobile) }}
      >
        <AntdApp>{children}</AntdApp>
      </ConfigProvider>
    ),
  });
};
