// @vitest-environment jsdom

import { act, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AppInitialState } from '@/app';
import type { CurrentUser } from '@/types/api';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean })
  .IS_REACT_ACT_ENVIRONMENT = true;

const syncMocks = vi.hoisted(() => ({
  loadCurrentUserSnapshot: vi.fn(),
  loadCurrentNavigationSnapshot: vi.fn(),
}));

vi.mock('@/auth/currentUserSync', () => ({
  CURRENT_USER_SYNC_EVENT: 'lumira:current-user-sync',
  CURRENT_USER_SYNC_INTERVAL_MS: 60_000,
  hasCurrentUserSnapshotChanged: (previous: CurrentUser | undefined, next: CurrentUser) =>
    JSON.stringify(previous ?? null) !== JSON.stringify(next),
  hasCurrentUserNavigationChanged: (previous: CurrentUser | undefined, next: CurrentUser) =>
    previous?.permissionsVersion !== next.permissionsVersion,
  loadCurrentUserSnapshot: syncMocks.loadCurrentUserSnapshot,
  loadCurrentNavigationSnapshot: syncMocks.loadCurrentNavigationSnapshot,
}));

vi.mock('@/auth/authenticatedMenuTree', () => ({
  normalizeAuthenticatedMenuTree: (menuTree: unknown) => menuTree,
}));

vi.mock('@/auth/sessionState', () => ({
  persistCurrentUser: (currentUser: CurrentUser) => currentUser,
}));

vi.mock('@/auth/token', () => ({
  tokenManager: { hasToken: () => true },
}));

import { useCurrentUserRealtimeSync } from '@/hooks/useCurrentUserRealtimeSync';

const currentUser = (overrides: Partial<CurrentUser> = {}): CurrentUser => ({
  userId: 1001,
  userUuid: 'user-uuid-1001',
  username: 'admin',
  sessionId: 'session-1001',
  sessionVersion: 1,
  permissionsVersion: 'permissions-1',
  permissions: ['dashboard:view'],
  roleIds: [1],
  ...overrides,
});

describe('useCurrentUserRealtimeSync ordering', () => {
  afterEach(() => {
    syncMocks.loadCurrentUserSnapshot.mockReset();
    syncMocks.loadCurrentNavigationSnapshot.mockReset();
    document.body.innerHTML = '';
  });

  it('does not let an older polling response overwrite a newer role snapshot', async () => {
    const olderRequest = Promise.withResolvers<CurrentUser>();
    const newerRequest = Promise.withResolvers<CurrentUser>();
    syncMocks.loadCurrentUserSnapshot
      .mockReturnValueOnce(olderRequest.promise)
      .mockReturnValueOnce(newerRequest.promise);
    syncMocks.loadCurrentNavigationSnapshot.mockResolvedValue({
      menuTree: [{ id: 2, menuCode: 'workflow.root', name: 'Workflow', path: '/workflows' }],
      availablePlugins: [],
    });

    const initialState = {
      currentUser: currentUser(),
      menuTree: [{ id: 1, menuCode: 'dashboard.home', name: 'Dashboard', path: '/dashboard/home' }],
      menuVersion: 1,
      availablePlugins: [],
    } as unknown as AppInitialState;
    let latestState: AppInitialState | undefined = initialState;
    const container = document.createElement('div');
    document.body.appendChild(container);
    const root = createRoot(container);

    const Harness = () => {
      const [state, setState] = useState<AppInitialState | undefined>(initialState);
      latestState = state;
      useCurrentUserRealtimeSync({ currentUser: state?.currentUser, setInitialState: setState });
      return null;
    };

    try {
      await act(async () => {
        root.render(<Harness />);
      });
      act(() => window.dispatchEvent(new Event('lumira:current-user-sync')));
      act(() => window.dispatchEvent(new Event('lumira:current-user-sync')));
      expect(syncMocks.loadCurrentUserSnapshot).toHaveBeenCalledTimes(2);

      await act(async () => {
        newerRequest.resolve(currentUser({
          permissionsVersion: 'permissions-2',
          permissions: ['dashboard:view', 'workflow:approve'],
          roleIds: [1, 2],
        }));
        await newerRequest.promise;
      });
      expect(latestState?.currentUser?.permissionsVersion).toBe('permissions-2');
      expect(latestState?.menuTree.map((item) => item.menuCode)).toEqual(['workflow.root']);

      await act(async () => {
        olderRequest.resolve(currentUser());
        await olderRequest.promise;
      });
      expect(latestState?.currentUser?.permissionsVersion).toBe('permissions-2');
      expect(latestState?.currentUser?.roleIds).toEqual([1, 2]);
      expect(latestState?.menuTree.map((item) => item.menuCode)).toEqual(['workflow.root']);
      expect(latestState?.menuVersion).toBe(2);
    } finally {
      await act(async () => root.unmount());
    }
  });
});
