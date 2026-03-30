import { AppstoreOutlined } from '@ant-design/icons';
import type { RunTimeLayoutConfig } from '@umijs/max';
import { history } from 'umi';
import { getStoredCurrentUser, isLoggedIn, restoreSession } from '@/auth/session';
import { TopActions } from '@/layouts/components/TopActions';
import NoPermission from '@/pages/exception/NoPermission';
import { backendRouteMeta } from '@/routes/meta';
import { pluginService } from '@/services/plugin';
import { tenantContext } from '@/tenant/context';
import type { CurrentUser, MenuNode, MyTenant, TenantPlugin, TenantSummary } from '@/types/api';

const LOGIN_PATH = '/user/login';
const DEFAULT_HOME_PATH = '/dashboard/home';
const PUBLIC_PATHS = new Set([LOGIN_PATH, '/403', '/404', '/blank/workflow']);

export interface AppInitialState {
  currentUser?: CurrentUser;
  currentTenant?: TenantSummary | null;
  myTenants: MyTenant[];
  menuTree: MenuNode[];
  availablePlugins: TenantPlugin[];
}

interface RuntimeMenuDataItem {
  path?: string;
  name?: string;
  icon?: string;
  children?: RuntimeMenuDataItem[];
  hideInMenu?: boolean;
}

const routeMetaMap = new Map(backendRouteMeta.map((item) => [item.path, item]));

const isPluginRuntimePath = (path?: string) => Boolean(path && /^\/plugins\/[^/]+$/.test(path));

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
  return {
    ...localMeta,
    path: backendNode.path || localMeta?.path,
    name: backendNode.name || localMeta?.name || mergedMeta?.name,
    icon: backendNode.icon || localMeta?.icon || mergedMeta?.icon,
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
  try {
    const restored = await restoreSession();
    if (restored?.currentUser) {
      const [menuTree, availablePlugins] = await loadPluginBootstrap();
      return {
        currentUser: restored.currentUser,
        currentTenant: tenantContext.getCurrentTenant(),
        myTenants: tenantContext.getMyTenants(),
        menuTree,
        availablePlugins,
      };
    }
  } catch (error) {
  }

  return {
    currentUser: getStoredCurrentUser() || undefined,
    currentTenant: tenantContext.getCurrentTenant(),
    myTenants: tenantContext.getMyTenants(),
    menuTree: [],
    availablePlugins: [],
  };
}

export const layout: RunTimeLayoutConfig = ({ initialState }) => ({
  title: '宏翔商道',
  logo: <AppstoreOutlined style={{ fontSize: 16, color: '#1677ff' }} />,
  fixedHeader: true,
  fixSiderbar: true,
  layout: 'side',
  splitMenus: false,
  menuHeaderRender: (logoDom, titleDom) => (
    <div
      onClick={() => {
        history.push(DEFAULT_HOME_PATH);
      }}
      style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}
    >
      {logoDom}
      {titleDom}
    </div>
  ),
  rightContentRender: () => <TopActions />,
  unAccessible: <NoPermission />,
  pageTitleRender: (props, defaultTitle) => {
    if (!props?.title) {
      return defaultTitle || '宏翔商道';
    }
    return `${props.title} - 宏翔商道`;
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
});

const loadPluginBootstrap = async (): Promise<[MenuNode[], TenantPlugin[]]> => {
  try {
    const [menuTree, availablePlugins] = await Promise.all([
      pluginService.currentMenus({ autoRedirectOnUnauthorized: false }),
      pluginService.currentAvailable({ autoRedirectOnUnauthorized: false }),
    ]);
    return [menuTree, availablePlugins];
  } catch (error) {
    return [[], []];
  }
};
