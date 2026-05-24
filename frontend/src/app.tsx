import type { ReactNode } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { ThemePreferenceProvider } from '@/theme/ThemePreferenceProvider';
import { getAppInitialState } from '@/app.bootstrap';
import { createLayoutConfig } from '@/app.layout';
import { AppWatermarkLayer } from '@/app.watermark';
import { queryClient } from '@/query/queryClient';
import { syncAntdStaticThemeHolder } from '@/theme/antdTheme';
import { ThemeRuntimeBridge } from '@/theme/ThemeRuntimeBridge';
import './global.css';

export type { AppInitialState } from '@/app.types';

export const getInitialState = getAppInitialState;

syncAntdStaticThemeHolder();

export const layout = createLayoutConfig;

export const rootContainer = (container: ReactNode) => (
  <QueryClientProvider client={queryClient}>
    <ThemePreferenceProvider>
      <ThemeRuntimeBridge />
      <AppWatermarkLayer>{container}</AppWatermarkLayer>
    </ThemePreferenceProvider>
  </QueryClientProvider>
);
