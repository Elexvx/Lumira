import { ArrowLeftOutlined } from '@ant-design/icons';
import { formatMessage, history, useLocation } from '@umijs/max';
import type { RunTimeLayoutConfig } from '@umijs/max';
import React from 'react';
import { Button, Tooltip } from 'antd';
import { applyFavicon, buildCopyrightText, normalizeBrandingSettings, DEFAULT_BRANDING_SETTINGS } from '@/branding/settings';
import { SessionActivityGuard } from '@/auth/SessionActivityGuard';
import { isLoggedIn } from '@/auth/session';
import { resolveLoginRedirectTarget } from '@/auth/loginRedirect';
import { TopActions } from '@/layouts/components/TopActions';
import {
  buildVisibleSettingsNavigationItems,
  isMainMenuHiddenMonitoringPath,
  isMainMenuHiddenSettingPath,
  isSettingsShellPath,
  resolveNavigationIcon,
} from '@/navigation/settingsNavigation';
import NoPermission from '@/pages/exception/NoPermission';
import { backendRouteMeta } from '@/routes/meta';
import { buildBreadcrumbItems } from '@/app.breadcrumb';
import { DEFAULT_HOME_PATH, LOGIN_PATH, PUBLIC_PATHS } from '@/app.constants';
import { useResponsive } from '@/hooks/useResponsive';
import { resolveLayoutNavTheme } from '@/theme/runtime';
import type { AppInitialState, RuntimeMenuDataItem } from '@/app.types';
import type { BrandingSettings, MenuNode } from '@/types/api';
import buildAccess from '@/access';
import type { BreadcrumbProps } from 'antd';

type BreadcrumbItem = NonNullable<BreadcrumbProps['items']>[number];

const routeMetaMap = new Map(backendRouteMeta.map((item) => [item.path, item]));
const LAYOUT_HEADER_HEIGHT = 48;
const LIGHT_SIDER_BACKGROUND = '#ffffff';
const DARK_SIDER_BACKGROUND = '#0c0c0c';
const AI_MAIN_ROUTE_PATH = '/ai';
const isPluginRuntimePath = (path?: string) => Boolean(path && /^\/plugins\/[^/]+$/.test(path));

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

const translateBreadcrumbItems = (items: RuntimeMenuDataItem[]): BreadcrumbItem[] =>
  items.map((item) => {
    const breadcrumbTitle = item.title || item.name || item.path || '';
    return {
      key: item.path || item.name || breadcrumbTitle,
      path: item.path,
      title: typeof breadcrumbTitle === 'string'
        ? formatMessage({ id: breadcrumbTitle, defaultMessage: breadcrumbTitle })
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
) => {
  const access = buildAccess({ currentUser: initialState?.currentUser });
  const visibleMenus = [...menuData] as RuntimeMenuDataItem[];
  const composedMenus = initialState?.menuTree || [];

  const hasAiAssistantMenu = visibleMenus.some((item) => item.path === AI_MAIN_ROUTE_PATH)
    || composedMenus.some((item) => item.path === AI_MAIN_ROUTE_PATH);

  if (!hasAiAssistantMenu && Boolean((access as Record<string, unknown>).canVisitAiAssistant)) {
    const aiRouteMeta = routeMetaMap.get(AI_MAIN_ROUTE_PATH);
    visibleMenus.push({
      path: AI_MAIN_ROUTE_PATH,
      name: formatMessage({
        id: aiRouteMeta?.name || 'nav.ai.assistant',
        defaultMessage: 'AI 助手',
      }),
      icon: resolveNavigationIcon(aiRouteMeta?.icon),
    });
  }

  return visibleMenus;
};

const composeMenuItem = (
  backendNode: MenuNode,
  localByPath: Map<string, RuntimeMenuDataItem>,
): RuntimeMenuDataItem | null => {
  if (isMainMenuHiddenSettingPath(backendNode.path) || isMainMenuHiddenMonitoringPath(backendNode.path)) {
    return null;
  }

  const localMeta = localByPath.get(backendNode.path);
  const hasLocalRoute = localMeta || isPluginRuntimePath(backendNode.path);
  const children = (backendNode.children || [])
    .map((child) => composeMenuItem(child, localByPath))
    .filter(Boolean) as RuntimeMenuDataItem[];

  if (!hasLocalRoute && !children.length) {
    return null;
  }

  const mergedMeta = routeMetaMap.get(backendNode.path || '');
  const icon = resolveNavigationIcon(backendNode.icon) ?? resolveNavigationIcon(localMeta?.icon) ?? resolveNavigationIcon(mergedMeta?.icon);
  const menuLabelId = mergedMeta?.name || backendNode.menuCode || backendNode.name;

  return {
    ...localMeta,
    path: backendNode.path || localMeta?.path,
    name: formatMessage({ id: menuLabelId, defaultMessage: backendNode.name }),
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
  const navTheme = resolveLayoutNavTheme();
  const siderBackground = navTheme === 'realDark' ? DARK_SIDER_BACKGROUND : LIGHT_SIDER_BACKGROUND;

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
      sider: {
        colorMenuBackground: siderBackground,
      },
    },
    navTheme,
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
    childrenRender: (dom) => <SessionActivityGuard>{dom}</SessionActivityGuard>,
    headerContentRender: () => null,
    rightContentRender: () => <TopActions />,
    actionsRender: () => <TopActions />,
    footerRender: () => renderFooter(brandingSettings),
    unAccessible: <NoPermission />,
    pageTitleRender: (props, defaultTitle) => (!props?.title ? defaultTitle || brandName : `${props.title} - ${brandName}`),
    menu: {
      params: {
        menuVersion: initialState?.menuVersion ?? 0,
      },
    },
    menuDataRender: (menuData) => {
      const pathname = history.location.pathname;
      if (isSettingsShellPath(pathname)) {
        return buildSettingsMenuData(initialState);
      }

      const backendMenus = initialState?.menuTree || [];
      if (!backendMenus.length) {
        return menuData as RuntimeMenuDataItem[];
      }

      const localByPath = flattenLocalMenuMap(menuData as RuntimeMenuDataItem[]);
      const composedMenus = backendMenus
        .map((node) => composeMenuItem(node, localByPath))
        .filter(Boolean) as RuntimeMenuDataItem[];

      return buildMainMenuData(initialState, composedMenus);
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
      }
    },
  };
};
