import { beforeEach, describe, expect, it, vi } from 'vitest';
import { downloadCertificate, downloadMyCertificate, listCompetitionWorkspaceCertificateBatches } from './api';

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

  it('lists generation batches through the selected competition workspace endpoint', () => {
    listCompetitionWorkspaceCertificateBatches('competition uuid', { pageNo: 2, pageSize: 20 });

    expect(mocks.request).toHaveBeenCalledWith(
      '/v2/aiadc/competitions/competition%20uuid/certificate-batches',
      { method: 'GET', params: { pageNo: 2, pageSize: 20 } },
    );
  });
});
