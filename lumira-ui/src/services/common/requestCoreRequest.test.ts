import { beforeEach, describe, expect, it, vi } from 'vitest';

const { fetchWithTimeoutMock, releaseDuplicateKeyMock } = vi.hoisted(() => ({
  fetchWithTimeoutMock: vi.fn(),
  releaseDuplicateKeyMock: vi.fn(),
}));

vi.mock('./requestInternalsTimeout', () => ({
  fetchWithTimeout: fetchWithTimeoutMock,
}));

vi.mock('./requestCoreShared', () => ({
  captureRequestAuthSnapshot: () => ({
    accessToken: 'test-token',
    hasAuthToken: true,
  }),
  ensureUniqueWriteRequest: () => releaseDuplicateKeyMock,
  refreshAuthSession: vi.fn(),
}));

vi.mock('./requestInternalsHeaders', () => ({
  buildRequestHeaders: () => ({}),
  buildRequestUrl: (url: string) => url,
}));

vi.mock('./requestInternalsPayload', () => ({
  buildRequestBody: () => undefined,
}));

vi.mock('./requestInternalsResponse', () => ({
  getResponseRequestId: () => undefined,
  isApiResponse: () => false,
  parseResponseData: async () => undefined,
  withRequestId: (value: unknown) => value,
}));

vi.mock('./requestInternalsAuth', () => ({
  shouldRefreshAndRetryUnauthorized: () => false,
}));

import { executeRequest } from './requestCoreRequest';

describe('executeRequest empty success responses', () => {
  beforeEach(() => {
    fetchWithTimeoutMock.mockReset();
    releaseDuplicateKeyMock.mockReset();
  });

  it.each([204, 205])('accepts HTTP %s without requiring an API envelope', async (status) => {
    fetchWithTimeoutMock.mockResolvedValue(new Response(null, { status }));

    await expect(executeRequest<void>('/v2/user-drafts/competition.create', {
      method: 'DELETE',
      silent: true,
    })).resolves.toBeUndefined();
    expect(releaseDuplicateKeyMock).toHaveBeenCalledOnce();
  });
});
