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
