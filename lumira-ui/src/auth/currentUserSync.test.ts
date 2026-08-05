import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CurrentUser } from '@/types/api';
import { request } from '@/services/common/request';

vi.mock('@/services/common/request', () => ({
  request: vi.fn(),
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
  });

  it('falls back to the legacy current-user endpoint', async () => {
    vi.mocked(request)
      .mockRejectedValueOnce(new Error('v2 unavailable'))
      .mockResolvedValueOnce(currentUser());
    const { loadCurrentUserSnapshot } = await import('@/auth/currentUserSync');

    await expect(loadCurrentUserSnapshot()).resolves.toMatchObject({ username: 'admin' });
    expect(request).toHaveBeenNthCalledWith(1, '/v2/auth/current-user', expect.any(Object));
    expect(request).toHaveBeenNthCalledWith(2, '/v1/auth/current-user', expect.any(Object));
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
  });

  it('falls back to the legacy navigation bootstrap endpoint', async () => {
    vi.mocked(request)
      .mockRejectedValueOnce(new Error('v2 unavailable'))
      .mockResolvedValueOnce({ menuTree: [], availablePlugins: [] });
    const { loadCurrentNavigationSnapshot } = await import('@/auth/currentUserSync');

    await expect(loadCurrentNavigationSnapshot()).resolves.toEqual({ menuTree: [], availablePlugins: [] });
    expect(request).toHaveBeenNthCalledWith(1, '/v2/plugins/current/bootstrap', expect.any(Object));
    expect(request).toHaveBeenNthCalledWith(2, '/v1/plugins/current/bootstrap', expect.any(Object));
  });
});
