import type { BrandingSettings, CurrentUser } from '@/types/api';

export const MAINTENANCE_ADMIN_PATH = '/settings/personalization';
export const MAINTENANCE_LOGIN_PATH = '/user/login';
export const MAINTENANCE_ADMIN_TARGET = `${MAINTENANCE_ADMIN_PATH}?tab=maintenance`;

export const isMaintenanceAdminLoginTarget = (search = '') =>
  new URLSearchParams(search).get('redirect') === MAINTENANCE_ADMIN_TARGET;

export const canManageMaintenanceMode = (currentUser?: CurrentUser | null) => {
  const permissions = currentUser?.permissions || [];
  return permissions.includes('*') || permissions.includes('system:config:update');
};

export const shouldShowMaintenancePage = ({
  brandingSettings,
  pathname,
  search,
  currentUser,
}: {
  brandingSettings: BrandingSettings;
  pathname: string;
  search?: string;
  currentUser?: CurrentUser | null;
}) => {
  if (!brandingSettings.maintenanceModeEnabled) {
    return false;
  }

  if (pathname === MAINTENANCE_LOGIN_PATH) {
    return !isMaintenanceAdminLoginTarget(search);
  }

  // Once a configuration operator has authenticated, keep the management
  // surface navigable so they can inspect and disable maintenance mode from
  // any settings page. Public and non-operator sessions remain gated.
  return !canManageMaintenanceMode(currentUser);
};
