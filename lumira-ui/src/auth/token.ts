import { bumpAuthSessionEpoch } from '@/auth/loginFlowState';

export const TOKEN_STORAGE_KEY = 'auth_tokens';
export const AUTH_SESSION_BROADCAST_CHANNEL = 'lumira-auth-session';
let authTokenGeneration = 0;

export interface AuthTokenState {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  expiresAt: number;
}

let memoryTokenState: AuthTokenState | null = null;

const getTokenState = (): AuthTokenState | null => memoryTokenState;

const writeTokenState = (state: AuthTokenState) => {
  memoryTokenState = state;
};

const removeTokenState = () => {
  memoryTokenState = null;
  try {
    if (typeof window !== 'undefined') {
      window.localStorage?.removeItem(TOKEN_STORAGE_KEY);
    }
  } catch {
    // Access tokens are memory-only; legacy persisted tokens are best-effort cleared.
  }
};

const broadcastAuthSession = (type: 'updated' | 'cleared') => {
  if (typeof BroadcastChannel === 'undefined') {
    return;
  }
  const channel = new BroadcastChannel(AUTH_SESSION_BROADCAST_CHANNEL);
  channel.postMessage({ type, generation: authTokenGeneration, occurredAt: Date.now() });
  channel.close();
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
    broadcastAuthSession('updated');
  },
  clearTokenState: () => {
    removeTokenState();
    authTokenGeneration += 1;
    bumpAuthSessionEpoch();
    broadcastAuthSession('cleared');
  },
};
