import enUSMessages from '@/locales/en-US';
import zhCNMessages from '@/locales/zh-CN';
import { normalizeLocale, resolvePreferredLocale } from '@/i18n/locale';

const BUILTIN_MESSAGES: Record<string, Record<string, string>> = {
  'zh-CN': zhCNMessages,
  'en-US': enUSMessages,
};

const looksLikeMessageKey = (value?: string | null) => Boolean(value && /^[a-z][\w-]*(?:\.[\w-]+)+$/.test(value));

export const resolveBuiltinMessage = (id?: string | null, fallback?: string | null) => {
  const normalizedId = id?.trim();
  const normalizedFallback = fallback?.trim();
  if (!normalizedId) {
    return normalizedFallback || '';
  }

  const locale = typeof document !== 'undefined' ? normalizeLocale(document.documentElement.lang) : resolvePreferredLocale();
  const messages = BUILTIN_MESSAGES[locale] || BUILTIN_MESSAGES['zh-CN'];
  const translated = (messages as Record<string, string>)[normalizedId];
  if (translated) {
    return translated;
  }

  if (normalizedFallback && looksLikeMessageKey(normalizedFallback)) {
    const translatedFallback = (messages as Record<string, string>)[normalizedFallback];
    if (translatedFallback) {
      return translatedFallback;
    }
  }

  if (normalizedFallback && !looksLikeMessageKey(normalizedFallback)) {
    return normalizedFallback;
  }

  return normalizedFallback || normalizedId;
};
