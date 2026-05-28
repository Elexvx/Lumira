import { ArrowLeftOutlined } from '@ant-design/icons';
import { formatMessage, history, useLocation } from '@umijs/max';
import type { RunTimeLayoutConfig } from '@umijs/max';
import React from 'react';
import { Button, Tooltip } from 'antd';
import { applyFavicon, buildCopyrightText, normalizeBrandingSettings, DEFAULT_BRANDING_SETTINGS } from '@/branding/settings';
import { SessionActivityGuard } from '@/auth/SessionActivityGuard';
import { isLoggedIn } from '@/auth/session';
import { resolveLoginRedirectTarget, resolveRouteAccessStatus } from '@/auth/loginRedirect';
import { GlobalFloatActions } from '@/layouts/components/GlobalFloatActions';
import { TopActions } from '@/layouts/components/TopActions';
import {
  buildVisibleSettingsNavigationItems,
  isMainMenuHiddenMonitoringPath,
  isMainMenuHiddenSettingPath,
  isSettingsShellPath,
  resolveNavigationIcon,
} from '@/navigation/settingsNavigation';
import NoPermission from '@/pages/exception/NoPermission';
import { backendRouteMeta, realPageRouteMetaMap } from '@/routes/meta';
import { buildBreadcrumbItems } from '@/app.breadcrumb';
import { DEFAULT_HOME_PATH, LOGIN_PATH, PUBLIC_PATHS } from '@/app.constants';
import { useResponsive } from '@/hooks/useResponsive';
import { resolveProLayoutThemeSettings } from '@/theme/proLayoutTheme';
import { ThemeRuntimeBridge } from '@/theme/ThemeRuntimeBridge';
import type { AppInitialState, RuntimeMenuDataItem } from '@/app.types';
import type { BrandingSettings, MenuNode } from '@/types/api';
import buildAccess from '@/access';
import { resolveBuiltinMessage } from '@/i18n/messages';
import type { BreadcrumbProps } from 'antd';

type BreadcrumbItem = NonNullable<BreadcrumbProps['items']>[number];
type LocalRuntimeMenuDataItem = RuntimeMenuDataItem & { redirect?: string };

const routeMetaMap = new Map(backendRouteMeta.map((item) => [item.path, item]));
const realPagePathSet = new Set(realPageRouteMetaMap.keys());
const LAYOUT_HEADER_HEIGHT = 48;
const STABLE_MAIN_ROUTE_PATHS = ['/dashboard/home', '/ai'];
const HIDDEN_MAIN_MENU_LEAF_PATHS = new Set(['/user-center/personal-center']);
const isPluginRuntimePath = (path?: string) => Boolean(path && /^\/plugins\/[^/]+$/.test(path));
const resolveSiderMenuMode = (pathname: string) => (isSettingsShellPath(pathname) ? 'settings' : 'main');

const flattenLocalMenuMap = (items: RuntimeMenuDataItem[], map = new Map<string, RuntimeMenuDataItem>()) => {
  items.forEach((item) => {
    const localItem = item as LocalRuntimeMenuDataItem;
    if (item.path && !localItem.redirect) {
      map.set(item.path, item);
    }
    if (item.children?.length) {
      flattenLocalMenuMap(item.children, map);
    }
  });

  return map;
};

const collectMenuPaths = (items: RuntimeMenuDataItem[], paths = new Set<string>()) => {
  items.forEach((item) => {
    if (item.path) {
      paths.add(item.path);
    }
    if (item.children?.length) {
      collectMenuPaths(item.children, paths);
    }
  });

  return paths;
};

const hasMenuPathOrChild = (paths: Set<string>, targetPath: string) =>
  paths.has(targetPath) || [...paths].some((path) => path.startsWith(`${targetPath}/`));

const translateBreadcrumbItems = (items: RuntimeMenuDataItem[]): BreadcrumbItem[] =>
  items.map((item) => {
    const breadcrumbTitle = item.title || item.name || item.path || '';
    return {
      key: item.path || item.name || breadcrumbTitle,
      path: item.path,
      title: typeof breadcrumbTitle === 'string'
        ? resolveBuiltinMessage(breadcrumbTitle, formatMessage({ id: breadcrumbTitle, defaultMessage: breadcrumbTitle }))
        : breadcrumbTitle,
    };
  });

const buildSettingsMenuData = (initialState?: AppInitialState) => {
  const access = buildAccess({ currentUser: initialState?.currentUser });
  return buildVisibleSettingsNavigationItems(
    initialState?.menuTree,
    (accessKey) => Boolean((access as Record<string, unknown>)[accessKey]),
  );
};

