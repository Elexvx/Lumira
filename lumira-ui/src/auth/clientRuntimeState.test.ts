import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  clearQueries: vi.fn(),
  clearStoredSessionState: vi.fn(),
  clearSecuritySettings: vi.fn(),
  clearWatermarkSettings: vi.fn(),
  getStoredWatermarkSettings: vi.fn(() => null),
  removeSessionItem: vi.fn(),
}));

vi.mock('@/query/queryClient', () => ({
  queryClient: {
    clear: mocks.clearQueries,
  },
}));

vi.mock('@/auth/sessionState', () => ({
  clearStoredSessionState: mocks.clearStoredSessionState,
}));

vi.mock('@/auth/securitySettingsStorage', () => ({
  clearSecuritySettings: mocks.clearSecuritySettings,
}));

vi.mock('@/watermark/settingsStorage', () => ({
  clearWatermarkSettings: mocks.clearWatermarkSettings,
  getStoredWatermarkSettings: mocks.getStoredWatermarkSettings,
}));

import { clearClientRuntimeState } from './clientRuntimeState';

describe('clearClientRuntimeState', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('window', {
      sessionStorage: {
        removeItem: mocks.removeSessionItem,
      },
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('removes account-scoped caches and transient browser state on logout', () => {
    clearClientRuntimeState();

    expect(mocks.clearQueries).toHaveBeenCalledOnce();
    expect(mocks.clearStoredSessionState).toHaveBeenCalledOnce();
    expect(mocks.clearSecuritySettings).toHaveBeenCalledOnce();
    expect(mocks.clearWatermarkSettings).not.toHaveBeenCalled();
    expect(mocks.removeSessionItem).toHaveBeenCalledWith('lumira_wechat_contact_bind_required');
  });
});
