import type { TablePaginationConfig } from 'antd';
import { DEFAULT_TABLE_PAGE_SIZE } from './proTableRequest';

export type TablePaginationInput = TablePaginationConfig | boolean | undefined;

export const normalizeTablePagination = (
  pagination: TablePaginationInput,
  isMobile: boolean,
): TablePaginationInput => {
  if (pagination === false || pagination === undefined) {
    return pagination;
  }

  if (pagination === true) {
    return isMobile
      ? { simple: true, showSizeChanger: false, defaultPageSize: DEFAULT_TABLE_PAGE_SIZE }
      : true;
  }

  const { pageSize, defaultPageSize, current, ...rest } = pagination;
  const controlsCurrentPage = current !== undefined;

  return {
    ...rest,
    ...(controlsCurrentPage ? { current, pageSize } : {}),
    defaultPageSize: defaultPageSize ?? pageSize ?? DEFAULT_TABLE_PAGE_SIZE,
    ...(isMobile ? { simple: true, showSizeChanger: false } : {}),
  };
};
