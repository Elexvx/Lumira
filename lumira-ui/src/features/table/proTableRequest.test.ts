import { describe, expect, it } from 'vitest';
import { buildTableRequest, type PageRequestPayload, type PagedResponse } from './proTableRequest';

interface TestRecord {
  id: number;
}

interface TestParams extends PageRequestPayload {
  keyword?: string;
}

describe('buildTableRequest', () => {
  it('translates ProTable pagination into the backend page contract', async () => {
    let capturedParams: TestParams | undefined;
    const request = async (params: TestParams): Promise<PagedResponse<TestRecord>> => {
      capturedParams = params;
      return { records: [{ id: 1 }], total: 7 };
    };

    const tableRequest = buildTableRequest<TestRecord, TestParams>(request);

    await expect(tableRequest({ current: 3, pageSize: 25, keyword: 'alpha' })).resolves.toEqual({
      data: [{ id: 1 }],
      success: true,
      total: 7,
    });
    expect(capturedParams).toEqual({ pageNo: 3, pageSize: 25, keyword: 'alpha' });
  });

  it('applies shared defaults when ProTable omits pagination values', async () => {
    let capturedParams: PageRequestPayload | undefined;
    const request = async (params: PageRequestPayload): Promise<PagedResponse<TestRecord>> => {
      capturedParams = params;
      return { records: [], total: 0 };
    };

    const tableRequest = buildTableRequest<TestRecord>(request);
    await tableRequest({});

    expect(capturedParams).toEqual({ pageNo: 1, pageSize: 10 });
  });
});
