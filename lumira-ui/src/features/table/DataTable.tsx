import type { ProColumns } from '@ant-design/pro-components';
import type { TableProps } from 'antd';
import { ManagementTable, type ManagementTableProps } from '@/features/management/ManagementTable';

export interface DataTableProps<RecordType extends object>
  extends Omit<ManagementTableProps<RecordType>, 'columns' | 'isMobile' | 'pagination' | 'size'> {
  columns: NonNullable<TableProps<RecordType>['columns']>;
  isMobile: boolean;
  pagination?: TableProps<RecordType>['pagination'];
  size?: TableProps<RecordType>['size'];
}

export const DataTable = <RecordType extends object>({
  columns,
  defaultSize,
  isMobile,
  options,
  pagination,
  search,
  size,
  toolBarRender,
  ...props
}: DataTableProps<RecordType>) => (
  <ManagementTable<RecordType>
    {...props}
    columns={columns as unknown as ProColumns<RecordType>[]}
    defaultSize={defaultSize ?? size ?? 'middle'}
    isMobile={isMobile}
    options={options ?? false}
    pagination={pagination}
    search={search ?? false}
    toolBarRender={toolBarRender ?? false}
  />
);
