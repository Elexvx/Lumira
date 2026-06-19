import { storage } from '@/cache/storage';
import { bumpAuthSessionEpoch } from '@/auth/loginFlowState';

export const TOKEN_STORAGE_KEY = 'auth_tokens';
let authTokenGeneration = 0;

export interface AuthTokenState {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  expiresAt: number;
}

let memoryTokenState: AuthTokenState | null = null;

const getPersistedTokenMeta = (): AuthTokenState | null => {
  const state = storage.get<Partial<AuthTokenState> & { refreshToken?: string }>(TOKEN_STORAGE_KEY);
  if (!state?.accessToken) {
    return null;
  }
  return {
    accessToken: state.accessToken,
    tokenType: state.tokenType || 'Bearer',
    expiresIn: Number(state.expiresIn || 0),
    expiresAt: Number(state.expiresAt || 0),
  };
};

const getTokenState = (): AuthTokenState | null => memoryTokenState ?? getPersistedTokenMeta();

const writeTokenState = (state: AuthTokenState) => {
  memoryTokenState = state;
  storage.set(TOKEN_STORAGE_KEY, {
    accessToken: state.accessToken,
    tokenType: state.tokenType,
    expiresIn: state.expiresIn,
    expiresAt: state.expiresAt,
  });
};

const removeTokenState = () => {
  memoryTokenState = null;
  storage.remove(TOKEN_STORAGE_KEY);
};

export const tokenManager = {
  getTokenState,
  getAccessToken: () => getTokenState()?.accessToken ?? '',
  getRefreshToken: () => '',
  getTokenGeneration: () => authTokenGeneration,
  hasToken: () => Boolean(getTokenState()?.accessToken),
  setTokens: (payload: { accessToken: string; refreshToken?: string; tokenType?: string; expiresIn: number }) => {
    const expiresAt = Date.now() + payload.expiresIn * 1000;
    writeTokenState({
      accessToken: payload.accessToken,
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
