import { useEffect, useRef } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useThemePreference } from '@/theme/ThemePreferenceProvider';
import { buildThemeRuntimeRevisionKey, shouldAdvanceThemeRevision } from '@/theme/layoutRevision';

export const ThemeRuntimeBridge = () => {
  const { themePreference, resolvedColorMode } = useThemePreference();
  const { setInitialState } = useInitialStateModel();
  const previousThemeKeyRef = useRef<string | undefined>(undefined);

  useEffect(() => {
    const nextThemeKey = buildThemeRuntimeRevisionKey(themePreference, resolvedColorMode);
    const shouldAdvanceRevision = shouldAdvanceThemeRevision(previousThemeKeyRef.current, nextThemeKey);
    previousThemeKeyRef.current = nextThemeKey;

    if (!shouldAdvanceRevision) {
      return;
    }

    setInitialState((prev) =>
      prev
        ? {
            ...prev,
            themeRevision: (prev.themeRevision ?? 0) + 1,
          }
        : prev,
    );
  }, [resolvedColorMode, setInitialState, themePreference]);

  return null;
};
