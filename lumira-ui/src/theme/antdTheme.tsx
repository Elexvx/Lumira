import { ConfigProvider, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
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
  colorInfo: '#1677ff',
  colorLink: '#1677ff',
  colorSuccess: '#16a34a',
  colorWarning: '#d97706',
  colorError: '#dc2626',
  borderRadius: 6,
  borderRadiusLG: 8,
  borderRadiusSM: 4,
};

const buildGlobalSpacingToken = (isMobile: boolean): NonNullable<AntdThemeConfig>['token'] =>
  isMobile ? APP_SPACING.antdMobileTokens : APP_SPACING.antdDesktopTokens;

const lightThemeToken: NonNullable<AntdThemeConfig>['token'] = {
  colorBgBase: '#ffffff',
  colorBgContainer: '#ffffff',
  colorBgElevated: '#ffffff',
  colorBgLayout: '#f5f5f5',
  colorTextBase: '#000000',
  colorText: 'rgba(0, 0, 0, 0.88)',
  colorBorder: '#d9d9d9',
};

const darkThemeToken: NonNullable<AntdThemeConfig>['token'] = {
  colorBgBase: '#000000',
  colorBgContainer: '#141414',
  colorBgElevated: '#1b1b1b',
  colorBgLayout: '#141414',
  colorTextBase: '#ffffff',
  colorText: 'rgba(255, 255, 255, 0.85)',
  colorBorder: '#434343',
  colorBorderSecondary: '#303030',
};

const resolveIsMobile = () => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false;
  }

  return window.matchMedia('(max-width: 767px)').matches;
};

const resolveCssVarKey = (themePreference: ThemePreference, resolvedColorMode: 'light' | 'dark') => {
  if (themePreference === 'compact') {
    return 'lumira-compact';
  }

  return `lumira-${resolvedColorMode}`;
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
        ...lightThemeToken,
        ...globalSpacingToken,
      },
      algorithm: [antdTheme.defaultAlgorithm, antdTheme.compactAlgorithm],
    };
  }

  if (resolvedColorMode !== 'dark') {
    return {
      cssVar,
      token: {
        ...lightThemeToken,
        ...globalSpacingToken,
        ...baseThemeToken,
      },
      algorithm: [antdTheme.defaultAlgorithm],
    };
  }

  return {
    cssVar,
    algorithm: [antdTheme.darkAlgorithm],
    token: {
      ...darkThemeToken,
      ...globalSpacingToken,
      ...baseThemeToken,
    },
  };
};

export { resolveAntdLocale };
