import buildAccess from '@/access';
import { realPageRouteMetaList, resolveCanonicalRoutePath } from '@/routes/meta';
import type { CurrentUser, MenuNode } from '@/types/api';

const routePathMatches = (routePath: string, pathname: string) => {
  const pattern = routePath
    .replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    .replace(/:([^/]+)/g, '[^/]+');

  return new RegExp(`^${pattern}$`).test(pathname);
};

const canVisitMenuPath = (path: string | undefined, currentUser: CurrentUser) => {
  if (!path) {
    return true;
  }
  const canonicalPath = resolveCanonicalRoutePath(path);
  const routeMeta = realPageRouteMetaList.find((item) => routePathMatches(item.path, canonicalPath));
  if (!routeMeta?.access) {
    return true;
  }
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  return Boolean(access[routeMeta.access]);
};

const filterMenusByAccess = (menus: MenuNode[] | undefined, currentUser: CurrentUser): MenuNode[] =>
  (menus || [])
    .map((menu) => {
      const children = filterMenusByAccess(menu.children, currentUser);
      if (!canVisitMenuPath(menu.path, currentUser) && children.length === 0) {
        return null;
      }
      return {
        ...menu,
        ...(menu.children || children.length ? { children } : {}),
      };
    })
    .filter((menu): menu is MenuNode => Boolean(menu));

const dedupeMenuPaths = (menus: MenuNode[], seenPaths = new Set<string>()): MenuNode[] =>
  menus.flatMap((menu) => {
    let duplicatePath = false;
    if (menu.path) {
      const normalizedPath = menu.path.trim().replace(/\/+$/, '') || '/';
      if (seenPaths.has(normalizedPath)) {
        duplicatePath = true;
      } else {
        seenPaths.add(normalizedPath);
      }
    }
    const children = dedupeMenuPaths(menu.children || [], seenPaths);
    if (duplicatePath) {
      return children;
    }
    return [{
      ...menu,
      ...(menu.children || children.length ? { children } : {}),
    }];
  });

export const normalizeAuthenticatedMenuTree = (
  menuTree: MenuNode[] | undefined,
  currentUser: CurrentUser,
): MenuNode[] =>
  dedupeMenuPaths(filterMenusByAccess(menuTree, currentUser));
