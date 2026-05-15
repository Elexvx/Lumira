import {
  DEFAULT_BRANDING_SETTINGS,
  applyFavicon,
  getStoredBrandingSettings,
  normalizeBrandingSettings,
  persistBrandingSettings,
} from '@/branding/settings';
import { DEFAULT_AGREEMENT_SETTINGS, normalizeAgreementSettings } from '@/agreement/settings';
import { DEFAULT_SECURITY_SETTINGS, normalizeSecuritySettings, persistSecuritySettings } from '@/auth/securitySettings';
import { clearAuthSession, isLoggedIn, restoreSession } from '@/auth/session';
import { resetBootstrapSnapshot, setBootstrapSnapshot } from '@/bootstrap/bootstrapStore';
import { loadRuntimeLocalizationBundle } from '@/i18n/runtimeLocalization';
import { pluginService } from '@/services/plugin';
import { systemService } from '@/services/system';
import { API_PREFIX } from '@/constants/http';
import {
  DEFAULT_WATERMARK_SETTINGS,
  persistWatermarkSettings,
} from '@/watermark/settings';
import type { AppInitialState } from '@/app.types';
import type { AgreementSettings, BrandingSettings, CurrentUser, LoginCapabilities, MenuNode, SecuritySettings, TenantPlugin } from '@/types/api';
import { getLocale } from '@umijs/max';

const loadBrandingSettings = async (authenticated: boolean): Promise<BrandingSettings> => {
  const settings = normalizeBrandingSettings(
    authenticated
      ? await systemService.brandingSettings({
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
          silent: true,
        }).catch(() => DEFAULT_BRANDING_SETTINGS)
      : await systemService.publicBrandingSettings({ autoRedirectOnUnauthorized: false, silent: true }).catch(() => DEFAULT_BRANDING_SETTINGS),
  );
  persistBrandingSettings(settings);
  applyFavicon(settings.websiteFaviconUrl);
  return settings;
};

const loadPluginBootstrap = async (): Promise<[MenuNode[], TenantPlugin[]]> => {
  try {
    const [menuTree, availablePlugins] = await Promise.all([
      pluginService.currentMenus({
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
      }),
      pluginService.currentAvailable({
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
      }),
    ]);

    return [menuTree, availablePlugins];
  } catch {
    return [[], []];
  }
};

const loadPublicSecuritySettings = async (): Promise<SecuritySettings> => {
  const settings = normalizeSecuritySettings(
    await systemService.publicSecuritySettings({ autoRedirectOnUnauthorized: false, silent: true }).catch(() => DEFAULT_SECURITY_SETTINGS),
  );
  persistSecuritySettings(settings);
  return settings;
};

const loadPublicAgreementSettings = async (): Promise<AgreementSettings> =>
  normalizeAgreementSettings(
    await systemService.publicAgreementSettings({ autoRedirectOnUnauthorized: false, silent: true }).catch(() => DEFAULT_AGREEMENT_SETTINGS),
  );

const loadPublicLoginCapabilities = async (): Promise<LoginCapabilities> =>
  await systemService.publicLoginCapabilities({ autoRedirectOnUnauthorized: false, silent: true }).catch(() => ({
    passwordLoginAvailable: true,
    smsLoginAvailable: false,
    emailLoginAvailable: false,
    wechatLoginAvailable: false,
    passkeyLoginAvailable: false,
    passkeyPasswordlessAvailable: false,
  }));

const buildAuthenticatedInitialState = async (
  currentUser: CurrentUser,
  securitySettings: SecuritySettings,
  storedBrandingSettings: BrandingSettings,
): Promise<AppInitialState> => {
  setBootstrapSnapshot({
    phase: 'branding',
    progress: 48,
    title: '同步系统资源',
    description: '正在准备登录后的菜单、插件和外观设置',
    brandName: storedBrandingSettings.websiteName,
  });

  const [[menuTree, availablePlugins], loadedBrandingSettings, watermarkSettings] = await Promise.all([
    loadPluginBootstrap(),
    loadBrandingSettings(true),
    systemService.watermarkSettings({
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      silent: true,
    }),
  ]);

  await loadRuntimeLocalizationBundle(currentUser.locale || getLocale());

  setBootstrapSnapshot({
    phase: 'ready',
    progress: 100,
    title: '系统已就绪',
    description: '正在进入工作台',
    ready: true,
    retryInMs: undefined,
    errorMessage: undefined,
    brandName: loadedBrandingSettings.websiteName,
  });

  persistWatermarkSettings(watermarkSettings);

  return {
    currentUser,
    menuTree,
    menuVersion: 0,
    availablePlugins,
    securitySettings,
    brandingSettings: loadedBrandingSettings,
    watermarkSettings,
  };
};

