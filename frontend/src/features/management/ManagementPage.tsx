import { PageContainer } from '@ant-design/pro-components';
import type { ComponentProps } from 'react';

type PageContainerProps = ComponentProps<typeof PageContainer>;

const mergeClassName = (...classNames: Array<string | undefined>) => classNames.filter(Boolean).join(' ');

export const ManagementPage = ({ className, children, ...props }: PageContainerProps) => (
  <PageContainer {...props} className={mergeClassName('saas-management-page', className)}>
    {children}
  </PageContainer>
);
