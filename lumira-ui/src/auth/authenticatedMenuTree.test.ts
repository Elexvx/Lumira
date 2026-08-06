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
  it('does not synthesize routes that are absent from the server menu tree', () => {
    const currentUser = buildCurrentUser([
      'dashboard:view',
      'profile:view',
      'system:file:view',
      'aiadc:registration:view',
      'aiadc:activity:create',
    ]);

    const normalized = normalizeAuthenticatedMenuTree(baseMenuTree, currentUser);

    expect(normalized).toEqual(baseMenuTree);
    expect(normalized.some((menu) => menu.menuCode === 'registration.root')).toBe(false);
  });

  it('filters server-provided registration routes without adding missing siblings', () => {
    const currentUser = buildCurrentUser([
      'dashboard:view',
      'profile:view',
      'system:file:view',
    ]);

    const normalized = normalizeAuthenticatedMenuTree([
      ...baseMenuTree,
      {
        id: 2,
        menuCode: 'registration.root',
        name: '报名',
        path: '/registration',
        children: [
          { id: 3, menuCode: 'competition.registration', name: '赛事报名', path: '/competitions/register' },
          { id: 4, menuCode: 'certificate.mine', name: '我的证书', path: '/certificates/mine' },
        ],
      },
    ], currentUser);

    const registrationRoot = normalized.find((menu) => menu.menuCode === 'registration.root');
    expect(registrationRoot?.children?.map((menu) => menu.menuCode)).toEqual(['certificate.mine']);
  });

  it('deduplicates canonical menu paths and preserves unique descendants', () => {
    const currentUser = buildCurrentUser([
      'dashboard:view',
      'profile:view',
      'system:file:view',
      'aiadc:certificate-template:view',
    ]);
    const normalized = normalizeAuthenticatedMenuTree([
      {
        id: 10,
        menuCode: 'certificate.root.primary',
        name: '证书',
        path: '/certificates',
        children: [{ id: 11, menuCode: 'certificate.templates', name: '模板', path: '/certificates/templates' }],
      },
      {
        id: 12,
        menuCode: 'certificate.root.duplicate',
        name: '重复证书',
        path: '/certificates',
        children: [{ id: 13, menuCode: 'certificate.mine', name: '我的证书', path: '/certificates/mine' }],
      },
    ], currentUser);
    const flatten = (menus: MenuNode[]): MenuNode[] =>
      menus.flatMap((menu) => [menu, ...flatten(menu.children || [])]);
    const all = flatten(normalized);

    expect(all.filter((menu) => menu.path === '/certificates')).toHaveLength(1);
    expect(all.map((menu) => menu.path)).toContain('/certificates/templates');
    expect(all.map((menu) => menu.path)).toContain('/certificates/mine');
  });

  it('deduplicates canonical menu paths and preserves unique descendants', () => {
    const currentUser = buildCurrentUser([
      'dashboard:view',
      'profile:view',
      'system:file:view',
      'aiadc:certificate-template:view',
    ]);
    const normalized = normalizeAuthenticatedMenuTree([
      {
        id: 10,
        menuCode: 'certificate.root.primary',
        name: '证书',
        path: '/certificates',
        children: [{ id: 11, menuCode: 'certificate.templates', name: '模板', path: '/certificates/templates' }],
      },
      {
        id: 12,
        menuCode: 'certificate.root.duplicate',
        name: '重复证书',
        path: '/certificates',
        children: [{ id: 13, menuCode: 'certificate.mine', name: '我的证书', path: '/certificates/mine' }],
      },
    ], currentUser);
    const flatten = (menus: MenuNode[]): MenuNode[] =>
      menus.flatMap((menu) => [menu, ...flatten(menu.children || [])]);
    const all = flatten(normalized);

    expect(all.filter((menu) => menu.path === '/certificates')).toHaveLength(1);
    expect(all.map((menu) => menu.path)).toContain('/certificates/templates');
    expect(all.map((menu) => menu.path)).toContain('/certificates/mine');
  });
});
