import { ProTable, type ProColumns, type ProTableProps } from '@ant-design/pro-components';
import { Button, type TablePaginationConfig, type TableProps } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { DEFAULT_TABLE_PAGE_SIZE } from '@/features/table/proTableRequest';
import { isResponsiveColumnVisible } from './managementTableLayout';

type ManagementTableOptions = Exclude<ProTableProps<object, Record<string, unknown>>['options'], false | undefined>;

const DEFAULT_MANAGEMENT_TABLE_OPTIONS: ManagementTableOptions = {
  reload: false,
  density: true,
  setting: true,
};

type MobilePagination = TablePaginationConfig | false | undefined;
type TablePagination = Exclude<MobilePagination, false | undefined>;

const TABLE_HORIZONTAL_SCROLL_WIDTH_THRESHOLD = 1100;
const TABLE_HORIZONTAL_SCROLL_BUFFER = 1;
const DEFAULT_DATA_COLUMN_WIDTH = 160;

const parseColumnWidth = (width: ProColumns['width']) => {
  if (typeof width === 'number') {
    return width;
  }
  if (typeof width === 'string' && /^\d+$/.test(width)) {
    return Number(width);
  }
  if (typeof width === 'string') {
    const spacingValue = width.match(/^var\(--saas-spacing-(\d+)\)$/)?.[1];
    return spacingValue ? Number(spacingValue) : undefined;
  }
  return undefined;
};

