export interface RuntimeMenuIdentityItem {
  key?: string | number;
}

interface ResolveRuntimeMenuIdentityOptions<T extends RuntimeMenuIdentityItem> {
  backendPath: string;
  canonicalPath: string;
  localByPath: Map<string, T>;
  stableKeyByPath?: Record<string, string>;
}

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
  const directLocalItem = localByPath.get(backendPath);
  const localItem = directLocalItem || localByPath.get(canonicalPath);
  const localKey = localItem?.key;

  return {
    localItem,
    key:
      stableKeyByPath[backendPath]
      || stableKeyByPath[canonicalPath]
      || (localKey === undefined ? undefined : String(localKey)),
  };
};
