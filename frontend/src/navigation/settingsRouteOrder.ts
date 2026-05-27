import { storage } from '@/cache/storage';

export const DEFAULT_SETTING_ROUTE_ORDER = [
  '/settings/tenants',
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
  '/settings/monitoring/update',
  '/settings/api-docs',
  '/settings/audit',
];

const SETTING_ROUTE_ORDER_KEY = 'settings_route_order';
const SETTING_ROUTE_ICON_KEY = 'settings_route_icons';

export const getStoredSettingRouteOrder = () => {
  const storedOrder = storage.get<string[]>(SETTING_ROUTE_ORDER_KEY) || [];
  const storedPathSet = new Set(storedOrder);
  return [
    ...storedOrder.filter((path) => DEFAULT_SETTING_ROUTE_ORDER.includes(path)),
    ...DEFAULT_SETTING_ROUTE_ORDER.filter((path) => !storedPathSet.has(path)),
  ];
};

export const persistSettingRouteOrder = (order: string[]) => {
  const validPathSet = new Set(DEFAULT_SETTING_ROUTE_ORDER);
  storage.set(
    SETTING_ROUTE_ORDER_KEY,
    order.filter((path, index, array) => validPathSet.has(path) && array.indexOf(path) === index),
  );
};

export const resetSettingRouteOrder = () => {
  storage.remove(SETTING_ROUTE_ORDER_KEY);
};

export const getStoredSettingRouteIcons = () => {
  const validPathSet = new Set(DEFAULT_SETTING_ROUTE_ORDER);
  const storedIcons = storage.get<Record<string, string>>(SETTING_ROUTE_ICON_KEY) || {};
  return Object.fromEntries(
    Object.entries(storedIcons)
      .map(([path, icon]) => [path, icon.trim()])
      .filter(([path, icon]) => validPathSet.has(path) && Boolean(icon)),
  );
};

export const persistSettingRouteIcons = (icons: Record<string, string>) => {
  const validPathSet = new Set(DEFAULT_SETTING_ROUTE_ORDER);
  storage.set(
    SETTING_ROUTE_ICON_KEY,
    Object.fromEntries(
      Object.entries(icons)
        .map(([path, icon]) => [path, icon.trim()])
        .filter(([path, icon]) => validPathSet.has(path) && Boolean(icon)),
    ),
  );
};
