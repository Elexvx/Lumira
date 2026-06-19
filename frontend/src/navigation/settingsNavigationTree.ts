import type { MenuNode, TenantPlugin } from '@/types/api';
import { storage } from '@/cache/storage';
import type { SettingsNavigationSourceItem } from './settingsNavigationConfig';
import {
  DEFAULT_SETTING_ROUTE_ORDER,
  LEGACY_PATH_ALIASES,
  LEGACY_SETTING_ROUTE_PREFIXES,
  SETTINGS_FALLBACK_ITEM_MAP,
  SETTINGS_FALLBACK_PATH_SET,
  SETTINGS_ROUTE_PREFIX,
  SETTING_ROUTE_ORDER_KEY,
  routeMetaByPath,
} from './settingsNavigationConfig';

export const normalizeMenuPath = (path?: string) => {
  if (!path) {
    return undefined;
  }

  const trimmed = path.trim();
  if (!trimmed) {
    return undefined;
  }

  return LEGACY_PATH_ALIASES[trimmed] || trimmed;
};

const getRouteMeta = (path?: string) => {
  const normalizedPath = normalizeMenuPath(path);
  return normalizedPath ? routeMetaByPath.get(normalizedPath) : undefined;
};

const isVisibleSettingsPath = (path?: string) => {
  const normalizedPath = normalizeMenuPath(path);
  return Boolean(normalizedPath && normalizedPath.startsWith(`${SETTINGS_ROUTE_PREFIX}/`) && normalizedPath !== '/settings/overview');
};

const isVisiblePluginPath = (path?: string) => {
  const normalizedPath = normalizeMenuPath(path);
  return Boolean(normalizedPath && normalizedPath.startsWith('/plugins/'));
};

const isSettingsRootNode = (path?: string) => {
  const normalizedPath = normalizeMenuPath(path);
  return normalizedPath === SETTINGS_ROUTE_PREFIX || LEGACY_SETTING_ROUTE_PREFIXES.some((prefix) => normalizedPath === prefix);
};

const findMenuNodeByPath = (menuNodes: MenuNode[] | undefined, targetPath: string): MenuNode | undefined => {
  if (!menuNodes?.length) {
    return undefined;
  }

  const normalizedTarget = normalizeMenuPath(targetPath);

  for (const node of menuNodes) {
    if (normalizeMenuPath(node.path) === normalizedTarget) {
      return node;
    }

    const childMatch = findMenuNodeByPath(node.children, targetPath);
    if (childMatch) {
      return childMatch;
    }
  }

  return undefined;
};

const sortNavigationItems = (items: SettingsNavigationSourceItem[]) =>
  items.sort((left, right) => {
    const leftSort = left.sortNo ?? Number.MAX_SAFE_INTEGER;
    const rightSort = right.sortNo ?? Number.MAX_SAFE_INTEGER;
    if (leftSort !== rightSort) {
      return leftSort - rightSort;
    }

    return left.path.localeCompare(right.path);
  });

const getStoredSettingRouteOrder = () => {
  const storedOrder = storage.get<string[]>(SETTING_ROUTE_ORDER_KEY) || [];
  const storedPathSet = new Set(storedOrder);
  return [
    ...storedOrder.filter((path) => SETTINGS_FALLBACK_PATH_SET.has(path)),
    ...DEFAULT_SETTING_ROUTE_ORDER.filter((path) => !storedPathSet.has(path)),
  ];
};

const cloneSettingsFallbackItems = () =>
  getStoredSettingRouteOrder()
    .map((path) => SETTINGS_FALLBACK_ITEM_MAP.get(path))
    .filter((item): item is SettingsNavigationSourceItem => Boolean(item))
    .map((item) => ({ ...item }))
    .filter((item) => SETTINGS_FALLBACK_PATH_SET.has(item.path))
    .map((item) => ({
      ...item,
      icon: item.icon,
    })) as SettingsNavigationSourceItem[];

const normalizePluginMenuNode = (node: Partial<MenuNode>, plugin: TenantPlugin): SettingsNavigationSourceItem | null => {
  const path = normalizeMenuPath(node.path);
  if (!path || !path.startsWith('/plugins/')) {
    return null;
  }

  return {
    path,
    name: node.name || plugin.pluginName || plugin.pluginCode,
    icon: node.icon || 'ApiOutlined',
    access: node.permissionKey || `plugin:${plugin.pluginCode}:view`,
    sortNo: node.sortNo,
  };
};

