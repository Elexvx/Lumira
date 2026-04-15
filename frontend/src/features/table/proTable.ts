import type { ProColumns } from '@ant-design/pro-components';

export interface PageRequestPayload {
  pageNo?: number;
  pageSize?: number;
  [key: string]: unknown;
}

export interface PagedResponse<RecordType> {
  records: RecordType[];
  total: number;
}

export const buildMobilePagination = <T extends { simple?: boolean; showSizeChanger?: boolean } | boolean | undefined>(
  pagination: T,
  isMobile: boolean,
) => {
  if (!pagination || !isMobile) {
    return pagination;
  }

  if (pagination === true) {
    return { simple: true, showSizeChanger: false };
  }

  if (typeof pagination !== 'object') {
    return pagination;
  }

  return {
    ...pagination,
    simple: true,
    showSizeChanger: false,
  };
};

export const buildTableScroll = <RecordType extends object>(
  columns: ProColumns<RecordType>[],
  isMobile: boolean,
  options?: { wide?: boolean; fallbackX?: number | string },
) => {
  if (isMobile) {
    return undefined;
  }

  if (options?.wide) {
    return { x: options.fallbackX || 'max-content' };
  }

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
    return sum;
  }, 0);

  return estimatedWidth > 0 ? { x: estimatedWidth } : undefined;
};

export const buildTableRequest = <RecordType, Params extends PageRequestPayload = PageRequestPayload>(
  request: (params: Params) => Promise<PagedResponse<RecordType>>,
) => {
  return async (params: Record<string, unknown>) => {
    const { current, pageSize, ...rest } = params;
    const result = await request({
      pageNo: Number(current) || 1,
      pageSize: Number(pageSize) || 20,
      ...(rest as Omit<Params, 'pageNo' | 'pageSize'>),
    } as Params);

    return {
      data: result.records,
      success: true,
      total: result.total,
    };
  };
};
