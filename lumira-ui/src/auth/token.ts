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

const isBrowserRuntime = () => typeof window !== 'undefined';

const safeStorage = (storageName: 'sessionStorage' | 'localStorage'): Storage | null => {
  if (!isBrowserRuntime()) {
    return null;
  }
  try {
    return window[storageName];
  } catch {
    return null;
  }
};

const readStoredTokenState = (): AuthTokenState | null => {
  const candidates: Array<'localStorage' | 'sessionStorage'> = ['localStorage', 'sessionStorage'];
  for (const storageName of candidates) {
    const storage = safeStorage(storageName);
    const raw = storage?.getItem(TOKEN_STORAGE_KEY);
    if (!raw) {
      continue;
    }

    try {
      const parsed = JSON.parse(raw) as Partial<AuthTokenState>;
      if (!parsed.accessToken || !parsed.expiresAt || parsed.expiresAt <= Date.now()) {
        storage?.removeItem(TOKEN_STORAGE_KEY);
        continue;
      }
      return {
        accessToken: parsed.accessToken,
        tokenType: parsed.tokenType || 'Bearer',
        expiresIn: Number(parsed.expiresIn || 0),
        expiresAt: Number(parsed.expiresAt),
      };
    } catch {
      storage?.removeItem(TOKEN_STORAGE_KEY);
    }
  }

  return null;
};

let memoryTokenState: AuthTokenState | null = readStoredTokenState();

const getTokenState = (): AuthTokenState | null => memoryTokenState;

const tokenStatesMatch = (left: AuthTokenState | null, right: AuthTokenState | null) =>
  left?.accessToken === right?.accessToken &&
  left?.tokenType === right?.tokenType &&
  left?.expiresIn === right?.expiresIn &&
  left?.expiresAt === right?.expiresAt;

const syncTokenStateFromStorage = () => {
  const storedTokenState = readStoredTokenState();
  if (!tokenStatesMatch(memoryTokenState, storedTokenState)) {
    memoryTokenState = storedTokenState;
    authTokenGeneration += 1;
    bumpAuthSessionEpoch();
  }
  return Boolean(memoryTokenState?.accessToken);
};

const clearStoredTokenState = () => {
  safeStorage('sessionStorage')?.removeItem(TOKEN_STORAGE_KEY);
  safeStorage('localStorage')?.removeItem(TOKEN_STORAGE_KEY);
};

const writeTokenState = (state: AuthTokenState) => {
  memoryTokenState = state;
  clearStoredTokenState();
  safeStorage('localStorage')?.setItem(TOKEN_STORAGE_KEY, JSON.stringify(state));
};

const removeTokenState = () => {
  memoryTokenState = null;
  clearStoredTokenState();
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
  syncFromStorage: syncTokenStateFromStorage,
  setTokens: (payload: { accessToken: string; refreshToken?: string; tokenType?: string; expiresIn: number; remember?: boolean }) => {
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
