import type { CurrentUser } from '@/types/api';

const PROTECTED_ADMIN_ID = 1001;
const PROTECTED_ADMIN_USERNAME = 'admin';
const SUPER_ADMIN_ROLE_CODES = new Set(['super_admin', 'super-admin', 'superadmin', 'admin']);
const SUPER_ADMIN_ROLE_NAMES = new Set(['超级管理员', '平台超级管理员', '系统超级管理员', 'super admin', 'super administrator']);

export const isProtectedAdminAccount = (user?: Pick<CurrentUser, 'userId' | 'username'> | null) =>
  Boolean(user && (user.userId === PROTECTED_ADMIN_ID || user.username?.toLowerCase() === PROTECTED_ADMIN_USERNAME));

export const isSuperAdminUser = (user?: CurrentUser | null) => {
  if (!user || user.simulatedRoleId) {
    return false;
  }
  if (isProtectedAdminAccount(user)) {
    return true;
  }

  return Boolean(user.availableRoles?.some((role) => {
    const roleCode = role.roleCode?.trim().toLowerCase();
    const roleName = role.roleName?.trim().toLowerCase();
    return SUPER_ADMIN_ROLE_CODES.has(roleCode) || SUPER_ADMIN_ROLE_NAMES.has(roleName);
  }));
};

export const isSettingsPermission = (permission: string) =>
  permission.startsWith('payment:') ||
  permission.startsWith('system:config:') ||
  permission.startsWith('system:dict:') ||
  permission.startsWith('system:file:manage') ||
  permission.startsWith('system:menu:') ||
  permission.startsWith('system:monitor:') ||
  permission.startsWith('system:notification:') ||
  permission.startsWith('system:profile-field:') ||
  permission.startsWith('system:profile_field:') ||
  permission.startsWith('system:security:') ||
  permission.startsWith('system:update:') ||
  permission.startsWith('system:verification:') ||
  permission.startsWith('plugin:management:') ||
  permission.startsWith('plugin:sensitive-words:') ||
  permission.startsWith('audit:') ||
  permission.startsWith('localization:') ||
  permission === 'system:file:manage' ||
  permission === 'system:monitor:view' ||
  permission === 'plugin:management:view' ||
  permission === 'audit:view' ||
  permission === 'localization:view';
