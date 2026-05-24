import type { ProSettings } from '@ant-design/pro-components';
import type { ThemeRuntimeSnapshot } from '@/theme/runtime';
import { resolveThemeRuntimeSnapshot } from '@/theme/runtime';

type ProLayoutThemeSettings = Pick<ProSettings, 'navTheme'>;

export const resolveProLayoutNavTheme = (
  snapshot: Pick<ThemeRuntimeSnapshot, 'resolvedColorMode'> = resolveThemeRuntimeSnapshot(),
): NonNullable<ProSettings['navTheme']> => (snapshot.resolvedColorMode === 'dark' ? 'realDark' : 'light');

export const resolveProLayoutThemeSettings = (
  snapshot: Pick<ThemeRuntimeSnapshot, 'resolvedColorMode'> = resolveThemeRuntimeSnapshot(),
): ProLayoutThemeSettings => ({
  navTheme: resolveProLayoutNavTheme(snapshot),
});
