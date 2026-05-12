import { getLocale, setLocale } from '@umijs/max';

export const BUILTIN_LOCALES = ['zh-CN', 'en-US'] as const;

export type AppLocale = (typeof BUILTIN_LOCALES)[number] | (string & {});

export const DEFAULT_APP_LOCALE: AppLocale = 'zh-CN';

const LOCALE_ALIASES: Record<string, AppLocale> = {
  zh: 'zh-CN',
  'zh-CN': 'zh-CN',
  'zh-cn': 'zh-CN',
  en: 'en-US',
  'en-US': 'en-US',
  'en-us': 'en-US',
};

const isWellFormedLocale = (value: string) => /^[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$/.test(value);

export const normalizeLocale = (value?: string | null): AppLocale => {
  if (!value) {
    return DEFAULT_APP_LOCALE;
  }

  const trimmed = value.trim();
  if (!trimmed) {
    return DEFAULT_APP_LOCALE;
  }

  const aliased = LOCALE_ALIASES[trimmed] || LOCALE_ALIASES[trimmed.toLowerCase()];
  if (aliased) {
    return aliased;
  }

  if (isWellFormedLocale(trimmed)) {
    return trimmed;
  }

  return DEFAULT_APP_LOCALE;
};

export const resolveBrowserLocale = (): AppLocale => {
  if (typeof navigator === 'undefined' || typeof navigator.language !== 'string') {
    return DEFAULT_APP_LOCALE;
  }

  return normalizeLocale(navigator.language);
};

export const resolvePreferredLocale = (value?: string | null): AppLocale => {
  const normalized = normalizeLocale(value);
  if (normalized !== DEFAULT_APP_LOCALE || value?.trim()) {
    return normalized;
  }

  if (typeof window !== 'undefined') {
    const storedLocale = window.localStorage?.getItem('umi_locale');
    if (storedLocale) {
      return normalizeLocale(storedLocale);
    }
  }

  return DEFAULT_APP_LOCALE;
};

export const resolveRuntimeLocale = (): AppLocale => {
  const runtimeLocale = normalizeLocale(getLocale());
  if (runtimeLocale) {
    return runtimeLocale;
  }

  return resolveBrowserLocale();
};

export const applyLocalePreference = (value?: string | null, reload = false) => {
  const locale = resolvePreferredLocale(value);
  if (getLocale() !== locale) {
    setLocale(locale, reload);
  }

  return locale;
};

export const getLocaleDisplayName = (value?: string | null) => {
  const locale = normalizeLocale(value);
  if (locale === 'en-US') {
    return 'English';
  }
  if (locale === 'zh-CN') {
    return '中文';
  }

  return locale;
};

export const createLocalePreferenceBootstrapScript = () => {
  const defaultLocale = JSON.stringify(DEFAULT_APP_LOCALE);
  return `(function(){try{var locale=${defaultLocale};var stored=localStorage.getItem('umi_locale');if(stored){locale=stored;}if(!/^[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$/.test(locale)){locale=${defaultLocale};}document.documentElement.lang=locale;document.documentElement.dataset.locale=locale;}catch(_error){}})();`;
};
