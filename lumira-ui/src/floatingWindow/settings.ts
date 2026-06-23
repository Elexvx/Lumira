import { normalizeUploadUrl } from '@/utils/uploadUrl';
import type { FloatingWindowSettings } from '@/types/api';

export const DEFAULT_FLOATING_WINDOW_SETTINGS: FloatingWindowSettings = {
  apiDocsQrEnabled: false,
  apiDocsQrTitle: '',
  apiDocsQrImageUrl: '',
};

export const normalizeFloatingWindowSettings = (settings?: Partial<FloatingWindowSettings> | null): FloatingWindowSettings => ({
  ...DEFAULT_FLOATING_WINDOW_SETTINGS,
  ...settings,
  apiDocsQrEnabled: settings?.apiDocsQrEnabled ?? DEFAULT_FLOATING_WINDOW_SETTINGS.apiDocsQrEnabled,
  apiDocsQrTitle: settings?.apiDocsQrTitle?.trim() ?? DEFAULT_FLOATING_WINDOW_SETTINGS.apiDocsQrTitle,
  apiDocsQrImageUrl: normalizeUploadUrl(settings?.apiDocsQrImageUrl),
});
