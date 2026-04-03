import { ProCard, ProTable } from '@ant-design/pro-components';
import { Empty, Pagination, Spin } from 'antd';
import type { ColumnsType, TablePaginationConfig, TableProps } from 'antd/es/table';
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useResponsive } from '@/hooks/useResponsive';
import { ApiRequestError } from '@/services/common/request';

export interface DataTablePageResult<T> {
  records: T[];
  total: number;
}

export interface DataTableRequestParams {
  current: number;
  pageSize: number;
  [key: string]: unknown;
}

export interface DataTableProps<T extends object> extends Omit<TableProps<T>, 'dataSource' | 'columns' | 'loading' | 'pagination' | 'footer'> {
  columns: ColumnsType<T>;
  dataSource?: T[];
  request?: (params: DataTableRequestParams) => Promise<DataTablePageResult<T> | T[]>;
  params?: Record<string, unknown>;
  loading?: boolean;
  pagination?: false | TablePaginationConfig;
  toolbar?: ReactNode;
  footer?: ReactNode;
  emptyText?: ReactNode;
  middleScroll?: boolean;
  mobileCardRender?: (record: T) => ReactNode;
}

const DEFAULT_PAGE_SIZE = 10;

export const DataTable = <T extends object>({
  columns,
  dataSource,
  request,
  params,
  loading,
  pagination,
  toolbar,
  footer,
  emptyText,
  middleScroll = true,
  mobileCardRender,
  rowKey = 'id',
  size = 'middle',
  className,
  style,
  scroll,
  ...tableProps
}: DataTableProps<T>) => {
  const { isMobile } = useResponsive();
  const rootRef = useRef<HTMLDivElement>(null);
  const toolbarRef = useRef<HTMLDivElement>(null);
  const paginationRef = useRef<HTMLDivElement>(null);
  const [tableScrollY, setTableScrollY] = useState<number>();
  const initialPagination = typeof pagination === 'object' && pagination ? pagination : undefined;
  const [pageState, setPageState] = useState<TablePaginationConfig>({
    current: initialPagination?.current || 1,
    pageSize: initialPagination?.pageSize || DEFAULT_PAGE_SIZE,
  });
  const [remoteRecords, setRemoteRecords] = useState<T[]>([]);
  const [remoteTotal, setRemoteTotal] = useState<number>(0);
  const [requestLoading, setRequestLoading] = useState(false);
  const [requestFailed, setRequestFailed] = useState(false);

  useEffect(() => {
    if (!request) {
      return;
    }
    let active = true;
    setRequestLoading(true);
    setRequestFailed(false);
    request({
      current: Number(pageState.current || 1),
      pageSize: Number(pageState.pageSize || DEFAULT_PAGE_SIZE),
      ...(params || {}),
    })
      .then((result) => {
        if (!active) {
          return;
        }
        if (Array.isArray(result)) {
          setRemoteRecords(result);
          setRemoteTotal(result.length);
          return;
        }
        setRemoteRecords(result.records);
        setRemoteTotal(result.total);
      })
      .catch((error) => {
        if (!active) {
          return;
        }
        setRemoteRecords([]);
        setRemoteTotal(0);
        if (!(error instanceof ApiRequestError)) {
          console.error(error);
        }
        setRequestFailed(true);
      })
      .finally(() => {
        if (active) {
          setRequestLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [pageState.current, pageState.pageSize, params, request]);

  useEffect(() => {
    if (!middleScroll) {
      return;
    }
    const updateHeight = () => {
      const root = rootRef.current;
      if (!root) {
        return;
      }
      const rootHeight = root.getBoundingClientRect().height;
      const toolbarHeight = toolbarRef.current?.getBoundingClientRect().height ?? 0;
      const paginationHeight = paginationRef.current?.getBoundingClientRect().height ?? 0;
      const available = Math.floor(rootHeight - toolbarHeight - paginationHeight - 16);
      setTableScrollY(Math.max(240, available));
    };

    updateHeight();
    const resizeObserver = new ResizeObserver(updateHeight);
    if (rootRef.current) {
      resizeObserver.observe(rootRef.current);
    }
    if (toolbarRef.current) {
      resizeObserver.observe(toolbarRef.current);
    }
    if (paginationRef.current) {
      resizeObserver.observe(paginationRef.current);
    }
    window.addEventListener('resize', updateHeight);
    return () => {
      resizeObserver.disconnect();
      window.removeEventListener('resize', updateHeight);
    };
  }, [middleScroll, remoteRecords.length, requestLoading, footer, toolbar]);

  const mergedLoading = loading || requestLoading;
  const records = request ? remoteRecords : dataSource || [];
  const total = request ? remoteTotal : records.length;
  const effectivePagination = typeof pagination === 'object' && pagination ? pagination : {};
  const shouldRenderCardList = Boolean(isMobile && mobileCardRender);
  const mergedClassName = ['saas-data-table', className].filter(Boolean).join(' ');

  const currentPagination = useMemo<TablePaginationConfig>(
    () => ({
      current: Number(pageState.current || 1),
      pageSize: Number(pageState.pageSize || DEFAULT_PAGE_SIZE),
      total,
      showSizeChanger: true,
      ...effectivePagination,
    }),
    [effectivePagination, pageState.current, pageState.pageSize, total],
  );

  return (
    <ProCard
      className={mergedClassName}
      style={{
        display: 'flex',
        flexDirection: 'column',
        minHeight: 0,
        height: '100%',
        ...style,
      }}
    >
      <div ref={rootRef} style={{ display: 'flex', flexDirection: 'column', minHeight: 0, height: '100%' }}>
        {toolbar ? (
          <div ref={toolbarRef} style={{ marginBottom: 16 }}>
            {toolbar}
          </div>
        ) : null}

        <div style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
          {mergedLoading ? (
            <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
              <Spin />
            </div>
          ) : records.length === 0 ? (
            <Empty description={requestFailed ? '数据加载失败，请稍后重试' : emptyText || '暂无数据'} />
          ) : shouldRenderCardList ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {records.map((record, index) => (
                <div key={String(typeof rowKey === 'string' ? (record as Record<string, unknown>)[rowKey] ?? index : index)}>
                  {mobileCardRender?.(record)}
                </div>
              ))}
            </div>
          ) : (
            <ProTable<T>
              {...(tableProps as Record<string, unknown>)}
              rowKey={rowKey}
              size={size}
              columns={columns as never}
              dataSource={records}
              loading={false}
              search={false}
              options={false}
              toolBarRender={false}
              cardBordered={false}
              tableAlertRender={false}
              tableAlertOptionRender={false}
              pagination={false}
              locale={{
                emptyText: emptyText || '暂无数据',
              }}
              scroll={{
                x: 'max-content',
                y: middleScroll ? tableScrollY : scroll?.y,
                ...scroll,
              }}
            />
          )}
        </div>

        {pagination === false ? null : (
          <div ref={paginationRef} style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
            <Pagination
              {...currentPagination}
              onChange={(current, pageSize) => {
                setPageState({ current, pageSize });
              }}
              onShowSizeChange={(current, pageSize) => {
                setPageState({ current, pageSize });
              }}
            />
          </div>
        )}

        {footer ? <div style={{ marginTop: 16 }}>{footer}</div> : null}
      </div>
    </ProCard>
  );
};
