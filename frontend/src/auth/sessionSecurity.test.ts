import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { SecuritySettings } from '@/types/api';

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  persistSecuritySettings: vi.fn(),
  getStoredSecuritySettings: vi.fn(() => null as SecuritySettings | null),
  normalizeSecuritySettings: vi.fn((value: SecuritySettings) => value),
}));

vi.mock('@/services/common/request', () => ({
  request: mocks.request,
}));

vi.mock('@/auth/securitySettingsTypes', () => ({
  DEFAULT_SECURITY_SETTINGS: { captchaEnabled: false } as SecuritySettings,
}));

vi.mock('@/auth/securitySettingsNormalize', () => ({
  normalizeSecuritySettings: mocks.normalizeSecuritySettings,
}));

vi.mock('@/auth/securitySettingsStorage', () => ({
  getStoredSecuritySettings: mocks.getStoredSecuritySettings,
  persistSecuritySettings: mocks.persistSecuritySettings,
}));

describe('sessionSecurity', () => {
  beforeEach(() => {
    mocks.request.mockReset();
    mocks.persistSecuritySettings.mockReset();
    mocks.getStoredSecuritySettings.mockReset();
    mocks.normalizeSecuritySettings.mockReset();
    mocks.normalizeSecuritySettings.mockImplementation((value) => value);
  });

  it('deduplicates concurrent security settings requests by request profile', async () => {
    const response = { captchaEnabled: false };
    mocks.request.mockResolvedValue(response);

    const { loadSecuritySettings } = await import('@/auth/sessionSecurity');
    const [one, two, three] = await Promise.all([
      loadSecuritySettings({ allowUnauthorizedWithoutRedirect: true, timeoutMs: 3000 }),
      loadSecuritySettings({ allowUnauthorizedWithoutRedirect: true, timeoutMs: 3000 }),
      loadSecuritySettings({ allowUnauthorizedWithoutRedirect: true, timeoutMs: 3000 }),
    ]);

    expect(one).toEqual(response);
    expect(two).toEqual(response);
    expect(three).toEqual(response);
    expect(mocks.request).toHaveBeenCalledTimes(1);
    expect(mocks.persistSecuritySettings).toHaveBeenCalledTimes(1);
  });

  it('does not dedupe different request profiles', async () => {
    const response = { captchaEnabled: false };
    mocks.request.mockResolvedValue(response);

    const { loadSecuritySettings } = await import('@/auth/sessionSecurity');
    await loadSecuritySettings({ allowUnauthorizedWithoutRedirect: true, timeoutMs: 2000 });
    await loadSecuritySettings({ allowUnauthorizedWithoutRedirect: false, timeoutMs: 2000 });

    expect(mocks.request).toHaveBeenCalledTimes(2);
  });

  it('falls back to stored security settings on network error', async () => {
    mocks.request.mockRejectedValue(new Error('net'));
    mocks.getStoredSecuritySettings.mockReturnValue({ captchaEnabled: true } as SecuritySettings);

    const { loadSecuritySettings } = await import('@/auth/sessionSecurity');
    const settings = await loadSecuritySettings();

    expect(settings).toEqual({ captchaEnabled: true });
    expect(mocks.persistSecuritySettings).not.toHaveBeenCalled();
  });

  it('prefers v2 security endpoint and falls back to legacy endpoint when unavailable', async () => {
    const fallback = { captchaEnabled: true } as SecuritySettings;
    mocks.request.mockImplementation((url: string) => {
      if (url === '/v2/platform/security-settings') {
        return Promise.reject(new Error('v2 not ready'));
      }
      if (url === '/v1/public/security-settings') {
        return Promise.resolve(fallback);
      }
      return Promise.reject(new Error(`unexpected endpoint: ${url}`));
    });

    const { loadSecuritySettings } = await import('@/auth/sessionSecurity');
    const settings = await loadSecuritySettings({ allowUnauthorizedWithoutRedirect: true });

    expect(settings).toEqual(fallback);
    expect(mocks.request).toHaveBeenCalledTimes(2);
    expect(mocks.request).toHaveBeenNthCalledWith(1, '/v2/platform/security-settings', expect.objectContaining({ method: 'GET' }));
    expect(mocks.request).toHaveBeenNthCalledWith(
      2,
      '/v1/public/security-settings',
      expect.objectContaining({ method: 'GET' }),
    );
  });
});