const estimateTableWidth = <RecordType extends object>(columns: ProColumns<RecordType>[]) =>
  columns.reduce((sum, column) => {
    const width = parseColumnWidth(column.width);
    if (width) {
      return sum + width;
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
    return sum + DEFAULT_DATA_COLUMN_WIDTH;
  }, 0);

const buildTableScroll = <RecordType extends object>(columns: ProColumns<RecordType>[], isMobile: boolean) => {
  const hasFixedColumn = columns.some((column) => Boolean(column.fixed));
  const estimatedWidth = estimateTableWidth(columns);
  const scrollX = Math.max(estimatedWidth + TABLE_HORIZONTAL_SCROLL_BUFFER, TABLE_HORIZONTAL_SCROLL_WIDTH_THRESHOLD);

  return hasFixedColumn || estimatedWidth >= TABLE_HORIZONTAL_SCROLL_WIDTH_THRESHOLD || isMobile ? { x: scrollX } : undefined;
};

const buildAutoWidthScroll = <RecordType extends object>(
  scroll: TableProps<RecordType>['scroll'] | undefined,
  fallbackScroll: TableProps<RecordType>['scroll'] | undefined,
  autoContentWidth: boolean,
) => {
  const resolvedScroll = scroll ?? fallbackScroll;
  const fallbackX = fallbackScroll?.x;
  if (!resolvedScroll) {
    return fallbackScroll ?? { x: TABLE_HORIZONTAL_SCROLL_WIDTH_THRESHOLD };
  }

  if (autoContentWidth && resolvedScroll.x === 'max-content') {
    return resolvedScroll;
  }

  return {
    ...resolvedScroll,
    x: resolvedScroll.x === 'max-content' || resolvedScroll.x === true || resolvedScroll.x === undefined ? fallbackX ?? TABLE_HORIZONTAL_SCROLL_WIDTH_THRESHOLD : resolvedScroll.x,
  };
};

const buildAutoWidthColumns = <RecordType extends object>(columns: ProColumns<RecordType>[], autoContentWidth = false): ProColumns<RecordType>[] =>
  columns.map((column) => {
    const children = Array.isArray(column.children) ? buildAutoWidthColumns(column.children as ProColumns<RecordType>[], autoContentWidth) : column.children;
    const isFixedColumn = Boolean(column.fixed);
    const isActionColumn = column.valueType === 'option';
    const isIndexColumn = column.valueType === 'index';

    if (isFixedColumn || isActionColumn || isIndexColumn) {
      const width = parseColumnWidth(column.width);
      return {
        ...column,
        align: isActionColumn ? 'center' : column.align,
        className: isActionColumn ? mergeClassName(column.className, 'saas-table-action-column') : column.className,
        fixed: isActionColumn ? column.fixed ?? 'right' : column.fixed,
        children,
        width: width ?? column.width,
      };
    }

    const width = parseColumnWidth(column.width);
    if (autoContentWidth && !width && column.width === undefined) {
      return {
        ...column,
        children,
        minWidth: column.minWidth ?? DEFAULT_DATA_COLUMN_WIDTH,
      };
    }

    const normalizedWidth = width ?? column.minWidth ?? DEFAULT_DATA_COLUMN_WIDTH;
    return {
      ...column,
      children,
      width: normalizedWidth,
      minWidth: column.minWidth ?? normalizedWidth,
    };
  });

const mergeClassName = (className: ProColumns['className'], nextClassName: string) => {
  if (typeof className !== 'string' || !className.trim()) {
    return nextClassName;
  }
  const classNames = className.split(/\s+/);
  return classNames.includes(nextClassName) ? className : `${className} ${nextClassName}`;
};

const normalizeFixedColumnOrder = <RecordType extends object>(columns: ProColumns<RecordType>[]): ProColumns<RecordType>[] => {
  const leadingIndexColumns: ProColumns<RecordType>[] = [];
  const leftFixedColumns: ProColumns<RecordType>[] = [];
  const regularColumns: ProColumns<RecordType>[] = [];
  const rightFixedColumns: ProColumns<RecordType>[] = [];

  columns.forEach((column) => {
    if (column.valueType === 'index') {
      leadingIndexColumns.push({ ...column, fixed: undefined });
      return;
    }
    if (column.fixed === 'left') {
      leftFixedColumns.push(column);
      return;
    }
    if (column.fixed === 'right') {
      rightFixedColumns.push(column);
      return;
    }
    regularColumns.push(column);
  });

  return [...leadingIndexColumns, ...leftFixedColumns, ...regularColumns, ...rightFixedColumns];
};

const filterColumnsByContainerWidth = <RecordType extends object>(
  columns: ProColumns<RecordType>[],
  containerWidth: number,
): ProColumns<RecordType>[] =>
  columns
    .filter((column) => isResponsiveColumnVisible(column.responsive, containerWidth))
    .map((column) => ({
      ...column,
      children: Array.isArray(column.children)
        ? filterColumnsByContainerWidth(column.children as ProColumns<RecordType>[], containerWidth)
        : column.children,
    }));

const buildMobilePagination = (pagination: MobilePagination | boolean, isMobile: boolean): MobilePagination => {
  if (!pagination || !isMobile) {
    return normalizePaginationDefaults(pagination as MobilePagination);
  }

  if (pagination === true) {
    return { simple: true, showSizeChanger: false, defaultPageSize: DEFAULT_TABLE_PAGE_SIZE };
  }

  if (typeof pagination !== 'object') {
    return pagination as MobilePagination;
  }

  return normalizePaginationDefaults({
    ...pagination,
    defaultPageSize: pagination.defaultPageSize ?? pagination.pageSize ?? DEFAULT_TABLE_PAGE_SIZE,
    simple: true,
    showSizeChanger: false,
  });
};

const normalizePaginationDefaults = (pagination: MobilePagination): MobilePagination => {
  if (!pagination || typeof pagination !== 'object') {
    return pagination;
  }

  const { pageSize, defaultPageSize, current, ...rest } = pagination;
  const controlsCurrentPage = current !== undefined;
  const normalized: TablePagination = {
    ...rest,
    ...(controlsCurrentPage ? { current } : {}),
    ...(controlsCurrentPage ? { pageSize } : {}),
    defaultPageSize: defaultPageSize ?? pageSize ?? DEFAULT_TABLE_PAGE_SIZE,
  };

  return normalized;
};

export interface ManagementTableProps<RecordType extends object = object, Params extends Record<string, unknown> = Record<string, unknown>>
  extends ProTableProps<RecordType, Params> {
  columns: ProColumns<RecordType>[];
  isMobile: boolean;
  autoContentWidth?: boolean;
  adaptiveSpacing?: boolean;
  containerResponsive?: boolean;
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

  return {
    ...mergedOptions,
    setting:
      mergedOptions.setting === false
        ? false
        : {
            ...(typeof mergedOptions.setting === 'object' ? mergedOptions.setting : {}),
            draggable: false,
          },
  };
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
  autoContentWidth = false,
  adaptiveSpacing = false,
  columns,
  containerResponsive = false,
  isMobile,
  pagination = { showSizeChanger: true, defaultPageSize: DEFAULT_TABLE_PAGE_SIZE },
  options,
  onRefresh,
  scroll,
  tableLayout,
  toolBarRender,
  ...props
}: ManagementTableProps<RecordType, Params>) => {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const [containerWidth, setContainerWidth] = useState(0);

  useEffect(() => {
    if (!containerResponsive || !wrapperRef.current || typeof ResizeObserver === 'undefined') {
      return undefined;
    }

    const observer = new ResizeObserver(([entry]) => {
      const nextWidth = Math.round(entry?.contentRect.width || 0);
      setContainerWidth((currentWidth) => (currentWidth === nextWidth ? currentWidth : nextWidth));
    });
    observer.observe(wrapperRef.current);
    return () => observer.disconnect();
  }, [containerResponsive]);

  const visibleColumns = useMemo(
    () => containerResponsive ? filterColumnsByContainerWidth(columns, containerWidth) : columns,
    [columns, containerResponsive, containerWidth],
  );
  const wrapperClassName = adaptiveSpacing
    ? 'saas-table-wrap saas-table-wrap--adaptive-spacing'
    : 'saas-table-wrap';

  return (
    <div ref={wrapperRef} className={wrapperClassName}>
      <ProTable<RecordType, Params>
        {...props}
        columns={normalizeFixedColumnOrder(buildAutoWidthColumns(visibleColumns, autoContentWidth))}
        options={buildManagementTableOptions(options)}
        pagination={buildMobilePagination(pagination, isMobile) as ProTableProps<RecordType, Params>['pagination']}
        scroll={buildAutoWidthScroll(scroll, buildTableScroll(visibleColumns, isMobile), autoContentWidth)}
        tableLayout={tableLayout}
        toolBarRender={buildManagementToolbar(toolBarRender, onRefresh)}
      />
    </div>
  );
};
