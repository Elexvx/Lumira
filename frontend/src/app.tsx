import * as AntdIcons from '@ant-design/icons';
import { AppstoreOutlined } from '@ant-design/icons';
import type { RunTimeLayoutConfig } from '@umijs/max';
import { createElement, type ComponentType, type ReactNode } from 'react';
import { history } from 'umi';
import {
  DEFAULT_BRANDING_SETTINGS,
  applyFavicon,
  getStoredBrandingSettings,
  normalizeBrandingSettings,
  persistBrandingSettings,
} from '@/branding/settings';
import { SessionActivityGuard } from '@/auth/SessionActivityGuard';
import { DEFAULT_SECURITY_SETTINGS, getStoredSecuritySettings, normalizeSecuritySettings } from '@/auth/securitySettings';
import { getStoredCurrentUser, isLoggedIn, restoreSession } from '@/auth/session';
import { TopActions } from '@/layouts/components/TopActions';
import NoPermission from '@/pages/exception/NoPermission';
import { backendRouteMeta } from '@/routes/meta';
import { pluginService } from '@/services/plugin';
import { systemService } from '@/services/system';
import { tenantContext } from '@/tenant/context';
import type { BrandingSettings, CurrentUser, MenuNode, MyTenant, SecuritySettings, TenantPlugin, TenantSummary } from '@/types/api';

const LOGIN_PATH = '/user/login';
const DEFAULT_HOME_PATH = '/dashboard/home';
const PUBLIC_PATHS = new Set([LOGIN_PATH, '/403', '/404', '/blank/workflow']);

export interface AppInitialState {
  currentUser?: CurrentUser;
  currentTenant?: TenantSummary | null;
  myTenants: MyTenant[];
  menuTree: MenuNode[];
  availablePlugins: TenantPlugin[];
  securitySettings: SecuritySettings;
  brandingSettings: BrandingSettings;
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

const renderBrand = (logoDom: ReactNode, brandingSettings: BrandingSettings) => (
  <div
    onClick={() => {
      history.push(DEFAULT_HOME_PATH);
    }}
    style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}
  >
    {logoDom}
    <span style={{ fontSize: 20, fontWeight: 700, color: '#1f2430' }}>{brandingSettings.websiteName}</span>
  </div>
);

const renderFooter = (brandingSettings: BrandingSettings) => {
  const fallbackCopyright = `Copyright © ${new Date().getFullYear()} ${brandingSettings.websiteName} All Rights Reserved`;
  const copyrightText = brandingSettings.footerCopyright || fallbackCopyright;

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
    name: backendNode.name || localMeta?.name || mergedMeta?.name,
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

export async function getInitialState(): Promise<AppInitialState> {
  const storedBrandingSettings = normalizeBrandingSettings(getStoredBrandingSettings() || DEFAULT_BRANDING_SETTINGS);
  let brandingSettings = storedBrandingSettings;

  try {
    const restored = await restoreSession();
    if (restored?.currentUser) {
      const [[menuTree, availablePlugins], loadedBrandingSettings] = await Promise.all([
        loadPluginBootstrap(),
        loadBrandingSettings(true),
      ]);
      brandingSettings = loadedBrandingSettings;
      return {
        currentUser: restored.currentUser,
        currentTenant: tenantContext.getCurrentTenant(),
        myTenants: tenantContext.getMyTenants(),
        menuTree,
        availablePlugins,
        securitySettings: restored.securitySettings,
        brandingSettings,
      };
    }
  } catch {
  }

  brandingSettings = await loadBrandingSettings(false);

  return {
    currentUser: getStoredCurrentUser() || undefined,
    currentTenant: tenantContext.getCurrentTenant(),
    myTenants: tenantContext.getMyTenants(),
    menuTree: [],
    availablePlugins: [],
    securitySettings: normalizeSecuritySettings(getStoredSecuritySettings() || DEFAULT_SECURITY_SETTINGS),
    brandingSettings,
  };
}

export const layout: RunTimeLayoutConfig = ({ initialState }) => {
  const brandingSettings = normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS);
  const brandName = brandingSettings.websiteName;
  const logoNode = brandingSettings.websiteLogoUrl
    ? <img src={brandingSettings.websiteLogoUrl} alt={brandName} className="saas-brand-logo" />
    : <AppstoreOutlined style={{ fontSize: 16, color: '#1677ff' }} />;

  applyFavicon(brandingSettings.websiteFaviconUrl);

  return {
    title: brandName,
    logo: logoNode,
    fixedHeader: true,
    fixSiderbar: true,
    layout: 'mix',
    splitMenus: false,
    menuHeaderRender: false,
    menuFooterRender: false,
    menuExtraRender: false,
    headerTitleRender: (logoDom) => renderBrand(logoDom, brandingSettings),
    childrenRender: (dom) => <SessionActivityGuard>{dom}</SessionActivityGuard>,
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
        menuVersion: initialState?.menuTree?.length ?? 0,
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

const loadBrandingSettings = async (authenticated: boolean): Promise<BrandingSettings> => {
  const fallback = normalizeBrandingSettings(getStoredBrandingSettings() || DEFAULT_BRANDING_SETTINGS);
  try {
    const settings = normalizeBrandingSettings(
      authenticated
        ? await systemService.brandingSettings({ autoRedirectOnUnauthorized: false, silent: true })
        : await systemService.publicBrandingSettings({ autoRedirectOnUnauthorized: false, silent: true }),
    );
    persistBrandingSettings(settings);
    applyFavicon(settings.websiteFaviconUrl);
    return settings;
  } catch {
    applyFavicon(fallback.websiteFaviconUrl);
    return fallback;
  }
};

const loadPluginBootstrap = async (): Promise<[MenuNode[], TenantPlugin[]]> => {
  try {
    const [menuTree, availablePlugins] = await Promise.all([
      pluginService.currentMenus({ autoRedirectOnUnauthorized: false }),
      pluginService.currentAvailable({ autoRedirectOnUnauthorized: false }),
    ]);
    return [menuTree, availablePlugins];
  } catch {
    return [[], []];
  }
};
