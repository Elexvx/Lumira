import type { WatermarkSettings } from '@/types/api';
import { normalizeWatermarkSettings } from './settingsNormalize';
import { DEFAULT_WATERMARK_SETTINGS } from './settingsTypes';

type Listener = () => void;

const listeners = new Set<Listener>();
let currentWatermarkSettings: WatermarkSettings = DEFAULT_WATERMARK_SETTINGS;

const emitChange = () => {
  listeners.forEach((listener) => listener());
};

// Runtime-only snapshot. The durable watermark configuration is loaded from
// and saved to the backend database.
export const getStoredWatermarkSettings = (): WatermarkSettings | null => currentWatermarkSettings;

export const getWatermarkSettingsSnapshot = () => currentWatermarkSettings;

export const persistWatermarkSettings = (settings: WatermarkSettings) => {
  currentWatermarkSettings = normalizeWatermarkSettings(settings);
  emitChange();
};

export const clearWatermarkSettings = () => {
  currentWatermarkSettings = DEFAULT_WATERMARK_SETTINGS;
  emitChange();
};

export const subscribeWatermarkSettings = (listener: Listener) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};

export const bootstrapWatermarkSettings = () => {
  currentWatermarkSettings = normalizeWatermarkSettings(currentWatermarkSettings);
};
