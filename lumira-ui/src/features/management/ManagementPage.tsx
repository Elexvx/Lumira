import { PageContainer } from '@ant-design/pro-components';
import { useLocation } from '@umijs/max';
import type { ComponentProps } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { buildBreadcrumbItems } from './managementBreadcrumb';

type PageContainerProps = ComponentProps<typeof PageContainer>;
const mergeClassName = (...classNames: Array<string | undefined>) => classNames.filter(Boolean).join(' ');

export const ManagementPage = ({ breadcrumb, className, children, ...props }: PageContainerProps) => {
  const location = useLocation();
  const { initialState } = useInitialStateModel();
  const responsive = useResponsive();
  const dynamicBreadcrumbItems = buildBreadcrumbItems(initialState?.menuTree, location.pathname);
  const dynamicBreadcrumb = dynamicBreadcrumbItems.length ? { items: dynamicBreadcrumbItems } : undefined;
  const pageContainerToken = {
    paddingInlinePageContainerContent: responsive.profile.pageGutter,
    paddingBlockPageContainerContent: responsive.profile.pageSectionGap,
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
