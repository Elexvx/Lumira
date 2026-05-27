import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  clearAuthSession: vi.fn(),
  restoreSession: vi.fn(),
  brandingSettings: vi.fn(),
  watermarkSettings: vi.fn(),
  currentMenus: vi.fn(),
  currentAvailable: vi.fn(),
  persistWatermarkSettings: vi.fn(),
  defaultBrandingSettings: {
    websiteName: '宏翔商道',
    websiteLogoUrl: '',
    websiteFaviconUrl: '',
    footerCopyright: '',
    footerIcp: '',
  },
  defaultWatermarkSettings: {
    enabled: false,
    mode: 'TEXT',
    textLines: ['宏翔商道', '后台管理系统'],
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

vi.mock('@/auth/session', () => ({
  clearAuthSession: mocks.clearAuthSession,
  isLoggedIn: vi.fn(() => true),
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

vi.mock('@/auth/securitySettings', () => ({
  DEFAULT_SECURITY_SETTINGS: {},
  normalizeSecuritySettings: vi.fn((settings) => settings || {}),
  persistSecuritySettings: vi.fn(),
}));

vi.mock('@/bootstrap/bootstrapStore', () => ({
  resetBootstrapSnapshot: vi.fn(),
  setBootstrapSnapshot: vi.fn(),
}));

vi.mock('@/i18n/runtimeLocalization', () => ({
  loadRuntimeLocalizationBundle: vi.fn(() => Promise.resolve()),
}));

vi.mock('@/services/plugin', () => ({
  pluginService: {
    currentMenus: mocks.currentMenus,
    currentAvailable: mocks.currentAvailable,
  },
}));

vi.mock('@/services/system', () => ({
  systemService: {
    brandingSettings: mocks.brandingSettings,
    watermarkSettings: mocks.watermarkSettings,
    publicBrandingSettings: vi.fn(),
    publicSecuritySettings: vi.fn(),
    publicAgreementSettings: vi.fn(),
    publicLoginCapabilities: vi.fn(),
  },
}));

vi.mock('@/constants/http', () => ({
  API_PREFIX: '/api',
}));

vi.mock('@/watermark/settings', () => ({
  DEFAULT_WATERMARK_SETTINGS: mocks.defaultWatermarkSettings,
  normalizeWatermarkSettings: vi.fn((settings) => ({ ...mocks.defaultWatermarkSettings, ...(settings || {}) })),
  persistWatermarkSettings: mocks.persistWatermarkSettings,
}));

describe('getAppInitialState', () => {
  beforeEach(() => {
    mocks.clearAuthSession.mockReset();
    mocks.restoreSession.mockReset();
    mocks.brandingSettings.mockReset();
    mocks.watermarkSettings.mockReset();
    mocks.currentMenus.mockReset();
    mocks.currentAvailable.mockReset();
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
    mocks.currentMenus.mockResolvedValue([]);
    mocks.currentAvailable.mockResolvedValue([]);
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
