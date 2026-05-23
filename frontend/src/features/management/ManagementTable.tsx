import { ProTable, type ProColumns, type ProTableProps } from '@ant-design/pro-components';
import { buildMobilePagination, buildTableScroll } from '@/features/table/proTable';

type ManagementTableOptions = Exclude<ProTableProps<object, Record<string, unknown>>['options'], false | undefined>;

const DEFAULT_MANAGEMENT_TABLE_OPTIONS: ManagementTableOptions = {
  reload: true,
  density: true,
  setting: true,
};

export interface ManagementTableProps<RecordType extends object = object, Params extends Record<string, unknown> = Record<string, unknown>>
  extends ProTableProps<RecordType, Params> {
  columns: ProColumns<RecordType>[];
  isMobile: boolean;
  onRefresh?: () => void | Promise<unknown>;
}

const buildManagementTableOptions = <
  RecordType extends object,
  Params extends Record<string, unknown>,
>(
  options: ManagementTableProps<RecordType, Params>['options'],
  onRefresh?: ManagementTableProps<RecordType, Params>['onRefresh'],
): ProTableProps<RecordType, Params>['options'] => {
  if (options === false) {
    return false;
  }

  const mergedOptions = {
    ...DEFAULT_MANAGEMENT_TABLE_OPTIONS,
    ...(options || {}),
  } as Exclude<ProTableProps<RecordType, Params>['options'], false | undefined>;

  if (onRefresh) {
    mergedOptions.reload = () => {
      void onRefresh();
    };
  }

  return mergedOptions;
};

export const ManagementTable = <RecordType extends object = object, Params extends Record<string, unknown> = Record<string, unknown>>({
  columns,
  isMobile,
  pagination = { showSizeChanger: true },
  options,
  onRefresh,
  scroll,
  ...props
}: ManagementTableProps<RecordType, Params>) => (
  <div className="saas-table-wrap">
    <ProTable<RecordType, Params>
      {...props}
      columns={columns}
      options={buildManagementTableOptions(options, onRefresh)}
      pagination={buildMobilePagination(pagination, isMobile) as ProTableProps<RecordType, Params>['pagination']}
      scroll={scroll ?? buildTableScroll(columns, isMobile)}
    />
  </div>
);
