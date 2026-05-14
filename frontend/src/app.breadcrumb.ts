import { formatMessage } from '@umijs/max';
import type { BreadcrumbProps } from 'antd';
import { backendRouteMeta } from '@/routes/meta';
import { resolveBuiltinMessage } from '@/i18n/messages';
import type { MenuNode } from '@/types/api';

type BreadcrumbItem = NonNullable<BreadcrumbProps['items']>[number];

const routeMetaMap = new Map(backendRouteMeta.map((item) => [item.path, item]));

const resolveRouteMetaTitle = (path: string) => {
  const meta = routeMetaMap.get(path);
  if (!meta) {
    return path;
  }

  return resolveBuiltinMessage(
    meta.name,
    formatMessage({
      id: meta.name,
      defaultMessage: meta.name,
    }),
  );
};

const buildRouteMetaTrail = (pathname: string): BreadcrumbItem[] => {
  const matchedRoutes = backendRouteMeta
    .filter((item) => pathname === item.path || pathname.startsWith(`${item.path}/`))
    .sort((left, right) => left.path.length - right.path.length);

  return matchedRoutes.map((item, index) => ({
    key: item.path,
    title: resolveRouteMetaTitle(item.path),
    path: index === matchedRoutes.length - 1 ? undefined : item.path,
  }));
};

const findMenuTrail = (menuNodes: MenuNode[], pathname: string): MenuNode[] => {
  for (const node of menuNodes) {
    const children = node.children || [];
    const childTrail = children.length ? findMenuTrail(children, pathname) : [];
    if (childTrail.length) {
      return [node, ...childTrail];
    }
    if (node.path === pathname) {
      return [node];
    }
  }

  return [];
};

export const buildBreadcrumbItems = (menuNodes: MenuNode[] | undefined, pathname: string): BreadcrumbItem[] => {
  if (pathname.startsWith('/settings')) {
    return [];
  }
  const routeMetaTrail = buildRouteMetaTrail(pathname);
  if (!menuNodes?.length) {
    return routeMetaTrail;
  }

  const trail = findMenuTrail(menuNodes, pathname);
  if (!trail.length) {
    return routeMetaTrail;
  }

  const menuTrail = trail.map((node, index) => {
    const messageId = routeMetaMap.get(node.path || '')?.name || node.name || node.path || '';
    const fallback = node.name || node.path || '';
    return {
      key: node.path || String(node.id),
      title: resolveBuiltinMessage(
        messageId,
        formatMessage({
          id: messageId,
          defaultMessage: fallback,
        }),
      ),
      path: index === trail.length - 1 ? undefined : node.path,
    };
  });

  return menuTrail.length >= routeMetaTrail.length ? menuTrail : routeMetaTrail;
};
