const APP_PREFIX = 'saas:portal';

const buildKey = (key: string, tenantId?: string) => {
  return tenantId ? `${APP_PREFIX}:${tenantId}:${key}` : `${APP_PREFIX}:${key}`;
};

export const storage = {
  get<T>(key: string, tenantId?: string): T | null {
    const raw = localStorage.getItem(buildKey(key, tenantId));
    return raw ? (JSON.parse(raw) as T) : null;
  },
  set(key: string, value: unknown, tenantId?: string) {
    localStorage.setItem(buildKey(key, tenantId), JSON.stringify(value));
  },
  remove(key: string, tenantId?: string) {
    localStorage.removeItem(buildKey(key, tenantId));
  },
  clearTenant(tenantId: string) {
    const prefix = `${APP_PREFIX}:${tenantId}:`;
    Object.keys(localStorage)
      .filter((key) => key.startsWith(prefix))
      .forEach((key) => localStorage.removeItem(key));
  },
};
