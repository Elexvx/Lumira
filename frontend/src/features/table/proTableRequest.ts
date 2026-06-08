export interface PageRequestPayload {
  pageNo?: number;
  pageSize?: number;
  [key: string]: unknown;
}

export interface PagedResponse<RecordType> {
  records: RecordType[];
  total: number;
}

export const DEFAULT_TABLE_PAGE_SIZE = 10;

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

export const buildTableRequest = <RecordType, Params extends PageRequestPayload = PageRequestPayload>(
  request: (params: Params, sorter?: Record<string, unknown>, filter?: Record<string, unknown>) => Promise<PagedResponse<RecordType>>,
) => {
  return async (params: Record<string, unknown>, sorter?: Record<string, unknown>, filter?: Record<string, unknown>) => {
    const { current, pageSize, ...rest } = params;
    const result = await request(
      {
        pageNo: Number(current) || 1,
        pageSize: Number(pageSize) || DEFAULT_TABLE_PAGE_SIZE,
        ...(rest as Omit<Params, 'pageNo' | 'pageSize'>),
      } as Params,
      sorter,
      filter,
    );

    return adaptPageResult(result);
  };
};