const buildAvailablePluginNavigationItems = (availablePlugins: TenantPlugin[] | undefined) =>
  (availablePlugins || [])
    .flatMap((plugin) => {
      const menuItems = (plugin.menus || [])
        .map((menu) => normalizePluginMenuNode(menu, plugin))
        .filter(Boolean) as SettingsNavigationSourceItem[];

      if (menuItems.length) {
        return menuItems;
      }

      const firstRoute = plugin.routes?.find((route) => normalizeMenuPath(route)?.startsWith('/plugins/'));
      if (!firstRoute) {
        return [];
      }

      return [{
        path: firstRoute,
        name: plugin.pluginName || plugin.pluginCode,
        icon: 'ApiOutlined',
        access: `plugin:${plugin.pluginCode}:view`,
      }];
    })
    .sort((left, right) => (left.sortNo ?? Number.MAX_SAFE_INTEGER) - (right.sortNo ?? Number.MAX_SAFE_INTEGER) || left.path.localeCompare(right.path));

const toSourceItem = (node: MenuNode): SettingsNavigationSourceItem | null => {
  const path = normalizeMenuPath(node.path);
  if (!path || !isVisibleSettingsPath(path) && !isSettingsRootNode(path) && !isVisiblePluginPath(path)) {
    return null;
  }

  const routeMeta = getRouteMeta(path);
  const isPluginPath = isVisiblePluginPath(path);

  return {
    path,
    name: isPluginPath ? node.name || path : routeMeta?.name || node.name || path,
    icon: isPluginPath ? node.icon || 'ApiOutlined' : routeMeta?.icon || node.icon,
    access: routeMeta?.access || node.permissionKey,
    sortNo: node.sortNo,
  };
};

export const buildSettingsSourceItems = (menuTree: MenuNode[] | undefined, availablePlugins: TenantPlugin[] | undefined = []) => {
  const rootNode = findMenuNodeByPath(menuTree, SETTINGS_ROUTE_PREFIX);
  const candidateNodes: MenuNode[] = [];
  const seenPaths = new Set<string>();

  const pushCandidate = (node: MenuNode | undefined) => {
    if (!node) {
      return;
    }

    const normalizedPath = normalizeMenuPath(node.path);
    if (!normalizedPath || normalizedPath === SETTINGS_ROUTE_PREFIX || normalizedPath === '/settings/overview' || seenPaths.has(normalizedPath)) {
      return;
    }

    if (isVisiblePluginPath(normalizedPath) || isVisibleSettingsPath(normalizedPath) && SETTINGS_FALLBACK_PATH_SET.has(normalizedPath)) {
      candidateNodes.push(node);
      seenPaths.add(normalizedPath);
    }

    (node.children || []).forEach(pushCandidate);
  };

  if (rootNode?.children?.length) {
    rootNode.children.forEach(pushCandidate);
  }

  (menuTree || [])
    .filter((node) => {
      const normalizedPath = normalizeMenuPath(node.path);
      return Boolean(normalizedPath && normalizedPath.startsWith(`${SETTINGS_ROUTE_PREFIX}/`) && normalizedPath !== '/settings/overview');
    })
    .forEach(pushCandidate);

  const backendItemsByPath = new Map(
    sortNavigationItems(candidateNodes.map((node) => toSourceItem(node)).filter(Boolean) as SettingsNavigationSourceItem[])
      .map((item) => [item.path, item]),
  );

  const pluginItemsByPath = new Map<string, SettingsNavigationSourceItem>();
  sortNavigationItems(candidateNodes.map((node) => toSourceItem(node)).filter((item) => item?.path.startsWith('/plugins/')) as SettingsNavigationSourceItem[])
    .forEach((item) => pluginItemsByPath.set(item.path, item));
  buildAvailablePluginNavigationItems(availablePlugins).forEach((item) => {
    if (!pluginItemsByPath.has(item.path)) {
      pluginItemsByPath.set(item.path, item);
    }
  });
  const pluginItems = Array.from(pluginItemsByPath.values());

  return cloneSettingsFallbackItems().map((fallbackItem) => {
    const item = {
      ...fallbackItem,
      ...backendItemsByPath.get(fallbackItem.path),
      name: fallbackItem.name,
      icon: fallbackItem.icon,
      access: fallbackItem.access,
    };

    if (item.path !== '/settings/plugins') {
      return item;
    }

    return {
      ...item,
      children: [
        {
          path: item.path,
          name: item.name,
          icon: item.icon,
          access: item.access,
          sortNo: Number.MIN_SAFE_INTEGER,
        },
        ...pluginItems,
      ],
    };
  });
};
