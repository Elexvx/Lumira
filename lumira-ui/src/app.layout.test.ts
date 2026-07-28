import { describe, expect, it } from 'vitest';
import { filterRetiredMainMenuNodes } from './navigation/mainMenuFilter';
import type { MenuNode } from '@/types/api';

describe('filterRetiredMainMenuNodes', () => {
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
});
