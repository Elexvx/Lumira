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

describe('tokenManager', () => {
  let originalWindow: typeof globalThis.window | undefined;
  let localStorage: Storage;
  let sessionStorage: Storage;

  beforeEach(() => {
    vi.resetModules();
    originalWindow = globalThis.window;
    localStorage = createStorage();
    sessionStorage = createStorage();
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
});
