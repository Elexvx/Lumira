import { ProTable, useIntl, type ActionType, type ProColumns, type ProTableProps } from '@ant-design/pro-components';
import { ReloadOutlined } from '@ant-design/icons';
import { Button, type TableProps } from 'antd';
import { isValidElement, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { DEFAULT_TABLE_PAGE_SIZE } from '@/features/table/proTableRequest';
import { normalizeTablePagination } from '@/features/table/tablePagination';
import { TableSurface } from '@/features/table/TableSurface';
import { isResponsiveColumnVisible, resolveEstimatedTableScrollX } from './managementTableLayout';

type ManagementTableOptions = Exclude<ProTableProps<object, Record<string, unknown>>['options'], false | undefined>;

const DEFAULT_MANAGEMENT_TABLE_OPTIONS: ManagementTableOptions = {
  reload: false,
  density: true,
  setting: true,
};

const TABLE_HORIZONTAL_SCROLL_WIDTH_THRESHOLD = 1100;
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
  const scrollX = resolveEstimatedTableScrollX(estimatedWidth, hasFixedColumn, isMobile);

  return scrollX === undefined ? undefined : { x: scrollX };
};

const buildAutoWidthScroll = <RecordType extends object>(
  scroll: TableProps<RecordType>['scroll'] | undefined,
  fallbackScroll: TableProps<RecordType>['scroll'] | undefined,
  autoContentWidth: boolean,
) => {
  const resolvedScroll = scroll ?? fallbackScroll;
  const fallbackX = fallbackScroll?.x;
  if (!resolvedScroll) {
    return undefined;
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

export interface ManagementTableProps<RecordType extends object = object, Params extends Record<string, unknown> = Record<string, unknown>>
  extends ProTableProps<RecordType, Params> {
  columns: ProColumns<RecordType>[];
  isMobile: boolean;
  autoContentWidth?: boolean;
  adaptiveSpacing?: boolean;
  containerResponsive?: boolean;
  surfaceClassName?: string;
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
  hasRequest = false,
): ManagementTableProps<RecordType, Params>['toolBarRender'] => {
  if (toolBarRender === false) {
    return false;
  }
  if (!onRefresh && !hasRequest) {
    return toolBarRender;
  }
  return (action, rows) => {
    const customActions: ReactNode[] =
      typeof toolBarRender === 'function' ? toolBarRender(action, rows) || [] : [];
    const hasCustomRefresh = customActions.some(
      (item) => isValidElement(item) && (item.key === 'refresh' || item.key === 'reload'),
    );

    if (hasCustomRefresh) {
      return customActions;
    }

    return [
      <ManagementTableRefreshButton key="management-table-refresh" action={action} onRefresh={onRefresh} />,
      ...customActions,
    ];
  };
};

const ManagementTableRefreshButton = ({
  action,
  onRefresh,
}: {
  action?: ActionType;
  onRefresh?: () => void | Promise<unknown>;
}) => {
  const intl = useIntl();
  const [loading, setLoading] = useState(false);

  const handleRefresh = async () => {
    setLoading(true);
    try {
      if (onRefresh) {
        await onRefresh();
      } else {
        await action?.reload?.();
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Button key="management-table-refresh" icon={<ReloadOutlined />} loading={loading} onClick={() => void handleRefresh()}>
      {intl.getMessage('tableToolBar.reload', '刷新')}
    </Button>
  );
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
  request,
  scroll,
  surfaceClassName,
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
  const normalizedColumns = useMemo(
    () => normalizeFixedColumnOrder(buildAutoWidthColumns(visibleColumns, autoContentWidth)),
    [autoContentWidth, visibleColumns],
  );
  const normalizedOptions = useMemo(
    () => buildManagementTableOptions(options),
    [options],
  );
  const normalizedPagination = useMemo(
    () => normalizeTablePagination(pagination, isMobile) as ProTableProps<RecordType, Params>['pagination'],
    [isMobile, pagination],
  );
  const normalizedScroll = useMemo(
    () => buildAutoWidthScroll(scroll, buildTableScroll(visibleColumns, isMobile), autoContentWidth),
    [autoContentWidth, isMobile, scroll, visibleColumns],
  );
  const normalizedToolbar = useMemo(
    () => buildManagementToolbar(toolBarRender, onRefresh, Boolean(request)),
    [onRefresh, request, toolBarRender],
  );

  return (
    <TableSurface ref={wrapperRef} adaptiveSpacing={adaptiveSpacing} className={surfaceClassName}>
      <ProTable<RecordType, Params>
        {...props}
        columns={normalizedColumns}
        options={normalizedOptions}
        pagination={normalizedPagination}
        request={request}
        scroll={normalizedScroll}
        tableLayout={tableLayout}
        toolBarRender={normalizedToolbar}
      />
    </TableSurface>
  );
};
