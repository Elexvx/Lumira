import * as AntdIcons from '@ant-design/icons';
import { history } from '@umijs/max';
import type { RunTimeLayoutConfig } from '@umijs/max';
import type { BreadcrumbProps } from 'antd';
import React, { createElement, type ComponentType, type ReactNode } from 'react';
import {
  DEFAULT_BRANDING_SETTINGS,
  buildCopyrightText,
  applyFavicon,
  getStoredBrandingSettings,
  normalizeBrandingSettings,
  persistBrandingSettings,
} from '@/branding/settings';
import { SessionActivityGuard } from '@/auth/SessionActivityGuard';
import { normalizeSecuritySettings, persistSecuritySettings } from '@/auth/securitySettings';
import { isLoggedIn, restoreSession } from '@/auth/session';
import { resetBootstrapSnapshot, setBootstrapSnapshot } from '@/bootstrap/bootstrapStore';
import { TopActions } from '@/layouts/components/TopActions';
import NoPermission from '@/pages/exception/NoPermission';
import { backendRouteMeta } from '@/routes/meta';
import { pluginService } from '@/services/plugin';
import { systemService } from '@/services/system';
import { tenantContext } from '@/tenant/context';
import StaticWatermark from '@/layouts/components/StaticWatermark';
import {
  DEFAULT_WATERMARK_SETTINGS,
  getWatermarkSettingsSnapshot,
  persistWatermarkSettings,
  subscribeWatermarkSettings,
} from '@/watermark/settings';
import type {
  BrandingSettings,
  CurrentUser,
  MenuNode,
  MyTenant,
  SecuritySettings,
  TenantPlugin,
  TenantSummary,
  WatermarkSettings,
} from '@/types/api';
import { ThemePreferenceProvider } from '@/theme/ThemePreferenceProvider';
import { useSyncExternalStore } from 'react';

const LOGIN_PATH = '/user/login';
const DEFAULT_HOME_PATH = '/dashboard/home';
const PUBLIC_PATHS = new Set([LOGIN_PATH, '/403', '/404', '/blank/workflow']);
const LAYOUT_HEADER_HEIGHT = 64;
const LAYOUT_SIDER_WIDTH = 224;

const resolveLayoutNavTheme = (): 'light' | 'realDark' => {
  if (typeof document !== 'undefined') {
    const theme = document.documentElement.dataset.theme;
    if (theme === 'dark') {
      return 'realDark';
    }
  }

  return 'light';
};

export interface AppInitialState {
  currentUser?: CurrentUser;
  currentTenant?: TenantSummary | null;
  myTenants: MyTenant[];
  menuTree: MenuNode[];
  menuVersion: number;
  themeRevision?: number;
  availablePlugins: TenantPlugin[];
  securitySettings: SecuritySettings;
  brandingSettings: BrandingSettings;
  watermarkSettings?: WatermarkSettings;
}

interface RuntimeMenuDataItem {
  path?: string;
  name?: string;
  icon?: ReactNode | string;
  children?: RuntimeMenuDataItem[];
  hideInMenu?: boolean;
}

type AntdIconComponent = ComponentType<Record<string, unknown>>;

const ANT_DESIGN_ICONS = AntdIcons as unknown as Record<string, AntdIconComponent>;
const OUTLINED_ICON_SUFFIX = 'Outlined';

const routeMetaMap = new Map(backendRouteMeta.map((item) => [item.path, item]));
type BreadcrumbItem = NonNullable<BreadcrumbProps['items']>[number];

const WatermarkLayer = ({ children }: { children: ReactNode }) => {
  const watermark = useSyncExternalStore(
    subscribeWatermarkSettings,
    getWatermarkSettingsSnapshot,
    () => DEFAULT_WATERMARK_SETTINGS,
  );

  if (!watermark.enabled) {
    return <>{children}</>;
  }

  return <StaticWatermark settings={watermark}>{children}</StaticWatermark>;
};

const isPluginRuntimePath = (path?: string) => Boolean(path && /^\/plugins\/[^/]+$/.test(path));

const normalizeMenuIconName = (iconName: string) =>
  iconName
    .trim()
    .replace(/(^\w)|-(\w)/g, (_, firstChar: string, hyphenChar: string) => (firstChar || hyphenChar).toUpperCase());

