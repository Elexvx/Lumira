import { getLocale } from '@umijs/max';
import { getApiPrefix } from '@/constants/http';
import { DEFAULT_BRANDING_SETTINGS, applyFavicon, getStoredBrandingSettings, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { DEFAULT_AGREEMENT_SETTINGS, normalizeAgreementSettings } from '@/agreement/settings';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettingsTypes';
import { normalizeSecuritySettings } from '@/auth/securitySettingsNormalize';
import { persistSecuritySettings } from '@/auth/securitySettingsStorage';
import { loadRuntimeLocalizationBundle } from '@/i18n/runtimeLocalization';
import { normalizeLocale } from '@/i18n/locale';
import { clearAuthSession, isLoggedIn } from '@/auth/sessionLifecycle';
import { restoreSession } from '@/auth/sessionBootstrap';
import type { SessionBootstrapResult } from '@/auth/sessionBootstrap';
import buildAccess from '@/access';
import enUSMessages from '@/locales/en-US';
import zhCNMessages from '@/locales/zh-CN';
import { request } from '@/services/common/request';
import { DEFAULT_WATERMARK_SETTINGS } from '@/watermark/settingsTypes';
import { normalizeWatermarkSettings } from '@/watermark/settingsNormalize';
import { persistWatermarkSettings } from '@/watermark/settingsStorage';
import { DEFAULT_FLOATING_WINDOW_SETTINGS, normalizeFloatingWindowSettings } from '@/floatingWindow/settings';
import type { AppInitialState } from '@/app.types';
import type { AgreementSettings, BrandingSettings, FloatingWindowSettings, LoginCapabilities, MenuNode, SecuritySettings, PluginAvailability, RuntimeAppearanceSettings, WatermarkSettings } from '@/types/api';
import { API_OPTS } from '@/utils/errorMessage';
import { realPageRouteMetaList, resolveCanonicalRoutePath } from '@/routes/meta';

const MAX_AUTHENTICATED_BOOTSTRAP_RETRIES = 3;
const GUEST_BOOTSTRAP_TIMEOUT_MS = 3000;
const GUEST_PUBLIC_REQUEST_TIMEOUT_MS = 1500;
const resolveBootstrapLocale = () => {
  if (typeof document !== 'undefined') {
    const domLocale = normalizeLocale(document.documentElement.lang);
    if (domLocale) {
      return domLocale;
    }
  }

  if (typeof window !== 'undefined') {
    return normalizeLocale(window.localStorage?.getItem('umi_locale'));
  }

  return 'zh-CN';
};

const t = (id: string, fallback: string) => {
  const messages = (resolveBootstrapLocale() === 'en-US' ? enUSMessages : zhCNMessages) as Record<string, string>;
  return messages[id] || fallback;
};

type BootstrapPhase = 'idle' | 'health' | 'branding' | 'security' | 'captcha' | 'ready' | 'error';

interface BootstrapSnapshot {
  phase: BootstrapPhase;
  progress: number;
  title: string;
  description: string;
  retryCount: number;
  retryInMs?: number;
  brandName?: string;
  errorMessage?: string;
  ready: boolean;
  updatedAt: number;
}

interface PluginBootstrapResponse {
  menuTree?: MenuNode[];
  availablePlugins?: PluginAvailability[];
}

interface PublicBootstrapResponse {
  brandingSettings?: BrandingSettings;
  securitySettings?: SecuritySettings;
  agreementSettings?: AgreementSettings;
  loginCapabilities?: LoginCapabilities;
}

type BootstrapMenuNode = MenuNode & { access?: string };

const COMPETITION_MENU_ROOT: MenuNode = {
  id: -1070,
  menuCode: 'competition.root',
  name: '\u8d5b\u4e8b',
  path: '/competitions',
  component: 'redirect:/competitions/register',
  icon: 'TrophyOutlined',
  sortNo: 5,
  children: [],
};

const REGISTRATION_MENU_ROOT: MenuNode = {
  id: -1069,
  menuCode: 'registration.root',
  name: '\u62a5\u540d',
  path: '/registration',
  component: 'redirect:/competitions/register',
  icon: 'FormOutlined',
  sortNo: 4,
  children: [],
};

const EXPERT_REVIEW_MENU_ROOT: MenuNode = {
  id: -1068,
  menuCode: 'expert.review.root',
  name: '\u4e13\u5bb6\u4e0e\u8bc4\u5ba1',
  path: '/expert-review',
  component: 'redirect:/competitions/expert-apply',
  icon: 'SolutionOutlined',
  sortNo: 6,
  children: [],
};

const COMPETITION_APPLICATION_MENUS: BootstrapMenuNode[] = [
];

const EXPERT_REVIEW_APPLICATION_MENUS: BootstrapMenuNode[] = [
  {
    id: -1077,
    parentId: -1068,
    menuCode: 'expert.application',
    name: '\u4e13\u5bb6\u7533\u8bf7',
    path: '/competitions/expert-apply',
    component: '@/pages/competition',
    icon: 'SolutionOutlined',
    sortNo: 2,
    access: 'canVisitExperts',
  },
];

const REGISTRATION_APPLICATION_MENUS: BootstrapMenuNode[] = [
  {
    id: -1075,
    parentId: -1069,
    menuCode: 'competition.registration',
    name: '\u8d5b\u4e8b\u62a5\u540d',
    path: '/competitions/register',
    component: '@/pages/competition',
    icon: 'FormOutlined',
    sortNo: 1,
    access: 'canVisitCompetitionRegister',
  },
  {
    id: -1076,
    parentId: -1069,
    menuCode: 'activity.registration',
    name: '\u6d3b\u52a8\u62a5\u540d',
    path: '/activities/register',
    component: '@/pages/competition',
    icon: 'CalendarOutlined',
    sortNo: 2,
    access: 'canVisitActivityRegister',
  },
];

const hasMenuPath = (menus: MenuNode[] | undefined, path: string): boolean =>
  Boolean(menus?.some((menu) => menu.path === path || hasMenuPath(menu.children, path)));

const routePathMatches = (routePath: string, pathname: string) => {
  const pattern = routePath
    .replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    .replace(/:([^/]+)/g, '[^/]+');

  return new RegExp(`^${pattern}$`).test(pathname);
};

const canVisitMenuPath = (path: string | undefined, currentUser: AppInitialState['currentUser']) => {
  if (!path) {
    return true;
  }
  const canonicalPath = resolveCanonicalRoutePath(path);
  const routeMeta = realPageRouteMetaList.find((item) => routePathMatches(item.path, canonicalPath));
  if (!routeMeta?.access) {
    return true;
  }
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  return Boolean(access[routeMeta.access]);
};

const filterMenusByAccess = (menus: MenuNode[] | undefined, currentUser: AppInitialState['currentUser']): MenuNode[] =>
  (menus || [])
    .map((menu) => {
      const children = filterMenusByAccess(menu.children, currentUser);
      if (!canVisitMenuPath(menu.path, currentUser) && children.length === 0) {
        return null;
      }
      return {
        ...menu,
        ...(menu.children || children.length ? { children } : {}),
      };
    })
    .filter((menu): menu is MenuNode => Boolean(menu));

const mergeCompetitionApplicationChildren = (children: MenuNode[] | undefined, currentUser: AppInitialState['currentUser']) => {
  const nextChildren = [...(children || [])];
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  COMPETITION_APPLICATION_MENUS.forEach((requiredMenu) => {
    if (requiredMenu.access && !access[requiredMenu.access]) {
      return;
    }
    if (!hasMenuPath(nextChildren, requiredMenu.path)) {
      nextChildren.push({ ...requiredMenu });
    }
  });
  nextChildren.sort((left, right) => (left.sortNo ?? 0) - (right.sortNo ?? 0));
  return nextChildren;
};

const ensureCompetitionApplicationMenus = (menus: MenuNode[], currentUser: AppInitialState['currentUser']): MenuNode[] => {
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  const visibleCompetitionMenus = COMPETITION_APPLICATION_MENUS.filter((menu) => !menu.access || Boolean(access[menu.access]));
  const normalizedMenus = menus;
  if (!visibleCompetitionMenus.length) {
    return normalizedMenus;
  }
  if (visibleCompetitionMenus.every((menu) => hasMenuPath(normalizedMenus, menu.path))) {
    return normalizedMenus;
  }

  let attached = false;
  const nextMenus = normalizedMenus.map((menu) => {
    const isCompetitionRoot = menu.menuCode === COMPETITION_MENU_ROOT.menuCode || menu.path === COMPETITION_MENU_ROOT.path;
    if (!isCompetitionRoot) {
      return menu;
    }

    attached = true;
    return {
      ...menu,
      children: mergeCompetitionApplicationChildren(menu.children, currentUser),
    };
  });

  return attached
    ? nextMenus
    : [
        ...nextMenus,
        {
          ...COMPETITION_MENU_ROOT,
          children: mergeCompetitionApplicationChildren(COMPETITION_MENU_ROOT.children, currentUser),
        },
      ];
};

const isExpertReviewApplicationMenu = (menu: MenuNode) =>
  menu.menuCode === 'expert.application'
  || menu.menuCode === 'expert.review.application'
  || menu.path === '/competitions/expert-apply';

const removeExpertReviewApplicationMenus = (menus: MenuNode[]): MenuNode[] =>
  menus
    .filter((menu) => !isExpertReviewApplicationMenu(menu))
    .map((menu) => ({
      ...menu,
      ...(menu.children ? { children: removeExpertReviewApplicationMenus(menu.children) } : {}),
    }));

const mergeExpertReviewApplicationChildren = (children: MenuNode[] | undefined, currentUser: AppInitialState['currentUser']) => {
  const nextChildren = (children || []).filter(
    (menu) => !isExpertReviewApplicationMenu(menu),
  );
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  EXPERT_REVIEW_APPLICATION_MENUS.forEach((requiredMenu) => {
    if (requiredMenu.access && !access[requiredMenu.access]) {
      return;
    }
    if (!hasMenuPath(nextChildren, requiredMenu.path)) {
      nextChildren.push({ ...requiredMenu });
    }
  });
  nextChildren.sort((left, right) => (left.sortNo ?? 0) - (right.sortNo ?? 0));
  return nextChildren;
};

const ensureExpertReviewApplicationMenus = (menus: MenuNode[], currentUser: AppInitialState['currentUser']): MenuNode[] => {
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  const visibleExpertReviewMenus = EXPERT_REVIEW_APPLICATION_MENUS.filter((menu) => !menu.access || Boolean(access[menu.access]));
  const normalizedMenus = removeExpertReviewApplicationMenus(menus);
  if (!visibleExpertReviewMenus.length) {
    return normalizedMenus;
  }

  let attached = false;
  const nextMenus = normalizedMenus.map((menu) => {
    const isExpertReviewRoot = menu.menuCode === EXPERT_REVIEW_MENU_ROOT.menuCode || menu.path === EXPERT_REVIEW_MENU_ROOT.path;
    if (!isExpertReviewRoot) {
      return menu;
    }

    attached = true;
    return {
      ...menu,
      path: EXPERT_REVIEW_MENU_ROOT.path,
      component: EXPERT_REVIEW_MENU_ROOT.component,
      children: mergeExpertReviewApplicationChildren(menu.children, currentUser),
    };
  });

  return attached
    ? nextMenus
    : [
        ...nextMenus,
        {
          ...EXPERT_REVIEW_MENU_ROOT,
          children: mergeExpertReviewApplicationChildren(EXPERT_REVIEW_MENU_ROOT.children, currentUser),
        },
      ];
};

const isRegistrationApplicationMenu = (menu: MenuNode) =>
  menu.menuCode === 'competition.registration'
  || menu.menuCode === 'activity.registration'
  || menu.path === '/competitions/register'
  || menu.path === '/competitions/activity-register'
  || menu.path === '/activities/register';

const removeRegistrationApplicationMenus = (menus: MenuNode[]): MenuNode[] =>
  menus
    .filter((menu) => !isRegistrationApplicationMenu(menu))
    .map((menu) => ({
      ...menu,
      ...(menu.children ? { children: removeRegistrationApplicationMenus(menu.children) } : {}),
    }));

const mergeRegistrationApplicationChildren = (children: MenuNode[] | undefined, currentUser: AppInitialState['currentUser']) => {
  const nextChildren = (children || []).filter(
    (menu) => !isRegistrationApplicationMenu(menu),
  );
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  REGISTRATION_APPLICATION_MENUS.forEach((requiredMenu) => {
    if (requiredMenu.access && !access[requiredMenu.access]) {
      return;
    }
    if (!hasMenuPath(nextChildren, requiredMenu.path)) {
      nextChildren.push({ ...requiredMenu });
    }
  });
  nextChildren.sort((left, right) => (left.sortNo ?? 0) - (right.sortNo ?? 0));
  return nextChildren;
};

const ensureRegistrationApplicationMenus = (menus: MenuNode[], currentUser: AppInitialState['currentUser']): MenuNode[] => {
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  const visibleRegistrationMenus = REGISTRATION_APPLICATION_MENUS.filter((menu) => !menu.access || Boolean(access[menu.access]));
  const normalizedMenus = removeRegistrationApplicationMenus(menus);
  if (!visibleRegistrationMenus.length) {
    return normalizedMenus;
  }

  let attached = false;
  const nextMenus = normalizedMenus.map((menu) => {
    const isRegistrationRoot = menu.menuCode === REGISTRATION_MENU_ROOT.menuCode || menu.path === REGISTRATION_MENU_ROOT.path;
    if (!isRegistrationRoot) {
      return menu;
    }

    attached = true;
    return {
      ...menu,
      path: REGISTRATION_MENU_ROOT.path,
      component: REGISTRATION_MENU_ROOT.component,
      children: mergeRegistrationApplicationChildren(menu.children, currentUser),
    };
  });

  return attached
    ? nextMenus
    : [
        ...nextMenus,
        {
          ...REGISTRATION_MENU_ROOT,
          children: mergeRegistrationApplicationChildren(REGISTRATION_MENU_ROOT.children, currentUser),
        },
      ];
};

const buildInitialBootstrapSnapshot = (): BootstrapSnapshot => ({
  phase: 'idle',
  progress: 0,
  title: t('app.bootstrap.starting', '正在启动系统'),
  description: t('app.bootstrap.checkingBackend', '正在检查后端服务'),
  retryCount: 0,
  ready: false,
  updatedAt: Date.now(),
});

let bootstrapSnapshot = buildInitialBootstrapSnapshot();

const setBootstrapSnapshot = (patch: Partial<BootstrapSnapshot>) => {
  bootstrapSnapshot = {
    ...bootstrapSnapshot,
    ...patch,
    updatedAt: Date.now(),
  };
};

const resetBootstrapSnapshot = () => {
  bootstrapSnapshot = buildInitialBootstrapSnapshot();
};

class BackendProxyUnavailableError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'BackendProxyUnavailableError';
  }
}

