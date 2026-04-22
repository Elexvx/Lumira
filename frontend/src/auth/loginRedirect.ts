import { DEFAULT_HOME_PATH, LOGIN_PATH } from '@/app.constants';
import { buildStorageKey } from '@/cache/storage';
import { TOKEN_STORAGE_KEY } from '@/auth/token';

export const AUTH_TOKEN_STORAGE_KEY = buildStorageKey(TOKEN_STORAGE_KEY);

export const resolveLoginRedirectTarget = (search: string, fallback = DEFAULT_HOME_PATH) => {
  const redirect = new URLSearchParams(search).get('redirect')?.trim();
  if (!redirect || redirect === LOGIN_PATH || !redirect.startsWith('/')) {
    return fallback;
  }

  return redirect;
};

export const isAuthTokenStorageEvent = (event: Pick<StorageEvent, 'key' | 'newValue'>) =>
  event.key === AUTH_TOKEN_STORAGE_KEY && Boolean(event.newValue);

export const createLoginStorageHandler = (redirectTarget: string, onNavigate: (target: string) => void) => {
  return (event: Pick<StorageEvent, 'key' | 'newValue'>) => {
    if (!isAuthTokenStorageEvent(event)) {
      return;
    }

    onNavigate(redirectTarget);
  };
};