const resolveMenuIcon = (icon?: ReactNode | string) => {
  if (!icon) {
    return undefined;
  }

  if (typeof icon !== 'string') {
    return icon;
  }

  const normalizedIconName = normalizeMenuIconName(icon);
  const IconComponent = ANT_DESIGN_ICONS[normalizedIconName] || ANT_DESIGN_ICONS[`${normalizedIconName}${OUTLINED_ICON_SUFFIX}`];

  if (!IconComponent) {
    return undefined;
  }

  return createElement(IconComponent);
};

const renderFooter = (brandingSettings: BrandingSettings) => {
  const copyrightText = brandingSettings.footerCopyright || buildCopyrightText(brandingSettings);

  if (!brandingSettings.footerIcp && !copyrightText) {
    return null;
  }

  return (
    <div className="saas-layout-footer">
      {brandingSettings.footerIcp ? <div className="saas-layout-footer__line">{brandingSettings.footerIcp}</div> : null}
      {copyrightText ? <div className="saas-layout-footer__line">{copyrightText}</div> : null}
    </div>
  );
};

const composeMenuItem = (
  backendNode: MenuNode,
  localByPath: Map<string, RuntimeMenuDataItem>,
): RuntimeMenuDataItem | null => {
  const localMeta = localByPath.get(backendNode.path);
  const hasLocalRoute = localMeta || isPluginRuntimePath(backendNode.path);
  const children = (backendNode.children || [])
    .map((child) => composeMenuItem(child, localByPath))
    .filter(Boolean) as RuntimeMenuDataItem[];

  if (!hasLocalRoute && !children.length) {
    return null;
  }

  const mergedMeta = routeMetaMap.get(backendNode.path || '');
  const icon = resolveMenuIcon(backendNode.icon) ?? resolveMenuIcon(localMeta?.icon) ?? resolveMenuIcon(mergedMeta?.icon);
  return {
    ...localMeta,
    path: backendNode.path || localMeta?.path,
    name: localMeta?.name || mergedMeta?.name || backendNode.name,
    icon,
    hideInMenu: localMeta?.hideInMenu || mergedMeta?.hideInMenu,
    children: children.length ? children : undefined,
  };
};

const flattenLocalMenuMap = (items: RuntimeMenuDataItem[], map = new Map<string, RuntimeMenuDataItem>()) => {
  items.forEach((item) => {
    if (item.path) {
      map.set(item.path, item);
    }
    if (item.children?.length) {
      flattenLocalMenuMap(item.children, map);
    }
  });
  return map;
};

const findMenuTrail = (menuNodes: MenuNode[], pathname: string): MenuNode[] => {
  for (const node of menuNodes) {
    const children = node.children || [];
    const childTrail = children.length ? findMenuTrail(children, pathname) : [];
    if (childTrail.length) {
      return [node, ...childTrail];
    }
    if (node.path === pathname) {
      return [node];
    }
  }
  return [];
};

const buildBreadcrumbItems = (menuNodes: MenuNode[] | undefined, pathname: string): BreadcrumbItem[] => {
  if (!menuNodes?.length) {
    return [];
  }

  const trail = findMenuTrail(menuNodes, pathname);
  if (!trail.length) {
    return [];
  }

  return trail.map((node, index) => ({
    key: node.path || String(node.id),
    title: routeMetaMap.get(node.path || '')?.name || node.name || node.path,
    path: index === trail.length - 1 ? undefined : node.path,
  }));
};

export async function getInitialState(): Promise<AppInitialState> {
  resetBootstrapSnapshot();
  const storedBrandingSettings = normalizeBrandingSettings(getStoredBrandingSettings() || DEFAULT_BRANDING_SETTINGS);
  let retryCount = 0;

  while (true) {
    try {
      await waitForBackendReady();
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

      return await buildGuestInitialState(storedBrandingSettings);
    } catch (error) {
      retryCount += 1;
      const retryInMs = getHealthRetryDelay(retryCount);
      setBootstrapSnapshot({
        phase: 'error',
        progress: Math.min(28 + retryCount * 2, 45),
        title: '启动重试中',
        description: `启动阶段暂时失败，${Math.ceil(retryInMs / 1000)} 秒后重新尝试`,
        retryCount,
        retryInMs,
        errorMessage: getErrorMessage(error),
        brandName: storedBrandingSettings.websiteName,
        ready: false,
      });
      await sleep(retryInMs);
    }
  }
}

