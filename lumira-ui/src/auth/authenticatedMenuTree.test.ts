import { describe, expect, it } from 'vitest';
import { normalizeAuthenticatedMenuTree } from '@/auth/authenticatedMenuTree';
import type { CurrentUser, MenuNode } from '@/types/api';

const buildCurrentUser = (permissions: string[]): CurrentUser => ({
  userId: 1003,
  userUuid: '453725634578177442',
  username: 'wx_o3HZz2SqjlZ0fJyd3wIiUc_g',
  sessionId: 'session-1003',
  permissionsVersion: 'v9:data-scope-cache-v4',
  sessionVersion: 1,
  permissions,
  roleIds: [1002],
});

const baseMenuTree: MenuNode[] = [
  {
    id: 1,
    menuCode: 'dashboard.home',
    name: '工作台',
    path: '/dashboard/home',
  },
];

describe('normalizeAuthenticatedMenuTree', () => {
  it('restores competition registration menus for common users with registration permissions', () => {
    const currentUser = buildCurrentUser([
      'dashboard:view',
      'profile:view',
      'system:file:view',
      'aiadc:registration:view',
      'aiadc:activity:create',
    ]);

    const normalized = normalizeAuthenticatedMenuTree(baseMenuTree, currentUser);
    const registrationRoot = normalized.find((menu) => menu.menuCode === 'registration.root');

    expect(registrationRoot).toBeDefined();
    expect(registrationRoot?.children?.map((menu) => menu.menuCode)).toEqual(['competition.registration', 'activity.registration']);
  });

  it('does not inject registration menus when the current user has no registration access', () => {
    const currentUser = buildCurrentUser([
      'dashboard:view',
      'profile:view',
      'system:file:view',
    ]);

    const normalized = normalizeAuthenticatedMenuTree(baseMenuTree, currentUser);

    expect(normalized.some((menu) => menu.menuCode === 'registration.root')).toBe(false);
    expect(normalized.some((menu) => menu.menuCode === 'competition.registration')).toBe(false);
  });
});
