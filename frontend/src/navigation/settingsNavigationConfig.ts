import { backendRouteMeta } from '@/routes/meta';

export type SettingsNavigationSourceItem = {
  path: string;
  name: string;
  icon?: string;
  access?: string;
  accessAny?: string[];
  matchPrefixes?: string[];
  sortNo?: number;
  children?: SettingsNavigationSourceItem[];
};

export const ANT_DESIGN_ICON_SUFFIX = 'Outlined';
export const SETTINGS_ROUTE_PREFIX = '/settings';
export const PROFILE_PATH = '/user-center/personal-center/profile';
export const LEGACY_SETTING_ROUTE_PREFIXES = ['/system'];
export const SETTING_ROUTE_ORDER_KEY = 'settings_route_order';
export const DEFAULT_SETTING_ROUTE_ORDER = [
  '/settings/menus',
  '/settings/dicts',
  '/settings/profile-fields',
  '/settings/personalization',
  '/settings/security',
  '/settings/verification',
  '/settings/notifications',
  '/settings/ai-employees',
  '/settings/plugins',
  '/settings/files/all',
  '/settings/localization',
  '/settings/monitoring',
  '/settings/api-docs',
  '/settings/audit',
];
export const LEGACY_PATH_ALIASES: Record<string, string> = {
  '/localization': '/settings/localization',
  '/settings/monitoring/api-docs': '/settings/api-docs',
  '/settings/monitoring/audit': '/settings/audit',
  '/system/payment': '/settings/payment',
};

export const SETTINGS_FALLBACK_ITEM_MAP = new Map<string, SettingsNavigationSourceItem>([
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
    path: '/settings/payment',
    name: 'nav.system.payment',
    icon: 'CreditCardOutlined',
    access: 'canVisitSystemPayment',
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
].map((item) => [item.path, item]));

export const SETTINGS_FALLBACK_PATH_SET = new Set([
  '/settings/menus',
  '/settings/dicts',
  '/settings/profile-fields',
  '/settings/personalization',
  '/settings/security',
  '/settings/verification',
  '/settings/payment',
  '/settings/notifications',
  '/settings/ai-employees',
  '/settings/plugins',
  '/settings/files/all',
  '/settings/localization',
  '/settings/monitoring',
  '/settings/api-docs',
  '/settings/audit',
]);

export const routeMetaByPath = new Map(backendRouteMeta.map((item) => [item.path, item]));
