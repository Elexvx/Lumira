import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  refresh: vi.fn(),
  logout: vi.fn(),
  hasToken: { value: true },
}));

vi.mock('@/auth/sessionLifecycle', () => ({
  tryRefreshTokenOutcome: mocks.refresh,
  performLogout: mocks.logout,
  hasUsableTokenAfterRefresh: (outcome: string) =>
    outcome === 'refreshed' || (outcome === 'superseded' && mocks.hasToken.value),
}));

import { refreshPluginMutationSession } from './pluginMutationSession';

describe('refreshPluginMutationSession', () => {
  beforeEach(() => {
    mocks.refresh.mockReset();
    mocks.logout.mockReset();
    mocks.hasToken.value = true;
  });

  it.each(['refreshed', 'superseded'])('continues with a usable %s session', async (outcome) => {
    mocks.refresh.mockResolvedValue(outcome);

    await expect(refreshPluginMutationSession()).resolves.toBe('ready');
    expect(mocks.logout).not.toHaveBeenCalled();
  });

  it('keeps the account signed in when permission refresh is temporarily unavailable', async () => {
    mocks.refresh.mockResolvedValue('temporarily_unavailable');

    await expect(refreshPluginMutationSession()).resolves.toBe('temporarily_unavailable');
    expect(mocks.logout).not.toHaveBeenCalled();
  });

  it('stops follow-up reads when a superseding session has no usable token', async () => {
    mocks.hasToken.value = false;
    mocks.refresh.mockResolvedValue('superseded');

    await expect(refreshPluginMutationSession()).resolves.toBe('temporarily_unavailable');
    expect(mocks.logout).not.toHaveBeenCalled();
  });

  it('logs out only when refresh confirms that the session really expired', async () => {
    mocks.refresh.mockResolvedValue('session_expired');

    await expect(refreshPluginMutationSession()).resolves.toBe('session_expired');
    expect(mocks.logout).toHaveBeenCalledWith({ reason: 'forced_expired' });
  });
});
