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

const CATALOG_LABEL_BY_PAGE_KEY = new Map<string, string>([
  ['-1100', 'nav.data.management'],
  ['data.management.root', 'nav.data.management'],
  ['-1101', 'nav.data.queryCenter'],
  ['data.query-center', 'nav.data.queryCenter'],
  ['-1069', 'nav.competitions.register'],
  ['registration.root', 'nav.competitions.register'],
  ['-1070', 'nav.competitions.root'],
  ['competition.root', 'nav.competitions.root'],
  ['-1079', 'nav.certificates.root'],
  ['certificate.root', 'nav.certificates.root'],
  ['-1060', 'nav.experts.root'],
  ['expert.root', 'nav.experts.root'],
  ['-950', 'nav.user.center'],
  ['user.center.root', 'nav.user.center'],
  ['-940', 'nav.user.personalCenter'],
  ['user.center.personal', 'nav.user.personalCenter'],
]);

const CATALOG_LABEL_BY_RAW_NAME = new Map<string, string>([
  ['Data Management', 'nav.data.management'],
  ['数据管理', 'nav.data.management'],
  ['Query Center', 'nav.data.queryCenter'],
  ['查询中心', 'nav.data.queryCenter'],
  ['查询中心', 'nav.data.queryCenter'],
  ['Registration', 'nav.competitions.register'],
  ['报名', 'nav.competitions.register'],
  ['Competitions', 'nav.competitions.root'],
  ['赛事', 'nav.competitions.root'],
  ['Certificate Management', 'nav.certificates.root'],
  ['证书管理', 'nav.certificates.root'],
  ['证书管理', 'nav.certificates.root'],
  ['Expert Library', 'nav.experts.root'],
  ['Expert library', 'nav.experts.root'],
  ['专家库', 'nav.experts.root'],
  ['User Center', 'nav.user.center'],
  ['用户中心', 'nav.user.center'],
  ['用户中心', 'nav.user.center'],
  ['Personal Center', 'nav.user.personalCenter'],
  ['个人中心', 'nav.user.personalCenter'],
  ['个人中心', 'nav.user.personalCenter'],
]);

const resolveCatalogMessageKey = (node: PermissionTreeRecord) => {
  const pageKeyMessageKey = node.pageKey ? CATALOG_LABEL_BY_PAGE_KEY.get(node.pageKey) : undefined;
  if (pageKeyMessageKey) {
    return pageKeyMessageKey;
  }

  const rawName = node.pageName?.trim();
  const rawNameMessageKey = rawName ? CATALOG_LABEL_BY_RAW_NAME.get(rawName) : undefined;
  if (rawNameMessageKey) {
    return rawNameMessageKey;
  }

  return node.permissionKey ? CATALOG_LABEL_BY_PERMISSION_KEY.get(node.permissionKey) : undefined;
};

const INFERRED_PAGE_PERMISSIONS = new Map<
  string,
  {
    permissionKey: string;
    actionPermissions?: Array<{ permissionKey: string; permissionName: string }>;
  }
>([
  [
    '/competitions/register',
    {
      permissionKey: 'aiadc:registration:view',
      actionPermissions: [
        { permissionKey: 'aiadc:registration:create', permissionName: '创建赛事报名' },
        { permissionKey: 'aiadc:registration:update', permissionName: '编辑赛事报名' },
        { permissionKey: 'aiadc:registration:pay', permissionName: '支付报名费用' },
        { permissionKey: 'aiadc:project:view', permissionName: 'View projects for registration' },
        { permissionKey: 'aiadc:project:create', permissionName: 'Create projects for registration' },
      ],
    },
  ],
  [
    '/activities/register',
    {
      permissionKey: 'aiadc:activity:view',
      actionPermissions: [
        { permissionKey: 'aiadc:activity:create', permissionName: '创建活动报名' },
      ],
    },
  ],
]);

