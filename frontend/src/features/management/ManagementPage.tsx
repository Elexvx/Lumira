import { PageContainer } from '@ant-design/pro-components';
import { formatMessage, useLocation } from '@umijs/max';
import type { BreadcrumbProps } from 'antd';
import type { ComponentProps } from 'react';
import { backendRouteMeta } from '@/routes/meta';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { resolveBuiltinMessage } from '@/i18n/messages';
import type { MenuNode } from '@/types/api';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

type PageContainerProps = ComponentProps<typeof PageContainer>;
type BreadcrumbItem = NonNullable<BreadcrumbProps['items']>[number];

const routeMetaMap = new Map(backendRouteMeta.map((item) => [item.path, item]));

const mergeClassName = (...classNames: Array<string | undefined>) => classNames.filter(Boolean).join(' ');

const normalizeBreadcrumbPath = (path: string | null | undefined, id: number) => path || `#menu-${id}`;

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
      path: index === trail.length - 1 ? undefined : normalizeBreadcrumbPath(node.path, node.id),
    };
  });

  return menuTrail;
};

export const ManagementPage = ({ breadcrumb, className, children, ...props }: PageContainerProps) => {
  const location = useLocation();
  const { initialState } = useInitialStateModel();
  const responsive = useResponsive();
  const dynamicBreadcrumbItems = buildBreadcrumbItems(initialState?.menuTree, location.pathname);
  const dynamicBreadcrumb = dynamicBreadcrumbItems.length ? { items: dynamicBreadcrumbItems } : undefined;
  const pageContainerToken = {
    paddingInlinePageContainerContent: resolveResponsiveValue(APP_SPACING.pageContainerPaddingInline, responsive.isMobile),
    paddingBlockPageContainerContent: resolveResponsiveValue(APP_SPACING.pageContainerPaddingBlock, responsive.isMobile),
    ...(props.token || {}),
  };

  return (
    <PageContainer
      {...props}
      breadcrumb={breadcrumb ?? dynamicBreadcrumb}
      token={pageContainerToken}
      className={mergeClassName('saas-management-page', className)}
    >
      {children}
    </PageContainer>
  );
};
