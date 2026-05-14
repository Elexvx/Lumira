const APP_PREFIX = 'saas:portal';

export const buildStorageKey = (key: string) => `${APP_PREFIX}:${key}`;

export const storage = {
  get<T>(key: string): T | null {
    const raw = localStorage.getItem(buildStorageKey(key));
    return raw ? (JSON.parse(raw) as T) : null;
  },
  set(key: string, value: unknown) {
    localStorage.setItem(buildStorageKey(key), JSON.stringify(value));
  },
  remove(key: string) {
    localStorage.removeItem(buildStorageKey(key));
  },
};
