import * as AntdIcons from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { createElement, type ComponentType, type ReactNode } from 'react';
import { backendRouteMeta } from '@/routes/meta';
import type { MenuNode } from '@/types/api';

type AntdIconComponent = ComponentType<Record<string, unknown>>;

interface SettingsNavigationItem {
  key: string;
  label: string;
  path: string;
  icon: string;
  access: string;
  accessAny?: string[];
  matchPrefixes?: string[];
}

const ANT_DESIGN_ICONS = AntdIcons as unknown as Record<string, AntdIconComponent>;
const OUTLINED_ICON_SUFFIX = 'Outlined';

const SETTINGS_ROUTE_PREFIXES = ['/system', '/user-center'];
const PROFILE_PATH = '/user-center/profile';

export const SETTINGS_PROFILE_PATH = PROFILE_PATH;

export const SETTINGS_NAVIGATION_ITEMS: SettingsNavigationItem[] = [
  {
    key: 'plugins',
    label: '插件管理',
    path: '/system/plugins',
    icon: 'ApiOutlined',
    access: 'canVisitSystemPlugins',
  },
  {
    key: 'user-permissions',
    label: '用户和权限',
    path: '/user-center',
    icon: 'TeamOutlined',
    access: 'canVisitUserCenter',
    accessAny: ['canVisitSystemUsers', 'canVisitSystemOnlineUsers', 'canVisitSystemRoles'],
    matchPrefixes: ['/user-center/'],
  },
  {
    key: 'menus',
    label: '菜单管理',
    path: '/system/menus',
    icon: 'AppstoreOutlined',
    access: 'canVisitSystemMenus',
  },
  {
    key: 'dicts',
    label: '字典管理',
    path: '/system/dicts',
    icon: 'DatabaseOutlined',
    access: 'canVisitSystemDicts',
  },
  {
    key: 'profile-fields',
    label: '字段管理',
    path: '/system/profile-fields',
    icon: 'FormOutlined',
    access: 'canVisitSystemProfileFields',
  },
  {
    key: 'personalization',
    label: '个性化设置',
    path: '/system/personalization',
    icon: 'SkinOutlined',
    access: 'canVisitSystemPersonalization',
  },
  {
    key: 'security',
    label: '安全设置',
    path: '/system/security',
    icon: 'SafetyOutlined',
    access: 'canVisitSystemSecurity',
  },
  {
    key: 'verification',
    label: '验证管理',
    path: '/system/verification',
    icon: 'SafetyOutlined',
    access: 'canVisitSystemVerification',
  },
  {
    key: 'notifications',
    label: '站内信归档',
    path: '/system/notifications',
    icon: 'NotificationOutlined',
    access: 'canVisitSystemNotifications',
  },
  {
    key: 'monitoring',
    label: '系统监控',
    path: '/system/monitoring',
    icon: 'FundOutlined',
    access: 'canVisitSystemMonitoring',
    matchPrefixes: ['/system/monitoring/'],
  },
];

const routeMetaByPath = new Map(backendRouteMeta.map((item) => [item.path, item]));

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

export const isSettingsNavigationPath = (path?: string) => {
  if (!path) {
    return false;
  }

  return SETTINGS_ROUTE_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}/`));
};

export const isSettingsShellPath = (path?: string) => isSettingsNavigationPath(path) && path !== PROFILE_PATH;

const collectMenuPaths = (menuNodes: MenuNode[] | undefined, paths = new Set<string>()) => {
  menuNodes?.forEach((node) => {
    if (node.path) {
      paths.add(node.path);
    }
    if (node.children?.length) {
      collectMenuPaths(node.children, paths);
    }
  });

  return paths;
};

const hasMenuPath = (menuPaths: Set<string>, item: SettingsNavigationItem) => {
  if (!menuPaths.size) {
    return true;
  }

  if (menuPaths.has(item.path)) {
    return true;
  }

  const menuPathList = Array.from(menuPaths).filter((path) => path !== PROFILE_PATH);
  return item.matchPrefixes?.some((prefix) => menuPathList.some((path) => path.startsWith(prefix))) ?? false;
};

const canVisitSetting = (item: SettingsNavigationItem, canVisitAccessKey: (accessKey: string) => boolean) => {
  if (item.accessAny?.length) {
    return item.accessAny.some((accessKey) => canVisitAccessKey(accessKey));
  }

  const routeMeta = routeMetaByPath.get(item.path);
  const accessKey = routeMeta?.access || item.access;
  return canVisitAccessKey(accessKey);
};

export const buildVisibleSettingsNavigationItems = (
  menuTree: MenuNode[] | undefined,
  canVisitAccessKey: (accessKey: string) => boolean,
) => {
  const menuPaths = collectMenuPaths(menuTree);

  return SETTINGS_NAVIGATION_ITEMS.filter((item) => item.path !== PROFILE_PATH)
    .filter((item) => hasMenuPath(menuPaths, item))
    .filter((item) => canVisitSetting(item, canVisitAccessKey));
};

export const buildSettingsDropdownItems = (
  menuTree: MenuNode[] | undefined,
  canVisitAccessKey: (accessKey: string) => boolean,
): MenuProps['items'] =>
  buildVisibleSettingsNavigationItems(menuTree, canVisitAccessKey).map((item) => ({
      key: item.path,
      icon: resolveNavigationIcon(item.icon),
      label: item.label,
    }));

export const resolveActiveSettingsPath = (pathname: string, items: SettingsNavigationItem[]) => {
  const matchedItem = items.find((item) => {
    if (pathname === item.path) {
      return true;
    }
    return item.matchPrefixes?.some((prefix) => pathname.startsWith(prefix)) ?? false;
  });

  return matchedItem?.path;
};
