import { DEFAULT_HOME_PATH, LOGIN_PATH } from '@/app.constants';
import buildAccess from '@/access';
import { getCurrentRoleDefaultHomePath } from '@/auth/defaultHomePath';
import { isLoginInProgress } from '@/auth/loginFlowState';
import { AUTH_SESSION_BROADCAST_CHANNEL, tokenManager } from '@/auth/token';
import { realPageRouteMetaList, resolveCanonicalRoutePath } from '@/routes/meta';
import type { CurrentUser, MenuNode } from '@/types/api';

const STABLE_ACCESSIBLE_FALLBACK_PATHS = [
  '/competitions/register',
  '/activities/register',
  '/dashboard/home',
  '/data-management',
  '/certificates/templates',
  '/certificates/generate',
  '/certificates/records',
  '/experts/management',
  '/workflows/tasks',
  '/user-center/personal-center/profile',
  '/user-center/personal-center/files',
] as const;

export const resolveLoginRedirectTarget = (search: string, fallback = DEFAULT_HOME_PATH) => {
  const redirect = new URLSearchParams(search).get('redirect')?.trim();
  if (
    !redirect
    || redirect === LOGIN_PATH
    || !redirect.startsWith('/')
    || redirect.startsWith('//')
    || redirect.startsWith('/\\')
  ) {
    return fallback;
  }

  return redirect;
};

export const resolveLoginPageRuntimeRedirectTarget = ({
  pathname,
  search,
  isAuthenticated,
  forcePasswordChangeRequested = false,
}: {
  pathname: string;
  search: string;
  isAuthenticated: boolean;
  forcePasswordChangeRequested?: boolean;
}) => {
  if (pathname === LOGIN_PATH && !isAuthenticated && !forcePasswordChangeRequested) {
    return DEFAULT_HOME_PATH;
  }

  return resolveLoginRedirectTarget(search);
};

const normalizePathname = (target: string) => {
  const raw = target.split('?')[0].split('#')[0];
  const normalized = resolveCanonicalRoutePath(raw || DEFAULT_HOME_PATH);
  return normalized || DEFAULT_HOME_PATH;
};

const normalizeTargetWithOriginalQuery = (target: string) => {
  if (!target.startsWith('/')) {
    return target;
  }

  const [pathAndQuery, ...hashParts] = target.split('#');
  const hashSuffix = hashParts.length ? `#${hashParts.join('#')}` : '';
  const [pathname, ...queryParts] = pathAndQuery.split('?');
  const querySuffix = queryParts.length ? `?${queryParts.join('?')}` : '';
  return `${resolveCanonicalRoutePath(pathname)}${querySuffix}${hashSuffix}`;
};

const routePathMatches = (routePath: string, pathname: string) => {
  const pattern = routePath
    .replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    .replace(/:([^/]+)/g, '[^/]+');

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

const findFirstAccessibleStableFallbackPath = (currentUser: CurrentUser): string | null => {
  for (const candidatePath of STABLE_ACCESSIBLE_FALLBACK_PATHS) {
    if (canVisitPath(candidatePath, currentUser)) {
      return candidatePath;
    }
  }

  return null;
};

const findFirstAccessibleRoutePath = (currentUser: CurrentUser): string | null => {
  for (const routeMeta of realPageRouteMetaList) {
    if (!routeMeta.access || routeMeta.hideInMenu || routeMeta.path === LOGIN_PATH || routeMeta.path.includes('/:')) {
      continue;
    }
    if (canVisitPath(routeMeta.path, currentUser)) {
      return routeMeta.path;
    }
  }

  return null;
};

export const resolveAuthorizedLoginRedirectTarget = (
  search: string,
  currentUser: CurrentUser,
  menuTree?: MenuNode[],
  fallback = DEFAULT_HOME_PATH,
) => {
  const preferredFallback = resolveCanonicalRoutePath(getCurrentRoleDefaultHomePath(currentUser, fallback));
  const redirectTarget = resolveLoginRedirectTarget(search, preferredFallback);
  const canonicalRedirectTarget = normalizeTargetWithOriginalQuery(redirectTarget);
  if (canVisitPath(canonicalRedirectTarget, currentUser)) {
    return canonicalRedirectTarget;
  }

  if (canVisitPath(preferredFallback, currentUser)) {
    return preferredFallback;
  }

  const menuTarget = findFirstAccessibleMenuPath(menuTree, currentUser);
  if (menuTarget) {
    return resolveCanonicalRoutePath(menuTarget);
  }

  const stableFallbackTarget = findFirstAccessibleStableFallbackPath(currentUser);
  if (stableFallbackTarget) {
    return resolveCanonicalRoutePath(stableFallbackTarget);
  }

  const routeTarget = findFirstAccessibleRoutePath(currentUser);
  if (routeTarget) {
    return resolveCanonicalRoutePath(routeTarget);
  }

  const canonicalFallback = resolveCanonicalRoutePath(fallback);
  return canVisitPath(canonicalFallback, currentUser) ? canonicalFallback : '/403';
};

export const createLoginSessionBroadcastListener = (
  redirectTarget: string,
  onNavigate: (target: string) => void,
  shouldNavigate: () => boolean = () => true,
) => {
  if (typeof BroadcastChannel === 'undefined') {
    return () => {};
  }
  const channel = new BroadcastChannel(AUTH_SESSION_BROADCAST_CHANNEL);
  channel.onmessage = (event: MessageEvent<{ type?: string }>) => {
    if (event.data?.type !== 'updated') {
      return;
    }
    if (isLoginInProgress()) {
      return;
    }
    if (!shouldNavigate()) {
      return;
    }

    tokenManager.syncFromStorage();
    onNavigate(redirectTarget);
  };
  return () => channel.close();
};
