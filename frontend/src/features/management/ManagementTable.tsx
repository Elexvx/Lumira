import { ProTable, type ProColumns, type ProTableProps } from '@ant-design/pro-components';
import { buildMobilePagination, buildTableScroll } from '@/features/table/proTable';

export interface ManagementTableProps<RecordType extends object = object, Params extends Record<string, unknown> = Record<string, unknown>>
  extends ProTableProps<RecordType, Params> {
  columns: ProColumns<RecordType>[];
  isMobile: boolean;
}

export const ManagementTable = <RecordType extends object = object, Params extends Record<string, unknown> = Record<string, unknown>>({
  columns,
  isMobile,
  pagination = { showSizeChanger: true },
  options = false,
  scroll,
  ...props
}: ManagementTableProps<RecordType, Params>) => (
  <div className="saas-table-wrap">
    <ProTable<RecordType, Params>
      {...props}
      columns={columns}
      options={options}
      pagination={buildMobilePagination(pagination, isMobile) as ProTableProps<RecordType, Params>['pagination']}
      scroll={scroll ?? buildTableScroll(columns, isMobile)}
    />
  </div>
);
