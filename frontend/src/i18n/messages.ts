import { getLocale } from '@umijs/max';
import enUSMessages from '@/locales/en-US';
import zhCNMessages from '@/locales/zh-CN';
import { normalizeLocale } from '@/i18n/locale';

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

  const locale = normalizeLocale(typeof getLocale === 'function' ? getLocale() : 'zh-CN');
  const messages = BUILTIN_MESSAGES[locale] || BUILTIN_MESSAGES['zh-CN'];
  const translated = messages[normalizedId];
  if (translated) {
    return translated;
  }

  if (normalizedFallback && looksLikeMessageKey(normalizedFallback)) {
    const translatedFallback = messages[normalizedFallback];
    if (translatedFallback) {
      return translatedFallback;
    }
  }

  if (normalizedFallback && !looksLikeMessageKey(normalizedFallback)) {
    return normalizedFallback;
  }

  return normalizedFallback || normalizedId;
};
