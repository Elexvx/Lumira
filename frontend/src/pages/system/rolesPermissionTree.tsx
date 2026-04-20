import { realPageRouteMetaMap, realPageRoutePaths } from '@/routes/meta';
import type { PermissionActionRecord, PermissionRecord, PermissionTreeRecord } from '@/types/api';

export interface NormalizedPermissionTreeRecord extends Omit<PermissionTreeRecord, 'children'> {
  children?: NormalizedPermissionTreeRecord[];
  routeMatched?: boolean;
}

export interface RolePermissionDisplayItem {
  permissionKey: string;
  permissionName: string;
  isPagePermission: boolean;
}

export interface RolePermissionDisplayPage {
  pageKey: string;
  pageName: string;
  permissionGroup: string;
  routePath?: string;
  permissions: RolePermissionDisplayItem[];
}

export interface RolePermissionDisplayGroup {
  permissionGroup: string;
  pages: RolePermissionDisplayPage[];
}

const getNodeType = (node: PermissionTreeRecord): PermissionTreeRecord['nodeType'] => node.nodeType || 'PAGE';

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

    const routePath = node.routePath?.trim() || '';
    const routeMeta = routePath ? realPageRouteMetaMap.get(routePath) : undefined;
    const routeMatched = Boolean(routeMeta) || nodeType === 'CATALOG';
    const routeMismatch = nodeType === 'PAGE' && (!routePath || !allowedRoutePaths.has(routePath));
    const selectablePage = nodeType === 'PAGE' && Boolean(node.selectable && node.permissionKey && !routeMismatch);

    if (selectablePage && routePath) {
      if (seenRoutePaths.has(routePath)) {
        return;
      }
      seenRoutePaths.add(routePath);
    }

    if (!selectablePage && !children.length && nodeType !== 'CATALOG' && !routeMismatch) {
      return;
    }

    result.push({
      ...node,
      nodeType,
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

export const buildPermissionTreeData = (
  nodes: NormalizedPermissionTreeRecord[],
): Array<NormalizedPermissionTreeRecord & { key: string; title: JSX.Element; disableCheckbox: boolean }> =>
  nodes.map((node) => ({
    ...node,
    key: node.pageKey,
    disableCheckbox: node.nodeType === 'PAGE' ? !node.selectable : !node.children?.length,
    selectable: node.selectable,
    title: (
      <div className={`role-page-row${node.routeMatched ? '' : ' role-page-row--mismatch'}`}>
        <span className="role-page-row__name">{node.pageName}</span>
        <span className="role-page-row__meta">
          {node.nodeType === 'CATALOG' ? <span className="role-page-row__kind">目录</span> : null}
          {node.nodeType === 'PAGE' && node.routePath ? (
            <span className={`role-page-row__route${node.routeMatched ? '' : ' role-page-row__route--mismatch'}`}>
              {node.routePath}
            </span>
          ) : null}
          {node.nodeType === 'PAGE' && !node.routeMatched ? (
            <span className="role-page-row__hint role-page-row__hint--mismatch">路由失配</span>
          ) : null}
          {node.nodeType === 'CATALOG' ? (
            <span className="role-page-row__hint role-page-row__hint--catalog">仅作目录分组</span>
          ) : null}
        </span>
      </div>
    ),
    children: node.children?.length ? buildPermissionTreeData(node.children) : undefined,
  }));

export const collectSelectablePages = (
  nodes: NormalizedPermissionTreeRecord[],
  result: NormalizedPermissionTreeRecord[] = [],
) => {
  nodes.forEach((node) => {
    if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
      result.push(node);
    }
    if (node.children?.length) {
      collectSelectablePages(node.children, result);
    }
  });
  return result;
};

export const collectSelectablePageNodeMap = (
  nodes: NormalizedPermissionTreeRecord[],
  result = new Map<string, NormalizedPermissionTreeRecord>(),
) => {
  nodes.forEach((node) => {
    if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
      result.set(node.pageKey, node);
    }
    if (node.children?.length) {
      collectSelectablePageNodeMap(node.children, result);
    }
  });
  return result;
};

export const collectPermissionKeyToPageKeyMap = (
  nodes: NormalizedPermissionTreeRecord[],
  result = new Map<string, string[]>(),
) => {
  nodes.forEach((node) => {
    if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
      const pageKeys = result.get(node.permissionKey) ?? [];
      if (!pageKeys.includes(node.pageKey)) {
        pageKeys.push(node.pageKey);
      }
      result.set(node.permissionKey, pageKeys);
    }
    if (node.children?.length) {
      collectPermissionKeyToPageKeyMap(node.children, result);
    }
  });
  return result;
};

export const collectActionPermissionPageMap = (
  nodes: NormalizedPermissionTreeRecord[],
  result = new Map<string, string>(),
) => {
  nodes.forEach((node) => {
    if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
      node.actionPermissions?.forEach((action: PermissionActionRecord) => {
        if (action.permissionKey) {
          result.set(action.permissionKey, node.permissionKey as string);
        }
      });
    }
    if (node.children?.length) {
      collectActionPermissionPageMap(node.children, result);
    }
  });
  return result;
};

export const collectExpandableKeys = (nodes: NormalizedPermissionTreeRecord[], result: string[] = []) => {
  nodes.forEach((node) => {
    if (node.children?.length) {
      result.push(node.pageKey);
      collectExpandableKeys(node.children, result);
    }
  });
  return result;
};

