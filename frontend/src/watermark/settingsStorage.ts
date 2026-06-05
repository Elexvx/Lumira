import { storage } from '@/cache/storage';
import type { WatermarkSettings } from '@/types/api';
import { normalizeWatermarkSettings } from './settingsNormalize';
import { DEFAULT_WATERMARK_SETTINGS } from './settingsTypes';

const WATERMARK_SETTINGS_KEY = 'watermark_settings';

type Listener = () => void;

const listeners = new Set<Listener>();
let currentWatermarkSettings: WatermarkSettings = DEFAULT_WATERMARK_SETTINGS;

const emitChange = () => {
  listeners.forEach((listener) => listener());
};

export const getStoredWatermarkSettings = (): WatermarkSettings | null => storage.get<WatermarkSettings>(WATERMARK_SETTINGS_KEY);

export const getWatermarkSettingsSnapshot = () => currentWatermarkSettings;

export const persistWatermarkSettings = (settings: WatermarkSettings) => {
  currentWatermarkSettings = normalizeWatermarkSettings(settings);
  storage.set(WATERMARK_SETTINGS_KEY, currentWatermarkSettings);
  emitChange();
};

export const clearWatermarkSettings = () => {
  currentWatermarkSettings = DEFAULT_WATERMARK_SETTINGS;
  storage.remove(WATERMARK_SETTINGS_KEY);
  emitChange();
};

export const subscribeWatermarkSettings = (listener: Listener) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};

export const bootstrapWatermarkSettings = () => {
  const storedSettings = getStoredWatermarkSettings();
  currentWatermarkSettings = normalizeWatermarkSettings(storedSettings || DEFAULT_WATERMARK_SETTINGS);
};
