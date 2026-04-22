import type { ReactNode } from 'react';
import { ThemePreferenceProvider } from '@/theme/ThemePreferenceProvider';
import { getAppInitialState } from '@/app.bootstrap';
import { createLayoutConfig } from '@/app.layout';
import { AppWatermarkLayer } from '@/app.watermark';
import { syncAntdStaticThemeHolder } from '@/theme/antdTheme';

export type { AppInitialState } from '@/app.types';

export const getInitialState = getAppInitialState;

syncAntdStaticThemeHolder();

export const layout = createLayoutConfig;

export const rootContainer = (container: ReactNode) => (
  <ThemePreferenceProvider>
    <AppWatermarkLayer>{container}</AppWatermarkLayer>
  </ThemePreferenceProvider>
);
