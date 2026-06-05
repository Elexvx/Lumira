import type { WatermarkSettings } from '@/types/api';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { DEFAULT_WATERMARK_SETTINGS } from './settingsTypes';

export const normalizeWatermarkSettings = (settings?: Partial<WatermarkSettings> | null): WatermarkSettings => ({
  enabled: normalizeBoolean(settings?.enabled, DEFAULT_WATERMARK_SETTINGS.enabled),
  mode: settings?.mode === 'IMAGE' ? 'IMAGE' : DEFAULT_WATERMARK_SETTINGS.mode,
  textLines: normalizeTextLines(settings?.textLines, DEFAULT_WATERMARK_SETTINGS.textLines),
  imageUrl: normalizeUploadUrl(settings?.imageUrl),
  fontColor: normalizeText(settings?.fontColor, DEFAULT_WATERMARK_SETTINGS.fontColor),
  fontSize: normalizePositiveNumber(settings?.fontSize, DEFAULT_WATERMARK_SETTINGS.fontSize),
  fontWeight: normalizeText(settings?.fontWeight, DEFAULT_WATERMARK_SETTINGS.fontWeight),
  rotate: normalizeNumber(settings?.rotate, DEFAULT_WATERMARK_SETTINGS.rotate),
  gapX: normalizePositiveNumber(settings?.gapX, DEFAULT_WATERMARK_SETTINGS.gapX),
  gapY: normalizePositiveNumber(settings?.gapY, DEFAULT_WATERMARK_SETTINGS.gapY),
  offsetX: normalizeNumber(settings?.offsetX, DEFAULT_WATERMARK_SETTINGS.offsetX),
  offsetY: normalizeNumber(settings?.offsetY, DEFAULT_WATERMARK_SETTINGS.offsetY),
  zIndex: normalizePositiveNumber(settings?.zIndex, DEFAULT_WATERMARK_SETTINGS.zIndex),
  opacity: normalizeOpacity(settings?.opacity, DEFAULT_WATERMARK_SETTINGS.opacity),
});

const normalizeBoolean = (value: unknown, fallback: boolean) => {
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'number') {
    return value === 1;
  }
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (['true', '1', 'yes', 'on'].includes(normalized)) {
      return true;
    }
    if (['false', '0', 'no', 'off'].includes(normalized)) {
      return false;
    }
  }
  return fallback;
};

const normalizeNumber = (value: unknown, fallback: number) => {
  const numeric = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(numeric) ? numeric : fallback;
};

const normalizePositiveNumber = (value: unknown, fallback: number) => {
  const numeric = normalizeNumber(value, fallback);
  return numeric > 0 ? Math.floor(numeric) : fallback;
};

const normalizeOpacity = (value: unknown, fallback: number) => {
  const numeric = normalizeNumber(value, fallback);
  return Number.isFinite(numeric) ? Math.max(0, Math.min(1, numeric)) : fallback;
};

const normalizeText = (value: unknown, fallback: string) => {
  if (typeof value !== 'string') {
    return fallback;
  }
  const trimmed = value.trim();
  return trimmed || fallback;
};

const normalizeTextLines = (value: unknown, fallback: string[]) => {
  if (!Array.isArray(value)) {
    return fallback;
  }
  const lines = value
    .map((line) => (typeof line === 'string' ? line.trim() : ''))
    .filter(Boolean);
  return lines.length ? lines : fallback;
};
