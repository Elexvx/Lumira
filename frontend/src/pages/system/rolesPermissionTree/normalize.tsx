import type { PermissionTreeRecord } from '@/types/api';
import { backendRouteMetaMap, realPageRouteMetaMap, realPageRoutePaths, resolveCanonicalRoutePath } from '@/routes/meta';
import { resolveBuiltinMessage } from '@/i18n/messages';

const getNodeType = (node: PermissionTreeRecord): PermissionTreeRecord['nodeType'] => node.nodeType || 'PAGE';

const ROLE_PERMISSION_EXCLUDED_ROUTE_PREFIXES = ['/settings'];

const isRoleAssignableRoutePath = (routePath: string) => {
  const normalizedPath = routePath.trim();
  if (!normalizedPath) {
    return false;
  }
  return !ROLE_PERMISSION_EXCLUDED_ROUTE_PREFIXES.some(
    (prefix) => normalizedPath === prefix || normalizedPath.startsWith(`${prefix}/`),
  );
};

const CATALOG_LABEL_BY_PERMISSION_KEY = new Map<string, string>([
  ['user:center:view', 'nav.user.center'],
  ['profile:view', 'nav.user.personalCenter'],
]);

const resolveCanonicalPageName = (node: PermissionTreeRecord, routePath: string) => {
  const routeMeta = routePath ? realPageRouteMetaMap.get(routePath) ?? backendRouteMetaMap.get(routePath) : undefined;
  if (routeMeta?.name) {
    return resolveBuiltinMessage(routeMeta.name, node.pageName);
  }

  const catalogMessageKey = node.permissionKey ? CATALOG_LABEL_BY_PERMISSION_KEY.get(node.permissionKey) : undefined;
  if (catalogMessageKey) {
    return resolveBuiltinMessage(catalogMessageKey, node.pageName);
  }

  return node.pageName;
};

export interface NormalizedPermissionTreeRecord extends Omit<PermissionTreeRecord, 'children'> {
  children?: NormalizedPermissionTreeRecord[];
  routeMatched?: boolean;
}

export const normalizePermissionTree = (
  nodes: PermissionTreeRecord[],
  allowedRoutePaths: Set<string> = realPageRoutePaths,
  seenRoutePaths = new Set<string>(),
): NormalizedPermissionTreeRecord[] => {
  const result: NormalizedPermissionTreeRecord[] = [];

  nodes.forEach((node) => {
    const nodeType = getNodeType(node);
    const children = node.children?.length
      ? normalizePermissionTree(node.children, allowedRoutePaths, seenRoutePaths)
      : [];

    if (nodeType === 'ALIAS') {
      if (children.length) {
        result.push(...children);
      }
      return;
    }

    const routePath = node.routePath ? resolveCanonicalRoutePath(node.routePath) : '';
    const routeMeta = routePath ? realPageRouteMetaMap.get(routePath) : undefined;
    const routeMatched = Boolean(routeMeta) || nodeType === 'CATALOG';
    const routeMismatch = nodeType === 'PAGE' && (!routePath || !allowedRoutePaths.has(routePath));
    const routeExcluded = nodeType === 'PAGE' && Boolean(routePath) && !isRoleAssignableRoutePath(routePath);
    if (routeExcluded) {
      if (children.length) {
        result.push(...children);
      }
      return;
    }

    if (routeMismatch) {
      if (children.length) {
        result.push(...children);
      }
      return;
    }

    const selectablePage = nodeType === 'PAGE' && Boolean(node.selectable && node.permissionKey && !routeMismatch);

    if (selectablePage && routePath) {
      if (seenRoutePaths.has(routePath)) {
        return;
      }
      seenRoutePaths.add(routePath);
    }

    if (!selectablePage && !children.length) {
      return;
    }

    result.push({
      ...node,
      nodeType,
      pageName: resolveCanonicalPageName(node, routePath),
      selectable: selectablePage,
      routePath: nodeType === 'PAGE' ? routePath : undefined,
      routeMatched,
      permissionKey: node.permissionKey,
      actionPermissions: selectablePage ? node.actionPermissions : undefined,
      children: children.length ? children : undefined,
    });
  });

  return result;
};
