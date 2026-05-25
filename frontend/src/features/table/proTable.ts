import type { ProColumns } from '@ant-design/pro-components';
import type { TablePaginationConfig } from 'antd';

const TABLE_HORIZONTAL_SCROLL_WIDTH_THRESHOLD = 1100;

export interface PageRequestPayload {
  pageNo?: number;
  pageSize?: number;
  [key: string]: unknown;
}

export interface PagedResponse<RecordType> {
  records: RecordType[];
  total: number;
}

export interface ProTableResponse<RecordType> {
  data: RecordType[];
  success: boolean;
  total: number;
}

export const adaptPageResult = <RecordType>(result: PagedResponse<RecordType>): ProTableResponse<RecordType> => ({
  data: result.records,
  success: true,
  total: result.total,
});

type MobilePagination = TablePaginationConfig | false | undefined;

export const buildMobilePagination = (
  pagination: MobilePagination | boolean,
  isMobile: boolean,
) : MobilePagination => {
  if (!pagination || !isMobile) {
    return pagination as MobilePagination;
  }

  if (pagination === true) {
    return { simple: true, showSizeChanger: false };
  }

  if (typeof pagination !== 'object') {
    return pagination as MobilePagination;
  }

  return {
    ...pagination,
    simple: true,
    showSizeChanger: false,
  } as MobilePagination;
};

export const buildTableScroll = <RecordType extends object>(
  columns: ProColumns<RecordType>[],
  isMobile: boolean,
) => {
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

export const buildTableRequest = <RecordType, Params extends PageRequestPayload = PageRequestPayload>(
  request: (
    params: Params,
    sorter?: Record<string, unknown>,
    filter?: Record<string, unknown>,
  ) => Promise<PagedResponse<RecordType>>,
) => {
  return async (params: Record<string, unknown>, sorter?: Record<string, unknown>, filter?: Record<string, unknown>) => {
    const { current, pageSize, ...rest } = params;
    const result = await request(
      {
        pageNo: Number(current) || 1,
        pageSize: Number(pageSize) || 20,
        ...(rest as Omit<Params, 'pageNo' | 'pageSize'>),
      } as Params,
      sorter,
      filter,
    );

    return adaptPageResult(result);
  };
};
