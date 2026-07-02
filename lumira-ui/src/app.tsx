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

const FRONTEND_VERSION_STORAGE_KEY = 'lumira:frontend-version';

const FrontendVersionGuard = () => {
  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }

    const abortController = new AbortController();

    const checkVersion = async () => {
      try {
        const response = await fetch(`/__version.json?_t=${Date.now()}`, {
          cache: 'no-store',
          signal: abortController.signal,
        });
        if (!response.ok) {
          return;
        }
        const payload = await response.json();
        const nextVersion = typeof payload?.commit === 'string' ? payload.commit : payload?.short;
        if (!nextVersion) {
          return;
        }
        const currentVersion = window.localStorage.getItem(FRONTEND_VERSION_STORAGE_KEY);
        window.localStorage.setItem(FRONTEND_VERSION_STORAGE_KEY, nextVersion);
        if (currentVersion && currentVersion !== nextVersion) {
          const nextUrl = new URL(window.location.href);
          nextUrl.searchParams.set('_v', String(payload.short || nextVersion).slice(0, 12));
          window.location.replace(nextUrl.toString());
        }
      } catch (error) {
        if ((error as { name?: string }).name !== 'AbortError') {
          window.setTimeout(() => void checkVersion(), 30000);
        }
      }
    };

    void checkVersion();

    return () => {
      abortController.abort();
    };
  }, []);

  return null;
};

export const layout = createLayoutConfig;

export const rootContainer = (container: ReactNode) => (
  <QueryClientProvider client={queryClient}>
    <FrontendVersionGuard />
    <ThemePreferenceProvider>
      <AppWatermarkLayer>
        {container}
      </AppWatermarkLayer>
    </ThemePreferenceProvider>
  </QueryClientProvider>
);
