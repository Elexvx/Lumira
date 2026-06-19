import { beforeEach, describe, expect, it, vi } from 'vitest';
type CurrentUserFixture = {
  userId: number;
  username: string;
  nickname: string;
  permissions: string[];
  sessionId: string;
};

const mocks = vi.hoisted(() => ({
  hasToken: vi.fn(),
  request: vi.fn(),
  loadSecuritySettings: vi.fn(),
  clearAuthSession: vi.fn(),
  tryRefreshToken: vi.fn(),
  withBootstrapFlow: vi.fn((fn: () => Promise<unknown>) => fn()),
  persistCurrentUser: vi.fn((user) => user),
  buildFallbackCurrentUser: vi.fn(),
  persistSessionMeta: vi.fn(),
  applyLocalePreference: vi.fn(),
}));

vi.mock('@/auth/token', () => ({
  tokenManager: {
    hasToken: mocks.hasToken,
  },
}));

vi.mock('@/services/common/request', () => ({
  request: mocks.request,
}));

vi.mock('@/auth/sessionSecurity', () => ({
  loadSecuritySettings: mocks.loadSecuritySettings,
}));

vi.mock('@/auth/sessionLifecycle', () => ({
  clearAuthSession: mocks.clearAuthSession,
  tryRefreshToken: mocks.tryRefreshToken,
  withBootstrapFlow: mocks.withBootstrapFlow,
}));

vi.mock('@/auth/sessionState', () => ({
  persistCurrentUser: mocks.persistCurrentUser,
  buildFallbackCurrentUser: mocks.buildFallbackCurrentUser,
  persistSessionMeta: mocks.persistSessionMeta,
}));

vi.mock('@/auth/activity', () => ({
  persistSessionActivity: vi.fn(),
}));

vi.mock('@/i18n/locale', () => ({
  applyLocalePreference: mocks.applyLocalePreference,
}));

describe('sessionBootstrap', () => {
  beforeEach(() => {
    mocks.hasToken.mockReset();
    mocks.request.mockReset();
    mocks.loadSecuritySettings.mockReset();
    mocks.clearAuthSession.mockReset();
    mocks.tryRefreshToken.mockReset();
    mocks.withBootstrapFlow.mockReset();
    mocks.persistCurrentUser.mockReset();
    mocks.buildFallbackCurrentUser.mockReset();
    mocks.persistSessionMeta.mockReset();
    mocks.applyLocalePreference.mockReset();

    mocks.hasToken.mockReturnValue(true);
    mocks.withBootstrapFlow.mockImplementation((fn: () => Promise<unknown>) => fn());
    mocks.persistCurrentUser.mockImplementation((user) => user);
    mocks.buildFallbackCurrentUser.mockImplementation((response: { user?: unknown }) => ({ ...(response as object), userId: 1, username: 'fallback' }));
  });

  it('uses /v2/auth/bootstrap to initialize session', async () => {
    const bootstrapUser = {
      userId: 10,
      username: 'ordinary',
      nickname: 'Ordinary User',
      permissions: [],
      sessionId: 'session-ordinary',
      locale: 'zh-CN',
    };
    const securitySettings = { captchaEnabled: true };

    mocks.request.mockResolvedValue({
      currentUser: bootstrapUser,
      securitySettings,
    });

    const { restoreSession } = await import('@/auth/sessionBootstrap');

    const restored = await restoreSession();

    expect(restored).toMatchObject({
      currentUser: bootstrapUser,
      securitySettings,
    });
    expect(mocks.request).toHaveBeenCalledWith('/v2/auth/bootstrap', expect.objectContaining({ method: 'GET' }));
    expect(mocks.loadSecuritySettings).not.toHaveBeenCalled();
    expect(mocks.tryRefreshToken).not.toHaveBeenCalled();
    expect(mocks.clearAuthSession).not.toHaveBeenCalled();
  });

  it('falls back to legacy flow when bootstrap endpoint is unavailable', async () => {
    let currentUserResolve: ((value: CurrentUserFixture) => void) | undefined;
    const currentUserPromise = new Promise((resolve) => {
      currentUserResolve = resolve as (value: CurrentUserFixture) => void;
    });

    mocks.request.mockImplementation((url: string) => {
      if (url === '/v2/auth/bootstrap') {
        return Promise.reject(new Error('bootstrap unavailable'));
      }
      if (url === '/v2/auth/current-user') {
        return Promise.reject(new Error('v2 current-user unavailable'));
      }
      if (url === '/v1/auth/current-user') {
        return currentUserPromise;
      }
      throw new Error(`Unexpected request: ${url}`);
    });

    const securitySettings = { captchaEnabled: false };
    mocks.loadSecuritySettings.mockResolvedValue(securitySettings);

    const { restoreSession } = await import('@/auth/sessionBootstrap');

    const restorePromise = restoreSession();
    await Promise.resolve();

    currentUserResolve?.({
      userId: 10,
      username: 'ordinary',
      nickname: 'Ordinary User',
      permissions: [],
      sessionId: 'session-ordinary',
    });

    await expect(restorePromise).resolves.toMatchObject({
      currentUser: { userId: 10, username: 'ordinary', nickname: 'Ordinary User', sessionId: 'session-ordinary' },
      securitySettings,
    });
    expect(mocks.loadSecuritySettings).toHaveBeenCalledTimes(1);
    expect(mocks.request).toHaveBeenNthCalledWith(
      1,
      '/v2/auth/bootstrap',
      expect.objectContaining({ method: 'GET' }),
    );
    expect(mocks.request).toHaveBeenCalledWith('/v2/auth/current-user', expect.objectContaining({ method: 'GET' }));
    expect(mocks.request).toHaveBeenCalledWith('/v1/auth/current-user', expect.objectContaining({ method: 'GET' }));
    expect(mocks.tryRefreshToken).not.toHaveBeenCalled();
    expect(mocks.clearAuthSession).not.toHaveBeenCalled();
  });

  it('reuses the in-flight security settings call after refresh retry', async () => {
    let currentUserCall = 0;
    const secondCurrentUserResponse = {
      userId: 22,
      username: 'refreshed',
      nickname: 'Refreshed User',
      permissions: ['user:profile:read'],
      sessionId: 'session-refreshed',
    };

    mocks.request.mockImplementation(async (url: string) => {
      if (url === '/v2/auth/bootstrap') {
        throw new Error('bootstrap unavailable');
      }
      if (url === '/v2/auth/current-user') {
        currentUserCall += 1;
        if (currentUserCall === 1) {
          throw new Error('v2 unavailable');
        }
        return secondCurrentUserResponse;
      }
      if (url === '/v1/auth/current-user') {
        if (currentUserCall === 1) {
          throw new Error('v1 unavailable');
        }
        return secondCurrentUserResponse;
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    mocks.tryRefreshToken.mockResolvedValue(true);
    const securitySettings = { captchaEnabled: true };
    mocks.loadSecuritySettings.mockResolvedValue(securitySettings);

    const { restoreSession } = await import('@/auth/sessionBootstrap');

    const restored = await restoreSession();

    expect(restored).toMatchObject({
      currentUser: secondCurrentUserResponse,
      securitySettings,
    });
    expect(mocks.tryRefreshToken).toHaveBeenCalledTimes(1);
    expect(mocks.loadSecuritySettings).toHaveBeenCalledTimes(1);
    expect(mocks.request).toHaveBeenCalledTimes(4);
  });
});
