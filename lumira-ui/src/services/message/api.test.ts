import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
}));

vi.mock('@/services/common/request', () => ({
  request: mocks.request,
}));

describe('message api fallback wrappers', () => {
  beforeEach(() => {
    mocks.request.mockReset();
  });

  it('uses v2 endpoint first for unread-count', async () => {
    mocks.request.mockResolvedValueOnce({ unreadCount: 8 });

    const { requestMessageUnreadCount } = await import('@/services/message/api');
    const result = await requestMessageUnreadCount({ method: 'GET', autoRedirectOnUnauthorized: false, silent: true });

    expect(result).toEqual({ unreadCount: 8 });
    expect(mocks.request).toHaveBeenCalledTimes(1);
    expect(mocks.request).toHaveBeenNthCalledWith(1, '/v2/message/unread-count', {
      method: 'GET',
      autoRedirectOnUnauthorized: false,
      silent: true,
    });
  });

  it('falls back to legacy v1 message endpoint when v2 is unavailable', async () => {
    const legacyResponse = { unreadCount: 4 };
    mocks.request
      .mockRejectedValueOnce(new Error('v2 unavailable'))
      .mockResolvedValueOnce(legacyResponse);

    const { requestMessageUnreadCount } = await import('@/services/message/api');
    const result = await requestMessageUnreadCount({ method: 'GET', autoRedirectOnUnauthorized: false, silent: true });

    expect(result).toEqual(legacyResponse);
    expect(mocks.request).toHaveBeenCalledTimes(2);
    expect(mocks.request).toHaveBeenNthCalledWith(1, '/v2/message/unread-count', {
      method: 'GET',
      autoRedirectOnUnauthorized: false,
      silent: true,
    });
    expect(mocks.request).toHaveBeenNthCalledWith(2, '/v1/message/unread-count', {
      method: 'GET',
      autoRedirectOnUnauthorized: false,
      silent: true,
    });
  });

  it('falls back to legacy create endpoint when v2 is unavailable', async () => {
    const legacyMessage = {
      id: 100,
      tenantId: 1,
      messageType: 'MESSAGE',
      targetScope: 'TENANT',
      title: 'Welcome',
      content: 'Welcome to system',
      sourceType: 'MANUAL',
      publishStatus: 'PUBLISHED',
      createdAt: '2026-06-15T00:00:00Z',
    } as const;

    mocks.request
      .mockRejectedValueOnce(new Error('v2 unavailable'))
      .mockResolvedValueOnce(legacyMessage);

    const { requestMessageCreate } = await import('@/services/message/api');
    const result = await requestMessageCreate({
      method: 'POST',
      data: { title: 'Welcome', content: 'Welcome to system', channels: ['INBOX'], targetScope: 'TENANT' },
    });

    expect(result).toEqual(legacyMessage);
    expect(mocks.request).toHaveBeenNthCalledWith(1, '/v2/message/messages', {
      method: 'POST',
      data: { title: 'Welcome', content: 'Welcome to system', channels: ['INBOX'], targetScope: 'TENANT' },
    });
    expect(mocks.request).toHaveBeenNthCalledWith(2, '/v1/message/messages', {
      method: 'POST',
      data: { title: 'Welcome', content: 'Welcome to system', channels: ['INBOX'], targetScope: 'TENANT' },
    });
  });

  it('propagates message list bounded pagination markers', async () => {
    const boundedResponse = {
      records: [],
      total: -1,
      pageNo: 1,
      pageSize: 10,
      hasMore: true,
      totalCapped: true,
    } as const;

    mocks.request.mockResolvedValueOnce(boundedResponse);

    const { requestMessageList } = await import('@/services/message/api');
    const result = await requestMessageList({
      method: 'GET',
      params: { pageNo: 1, pageSize: 10 },
      autoRedirectOnUnauthorized: false,
      silent: true,
    });

    expect(result).toEqual(boundedResponse);
    expect(result.totalCapped).toBe(true);
    expect(result.hasMore).toBe(true);
    expect(mocks.request).toHaveBeenNthCalledWith(1, '/v2/message/messages', {
      method: 'GET',
      params: { pageNo: 1, pageSize: 10 },
      autoRedirectOnUnauthorized: false,
      silent: true,
    });
  });

  it('falls back to legacy message list endpoint when v2 is unavailable', async () => {
    const legacyResponse = {
      records: [{
        id: 100,
        messageType: 'MESSAGE',
        targetScope: 'TENANT',
        title: 'Welcome',
        content: 'Welcome to system',
        sourceType: 'MANUAL',
        publishStatus: 'PUBLISHED',
        createdAt: '2026-06-15T00:00:00Z',
      }],
      total: -1,
      pageNo: 1,
      pageSize: 10,
      hasMore: false,
      totalCapped: false,
    } as const;

    mocks.request
      .mockRejectedValueOnce(new Error('v2 unavailable'))
      .mockResolvedValueOnce(legacyResponse);

    const { requestMessageList } = await import('@/services/message/api');
    const result = await requestMessageList({
      method: 'GET',
      params: { pageNo: 1, pageSize: 10 },
      autoRedirectOnUnauthorized: false,
      silent: true,
    });

    expect(result).toEqual(legacyResponse);
    expect(mocks.request).toHaveBeenNthCalledWith(1, '/v2/message/messages', {
      method: 'GET',
      params: { pageNo: 1, pageSize: 10 },
      autoRedirectOnUnauthorized: false,
      silent: true,
    });
    expect(mocks.request).toHaveBeenNthCalledWith(2, '/v1/message/messages', expect.objectContaining({
      method: 'GET',
      params: { pageNo: 1, pageSize: 10 },
      autoRedirectOnUnauthorized: false,
      silent: true,
    }));
  });

  it('propagates archive list bounded pagination markers', async () => {
    const boundedResponse = {
      records: [],
      total: 21,
      pageNo: 1,
      pageSize: 20,
      hasMore: true,
      totalCapped: true,
    } as const;

    mocks.request.mockResolvedValueOnce(boundedResponse);

    const { requestMessageArchive } = await import('@/services/message/api');
    const result = await requestMessageArchive({
      method: 'GET',
      params: { pageNo: 1, pageSize: 20, keyword: '归档' },
      autoRedirectOnUnauthorized: false,
      silent: true,
    });

    expect(result).toEqual(boundedResponse);
    expect(result.totalCapped).toBe(true);
    expect(result.hasMore).toBe(true);
    expect(mocks.request).toHaveBeenNthCalledWith(1, '/v2/message/archive', {
      method: 'GET',
      params: { pageNo: 1, pageSize: 20, keyword: '归档' },
      autoRedirectOnUnauthorized: false,
      silent: true,
    });
  });

  it('propagates delivery log bounded pagination markers', async () => {
    const boundedResponse = {
      records: [],
      total: 1000,
      pageNo: 2,
      pageSize: 10,
      hasMore: true,
      totalCapped: true,
    } as const;

    mocks.request.mockResolvedValueOnce(boundedResponse);

    const { requestMessageDeliveryLogs } = await import('@/services/message/api');
    const result = await requestMessageDeliveryLogs({
      method: 'GET',
      params: { pageNo: 2, pageSize: 10, keyword: 'delivery' },
      autoRedirectOnUnauthorized: false,
      silent: true,
    });

    expect(result).toEqual(boundedResponse);
    expect(result.totalCapped).toBe(true);
    expect(result.hasMore).toBe(true);
    expect(mocks.request).toHaveBeenNthCalledWith(1, '/v2/message/delivery-logs', {
      method: 'GET',
      params: { pageNo: 2, pageSize: 10, keyword: 'delivery' },
      autoRedirectOnUnauthorized: false,
      silent: true,
    });
  });
});
