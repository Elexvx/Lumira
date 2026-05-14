import type { ReactNode } from 'react';
import { useLayoutEffect, useRef } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { ThemePreferenceProvider, useThemePreference } from '@/theme/ThemePreferenceProvider';
import { getAppInitialState } from '@/app.bootstrap';
import { createLayoutConfig } from '@/app.layout';
import { AppWatermarkLayer } from '@/app.watermark';
import type { AppInitialState } from '@/app.types';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { queryClient } from '@/query/queryClient';
import { syncAntdStaticThemeHolder } from '@/theme/antdTheme';

export type { AppInitialState } from '@/app.types';

export const getInitialState = getAppInitialState;

syncAntdStaticThemeHolder();

export const layout = createLayoutConfig;

const ThemeLayoutSync = ({ children }: { children: ReactNode }) => {
  const { resolvedColorMode, themePreference } = useThemePreference();
  const { setInitialState } = useInitialStateModel();
  const mountedRef = useRef(false);

  useLayoutEffect(() => {
    if (!mountedRef.current) {
      mountedRef.current = true;
      return;
    }

    setInitialState((prev: AppInitialState | undefined) =>
      prev
        ? {
            ...prev,
            themeRevision: (prev.themeRevision ?? 0) + 1,
          }
        : prev,
    );
  }, [resolvedColorMode, setInitialState, themePreference]);

  return <>{children}</>;
};

export const rootContainer = (container: ReactNode) => (
  <QueryClientProvider client={queryClient}>
    <ThemePreferenceProvider>
      <ThemeLayoutSync>
        <AppWatermarkLayer>{container}</AppWatermarkLayer>
      </ThemeLayoutSync>
    </ThemePreferenceProvider>
  </QueryClientProvider>
);
