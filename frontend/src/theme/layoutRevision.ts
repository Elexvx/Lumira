import type { ThemePreference } from '@/theme/settings';

export const buildThemeRuntimeRevisionKey = (
  themePreference: ThemePreference,
  resolvedColorMode: 'light' | 'dark',
) => `${themePreference}:${resolvedColorMode}`;

export const shouldAdvanceThemeRevision = (
  previousThemeKey: string | undefined,
  nextThemeKey: string,
) => Boolean(previousThemeKey && previousThemeKey !== nextThemeKey);
