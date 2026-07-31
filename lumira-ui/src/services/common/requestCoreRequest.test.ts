import { beforeEach, describe, expect, it, vi } from 'vitest';

const {
  authSnapshotState,
  fetchWithTimeoutMock,
  refreshAuthSessionMock,
  releaseDuplicateKeyMock,
  runtimeState,
} = vi.hoisted(() => ({
  authSnapshotState: {
    skipAuth: false,
    accessToken: 'test-token',
    hasAuthToken: true,
    authSessionEpoch: 1,
    tokenGeneration: 1,
  },
  fetchWithTimeoutMock: vi.fn(),
  refreshAuthSessionMock: vi.fn(),
  releaseDuplicateKeyMock: vi.fn(),
  runtimeState: {
    pathname: '/dashboard/home',
    currentAccessToken: 'test-token',
    currentAuthSessionEpoch: 1,
    currentTokenGeneration: 1,
    loginInProgress: false,
    bootstrapInProgress: false,
  },
}));

vi.mock('./requestInternalsTimeout', () => ({
  fetchWithTimeout: fetchWithTimeoutMock,
}));

vi.mock('./requestCoreShared', () => ({
  captureRequestAuthSnapshot: () => ({ ...authSnapshotState }),
  ensureUniqueWriteRequest: () => releaseDuplicateKeyMock,
  refreshAuthSession: refreshAuthSessionMock,
}));

vi.mock('@/auth/unauthorized', () => ({
  buildUnauthorizedRuntimeState: () => ({ ...runtimeState }),
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

vi.mock('./requestInternalsApiErrorHandling', () => ({
  handleApiError: vi.fn(),
}));

import { executeRequest } from './requestCoreRequest';

describe('executeRequest empty success responses', () => {
  beforeEach(() => {
    fetchWithTimeoutMock.mockReset();
    refreshAuthSessionMock.mockReset();
    releaseDuplicateKeyMock.mockReset();
    Object.assign(authSnapshotState, {
      skipAuth: false,
      accessToken: 'test-token',
      hasAuthToken: true,
      authSessionEpoch: 1,
      tokenGeneration: 1,
    });
    Object.assign(runtimeState, {
      pathname: '/dashboard/home',
      currentAccessToken: 'test-token',
      currentAuthSessionEpoch: 1,
      currentTokenGeneration: 1,
      loginInProgress: false,
      bootstrapInProgress: false,
    });
  });

  it.each([204, 205])('accepts HTTP %s without requiring an API envelope', async (status) => {
    fetchWithTimeoutMock.mockResolvedValue(new Response(null, { status }));

    await expect(executeRequest<void>('/v2/user-drafts/competition.create', {
      method: 'DELETE',
      silent: true,
    })).resolves.toBeUndefined();
    expect(releaseDuplicateKeyMock).toHaveBeenCalledOnce();
  });

  it('does not refresh or retry an old-role URL after the auth generation changes', async () => {
    Object.assign(runtimeState, {
      currentAccessToken: 'token-after-role-switch',
      currentAuthSessionEpoch: 2,
      currentTokenGeneration: 2,
    });
    fetchWithTimeoutMock.mockResolvedValue(new Response(null, { status: 401 }));

    await expect(executeRequest('/v1/business/old-role-resource')).rejects.toMatchObject({
      httpStatus: 401,
    });

    expect(fetchWithTimeoutMock).toHaveBeenCalledOnce();
    expect(refreshAuthSessionMock).not.toHaveBeenCalled();
    expect(releaseDuplicateKeyMock).toHaveBeenCalledOnce();
  });
});
