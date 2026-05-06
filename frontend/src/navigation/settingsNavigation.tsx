import * as AntdIcons from '@ant-design/icons';
import { formatMessage } from '@umijs/max';
import type { MenuProps } from 'antd';
import { createElement, type ComponentType, type ReactNode } from 'react';
import { backendRouteMeta } from '@/routes/meta';

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
const SETTINGS_ROUTE_PREFIX = '/settings';
const PROFILE_PATH = '/user-center/profile';
const LEGACY_SETTING_ROUTE_PREFIXES = ['/system'];

export const SETTINGS_PROFILE_PATH = PROFILE_PATH;

export const SETTINGS_NAVIGATION_ITEMS: SettingsNavigationItem[] = [
  {
    key: 'menus',
    label: '菜单管理',
    path: '/settings/menus',
    icon: 'AppstoreOutlined',
    access: 'canVisitSystemMenus',
  },
  {
    key: 'dicts',
    label: '字典管理',
    path: '/settings/dicts',
    icon: 'DatabaseOutlined',
    access: 'canVisitSystemDicts',
  },
  {
    key: 'profile-fields',
    label: '字段管理',
    path: '/settings/profile-fields',
    icon: 'FormOutlined',
    access: 'canVisitSystemProfileFields',
  },
  {
    key: 'personalization',
    label: '个性化设置',
    path: '/settings/personalization',
    icon: 'SkinOutlined',
    access: 'canVisitSystemPersonalization',
  },
  {
    key: 'security',
    label: '安全设置',
    path: '/settings/security',
    icon: 'SafetyOutlined',
    access: 'canVisitSystemSecurity',
  },
  {
    key: 'verification',
    label: '验证管理',
    path: '/settings/verification',
    icon: 'SafetyOutlined',
    access: 'canVisitSystemVerification',
  },
  {
    key: 'notifications',
    label: '站内信归档',
    path: '/settings/notifications',
    icon: 'NotificationOutlined',
    access: 'canVisitSystemNotifications',
  },
  {
    key: 'plugins',
    label: '插件管理',
    path: '/settings/plugins',
    icon: 'ApiOutlined',
    access: 'canVisitSystemPlugins',
  },
  {
    key: 'all-files',
    label: '全站文件管理',
    path: '/settings/files/all',
    icon: 'FolderOpenOutlined',
    access: 'canVisitSystemAllFiles',
  },
  {
    key: 'monitoring',
    label: '系统监控',
    path: '/settings/monitoring',
    icon: 'FundOutlined',
    access: 'canVisitSystemMonitoring',
    matchPrefixes: ['/settings/monitoring/'],
  },
  {
    key: 'api-docs',
    label: '接口文档',
    path: '/settings/monitoring/api-docs',
    icon: 'FileTextOutlined',
    access: 'canVisitSystemMonitoringDocs',
  },
  {
    key: 'audit',
    label: '审计中心',
    path: '/settings/monitoring/audit',
    icon: 'AuditOutlined',
    access: 'canVisitAudit',
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

export const isSettingsNavigationPath = (path?: string) => Boolean(path && (path === SETTINGS_ROUTE_PREFIX || path.startsWith(`${SETTINGS_ROUTE_PREFIX}/`)));

export const isSettingsShellPath = (path?: string) => isSettingsNavigationPath(path);

export const isMainMenuHiddenSettingPath = (path?: string) =>
  Boolean(
    path &&
      (isSettingsNavigationPath(path) ||
        LEGACY_SETTING_ROUTE_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}/`)) ||
        path === '/files' ||
        path === '/files/all'),
  );

export const isMainMenuHiddenMonitoringPath = (path?: string) =>
  path === '/settings/monitoring/service' ||
  path === '/settings/monitoring/redis' ||
  path === '/settings/monitoring/api-docs' ||
  path === '/settings/monitoring/audit';

const canVisitSetting = (item: SettingsNavigationItem, canVisitAccessKey: (accessKey: string) => boolean) => {
  if (item.accessAny?.length) {
    return item.accessAny.some((accessKey) => canVisitAccessKey(accessKey));
  }

  const routeMeta = routeMetaByPath.get(item.path);
  const accessKey = routeMeta?.access || item.access;
  return canVisitAccessKey(accessKey);
};

export const buildVisibleSettingsNavigationItems = (canVisitAccessKey: (accessKey: string) => boolean) =>
  SETTINGS_NAVIGATION_ITEMS.filter((item) => item.path !== PROFILE_PATH).filter((item) => canVisitSetting(item, canVisitAccessKey));

export const buildSettingsDropdownItems = (canVisitAccessKey: (accessKey: string) => boolean): MenuProps['items'] =>
  buildVisibleSettingsNavigationItems(canVisitAccessKey).map((item) => ({
    key: item.path,
    icon: resolveNavigationIcon(item.icon),
    label: formatMessage({
      id: routeMetaByPath.get(item.path)?.name || item.key,
      defaultMessage: item.label,
    }),
  }));

export const resolveActiveSettingsNavigationPath = (pathname: string, canVisitAccessKey: (accessKey: string) => boolean) => {
  const visibleItems = buildVisibleSettingsNavigationItems(canVisitAccessKey);
  const matchedItem = visibleItems.find(
    (item) =>
      pathname === item.path ||
      item.matchPrefixes?.some((prefix) => pathname.startsWith(prefix)) ||
      pathname.startsWith(`${item.path}/`),
  );

  return matchedItem?.path || pathname;
};
