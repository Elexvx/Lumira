import type { MenuNode } from '@/types/api';

const RETIRED_MAIN_MENU_PATHS = new Set([
  '/projects/management',
  '/team/management',
  '/data-management/query-center',
  '/team/search',
  '/projects/search',
  '/activities/search',
  '/payments/status',
]);
const normalizeMenuPath = (path: string) => path.trim().replace(/\/+$/, '') || '/';

export const filterRetiredMainMenuNodes = (
  items: MenuNode[] | undefined,
  legacyRootMenuCode?: string,
): MenuNode[] =>
  (items || []).flatMap((item) => {
    const normalizedPath = item.path ? normalizeMenuPath(item.path) : undefined;
    const children = filterRetiredMainMenuNodes(item.children, legacyRootMenuCode);
    if (normalizedPath && RETIRED_MAIN_MENU_PATHS.has(normalizedPath)) {
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
