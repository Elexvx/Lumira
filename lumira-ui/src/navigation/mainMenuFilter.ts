import type { MenuNode } from '@/types/api';

const RETIRED_MAIN_MENU_PATHS = new Set([
  '/projects/management',
  '/team/management',
  '/data-management/query-center',
  '/team/search',
  '/projects/search',
  '/activities/search',
  '/payments/status',
  '/experts/query',
]);
const normalizeMenuPath = (path: string) => path.trim().replace(/\/+$/, '') || '/';

export const isRetiredMainMenuPath = (path?: string | null) =>
  Boolean(path && RETIRED_MAIN_MENU_PATHS.has(normalizeMenuPath(path)));

export const filterRetiredMainMenuNodes = (
  items: MenuNode[] | undefined,
  legacyRootMenuCode?: string,
): MenuNode[] =>
  (items || []).flatMap((item) => {
    const children = filterRetiredMainMenuNodes(item.children, legacyRootMenuCode);
    if (isRetiredMainMenuPath(item.path)) {
      return [];
    }
    if (legacyRootMenuCode && item.menuCode === legacyRootMenuCode) {
      return children;
    }
    return {
      ...item,
      children: children.length ? children : undefined,
    };
  });