const buildMainMenuData = (
  initialState: AppInitialState | undefined,
  menuData: RuntimeMenuDataItem[],
  fallbackSourceMenuData: RuntimeMenuDataItem[] = menuData,
) => {
  const access = buildAccess({ currentUser: initialState?.currentUser });
  const visibleMenus = [...menuData] as RuntimeMenuDataItem[];
  const existingPaths = collectMenuPaths(visibleMenus);
  const fallbackByPath = flattenLocalMenuMap(fallbackSourceMenuData);
  const accessMap = access as Record<string, unknown>;

  const fallbackMenus = STABLE_MAIN_ROUTE_PATHS
    .filter((path) => !hasMenuPathOrChild(existingPaths, path))
    .map((path) => {
      const localMenu = fallbackByPath.get(path);
      if (localMenu) {
        return localMenu;
      }

      const meta = routeMetaMap.get(path);
      if (!meta || (meta.access && !accessMap[meta.access])) {
        return null;
      }

      return {
        path: meta.path,
        name: resolveBuiltinMessage(meta.name, formatMessage({ id: meta.name, defaultMessage: meta.name })),
        locale: false as const,
        icon: resolveNavigationIcon(meta.icon),
        hideInMenu: meta.hideInMenu,
      };
    })
    .filter(Boolean) as RuntimeMenuDataItem[];

  return [
    ...fallbackMenus,
    ...visibleMenus,
  ].sort((a, b) => {
    const leftIndex = STABLE_MAIN_ROUTE_PATHS.indexOf(a.path || '');
    const rightIndex = STABLE_MAIN_ROUTE_PATHS.indexOf(b.path || '');
    if (leftIndex !== -1 || rightIndex !== -1) {
      return (leftIndex === -1 ? Number.MAX_SAFE_INTEGER : leftIndex) - (rightIndex === -1 ? Number.MAX_SAFE_INTEGER : rightIndex);
    }
    return 0;
  });
};

const removeRedundantParentPathItems = (
  items: RuntimeMenuDataItem[],
  ancestorGroupPaths = new Set<string>(),
): RuntimeMenuDataItem[] =>
  items
    .map((item) => {
      const nextAncestorGroupPaths = new Set(ancestorGroupPaths);
      if (item.path && item.children?.length) {
        nextAncestorGroupPaths.add(item.path);
      }

      const children = item.children?.length ? removeRedundantParentPathItems(item.children, nextAncestorGroupPaths) : [];
      return {
        ...item,
        children: children.length ? children : undefined,
      };
    })
    .filter((item) => {
      if (!item.path || item.children?.length) {
        return true;
      }
      return !ancestorGroupPaths.has(item.path) && !HIDDEN_MAIN_MENU_LEAF_PATHS.has(item.path);
    });

const translateVisibleLocalMenuData = (
  initialState: AppInitialState | undefined,
  items: RuntimeMenuDataItem[],
): RuntimeMenuDataItem[] => {
  const access = buildAccess({ currentUser: initialState?.currentUser }) as Record<string, unknown>;

  return items.map((item) => {
    const localItem = item as LocalRuntimeMenuDataItem;
    if (localItem.redirect) {
      return null;
    }

    const routeMeta = item.path ? routeMetaMap.get(item.path) : undefined;
    const hasRealPageRoute = item.path ? realPagePathSet.has(item.path) : false;
    const children = item.children?.length ? translateVisibleLocalMenuData(initialState, item.children) : [];
    if ((!routeMeta || !hasRealPageRoute) && !children.length) {
      return null;
    }
    if (routeMeta?.hideInMenu && !children.length) {
      return null;
    }
    if (routeMeta?.access && !access[routeMeta.access] && !children.length) {
      return null;
    }

    const labelId = typeof item.locale === 'string' ? item.locale : item.name || item.title || item.path;
    return {
      ...item,
      path: routeMeta?.path || item.path,
      name: typeof labelId === 'string'
        ? resolveBuiltinMessage(labelId, typeof item.name === 'string' ? item.name : undefined)
        : item.name,
      locale: false as const,
      hideInMenu: routeMeta?.hideInMenu,
      children: children.length ? children : undefined,
    };
  }).filter(Boolean) as RuntimeMenuDataItem[];
};

