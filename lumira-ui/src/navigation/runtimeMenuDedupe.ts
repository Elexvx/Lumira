export interface RuntimeMenuLike {
  key?: string | number;
  path?: string;
  children?: RuntimeMenuLike[];
}

const normalizeMenuIdentityPath = (path: string) =>
  path.trim().replace(/\/+$/, '') || '/';

/**
 * ProLayout builds its final menu after combining route metadata with the
 * server-provided tree. A path can therefore reappear even when the backend
 * tree was already de-duplicated. Keep the first visible placement and hoist
 * any unique descendants from a discarded duplicate group.
 */
export const dedupeRuntimeMenuItems = <T extends RuntimeMenuLike>(
  items: T[],
  seenPaths = new Set<string>(),
  seenKeys = new Set<string>(),
): T[] =>
  items.flatMap((item) => {
    const pathIdentity = item.path ? normalizeMenuIdentityPath(item.path) : undefined;
    const keyIdentity = item.key === undefined ? undefined : String(item.key);
    const duplicate =
      Boolean(pathIdentity && seenPaths.has(pathIdentity))
      || Boolean(keyIdentity && seenKeys.has(keyIdentity));

    if (!duplicate) {
      if (pathIdentity) {
        seenPaths.add(pathIdentity);
      }
      if (keyIdentity) {
        seenKeys.add(keyIdentity);
      }
    }

    const children = item.children?.length
      ? dedupeRuntimeMenuItems(item.children as T[], seenPaths, seenKeys)
      : [];

    if (duplicate) {
      return children;
    }

    return [{
      ...item,
      children: children.length ? children : undefined,
    } as T];
  });