const getErrorMessage = (error: unknown) => {
  if (error instanceof Error && error.message) {
    return error.message;
  }

  return t('app.bootstrap.backendNotReady', '后端暂未准备好');
};

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

const getHealthRetryDelay = (attempt: number) => {
  const baseDelay = 800;
  const maxDelay = 4000;
  return Math.min(baseDelay * 2 ** Math.min(attempt - 1, 3), maxDelay);
};

const loadBrandingSettings = async (authenticated: boolean): Promise<BrandingSettings> => {
  const settings = normalizeBrandingSettings(
    authenticated
      ? await request<BrandingSettings>('/v2/platform/branding-settings', {
          method: 'GET',
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
          silent: true,
        })
          .catch(() => request<BrandingSettings>('/v1/system/branding-settings', {
            method: 'GET',
            autoRedirectOnUnauthorized: false,
            allowUnauthorizedWithoutRedirect: true,
            silent: true,
          }).catch(() => DEFAULT_BRANDING_SETTINGS))
      : await request<BrandingSettings>('/v1/public/branding-settings', {
          method: 'GET',
          skipAuth: true,
          silent: true,
          timeoutMs: GUEST_PUBLIC_REQUEST_TIMEOUT_MS,
          ...API_OPTS.SILENT_NO_REDIRECT,
        }).catch(() => DEFAULT_BRANDING_SETTINGS),
  );
  persistBrandingSettings(settings);
  applyFavicon(settings.websiteFaviconUrl);
  return settings;
};

