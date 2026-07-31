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

interface StoredTokenReadResult {
  available: boolean;
  state: AuthTokenState | null;
}

interface StoredAccessTokenObservation {
  available: boolean;
  accessToken: string | null;
}

const readStorageItem = (storageName: 'sessionStorage' | 'localStorage') => {
  const storage = safeStorage(storageName);
  if (!storage) {
    return { available: false, raw: null, storage: null };
  }
  try {
    return { available: true, raw: storage.getItem(TOKEN_STORAGE_KEY), storage };
  } catch {
    return { available: false, raw: null, storage };
  }
};

const removeStoredItem = (storage: Storage | null) => {
  try {
    storage?.removeItem(TOKEN_STORAGE_KEY);
  } catch {
    // Storage cleanup is best-effort. In-memory auth state remains authoritative.
  }
};

const writeStoredItem = (storage: Storage | null, state: AuthTokenState) => {
  try {
    storage?.setItem(TOKEN_STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Memory state still advances. A later hard navigation can recover from
    // the shared HttpOnly refresh cookie when persistence is unavailable.
  }
};

const readStoredTokenState = (): StoredTokenReadResult => {
  const candidates: Array<'localStorage' | 'sessionStorage'> = ['localStorage', 'sessionStorage'];
  for (const storageName of candidates) {
    const { available, raw, storage } = readStorageItem(storageName);
    if (!available) {
      return { available: false, state: null };
    }
    if (!raw) {
      continue;
    }

    try {
      const parsed = JSON.parse(raw) as Partial<AuthTokenState>;
      if (!parsed.accessToken || !parsed.expiresAt || parsed.expiresAt <= Date.now()) {
        removeStoredItem(storage);
        continue;
      }
      return {
        available: true,
        state: {
          accessToken: parsed.accessToken,
          tokenType: parsed.tokenType || 'Bearer',
          expiresIn: Number(parsed.expiresIn || 0),
          expiresAt: Number(parsed.expiresAt),
        },
      };
    } catch {
      removeStoredItem(storage);
    }
  }

  return { available: true, state: null };
};

const observeStoredAccessToken = (): StoredAccessTokenObservation => {
  const candidates: Array<'localStorage' | 'sessionStorage'> = ['localStorage', 'sessionStorage'];
  for (const storageName of candidates) {
    const { available, raw } = readStorageItem(storageName);
    if (!available) {
      return { available: false, accessToken: null };
    }
    if (!raw) {
      continue;
    }
    try {
      const parsed = JSON.parse(raw) as Partial<AuthTokenState>;
      if (parsed.accessToken) {
        return { available: true, accessToken: parsed.accessToken };
      }
    } catch {
      // The normal storage reader will discard malformed state when needed.
    }
  }
  return { available: true, accessToken: null };
};

let memoryTokenState: AuthTokenState | null = readStoredTokenState().state;

const getTokenState = (): AuthTokenState | null => memoryTokenState;

const tokenStatesMatch = (left: AuthTokenState | null, right: AuthTokenState | null) =>
  left?.accessToken === right?.accessToken &&
  left?.tokenType === right?.tokenType &&
  left?.expiresIn === right?.expiresIn &&
  left?.expiresAt === right?.expiresAt;

const syncTokenStateFromStorage = (expectedAccessToken?: string) => {
  if (expectedAccessToken !== undefined) {
    const storedToken = observeStoredAccessToken();
    if (!storedToken.available || storedToken.accessToken === expectedAccessToken) {
      return Boolean(memoryTokenState?.accessToken);
    }
  }
  const storedToken = readStoredTokenState();
  if (!storedToken.available) {
    return Boolean(memoryTokenState?.accessToken);
  }
  if (!tokenStatesMatch(memoryTokenState, storedToken.state)) {
    memoryTokenState = storedToken.state;
    authTokenGeneration += 1;
    bumpAuthSessionEpoch();
  }
  return Boolean(memoryTokenState?.accessToken);
};

const clearStoredTokenState = () => {
  removeStoredItem(safeStorage('sessionStorage'));
  removeStoredItem(safeStorage('localStorage'));
};

const writeTokenState = (state: AuthTokenState) => {
  memoryTokenState = state;
  clearStoredTokenState();
  writeStoredItem(safeStorage('localStorage'), state);
};

const removeTokenState = () => {
  memoryTokenState = null;
  clearStoredTokenState();
};

const broadcastAuthSession = (type: 'updated' | 'cleared') => {
  if (typeof BroadcastChannel === 'undefined') {
    return;
  }
  try {
    const channel = new BroadcastChannel(AUTH_SESSION_BROADCAST_CHANNEL);
    channel.postMessage({ type, generation: authTokenGeneration, occurredAt: Date.now() });
    channel.close();
  } catch {
    // Cross-tab notification is best-effort and must not roll back memory auth.
  }
};

export const tokenManager = {
  getTokenState,
  getAccessToken: () => getTokenState()?.accessToken ?? '',
  getRefreshToken: () => '',
  getTokenGeneration: () => authTokenGeneration,
  hasToken: () => Boolean(getTokenState()?.accessToken),
  syncFromStorage: syncTokenStateFromStorage,
  setTokens: (
    payload: { accessToken: string; refreshToken?: string; tokenType?: string; expiresIn: number; remember?: boolean },
    options: { broadcast?: boolean } = {},
  ) => {
    const expiresAt = Date.now() + payload.expiresIn * 1000;
    writeTokenState({
      accessToken: payload.accessToken,
      tokenType: payload.tokenType || 'Bearer',
      expiresIn: payload.expiresIn,
      expiresAt,
    });
    authTokenGeneration += 1;
    bumpAuthSessionEpoch();
    if (options.broadcast !== false) {
      broadcastAuthSession('updated');
    }
  },
  clearTokenState: () => {
    removeTokenState();
    authTokenGeneration += 1;
    bumpAuthSessionEpoch();
    broadcastAuthSession('cleared');
  },
};
