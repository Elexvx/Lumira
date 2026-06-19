import { storage } from '@/cache/storage';
import { bumpAuthSessionEpoch } from '@/auth/loginFlowState';

export const TOKEN_STORAGE_KEY = 'auth_tokens';
let authTokenGeneration = 0;

export interface AuthTokenState {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  expiresAt: number;
}

const getTokenState = (): AuthTokenState | null => storage.get<AuthTokenState>(TOKEN_STORAGE_KEY);

const writeTokenState = (state: AuthTokenState) => storage.set(TOKEN_STORAGE_KEY, state);

const removeTokenState = () => storage.remove(TOKEN_STORAGE_KEY);

export const tokenManager = {
  getTokenState,
  getAccessToken: () => getTokenState()?.accessToken ?? '',
  getRefreshToken: () => getTokenState()?.refreshToken ?? '',
  getTokenGeneration: () => authTokenGeneration,
  hasToken: () => Boolean(getTokenState()?.accessToken && getTokenState()?.refreshToken),
  setTokens: (payload: { accessToken: string; refreshToken: string; tokenType?: string; expiresIn: number }) => {
    const expiresAt = Date.now() + payload.expiresIn * 1000;
    writeTokenState({
      accessToken: payload.accessToken,
      refreshToken: payload.refreshToken,
      tokenType: payload.tokenType || 'Bearer',
      expiresIn: payload.expiresIn,
      expiresAt,
    });
    authTokenGeneration += 1;
    bumpAuthSessionEpoch();
  },
  clearTokenState: () => {
    removeTokenState();
    authTokenGeneration += 1;
    bumpAuthSessionEpoch();
  },
};
