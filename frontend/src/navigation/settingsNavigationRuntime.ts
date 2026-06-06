import { formatMessage } from '@umijs/max';
import type { MenuProps } from 'antd';
import type { RuntimeMenuDataItem } from '@/app.types';
import { resolveBuiltinMessage } from '@/i18n/messages';
import type { MenuNode } from '@/types/api';
import { resolveNavigationIcon } from './settingsNavigationIcon';
import { buildSettingsSourceItems, normalizeMenuPath } from './settingsNavigationTree';
import type { SettingsNavigationSourceItem } from './settingsNavigationConfig';
import { LEGACY_SETTING_ROUTE_PREFIXES, SETTINGS_ROUTE_PREFIX } from './settingsNavigationConfig';

const mapSourceItemToRuntimeMenuItem = (
  item: SettingsNavigationSourceItem,
  canVisitAccessKey: (accessKey: string) => boolean,
): RuntimeMenuDataItem | null => {
  if (item.access && !canVisitAccessKey(item.access)) {
    return null;
  }

  const children = (item.children || [])
    .map((child) => mapSourceItemToRuntimeMenuItem(child, canVisitAccessKey))
    .filter(Boolean) as RuntimeMenuDataItem[];

  return {
    path: item.path,
    name: resolveBuiltinMessage(
      item.name,
      formatMessage({
        id: item.name,
        defaultMessage: item.name,
      }),
    ),
    locale: false as const,
    icon: resolveNavigationIcon(item.icon),
    hideInMenu: false,
    children: children.length ? children : undefined,
  };
};

const flattenRuntimeMenuItems = (items: RuntimeMenuDataItem[]) => {
  const result: RuntimeMenuDataItem[] = [];

  const walk = (nodes: RuntimeMenuDataItem[]) => {
    nodes.forEach((node) => {
      if (node.path) {
        result.push(node);
      }

      if (node.children?.length) {
        walk(node.children);
      }
    });
  };

  walk(items);
  return result;
};

const buildVisibleSettingsNavigationTree = (menuTree: MenuNode[] | undefined, canVisitAccessKey: (accessKey: string) => boolean) =>
  buildSettingsSourceItems(menuTree)
    .map((item) => mapSourceItemToRuntimeMenuItem(item, canVisitAccessKey))
    .filter(Boolean) as RuntimeMenuDataItem[];

export const buildVisibleSettingsNavigationItems = (menuTree: MenuNode[] | undefined, canVisitAccessKey: (accessKey: string) => boolean) =>
  buildVisibleSettingsNavigationTree(menuTree, canVisitAccessKey);

export const buildSettingsDropdownItems = (menuTree: MenuNode[] | undefined, canVisitAccessKey: (accessKey: string) => boolean): MenuProps['items'] =>
  flattenRuntimeMenuItems(buildVisibleSettingsNavigationTree(menuTree, canVisitAccessKey))
    .filter((item): item is RuntimeMenuDataItem & { path: string; name: string } => Boolean(item.path && item.name))
    .map((item) => ({
      key: item.path,
      icon: item.icon,
      label: item.name,
    }));

export const resolveActiveSettingsNavigationPath = (pathname: string, menuTree: MenuNode[] | undefined, canVisitAccessKey: (accessKey: string) => boolean) => {
  const normalizedPathname = normalizeMenuPath(pathname) || pathname;
  const visibleItems = flattenRuntimeMenuItems(buildVisibleSettingsNavigationTree(menuTree, canVisitAccessKey));
  const exactMatch = visibleItems.find((item) => item.path === normalizedPathname);
  if (exactMatch?.path) {
    return exactMatch.path;
  }

  const matchedItem = visibleItems
    .filter((item) => item.path && normalizedPathname.startsWith(`${item.path}/`))
    .sort((left, right) => (right.path?.length || 0) - (left.path?.length || 0))[0];

  return matchedItem?.path || normalizedPathname;
};

export const resolveFirstSettingsNavigationPath = (menuTree: MenuNode[] | undefined, canVisitAccessKey: (accessKey: string) => boolean) => {
  const visibleItems = flattenRuntimeMenuItems(buildVisibleSettingsNavigationTree(menuTree, canVisitAccessKey));
  return visibleItems[0]?.path;
};

export const isSettingsNavigationPath = (path?: string) => {
  const normalizedPath = normalizeMenuPath(path);
  return Boolean(normalizedPath && (normalizedPath === SETTINGS_ROUTE_PREFIX || normalizedPath.startsWith(`${SETTINGS_ROUTE_PREFIX}/`)));
};

export const isSettingsShellPath = (path?: string) => isSettingsNavigationPath(path);

export const isMainMenuHiddenSettingPath = (path?: string) =>
  Boolean(
    path &&
      (isSettingsNavigationPath(path) ||
        LEGACY_SETTING_ROUTE_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}/`)) ||
        path === '/files' ||
        path === '/files/all' ||
        path === '/localization'),
  );

export const isMainMenuHiddenMonitoringPath = (path?: string) =>
  path === '/settings/monitoring/service' ||
  path === '/settings/monitoring/redis';