const loadPluginBootstrap = async (): Promise<[MenuNode[], PluginAvailability[]]> => {
  try {
    const bootstrap = await request<PluginBootstrapResponse>('/v2/plugins/current/bootstrap', {
      method: 'GET',
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      silent: true,
    });

    return [bootstrap.menuTree || [], bootstrap.availablePlugins || []];
  } catch {
    // Fall through to the older split endpoints for compatibility during rolling deploys.
  }

  try {
    const bootstrap = await request<PluginBootstrapResponse>('/v1/plugins/current/bootstrap', {
      method: 'GET',
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      silent: true,
    });
    return [bootstrap.menuTree || [], bootstrap.availablePlugins || []];
  } catch {
    // Fall through to the older split endpoints for compatibility during rolling deploys.
  }

  try {
    const [menuTree, availablePlugins] = await Promise.all([
      request<MenuNode[]>('/v1/plugins/current/menus', {
        method: 'GET',
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
      }),
      request<PluginAvailability[]>('/v1/plugins/current/available', {
        method: 'GET',
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
    await request<SecuritySettings>('/v1/public/security-settings', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      timeoutMs: GUEST_PUBLIC_REQUEST_TIMEOUT_MS,
      ...API_OPTS.SILENT_NO_REDIRECT,
    }).catch(() => DEFAULT_SECURITY_SETTINGS),
  );
  persistSecuritySettings(settings);
  return settings;
};

const loadPublicAgreementSettings = async (): Promise<AgreementSettings> =>
  normalizeAgreementSettings(
    await request<AgreementSettings>('/v1/public/agreement-settings', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      timeoutMs: GUEST_PUBLIC_REQUEST_TIMEOUT_MS,
      ...API_OPTS.SILENT_NO_REDIRECT,
    }).catch(() => DEFAULT_AGREEMENT_SETTINGS),
  );

const loadPublicLoginCapabilities = async (): Promise<LoginCapabilities> =>
  await request<LoginCapabilities>('/v1/public/login-capabilities', {
    method: 'GET',
    skipAuth: true,
    silent: true,
    timeoutMs: GUEST_PUBLIC_REQUEST_TIMEOUT_MS,
    ...API_OPTS.SILENT_NO_REDIRECT,
  }).catch(() => ({
    passwordLoginAvailable: true,
    smsLoginAvailable: false,
    emailLoginAvailable: false,
    wechatLoginAvailable: false,
    passkeyLoginAvailable: false,
    passkeyPasswordlessAvailable: false,
  }));

const fallbackLoginCapabilities = (): LoginCapabilities => ({
  passwordLoginAvailable: true,
  smsLoginAvailable: false,
  emailLoginAvailable: false,
  wechatLoginAvailable: false,
  passkeyLoginAvailable: false,
  passkeyPasswordlessAvailable: false,
});

const loadPublicBootstrap = async (): Promise<{
  brandingSettings: BrandingSettings;
  securitySettings: SecuritySettings;
  agreementSettings: AgreementSettings;
  loginCapabilities: LoginCapabilities;
}> => {
  try {
    const bootstrap = await request<PublicBootstrapResponse>('/v2/platform/public/bootstrap', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      timeoutMs: GUEST_BOOTSTRAP_TIMEOUT_MS,
      ...API_OPTS.SILENT_NO_REDIRECT,
    });
    const brandingSettings = normalizeBrandingSettings(bootstrap.brandingSettings || DEFAULT_BRANDING_SETTINGS);
    const securitySettings = normalizeSecuritySettings(bootstrap.securitySettings || DEFAULT_SECURITY_SETTINGS);
    persistBrandingSettings(brandingSettings);
    applyFavicon(brandingSettings.websiteFaviconUrl);
    persistSecuritySettings(securitySettings);
    return {
      brandingSettings,
      securitySettings,
      agreementSettings: normalizeAgreementSettings(bootstrap.agreementSettings || DEFAULT_AGREEMENT_SETTINGS),
      loginCapabilities: bootstrap.loginCapabilities || fallbackLoginCapabilities(),
    };
  } catch {
    try {
      const bootstrap = await request<PublicBootstrapResponse>('/v1/public/bootstrap', {
        method: 'GET',
        skipAuth: true,
        silent: true,
        timeoutMs: GUEST_BOOTSTRAP_TIMEOUT_MS,
        ...API_OPTS.SILENT_NO_REDIRECT,
      });
      const brandingSettings = normalizeBrandingSettings(bootstrap.brandingSettings || DEFAULT_BRANDING_SETTINGS);
      const securitySettings = normalizeSecuritySettings(bootstrap.securitySettings || DEFAULT_SECURITY_SETTINGS);
      persistBrandingSettings(brandingSettings);
      applyFavicon(brandingSettings.websiteFaviconUrl);
      persistSecuritySettings(securitySettings);
      return {
        brandingSettings,
        securitySettings,
        agreementSettings: normalizeAgreementSettings(bootstrap.agreementSettings || DEFAULT_AGREEMENT_SETTINGS),
        loginCapabilities: bootstrap.loginCapabilities || fallbackLoginCapabilities(),
      };
    } catch {
      const [brandingSettings, securitySettings, agreementSettings, loginCapabilities] = await Promise.all([
        loadBrandingSettings(false),
        loadPublicSecuritySettings(),
        loadPublicAgreementSettings(),
        loadPublicLoginCapabilities(),
      ]);
      return { brandingSettings, securitySettings, agreementSettings, loginCapabilities };
    }
  }
};

const normalizeRuntimeAppearanceSettingsPayload = (settings?: RuntimeAppearanceSettings) => ({
  brandingSettings: normalizeBrandingSettings(settings?.brandingSettings || DEFAULT_BRANDING_SETTINGS),
  watermarkSettings: normalizeWatermarkSettings(settings?.watermarkSettings || DEFAULT_WATERMARK_SETTINGS),
  floatingWindowSettings: normalizeFloatingWindowSettings(settings?.floatingWindowSettings || DEFAULT_FLOATING_WINDOW_SETTINGS),
});

const loadRuntimeAppearanceSettings = async (): Promise<{
  brandingSettings: BrandingSettings;
  watermarkSettings: WatermarkSettings;
  floatingWindowSettings: FloatingWindowSettings;
}> => {
  try {
    const settings = await request<RuntimeAppearanceSettings>('/v2/platform/runtime-appearance-settings', {
      method: 'GET',
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      silent: true,
    }).catch(() =>
      request<RuntimeAppearanceSettings>('/v1/system/runtime-appearance-settings', {
        method: 'GET',
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
      }),
    );
    return normalizeRuntimeAppearanceSettingsPayload(settings);
  } catch {
    const [brandingSettings, watermarkSettings, floatingWindowSettings] = await Promise.all([
      loadBrandingSettings(true),
      request<WatermarkSettings>('/v1/system/watermark-settings', {
        method: 'GET',
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
      })
        .then(normalizeWatermarkSettings)
        .catch(() => DEFAULT_WATERMARK_SETTINGS),
      request<FloatingWindowSettings>('/v1/system/floating-window-settings', {
        method: 'GET',
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
      })
        .then(normalizeFloatingWindowSettings)
        .catch(() => DEFAULT_FLOATING_WINDOW_SETTINGS),
    ]);
    return { brandingSettings, watermarkSettings, floatingWindowSettings };
  }
};

const buildAuthenticatedInitialState = async (
  sessionBootstrap: SessionBootstrapResult,
  storedBrandingSettings: BrandingSettings,
): Promise<AppInitialState> => {
  const {
    currentUser,
    securitySettings,
    menuTree: bootstrapMenuTree,
    availablePlugins: bootstrapAvailablePlugins,
    runtimeAppearanceSettings: bootstrapRuntimeAppearanceSettings,
  } = sessionBootstrap;
  setBootstrapSnapshot({
    phase: 'branding',
    progress: 48,
    title: t('app.bootstrap.syncResources', '同步系统资源'),
    description: t('app.bootstrap.preparePostLoginResources', '正在准备登录后的菜单、插件和外观设置'),
    brandName: storedBrandingSettings.websiteName,
  });

  const [
    [menuTree, availablePlugins],
    runtimeAppearanceSettings,
    _guestLocaleBundle,
  ] = await Promise.all([
    bootstrapMenuTree !== undefined && bootstrapAvailablePlugins !== undefined
      ? Promise.resolve<[MenuNode[], PluginAvailability[]]>([bootstrapMenuTree, bootstrapAvailablePlugins])
      : loadPluginBootstrap(),
    bootstrapRuntimeAppearanceSettings !== undefined
      ? Promise.resolve(normalizeRuntimeAppearanceSettingsPayload(bootstrapRuntimeAppearanceSettings))
      : loadRuntimeAppearanceSettings(),
    // Keep localization warm while resource bootstrap is doing I/O.
    loadRuntimeLocalizationBundle(currentUser.locale || getLocale()),
  ]);
  const { brandingSettings: loadedBrandingSettings, watermarkSettings, floatingWindowSettings } = runtimeAppearanceSettings;

  setBootstrapSnapshot({
    phase: 'ready',
    progress: 100,
    title: t('app.bootstrap.ready', '系统已就绪'),
    description: t('app.bootstrap.enterWorkbench', '正在进入工作台'),
    ready: true,
    retryInMs: undefined,
    errorMessage: undefined,
    brandName: loadedBrandingSettings.websiteName,
  });

  persistBrandingSettings(loadedBrandingSettings);
  applyFavicon(loadedBrandingSettings.websiteFaviconUrl);
  persistWatermarkSettings(watermarkSettings);

  return {
    currentUser,
    menuTree: ensureRegistrationApplicationMenus(
      ensureExpertReviewApplicationMenus(
        ensureCompetitionApplicationMenus(filterMenusByAccess(menuTree, currentUser), currentUser),
        currentUser,
      ),
      currentUser,
    ),
    menuVersion: 0,
    availablePlugins,
    securitySettings,
    brandingSettings: loadedBrandingSettings,
    watermarkSettings,
    floatingWindowSettings,
  };
};

const buildGuestInitialState = async (storedBrandingSettings: BrandingSettings): Promise<AppInitialState> => {
  setBootstrapSnapshot({
    phase: 'security',
    progress: 58,
    title: t('app.bootstrap.loadSecuritySettings', '加载安全配置'),
    description: t('app.bootstrap.syncLoginPolicy', '正在同步登录策略'),
    brandName: storedBrandingSettings.websiteName,
    retryInMs: undefined,
    errorMessage: undefined,
  });

  const [publicBootstrap] = await Promise.all([
    loadPublicBootstrap(),
    loadRuntimeLocalizationBundle(getLocale()),
  ]);
  const { brandingSettings, securitySettings, agreementSettings, loginCapabilities } = publicBootstrap;
  persistWatermarkSettings(DEFAULT_WATERMARK_SETTINGS);

  setBootstrapSnapshot({
    phase: 'ready',
    progress: 100,
    title: t('app.bootstrap.ready', '系统已就绪'),
    description: t('app.bootstrap.showLoginPage', '正在展示登录页'),
    ready: true,
    retryInMs: undefined,
    errorMessage: undefined,
    brandName: brandingSettings.websiteName,
  });

  return {
    currentUser: undefined,
    menuTree: [],
    menuVersion: 0,
    publicBootstrapLoadedAt: Date.now(),
    availablePlugins: [],
    securitySettings,
    brandingSettings,
    watermarkSettings: DEFAULT_WATERMARK_SETTINGS,
    agreementSettings,
    loginCapabilities,
  };
};

const checkBackendHealth = async () => {
  const response = await fetch(`${getApiPrefix()}/health`);
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('text/html')) {
    throw new BackendProxyUnavailableError(t('app.bootstrap.healthHtmlResponse', '后端健康检查返回了前端页面，请检查 API 代理配置'));
  }
  if (!response.ok) {
    throw new Error(t('app.bootstrap.healthHttpFailed', '后端健康检查失败：HTTP {status}').replace('{status}', String(response.status)));
  }
  if (!contentType.includes('application/json')) {
    throw new BackendProxyUnavailableError(t('app.bootstrap.healthJsonResponse', '后端健康检查未返回 JSON，请检查 API 代理配置'));
  }
  const payload = await response.json();
  const health = payload?.data;

  if (health?.status && health.status.toUpperCase() !== 'UP') {
    throw new Error(t('app.bootstrap.healthStatusAbnormal', '后端健康状态异常：{status}').replace('{status}', String(health.status)));
  }
};

