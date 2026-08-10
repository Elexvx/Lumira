export interface RuntimeMenuIdentityItem {
  key?: string | number;
}

interface ResolveRuntimeMenuIdentityOptions<T extends RuntimeMenuIdentityItem> {
  backendPath: string;
  canonicalPath: string;
  localByPath: Map<string, T>;
  stableKeyByPath?: Record<string, string>;
}

const normalizeIdentityPath = (path: string) => {
  const pathname = path.trim().split('?')[0].split('#')[0];
  return pathname.replace(/\/+$/, '') || '/';
};

interface ResolveRuntimeMenuPathOptions {
  backendPath: string;
  canonicalPath: string;
  component?: string;
  hasChildren: boolean;
  stableKeyByPath?: Record<string, string>;
  keepPathlessGroup?: boolean;
}

interface ResolveRuntimeMenuHideInMenuOptions {
  backendPath: string;
  canonicalPath: string;
  localHideInMenu?: boolean;
  routeHideInMenu?: boolean;
  stableKeyByPath?: Record<string, string>;
}

/**
 * Umi can propagate `hideInMenu` from a same-path redirect child to its parent
 * catalog. Stable main-menu catalogs are explicitly visible navigation groups,
 * so redirect metadata must not hide the group itself.
 */
export const resolveRuntimeMenuHideInMenu = ({
  backendPath,
  canonicalPath,
  localHideInMenu,
  routeHideInMenu,
  stableKeyByPath = {},
}: ResolveRuntimeMenuHideInMenuOptions) => {
  const normalizedBackendPath = normalizeIdentityPath(backendPath);
  const isStableCatalog = Boolean(
    stableKeyByPath[backendPath]
    || stableKeyByPath[normalizedBackendPath]
    || stableKeyByPath[canonicalPath],
  );

  return isStableCatalog ? false : Boolean(localHideInMenu || routeHideInMenu);
};

/**
 * Redirect catalogs still need their own path when they are stable navigation
 * groups. Otherwise the parent becomes pathless and can disappear when a menu
 * renderer deduplicates it against its canonical child.
 */
export const resolveRuntimeMenuPath = ({
  backendPath,
  canonicalPath,
  component,
  hasChildren,
  stableKeyByPath = {},
  keepPathlessGroup = false,
}: ResolveRuntimeMenuPathOptions) => {
  const normalizedBackendPath = normalizeIdentityPath(backendPath);
  const isRedirectGroup = hasChildren && Boolean(component?.startsWith('redirect:'));
  const isStableCatalog = Boolean(
    stableKeyByPath[backendPath]
    || stableKeyByPath[normalizedBackendPath],
  );

  if (isRedirectGroup && isStableCatalog) {
    return normalizedBackendPath;
  }
  if (isRedirectGroup || (keepPathlessGroup && hasChildren)) {
    return undefined;
  }
  return canonicalPath || normalizedBackendPath;
};

/**
 * Route aliases describe navigation targets, not menu identity. A catalog such
 * as `/certificates` may redirect to `/certificates/mine`, but it must still
 * keep the catalog's own key and metadata or it will collide with the leaf.
 */
export const resolveRuntimeMenuIdentity = <T extends RuntimeMenuIdentityItem>({
  backendPath,
  canonicalPath,
  localByPath,
  stableKeyByPath = {},
}: ResolveRuntimeMenuIdentityOptions<T>) => {
  const normalizedBackendPath = normalizeIdentityPath(backendPath);
  const directLocalItem = localByPath.get(backendPath) || localByPath.get(normalizedBackendPath);
  const localItem = directLocalItem || localByPath.get(canonicalPath);
  const localKey = localItem?.key;

  return {
    localItem,
    key:
      stableKeyByPath[backendPath]
      || stableKeyByPath[normalizedBackendPath]
      || stableKeyByPath[canonicalPath]
      || (localKey === undefined ? undefined : String(localKey)),
  };
};
