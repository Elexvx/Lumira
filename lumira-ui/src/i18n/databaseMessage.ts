import { normalizeLocale, resolvePreferredLocale } from './locale';

type MessageValues = Record<string, string | number | boolean | null | undefined>;

const runtimeMessages = new Map<string, Record<string, string>>();
const CACHE_PREFIX = 'lumira:localization-runtime:';

const currentLocale = () => {
  if (typeof document !== 'undefined') {
    return normalizeLocale(document.documentElement.lang);
  }
  return resolvePreferredLocale();
};

const readPersistedMessages = (localeCode: string) => {
  if (typeof window === 'undefined') {
    return undefined;
  }
  try {
    const raw = window.localStorage.getItem(`${CACHE_PREFIX}${localeCode}`);
    if (!raw) {
      return undefined;
    }
    const parsed = JSON.parse(raw) as { messages?: Record<string, string> };
    return parsed.messages && typeof parsed.messages === 'object' ? parsed.messages : undefined;
  } catch {
    return undefined;
  }
};

export const installDatabaseMessages = (localeCode: string, messages: Record<string, string>) => {
  const locale = normalizeLocale(localeCode);
  runtimeMessages.set(locale, { ...messages });
  if (typeof window !== 'undefined') {
    try {
      window.localStorage.setItem(`${CACHE_PREFIX}${locale}`, JSON.stringify({ messages }));
    } catch {
      // Runtime messages remain available in memory when browser storage is unavailable.
    }
  }
};

export const clearDatabaseMessages = () => {
  runtimeMessages.clear();
};

export const databaseMessage = (
  id: string,
  values: MessageValues = {},
  fallback?: string | null,
) => {
  const locale = currentLocale();
  let messages = runtimeMessages.get(locale);
  if (!messages) {
    messages = readPersistedMessages(locale);
    if (messages) {
      runtimeMessages.set(locale, messages);
    }
  }
  const template = messages?.[id] || fallback || id;
  return template.replace(/\{([A-Za-z_][\w-]*)\}/g, (token, key: string) => {
    const value = values[key];
    return value === undefined || value === null ? token : String(value);
  });
};

export const hasDatabaseMessage = (id: string, localeCode = currentLocale()) => {
  const locale = normalizeLocale(localeCode);
  return Boolean(runtimeMessages.get(locale)?.[id] || readPersistedMessages(locale)?.[id]);
};
