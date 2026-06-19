import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  clearAuthSession: vi.fn(),
  isLoggedIn: vi.fn(() => true),
  restoreSession: vi.fn(),
  request: vi.fn(),
  brandingSettings: vi.fn(),
  watermarkSettings: vi.fn(),
  persistBrandingSettings: vi.fn(),
  applyFavicon: vi.fn(),
  persistSecuritySettings: vi.fn(),
  normalizeAgreementSettings: vi.fn((settings) => settings || {}),
  normalizeSecuritySettings: vi.fn((settings) => settings || {}),
  normalizeBrandingSettings: vi.fn((settings) => ({ websiteName: '宏翔商道', ...(settings || {}) })),
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
  defaultFloatingWindowSettings: {
    apiDocsQrEnabled: false,
    apiDocsQrTitle: '',
    apiDocsQrImageUrl: '',
  },
}));

vi.mock('@umijs/max', () => ({
  getLocale: vi.fn(() => 'zh-CN'),
}));

vi.mock('@/auth/sessionLifecycle', () => ({
  clearAuthSession: mocks.clearAuthSession,
  isLoggedIn: mocks.isLoggedIn,
}));

vi.mock('@/auth/sessionBootstrap', () => ({
  restoreSession: mocks.restoreSession,
}));

vi.mock('@/branding/settings', () => ({
  DEFAULT_BRANDING_SETTINGS: mocks.defaultBrandingSettings,
  applyFavicon: mocks.applyFavicon,
  getStoredBrandingSettings: vi.fn(() => null),
  normalizeBrandingSettings: mocks.normalizeBrandingSettings,
  persistBrandingSettings: mocks.persistBrandingSettings,
}));

vi.mock('@/agreement/settings', () => ({
  DEFAULT_AGREEMENT_SETTINGS: {},
  normalizeAgreementSettings: mocks.normalizeAgreementSettings,
}));

vi.mock('@/auth/securitySettingsTypes', () => ({
  DEFAULT_SECURITY_SETTINGS: {},
}));

vi.mock('@/auth/securitySettingsNormalize', () => ({
  normalizeSecuritySettings: mocks.normalizeSecuritySettings,
}));

