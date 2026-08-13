import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CurrentUser } from '@/types/api';
import { request } from '@/services/common/request';
import { ApiRequestError } from '@/services/common/requestInternalsTypes';
import { ErrorCode } from '@/enums/errorCode';

const mocks = vi.hoisted(() => ({
  tokenGeneration: { value: 1 },
  refreshAuthSession: vi.fn(),
}));

vi.mock('@/services/common/request', () => ({
  request: vi.fn(),
}));

vi.mock('@/auth/token', () => ({
  tokenManager: {
    getTokenGeneration: () => mocks.tokenGeneration.value,
  },
}));

vi.mock('@/auth/sessionLifecycle', () => ({
  tryRefreshTokenOutcome: mocks.refreshAuthSession,
}));

const currentUser = (overrides: Partial<CurrentUser> = {}): CurrentUser => ({
  userId: 1001,
  userUuid: 'user-uuid-1001',
  username: 'admin',
  sessionId: 'session-1001',
  sessionVersion: 1,
  permissionsVersion: 'permissions-1',
  permissions: ['dashboard:view'],
  roleIds: [1],
  availableRoles: [
    {
      id: 1,
      roleCode: 'admin',
      roleName: 'Administrator',
      roleType: 'FUNCTIONAL',
    },
  ],
  ...overrides,
});

