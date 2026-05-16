import { PageContainer } from '@ant-design/pro-components';
import type { ReactNode } from 'react';

interface SiteAdminPageProps {
  title: string;
  description: string;
  extra?: ReactNode;
  children: ReactNode;
}

const SiteAdminPage = ({ title, description, extra, children }: SiteAdminPageProps) => (
  <PageContainer title={title} content={description} extra={extra} className="site-admin-page-container">
    <div className="site-admin-page">{children}</div>
  </PageContainer>
);

export default SiteAdminPage;