const buildGuestInitialState = async (storedBrandingSettings: BrandingSettings): Promise<AppInitialState> => {
  setBootstrapSnapshot({
    phase: 'security',
    progress: 58,
    title: '加载安全配置',
    description: '正在同步登录策略',
    brandName: storedBrandingSettings.websiteName,
    retryInMs: undefined,
    errorMessage: undefined,
  });

  const [brandingSettings, securitySettings, agreementSettings, loginCapabilities] = await Promise.all([
    loadBrandingSettings(false),
    loadPublicSecuritySettings(),
    loadPublicAgreementSettings(),
    loadPublicLoginCapabilities(),
  ]);
  await loadRuntimeLocalizationBundle(getLocale());
  persistWatermarkSettings(DEFAULT_WATERMARK_SETTINGS);

  setBootstrapSnapshot({
    phase: 'ready',
    progress: 100,
    title: '系统已就绪',
    description: '正在展示登录页',
    ready: true,
    retryInMs: undefined,
    errorMessage: undefined,
    brandName: brandingSettings.websiteName,
  });

  return {
    currentUser: undefined,
    menuTree: [],
    menuVersion: 0,
    availablePlugins: [],
    securitySettings,
    brandingSettings,
    watermarkSettings: DEFAULT_WATERMARK_SETTINGS,
    agreementSettings,
    loginCapabilities,
  };
};

const getErrorMessage = (error: unknown) => {
  if (error instanceof Error && error.message) {
    return error.message;
  }

  return '后端暂未准备好';
};

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

const getHealthRetryDelay = (attempt: number) => {
  const baseDelay = 800;
  const maxDelay = 4000;
  return Math.min(baseDelay * 2 ** Math.min(attempt - 1, 3), maxDelay);
};
const MAX_AUTHENTICATED_BOOTSTRAP_RETRIES = 3;

class BackendProxyUnavailableError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'BackendProxyUnavailableError';
  }
}

const checkBackendHealth = async () => {
  const response = await fetch(`${API_PREFIX}/health`);
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('text/html')) {
    throw new BackendProxyUnavailableError('后端健康检查返回了前端页面，请检查 API 代理配置');
  }
  if (!response.ok) {
    throw new Error(`后端健康检查失败：HTTP ${response.status}`);
  }
  if (!contentType.includes('application/json')) {
    throw new BackendProxyUnavailableError('后端健康检查未返回 JSON，请检查 API 代理配置');
  }
  const payload = await response.json();
  const health = payload?.data;

  if (health?.status && health.status.toUpperCase() !== 'UP') {
    throw new Error(`后端健康状态异常：${health.status}`);
  }
};

const waitForBackendReady = async (options: { maxAttempts?: number } = {}) => {
  let attempt = 0;

  while (true) {
    attempt += 1;
    setBootstrapSnapshot({
      phase: 'health',
      progress: Math.min(12 + attempt * 4, 26),
      title: '正在连接后端',
      description: attempt === 1 ? '正在检查服务是否可用' : `后端暂未就绪，正在进行第 ${attempt} 次重试`,
      retryCount: attempt - 1,
      retryInMs: undefined,
      errorMessage: undefined,
      brandName: normalizeBrandingSettings(getStoredBrandingSettings() || DEFAULT_BRANDING_SETTINGS).websiteName,
    });

    try {
      await checkBackendHealth();
      return;
    } catch (error) {
      if (error instanceof BackendProxyUnavailableError) {
        throw error;
      }
      if (options.maxAttempts && attempt >= options.maxAttempts) {
        throw error;
      }
      const retryInMs = getHealthRetryDelay(attempt);
      setBootstrapSnapshot({
        phase: 'health',
        progress: Math.min(14 + attempt * 3, 28),
        title: '后端启动中',
        description: `后端暂未启动，${Math.ceil(retryInMs / 1000)} 秒后自动重试`,
        retryCount: attempt,
        retryInMs,
        errorMessage: getErrorMessage(error),
        brandName: normalizeBrandingSettings(getStoredBrandingSettings() || DEFAULT_BRANDING_SETTINGS).websiteName,
      });
      await sleep(retryInMs);
    }
  }
};

export async function getAppInitialState(): Promise<AppInitialState> {
  resetBootstrapSnapshot();
  const storedBrandingSettings = normalizeBrandingSettings(getStoredBrandingSettings() || DEFAULT_BRANDING_SETTINGS);

  if (!isLoggedIn()) {
    setBootstrapSnapshot({
      phase: 'ready',
      progress: 100,
      title: '系统已就绪',
      description: '正在展示登录页',
      ready: true,
      retryInMs: undefined,
      errorMessage: undefined,
      brandName: storedBrandingSettings.websiteName,
    });

    return await buildGuestInitialState(storedBrandingSettings);
  }

  while (true) {
    try {
      const restored = await restoreSession().catch(() => null);

      setBootstrapSnapshot({
        phase: 'branding',
        progress: 30,
        title: '加载品牌信息',
        description: restored?.currentUser
          ? '正在同步登录后可见的品牌与外观设置'
          : '正在同步登录页品牌与外观设置',
        brandName: storedBrandingSettings.websiteName,
        retryInMs: undefined,
        errorMessage: undefined,
      });

      if (restored?.currentUser) {
        return await buildAuthenticatedInitialState(restored.currentUser, restored.securitySettings, storedBrandingSettings);
      }

      await waitForBackendReady({ maxAttempts: MAX_AUTHENTICATED_BOOTSTRAP_RETRIES });
      return await buildGuestInitialState(storedBrandingSettings);
    } catch (error) {
      if (error instanceof BackendProxyUnavailableError) {
        return await buildGuestInitialState(storedBrandingSettings);
      }
      clearAuthSession();
      return await buildGuestInitialState(storedBrandingSettings);
    }
  }
}