export const normalizePermissionKeysByPages = (
  currentPermissionKeys: string[],
  nextPageKeys: string[],
  allPageKeys: Set<string>,
  actionPermissionPageMap: Map<string, string>,
) => {
  const nextPageKeySet = new Set(nextPageKeys);
  const nextPermissionKeys = new Set<string>();

  currentPermissionKeys.forEach((permissionKey) => {
    if (nextPageKeySet.has(permissionKey)) {
      nextPermissionKeys.add(permissionKey);
      return;
    }

    const pageKey = actionPermissionPageMap.get(permissionKey);
    if (pageKey) {
      if (nextPageKeySet.has(pageKey)) {
        nextPermissionKeys.add(permissionKey);
      }
      return;
    }

    if (!allPageKeys.has(permissionKey)) {
      nextPermissionKeys.add(permissionKey);
    }
  });

  nextPageKeys.forEach((permissionKey) => {
    nextPermissionKeys.add(permissionKey);
  });

  return Array.from(nextPermissionKeys);
};

const resolvePermissionGroup = (
  nodePermissionGroup: string | undefined,
  permissionKey: string | undefined,
  permissionCatalogMap: Map<string, PermissionRecord>,
) => {
  if (nodePermissionGroup?.trim()) {
    return nodePermissionGroup.trim();
  }
  if (permissionKey && permissionCatalogMap.has(permissionKey)) {
    const catalogGroup = permissionCatalogMap.get(permissionKey)?.permissionGroup?.trim();
    if (catalogGroup) {
      return catalogGroup;
    }
  }
  return permissionKey?.split(':')[0] || 'other';
};

const resolvePermissionName = (
  permissionKey: string,
  fallbackName: string,
  permissionCatalogMap: Map<string, PermissionRecord>,
) => permissionCatalogMap.get(permissionKey)?.permissionName || fallbackName || permissionKey;

export const buildRolePermissionDisplayGroups = (
  nodes: NormalizedPermissionTreeRecord[],
  selectedPermissionKeys: string[],
  permissionCatalogMap: Map<string, PermissionRecord> = new Map(),
) => {
  const selectedPermissionKeySet = new Set(selectedPermissionKeys);
  const groupMap = new Map<
    string,
    Map<string, RolePermissionDisplayPage>
  >();
  const seenPermissionKeys = new Set<string>();

  const addPermission = (
    permissionGroup: string,
    pageKey: string,
    pageName: string,
    routePath: string | undefined,
    permissionKey: string,
    permissionName: string,
    isPagePermission: boolean,
  ) => {
    seenPermissionKeys.add(permissionKey);
    const pageMap = groupMap.get(permissionGroup) ?? new Map();
    const page: RolePermissionDisplayPage = pageMap.get(pageKey) ?? {
      pageKey,
      pageName,
      permissionGroup,
      routePath,
      permissions: [],
    };

    if (!page.routePath && routePath) {
      page.routePath = routePath;
    }

    if (!page.permissions.some((item) => item.permissionKey === permissionKey)) {
      page.permissions.push({
        permissionKey,
        permissionName,
        isPagePermission,
      });
    }

    pageMap.set(pageKey, page);
    groupMap.set(permissionGroup, pageMap);
  };

  const visit = (items: NormalizedPermissionTreeRecord[]) => {
    items.forEach((node) => {
      if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
        const groupKey = resolvePermissionGroup(node.permissionGroup, node.permissionKey, permissionCatalogMap);
        const pageName = node.pageName || resolvePermissionName(node.permissionKey, node.pageName || node.permissionKey, permissionCatalogMap);

        if (selectedPermissionKeySet.has(node.permissionKey)) {
          addPermission(
            groupKey,
            node.pageKey,
            pageName,
            node.routePath,
            node.permissionKey,
            resolvePermissionName(node.permissionKey, pageName, permissionCatalogMap),
            true,
          );
        }

        node.actionPermissions?.forEach((action: PermissionActionRecord) => {
          if (!action.permissionKey || !selectedPermissionKeySet.has(action.permissionKey)) {
            return;
          }
          addPermission(
            groupKey,
            node.pageKey,
            pageName,
            node.routePath,
            action.permissionKey,
            resolvePermissionName(action.permissionKey, action.permissionName, permissionCatalogMap),
            false,
          );
        });
      }

      if (node.children?.length) {
        visit(node.children);
      }
    });
  };

  visit(nodes);

  selectedPermissionKeys.forEach((permissionKey) => {
    if (seenPermissionKeys.has(permissionKey)) {
      return;
    }

    const catalog = permissionCatalogMap.get(permissionKey);
    const groupKey = resolvePermissionGroup(catalog?.permissionGroup, permissionKey, permissionCatalogMap);
    const permissionName = resolvePermissionName(permissionKey, catalog?.permissionName || permissionKey, permissionCatalogMap);
    addPermission(groupKey, permissionKey, permissionName, undefined, permissionKey, permissionName, true);
  });

  return Array.from(groupMap.entries())
    .map(([permissionGroup, pageMap]) => ({
      permissionGroup,
      pages: Array.from(pageMap.values()).sort((left, right) => left.pageName.localeCompare(right.pageName, 'zh-Hans-CN')),
    }))
    .sort((left, right) => left.permissionGroup.localeCompare(right.permissionGroup, 'zh-Hans-CN'));
};