export const layout: RunTimeLayoutConfig = ({ initialState }) => {
  const brandingSettings = normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS);
  const brandName = brandingSettings.websiteName;
  const hasBrandLogo = Boolean(brandingSettings.websiteLogoUrl);
  const logoNode = hasBrandLogo ? brandingSettings.websiteLogoUrl : false;
  const navTheme = resolveLayoutNavTheme();

  applyFavicon(brandingSettings.websiteFaviconUrl);

  return {
    title: brandName,
    logo: logoNode,
    fixedHeader: true,
    fixSiderbar: true,
    layout: 'mix',
    navTheme,
    siderWidth: LAYOUT_SIDER_WIDTH,
    splitMenus: false,
    token: {
      header: {
        heightLayoutHeader: LAYOUT_HEADER_HEIGHT,
      },
    },
    breadcrumbRender: (routers = []) => {
      const pathname = history.location.pathname;
      const menuBreadcrumb = buildBreadcrumbItems(initialState?.menuTree, pathname);
      return menuBreadcrumb.length ? menuBreadcrumb : routers;
    },
    breadcrumbProps: {
      minLength: 1,
    },
    headerTitleRender: (logo, title) => (hasBrandLogo ? logo : title),
    menuHeaderRender: false,
    menuFooterRender: false,
    menuExtraRender: false,
    childrenRender: (dom) => {
      return <SessionActivityGuard>{dom}</SessionActivityGuard>;
    },
    headerContentRender: () => null,
    rightContentRender: () => <TopActions />,
    footerRender: () => renderFooter(brandingSettings),
    unAccessible: <NoPermission />,
    pageTitleRender: (props, defaultTitle) => {
      if (!props?.title) {
        return defaultTitle || brandName;
      }
      return `${props.title} - ${brandName}`;
    },
    menu: {
      params: {
        menuVersion: initialState?.menuVersion ?? 0,
      },
    },
    menuDataRender: (menuData) => {
      const backendMenus = initialState?.menuTree || [];
      if (!backendMenus.length) {
        return menuData;
      }

      const localByPath = flattenLocalMenuMap(menuData as RuntimeMenuDataItem[]);
      return backendMenus
        .map((node) => composeMenuItem(node, localByPath))
        .filter(Boolean) as RuntimeMenuDataItem[];
    },
    onPageChange: () => {
      const { location } = history;
      const path = location.pathname;
      const loggedIn = isLoggedIn();
      const isPublicPath = PUBLIC_PATHS.has(path);

      if (!loggedIn && !isPublicPath) {
        const redirect = `${path}${location.search || ''}`;
        history.replace(`${LOGIN_PATH}?redirect=${encodeURIComponent(redirect)}`);
        return;
      }

      if (loggedIn && path === LOGIN_PATH) {
        const searchParams = new URLSearchParams(location.search || '');
        const redirect = searchParams.get('redirect') || DEFAULT_HOME_PATH;
        history.replace(redirect);
      }
    },
  };
};

export const rootContainer = (container: ReactNode) => {
  return (
    <ThemePreferenceProvider>
      <WatermarkLayer>{container}</WatermarkLayer>
    </ThemePreferenceProvider>
  );
};

const loadBrandingSettings = async (authenticated: boolean): Promise<BrandingSettings> => {
  const settings = normalizeBrandingSettings(
    authenticated
      ? await systemService.brandingSettings({
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
          silent: true,
        })
      : await systemService.publicBrandingSettings({ autoRedirectOnUnauthorized: false, silent: true }),
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
    await systemService.publicSecuritySettings({ autoRedirectOnUnauthorized: false, silent: true }),
  );
  persistSecuritySettings(settings);
  return settings;
};

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
    currentTenant: tenantContext.getCurrentTenant(),
    myTenants: tenantContext.getMyTenants(),
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

  const brandingSettings = await loadBrandingSettings(false);
  const securitySettings = await loadPublicSecuritySettings();
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
    currentTenant: null,
    myTenants: [],
    menuTree: [],
    menuVersion: 0,
    availablePlugins: [],
    securitySettings,
    brandingSettings,
    watermarkSettings: DEFAULT_WATERMARK_SETTINGS,
  };
};

const waitForBackendReady = async () => {
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
      const health = await systemService.health({
        autoRedirectOnUnauthorized: false,
        skipAuth: true,
        silent: true,
      });

      if (health?.status && health.status.toUpperCase() !== 'UP') {
        throw new Error(`后端健康状态异常：${health.status}`);
      }

      return;
    } catch (error) {
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

const getHealthRetryDelay = (attempt: number) => {
  const baseDelay = 800;
  const maxDelay = 4000;
  return Math.min(baseDelay * 2 ** Math.min(attempt - 1, 3), maxDelay);
};

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

const getErrorMessage = (error: unknown) => {
  if (error instanceof Error && error.message) {
    return error.message;
  }

  return '后端暂未准备好';
};
