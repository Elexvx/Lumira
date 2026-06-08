import { normalizeUploadUrl } from '@/utils/uploadUrl';
import type { FloatingWindowSettings } from '@/types/api';
import { resolvePreferredLocale } from '@/i18n/locale';

// Avoid calling Umi runtime locale APIs during module evaluation.
const isEnglishLocale = () => resolvePreferredLocale().startsWith('en');
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

export const DEFAULT_FLOATING_WINDOW_SETTINGS: FloatingWindowSettings = {
  apiDocsQrEnabled: true,
  apiDocsQrTitle: t('微信扫码联系我们', 'Scan the QR code on WeChat to contact us'),
  apiDocsQrImageUrl: '',
};

export const normalizeFloatingWindowSettings = (settings?: Partial<FloatingWindowSettings> | null): FloatingWindowSettings => ({
  ...DEFAULT_FLOATING_WINDOW_SETTINGS,
  ...settings,
  apiDocsQrEnabled: settings?.apiDocsQrEnabled ?? DEFAULT_FLOATING_WINDOW_SETTINGS.apiDocsQrEnabled,
  apiDocsQrTitle: settings?.apiDocsQrTitle?.trim() || DEFAULT_FLOATING_WINDOW_SETTINGS.apiDocsQrTitle,
  apiDocsQrImageUrl: normalizeUploadUrl(settings?.apiDocsQrImageUrl),
});
