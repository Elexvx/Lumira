import { DEFAULT_HOME_PATH, LOGIN_PATH } from '@/app.constants';
import buildAccess from '@/access';
import { buildStorageKey } from '@/cache/storage';
import { TOKEN_STORAGE_KEY } from '@/auth/token';
import { realPageRouteMetaList } from '@/routes/meta';
import type { CurrentUser, MenuNode } from '@/types/api';

export const AUTH_TOKEN_STORAGE_KEY = buildStorageKey(TOKEN_STORAGE_KEY);

export const resolveLoginRedirectTarget = (search: string, fallback = DEFAULT_HOME_PATH) => {
  const redirect = new URLSearchParams(search).get('redirect')?.trim();
  if (!redirect || redirect === LOGIN_PATH || !redirect.startsWith('/')) {
    return fallback;
  }

  return redirect;
};

const normalizePathname = (target: string) => target.split('?')[0].split('#')[0] || DEFAULT_HOME_PATH;

const routePathMatches = (routePath: string, pathname: string) => {
  const pattern = routePath
    .replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    .replace(/\\:([^/]+)/g, '[^/]+');

  return new RegExp(`^${pattern}$`).test(pathname);
};

export type RouteAccessStatus = 'allowed' | 'denied' | 'unknown';

export const resolveRouteAccessStatus = (target: string, currentUser: CurrentUser): RouteAccessStatus => {
  const pathname = normalizePathname(target);
  const routeMeta = realPageRouteMetaList.find((item) => routePathMatches(item.path, pathname));
  if (!routeMeta) {
    return 'unknown';
  }
  if (!routeMeta.access) {
    return 'allowed';
  }

  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  return access[routeMeta.access] ? 'allowed' : 'denied';
};

const canVisitPath = (target: string, currentUser: CurrentUser) =>
  resolveRouteAccessStatus(target, currentUser) === 'allowed';

const findFirstAccessibleMenuPath = (menuTree: MenuNode[] | undefined, currentUser: CurrentUser): string | null => {
  const walk = (nodes: MenuNode[] = []): string | null => {
    for (const node of nodes) {
      if (node.path && canVisitPath(node.path, currentUser)) {
        return node.path;
      }
      const childPath = walk(node.children || []);
      if (childPath) {
        return childPath;
      }
    }

    return null;
  };

  return walk(menuTree);
};

export const resolveAuthorizedLoginRedirectTarget = (
  search: string,
  currentUser: CurrentUser,
  menuTree?: MenuNode[],
  fallback = DEFAULT_HOME_PATH,
) => {
  const preferredFallback = currentUser.defaultHomePath?.trim() || fallback;
  const redirectTarget = resolveLoginRedirectTarget(search, preferredFallback);
  if (canVisitPath(redirectTarget, currentUser)) {
    return redirectTarget;
  }

  if (canVisitPath(preferredFallback, currentUser)) {
    return preferredFallback;
  }

  const menuTarget = findFirstAccessibleMenuPath(menuTree, currentUser);
  if (menuTarget) {
    return menuTarget;
  }

  return canVisitPath(fallback, currentUser) ? fallback : '/403';
};

export const isAuthTokenStorageEvent = (event: Pick<StorageEvent, 'key' | 'newValue'>) =>
  event.key === AUTH_TOKEN_STORAGE_KEY && Boolean(event.newValue);

export const createLoginStorageHandler = (redirectTarget: string, onNavigate: (target: string) => void) => {
  return (event: Pick<StorageEvent, 'key' | 'newValue'>) => {
    if (!isAuthTokenStorageEvent(event)) {
      return;
    }

    onNavigate(redirectTarget);
  };
};
