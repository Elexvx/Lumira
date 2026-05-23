import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  refreshToken: vi.fn(),
  getRefreshToken: vi.fn(),
  setTokens: vi.fn(),
}));

vi.mock('@/services/auth', () => ({
  authService: {
    refreshToken: mocks.refreshToken,
    currentUser: vi.fn(),
    logout: vi.fn(),
  },
}));

vi.mock('@/services/system', () => ({
  systemService: {
    securitySettings: vi.fn(),
  },
}));

vi.mock('@/auth/token', () => ({
  tokenManager: {
    getRefreshToken: mocks.getRefreshToken,
    setTokens: mocks.setTokens,
    getAccessToken: vi.fn(() => ''),
    getTokenGeneration: vi.fn(() => 0),
    hasToken: vi.fn(() => true),
    getTokenState: vi.fn(() => null),
    clearTokenState: vi.fn(),
  },
}));

vi.mock('@umijs/max', () => ({
  history: {
    location: { pathname: '/dashboard/home' },
    replace: vi.fn(),
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

vi.mock('@/auth/securitySettings', () => ({
  DEFAULT_SECURITY_SETTINGS: {},
  getStoredSecuritySettings: vi.fn(() => null),
  normalizeSecuritySettings: vi.fn((value) => value || {}),
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
    mocks.refreshToken.mockReset();
    mocks.getRefreshToken.mockReset();
    mocks.setTokens.mockReset();
    mocks.getRefreshToken.mockReturnValue('refresh-token');
  });

  it('shares one in-flight refresh request across concurrent callers', async () => {
    const { tryRefreshToken } = await import('@/auth/session');
    let resolveRefresh: ((value: {
      accessToken: string;
      refreshToken: string;
      tokenType: string;
      expiresIn: number;
    }) => void) | undefined;

    mocks.refreshToken.mockImplementation(
      () => new Promise((resolve) => {
        resolveRefresh = resolve;
      }),
    );

    const first = tryRefreshToken();
    const second = tryRefreshToken();

    expect(mocks.refreshToken).toHaveBeenCalledTimes(1);

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
