import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const createStorage = () => {
  const values = new Map<string, string>();
  return {
    getItem: vi.fn((key: string) => values.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => {
      values.set(key, value);
    }),
    removeItem: vi.fn((key: string) => {
      values.delete(key);
    }),
    clear: vi.fn(() => {
      values.clear();
    }),
    key: vi.fn((index: number) => Array.from(values.keys())[index] ?? null),
    get length() {
      return values.size;
    },
  } as Storage;
};

const broadcastMessages: unknown[] = [];

class FakeBroadcastChannel {
  constructor(public name: string) {}

  postMessage(message: unknown) {
    broadcastMessages.push(message);
  }

  close() {}
}

describe('tokenManager', () => {
  let originalWindow: typeof globalThis.window | undefined;
  let localStorage: Storage;
  let sessionStorage: Storage;

  beforeEach(() => {
    vi.resetModules();
    originalWindow = globalThis.window;
    localStorage = createStorage();
    sessionStorage = createStorage();
    broadcastMessages.length = 0;
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel);
    Object.defineProperty(globalThis, 'window', {
      value: {
        localStorage,
        sessionStorage,
      },
      configurable: true,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    Object.defineProperty(globalThis, 'window', {
      value: originalWindow,
      configurable: true,
    });
  });

  it('persists access tokens in localStorage so a new same-origin tab can restore the session', async () => {
    const { tokenManager, TOKEN_STORAGE_KEY } = await import('@/auth/token');

    tokenManager.setTokens({
      accessToken: 'access-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      remember: false,
    });

    expect(localStorage.setItem).toHaveBeenCalledWith(
      TOKEN_STORAGE_KEY,
      expect.stringContaining('"accessToken":"access-token"'),
    );
    expect(sessionStorage.setItem).not.toHaveBeenCalled();

    vi.resetModules();
    const restored = await import('@/auth/token');

    expect(restored.tokenManager.getAccessToken()).toBe('access-token');
    expect(restored.tokenManager.hasToken()).toBe(true);
  });

  it('reloads a rotated access token into an already-open tab without rebroadcasting it', async () => {
    const { tokenManager, TOKEN_STORAGE_KEY } = await import('@/auth/token');

    tokenManager.setTokens({
      accessToken: 'token-before-role-switch',
      tokenType: 'Bearer',
      expiresIn: 3600,
    });
    const generationBeforeSync = tokenManager.getTokenGeneration();
    localStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify({
      accessToken: 'token-after-role-switch',
      tokenType: 'Bearer',
      expiresIn: 3600,
      expiresAt: Date.now() + 3600_000,
    }));

    expect(tokenManager.syncFromStorage()).toBe(true);
    expect(tokenManager.getAccessToken()).toBe('token-after-role-switch');
    expect(tokenManager.getTokenGeneration()).toBe(generationBeforeSync + 1);
  });

  it('keeps ordinary token broadcasts by default but can suppress them for an atomic role switch', async () => {
    const { tokenManager } = await import('@/auth/token');

    tokenManager.setTokens({
      accessToken: 'ordinary-refresh-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
    });
    expect(broadcastMessages).toContainEqual(expect.objectContaining({ type: 'updated' }));

    broadcastMessages.length = 0;
    tokenManager.setTokens(
      {
        accessToken: 'role-switch-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
      },
      { broadcast: false },
    );

    expect(broadcastMessages).toEqual([]);
  });

  it('does not mistake the current token expiring in storage for a cross-tab replacement', async () => {
    const { tokenManager, TOKEN_STORAGE_KEY } = await import('@/auth/token');

    tokenManager.setTokens({
      accessToken: 'token-near-expiry',
      tokenType: 'Bearer',
      expiresIn: 1,
    });
    const generationBeforeSync = tokenManager.getTokenGeneration();
    localStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify({
      accessToken: 'token-near-expiry',
      tokenType: 'Bearer',
      expiresIn: 1,
      expiresAt: Date.now() - 1,
    }));

    expect(tokenManager.syncFromStorage('token-near-expiry')).toBe(true);
    expect(tokenManager.getAccessToken()).toBe('token-near-expiry');
    expect(tokenManager.getTokenGeneration()).toBe(generationBeforeSync);
  });

  it('keeps the in-flight token when persistent storage cannot be inspected', async () => {
    const { tokenManager } = await import('@/auth/token');

    tokenManager.setTokens({
      accessToken: 'memory-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
    });
    const generationBeforeSync = tokenManager.getTokenGeneration();
    vi.mocked(localStorage.getItem).mockImplementation(() => {
      throw new Error('storage unavailable');
    });

    expect(tokenManager.syncFromStorage('memory-token')).toBe(true);
    expect(tokenManager.getAccessToken()).toBe('memory-token');
    expect(tokenManager.getTokenGeneration()).toBe(generationBeforeSync);
  });

  it('keeps the new memory token and advances generation when storage mutation fails', async () => {
    const { tokenManager } = await import('@/auth/token');

    const generationBeforeWrite = tokenManager.getTokenGeneration();
    vi.mocked(sessionStorage.removeItem).mockImplementation(() => {
      throw new Error('session storage removal failed');
    });
    vi.mocked(localStorage.removeItem).mockImplementation(() => {
      throw new Error('local storage removal failed');
    });
    vi.mocked(localStorage.setItem).mockImplementation(() => {
      throw new Error('local storage write failed');
    });

    expect(() =>
      tokenManager.setTokens({
        accessToken: 'new-memory-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
      }),
    ).not.toThrow();
    expect(tokenManager.getAccessToken()).toBe('new-memory-token');
    expect(tokenManager.getTokenGeneration()).toBe(generationBeforeWrite + 1);
  });

  it('does treat a removed persisted token as a cross-tab logout', async () => {
    const { tokenManager, TOKEN_STORAGE_KEY } = await import('@/auth/token');

    tokenManager.setTokens({
      accessToken: 'token-before-logout',
      tokenType: 'Bearer',
      expiresIn: 3600,
    });
    const generationBeforeSync = tokenManager.getTokenGeneration();
    localStorage.removeItem(TOKEN_STORAGE_KEY);

    expect(tokenManager.syncFromStorage('token-before-logout')).toBe(false);
    expect(tokenManager.hasToken()).toBe(false);
    expect(tokenManager.getTokenGeneration()).toBe(generationBeforeSync + 1);
  });
});
