import { describe, expect, it } from 'vitest';
import { filterRetiredMainMenuNodes, isRetiredMainMenuPath } from './navigation/mainMenuFilter';
import type { MenuNode } from '@/types/api';

describe('filterRetiredMainMenuNodes', () => {
  it('recognizes retired paths before canonical redirects can replace active menu labels', () => {
    expect(isRetiredMainMenuPath('/data-management/query-center')).toBe(true);
    expect(isRetiredMainMenuPath('/data-management/query-center/')).toBe(true);
    expect(isRetiredMainMenuPath('/experts/query')).toBe(true);
    expect(isRetiredMainMenuPath('/experts/query/')).toBe(true);
    expect(isRetiredMainMenuPath('/dashboard/home')).toBe(false);
  });

  it('removes retired management and query-center navigation from persisted menu trees', () => {
    const menuTree = [
      {
        menuCode: 'data.management',
        path: '/data-management',
        children: [
          { menuCode: 'project.management', path: '/projects/management' },
          { menuCode: 'team.management', path: '/team/management' },
          {
            menuCode: 'query.center',
            path: '/data-management/query-center',
            children: [
              { menuCode: 'team.search', path: '/team/search' },
              { menuCode: 'project.search', path: '/projects/search' },
              { menuCode: 'activity.search', path: '/activities/search' },
              { menuCode: 'payment.status', path: '/payments/status' },
            ],
          },
          { menuCode: 'download.center', path: '/data-management/download-center' },
        ],
      },
    ] as MenuNode[];

    expect(filterRetiredMainMenuNodes(menuTree, 'competition.root')).toEqual([
      {
        menuCode: 'data.management',
        path: '/data-management',
        children: [{ menuCode: 'download.center', path: '/data-management/download-center' }],
      },
    ]);
  });

  it('keeps flattening the legacy competition root while preserving its children', () => {
    const menuTree = [
      {
        menuCode: 'competition.root',
        path: '/competition',
        children: [{ menuCode: 'competition.management', path: '/competitions/management' }],
      },
    ] as MenuNode[];

    expect(filterRetiredMainMenuNodes(menuTree, 'competition.root')).toEqual([
      { menuCode: 'competition.management', path: '/competitions/management' },
    ]);
  });

  it('removes the retired standalone expert query from persisted menu trees', () => {
    const menuTree = [
      {
        menuCode: 'expert.root',
        path: '/experts',
        children: [
          { menuCode: 'expert.management', path: '/experts/management' },
          { menuCode: 'expert.query', path: '/experts/query' },
        ],
      },
    ] as MenuNode[];

    expect(filterRetiredMainMenuNodes(menuTree)).toEqual([
      {
        menuCode: 'expert.root',
        path: '/experts',
        children: [{ menuCode: 'expert.management', path: '/experts/management' }],
      },
    ]);
  });

  it('moves legacy certificate administration into data management', () => {
    const menuTree = [
      {
        menuCode: 'data.management.root',
        path: '/data-management',
        id: 1100,
        children: [
          { menuCode: 'download.center', path: '/data-management/download-center', id: 956 },
        ],
      },
      {
        menuCode: 'certificate.root',
        path: '/certificates',
        id: 1079,
        children: [
          { menuCode: 'certificate.templates', path: '/certificates/templates', id: 1080 },
          { menuCode: 'certificate.records', path: '/certificates/records', id: 1082 },
        ],
      },
    ] as MenuNode[];

    expect(filterRetiredMainMenuNodes(menuTree)).toEqual([
      {
        menuCode: 'data.management.root',
        path: '/data-management',
        id: 1100,
        children: [
          { menuCode: 'download.center', path: '/data-management/download-center', id: 956 },
          { menuCode: 'certificate.templates', path: '/certificates/templates', id: 1080, parentId: 1100 },
          { menuCode: 'certificate.records', path: '/certificates/records', id: 1082, parentId: 1100 },
        ],
      },
    ]);
  });
});
