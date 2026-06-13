import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  clearAuthSession: vi.fn(),
  restoreSession: vi.fn(),
  brandingSettings: vi.fn(),
  watermarkSettings: vi.fn(),
  persistWatermarkSettings: vi.fn(),
  defaultBrandingSettings: {
    websiteName: '宏翔商道',
    websiteLogoUrl: '',
    websiteFaviconUrl: '',
    footerCopyright: '',
    footerIcp: '',
    footerPoliceBeian: '',
  },
  defaultWatermarkSettings: {
    enabled: false,
    mode: 'TEXT',
    textLines: ['宏翔商道', 'Admin system'],
    imageUrl: '',
    fontColor: 'rgba(0,0,0,0.15)',
    fontSize: 14,
    fontWeight: 'normal',
    rotate: -22,
    gapX: 100,
    gapY: 100,
    offsetX: 0,
    offsetY: 0,
    zIndex: 9,
    opacity: 0.15,
  },
}));

vi.mock('@umijs/max', () => ({
  getLocale: vi.fn(() => 'zh-CN'),
}));

vi.mock('@/auth/sessionLifecycle', () => ({
  clearAuthSession: mocks.clearAuthSession,
  isLoggedIn: vi.fn(() => true),
}));

vi.mock('@/auth/sessionBootstrap', () => ({
  restoreSession: mocks.restoreSession,
}));

vi.mock('@/branding/settings', () => ({
  DEFAULT_BRANDING_SETTINGS: mocks.defaultBrandingSettings,
  applyFavicon: vi.fn(),
  getStoredBrandingSettings: vi.fn(() => null),
  normalizeBrandingSettings: vi.fn((settings) => ({ ...mocks.defaultBrandingSettings, ...(settings || {}) })),
  persistBrandingSettings: vi.fn(),
}));

vi.mock('@/agreement/settings', () => ({
  DEFAULT_AGREEMENT_SETTINGS: {},
  normalizeAgreementSettings: vi.fn((settings) => settings || {}),
}));

vi.mock('@/auth/securitySettingsTypes', () => ({
  DEFAULT_SECURITY_SETTINGS: {},
}));

vi.mock('@/auth/securitySettingsNormalize', () => ({
  normalizeSecuritySettings: vi.fn((settings) => settings || {}),
}));

vi.mock('@/auth/securitySettingsStorage', () => ({
  clearSecuritySettings: vi.fn(),
  getStoredSecuritySettings: vi.fn(() => null),
  persistSecuritySettings: vi.fn(),
}));

vi.mock('@/i18n/runtimeLocalization', () => ({
  loadRuntimeLocalizationBundle: vi.fn(() => Promise.resolve()),
}));

vi.mock('@/services/system/public', () => ({
  publicSystemService: {
    publicBrandingSettings: vi.fn(),
    publicSecuritySettings: vi.fn(),
    publicAgreementSettings: vi.fn(),
    publicLoginCapabilities: vi.fn(),
  },
}));

vi.mock('@/constants/http', () => ({
  API_PREFIX: '/api',
}));

vi.mock('@/watermark/settingsTypes', () => ({
  DEFAULT_WATERMARK_SETTINGS: mocks.defaultWatermarkSettings,
}));

vi.mock('@/watermark/settingsNormalize', () => ({
  normalizeWatermarkSettings: vi.fn((settings) => ({ ...mocks.defaultWatermarkSettings, ...(settings || {}) })),
}));

vi.mock('@/watermark/settingsStorage', () => ({
  bootstrapWatermarkSettings: vi.fn(),
  clearWatermarkSettings: vi.fn(),
  getStoredWatermarkSettings: vi.fn(() => null),
  getWatermarkSettingsSnapshot: vi.fn(() => mocks.defaultWatermarkSettings),
  persistWatermarkSettings: mocks.persistWatermarkSettings,
  subscribeWatermarkSettings: vi.fn(() => () => {}),
}));

describe('getAppInitialState', () => {
  beforeEach(() => {
    mocks.clearAuthSession.mockReset();
    mocks.restoreSession.mockReset();
    mocks.brandingSettings.mockReset();
    mocks.watermarkSettings.mockReset();
    mocks.persistWatermarkSettings.mockReset();

    mocks.restoreSession.mockResolvedValue({
      currentUser: {
        userId: 10,
        username: 'ordinary',
        nickname: 'Ordinary User',
        permissions: [],
        sessionId: 'session-ordinary',
      },
      securitySettings: {},
    });
    mocks.brandingSettings.mockResolvedValue(mocks.defaultBrandingSettings);
  });

  it('keeps the session when optional authenticated watermark settings return forbidden', async () => {
    mocks.watermarkSettings.mockRejectedValue(new Error('A0403'));

    const { getAppInitialState } = await import('@/app.bootstrap');
    const initialState = await getAppInitialState();

    expect(initialState.currentUser?.username).toBe('ordinary');
    expect(initialState.watermarkSettings).toEqual(mocks.defaultWatermarkSettings);
    expect(mocks.persistWatermarkSettings).toHaveBeenCalledWith(mocks.defaultWatermarkSettings);
    expect(mocks.clearAuthSession).not.toHaveBeenCalled();
  });
});