const resolveCanonicalPageName = (node: PermissionTreeRecord, routePath: string, nodeType: PermissionTreeRecord['nodeType']) => {
  const catalogMessageKey = nodeType === 'CATALOG' ? resolveCatalogMessageKey(node) : undefined;
  if (catalogMessageKey) {
    return resolveBuiltinMessage(catalogMessageKey, node.pageName);
  }

  const routeMeta = routePath ? realPageRouteMetaMap.get(routePath) ?? backendRouteMetaMap.get(routePath) : undefined;
  if (routeMeta?.name) {
    return resolveBuiltinMessage(routeMeta.name, node.pageName);
  }

  const permissionCatalogMessageKey = resolveCatalogMessageKey(node);
  if (permissionCatalogMessageKey) {
    return resolveBuiltinMessage(permissionCatalogMessageKey, node.pageName);
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
  injectSyntheticPages = true,
): NormalizedPermissionTreeRecord[] => {
  const result: NormalizedPermissionTreeRecord[] = [];

  nodes.forEach((node) => {
    const nodeType = getNodeType(node);
    const children = node.children?.length
      ? normalizePermissionTree(node.children, allowedRoutePaths, seenRoutePaths, false)
      : [];

    if (nodeType === 'ALIAS') {
      if (children.length) {
        result.push(...children);
      }
      return;
    }

    const routePath = node.routePath ? resolveCanonicalRoutePath(node.routePath) : '';
    const routeMeta = routePath ? realPageRouteMetaMap.get(routePath) : undefined;
    const inferredPermission = routePath ? INFERRED_PAGE_PERMISSIONS.get(routePath) : undefined;
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

    const effectivePermissionKey = node.permissionKey || inferredPermission?.permissionKey;
    const effectiveActionPermissions = [
      ...(node.actionPermissions || []),
      ...((inferredPermission?.actionPermissions || []).filter(
        (action) => !(node.actionPermissions || []).some((existing) => existing.permissionKey === action.permissionKey),
      )),
    ];
    const selectablePage = nodeType === 'PAGE' && Boolean(node.selectable && effectivePermissionKey && !routeMismatch);

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
      pageName: resolveCanonicalPageName(node, routePath, nodeType),
      selectable: selectablePage,
      routePath: nodeType === 'PAGE' ? routePath : undefined,
      routeMatched,
      permissionKey: effectivePermissionKey,
      actionPermissions: selectablePage ? effectiveActionPermissions : undefined,
      children: children.length ? children : undefined,
    });
  });

  if (injectSyntheticPages) {
    const syntheticCompetitionChildren: NormalizedPermissionTreeRecord[] = [];

    if (!seenRoutePaths.has('/competitions/register')) {
      seenRoutePaths.add('/competitions/register');
      syntheticCompetitionChildren.push({
        nodeType: 'PAGE',
        pageKey: 'competition.registration.synthetic',
        pageName: resolveBuiltinMessage('nav.competitions.register', '赛事报名'),
        routePath: '/competitions/register',
        permissionKey: 'aiadc:registration:view',
        permissionGroup: 'aiadc',
        selectable: true,
        routeMatched: true,
        actionPermissions: [
          { permissionKey: 'aiadc:registration:create', permissionName: '创建赛事报名' },
          { permissionKey: 'aiadc:registration:update', permissionName: '编辑赛事报名' },
          { permissionKey: 'aiadc:registration:pay', permissionName: '支付报名费用' },
          { permissionKey: 'aiadc:project:view', permissionName: 'View projects for registration' },
          { permissionKey: 'aiadc:project:create', permissionName: 'Create projects for registration' },
        ],
      });
    }

    if (!seenRoutePaths.has('/activities/register')) {
      seenRoutePaths.add('/activities/register');
      syntheticCompetitionChildren.push({
        nodeType: 'PAGE',
        pageKey: 'activity.registration.synthetic',
        pageName: resolveBuiltinMessage('nav.activities.activityRegister', '\u6d3b\u52a8\u62a5\u540d'),
        routePath: '/activities/register',
        permissionKey: 'aiadc:activity:view',
        permissionGroup: 'aiadc',
        selectable: true,
        routeMatched: true,
        actionPermissions: [
          { permissionKey: 'aiadc:activity:create', permissionName: '创建活动报名' },
        ],
      });
    }

    if (syntheticCompetitionChildren.length) {
      result.push({
        nodeType: 'CATALOG',
        pageKey: 'competition.synthetic.catalog',
        pageName: resolveBuiltinMessage('nav.competitions.root', '赛事'),
        selectable: false,
        routeMatched: true,
        children: syntheticCompetitionChildren,
      });
    }
  }

  return result;
};



