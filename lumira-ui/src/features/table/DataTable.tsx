import { Table, type TableProps } from 'antd';
import { TableSurface } from './TableSurface';
import { normalizeTablePagination } from './tablePagination';

export interface DataTableProps<RecordType extends object> extends TableProps<RecordType> {
  adaptiveSpacing?: boolean;
  isMobile: boolean;
  surfaceClassName?: string;
}

export const DataTable = <RecordType extends object>({
  adaptiveSpacing = false,
  isMobile,
  pagination,
  surfaceClassName,
  ...props
}: DataTableProps<RecordType>) => (
  <TableSurface adaptiveSpacing={adaptiveSpacing} className={surfaceClassName}>
    <Table<RecordType>
      {...props}
      pagination={normalizeTablePagination(pagination, isMobile) as TableProps<RecordType>['pagination']}
    />
  </TableSurface>
);
