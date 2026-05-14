import { PageContainer } from '@ant-design/pro-components';
import { useLocation } from '@umijs/max';
import type { ComponentProps } from 'react';
import { buildBreadcrumbItems } from '@/app.breadcrumb';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';

type PageContainerProps = ComponentProps<typeof PageContainer>;

const mergeClassName = (...classNames: Array<string | undefined>) => classNames.filter(Boolean).join(' ');

export const ManagementPage = ({ breadcrumb, className, children, ...props }: PageContainerProps) => {
  const location = useLocation();
  const { initialState } = useInitialStateModel();
  const dynamicBreadcrumbItems = buildBreadcrumbItems(initialState?.menuTree, location.pathname);
  const dynamicBreadcrumb = dynamicBreadcrumbItems.length ? { items: dynamicBreadcrumbItems } : undefined;

  return (
    <PageContainer
      {...props}
      breadcrumb={breadcrumb ?? dynamicBreadcrumb}
      className={mergeClassName('saas-management-page', className)}
    >
      {children}
    </PageContainer>
  );
};
