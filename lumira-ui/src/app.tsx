import type { ReactNode } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { Watermark } from 'antd';
import { useEffect, useSyncExternalStore } from 'react';
import { ThemePreferenceProvider } from '@/theme/ThemePreferenceProvider';
import { getAppInitialState } from '@/app.bootstrap';
import { createLayoutConfig } from '@/app.layout';
import { queryClient } from '@/query/queryClient';
import { DEFAULT_WATERMARK_SETTINGS } from '@/watermark/settingsTypes';
import { getWatermarkSettingsSnapshot, subscribeWatermarkSettings } from '@/watermark/settingsStorage';
import { applyWatermarkOpacity } from '@/watermark/color';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { MaintenanceModeGate } from '@/maintenance/MaintenanceModeGate';
import { startFrontendVersionPolling, startPublicBrandingPolling } from '@/app.runtime';
import './global.css';

export type { AppInitialState } from '@/app.types';

export const getInitialState = getAppInitialState;

const normalizeWatermarkFontWeight = (value: string): number | 'normal' | 'bold' | 'lighter' | 'bolder' | undefined => {
  const numeric = Number(value);
  if (Number.isFinite(numeric)) {
    return numeric;
  }
  if (value === 'light') {
    return 'lighter';
  }
  if (value === 'weight') {
    return 'bold';
  }
  return ['normal', 'bold', 'lighter', 'bolder'].includes(value) ? (value as 'normal' | 'bold' | 'lighter' | 'bolder') : undefined;
};

const AppWatermarkLayer = ({ children }: { children: ReactNode }) => {
  const watermark = useSyncExternalStore(
    subscribeWatermarkSettings,
    getWatermarkSettingsSnapshot,
    () => DEFAULT_WATERMARK_SETTINGS,
  );

  if (!watermark.enabled) {
    return <>{children}</>;
  }

  return (
    <Watermark
      zIndex={watermark.zIndex}
      rotate={watermark.rotate}
      gap={[watermark.gapX, watermark.gapY]}
      offset={[watermark.offsetX, watermark.offsetY]}
      content={watermark.mode === 'TEXT' ? watermark.textLines : undefined}
      image={watermark.mode === 'IMAGE' ? normalizeUploadUrl(watermark.imageUrl) : undefined}
      font={{
        color: applyWatermarkOpacity(watermark.fontColor, watermark.opacity),
        fontSize: watermark.fontSize,
        fontWeight: normalizeWatermarkFontWeight(watermark.fontWeight),
      }}
    >
      {children}
    </Watermark>
  );
};

const RuntimeRefreshGuard = () => {
  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }

    const stopVersionPolling = startFrontendVersionPolling();
    const stopBrandingPolling = startPublicBrandingPolling();

    return () => {
      stopVersionPolling();
      stopBrandingPolling();
    };
  }, []);

  return null;
};

export const layout = createLayoutConfig;

export const rootContainer = (container: ReactNode) => (
  <QueryClientProvider client={queryClient}>
    <RuntimeRefreshGuard />
    <ThemePreferenceProvider>
      <MaintenanceModeGate>
        <AppWatermarkLayer>
          {container}
        </AppWatermarkLayer>
      </MaintenanceModeGate>
    </ThemePreferenceProvider>
  </QueryClientProvider>
);