const composeMenuItem = (
  backendNode: MenuNode,
  localByPath: Map<string, RuntimeMenuDataItem>,
): RuntimeMenuDataItem | null => {
  if (isMainMenuHiddenSettingPath(backendNode.path) || isMainMenuHiddenMonitoringPath(backendNode.path)) {
    return null;
  }

  const localMeta = localByPath.get(backendNode.path);
  const mergedMeta = routeMetaMap.get(backendNode.path || '');
  const hasLocalRoute = Boolean(
    (backendNode.path && realPagePathSet.has(backendNode.path))
      || isPluginRuntimePath(backendNode.path),
  );
  const children = (backendNode.children || [])
    .map((child) => composeMenuItem(child, localByPath))
    .filter(Boolean) as RuntimeMenuDataItem[];

  if (!hasLocalRoute && !children.length) {
    return null;
  }

  const { children: _localChildren, routes: _localRoutes, ...localItemMeta } =
    (localMeta || {}) as RuntimeMenuDataItem & { routes?: RuntimeMenuDataItem[] };
  const icon = resolveNavigationIcon(backendNode.icon) ?? resolveNavigationIcon(localMeta?.icon) ?? resolveNavigationIcon(mergedMeta?.icon);
  const menuLabelId = mergedMeta?.name || backendNode.name || backendNode.menuCode;
  const isRedirectGroup = children.length > 0 && Boolean(backendNode.component?.startsWith('redirect:'));

  return {
    ...localItemMeta,
    path: isRedirectGroup ? undefined : backendNode.path || localMeta?.path,
    name: resolveBuiltinMessage(menuLabelId, formatMessage({ id: menuLabelId, defaultMessage: backendNode.name })),
    locale: false as const,
    icon,
    hideInMenu: localMeta?.hideInMenu || mergedMeta?.hideInMenu,
    children: children.length ? children : undefined,
  };
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

const CollapsedButtonWithReturn = ({ defaultDom }: { defaultDom: React.ReactNode }) => {
  const location = useLocation();
  const { isMobile } = useResponsive();

  if (!isMobile || !isSettingsShellPath(location.pathname)) {
    return <>{defaultDom}</>;
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
      {defaultDom}
      <Tooltip title="返回主路由">
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          aria-label="返回主路由"
          onClick={() => history.push(DEFAULT_HOME_PATH)}
        />
      </Tooltip>
    </div>
  );
};

export const createLayoutConfig: RunTimeLayoutConfig = ({ initialState }) => {
  const brandingSettings = normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS);
  const brandName = brandingSettings.websiteName;
  const hasBrandLogo = Boolean(brandingSettings.websiteLogoUrl);
  const layoutThemeSettings = resolveProLayoutThemeSettings();
  const currentPathname = history.location.pathname;
  const siderMenuMode = resolveSiderMenuMode(currentPathname);

  applyFavicon(brandingSettings.websiteFaviconUrl);

  return {
    title: brandName,
    logo: hasBrandLogo ? brandingSettings.websiteLogoUrl : false,
    fixedHeader: false,
    fixSiderbar: true,
    siderWidth: 200,
    layout: 'mix',
    token: {
      header: {
        heightLayoutHeader: LAYOUT_HEADER_HEIGHT,
      },
    },
    ...layoutThemeSettings,
    splitMenus: false,
    breadcrumbRender: (routers = []) => {
      const pathname = history.location.pathname;
      if (isSettingsShellPath(pathname)) {
        return [];
      }
      const menuBreadcrumb = buildBreadcrumbItems(initialState?.menuTree, pathname);
      return menuBreadcrumb.length ? menuBreadcrumb : translateBreadcrumbItems(routers as RuntimeMenuDataItem[]);
    },
    breadcrumbProps: {
      minLength: 1,
    },
    headerTitleRender: (logo) => logo,
    menuHeaderRender: false,
    menuFooterRender: false,
    menuExtraRender: false,
    collapsedButtonRender: (_, defaultDom) => <CollapsedButtonWithReturn defaultDom={defaultDom} />,
    menuRender: (_, defaultDom) => defaultDom,
    childrenRender: (dom) => (
      <React.Fragment key={initialState?.themeRevision ?? 0}>
        <SessionActivityGuard>
          <ThemeRuntimeBridge />
          {dom}
          <GlobalFloatActions />
        </SessionActivityGuard>
      </React.Fragment>
    ),
    headerContentRender: () => null,
    rightContentRender: () => <TopActions />,
    actionsRender: () => <TopActions />,
    footerRender: () => renderFooter(brandingSettings),
    unAccessible: <NoPermission />,
    pageTitleRender: (props, defaultTitle) => (!props?.title ? defaultTitle || brandName : `${props.title} - ${brandName}`),
    menuTextRender: (item, defaultDom) =>
      typeof defaultDom === 'string'
        ? resolveBuiltinMessage(
            typeof item.locale === 'string' ? item.locale : item.name,
            defaultDom,
          )
        : defaultDom,
    menu: {
      params: {
        pathname: currentPathname,
        siderMenuMode,
        menuVersion: initialState?.menuVersion ?? 0,
        themeRevision: initialState?.themeRevision ?? 0,
      },
    },
    menuDataRender: (menuData) => {
      if (siderMenuMode === 'settings') {
        return buildSettingsMenuData(initialState);
      }

      const backendMenus: MenuNode[] = initialState?.menuTree || [];
      const translatedLocalMenus = translateVisibleLocalMenuData(initialState, menuData as RuntimeMenuDataItem[]);
      if (!backendMenus.length) {
        return buildMainMenuData(initialState, translatedLocalMenus, translatedLocalMenus);
      }

      const localByPath = flattenLocalMenuMap(menuData as RuntimeMenuDataItem[]);
      const composedMenus = backendMenus
        .map((node) => composeMenuItem(node, localByPath))
        .filter(Boolean) as RuntimeMenuDataItem[];

      return removeRedundantParentPathItems(buildMainMenuData(initialState, composedMenus, translatedLocalMenus));
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
        history.replace(resolveLoginRedirectTarget(location.search || '', DEFAULT_HOME_PATH));
        return;
      }

      if (loggedIn && !isPublicPath && initialState?.currentUser) {
        const routeAccessStatus = resolveRouteAccessStatus(path, initialState.currentUser);
        if (routeAccessStatus === 'denied') {
          history.replace('/403');
        }
      }
    },
  };
};
