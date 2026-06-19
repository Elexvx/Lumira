import { ProTable, type ProColumns, type ProTableProps } from '@ant-design/pro-components';
import { Button, type TablePaginationConfig, type TableProps } from 'antd';
import { DEFAULT_TABLE_PAGE_SIZE } from '@/features/table/proTableRequest';

type ManagementTableOptions = Exclude<ProTableProps<object, Record<string, unknown>>['options'], false | undefined>;

const DEFAULT_MANAGEMENT_TABLE_OPTIONS: ManagementTableOptions = {
  reload: false,
  density: true,
  setting: true,
};

type MobilePagination = TablePaginationConfig | false | undefined;

const TABLE_HORIZONTAL_SCROLL_WIDTH_THRESHOLD = 1100;

const parseColumnWidth = (width: ProColumns['width']) => {
  if (typeof width === 'number') {
    return width;
  }
  if (typeof width === 'string' && /^\d+$/.test(width)) {
    return Number(width);
  }
  return undefined;
};

const buildTableScroll = <RecordType extends object>(columns: ProColumns<RecordType>[], isMobile: boolean) => {
  const hasFixedColumn = columns.some((column) => Boolean(column.fixed));
  const estimatedWidth = columns.reduce((sum, column) => {
    if (typeof column.width === 'number') {
      return sum + column.width;
    }
    if (typeof column.width === 'string' && /^\d+$/.test(column.width)) {
      return sum + Number(column.width);
    }
    if (column.valueType === 'option') {
      return sum + 160;
    }
    if (column.valueType === 'index') {
      return sum + 72;
    }
    if (column.ellipsis) {
      return sum + 160;
    }
    return sum + 120;
  }, 0);

  return hasFixedColumn || estimatedWidth >= TABLE_HORIZONTAL_SCROLL_WIDTH_THRESHOLD || isMobile ? { x: 'max-content' } : undefined;
};

const buildAutoWidthScroll = <RecordType extends object>(
  scroll: TableProps<RecordType>['scroll'] | undefined,
  fallbackScroll: TableProps<RecordType>['scroll'] | undefined,
) => {
  const resolvedScroll = scroll ?? fallbackScroll;
  if (!resolvedScroll) {
    return { x: 'max-content' };
  }

  return {
    ...resolvedScroll,
    x: 'max-content',
  };
};

const buildAutoWidthColumns = <RecordType extends object>(columns: ProColumns<RecordType>[]): ProColumns<RecordType>[] =>
  columns.map((column) => {
    const children = Array.isArray(column.children) ? buildAutoWidthColumns(column.children as ProColumns<RecordType>[]) : column.children;
    const isFixedColumn = Boolean(column.fixed);
    const isActionColumn = column.valueType === 'option';
    const isIndexColumn = column.valueType === 'index';

    if (isFixedColumn || isActionColumn || isIndexColumn) {
      return {
        ...column,
        children,
      };
    }

    const width = parseColumnWidth(column.width);
    return {
      ...column,
      children,
      width: undefined,
      minWidth: column.minWidth ?? width,
      ellipsis: false,
    };
  });

const buildMobilePagination = (pagination: MobilePagination | boolean, isMobile: boolean): MobilePagination => {
  if (!pagination || !isMobile) {
    return pagination as MobilePagination;
  }

  if (pagination === true) {
    return { simple: true, showSizeChanger: false, pageSize: DEFAULT_TABLE_PAGE_SIZE };
  }

  if (typeof pagination !== 'object') {
    return pagination as MobilePagination;
  }

  return {
    ...pagination,
    pageSize: pagination.pageSize ?? DEFAULT_TABLE_PAGE_SIZE,
    simple: true,
    showSizeChanger: false,
  } as MobilePagination;
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
): ProTableProps<RecordType, Params>['options'] => {
  if (options === false) {
    return false;
  }

  const mergedOptions = {
    ...DEFAULT_MANAGEMENT_TABLE_OPTIONS,
    ...(options || {}),
  } as Exclude<ProTableProps<RecordType, Params>['options'], false | undefined>;

  return mergedOptions;
};

const buildManagementToolbar = <
  RecordType extends object,
  Params extends Record<string, unknown>,
>(
  toolBarRender: ManagementTableProps<RecordType, Params>['toolBarRender'],
  onRefresh?: ManagementTableProps<RecordType, Params>['onRefresh'],
): ManagementTableProps<RecordType, Params>['toolBarRender'] => {
  if (toolBarRender !== undefined || !onRefresh) {
    return toolBarRender;
  }

  return () => [
    <Button key="refresh" onClick={() => void onRefresh()}>
      Refresh
    </Button>,
  ];
};

export const ManagementTable = <RecordType extends object = object, Params extends Record<string, unknown> = Record<string, unknown>>({
  columns,
  isMobile,
  pagination = { showSizeChanger: true, pageSize: DEFAULT_TABLE_PAGE_SIZE },
  options,
  onRefresh,
  scroll,
  toolBarRender,
  ...props
}: ManagementTableProps<RecordType, Params>) => (
  <div className="saas-table-wrap">
    <ProTable<RecordType, Params>
      {...props}
      columns={buildAutoWidthColumns(columns)}
      options={buildManagementTableOptions(options)}
      pagination={buildMobilePagination(pagination, isMobile) as ProTableProps<RecordType, Params>['pagination']}
      scroll={buildAutoWidthScroll(scroll, buildTableScroll(columns, isMobile))}
      tableLayout="auto"
      toolBarRender={buildManagementToolbar(toolBarRender, onRefresh)}
    />
  </div>
);