const waitForBackendReady = async (options: { maxAttempts?: number } = {}) => {
  let attempt = 0;

  while (true) {
    attempt += 1;
    setBootstrapSnapshot({
      phase: 'health',
      progress: Math.min(12 + attempt * 4, 26),
      title: t('app.bootstrap.connectingBackend', '正在连接后端'),
      description:
        attempt === 1
          ? t('app.bootstrap.checkingServiceReady', '正在检查服务是否可用')
          : t('app.bootstrap.backendRetrying', '后端暂未就绪，正在进行第 {attempt} 次重试').replace('{attempt}', String(attempt)),
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
        title: t('app.bootstrap.backendStarting', '后端启动中'),
        description: t('app.bootstrap.backendRetryInSeconds', '后端暂未启动，{seconds} 秒后自动重试').replace('{seconds}', String(Math.ceil(retryInMs / 1000))),
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
    const restored = await restoreSession().catch(() => null);
    if (restored?.currentUser) {
      return await buildAuthenticatedInitialState(restored, storedBrandingSettings);
    }

    setBootstrapSnapshot({
      phase: 'ready',
      progress: 100,
      title: t('app.bootstrap.ready', '系统已就绪'),
      description: t('app.bootstrap.showLoginPage', '正在展示登录页'),
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
        title: t('app.bootstrap.loadBranding', '加载品牌信息'),
        description: restored?.currentUser
          ? t('app.bootstrap.syncPostLoginBranding', '正在同步登录后可见的品牌与外观设置')
          : t('app.bootstrap.syncLoginPageBranding', '正在同步登录页品牌与外观设置'),
        brandName: storedBrandingSettings.websiteName,
        retryInMs: undefined,
        errorMessage: undefined,
      });

      if (restored?.currentUser) {
        return await buildAuthenticatedInitialState(restored, storedBrandingSettings);
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
