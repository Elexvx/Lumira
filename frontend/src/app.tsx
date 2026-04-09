import * as AntdIcons from '@ant-design/icons';
import type { RunTimeLayoutConfig } from '@umijs/max';
import type { BreadcrumbProps } from 'antd';
import { Watermark } from 'antd';
import { createElement, type ComponentType, type ReactNode } from 'react';
import { history } from 'umi';
import {
  DEFAULT_BRANDING_SETTINGS,
  buildCopyrightText,
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
import type { BrandingSettings, CurrentUser, MenuNode, MyTenant, SecuritySettings, TenantPlugin, TenantSummary, WatermarkSettings } from '@/types/api';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { ThemePreferenceProvider } from '@/theme/ThemePreferenceProvider';

const LOGIN_PATH = '/user/login';
const DEFAULT_HOME_PATH = '/dashboard/home';
const PUBLIC_PATHS = new Set([LOGIN_PATH, '/403', '/404', '/blank/workflow']);

const DEFAULT_WATERMARK_SETTINGS: WatermarkSettings = {
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
};

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
    title: node.name || routeMetaMap.get(node.path || '')?.name || node.path,
    path: index === trail.length - 1 ? undefined : node.path,
  }));
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
        menuVersion: 0,
        availablePlugins,
        securitySettings: restored.securitySettings,
        brandingSettings,
        watermarkSettings: await systemService.watermarkSettings({ autoRedirectOnUnauthorized: false, silent: true }),
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
    menuVersion: 0,
    availablePlugins: [],
    securitySettings: normalizeSecuritySettings(getStoredSecuritySettings() || DEFAULT_SECURITY_SETTINGS),
    brandingSettings,
    watermarkSettings: DEFAULT_WATERMARK_SETTINGS,
  };
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
    splitMenus: false,
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
      const watermark = initialState?.watermarkSettings || DEFAULT_WATERMARK_SETTINGS;
      const content = <SessionActivityGuard>{dom}</SessionActivityGuard>;
      if (!watermark.enabled) {
        return content;
      }
      return (
        <Watermark
          content={watermark.mode === 'TEXT' ? watermark.textLines : undefined}
          image={watermark.mode === 'IMAGE' ? normalizeUploadUrl(watermark.imageUrl) : undefined}
          gap={[watermark.gapX, watermark.gapY]}
          offset={[watermark.offsetX, watermark.offsetY]}
          zIndex={watermark.zIndex}
          rotate={watermark.rotate}
          font={{ color: watermark.fontColor, fontSize: watermark.fontSize, fontWeight: watermark.fontWeight as never }}
        >
          {content}
        </Watermark>
      );
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
  return <ThemePreferenceProvider>{container}</ThemePreferenceProvider>;
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
