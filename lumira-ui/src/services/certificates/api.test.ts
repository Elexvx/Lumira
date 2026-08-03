import { beforeEach, describe, expect, it, vi } from 'vitest';
import { downloadCertificate, downloadMyCertificate } from './api';

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  requestFile: vi.fn(),
}));

vi.mock('@/services/common/request', () => ({
  request: mocks.request,
  requestFile: mocks.requestFile,
}));

describe('certificate download API', () => {
  beforeEach(() => {
    mocks.request.mockReset();
    mocks.requestFile.mockReset();
  });

  it('downloads a managed certificate through the authenticated file client', () => {
    downloadCertificate(42);

    expect(mocks.requestFile).toHaveBeenCalledWith('/v2/aiadc/certificates/42/download', {
      method: 'GET',
    });
  });

  it('downloads the current user certificate through the authenticated file client', () => {
    downloadMyCertificate(43);

    expect(mocks.requestFile).toHaveBeenCalledWith('/v2/aiadc/certificates/mine/43/download', {
      method: 'GET',
    });
  });
});
