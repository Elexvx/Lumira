import type { WatermarkSettings } from '@/types/api';

export type { WatermarkSettings };

export const DEFAULT_WATERMARK_SETTINGS: WatermarkSettings = {
  enabled: false,
  mode: 'TEXT',
  textLines: ['宏翔商道', 'Admin system'],
  imageUrl: '',
  fontColor: 'rgba(0,0,0,0.15)',
  fontSize: 14,
  fontWeight: 'normal',
  rotate: -22,
  gapX: 100,
  gapY: 100,
  offsetX: 0,
  offsetY: 0,
  zIndex: 9,
  opacity: 0.15,
};
