import type { ProColumns } from '@ant-design/pro-components';
import type { ReactNode } from 'react';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import type { ManagementDrawerProps, ManagementTableProps } from '@/features/management';
import { useResponsive } from '@/hooks/useResponsive';

interface SiteAdminPageProps {
  title: string;
  extra?: ReactNode;
  children: ReactNode;
}

const SiteAdminPage = ({ title, extra, children }: SiteAdminPageProps) => (
  <ManagementPage title={title} extra={extra} className="site-admin-page-container">
    <div className="site-admin-page">{children}</div>
  </ManagementPage>
);

type SiteAdminTableProps<RecordType extends object> = Omit<
  ManagementTableProps<RecordType>,
  'columns' | 'isMobile'
> & {
  columns: ProColumns<RecordType>[];
};

export const SiteAdminTable = <RecordType extends object>({
  search = false,
  columns,
  ...props
}: SiteAdminTableProps<RecordType>) => {
  const responsive = useResponsive();

  return (
    <ManagementTable<RecordType>
      {...props}
      columns={columns}
      isMobile={responsive.isMobile}
      search={search}
    />
  );
};

export const SiteAdminDrawer = (props: ManagementDrawerProps) => <ManagementDrawer {...props} />;

export default SiteAdminPage;
