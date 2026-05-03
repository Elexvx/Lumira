import type { ReactNode } from 'react';
import { useSyncExternalStore } from 'react';
import { Watermark } from 'antd';
import { DEFAULT_WATERMARK_SETTINGS, getWatermarkSettingsSnapshot, subscribeWatermarkSettings } from '@/watermark/settings';
import { normalizeUploadUrl } from '@/utils/uploadUrl';

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

export const AppWatermarkLayer = ({ children }: { children: ReactNode }) => {
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
        color: watermark.fontColor,
        fontSize: watermark.fontSize,
        fontWeight: normalizeWatermarkFontWeight(watermark.fontWeight),
      }}
    >
      {children}
    </Watermark>
  );
};
