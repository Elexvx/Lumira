import { beforeEach, describe, expect, it, vi } from 'vitest';
import { revokeWorkspaceReviewAssignment } from './api';

const mocks = vi.hoisted(() => ({ request: vi.fn() }));

vi.mock('@/services/common/request', () => ({ request: mocks.request }));

describe('competition workspace review API', () => {
  beforeEach(() => {
    mocks.request.mockReset();
  });

  it('keeps assignment revocation inside the selected competition UUID boundary', () => {
    revokeWorkspaceReviewAssignment('competition-uuid', 31, 41, '名单调整');

    expect(mocks.request).toHaveBeenCalledWith(
      '/v2/aiadc/competitions/competition-uuid/reviews/batches/31/assignments/41/revoke',
      { method: 'POST', data: { reason: '名单调整' } },
    );
  });
});
