import {
  ApiOutlined,
  AppstoreOutlined,
  AuditOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  FileOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  FormOutlined,
  FundOutlined,
  IdcardOutlined,
  NotificationOutlined,
  RobotOutlined,
  SafetyOutlined,
  SettingOutlined,
  SkinOutlined,
  TeamOutlined,
  TranslationOutlined,
  UserOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons';
import { formatMessage } from '@umijs/max';
import type { MenuProps } from 'antd';
import { createElement, type ComponentType, type ReactNode } from 'react';
import { backendRouteMeta } from '@/routes/meta';
import type { RuntimeMenuDataItem } from '@/app.types';
import { resolveBuiltinMessage } from '@/i18n/messages';
import type { MenuNode } from '@/types/api';

type AntdIconComponent = ComponentType<Record<string, unknown>>;

interface SettingsNavigationSourceItem {
  path: string;
  name: string;
  icon?: string;
  access?: string;
  accessAny?: string[];
  matchPrefixes?: string[];
  sortNo?: number;
  children?: SettingsNavigationSourceItem[];
}

const ANT_DESIGN_ICONS: Record<string, AntdIconComponent> = {
  ApiOutlined,
  AppstoreOutlined,
  AuditOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  FileOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  FormOutlined,
  FundOutlined,
  IdcardOutlined,
  NotificationOutlined,
  RobotOutlined,
  SafetyOutlined,
  SettingOutlined,
  SkinOutlined,
  TeamOutlined,
  TranslationOutlined,
  UserOutlined,
  UserSwitchOutlined,
};
const OUTLINED_ICON_SUFFIX = 'Outlined';
const SETTINGS_ROUTE_PREFIX = '/settings';
const PROFILE_PATH = '/user-center/profile';
const LEGACY_SETTING_ROUTE_PREFIXES = ['/system'];
const LEGACY_PATH_ALIASES: Record<string, string> = {
  '/localization': '/settings/localization',
};

export const SETTINGS_PROFILE_PATH = PROFILE_PATH;

const SETTINGS_FALLBACK_ITEMS: SettingsNavigationSourceItem[] = [
  {
    path: '/settings/menus',
    name: 'nav.system.menus',
    icon: 'AppstoreOutlined',
    access: 'canVisitSystemMenus',
  },
  {
    path: '/settings/dicts',
    name: 'nav.system.dicts',
    icon: 'DatabaseOutlined',
    access: 'canVisitSystemDicts',
  },
  {
    path: '/settings/profile-fields',
    name: 'nav.system.profileFields',
    icon: 'FormOutlined',
    access: 'canVisitSystemProfileFields',
  },
  {
    path: '/settings/personalization',
    name: 'nav.system.personalization',
    icon: 'SkinOutlined',
    access: 'canVisitSystemPersonalization',
  },
  {
    path: '/settings/security',
    name: 'nav.system.security',
    icon: 'SafetyOutlined',
    access: 'canVisitSystemSecurity',
  },
  {
    path: '/settings/verification',
    name: 'nav.system.verification',
    icon: 'SafetyOutlined',
    access: 'canVisitSystemVerification',
  },
  {
    path: '/settings/notifications',
    name: 'nav.system.notifications',
    icon: 'NotificationOutlined',
    access: 'canVisitSystemNotifications',
  },
  {
    path: '/settings/ai-employees',
    name: 'nav.system.aiEmployees',
    icon: 'RobotOutlined',
    access: 'canVisitAiEmployees',
  },
  {
    path: '/settings/plugins',
    name: 'nav.system.plugins',
    icon: 'ApiOutlined',
    access: 'canVisitSystemPlugins',
  },
  {
    path: '/settings/files/all',
    name: 'nav.files.all',
    icon: 'FolderOpenOutlined',
    access: 'canVisitSystemAllFiles',
  },
  {
    path: '/settings/localization',
    name: 'nav.localization.root',
    icon: 'TranslationOutlined',
    access: 'canVisitLocalization',
  },
  {
    path: '/settings/monitoring',
    name: 'nav.system.monitoring.root',
    icon: 'FundOutlined',
    access: 'canVisitSystemMonitoring',
  },
  {
    path: '/settings/api-docs',
    name: 'nav.system.monitoring.apiDocs',
    icon: 'FileTextOutlined',
    access: 'canVisitSystemMonitoringDocs',
  },
  {
    path: '/settings/audit',
    name: 'nav.system.monitoring.audit',
    icon: 'AuditOutlined',
    access: 'canVisitAudit',
  },
];

const routeMetaByPath = new Map(backendRouteMeta.map((item) => [item.path, item]));
const settingsFallbackPathSet = new Set(SETTINGS_FALLBACK_ITEMS.map((item) => item.path));

const normalizeMenuPath = (path?: string) => {
  if (!path) {
    return undefined;
  }

  const trimmed = path.trim();
  if (!trimmed) {
    return undefined;
  }

  return LEGACY_PATH_ALIASES[trimmed] || trimmed;
};

const normalizeMenuIconName = (iconName: string) =>
  iconName
    .trim()
    .replace(/(^\w)|-(\w)/g, (_, firstChar: string, hyphenChar: string) => (firstChar || hyphenChar).toUpperCase());

export const resolveNavigationIcon = (icon?: ReactNode | string) => {
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

const cloneSettingsFallbackItems = () => SETTINGS_FALLBACK_ITEMS.map((item) => ({ ...item }));

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

const buildSettingsSourceItems = (menuTree: MenuNode[] | undefined) => {
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

    if (isVisibleSettingsPath(normalizedPath) && settingsFallbackPathSet.has(normalizedPath)) {
      candidateNodes.push(node);
      seenPaths.add(normalizedPath);
    }

    // Recursively flatten children so nested backend nodes become siblings
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
  const matchedItem = visibleItems.find(
    (item) => item.path === normalizedPathname || normalizedPathname.startsWith(`${item.path}/`),
  );

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