vi.mock('@/auth/securitySettingsStorage', () => ({
  clearSecuritySettings: vi.fn(),
  getStoredSecuritySettings: vi.fn(() => null),
  persistSecuritySettings: mocks.persistSecuritySettings,
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

vi.mock('@/services/common/request', () => ({
  request: mocks.request,
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

vi.mock('@/floatingWindow/settings', () => ({
  DEFAULT_FLOATING_WINDOW_SETTINGS: mocks.defaultFloatingWindowSettings,
  normalizeFloatingWindowSettings: vi.fn((settings) => ({ ...mocks.defaultFloatingWindowSettings, ...(settings || {}) })),
}));

describe('getAppInitialState', () => {
  beforeEach(() => {
    vi.resetModules();
    mocks.clearAuthSession.mockReset();
    mocks.isLoggedIn.mockReset();
    mocks.isLoggedIn.mockReturnValue(true);
    mocks.restoreSession.mockReset();
    mocks.request.mockReset();
    mocks.brandingSettings.mockReset();
    mocks.watermarkSettings.mockReset();
    mocks.persistBrandingSettings.mockReset();
    mocks.applyFavicon.mockReset();
    mocks.persistSecuritySettings.mockReset();
    mocks.normalizeAgreementSettings.mockClear();
    mocks.normalizeSecuritySettings.mockClear();
    mocks.normalizeBrandingSettings.mockClear();
    mocks.persistWatermarkSettings.mockReset();
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({
        ok: true,
        headers: {
          get: (name: string) => (name.toLowerCase() === 'content-type' ? 'application/json' : ''),
        },
        json: async () => ({ data: { status: 'UP' } }),
      })),
    );

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
    mocks.request.mockImplementation(async (url: string) => {
      if (url === '/v1/plugins/current/bootstrap') {
        return { menuTree: [], availablePlugins: [] };
      }
      if (url === '/v2/plugins/current/bootstrap') {
        return { menuTree: [], availablePlugins: [] };
      }
      if (url === '/v2/platform/runtime-appearance-settings') {
        return {
          brandingSettings: mocks.defaultBrandingSettings,
          watermarkSettings: mocks.defaultWatermarkSettings,
          floatingWindowSettings: mocks.defaultFloatingWindowSettings,
        };
      }
      if (url === '/v1/system/runtime-appearance-settings') {
        return {
          brandingSettings: mocks.defaultBrandingSettings,
          watermarkSettings: mocks.defaultWatermarkSettings,
          floatingWindowSettings: mocks.defaultFloatingWindowSettings,
        };
      }
      if (url === '/v1/system/branding-settings') {
        return mocks.defaultBrandingSettings;
      }
      if (url === '/v2/platform/branding-settings') {
        return mocks.defaultBrandingSettings;
      }
      if (url === '/v1/system/watermark-settings') {
        return mocks.defaultWatermarkSettings;
      }
      if (url === '/v1/system/floating-window-settings') {
        return mocks.defaultFloatingWindowSettings;
      }
      if (url === '/v1/localization/runtime/zh-CN') {
        return { localeCode: 'zh-CN', messages: {} };
      }
      throw new Error(`Unhandled request: ${url}`);
    });
  });

  it('keeps the session when optional authenticated watermark settings return forbidden', async () => {
    mocks.watermarkSettings.mockRejectedValue(new Error('A0403'));

    const { getAppInitialState } = await import('@/app.bootstrap');
    const initialState = await getAppInitialState();

    expect(initialState.currentUser?.username).toBe('ordinary');
    expect(initialState.watermarkSettings).toEqual(mocks.defaultWatermarkSettings);
    expect(mocks.request).toHaveBeenCalledWith('/v2/platform/runtime-appearance-settings', expect.objectContaining({ method: 'GET' }));
    expect(mocks.request).toHaveBeenCalledWith('/v2/plugins/current/bootstrap', expect.objectContaining({ method: 'GET' }));
    expect(mocks.request).not.toHaveBeenCalledWith('/v2/platform/branding-settings', expect.any(Object));
    expect(mocks.request).not.toHaveBeenCalledWith('/v1/system/branding-settings', expect.any(Object));
    expect(mocks.request).not.toHaveBeenCalledWith('/v1/plugins/current/bootstrap', expect.any(Object));
    expect(mocks.request).not.toHaveBeenCalledWith('/v1/system/watermark-settings', expect.any(Object));
    expect(mocks.request).not.toHaveBeenCalledWith('/v1/system/floating-window-settings', expect.any(Object));
    expect(mocks.persistWatermarkSettings).toHaveBeenCalledWith(mocks.defaultWatermarkSettings);
    expect(mocks.clearAuthSession).not.toHaveBeenCalled();
  }, 15_000);

  it('falls back to authenticated v1 platform settings when v2 runtime appearance endpoint is unavailable', async () => {
    mocks.request.mockImplementation(async (url: string) => {
      if (url === '/v2/platform/runtime-appearance-settings') {
        throw new Error('v2 runtime appearance unavailable');
      }
      if (url === '/v1/system/runtime-appearance-settings') {
        throw new Error('v1 runtime appearance unavailable');
      }
      if (url === '/v2/platform/branding-settings') {
        throw new Error('v2 branding unavailable');
      }
      if (url === '/v2/plugins/current/bootstrap') {
        throw new Error('v2 plugin bootstrap unavailable');
      }
      if (url === '/v1/system/branding-settings') {
        return mocks.defaultBrandingSettings;
      }
      if (url === '/v1/system/watermark-settings') {
        return mocks.defaultWatermarkSettings;
      }
      if (url === '/v1/system/floating-window-settings') {
        return mocks.defaultFloatingWindowSettings;
      }
      if (url === '/v1/plugins/current/bootstrap') {
        return { menuTree: [], availablePlugins: [] };
      }
      if (url === '/v1/localization/runtime/zh-CN') {
        return { localeCode: 'zh-CN', messages: {} };
      }
      throw new Error(`Unhandled request: ${url}`);
    });

    const { getAppInitialState } = await import('@/app.bootstrap');
    const initialState = await getAppInitialState();

    expect(mocks.request).toHaveBeenCalledWith('/v2/platform/runtime-appearance-settings', expect.objectContaining({ method: 'GET' }));
    expect(mocks.request).toHaveBeenCalledWith('/v1/system/runtime-appearance-settings', expect.objectContaining({ method: 'GET' }));
    expect(mocks.request).toHaveBeenCalledWith('/v2/platform/branding-settings', expect.objectContaining({ method: 'GET' }));
    expect(mocks.request).toHaveBeenCalledWith('/v1/system/branding-settings', expect.objectContaining({ method: 'GET' }));
    expect(initialState.brandingSettings).toEqual(mocks.defaultBrandingSettings);
    expect(initialState.watermarkSettings).toEqual(mocks.defaultWatermarkSettings);
  });

  it('loads guest bootstrap through the aggregated public endpoint', async () => {
    mocks.isLoggedIn.mockReturnValue(false);
    mocks.request.mockImplementation(async (url: string) => {
      if (url === '/v2/platform/public/bootstrap') {
        return {
          brandingSettings: { websiteName: 'Lumira Fast' },
          securitySettings: { captchaEnabled: false },
          agreementSettings: { userAgreementMarkdown: 'u', privacyAgreementMarkdown: 'p' },
          loginCapabilities: { passwordLoginAvailable: true, smsLoginAvailable: true, emailLoginAvailable: false },
        };
      }
      if (url === '/v1/public/bootstrap') {
        return {
          brandingSettings: { websiteName: 'Legacy Bootstrap' },
          securitySettings: { captchaEnabled: true },
          agreementSettings: { userAgreementMarkdown: 'legacy-u', privacyAgreementMarkdown: 'legacy-p' },
          loginCapabilities: { passwordLoginAvailable: true, smsLoginAvailable: false, emailLoginAvailable: false },
        };
      }
      if (url === '/v1/localization/runtime/zh-CN') {
        return { localeCode: 'zh-CN', messages: {} };
      }
      throw new Error(`Unexpected request: ${url}`);
    });

    const { getAppInitialState } = await import('@/app.bootstrap');
    const initialState = await getAppInitialState();

    const publicBootstrapCalls = mocks.request.mock.calls.filter(
      ([url]) => url === '/v2/platform/public/bootstrap' || url === '/v1/public/bootstrap',
    );
    expect(publicBootstrapCalls).toHaveLength(1);
    expect(mocks.request).toHaveBeenCalledWith('/v2/platform/public/bootstrap', expect.objectContaining({ method: 'GET', skipAuth: true }));
    expect(initialState.currentUser).toBeUndefined();
    expect(initialState.brandingSettings.websiteName).toBe('Lumira Fast');
    expect(initialState.loginCapabilities?.smsLoginAvailable).toBe(true);
    expect(mocks.persistSecuritySettings).toHaveBeenCalledWith(expect.objectContaining({ captchaEnabled: false }));
  });

  it('falls back to split public endpoints when aggregated guest bootstrap is unavailable', async () => {
    mocks.isLoggedIn.mockReturnValue(false);
    mocks.request.mockImplementation(async (url: string) => {
      if (url === '/v2/platform/public/bootstrap') {
        throw new Error('new endpoint not deployed');
      }
      if (url === '/v1/public/bootstrap') {
        throw new Error('not deployed yet');
      }
      if (url === '/v1/public/branding-settings') {
        return { websiteName: 'Fallback Brand' };
      }
      if (url === '/v1/public/security-settings') {
        return { captchaEnabled: true };
      }
      if (url === '/v1/public/agreement-settings') {
        return { userAgreementMarkdown: 'u2', privacyAgreementMarkdown: 'p2' };
      }
      if (url === '/v1/public/login-capabilities') {
        return { passwordLoginAvailable: true, smsLoginAvailable: false, emailLoginAvailable: true };
      }
      if (url === '/v1/localization/runtime/zh-CN') {
        return { localeCode: 'zh-CN', messages: {} };
      }
      throw new Error(`Unexpected request: ${url}`);
    });

    const { getAppInitialState } = await import('@/app.bootstrap');
    const initialState = await getAppInitialState();

    expect(mocks.request).toHaveBeenCalledWith('/v2/platform/public/bootstrap', expect.any(Object));
    expect(mocks.request).toHaveBeenCalledWith('/v1/public/bootstrap', expect.any(Object));
    expect(mocks.request).toHaveBeenCalledWith('/v1/public/branding-settings', expect.any(Object));
    expect(mocks.request).toHaveBeenCalledWith('/v1/public/security-settings', expect.any(Object));
    expect(mocks.request).toHaveBeenCalledWith('/v1/public/agreement-settings', expect.any(Object));
    expect(mocks.request).toHaveBeenCalledWith('/v1/public/login-capabilities', expect.any(Object));
    expect(initialState.brandingSettings.websiteName).toBe('Fallback Brand');
    expect(initialState.loginCapabilities?.emailLoginAvailable).toBe(true);
  });
});
