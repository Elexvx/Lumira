import { ConfigProvider, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import { resolveRuntimeLocale } from '@/i18n/locale';
import { getThemeRuntimeSnapshot } from '@/theme/runtime';
import { getResponsiveProfile, resolveViewportTier, type ViewportTier } from '@/theme/responsive';
import type { ThemePreference } from '@/theme/settings';
import { APP_SPACING } from '@/theme/spacing';

type AntdThemeConfig = NonNullable<Parameters<typeof ConfigProvider>[0]>['theme'];

interface BuildAntdThemeConfigOptions {
  themePreference?: ThemePreference;
  resolvedColorMode?: 'light' | 'dark';
  isMobile?: boolean;
  viewportTier?: ViewportTier;
}

type ResponsiveSpaceSize = NonNullable<NonNullable<Parameters<typeof ConfigProvider>[0]>['space']>;

export const resolveResponsiveSpaceSize = (isMobileOrTier: boolean | ViewportTier): NonNullable<ResponsiveSpaceSize>['size'] => {
  if (typeof isMobileOrTier === 'boolean') {
    return isMobileOrTier ? 'small' : 'middle';
  }

  return isMobileOrTier === 'mobile' ? 'small' : isMobileOrTier === 'wide' || isMobileOrTier === 'ultra' ? 'large' : 'middle';
};

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

const buildGlobalSpacingToken = (
  isMobile: boolean,
  viewportTier: ViewportTier,
  isCompact: boolean,
): NonNullable<AntdThemeConfig>['token'] => {
  const profile = getResponsiveProfile(viewportTier);
  const baseToken = isMobile ? APP_SPACING.antdMobileTokens : APP_SPACING.antdDesktopTokens;
  const compactOffset = isCompact ? 4 : 0;

  return {
    ...baseToken,
    controlHeight: Math.max(isMobile ? 40 : 32, profile.controlHeight - compactOffset),
    controlHeightSM: Math.max(isMobile ? 32 : 24, profile.controlHeightSM - compactOffset),
    controlHeightLG: Math.max(isMobile ? 44 : 40, profile.controlHeightLG - compactOffset),
    fontSize: Math.max(14, profile.bodyFontSize - (isCompact ? 1 : 0)),
    fontSizeSM: Math.max(12, profile.fontSizeSM - (isCompact ? 1 : 0)),
    fontSizeLG: Math.max(16, profile.fontSizeLG - (isCompact ? 1 : 0)),
    fontSizeXL: Math.max(18, profile.fontSizeXL - (isCompact ? 1 : 0)),
  };
};

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
  const viewportTier = options?.viewportTier ?? resolveViewportTier(typeof window === 'undefined' ? 1280 : window.innerWidth);
  const globalSpacingToken = buildGlobalSpacingToken(isMobile, viewportTier, themePreference === 'compact');
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
