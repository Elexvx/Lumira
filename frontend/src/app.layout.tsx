import { history } from '@umijs/max';
import type { RunTimeLayoutConfig } from '@umijs/max';
import React from 'react';
import { applyFavicon, buildCopyrightText, normalizeBrandingSettings, DEFAULT_BRANDING_SETTINGS } from '@/branding/settings';
import { SessionActivityGuard } from '@/auth/SessionActivityGuard';
import { isLoggedIn } from '@/auth/session';
import { resolveLoginRedirectTarget } from '@/auth/loginRedirect';
import { TopActions } from '@/layouts/components/TopActions';
import { resolveNavigationIcon } from '@/navigation/settingsNavigation';
import NoPermission from '@/pages/exception/NoPermission';
import { backendRouteMeta } from '@/routes/meta';
import { buildBreadcrumbItems } from '@/app.breadcrumb';
import { DEFAULT_HOME_PATH, LOGIN_PATH, PUBLIC_PATHS } from '@/app.constants';
import { resolveLayoutNavTheme } from '@/theme/runtime';
import type { AppInitialState, RuntimeMenuDataItem } from '@/app.types';
import type { BrandingSettings, MenuNode } from '@/types/api';

const routeMetaMap = new Map(backendRouteMeta.map((item) => [item.path, item]));

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
  const icon = resolveNavigationIcon(backendNode.icon) ?? resolveNavigationIcon(localMeta?.icon) ?? resolveNavigationIcon(mergedMeta?.icon);

  return {
    ...localMeta,
    path: backendNode.path || localMeta?.path,
    name: localMeta?.name || mergedMeta?.name || backendNode.name,
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

export const createLayoutConfig: RunTimeLayoutConfig = ({ initialState }) => {
  const brandingSettings = normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS);
  const brandName = brandingSettings.websiteName;
  const hasBrandLogo = Boolean(brandingSettings.websiteLogoUrl);
  const navTheme = resolveLayoutNavTheme();

  applyFavicon(brandingSettings.websiteFaviconUrl);

  return {
    title: brandName,
    logo: hasBrandLogo ? brandingSettings.websiteLogoUrl : false,
    fixedHeader: false,
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
    headerTitleRender: (logo) => logo,
    menuHeaderRender: false,
    menuFooterRender: false,
    menuExtraRender: false,
    collapsedButtonRender: (_, defaultDom) => defaultDom,
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
      const backendMenus = initialState?.menuTree || [];
      if (!backendMenus.length) {
        return menuData as RuntimeMenuDataItem[];
      }

      const localByPath = flattenLocalMenuMap(menuData as RuntimeMenuDataItem[]);
      const composedMenus = backendMenus
        .map((node) => composeMenuItem(node, localByPath))
        .filter(Boolean) as RuntimeMenuDataItem[];

      return composedMenus;
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
