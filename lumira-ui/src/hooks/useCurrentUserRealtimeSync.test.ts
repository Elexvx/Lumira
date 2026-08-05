import { describe, expect, it } from 'vitest';
import type { AppInitialState } from '@/app';
import { mergeCurrentUserRuntimeState } from '@/hooks/useCurrentUserRealtimeSync';
import type { CurrentUser } from '@/types/api';

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

describe('mergeCurrentUserRuntimeState', () => {
  it('updates the authoritative menu tree together with a changed permission snapshot', () => {
    const previousUser = currentUser();
    const refreshedUser = currentUser({
      permissionsVersion: 'permissions-2',
      permissions: ['dashboard:view', 'workflow:approve'],
    });
    const previousState = {
      currentUser: previousUser,
      menuTree: [{ id: 1, menuCode: 'dashboard.home', name: 'Dashboard', path: '/dashboard/home' }],
      menuVersion: 4,
      availablePlugins: [],
    } as unknown as AppInitialState;

    const nextState = mergeCurrentUserRuntimeState(previousState, refreshedUser, {
      menuTree: [
        previousState.menuTree[0],
        {
          id: 2,
          menuCode: 'workflow.root',
          name: 'Workflow',
          path: '/workflows',
          children: [
            { id: 3, menuCode: 'workflow.tasks', name: 'My approvals', path: '/workflows/tasks' },
          ],
        },
      ],
      availablePlugins: [],
    });

    expect(nextState?.currentUser?.permissionsVersion).toBe('permissions-2');
    expect(nextState?.menuTree.map((menu) => menu.menuCode)).toEqual([
      'dashboard.home',
      'workflow.root',
    ]);
    expect(nextState?.menuVersion).toBe(5);
  });
});
