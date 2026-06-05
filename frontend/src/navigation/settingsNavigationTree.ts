import type { MenuNode } from '@/types/api';
import type { SettingsNavigationSourceItem } from './settingsNavigationConfig';
import {
  LEGACY_PATH_ALIASES,
  LEGACY_SETTING_ROUTE_PREFIXES,
  SETTINGS_FALLBACK_ITEM_MAP,
  SETTINGS_FALLBACK_PATH_SET,
  SETTINGS_ROUTE_PREFIX,
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

const cloneSettingsFallbackItems = () =>
  Array.from(SETTINGS_FALLBACK_ITEM_MAP.values())
    .map((item) => ({ ...item }))
    .filter((item) => SETTINGS_FALLBACK_PATH_SET.has(item.path))
    .map((item) => ({
      ...item,
      icon: item.icon,
    })) as SettingsNavigationSourceItem[];

const toSourceItem = (node: MenuNode): SettingsNavigationSourceItem | null => {
  const path = normalizeMenuPath(node.path);
  if (!path || !isVisibleSettingsPath(path) && !isSettingsRootNode(path)) {
    return null;
  }

  const routeMeta = getRouteMeta(path);

  return {
    path,
    name: routeMeta?.name || node.name || path,
    icon: routeMeta?.icon || node.icon,
    access: routeMeta?.access || node.permissionKey,
    sortNo: node.sortNo,
  };
};

export const buildSettingsSourceItems = (menuTree: MenuNode[] | undefined) => {
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

    if (isVisibleSettingsPath(normalizedPath) && SETTINGS_FALLBACK_PATH_SET.has(normalizedPath)) {
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

  return cloneSettingsFallbackItems().map((fallbackItem) => ({
    ...fallbackItem,
    ...backendItemsByPath.get(fallbackItem.path),
    name: fallbackItem.name,
    icon: fallbackItem.icon,
    access: fallbackItem.access,
  }));
};
