import { formatMessage } from '@umijs/max';
import type { BreadcrumbProps } from 'antd';
import { backendRouteMeta } from '@/routes/meta';
import { resolveBuiltinMessage } from '@/i18n/messages';
import type { MenuNode } from '@/types/api';

type BreadcrumbItem = NonNullable<BreadcrumbProps['items']>[number];

const routeMetaMap = new Map(backendRouteMeta.map((item) => [item.path, item]));

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
  if (!menuNodes?.length) {
    return [];
  }

  const trail = findMenuTrail(menuNodes, pathname);
  if (!trail.length) {
    return [];
  }

  return trail.map((node, index) => {
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
};
