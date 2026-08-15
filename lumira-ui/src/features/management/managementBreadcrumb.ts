import type { BreadcrumbProps } from 'antd';
import { resolveBuiltinMessage } from '@/i18n/messages';
import { backendRouteMeta } from '@/routes/meta';
import type { MenuNode } from '@/types/api';

type BreadcrumbItem = NonNullable<BreadcrumbProps['items']>[number];

const routeMetaMap = new Map(backendRouteMeta.map((item) => [item.path, item]));
const DATA_MANAGEMENT_CERTIFICATE_PATHS = new Set([
  '/certificates/templates',
  '/certificates/generate',
  '/certificates/records',
]);

const normalizeBreadcrumbHref = (path: string | null | undefined, id: number) => path || `#menu-${id}`;

const resolveRouteMetaTitle = (path: string) => {
  const meta = routeMetaMap.get(path);
  return meta ? resolveBuiltinMessage(meta.name, meta.name) : path;
};

const buildRouteMetaTrail = (pathname: string): BreadcrumbItem[] => {
  const matchedRoutes = backendRouteMeta
    .filter((item) => pathname === item.path || pathname.startsWith(`${item.path}/`))
    .sort((left, right) => left.path.length - right.path.length);

  return matchedRoutes.map((item, index) => ({
    key: item.path,
    title: resolveRouteMetaTitle(item.path),
    href: index === matchedRoutes.length - 1 ? undefined : item.path,
  }));
};

const findMenuTrail = (menuNodes: MenuNode[], pathname: string): MenuNode[] => {
  for (const node of menuNodes) {
    const children = node.children || [];
    const childTrail = children.length ? findMenuTrail(children, pathname) : [];
    if (childTrail.length) return [node, ...childTrail];
    if (node.path === pathname) return [node];
  }
  return [];
};

export const buildBreadcrumbItems = (menuNodes: MenuNode[] | undefined, pathname: string): BreadcrumbItem[] => {
  if (pathname.startsWith('/settings')) return [];
  const routeMetaTrail = buildRouteMetaTrail(pathname);
  if (!menuNodes?.length) return routeMetaTrail;

  const trail = findMenuTrail(menuNodes, pathname);
  const shouldUseDataManagementCertificateTrail = DATA_MANAGEMENT_CERTIFICATE_PATHS.has(pathname)
    && (!trail.length || trail[0]?.path === '/certificates');
  if (shouldUseDataManagementCertificateTrail) {
    return [
      {
        key: '/data-management',
        title: resolveRouteMetaTitle('/data-management'),
        href: '/data-management',
      },
      {
        key: pathname,
        title: resolveRouteMetaTitle(pathname),
      },
    ];
  }
  if (!trail.length) return routeMetaTrail;

  return trail.map((node, index) => {
    const messageId = routeMetaMap.get(node.path || '')?.name || node.name || node.path || '';
    const fallback = node.name || node.path || '';
    return {
      key: node.path || String(node.id),
      title: resolveBuiltinMessage(messageId, fallback),
      href: index === trail.length - 1 ? undefined : normalizeBreadcrumbHref(node.path, node.id),
    };
  });
};