describe('currentUserSync', () => {
  beforeEach(() => {
    vi.mocked(request).mockReset();
    mocks.tokenGeneration.value = 1;
    mocks.refreshAuthSession.mockReset();
    mocks.refreshAuthSession.mockResolvedValue('refreshed');
  });

  it('detects role and permission changes in a refreshed current-user snapshot', async () => {
    const { hasCurrentUserNavigationChanged, hasCurrentUserSnapshotChanged } = await import('@/auth/currentUserSync');
    const previous = currentUser();
    const refreshed = currentUser({
      permissionsVersion: 'permissions-2',
      permissions: ['dashboard:view', 'registration:view'],
      roleIds: [1, 2],
      availableRoles: [
        ...(previous.availableRoles || []),
        {
          id: 2,
          roleCode: 'commonuser',
          roleName: 'Common User',
          roleType: 'FUNCTIONAL',
        },
      ],
    });

    expect(hasCurrentUserSnapshotChanged(previous, refreshed)).toBe(true);
    expect(hasCurrentUserSnapshotChanged(refreshed, { ...refreshed })).toBe(false);
    expect(hasCurrentUserNavigationChanged(previous, refreshed)).toBe(true);
    expect(hasCurrentUserNavigationChanged(refreshed, { ...refreshed, nickname: 'Administrator' })).toBe(false);
  });

  it('deduplicates concurrent current-user refresh requests', async () => {
    const deferred = Promise.withResolvers<CurrentUser>();
    vi.mocked(request).mockReturnValue(deferred.promise);
    const { loadCurrentUserSnapshot } = await import('@/auth/currentUserSync');

    const first = loadCurrentUserSnapshot();
    const second = loadCurrentUserSnapshot();
    deferred.resolve(currentUser());

    await expect(first).resolves.toMatchObject({ userId: 1001 });
    await expect(second).resolves.toMatchObject({ userId: 1001 });
    expect(request).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledWith('/v2/auth/current-user', expect.objectContaining({
      method: 'GET',
      silent: true,
    }));
    expect(vi.mocked(request).mock.calls[0]?.[1]).not.toHaveProperty('allowUnauthorizedWithoutRedirect');
  });

  it('starts a fresh current-user request when an explicit role mutation sync races with polling', async () => {
    const pollingRequest = Promise.withResolvers<CurrentUser>();
    const mutationRequest = Promise.withResolvers<CurrentUser>();
    vi.mocked(request)
      .mockReturnValueOnce(pollingRequest.promise)
      .mockReturnValueOnce(mutationRequest.promise);
    const { loadCurrentUserSnapshot, notifyCurrentUserSync } = await import('@/auth/currentUserSync');

    const beforeMutation = loadCurrentUserSnapshot();
    notifyCurrentUserSync();
    const afterMutation = loadCurrentUserSnapshot();

    expect(afterMutation).not.toBe(beforeMutation);
    expect(request).toHaveBeenCalledTimes(2);
    pollingRequest.resolve(currentUser());
    mutationRequest.resolve(currentUser({
      permissionsVersion: 'permissions-2',
      roleIds: [1, 2],
    }));
    await expect(beforeMutation).resolves.toMatchObject({ permissionsVersion: 'permissions-1' });
    await expect(afterMutation).resolves.toMatchObject({ permissionsVersion: 'permissions-2' });
  });

  it('refreshes the access token before notifying current-user synchronization after a permission mutation', async () => {
    const pollingRequest = Promise.withResolvers<CurrentUser>();
    const mutationRequest = Promise.withResolvers<CurrentUser>();
    vi.mocked(request)
      .mockReturnValueOnce(pollingRequest.promise)
      .mockReturnValueOnce(mutationRequest.promise);
    mocks.refreshAuthSession.mockImplementation(async () => {
      expect(request).toHaveBeenCalledTimes(1);
      return 'refreshed';
    });
    const { loadCurrentUserSnapshot, refreshAuthSessionAndNotifyCurrentUserSync } = await import('@/auth/currentUserSync');

    const previousRequest = loadCurrentUserSnapshot();
    await expect(refreshAuthSessionAndNotifyCurrentUserSync()).resolves.toBe('refreshed');
    const nextRequest = loadCurrentUserSnapshot();

    expect(mocks.refreshAuthSession).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledTimes(2);
    pollingRequest.resolve(currentUser());
    mutationRequest.resolve(currentUser({ permissionsVersion: 'permissions-2' }));
    await expect(previousRequest).resolves.toMatchObject({ permissionsVersion: 'permissions-1' });
    await expect(nextRequest).resolves.toMatchObject({ permissionsVersion: 'permissions-2' });
  });

  it('never reuses an in-flight current-user request after the auth generation changes', async () => {
    const previousSession = Promise.withResolvers<CurrentUser>();
    const nextSession = Promise.withResolvers<CurrentUser>();
    vi.mocked(request)
      .mockReturnValueOnce(previousSession.promise)
      .mockReturnValueOnce(nextSession.promise);
    const { loadCurrentUserSnapshot } = await import('@/auth/currentUserSync');

    const previousRequest = loadCurrentUserSnapshot();
    mocks.tokenGeneration.value = 2;
    const nextRequest = loadCurrentUserSnapshot();

    expect(request).toHaveBeenCalledTimes(2);
    previousSession.resolve(currentUser({ userId: 1001, sessionId: 'previous-session' }));
    await expect(previousRequest).resolves.toMatchObject({ sessionId: 'previous-session' });

    const deduplicatedNextRequest = loadCurrentUserSnapshot();
    expect(deduplicatedNextRequest).toBe(nextRequest);
    expect(request).toHaveBeenCalledTimes(2);

    nextSession.resolve(currentUser({ userId: 2002, sessionId: 'next-session' }));
    await expect(nextRequest).resolves.toMatchObject({ userId: 2002, sessionId: 'next-session' });
  });

  it('falls back to the legacy current-user endpoint', async () => {
    vi.mocked(request)
      .mockRejectedValueOnce(new ApiRequestError(ErrorCode.NOT_FOUND, 'v2 unavailable', { httpStatus: 404 }))
      .mockResolvedValueOnce(currentUser());
    const { loadCurrentUserSnapshot } = await import('@/auth/currentUserSync');

    await expect(loadCurrentUserSnapshot()).resolves.toMatchObject({ username: 'admin' });
    expect(request).toHaveBeenNthCalledWith(1, '/v2/auth/current-user', expect.any(Object));
    expect(request).toHaveBeenNthCalledWith(2, '/v1/auth/current-user', expect.any(Object));
  });

  it('does not turn a v2 auth or concurrency failure into a legacy retry', async () => {
    const sessionChanged = new ApiRequestError(ErrorCode.SESSION_EXPIRED, 'Session changed concurrently', {
      httpStatus: 401,
    });
    vi.mocked(request).mockRejectedValueOnce(sessionChanged);
    const { loadCurrentUserSnapshot } = await import('@/auth/currentUserSync');

    await expect(loadCurrentUserSnapshot()).rejects.toBe(sessionChanged);
    expect(request).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledWith('/v2/auth/current-user', expect.any(Object));
  });

  it('reloads and deduplicates the authoritative menu tree after permission changes', async () => {
    const deferred = Promise.withResolvers<{
      menuTree: Array<{ menuCode: string; name: string; path: string }>;
      availablePlugins: [];
    }>();
    vi.mocked(request).mockReturnValue(deferred.promise);
    const { loadCurrentNavigationSnapshot } = await import('@/auth/currentUserSync');

    const first = loadCurrentNavigationSnapshot();
    const second = loadCurrentNavigationSnapshot();
    deferred.resolve({
      menuTree: [{ menuCode: 'workflow.root', name: 'Workflow', path: '/workflows' }],
      availablePlugins: [],
    });

    await expect(first).resolves.toMatchObject({ menuTree: [{ menuCode: 'workflow.root' }] });
    await expect(second).resolves.toMatchObject({ menuTree: [{ menuCode: 'workflow.root' }] });
    expect(request).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledWith('/v2/plugins/current/bootstrap', expect.objectContaining({
      method: 'GET',
      silent: true,
    }));
    expect(vi.mocked(request).mock.calls[0]?.[1]).not.toHaveProperty('allowUnauthorizedWithoutRedirect');
  });

  it('falls back to the legacy navigation bootstrap endpoint', async () => {
    vi.mocked(request)
      .mockRejectedValueOnce(new ApiRequestError(ErrorCode.NOT_FOUND, 'v2 unavailable', { httpStatus: 404 }))
      .mockResolvedValueOnce({ menuTree: [], availablePlugins: [] });
    const { loadCurrentNavigationSnapshot } = await import('@/auth/currentUserSync');

    await expect(loadCurrentNavigationSnapshot()).resolves.toEqual({ menuTree: [], availablePlugins: [] });
    expect(request).toHaveBeenNthCalledWith(1, '/v2/plugins/current/bootstrap', expect.any(Object));
    expect(request).toHaveBeenNthCalledWith(2, '/v1/plugins/current/bootstrap', expect.any(Object));
  });
});
