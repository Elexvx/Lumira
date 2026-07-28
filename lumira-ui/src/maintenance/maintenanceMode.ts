import type { BrandingSettings, CurrentUser } from '@/types/api';

export const MAINTENANCE_ADMIN_PATH = '/settings/personalization';
export const MAINTENANCE_LOGIN_PATH = '/user/login';
export const MAINTENANCE_ADMIN_TARGET = `${MAINTENANCE_ADMIN_PATH}?tab=maintenance`;

export const canManageMaintenanceMode = (currentUser?: CurrentUser | null) => {
  const permissions = currentUser?.permissions || [];
  return permissions.includes('*') || permissions.includes('system:config:update');
};

export const shouldShowMaintenancePage = ({
  brandingSettings,
  pathname,
  currentUser,
}: {
  brandingSettings: BrandingSettings;
  pathname: string;
  currentUser?: CurrentUser | null;
}) => {
  if (!brandingSettings.maintenanceModeEnabled || pathname === MAINTENANCE_LOGIN_PATH) {
    return false;
  }

  return !(pathname === MAINTENANCE_ADMIN_PATH && canManageMaintenanceMode(currentUser));
};
