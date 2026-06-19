import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  getRefreshToken: vi.fn(),
  setTokens: vi.fn(),
  clearTokenState: vi.fn(),
  historyReplace: vi.fn(),
}));

vi.mock('@/services/common/request', () => ({
  request: mocks.request,
}));

vi.mock('@/auth/token', () => ({
  tokenManager: {
    getRefreshToken: mocks.getRefreshToken,
    setTokens: mocks.setTokens,
    getAccessToken: vi.fn(() => ''),
    getTokenGeneration: vi.fn(() => 0),
    hasToken: vi.fn(() => true),
    getTokenState: vi.fn(() => null),
    clearTokenState: mocks.clearTokenState,
  },
}));

vi.mock('@umijs/max', () => ({
  history: {
    location: { pathname: '/dashboard/home' },
    replace: mocks.historyReplace,
  },
}));

vi.mock('@/cache/storage', () => ({
  storage: {
    get: vi.fn(() => null),
    set: vi.fn(),
    remove: vi.fn(),
  },
}));

vi.mock('@/auth/activity', () => ({
  clearSessionActivity: vi.fn(),
  persistSessionActivity: vi.fn(),
}));

vi.mock('@/auth/clientRuntimeState', () => ({
  clearClientRuntimeState: vi.fn(),
}));

vi.mock('@/auth/securitySettingsTypes', () => ({
  DEFAULT_SECURITY_SETTINGS: {},
}));

vi.mock('@/auth/securitySettingsNormalize', () => ({
  normalizeSecuritySettings: vi.fn((value) => value || {}),
}));

vi.mock('@/auth/securitySettingsStorage', () => ({
  clearSecuritySettings: vi.fn(),
  getStoredSecuritySettings: vi.fn(() => null),
  persistSecuritySettings: vi.fn(),
}));

vi.mock('@/auth/loginFlowState', () => ({
  beginBootstrapFlow: vi.fn(),
  endBootstrapFlow: vi.fn(),
}));

vi.mock('@/i18n/locale', () => ({
  applyLocalePreference: vi.fn(),
}));

describe('tryRefreshToken', () => {
  beforeEach(() => {
    mocks.request.mockReset();
    mocks.getRefreshToken.mockReset();
    mocks.setTokens.mockReset();
    mocks.clearTokenState.mockReset();
    mocks.historyReplace.mockReset();
    mocks.getRefreshToken.mockReturnValue('refresh-token');
  });

  it('shares one in-flight refresh request across concurrent callers', async () => {
    const { tryRefreshToken } = await import('@/auth/sessionLifecycle');
    let resolveRefresh: ((value: {
      accessToken: string;
      refreshToken: string;
      tokenType: string;
      expiresIn: number;
    }) => void) | undefined;

    mocks.request.mockImplementation((url: string) => {
      if (url !== '/v2/auth/refresh-token') {
        return Promise.reject(new Error(`Unexpected request: ${url}`));
      }
      return new Promise((resolve) => {
        resolveRefresh = resolve;
      });
    });

    const first = tryRefreshToken();
    const second = tryRefreshToken();

    expect(mocks.request).toHaveBeenCalledTimes(1);

    resolveRefresh?.({
      accessToken: 'access-next',
      refreshToken: 'refresh-next',
      tokenType: 'Bearer',
      expiresIn: 3600,
    });

    await expect(first).resolves.toBe(true);
    await expect(second).resolves.toBe(true);
    expect(mocks.setTokens).toHaveBeenCalledTimes(1);
  });
});

describe('performLogout', () => {
  beforeEach(() => {
    mocks.request.mockReset();
    mocks.clearTokenState.mockReset();
    mocks.historyReplace.mockReset();
  });

  it('clears local session and redirects without waiting for server logout', async () => {
    const { performLogout } = await import('@/auth/sessionLifecycle');
    mocks.request.mockImplementation((url: string) => {
      if (url !== '/v2/auth/logout') {
        return Promise.reject(new Error(`Unexpected request: ${url}`));
      }
      return new Promise(() => undefined);
    });

    await performLogout();

    expect(mocks.request).toHaveBeenCalledTimes(1);
    expect(mocks.clearTokenState).toHaveBeenCalledTimes(1);
    expect(mocks.historyReplace).toHaveBeenCalledWith('/user/login');
  });

  it('falls back logout request to legacy endpoint when v2 is unavailable', async () => {
    const { performLogout } = await import('@/auth/sessionLifecycle');
    mocks.request
      .mockRejectedValueOnce(new Error('v2 unavailable') as never)
      .mockResolvedValueOnce(true as never);

    await performLogout();

    expect(mocks.request).toHaveBeenNthCalledWith(
      1,
      '/v2/auth/logout',
      expect.objectContaining({
        method: 'POST',
      }),
    );
    expect(mocks.request).toHaveBeenNthCalledWith(
      2,
      '/v1/auth/logout',
      expect.objectContaining({
        method: 'POST',
      }),
    );
  });

  it('falls back refresh token to legacy endpoint when v2 is unavailable', async () => {
    const { tryRefreshToken } = await import('@/auth/sessionLifecycle');
    mocks.request.mockImplementation((url: string) => {
      if (url === '/v2/auth/refresh-token') {
        return Promise.reject(new Error('v2 unavailable'));
      }
      if (url === '/v1/auth/refresh-token') {
        return Promise.resolve({
          accessToken: 'access-legacy',
          refreshToken: 'refresh-legacy',
          tokenType: 'Bearer',
          expiresIn: 3600,
        });
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });

    const result = await tryRefreshToken();

    expect(result).toBe(true);
    expect(mocks.request).toHaveBeenCalledTimes(2);
  });
});
