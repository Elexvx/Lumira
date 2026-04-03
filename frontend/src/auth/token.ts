import { storage } from '@/cache/storage';

export const TOKEN_STORAGE_KEY = 'auth_tokens';

export interface AuthTokenState {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  expiresAt: number;
}

const getTokenState = (): AuthTokenState | null => storage.get<AuthTokenState>(TOKEN_STORAGE_KEY);

const setTokenState = (state: AuthTokenState) => storage.set(TOKEN_STORAGE_KEY, state);

const clearTokenState = () => storage.remove(TOKEN_STORAGE_KEY);

export const tokenManager = {
  getTokenState,
  setTokenState,
  clearTokenState,
  getAccessToken: () => getTokenState()?.accessToken ?? '',
  getRefreshToken: () => getTokenState()?.refreshToken ?? '',
  hasToken: () => Boolean(getTokenState()?.accessToken && getTokenState()?.refreshToken),
  setTokens: (payload: { accessToken: string; refreshToken: string; tokenType?: string; expiresIn: number }) => {
    const expiresAt = Date.now() + payload.expiresIn * 1000;
    setTokenState({
      accessToken: payload.accessToken,
      refreshToken: payload.refreshToken,
      tokenType: payload.tokenType || 'Bearer',
      expiresIn: payload.expiresIn,
      expiresAt,
    });
  },
};
