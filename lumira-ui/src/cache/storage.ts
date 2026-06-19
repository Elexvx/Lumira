const APP_PREFIX = 'saas:platform';
const LEGACY_APP_PREFIX = 'saas:portal';

export const buildStorageKey = (key: string) => `${APP_PREFIX}:${key}`;
const buildLegacyStorageKey = (key: string) => `${LEGACY_APP_PREFIX}:${key}`;

const getLocalStorage = (): Storage | undefined => {
  if (typeof globalThis === 'undefined' || !('localStorage' in globalThis)) {
    return undefined;
  }
  return globalThis.localStorage;
};

export const storage = {
  get<T>(key: string): T | null {
    const localStorage = getLocalStorage();
    const raw = localStorage?.getItem(buildStorageKey(key)) ?? localStorage?.getItem(buildLegacyStorageKey(key));
    return raw ? (JSON.parse(raw) as T) : null;
  },
  set(key: string, value: unknown) {
    getLocalStorage()?.setItem(buildStorageKey(key), JSON.stringify(value));
  },
  remove(key: string) {
    const localStorage = getLocalStorage();
    localStorage?.removeItem(buildStorageKey(key));
    localStorage?.removeItem(buildLegacyStorageKey(key));
  },
};
