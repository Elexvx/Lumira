import { describe, expect, it, vi } from 'vitest';

vi.mock('@umijs/max', () => ({ history: { replace: vi.fn() } }));
vi.mock('@/services/common/request', () => ({ request: vi.fn() }));
vi.mock('@/auth/token', () => ({
  tokenManager: {
    hasToken: vi.fn(),
    clearTokenState: vi.fn(),
    setTokens: vi.fn(),
  },
}));
vi.mock('@/auth/activity', () => ({ clearSessionActivity: vi.fn() }));
vi.mock('@/auth/clientRuntimeState', () => ({ clearClientRuntimeState: vi.fn() }));
vi.mock('@/auth/loginFlowState', () => ({
  beginBootstrapFlow: vi.fn(),
  endBootstrapFlow: vi.fn(),
}));
vi.mock('@/auth/sessionState', () => ({ persistSessionMeta: vi.fn() }));

describe('buildLogoutRedirectTarget', () => {
  it('preserves the current page when an expired session is forced to log in again', async () => {
    const { buildLogoutRedirectTarget } = await import('./sessionLifecycle');

    expect(buildLogoutRedirectTarget('forced_expired', {
      pathname: '/settings/payment',
      search: '?tab=providers',
      hash: '#alipay',
    })).toBe('/user/login?redirect=%2Fsettings%2Fpayment%3Ftab%3Dproviders%23alipay&reason=session_expired');
  });

  it('does not create nested redirects from the login page or a manual logout', async () => {
    const { buildLogoutRedirectTarget } = await import('./sessionLifecycle');

    expect(buildLogoutRedirectTarget('forced_expired', {
      pathname: '/user/login',
      search: '?redirect=%2Fsettings%2Fpayment',
      hash: '',
    })).toBe('/user/login');
    expect(buildLogoutRedirectTarget('user_initiated', {
      pathname: '/settings/payment',
      search: '',
      hash: '',
    })).toBe('/user/login');
  });

  it('identifies the durable session-expired notice on the login route', async () => {
    const { isSessionExpiredLoginSearch } = await import('./sessionLifecycle');

    expect(isSessionExpiredLoginSearch('?redirect=%2Fdashboard%2Fhome&reason=session_expired')).toBe(true);
    expect(isSessionExpiredLoginSearch('?redirect=%2Fdashboard%2Fhome')).toBe(false);
  });
});
